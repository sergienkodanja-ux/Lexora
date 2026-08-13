package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRendererZoom {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lexora$modifyZoomFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(Zoom.modifyFov(cir.getReturnValue()));
    }
}