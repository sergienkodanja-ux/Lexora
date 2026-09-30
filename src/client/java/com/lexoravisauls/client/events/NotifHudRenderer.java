package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.modern.ModernGuiRender;
import com.lexoravisauls.client.utils.NotifManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;

import java.util.List;
import java.util.Locale;

public class NotifHudRenderer {

    // Геометрия по стилю wedal.dlc
    public static final int   HEIGHT         = 17;
    public static final float HEIGHT_F       = 16.5f;
    public static final int   WIDTH          = 165; // Базовое значение для обратной совместимости

    public static final float PAD_X          = 8f;
    public static final float CORNER         = 5f;
    public static final float BAR_W          = 3f;
    public static final float BAR_PAD        = 5f;
    public static final float BAR_VERT_PAD   = 3.5f;
    public static final float GAP_Y          = 3.5f;

    public static final float TOGGLE_WIDTH        = 20f;
    public static final float TOGGLE_HEIGHT       = 10f;
    public static final float TOGGLE_CIRCLE_SIZE  = 7f;
    public static final float TOGGLE_PADDING      = 1.5f;
    public static final float TOGGLE_CORNER       = TOGGLE_HEIGHT * 0.5f;

    public static final int BG_COLOR      = 0xDC0F0F0F; // RGBA(15, 15, 15, 220)
    public static final int TEXT_COLOR    = 0xFFE6E6E6; // RGBA(230, 230, 230, 255)
    public static final int ENABLED_COL   = 0xFF50DC64; // RGBA(80, 220, 100, 255)
    public static final int DISABLED_COL  = 0xFFDC4141; // RGBA(220, 65,  65,  255)
    public static final int TOGGLE_OFF_BG = 0xFF323237; // RGBA(50, 50,  55,  255)
    public static final int TOGGLE_CIRCLE = 0xFFFFFFFF; // RGBA(255, 255, 255, 255)

    private static final float FONT_SIZE = 7.5f;

    // Иконки уведомлений (PNG 4 штуки: info, success, danger, error)
    private static final float ICON_SIZE = 11f;
    private static final float ICON_PAD = 4f;
    private static final Identifier ICON_INFO    = Identifier.of("lexoravisauls", "textures/notif/info.png");
    private static final Identifier ICON_SUCCESS = Identifier.of("lexoravisauls", "textures/notif/success.png");
    private static final Identifier ICON_DANGER  = Identifier.of("lexoravisauls", "textures/notif/danger.png");
    private static final Identifier ICON_ERROR   = Identifier.of("lexoravisauls", "textures/notif/error.png");

    private static final Identifier FONT_TEX = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    private static float previewAnim = 0.0f;
    private static long lastRenderTime = System.currentTimeMillis();

    private static MsdfFont getFont() {
        if (msdfFont == null) {
            msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        }
        return msdfFont;
    }

    public static float calcPreviewWidth() {
        String basePart = "Module «Notifications»  ";
        String statePart = "Enabled";
        float textW = width(basePart, FONT_SIZE) + width(statePart, FONT_SIZE);
        float toggleSpace = TOGGLE_WIDTH + 7f;
        // PAD_X (слева) + toggleSpace + textW + безопасный отступ 14px до полоски + BAR_W + BAR_PAD
        return PAD_X + toggleSpace + textW + 14f + BAR_W + BAR_PAD;
    }

    public static int getPreviewWidth() {
        return Math.max(140, Math.round(calcPreviewWidth()));
    }

    public static int getVisualX(int screenWidth) {
        float scale = LexoraGui.numSettings.getOrDefault("Notifications Scale", 1.0f);
        int scaledW = Math.round(getPreviewWidth() * scale);
        if (HudManager.notifX < 0) {
            // По умолчанию центрируем по горизонтали экрана
            return Math.max(10, (screenWidth - scaledW) / 2);
        }
        return HudManager.notifX;
    }

