package com.lexoravisauls.client.mixin;

import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ElytraFeatureRenderer.class)
public class MixinElytraFeatureRenderer {

    /**
     * Отвязываем элитры от плащей:
     * Возвращая null, мы заставляем ванильный EquipmentRenderer рендерить
     * стандартную чистую текстуру элитр (textures/entity/equipment/wings/elytra.png)
     * без узоров и искажений от кастомных плащей.
     */
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private static void onGetTexture(BipedEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
        cir.setReturnValue(null);
    }
}
