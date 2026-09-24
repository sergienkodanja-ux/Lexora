package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.Optimization;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public class MixinLightmapTextureManager {
    private static long lexora$lastUpdateMs = 0L;
    private static float lexora$lastDelta = -1f;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdateLightmap(float tickDelta, CallbackInfo ci) {
        if (Optimization.isFastLighting()) {
            long now = System.currentTimeMillis();
            if (now - lexora$lastUpdateMs < 33L && Math.abs(tickDelta - lexora$lastDelta) < 0.08f) {
                ci.cancel();
            } else {
                lexora$lastUpdateMs = now;
                lexora$lastDelta = tickDelta;
            }
        }
    }
}
