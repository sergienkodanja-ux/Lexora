package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.Color;
import java.util.*;

public class ParticleSystem {
    // 3 particle lists exactly as in source
    private static final List<Particle> targetParticles = new ArrayList<>();
    private static final List<Particle> worldParticles = new ArrayList<>();
    private static final List<Particle> flameParticles = new ArrayList<>();
    private static final Random random = new Random();

    // Caps to prevent memory overflow and guarantee high FPS
    private static final int MAX_PARTICLES_PER_LIST = 200;
    private static final double MAX_RENDER_DISTANCE = 64.0;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    // Authentic totem pop colors from source
    private static final int TOTEM_COLOR_GREEN = (255 << 24) | (127 << 16) | (221 << 8) | 144;
    private static final int TOTEM_COLOR_YELLOW = (255 << 24) | (221 << 16) | (218 << 8) | 127;

    // Textures
    public static final Identifier TEX_BLOOM = Identifier.of("lexoravisauls", "textures/gui/bloom.png");
    public static final Identifier TEX_STAR = Identifier.of("lexoravisauls", "textures/particle/star1.png");
    public static final Identifier TEX_HEART = Identifier.of("lexoravisauls", "textures/particle/heart1.png");
    public static final Identifier TEX_DOLLAR = Identifier.of("lexoravisauls", "textures/particle/bucks1.png");
    public static final Identifier TEX_SNOW = Identifier.of("lexoravisauls", "textures/particle/show1.png");
    public static final Identifier TEX_STAR2 = Identifier.of("lexoravisauls", "textures/particle/core1.png");
    public static final Identifier TEX_KRONEX = Identifier.of("lexoravisauls", "textures/particle/ded1.png");

    private static double lastPlayerX;
    private static double lastPlayerY;
    private static double lastPlayerZ;
    private static boolean positionInitialized = false;

    private static long lastUpdateTime = System.nanoTime();
    private static long lastTickTime = -1;
    private static long totemSpawnTime = 0L;
    private static boolean spawningTotemParticles = false;
    private static Entity currentTotemTarget = null;

    public enum ParticleType {
        BLOOM(TEX_BLOOM),
        STAR(TEX_STAR),
        HEART(TEX_HEART),
        DOLLAR(TEX_DOLLAR),
        SNOW(TEX_SNOW),
        STARNEW(TEX_STAR2),
        KRONEX(TEX_KRONEX),
        CUBE(null);

        private final Identifier texture;

        ParticleType(Identifier texture) {
            this.texture = texture;
        }

        public Identifier getTexture() {
            return texture;
        }

        public static ParticleType getRandom() {
            ParticleType[] types = new ParticleType[]{BLOOM, STAR, HEART, DOLLAR, SNOW, STARNEW, KRONEX};
            return types[random.nextInt(types.length)];
        }
    }

    public static class Particle {
        public static final double BASE_VELOCITY = 0.05;

        public final ParticleType type;
        public final boolean isCube;
        public final boolean isTotem;
        public final int fixedColor;
        public final float size;
        public final double speedMultiplier;
        public final long creationTime;
        public final float colorOffset;

        public Vec3d position;
        public Vec3d velocity;
        public float rotation;
        public float rotSpeed;
        public Vec3d rot3d;
        public Vec3d rotSpeed3d;

        public Particle(ParticleType type, Vec3d position, Vec3d velocity, int fixedColor, boolean isTotem,
                        float size, double speedMultiplier) {
            this.type = type;
            this.isCube = (type == ParticleType.CUBE);
            this.isTotem = isTotem;
            this.position = position;
            this.velocity = velocity.multiply(BASE_VELOCITY);
            this.fixedColor = fixedColor;
            this.size = size;
            this.speedMultiplier = speedMultiplier;
            this.creationTime = System.currentTimeMillis();
            this.colorOffset = random.nextFloat();
            this.rotation = random.nextFloat() * 360f;
            this.rotSpeed = (random.nextBoolean() ? 1f : -1f) * (60f + random.nextFloat() * 120f);
            this.rot3d = Vec3d.ZERO;
            this.rotSpeed3d = isCube
                    ? new Vec3d((random.nextDouble() - 0.5) * 4.0, (random.nextDouble() - 0.5) * 4.0, (random.nextDouble() - 0.5) * 4.0)
                    : Vec3d.ZERO;
        }
    }

