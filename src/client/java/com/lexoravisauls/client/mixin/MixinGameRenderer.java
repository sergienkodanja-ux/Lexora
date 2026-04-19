package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    // --- FULL BRIGHT (Ночное зрение на максимум) ---
    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void onGetNightVisionStrength(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (LexoraGui.moduleStates.getOrDefault("Full Bright", false)) {
            cir.setReturnValue(1.0f); // 1.0f - максимальная яркость
        }
    }

    // --- ASPECT RATIO ---
    @Redirect(method = "getBasicProjectionMatrix", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getFramebufferWidth()I"))
    private int redirectWidth(Window window) {
        if (LexoraGui.moduleStates.getOrDefault("Aspect Ratio", false)) {
            String mode = LexoraGui.modeSettings.getOrDefault("Ratio Mode", "Default");
            if (!mode.equals("Default")) {
                return 10000;
            }
        }
        return window.getFramebufferWidth();
    }

    @Redirect(method = "getBasicProjectionMatrix", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getFramebufferHeight()I"))
    private int redirectHeight(Window window) {
        if (LexoraGui.moduleStates.getOrDefault("Aspect Ratio", false)) {
            String mode = LexoraGui.modeSettings.getOrDefault("Ratio Mode", "Default");
            if (!mode.equals("Default")) {
                float ratio = 1.0f;
                if (mode.equals("4:3")) ratio = 4.0f / 3.0f;
                else if (mode.equals("16:9")) ratio = 16.0f / 9.0f;
                else if (mode.equals("16:10")) ratio = 16.0f / 10.0f;
                else if (mode.equals("Custom")) ratio = LexoraGui.numSettings.getOrDefault("Aspect Ratio Val", 1.33f);

                return (int) (10000 / ratio);
            }
        }
        return window.getFramebufferHeight();
    }

    // ==========================================
    // 🔥 NO RENDER: УБИРАЕМ ТОТЕМ И ТРЯСКУ 🔥
    // ==========================================

    // Убираем летящий тотем по центру экрана
    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void onShowFloatingItem(ItemStack floatingItem, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("No Render", false) && LexoraGui.moduleStates.getOrDefault("No Totem Anim", true)) {
            if (floatingItem.isOf(Items.TOTEM_OF_UNDYING)) {
                ci.cancel();
            }
        }
    }

    // Убираем тряску экрана (Hurt Cam) при получении урона
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void onTiltView(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("No Render", false) && LexoraGui.moduleStates.getOrDefault("No Hurt Cam", true)) {
            ci.cancel();
        }
    }
}