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


        boolean atmosphere = ClientData.moduleStates.getOrDefault("Atmosphere",
                LexoraGui.moduleStates.getOrDefault("Atmosphere", false));
        if (atmosphere) {
            String weatherMode = ClientData.modeSettings.getOrDefault("Weather Visual Mode",
                    LexoraGui.modeSettings.getOrDefault("Weather Visual Mode", "Rain"));
            if (weatherMode.equalsIgnoreCase("Winter")) {
                float radius = ClientData.numSettings.getOrDefault("Winter Radius",
                        LexoraGui.numSettings.getOrDefault("Winter Radius", 24.0f));
                float haze = ClientData.numSettings.getOrDefault("Winter Haze",
                        LexoraGui.numSettings.getOrDefault("Winter Haze", 0.72f));
                float storm = ClientData.numSettings.getOrDefault("Winter Blizzard",
                        LexoraGui.numSettings.getOrDefault("Winter Blizzard", 0.35f));
                float fogDistance = Math.max(18.0f, radius * (2.0f - haze * 0.55f - storm * 0.28f));
                float end = Math.min(fogDistance, viewDistance);
                float start = Math.max(2.0f, end * 0.15f);

                cir.setReturnValue(new Fog(start, end, FogShape.SPHERE, 0.784f, 0.847f, 0.910f, 1.0f));
                return;
            }
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
