package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.StreamerMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DrawContext.class)
public class MixinDrawContextStreamer {

    @ModifyVariable(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Text lexora$filterDrawText(Text text) {
        return StreamerMode.filterText(text);
    }
}