package com.lexoravisauls.client.cosmetic;

import com.lexoravisauls.client.cosmetic.model.CosmeticModel;
import com.lexoravisauls.client.cosmetic.render.CosmeticRenderer;
import com.lexoravisauls.client.render.cape.WaveyCapeFeatureRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

public class CosmeticFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public CosmeticFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbMultiplier) {
        MinecraftClient client = MinecraftClient.getInstance();
        AbstractClientPlayerEntity player = WaveyCapeFeatureRenderer.STATE_TO_PLAYER.get(state);
        if (player == null && client.player != null && state.id == client.player.getId()) {
            player = client.player;
        }
        if (player == null && client.world != null) {
            net.minecraft.entity.Entity entity = client.world.getEntityById(state.id);
            if (entity instanceof AbstractClientPlayerEntity clientPlayer) {
                player = clientPlayer;
            }
        }

        if (player == null || state.spectator || player.isInvisible()) {
            return;
        }

        boolean isLocal = (player == client.player);
        boolean isBot = (com.lexoravisauls.client.utils.FakePlayerManager.fakePlayer != null && player.getId() == com.lexoravisauls.client.utils.FakePlayerManager.fakePlayer.getId());

        List<CosmeticModel> models;
        if (isLocal || isBot) {
            models = CosmeticManager.getInstance().getEquipped3DModels();
        } else {
            String profileName = player.getGameProfile() != null ? player.getGameProfile().getName() : null;
            models = com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.getEquippedModels(profileName);
            if ((models == null || models.isEmpty()) && player.getName() != null) {
                models = com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.getEquippedModels(player.getName().getString());
            }
        }

        if (models == null || models.isEmpty()) {
            return;
        }
        float tickDelta = client.getRenderTickCounter().getTickDelta(true);
        for (CosmeticModel model : models) {
            if (model != null && model.getTextureId() != null) {
                CosmeticRenderer.getInstance().renderCosmetic(model, player, matrices, vertexConsumers, light, this.getContextModel(), tickDelta);
            }
        }
    }
}
