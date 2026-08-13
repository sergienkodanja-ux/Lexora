package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.AttackManager;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TargetESPRenderer {

    private static final Identifier RHOMBUS_TEX = Identifier.of("lexoravisauls", "textures/gui/rhombus.png");
    private static final Identifier ROUND_RHOMBUS_TEX = Identifier.of("lexoravisauls", "textures/gui/round_rhombus.png");
    private static final Identifier BLOOM_TEX = Identifier.of("lexoravisauls", "textures/gui/bloom.png");

    private static final Identifier SKULL_0_TEX = Identifier.of("lexoravisauls", "textures/gui/skull_0.png");
    private static final Identifier SKULL_1_TEX = Identifier.of("lexoravisauls", "textures/gui/skull_1.png");
    private static final Identifier SKULL_2_TEX = Identifier.of("lexoravisauls", "textures/gui/skull_2.png");

    private static final Map<UUID, Float> animProgress = new HashMap<>();
    private static final Map<UUID, Float> hurtAnimProgress = new HashMap<>();

    private static final Map<UUID, List<List<Vec3d>>> spiritTrails = new HashMap<>();
    private static final Map<UUID, Vec3d> spiritAnchors = new HashMap<>();

    private static UUID lastTargetId = null;
    private static long lastTargetLostTime = 0L;

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!LexoraGui.moduleStates.getOrDefault("Target ESP", false)) return;

        String style = LexoraGui.modeSettings.getOrDefault("Target ESP Mode", "Spirits");
        float speedSet = LexoraGui.numSettings.getOrDefault("ESP Speed", 1.0f);
        boolean redOnDamage = LexoraGui.moduleStates.getOrDefault("Red On Damage", true);
        boolean onlyOnCrit = LexoraGui.moduleStates.getOrDefault("Only On Crit", false);

        LivingEntity currentTarget = null;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (e instanceof LivingEntity living && e != mc.player) {
                currentTarget = living;
                lastTargetId = living.getUuid();
                lastTargetLostTime = System.currentTimeMillis();
            }
        }

        Vec3d camPos = camera.getPos();
        float time = mc.player.age + tickDelta;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player) continue;
            if (living.isInvisible()) continue;

            UUID id = living.getUuid();
            boolean isTarget = living == currentTarget;
            boolean isRecent = currentTarget == null
                    && id.equals(lastTargetId)
                    && (System.currentTimeMillis() - lastTargetLostTime) <= 1000L;

            float current = animProgress.getOrDefault(id, 0.0f);
            float step = 0.04f;
            current = (isTarget || isRecent)
                    ? Math.min(1.0f, current + step)
                    : Math.max(0.0f, current - step);
            animProgress.put(id, current);

            if (current <= 0.0f) {
                animProgress.remove(id);
                hurtAnimProgress.remove(id);
                spiritTrails.remove(id);
                spiritAnchors.remove(id);
                continue;
            }

            float hurt = hurtAnimProgress.getOrDefault(id, 0.0f);
            boolean takingDamage = living.hurtTime > 0;

            if (redOnDamage && takingDamage) {
                if (!onlyOnCrit || AttackManager.isRecentCrit(living) || hurt > 0.2f) {
                    hurt = Math.min(1.0f, hurt + 0.35f);
                } else {
                    hurt = Math.max(0.0f, hurt - 0.05f);
                }
            } else {
                hurt = Math.max(0.0f, hurt - 0.05f);
            }
            hurtAnimProgress.put(id, hurt);

            float alphaMod = ease(current);
            int themeColor = LexoraGui.getThemeColor(0);
            int baseColor = blendColors(themeColor | 0xFF000000, 0xFFFF0000, hurt);

            double x = MathHelper.lerp(tickDelta, living.prevX, living.getX()) - camPos.x;
            double y = MathHelper.lerp(tickDelta, living.prevY, living.getY()) - camPos.y;
            double z = MathHelper.lerp(tickDelta, living.prevZ, living.getZ()) - camPos.z;

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SrcFactor.SRC_ALPHA,
                    GlStateManager.DstFactor.ONE,
                    GlStateManager.SrcFactor.ZERO,
                    GlStateManager.DstFactor.ONE
            );
            if (style.equals("Spirits")) {
                // Призраки теперь честные - не светят сквозь блоки, только по прямой видимости
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            if (style.contains("Rhombus")) {
                Identifier tex = style.equals("Rhombus") ? RHOMBUS_TEX : ROUND_RHOMBUS_TEX;
                renderRhombus(matrices, camera, x, y, z, living, tex, baseColor, alphaMod, time, speedSet, hurt);
            } else if (style.equals("Spirits")) {
                renderSpirits(matrices, camera, tickDelta, camPos, living, baseColor, alphaMod, speedSet);
            } else if (style.equals("Crystals")) {
                renderCrystals(matrices, camera, tickDelta, camPos, living, baseColor, alphaMod, speedSet, hurt);
            } else if (style.equals("Circle")) {
                renderJello(matrices, camera, tickDelta, camPos, living, baseColor, alphaMod, speedSet, hurt);
            } else if (style.equals("Skull")) {
                renderSkull(matrices, camera, tickDelta, camPos, living, alphaMod, speedSet, hurt);
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SrcFactor.SRC_ALPHA,
                    GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
            );
            RenderSystem.enableCull();
        }
    }

    private static void renderRhombus(MatrixStack matrices,
                                      Camera camera,
                                      double x,
                                      double y,
                                      double z,
                                      LivingEntity living,
                                      Identifier tex,
                                      int baseColor,
                                      float alphaMod,
                                      float time,
                                      float speedSet,
                                      float hurt) {
        matrices.push();
        matrices.translate(x, y + living.getHeight() / 2.0f, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        float t = time * 0.025f * speedSet;
        float swingRot = MathHelper.sin(t) * 1080.0f;
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(swingRot));

        float targetSize = LexoraGui.numSettings.getOrDefault("Rhombus Size", 1.0f) * 1.3f;
        float finalScale = Math.max(0.1f, (targetSize + ((1.0f - alphaMod) * 3.0f)) - (hurt * 0.35f));

        matrices.scale(finalScale, finalScale, finalScale);
        drawTexturedQuad(matrices, tex, baseColor, alphaMod, 1.0f);
        matrices.pop();
    }

    private static void renderSpirits(MatrixStack matrices,
                                      Camera camera,
                                      float tickDelta,
                                      Vec3d camPos,
                                      LivingEntity target,
                                      int baseColor,
                                      float alphaMod,
                                      float speedSet) {
        UUID id = target.getUuid();

        int count = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Spirits Count", 3.0f).intValue(),
                1,
                10
        );

        int trailLength = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Trail Length", 80.0f).intValue(),
                5,
                100
        );

        float spiritSize = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Spirits Size", 0.67f),
                0.15f,
                2.0f
        );

        List<List<Vec3d>> trails = getSpiritTrails(id, count);

        double px = MathHelper.lerp(tickDelta, target.prevX, target.getX());
        double py = MathHelper.lerp(tickDelta, target.prevY, target.getY()) + target.getHeight() * 0.52;
        double pz = MathHelper.lerp(tickDelta, target.prevZ, target.getZ());

        Vec3d anchor = new Vec3d(px, py, pz);
        Vec3d prevAnchor = spiritAnchors.put(id, anchor);

        if (prevAnchor != null) {
            Vec3d delta = anchor.subtract(prevAnchor);

            if (delta.length() > 1.8) {
                for (List<Vec3d> trail : trails) {
                    trail.clear();
                }
            } else if (delta.lengthSquared() > 0.000001) {
                for (List<Vec3d> trail : trails) {
                    for (int j = 0; j < trail.size(); j++) {
                        trail.set(j, trail.get(j).add(delta));
                    }
                }
            }
        }

        double radius = 0.62 + Math.min(0.18, count * 0.018);
        double t = (MinecraftClient.getInstance().player.age + tickDelta) * 0.085 * Math.max(0.1f, speedSet);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, BLOOM_TEX);

        for (int i = 0; i < count; i++) {
            double phase = (Math.PI * 2.0 / count) * i;
            double orbit = t + phase;
            double softOrbit = t * 0.72 + phase * 1.35;

            double ox = Math.cos(orbit) * radius + Math.sin(softOrbit) * 0.10;
            double oz = Math.sin(orbit) * radius + Math.cos(softOrbit) * 0.10;
            double oy = Math.sin(orbit * 1.25 + phase) * target.getHeight() * 0.34;

            Vec3d orbPos = new Vec3d(px + ox, py + oy, pz + oz);
            List<Vec3d> trail = trails.get(i);

            if (trail.isEmpty() || trail.get(trail.size() - 1).distanceTo(orbPos) > 0.0025) {
                trail.add(orbPos);
            } else {
                trail.set(trail.size() - 1, orbPos);
            }

            while (trail.size() > trailLength) {
                trail.remove(0);
            }

            for (int j = 0; j < trail.size(); j++) {
                Vec3d pos = trail.get(j);
                float progress = (float) j / Math.max(1, trail.size() - 1);
                float alpha = alphaMod * progress * 0.82f;
                if (alpha <= 0.01f) continue;

                int themeColor = LexoraGui.getThemeColor(progress + (float) i / Math.max(1, count));
                int color = blendColors(themeColor | 0xFF000000, baseColor | 0xFF000000, 0.35f);
                color = mixToWhite(color, 0.18f + progress * 0.18f);

                float size = Math.max(0.05f, spiritSize * progress);
                drawBillboard(matrices, camera, camPos, pos, BLOOM_TEX, color, alpha, size);
            }
        }
    }

    private static void renderCrystals(MatrixStack matrices,
                                       Camera camera,
                                       float tickDelta,
                                       Vec3d camPos,
                                       LivingEntity target,
                                       int baseColor,
                                       float alphaMod,
                                       float speedSet,
                                       float hurt) {
        double tx = MathHelper.lerp(tickDelta, target.prevX, target.getX());
        double ty = MathHelper.lerp(tickDelta, target.prevY, target.getY());
        double tz = MathHelper.lerp(tickDelta, target.prevZ, target.getZ());

        double renderX = tx - camPos.x;
        double renderY = ty - camPos.y;
        double renderZ = tz - camPos.z;

        float entityHeight = target.getHeight();
        float entityWidth = target.getWidth();
        float halfWidth = entityWidth * 0.5f;

        int crystalCount = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Crystal Count", 20.0f).intValue(),
                8,
                30
        );

        float crystalScaleSetting = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Crystal Size", 0.8f),
                0.1f,
                2.0f
        );

        float time = (MinecraftClient.getInstance().player.age + tickDelta) * 3.2f * Math.max(0.1f, speedSet);

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ZERO,
                GlStateManager.DstFactor.ONE
        );
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, BLOOM_TEX);

        for (int i = 0; i < crystalCount; i++) {
            float seed1 = (float) (Math.sin(i * 1.7f + 0.3f) * 0.5f + 0.5f);
            float seed2 = (float) (Math.cos(i * 2.3f + 0.7f) * 0.5f + 0.5f);
            float seed3 = (float) (Math.sin(i * 3.1f + 1.1f) * 0.5f + 0.5f);

            float angleOffset = i * (360.0f / crystalCount) + seed1 * 12.0f;
            float angle = time + angleOffset;
            float radius = halfWidth + 0.25f + seed3 * 0.15f;

            float x = radius * MathHelper.cos((float) Math.toRadians(angle));
            float z = radius * MathHelper.sin((float) Math.toRadians(angle));
            float y = seed2 * entityHeight * 1.05f;

            float crystalScale = 0.15f * alphaMod * crystalScaleSetting;

            int glowColor = mixToWhite(baseColor, 0.18f);
            float glowAlpha = alphaMod * 0.25f * (1.0f - hurt * 0.15f);

            drawCrystalGlow(matrices, camera, x, y, z, crystalScale * 3.2f, glowColor, glowAlpha);
        }

        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.TRIANGLES,
                VertexFormats.POSITION_COLOR
        );

        for (int i = 0; i < crystalCount; i++) {
            float seed1 = (float) (Math.sin(i * 1.7f + 0.3f) * 0.5f + 0.5f);
            float seed2 = (float) (Math.cos(i * 2.3f + 0.7f) * 0.5f + 0.5f);
            float seed3 = (float) (Math.sin(i * 3.1f + 1.1f) * 0.5f + 0.5f);

            float angleOffset = i * (360.0f / crystalCount) + seed1 * 12.0f;
            float angle = time + angleOffset;
            float radius = halfWidth + 0.25f + seed3 * 0.15f;

            float x = radius * MathHelper.cos((float) Math.toRadians(angle));
            float z = radius * MathHelper.sin((float) Math.toRadians(angle));
            float y = seed2 * entityHeight * 1.05f;

            float crystalScale = 0.15f * alphaMod * crystalScaleSetting;

            drawCrystalBody(
                    buffer,
                    matrices,
                    x,
                    y,
                    z,
                    crystalScale,
                    angle,
                    baseColor,
                    alphaMod * 0.85f
            );
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void renderJello(MatrixStack matrices,
                                    Camera camera,
                                    float tickDelta,
                                    Vec3d camPos,
                                    LivingEntity target,
                                    int baseColor,
                                    float alphaMod,
                                    float speedSet,
                                    float hurt) {
        double x = MathHelper.lerp(tickDelta, target.prevX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(tickDelta, target.prevY, target.getY()) - camPos.y;
        double z = MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) - camPos.z;

        float entityWidth = target.getWidth() * 1.65f;
        float entityHeight = target.getHeight() - 0.15f;

        // было jelloMoving += 4f за кадр (зависело от fps), теперь от игрового времени + ESP Speed
        float jelloMoving = (MinecraftClient.getInstance().player.age + tickDelta) * 4.0f * Math.max(0.1f, speedSet);
        float scale = Math.max(0.5f, 0.7f - 0.2f * alphaMod);

        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, BLOOM_TEX);

        matrices.push();
        matrices.translate(x, y, z);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0; i < 360; i += 2) {
            double rad = Math.toRadians(i + jelloMoving);
            float xOffset = (float) (Math.cos(rad) * entityWidth * scale);
            float zOffset = (float) (Math.sin(rad) * entityWidth * scale);

            float sizeBase = 0.2f;

            for (int j = 0; j < 15; ++j) {
                float yOffsetLayer = entityHeight / 1.7f + (entityHeight / 2.0f) * (float) Math.cos(Math.toRadians(jelloMoving / 1.5f + j * 2.0f));

                matrices.push();
                matrices.translate(xOffset, yOffsetLayer, zOffset);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                MatrixStack.Entry entry = matrices.peek();
                int finalAlpha = (int) (255 * alphaMod * ((float) j / 15.0f) * 0.05f);

                buffer.vertex(entry.getPositionMatrix(), -sizeBase / 2.0f, -sizeBase / 2.0f, 0).texture(0, 0).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), sizeBase / 2.0f, -sizeBase / 2.0f, 0).texture(1, 0).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), sizeBase / 2.0f, sizeBase / 2.0f, 0).texture(1, 1).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), -sizeBase / 2.0f, sizeBase / 2.0f, 0).texture(0, 1).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), -sizeBase / 2.0f, -sizeBase / 2.0f, 0).texture(0, 0).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), sizeBase / 2.0f, -sizeBase / 2.0f, 0).texture(1, 0).color(r, g, b, finalAlpha);
                matrices.pop();
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        matrices.push();
        matrices.translate(x, y, z);
        RenderSystem.setShaderTexture(0, BLOOM_TEX);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0; i < 360; i += 2) {
            double rad = Math.toRadians(i + jelloMoving);
            float xOffset = (float) (Math.cos(rad) * entityWidth * scale);
            float zOffset = (float) (Math.sin(rad) * entityWidth * scale);
            float yOffset = entityHeight / 1.75f + (entityHeight / 2.0f) * (float) Math.cos(Math.toRadians(jelloMoving / 1.5f + 30.0f));

            float sizeLarge = 0.2f;

            matrices.push();
            matrices.translate(xOffset, yOffset, zOffset);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            MatrixStack.Entry entry = matrices.peek();
            int finalAlpha = (int) (255 * alphaMod * 0.2f);

            buffer.vertex(entry.getPositionMatrix(), -sizeLarge / 2.0f, -sizeLarge / 2.0f, 0).texture(0, 0).color(r, g, b, finalAlpha);
            buffer.vertex(entry.getPositionMatrix(), sizeLarge / 2.0f, -sizeLarge / 2.0f, 0).texture(1, 0).color(r, g, b, finalAlpha);
            buffer.vertex(entry.getPositionMatrix(), sizeLarge / 2.0f, sizeLarge / 2.0f, 0).texture(1, 1).color(r, g, b, finalAlpha);
            buffer.vertex(entry.getPositionMatrix(), -sizeLarge / 2.0f, sizeLarge / 2.0f, 0).texture(0, 1).color(r, g, b, finalAlpha);

            matrices.pop();
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    private static void renderSkull(MatrixStack matrices,
                                    Camera camera,
                                    float tickDelta,
                                    Vec3d camPos,
                                    LivingEntity target,
                                    float alphaMod,
                                    float speedSet,
                                    float hurt) {
        double x = MathHelper.lerp(tickDelta, target.prevX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(tickDelta, target.prevY, target.getY()) - camPos.y;
        double z = MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) - camPos.z;

        float pulse = 1.0f + 0.12f * MathHelper.sin(
                (MinecraftClient.getInstance().player.age + tickDelta) * 0.15f * Math.max(0.1f, speedSet)
        );

        float skullSize = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Skull Size", 1.0f),
                0.5f,
                3.0f
        );

        float finalSize = skullSize * pulse;
        Identifier skullTex = getSkullTexture(target);

        matrices.push();
        matrices.translate(x, y + target.getHeight() / 2.0f, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.scale(finalSize, finalSize, finalSize);

        int color = 0xFFFFFFFF;
        if (hurt > 0.0f) {
            color = blendColors(0xFFFFFFFF, 0xFFFF4040, hurt);
        }

        color = withAlpha(color, MathHelper.clamp((int) (alphaMod * 255.0f), 0, 255));
        drawTexturedQuad(matrices, skullTex, color, alphaMod, 1.0f);

        matrices.pop();
    }

    private static Identifier getSkullTexture(LivingEntity target) {
        float maxHp = Math.max(1.0f, target.getMaxHealth());
        float hpPercent = target.getHealth() / maxHp;

        if (hpPercent > 0.5f) {
            return SKULL_0_TEX;
        } else if (hpPercent > 0.25f) {
            return SKULL_1_TEX;
        } else {
            return SKULL_2_TEX;
        }
    }

    private static List<List<Vec3d>> getSpiritTrails(UUID id, int count) {
        List<List<Vec3d>> trails = spiritTrails.computeIfAbsent(id, k -> new ArrayList<>());

        while (trails.size() < count) {
            trails.add(new ArrayList<>());
        }
        while (trails.size() > count) {
            trails.remove(trails.size() - 1);
        }

        return trails;
    }

    private static void drawQuad(MatrixStack matrices, Identifier texture, int color, float size) {
        drawTexturedQuad(matrices, texture, color, ((color >> 24) & 0xFF) / 255.0f, size);
    }

    private static void drawTexturedQuad(MatrixStack matrices, Identifier texture, int color, float alpha, float size) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );

        float half = size * 0.5f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = MathHelper.clamp(alpha, 0.0f, 1.0f);

        buffer.vertex(matrix, -half, -half, 0.0f).texture(0.0f, 1.0f).color(r, g, b, a);
        buffer.vertex(matrix,  half, -half, 0.0f).texture(1.0f, 1.0f).color(r, g, b, a);
        buffer.vertex(matrix,  half,  half, 0.0f).texture(1.0f, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, -half,  half, 0.0f).texture(0.0f, 0.0f).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawBillboard(MatrixStack matrices,
                                      Camera camera,
                                      Vec3d camPos,
                                      Vec3d worldPos,
                                      Identifier texture,
                                      int color,
                                      float alpha,
                                      float size) {
        matrices.push();
        matrices.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        drawTexturedQuad(
                matrices,
                texture,
                withAlpha(color, MathHelper.clamp((int) (alpha * 255.0f), 0, 255)),
                alpha,
                size
        );

        matrices.pop();
    }

    private static void drawCrystalGlow(MatrixStack matrices,
                                        Camera camera,
                                        float x, float y, float z,
                                        float size,
                                        int color,
                                        float alpha) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        drawTexturedQuad(
                matrices,
                BLOOM_TEX,
                withAlpha(color, MathHelper.clamp((int) (alpha * 255.0f), 0, 255)),
                alpha,
                size
        );

        matrices.pop();
    }

    private static void drawCrystalBody(BufferBuilder buffer,
                                        MatrixStack matrices,
                                        float x, float y, float z,
                                        float scale,
                                        float yaw,
                                        int color,
                                        float alpha) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw + 90.0f));
        matrices.scale(scale, scale, scale);

        Matrix4f mat = matrices.peek().getPositionMatrix();

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = Math.max(0, Math.min(255, (int) (180.0f * alpha)));

        int rL = Math.min(255, (int) (r * 1.3f));
        int gL = Math.min(255, (int) (g * 1.3f));
        int bL = Math.min(255, (int) (b * 1.3f));

        int rD = Math.max(0, (int) (r * 0.6f));
        int gD = Math.max(0, (int) (g * 0.6f));
        int bD = Math.max(0, (int) (b * 0.6f));

        float w = 0.5f;
        float h = 1.0f;

        drawTriangle(buffer, mat, 0, 0, h, -w, 0, 0, 0,  w, 0, rL, gL, bL, a);
        drawTriangle(buffer, mat, 0, 0, h,  0,  w, 0, w, 0,  0, rL, gL, bL, a);
        drawTriangle(buffer, mat, 0, 0, h,  w, 0, 0, 0, -w, 0, r,  g,  b,  a);
        drawTriangle(buffer, mat, 0, 0, h,  0, -w, 0, -w, 0, 0, r,  g,  b,  a);

        drawTriangle(buffer, mat, 0, 0, -h, 0,  w, 0, -w, 0, 0, rD, gD, bD, a);
        drawTriangle(buffer, mat, 0, 0, -h, w, 0,  0, 0,  w, 0, rD, gD, bD, a);
        drawTriangle(buffer, mat, 0, 0, -h, 0, -w, 0, w,  0, 0, rD, gD, bD, a);
        drawTriangle(buffer, mat, 0, 0, -h, -w, 0, 0, 0, -w, 0, rD, gD, bD, a);

        matrices.pop();
    }

    private static void drawTriangle(BufferBuilder buffer,
                                     Matrix4f mat,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float x3, float y3, float z3,
                                     int r, int g, int b, int a) {
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;
        float af = a / 255.0f;

        buffer.vertex(mat, x1, y1, z1).color(rf, gf, bf, af);
        buffer.vertex(mat, x2, y2, z2).color(rf, gf, bf, af);
        buffer.vertex(mat, x3, y3, z3).color(rf, gf, bf, af);
    }

    private static int withAlpha(int color, int alpha) {
        alpha = MathHelper.clamp(alpha, 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float ease(float x) {
        return x < 0.5f
                ? 2.0f * x * x
                : -1.0f + (4.0f - 2.0f * x) * x;
    }

    private static int mixToWhite(int color, float factor) {
        factor = MathHelper.clamp(factor, 0.0f, 1.0f);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = (int) (r + (255 - r) * factor);
        g = (int) (g + (255 - g) * factor);
        b = (int) (b + (255 - b) * factor);

        return (r << 16) | (g << 8) | b;
    }

    private static int blendColors(int from, int to, float progress) {
        progress = MathHelper.clamp(progress, 0.0f, 1.0f);

        int a1 = (from >> 24) & 0xFF;
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >> 24) & 0xFF;
        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * progress);
        int r = (int) (r1 + (r2 - r1) * progress);
        int g = (int) (g1 + (g2 - g1) * progress);
        int b = (int) (b1 + (b2 - b1) * progress);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}