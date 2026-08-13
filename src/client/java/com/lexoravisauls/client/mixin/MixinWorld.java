package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.awt.Color;

@Mixin(ClientWorld.class)
public class MixinWorld {

    // 🔥 ФИКС: В 1.21.4 метод getSkyColor возвращает int (Integer), а не Vec3d!
    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void onGetSkyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (!LexoraGui.moduleStates.getOrDefault("World Customizer", false)) return;
        if (!LexoraGui.moduleStates.getOrDefault("Sky Customizer", true)) return;

        String skyType = LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard");

        // Шейдерные режимы — чёрный фон, шейдер рисует всё сам
        if (!skyType.equals("Standard")) {
            cir.setReturnValue(0x000000);
            return;
        }

        // Standard — обычная логика цвета
        float[] hsv;
        if (LexoraGui.modeSettings.getOrDefault("Sky Color Mode", "Theme").equals("Theme")) {
            int rgb = LexoraGui.getGuiThemeColor();
            hsv = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        } else {
            hsv = LexoraGui.colorSettings.getOrDefault("Custom Sky Color", new float[]{0.6f, 1f, 1f});
        }

        int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        cir.setReturnValue(rgb & 0xFFFFFF);
    }
}