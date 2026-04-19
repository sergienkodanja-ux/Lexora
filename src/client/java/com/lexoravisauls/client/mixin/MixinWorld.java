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
        if (LexoraGui.moduleStates.getOrDefault("World Customizer", false) && LexoraGui.moduleStates.getOrDefault("Sky Customizer", true)) {
            float[] hsv;
            if (LexoraGui.modeSettings.getOrDefault("Sky Color Mode", "Theme").equals("Theme")) {
                int rgb = LexoraGui.getGuiThemeColor();
                hsv = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            } else {
                hsv = LexoraGui.colorSettings.getOrDefault("Custom Sky Color", new float[]{0.6f, 1f, 1f});
            }

            // Получаем финальный цвет
            int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);

            // Возвращаем чистое число (int) без альфа-канала, как требует новая версия
            cir.setReturnValue(rgb & 0xFFFFFF);
        }
    }
}