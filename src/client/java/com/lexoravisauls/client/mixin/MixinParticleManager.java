package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class MixinParticleManager {

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;)V", at = @At("HEAD"), cancellable = true)
    private void onAddEmitter(Entity entity, ParticleEffect parameters, CallbackInfo ci) {
        // Если Майнкрафт пытается заспавнить ванильный эффект тотема...
        if (parameters.getType() == ParticleTypes.TOTEM_OF_UNDYING) {

            // Проверяем настройку блокировки в GUI
            if (LexoraGui.moduleStates.getOrDefault("Particles", false) &&
                    LexoraGui.moduleStates.getOrDefault("Disable Vanilla Totem", true)) {

                ci.cancel(); // Отменяем ванильный эффект!
            }
        }
    }
}