package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.HitWave;
import com.lexoravisauls.client.modules.ShiftTap;
import com.lexoravisauls.client.modules.PvPSave;
import com.lexoravisauls.client.utils.AttackManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {

        boolean isFalling = player.getVelocity().y < 0.0 || player.fallDistance > 0.0f;
        boolean isCrit = !player.isOnGround()
                && isFalling
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasVehicle();

        AttackManager.registerHit(target, isCrit);
        HitWave.addWave(target, isCrit);
        ShiftTap.onHit(isCrit);
    }
}