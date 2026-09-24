package com.lexoravisauls.client.modules.killeffect;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Cosmic Singularity kill effect (Complete Overhaul):
 * A floating, hypnotic 3D dark energy singularity forms at the kill spot.
 * Features:
 * - Camera-aligned spherical black hole shader with pitch-black event horizon,
 *   blinding Einstein photon ring, and swirling relativistic accretion plasma
 * - 3 gyroscopic orbital rings spinning smoothly across 3 separate 3D axes
 * - Inward contracting gravitational ripples (reverse shockwaves pulling spacetime into the core)
 * - Ground spacetime distortion rings
 * - Sudden gravitational implosion followed by an expanding translucent relativistic shockwave dome,
 *   equatorial blast ring, and ground wavefront
 * - Zero vertical beacon cylinders, zero clipping planes, zero square particles — pure smooth curves!
 */
public class CosmicSingularityEffect implements KillEffect<CosmicSingularityEffect.State> {

    @Override
    public State onStart(LivingEntity victim, double x, double y, double z, Random random) {
        float durationSec = KillEffectSettings.slider("Singularity Duration", 2.2f);
        float scale = KillEffectSettings.slider("Singularity Scale", 1.4f);
        int totalTicks = Math.max(10, Math.round(durationSec * 20.0f));

        return new State(x, y, z, scale, totalTicks);
    }

