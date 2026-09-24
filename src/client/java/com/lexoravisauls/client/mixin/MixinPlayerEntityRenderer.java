package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.NimbRenderer;
import com.lexoravisauls.client.utils.ChinaHatFeatureRenderer;
import com.lexoravisauls.client.utils.FakePlayerManager;
import com.lexoravisauls.client.render.cape.WaveyCapeFeatureRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerEntityRenderer extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {

    public MixinPlayerEntityRenderer(EntityRendererFactory.Context ctx, PlayerEntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        this.addFeature(new ChinaHatFeatureRenderer(this));
        this.addFeature(new NimbRenderer(this));

        this.features.removeIf(feature -> feature instanceof CapeFeatureRenderer);

        // Передаем (this), каст в 1.21.4 обычно не нужен, если типы в Renderer совпадают
        this.addFeature(new WaveyCapeFeatureRenderer(this));
        this.addFeature(new com.lexoravisauls.client.cosmetic.CosmeticFeatureRenderer(this));
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void onUpdateRenderState(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        boolean isMe = (player == MinecraftClient.getInstance().player);
        boolean isBot = (FakePlayerManager.fakePlayer != null && player.getId() == FakePlayerManager.fakePlayer.getId());

        ChinaHatFeatureRenderer.isRenderingLocalPlayer = (isMe || isBot);

        // ИСПРАВЛЕНО: Теперь кладем данные в мапу, которая лежит в WaveyCapeFeatureRenderer
        WaveyCapeFeatureRenderer.STATE_TO_PLAYER.put(state, player);
    }
}