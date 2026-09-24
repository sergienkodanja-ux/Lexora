package com.lexoravisauls.client.modules.killeffect;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Divine Wrath kill effect:
 * Celestial judgment descends from the heavens onto the fallen foe.
 * Features:
 * - Intricate sacred geometric runic seal rotating flat on the ground
 * - Colossal blinding orbital pillar of celestial light crashing down from 50+ blocks
 * - Multiple golden halo rings erupting from the seal and rushing upward into the clouds
 * - Branching golden-white divine lightning arcs crackling across the terrain
 * - Shimmering holy seraphim embers ascending into the sky
 */
public class DivineWrathEffect implements KillEffect<DivineWrathEffect.State> {

    @Override
    public State onStart(LivingEntity victim, double x, double y, double z, Random random) {
        float durationSec = KillEffectSettings.slider("Divine Duration", 2.4f);
        float beamWidth = KillEffectSettings.slider("Divine Beam Width", 1.5f);
        int totalTicks = Math.max(10, Math.round(durationSec * 20.0f));

        int boltCount = 6;
        LightningBranch[] bolts = new LightningBranch[boltCount];
        for (int i = 0; i < boltCount; i++) {
            double angle = (Math.PI * 2.0 / boltCount) * i + (random.nextDouble() - 0.5) * 0.4;
            double dist = 2.4 + random.nextDouble() * 1.6;
            bolts[i] = new LightningBranch(
                    Math.cos(angle) * dist,
                    (random.nextDouble() - 0.5) * 0.15,
                    Math.sin(angle) * dist,
                    random.nextInt(10000)
            );
        }

        int emberCount = 28;
        HolyEmber[] embers = new HolyEmber[emberCount];
        for (int i = 0; i < emberCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double rad = 0.3 + random.nextDouble() * 1.8;
            embers[i] = new HolyEmber(
                    Math.cos(angle) * rad,
                    Math.sin(angle) * rad,
                    random.nextFloat() * 1.5f,
                    0.5f + random.nextFloat() * 0.8f,
                    0.05f + random.nextFloat() * 0.05f
            );
        }

        return new State(x, y, z, beamWidth, totalTicks, bolts, embers);
    }

