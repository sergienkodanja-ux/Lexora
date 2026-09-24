package com.lexoravisauls.client.modules.killeffect;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Blood Nova kill effect ("Кристаллический разрыв"):
 * Violent crimson crystal eruption from the earth that shatters into a high-speed
 * ruby nova shockwave dome, tumbling faceted shards, and ascending blood spirals.
 * Features:
 * - 12 faceted 3D ruby crystal obelisks thrusting violently out of the ground
 * - Razor-sharp glowing crimson ridges and directionally shaded crystal faces
 * - Cataclysmic shatter into tumbling 3D diamond shards with realistic trajectory physics
 * - Massive expanding translucent crimson shockwave dome with glowing latitude rings
 * - Dual spiraling blood ribbons swirling up through the epicenter
 * - Ambient floating ruby embers and spark trails
 */
public class BloodNovaEffect implements KillEffect<BloodNovaEffect.State> {

    @Override
    public State onStart(LivingEntity victim, double x, double y, double z, Random random) {
        float durationSec = KillEffectSettings.slider("Nova Duration", 2.0f);
        int spikeCount = Math.max(6, Math.round(KillEffectSettings.slider("Nova Spikes", 12.0f)));
        float radius = KillEffectSettings.slider("Nova Radius", 2.2f);
        int totalTicks = Math.max(10, Math.round(durationSec * 20.0f));

        CrystalSpikeData[] spikes = new CrystalSpikeData[spikeCount];
        for (int i = 0; i < spikeCount; i++) {
            double angle = (Math.PI * 2.0 / spikeCount) * i + (random.nextDouble() - 0.5) * 0.35;
            double dist = 0.4 + random.nextDouble() * (radius * 0.65);
            float baseW = 0.16f + random.nextFloat() * 0.12f;
            float maxH = 1.6f + random.nextFloat() * 1.6f;
            float tiltX = (float) (Math.cos(angle) * (0.2 + random.nextDouble() * 0.35));
            float tiltZ = (float) (Math.sin(angle) * (0.2 + random.nextDouble() * 0.35));
            spikes[i] = new CrystalSpikeData(
                    Math.cos(angle) * dist,
                    Math.sin(angle) * dist,
                    baseW, maxH, tiltX, tiltZ,
                    random.nextFloat() * 360.0f
            );
        }

        int shardCount = 24;
        FlyingRubyShard[] shards = new FlyingRubyShard[shardCount];
        for (int i = 0; i < shardCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double horizSpeed = 1.4 + random.nextDouble() * 2.8;
            double vertSpeed = 1.8 + random.nextDouble() * 2.4;
            shards[i] = new FlyingRubyShard(
                    Math.cos(angle) * horizSpeed,
                    vertSpeed,
                    Math.sin(angle) * horizSpeed,
                    0.09f + random.nextFloat() * 0.08f,
                    random.nextFloat() * 360.0f,
                    random.nextFloat() * 360.0f,
                    12.0f + random.nextFloat() * 18.0f
            );
        }

        int emberCount = 24;
        BloodEmber[] embers = new BloodEmber[emberCount];
        for (int i = 0; i < emberCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = random.nextDouble() * radius;
            embers[i] = new BloodEmber(
                    Math.cos(angle) * dist,
                    Math.sin(angle) * dist,
                    random.nextFloat() * 2.0f,
                    0.4f + random.nextFloat() * 0.7f,
                    0.05f + random.nextFloat() * 0.04f
            );
        }

        return new State(x, y, z, radius, totalTicks, spikes, shards, embers);
    }

