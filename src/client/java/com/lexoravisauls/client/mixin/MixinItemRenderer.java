package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.Optimization;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @Inject(method = "getArmorGlintConsumer", at = @At("HEAD"), cancellable = true)
    private static void onGetArmorGlintConsumer(VertexConsumerProvider provider, RenderLayer layer, boolean hasGlint, CallbackInfoReturnable<VertexConsumer> cir) {
        if (Optimization.isNoGlint()) {
            cir.setReturnValue(provider.getBuffer(layer));
        }
    }

    @Inject(method = "getItemGlintConsumer", at = @At("HEAD"), cancellable = true)
    private static void onGetItemGlintConsumer(VertexConsumerProvider provider, RenderLayer layer, boolean solid, boolean glint, CallbackInfoReturnable<VertexConsumer> cir) {
        if (Optimization.isNoGlint()) {
            cir.setReturnValue(provider.getBuffer(layer));
        }
    }
}
