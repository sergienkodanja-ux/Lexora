package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.ItemSwap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinPlayerSprint {

    /**
     * Аппаратно блокирует спринт (даже если зажат Ctrl, включен AutoSprint или спамится клавиша W),
     * пока ItemSwap совершает переключение предмета.
     */
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void lexora$blockSprintOnSwap(boolean sprinting, CallbackInfo ci) {
        if (sprinting && (Object) this == MinecraftClient.getInstance().player && ItemSwap.isSprintLocked()) {
            ci.cancel();
        }
    }
}