    public static int getVisualY(int screenHeight) {
        if (HudManager.notifY < 0) {
            return 30; // Вверху по центру экрана
        }
        return HudManager.notifY;
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        boolean enabled = com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault("Notifications", LexoraGui.moduleStates.getOrDefault("Notifications", true));
        boolean showPreview = mc.currentScreen instanceof ChatScreen
                || mc.currentScreen instanceof com.lexoravisauls.client.gui.modern.ModernClickGui
                || mc.currentScreen instanceof LexoraGui;

        if (!enabled && !showPreview) return;

        long now = System.currentTimeMillis();
        float deltaTime = (lastRenderTime > 0) ? Math.min(0.1f, (now - lastRenderTime) / 1000f) : 0.016f;
        lastRenderTime = now;

        List<NotifManager.Notif> activeNotifs = NotifManager.getNotifs();
        if (activeNotifs.isEmpty() && !showPreview) {
            previewAnim = 0.0f;
            return;
        }

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        float scale = com.lexoravisauls.client.core.ClientData.numSettings.getOrDefault("Notifications Scale", LexoraGui.numSettings.getOrDefault("Notifications Scale", 1.0f));
        boolean blurEnabled = com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault("Notifications Blur", LexoraGui.moduleStates.getOrDefault("Notifications Blur", true));

        int baseX = getVisualX(screenWidth);
        int baseY = getVisualY(screenHeight);

        RenderSystem.enableBlend();

        // 1. Превью в чате, если нет активных уведомлений (позволяет перетаскивать HUD)
        if (activeNotifs.isEmpty()) {
            previewAnim += (1.0f - previewAnim) * Math.min(1.0f, 10.0f * deltaTime);
            if (previewAnim > 0.02f) {
                renderPreviewCard(context, baseX, baseY, scale, previewAnim, blurEnabled);
            }
            RenderSystem.disableBlend();
            return;
        }

        previewAnim = 0.0f;

        // 2. Отрисовка очереди реальных уведомлений (центрированных по центральной оси)
        float targetY = baseY;
        float centerAnchorX = baseX + (getPreviewWidth() * scale) / 2.0f;

        for (int i = 0; i < activeNotifs.size(); i++) {
            NotifManager.Notif notif = activeNotifs.get(i);

            // Анимация плавного появления сверху на место (~280мс, кривая ease-out cubic)
            notif.appear = Math.min(1.0f, notif.appear + 3.6f * deltaTime);
            float appearProgress = 1.0f - (float) Math.pow(1.0f - notif.appear, 3);

            // Анимация переключателя тоггла (10f lerp)
            float targetToggle = notif.moduleEnabled ? 1.0f : 0.0f;
            notif.toggleProgress += (targetToggle - notif.toggleProgress) * Math.min(1.0f, 10.0f * deltaTime);

            long age = now - notif.startTime;
            float timerProgress = Math.max(0.0f, 1.0f - (float) age / notif.maxTime);

            // Плавная прозрачность: при появлении быстро становится видимым (в первые 100мс),
            // чтобы игрок отчетливо видел само плавное падение сверху на место
            float appearAlpha = Math.min(1.0f, notif.appear * 2.5f);
            float fadeProgress = 0.0f;
            float alpha = appearAlpha;
            if (age > notif.maxTime - notif.fadeTime) {
                float fadeRem = (float) (notif.maxTime - age) / (float) notif.fadeTime;
                fadeProgress = Math.max(0.0f, Math.min(1.0f, 1.0f - fadeRem));
                alpha = (1.0f - fadeProgress) * appearAlpha;
            }
            alpha = Math.max(0.0f, Math.min(1.0f, alpha));
            if (alpha <= 0.01f) {
                targetY += (HEIGHT * scale) + (GAP_Y * scale);
                continue;
            }

            // Плавное перемещение по вертикали (12f lerp)
            if (!notif.initializedY) {
                notif.currentY = targetY;
                notif.initializedY = true;
            }
            float diff = targetY - notif.currentY;
            if (Math.abs(diff) > 0.05f) {
                notif.currentY += diff * Math.min(1.0f, 12.0f * deltaTime);
            } else {
                notif.currentY = targetY;
            }

            float entryW = calcWidth(notif, age);
            float cardScaledW = entryW * scale;
            // Оцентрирование каждого уведомления по центру (не в строку)
            float x = centerAnchorX - cardScaledW / 2.0f;

            // Анимация смещения по вертикали:
            // 1) При появлении отчетливо падает сверху на место (26px * scale)
            float appearOffset = (1.0f - appearProgress) * 26.0f * scale;
            // 2) При пропадании плавно опускается вниз с приятным замедлением
            float sinkEase = fadeProgress * (2.0f - fadeProgress);
            float disappearOffset = 14.0f * sinkEase * scale;
            float dy = notif.currentY - appearOffset + disappearOffset;

            renderCard(context, x, dy, entryW, HEIGHT, scale, alpha, blurEnabled, notif, timerProgress, age, appearProgress, fadeProgress);

            targetY += (HEIGHT * scale) + (GAP_Y * scale);
        }

        RenderSystem.disableBlend();
    }

