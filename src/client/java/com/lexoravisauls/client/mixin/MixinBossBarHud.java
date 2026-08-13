package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.events.PvpBossBarTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public class MixinBossBarHud {

    /**
     * Перехватываем render() босс-бара.
     * Если PvpBossBarTracker говорит что это PVP-бар — отменяем рендер.
     */
    @Inject(
            method = "render",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lexora_onRender(DrawContext context, CallbackInfo ci) {
        // Если трекер нашёл PVP босс-бар — скрываем ВЕСЬ BossBarHud рендер
        // (обычно PVP-бар единственный или самый важный)
        if (PvpBossBarTracker.isInPvp()) {
            ci.cancel();
        }
    }
}