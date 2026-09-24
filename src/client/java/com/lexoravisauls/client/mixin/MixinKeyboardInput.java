package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.ElytraSwap;
import com.lexoravisauls.client.modules.ItemSwap;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Inject(method = "tick", at = @At("RETURN"))
    private void onTick(CallbackInfo ci) {
        // ── Virtual Desktop In-World Typing (отключаем ходьбу и движения майна) ──
        if (com.lexoravisauls.client.modules.virtualdesktop.VirtualDesktopManager.getInstance().isTyping()) {
            Input self = (Input)(Object) this;
            self.movementForward  = 0f;
            self.movementSideways = 0f;
            if (self instanceof InputAccessor accessor) {
                accessor.setInput(new net.minecraft.util.PlayerInput(false, false, false, false, false, false, false));
            }
            return;
        }

        // ── ElytraSwap ──
        if (ElytraSwap.shouldSuppressMovement()) {
            Input self = (Input)(Object) this;
            self.movementForward  = 0f;
            self.movementSideways = 0f;
            return;
        }

        // ── ItemSwap инвентарь — "отпустил W" ──
        if (ItemSwap.shouldSuppressMovement()) {
            Input self = (Input)(Object) this;
            self.movementForward  = 0f;
            self.movementSideways = 0f;
            if (self instanceof InputAccessor accessor) {
                accessor.setInput(new net.minecraft.util.PlayerInput(false, false, false, false, false, false, false));
            }
        }
    }
}