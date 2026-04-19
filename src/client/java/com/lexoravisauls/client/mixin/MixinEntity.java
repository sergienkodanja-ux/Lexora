package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    public void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            if (FreeLook.isPerspective) {
                FreeLook.cameraYaw += (float) cursorDeltaX * 0.15f;
                FreeLook.cameraPitch += (float) cursorDeltaY * 0.15f;
                FreeLook.cameraPitch = Math.max(-90f, Math.min(90f, FreeLook.cameraPitch));
                ci.cancel();
            }
        }
    }
}