package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.Zoom;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MixinMouseZoom {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void lexora$handleZoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client.currentScreen != null) return;
        if (Zoom.onScroll(vertical)) {
            ci.cancel();
        }
    }
}