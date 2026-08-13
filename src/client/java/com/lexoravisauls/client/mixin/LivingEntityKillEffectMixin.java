package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.killeffect.KillEffectManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the kill effect the instant a living entity's death starts.
 * <p>
 * This mixin (through KillEffectManager) touches client-only classes. If your
 * mod is entirely client-side already (likely, given everything lives under a
 * "client" package), just register it wherever your other mixins go. If your
 * mod also ships server-side code, make sure this one specifically sits in the
 * CLIENT-ONLY section of your mixin config so a dedicated server never loads it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityKillEffectMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void lexoravisuals$onDeath(DamageSource damageSource, CallbackInfo ci) {
        KillEffectManager.trigger((LivingEntity) (Object) this, damageSource);
    }
}
