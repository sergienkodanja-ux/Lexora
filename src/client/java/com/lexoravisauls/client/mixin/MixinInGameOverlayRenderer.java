package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class MixinInGameOverlayRenderer {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        // Проверка: включен ли No Render и галочка No Fire в твоем LexoraGui
        if (LexoraGui.moduleStates.getOrDefault("No Render", false) &&
                LexoraGui.moduleStates.getOrDefault("No Fire", true)) {

            ci.cancel(); // Отменяем рендер огня
        }
    }
}