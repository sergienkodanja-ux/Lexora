package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.cosmetic.CosmeticManager;
import com.lexoravisauls.client.utils.FakePlayerManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class MixinPlayerCape {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity)(Object)this;
        boolean isMe = (self == client.player || (self.getUuid() != null && self.getUuid().equals(client.player.getUuid())));
        boolean isBot = (FakePlayerManager.fakePlayer != null && self.getId() == FakePlayerManager.fakePlayer.getId());

        if (!isMe && !isBot) {
            return;
        }

        CosmeticManager cm = CosmeticManager.getInstance();
        SkinTextures baseSkin = (isBot && client.player != null) ? client.player.getSkinTextures() : cir.getReturnValue();

        if (cm.isCustomCapeEnabled()) {
            Identifier customCape = cm.getActiveCapeTexture();
            if (customCape != null) {
                SkinTextures customSkin = new SkinTextures(
                        baseSkin.texture(),
                        baseSkin.textureUrl(),
                        customCape,
                        null,
                        baseSkin.model(),
                        baseSkin.secure()
                );
                cir.setReturnValue(customSkin);
                return;
            }
        }

        if (isBot && baseSkin != null) {
            cir.setReturnValue(baseSkin);
        }
    }
}