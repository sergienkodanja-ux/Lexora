package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.cosmetics.IPlayerEntityRenderStateSpearAccess;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Добавляет кастомное поле "держит ли игрок ванильный меч в главной руке" в
// стандартный PlayerEntityRenderState. Это НАДЁЖНЫЙ способ передать данные
// из живого AbstractClientPlayerEntity (где точно есть проверенный,
// документированный player.getMainHandStack()) в render-состояние, которое
// видит SpearFeatureRenderer для ЧУЖИХ игроков (там доступен только state,
// не живой player).
//
// РЕАЛИЗУЕТ IPlayerEntityRenderStateSpearAccess — это то, что позволяет
// другому коду (MixinPlayerEntityRendererSpear, SpearFeatureRenderer)
// легально получить доступ к полю lexora$holdingSword через каст к
// интерфейсу, а не к самому классу миксина напрямую (прямой каст к классу
// миксина не компилируется — "Mixin class cannot be referenced directly").
@Mixin(PlayerEntityRenderState.class)
public class MixinPlayerEntityRenderStateSpear implements IPlayerEntityRenderStateSpearAccess {

    @Unique
    private boolean lexora$holdingSword = false;

    @Override
    public boolean lexora$isHoldingSword() {
        return lexora$holdingSword;
    }

    @Override
    public void lexora$setHoldingSword(boolean value) {
        this.lexora$holdingSword = value;
    }
}