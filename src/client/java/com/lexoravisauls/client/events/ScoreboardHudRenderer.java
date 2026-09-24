package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.modules.StreamerMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ScoreboardHudRenderer {

    private static final int   PAD_X      = 8;
    private static final int   PAD_Y      = 8;
    private static final int   MIN_W      = 110;
    private static final int   ROW_H      = 11;
    private static final int   HEADER_H   = 20;
    private static final float RADIUS     = 6f;

    private static final int BG_RGB        = 0x101015;
    private static final int OUTLINE_RGB   = 0x3D3D46;
    private static final int TEXT_WHITE    = 0xF1F1F5;

    private static final float HEADER_FONT_SIZE = 8.0f;
    private static final float ROW_FONT_SIZE    = 7.5f;

    // ─── Кастомный MSDF-шрифт (с авто-фоллбеком на ванильный для чужих символов) ───
    private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    private static MsdfFont font() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    private static float hudAlpha  = 0f;
    private static float animScale = 0f;
    private static float animVel   = 0f;

    private static float animW = MIN_W;
    private static float animH = HEADER_H + PAD_Y;

    // ── Снапшот реальных экранных координат — обновляется каждый кадр рендера ──
    // Используется MixinChatScreen для точного хитбокса без пересчётов
    public static int lastRenderedX = 0;
    public static int lastRenderedY = 0;
    public static int lastRenderedW = 0;
    public static int lastRenderedH = 0;

    private ScoreboardHudRenderer() {}

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (!LexoraGui.moduleStates.getOrDefault("Scoreboard HUD", false)) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = getDisplayObjective(scoreboard);

        boolean preview = mc.currentScreen instanceof LexoraGui
                || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;

        boolean hasData = objective != null || preview;

        hudAlpha += ((hasData ? 1f : 0f) - hudAlpha) * 0.20f;

        float targetScale = hasData ? 1f : 0f;
        float tension     = hasData ? 0.30f : 0.18f;
        float dampening   = hasData ? 0.58f : 0.42f;
        animVel   += (targetScale - animScale) * tension;
        animVel   *= dampening;
        animScale += animVel;

        if (!hasData && hudAlpha < 0.02f && Math.abs(animScale) < 0.02f) {
            animScale = 0f;
            return;
        }

        int alpha = MathHelper.clamp((int)(hudAlpha * 255f), 0, 255);
        if (alpha < 3) return;

        List<ScoreEntry> entries = new ArrayList<>();
        String title = "Scoreboard";

        if (objective != null) {
            title = objective.getDisplayName().getString();
            Collection<ScoreboardEntry> scores = scoreboard.getScoreboardEntries(objective);

            scores.stream()
                    .sorted((a, b) -> Integer.compare(b.value(), a.value()))
                    .limit(15)
                    .forEach(e -> {
                        Text displayText = e.display();
                        if (displayText == null) {
                            Team team = scoreboard.getScoreHolderTeam(e.owner());
                            displayText = Team.decorateName(team, Text.literal(e.owner()));
                        }
                        entries.add(new ScoreEntry(displayText, e.value()));
                    });
        } else {
            title = "Scoreboard";
            entries.add(new ScoreEntry(Text.literal("Player1"), 42));
            entries.add(new ScoreEntry(Text.literal("Player2"), 31));
            entries.add(new ScoreEntry(Text.literal("Player3"), 17));
        }

        float smooth = 0.15f;
        int targetW = MIN_W;
        int titleW  = (int) mixedWidth(title, HEADER_FONT_SIZE) + PAD_X * 2 + 4;
        if (titleW > targetW) targetW = titleW;
        for (ScoreEntry e : entries) {
            // ОЧИСТКА: Берем строку, срезаем префикс и считаем ширину по чистой строке
            String cleanedStr = cleanServerPrefix(e.display.getString());
            int lineW = (int) mixedWidth(cleanedStr, ROW_FONT_SIZE) + PAD_X * 2;
            if (lineW > targetW) targetW = lineW;
        }

        int targetH = HEADER_H + entries.size() * ROW_H + PAD_Y + 4;
        boolean showHome = LexoraGui.moduleStates.getOrDefault(
                "Scoreboard HUD Home Indicator", true);
        if (showHome) targetH += 7;

        // ─── ФИКС ДРАГА: В режиме редактирования отключаем анимацию размеров ───
        // Это фиксит телепортации и дрожание хитбокса при перетаскивании
        if (preview) {
            animW = targetW;
            animH = targetH;
            hudAlpha = 1f;
            animScale = 1f;
            animVel = 0f;
        } else {
            animW += (targetW - animW) * smooth;
            animH += (targetH - animH) * smooth;
        }

        int w = Math.round(animW);
        int h = Math.round(animH);

        int baseX = (HudManager.scoreboardX < 0)
                ? mc.getWindow().getScaledWidth() - w - 4
                : HudManager.scoreboardX;
        int baseY = (HudManager.scoreboardY < 0)
                ? 4
                : HudManager.scoreboardY;

        // ── Сохраняем снапшот ДО применения userScale ─────────────────────────
        float userScale    = LexoraGui.numSettings.getOrDefault("Scoreboard HUD Scale", 1.0f);
        float displayScale = Math.max(0.001f, animScale);
        float finalScale   = userScale * displayScale;

        // Вычисляем реальный hitbox с учётом масштабирования от пивота
        float pivotX = baseX + w / 2f;
        float pivotY = baseY + h / 2f;
        float scaledW = w * finalScale;
        float scaledH = h * finalScale;
        float scaledX = pivotX - scaledW / 2f;
        float scaledY = pivotY - scaledH / 2f;

        lastRenderedX = Math.round(scaledX);
        lastRenderedY = Math.round(scaledY);
        lastRenderedW = Math.round(scaledW);
        lastRenderedH = Math.round(scaledH);
        // ──────────────────────────────────────────────────────────────────────

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.getMatrices().push();
        context.getMatrices().translate(pivotX, pivotY, 400f);
        context.getMatrices().scale(finalScale, finalScale, 1f);
        context.getMatrices().translate(-pivotX, -pivotY, 0f);

        drawPanel(context, baseX, baseY, w, h, alpha);
        drawHeader(context, baseX, baseY, w, title, alpha);
        drawRows(context, mc, baseX, baseY, w, entries, alpha);

        if (showHome) {
            drawHomeIndicator(context, baseX, baseY, w, h, alpha);
        }

        context.draw();
        context.getMatrices().pop();

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Убирает мусорные префиксы серверов вроде "01r", "02", "15a" из начала строки.
     */
    private static String cleanServerPrefix(String s) {
        if (s == null) return "";
        // Регулярка: срезает цифры в начале и одну букву сразу после них (если есть)
        return s.replaceFirst("^[0-9]+[a-zA-Z]?", "").trim();
    }

    private static void drawPanel(DrawContext ctx, int x, int y, int w, int h, int alpha) {
    boolean blur = ClientData.moduleStates.containsKey("Scoreboard HUD Blur") ? ClientData.moduleStates.get("Scoreboard HUD Blur") : LexoraGui.moduleStates.getOrDefault("Scoreboard HUD Blur", false);
    HudThemeHelper.drawHudPanel(ctx, x, y, w, h, RADIUS, alpha, blur);
}

private static void drawHeader(DrawContext ctx, int x, int y, int w,
                                   String title, int alpha) {
        long time = System.currentTimeMillis();
        float wave = (float)(Math.sin(time / 500.0) * 0.5 + 0.5);
        int accent = lerpRgb(getThemeColor1(), getThemeColor2(), smoothStep(wave));
        int titleColor = (alpha << 24) | (accent & 0xFFFFFF);

        int tw = (int) mixedWidth(title, HEADER_FONT_SIZE);
        int tx = x + (w - tw) / 2;
        drawMixedRun(ctx, title, tx, y + 6, HEADER_FONT_SIZE, titleColor, false);

        int lineAlpha = Math.min(alpha, 75);
        RoundedRectShader.draw(ctx,
                x + 6, y + HEADER_H - 2, w - 12, 1.2f, 0.6f,
                HudThemeHelper.getSeparatorColor(lineAlpha));
    }

    private static void drawRows(DrawContext ctx, MinecraftClient mc,
                                 int x, int y, int w,
                                 List<ScoreEntry> entries, int alpha) {
        int rowY = y + HEADER_H + 2;

        for (int i = 0; i < entries.size(); i++) {
            ScoreEntry e = entries.get(i);

            Text filteredName = StreamerMode.filterText(e.display);

            // ОЧИСТКА: Применяем удаление префикса к тексту перед отрисовкой
            String rawStr = filteredName.getString();
            String cleanedStr = cleanServerPrefix(rawStr);

            // Пересобираем Text, чтобы сохранить цвета команд, но убрать "01r"
            Text finalName = Text.literal(cleanedStr).setStyle(filteredName.getStyle());

            drawMixedText(ctx, finalName, x + PAD_X, rowY, ROW_FONT_SIZE,
                    HudThemeHelper.getTextColor(alpha), false);

            rowY += ROW_H;
        }
    }

    private static void drawHomeIndicator(DrawContext ctx,
                                          int x, int y, int w, int h, int alpha) {
        int pillW = Math.max(34, Math.min(48, (int)(w * 0.28f)));
        float pillH = 5f;
        float pillX = x + w / 2f - pillW / 2f;
        float pillY = y + h - 7f;

        int pillAlpha = Math.min(245, (int)(alpha * 0.92f));
        drawPerfectCapsule(ctx, pillX, pillY, pillW, pillH,
                (pillAlpha << 24) | 0xFFFFFF);
    }

    /** @deprecated Используй lastRenderedW — учитывает реальный масштаб */
    public static float getAnimW() {
        return animW;
    }

    /** @deprecated Используй lastRenderedH — учитывает реальный масштаб */
    public static float getAnimH() {
        return animH;
    }

    // =========================================================================
    //  СМЕШАННЫЙ ШРИФТ: кастомный MSDF только для своих символов, иначе ванильный
    // =========================================================================

    private static float drawMixedRun(DrawContext ctx, String text, float x, float y,
                                      float size, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return x;
        MinecraftClient mc = MinecraftClient.getInstance();
        MsdfFont mf = font();

        StringBuilder run = new StringBuilder();
        Boolean runIsCustom = null;
        float cursor = x;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int cc = Character.charCount(cp);
            boolean custom = mf.hasGlyph(cp);

            if (runIsCustom == null) runIsCustom = custom;
            if (custom != runIsCustom) {
                cursor = flushMixedRun(ctx, mc, run.toString(), runIsCustom, cursor, y, size, color, shadow);
                run.setLength(0);
                runIsCustom = custom;
            }
            run.appendCodePoint(cp);
            i += cc;
        }
        if (run.length() > 0) {
            cursor = flushMixedRun(ctx, mc, run.toString(), runIsCustom, cursor, y, size, color, shadow);
        }
        return cursor;
    }

    private static float flushMixedRun(DrawContext ctx, MinecraftClient mc, String run, boolean custom,
                                       float x, float y, float size, int color, boolean shadow) {
        if (run.isEmpty()) return x;
        if (custom) {
            font().draw(ctx.getMatrices(), run, x, y, size, color);
            return x + font().getWidth(run, size);
        } else {
            ctx.drawText(mc.textRenderer, run, (int) x, (int) y, color, shadow);
            return x + mc.textRenderer.getWidth(run);
        }
    }

    /** Замер ширины строки с учётом того, что часть символов уйдёт в ванильный шрифт. */
    private static float mixedWidth(String text, float size) {
        if (text == null || text.isEmpty()) return 0f;
        MinecraftClient mc = MinecraftClient.getInstance();
        MsdfFont mf = font();

        float total = 0f;
        StringBuilder run = new StringBuilder();
        Boolean runIsCustom = null;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int cc = Character.charCount(cp);
            boolean custom = mf.hasGlyph(cp);

            if (runIsCustom == null) runIsCustom = custom;
            if (custom != runIsCustom) {
                total += runIsCustom ? mf.getWidth(run.toString(), size) : mc.textRenderer.getWidth(run.toString());
                run.setLength(0);
                runIsCustom = custom;
            }
            run.appendCodePoint(cp);
            i += cc;
        }
        if (run.length() > 0) {
            total += runIsCustom ? mf.getWidth(run.toString(), size) : mc.textRenderer.getWidth(run.toString());
        }
        return total;
    }

    private static float drawMixedText(DrawContext ctx, Text text, float x, float y,
                                       float size, int fallbackColor, boolean shadow) {
        float[] cursor = {x};
        text.visit((style, str) -> {
            if (!str.isEmpty()) {
                TextColor tc = style.getColor();
                int color = (fallbackColor & 0xFF000000) | (tc != null ? (tc.getRgb() & 0xFFFFFF) : (fallbackColor & 0xFFFFFF));
                cursor[0] = drawMixedRun(ctx, str, cursor[0], y, size, color, shadow);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return cursor[0];
    }

    private static ScoreboardObjective getDisplayObjective(Scoreboard scoreboard) {
        try {
            return scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void rr(DrawContext ctx, float x, float y, float w, float h,
                           float r, int color) {
        if (w <= 0 || h <= 0) return;
        RoundedRectShader.draw(ctx, x, y, w, h, r, color);
    }

    private static void drawPerfectCapsule(DrawContext ctx,
                                           float x, float y, float w, float h, int color) {
        if (w <= 0f || h <= 0f) return;
        float r = h / 2f;
        if (w <= h) { rr(ctx, x, y, w, h, r, color); return; }
        rr(ctx, x + r,      y, w - h, h, 0f, color);
        rr(ctx, x,          y, h,     h, r,  color);
        rr(ctx, x + w - h,  y, h,     h, r,  color);
    }

    private static int clampAlpha(float v) {
        return MathHelper.clamp((int) v, 0, 255);
    }

    private static float smoothStep(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static int lerpRgb(int c1, int c2, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r = (int)(((c1>>16)&0xFF) + (((c2>>16)&0xFF)-((c1>>16)&0xFF))*t);
        int g = (int)(((c1>> 8)&0xFF) + (((c2>> 8)&0xFF)-((c1>> 8)&0xFF))*t);
        int b = (int)(( c1     &0xFF) + (( c2     &0xFF)-( c1     &0xFF))*t);
        return (r<<16)|(g<<8)|b;
    }

    private static int getThemeColor1() {
        if (com.lexoravisauls.client.core.ClientData.colorSettings
                .containsKey("Theme Color 1")) {
            float[] hsv = com.lexoravisauls.client.core.ClientData
                    .colorSettings.get("Theme Color 1");
            return java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        }
        if (LexoraGui.numSettings.containsKey("Theme Color")) {
            return LexoraGui.numSettings.get("Theme Color").intValue() & 0xFFFFFF;
        }
        return 0x22B8FF;
    }

    private static int getThemeColor2() {
        if (com.lexoravisauls.client.core.ClientData.colorSettings
                .containsKey("Theme Color 2")) {
            float[] hsv = com.lexoravisauls.client.core.ClientData
                    .colorSettings.get("Theme Color 2");
            return java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    private record ScoreEntry(Text display, int score) {}
}