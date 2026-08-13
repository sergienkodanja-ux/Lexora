package com.lexoravisauls.client.modules.killeffect;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.Random;

/**
 * Draws every active kill effect once per frame. Registered from
 * {@link KillEffectManager#init()} — nothing else to call here.
 * <p>
 * Confirmed via Fabric API docs: WorldRenderContext.matrixStack() is only
 * non-null from AFTER_ENTITIES onward, and consumers() is the exact
 * VertexConsumerProvider the world renderer itself uses for entities — that's
 * what the skin-ghost effect renders into.
 */
final class KillEffectRenderer {

    private static final Random RANDOM = new Random();

    private KillEffectRenderer() {
    }

    static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(KillEffectRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        if (KillEffectManager.ACTIVE.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        Camera camera = context.camera();
        VertexConsumerProvider consumers = context.consumers();
        Vec3d camPos = camera.getPos();
        float tickDelta = context.tickCounter().getTickDelta(true);
        float ageWithDelta = client.player.age + tickDelta;

        Iterator<KillEffectManager.ActiveEffect<?>> it = KillEffectManager.ACTIVE.iterator();
        while (it.hasNext()) {
            KillEffectManager.ActiveEffect<?> active = it.next();
            float elapsed = ageWithDelta - active.startAge;
            float progress = Math.min(1.0f, Math.max(0.0f, elapsed / active.durationTicks));

            active.render(matrices, camera, consumers, camPos, progress, ageWithDelta, RANDOM);

            if (elapsed >= active.durationTicks) {
                it.remove();
            }
        }
    }
}
