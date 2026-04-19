package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.HitColorHandler;
import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelPart.class)
public class MixinModelPart {

    // Внедряемся в параметр color
    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int modifyColor(int color) {
        if (HitColorHandler.isHurt) {
            return HitColorHandler.getColor(color);
        }
        return color;
    }

    // Внедряемся в параметр overlay
    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int modifyOverlay(int overlay) {
        if (HitColorHandler.isHurt) {
            return HitColorHandler.getOverlay(overlay);
        }
        return overlay;
    }
}