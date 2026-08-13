package com.lexoravisauls.client.modules.killeffect;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * The victim's body tears apart into six pieces — head, torso, two arms, two
 * legs — yanked toward a central point, tumbling and jostling past each other
 * on the way in, bouncing off the ground if pulled down that low, then
 * blasting back outward in an explosion.
 * <p>
 * Each piece fades smoothly into view rather than popping in, carries a small
 * decorative cube "node" orbiting it, and is connected to the torso by a
 * thin tether line that stretches and follows as it moves — like a broken
 * marionette still loosely held together by string.
 * <p>
 * Represented as wireframe boxes (matching the rest of this mod's look)
 * rather than the real skin model — reuses the same proven KillEffectDraw
 * primitives every other effect uses instead of reaching for the much
 * riskier "render individual real-model body parts" API surface. The
 * "jostle" is a randomized per-part wobble, not true collision physics
 * between the parts. Ground contact IS real — each part's path is clamped
 * against actual found ground. Duration comes from SETTINGS.
 */
public class DivineBeamEffect implements KillEffect<DivineBeamEffect.State> {

    @Override
    public State onStart(LivingEntity victim, double x, double y, double z, Random random) {
        float totalSeconds = KillEffectSettings.slider("Beam Duration", 1.8f);
        int totalTicks = Math.max(4, Math.round(totalSeconds * 20.0f));
        int collapseTicks = Math.max(2, Math.round(totalTicks * 0.65f));

        double groundY = findGroundY((ClientWorld) victim.getWorld(), x, y, z);

        // Home offsets roughly match a standing figure (feet at relative y=0).
        // Index 1 is the torso — every other part tethers to it.
        BodyPart[] parts = {
                new BodyPart(0.00, 1.62, 0.00, 0.26f, 1.1f, random),   // 0: head
                new BodyPart(0.00, 1.00, 0.00, 0.34f, 0.9f, random),   // 1: torso
                new BodyPart(-0.32, 1.05, 0.00, 0.15f, 1.3f, random),  // 2: left arm
                new BodyPart(0.32, 1.05, 0.00, 0.15f, 1.3f, random),   // 3: right arm
                new BodyPart(-0.12, 0.30, 0.00, 0.17f, 1.0f, random),  // 4: left leg
                new BodyPart(0.12, 0.30, 0.00, 0.17f, 1.0f, random),   // 5: right leg
        };

        return new State(x, y, z, groundY, parts, collapseTicks, totalTicks);
    }

    private static double findGroundY(ClientWorld world, double x, double y, double z) {
        BlockPos.Mutable pos = new BlockPos.Mutable(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        int bottom = world.getBottomY();
        while (pos.getY() > bottom && world.getBlockState(pos).isAir()) {
            pos.move(Direction.DOWN);
        }
        return pos.getY() + 1.0;
    }

    @Override
    public void render(State state, MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                        Vec3d camPos, float progress, float ageWithDelta, Random random) {
        float elapsedTicks = progress * state.totalTicks;

        KillEffectDraw.beginBlend();

        // Smooth appearance instead of an instant pop, same feel as Void Collapse.
        float fadeIn = MathHelper.clamp(elapsedTicks / 4.0f, 0.0f, 1.0f);

        double[] px = new double[state.parts.length];
        double[] py = new double[state.parts.length];
        double[] pz = new double[state.parts.length];
        float phaseAlpha;
        boolean collapsing = elapsedTicks <= state.collapseTicks;

        if (collapsing) {
            float t = elapsedTicks / state.collapseTicks;
            float pull = t * t; // ease-in: gravity "grabs" harder as it goes
            phaseAlpha = fadeIn;

            for (int i = 0; i < state.parts.length; i++) {
                BodyPart part = state.parts[i];
                double wobbleX = Math.sin(ageWithDelta * part.wobbleSpeed + part.wobblePhase) * 0.18 * (1.0 - pull);
                double wobbleZ = Math.cos(ageWithDelta * part.wobbleSpeed * 0.8f + part.wobblePhase) * 0.18 * (1.0 - pull);

                px[i] = state.x + part.homeX * (1.0 - pull) + wobbleX;
                pz[i] = state.z + part.homeZ * (1.0 - pull) + wobbleZ;
                py[i] = state.y + part.homeY * (1.0 - pull) + pull * 0.9;
                py[i] = Math.max(py[i], state.groundY + part.size * 0.5); // never clip through the floor
            }
        } else {
            float burstT = MathHelper.clamp(
                    (elapsedTicks - state.collapseTicks) / Math.max(1, state.totalTicks - state.collapseTicks), 0.0f, 1.0f);
            float outT = MathHelper.clamp(burstT / 0.8f, 0.0f, 1.0f);
            float outEase = 1.0f - (1.0f - outT) * (1.0f - outT); // ease-out
            phaseAlpha = 1.0f - MathHelper.clamp((burstT - 0.4f) / 0.6f, 0.0f, 1.0f);

            for (int i = 0; i < state.parts.length; i++) {
                BodyPart part = state.parts[i];
                double reach = 1.0 + outEase * 2.2;
                px[i] = state.x + part.homeX * reach;
                pz[i] = state.z + part.homeZ * reach;
                py[i] = state.y + part.homeY + outEase * 1.4;
                py[i] = Math.max(py[i], state.groundY + part.size * 0.5);
            }

            // Core flash at the moment everything converges.
            float coreAlpha = 1.0f - MathHelper.clamp(burstT / 0.35f, 0.0f, 1.0f);
            if (coreAlpha > 0.01f) {
                float coreSize = 0.24f * (1.0f - burstT * 0.6f);
                double flashY = Math.max(state.y, state.groundY + 0.3);
                KillEffectDraw.wireCube(matrices,
                        state.x - camPos.x, flashY - camPos.y, state.z - camPos.z,
                        coreSize, 0.05f, ageWithDelta * 20.0f, ageWithDelta * 14.0f,
                        0.85f, 0.95f, 1.0f, coreAlpha);
            }
        }

        // Tethers: thin lines from the torso (index 1) to every other part —
        // drawn first, underneath the parts themselves.
        if (phaseAlpha > 0.01f) {
            for (int i = 0; i < state.parts.length; i++) {
                if (i == 1) continue;
                drawTether(matrices, camPos, px[1], py[1], pz[1], px[i], py[i], pz[i],
                        0.65f, 0.4f, 1.0f, phaseAlpha * 0.55f);
            }
        }

        // The parts themselves, each with a small orbiting decorative node.
        for (int i = 0; i < state.parts.length; i++) {
            BodyPart part = state.parts[i];
            if (phaseAlpha <= 0.01f) continue;

            float spin = part.spinBase + ageWithDelta * part.spinSpeed * (collapsing ? 1.0f : 1.6f);
            float heat = collapsing ? MathHelper.clamp(elapsedTicks / state.collapseTicks, 0f, 1f) : 1.0f;
            float r = collapsing ? MathHelper.lerp(heat * heat, 0.45f, 0.85f) : 0.7f;
            float g = collapsing ? MathHelper.lerp(heat * heat, 0.15f, 0.95f) : 0.4f;
            float b = collapsing ? MathHelper.lerp(heat * heat, 0.75f, 1.0f) : 1.0f;

            KillEffectDraw.wireCube(matrices,
                    px[i] - camPos.x, py[i] - camPos.y, pz[i] - camPos.z,
                    part.size, 0.03f, spin, spin * 0.6f,
                    r, g, b, phaseAlpha);

            // Decorative node: a small cube orbiting each part, same colour family.
            double orbitR = part.size * 1.4;
            double orbitAngle = ageWithDelta * 6.0 + part.wobblePhase;
            double ox = px[i] + Math.cos(orbitAngle) * orbitR;
            double oy = py[i] + Math.sin(orbitAngle * 1.3) * orbitR * 0.6;
            double oz = pz[i] + Math.sin(orbitAngle) * orbitR;
            KillEffectDraw.wireCube(matrices,
                    ox - camPos.x, oy - camPos.y, oz - camPos.z,
                    part.size * 0.28f, 0.02f, (float) Math.toDegrees(orbitAngle), spin,
                    r, g, b, phaseAlpha * 0.8f);
        }

        KillEffectDraw.endBlend();
    }

    /** A thin stretched wireframe box between two points — the "rope" tether. */
    private static void drawTether(MatrixStack matrices, Vec3d camPos,
                                    double x1, double y1, double z1, double x2, double y2, double z2,
                                    float r, float g, float b, float a) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001) {
            return;
        }
        double midX = (x1 + x2) * 0.5, midY = (y1 + y2) * 0.5, midZ = (z1 + z2) * 0.5;

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        KillEffectDraw.wireCubeStretched(matrices,
                midX - camPos.x, midY - camPos.y, midZ - camPos.z,
                0.05f, (float) (length / 0.05), 0.012f, yaw, pitch,
                r, g, b, a);
    }

    @Override
    public int getDurationTicks(State state) {
        return state.totalTicks;
    }

    static final class BodyPart {
        final double homeX, homeY, homeZ;
        final float size;
        final float wobbleSpeed, wobblePhase;
        final float spinBase, spinSpeed;

        BodyPart(double homeX, double homeY, double homeZ, float size, float wobbleSpeed, Random random) {
            this.homeX = homeX;
            this.homeY = homeY;
            this.homeZ = homeZ;
            this.size = size;
            this.wobbleSpeed = wobbleSpeed;
            this.wobblePhase = random.nextFloat() * 360.0f;
            this.spinBase = random.nextFloat() * 360.0f;
            this.spinSpeed = (random.nextFloat() - 0.5f) * 12.0f;
        }
    }

    static final class State {
        final double x, y, z, groundY;
        final BodyPart[] parts;
        final int collapseTicks, totalTicks;

        State(double x, double y, double z, double groundY, BodyPart[] parts, int collapseTicks, int totalTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.groundY = groundY;
            this.parts = parts;
            this.collapseTicks = collapseTicks;
            this.totalTicks = totalTicks;
        }
    }
}
