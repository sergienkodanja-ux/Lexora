package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.Color;

@Mixin(BackgroundRenderer.class)
public class MixinBackgroundRenderer {

    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true)
    private static void onApplyFog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance, boolean thickFog, float tickDelta, CallbackInfoReturnable<Fog> cir) {
        if (com.lexoravisauls.client.modules.Optimization.isNoFog()) {
            cir.setReturnValue(Fog.DUMMY);
            return;
        }


        boolean worldCustomizer = ClientData.moduleStates.getOrDefault("World Customizer",
                LexoraGui.moduleStates.getOrDefault("World Customizer", false));
        boolean fogCustomizer = ClientData.moduleStates.getOrDefault("Fog Customizer",
                LexoraGui.moduleStates.getOrDefault("Fog Customizer", true));

        if (worldCustomizer && fogCustomizer) {
            float customDistance = ClientData.numSettings.getOrDefault("Fog Distance",
                    LexoraGui.numSettings.getOrDefault("Fog Distance", 0.5f));
            float start = viewDistance * 0.05f;
            float end = viewDistance * customDistance;

            String fogColorMode = ClientData.modeSettings.getOrDefault("Fog Color Mode",
                    LexoraGui.modeSettings.getOrDefault("Fog Color Mode", "Theme"));

            float[] hsv;
            if (fogColorMode.equalsIgnoreCase("Theme")) {
                int rgb = LexoraGui.getThemeColor(0f);
                hsv = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            } else {
                hsv = ClientData.colorSettings.getOrDefault("Custom Fog Color",
                        LexoraGui.colorSettings.getOrDefault("Custom Fog Color", new float[]{0.8f, 1f, 1f}));
            }

            int rgbColor = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
            float r = ((rgbColor >> 16) & 0xFF) / 255.0f;
            float g = ((rgbColor >> 8) & 0xFF) / 255.0f;
            float b = (rgbColor & 0xFF) / 255.0f;
            float a = 1.0f;

            cir.setReturnValue(new Fog(start, end, FogShape.SPHERE, r, g, b, a));
        }
    }
}
