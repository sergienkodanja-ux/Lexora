package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.StreamerMode;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class MixinPlayerListHudNames {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void lexora$filterPlayerName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Text result = cir.getReturnValue();
        cir.setReturnValue(StreamerMode.filterText(result));
    }
}