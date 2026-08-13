package com.lexoravisauls.client.modules.killeffect;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * A miniature singularity: wireframe shards fade smoothly into view, then
 * spiral inward on a flattened disk (like an accretion disk), accelerating
 * and stretching tangentially as they near the center ("spaghettification"),
 * colour drifting from cool purple at the rim to hot white-cyan at the core.
 * Every shard has a soft glow halo behind its crisp edges. Finishes with a
 * bright core flash and an expanding shockwave ring. Hand-drawn wireframe
 * geometry only — no vanilla particles. Seed count/radius/duration come from
 * SETTINGS sliders.
 */
public class VoidCollapseEffect implements KillEffect<VoidCollapseEffect.State> {

    @Override
    public State onStart(LivingEntity victim, double x, double y, double z, Random random) {
        int seedCount = Math.max(1, Math.round(KillEffectSettings.slider("Collapse Seed Count", 22.0f)));
        double radius = KillEffectSettings.slider("Collapse Radius", 1.3f);
        float totalSeconds = KillEffectSettings.slider("Collapse Duration", 1.6f);
        int totalTicks = Math.max(4, Math.round(totalSeconds * 20.0f));
        int spiralTicks = Math.max(2, Math.round(totalTicks * 0.72f));

        Seed[] seeds = new Seed[seedCount];
        for (int i = 0; i < seedCount; i++) {
            seeds[i] = new Seed(
                    random.nextDouble() * Math.PI * 2.0,
                    (random.nextDouble() - 0.5) * 0.35, // mostly flat disk, slight scatter
                    0.7f + random.nextFloat() * 0.6f,   // per-shard radius multiplier: ragged disk edge
                    random.nextFloat() * 360.0f
            );
        }

        return new State(x, y + 0.9, z, seeds, radius, spiralTicks, totalTicks);
    }

    @Override
    public void render(State state, MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                        Vec3d camPos, float progress, float ageWithDelta, Random random) {
        float elapsedTicks = progress * state.totalTicks;

        KillEffectDraw.beginBlend();

        if (elapsedTicks <= state.spiralTicks) {
            float t = elapsedTicks / state.spiralTicks;
            float pull = t * t; // ease-in: gravity "grabs" harder as it goes
            float fadeIn = MathHelper.clamp(t / 0.18f, 0.0f, 1.0f); // smooth appearance instead of an instant pop

            for (Seed seed : state.seeds) {
                double radius = state.radius * seed.radiusMul * (1.0 - pull);
                // extra spin on top of the inward pull, growing with pull itself —
                // the classic "water down a drain" accretion-disk look
                double angle = seed.angle + pull * pull * Math.PI * 5.0;

                double px = state.x + Math.cos(angle) * radius;
                double pz = state.z + Math.sin(angle) * radius;
                double py = state.y + seed.heightOffset * (1.0 - pull);

                // colour drifts from cool purple (outer) to hot white-cyan (inner)
                float heat = pull;
                float r = MathHelper.lerp(heat, 0.45f, 0.85f);
                float g = MathHelper.lerp(heat, 0.15f, 0.95f);
                float b = MathHelper.lerp(heat, 0.75f, 1.0f);

                // "spaghettification": shards stretch tangentially as they whip around faster
                float size = MathHelper.lerp(heat, 0.16f, 0.07f);
                float stretch = 1.0f + heat * 1.8f;
                float tangentDeg = (float) Math.toDegrees(angle) + 90.0f;
                float tumble = seed.tumble + ageWithDelta * 5.0f;

                // soft glow pass underneath — bigger, thinner-looking (via lower alpha), same shape
                KillEffectDraw.wireCubeStretched(matrices,
                        px - camPos.x, py - camPos.y, pz - camPos.z,
                        size * 2.1f, stretch, 0.05f, tangentDeg, tumble,
                        r, g, b, fadeIn * 0.25f);

                // crisp core edges on top, thinner
                KillEffectDraw.wireCubeStretched(matrices,
                        px - camPos.x, py - camPos.y, pz - camPos.z,
                        size, stretch, 0.02f, tangentDeg, tumble,
                        r, g, b, fadeIn);
            }
        } else {
            float burstT = MathHelper.clamp(
                    (elapsedTicks - state.spiralTicks) / Math.max(1, state.totalTicks - state.spiralTicks), 0.0f, 1.0f);

            // Core: a bright flash that snaps out fast, with a soft glow halo.
            float coreAlpha = 1.0f - MathHelper.clamp(burstT / 0.35f, 0.0f, 1.0f);
            if (coreAlpha > 0.01f) {
                float coreSize = 0.22f * (1.0f - burstT * 0.6f);
                KillEffectDraw.wireCube(matrices,
                        state.x - camPos.x, state.y - camPos.y, state.z - camPos.z,
                        coreSize * 2.0f, 0.06f, ageWithDelta * 20.0f, ageWithDelta * 14.0f,
                        0.85f, 0.95f, 1.0f, coreAlpha * 0.3f);
                KillEffectDraw.wireCube(matrices,
                        state.x - camPos.x, state.y - camPos.y, state.z - camPos.z,
                        coreSize, 0.025f, ageWithDelta * 20.0f, ageWithDelta * 14.0f,
                        0.85f, 0.95f, 1.0f, coreAlpha);
            }

            // Shockwave: a ring of shards expanding outward from the singularity, fading.
            float ringRadius = burstT * (float) (state.radius * 1.8);
            float ringAlpha = 1.0f - burstT;
            if (ringAlpha > 0.01f) {
                int ringCount = 14;
                for (int i = 0; i < ringCount; i++) {
                    double a = (Math.PI * 2.0 / ringCount) * i + ageWithDelta * 0.05;
                    double px = state.x + Math.cos(a) * ringRadius;
                    double pz = state.z + Math.sin(a) * ringRadius;
                    KillEffectDraw.wireCube(matrices,
                            px - camPos.x, state.y - camPos.y, pz - camPos.z,
                            0.10f + burstT * 0.05f, 0.02f,
                            (float) Math.toDegrees(a), 0.0f,
                            0.6f, 0.3f, 1.0f, ringAlpha);
                }
            }
        }

        KillEffectDraw.endBlend();
    }

    @Override
    public int getDurationTicks(State state) {
        return state.totalTicks;
    }

    static final class Seed {
        final double angle, heightOffset;
        final float radiusMul, tumble;

        Seed(double angle, double heightOffset, float radiusMul, float tumble) {
            this.angle = angle;
            this.heightOffset = heightOffset;
            this.radiusMul = radiusMul;
            this.tumble = tumble;
        }
    }

    static final class State {
        final double x, y, z;
        final Seed[] seeds;
        final double radius;
        final int spiralTicks;
        final int totalTicks;

        State(double x, double y, double z, Seed[] seeds, double radius, int spiralTicks, int totalTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.seeds = seeds;
            this.radius = radius;
            this.spiralTicks = spiralTicks;
            this.totalTicks = totalTicks;
        }
    }
}
