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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (player == null && mc.player != null && state.id == mc.player.getId()) {
            player = mc.player;
        }
        if (player == null && mc.world != null) {
            net.minecraft.entity.Entity entity = mc.world.getEntityById(state.id);
            if (entity instanceof AbstractClientPlayerEntity clientPlayer) {
                player = clientPlayer;
            }
        }
        if (player == null) return;

        boolean isLocal = (player == mc.player
                || (mc.player != null && player.getUuid().equals(mc.player.getUuid()))
                || (com.lexoravisauls.client.utils.FakePlayerManager.fakePlayer != null && player.getId() == com.lexoravisauls.client.utils.FakePlayerManager.fakePlayer.getId()));
        net.minecraft.util.Identifier capeTex = null;

        if (isLocal) {
            if (com.lexoravisauls.client.cosmetic.CosmeticManager.getInstance().isCustomCapeEnabled()) {
                capeTex = com.lexoravisauls.client.cosmetic.CosmeticManager.getInstance().getActiveCapeTexture();
            }
        } else {
            String profileName = player.getGameProfile() != null ? player.getGameProfile().getName() : null;
            capeTex = com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.getCapeTexture(profileName);
            if (capeTex == null && player.getName() != null) {
                capeTex = com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.getCapeTexture(player.getName().getString());
            }
        }

        if (capeTex == null) {
            capeTex = state.skinTextures.capeTexture();
        }

        if (player.isInvisible() || capeTex == null) {
            return;
        }

        if (isWearingElytra(state, player)) {
            return;
        }

        if (player instanceof CapeHolder capeHolder) {
            StickSimulation sim = capeHolder.getSimulation();
            if (sim != null && (sim.empty() || sim.getPoints().size() < 16)) {
                capeHolder.updateSimulation(player, 16);
            }
        }

        StickSimulation simulation = ((CapeHolder) player).getSimulation();
        if (simulation == null || simulation.getPoints().size() < 16) {
            return;
        }

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(capeTex));
        float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);

        matrices.push();
        this.getContextModel().body.rotate(matrices);
        geometry.renderSmoothCape(matrices, buffer, light, player, tickDelta, simulation, capeTex);
        matrices.pop();
    }

    public static boolean isWearingElytra(PlayerEntityRenderState state, AbstractClientPlayerEntity player) {
        if (state != null && state.equippedChestStack != null && !state.equippedChestStack.isEmpty()) {
            if (isElytra(state.equippedChestStack)) {
                return true;
            }
        }
        if (player != null) {
            ItemStack chestplate = player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestplate != null && !chestplate.isEmpty() && isElytra(chestplate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isElytra(ItemStack stack) {
        if (stack.isOf(Items.ELYTRA)) {
            return true;
        }
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null && equippable.slot() == EquipmentSlot.CHEST) {
            if (equippable.assetId().isPresent()) {
                String path = equippable.assetId().get().getValue().getPath().toLowerCase();
                if (path.contains("elytra") || path.contains("wings")) {
                    return true;
                }
            }
        }
        return false;
    }
}