package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.events.RoundedRectShader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Общие константы и хелперы внешнего вида Lexora: MSDF-шрифт, тёмная
 * палитра, скруглённые прямоугольники, блендинг цветов, easing-функции.
 *
 * Вынесено отдельно из LexoraMainMenu, чтобы один и тот же стиль можно было
 * использовать и в LexoraButtonWidget, и в LexoraBindsScreen, и в
 * ButtonWidgetMixin (который красит ВСЕ ButtonWidget в игре) — без
 * копипасты палитры в три места.
 *
 * Если у вас в LexoraMainMenu палитра/шрифт когда-нибудь поменяются —
 * меняйте и здесь, чтобы всё оставалось 1:1.
 */
public final class LexoraStyle {
    private LexoraStyle() {}

    private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    public static MsdfFont font() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    // ─── Палитра (как в LexoraMainMenu) ───────────────────────────────────
    public static final int COL_PANEL_BG       = 0x990A0A0A;
    public static final int COL_PANEL_BG2      = 0x99111111;
    public static final int COL_DIVIDER        = 0xFF1A1A1A;
    public static final int COL_BTN_NORMAL     = 0xCC181818;
    public static final int COL_BTN_HOVER      = 0xCC2E2E2E;
    public static final int COL_BTN_DISABLED   = 0x66101010;
    public static final int COL_TEXT_PRIMARY   = 0xFFE8E8E8;
    public static final int COL_TEXT_SECONDARY = 0xFF555555;
    public static final int COL_TEXT_MUTED     = 0xFF333333;
    public static final int COL_TEXT_WHITE     = 0xFFFFFFFF;
    public static final int COL_TEXT_DISABLED  = 0xFF5A5A5A;
    public static final int COL_KEY_BG         = 0x99151515;
    public static final int COL_KEY_HOVER      = 0xAA242424;
    public static final int COL_ACCENT         = 0xFF8A8FFF;
    public static final int COL_FIELD_BG       = 0xAA050505;
    public static final int COL_FIELD_FOCUSED  = 0xAA1A1A1A;

    // ─── Размеры текста ─────────────────────────────────────────────────────
    public static final float SIZE_TITLE = 16f;
    public static final float SIZE_LABEL = 10f;
    public static final float SIZE_SMALL = 8f;
    public static final float SIZE_TINY  = 7f;

    public static int blendColors(int c1, int c2, float r) {
        r = Math.max(0f, Math.min(1f, r));
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * r) << 24) | ((int) (r1 + (r2 - r1) * r) << 16)
                | ((int) (g1 + (g2 - g1) * r) << 8) | (int) (b1 + (b2 - b1) * r);
    }

    public static int adjustAlpha(int color, float alpha) {
        alpha = Math.max(0f, Math.min(1f, alpha));
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static void drawSmoothRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        drawSmoothRect(ctx, x, y, w, h, 6f, color);
    }

    public static void drawSmoothRect(DrawContext ctx, int x, int y, int w, int h, float radius, int color) {
        if (w <= 0 || h <= 0) return;
        RoundedRectShader.draw(ctx, x, y, w, h, radius, color);
    }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static float easeOutQuart(float x) { return 1f - (float) Math.pow(1f - x, 4f); }
    public static float easeOutCubic(float x) { return 1f - (float) Math.pow(1f - x, 3f); }
}