    private record ParticleRenderData(Particle particle, Vec3d pos, int r, int g, int b, int a, float size) {
    }

    public static void tick() {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) {
            clear();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            clear();
            return;
        }

        long currentTick = mc.world.getTime();
        if (currentTick == lastTickTime) return;
        lastTickTime = currentTick;

        spawnIdleParticles();
        spawnMoveParticles();
        spawnThrowParticles();
        updateTotemParticles();

        long now = System.currentTimeMillis();
        targetParticles.removeIf(p -> (now - p.creationTime) >= 1000L);
        worldParticles.removeIf(p -> (now - p.creationTime) >= 2000L);
        flameParticles.removeIf(p -> (now - p.creationTime) >= 2000L);
    }

    public static void clear() {
        targetParticles.clear();
        worldParticles.clear();
        flameParticles.clear();
        positionInitialized = false;
        spawningTotemParticles = false;
        currentTotemTarget = null;
        totemSpawnTime = 0L;
    }

    public static void onAttack(Entity target, boolean isCrit) {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) return;
        if (!isAttackEnabled()) return;
        if (target == null) return;

        int count = getAttackCount();
        boolean strongY = isStrongY();

        for (int i = 0; i < count; i++) {
            double yMotion = strongY ? randomValue(-1.6, 1.35) : randomValue(-1.25, 1.25);
            spawnParticle(targetParticles,
                    new Vec3d(target.getX() + randomValue(-0.4, 0.4),
                            target.getY() + randomValue(0.0, target.getHeight()),
                            target.getZ() + randomValue(-0.4, 0.4)),
                    new Vec3d(randomValue(-1.35, 1.35), yMotion, randomValue(-1.35, 1.35)));
        }
    }

    public static void spawnHitParticles(Vec3d pos, boolean isCrit) {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) return;
        if (!isAttackEnabled()) return;
        if (pos == null) return;

        int count = getAttackCount();
        boolean strongY = isStrongY();

        for (int i = 0; i < count; i++) {
            double yMotion = strongY ? randomValue(-1.6, 1.35) : randomValue(-1.25, 1.25);
            spawnParticle(targetParticles,
                    new Vec3d(pos.x + randomValue(-0.4, 0.4),
                            pos.y + randomValue(0.0, 1.8),
                            pos.z + randomValue(-0.4, 0.4)),
                    new Vec3d(randomValue(-1.35, 1.35), yMotion, randomValue(-1.35, 1.35)));
        }
    }

    public static void spawnCustomTotemEffect(Entity entity) {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) return;
        if (!isTotemEnabled()) return;
        if (entity == null) return;

        spawningTotemParticles = true;
        totemSpawnTime = System.currentTimeMillis();
        currentTotemTarget = entity;
    }

    private static void updateTotemParticles() {
        if (!spawningTotemParticles || currentTotemTarget == null) return;

        if (System.currentTimeMillis() - totemSpawnTime > 2500L) {
            spawningTotemParticles = false;
            currentTotemTarget = null;
            return;
        }

        int count = getTotemCount();
        for (int i = 0; i < count; i++) {
            int color = random.nextBoolean() ? TOTEM_COLOR_YELLOW : TOTEM_COLOR_GREEN;
            spawnParticleTotem(flameParticles,
                    new Vec3d(currentTotemTarget.getX() + randomValue(-0.4, 0.4),
                            currentTotemTarget.getY() + randomValue(0.0, 2.0),
                            currentTotemTarget.getZ() + randomValue(-0.4, 0.4)),
                    new Vec3d(randomValue(-0.8, 0.8), randomValue(-0.6, 0.1), randomValue(-0.8, 0.8)),
                    color);
        }
    }

    private static void spawnIdleParticles() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!isIdleEnabled()) return;

        int range = getIdleRange();
        int count = getIdleCount();

        for (int i = 0; i < count; i++) {
            Vec3d around = mc.player.getPos().add(randomValue(-range, range), 0.0, randomValue(-range, range));
            BlockPos pos = mc.world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(around));
            double x = pos.getX() + randomValue(0.0, 1.0);
            double z = pos.getZ() + randomValue(0.0, 1.0);
            double y = mc.player.getY() + randomValue(mc.player.getHeight(), range);
            Vec3d spawnPos = new Vec3d(x, y, z);

            while (!mc.world.getBlockState(BlockPos.ofFloored(spawnPos)).isAir() && spawnPos.y < mc.world.getTopYInclusive()) {
                spawnPos = spawnPos.add(0.0, 1.0, 0.0);
            }

            Vec3d playerVel = mc.player.getVelocity();
            spawnParticle(worldParticles, spawnPos,
                    new Vec3d(playerVel.x + randomValue(-0.5, 0.5), randomValue(-0.06, 0.06), playerVel.z + randomValue(-0.5, 0.5)));
        }
    }

    private static void spawnMoveParticles() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!isMoveEnabled() || !hasPlayerMoved() || mc.options.getPerspective().isFirstPerson()) return;

        int count = getMoveCount();
        for (int i = 0; i < count; i++) {
            Vec3d playerVel = mc.player.getVelocity();
            spawnParticle(flameParticles,
                    new Vec3d(mc.player.getX() + randomValue(-0.5, 0.5),
                            mc.player.getY() + randomValue(0.0, mc.player.getHeight()),
                            mc.player.getZ() + randomValue(-0.5, 0.5)),
                    new Vec3d(playerVel.x + randomValue(-0.25, 0.25),
                            randomValue(-0.15, 0.15),
                            playerVel.z + randomValue(-0.25, 0.25)));
        }
    }

    private static void spawnThrowParticles() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!isThrowEnabled()) return;

        Vec3d playerPos = mc.player.getPos();
        double maxDistSq = 64.0 * 64.0;
        int count = getThrowCount();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ProjectileEntity) || entity.isOnGround()) continue;
            if (entity.squaredDistanceTo(playerPos) > maxDistSq) continue;

            boolean isMoving = entity.prevX != entity.getX() || entity.prevY != entity.getY() || entity.prevZ != entity.getZ();
            if (!isMoving) continue;

            Vec3d pos = entity.getPos();
            for (int i = 0; i < count; i++) {
                spawnParticle(flameParticles,
                        new Vec3d(pos.x + randomValue(-0.5, 0.5), pos.y + randomValue(-0.5, 0.5), pos.z + randomValue(-0.5, 0.5)),
                        new Vec3d(randomValue(-0.06, 0.06), randomValue(-0.06, 0.06), randomValue(-0.06, 0.06)));
            }
        }
    }

    private static boolean hasPlayerMoved() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        if (!positionInitialized) {
            lastPlayerX = mc.player.getX();
            lastPlayerY = mc.player.getY();
            lastPlayerZ = mc.player.getZ();
            positionInitialized = true;
            return false;
        }

        boolean moved = Math.abs(mc.player.getX() - lastPlayerX) > 0.001
                || Math.abs(mc.player.getY() - lastPlayerY) > 0.001
                || Math.abs(mc.player.getZ() - lastPlayerZ) > 0.001;

        if (moved) {
            lastPlayerX = mc.player.getX();
            lastPlayerY = mc.player.getY();
            lastPlayerZ = mc.player.getZ();
        }

        return moved;
    }

    private static void spawnParticle(List<Particle> list, Vec3d position, Vec3d velocity) {
        if (list.size() >= MAX_PARTICLES_PER_LIST) return;
        float particleSize = 0.05f + (getSizeSetting() * 0.2f);
        list.add(new Particle(getParticleType(), position.add(0.0, particleSize, 0.0), velocity, 0, false, particleSize, getSpeedSetting()));
    }

    private static void spawnParticleTotem(List<Particle> list, Vec3d position, Vec3d velocity, int color) {
        if (list.size() >= MAX_PARTICLES_PER_LIST) return;
        float particleSize = 0.05f + (getSizeSetting() * 0.2f);
        list.add(new Particle(getParticleType(), position.add(0.0, particleSize, 0.0), velocity, color, true, particleSize, 2.0));
    }

    private static void updateParticle(Particle p, double deltaTime, boolean rotateEnabled) {
        if (rotateEnabled) {
            p.rotation += (float) (p.rotSpeed * deltaTime);
            if (p.isCube) {
                p.rot3d = p.rot3d.add(p.rotSpeed3d.multiply(deltaTime * 60.0 * 0.02));
                p.rotSpeed3d = p.rotSpeed3d.multiply(Math.pow(0.995, deltaTime * 60.0));
            }
        }

        collide(p);
        p.velocity = p.velocity.multiply(Math.pow(0.999, deltaTime * 60.0));
        p.position = p.position.add(p.velocity.multiply(deltaTime * 60.0 * p.speedMultiplier));
    }

    private static void collide(Particle p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        if (isBlockSolid(p.position.x, p.position.y, p.position.z + p.velocity.z)) {
            p.velocity = new Vec3d(p.velocity.x, p.velocity.y, -p.velocity.z);
        }
        if (isBlockSolid(p.position.x, p.position.y + p.velocity.y, p.position.z)) {
            p.velocity = new Vec3d(p.velocity.x, -p.velocity.y, p.velocity.z);
        }
        if (isBlockSolid(p.position.x + p.velocity.x, p.position.y, p.position.z)) {
            p.velocity = new Vec3d(-p.velocity.x, p.velocity.y, p.velocity.z);
        }
    }

    private static boolean isBlockSolid(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        return !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty();
    }

    private static float calculateAlpha(long age, long fadeInTime, long fadeOutTime, long lifespan) {
        if (age < 0) return 0.0f;
        if (age < fadeInTime) {
            float progress = (float) age / (float) fadeInTime;
            return (float) (1.0 - Math.pow(1.0 - Math.min(1.0f, progress), 3.0));
        }
        if (age < fadeOutTime) {
            return 1.0f;
        }
        if (age < lifespan) {
            float progress = (float) (age - fadeOutTime) / (float) (lifespan - fadeOutTime);
            return (float) Math.pow(1.0 - Math.min(1.0f, progress), 3.0);
        }
        return 0.0f;
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Particles", false)) return;
        if (targetParticles.isEmpty() && worldParticles.isEmpty() && flameParticles.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        long nowNano = System.nanoTime();
        double deltaTime = (nowNano - lastUpdateTime) / 1_000_000_000.0;
        lastUpdateTime = nowNano;
        if (deltaTime > 0.1) deltaTime = 0.1;
        if (deltaTime <= 0.0) deltaTime = 0.016;

        boolean rotateEnabled = isRotationEnabled();

        // Update physics for all lists
        for (Particle p : targetParticles) updateParticle(p, deltaTime, rotateEnabled);
        for (Particle p : worldParticles) updateParticle(p, deltaTime, rotateEnabled);
        for (Particle p : flameParticles) updateParticle(p, deltaTime, rotateEnabled);

        Vec3d camPos = camera.getPos();
        Quaternionf cameraRotation = camera.getRotation();

        long now = System.currentTimeMillis();
        List<ParticleRenderData> visible = new ArrayList<>();

        collectVisible(targetParticles, visible, camPos, now, 400L, 600L, 1000L);
        collectVisible(worldParticles, visible, camPos, now, 800L, 1500L, 2000L);
        collectVisible(flameParticles, visible, camPos, now, 700L, 1200L, 2000L);

        if (visible.isEmpty()) return;

        boolean throughWalls = isThroughWallsEnabled();
        boolean glow = isGlowEnabled();

        // Setup render state with additive blending
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ZERO
        );
        if (throughWalls) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        // 1. GLOW PASS (Bloom texture, scale * 2.0, alpha * 0.15)
        if (glow) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, TEX_BLOOM);
            BufferBuilder glowBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (ParticleRenderData d : visible) {
                int glowAlpha = (int) (d.a * 0.15f);
                if (glowAlpha <= 0) continue;

                matrices.push();
                matrices.translate(d.pos.x, d.pos.y, d.pos.z);
                matrices.multiply(cameraRotation);
                if (rotateEnabled) {
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(d.particle.rotation));
                }
                matrices.scale(d.size * 2.0f, d.size * 2.0f, d.size * 2.0f);
                drawQuad(matrices.peek().getPositionMatrix(), glowBuf, d.r, d.g, d.b, glowAlpha);
                matrices.pop();
            }
            BuiltBuffer builtGlow = glowBuf.endNullable();
            if (builtGlow != null) {
                BufferRenderer.drawWithGlobalProgram(builtGlow);
            }
        }

        // 2. SPRITE PASS (Grouped by texture for minimal draw calls)
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        Map<Identifier, List<ParticleRenderData>> byTexture = new HashMap<>();
        List<ParticleRenderData> cubes = null;

        for (ParticleRenderData d : visible) {
            if (d.particle.isCube) {
                if (cubes == null) cubes = new ArrayList<>();
                cubes.add(d);
            } else {
                Identifier tex = d.particle.type.getTexture();
                if (tex != null) {
                    byTexture.computeIfAbsent(tex, k -> new ArrayList<>()).add(d);
                }
            }
        }

        for (Map.Entry<Identifier, List<ParticleRenderData>> entry : byTexture.entrySet()) {
            RenderSystem.setShaderTexture(0, entry.getKey());
            BufferBuilder spriteBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (ParticleRenderData d : entry.getValue()) {
                matrices.push();
                matrices.translate(d.pos.x, d.pos.y, d.pos.z);
                matrices.multiply(cameraRotation);
                if (rotateEnabled) {
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(d.particle.rotation));
                }
                matrices.scale(d.size, d.size, d.size);
                drawQuad(matrices.peek().getPositionMatrix(), spriteBuf, d.r, d.g, d.b, d.a);
                matrices.pop();
            }
            BuiltBuffer builtSprite = spriteBuf.endNullable();
            if (builtSprite != null) {
                BufferRenderer.drawWithGlobalProgram(builtSprite);
            }
        }

        // 3. CUBE PASS (Wireframe 3D cube)
        if (cubes != null && !cubes.isEmpty()) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder cubeBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (ParticleRenderData d : cubes) {
                matrices.push();
                matrices.translate(d.pos.x, d.pos.y, d.pos.z);
                matrices.multiply(new Quaternionf().rotationXYZ((float) d.particle.rot3d.x, (float) d.particle.rot3d.y, (float) d.particle.rot3d.z));
                matrices.scale(d.size * 2.0f, d.size * 2.0f, d.size * 2.0f);
                drawWireCube(matrices, cubeBuf, d.r, d.g, d.b, d.a);
                matrices.pop();
            }
            BuiltBuffer builtCube = cubeBuf.endNullable();
            if (builtCube != null) {
                BufferRenderer.drawWithGlobalProgram(builtCube);
            }
        }

        matrices.pop();

        // Reset state
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ZERO
        );
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void collectVisible(List<Particle> list, List<ParticleRenderData> visible,
                                       Vec3d camPos, long now, long fadeIn, long fadeOut, long lifespan) {
        for (Particle p : list) {
            if (p.position.squaredDistanceTo(camPos) > MAX_RENDER_DISTANCE_SQ) continue;

            long age = now - p.creationTime;
            if (age < 0 || age >= lifespan) continue;

            float alpha = calculateAlpha(age, fadeIn, fadeOut, lifespan);
            int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
            if (a <= 0) continue;

            float lifePct = (float) age / (float) lifespan;
            int[] rgb = computeColorRGB(p, lifePct);

            visible.add(new ParticleRenderData(p, p.position, rgb[0], rgb[1], rgb[2], a, p.size));
        }
    }

    private static void drawQuad(Matrix4f mat, BufferBuilder buffer, int r, int g, int b, int a) {
        buffer.vertex(mat, -1, -1, 0).color(r, g, b, a).texture(0, 1);
        buffer.vertex(mat, -1, 1, 0).color(r, g, b, a).texture(0, 0);
        buffer.vertex(mat, 1, 1, 0).color(r, g, b, a).texture(1, 0);
        buffer.vertex(mat, 1, -1, 0).color(r, g, b, a).texture(1, 1);
    }

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

    private static int[] computeColorRGB(Particle p, float lifePct) {
        if (p.isTotem) {
            int rgb = p.fixedColor;
            return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        }

        String colorMode = LexoraGui.modeSettings.getOrDefault("Part. Color Mode", "Theme");
        if (colorMode.equals("Custom")) {
            String colorType = LexoraGui.modeSettings.getOrDefault("Part. Color Type", "Solid");
            if (colorType.equals("Gradient")) {
                float[] c1 = LexoraGui.colorSettings.getOrDefault("Part. Custom Color 1", new float[]{0, 1f, 1f});
                float[] c2 = LexoraGui.colorSettings.getOrDefault("Part. Custom Color 2", new float[]{0.5f, 1f, 1f});
                int rgb1 = Color.HSBtoRGB(c1[0], c1[1], c1[2]);
                int rgb2 = Color.HSBtoRGB(c2[0], c2[1], c2[2]);

                int r = (int) lerp((rgb1 >> 16) & 0xFF, (rgb2 >> 16) & 0xFF, lifePct);
                int g = (int) lerp((rgb1 >> 8) & 0xFF, (rgb2 >> 8) & 0xFF, lifePct);
                int b = (int) lerp(rgb1 & 0xFF, rgb2 & 0xFF, lifePct);
                return new int[]{r, g, b};
            } else {
                float[] c = LexoraGui.colorSettings.getOrDefault("Part. Custom Color", new float[]{0, 1f, 1f});
                int rgb = Color.HSBtoRGB(c[0], c[1], c[2]);
                return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
            }
        } else {
            int guiColor = LexoraGui.getThemeColor(p.colorOffset - ((System.currentTimeMillis() - p.creationTime) * 0.001f));
            return new int[]{(guiColor >> 16) & 0xFF, (guiColor >> 8) & 0xFF, guiColor & 0xFF};
        }
    }

    private static ParticleType getParticleType() {
        String mode = LexoraGui.modeSettings.getOrDefault("Part. Texture", "Bloom");
        return switch (mode) {
            case "Star" -> ParticleType.STAR;
            case "Heart" -> ParticleType.HEART;
            case "Dollar", "Bucks" -> ParticleType.DOLLAR;
            case "Snow", "Show" -> ParticleType.SNOW;
            case "Star 2", "Core" -> ParticleType.STARNEW;
            case "Kronex", "Skull" -> ParticleType.KRONEX;
            case "Cube" -> ParticleType.CUBE;
            case "Random" -> ParticleType.getRandom();
            default -> ParticleType.BLOOM;
        };
    }

    private static boolean isAttackEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Part. Attack",
                LexoraGui.moduleStates.getOrDefault("Part. Hit", true));
    }

    private static boolean isTotemEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Part. Totem", true);
    }

    private static boolean isMoveEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Part. Move",
                LexoraGui.moduleStates.getOrDefault("Part. Walk", false));
    }

    private static boolean isThrowEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Part. Throw",
                LexoraGui.moduleStates.getOrDefault("Part. Projectiles", true));
    }

    private static boolean isIdleEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Part. Idle",
                LexoraGui.moduleStates.getOrDefault("Part. Ambient", false));
    }

    private static boolean isRotationEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Part. Rotation", false);
    }

    private static boolean isGlowEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Part. Glow", true);
    }

    private static boolean isThroughWallsEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Part. Through Walls", false);
    }

    private static boolean isStrongY() {
        return LexoraGui.moduleStates.getOrDefault("Part. Strong Y", false);
    }

    private static int getAttackCount() {
        return (int) LexoraGui.numSettings.getOrDefault("Part. Attack Count",
                LexoraGui.numSettings.getOrDefault("Attack Count",
                        LexoraGui.numSettings.getOrDefault("Hit Count", 30.0f))).floatValue();
    }

    private static int getTotemCount() {
        return (int) LexoraGui.numSettings.getOrDefault("Part. Totem Count",
                LexoraGui.numSettings.getOrDefault("Totem Count", 8.0f)).floatValue();
    }

    private static int getMoveCount() {
        return (int) LexoraGui.numSettings.getOrDefault("Part. Move Count",
                LexoraGui.numSettings.getOrDefault("Move Count",
                        LexoraGui.numSettings.getOrDefault("Walk Count", 2.0f))).floatValue();
    }

    private static int getThrowCount() {
        return (int) LexoraGui.numSettings.getOrDefault("Part. Throw Count",
                LexoraGui.numSettings.getOrDefault("Throw Count",
                        LexoraGui.numSettings.getOrDefault("Proj Count", 6.0f))).floatValue();
    }

    private static int getIdleCount() {
        return (int) LexoraGui.numSettings.getOrDefault("Part. Idle Count",
                LexoraGui.numSettings.getOrDefault("Idle Count",
                        LexoraGui.numSettings.getOrDefault("Amb Chance", 5.0f))).floatValue();
    }

    private static int getIdleRange() {
        return (int) LexoraGui.numSettings.getOrDefault("Part. Idle Range",
                LexoraGui.numSettings.getOrDefault("Idle Range", 16.0f)).floatValue();
    }

    private static float getSpeedSetting() {
        return LexoraGui.numSettings.getOrDefault("Part. Speed",
                LexoraGui.numSettings.getOrDefault("Speed", 1.5f));
    }

    private static float getSizeSetting() {
        return LexoraGui.numSettings.getOrDefault("Part. Size",
                LexoraGui.numSettings.getOrDefault("Size", 0.3f));
    }

    private static double randomValue(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
