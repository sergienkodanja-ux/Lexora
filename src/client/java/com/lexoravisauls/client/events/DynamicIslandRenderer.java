package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.LexoraIcons;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.mixin.BossBarHudAccessor;
import com.lexoravisauls.client.utils.DynamicIslandManager;
import com.lexoravisauls.client.utils.LexoraMediaUtils;
import com.lexoravisauls.client.utils.NotifManager;
import com.lexoravisauls.client.utils.TabAnimState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DynamicIslandRenderer {

    private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;
    public static final float FONT_SIZE = 9.0f;

    private static final int HEIGHT = 20;

    private static float animTotalW  = 0;
    private static float animHeight  = 20;
    private static float animY       = 5;
    private static float animR = -1, animG = -1, animB = -1;

    private static float animMedia    = 0f;
    private static float animExpand   = 0f;
    private static float animControls = 0f;

    private static String currentMidText       = "LexoraVisuals";
    private static String lastMidText          = "LexoraVisuals";
    private static float  textTransitionProgress = 1.0f;

    public static int islandX, islandY, islandW, islandH;

    // === PVP РЕЖИМ ===
    private static float animPvpBoxW  = 0f;  // плавная ширина красного бокса
    private static float animPvpAlpha = 0f;  // плавный alpha PVP (0→1)

    // === ПАТИ-УВЕДОМЛЕНИЕ ===
    private static float animPartyNotif          = 0f;
    private static float animPartyHover          = 0f;
    private static float animPartyBtnHoverAccept  = 0f;
    private static float animPartyBtnHoverDecline = 0f;
    private static float partyVisualHeight = 0f;
    public  static float[] PARTY_ACCEPT_BOUNDS   = null;
    public  static float[] PARTY_DECLINE_BOUNDS  = null;

    // === CALLOUT-УВЕДОМЛЕНИЕ ===
    private static float animCallout = 0f;
    public  static float[] CALLOUT_GO_BOUNDS      = null;
    public  static float[] CALLOUT_DISMISS_BOUNDS = null;

    // === РАСШИРЕНИЕ ПРИ ОТКРЫТОМ ГУИ (Party/Events/GUI/Configs) ===
    // Плавный переход островка в "режим быстрого доступа": когда открыт ModernClickGui,
    // островок раздвигается и справа появляются названия категорий (текстом, без своих иконок).
    // Их экранные границы публикуются сюда каждый кадр — ModernClickGui сам их вычитывает и
    // обрабатывает клики (пока открыт Screen, именно он получает мышь, а не HUD).
    private static float animGuiOpen = 0f;
    // Масштаб худа (Watermark Scale) двигает картинку вокруг пивота (centerX, islandY) — визуал
    // едет вместе с ним, а хитбоксы должны ехать ТУДА ЖЕ, иначе при масштабе ≠ 1 наведение и клик
    // расходятся. Запоминаем масштаб и ширину экрана этого кадра, чтобы бонды пересчитать в тот же
    // экранный пивот перед тем, как отдать их ModernClickGui (которому масштаб острова не виден).
    private static float lastScreenWidth = 0f;
    private static float lastHudScale = 1f;
    private static final Map<String, Float> guiLabelHover = new LinkedHashMap<>();
    public  static final Map<String, float[]> GUI_ICON_BOUNDS = new LinkedHashMap<>();
    private static final String[] GUI_ICON_IDS = {"Party", "Events", "GUI", "Configs"};
    private static final float GUI_LABEL_SIZE = 7.5f;
    private static final float GUI_LABEL_GAP  = 10f;
    // Плавность "точки-индикатора", уезжающей от LexoraVisuals к активной категории (0 = дома, 1 = уехала)
    private static float animCatDotAway = 0f;

    private static String catDisplayName(String id) {
        return switch (id) {
            case "Party" -> "Пати";
            case "Events" -> "Ивенты";
            case "GUI" -> "Настройки";
            case "Configs" -> "Конфиги";
            default -> id;
        };
    }

    private static float guiIconsBlockW() {
        float w = 16f;
        for (int i = 0; i < GUI_ICON_IDS.length; i++) {
            w += width(catDisplayName(GUI_ICON_IDS[i]), GUI_LABEL_SIZE);
            if (i < GUI_ICON_IDS.length - 1) w += GUI_LABEL_GAP;
        }
        return w;
    }

    private static int indexOfCategory(String id) {
        for (int i = 0; i < GUI_ICON_IDS.length; i++) if (GUI_ICON_IDS[i].equals(id)) return i;
        return -1;
    }

    /** Считает X и ширину каждой метки быстрого доступа, ничего не рисуя — общий источник правды
     *  и для самой отрисовки текста, и для того, куда должна "доехать" точка-пилюля. */
    private static float[] computeLabelPositions() {
        float blockW = guiIconsBlockW() * MathHelper.clamp(animGuiOpen, 0f, 1f);
        float startX = islandX + islandW - blockW + 9f;
        float[] result = new float[GUI_ICON_IDS.length * 2];
        float ix = startX;
        for (int i = 0; i < GUI_ICON_IDS.length; i++) {
            float lw = width(catDisplayName(GUI_ICON_IDS[i]), GUI_LABEL_SIZE);
            result[i * 2] = ix;
            result[i * 2 + 1] = lw;
            ix += lw + GUI_LABEL_GAP;
        }
        return result;
    }

    // Точка-индикатор у "LexoraVisuals" в состоянии покоя. Когда выбрана категория — та же самая
    // точка плавно едет и раздувается в скруглённую плашку-подложку позади её названия; закрыл —
    // едет и сдувается обратно. -1000 как маркер "ещё не инициализирована".
    private static float animPillX = -1000f, animPillY = 0f, animPillW = 7f, animPillH = 7f;

    private static void updateAndDrawPill(DrawContext context, float baseAlpha, int currentColor) {
        boolean pvpActive = PvpBossBarTracker.isInPvp();
        String activePanel = com.lexoravisauls.client.gui.modern.ModernClickGui.activeIslandPanel;
        boolean categoryActive = !pvpActive && activePanel != null && animGuiOpen > 0.4f;

        float homeX = islandX + 12f, homeY = islandY + (20f - 7f) / 2f, homeW = 7f, homeH = 7f;
        float targetX = homeX, targetY = homeY, targetW = homeW, targetH = homeH;

        if (categoryActive) {
            float[] pos = computeLabelPositions();
            int idx = indexOfCategory(activePanel);
            if (idx >= 0) {
                targetX = pos[idx * 2] - 6f;
                targetW = pos[idx * 2 + 1] + 12f;
                targetY = islandY + 2f;
                targetH = 16f;
            }
        }

        if (animPillX < -999f) {
            animPillX = targetX;
            animPillY = targetY;
            animPillW = targetW;
            animPillH = targetH;
        }

        float speed = 0.16f;
        animPillX += (targetX - animPillX) * speed;
        animPillY += (targetY - animPillY) * speed;
        animPillW += (targetW - animPillW) * speed;
        animPillH += (targetH - animPillH) * speed;

        float pillAlphaF = baseAlpha * (1f - MathHelper.clamp(animPvpAlpha, 0f, 1f));
        if (pillAlphaF <= 0.02f) return;
        int pillColor = (Math.round(255 * pillAlphaF) << 24) | (currentColor & 0xFFFFFF);
        drawSmoothRectF(context, animPillX, animPillY, animPillW, animPillH, animPillH / 2f, pillColor);
    }

    // ──────────────────────────────────────────────────────────────────────────

    private static MsdfFont getFont() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    private static LexoraIcons.Icon getWifiIcon(int ping) {
        if (ping < 0)   return LexoraIcons.Icon.WIFI_1;
        if (ping < 60)  return LexoraIcons.Icon.WIFI_FULL;
        if (ping < 120) return LexoraIcons.Icon.WIFI_3;
        if (ping < 200) return LexoraIcons.Icon.WIFI_2;
        return LexoraIcons.Icon.WIFI_1;
    }

    // =========================================================================
    //  ГЛАВНЫЙ РЕНДЕР
    // =========================================================================
    public static void render(DrawContext context, float tickDelta) {
        float opacity = 1.0f;
        if (!isModuleEnabled("Watermark", true)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        boolean blurEnabled = LexoraGui.moduleStates.getOrDefault("Watermark Blur", true);
        if (blurEnabled) {
            context.draw();
            try { com.lexoravisauls.client.gui.modern.ScreenCaptureManager.captureScreen(); } catch (Throwable ignored) {}
        }

        float fadeFactor = 1.0f;
        if (isModuleEnabled("Animations", true) && isModuleEnabled("Anim Tab", true)) {
            fadeFactor = MathHelper.clamp(Math.abs(TabAnimState.animY) / 50.0f, 0.0f, 1.0f);
        } else if (mc.options.playerListKey.isPressed()) {
            fadeFactor = 0.0f;
        }
        if (fadeFactor < 0.02f) return;

        int   screenWidth  = mc.getWindow().getScaledWidth();
        int   screenHeight = mc.getWindow().getScaledHeight();
        float scale        = getNum("Watermark Scale", 1.0f);
        lastScreenWidth = screenWidth;
        lastHudScale = scale;
        String timeStr     = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        int ping = mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null
                ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency() : 0;
        String pingStr = ping + " ms";

        boolean hasMedia  = LexoraMediaUtils.hasMedia();
        animMedia += ((hasMedia ? 1f : 0f) - animMedia) * 0.35f;

        boolean isFreeMouse = mc.currentScreen != null;
        double  mx = isFreeMouse ? mc.mouse.getX() * screenWidth  / (double) mc.getWindow().getWidth()  : -1;
        double  my = isFreeMouse ? mc.mouse.getY() * screenHeight / (double) mc.getWindow().getHeight() : -1;

        // ФИКС хитбокса: раньше сравнивали mx/my напрямую с islandX/Y/W/H — это координаты
        // ДО ms.scale(scale, scale, 1f) чуть ниже (пивот — centerX, islandY). При Watermark
        // Scale < 1 картинка становилась компактнее, а зона наведения/клика — нет, отсюда и
        // "криво нажимается". Прогоняем те же границы через тот же пивот, что и рендер.
        boolean hovered = isFreeMouse && isInsideScaledIsland(mx, my, screenWidth, scale);
        animExpand += ((hovered && hasMedia && animGuiOpen < 0.5f ? 1f : 0f) - animExpand) * 0.35f;

        // Открыт ли наш новый ClickGui (3 башни) — по нему островок раздвигается и
        // показывает быстрый доступ к Party/Events/GUI/Configs.
        boolean isGuiOpen = mc.currentScreen instanceof com.lexoravisauls.client.gui.modern.ModernClickGui;
        animGuiOpen += ((isGuiOpen ? 1f : 0f) - animGuiOpen) * 0.30f;
        if (!isGuiOpen && animGuiOpen < 0.02f) {
            animGuiOpen = 0f;
            GUI_ICON_BOUNDS.clear();
        }

        // Считаем боссбары — PVP-бар не учитываем в сдвиге островка
        // (он уже скрыт MixinBossBarHud, но на всякий случай вычитаем)
        int   activeBossBars = ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBars().size();
        // Если PVP активен — этот бар скрыт мишем, не сдвигаем островок
        if (PvpBossBarTracker.isInPvp() && activeBossBars > 0) activeBossBars--;
        float targetY = 5 + (activeBossBars > 0 ? activeBossBars * 19 : 0);

        // ── Ширина островка: в PVP учитываем ширину "Вы в ПВП режиме!" ──
        String midForWidth = PvpBossBarTracker.isInPvp() ? "Вы в ПВП режиме!" : currentMidText;
        float pvpSecondsNow = PvpBossBarTracker.getSecondsSmooth();
        String secStr = (int) Math.ceil(pvpSecondsNow) + "с";
        float  secW   = width(secStr, FONT_SIZE);
        float  pvpBoxTarget = PvpBossBarTracker.isInPvp() ? (secW + 10f) : 7f;
        // animPvpBoxW обновляется в renderDefaultIsland, используем его для расчёта ширины
        float effectiveBoxW = animPvpBoxW > 0 ? animPvpBoxW : 7f;

        float defaultW     = 12 + effectiveBoxW + 4 + width(midForWidth, FONT_SIZE) + 12;
        float mediaW       = 130f + (180f - 130f) * animExpand;
        float targetTotalW = defaultW + (mediaW - defaultW) * animMedia + guiIconsBlockW() * animGuiOpen;
        float targetH      = 20f + (64f - 20f) * animExpand * animMedia;

        long time              = System.currentTimeMillis();
        int  dynamicStateColor = DynamicIslandManager.getState().color;
        int  targetColor       = getAccentColor(time, dynamicStateColor);

        if (animTotalW == 0) {
            animTotalW = targetTotalW; animHeight = targetH; animY = targetY;
            animR = ((targetColor >> 16) & 0xFF) / 255f;
            animG = ((targetColor >>  8) & 0xFF) / 255f;
            animB = ( targetColor        & 0xFF) / 255f;
        }

        float smoothSpeed = 0.45f;
        animTotalW += (targetTotalW - animTotalW) * smoothSpeed;
        animHeight += (targetH      - animHeight) * smoothSpeed;
        animY      += (targetY      - animY)      * 0.25f;
        animR      += (((targetColor >> 16) & 0xFF) / 255f - animR) * smoothSpeed;
        animG      += (((targetColor >>  8) & 0xFF) / 255f - animG) * smoothSpeed;
        animB      += (( targetColor        & 0xFF) / 255f - animB) * smoothSpeed;

        int currentColor = (0xFF << 24)
                | ((int)(animR * 255) << 16)
                | ((int)(animG * 255) <<  8)
                | ((int)(animB * 255));

        islandW = (int) animTotalW;
        islandH = (int) animHeight;
        islandX = (screenWidth - islandW) / 2;
        islandY = (int) animY;

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(screenWidth / 2f, islandY, -150);
        ms.scale(scale, scale, 1.0f);
        ms.translate(-screenWidth / 2f, -islandY, 0);

        boolean isSolid    = isModuleEnabled("Watermark Solid", false);
        int  baseBgAlpha   = isSolid ? 255 : 230;
        int  bgAlpha       = (int)(baseBgAlpha * fadeFactor);
        int  bgColor       = (bgAlpha << 24) | 0x1A1A20;

        float radius = Math.min(islandH / 2.0f, 15f);
        if (blurEnabled) {
            com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(
                    context, islandX, islandY, islandW, islandH, radius, 15f, bgColor);
        } else {
            drawSmoothRect(context, islandX, islandY, islandW, islandH, radius, bgColor);
        }

        // Ping / Time
        float outTextSize  = FONT_SIZE * 0.8f;
        float smallIconSize = 9.0f;
        int   timeTextW    = (int) width(timeStr, outTextSize);
        int   timeFullW    = (int)(smallIconSize + 3 + timeTextW);
        int   timeStartX   = islandX - 5 - timeFullW;
        int   pingStartX   = islandX + islandW + 5;
        float outY         = islandY + (20f - smallIconSize) / 2f;
        float textY        = islandY + (20f - outTextSize)   / 2f - 0.5f;

        float timePingCycle = (time % 5000L) / 5000.0f;
        float tpFactor = timePingCycle < 0.4f
                ? timePingCycle / 0.4f
                : (timePingCycle > 0.9f ? 1f - (timePingCycle - 0.9f) / 0.1f : 1f);
        int tpColorVal = (int)(40 + 215 * tpFactor);
        int tpColor    = ((int)(255 * fadeFactor) << 24)
                | (tpColorVal << 16) | (tpColorVal << 8) | tpColorVal;

        LexoraIcons.draw(context, LexoraIcons.Icon.AIRPLANE, timeStartX, outY, smallIconSize, tpColor);
        drawString(context, timeStr,  timeStartX + smallIconSize + 3, textY, outTextSize, tpColor);
        LexoraIcons.draw(context, getWifiIcon(ping), pingStartX, outY, smallIconSize, tpColor);
        drawString(context, pingStr,  pingStartX + smallIconSize + 3, textY, outTextSize, tpColor);

        updateAndDrawPill(context, fadeFactor * (1f - animMedia), currentColor);
        if (animMedia < 0.99f) renderDefaultIsland(context, ms, time, fadeFactor, currentColor, 1f - animMedia);
        if (animMedia > 0.01f) renderMusicIsland(context, ms, time, fadeFactor, opacity, screenWidth);
        if (animGuiOpen > 0.01f) renderGuiQuickAccess(context, fadeFactor * animGuiOpen, mx, my, isFreeMouse);

        renderNotifications(context, screenWidth, blurEnabled, bgColor);
        renderPartyInviteIsland(context, screenWidth, blurEnabled, bgColor, mx, my, isFreeMouse);
        renderCalloutIsland(context, screenWidth, blurEnabled, bgColor, mx, my, isFreeMouse);

        ms.pop();
    }

    // =========================================================================
    //  БЫСТРЫЙ ДОСТУП ПРИ ОТКРЫТОМ ГУИ: Party / Events / GUI / Configs (текстом)
    // =========================================================================
    private static void renderGuiQuickAccess(DrawContext context, float alphaF, double mx, double my, boolean isFreeMouse) {
        int alpha = (int) (255 * MathHelper.clamp(alphaF, 0f, 1f));
        if (alpha <= 5) return;

        float blockW = guiIconsBlockW() * MathHelper.clamp(animGuiOpen, 0f, 1f);
        float startX = islandX + islandW - blockW + 9f;

        // Тонкий разделитель между текстом островка и названиями категорий
        float dividerX = startX - 8f;
        drawSmoothRect(context, (int) dividerX, (int) (islandY + 5), 1, (int) (islandH - 10), 0f, withAlphaLocal(0xFFFFFFFF, (int) (alpha * 0.25f)));

        // Наведение проверяем по координатам ModernClickGui (те же, что реально ловит его
        // mouseClicked) — а не по отдельно пересчитанным тут, иначе подсветка и клик расходятся.
        int gmx = com.lexoravisauls.client.gui.modern.ModernClickGui.lastMouseX;
        int gmy = com.lexoravisauls.client.gui.modern.ModernClickGui.lastMouseY;

        String activePanel = com.lexoravisauls.client.gui.modern.ModernClickGui.activeIslandPanel;
        float labelY = islandY + (20f - GUI_LABEL_SIZE) / 2f - 0.5f;
        float[] pos = computeLabelPositions();

        for (int i = 0; i < GUI_ICON_IDS.length; i++) {
            String iconId = GUI_ICON_IDS[i];
            String label = catDisplayName(iconId);
            float ix = pos[i * 2];
            float labelW = pos[i * 2 + 1];
            boolean isActive = iconId.equals(activePanel);

            // Переводим границы метки из "локальных" координат острова (до применения масштаба
            // худа) в реальные экранные — иначе при Watermark Scale ≠ 1 наведение/клик и картинка
            // расходятся именно так, как ты описал (то слева ещё видит, то справа уже не видит).
            float rawBX = ix - 3f, rawBW = labelW + 6f;
            float scrBX = lastScreenWidth / 2f + (rawBX - lastScreenWidth / 2f) * lastHudScale;
            float scrBW = rawBW * lastHudScale;
            float scrBY = islandY; // Y-пивот трансформации — это и есть islandY, верх бокса с ним совпадает
            float scrBH = islandH * lastHudScale;

            boolean isHovered = gmx >= scrBX && gmx <= scrBX + scrBW && gmy >= scrBY && gmy <= scrBY + scrBH;
            float hoverT = guiLabelHover.getOrDefault(iconId, 0f);
            hoverT += ((isHovered ? 1f : 0f) - hoverT) * 0.3f;
            guiLabelHover.put(iconId, hoverT);

            int txtColor = withAlphaLocal(0xFFFFFFFF, isActive ? alpha : (int) (alpha * (0.42f + hoverT * 0.4f)));
            drawString(context, label, ix, labelY, GUI_LABEL_SIZE, txtColor);

            if (animGuiOpen > 0.85f) {
                // ФИКС: тут раньше сохранялись сырые (немасштабированные) ix/labelW — ровно та же
                // болезнь, что и с хитбоксом самого острова: чем дальше иконка от пивота
                // (Configs — самая правая, дальше всех от центра экрана), тем больше расхождение
                // между картинкой и кликабельной зоной. Кладём уже посчитанные выше scrBX/Y/W/H.
                GUI_ICON_BOUNDS.put(iconId, new float[]{scrBX, scrBY, scrBW, scrBH});
            }
        }
        if (animGuiOpen <= 0.85f) GUI_ICON_BOUNDS.clear();
    }

    private static int withAlphaLocal(int argb, int alpha255) {
        alpha255 = Math.max(0, Math.min(255, alpha255));
        return (alpha255 << 24) | (argb & 0x00FFFFFF);
    }

    // =========================================================================
    //  МУЗЫКАЛЬНЫЙ ОСТРОВОК
    // =========================================================================
    private static void renderMusicIsland(DrawContext context, MatrixStack ms,
                                          long time, float fadeFactor, float opacity, int screenWidth) {
        float e    = animExpand;
        float invE = 1f - e;
        int alpha  = (int)(255 * fadeFactor * opacity);
        if (alpha <= 5) return;

        float effE   = Math.max(0, (islandH - 20f) / 44f);
        float tSize  = 14f + 34f * effE;
        float tX     = islandX + 4f + 4f * effE;
        float tY     = islandY + 3f + 5f * effE;
        float tRadius = 7f - 1f * effE;
        float titleX  = islandX + 22f + 42f * effE;
        float titleY  = islandY + 5.5f + 6.5f * effE;
        float maxTextW = 88f + 20f * effE;

        String title = LexoraMediaUtils.getTitle();
        if (title == null || title.isEmpty()) title = "Unknown Track";
        float textW     = width(title, FONT_SIZE);
        float maxScroll = Math.max(0, textW - maxTextW);
        float scrollOffset = 0;
        if (maxScroll > 0) {
            float cycle = (time % 10000L) / 10000.0f;
            float wave  = MathHelper.clamp((float)Math.sin(cycle * Math.PI * 2) * 1.3f, -1f, 1f);
            scrollOffset = -maxScroll * (wave + 1f) / 2f;
        }

        float scissorBottom = islandY + 34f;
        context.enableScissor((int)titleX, (int)islandY, (int)(titleX + maxTextW), (int)scissorBottom);
        drawString(context, title, titleX + scrollOffset, titleY, FONT_SIZE, (alpha << 24) | 0xFFFFFF);
        if (e > 0.05f) {
            String artist = LexoraMediaUtils.getAuthor();
            if (artist == null || artist.isEmpty()) artist = "Unknown Artist";
            int artistAlpha = (int)(alpha * e);
            drawString(context, artist, titleX + scrollOffset, titleY + 10f, 7.5f, (artistAlpha << 24) | 0xAAAAAA);
        }
        context.disableScissor();

        Identifier thumb = LexoraMediaUtils.getThumbnail();
        if (thumb != null) {
            RoundedRectShader.drawTextured(context, thumb, tX, tY, tSize, tSize, tRadius, 0, 0, 1, 1, (alpha << 24) | 0xFFFFFF);
        } else {
            drawSmoothRect(context, (int)tX, (int)tY, (int)tSize, (int)tSize, tRadius, (alpha << 24) | 0x333333);
        }

        if (invE > 0.05f) {
            int   visAlpha = (int)(alpha * invE);
            float visX     = islandX + islandW - 14f;
            boolean playing = LexoraMediaUtils.isPlaying();
            float h1 = playing ? 4f + (float)Math.sin((time % 10000L) * 0.015) * 3f : 2f;
            float h2 = playing ? 4f + (float)Math.cos((time % 10000L) * 0.012 + 1.0) * 3f : 2f;
            float h3 = playing ? 4f + (float)Math.sin((time % 10000L) * 0.018 + 2.0) * 3f : 2f;
            int   visColor = (visAlpha << 24) | 0xFFFFFF;
            float centerY  = islandY + 10f;
            drawSmoothRect(context, (int)visX,     (int)(centerY - h1/2f), 2, (int)h1, 1f, visColor);
            drawSmoothRect(context, (int)visX + 3, (int)(centerY - h2/2f), 2, (int)h2, 1f, visColor);
            drawSmoothRect(context, (int)visX + 6, (int)(centerY - h3/2f), 2, (int)h3, 1f, visColor);
        }

        float controlsTarget = e > 0.85f ? 1f : 0f;
        float controlsSpeed  = controlsTarget > animControls ? 0.25f : 0.85f;
        animControls += (controlsTarget - animControls) * controlsSpeed;

        if (animControls > 0.01f) {
            float t      = animControls;
            float eased  = 1f - (float)Math.pow(1f - t, 3f);
            float rise   = (1f - eased) * 6f;
            float scAnim = 0.85f + 0.15f * eased;
            int   expAlpha = (int)(alpha * eased);
            if (expAlpha > 3) {
                float afterArt      = islandX + 4f + 4f * effE + (14f + 34f * effE) + 4f;
                long  prog = LexoraMediaUtils.getProgress();
                long  dur  = LexoraMediaUtils.getDuration();
                float p    = dur > 0 ? MathHelper.clamp((float)prog / dur, 0f, 1f) : 0f;

                float barX = afterArt;
                float barW = islandX + islandW - 8f - barX;
                float barH = 3f;
                float barY = islandY + 37f - rise;
                float barR = barH / 2f;
                if (barW > 4f) {
                    drawSmoothRect(context, (int)barX, (int)barY, (int)barW, (int)barH, barR, (expAlpha << 24) | 0x2A2A2A);
                    if (p > 0.001f) {
                        float fillW = Math.max(barH, barW * p);
                        drawSmoothRect(context, (int)barX, (int)barY, (int)fillW, (int)barH, barR, (expAlpha << 24) | 0xFFFFFF);
                        float dotR = 3f;
                        drawSmoothRect(context, (int)(barX + fillW - dotR), (int)(barY + barH/2f - dotR),
                                (int)(dotR*2), (int)(dotR*2), dotR, (expAlpha << 24) | 0xFFFFFF);
                    }
                }

                LexoraIcons.Icon playIcon    = LexoraMediaUtils.isPlaying() ? LexoraIcons.Icon.PAUSE : LexoraIcons.Icon.PLAY;
                float btnSide       = 13f;
                float btnPlay       = 16f;
                float btnGap        = 22f;
                float btnZoneCenter = (afterArt + islandX + islandW - 8f) / 2f;
                float btnY          = islandY + 47f - rise;
                float playX         = btnZoneCenter - btnPlay / 2f;
                float prevX         = btnZoneCenter - btnGap - btnSide;
                float nextX         = btnZoneCenter + btnGap;
                int   btnColor      = (expAlpha << 24) | 0xFFFFFF;

                ms.push();
                ms.translate(btnZoneCenter, btnY + btnPlay / 2f, 0);
                ms.scale(scAnim, scAnim, 1f);
                ms.translate(-btnZoneCenter, -(btnY + btnPlay / 2f), 0);
                LexoraIcons.draw(context, LexoraIcons.Icon.SKIP_PREV, prevX, btnY + (btnPlay-btnSide)/2f, btnSide, btnColor);
                LexoraIcons.draw(context, playIcon, playX, btnY, btnPlay, btnColor);
                LexoraIcons.draw(context, LexoraIcons.Icon.SKIP_NEXT, nextX, btnY + (btnPlay-btnSide)/2f, btnSide, btnColor);
                ms.pop();
            }
        }
    }

    // ФИКС хитбокса (часть 2): переводит "логическую" (до ms.scale) точку/границу острова
    // в реальные экранные координаты через тот же пивот (centerX, islandY), которым
    // пользуется render(). Используется и для наведения (hovered выше), и для кликов
    // по кнопкам медиаплеера ниже — раньше они тоже игнорировали scale.
    private static float toScreenX(float localX, float screenWidth, float scale) {
        float cx = screenWidth / 2f;
        return cx + (localX - cx) * scale;
    }

    private static float toScreenY(float localY, float scale) {
        return islandY + (localY - islandY) * scale;
    }

    private static boolean isInsideScaledIsland(double mx, double my, int screenWidth, float scale) {
        float left   = toScreenX(islandX, screenWidth, scale);
        float right  = toScreenX(islandX + islandW, screenWidth, scale);
        float top    = toScreenY(islandY, scale);
        float bottom = toScreenY(islandY + islandH, scale);
        return mx >= left && mx <= right && my >= top && my <= bottom;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!LexoraMediaUtils.hasMedia() || animExpand < 0.5f) return false;
        float effE          = Math.max(0, (islandH - 20f) / 44f);
        float afterArt      = islandX + 4f + 4f * effE + (14f + 34f * effE) + 4f;
        float btnZoneCenter = (afterArt + islandX + islandW - 8f) / 2f;
        float btnY = islandY + 47f;
        float btnSide = 13f, btnPlay = 16f, btnGap = 22f, hitPad = 5f;
        float prevX = btnZoneCenter - btnGap - btnSide;
        float playX = btnZoneCenter - btnPlay / 2f;
        float nextX = btnZoneCenter + btnGap;

        // Те же прямоугольники, что и раньше, но переведённые в экранные координаты через
        // scale/pivot — иначе при уменьшенном острове по Prev/Play/Next не попасть мышью.
        float sw = lastScreenWidth, sc = lastHudScale;
        float prevL = toScreenX(prevX - hitPad, sw, sc), prevR = toScreenX(prevX + btnSide + hitPad, sw, sc);
        float playL = toScreenX(playX - hitPad, sw, sc), playR = toScreenX(playX + btnPlay + hitPad, sw, sc);
        float nextL = toScreenX(nextX - hitPad, sw, sc), nextR = toScreenX(nextX + btnSide + hitPad, sw, sc);
        float btnT  = toScreenY(btnY - hitPad, sc),      btnB  = toScreenY(btnY + btnPlay + hitPad, sc);

        if (mx >= prevL && mx <= prevR && my >= btnT && my <= btnB) { LexoraMediaUtils.prev(); return true; }
        if (mx >= playL && mx <= playR && my >= btnT && my <= btnB) { LexoraMediaUtils.togglePlay(); return true; }
        if (mx >= nextL && mx <= nextR && my >= btnT && my <= btnB) { LexoraMediaUtils.next(); return true; }
        return false;
    }

    // =========================================================================
    //  DEFAULT ОСТРОВОК  (с PVP-поддержкой)
    // =========================================================================
    private static void renderDefaultIsland(DrawContext context, MatrixStack ms,
                                            long time, float fadeFactor, int currentColor, float opacity) {
        int alpha = (int)(255 * fadeFactor * opacity);
        if (alpha <= 5) return;

        // ── PVP состояние ──────────────────────────────────────────────────────
        boolean pvpActive = PvpBossBarTracker.isInPvp();
        String activeCategoryPanel = com.lexoravisauls.client.gui.modern.ModernClickGui.activeIslandPanel;
        boolean categoryActive = !pvpActive && activeCategoryPanel != null;

        // Плавно анимируем alpha PVP-блока (медленнее = красивее)
        animPvpAlpha += ((pvpActive ? 1f : 0f) - animPvpAlpha) * 0.15f;
        // Только схлопывает зарезервированное под точку место в тексте (сама точка теперь
        // рисуется отдельно, см. updateAndDrawPill — она уезжает и раздувается в плашку сама).
        animCatDotAway += ((categoryActive ? 1f : 0f) - animCatDotAway) * 0.18f;

        // ── Текст островка ────────────────────────────────────────────────────
        String targetTextStr;
        if (pvpActive) {
            targetTextStr = "Вы в ПВП режиме!";
        } else if (categoryActive) {
            targetTextStr = catDisplayName(activeCategoryPanel);
        } else {
            targetTextStr = DynamicIslandManager.getTargetText();
            if (targetTextStr.equals("Lexora")) targetTextStr = "LexoraVisuals";
        }

        if (!targetTextStr.equals(currentMidText)) {
            lastMidText = currentMidText;
            currentMidText = targetTextStr;
            textTransitionProgress = 0f;
        }
        textTransitionProgress += (1f - textTransitionProgress) * 0.35f;

        // ── Позиции ───────────────────────────────────────────────────────────
        float midContentX = islandX + 12f;
        float textY       = islandY + (20f - FONT_SIZE) / 2f - 0.5f;

        float wave     = (float)(Math.sin(time / 400.0) * 0.5 + 0.5);
        int   colorVal = (int)(150 + 80 * wave);
        int   shimmerRGB = (colorVal << 16) | (colorVal << 8) | colorVal;

        // ── PVP: таймер и ширина бокса ────────────────────────────────────────
        float pvpSecondsSmooth = PvpBossBarTracker.getSecondsSmooth();
        String secStr       = (int) Math.ceil(pvpSecondsSmooth) + "с";
        // Шрифт чуть крупнее чем раньше — FONT_SIZE без уменьшения
        float  pvpFontSize  = FONT_SIZE;
        float  secW         = width(secStr, pvpFontSize);
        // Ширина: текст + паддинг 10f (было 12f) — чуть уже. Если активна категория — точка
        // "уехала" в строку быстрого доступа, поэтому дома для неё ширины не оставляем.
        float  targetBoxW   = pvpActive ? (secW + 10f) : (categoryActive ? 0f : 7f);
        // Высота таблетки PVP — чуть больше для солидности
        float  targetBoxH   = pvpActive ? 9f : 7f;
        // Плавно анимируем и высоту тоже — и сжимаем, если точка уехала к категории
        float  animBoxH     = (7f + 2f * animPvpAlpha) * (1f - animCatDotAway);

        // Скорость анимации: разворачиваемся быстро, сворачиваемся немного медленнее
        float boxSpeed = pvpActive ? 0.20f : 0.20f;
        animPvpBoxW += (targetBoxW - animPvpBoxW) * boxSpeed;

        // ── Цвет точки/бокса: lerp accent → #EF4444 (красный) ────────────────
        int accentR = (currentColor >> 16) & 0xFF;
        int accentG = (currentColor >>  8) & 0xFF;
        int accentB =  currentColor        & 0xFF;
        // Пульсирующий красный когда мало времени (< 10с)
        float pulse = (pvpSecondsSmooth < 10f && pvpActive)
                ? 0.7f + 0.3f * (float) Math.sin(time / 150.0)
                : 1.0f;
        int pvpR = (int)(0xEF * pulse);
        int pvpG = 0x44;
        int pvpB = 0x44;

        int dotR = (int)(accentR + (pvpR - accentR) * animPvpAlpha);
        int dotG = (int)(accentG + (pvpG - accentG) * animPvpAlpha);
        int dotB = (int)(accentB + (pvpB - accentB) * animPvpAlpha);

        // Короткая вспышка ровно в момент продления кд — подтверждает игроку что кд обновился
        float extendPulse = PvpBossBarTracker.getExtendPulse(); // 0..1, гаснет за ~400мс
        if (extendPulse > 0f) {
            dotR = (int)(dotR + (255 - dotR) * extendPulse * 0.55f);
            dotG = (int)(dotG + (255 - dotG) * extendPulse * 0.55f);
            dotB = (int)(dotB + (255 - dotB) * extendPulse * 0.55f);
        }

        int dotColor = (alpha << 24) | (clamp255(dotR) << 16) | (clamp255(dotG) << 8) | clamp255(dotB);

        // ── Рисуем точку / таймер-бокс ────────────────────────────────────────
        float boxW = animPvpBoxW;
        float boxH = animBoxH;  // плавно меняется 7→9
        // ИДЕАЛЬНЫЙ КРУГ: когда boxW == boxH == 7 → radius = 3.5f → идеальный круг
        // Когда растягивается → radius = boxH/2 = полное скругление по высоте (таблетка)
        float boxRadius = boxH / 2f;
        // Вертикально центрируем бокс в полосе высотой 20px
        float boxY = islandY + (20f - boxH) / 2f;
        // ── Рисуем таймер-бокс — только для ПВП. Точку в покое / у категории теперь рисует
        //    отдельная "точка-пилюля" (updateAndDrawPill), она уже нарисована фоном раньше.
        if (animPvpAlpha > 0.02f) {
            drawSmoothRectF(context, midContentX, boxY, boxW, boxH, boxRadius, dotColor);
        }

        // ── Текст секунд внутри бокса (появляется плавно) ─────────────────────
        if (animPvpAlpha > 0.05f && boxW > 12f) {
            float textFade = MathHelper.clamp((boxW - 12f) / 12f, 0f, 1f);
            int secAlpha = (int)(alpha * animPvpAlpha * textFade);
            secAlpha = Math.max(0, Math.min(secAlpha, 255));
            if (secAlpha > 5) {
                float secTextX = midContentX + (boxW - secW) / 2f;
                // Центрируем по актуальной высоте бокса
                float secTextY = boxY + (boxH - pvpFontSize) / 2f - 0.5f;
                drawString(context, secStr, secTextX, secTextY, pvpFontSize,
                        (secAlpha << 24) | 0xFFFFFF);
            }
        }

        // ── Сдвигаем X для основного текста ──────────────────────────────────
        midContentX += boxW + 4f;

        // ── Основной текст ("LexoraVisuals" или "Вы в ПВП режиме!") ──────────
        if (textTransitionProgress < 0.99f) {
            ms.push();
            ms.translate(0, -((1f - textTransitionProgress) * 2f), 0);
            int alphaOld = (int) MathHelper.clamp((1f - textTransitionProgress) * alpha, 0, 255);
            if (alphaOld > 5)
                drawString(context, lastMidText, midContentX, textY, FONT_SIZE,
                        (alphaOld << 24) | shimmerRGB);
            ms.pop();

            int alphaNew = (int) MathHelper.clamp(textTransitionProgress * alpha, 0, 255);
            if (alphaNew > 5)
                drawString(context, currentMidText, midContentX, textY, FONT_SIZE,
                        (alphaNew << 24) | shimmerRGB);
        } else {
            drawString(context, currentMidText, midContentX, textY, FONT_SIZE,
                    (alpha << 24) | shimmerRGB);
        }
    }

    // =========================================================================
    //  ОБЫЧНЫЕ НОТИФЫ
    // =========================================================================
    private static void renderNotifications(DrawContext context, int screenWidth,
                                            boolean blurEnabled, int bgColor) {
        List<NotifManager.Notif> activeNotifs = NotifManager.getNotifs();
        if (activeNotifs.isEmpty()) return;

        float currentNotifY = islandY + islandH + 6;

        for (int i = activeNotifs.size() - 1; i >= 0; i--) {
            NotifManager.Notif notif = activeNotifs.get(i);
            long elapsed = System.currentTimeMillis() - notif.startTime;

            if (notif.isTimer) {
                float rem = Math.max(0, notif.maxTime - elapsed) / 1000f;
                notif.content = String.format(Locale.US, "Осталось: %.1f сек.", rem);
            }

            float alpha = 1f;
            if (elapsed < notif.fadeTime) {
                alpha = (float)elapsed / notif.fadeTime;
                alpha = (float)(1.0 - Math.pow(1.0 - alpha, 3));
            } else if (elapsed > notif.maxTime - notif.fadeTime) {
                // ФИКС: без клампа (maxTime - elapsed) уходит в минус при просрочке,
                // а возведение в квадрат превращает минус обратно в плюс —
                // уведомление "вспыхивает" на полную непрозрачность вместо исчезновения.
                float fadeOutT = (float)(notif.maxTime - elapsed) / notif.fadeTime;
                fadeOutT = MathHelper.clamp(fadeOutT, 0f, 1f);
                alpha = fadeOutT * fadeOutT;
            }

            int alphaInt = Math.max(0, Math.min(255, (int)(alpha * 255)));
            if (alphaInt <= 5) continue;

            float titleW   = width(notif.title,   8.0f);
            float contentW = width(notif.content, 7.5f);
            float targetW  = 24 + titleW + 8 + contentW + 12;

            notif.velocity += (1f - notif.scale) * 0.35f;
            notif.velocity *= 0.55f;
            notif.scale    += notif.velocity;

            if (!notif.animYSet) { notif.animY = islandY; notif.animYSet = true; }
            notif.animY += (currentNotifY - notif.animY) * 0.25f;

            float nx = (screenWidth - targetW) / 2f;
            float ny = notif.animY;
            float nh = 22;
            int notifBg = (alphaInt << 24) | (bgColor & 0xFFFFFF);

            context.getMatrices().push();
            context.getMatrices().translate(screenWidth / 2f, ny + nh / 2f, 0);
            context.getMatrices().scale(notif.scale, notif.scale, 1f);
            context.getMatrices().translate(-screenWidth / 2f, -(ny + nh / 2f), 0);

            if (blurEnabled) {
                com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(
                        context, nx, ny, targetW, nh, nh / 2f, 15f, notifBg);
            } else {
                drawSmoothRect(context, (int)nx, (int)ny, (int)targetW, (int)nh, nh / 2f, notifBg);
            }

            int iconColor = getNotifColor(notif.type, alphaInt);
            drawSmoothRect(context, (int)(nx + 8), (int)(ny + 7), 8, 8, 4f, iconColor);
            drawString(context, notif.title,   nx + 22, ny + 5.5f, 8.0f, (alphaInt << 24) | 0xFFFFFF);
            drawString(context, "-",           nx + 22 + titleW + 2, ny + 6.0f, 7.5f, (alphaInt << 24) | 0x555555);
            drawString(context, notif.content, nx + 22 + titleW + 8, ny + 6.0f, 7.5f, (alphaInt << 24) | 0xAAAAAA);

            context.getMatrices().pop();
            currentNotifY += (nh * notif.scale) + 4;
        }
    }

    // =========================================================================
    //  ПАТИ-УВЕДОМЛЕНИЕ
    // =========================================================================
    private static void renderPartyInviteIsland(DrawContext context, int screenWidth,
                                                boolean blurEnabled, int bgColor,
                                                double mx, double my, boolean isFreeMouse) {

        com.lexoravisauls.client.party.LexoraPartyManager.PartyInviteNotif notif =
                com.lexoravisauls.client.party.LexoraPartyManager.activeInviteNotif;
        boolean hasNotif = notif != null && !notif.expired();

        animPartyNotif += ((hasNotif ? 1f : 0f) - animPartyNotif) * 0.30f;
        if (animPartyNotif < 0.02f) {
            PARTY_ACCEPT_BOUNDS  = null;
            PARTY_DECLINE_BOUNDS = null;
            partyVisualHeight    = 0f;
            return;
        }

        float alpha  = animPartyNotif;
        int   alphaI = Math.max(0, Math.min(255, (int)(alpha * 255)));

        float nh        = 22f;
        String titlePart   = "★ Запрос в пати";
        String contentPart = notif != null ? notif.requesterName + " хочет вступить" : "";
        float  titleW      = width(titlePart,   8.0f);
        float  sepW        = width(" — ",        7.5f);
        float  contentW    = width(contentPart, 7.5f);
        float  pillW       = 24f + titleW + sepW + contentW + 12f;

        float btnW         = 60f;
        float btnH         = 20f;
        float btnGap       = 8f;
        float expandedW    = Math.max(pillW, btnW * 2 + btnGap + 24f);
        float expandedH    = nh + 10f + btnH + 8f;

        float notifY   = islandY + islandH + 6f;
        float curH = nh + animPartyHover * (expandedH - nh);
        float curW = pillW + animPartyHover * (expandedW - pillW);
        float curX = (screenWidth - curW) / 2f;
        float sc = 0.88f + 0.12f * animPartyNotif;

        float pivotX = screenWidth / 2f;
        float pivotY = notifY + curH / 2f;

        float btnsY_local    = notifY + nh + 5f;
        float centerX_local  = curX + curW / 2f;
        float acceptX_local  = centerX_local - btnW - btnGap / 2f;
        float declineX_local = centerX_local + btnGap / 2f;

        float acceptX_screen  = pivotX + (acceptX_local  - pivotX) * sc;
        float declineX_screen = pivotX + (declineX_local - pivotX) * sc;
        float btnsY_screen    = pivotY + (btnsY_local     - pivotY) * sc;
        float btnW_screen     = btnW  * sc;
        float btnH_screen     = btnH  * sc;

        float blockX_screen = pivotX + (curX    - pivotX) * sc;
        float blockY_screen = pivotY + (notifY  - pivotY) * sc;
        float blockW_screen = curW * sc;
        float blockH_screen = curH * sc;

        boolean overBlock = isFreeMouse
                && mx >= blockX_screen && mx <= blockX_screen + blockW_screen
                && my >= blockY_screen && my <= blockY_screen + blockH_screen;
        animPartyHover += ((overBlock ? 1f : 0f) - animPartyHover) * 0.25f;

        curH = nh + animPartyHover * (expandedH - nh);
        curW = pillW + animPartyHover * (expandedW - pillW);
        curX = (screenWidth - curW) / 2f;
        pivotY = notifY + curH / 2f;

        centerX_local  = curX + curW / 2f;
        acceptX_local  = centerX_local - btnW - btnGap / 2f;
        declineX_local = centerX_local + btnGap / 2f;
        btnsY_local    = notifY + nh + 5f;

        acceptX_screen  = pivotX + (acceptX_local  - pivotX) * sc;
        declineX_screen = pivotX + (declineX_local - pivotX) * sc;
        btnsY_screen    = pivotY + (btnsY_local     - pivotY) * sc;
        btnW_screen     = btnW * sc;
        btnH_screen     = btnH * sc;

        boolean overAccept  = isFreeMouse
                && mx >= acceptX_screen  && mx <= acceptX_screen  + btnW_screen
                && my >= btnsY_screen    && my <= btnsY_screen    + btnH_screen;
        boolean overDecline = isFreeMouse
                && mx >= declineX_screen && mx <= declineX_screen + btnW_screen
                && my >= btnsY_screen    && my <= btnsY_screen    + btnH_screen;

        animPartyBtnHoverAccept  += ((overAccept  ? 1f : 0f) - animPartyBtnHoverAccept)  * 0.35f;
        animPartyBtnHoverDecline += ((overDecline ? 1f : 0f) - animPartyBtnHoverDecline) * 0.35f;

        partyVisualHeight = blockH_screen + 4f;

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(pivotX, pivotY, 0);
        ms.scale(sc, sc, 1f);
        ms.translate(-pivotX, -pivotY, 0);

        int bgA     = (int)(alpha * 210);
        int notifBg = (bgA << 24) | (bgColor & 0xFFFFFF);

        if (blurEnabled) {
            com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(
                    context, curX, notifY, curW, curH, nh / 2f, 15f, notifBg);
        } else {
            drawSmoothRect(context, (int)curX, (int)notifY, (int)curW, (int)curH, nh / 2f, notifBg);
        }

        drawSmoothRect(context, (int)(curX + 8), (int)(notifY + 7), 8, 8, 4f, (alphaI << 24) | 0x22C55E);
        float tx = curX + 22f;
        float ty = notifY + 5.5f;
        drawString(context, titlePart,   tx, ty, 8.0f, (alphaI << 24) | 0xFFFFFF);  tx += titleW;
        drawString(context, " — ",       tx, ty + 0.5f, 7.5f, (alphaI << 24) | 0x555555); tx += sepW;
        drawString(context, contentPart, tx, ty + 0.5f, 7.5f, (alphaI << 24) | 0xAAAAAA);

        if (animPartyHover > 0.01f) {
            int btnAlpha = Math.max(0, Math.min(255, (int)(animPartyHover * alphaI)));

            int aR = (int)(0x1A + (0x22 - 0x1A) * animPartyBtnHoverAccept);
            int aG = (int)(0x2A + (0xC5 - 0x2A) * animPartyBtnHoverAccept);
            int aB = (int)(0x1A + (0x5E - 0x1A) * animPartyBtnHoverAccept);
            drawSmoothRect(context, (int)acceptX_local, (int)btnsY_local, (int)btnW, (int)btnH, 6f,
                    (btnAlpha << 24) | (aR << 16) | (aG << 8) | aB);
            String acceptLabel = "✓ Принять";
            float  acW = width(acceptLabel, 7.5f);
            drawString(context, acceptLabel,
                    acceptX_local + btnW / 2f - acW / 2f, btnsY_local + (btnH - 7.5f) / 2f, 7.5f,
                    (btnAlpha << 24) | 0xFFFFFF);

            int dR = (int)(0x2A + (0xEF - 0x2A) * animPartyBtnHoverDecline);
            int dG = (int)(0x1A + (0x44 - 0x1A) * animPartyBtnHoverDecline);
            int dB = (int)(0x1A + (0x44 - 0x1A) * animPartyBtnHoverDecline);
            drawSmoothRect(context, (int)declineX_local, (int)btnsY_local, (int)btnW, (int)btnH, 6f,
                    (btnAlpha << 24) | (dR << 16) | (dG << 8) | dB);
            String declineLabel = "✗ Отклонить";
            float  dcW = width(declineLabel, 7.5f);
            drawString(context, declineLabel,
                    declineX_local + btnW / 2f - dcW / 2f, btnsY_local + (btnH - 7.5f) / 2f, 7.5f,
                    (btnAlpha << 24) | 0xFFFFFF);

            PARTY_ACCEPT_BOUNDS  = new float[]{ acceptX_screen,  btnsY_screen, btnW_screen, btnH_screen };
            PARTY_DECLINE_BOUNDS = new float[]{ declineX_screen, btnsY_screen, btnW_screen, btnH_screen };
        } else {
            PARTY_ACCEPT_BOUNDS  = null;
            PARTY_DECLINE_BOUNDS = null;
        }

        ms.pop();
    }

    // =========================================================================
    //  CALLOUT
    // =========================================================================
    private static void renderCalloutIsland(DrawContext context, int screenWidth,
                                            boolean blurEnabled, int bgColor,
                                            double mx, double my, boolean isFreeMouse) {

        com.lexoravisauls.client.utils.CalloutManager.CalloutNotif notif =
                com.lexoravisauls.client.utils.CalloutManager.activeNotif;
        boolean hasNotif = notif != null && !notif.expired();

        animCallout += ((hasNotif ? 1f : 0f) - animCallout) * 0.28f;
        if (animCallout < 0.02f) {
            CALLOUT_GO_BOUNDS      = null;
            CALLOUT_DISMISS_BOUNDS = null;
            return;
        }

        float alpha  = animCallout;
        int   alphaI = Math.max(0, Math.min(255, (int)(alpha * 255)));

        String titlePart   = "Позывной";
        String contentPart = notif != null ? notif.senderName + " зовёт на мету" : "";
        float  titleW      = width(titlePart,   8.0f);
        float  sepW        = width(" — ",        7.5f);
        float  contentW    = width(contentPart, 7.5f);

        float nh     = 22f;
        float notifW = 24f + titleW + sepW + contentW + 12f;

        float partyShift = (animPartyNotif > 0.02f) ? partyVisualHeight : 0f;
        float notifX = (screenWidth - notifW) / 2f;
        float notifY = islandY + islandH + 6f + partyShift;

        float sc = 0.88f + 0.12f * animCallout;
        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(screenWidth / 2f, notifY + nh / 2f, 0);
        ms.scale(sc, sc, 1f);
        ms.translate(-screenWidth / 2f, -(notifY + nh / 2f), 0);

        int bgA     = (int)(alpha * 210);
        int notifBg = (bgA << 24) | (bgColor & 0xFFFFFF);

        if (blurEnabled) {
            com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(
                    context, notifX, notifY, notifW, nh, nh / 2f, 15f, notifBg);
        } else {
            drawSmoothRect(context, (int)notifX, (int)notifY, (int)notifW, (int)nh, nh / 2f, notifBg);
        }

        drawSmoothRect(context, (int)(notifX + 8), (int)(notifY + 7), 8, 8, 4f, (alphaI << 24) | 0xF59E0B);

        float tx = notifX + 22f;
        float ty = notifY + 5.5f;
        drawString(context, titlePart,   tx, ty, 8.0f, (alphaI << 24) | 0xFFFFFF);  tx += titleW;
        drawString(context, " — ",       tx, ty + 0.5f, 7.5f, (alphaI << 24) | 0x555555); tx += sepW;
        drawString(context, contentPart, tx, ty + 0.5f, 7.5f, (alphaI << 24) | 0xAAAAAA);

        ms.pop();
        CALLOUT_GO_BOUNDS      = null;
        CALLOUT_DISMISS_BOUNDS = null;
    }

    // =========================================================================
    //  УТИЛИТЫ
    // =========================================================================
    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // Float-версия без потери точности позиционирования
    // Используем Math.round вместо (int) чтобы избежать смещения на 0.5px
    private static void drawSmoothRectF(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0) return;
        // Пробуем передать float напрямую в шейдер (если есть такая перегрузка)
        // Иначе Math.round даёт точнее чем (int) для дробных координат
        RoundedRectShader.draw(context, Math.round(x), Math.round(y), Math.round(w), Math.round(h), radius, color);
    }

    private static int getNotifColor(NotifManager.NotifType type, int alpha) {
        int color = switch (type) {
            case MODULE_ON,  SUCCESS -> 0x22C55E;
            case MODULE_OFF, ERROR   -> 0xEF4444;
            case WARNING             -> 0xF59E0B;
            case SWAP                -> 0x9333EA;
        };
        return (alpha << 24) | color;
    }

    private static void drawString(DrawContext context, String text, float x, float y, float size, int color) {
        if (text == null || text.trim().isEmpty()) return;
        getFont().draw(context.getMatrices(), text, x, y, size, color);
    }

    private static float width(String text, float size) {
        if (text == null || text.trim().isEmpty()) return 0f;
        return getFont().getWidth(text, size);
    }

    private static int getAccentColor(long time, int fallbackColor) {
        String mode = getMode("Watermark Circle Mode", "Theme").trim();
        if (mode.equalsIgnoreCase("Gradient") || mode.equalsIgnoreCase("Градиент")
                || mode.equalsIgnoreCase("Rainbow") || mode.equalsIgnoreCase("Переливание")) {
            int theme1 = getThemeColor(fallbackColor);
            int theme2 = 0xFFFFFF;
            if (ClientData.colorSettings.containsKey("Theme Color 2")) {
                float[] hsv = ClientData.colorSettings.get("Theme Color 2");
                theme2 = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
            }
            int c1 = getColorSetting("Watermark Circle Color 1", theme1);
            int c2 = getColorSetting("Watermark Circle Color 2", theme2);
            return lerpColor(c1, c2, smoothStep((float)(Math.sin(time / 850.0) * 0.5 + 0.5)));
        }
        if (mode.equalsIgnoreCase("Custom") || mode.equalsIgnoreCase("Static")
                || mode.equalsIgnoreCase("Свой") || mode.equalsIgnoreCase("Статичный")) {
            return getColorSetting("Watermark Circle Color", getThemeColor(fallbackColor));
        }
        if (mode.equalsIgnoreCase("State") || mode.equalsIgnoreCase("Event")
                || mode.equalsIgnoreCase("Состояние")) return normalizeRgb(fallbackColor);
        return getThemeColor(fallbackColor);
    }

    private static int getThemeColor(int fallback) {
        if (ClientData.colorSettings.containsKey("Theme Color 1")) {
            float[] hsv = ClientData.colorSettings.get("Theme Color 1");
            return Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        }
        String[] keys = {"Theme Color","Client Color","Main Color","Accent Color","Gui Color","GUI Color","Color","Watermark Color"};
        for (String k : keys) {
            if (LexoraGui.numSettings.containsKey(k))    return normalizeRgb(LexoraGui.numSettings.get(k).intValue());
            if (ClientData.numSettings.containsKey(k))   return normalizeRgb(ClientData.numSettings.get(k).intValue());
        }
        return normalizeRgb(fallback);
    }

    private static int getColorSetting(String key, int fallback) {
        if (ClientData.colorSettings.containsKey(key)) {
            float[] hsv = ClientData.colorSettings.get(key);
            return Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        }
        if (LexoraGui.numSettings.containsKey(key))  return normalizeRgb(LexoraGui.numSettings.get(key).intValue());
        if (ClientData.numSettings.containsKey(key)) return normalizeRgb(ClientData.numSettings.get(key).intValue());
        return normalizeRgb(fallback);
    }

    private static boolean isModuleEnabled(String key, boolean fallback) {
        return LexoraGui.moduleStates.getOrDefault(key, ClientData.moduleStates.getOrDefault(key, fallback))
                || ClientData.moduleStates.getOrDefault(key, fallback);
    }

    private static float getNum(String key, float fallback) {
        if (LexoraGui.numSettings.containsKey(key)) return LexoraGui.numSettings.get(key);
        return ClientData.numSettings.getOrDefault(key, fallback);
    }

    private static String getMode(String key, String fallback) {
        if (LexoraGui.modeSettings.containsKey(key)) return LexoraGui.modeSettings.get(key);
        return ClientData.modeSettings.getOrDefault(key, fallback);
    }

    private static int normalizeRgb(int color) { return color & 0xFFFFFF; }

    private static float smoothStep(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static int lerpColor(int c1, int c2, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r1=(c1>>16)&0xFF, g1=(c1>>8)&0xFF, b1=c1&0xFF;
        int r2=(c2>>16)&0xFF, g2=(c2>>8)&0xFF, b2=c2&0xFF;
        return ((int)(r1+(r2-r1)*t)<<16)|((int)(g1+(g2-g1)*t)<<8)|(int)(b1+(b2-b1)*t);
    }

    private static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, float radius, int color) {
        if (width <= 0 || height <= 0) return;
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }
}