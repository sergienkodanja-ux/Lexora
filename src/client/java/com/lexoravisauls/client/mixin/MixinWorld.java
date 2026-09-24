package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.core.ClientData;
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

    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void onGetSkyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        boolean worldCustomizer = ClientData.moduleStates.getOrDefault("World Customizer",
                LexoraGui.moduleStates.getOrDefault("World Customizer", false));
        if (!worldCustomizer) return;

        boolean skyCustomizer = ClientData.moduleStates.getOrDefault("Sky Customizer",
                LexoraGui.moduleStates.getOrDefault("Sky Customizer", true));
        if (!skyCustomizer) return;

        String skyType = ClientData.modeSettings.getOrDefault("Sky Type",
                LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard"));

        // Шейдерные режимы — чёрный фон, шейдер рисует всё сам
        if (!skyType.equals("Standard")) {
            cir.setReturnValue(0xFF000000);
            return;
        }

        // Standard — обычная логика цвета
        String colorMode = ClientData.modeSettings.getOrDefault("Sky Color Mode",
                LexoraGui.modeSettings.getOrDefault("Sky Color Mode", "Theme"));

        float[] hsv;
        if (colorMode.equalsIgnoreCase("Theme")) {
            int rgb = LexoraGui.getThemeColor(0f);
            hsv = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        } else {
            hsv = ClientData.colorSettings.getOrDefault("Custom Sky Color",
                    ClientData.colorSettings.getOrDefault("Sky Custom Color",
                    LexoraGui.colorSettings.getOrDefault("Custom Sky Color", new float[]{0.6f, 1f, 1f})));
        }

        int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        cir.setReturnValue(0xFF000000 | (rgb & 0xFFFFFF));
    }
}
