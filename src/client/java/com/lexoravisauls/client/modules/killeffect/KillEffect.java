package com.lexoravisauls.client.modules.killeffect;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * A single kill-effect implementation. Everything is hand-drawn each frame —
 * either raw BufferBuilder geometry (see KillEffectDraw) or, for the skin-ghost
 * effect, a direct call into the entity renderer. No vanilla particle types
 * are used anywhere.
 */
public interface KillEffect<T> {

    /**
     * Called once, right when the effect starts. Resolve SETTINGS sliders here
     * (via KillEffectSettings) and bake them into the returned state so render()
     * doesn't re-read settings every frame. The victim reference is kept alive
     * for the rest of the effect's life (needed by the skin-ghost effect).
     */
    T onStart(LivingEntity victim, double x, double y, double z, Random random);

    /**
     * Called every frame while the effect is alive.
     *
     * @param progress     0 at the start, 1 at the end.
     * @param ageWithDelta player.age + tickDelta — same clock TargetESPRenderer/
     *                     MotionClones use, for raw-time animation (sway, spin).
     */
    void render(T state, MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                Vec3d camPos, float progress, float ageWithDelta, Random random);

    /** Total lifetime of this instance, in ticks (20 ticks = 1 second). */
    int getDurationTicks(T state);
}
