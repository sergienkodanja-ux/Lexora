package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.gui.DrawContext;

public class HudThemeHelper {

    public static boolean isDark() {
        if (ClientData.moduleStates.containsKey("DarkTheme")) {
            return ClientData.moduleStates.get("DarkTheme");
        }
        if (LexoraGui.moduleStates != null && LexoraGui.moduleStates.containsKey("DarkTheme")) {
            return LexoraGui.moduleStates.get("DarkTheme");
        }
        return !ClientData.lightTheme;
    }

    public static int getTextColor(int alpha) {
        boolean dark = isDark();
        int rgb = dark ? 0xF1F1F6 : 0x0C0C10;
        return (alpha << 24) | rgb;
    }

    public static int getSecondaryTextColor(int alpha) {
        boolean dark = isDark();
        // В светлой теме - темный четкий цвет (0x22222E), чтобы метры, UUID и подписи были идеально видны
        int rgb = dark ? 0x8E8E9F : 0x22222E;
        return (alpha << 24) | rgb;
    }

    public static int getIconColor(int alpha) {
        boolean dark = isDark();
        int rgb = dark ? 0xFFFFFF : 0x0C0C10;
        return (alpha << 24) | rgb;
    }

    public static int getSeparatorColor(int alpha) {
        boolean dark = isDark();
        int rgb = dark ? 0x2A2A38 : 0x555566;
        return (alpha << 24) | rgb;
    }

    public static int getSlotBgColor(int alpha) {
        boolean dark = isDark();
        int rgb = dark ? 0x14141C : 0xE4E8F0;
        return ((Math.min(alpha, 230)) << 24) | rgb;
    }

    public static int getBarTrackColor(int alpha) {
        boolean dark = isDark();
        int rgb = dark ? 0x1E1E28 : 0xD5D9E2;
        return (alpha << 24) | rgb;
    }

    public static void drawHudPanel(DrawContext context, float x, float y, float w, float h, float radius, int alpha, boolean blurEnabled) {
        boolean dark = isDark();
        int bgColor;
        int borderCol;

        if (dark) {
            if (blurEnabled) {
                // Чистый темный фон под блюром БЕЗ белого свечения
                bgColor = (Math.min(alpha, 195) << 24) | 0x0A0A0E;
                borderCol = (Math.min(alpha, 40) << 24) | 0x1E1E28;
            } else {
                // 100% непрозрачный черный фон БЕЗ белых окантовок
                bgColor = (alpha << 24) | 0x0A0A0E;
                borderCol = (Math.min(alpha, 35) << 24) | 0x1E1E28;
            }
        } else {
            if (blurEnabled) {
                // Видный четкий белый цвет под блюром (молочное стекло)
                bgColor = (Math.min(alpha, 220) << 24) | 0xFFFFFF;
                borderCol = (Math.min(alpha, 40) << 24) | 0x000000;
            } else {
                // 100% непрозрачный чисто белый фон
                bgColor = (alpha << 24) | 0xFFFFFF;
                borderCol = (Math.min(alpha, 35) << 24) | 0x000000;
            }
        }

        if (blurEnabled && alpha > 10) {
            context.draw();
            try {
                com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(context, x, y, w, h, radius, 15f, bgColor);
            } catch (Throwable ignored) {
                RoundedRectShader.draw(context, x, y, w, h, radius, bgColor);
            }
            RoundedRectShader.draw(context, x - 0.7f, y - 0.7f, w + 1.4f, h + 1.4f, radius + 0.7f, borderCol);
        } else {
            RoundedRectShader.draw(context, x, y, w, h, radius, bgColor);
            RoundedRectShader.draw(context, x - 0.7f, y - 0.7f, w + 1.4f, h + 1.4f, radius + 0.7f, borderCol);
        }
    }
}
