package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.render.OverlayTexture;
import java.awt.Color;

public class HitColorHandler {
    public static boolean isHurt = false;
    public static float hurtPercent = 0.0f;
    public static boolean isArmorRendering = false;

    public static int getColor(int originalColor) {
        if (!LexoraGui.moduleStates.getOrDefault("Hit Color", false)) return originalColor;

        String target = LexoraGui.modeSettings.getOrDefault("Hit Target", "All");
        if (target.equals("Body Only") && isArmorRendering) {
            return originalColor;
        }

        String mode = LexoraGui.modeSettings.getOrDefault("Hit Color Mode", "Theme");
        int customRGB;

        if (mode.equals("Theme")) {
            customRGB = LexoraGui.getThemeColor(0);
        } else {
            float[] custom = LexoraGui.colorSettings.getOrDefault("Hit Custom Color", new float[]{0f, 1f, 1f});
            customRGB = Color.HSBtoRGB(custom[0], custom[1], custom[2]);
        }

        float r = ((customRGB >> 16) & 0xFF) / 255f;
        float g = ((customRGB >> 8) & 0xFF) / 255f;
        float b = (customRGB & 0xFF) / 255f;

        // Делаем альфу агрессивнее, чтобы цвет пробивался даже через темные текстуры
        float alpha = hurtPercent * 0.85f;

        int origA = (originalColor >> 24) & 0xFF;
        int origR = (originalColor >> 16) & 0xFF;
        int origG = (originalColor >> 8) & 0xFF;
        int origB = originalColor & 0xFF;

        // Жесткое смешивание: заставляем пиксели стать нашего цвета
        int finalR = (int) (origR * (1.0f - alpha) + (r * 255.0f) * alpha);
        int finalG = (int) (origG * (1.0f - alpha) + (g * 255.0f) * alpha);
        int finalB = (int) (origB * (1.0f - alpha) + (b * 255.0f) * alpha);

        // Защита от переполнения
        finalR = Math.min(255, Math.max(0, finalR));
        finalG = Math.min(255, Math.max(0, finalG));
        finalB = Math.min(255, Math.max(0, finalB));

        return (origA << 24) | (finalR << 16) | (finalG << 8) | finalB;
    }

    public static int getOverlay(int originalOverlay) {
        if (!LexoraGui.moduleStates.getOrDefault("Hit Color", false)) return originalOverlay;

        String target = LexoraGui.modeSettings.getOrDefault("Hit Target", "All");
        if (target.equals("Body Only") && isArmorRendering) {
            return originalOverlay;
        }

        return OverlayTexture.DEFAULT_UV;
    }
}