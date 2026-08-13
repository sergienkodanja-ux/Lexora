package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class MotionClones {

    private static final List<CloneSnapshot> CLONES = new ArrayList<>();

    private static long nextNormalSpawnAtMs = 0L;
    private static Object lastWorld = null;
    private static UUID lastPlayerId = null;

    private static class CloneSnapshot {
        UUID ownerId;
        boolean ownerIsSelf;
        boolean totemClone;

        Vec3d pos;

        float bodyYaw;
        float headPitch;

        float limbAngle;
        float limbDistance;

        boolean sneaking;
        boolean sprinting;

        int age;
        int maxAge;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.player instanceof AbstractClientPlayerEntity player) || mc.world == null) return;

        // Фикс после перезахода / респавна / смены мира
        if (lastWorld != mc.world || lastPlayerId == null || !lastPlayerId.equals(player.getUuid())) {
            resetAll(mc);
            lastWorld = mc.world;
            lastPlayerId = player.getUuid();
        }

        if (!LexoraGui.moduleStates.getOrDefault("Motion Clones", false)) {
            resetAll(mc);
            return;
        }

        int maxClones = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Clone Count", 6.0f).intValue(),
                1,
                20
        );

        float delaySeconds = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Clone Delay", 1.0f),
                0.1f,
                8.0f
        );

        int moveLife = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Clone Life", 32.0f).intValue(),
                6,
                120
        );

        boolean normalEnabled = LexoraGui.moduleStates.getOrDefault("Clone Normal", true);

        boolean moving = player.getVelocity().horizontalLengthSquared() > 0.0025;
        boolean airborne = !player.isOnGround();
        boolean swinging = player.handSwinging;
        boolean sprinting = player.isSprinting();
        boolean active = moving || airborne || swinging || sprinting;

        long nowMs = System.currentTimeMillis();

        if (normalEnabled && active && nowMs >= nextNormalSpawnAtMs) {
            spawnCloneFromPlayer(player, moveLife, true, false);
            nextNormalSpawnAtMs = nowMs + Math.max(100L, (long) (delaySeconds * 1000.0f));
        }

        Iterator<CloneSnapshot> it = CLONES.iterator();
        while (it.hasNext()) {
            CloneSnapshot clone = it.next();
            clone.age++;
            if (clone.age >= clone.maxAge) {
                it.remove();
            }
        }

        while (CLONES.size() > maxClones) {
            CLONES.remove(0);
        }
    }

    public static void onTotemPop(LivingEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!LexoraGui.moduleStates.getOrDefault("Motion Clones", false)) return;
        if (!LexoraGui.moduleStates.getOrDefault("Clone Totem", true)) return;
        if (!(entity instanceof AbstractClientPlayerEntity playerEntity)) return;

        boolean isSelf = playerEntity == mc.player;

        if (isSelf && !LexoraGui.moduleStates.getOrDefault("Clone Totem Self", true)) {
            return;
        }

        if (!isSelf && !LexoraGui.moduleStates.getOrDefault("Clone Totem Players", false)) {
            return;
        }

        int totemLife = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Clone Totem Life", 42.0f).intValue(),
                10,
                120
        );

        spawnCloneFromPlayer(playerEntity, totemLife, isSelf, true);
    }

    private static void spawnCloneFromPlayer(AbstractClientPlayerEntity player, int life, boolean isSelf, boolean totemClone) {
        CloneSnapshot clone = new CloneSnapshot();
        clone.ownerId = player.getUuid();
        clone.ownerIsSelf = isSelf;
        clone.totemClone = totemClone;

        clone.pos = player.getPos();
        clone.bodyYaw = player.bodyYaw;
        clone.headPitch = player.getPitch();

        clone.limbAngle = player.limbAnimator.getPos();
        clone.limbDistance = player.limbAnimator.getSpeed();

        clone.sneaking = player.isSneaking();
        clone.sprinting = player.isSprinting();

        clone.age = 0;
        clone.maxAge = life;

        CLONES.add(clone);
    }

    private static void resetAll(MinecraftClient mc) {
        CLONES.clear();
        nextNormalSpawnAtMs = System.currentTimeMillis() + 250L;
        if (mc.player != null) {
            lastPlayerId = mc.player.getUuid();
        }
        lastWorld = mc.world;
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!LexoraGui.moduleStates.getOrDefault("Motion Clones", false)) return;
        if (CLONES.isEmpty()) return;

        boolean renderFirstPerson = LexoraGui.moduleStates.getOrDefault("Clone First Person", false);
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();

        Vec3d camPos = camera.getPos();

        float scaleSetting = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Clone Scale", 1.0f),
                0.7f,
                1.3f
        );

        float alphaSetting = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Clone Alpha", 0.85f),
                0.1f,
                1.0f
        );

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        for (CloneSnapshot clone : CLONES) {
            if (clone.ownerIsSelf && firstPerson && !renderFirstPerson) {
                continue;
            }

            float progress = MathHelper.clamp((clone.age + tickDelta) / (float) clone.maxAge, 0.0f, 1.0f);

            float alpha = clone.totemClone
                    ? getTotemAlpha(progress) * alphaSetting
                    : getSmoothAlpha(progress) * alphaSetting;

            if (alpha <= 0.01f) continue;

            int color = resolveColor(progress, clone.totemClone);
            int glowColor = brighten(color, 1.20f);

            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            float gr = ((glowColor >> 16) & 0xFF) / 255.0f;
            float gg = ((glowColor >> 8) & 0xFF) / 255.0f;
            float gb = (glowColor & 0xFF) / 255.0f;

            double x = clone.pos.x - camPos.x;
            double y = clone.pos.y - camPos.y;
            double z = clone.pos.z - camPos.z;

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-clone.bodyYaw));

            if (clone.totemClone) {
                float flyEase = easeOutCubic(progress);
                float flyUp = flyEase * 1.75f;
                float swayX = MathHelper.sin((clone.age + tickDelta) * 0.24f) * 0.03f;
                float swayZ = MathHelper.cos((clone.age + tickDelta) * 0.20f) * 0.03f;

                matrices.translate(swayX, flyUp, swayZ);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                        MathHelper.sin((clone.age + tickDelta) * 0.16f) * 8.5f
                ));
            } else {
                float lift = (1.0f - alpha) * 0.04f;
                matrices.translate(0.0f, lift, 0.0f);
            }

            matrices.scale(scaleSetting, scaleSetting, scaleSetting);

            if (clone.sneaking && !clone.totemClone) {
                matrices.translate(0.0f, -0.08f, 0.0f);
            }

            matrices.push();
            matrices.scale(1.03f, 1.03f, 1.03f);
            renderCloneBody(matrices, gr, gg, gb, alpha * (clone.totemClone ? 0.30f : 0.22f), clone);
            matrices.pop();

            renderCloneBody(matrices, r, g, b, alpha, clone);

            matrices.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void renderCloneBody(MatrixStack matrices, float r, float g, float b, float a, CloneSnapshot clone) {
        float limbAmount = clone.totemClone
                ? 0.0f
                : MathHelper.clamp(clone.limbDistance * (clone.sprinting ? 1.15f : 1.0f), 0.0f, 1.2f);

        float rightArmRot = MathHelper.cos(clone.limbAngle * 0.6662f + (float) Math.PI) * 1.4f * limbAmount;
        float leftArmRot  = MathHelper.cos(clone.limbAngle * 0.6662f) * 1.4f * limbAmount;
        float rightLegRot = MathHelper.cos(clone.limbAngle * 0.6662f) * 1.4f * limbAmount;
        float leftLegRot  = MathHelper.cos(clone.limbAngle * 0.6662f + (float) Math.PI) * 1.4f * limbAmount;

        float rightArmDeg = rightArmRot * 57.29578f;
        float leftArmDeg = leftArmRot * 57.29578f;
        float rightLegDeg = rightLegRot * 57.29578f;
        float leftLegDeg = leftLegRot * 57.29578f;

        if (clone.totemClone) {
            rightArmDeg = -22.0f;
            leftArmDeg = -22.0f;
            rightLegDeg = 10.0f;
            leftLegDeg = -10.0f;
        } else if (clone.sneaking) {
            rightArmDeg -= 8.0f;
            leftArmDeg -= 8.0f;
            rightLegDeg += 8.0f;
            leftLegDeg += 8.0f;
        }

        renderLeg(matrices, -0.13f, 0.78f, rightLegDeg, r, g, b, a);
        renderLeg(matrices,  0.13f, 0.78f, leftLegDeg, r, g, b, a);

        if (clone.sneaking && !clone.totemClone) {
            matrices.push();
            matrices.translate(0.0f, 0.78f, 0.05f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28.0f));

            drawBox(matrices, -0.25f, 0.00f, -0.13f, 0.25f, 0.74f, 0.13f, r, g, b, a);

            renderArmLocal(matrices, -0.34f, 0.68f, rightArmDeg, r, g, b, a);
            renderArmLocal(matrices,  0.34f, 0.68f, leftArmDeg, r, g, b, a);

            matrices.push();
            matrices.translate(0.0f, 0.84f, -0.03f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(clone.headPitch * 0.35f));
            drawBox(matrices, -0.24f, -0.24f, -0.24f, 0.24f, 0.24f, 0.24f, r, g, b, a);
            matrices.pop();

            matrices.pop();
        } else {
            drawBox(matrices, -0.25f, 0.78f, -0.13f, 0.25f, 1.50f, 0.13f, r, g, b, a);

            renderArm(matrices, -0.34f, 1.43f, rightArmDeg, r, g, b, a);
            renderArm(matrices,  0.34f, 1.43f, leftArmDeg, r, g, b, a);

            matrices.push();
            matrices.translate(0.0f, 1.79f, 0.0f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(clone.totemClone ? -14.0f : clone.headPitch * 0.35f));
            drawBox(matrices, -0.24f, -0.24f, -0.24f, 0.24f, 0.24f, 0.24f, r, g, b, a);
            matrices.pop();
        }
    }

    private static void renderArm(MatrixStack matrices, float pivotX, float pivotY, float rotDeg, float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(pivotX, pivotY, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotDeg));
        drawBox(matrices, -0.08f, -0.62f, -0.08f, 0.08f, 0.05f, 0.08f, r, g, b, a);
        matrices.pop();
    }

    private static void renderArmLocal(MatrixStack matrices, float pivotX, float pivotY, float rotDeg, float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(pivotX, pivotY, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotDeg));
        drawBox(matrices, -0.08f, -0.58f, -0.08f, 0.08f, 0.04f, 0.08f, r, g, b, a);
        matrices.pop();
    }

    private static void renderLeg(MatrixStack matrices, float pivotX, float pivotY, float rotDeg, float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(pivotX, pivotY, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotDeg));
        drawBox(matrices, -0.10f, -0.78f, -0.10f, 0.10f, 0.00f, 0.10f, r, g, b, a);
        matrices.pop();
    }

    private static void drawBox(MatrixStack matrices,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b, float a) {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR
        );

        vertex(buffer, mat, x1, y1, z2, r, g, b, a);
        vertex(buffer, mat, x2, y1, z2, r, g, b, a);
        vertex(buffer, mat, x2, y2, z2, r, g, b, a);
        vertex(buffer, mat, x1, y2, z2, r, g, b, a);

        vertex(buffer, mat, x2, y1, z1, r, g, b, a);
        vertex(buffer, mat, x1, y1, z1, r, g, b, a);
        vertex(buffer, mat, x1, y2, z1, r, g, b, a);
        vertex(buffer, mat, x2, y2, z1, r, g, b, a);

        vertex(buffer, mat, x1, y1, z1, r, g, b, a);
        vertex(buffer, mat, x1, y1, z2, r, g, b, a);
        vertex(buffer, mat, x1, y2, z2, r, g, b, a);
        vertex(buffer, mat, x1, y2, z1, r, g, b, a);

        vertex(buffer, mat, x2, y1, z2, r, g, b, a);
        vertex(buffer, mat, x2, y1, z1, r, g, b, a);
        vertex(buffer, mat, x2, y2, z1, r, g, b, a);
        vertex(buffer, mat, x2, y2, z2, r, g, b, a);

        vertex(buffer, mat, x1, y2, z2, r, g, b, a);
        vertex(buffer, mat, x2, y2, z2, r, g, b, a);
        vertex(buffer, mat, x2, y2, z1, r, g, b, a);
        vertex(buffer, mat, x1, y2, z1, r, g, b, a);

        vertex(buffer, mat, x1, y1, z1, r, g, b, a);
        vertex(buffer, mat, x2, y1, z1, r, g, b, a);
        vertex(buffer, mat, x2, y1, z2, r, g, b, a);
        vertex(buffer, mat, x1, y1, z2, r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void vertex(BufferBuilder buffer, Matrix4f mat, float x, float y, float z, float r, float g, float b, float a) {
        buffer.vertex(mat, x, y, z).color(r, g, b, a);
    }

    private static float getSmoothAlpha(float progress) {
        float fadeIn = MathHelper.clamp(progress / 0.18f, 0.0f, 1.0f);
        fadeIn = fadeIn * fadeIn * (3.0f - 2.0f * fadeIn);

        float fadeOut = 1.0f - MathHelper.clamp((progress - 0.55f) / 0.45f, 0.0f, 1.0f);
        fadeOut = fadeOut * fadeOut * (3.0f - 2.0f * fadeOut);

        return MathHelper.clamp(fadeIn * fadeOut, 0.0f, 1.0f);
    }

    private static float getTotemAlpha(float progress) {
        float fadeIn = MathHelper.clamp(progress / 0.08f, 0.0f, 1.0f);
        fadeIn = fadeIn * fadeIn * (3.0f - 2.0f * fadeIn);

        float fadeOut = 1.0f - MathHelper.clamp((progress - 0.22f) / 0.78f, 0.0f, 1.0f);
        fadeOut = fadeOut * fadeOut * (3.0f - 2.0f * fadeOut);

        return MathHelper.clamp(fadeIn * fadeOut, 0.0f, 1.0f);
    }

    private static float easeOutCubic(float x) {
        float inv = 1.0f - x;
        return 1.0f - inv * inv * inv;
    }

    private static int resolveColor(float progress, boolean totemClone) {
        String mode = LexoraGui.modeSettings.getOrDefault("Clone Color Mode", "Client");

        if (mode.equals("Custom")) {
            float[] hsv = LexoraGui.colorSettings.getOrDefault(
                    "Clone Custom Color",
                    new float[]{0.33f, 0.85f, 1.0f}
            );
            return Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        }

        if (totemClone) {
            return LexoraGui.getThemeColor(0.65f + progress * 0.15f);
        }

        return LexoraGui.getThemeColor(progress * 0.35f);
    }

    private static int brighten(int color, float factor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = Math.min(255, (int) (r * factor));
        g = Math.min(255, (int) (g * factor));
        b = Math.min(255, (int) (b * factor));

        return (r << 16) | (g << 8) | b;
    }
}