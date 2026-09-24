package com.lexoravisauls.client.modules.weather;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.*;

public class WeatherFX {
    private static final double GRAVITY = 22.0;
    private static final double TERMINAL_VELOCITY = 34.0;
    private static final int RIPPLE_SEGMENTS = 14;
    private static final int MAX_DROPS = 3500;
    private static final int MAX_MIST = 90;
    private static final int MAX_SNOWFLAKES = 2500;

    private static final List<Drop> drops = new ArrayList<>();
    private static final List<Splash> splashList = new ArrayList<>();
    private static final List<Droplet> dropletList = new ArrayList<>();
    private static final List<Mist> mistList = new ArrayList<>();
    private static final Map<Long, Integer> groundCache = new HashMap<>();
    private static final BlockPos.Mutable scratchPos = new BlockPos.Mutable();
    private static final Random random = new Random();

    private static long lastFrameNanos = 0L;
    private static float clock = 0.0f;
    private static float flash = 0.0f;
    private static float nextFlashIn = 9.0f;
    private static int cacheTicks = 0;

    public static boolean isModuleEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Atmosphere",
                LexoraGui.moduleStates.getOrDefault("WeatherFX", false));
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !isModuleEnabled()) {
            clearAll();
            return;
        }

        String mode = getNormalizedMode();
        if (!mode.equals("Rain")) {
            if (!drops.isEmpty() || !splashList.isEmpty() || !dropletList.isEmpty() || !mistList.isEmpty()) {
                clearRain();
            }
        }

        if (++cacheTicks > 30) {
            cacheTicks = 0;
            groundCache.clear();
        }
    }

    public static void clearAll() {
        clearRain();
        flash = 0.0f;
        lastFrameNanos = 0L;
    }

    private static void clearRain() {
        synchronized (drops) {
            drops.clear();
        }
        splashList.clear();
        dropletList.clear();
        mistList.clear();
        groundCache.clear();
    }

    private static String getNormalizedMode() {
        String mode = LexoraGui.modeSettings.getOrDefault("Weather Visual Mode", "Rain");
        if (!mode.equals("Wet Floor") && !mode.equals("Rain")) {
            mode = "Rain";
            LexoraGui.modeSettings.put("Weather Visual Mode", "Rain");
        }
        return mode;
    }

    public static void render(MatrixStack matrices, Camera camera, Matrix4f projectionMatrix, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || camera == null) return;
        if (!isModuleEnabled()) {
            clearAll();
            return;
        }

        boolean rainOnly = LexoraGui.moduleStates.getOrDefault("Weather Rain Only", false);
        if (rainOnly && !mc.world.isRaining()) {
            clearAll();
            return;
        }

        String mode = getNormalizedMode();

        // 1. Wet Floor Render Pass (SSR reflections and wet darkening on blocks)
        if (mode.equals("Wet Floor")) {
            clearRain();

            try {
                WetSurfaceRenderer.Parameters params = new WetSurfaceRenderer.Parameters();
                params.reflectionStrength = LexoraGui.numSettings.getOrDefault("Wet Strength", 0.85f);
                params.darkening = LexoraGui.numSettings.getOrDefault("Wet Darkening", 0.30f);
                String quality = LexoraGui.modeSettings.getOrDefault("Wet Quality", "Balanced");
                params.qualityIdx = quality.equals("Ultra") ? 3 : (quality.equals("High") ? 2 : (quality.equals("Performance") ? 0 : 1));
                params.ripples = LexoraGui.moduleStates.getOrDefault("Wet Ripples", false);
                params.rippleSpeed = LexoraGui.numSettings.getOrDefault("Ripple Speed", 1.0f);

                WetSurfaceRenderer.getInstance().apply(mc, camera, projectionMatrix, params);
            } catch (Throwable t) {
                // Prevent any render pass exception from killing client
            }
            return;
        }

        // 2. Volumetric Rain Render Pass (falling rain, splashes, mist, lightning)
        if (mode.equals("Rain")) {
            renderRain(matrices, camera, tickDelta);
        }
    }

    private static void renderRain(MatrixStack matrices, Camera camera, float tickDelta) {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        dt = MathHelper.clamp(dt, 0.0f, 0.05f);
        clock += dt;

        Vec3d cam = camera.getPos();

        updateDrops(cam, dt);
        updateSplashes(dt);
        updateDroplets(dt);
        updateMist(cam, dt);
        updateLightning(dt);

        if (drops.isEmpty() && splashList.isEmpty() && dropletList.isEmpty() && mistList.isEmpty()) return;

        float[] rgb = resolveColor();
        float ambient = ambientLight(camera);
        float brightnessMul = MathHelper.clamp(ambient + flash * 0.9f, 0.0f, 1.65f);
        float alphaMul = LexoraGui.numSettings.getOrDefault("Rain Opacity", 1.0f) * brightnessMul;

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        if (LexoraGui.moduleStates.getOrDefault("Rain Mist", true)) drawMist(matrices, camera, cam, rgb, alphaMul);
        drawStreaks(matrices, camera, cam, rgb, alphaMul, false);
        if (LexoraGui.moduleStates.getOrDefault("Rain Splashes", true)) drawRipples(matrices, cam, rgb, alphaMul);

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        drawStreaks(matrices, camera, cam, rgb, alphaMul, true);
        if (LexoraGui.moduleStates.getOrDefault("Rain Splashes", true)) {
            drawCrowns(matrices, camera, cam, rgb, alphaMul);
            if (LexoraGui.moduleStates.getOrDefault("Rain Droplets", true)) drawDroplets(matrices, camera, cam, rgb, alphaMul);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void drawStreaks(MatrixStack matrices, Camera camera, Vec3d cam, float[] rgb, float alphaMul, boolean highlight) {
        if (drops.isEmpty()) return;

        BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float sizeMul = LexoraGui.numSettings.getOrDefault("Rain Drop Size", 1.0f);
        float r = LexoraGui.numSettings.getOrDefault("Rain Radius", 26.0f);
        float fadeStart = r * 0.62f;

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        for (int i = 0, size = drops.size(); i < size; i++) {
            Drop d = drops.get(i);

            double dx = d.x - cam.x;
            double dy = d.y - cam.y;
            double dz = d.z - cam.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            double dist = Math.sqrt(distSq);
            if (dist > r + 4.0) continue;

            float distanceFade = dist <= fadeStart ? 1.0f
                    : 1.0f - MathHelper.clamp((float) ((dist - fadeStart) / (r - fadeStart + 1.0f)), 0.0f, 1.0f);
            if (distanceFade <= 0.02f) continue;

            float nearFade = dist < 0.8 ? (float) (dist / 0.8) : 1.0f;

            double speed = Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz);
            if (speed < 0.0001) continue;

            float length = (float) MathHelper.clamp(speed * 0.055 * sizeMul, 0.18, 2.4);
            float width = (float) (0.014 + 0.018 * d.mass) * sizeMul * (highlight ? 0.45f : 1.0f);

            float alpha = alphaMul * d.brightness * distanceFade * nearFade * (highlight ? 0.32f : 0.45f);
            if (alpha <= 0.006f) continue;
            alpha = Math.min(alpha, 0.95f);

            float yawRad = (float) Math.toRadians(camera.getYaw());
            double rightX = Math.cos(yawRad);
            double rightZ = Math.sin(yawRad);
            double horizontal = d.vx * rightX + d.vz * rightZ;
            float tilt = (float) Math.toDegrees(Math.atan2(horizontal, Math.abs(d.vy) + 0.0001));
            tilt = MathHelper.clamp(tilt, -45.0f, 45.0f);

            matrices.push();
            matrices.translate((float) d.x, (float) d.y, (float) d.z);
            matrices.multiply(camera.getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-tilt));

            Matrix4f m = matrices.peek().getPositionMatrix();

            float half = width * 0.5f;
            float top = length * 0.5f;
            float bottom = -length * 0.5f;

            float headAlpha = alpha;
            float tailAlpha = 0.0f;
            float headWidth = half;
            float tailWidth = half * 0.35f;

            buffer.vertex(m, -tailWidth, top, 0.0f).color(rgb[0], rgb[1], rgb[2], tailAlpha);
            buffer.vertex(m, tailWidth, top, 0.0f).color(rgb[0], rgb[1], rgb[2], tailAlpha);
            buffer.vertex(m, headWidth, bottom, 0.0f).color(rgb[0], rgb[1], rgb[2], headAlpha);
            buffer.vertex(m, -headWidth, bottom, 0.0f).color(rgb[0], rgb[1], rgb[2], headAlpha);

            if (!highlight) {
                float halo = half * 2.5f;
                float haloAlpha = alpha * 0.20f;
                buffer.vertex(m, -halo * 0.4f, top, 0.0f).color(rgb[0], rgb[1], rgb[2], 0.0f);
                buffer.vertex(m, halo * 0.4f, top, 0.0f).color(rgb[0], rgb[1], rgb[2], 0.0f);
                buffer.vertex(m, halo, bottom, 0.0f).color(rgb[0], rgb[1], rgb[2], haloAlpha);
                buffer.vertex(m, -halo, bottom, 0.0f).color(rgb[0], rgb[1], rgb[2], haloAlpha);
            } else {
                float lensTop = bottom + length * 0.35f;
                float lensWidth = half * 1.15f;
                buffer.vertex(m, -lensWidth, lensTop, 0.0f).color(1.0f, 1.0f, 1.0f, 0.0f);
                buffer.vertex(m, lensWidth, lensTop, 0.0f).color(1.0f, 1.0f, 1.0f, 0.0f);
                buffer.vertex(m, lensWidth, bottom, 0.0f).color(1.0f, 1.0f, 1.0f, alpha * 0.85f);
                buffer.vertex(m, -lensWidth, bottom, 0.0f).color(1.0f, 1.0f, 1.0f, alpha * 0.85f);
            }

            matrices.pop();
        }

        matrices.pop();

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }

    private static void drawRipples(MatrixStack matrices, Vec3d cam, float[] rgb, float alphaMul) {
        if (splashList.isEmpty()) return;

        BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        for (int i = 0, size = splashList.size(); i < size; i++) {
            Splash s = splashList.get(i);
            float progress = MathHelper.clamp(s.life / s.maxLife, 0.0f, 1.0f);
            float eased = 1.0f - (1.0f - progress) * (1.0f - progress);

            float outer = s.scale * (0.18f + eased * (s.water ? 1.25f : 0.80f));
            float inner = outer * (0.55f + 0.35f * eased);
            float alpha = alphaMul * (1.0f - progress) * (s.water ? 0.45f : 0.32f);
            if (alpha <= 0.01f) continue;

            matrices.push();
            matrices.translate((float) s.x, (float) s.y + 0.005f, (float) s.z);
            Matrix4f m = matrices.peek().getPositionMatrix();

            for (int seg = 0; seg < RIPPLE_SEGMENTS; seg++) {
                double a0 = (Math.PI * 2.0 / RIPPLE_SEGMENTS) * seg;
                double a1 = (Math.PI * 2.0 / RIPPLE_SEGMENTS) * (seg + 1);

                float ix0 = (float) (Math.cos(a0) * inner);
                float iz0 = (float) (Math.sin(a0) * inner);
                float ix1 = (float) (Math.cos(a1) * inner);
                float iz1 = (float) (Math.sin(a1) * inner);
                float ox0 = (float) (Math.cos(a0) * outer);
                float oz0 = (float) (Math.sin(a0) * outer);
                float ox1 = (float) (Math.cos(a1) * outer);
                float oz1 = (float) (Math.sin(a1) * outer);

                buffer.vertex(m, ix0, 0.0f, iz0).color(rgb[0], rgb[1], rgb[2], alpha);
                buffer.vertex(m, ix1, 0.0f, iz1).color(rgb[0], rgb[1], rgb[2], alpha);
                buffer.vertex(m, ox1, 0.0f, oz1).color(rgb[0], rgb[1], rgb[2], 0.0f);
                buffer.vertex(m, ox0, 0.0f, oz0).color(rgb[0], rgb[1], rgb[2], 0.0f);
            }

            matrices.pop();
        }

        matrices.pop();

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }

    private static void drawCrowns(MatrixStack matrices, Camera camera, Vec3d cam, float[] rgb, float alphaMul) {
        if (splashList.isEmpty()) return;

        BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        for (int i = 0, size = splashList.size(); i < size; i++) {
            Splash s = splashList.get(i);
            float progress = MathHelper.clamp(s.life / s.maxLife, 0.0f, 1.0f);
            if (progress > 0.55f) continue;

            float local = progress / 0.55f;
            float height = s.scale * (0.45f * (float) Math.sin(local * Math.PI));
            float alpha = alphaMul * (1.0f - local) * 0.30f;
            if (alpha <= 0.01f || height <= 0.005f) continue;

            matrices.push();
            matrices.translate((float) s.x, (float) s.y, (float) s.z);
            matrices.multiply(camera.getRotation());
            Matrix4f m = matrices.peek().getPositionMatrix();

            float base = s.scale * 0.15f;
            buffer.vertex(m, -base * 0.25f, height, 0.0f).color(rgb[0], rgb[1], rgb[2], 0.0f);
            buffer.vertex(m, base * 0.25f, height, 0.0f).color(rgb[0], rgb[1], rgb[2], 0.0f);
            buffer.vertex(m, base, 0.0f, 0.0f).color(rgb[0], rgb[1], rgb[2], alpha);
            buffer.vertex(m, -base, 0.0f, 0.0f).color(rgb[0], rgb[1], rgb[2], alpha);

            matrices.pop();
        }

        matrices.pop();

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }

    private static void drawDroplets(MatrixStack matrices, Camera camera, Vec3d cam, float[] rgb, float alphaMul) {
        if (dropletList.isEmpty()) return;

        BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float sizeMul = LexoraGui.numSettings.getOrDefault("Rain Drop Size", 1.0f);

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        for (int i = 0, size = dropletList.size(); i < size; i++) {
            Droplet d = dropletList.get(i);
            float progress = MathHelper.clamp(d.life / d.maxLife, 0.0f, 1.0f);
            float alpha = alphaMul * (1.0f - progress) * 0.5f;
            if (alpha <= 0.01f) continue;

            double speed = Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz);
            float length = (float) MathHelper.clamp(speed * 0.025 * sizeMul, 0.03, 0.35);
            float width = 0.015f * sizeMul;

            float yawRad = (float) Math.toRadians(camera.getYaw());
            double horizontal = d.vx * Math.cos(yawRad) + d.vz * Math.sin(yawRad);
            float tilt = (float) Math.toDegrees(Math.atan2(horizontal, -d.vy + 0.0001));

            matrices.push();
            matrices.translate((float) d.x, (float) d.y, (float) d.z);
            matrices.multiply(camera.getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-tilt));
            Matrix4f m = matrices.peek().getPositionMatrix();

            buffer.vertex(m, -width * 0.35f, length * 0.5f, 0.0f).color(rgb[0], rgb[1], rgb[2], 0.0f);
            buffer.vertex(m, width * 0.35f, length * 0.5f, 0.0f).color(rgb[0], rgb[1], rgb[2], 0.0f);
            buffer.vertex(m, width, -length * 0.5f, 0.0f).color(rgb[0], rgb[1], rgb[2], alpha);
            buffer.vertex(m, -width, -length * 0.5f, 0.0f).color(rgb[0], rgb[1], rgb[2], alpha);

            matrices.pop();
        }

        matrices.pop();

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }

    private static void drawMist(MatrixStack matrices, Camera camera, Vec3d cam, float[] rgb, float alphaMul) {
        if (mistList.isEmpty()) return;

        BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        for (int i = 0, size = mistList.size(); i < size; i++) {
            Mist mist = mistList.get(i);
            float progress = MathHelper.clamp(mist.life / mist.maxLife, 0.0f, 1.0f);
            float fade = (float) Math.sin(progress * Math.PI);
            float alpha = alphaMul * fade * 0.05f;
            if (alpha <= 0.004f) continue;

            matrices.push();
            matrices.translate((float) mist.x, (float) mist.y, (float) mist.z);
            matrices.multiply(camera.getRotation());
            Matrix4f m = matrices.peek().getPositionMatrix();

            float half = mist.size * 0.5f;
            float top = mist.size * 0.40f;

            buffer.vertex(m, -half, top, 0.0f).color(rgb[0], rgb[1], rgb[2], 0.0f);
            buffer.vertex(m, half, top, 0.0f).color(rgb[0], rgb[1], rgb[2], 0.0f);
            buffer.vertex(m, half, 0.0f, 0.0f).color(rgb[0], rgb[1], rgb[2], alpha);
            buffer.vertex(m, -half, 0.0f, 0.0f).color(rgb[0], rgb[1], rgb[2], alpha);

            matrices.pop();
        }

        matrices.pop();

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }

    private static void updateDrops(Vec3d center, float dt) {
        int target = targetDropCount();
        float r = LexoraGui.numSettings.getOrDefault("Rain Radius", 26.0f);
        float alt = LexoraGui.numSettings.getOrDefault("Rain Altitude", 16.0f);

        drops.removeIf(d -> {
            double dx = d.x - center.x;
            double dz = d.z - center.z;
            return d.dead || dx * dx + dz * dz > (r + 6.0f) * (r + 6.0f) || d.y < center.y - alt - 12.0;
        });

        boolean fill = drops.size() < target / 2;
        int spawnBudget = fill ? Math.min(target - drops.size(), 900) : Math.min(target - drops.size(), 260);
        for (int i = 0; i < spawnBudget; i++) {
            spawnDrop(center, !fill);
        }

        float speedMul = LexoraGui.numSettings.getOrDefault("Rain Fall Speed", 1.0f);
        float windMul = LexoraGui.numSettings.getOrDefault("Rain Wind", 1.0f) * presetWind();
        boolean doSplashes = LexoraGui.moduleStates.getOrDefault("Rain Splashes", true);

        for (int i = 0, size = drops.size(); i < size; i++) {
            Drop d = drops.get(i);

            double gust = 0.65 + 0.35 * Math.sin(clock * 0.55 + d.phase * 0.15)
                    + 0.18 * Math.sin(clock * 1.7 + d.phase);
            double windX = windMul * 3.1 * gust;
            double windZ = windMul * 1.7 * Math.sin(clock * 0.31 + d.phase * 0.05) * gust;

            double drag = 1.0 - Math.min(0.92, Math.abs(d.vy) / (TERMINAL_VELOCITY * d.mass));
            d.vy -= GRAVITY * d.mass * drag * dt;
            if (d.vy < -TERMINAL_VELOCITY * d.mass) d.vy = -TERMINAL_VELOCITY * d.mass;

            d.vx += (windX - d.vx) * Math.min(1.0, dt * 3.2);
            d.vz += (windZ - d.vz) * Math.min(1.0, dt * 3.2);

            double nx = d.x + d.vx * dt * speedMul;
            double ny = d.y + d.vy * dt * speedMul;
            double nz = d.z + d.vz * dt * speedMul;

            d.prevX = d.x;
            d.prevY = d.y;
            d.prevZ = d.z;

            boolean hit = false;
            if (ny <= d.ground + 2.5) {
                if (isBlocking(nx, ny, nz)) {
                    hit = true;
                } else if (ny <= d.ground - 3.0) {
                    d.dead = true;
                }
            }

            if (hit) {
                if (doSplashes) addSplash(nx, ny, nz, d);
                d.dead = true;
                continue;
            }

            d.x = nx;
            d.y = ny;
            d.z = nz;
        }
    }

    private static void spawnDrop(Vec3d center, boolean fromTop) {
        float r = LexoraGui.numSettings.getOrDefault("Rain Radius", 26.0f);
        float alt = LexoraGui.numSettings.getOrDefault("Rain Altitude", 16.0f);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(random.nextDouble()) * r;

        double x = center.x + Math.cos(angle) * dist;
        double z = center.z + Math.sin(angle) * dist;
        double y = fromTop
                ? center.y + alt * random(0.75f, 1.0f)
                : center.y + random(-4.0f, alt);

        if (!skyVisible(x, y, z)) return;

        Drop drop = new Drop();
        drop.x = x;
        drop.y = y;
        drop.z = z;
        drop.mass = random(0.6f, 1.4f) * presetMass();
        drop.vy = -random(6.0f, 11.0f) * drop.mass;
        drop.phase = random(0.0f, 62.8f);
        drop.brightness = random(0.55f, 1.0f);
        drop.ground = surfaceY(MathHelper.floor(x), MathHelper.floor(z));
        drops.add(drop);
    }

    private static void addSplash(double x, double y, double z, Drop d) {
        if (splashList.size() > 700) return;

        double impactY = Math.floor(y) + 1.0005;
        Splash splash = new Splash();
        splash.x = x;
        splash.y = impactY;
        splash.z = z;
        splash.life = 0.0f;
        splash.maxLife = random(0.30f, 0.52f);
        splash.scale = (float) (0.32 + 0.22 * d.mass);
        splash.water = isWater(x, y - 0.15, z);
        splashList.add(splash);

        if (!LexoraGui.moduleStates.getOrDefault("Rain Droplets", true) || dropletList.size() > 1400) return;

        int shards = splash.water ? 4 : 3;
        for (int i = 0; i < shards; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double sp = random(1.1f, 2.6f) * d.mass;
            Droplet drop = new Droplet();
            drop.x = x;
            drop.y = impactY + 0.02;
            drop.z = z;
            drop.vx = Math.cos(a) * sp * 0.45 + d.vx * 0.08;
            drop.vz = Math.sin(a) * sp * 0.45 + d.vz * 0.08;
            drop.vy = random(1.9f, 4.1f);
            drop.maxLife = random(0.28f, 0.5f);
            dropletList.add(drop);
        }
    }

    private static void updateSplashes(float dt) {
        for (int i = splashList.size() - 1; i >= 0; i--) {
            Splash s = splashList.get(i);
            s.life += dt;
            if (s.life >= s.maxLife) splashList.remove(i);
        }
    }

    private static void updateDroplets(float dt) {
        for (int i = dropletList.size() - 1; i >= 0; i--) {
            Droplet dr = dropletList.get(i);
            dr.life += dt;
            if (dr.life >= dr.maxLife) {
                dropletList.remove(i);
                continue;
            }
            dr.prevX = dr.x;
            dr.prevY = dr.y;
            dr.prevZ = dr.z;
            dr.vy -= GRAVITY * 0.55 * dt;
            dr.x += dr.vx * dt;
            dr.y += dr.vy * dt;
            dr.z += dr.vz * dt;
        }
    }

    private static void updateMist(Vec3d center, float dt) {
        if (!LexoraGui.moduleStates.getOrDefault("Rain Mist", true)) {
            mistList.clear();
            return;
        }

        float r = LexoraGui.numSettings.getOrDefault("Rain Radius", 26.0f);
        float windVal = LexoraGui.numSettings.getOrDefault("Rain Wind", 1.0f);
        int target = (int) MathHelper.clamp(22 * getDensity() * presetDensity(), 6, MAX_MIST);

        mistList.removeIf(m -> {
            m.life += dt;
            double dx = m.x - center.x;
            double dz = m.z - center.z;
            m.x += m.vx * dt;
            m.z += m.vz * dt;
            return m.life > m.maxLife || dx * dx + dz * dz > r * r;
        });

        while (mistList.size() < target) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(random.nextDouble()) * r * 0.85;
            double x = center.x + Math.cos(angle) * dist;
            double z = center.z + Math.sin(angle) * dist;
            int ground = surfaceY(MathHelper.floor(x), MathHelper.floor(z));

            Mist m = new Mist();
            m.x = x;
            m.z = z;
            m.y = ground + random(0.05f, 0.9f);
            m.size = random(2.2f, 5.6f);
            m.maxLife = random(2.4f, 5.5f);
            m.life = random(0.0f, 1.0f);
            m.vx = random(-0.35f, 0.35f) * windVal;
            m.vz = random(-0.35f, 0.35f) * windVal;
            mistList.add(m);
        }
    }

    private static void updateLightning(float dt) {
        if (!LexoraGui.moduleStates.getOrDefault("Rain Lightning", false)) {
            flash = 0.0f;
            return;
        }
        flash = Math.max(0.0f, flash - dt * 3.4f);
        nextFlashIn -= dt;
        if (nextFlashIn <= 0.0f) {
            flash = random(0.55f, 1.0f);
            nextFlashIn = random(6.0f, 16.0f);
        }
    }

    private static int surfaceY(int x, int z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 64;
        long key = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
        Integer cached = groundCache.get(key);
        if (cached != null) return cached;

        int top;
        try {
            top = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        } catch (Throwable ignored) {
            top = mc.world.getBottomY();
        }
        if (groundCache.size() < 20000) groundCache.put(key, top);
        return top;
    }

    private static boolean isBlocking(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        scratchPos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        if (!mc.world.getChunkManager().isChunkLoaded(scratchPos.getX() >> 4, scratchPos.getZ() >> 4)) return false;

        BlockState state = mc.world.getBlockState(scratchPos);
        if (!state.getFluidState().isEmpty()) return true;
        if (state.isAir()) return false;
        try {
            return !state.getCollisionShape(mc.world, scratchPos).isEmpty();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean isWater(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        scratchPos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        try {
            return !mc.world.getBlockState(scratchPos).getFluidState().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean skyVisible(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!LexoraGui.moduleStates.getOrDefault("Rain Sky Check", true) || mc.world == null) return true;
        scratchPos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        try {
            return mc.world.isSkyVisible(scratchPos);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static float getDensity() {
        return LexoraGui.numSettings.getOrDefault("Rain Density", 1.0f);
    }

    private static float presetDensity() {
        String preset = LexoraGui.modeSettings.getOrDefault("Rain Preset", "Rain");
        if (preset.equals("Drizzle")) return 0.45f;
        if (preset.equals("Downpour")) return 1.9f;
        if (preset.equals("Storm")) return 2.8f;
        return 1.0f;
    }

    private static float presetWind() {
        String preset = LexoraGui.modeSettings.getOrDefault("Rain Preset", "Rain");
        if (preset.equals("Drizzle")) return 0.5f;
        if (preset.equals("Downpour")) return 1.35f;
        if (preset.equals("Storm")) return 2.4f;
        return 1.0f;
    }

    private static float presetMass() {
        String preset = LexoraGui.modeSettings.getOrDefault("Rain Preset", "Rain");
        if (preset.equals("Drizzle")) return 0.55f;
        if (preset.equals("Downpour")) return 1.25f;
        if (preset.equals("Storm")) return 1.5f;
        return 1.0f;
    }

    private static int targetDropCount() {
        float r = LexoraGui.numSettings.getOrDefault("Rain Radius", 26.0f);
        float area = r * r * 0.0155f;
        int count = (int) (area * getDensity() * presetDensity() * 26.0f);
        return MathHelper.clamp(count, 40, MAX_DROPS);
    }

    private static float[] resolveColor() {
        String colorMode = LexoraGui.modeSettings.getOrDefault("Rain Color Mode", "Realistic");
        if (colorMode.equals("Custom")) {
            float[] c = LexoraGui.colorSettings.getOrDefault("Rain Custom Color", new float[]{0.6f, 0.75f, 0.9f});
            int rgb = Color.HSBtoRGB(c[0], c[1], c[2]);
            return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
        } else if (colorMode.equals("Client")) {
            int rgb = LexoraGui.getThemeColor(0f);
            return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
        } else {
            return new float[]{200f / 255f, 220f / 255f, 232f / 255f};
        }
    }

    private static float ambientLight(Camera camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 1.0f;
        try {
            int light = mc.world.getLightLevel(camera.getBlockPos());
            return MathHelper.clamp(light / 15.0f, 0.3f, 1.0f);
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    private static float random(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private static final class Drop {
        double x, y, z;
        double prevX, prevY, prevZ;
        double vx, vy, vz;
        double mass = 1.0;
        double ground;
        float phase;
        float brightness = 1.0f;
        boolean dead;
    }

    private static final class Droplet {
        double x, y, z;
        double prevX, prevY, prevZ;
        double vx, vy, vz;
        float life;
        float maxLife = 0.4f;
    }

    private static final class Splash {
        double x, y, z;
        float life;
        float maxLife = 0.4f;
        float scale = 0.4f;
        boolean water;
    }

    private static final class Mist {
        double x, y, z;
        double vx, vz;
        float size = 3.0f;
        float life;
        float maxLife = 4.0f;
    }
}
