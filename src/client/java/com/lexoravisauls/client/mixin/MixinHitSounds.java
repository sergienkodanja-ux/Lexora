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
        if (!(target instanceof LivingEntity)) return;

        boolean isFalling = player.getVelocity().y < 0.0 || player.fallDistance > 0.0f;
        boolean isCrit = isFalling
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.hasVehicle();

        ParticleSystem.onAttack(target, isCrit);

        if (!LexoraGui.moduleStates.getOrDefault("Hit Sounds", false)) return;

        boolean onlyCrit = LexoraGui.moduleStates.getOrDefault("Hit Sound Only Crit", false);
        if (onlyCrit && !isCrit) return;

        String mode = LexoraGui.modeSettings.getOrDefault("Hit Sound Mode", "Crime");
        float volume = LexoraGui.numSettings.getOrDefault("Hit Sound Volume", 50.0f) / 100.0f;

        String soundEventName;
        switch (mode) {
            case "Bubble":
                soundEventName = "hit_bubble";
                break;
            case "Metallic":
                soundEventName = "hit_metallic";
                break;
            case "Bell":
                soundEventName = "hit_bell";
                break;
            case "Bonk":
                soundEventName = "hit_bonk";
                break;
            // --- НОВЫЕ ЗВУКИ ---
            case "Hit 1":
                soundEventName = "hit1";
                break;
            case "Hit 2":
                soundEventName = "hit2";
                break;
            case "Hit 3":
                soundEventName = "hit3";
                break;
            case "UwU":
                soundEventName = "uwu";
                break;
            case "Moan 1":
                soundEventName = "moan1";
                break;
            case "Moan 2":
                soundEventName = "moan2";
                break;
            case "Moan 3":
                soundEventName = "moan3";
                break;
            case "Moan 4":
                soundEventName = "moan4";
                break;
            case "Pop":
                soundEventName = "pop";
                break;
            // -------------------
            case "Crime":
            default:
                soundEventName = "hit_crime";
                break;
        }

        SoundUtil.playCustomSound(soundEventName, volume);
    }
}