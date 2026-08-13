package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.badge.LexoraModUsers;
import com.lexoravisauls.client.badge.LexoraNameDecorator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerListHud.class)
public abstract class MixinLexoraPlayerListBadge {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void lexora$addBadgeToTab(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (entry == null || entry.getProfile() == null || cir.getReturnValue() == null) {
            return;
        }

        UUID uuid = entry.getProfile().getId();
        String name = entry.getProfile().getName();

        MinecraftClient client = MinecraftClient.getInstance();

        boolean isSelfByUuid = client.player != null
                && client.player.getUuid().equals(uuid);

        boolean isSelfByName = client.player != null
                && client.player.getName().getString().equalsIgnoreCase(name);

        boolean isLexoraUser = LexoraModUsers.has(uuid, name);

        if (isSelfByUuid || isSelfByName || isLexoraUser) {
            cir.setReturnValue(LexoraNameDecorator.decorate(cir.getReturnValue()));
        }
    }
}