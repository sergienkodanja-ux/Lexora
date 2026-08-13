package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.emotion.EmotionManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeFeatureRenderer.class)
public abstract class MixinCapeFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public MixinCapeFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
            at = @At("HEAD"))
    private void onCapeRenderHead(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (state.name == null) return;

        if (EmotionManager.playingEmotions.containsKey(state.name)) {
            PlayerEntityModel model = this.getContextModel();

            matrices.push();
            // Нативно привязываем плащ к повороту и позиции туловища
            model.body.rotate(matrices);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
            at = @At("RETURN"))
    private void onCapeRenderReturn(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (state.name == null) return;

        if (EmotionManager.playingEmotions.containsKey(state.name)) {
            matrices.pop();
        }
    }
}