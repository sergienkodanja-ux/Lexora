package com.lexoravisauls.client.cosmetics;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

public class WingsFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public WingsFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float headYaw, float headPitch) {
        // Если игрок в инвизе — не рендерим косметику
        if (state.invisible) {
            return;
        }

        if (state.name == null || state.name.isEmpty()) {
            return;
        }
        String playerName = state.name;

        if (!com.lexoravisauls.client.badge.LexoraModUsers.hasName(playerName)) {
            return;
        }

        CosmeticsManager.ensureFetched(playerName);

        String wings = CosmeticsManager.getEquipped(playerName, "back");
        if (!"angel_wings".equals(wings)) return;

        matrices.push();
        this.getContextModel().body.rotate(matrices);
        WingsRenderer.renderWings(matrices, vertexConsumers, light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, state.age, state.sneaking, false);
        matrices.pop();
    }
}