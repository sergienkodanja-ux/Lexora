package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.HitColorHandler;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class MixinArmorFeatureRenderer {

    @Inject(method = "render*", at = @At("HEAD"))
    private void onRenderArmorHead(CallbackInfo ci) {
        HitColorHandler.isArmorRendering = true;
    }

    @Inject(method = "render*", at = @At("RETURN"))
    private void onRenderArmorReturn(CallbackInfo ci) {
        HitColorHandler.isArmorRendering = false;
    }
}