package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.ParticleSystem;
import com.lexoravisauls.client.utils.SoundUtil;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinHitSounds {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (target instanceof LivingEntity) {

            // Улучшенная проверка на крит (как в нашем AttackManager)
            boolean isFalling = player.getVelocity().y < 0.0 || player.fallDistance > 0.0f;
            boolean isCrit = isFalling && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS) && !player.hasVehicle();

            // Вызов наших кастомных партиклов!
            ParticleSystem.spawnHitParticles(target.getPos(), isCrit);

            if (LexoraGui.moduleStates.getOrDefault("Hit Sounds", false)) {

                // --- ФИКС ТУТ! Читаем галочку именно от Hit Sounds, а не от Target ESP ---
                boolean onlyCrit = LexoraGui.moduleStates.getOrDefault("Hit Sound Only Crit", false);

                if (!onlyCrit || isCrit) {
                    String mode = LexoraGui.modeSettings.getOrDefault("Hit Sound Mode", "Bubble").toLowerCase();
                    float volume = LexoraGui.numSettings.getOrDefault("Hit Sound Volume", 50.0f) / 100.0f;
                    SoundUtil.playCustomSound("hit_" + mode, volume);
                }
            }
        }
    }
}