    private static void renderCard(DrawContext context, float x, float y, float width, float height,
                                   float scale, float alpha, boolean blurEnabled,
                                   NotifManager.Notif notif, float timerProgress, long age,
                                   float appearProgress, float fadeProgress) {

        float popScale = (0.92f + 0.08f * appearProgress) * (1.0f - 0.04f * fadeProgress);
        float cardScaledW = width * scale;
        float cardScaledH = height * scale;
        float centerX = x + cardScaledW * 0.5f;
        float centerY = y + cardScaledH * 0.5f;

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(scale * popScale, scale * popScale, 1.0f);
        context.getMatrices().translate(-width * 0.5f, -height * 0.5f, 0);

        int bgCol = setAlpha(BG_COLOR, alpha);
        int borderCol = setAlpha(0x5A28282D, alpha);

        // 1. Фон плашки (жидкое стекло при блюре либо скругленный прямоугольник)
        if (blurEnabled && alpha > 0.05f) {
            try {
                ModernGuiRender.drawLiquidGlass(context, 0, 0, width, height, CORNER, 12f, bgCol);
            } catch (Throwable ignored) {
                RoundedRectShader.draw(context, 0, 0, width, height, CORNER, bgCol);
            }
            RoundedRectShader.drawOutline(context, 0, 0, width, height, CORNER, 0.7f, borderCol);
        } else {
            RoundedRectShader.draw(context, 0, 0, width, height, CORNER, bgCol);
            RoundedRectShader.drawOutline(context, 0, 0, width, height, CORNER, 0.7f, borderCol);
        }

        // 2. Содержимое: Модуль (тумблер + текст) либо Кастомное (только чистый текст без иконок)
        if (notif.isModuleToggle) {
            // Мини переключатель-тумблер слева
            float toggleX = PAD_X;
            float toggleY = (height - TOGGLE_HEIGHT) * 0.5f;

            int toggleBgColor = interpolateRgb(TOGGLE_OFF_BG, ENABLED_COL, notif.toggleProgress);
            RoundedRectShader.draw(context, toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                    TOGGLE_CORNER, setAlpha(toggleBgColor, alpha));

            // Круглый ползунок внутри тумблера
            float circleX = toggleX + TOGGLE_PADDING +
                    (TOGGLE_WIDTH - TOGGLE_PADDING * 2 - TOGGLE_CIRCLE_SIZE) * notif.toggleProgress;
            float circleY = toggleY + (TOGGLE_HEIGHT - TOGGLE_CIRCLE_SIZE) * 0.5f;
            RoundedRectShader.draw(context, circleX, circleY, TOGGLE_CIRCLE_SIZE, TOGGLE_CIRCLE_SIZE,
                    TOGGLE_CIRCLE_SIZE * 0.5f, setAlpha(TOGGLE_CIRCLE, alpha));

            // Текст: Module «Название»  Enabled / Disabled
            float textStartX = toggleX + TOGGLE_WIDTH + 7f;
            float textY = (height - FONT_SIZE) * 0.5f + 0.5f;

            String basePart = "Module «" + notif.moduleName + "»  ";
            String statePart = notif.moduleEnabled ? "Enabled" : "Disabled";

            int txtCol = setAlpha(TEXT_COLOR, alpha);
            int stateCol = setAlpha(notif.moduleEnabled ? ENABLED_COL : DISABLED_COL, alpha);

            drawString(context, basePart, textStartX, textY, FONT_SIZE, txtCol);
            drawString(context, statePart, textStartX + width(basePart, FONT_SIZE), textY, FONT_SIZE, stateCol);

        } else {
            // Кастомные уведомления: иконка слева + текст
            boolean showIcons = com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault("Notif Icons",
                    com.lexoravisauls.client.gui.LexoraGui.moduleStates.getOrDefault("Notif Icons", true));
            float textStartX;
            if (showIcons) {
                // Рисуем иконку слева
                Identifier iconTex = getIconForType(notif.type);
                float iconX = PAD_X;
                float iconY = (height - ICON_SIZE) * 0.5f;
                // drawTexture: region (u,v,regionW,regionH) из текстуры (texW,texH) отрисовывается по координатам (x,y) с размером (w,h)
                context.drawTexture(RenderLayer::getGuiTextured, iconTex,
                        (int) iconX, (int) iconY, 0f, 0f,
                        (int) ICON_SIZE, (int) ICON_SIZE,
                        (int) ICON_SIZE, (int) ICON_SIZE);
                textStartX = PAD_X + ICON_SIZE + ICON_PAD;
            } else {
                textStartX = PAD_X;
            }
            float textY = (height - FONT_SIZE) * 0.5f + 0.5f;

            String text = getDisplayText(notif, age);
            int txtCol = setAlpha(TEXT_COLOR, alpha);

            drawString(context, text, textStartX, textY, FONT_SIZE, txtCol);
        }

        // 3. Вертикальный индикатор времени справа (убывающий сверху вниз)
        boolean disabled = notif.isModuleToggle && !notif.moduleEnabled;
        int barColor = disabled ? DISABLED_COL : (notif.isModuleToggle ? ENABLED_COL : getNotifAccentColor(notif));

        float barX = width - BAR_PAD - BAR_W;
        float barInnerH = height - BAR_VERT_PAD * 2f;
        float barCurH = barInnerH * timerProgress;
        float barBottom = BAR_VERT_PAD + barInnerH;
        float barStartY = barBottom - barCurH;
        if (barCurH > 0.5f) {
            int barCol = setAlpha(barColor, alpha);
            RoundedRectShader.draw(context, barX, barStartY, BAR_W, barCurH, BAR_W * 0.5f, barCol);
        }

        context.getMatrices().pop();
    }

