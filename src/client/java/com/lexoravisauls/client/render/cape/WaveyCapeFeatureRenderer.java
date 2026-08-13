package com.lexoravisauls.client.render.cape;

import com.lexoravisauls.client.render.cape.sim.CapeHolder;
import com.lexoravisauls.client.render.cape.sim.StickSimulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Map;
import java.util.WeakHashMap;

public class WaveyCapeFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    // ПЕРЕНЕСЛИ МАПУ СЮДА (теперь ошибки Mixin не будет)
    public static final Map<PlayerEntityRenderState, AbstractClientPlayerEntity> STATE_TO_PLAYER = new WeakHashMap<>();

    private final WaveyCapeGeometry geometry = new WaveyCapeGeometry();

    public WaveyCapeFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbMultiplier) {
        // Достаем игрока из мапы, которая теперь лежит в этом же классе
        AbstractClientPlayerEntity player = STATE_TO_PLAYER.get(state);
        if (player == null) return;

        if (player.isInvisible() || state.skinTextures.capeTexture() == null) {
            return;
        }

        ItemStack chestplate = player.getEquippedStack(EquipmentSlot.CHEST);
        if (chestplate.isOf(Items.ELYTRA)) {
            return;
        }

        StickSimulation simulation = ((CapeHolder) player).getSimulation();
        if (simulation == null || simulation.getPoints().size() < 16) {
            return;
        }

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(state.skinTextures.capeTexture()));
        float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);

        geometry.renderSmoothCape(matrices, buffer, light, player, tickDelta, simulation);
    }
}