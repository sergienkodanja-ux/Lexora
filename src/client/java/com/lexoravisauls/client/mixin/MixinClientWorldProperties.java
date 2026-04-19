package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public class MixinClientWorldProperties {

    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void onGetTimeOfDay(CallbackInfoReturnable<Long> cir) {
        if (LexoraGui.moduleStates.getOrDefault("World Customizer", false) && LexoraGui.moduleStates.getOrDefault("Custom Time", true)) {
            // 🔥 ФИКС: Используем .longValue() для правильной конвертации
            cir.setReturnValue(LexoraGui.numSettings.getOrDefault("World Time", 6000f).longValue());
        }
    }
}