    @Override
    public void render(State state, MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                        Vec3d camPos, float progress, float ageWithDelta, Random random) {
        double relX = state.x - camPos.x;
        double relY = state.y - camPos.y;
        double relZ = state.z - camPos.z;

        float coreHeight = 1.15f;
        double centerY = relY + coreHeight;

        float scale = state.scale;
        float collapsePoint = 0.65f;

        if (progress <= collapsePoint) {
            // ─────────────────────────────────────────────────────────────
            // PHASE 1: FLOATING BLACK HOLE & GYROSCOPIC ACCRETION RINGS
            // ─────────────────────────────────────────────────────────────
            float t = progress / collapsePoint;
            float fadeIn = MathHelper.clamp(t / 0.12f, 0.0f, 1.0f);
            float pull = t * t * t; // Cubic gravitational inward acceleration

            // 1. Camera-facing Spherical Singularity (Black Hole Event Horizon + Photon Ring)
            float orbSize = 2.4f * scale * (1.0f - pull * 0.35f);
            KillEffectShaders.renderSingularitySphere(matrices, camera,
                    relX, centerY, relZ,
                    orbSize, ageWithDelta * 0.06f, t, fadeIn,
                    0.05f, 0.95f, 1.0f, 0.68f, 0.12f, 1.0f);

            KillEffectDraw.beginGlow(true);

            // 2. Three Gyroscopic 3D Orbital Rings (spinning around the floating core)
            float spin1 = ageWithDelta * 9.0f;
            float spin2 = -ageWithDelta * 12.0f;
            float spin3 = ageWithDelta * 7.0f;
            float rBase = 0.92f * scale * (1.0f - pull * 0.3f);

            // 2a. Equatorial Horizontal Ring (Electric Cyan)
            KillEffectDraw.drawRing(matrices,
                    relX, centerY, relZ,
                    rBase, 0.055f * scale,
                    0.0f, spin1, 0.0f,
                    0.1f, 0.95f, 1.0f, fadeIn * 0.9f, 48);

            // 2b. Tilted Oblique Ring 1 (Ultra-Violet)
            KillEffectDraw.drawRing(matrices,
                    relX, centerY, relZ,
                    rBase * 1.08f, 0.065f * scale,
                    42.0f, spin2, 22.0f,
                    0.72f, 0.15f, 1.0f, fadeIn * 0.85f, 48);

            // 2c. Counter-tilted Ring 2 (Deep Violet / White Core)
            KillEffectDraw.drawRing(matrices,
                    relX, centerY, relZ,
                    rBase * 1.16f, 0.05f * scale,
                    -38.0f, spin3, -25.0f,
                    0.35f, 0.75f, 1.0f, fadeIn * 0.75f, 48);

            // 3. Inward Contracting Gravitational Ripples (Spacetime imploding into the center)
            for (int w = 0; w < 2; w++) {
                float waveProg = (t * 2.2f + w * 0.5f) % 1.0f;
                float inRadius = (1.0f - waveProg) * 2.6f * scale;
                float inAlpha = waveProg * fadeIn * 0.65f;
                KillEffectDraw.drawRing(matrices,
                        relX, centerY, relZ,
                        inRadius, 0.04f * scale,
                        15.0f, ageWithDelta * 5.0f, 10.0f,
                        0.2f, 0.9f, 1.0f, inAlpha, 36);
            }

            // 4. Ground Spacetime Distortion Waves
            float groundWave = (ageWithDelta * 0.07f) % 1.0f;
            float gR = groundWave * 2.5f * scale;
            float gA = (1.0f - groundWave) * fadeIn * 0.55f;
            KillEffectDraw.drawRing(matrices,
                    relX, relY + 0.02, relZ,
                    gR, 0.05f * scale,
                    0, 0, 0,
                    0.2f, 0.85f, 1.0f, gA, 36);

            KillEffectDraw.endGlow();

        } else {
            // ─────────────────────────────────────────────────────────────
            // PHASE 2: SUPERNOVA DETONATION & RELATIVISTIC SHOCKWAVE
            // ─────────────────────────────────────────────────────────────
            float burstT = (progress - collapsePoint) / (1.0f - collapsePoint);
            float burstEase = 1.0f - (float) Math.pow(1.0f - burstT, 3.0); // cubic ease-out
            float alpha = 1.0f - burstT;

            KillEffectDraw.beginGlow(true);

            // 1. Blinding White-Hot Supernova Flash Core
            float flashAlpha = 1.0f - MathHelper.clamp(burstT / 0.25f, 0.0f, 1.0f);
            if (flashAlpha > 0.01f) {
                float flashR = (0.4f + burstT * 1.6f) * scale;
                KillEffectDraw.drawDisk(matrices, relX, centerY, relZ,
                        flashR, 0, 0,
                        1.0f, 1.0f, 1.0f, flashAlpha, 0.0f, 32);
                KillEffectDraw.drawDisk(matrices, relX, centerY, relZ,
                        flashR * 1.5f, 0, 0,
                        0.25f, 0.85f, 1.0f, flashAlpha * 0.5f, 0.0f, 32);
            }

            // 2. Expanding Relativistic Equatorial Blast Ring
            float shockR = (0.45f + burstEase * 3.6f) * scale;

            // Primary horizontal blast ring
            KillEffectDraw.drawRing(matrices, relX, centerY, relZ,
                    shockR, 0.11f * scale, 0, 0, 0,
                    0.15f, 0.95f, 1.0f, alpha * 0.95f, 48);

            // Secondary tilted blast ring
            KillEffectDraw.drawRing(matrices, relX, centerY, relZ,
                    shockR * 0.95f, 0.08f * scale, 25.0f, ageWithDelta * 10.0f, 15.0f,
                    0.7f, 0.2f, 1.0f, alpha * 0.75f, 48);

            // Ground expanding wavefront
            KillEffectDraw.drawRing(matrices, relX, relY + 0.02, relZ,
                    shockR * 1.12f, 0.09f * scale, 0, 0, 0,
                    0.25f, 0.85f, 1.0f, alpha * 0.7f, 48);

            // 3. Smooth Expanding Translucent Shockwave Dome
            float domeAlpha = alpha * 0.40f;
            if (domeAlpha > 0.01f) {
                KillEffectDraw.drawShockwaveDome(matrices,
                        relX, centerY - 0.2, relZ,
                        shockR * 0.85f, 0.75f,
                        0.3f, 0.85f, 1.0f, domeAlpha,
                        16, 24);
            }

            KillEffectDraw.endGlow();
        }
    }

    @Override
    public int getDurationTicks(State state) {
        return state.totalTicks;
    }

    static final class State {
        final double x, y, z;
        final float scale;
        final int totalTicks;

        State(double x, double y, double z, float scale, int totalTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.totalTicks = totalTicks;
        }
    }
}
