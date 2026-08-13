package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class MixinScoreboardHud {

    /**
     * Подавляем рендер ванильного скорборда (сайдбар справа)
     * когда наш "Scoreboard HUD" включён.
     */
    @Inject(
            method = "renderScoreboardSidebar",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lexora$cancelVanillaScoreboard(DrawContext context,
                                                RenderTickCounter tickCounter,
                                                CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("Scoreboard HUD", false)) {
            ci.cancel();
        }
    }
}