package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.LexoraSkyRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    static {
        LexoraSkyRenderer.ensureRegistered();
    }

    // Отменяем стандартный черный контур при активном Block Overlay
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onDrawBlockOutline(
            net.minecraft.client.util.math.MatrixStack matrices,
            VertexConsumer vertexConsumer,
            Entity entity,
            double cameraX, double cameraY, double cameraZ,
            BlockPos pos,
            BlockState state,
            int color,
            CallbackInfo ci
    ) {
        if (LexoraGui.moduleStates.getOrDefault("Block Overlay", false)) {
            ci.cancel();
        }
    }

    // Оптимизация: отключение 3D облаков
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void onRenderClouds(
            FrameGraphBuilder frameGraphBuilder,
            org.joml.Matrix4f modelMatrix,
            org.joml.Matrix4f projectionMatrix,
            net.minecraft.client.option.CloudRenderMode cloudRenderMode,
            net.minecraft.util.math.Vec3d cameraPos,
            float tickDelta,
            int color,
            float cloudHeight,
            CallbackInfo ci
    ) {
        if (com.lexoravisauls.client.modules.Optimization.isNoClouds()) {
            ci.cancel();
        }
    }

    // No Render: Погода
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void onRenderWeather(
            FrameGraphBuilder frameGraphBuilder,
            net.minecraft.util.math.Vec3d pos,
            float tickDelta,
            Fog fog,
            CallbackInfo ci
    ) {
        if ((LexoraGui.moduleStates.getOrDefault("No Render", false) && LexoraGui.moduleStates.getOrDefault("No Weather", true))
                || com.lexoravisauls.client.modules.Optimization.isFastWeather()) {
            ci.cancel();
        }
    }
}