    private static void renderPreviewCard(DrawContext context, float x, float y, float scale,
                                          float alpha, boolean blurEnabled) {
        float previewW = calcPreviewWidth();
        float previewProgress = 1.0f - (float) Math.pow(1.0f - previewAnim, 3);
        float previewDrop = (1.0f - previewProgress) * 18.0f * scale;

        float cardScaledW = previewW * scale;
        float cardScaledH = HEIGHT * scale;
        float centerX = x + cardScaledW * 0.5f;
        float centerY = (y - previewDrop) + cardScaledH * 0.5f;
        float popScale = 0.92f + 0.08f * previewProgress;

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(scale * popScale, scale * popScale, 1.0f);
        context.getMatrices().translate(-previewW * 0.5f, -HEIGHT * 0.5f, 0);

        int bgCol = setAlpha(BG_COLOR, alpha);
        int borderCol = setAlpha(0x5A28282D, alpha);

        if (blurEnabled && alpha > 0.05f) {
            try {
                ModernGuiRender.drawLiquidGlass(context, 0, 0, previewW, HEIGHT, CORNER, 12f, bgCol);
            } catch (Throwable ignored) {
                RoundedRectShader.draw(context, 0, 0, previewW, HEIGHT, CORNER, bgCol);
            }
            RoundedRectShader.drawOutline(context, 0, 0, previewW, HEIGHT, CORNER, 0.7f, borderCol);
        } else {
            RoundedRectShader.draw(context, 0, 0, previewW, HEIGHT, CORNER, bgCol);
            RoundedRectShader.drawOutline(context, 0, 0, previewW, HEIGHT, CORNER, 0.7f, borderCol);
        }

        // Тумблер ON
        float toggleX = PAD_X;
        float toggleY = (HEIGHT - TOGGLE_HEIGHT) * 0.5f;
        RoundedRectShader.draw(context, toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                TOGGLE_CORNER, setAlpha(ENABLED_COL, alpha));

        float circleX = toggleX + TOGGLE_PADDING + (TOGGLE_WIDTH - TOGGLE_PADDING * 2 - TOGGLE_CIRCLE_SIZE);
        float circleY = toggleY + (TOGGLE_HEIGHT - TOGGLE_CIRCLE_SIZE) * 0.5f;
        RoundedRectShader.draw(context, circleX, circleY, TOGGLE_CIRCLE_SIZE, TOGGLE_CIRCLE_SIZE,
                TOGGLE_CIRCLE_SIZE * 0.5f, setAlpha(TOGGLE_CIRCLE, alpha));

        // Текст: Module «Notifications»  Enabled
        float textStartX = toggleX + TOGGLE_WIDTH + 7f;
        float textY = (HEIGHT - FONT_SIZE) * 0.5f + 0.5f;
        String basePart = "Module «Notifications»  ";
        String statePart = "Enabled";

        drawString(context, basePart, textStartX, textY, FONT_SIZE, setAlpha(TEXT_COLOR, alpha));
        drawString(context, statePart, textStartX + width(basePart, FONT_SIZE), textY, FONT_SIZE, setAlpha(ENABLED_COL, alpha));

        // Вертикальная зеленая полоска справа
        float barX = previewW - BAR_PAD - BAR_W;
        float barInnerH = HEIGHT - BAR_VERT_PAD * 2f;
        RoundedRectShader.draw(context, barX, BAR_VERT_PAD, BAR_W, barInnerH, BAR_W * 0.5f, setAlpha(ENABLED_COL, alpha));

        context.getMatrices().pop();
    }

