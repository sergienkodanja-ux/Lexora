package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.virtualdesktop.VirtualDesktopManager;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        VirtualDesktopManager vdm = VirtualDesktopManager.getInstance();
        if (vdm.isEnabled() && vdm.isTyping()) {
            if (vdm.handleKeyboardKey(key, scancode, action, modifiers)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        VirtualDesktopManager vdm = VirtualDesktopManager.getInstance();
        if (vdm.isEnabled() && vdm.isTyping()) {
            if (vdm.handleKeyboardChar(codePoint, modifiers)) {
                ci.cancel();
            }
        }
    }
}
