package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.cosmetics.IPlayerEntityRenderStateSpearAccess;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.item.SwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Заполняет lexora$holdingSword (добавлено в MixinPlayerEntityRenderStateSpear)
// из живого player — это работает для ЛЮБОГО игрока, включая чужих, потому
// что updateRenderState вызывается движком для каждого рендерящегося игрока
// на экране, а не только для себя. Именно поэтому SpearFeatureRenderer сможет
// узнать "держит ли ЭТОТ конкретный чужой игрок меч прямо сейчас" — без
// всякой сети, мгновенно, потому что Minecraft УЖЕ синхронизирует инвентарь/
// руки всех игроков через стандартный игровой протокол (иначе ты бы вообще
// не видел, что другие держат мечи, луки, еду — это базовая механика игры).
//
// КАСТ (PlayerEntityRenderState) -> (IPlayerEntityRenderStateSpearAccess):
// каст к ИНТЕРФЕЙСУ, не к классу миксина напрямую — прямой каст к классу
// миксина не компилируется ("Mixin class cannot be referenced directly").
// MixinPlayerEntityRenderStateSpear реализует этот интерфейс, а после того
// как Mixin применяет трансформацию байткода, PlayerEntityRenderState
// фактически реализует интерфейс тоже — каст легален и рабочий.
@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerEntityRendererSpear {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void lexora$updateHoldingSword(
            AbstractClientPlayerEntity player,
            PlayerEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (player == null || state == null) {
            return;
        }

        boolean holdingSword = player.getMainHandStack().getItem() instanceof SwordItem;

        ((IPlayerEntityRenderStateSpearAccess) (Object) state).lexora$setHoldingSword(holdingSword);
    }
}