package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.ArmorDurabilityColor;
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

// Тот же паттерн, что MixinEntityRenderDispatcher для Hit Color:
// HEAD выставляет стейт по итемстеку, RETURN сбрасывает. Сам цвет накладывается
// в MixinModelPart (см. modifyColor) — там, где Hit Color уже красит packed int color,
// а не через RenderSystem.setShaderColor, как было в прошлой версии этого файла.
@Mixin(ArmorFeatureRenderer.class)
public abstract class MixinArmorFeatureRenderer<S extends BipedEntityRenderState, M extends BipedEntityModel<S>, A extends BipedEntityModel<S>> {

    @Inject(method = "renderArmor", at = @At("HEAD"))
    private void lexora$beginTint(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack, EquipmentSlot slot, int light, A armorModel, CallbackInfo ci) {
        ArmorDurabilityColor.beginTint(stack);
    }

    @Inject(method = "renderArmor", at = @At("RETURN"))
    private void lexora$endTint(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack, EquipmentSlot slot, int light, A armorModel, CallbackInfo ci) {
        ArmorDurabilityColor.endTint();
    }
}