package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.badge.LexoraModUsers;
import com.lexoravisauls.client.badge.LexoraNameDecorator;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class MixinLexoraPlayerBadgeRenderer {

    private boolean lexora$isSelfNametagsEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Self Nametags", false)
                || LexoraGui.moduleStates.getOrDefault("Self Nametag", false)
                || LexoraGui.moduleStates.getOrDefault("Self NameTags", false)
                || LexoraGui.moduleStates.getOrDefault("Self Name Tags", false);
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void lexora$addBadgeAboveHead(
            AbstractClientPlayerEntity player,
            PlayerEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (player == null || state == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        String name = player.getName().getString();

        boolean isSelfByUuid = client.player != null
                && client.player.getUuid().equals(player.getUuid());

        boolean isSelfByName = client.player != null
                && client.player.getName().getString().equalsIgnoreCase(name);

        boolean isSelf = isSelfByUuid || isSelfByName;
        boolean selfNametags = isSelf && lexora$isSelfNametagsEnabled();

        boolean isLexoraUser = LexoraModUsers.has(player.getUuid(), name);

        /*
         * Если это ты и Self Nametags включён — принудительно ставим displayName.
         * Без этого Minecraft часто не рисует ник над самим собой.
         */
        if (selfNametags && state.displayName == null) {
            Text display = player.getDisplayName();

            if (display == null) {
                display = player.getName();
            }

            state.displayName = display;
        }

        if (!isSelf && !isLexoraUser) {
            return;
        }

        if (state.displayName != null) {
            state.displayName = LexoraNameDecorator.decorate(state.displayName);
        }
    }
}