    @Override
    public void render(State state, MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                        Vec3d camPos, float progress, float ageWithDelta, Random random) {
        KillEffectDraw.beginGlow(true);

        double relX = state.x - camPos.x;
        double relY = state.y - camPos.y;
        double relZ = state.z - camPos.z;

        float radius = state.radius;
        float shatterPoint = 0.32f;

        // ─────────────────────────────────────────────────────────────
        // 1. GROUND BLOOD FISSURE RINGS
        // ─────────────────────────────────────────────────────────────
        float groundFadeIn = MathHelper.clamp(progress / 0.10f, 0.0f, 1.0f);
        float groundFadeOut = 1.0f - MathHelper.clamp((progress - 0.75f) / 0.25f, 0.0f, 1.0f);
        float groundAlpha = groundFadeIn * groundFadeOut;

        if (groundAlpha > 0.01f) {
            // Inner glowing blood pool / disk
            KillEffectDraw.drawDisk(matrices, relX, relY + 0.01, relZ,
                    radius * 0.85f, 0, 0,
                    0.85f, 0.05f, 0.12f, groundAlpha * 0.65f, 0.0f, 24);

            // Ground fracture boundary ring
            KillEffectDraw.drawRing(matrices, relX, relY + 0.02, relZ,
                    radius * 0.85f, 0.14f, 0, 0, 0,
                    1.0f, 0.1f, 0.2f, groundAlpha * 0.85f, 36);
        }

        // ─────────────────────────────────────────────────────────────
        // 2. 3D FACETED RUBY CRYSTAL SPIKES (Violent Eruption)
        // ─────────────────────────────────────────────────────────────
        if (progress < shatterPoint + 0.08f) {
            // Fast violent surge up (cubic ease-out)
            float eruptProg = MathHelper.clamp(progress / shatterPoint, 0.0f, 1.0f);
            float eruptEase = (float) Math.sin(eruptProg * Math.PI * 0.5); // snap up
            float spikeAlpha = 1.0f;
            if (progress >= shatterPoint) {
                // Flash-fade right as they shatter
                spikeAlpha = 1.0f - (progress - shatterPoint) / 0.08f;
            }

            for (CrystalSpikeData spike : state.spikes) {
                float curH = spike.maxHeight * eruptEase;
                KillEffectDraw.drawCrystalSpike(matrices,
                        relX + spike.offsetX, relY, relZ + spike.offsetZ,
                        spike.baseWidth, curH,
                        spike.tiltX, spike.tiltZ, spike.yaw,
                        1.0f, 0.08f, 0.22f, spikeAlpha);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 3. CATACLYSMIC SHATTER NOVA & EXPANDING DOME
        // ─────────────────────────────────────────────────────────────
        if (progress >= shatterPoint) {
            float burstT = (progress - shatterPoint) / (1.0f - shatterPoint);
            float burstEase = 1.0f - (1.0f - burstT) * (1.0f - burstT);
            float novaAlpha = 1.0f - burstT;

            // Custom GLSL Shader Voronoi Crystal Fractures & Expanding Shockwave Ripple
            KillEffectShaders.renderBloodNovaWave(matrices,
                    relX, relY, relZ,
                    (0.6f + burstEase * (radius * 1.8f)),
                    ageWithDelta * 0.05f, progress, novaAlpha,
                    1.0f, 0.12f, 0.28f, 0.65f, 0.02f, 0.08f);

            // Expanding Translucent Crimson Shockwave Dome
            float domeRadius = (0.5f + burstEase * (radius * 1.8f));
            KillEffectDraw.drawShockwaveDome(matrices,
                    relX, relY, relZ,
                    domeRadius, 0.75f,
                    1.0f, 0.12f, 0.25f, novaAlpha * 0.85f, 8, 24);

            // Ground expanding blast ring
            KillEffectDraw.drawRing(matrices, relX, relY + 0.03, relZ,
                    domeRadius * 1.05f, 0.18f, 0, 0, 0,
                    1.0f, 0.2f, 0.35f, novaAlpha * 0.95f, 40);

            // Center detonation core flash (fast fade)
            float coreFlash = 1.0f - MathHelper.clamp(burstT / 0.25f, 0.0f, 1.0f);
            if (coreFlash > 0.01f) {
                KillEffectDraw.drawDisk(matrices, relX, relY + 0.8, relZ,
                        (0.6f + burstT * 1.8f), 0, 0,
                        1.0f, 0.9f, 0.95f, coreFlash * 0.9f, 0.0f, 24);
            }

            // Dual Ascending Crimson Blood Spirals (vortex)
            float spiralH = 16.0f * burstEase;
            KillEffectDraw.drawHelix(matrices,
                    relX, relY, relZ,
                    0.85f * radius * (1.0f - burstT * 0.3f), spiralH,
                    ageWithDelta * 0.4f, 3.0f,
                    0.16f, 1.0f, 0.1f, 0.25f, novaAlpha * 0.85f, 36);
            KillEffectDraw.drawHelix(matrices,
                    relX, relY, relZ,
                    0.85f * radius * (1.0f - burstT * 0.3f), spiralH,
                    (float) (ageWithDelta * 0.4f + Math.PI), 3.0f,
                    0.16f, 0.85f, 0.05f, 0.15f, novaAlpha * 0.85f, 36);

            // Tumbling 3D Ruby Shards with parabolic ballistic trajectory
            for (FlyingRubyShard s : state.shards) {
                double sx = relX + s.vx * burstEase;
                double sy = relY + s.vy * burstEase - burstT * burstT * 2.8;
                double sz = relZ + s.vz * burstEase;

                // Stop at ground
                sy = Math.max(sy, relY + 0.08);

                float tumbleYaw = s.yawBase + ageWithDelta * s.spinSpeed;
                float tumblePitch = s.pitchBase + ageWithDelta * s.spinSpeed * 0.7f;
                float tumbleRoll = ageWithDelta * s.spinSpeed * 0.5f;

                KillEffectDraw.drawDiamondShard(matrices,
                        sx, sy, sz, s.size * (1.0f - burstT * 0.35f),
                        tumbleYaw, tumblePitch, tumbleRoll,
                        1.0f, 0.15f, 0.28f, novaAlpha * 0.95f);

            }
        }

        // ─────────────────────────────────────────────────────────────
        // 4. ASCENDING CRIMSON PULSE RINGS
        // ─────────────────────────────────────────────────────────────
        if (progress >= 0.15f) {
            float pulseProg = (progress - 0.15f) / 0.85f;
            float pulseAlpha = (1.0f - pulseProg) * 0.75f;
            for (int r = 0; r < 2; r++) {
                float wave = (pulseProg * 2.0f + r * 0.5f) % 1.0f;
                float ringR = (0.5f + wave * 2.5f) * (radius * 0.7f);
                float ringA = (1.0f - wave) * pulseAlpha;
                KillEffectDraw.drawRing(matrices,
                        relX, relY + 0.03, relZ,
                        ringR, 0.05f * radius, 0, 0, 0,
                        1.0f, 0.15f, 0.25f, ringA, 32);
            }
        }

        KillEffectDraw.endGlow();
    }

    @Override
    public int getDurationTicks(State state) {
        return state.totalTicks;
    }

    static final class CrystalSpikeData {
        final double offsetX, offsetZ;
        final float baseWidth, maxHeight, tiltX, tiltZ, yaw;

        CrystalSpikeData(double offsetX, double offsetZ, float baseWidth, float maxHeight,
                         float tiltX, float tiltZ, float yaw) {
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.baseWidth = baseWidth;
            this.maxHeight = maxHeight;
            this.tiltX = tiltX;
            this.tiltZ = tiltZ;
            this.yaw = yaw;
        }
    }

    static final class FlyingRubyShard {
        final double vx, vy, vz;
        final float size, yawBase, pitchBase, spinSpeed;

        FlyingRubyShard(double vx, double vy, double vz, float size,
                        float yawBase, float pitchBase, float spinSpeed) {
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.yawBase = yawBase;
            this.pitchBase = pitchBase;
            this.spinSpeed = spinSpeed;
        }
    }

    static final class BloodEmber {
        final double ox, oz;
        final float phase, riseSpeed, size;

        BloodEmber(double ox, double oz, float phase, float riseSpeed, float size) {
            this.ox = ox;
            this.oz = oz;
            this.phase = phase;
            this.riseSpeed = riseSpeed;
            this.size = size;
        }
    }

    static final class State {
        final double x, y, z;
        final float radius;
        final int totalTicks;
        final CrystalSpikeData[] spikes;
        final FlyingRubyShard[] shards;
        final BloodEmber[] embers;

        State(double x, double y, double z, float radius, int totalTicks,
              CrystalSpikeData[] spikes, FlyingRubyShard[] shards, BloodEmber[] embers) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.totalTicks = totalTicks;
            this.spikes = spikes;
            this.shards = shards;
            this.embers = embers;
        }
    }
}
