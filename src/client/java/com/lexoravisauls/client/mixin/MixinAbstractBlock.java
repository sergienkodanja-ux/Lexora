package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.block.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public abstract class MixinAbstractBlock {

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void onGetRenderType(BlockState state, CallbackInfoReturnable<BlockRenderType> cir) {
        // Проверяем, включена ли функция удаления травы
        if (LexoraGui.moduleStates.getOrDefault("No Render", false) && LexoraGui.moduleStates.getOrDefault("No Grass", true)) {
            Block block = state.getBlock();

            // ShortPlantBlock - обычная трава и папоротники
            // TallPlantBlock - высокая трава
            // FlowerBlock - все мелкие цветы
            if (block instanceof ShortPlantBlock || block instanceof TallPlantBlock || block instanceof FlowerBlock) {
                // Говорим игре, что этот блок вообще не нужно рисовать
                cir.setReturnValue(BlockRenderType.INVISIBLE);
            }
        }
    }
}