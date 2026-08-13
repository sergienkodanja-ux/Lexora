package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class AuraParticles {

    private static final Identifier BLOOM_TEX = Identifier.of("lexoravisauls", "textures/gui/bloom.png");
    private static final Random RANDOM = new Random();

    private static final List<AuraParticle> PARTICLES = new ArrayList<>();
    private static final Map<UUID, AuraEmitter> EMITTERS = new HashMap<>();

    private static class AuraEmitter {
        LivingEntity target;
        Vec3d fallbackCenter;
        int lastHitTick;
        int nextSpawnTick;

        AuraEmitter(LivingEntity target, int currentTick) {
            this.target = target;
            this.fallbackCenter = target.getPos();
            this.lastHitTick = currentTick;
            this.nextSpawnTick = currentTick;
        }
    }

    private static class AuraParticle {
        UUID ownerId;
        LivingEntity target;
        Vec3d fallbackCenter;

        float startAngle;
        float orbitSpeed;
        float radius;

        float startYOffset;
        float targetYOffset;

        float size;
        float colorOffset;

        int age;
        int maxAge;
    }

    public static void onHit(LivingEntity target) {
        if (target == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // ФИКС: на инвизке не создаем вообще
        if (target.isInvisible()) return;

        AuraEmitter emitter = EMITTERS.get(target.getUuid());
        if (emitter == null) {
            emitter = new AuraEmitter(target, mc.player.age);
            EMITTERS.put(target.getUuid(), emitter);
        } else {
            emitter.target = target;
            emitter.fallbackCenter = target.getPos();
            emitter.lastHitTick = mc.player.age;
        }
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (!LexoraGui.moduleStates.getOrDefault("AuraParticles", false)) {
            PARTICLES.clear();
            EMITTERS.clear();
            return;
        }

        int now = mc.player.age;

        Iterator<Map.Entry<UUID, AuraEmitter>> emitterIterator = EMITTERS.entrySet().iterator();
        while (emitterIterator.hasNext()) {
            AuraEmitter emitter = emitterIterator.next().getValue();

            if (emitter.target == null
                    || emitter.target.isRemoved()
                    || emitter.target.isDead()
                    || emitter.target.isInvisible()) {
                emitterIterator.remove();
                continue;
            }

            emitter.fallbackCenter = emitter.target.getPos();

            if (now - emitter.lastHitTick > 60) {
                emitterIterator.remove();
            }
        }

        int delay = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Aura Particles Delay", 2.0f).intValue(),
                1,
                8
        );

        int maxPerTarget = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Aura Particles Count", 24.0f).intValue(),
                1,
                80
        );

        float sizeBase = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Aura Particles Size", 0.18f),
                0.05f,
                0.60f
        );

        float radiusMax = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Aura Particles Radius", 1.45f),
                1.0f,
                2.0f
        );

        for (AuraEmitter emitter : EMITTERS.values()) {
            if (emitter.target == null || emitter.target.isInvisible()) continue;
            if (now - emitter.lastHitTick > 60) continue;

            int ownedCount = 0;
            for (AuraParticle particle : PARTICLES) {
                if (particle.ownerId != null
                        && emitter.target != null
                        && particle.ownerId.equals(emitter.target.getUuid())) {
                    ownedCount++;
                }
            }

            if (ownedCount >= maxPerTarget) continue;

            if (now >= emitter.nextSpawnTick) {
                spawnSingleParticle(emitter, sizeBase, radiusMax);
                emitter.nextSpawnTick = now + delay;
            }
        }

        for (int i = PARTICLES.size() - 1; i >= 0; i--) {
            AuraParticle particle = PARTICLES.get(i);

            if (particle.target == null
                    || particle.target.isRemoved()
                    || particle.target.isDead()
                    || particle.target.isInvisible()) {
                PARTICLES.remove(i);
                continue;
            }

            particle.age++;
            particle.fallbackCenter = particle.target.getPos();

            if (particle.age >= particle.maxAge) {
                PARTICLES.remove(i);
            }
        }
    }

    private static void spawnSingleParticle(AuraEmitter emitter, float sizeBase, float radiusMax) {
        if (emitter.target == null || emitter.target.isInvisible()) return;

        AuraParticle particle = new AuraParticle();
        particle.ownerId = emitter.target.getUuid();
        particle.target = emitter.target;
        particle.fallbackCenter = emitter.fallbackCenter;

        particle.startAngle = RANDOM.nextFloat() * 360.0f;
        particle.orbitSpeed = 0.8f + RANDOM.nextFloat() * 1.2f;

        particle.radius = 1.00f + RANDOM.nextFloat() * Math.max(0.01f, (radiusMax - 1.00f));

        float targetHeight = emitter.target.getHeight();

        particle.startYOffset = -0.26f - RANDOM.nextFloat() * 0.10f;
        particle.targetYOffset = 0.06f + RANDOM.nextFloat() * Math.max(0.2f, targetHeight - 0.12f);

        particle.size = sizeBase * (1.10f + RANDOM.nextFloat() * 0.35f);
        particle.colorOffset = RANDOM.nextFloat();

        particle.age = 0;
        particle.maxAge = 42 + RANDOM.nextInt(18);

        PARTICLES.add(particle);
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!LexoraGui.moduleStates.getOrDefault("AuraParticles", false)) return;
        if (PARTICLES.isEmpty()) return;

        Vec3d camPos = camera.getPos();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ZERO,
                GlStateManager.DstFactor.ONE
        );

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        for (AuraParticle particle : PARTICLES) {
            if (particle.target == null || particle.target.isInvisible()) {
                continue;
            }

            Vec3d center = particle.fallbackCenter;
            float entityHeight = 1.8f;

            if (particle.target != null && !particle.target.isRemoved()) {
                double tx = MathHelper.lerp(tickDelta, particle.target.prevX, particle.target.getX());
                double ty = MathHelper.lerp(tickDelta, particle.target.prevY, particle.target.getY());
                double tz = MathHelper.lerp(tickDelta, particle.target.prevZ, particle.target.getZ());
                center = new Vec3d(tx, ty, tz);
                entityHeight = particle.target.getHeight();
            }

            float progress = MathHelper.clamp((particle.age + tickDelta) / (float) particle.maxAge, 0.0f, 1.0f);

            float rise = easeOutCubic(progress);
            float yOffset = MathHelper.lerp(rise, particle.startYOffset, particle.targetYOffset);

            float angleDeg = particle.startAngle + (particle.age + tickDelta) * particle.orbitSpeed * 4.0f;
            float angleRad = (float) Math.toRadians(angleDeg);

            float animatedRadius = particle.radius + MathHelper.sin((particle.age + tickDelta) * 0.14f + particle.startAngle) * 0.035f;
            float wave = MathHelper.sin(progress * 3.1415927f) * 0.04f;

            float px = (float) (center.x + MathHelper.cos(angleRad) * animatedRadius);
            float pz = (float) (center.z + MathHelper.sin(angleRad) * animatedRadius);
            float py = (float) (
                    center.y
                            + MathHelper.clamp(yOffset, -0.35f, entityHeight + 0.05f)
                            + wave
            );

            float alpha = (1.0f - progress);
            alpha = (float) Math.pow(alpha, 1.15f) * 1.15f;
            alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);

            float glowAlpha = MathHelper.clamp(alpha * 0.95f, 0.0f, 1.0f);

            int color = resolveParticleColor(particle, progress);
            color = brighten(color, 1.35f);

            Vec3d renderPos = new Vec3d(px, py, pz);

            drawBillboard(matrices, camera, camPos, renderPos, BLOOM_TEX, color, glowAlpha * 0.75f, particle.size * 3.2f);
            drawBillboard(matrices, camera, camPos, renderPos, BLOOM_TEX, color, alpha, particle.size * 1.35f);
            drawBillboard(matrices, camera, camPos, renderPos, BLOOM_TEX, color, alpha * 0.9f, particle.size * 0.75f);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
    }

    private static int resolveParticleColor(AuraParticle particle, float progress) {
        String colorMode = LexoraGui.modeSettings.getOrDefault("Aura Particles Color Mode", "Client");

        if (colorMode.equals("Custom")) {
            float[] hsv = LexoraGui.colorSettings.getOrDefault(
                    "Aura Particles Custom Color",
                    new float[]{0f, 1f, 1f}
            );
            return Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        }

        return LexoraGui.getThemeColor(particle.colorOffset + progress * 0.18f);
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

    private static float easeOutCubic(float x) {
        float inv = 1.0f - x;
        return 1.0f - inv * inv * inv;
    }

    private static void drawBillboard(MatrixStack matrices,
                                      Camera camera,
                                      Vec3d camPos,
                                      Vec3d worldPos,
                                      Identifier texture,
                                      int color,
                                      float alpha,
                                      float size) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);

        matrices.push();
        matrices.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

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
        matrices.pop();
    }
}