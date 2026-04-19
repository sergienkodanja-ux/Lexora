package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.FreeLook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class MixinCamera {
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void modifyCameraRotation(Args args) {
        if (FreeLook.isPerspective) {
            // Подменяем ванильные Yaw и Pitch на координаты нашего Free Look
            args.set(0, FreeLook.cameraYaw);
            args.set(1, FreeLook.cameraPitch);
        }
    }
}