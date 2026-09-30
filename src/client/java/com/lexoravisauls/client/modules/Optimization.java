package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Optimization {

    // ── СОСТОЯНИЕ МОДУЛЯ ─────────────────────────────────────────────────────
    public static boolean isEnabled() {
        return ClientData.moduleStates.getOrDefault("Optimization", false)
                || LexoraGui.moduleStates.getOrDefault("Optimization", false);
    }

    public static boolean getSetting(String key, boolean def) {
        if (!isEnabled()) return false;
        return ClientData.moduleStates.getOrDefault(key,
                LexoraGui.moduleStates.getOrDefault(key, def));
    }

    public static float getNum(String key, float def) {
        if (!isEnabled()) return def;
        return ClientData.numSettings.getOrDefault(key,
                LexoraGui.numSettings.getOrDefault(key, def));
    }

    // ── КАДРЫ, ТАЙМИНГИ И ДИНАМИЧЕСКОЕ КАЧЕСТВО (FRAME SYNC & FPS) ───────────
    private static final int SAMPLES = 60;
    private static final long[] frameTimes = new long[SAMPLES];
    private static int frameIndex = 0;
    private static int frameCount = 0;
    private static int currentFps = 60;
    private static float qualityLevel = 1.0F;

    public static boolean isFrameSync() {
        return getSetting("Opt Frame Sync", true);
    }

    public static void sampleFrame() {
        if (!isEnabled() || !isFrameSync()) {
            currentFps = 60;
            qualityLevel = 1.0F;
            return;
        }
        long now = System.nanoTime();
        frameTimes[frameIndex] = now;
        frameIndex = (frameIndex + 1) % SAMPLES;
        if (frameCount < SAMPLES) {
            frameCount++;
        }
        if (frameCount < 2) {
            currentFps = 60;
            qualityLevel = 1.0F;
            return;
        }
        int newest = (frameIndex - 1 + SAMPLES) % SAMPLES;
        int oldest = frameCount < SAMPLES ? 0 : frameIndex;
        long dt = frameTimes[newest] - frameTimes[oldest];
        if (dt > 0L) {
            currentFps = (int) Math.round((frameCount - 1) * 1_000_000_000.0D / dt);
        }
        if (currentFps >= 55) {
            qualityLevel = 1.0F;
        } else if (currentFps >= 35) {
            qualityLevel = 0.7F;
        } else if (currentFps >= 20) {
            qualityLevel = 0.45F;
        } else {
            qualityLevel = 0.25F;
        }
    }

    public static int getEstimatedFps() {
        return currentFps;
    }

    public static float getQualityLevel() {
        return isEnabled() ? qualityLevel : 1.0F;
    }

    public static boolean isAdaptiveBlurEnabled() {
        return getSetting("Opt Adaptive Blur", true);
    }

    // ── ЧАСТИЦЫ ─────────────────────────────────────────────────────────────
    public static boolean isNoAllParticles() {
        return getSetting("Opt All Particles", false);
    }

    public static boolean isNoRainParticles() {
        return isNoAllParticles() || getSetting("Opt Rain Particles", true);
    }

    public static boolean isNoPotionParticles() {
        return isNoAllParticles() || getSetting("Opt Potion Particles", false);
    }

    public static boolean isNoExplosionParticles() {
        return isNoAllParticles() || getSetting("Opt Explosion Particles", false);
    }

    public static boolean isNoSmokeParticles() {
        return isNoAllParticles() || getSetting("Opt Smoke Particles", false);
    }

    public static boolean isNoFireParticles() {
        return isNoAllParticles() || getSetting("Opt Fire Particles", false);
    }

    public static boolean isNoTotemParticles() {
        return isNoAllParticles() || getSetting("Opt Totem Particles", false);
    }

    public static boolean isParticleDistanceCullEnabled() {
        return getSetting("Opt Particle Dist Cull", true);
    }

    public static float getParticleCullDistanceSq() {
        float dist = getNum("Opt Particle Dist", 36.0f);
        return dist * dist;
    }

    public static boolean isParticlePosTooFar(double x, double y, double z) {
        if (!isEnabled() || !isParticleDistanceCullEnabled()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return false;
        var camera = mc.gameRenderer.getCamera();
        var camPos = camera.getPos();
        if (camPos == null) return false;
        double dx = x - camPos.x;
        double dy = y - camPos.y;
        double dz = z - camPos.z;
        return (dx * dx + dy * dy + dz * dz) > getParticleCullDistanceSq();
    }

    public static boolean shouldCancelParticle(ParticleEffect effect) {
        if (!isEnabled()) return false;
        if (isNoAllParticles()) return true;
        if (effect == null) return false;

        var type = effect.getType();
        if (isNoRainParticles() && (type == ParticleTypes.RAIN || type == ParticleTypes.SPLASH || type == ParticleTypes.UNDERWATER)) {
            return true;
        }
        if (isNoPotionParticles() && (type == ParticleTypes.ENTITY_EFFECT || type == ParticleTypes.INSTANT_EFFECT || type == ParticleTypes.WITCH)) {
            return true;
        }
        if (isNoExplosionParticles() && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.SONIC_BOOM)) {
            return true;
        }
        if (isNoSmokeParticles() && (type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE || type == ParticleTypes.WHITE_SMOKE
                || type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE)) {
            return true;
        }
        if (isNoFireParticles() && (type == ParticleTypes.FLAME || type == ParticleTypes.SMALL_FLAME || type == ParticleTypes.SOUL_FIRE_FLAME || type == ParticleTypes.LAVA)) {
            return true;
        }
        if (isNoTotemParticles() && type == ParticleTypes.TOTEM_OF_UNDYING) {
            return true;
        }
        return false;
    }

    public static boolean shouldCancelParticle(ParticleEffect effect, double x, double y, double z) {
        if (!isEnabled()) return false;
        if (isNoAllParticles()) return true;
        if (isParticlePosTooFar(x, y, z)) return true;
        return shouldCancelParticle(effect);
    }

    // ── СУЩНОСТИ И БРОНЯ ────────────────────────────────────────────────────
    public static boolean isNoArmor() {
        return getSetting("Opt No Armor", false);
    }

    public static boolean isNoNametags() {
        return getSetting("Opt No Nametags", false);
    }

    public static boolean isNoShadows() {
        return getSetting("Opt No Shadows", true);
    }

    public static boolean isNoGlint() {
        return getSetting("Opt No Glint", false);
    }

    public static boolean isNoArmorStands() {
        return getSetting("Opt No Armor Stands", false);
    }

    public static boolean isNoBeaconBeams() {
        return getSetting("Opt No Beacons", false);
    }

    public static boolean isFastWeather() {
        return getSetting("Opt Fast Weather", true);
    }

    public static boolean isEntityCullingEnabled() {
        return getSetting("Opt Entity Culling", true);
    }

    public static float getEntityCullDistanceSq() {
        float dist = getNum("Opt Entity Dist", 48.0f);
        return dist * dist;
    }

    public static boolean isFastItems() {
        return getSetting("Opt Fast Items", true);
    }

    public static float getItemsCullDistance() {
        return getNum("Opt Items Dist", 20.0f);
    }

    // ── ЯДРО РЕНДЕРА: ОБЛАКА, ТУМАН, СУНДУКИ, СВЕТ, ХОТБАР ────────────────────
    public static boolean isNoClouds() {
        return getSetting("Opt No Clouds", true);
    }

    public static boolean isNoFog() {
        return getSetting("Opt No Fog", true);
    }

    public static boolean isBlockEntityCullingEnabled() {
        return getSetting("Opt Block Culling", true);
    }

    public static boolean isFastLighting() {
        return getSetting("Opt Fast Lighting", true);
    }

    public static boolean isFastHud() {
        return getSetting("Opt Fast HUD", true);
    }

    public static boolean shouldCullBlockEntity(BlockEntity be) {
        if (!isEnabled() || !isBlockEntityCullingEnabled() || be == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null || mc.player == null) return false;

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        BlockPos pos = be.getPos();

        double dx = (pos.getX() + 0.5) - camPos.x;
        double dy = (pos.getY() + 0.5) - camPos.y;
        double dz = (pos.getZ() + 0.5) - camPos.z;

        double distSq = dx * dx + dy * dy + dz * dz;
        float maxDist = getNum("Opt Block Dist", 48.0f);
        if (distSq > (maxDist * maxDist)) {
            return true;
        }

        return false;
    }

    // ── ДИНАМИЧЕСКИЙ ЛИМИТ ПРОРИСОВКИ ЧАНКОВ (RENDER DISTANCE GOVERNOR) ───────
    private static int savedViewDistance = -1;
    private static int appliedCap = -1;

    public static boolean isLimitRenderDistanceEnabled() {
        return getSetting("Opt Limit Render Distance", false);
    }

    public static void checkRenderDistance() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        boolean enabled = isEnabled() && isLimitRenderDistanceEnabled();
        var option = mc.options.getViewDistance();
        int current = option.getValue();

        if (enabled) {
            // Если качество просело (FPS < 35) или прорисовка завышена при лагах
            int targetCap = qualityLevel < 0.45F ? 8 : (qualityLevel < 0.7F ? 10 : 12);
            if (current > targetCap) {
                if (savedViewDistance < 0) {
                    savedViewDistance = current;
                }
                appliedCap = targetCap;
                option.setValue(targetCap);
                return;
            }
        } else {
            restoreRenderDistance();
        }
    }

    public static void restoreRenderDistance() {
        if (savedViewDistance < 0) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options != null) {
                int current = mc.options.getViewDistance().getValue();
                if (appliedCap < 0 || current == appliedCap) {
                    mc.options.getViewDistance().setValue(savedViewDistance);
                }
            }
        } catch (Throwable ignored) {}
        savedViewDistance = -1;
        appliedCap = -1;
    }
}
