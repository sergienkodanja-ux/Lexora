package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.ArmorDurabilityColor;
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
        int result = color;

        // Durability-тон брони — накладывается первым
        if (ArmorDurabilityColor.isTinting) {
            result = ArmorDurabilityColor.applyTint(result);
        }

        // Морозный тон инея на броне при зимнем режиме
        if (com.lexoravisauls.client.modules.weather.winter.WinterArmorFrost.isFrostTinting()) {
            result = com.lexoravisauls.client.modules.weather.winter.WinterArmorFrost.applyFrostTint(result);
        }

        // Поверх — вспышка от Hit Color (если оба активны одновременно, например
        // ударили моба в повреждённой броне — тона перемножатся)
        if (HitColorHandler.isHurt) {
            result = HitColorHandler.getColor(result);
        }

        return result;
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
