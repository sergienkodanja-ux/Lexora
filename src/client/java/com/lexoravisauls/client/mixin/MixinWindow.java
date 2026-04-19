package com.lexoravisauls.client.mixin;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {
    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void onSetTitle(String title, CallbackInfo ci) {
        org.lwjgl.glfw.GLFW.glfwSetWindowTitle(((Window)(Object)this).getHandle(), "Lexora Visuals");
        ci.cancel();
    }
}