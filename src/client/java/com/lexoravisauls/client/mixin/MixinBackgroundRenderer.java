package com.lexoravisauls.client.mixin;

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

    // 🔥 Теперь всё делается в одном инжекте!
    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true)
    private static void onApplyFog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance, boolean thickFog, float tickDelta, CallbackInfoReturnable<Fog> cir) {
        if (LexoraGui.moduleStates.getOrDefault("World Customizer", false) && LexoraGui.moduleStates.getOrDefault("Fog Customizer", true)) {

            // 1. Считаем дистанцию
            float customDistance = LexoraGui.numSettings.getOrDefault("Fog Distance", 0.5f);
            float start = viewDistance * 0.05f;
            float end = viewDistance * customDistance;

            // 2. Считаем цвет
            float[] hsv;
            if (LexoraGui.modeSettings.getOrDefault("Fog Color Mode", "Theme").equals("Theme")) {
                int rgb = LexoraGui.getGuiThemeColor();
                hsv = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            } else {
                hsv = LexoraGui.colorSettings.getOrDefault("Custom Fog Color", new float[]{0.8f, 1f, 1f});
            }

            int rgbColor = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
            float r = ((rgbColor >> 16) & 0xFF) / 255.0f;
            float g = ((rgbColor >> 8) & 0xFF) / 255.0f;
            float b = (rgbColor & 0xFF) / 255.0f;
            float a = 1.0f; // Прозрачность

            // 3. 🔥 ГЛАВНЫЙ ФИКС: Возвращаем туман с 7 аргументами (старт, конец, форма, R, G, B, A)
            cir.setReturnValue(new Fog(start, end, FogShape.SPHERE, r, g, b, a));
        }
    }
}