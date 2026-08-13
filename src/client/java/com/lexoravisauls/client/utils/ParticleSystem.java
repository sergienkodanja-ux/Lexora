package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ParticleSystem {
    private static final List<CustomParticle> particles = new ArrayList<>();
    private static final Random random = new Random();

    // Защита от просадок fps: жёсткий кап количества и отсечение рендера по дистанции.
    // Обе константы можно крутить под себя.
    private static final int MAX_PARTICLES = 400;
    private static final double MAX_RENDER_DISTANCE = 64.0;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    // Старые текстуры
    private static final Identifier TEX_STAR = Identifier.of("lexoravisauls", "textures/particle/star1.png");
    private static final Identifier TEX_SKULL = Identifier.of("lexoravisauls", "textures/particle/ded1.png");
    private static final Identifier TEX_BUCKS = Identifier.of("lexoravisauls", "textures/particle/bucks1.png");
    private static final Identifier TEX_SNOW = Identifier.of("lexoravisauls", "textures/particle/snownew1.png");
    private static final Identifier TEX_BLAST = Identifier.of("lexoravisauls", "textures/particle/snowblast1.png");
    private static final Identifier TEX_BRICH = Identifier.of("lexoravisauls", "textures/particle/snowbrich1.png");
    private static final Identifier TEX_CORE = Identifier.of("lexoravisauls", "textures/particle/core1.png");
    private static final Identifier TEX_SHOW = Identifier.of("lexoravisauls", "textures/particle/show1.png");
    private static final Identifier TEX_SNOWBAG = Identifier.of("lexoravisauls", "textures/particle/snowbag1.png");
    private static final Identifier TEX_GENSHIN = Identifier.of("lexoravisauls", "textures/particle/genshin.png");
    private static final Identifier TEX_HEART = Identifier.of("lexoravisauls", "textures/particle/heart1.png");

    // Текстура свечения (фонарик)
    private static final Identifier TEX_BLOOM = Identifier.of("lexoravisauls", "textures/gui/bloom.png");

    private static int totemTimer = 0;
    private static Entity totemEntity = null;
    private static int totemTotalCount = 0;
    private static int totemSpawnedCount = 0;

    private static Vec3d lastWalkPlayerPos = null;
    private static double walkDistanceAccumulator = 0.0;

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

        public boolean snowLike;
        public float driftSeedA;
        public float driftSeedB;
        public float driftSeedC;

        // Кубик-стиль рендера: вместо спрайта рисуется wireframe-куб с независимым 3D вращением
        public boolean isCube;
        public Vec3d rot3d;
        public Vec3d prevRot3d;
        public Vec3d rotSpeed3d;

        public CustomParticle(Vec3d pos, Vec3d vel, int maxAge, float size, Identifier texture,
                              Integer fixedColor, boolean hasGravity, boolean canBounce, boolean snowLike,
                              boolean isCube) {
            this.pos = pos;
            this.prevPos = pos;
            this.vel = vel;
            this.age = 0;
            this.maxAge = maxAge * 3;
            this.size = size;
            this.texture = texture;
            this.rot = random.nextFloat() * 360f;
            this.prevRot = this.rot;
            this.rotSpeed = (random.nextFloat() - 0.5f) * 6f;
            this.colorOffset = random.nextFloat();
            this.fixedColor = fixedColor;
            this.hasGravity = hasGravity;
            this.canBounce = canBounce;
            this.snowLike = snowLike;
            this.driftSeedA = random.nextFloat() * 6.2831855f;
            this.driftSeedB = random.nextFloat() * 6.2831855f;
            this.driftSeedC = random.nextFloat() * 6.2831855f;

            this.isCube = isCube;
            this.rot3d = Vec3d.ZERO;
            this.prevRot3d = Vec3d.ZERO;
            this.rotSpeed3d = isCube
                    ? new Vec3d((random.nextDouble() - 0.5) * 4.0, (random.nextDouble() - 0.5) * 4.0, (random.nextDouble() - 0.5) * 4.0)
                    : Vec3d.ZERO;
        }
    }

    public static void tick() {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) {
            particles.clear();
            totemTimer = 0;
            totemEntity = null;
            totemTotalCount = 0;
            totemSpawnedCount = 0;
            lastWalkPlayerPos = null;
            walkDistanceAccumulator = 0.0;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            lastWalkPlayerPos = null;
            walkDistanceAccumulator = 0.0;
            return;
        }

        Identifier tex = getCurrentTexture();
        boolean cube = isCubeMode();

        for (int i = particles.size() - 1; i >= 0; i--) {
            CustomParticle p = particles.get(i);
            p.prevPos = p.pos;
            p.prevRot = p.rot;
            if (p.isCube) p.prevRot3d = p.rot3d;

            p.age++;
            if (p.age >= p.maxAge) {
                particles.remove(i);
                continue;
            }

            if (p.snowLike) {
                updateSnowLikeParticle(p, 0, 0);
            } else {
                p.vel = p.vel.multiply(0.985);

                if (p.canBounce) {
                    p.vel = p.vel.add(0, -0.015, 0);

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
                        p.vel = new Vec3d(p.vel.x * 0.7, p.vel.y * -0.5, p.vel.z * 0.7);
                        p.pos = new Vec3d(p.pos.x, surfaceY + 0.05, p.pos.z);

                        if (Math.abs(p.vel.y) < 0.02) {
                            p.vel = new Vec3d(p.vel.x * 0.9, 0, p.vel.z * 0.9);
                            p.rotSpeed *= 0.8;
                        }
                    }
                } else if (p.hasGravity) {
                    p.vel = p.vel.add(0, -0.003, 0);
                } else {
                    double swirl = Math.sin(p.age * 0.05) * 0.002;
                    p.vel = p.vel.add(swirl, 0, Math.cos(p.age * 0.05) * 0.002);
                }
            }

            p.pos = p.pos.add(p.vel);
            p.rot += p.rotSpeed * (float) (p.vel.length() * 1.5 + 0.1);
            p.rotSpeed *= 0.99f;

            if (p.isCube) {
                p.rot3d = p.rot3d.add(p.rotSpeed3d.multiply(0.02));
                p.rotSpeed3d = p.rotSpeed3d.multiply(0.995);
            }
        }

        if (totemTimer > 0 && totemEntity != null && totemEntity.isAlive() && totemSpawnedCount < totemTotalCount) {
            float size = LexoraGui.numSettings.getOrDefault("Totem Size", 1.5f);
            int life = LexoraGui.numSettings.getOrDefault("Totem Life", 80.0f).intValue();
            float spread = LexoraGui.numSettings.getOrDefault("Totem Spread", 1.0f);

            int remainingTicks = Math.max(1, totemTimer);
            int remainingCount = Math.max(0, totemTotalCount - totemSpawnedCount);
            int particlesThisTick = Math.min(remainingCount, (int) Math.ceil(remainingCount / (double) remainingTicks));

            Vec3d currentEntityPos = totemEntity.getPos().add(0, totemEntity.getHeight() / 2.0, 0);

            for (int i = 0; i < particlesThisTick; i++) {
                double phi = random.nextDouble() * 2 * Math.PI;
                double costheta = random.nextDouble() * 2 - 1;
                double theta = Math.acos(costheta);

                double speed = (random.nextDouble() * 0.4 + 0.1) * spread;
                double velX = Math.sin(theta) * Math.cos(phi) * speed;
                double velY = Math.sin(theta) * Math.sin(phi) * speed;
                double velZ = Math.cos(theta) * speed;

                Vec3d spawnP = currentEntityPos.add(
                        (random.nextDouble() - 0.5) * 0.5,
                        (random.nextDouble() - 0.5) * 0.5,
                        (random.nextDouble() - 0.5) * 0.5
                );

                spawnParticle(new CustomParticle(
                        spawnP,
                        new Vec3d(velX, velY, velZ),
                        life,
                        size,
                        tex,
                        null,
                        false,
                        false,
                        false,
                        cube
                ));
            }

            totemSpawnedCount += particlesThisTick;
            totemTimer--;

            if (totemTimer <= 0 || totemSpawnedCount >= totemTotalCount) {
                totemTimer = 0;
                totemEntity = null;
            }
        } else if (totemTimer > 0 && (totemEntity == null || !totemEntity.isAlive())) {
            totemTimer = 0;
            totemEntity = null;
        }

        if (LexoraGui.moduleStates.getOrDefault("Part. Ambient", false)) {
            int amount = clampInt(LexoraGui.numSettings.getOrDefault("Amb Chance", 8.0f).intValue(), 1, 120);

            int spawnCount = Math.max(1, amount / 8);
            if (random.nextFloat() < (amount % 8) / 8.0f) spawnCount++;

            float size = LexoraGui.numSettings.getOrDefault("Amb Size", 1.0f);
            int life = LexoraGui.numSettings.getOrDefault("Amb Life", 80.0f).intValue();
            float spread = LexoraGui.numSettings.getOrDefault("Amb Spread", 1.0f);

            double spawnRadius = getAmbientSpawnRadius();
            double minBelow = 10.0;
            double maxAbove = 18.0;

            for (int i = 0; i < spawnCount; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = Math.sqrt(random.nextDouble()) * spawnRadius;

                double offX = Math.cos(angle) * dist;
                double offZ = Math.sin(angle) * dist;
                double y = mc.player.getY() - minBelow + random.nextDouble() * (minBelow + maxAbove);

                Vec3d spawnPos = new Vec3d(mc.player.getX() + offX, y, mc.player.getZ() + offZ);
                Vec3d startVel = new Vec3d(
                        (random.nextDouble() - 0.5) * 0.02 * spread,
                        -(0.002 + random.nextDouble() * 0.005) * spread,
                        (random.nextDouble() - 0.5) * 0.02 * spread
                );

                // ФИКС: раньше здесь было "life * 2". С учётом *3 в конструкторе амбиент-партиклы
                // жили в 6 раз дольше настройки Amb Life вместо 3х как у всех остальных типов —
                // отсюда неограниченный рост количества партиклов в фоне при "Part. Ambient" on.
                spawnParticle(new CustomParticle(spawnPos, startVel, life, size * 1.15f, tex, null, false, false, true, cube));
            }
        }

        if (LexoraGui.moduleStates.getOrDefault("Part. Walk", false)) {
            Vec3d currentPos = mc.player.getPos();
            if (lastWalkPlayerPos == null) lastWalkPlayerPos = currentPos;

            double dx = currentPos.x - lastWalkPlayerPos.x;
            double dz = currentPos.z - lastWalkPlayerPos.z;
            double horizontalMoved = Math.sqrt(dx * dx + dz * dz);
            lastWalkPlayerPos = currentPos;

            boolean isMoving = mc.player.getVelocity().horizontalLengthSquared() > 0.0025;
            if (isMoving && mc.player.isOnGround()) {
                walkDistanceAccumulator += horizontalMoved;

                int actualCount = Math.max(1, LexoraGui.numSettings.getOrDefault("Walk Count", 1.0f).intValue());
                float size = LexoraGui.numSettings.getOrDefault("Walk Size", 0.6f);
                int life = LexoraGui.numSettings.getOrDefault("Walk Life", 20.0f).intValue();
                float spread = LexoraGui.numSettings.getOrDefault("Walk Spread", 1.0f);

                double stepDistance = 0.36;

                while (walkDistanceAccumulator >= stepDistance) {
                    walkDistanceAccumulator -= stepDistance;

                    for (int i = 0; i < actualCount; i++) {
                        Vec3d spawnPos = mc.player.getPos().add(
                                (random.nextDouble() - 0.5) * 0.6,
                                random.nextDouble() * 1.8,
                                (random.nextDouble() - 0.5) * 0.6
                        );
                        Vec3d vel = new Vec3d(
                                (random.nextDouble() - 0.5) * 0.05 * spread,
                                (random.nextDouble() * 0.08 + 0.02) * spread,
                                (random.nextDouble() - 0.5) * 0.05 * spread
                        );
                        spawnParticle(new CustomParticle(spawnPos, vel, life, size, tex, null, false, true, false, cube));
                    }
                }
            } else {
                walkDistanceAccumulator = 0.0;
            }
        } else {
            lastWalkPlayerPos = mc.player.getPos();
            walkDistanceAccumulator = 0.0;
        }

        if (LexoraGui.moduleStates.getOrDefault("Part. Projectiles", false)) {
            int actualCount = Math.max(1, LexoraGui.numSettings.getOrDefault("Proj Count", 1.0f).intValue());
            float size = LexoraGui.numSettings.getOrDefault("Proj Size", 0.6f);
            int life = LexoraGui.numSettings.getOrDefault("Proj Life", 30.0f).intValue();
            float spread = LexoraGui.numSettings.getOrDefault("Proj Spread", 1.0f);

            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof ProjectileEntity projectile)) continue;

                Entity owner = projectile.getOwner();
                if (owner != mc.player) continue;

                Vec3d vel = projectile.getVelocity();
                if (vel.lengthSquared() < 0.01 || projectile.isOnGround()) continue;

                Vec3d center = projectile.getPos().add(0, 0.1, 0);

                for (int i = 0; i < actualCount; i++) {
                    Vec3d rndOffset = new Vec3d(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).multiply(0.4);
                    Vec3d shardVel = vel.multiply(-0.05).add(
                            (random.nextDouble() - 0.5) * 0.1 * spread,
                            random.nextDouble() * 0.1 * spread,
                            (random.nextDouble() - 0.5) * 0.1 * spread
                    );

                    spawnParticle(new CustomParticle(center.add(rndOffset), shardVel, life, size, tex, null, false, true, false, cube));
                }
            }
        }
    }

    // Спавн с уважением к MAX_PARTICLES — вместо неограниченного particles.add(...) везде
    private static void spawnParticle(CustomParticle p) {
        if (particles.size() < MAX_PARTICLES) {
            particles.add(p);
        }
    }

    private static void updateSnowLikeParticle(CustomParticle p, float windX, float windZ) {
        double t = p.age * 0.055 + p.colorOffset * 7.0;

        double targetX = Math.sin(t + p.driftSeedA) * 0.018 + Math.cos(t * 0.65 + p.driftSeedB) * 0.010;
        double targetZ = Math.cos(t + p.driftSeedB) * 0.018 + Math.sin(t * 0.72 + p.driftSeedC) * 0.010;
        double targetY = -(0.006 + Math.abs(Math.sin(t * 0.45 + p.driftSeedC)) * 0.006) + Math.sin(t * 0.35 + p.driftSeedA) * 0.0015;

        p.vel = p.vel.multiply(0.985);
        p.vel = new Vec3d(
                lerp(p.vel.x, targetX, 0.055),
                lerp(p.vel.y, targetY, 0.035),
                lerp(p.vel.z, targetZ, 0.055)
        );
        p.rotSpeed = (float) lerp(p.rotSpeed, Math.sin(t + p.driftSeedB) * 1.8, 0.04);
    }

    public static void spawnHitParticles(Vec3d pos, boolean isCrit) {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) return;

        boolean hitEnabled = LexoraGui.moduleStates.getOrDefault("Part. Hit", true);
        boolean critEnabled = LexoraGui.moduleStates.getOrDefault("Part. Crit", true);

        if (isCrit && !critEnabled) return;
        if (!isCrit && !hitEnabled) return;

        float size = LexoraGui.numSettings.getOrDefault(isCrit ? "Crit Size" : "Hit Size", 1.0f);
        int actualCount = Math.max(1, LexoraGui.numSettings.getOrDefault(isCrit ? "Crit Count" : "Hit Count", 8.0f).intValue());
        int life = LexoraGui.numSettings.getOrDefault(isCrit ? "Crit Life" : "Hit Life", 40.0f).intValue();
        float spread = LexoraGui.numSettings.getOrDefault(isCrit ? "Crit Spread" : "Hit Spread", 1.0f);

        Identifier tex = getCurrentTexture();
        boolean cube = isCubeMode();

        for (int i = 0; i < actualCount; i++) {
            double speed = (random.nextDouble() * 0.3 + 0.05) * spread;
            Vec3d vel = new Vec3d(
                    random.nextDouble() - 0.5,
                    random.nextDouble() - 0.5,
                    random.nextDouble() - 0.5
            ).normalize().multiply(speed);

            spawnParticle(new CustomParticle(pos.add(0, 1, 0), vel, life, size, tex, null, false, false, false, cube));
        }
    }

    public static void spawnCustomTotemEffect(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !LexoraGui.moduleStates.getOrDefault("Particles", false)) return;
        if (!LexoraGui.moduleStates.getOrDefault("Part. Totem", true)) return;

        totemEntity = entity;
        totemTimer = 30;
        totemTotalCount = Math.max(1, LexoraGui.numSettings.getOrDefault("Totem Count", 50.0f).intValue());
        totemSpawnedCount = 0;
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
            case "Snowbag": return TEX_SNOWBAG;
            case "Genshin": return TEX_GENSHIN;
            case "Heart": return TEX_HEART;
            case "Cube": return TEX_STAR; // текстура не используется для кубика, валидный fallback
            default: return TEX_STAR;
        }
    }

    private static boolean isCubeMode() {
        return "Cube".equals(LexoraGui.modeSettings.getOrDefault("Part. Texture", "Star"));
    }

    private static double getAmbientSpawnRadius() {
        // Раньше радиус рос вместе с view distance (до 180+ блоков на 12 чанках) и партиклы
        // спавнились там, где их всё равно не видно. Теперь просто = дальности рендера партиклов.
        return MAX_RENDER_DISTANCE;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // Кэш посчитанных за кадр значений на партикл — чтобы не пересчитывать лерп/цвет/альфу
    // дважды и не таскать их между двумя проходами руками.
    private record ParticleRenderData(CustomParticle particle, int r, int g, int b, int a, float s,
                                      double lerpX, double lerpY, double lerpZ, float lerpRot) {
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (particles.isEmpty() || !LexoraGui.moduleStates.getOrDefault("Particles", false)) return;

        Vec3d camPos = camera.getPos();
        Quaternionf cameraRotation = camera.getRotation();

        // Один проход считает лерп/цвет/альфу и заодно режет по дальности —
        // дальше это используется в рендер-пассах ниже без пересчёта.
        List<ParticleRenderData> visible = new ArrayList<>(particles.size());
        for (CustomParticle p : particles) {
            if (p.pos.squaredDistanceTo(camPos) > MAX_RENDER_DISTANCE_SQ) continue;

            float lifePct = ((float) p.age + tickDelta) / p.maxAge;
            float alpha = (float) Math.pow(1.0f - lifePct, 1.5);
            int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
            float s = p.size * 0.3f * (float) Math.sin(alpha * Math.PI / 2);
            int[] rgb = computeColorRGB(p, lifePct);

            double lerpX = p.prevPos.x + (p.pos.x - p.prevPos.x) * tickDelta;
            double lerpY = p.prevPos.y + (p.pos.y - p.prevPos.y) * tickDelta;
            double lerpZ = p.prevPos.z + (p.pos.z - p.prevPos.z) * tickDelta;
            float lerpRot = p.prevRot + (p.rot - p.prevRot) * tickDelta;

            visible.add(new ParticleRenderData(p, rgb[0], rgb[1], rgb[2], a, s, lerpX, lerpY, lerpZ, lerpRot));
        }

        if (!visible.isEmpty()) {
            renderGlowAndSprites(matrices, camPos, cameraRotation, visible);
        }

        renderCubeParticles(matrices, camPos, tickDelta);
    }

    // Glow и спрайты — сырой Tesselator с лёгким шейдером POSITION_TEX_COLOR (как куб, и как
    // в сурсе), вместо VertexConsumerProvider.Immediate + RenderLayer.getEntityTranslucentEmissive.
    // Причина просадки была именно тут: entity-emissive layer тянет за собой полный
    // entity-шейдер с сэмплами лайтмапы и оверлея НА КАЖДЫЙ ПИКСЕЛЬ — а мы всё равно жёстко
    // форсили MAX_LIGHT_COORDINATE и DEFAULT_UV, то есть платили GPU-время за лайтинг/оверлей,
    // которые ни на что не влияли. POSITION_TEX_COLOR — это только текстура + вершинный цвет,
    // без лишних сэмплов. При overdraw от больших полупрозрачных glow-квадов (а их вдвое больше,
    // чем спрайтов — по 2 слоя на партикл) разница в цене шейдера на пиксель как раз и даёт
    // те самые "съеденные" fps, которых нет у куба (у него вообще нет текстуры и это тонкие линии).
    // Плюс все партиклы одного типа/текстуры льются в ОДИН буфер = один draw call, а не по одному
    // на частицу.
    private static void renderGlowAndSprites(MatrixStack matrices, Vec3d camPos, Quaternionf cameraRotation, List<ParticleRenderData> visible) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        // ---- GLOW: одна текстура (TEX_BLOOM) на всех партиклов сразу (и на кубики тоже), один draw call ----
        RenderSystem.setShaderTexture(0, TEX_BLOOM);
        BufferBuilder glowBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (ParticleRenderData d : visible) {
            matrices.push();
            matrices.translate(d.lerpX(), d.lerpY(), d.lerpZ());
            matrices.multiply(cameraRotation);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(d.lerpRot()));

            for (int layer = 1; layer <= 2; layer++) {
                float glowScale = d.s() * (1.2f + layer * 0.6f);
                int glowAlpha = (int) (d.a() * (0.35f / layer));

                if (glowAlpha > 0) {
                    matrices.push();
                    matrices.scale(glowScale, glowScale, glowScale);
                    drawQuad(matrices.peek().getPositionMatrix(), glowBuf, d.r(), d.g(), d.b(), glowAlpha);
                    matrices.pop();
                }
            }

            matrices.pop();
        }
        BuiltBuffer builtGlow = glowBuf.endNullable();
        if (builtGlow != null) {
            BufferRenderer.drawWithGlobalProgram(builtGlow);
        }

        // ---- СПРАЙТЫ: группируем по текстуре. Обычно она одна на всех живых партиклов сразу
        // (Part. Texture — глобальная настройка), но если её только что переключили, часть
        // старых партиклов ещё донашивает старую текстуру — на этот случай и группировка,
        // а не жёсткое предположение "текстура всегда одна". Кубики сюда не попадают —
        // они рисуются отдельно в renderCubeParticles ----
        Map<Identifier, List<ParticleRenderData>> byTexture = new LinkedHashMap<>();
        for (ParticleRenderData d : visible) {
            if (d.particle().isCube) continue;
            byTexture.computeIfAbsent(d.particle().texture, k -> new ArrayList<>()).add(d);
        }

        for (Map.Entry<Identifier, List<ParticleRenderData>> entry : byTexture.entrySet()) {
            RenderSystem.setShaderTexture(0, entry.getKey());
            BufferBuilder spriteBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (ParticleRenderData d : entry.getValue()) {
                matrices.push();
                matrices.translate(d.lerpX(), d.lerpY(), d.lerpZ());
                matrices.multiply(cameraRotation);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(d.lerpRot()));
                matrices.scale(d.s(), d.s(), d.s());
                drawQuad(matrices.peek().getPositionMatrix(), spriteBuf, d.r(), d.g(), d.b(), d.a());
                matrices.pop();
            }
            BuiltBuffer builtSprite = spriteBuf.endNullable();
            if (builtSprite != null) {
                BufferRenderer.drawWithGlobalProgram(builtSprite);
            }
        }

        matrices.pop();

        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
    }

    private static void drawQuad(Matrix4f mat, BufferBuilder buffer, int r, int g, int b, int a) {
        buffer.vertex(mat, -1, -1, 0).color(r, g, b, a).texture(1, 1);
        buffer.vertex(mat, -1, 1, 0).color(r, g, b, a).texture(1, 0);
        buffer.vertex(mat, 1, 1, 0).color(r, g, b, a).texture(0, 0);
        buffer.vertex(mat, 1, -1, 0).color(r, g, b, a).texture(0, 1);
    }

    // Отдельный пасс для кубиков: свой pipeline (DEBUG_LINES + POSITION_COLOR).
    private static void renderCubeParticles(MatrixStack matrices, Vec3d camPos, float tickDelta) {
        boolean anyCube = false;
        for (CustomParticle p : particles) {
            if (p.isCube) {
                anyCube = true;
                break;
            }
        }
        if (!anyCube) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        for (CustomParticle p : particles) {
            if (!p.isCube) continue;
            if (p.pos.squaredDistanceTo(camPos) > MAX_RENDER_DISTANCE_SQ) continue;

            float lifePct = ((float) p.age + tickDelta) / p.maxAge;
            float alpha = (float) Math.pow(1.0f - lifePct, 1.5);
            int a = Math.max(0, (int) (alpha * 205));
            float s = p.size * 0.3f * (float) Math.sin(alpha * Math.PI / 2);

            int[] rgb = computeColorRGB(p, lifePct);

            double lerpX = p.prevPos.x + (p.pos.x - p.prevPos.x) * tickDelta;
            double lerpY = p.prevPos.y + (p.pos.y - p.prevPos.y) * tickDelta;
            double lerpZ = p.prevPos.z + (p.pos.z - p.prevPos.z) * tickDelta;
            double lerpRotX = p.prevRot3d.x + (p.rot3d.x - p.prevRot3d.x) * tickDelta;
            double lerpRotY = p.prevRot3d.y + (p.rot3d.y - p.prevRot3d.y) * tickDelta;
            double lerpRotZ = p.prevRot3d.z + (p.rot3d.z - p.prevRot3d.z) * tickDelta;

            matrices.push();
            matrices.translate(lerpX, lerpY, lerpZ);
            matrices.multiply(new Quaternionf().rotationXYZ((float) lerpRotX, (float) lerpRotY, (float) lerpRotZ));
            matrices.scale(s * 2.0f, s * 2.0f, s * 2.0f);
            drawWireCube(matrices, buffer, rgb[0], rgb[1], rgb[2], a);
            matrices.pop();
        }

        matrices.pop();

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // Простой wireframe-куб: 12 рёбер без dashed/rounded — в оригинальном сурсе это было
    // ~70 дэшей на 6 гранях (144 вершины НА ОДИН партикл), что при твоей ситуации с fps
    // убило бы всё заново на 30-40 кубиках. Здесь 12 рёбер = 24 вершины на партикл.
    private static void drawWireCube(MatrixStack matrices, BufferBuilder buffer, int r, int g, int b, int a) {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        float h = 0.5f;
        float[][] edges = {
                {-h, -h, -h, h, -h, -h}, {h, -h, -h, h, -h, h}, {h, -h, h, -h, -h, h}, {-h, -h, h, -h, -h, -h},
                {-h, h, -h, h, h, -h}, {h, h, -h, h, h, h}, {h, h, h, -h, h, h}, {-h, h, h, -h, h, -h},
                {-h, -h, -h, -h, h, -h}, {h, -h, -h, h, h, -h}, {h, -h, h, h, h, h}, {-h, -h, h, -h, h, h}
        };
        for (float[] e : edges) {
            buffer.vertex(mat, e[0], e[1], e[2]).color(r, g, b, a);
            buffer.vertex(mat, e[3], e[4], e[5]).color(r, g, b, a);
        }
    }

    private static int[] computeColorRGB(CustomParticle p, float lifePct) {
        int r, g, b;
        String colorMode = LexoraGui.modeSettings.getOrDefault("Part. Color Mode", "Theme");
        if (colorMode.equals("Custom")) {
            String colorType = LexoraGui.modeSettings.getOrDefault("Part. Color Type", "Solid");
            if (colorType.equals("Gradient")) {
                float[] c1 = LexoraGui.colorSettings.getOrDefault("Part. Custom Color 1", new float[]{0, 1f, 1f});
                float[] c2 = LexoraGui.colorSettings.getOrDefault("Part. Custom Color 2", new float[]{0.5f, 1f, 1f});
                int rgb1 = Color.HSBtoRGB(c1[0], c1[1], c1[2]);
                int rgb2 = Color.HSBtoRGB(c2[0], c2[1], c2[2]);

                r = (int) lerp((rgb1 >> 16) & 0xFF, (rgb2 >> 16) & 0xFF, lifePct);
                g = (int) lerp((rgb1 >> 8) & 0xFF, (rgb2 >> 8) & 0xFF, lifePct);
                b = (int) lerp(rgb1 & 0xFF, rgb2 & 0xFF, lifePct);
            } else {
                float[] c = LexoraGui.colorSettings.getOrDefault("Part. Custom Color", new float[]{0, 1f, 1f});
                int rgb = Color.HSBtoRGB(c[0], c[1], c[2]);
                r = (rgb >> 16) & 0xFF;
                g = (rgb >> 8) & 0xFF;
                b = rgb & 0xFF;
            }
        } else {
            int guiColor = LexoraGui.getThemeColor(p.colorOffset - (p.age * 0.005f));
            r = (guiColor >> 16) & 0xFF;
            g = (guiColor >> 8) & 0xFF;
            b = guiColor & 0xFF;
        }
        return new int[]{r, g, b};
    }
}