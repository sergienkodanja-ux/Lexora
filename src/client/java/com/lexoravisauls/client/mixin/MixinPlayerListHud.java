package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.StreamerMode;
import com.lexoravisauls.client.utils.TabAnimState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class MixinPlayerListHud {

    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(DrawContext context, int windowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        TabAnimState.lastScoreboard = scoreboard;
        TabAnimState.lastObjective = objective;

        if (!LexoraGui.moduleStates.getOrDefault("Animations", true)
                || !LexoraGui.moduleStates.getOrDefault("Anim Tab", true)) {
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(0, TabAnimState.animY, 400.0f);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void postRender(DrawContext context, int windowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("Animations", true)
                && LexoraGui.moduleStates.getOrDefault("Anim Tab", true)) {
            context.getMatrices().pop();
        }
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void lexora$filterTabName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Text result = cir.getReturnValue();
        if (result != null) {
            cir.setReturnValue(StreamerMode.filterText(result));
        }
    }
}