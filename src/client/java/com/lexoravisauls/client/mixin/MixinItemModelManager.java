package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.ItemReplacer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelManager.class)
public abstract class MixinItemModelManager {
    @ModifyVariable(
            method = "update",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack lexora$hookModelUpdate(ItemStack stack) {
        return ItemReplacer.getRenderStack(stack);
    }
}
