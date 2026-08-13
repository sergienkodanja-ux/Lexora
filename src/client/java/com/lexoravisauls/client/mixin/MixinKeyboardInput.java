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

    /**
     * Запускается ПОСЛЕ того как KeyboardInput посчитал движение из клавиш.
     * Если идёт свап — обнуляем movementForward чтобы пакет движения
     * совпадал с STOP_SPRINTING и AC не флагал.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void onTick(CallbackInfo ci) {
        if (ItemSwap.isSwapping() || ElytraSwap.isSwapping()) {
            Input self = (Input)(Object)this;
            self.movementForward  = 0f;
            self.movementSideways = 0f;
        }
    }
}