    @Override
    public void render(State state, MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                        Vec3d camPos, float progress, float ageWithDelta, Random random) {
        KillEffectDraw.beginGlow(true);

        double relX = state.x - camPos.x;
        double relY = state.y - camPos.y;
        double relZ = state.z - camPos.z;

        float beamWidth = state.beamWidth;
        float sealRadius = 2.2f * beamWidth;

        // ─────────────────────────────────────────────────────────────
        // 1. SACRED RUNIC SEAL (Custom GLSL Shader & Geometric Seal)
        // ─────────────────────────────────────────────────────────────
        float sealFadeIn = MathHelper.clamp(progress / 0.12f, 0.0f, 1.0f);
        float sealFadeOut = 1.0f - MathHelper.clamp((progress - 0.78f) / 0.22f, 0.0f, 1.0f);
        float sealAlpha = sealFadeIn * sealFadeOut;

        if (sealAlpha > 0.01f) {
            // Custom GLSL shader with animated octagram, runic ticks & sunbeams
            KillEffectShaders.renderDivineSeal(matrices,
                    relX, relY, relZ,
                    sealRadius * 1.05f,
                    ageWithDelta * 0.06f, progress, sealAlpha,
                    1.0f, 0.9f, 0.35f, 1.0f, 0.55f, 0.1f);

            float spinOuter = ageWithDelta * 18.0f;
            float spinInner = -ageWithDelta * 24.0f;

            KillEffectDraw.drawRunicSeal(matrices,
                    relX, relY, relZ,
                    sealRadius, spinOuter, spinInner,
                    1.0f, 0.82f, 0.2f, sealAlpha * 0.95f);

            // Subtle celestial boundary spires on the seal edges
            for (int i = 0; i < 6; i++) {
                double edgeAng = (Math.PI * 2.0 / 6.0) * i + ageWithDelta * 0.03;
                double ex = relX + Math.cos(edgeAng) * (sealRadius * 0.98);
                double ez = relZ + Math.sin(edgeAng) * (sealRadius * 0.98);
                KillEffectDraw.drawVolumetricCylinder(matrices,
                        ex, relY, ez,
                        0.035f * beamWidth, 0.015f * beamWidth, 2.4f * sealAlpha,
                        0.0f,
                        1.0f, 0.92f, 0.45f, sealAlpha * 0.65f, 0.0f, 8);
                KillEffectDraw.drawRing(matrices,
                        ex, relY + 0.02, ez,
                        0.18f * beamWidth, 0.025f * beamWidth,
                        0, 0, 0,
                        1.0f, 0.85f, 0.3f, sealAlpha * 0.7f, 16);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 2. ORBITAL CELESTIAL PILLAR OF LIGHT (Sky to ground)
        // ─────────────────────────────────────────────────────────────
        float beamStart = 0.15f;
        float beamEnd = 0.80f;

        if (progress >= beamStart && progress <= beamEnd) {
            float beamProg = (progress - beamStart) / (beamEnd - beamStart);
            float beamIn = MathHelper.clamp(beamProg / 0.10f, 0.0f, 1.0f);
            float beamOut = 1.0f - MathHelper.clamp((beamProg - 0.70f) / 0.30f, 0.0f, 1.0f);
            float beamAlpha = beamIn * beamOut;

            float pillarHeight = 55.0f;

            // Inner blinding white-gold core
            KillEffectDraw.drawVolumetricCylinder(matrices,
                    relX, relY, relZ,
                    0.28f * beamWidth, 0.22f * beamWidth, pillarHeight,
                    ageWithDelta * 15.0f,
                    1.0f, 0.98f, 0.92f, beamAlpha * 0.95f, 0.0f, 20);

            // Outer celestial gold pillar
            KillEffectDraw.drawVolumetricCylinder(matrices,
                    relX, relY, relZ,
                    0.85f * beamWidth, 0.65f * beamWidth, pillarHeight,
                    -ageWithDelta * 10.0f,
                    1.0f, 0.75f, 0.15f, beamAlpha * 0.55f, 0.0f, 24);

            // Outermost soft golden aura
            KillEffectDraw.drawVolumetricCylinder(matrices,
                    relX, relY, relZ,
                    1.45f * beamWidth, 1.15f * beamWidth, pillarHeight,
                    ageWithDelta * 6.0f,
                    1.0f, 0.6f, 0.05f, beamAlpha * 0.25f, 0.0f, 24);

            // Double helix energy streams swirling up the beam
            KillEffectDraw.drawHelix(matrices,
                    relX, relY, relZ,
                    0.95f * beamWidth, 25.0f, ageWithDelta * 0.35f, 4.0f,
                    0.12f, 1.0f, 0.9f, 0.3f, beamAlpha * 0.75f, 40);
            KillEffectDraw.drawHelix(matrices,
                    relX, relY, relZ,
                    0.95f * beamWidth, 25.0f, (float) (ageWithDelta * 0.35f + Math.PI), 4.0f,
                    0.12f, 1.0f, 0.7f, 0.1f, beamAlpha * 0.75f, 40);

            // ─────────────────────────────────────────────────────────────
            // 3. ASCENDING CELESTIAL HALO RINGS
            // ─────────────────────────────────────────────────────────────
            int haloCount = 3;
            for (int h = 0; h < haloCount; h++) {
                float haloPhase = (beamProg * 2.5f + h * 0.33f) % 1.0f;
                float haloY = (float) (Math.pow(haloPhase, 1.4) * 22.0);
                float haloR = (0.5f + haloPhase * 1.6f) * beamWidth;
                float haloA = (1.0f - haloPhase) * beamAlpha * 0.85f;

                KillEffectDraw.drawRing(matrices,
                        relX, relY + haloY, relZ,
                        haloR, 0.10f * beamWidth, 0, 0, 0,
                        1.0f, 0.9f, 0.4f, haloA, 36);
            }

            // ─────────────────────────────────────────────────────────────
            // 4. BRANCHING DIVINE GROUND LIGHTNING
            // ─────────────────────────────────────────────────────────────
            if (beamProg < 0.65f) {
                float boltAlpha = (1.0f - beamProg / 0.65f) * beamIn;
                for (LightningBranch bolt : state.bolts) {
                    Random boltRand = new Random(bolt.seed + (long) (ageWithDelta * 3.0f));
                    KillEffectDraw.drawBranchingLightning(matrices,
                            relX, relY + 0.05, relZ,
                            relX + bolt.targetX, relY + 0.05 + bolt.targetY, relZ + bolt.targetZ,
                            3, boltRand,
                            1.0f, 0.85f, 0.3f, boltAlpha * 0.9f, 0.045f);
                }
            }

            // Ground impact flash disk
            float flashR = (0.6f + beamProg * 1.8f) * beamWidth;
            KillEffectDraw.drawDisk(matrices, relX, relY + 0.03, relZ,
                    flashR, 0, 0,
                    1.0f, 0.95f, 0.8f, beamAlpha * 0.7f, 0.0f, 28);
        }

        // ─────────────────────────────────────────────────────────────
        // 5. GENTLE EXPANDING CELESTIAL RADIANCE RINGS
        // ─────────────────────────────────────────────────────────────
        if (progress >= 0.20f) {
            float auraProg = (progress - 0.20f) / 0.80f;
            float auraAlpha = (1.0f - auraProg) * 0.75f;
            for (int r = 0; r < 2; r++) {
                float wave = (auraProg * 1.8f + r * 0.5f) % 1.0f;
                float rDist = (0.4f + wave * 2.2f) * beamWidth;
                float rAlpha = (1.0f - wave) * auraAlpha;
                KillEffectDraw.drawRing(matrices,
                        relX, relY + 0.03, relZ,
                        rDist, 0.04f * beamWidth, 0, 0, 0,
                        1.0f, 0.9f, 0.4f, rAlpha, 32);
            }
        }

        KillEffectDraw.endGlow();
    }

    @Override
    public int getDurationTicks(State state) {
        return state.totalTicks;
    }

    static final class LightningBranch {
        final double targetX, targetY, targetZ;
        final int seed;

        LightningBranch(double targetX, double targetY, double targetZ, int seed) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.seed = seed;
        }
    }

    static final class HolyEmber {
        final double ox, oz;
        final float phase, riseSpeed, size;

        HolyEmber(double ox, double oz, float phase, float riseSpeed, float size) {
            this.ox = ox;
            this.oz = oz;
            this.phase = phase;
            this.riseSpeed = riseSpeed;
            this.size = size;
        }
    }

    static final class State {
        final double x, y, z;
        final float beamWidth;
        final int totalTicks;
        final LightningBranch[] bolts;
        final HolyEmber[] embers;

        State(double x, double y, double z, float beamWidth, int totalTicks,
              LightningBranch[] bolts, HolyEmber[] embers) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.beamWidth = beamWidth;
            this.totalTicks = totalTicks;
            this.bolts = bolts;
            this.embers = embers;
        }
    }
}
