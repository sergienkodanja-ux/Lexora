package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleSystem {
    private static final List<CustomParticle> particles = new ArrayList<>();
    private static final Random random = new Random();

    private static final Identifier TEX_STAR = Identifier.of("lexoravisauls", "textures/particle/star1.png");
    private static final Identifier TEX_SKULL = Identifier.of("lexoravisauls", "textures/particle/ded1.png");
    private static final Identifier TEX_BUCKS = Identifier.of("lexoravisauls", "textures/particle/bucks1.png");
    private static final Identifier TEX_SNOW = Identifier.of("lexoravisauls", "textures/particle/snownew1.png");
    private static final Identifier TEX_BLAST = Identifier.of("lexoravisauls", "textures/particle/snowblast1.png");
    private static final Identifier TEX_BRICH = Identifier.of("lexoravisauls", "textures/particle/snowbrich1.png");
    private static final Identifier TEX_CORE = Identifier.of("lexoravisauls", "textures/particle/core1.png");
    private static final Identifier TEX_SHOW = Identifier.of("lexoravisauls", "textures/particle/show1.png");

    private static int totemTimer = 0;
    private static Vec3d totemPos = null;
    private static int totemTotalCount = 0;

    public static class CustomParticle {
        public Vec3d pos;
        public Vec3d prevPos;
        public Vec3d vel;
        public int age;
        public int maxAge;
        public float size;
        public float rot;
        public float prevRot;
        public float rotSpeed;
        public Identifier texture;
        public float colorOffset;
        public Integer fixedColor;
        public boolean hasGravity;
        public boolean canBounce;

        public CustomParticle(Vec3d pos, Vec3d vel, int maxAge, float size, Identifier texture, Integer fixedColor, boolean hasGravity, boolean canBounce) {
            this.pos = pos;
            this.prevPos = pos;
            this.vel = vel;
            this.age = 0;
            this.maxAge = maxAge * 3;
            this.size = size;
            this.texture = texture;
            this.rot = random.nextFloat() * 360f;
            this.prevRot = this.rot;
            this.rotSpeed = (random.nextFloat() - 0.5f) * 15f;
            this.colorOffset = random.nextFloat();
            this.fixedColor = fixedColor;
            this.hasGravity = hasGravity;
            this.canBounce = canBounce;
        }
    }

    public static void tick() {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) {
            particles.clear();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        float windX = LexoraGui.numSettings.getOrDefault("Part. Wind X", 0.0f) / 100f;
        float windZ = LexoraGui.numSettings.getOrDefault("Part. Wind Z", 0.0f) / 100f;
        Identifier tex = getCurrentTexture();

        for (int i = particles.size() - 1; i >= 0; i--) {
            CustomParticle p = particles.get(i);
            p.prevPos = p.pos;
            p.prevRot = p.rot;

            p.age++;
            if (p.age >= p.maxAge) {
                particles.remove(i);
                continue;
            }

            p.vel = p.vel.multiply(0.94);

            if (p.canBounce) {
                p.vel = p.vel.add(0, -0.025, 0);

                net.minecraft.util.math.BlockPos blockPos = net.minecraft.util.math.BlockPos.ofFloored(p.pos.x, p.pos.y, p.pos.z);
                net.minecraft.block.BlockState state = mc.world.getBlockState(blockPos);
                net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(mc.world, blockPos);

                boolean hitGround = false;
                double surfaceY = 0;

                if (!shape.isEmpty()) {
                    surfaceY = blockPos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                    hitGround = true;
                } else {
                    net.minecraft.util.math.BlockPos below = blockPos.down();
                    net.minecraft.block.BlockState stateBelow = mc.world.getBlockState(below);
                    net.minecraft.util.shape.VoxelShape shapeBelow = stateBelow.getCollisionShape(mc.world, below);
                    if (!shapeBelow.isEmpty()) {
                        surfaceY = below.getY() + shapeBelow.getMax(net.minecraft.util.math.Direction.Axis.Y);
                        hitGround = true;
                    }
                }

                if (hitGround && p.pos.y <= surfaceY + 0.05 && p.vel.y < 0) {
                    p.vel = new Vec3d(p.vel.x * 0.6, p.vel.y * -0.4, p.vel.z * 0.6);
                    p.pos = new Vec3d(p.pos.x, surfaceY + 0.05, p.pos.z);

                    if (Math.abs(p.vel.y) < 0.03) {
                        p.vel = new Vec3d(p.vel.x * 0.8, 0, p.vel.z * 0.8);
                        p.rotSpeed *= 0.5;
                    }
                }
            } else if (p.hasGravity) {
                p.vel = p.vel.add(0, -0.015, 0);
            } else {
                p.vel = p.vel.add(windX * 0.05, -0.001, windZ * 0.05);
                double swirl = Math.sin(p.age * 0.1) * 0.015;
                p.vel = p.vel.add(swirl, 0, Math.cos(p.age * 0.1) * 0.015);
            }

            p.pos = p.pos.add(p.vel);
            p.rot += p.rotSpeed * (p.vel.length() * 5.0 + 0.2);
        }

        if (totemTimer > 0 && totemPos != null) {
            float size = LexoraGui.numSettings.getOrDefault("Totem Size", 1.5f);
            int life = LexoraGui.numSettings.getOrDefault("Totem Life", 80.0f).intValue();
            int[] totemColors = {0x7CFF00, 0xFFD700, 0xFFFFFF};

            int particlesPerTick = Math.max(1, totemTotalCount / 30);

            for (int i = 0; i < particlesPerTick; i++) {
                double phi = random.nextDouble() * 2 * Math.PI;
                double costheta = random.nextDouble() * 2 - 1;
                double theta = Math.acos(costheta);

                double speed = random.nextDouble() * 0.8 + 0.2;
                double velX = Math.sin(theta) * Math.cos(phi) * speed;
                double velY = Math.sin(theta) * Math.sin(phi) * speed;
                double velZ = Math.cos(theta) * speed;

                int color = totemColors[random.nextInt(totemColors.length)];

                Vec3d spawnP = totemPos.add(
                        (random.nextDouble() - 0.5) * 0.5,
                        (random.nextDouble() - 0.5) * 0.5,
                        (random.nextDouble() - 0.5) * 0.5
                );

                particles.add(new CustomParticle(spawnP, new Vec3d(velX, velY, velZ), life, size, tex, color, true, false));
            }
            totemTimer--;
        }

        if (LexoraGui.moduleStates.getOrDefault("Part. Ambient", false)) {
            int chance = LexoraGui.numSettings.getOrDefault("Amb Chance", 8.0f).intValue();
            if (random.nextInt(chance) == 0) {
                float size = LexoraGui.numSettings.getOrDefault("Amb Size", 1.0f);
                int life = LexoraGui.numSettings.getOrDefault("Amb Life", 80.0f).intValue();
                Vec3d spawnPos = mc.player.getPos().add(
                        (random.nextDouble() - 0.5) * 15,
                        random.nextDouble() * 10,
                        (random.nextDouble() - 0.5) * 15
                );
                particles.add(new CustomParticle(spawnPos, new Vec3d(0, random.nextDouble() * 0.02, 0), life * 2, size * 1.2f, tex, null, false, false));
            }
        }

        if (LexoraGui.moduleStates.getOrDefault("Part. Walk", false)) {
            boolean isMoving = mc.player.getVelocity().horizontalLengthSquared() > 0.0025;
            if (isMoving && mc.player.isOnGround()) {
                int maxCount = LexoraGui.numSettings.getOrDefault("Walk Count", 1.0f).intValue();
                int actualCount = random.nextInt(maxCount + 2);

                float size = LexoraGui.numSettings.getOrDefault("Walk Size", 0.6f);
                int life = LexoraGui.numSettings.getOrDefault("Walk Life", 20.0f).intValue();

                for (int i = 0; i < actualCount; i++) {
                    Vec3d spawnPos = mc.player.getPos().add(
                            (random.nextDouble() - 0.5) * 0.6,
                            0.2,
                            (random.nextDouble() - 0.5) * 0.6
                    );
                    Vec3d vel = new Vec3d(
                            (random.nextDouble() - 0.5) * 0.1,
                            random.nextDouble() * 0.15 + 0.05,
                            (random.nextDouble() - 0.5) * 0.1
                    );
                    particles.add(new CustomParticle(spawnPos, vel, life, size, tex, null, true, true));
                }
            }
        }

        // PROJECTILES (ТОЛЬКО МОИ СНАРЯДЫ)
        if (LexoraGui.moduleStates.getOrDefault("Part. Projectiles", false)) {
            int maxCount = LexoraGui.numSettings.getOrDefault("Proj Count", 1.0f).intValue();
            float size = LexoraGui.numSettings.getOrDefault("Proj Size", 0.6f);
            int life = LexoraGui.numSettings.getOrDefault("Proj Life", 30.0f).intValue();

            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof ProjectileEntity projectile)) continue;

                // 🔥 ФИКС: только мои снаряды
                Entity owner = projectile.getOwner();
                if (owner != mc.player) continue;

                Vec3d vel = projectile.getVelocity();
                if (vel.lengthSquared() < 0.01 || projectile.isOnGround()) continue;

                int actualCount = random.nextInt(maxCount + 2);
                Vec3d center = projectile.getPos().add(0, 0.1, 0);

                for (int i = 0; i < actualCount; i++) {
                    Vec3d rndOffset = new Vec3d(
                            random.nextDouble() - 0.5,
                            random.nextDouble() - 0.5,
                            random.nextDouble() - 0.5
                    ).multiply(0.4);

                    Vec3d shardVel = vel.multiply(-0.1).add(
                            (random.nextDouble() - 0.5) * 0.2,
                            random.nextDouble() * 0.2,
                            (random.nextDouble() - 0.5) * 0.2
                    );

                    particles.add(new CustomParticle(
                            center.add(rndOffset),
                            shardVel,
                            life,
                            size,
                            tex,
                            null,
                            true,
                            true
                    ));
                }
            }
        }
    }

    public static void spawnHitParticles(Vec3d pos, boolean isCrit) {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) return;

        boolean hitEnabled = LexoraGui.moduleStates.getOrDefault("Part. Hit", true);
        boolean critEnabled = LexoraGui.moduleStates.getOrDefault("Part. Crit", true);

        if (isCrit && !critEnabled) return;
        if (!isCrit && !hitEnabled) return;

        float size = LexoraGui.numSettings.getOrDefault(isCrit ? "Crit Size" : "Hit Size", 1.0f);
        int maxCount = LexoraGui.numSettings.getOrDefault(isCrit ? "Crit Count" : "Hit Count", 8.0f).intValue();
        int actualCount = maxCount / 2 + random.nextInt(maxCount / 2 + 1);
        int life = LexoraGui.numSettings.getOrDefault(isCrit ? "Crit Life" : "Hit Life", 40.0f).intValue();
        Identifier tex = getCurrentTexture();

        for (int i = 0; i < actualCount; i++) {
            double speed = random.nextDouble() * 0.5 + 0.1;
            Vec3d vel = new Vec3d(
                    random.nextDouble() - 0.5,
                    random.nextDouble() - 0.5,
                    random.nextDouble() - 0.5
            ).normalize().multiply(speed);

            particles.add(new CustomParticle(pos.add(0, 1, 0), vel, life, size, tex, null, false, false));
        }
    }

    public static void spawnCustomTotemEffect(Vec3d pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !LexoraGui.moduleStates.getOrDefault("Particles", false)) return;
        if (!LexoraGui.moduleStates.getOrDefault("Part. Totem", true)) return;

        totemPos = pos.add(0, 1, 0);
        totemTimer = 30;
        totemTotalCount = LexoraGui.numSettings.getOrDefault("Totem Count", 50.0f).intValue();
    }

    private static Identifier getCurrentTexture() {
        String mode = LexoraGui.modeSettings.getOrDefault("Part. Texture", "Star");
        switch (mode) {
            case "Skull": return TEX_SKULL;
            case "Bucks": return TEX_BUCKS;
            case "Snow": return TEX_SNOW;
            case "Blast": return TEX_BLAST;
            case "Brich": return TEX_BRICH;
            case "Core": return TEX_CORE;
            case "Show": return TEX_SHOW;
            default: return TEX_STAR;
        }
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (particles.isEmpty() || !LexoraGui.moduleStates.getOrDefault("Particles", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d camPos = camera.getPos();
        Quaternionf cameraRotation = camera.getRotation();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        for (CustomParticle p : particles) {
            VertexConsumer buffer = immediate.getBuffer(RenderLayer.getEntityTranslucentEmissive(p.texture));

            float lifePct = ((float) p.age + tickDelta) / p.maxAge;
            float alpha = (float) Math.pow(1.0f - lifePct, 1.5);
            int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
            float s = p.size * 0.3f * (float) Math.sin(alpha * Math.PI / 2);

            int r, g, b;
            if (p.fixedColor != null) {
                r = (p.fixedColor >> 16) & 0xFF;
                g = (p.fixedColor >> 8) & 0xFF;
                b = p.fixedColor & 0xFF;
            } else {
                int guiColor = LexoraGui.getThemeColor(p.colorOffset - (p.age * 0.005f));
                r = (guiColor >> 16) & 0xFF;
                g = (guiColor >> 8) & 0xFF;
                b = guiColor & 0xFF;
            }

            double lerpX = p.prevPos.x + (p.pos.x - p.prevPos.x) * tickDelta;
            double lerpY = p.prevPos.y + (p.pos.y - p.prevPos.y) * tickDelta;
            double lerpZ = p.prevPos.z + (p.pos.z - p.prevPos.z) * tickDelta;
            float lerpRot = p.prevRot + (p.rot - p.prevRot) * tickDelta;

            matrices.push();
            matrices.translate(lerpX, lerpY, lerpZ);
            matrices.multiply(cameraRotation);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(lerpRot));
            matrices.scale(s, s, s);

            Matrix4f mat = matrices.peek().getPositionMatrix();

            buffer.vertex(mat, -1, -1, 0).color(r, g, b, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0);
            buffer.vertex(mat, -1,  1, 0).color(r, g, b, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0);
            buffer.vertex(mat,  1,  1, 0).color(r, g, b, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0);
            buffer.vertex(mat,  1, -1, 0).color(r, g, b, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0);

            matrices.pop();
        }

        matrices.pop();
        immediate.draw();
    }
}