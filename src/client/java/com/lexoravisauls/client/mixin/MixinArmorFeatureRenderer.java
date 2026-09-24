package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.ArmorDurabilityColor;
import com.lexoravisauls.client.modules.Optimization;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class MixinArmorFeatureRenderer<S extends BipedEntityRenderState, M extends BipedEntityModel<S>, A extends BipedEntityModel<S>> {

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private void lexora$beginTint(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack, EquipmentSlot slot, int light, A armorModel, CallbackInfo ci) {
        if (Optimization.isNoArmor()) {
            ci.cancel();
            return;
        }
        ArmorDurabilityColor.beginTint(stack);
    }

    @Inject(method = "renderArmor", at = @At("RETURN"))
    private void lexora$endTint(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack, EquipmentSlot slot, int light, A armorModel, CallbackInfo ci) {
        ArmorDurabilityColor.endTint();
    }
}
