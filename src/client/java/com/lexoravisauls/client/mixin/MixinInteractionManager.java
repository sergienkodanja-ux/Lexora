package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.utils.FakePlayerManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinInteractionManager {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        // Проверяем совпадение по нашему отрицательному ID
        if (FakePlayerManager.fakePlayer != null && target.getId() == FakePlayerManager.fakePlayer.getId()) {

            // Запускаем всю физику и анимацию
            FakePlayerManager.handleAttack(player);

            // Отменяем ванильный код, чтобы клиент не отправлял пакет об ударе на сервер
            // (иначе сервер бы кикнул за удары по несуществующему игроку)
            ci.cancel();
        }
    }
}