package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.killeffect.KillEffectGhostState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * LivingEntityRenderer#getRenderLayer(S state, boolean showBody, boolean
 * translucent, boolean showOutline) is the actual, official mechanism
 * Minecraft uses to pick between an entity's normal (cutout) and translucent
 * render layer — confirmed present since at least 1.16 through the current
 * version. This forces that `translucent` argument to true for the exact
 * duration of the ghost's render() call, so the ENGINE'S OWN logic picks the
 * real translucent layer, instead of us guessing at which specific
 * RenderLayer factory method to redirect from outside (which is what the
 * previous version of this mixin did, and why it only worked sometimes).
 * <p>
 * `translucent` is the second boolean parameter (ordinal 1 among the method's
 * boolean args): showBody=0, translucent=1, showOutline=2.
 */
@Mixin(net.minecraft.client.render.entity.LivingEntityRenderer.class)
public class RenderLayerGhostMixin {

    @ModifyVariable(method = "getRenderLayer", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private boolean lexoravisuals$forceTranslucent(boolean translucent) {
        return KillEffectGhostState.RENDERING || translucent;
    }
}
