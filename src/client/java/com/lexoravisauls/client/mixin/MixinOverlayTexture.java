package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.Color;

@Mixin(OverlayTexture.class)
public class MixinOverlayTexture {

    @Shadow @Final private NativeImageBackedTexture texture;

    private static NativeImageBackedTexture activeTexture;
    private static int lastColor = -1;
    private static long lastUpdateTime = 0;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        activeTexture = this.texture;
    }

    @Inject(method = "getV", at = @At("HEAD"), cancellable = true)
    private static void onGetV(boolean hurt, CallbackInfoReturnable<Integer> cir) {
        if (!hurt || activeTexture == null) return;

        boolean enabled = LexoraGui.moduleStates.getOrDefault("Hit Color", false);
        long time = System.currentTimeMillis();

        int targetColor = 0xFF0000;

        if (enabled) {
            String mode = LexoraGui.modeSettings.getOrDefault("Hit Color Mode", "Theme");
            if (mode.equals("Custom")) {
                float[] hsv = LexoraGui.colorSettings.getOrDefault("Hit Custom Color", new float[]{0f, 1f, 1f});
                targetColor = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
            } else {
                targetColor = LexoraGui.getThemeColor(0);
            }
        }

        // Обновляем не чаще раза в полсекунды (снижает лаги) или при смене цвета
        if (targetColor != lastColor || (enabled && time - lastUpdateTime > 500)) {
            lastColor = targetColor;
            lastUpdateTime = time;

            int r = Math.min(255, (int) (((targetColor >> 16) & 0xFF) * 4.0f));
            int g = Math.min(255, (int) (((targetColor >> 8) & 0xFF) * 4.0f));
            int b = Math.min(255, (int) ((targetColor & 0xFF) * 4.0f));
            int argb = (255 << 24) | (r << 16) | (g << 8) | b;

            // ФИКС: Выполняем загрузку текстуры в основном OpenGL потоке
            RenderSystem.recordRenderCall(() -> {
                NativeImage image = activeTexture.getImage();
                if (image != null) {
                    for (int y = 8; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            image.setColorArgb(x, y, argb);
                        }
                    }
                    activeTexture.bindTexture();
                    activeTexture.upload();
                }
            });
        }
    }
}