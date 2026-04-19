package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.ItemHighlighter;
import com.lexoravisauls.client.modules.HealingHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public class MixinDrawContext {

    // --- Отрисовка подсветки предметов (Инвентарь) ---
    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;III)V", at = @At("RETURN"))
    private void onDrawItem1(ItemStack item, int x, int y, int seed, CallbackInfo ci) {
        if (item != null && !item.isEmpty()) {
            if (HealingHelper.getHighlightColor(item.getItem()) != null) {
                HealingHelper.renderHighlight((DrawContext) (Object) this, item.getItem(), x, y);
            } else {
                ItemHighlighter.renderHighlight((DrawContext) (Object) this, item.getItem(), x, y);
            }
        }
    }

    // --- Отрисовка подсветки предметов (Хотбар, Креатив) ---
    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V", at = @At("RETURN"))
    private void onDrawItem2(net.minecraft.entity.LivingEntity entity, net.minecraft.world.World world, ItemStack item, int x, int y, int seed, int z, CallbackInfo ci) {
        if (item != null && !item.isEmpty()) {
            if (HealingHelper.getHighlightColor(item.getItem()) != null) {
                HealingHelper.renderHighlight((DrawContext) (Object) this, item.getItem(), x, y);
            } else {
                ItemHighlighter.renderHighlight((DrawContext) (Object) this, item.getItem(), x, y);
            }
        }
    }
}