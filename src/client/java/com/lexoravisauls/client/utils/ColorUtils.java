package com.lexoravisauls.utils;

import java.awt.Color;

public class ColorUtils {

    // Получение градиента между двумя цветами в зависимости от времени
    public static int getGradient(int color1, int color2, long time, float speed) {
        float wave = (float) ((Math.sin(time / (500.0f / speed)) + 1.0f) / 2.0f);

        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a1 = (color1 >> 24) & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;

        int r = (int) (r1 + (r2 - r1) * wave);
        int g = (int) (g1 + (g2 - g1) * wave);
        int b = (int) (b1 + (b2 - b1) * wave);
        int a = (int) (a1 + (a2 - a1) * wave);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Перевод HSB в обычный RGB (удобно для LexoraGui.themeColor1Hue)
    public static int getThemeColor(float hue) {
        return Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }
}