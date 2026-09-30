package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.events.JumpCircleRenderer;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "hasStatusEffect", at = @At("HEAD"), cancellable = true)
    private void onHasStatusEffect(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<Boolean> cir) {

        // ==========================================
        // 🔥 NO RENDER: УБИРАЕМ ПЛОХИЕ ЭФФЕКТЫ 🔥
        // ==========================================
        if (LexoraGui.moduleStates.getOrDefault("No Render", false) && LexoraGui.moduleStates.getOrDefault("No Bad Effects", true)) {
            // Проверяем, является ли запрашиваемый эффект слепотой, тьмой или тошнотой
            if (effect.matchesId(StatusEffects.BLINDNESS.getKey().get().getValue()) ||
                    effect.matchesId(StatusEffects.DARKNESS.getKey().get().getValue()) ||
                    effect.matchesId(StatusEffects.NAUSEA.getKey().get().getValue())) {
                cir.setReturnValue(false); // Говорим игре: "Нет, этого эффекта на нас нет!"
                return; // Прерываем выполнение, дальше проверять не нужно
            }
        }

        // ==========================================
        // 🔥 FULL BRIGHT: ФЕЙКОВОЕ НОЧНОЕ ЗРЕНИЕ 🔥
        // ==========================================
        if (LexoraGui.moduleStates.getOrDefault("Full Bright", false)) {
            // Проверяем, совпадает ли запрашиваемый эффект с ключом Night Vision
            if (effect.matchesId(StatusEffects.NIGHT_VISION.getKey().get().getValue())) {
                cir.setReturnValue(true); // Говорим игре: "Да, на нас есть ночное зрение!"
            }
        }
    }

    // 🔥 ФИКС: СКОРОСТЬ АНИМАЦИИ ВЗМАХА 🔥
    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void onGetHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity != mc.player) {
            return;
        }

        boolean inEditor = mc.currentScreen instanceof com.lexoravisauls.client.modules.swinganim.SwingAnimScreen;
        boolean vmEnabled = inEditor
                || com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault("View Model",
                LexoraGui.moduleStates.getOrDefault("View Model", false))
                || com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault("ViewModel",
                LexoraGui.moduleStates.getOrDefault("ViewModel", false));

        if (vmEnabled) {
            String mode = com.lexoravisauls.client.core.ClientData.modeSettings.getOrDefault("VM Anim",
                    LexoraGui.modeSettings.getOrDefault("VM Anim", "Standard"));

            float speed;
            if (inEditor || (com.lexoravisauls.client.modules.VMAnimations.isCustomAnimMode(mode)
                    && (mode.toLowerCase().contains("кастом") || mode.toLowerCase().contains("custom") || mode.toLowerCase().contains("редактор")))) {
                speed = com.lexoravisauls.client.modules.swinganim.SwingManager.getInstance().getSpeed();
            } else {
                speed = com.lexoravisauls.client.core.ClientData.numSettings.getOrDefault("VM Speed",
                        LexoraGui.numSettings.getOrDefault("VM Speed", 1.0f));
            }

            if (speed > 0.05f && Math.abs(speed - 1.0f) > 0.01f) {
                int vanillaDuration = 6;
                if (entity.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE)) {
                    vanillaDuration = 6 - (1 + entity.getStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE).getAmplifier());
                } else if (entity.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE)) {
                    vanillaDuration = 6 + (1 + entity.getStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE).getAmplifier()) * 2;
                }

                int customDuration = Math.max(1, Math.round(vanillaDuration / speed));
                cir.setReturnValue(customDuration);
            }
        }
    }

    @Inject(method = "jump", at = @At("HEAD"))
    private void onJump(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        // Проверяем, что прыгнул именно ТЫ, а не кто-то другой
        if (entity == MinecraftClient.getInstance().player) {
            JumpCircleRenderer.onJump(entity.getX(), entity.getY(), entity.getZ());
        }
    }
}