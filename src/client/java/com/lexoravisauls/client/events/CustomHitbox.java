package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CustomHitbox {

    // Плавная анимация появления/исчезновения хитбокса сущностей
    private static final Map<UUID, Float> animProgress = new HashMap<>();

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Custom Hitboxes", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        String style = LexoraGui.modeSettings.getOrDefault("Hitbox Style", "Solid");
        String colorMode = LexoraGui.modeSettings.getOrDefault("Hitbox Color Mode", "Custom");
        float alphaSetting = LexoraGui.numSettings.getOrDefault("Hitbox Alpha", 0.45f);
        float lineWidth = LexoraGui.numSettings.getOrDefault("Hitbox Line Width", 2.0f);
        float maxRange = LexoraGui.numSettings.getOrDefault("HB Range", 32.0f);
        boolean targetOnly = LexoraGui.moduleStates.getOrDefault("HB Only Target", false);
        boolean hurtPulse = LexoraGui.moduleStates.getOrDefault("HB Hurt Pulse", true);
        boolean renderPlayers = LexoraGui.moduleStates.getOrDefault("HB Players", true);
        boolean renderMobs = LexoraGui.moduleStates.getOrDefault("HB Mobs", false);
        boolean renderItems = LexoraGui.moduleStates.getOrDefault("HB Items", false);

        Entity crossTarget = null;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            crossTarget = ((EntityHitResult) mc.crosshairTarget).getEntity();
        }

        Vec3d cameraPos = camera.getPos();
        float time = (System.currentTimeMillis() % 100000L) / 1000f;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (entity.isRemoved()) continue;
            if (entity.isInvisible()) continue;

            boolean typeValid = false;
            if (entity instanceof PlayerEntity && renderPlayers) typeValid = true;
            else if ((entity instanceof HostileEntity || entity instanceof MobEntity) && renderMobs) typeValid = true;
            else if (entity instanceof ItemEntity && renderItems) typeValid = true;

            if (!typeValid) continue;

            double distSq = entity.squaredDistanceTo(mc.player);
            if (distSq > maxRange * maxRange) continue;

            UUID id = entity.getUuid();
            boolean isTarget = (entity == crossTarget);

            float currentAnim = animProgress.getOrDefault(id, 0.0f);
            if (targetOnly) {
                currentAnim = isTarget ? Math.min(1.0f, currentAnim + 0.15f) : Math.max(0.0f, currentAnim - 0.15f);
            } else {
                currentAnim = Math.min(1.0f, currentAnim + 0.15f);
            }
            animProgress.put(id, currentAnim);

            if (currentAnim <= 0.01f) continue;

            // Вычисление цвета
            float r, g, b;
            if (colorMode.equals("Theme")) {
                int themeRgb = LexoraGui.getThemeColor(0);
                r = ((themeRgb >> 16) & 0xFF) / 255f;
                g = ((themeRgb >> 8) & 0xFF) / 255f;
                b = (themeRgb & 0xFF) / 255f;
            } else if (colorMode.equals("Health") && entity instanceof LivingEntity living) {
                float hpPercent = Math.max(0.0f, Math.min(1.0f, living.getHealth() / Math.max(1.0f, living.getMaxHealth())));
                if (hpPercent > 0.5f) {
                    float t = (hpPercent - 0.5f) * 2.0f;
                    r = 1.0f - t;
                    g = 1.0f;
                    b = 0.1f;
                } else {
                    float t = hpPercent * 2.0f;
                    r = 1.0f;
                    g = t;
                    b = 0.1f;
                }
            } else if (colorMode.equals("Rainbow")) {
                float hue = (time * 0.2f + (entity.getId() % 10) * 0.1f) % 1.0f;
                int rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f);
                r = ((rgb >> 16) & 0xFF) / 255f;
                g = ((rgb >> 8) & 0xFF) / 255f;
                b = (rgb & 0xFF) / 255f;
            } else {
                float[] hsv = LexoraGui.colorSettings.getOrDefault("Hitbox Color", new float[]{280f / 360f, 1f, 1f});
                int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
                r = ((rgb >> 16) & 0xFF) / 255f;
                g = ((rgb >> 8) & 0xFF) / 255f;
                b = (rgb & 0xFF) / 255f;
            }

            // Реакция на получение урона (красная вспышка)
            if (hurtPulse && entity instanceof LivingEntity living && living.hurtTime > 0) {
                float hurtFactor = living.hurtTime / 10.0f;
                r = MathHelper.lerp(hurtFactor, r, 1.0f);
                g = MathHelper.lerp(hurtFactor, g, 0.15f);
                b = MathHelper.lerp(hurtFactor, b, 0.15f);
            }

            float finalAlpha = alphaSetting * currentAnim;

            // Точные координаты с субпиксельной интерполяцией
            double lerpX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX()) - cameraPos.x;
            double lerpY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY()) - cameraPos.y;
            double lerpZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ()) - cameraPos.z;

            Box bb = entity.getBoundingBox();
            float hw = (float) (bb.getLengthX() * 0.5);
            float h = (float) bb.getLengthY();
            float hd = (float) (bb.getLengthZ() * 0.5);

            float x1 = -hw, y1 = 0.0f, z1 = -hd;
            float x2 = hw, y2 = h, z2 = hd;

            matrices.push();
            matrices.translate(lerpX, lerpY, lerpZ);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

            try {
                if (style.equals("Solid")) {
                    drawSolidBox(matrices, x1, y1, z1, x2, y2, z2, r, g, b, finalAlpha);
                    drawBoxOutline(matrices, x1, y1, z1, x2, y2, z2, r, g, b, Math.min(1.0f, finalAlpha * 2.0f + 0.3f), lineWidth);
                } else if (style.equals("Nebula")) {
                    drawNebulaBox(matrices, x1, y1, z1, x2, y2, z2, r, g, b, finalAlpha, time);
                    drawBoxOutline(matrices, x1, y1, z1, x2, y2, z2, r, g, b, Math.min(1.0f, finalAlpha * 2.0f + 0.25f), lineWidth);
                } else {
                    // Flame (Плазма / Огонь)
                    drawFlameBox(matrices, x1, y1, z1, x2, y2, z2, r, g, b, finalAlpha, time);
                    drawBoxOutline(matrices, x1, y1, z1, x2, y2, z2, Math.min(1f, r + 0.2f), Math.min(1f, g + 0.2f), b, Math.min(1.0f, finalAlpha * 2.0f + 0.3f), lineWidth);
                }
            } finally {
                matrices.pop();
            }
        }

        // Восстановление глобальных состояний рендера
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void drawSolidBox(MatrixStack matrices, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Front
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);

        // Back
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);

        // Left
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        // Right
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);

        // Top
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        // Bottom
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawFlameBox(MatrixStack matrices, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float time) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Плавная динамическая пульсация пламени
        float flameFlicker1 = (float) (Math.sin(time * 4.5f) * 0.2f + 0.8f);
        float flameFlicker2 = (float) (Math.cos(time * 3.8f + 1.2f) * 0.2f + 0.8f);
        float flameFlicker3 = (float) (Math.sin(time * 5.2f + 2.4f) * 0.2f + 0.8f);
        float flameFlicker4 = (float) (Math.cos(time * 4.1f + 3.6f) * 0.2f + 0.8f);

        // Основание (горячее, яркое ядро пламени)
        float baseR = Math.min(1.0f, r + 0.3f);
        float baseG = Math.min(1.0f, g + 0.2f);
        float baseB = b * 0.8f;
        float baseAlpha = Math.min(1.0f, a * 1.1f);

        // Верх (языки пламени, плавно затухающие и колышущиеся)
        float topR = r;
        float topG = g * 0.85f;
        float topB = b * 0.6f;
        float topA1 = a * 0.35f * flameFlicker1;
        float topA2 = a * 0.35f * flameFlicker2;
        float topA3 = a * 0.35f * flameFlicker3;
        float topA4 = a * 0.35f * flameFlicker4;

        // Front (x1..x2, y1..y2, z2)
        buffer.vertex(matrix, x1, y1, z2).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x2, y1, z2).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x2, y2, z2).color(topR, topG, topB, topA2);
        buffer.vertex(matrix, x1, y2, z2).color(topR, topG, topB, topA1);

        // Back (x2..x1, y1..y2, z1)
        buffer.vertex(matrix, x2, y1, z1).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x1, y1, z1).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x1, y2, z1).color(topR, topG, topB, topA4);
        buffer.vertex(matrix, x2, y2, z1).color(topR, topG, topB, topA3);

        // Left (x1, y1..y2, z1..z2)
        buffer.vertex(matrix, x1, y1, z1).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x1, y1, z2).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x1, y2, z2).color(topR, topG, topB, topA1);
        buffer.vertex(matrix, x1, y2, z1).color(topR, topG, topB, topA4);

        // Right (x2, y1..y2, z2..z1)
        buffer.vertex(matrix, x2, y1, z2).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x2, y1, z1).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x2, y2, z1).color(topR, topG, topB, topA3);
        buffer.vertex(matrix, x2, y2, z2).color(topR, topG, topB, topA2);

        // Top
        float avgTopA = (topA1 + topA2 + topA3 + topA4) * 0.25f;
        buffer.vertex(matrix, x1, y2, z2).color(topR, topG, topB, avgTopA);
        buffer.vertex(matrix, x2, y2, z2).color(topR, topG, topB, avgTopA);
        buffer.vertex(matrix, x2, y2, z1).color(topR, topG, topB, avgTopA);
        buffer.vertex(matrix, x1, y2, z1).color(topR, topG, topB, avgTopA);

        // Bottom
        buffer.vertex(matrix, x1, y1, z1).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x2, y1, z1).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x2, y1, z2).color(baseR, baseG, baseB, baseAlpha);
        buffer.vertex(matrix, x1, y1, z2).color(baseR, baseG, baseB, baseAlpha);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawNebulaBox(MatrixStack matrices, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float time) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Космические гармоники по 4 углам
        float w1 = (float) (Math.sin(time * 1.8f) * 0.35f + 0.65f);
        float w2 = (float) (Math.cos(time * 2.2f + 1.0f) * 0.35f + 0.65f);
        float w3 = (float) (Math.sin(time * 1.5f + 2.0f) * 0.35f + 0.65f);
        float w4 = (float) (Math.cos(time * 2.6f + 3.0f) * 0.35f + 0.65f);

        // Цвета вершин: красивый космический градиент (индиго, фиолетовый, бирюзовый, неоновый)
        float c1R = Math.min(1.0f, r * w1 + 0.15f), c1G = g * w1, c1B = Math.min(1.0f, b * w1 + 0.25f), c1A = a * (0.5f + 0.5f * w1);
        float c2R = r * w2, c2G = Math.min(1.0f, g * w2 + 0.15f), c2B = Math.min(1.0f, b * w2 + 0.35f), c2A = a * (0.5f + 0.5f * w2);
        float c3R = Math.min(1.0f, r * w3 + 0.25f), c3G = Math.min(1.0f, g * w3 + 0.1f), c3B = b * w3, c3A = a * (0.5f + 0.5f * w3);
        float c4R = r * w4, c4G = g * w4, c4B = Math.min(1.0f, b * w4 + 0.3f), c4A = a * (0.5f + 0.5f * w4);

        // Front
        buffer.vertex(matrix, x1, y1, z2).color(c1R, c1G, c1B, c1A);
        buffer.vertex(matrix, x2, y1, z2).color(c2R, c2G, c2B, c2A);
        buffer.vertex(matrix, x2, y2, z2).color(c3R, c3G, c3B, c3A);
        buffer.vertex(matrix, x1, y2, z2).color(c4R, c4G, c4B, c4A);

        // Back
        buffer.vertex(matrix, x2, y1, z1).color(c2R, c2G, c2B, c2A);
        buffer.vertex(matrix, x1, y1, z1).color(c1R, c1G, c1B, c1A);
        buffer.vertex(matrix, x1, y2, z1).color(c4R, c4G, c4B, c4A);
        buffer.vertex(matrix, x2, y2, z1).color(c3R, c3G, c3B, c3A);

        // Left
        buffer.vertex(matrix, x1, y1, z1).color(c1R, c1G, c1B, c1A);
        buffer.vertex(matrix, x1, y1, z2).color(c1R, c1G, c1B, c1A);
        buffer.vertex(matrix, x1, y2, z2).color(c4R, c4G, c4B, c4A);
        buffer.vertex(matrix, x1, y2, z1).color(c4R, c4G, c4B, c4A);

        // Right
        buffer.vertex(matrix, x2, y1, z2).color(c2R, c2G, c2B, c2A);
        buffer.vertex(matrix, x2, y1, z1).color(c2R, c2G, c2B, c2A);
        buffer.vertex(matrix, x2, y2, z1).color(c3R, c3G, c3B, c3A);
        buffer.vertex(matrix, x2, y2, z2).color(c3R, c3G, c3B, c3A);

        // Top
        buffer.vertex(matrix, x1, y2, z2).color(c4R, c4G, c4B, c4A * 0.8f);
        buffer.vertex(matrix, x2, y2, z2).color(c3R, c3G, c3B, c3A * 0.8f);
        buffer.vertex(matrix, x2, y2, z1).color(c3R, c3G, c3B, c3A * 0.8f);
        buffer.vertex(matrix, x1, y2, z1).color(c4R, c4G, c4B, c4A * 0.8f);

        // Bottom
        buffer.vertex(matrix, x1, y1, z1).color(c1R, c1G, c1B, c1A * 0.6f);
        buffer.vertex(matrix, x2, y1, z1).color(c2R, c2G, c2B, c2A * 0.6f);
        buffer.vertex(matrix, x2, y1, z2).color(c2R, c2G, c2B, c2A * 0.6f);
        buffer.vertex(matrix, x1, y1, z2).color(c1R, c1G, c1B, c1A * 0.6f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawBoxOutline(MatrixStack matrices, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float width) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Bottom rectangle
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);

        // Top rectangle
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        // Pillars
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public static void cleanup() {
        animProgress.clear();
    }
}