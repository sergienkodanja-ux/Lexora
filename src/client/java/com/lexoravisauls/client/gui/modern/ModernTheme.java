package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.gui.LexoraGui;

public class ModernTheme {

    public static int accent() {
        return 0xFF000000 | LexoraGui.getThemeColor(0.0f);
    }

    public static int accent(float offset) {
        return 0xFF000000 | LexoraGui.getThemeColor(offset);
    }

    public static int bgOverlay() {
        return 0x8A05030A;
    }

    public static int columnBg() {
        return 0xD2141018;
    }

    public static int moduleBg() {
        return 0xC919141E;
    }

    public static int moduleEnabledBg() {
        return 0xD04A2A52;
    }

    public static int settingsBg() {
        return 0xCC120E16;
    }

    public static int text() {
        return 0xFFF1ECF7;
    }

    public static int subText() {
        return 0xFFAAA3B8;
    }

    public static int outline() {
        return 0x30FFFFFF;
    }

    public static int softWhite() {
        return 0x12FFFFFF;
    }

    public static int danger() {
        return 0xFFFF5A5A;
    }

    public static int success() {
        return 0xFF67E58A;
    }
}