    private static String getDisplayText(NotifManager.Notif notif, long age) {
        if (notif.isTimer) {
            float rem = Math.max(0, notif.maxTime - age) / 1000f;
            String t = notif.title != null ? notif.title : "";
            return t + String.format(Locale.US, "  %.1fs", rem);
        }
        if (notif.title != null && !notif.title.isEmpty() && notif.content != null && !notif.content.isEmpty()) {
            return notif.title + "  " + notif.content;
        }
        if (notif.content != null && !notif.content.isEmpty()) {
            return notif.content;
        }
        return notif.title != null ? notif.title : "";
    }

    private static float calcWidth(NotifManager.Notif notif, long age) {
        float contentW;
        if (notif.isModuleToggle) {
            String basePart = "Module «" + notif.moduleName + "»  ";
            String statePart = notif.moduleEnabled ? "Enabled" : "Disabled";
            contentW = TOGGLE_WIDTH + 7f + width(basePart, FONT_SIZE) + width(statePart, FONT_SIZE);
        } else {
            boolean showIcons = com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault("Notif Icons",
                    com.lexoravisauls.client.gui.LexoraGui.moduleStates.getOrDefault("Notif Icons", true));
            float iconSpace = showIcons ? (ICON_SIZE + ICON_PAD) : 0f;
            if (notif.isTimer) {
                String timerSample = (notif.title != null ? notif.title : "") + "  00.0s";
                contentW = iconSpace + Math.max(width(timerSample, FONT_SIZE), width(getDisplayText(notif, age), FONT_SIZE));
            } else {
                contentW = iconSpace + width(getDisplayText(notif, age), FONT_SIZE);
            }
        }
        // PAD_X (слева) + contentW + 14px запас до полоски + BAR_W + BAR_PAD
        float total = PAD_X + contentW + 14f + BAR_W + BAR_PAD;
        return Math.max(120f, total);
    }

    private static Identifier getIconForType(NotifManager.NotifType type) {
        if (type == null) return ICON_INFO;
        return switch (type) {
            case SUCCESS, MODULE_ON -> ICON_SUCCESS;
            case ERROR, MODULE_OFF -> ICON_ERROR;
            case WARNING           -> ICON_DANGER;
            case SWAP              -> ICON_INFO;
        };
    }

    private static int getNotifAccentColor(NotifManager.Notif notif) {
        if (notif.title != null) {
            String lower = notif.title.toLowerCase(Locale.ROOT);
            if (lower.contains("здоровье") || lower.contains("хп") || lower.contains("hp")) {
                return DISABLED_COL;
            }
            if (lower.contains("зелье") || lower.contains("эффект")) {
                return 0xFFFFAA00;
            }
            if (lower.contains("ломается") || lower.contains("броня")) {
                return 0xFFFF5252;
            }
        }

        if (notif.type == null) return ENABLED_COL;

        return switch (notif.type) {
            case MODULE_ON, SUCCESS -> ENABLED_COL;
            case MODULE_OFF, ERROR  -> DISABLED_COL;
            case WARNING            -> 0xFFFFAA00;
            case SWAP               -> 0xFFBF5AF2;
        };
    }

    private static int setAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((color >> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int interpolateRgb(int c1, int c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static void drawString(DrawContext context, String text, float x, float y, float size, int color) {
        if (text == null || text.trim().isEmpty()) return;
        getFont().draw(context.getMatrices(), text, x, y, size, color);
    }

    private static float width(String text, float size) {
        if (text == null || text.trim().isEmpty()) return 0.0f;
        try {
            return getFont().getWidth(text, size);
        } catch (Throwable ignored) {
            return text.length() * size * 0.55f;
        }
    }
}
