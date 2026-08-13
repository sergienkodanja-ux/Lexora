package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HitIndicatorRenderer {

    private static final List<Indicator> indicators = new ArrayList<>();
    private static float lastHealth = -1.0f;

    // --- УМНЫЕ ХЕЛПЕРЫ ДЛЯ НАСТРОЕК (как в LexoraSkyRenderer) ---
    private static boolean isEnabled(String key, boolean fallback) {
        if (LexoraGui.moduleStates.containsKey(key)) return LexoraGui.moduleStates.get(key);
        return ClientData.moduleStates.getOrDefault(key, fallback);
    }

    private static float getNum(String key, float fallback) {
        if (LexoraGui.numSettings.containsKey(key)) return LexoraGui.numSettings.get(key);
        return ClientData.numSettings.getOrDefault(key, fallback);
    }
    // -------------------------------------------------------------

    private static class Indicator {
        private final float worldYaw;
        private final long spawnTime;

        private Indicator(float worldYaw, long spawnTime) {
            this.worldYaw = worldYaw;
            this.spawnTime = spawnTime;
        }
    }

    // ВЫЗЫВАТЬ КАЖДЫЙ ТИК (ClientTickEvents.END_CLIENT_TICK)
    public static void onTick(MinecraftClient mc) {
        if (!isEnabled("Hit Indicator", false)) {
            indicators.clear();
            lastHealth = -1.0f;
            return;
        }

        if (mc.player == null || mc.world == null) {
            lastHealth = -1.0f;
            indicators.clear();
            return;
        }

        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (lastHealth < 0.0f) {
            lastHealth = currentHealth;
            return;
        }

        // Если здоровье уменьшилось (получили урон)
        if (currentHealth + 0.01f < lastHealth) {
            Float yaw = resolveDamageYaw(mc);
            if (yaw != null) {
                indicators.add(new Indicator(yaw, System.currentTimeMillis()));
                // Ограничиваем количество одновременных индикаторов
                while (indicators.size() > 8) {
                    indicators.remove(0);
                }
            }
        }

        lastHealth = currentHealth;
    }

    // ВЫЗЫВАТЬ В ИВЕНТЕ РЕНДЕРА HUD (Там же, где PredictionRenderer.renderHud)
    public static void renderHud(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || indicators.isEmpty() || mc.options.hudHidden) {
            return;
        }
        if (!isEnabled("Hit Indicator", false)) return;

        long now = System.currentTimeMillis();
        float width = mc.getWindow().getScaledWidth();
        float height = mc.getWindow().getScaledHeight();
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;

        float maxRadius = Math.max(24.0f, Math.min(centerX, centerY) - 8.0f);

        // Получаем настройки
        float radius = Math.min(getNum("Hit Radius", 125.0f), maxRadius);
        float arcLength = getNum("Hit Arc Length", 76.0f);
        float thickness = getNum("Hit Thickness", 2.4f);
        float duration = getNum("Hit Duration", 700.0f);
        float opacity = getNum("Hit Opacity", 0.9f);
        boolean glow = isEnabled("Hit Glow", true);

        float cameraYaw = mc.gameRenderer.getCamera().getYaw();

        Iterator<Indicator> iterator = indicators.iterator();
        while (iterator.hasNext()) {
            Indicator indicator = iterator.next();
            float age = now - indicator.spawnTime;

            if (age >= duration) {
                iterator.remove();
                continue;
            }

            float progress = MathHelper.clamp(age / duration, 0.0f, 1.0f);
            float fadeIn = MathHelper.clamp(age / 90.0f, 0.0f, 1.0f);

            // Математика прозрачности из сурса
            float alpha = opacity * fadeIn * (1.0f - progress * progress);
            if (alpha <= 0.02f) continue;

            float relativeYaw = MathHelper.wrapDegrees(indicator.worldYaw - cameraYaw);

            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

            if (glow) {
                drawArc(matrix, centerX, centerY, radius, relativeYaw, arcLength, thickness + 3.2f, alpha * 0.22f);
            }
            drawArc(matrix, centerX, centerY, radius, relativeYaw, arcLength, thickness, alpha);
        }
    }

    private static Float resolveDamageYaw(MinecraftClient mc) {
        float sourceRange = getNum("Hit Source Range", 32.0f);
        Entity source = mc.player.getAttacker();

        if (!isValidSource(source, mc, sourceRange)) {
            source = getNearestLivingSource(mc, sourceRange);
        }
        if (!isValidSource(source, mc, sourceRange)) {
            return null;
        }

        double dx = source.getX() - mc.player.getX();
        double dz = source.getZ() - mc.player.getZ();

        if (dx * dx + dz * dz < 1.0E-4D) return null;

        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
    }

    private static Entity getNearestLivingSource(MinecraftClient mc, float maxRange) {
        Entity nearest = null;
        Entity nearestSwinging = null;
        double bestDistance = maxRange * maxRange;
        double bestSwingingDistance = bestDistance;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValidSource(entity, mc, maxRange)) {
                continue;
            }

            double distance = mc.player.squaredDistanceTo(entity);

            // Если энтити машет рукой, приоритет ему
            if (living.handSwinging && distance < bestSwingingDistance) {
                bestSwingingDistance = distance;
                nearestSwinging = entity;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = entity;
            }
        }
        return nearestSwinging != null ? nearestSwinging : nearest;
    }

    private static boolean isValidSource(Entity entity, MinecraftClient mc, float maxRange) {
        return entity != null
                && entity != mc.player
                && entity.isAlive()
                && mc.player != null
                && mc.player.squaredDistanceTo(entity) <= maxRange * maxRange;
    }

    // Современный рендер дуги через TRIANGLE_STRIP
    private static void drawArc(Matrix4f matrix, float centerX, float centerY, float radius, float yaw, float length, float thickness, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Отключаем Culling и Depth Test
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        int segments = Math.max(18, (int) (length / 3.0f));
        float start = yaw - length / 2.0f;
        int baseAlpha = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);

        float rInner = radius - thickness / 2.0f;
        float rOuter = radius + thickness / 2.0f;

        for (int i = 0; i <= segments; i++) {
            float part = (float) i / (float) segments;
            float edge = Math.min(part, 1.0f - part) * 2.0f;
            int pointAlpha = (int) (baseAlpha * (0.35f + 0.65f * edge));

            float angle = (float) Math.toRadians(start + length * part);
            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);

            buf.vertex(matrix, centerX + sin * rInner, centerY - cos * rInner, 0.0f).color(255, 35, 45, pointAlpha);
            buf.vertex(matrix, centerX + sin * rOuter, centerY - cos * rOuter, 0.0f).color(255, 35, 45, pointAlpha);
        } // <- Вот тут заканчивается цикл

        // Эти строчки теперь правильно находятся ВНУТРИ метода
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}