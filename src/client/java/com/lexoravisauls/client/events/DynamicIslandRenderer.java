package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.LexoraIcons;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.mixin.BossBarHudAccessor;
import com.lexoravisauls.client.utils.DynamicIslandManager;
import com.lexoravisauls.client.utils.LexoraMediaUtils;
import com.lexoravisauls.client.utils.LyricsManager;
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

    // === PVP МУЗЫКАЛЬНЫЙ ПУЗЫРЬ (Выпадающее снизу окошко с субтитрами/бегущей строкой) ===
    private static float animPvpMusicBubble = 0f;
    private static float pvpBubbleX = 0f, pvpBubbleY = 0f, pvpBubbleW = 0f, pvpBubbleH = 0f;

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

    // Плавная анимация перехода между строками караоке и названием трека
    private static String lastRenderedLyricLine = "";
    private static String prevRenderedLyricLine = "";
    private static boolean lastWasKaraoke = false;
    private static boolean prevWasKaraoke = false;
    private static float lyricTransitionProgress = 1.0f;
    private static long lastRenderFrameTime = 0L;
    private static float smoothKaraokeScrollOffset = 0f;

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
        float homeX = islandX + 12f, homeY = islandY + (20f - 7f) / 2f, homeW = 7f, homeH = 7f;
        if (animPillX < -999f) {
            animPillX = homeX;
            animPillY = homeY;
            animPillW = homeW;
            animPillH = homeH;
        }

        float speed = 0.16f;
        animPillX += (homeX - animPillX) * speed;
        animPillY += (homeY - animPillY) * speed;
        animPillW += (homeW - animPillW) * speed;
        animPillH += (homeH - animPillH) * speed;

        float pillAlphaF = baseAlpha * (1f - MathHelper.clamp(animPvpAlpha, 0f, 1f));
        if (pillAlphaF <= 0.02f) return;
        int pillColor = (Math.round(255 * pillAlphaF) << 24) | (currentColor & 0xFFFFFF);
        drawSmoothRectF(context, animPillX, animPillY, animPillW, animPillH, animPillH / 2f, pillColor);
    }

    public static MsdfFont getFont() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    public static boolean isIslandLyricsEnabled() {
        boolean moduleEnabled = ClientData.moduleStates.getOrDefault("Kinetic Lyrics",
                LexoraGui.moduleStates.getOrDefault("Kinetic Lyrics", true));
        if (!moduleEnabled) return false;
        return ClientData.moduleStates.getOrDefault("Lyrics In Island",
                LexoraGui.moduleStates.getOrDefault("Lyrics In Island", true));
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
        // Пункт 2: пока идёт PVP, музыкальный островок должен плавно уступить место PVP-виду,
        // даже если трек всё ещё играет (hasMedia остаётся true — плеер сам не встаёт на паузу).
        // Поэтому цель для animMedia форсируем в 0, когда PvpBossBarTracker.isInPvp() — лерп тот
        // же (0.35f), так что "плавно замена" получается сама собой, без отдельного таймера.
        boolean pvpActiveNow = PvpBossBarTracker.isInPvp();
        float   mediaTarget  = (hasMedia && !pvpActiveNow) ? 1f : 0f;
        animMedia += (mediaTarget - animMedia) * 0.35f;

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
        if (!isGuiOpen) {
            com.lexoravisauls.client.gui.modern.ModernClickGui.activeIslandPanel = null;
        }
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
        float targetTotalW = defaultW + (mediaW - defaultW) * animMedia;
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

            float radius = Math.min(islandH / 2.0f, 15f);
    int islandAlpha = (int)(255 * fadeFactor);
    HudThemeHelper.drawHudPanel(context, islandX, islandY, islandW, islandH, radius, islandAlpha, blurEnabled);
    int bgColor = HudThemeHelper.getSlotBgColor(islandAlpha);

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
    int tpColorVal;
    if (HudThemeHelper.isDark()) {
        tpColorVal = (int)(40 + 215 * tpFactor);
    } else {
        tpColorVal = (int)(220 - 200 * tpFactor);
    }
    int tpColor = ((int)(255 * fadeFactor) << 24)
            | (tpColorVal << 16) | (tpColorVal << 8) | tpColorVal;

    LexoraIcons.draw(context, LexoraIcons.Icon.AIRPLANE, timeStartX, outY, smallIconSize, tpColor);
    drawString(context, timeStr,  timeStartX + smallIconSize + 3, textY, outTextSize, tpColor);
    LexoraIcons.draw(context, getWifiIcon(ping), pingStartX, outY, smallIconSize, tpColor);
    drawString(context, pingStr,  pingStartX + smallIconSize + 3, textY, outTextSize, tpColor);

updateAndDrawPill(context, fadeFactor * (1f - animMedia), currentColor);
    if (animMedia < 0.99f) renderDefaultIsland(context, ms, time, fadeFactor, currentColor, 1f - animMedia);
    if (animMedia > 0.01f) renderMusicIsland(context, ms, time, fadeFactor, opacity, screenWidth);
    renderPvpMusicBubble(context, ms, time, fadeFactor, opacity, currentColor);
    // quick access categories removed

    renderPartyInviteIsland(context, screenWidth, blurEnabled, bgColor, mx, my, isFreeMouse, scale);
    renderCalloutIsland(context, screenWidth, blurEnabled, bgColor, mx, my, isFreeMouse);

    ms.pop();
}

// =========================================================================
private static int withAlphaLocal(int argb, int alpha255) {
        alpha255 = Math.max(0, Math.min(255, alpha255));
        return (alpha255 << 24) | (argb & 0x00FFFFFF);
    }

    public static boolean isMusicExpanded() {
        return animExpand > 0.05f && LexoraMediaUtils.hasMedia();
    }

    private static String formatTime(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10 ? "0" + sec : sec);
    }

    // =========================================================================
    //  МУЗЫКАЛЬНЫЙ ОСТРОВОК
    // =========================================================================
    private static void renderMusicIsland(DrawContext context, MatrixStack ms,
                                          long time, float fadeFactor, float opacity, int screenWidth) {
        try {
            float e    = animExpand;
            float invE = 1f - e;
            int alpha  = (int)(255 * fadeFactor * opacity);
            if (alpha <= 5) return;

            float effE   = Math.max(0, (islandH - 20f) / 44f);
            float tSize  = 14f + 34f * effE;
            float tX     = islandX + 4f + 3f * effE;
            float tY     = islandY + 3f + 5f * effE;
            float tRadius = 7f - 1f * effE;

            String title = LexoraMediaUtils.getTitle();
            if (title == null || title.isEmpty()) title = "Unknown Track";
            String artist = LexoraMediaUtils.getAuthor();
            if (artist == null || artist.isEmpty()) artist = "Unknown Artist";
            long progress = LexoraMediaUtils.getProgress();
            long duration = LexoraMediaUtils.getDuration();

            // Обновляем LyricsManager каждый кадр
            try {
                LyricsManager.update(title, artist, duration);
            } catch (Throwable ignored) {}

            // ── СВЁРНУТЫЙ РЕЖИМ (КАРАОКЕ / НАЗВАНИЕ ПЕСНИ) ─────────────────────────
            if (invE > 0.1f) {
                float quickAccessW = guiIconsBlockW() * animGuiOpen;
                float titleX  = islandX + 23.5f;
                float titleY  = islandY + 5.5f;
                float maxTextW = Math.max(20f, islandW - 41f - quickAccessW);
                float scissorLeft = islandX + 22.5f;
                float scissorRight = islandX + islandW - 17.5f;
                float scissorTop = islandY + 1f;
                float scissorBottom = islandY + 19f;

                if (scissorRight > scissorLeft + 5f) {
                    context.enableScissor((int)scissorLeft, (int)scissorTop, (int)scissorRight, (int)scissorBottom);
                    try {
                        int curAlpha = (int)(alpha * invE);
                        String displayLine;
                        float karaokeProgress = 0f;
                        boolean isKaraoke = false;

                        // При открытом GUI или если выключен тумблер "В Dynamic Island", показываем ТОЛЬКО чистое название песни без караоке и без сдвигов
                        boolean islandLyrics = isIslandLyricsEnabled();
                        boolean isSinging = islandLyrics && LyricsManager.hasLyrics() && LyricsManager.isSinging(progress) && animGuiOpen < 0.15f;

                        if (animGuiOpen >= 0.15f || !islandLyrics) {
                            displayLine = title;
                            isKaraoke = false;
                        } else if (isSinging) {
                            String lyric = LyricsManager.getCurrentLine(progress);
                            karaokeProgress = LyricsManager.getLineProgress(progress);
                            displayLine = (lyric != null && !lyric.trim().isEmpty()) ? lyric : title;
                            isKaraoke = (lyric != null && !lyric.trim().isEmpty());
                        } else if (LyricsManager.isFetching()) {
                            displayLine = "♪ Загрузка текста...";
                            isKaraoke = false;
                        } else {
                            displayLine = title;
                            isKaraoke = false;
                        }

                        if (lastRenderFrameTime == 0L) lastRenderFrameTime = time;
                        float dt = Math.min(0.05f, (time - lastRenderFrameTime) / 1000f);
                        lastRenderFrameTime = time;

                        if (!displayLine.equals(lastRenderedLyricLine)) {
                            prevRenderedLyricLine = lastRenderedLyricLine;
                            prevWasKaraoke = lastWasKaraoke;
                            lastRenderedLyricLine = displayLine;
                            lastWasKaraoke = isKaraoke;
                            lyricTransitionProgress = 0f;
                        }

                        if (lyricTransitionProgress < 1.0f) {
                            lyricTransitionProgress = Math.min(1.0f, lyricTransitionProgress + dt * 6.0f);
                        }

                        float textW = width(displayLine, FONT_SIZE);
                        float scrollOffset = 0;

                        if (textW > maxTextW) {
                            if (isKaraoke) {
                                float activeX = textW * karaokeProgress;
                                float center = maxTextW / 2f;
                                scrollOffset = -(activeX - center);
                                scrollOffset = Math.min(0, scrollOffset);
                                scrollOffset = Math.max(-(textW - maxTextW), scrollOffset);
                            } else {
                                float maxScroll = textW - maxTextW;
                                float cycle = (time % 8000L) / 8000f;
                                float t = (float)(Math.sin(cycle * Math.PI * 2.0) * 0.5 + 0.5);
                                scrollOffset = -maxScroll * t;
                            }
                        }

                        int colorDone    = (curAlpha << 24) | (HudThemeHelper.isDark() ? 0xF1F1F6 : 0x0C0C10);
                        int colorPending = (curAlpha << 24) | (HudThemeHelper.isDark() ? 0x777788 : 0x888899);

                        if (lyricTransitionProgress < 1.0f && !prevRenderedLyricLine.isEmpty()) {
                            // 🪜 ЛЕСЕНКА: Ступенчатый уход старой строки (слова улетают вверх по очереди)
                            drawLadderExit(context, prevRenderedLyricLine, titleX + smoothKaraokeScrollOffset, titleY, FONT_SIZE, curAlpha, lyricTransitionProgress, prevWasKaraoke);

                            // Плавное появление новой строки снизу
                            float newAlphaFactor = lyricTransitionProgress;
                            int nCurAlpha = (int)(curAlpha * newAlphaFactor);
                            float nY = titleY + (6f * (1f - lyricTransitionProgress));
                            int nDone = (nCurAlpha << 24) | (HudThemeHelper.isDark() ? 0xF1F1F6 : 0x0C0C10);
                            int nPending = (nCurAlpha << 24) | (HudThemeHelper.isDark() ? 0x777788 : 0x888899);

                            if (isKaraoke) {
                                drawDynamicKaraokeLine(context, displayLine, titleX, nY, FONT_SIZE, nDone, nPending, karaokeProgress, maxTextW, dt);
                            } else {
                                getFont().draw(context, displayLine, titleX + scrollOffset, nY, FONT_SIZE, nDone);
                            }
                        } else {
                            if (isKaraoke) {
                                drawDynamicKaraokeLine(context, displayLine, titleX, titleY, FONT_SIZE, colorDone, colorPending, karaokeProgress, maxTextW, dt);
                            } else {
                                getFont().draw(context, displayLine, titleX + scrollOffset, titleY, FONT_SIZE, colorDone);
                            }
                        }
                    } finally {
                        context.disableScissor();
                    }
                }
            }

            // ── ОБЛОЖКА ТРЕКА ──────────────────────────────────────────────────────
            Identifier thumb = LexoraMediaUtils.getThumbnail();
            boolean thumbDrawn = false;
            if (thumb != null) {
                try {
                    RoundedRectShader.drawTextured(context, thumb, tX, tY, tSize, tSize, tRadius, 0, 0, 1, 1, (alpha << 24) | 0xFFFFFF);
                    thumbDrawn = true;
                } catch (Throwable ignored) {
                }
            }
            if (!thumbDrawn) {
                drawSmoothRectF(context, tX, tY, tSize, tSize, tRadius, (alpha << 24) | 0x282830);
                LexoraIcons.draw(context, LexoraIcons.Icon.MONITOR, tX + (tSize - 10f)/2f, tY + (tSize - 10f)/2f, 10f, (alpha << 24) | 0x666670);
            }

            // ── ЗВУКОВОЙ ВИЗУАЛИЗАТОР (СПРАВА В СВЁРНУТОМ ВИДЕ, только если GUI закрыт) ────
            if (invE > 0.05f && animGuiOpen < 0.1f) {
                int   visAlpha = (int)(alpha * invE * (1f - animGuiOpen * 10f));
                if (visAlpha > 5) {
                    float visX     = islandX + islandW - 14f;
                    boolean playing = LexoraMediaUtils.isPlaying();
                    float h1 = playing ? 4f + (float)Math.sin((time % 10000L) * 0.015) * 3f : 2f;
                    float h2 = playing ? 4f + (float)Math.cos((time % 10000L) * 0.012 + 1.0) * 3f : 2f;
                    float h3 = playing ? 4f + (float)Math.sin((time % 10000L) * 0.018 + 2.0) * 3f : 2f;
                    int   visColor = (visAlpha << 24) | (HudThemeHelper.isDark() ? 0xFFFFFF : 0x0C0C10);
                    float centerY  = islandY + 10f;
                    drawSmoothRect(context, (int)visX,     (int)(centerY - h1/2f), 2, (int)h1, 1f, visColor);
                    drawSmoothRect(context, (int)visX + 3, (int)(centerY - h2/2f), 2, (int)h2, 1f, visColor);
                    drawSmoothRect(context, (int)visX + 6, (int)(centerY - h3/2f), 2, (int)h3, 1f, visColor);
                }
            }

        // ── РАСШИРЕННЫЙ РЕЖИМ (ПЛЕЕР ПРИ НАВЕДЕНИИ) ──────────────────────────
        float controlsTarget = e > 0.85f ? 1f : 0f;
        float controlsSpeed  = controlsTarget > animControls ? 0.25f : 0.85f;
        animControls += (controlsTarget - animControls) * controlsSpeed;

        if (animControls > 0.01f) {
            float t        = animControls;
            float eased    = 1f - (float)Math.pow(1f - t, 3f);
            float rise     = (1f - eased) * 4f;
            int   expAlpha = (int)(alpha * eased);

            if (expAlpha > 3) {
                float afterArt = tX + tSize + 8f;
                float contentW = islandX + islandW - 8f - afterArt;

                // Название трека и артист
                float titleY  = islandY + 6.5f - rise;
                float artistY = islandY + 17f - rise;

                context.enableScissor((int)afterArt, (int)islandY, (int)(afterArt + contentW), (int)(islandY + 64));
                try {
                    float titleW = width(title, 8.5f);
                    float maxTitleScroll = Math.max(0, titleW - contentW);
                    float titleScroll = 0;
                    if (maxTitleScroll > 0) {
                        float cycle = (time % 10000L) / 10000.0f;
                        float wave  = MathHelper.clamp((float)Math.sin(cycle * Math.PI * 2) * 1.3f, -1f, 1f);
                        titleScroll = -maxTitleScroll * (wave + 1f) / 2f;
                    }
                    drawString(context, title, afterArt + titleScroll, titleY, 8.5f, (expAlpha << 24) | (HudThemeHelper.isDark() ? 0xFFFFFF : 0x0C0C10));

                    float artistW = width(artist, 7.0f);
                    float maxArtistScroll = Math.max(0, artistW - contentW);
                    float artistScroll = 0;
                    if (maxArtistScroll > 0) {
                        float cycle = (time % 10000L) / 10000.0f;
                        float wave  = MathHelper.clamp((float)Math.sin(cycle * Math.PI * 2) * 1.3f, -1f, 1f);
                        artistScroll = -maxArtistScroll * (wave + 1f) / 2f;
                    }
                    drawString(context, artist, afterArt + artistScroll, artistY, 7.0f, (expAlpha << 24) | (HudThemeHelper.isDark() ? 0x9E9EA6 : 0x444455));
                } finally {
                    context.disableScissor();
                }

                // ── Полоса времени ──
                float p = duration > 0 ? MathHelper.clamp((float)progress / duration, 0f, 1f) : 0f;
                float barX = afterArt;
                float barW = contentW;
                float barH = 3.0f;
                float barY = islandY + 29.5f - rise;
                float barR = barH / 2f;

                if (barW > 4f) {
                    // Тёмная подложка полоски
                    drawSmoothRectF(context, barX, barY, barW, barH, barR, (expAlpha << 24) | (HudThemeHelper.isDark() ? 0x2C2C35 : 0xD5D9E2));

                    // Белое заполнение
                    if (p > 0.001f) {
                        float fillW = Math.max(barH, barW * p);
                        drawSmoothRectF(context, barX, barY, fillW, barH, barR, (expAlpha << 24) | (HudThemeHelper.isDark() ? 0xFFFFFF : 0x0C0C10));

                        // Аккуратная светящаяся точка на конце
                        float dotR = 2.5f;
                        drawSmoothRectF(context, barX + fillW - dotR, barY + barH/2f - dotR, dotR*2, dotR*2, dotR, (expAlpha << 24) | (HudThemeHelper.isDark() ? 0xFFFFFF : 0x0C0C10));
                    }

                    // Текст времени: 1:23 / 3:45
                    String curTimeStr = formatTime(progress);
                    String durTimeStr = formatTime(duration);
                    float timeTextY   = barY + barH + 2.0f;
                    int timeColor     = (expAlpha << 24) | (HudThemeHelper.isDark() ? 0x6E6E78 : 0x444455);
                    drawString(context, curTimeStr, barX, timeTextY, 5.5f, timeColor);
                    float durW = width(durTimeStr, 5.5f);
                    drawString(context, durTimeStr, barX + barW - durW, timeTextY, 5.5f, timeColor);
                }

                // ── Кнопки управления (белые, чистые, без фоновых плашек) ──
                LexoraIcons.Icon playIcon = LexoraMediaUtils.isPlaying() ? LexoraIcons.Icon.PAUSE : LexoraIcons.Icon.PLAY;
                float btnSide       = 11f;
                float btnPlay       = 14f;
                float btnGap        = 22f;
                float btnZoneCenter = afterArt + contentW / 2f;
                float btnY          = islandY + 47f - rise;
                float playX         = btnZoneCenter - btnPlay / 2f;
                float prevX         = btnZoneCenter - btnGap - btnSide;
                float nextX         = btnZoneCenter + btnGap;

                int mainBtnColor = (expAlpha << 24) | (HudThemeHelper.isDark() ? 0xFFFFFF : 0x0C0C10);
                int sideBtnColor = (expAlpha << 24) | (HudThemeHelper.isDark() ? 0xD8D8E0 : 0x444455);

                LexoraIcons.draw(context, LexoraIcons.Icon.SKIP_PREV, prevX, btnY + (btnPlay - btnSide)/2f, btnSide, sideBtnColor);
                LexoraIcons.draw(context, playIcon, playX, btnY, btnPlay, mainBtnColor);
                LexoraIcons.draw(context, LexoraIcons.Icon.SKIP_NEXT, nextX, btnY + (btnPlay - btnSide)/2f, btnSide, sideBtnColor);
            }
        }
    } catch (Throwable ignored) {
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
        if (handlePartyNotifClick(mx, my, button)) return true;
        if (animPvpMusicBubble > 0.5f && pvpBubbleW > 0) {
            float sw = lastScreenWidth, sc = lastHudScale;
            float bL = toScreenX(pvpBubbleX, sw, sc), bR = toScreenX(pvpBubbleX + pvpBubbleW, sw, sc);
            float bT = toScreenY(pvpBubbleY, sc), bB = toScreenY(pvpBubbleY + pvpBubbleH, sc);
            if (mx >= bL && mx <= bR && my >= bT && my <= bB) {
                if (button == 0) {
                    LexoraMediaUtils.togglePlay();
                } else if (button == 1) {
                    LexoraMediaUtils.next();
                }
                return true;
            }
        }
        if (!LexoraMediaUtils.hasMedia() || animExpand < 0.5f) return false;
        float effE          = Math.max(0, (islandH - 20f) / 44f);
        float tSize         = 14f + 34f * effE;
        float tX            = islandX + 4f + 3f * effE;
        float afterArt      = tX + tSize + 8f;
        float contentW      = islandX + islandW - 8f - afterArt;
        float btnZoneCenter = afterArt + contentW / 2f;
        float btnY          = islandY + 47f;
        float btnSide       = 11f, btnPlay = 14f, btnGap = 22f, hitPad = 6f;
        float prevX         = btnZoneCenter - btnGap - btnSide;
        float playX         = btnZoneCenter - btnPlay / 2f;
        float nextX         = btnZoneCenter + btnGap;

        float sw = lastScreenWidth, sc = lastHudScale;
        float prevL = toScreenX(prevX - hitPad, sw, sc), prevR = toScreenX(prevX + btnSide + hitPad, sw, sc);
        float playL = toScreenX(playX - hitPad, sw, sc), playR = toScreenX(playX + btnPlay + hitPad, sw, sc);
        float nextL = toScreenX(nextX - hitPad, sw, sc), nextR = toScreenX(nextX + btnSide + hitPad, sw, sc);
        float btnT  = toScreenY(btnY - hitPad, sc),      btnB  = toScreenY(btnY + btnPlay + hitPad, sc);

        if (mx >= prevL && mx <= prevR && my >= btnT && my <= btnB) { LexoraMediaUtils.prev(); return true; }
        if (mx >= playL && mx <= playR && my >= btnT && my <= btnB) { LexoraMediaUtils.togglePlay(); return true; }
        if (mx >= nextL && mx <= nextR && my >= btnT && my <= btnB) { LexoraMediaUtils.next(); return true; }

        // Клик по полосе времени для перемотки
        float barX = afterArt;
        float barW = contentW;
        float barY = islandY + 29.5f;
        float barL = toScreenX(barX, sw, sc), barR = toScreenX(barX + barW, sw, sc);
        float barT = toScreenY(barY - 4f, sc), barB = toScreenY(barY + 10f, sc);
        if (mx >= barL && mx <= barR && my >= barT && my <= barB) {
            float seekProgress = MathHelper.clamp((float)(mx - barL) / (barR - barL), 0f, 1f);
            LexoraMediaUtils.seek(seekProgress);
            return true;
        }

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
        // Пункт 3: если модуль только что переключили по бинду — на короткий миг (см.
        // DynamicIslandManager.RECENT_WINDOW_TICKS) его текст перебивает даже "Вы в ПВП режиме!",
        // чтобы игрок увидел подтверждение своего действия. Категории (Party/Events/GUI/Configs)
        // этим не перебиваются — про них в задаче речи не было, трогаем только пару PVP vs тоггл.
        boolean moduleJustTriggered = DynamicIslandManager.isRecentlyTriggered();
        String targetTextStr;
        if (moduleJustTriggered) {
            targetTextStr = DynamicIslandManager.getTargetText();
        } else if (pvpActive) {
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

        float wave     = (float)(Math.sin(time / 350.0) * 0.5 + 0.5);
        int shimmerRGB;
        if (HudThemeHelper.isDark()) {
            int colorVal = (int)(130 + 125 * wave);
            shimmerRGB = (colorVal << 16) | (colorVal << 8) | colorVal;
        } else {
            int colorVal = (int)(15 + 85 * (1f - wave));
            shimmerRGB = (colorVal << 16) | (colorVal << 8) | colorVal;
        }

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
    //  PVP МУЗЫКАЛЬНЫЙ ПУЗЫРЬ (Выпадает вниз во время PVP, если играет музыка)
    // =========================================================================
    private static void renderPvpMusicBubble(DrawContext context, MatrixStack ms, long time,
                                             float fadeFactor, float opacity, int currentColor) {
        boolean pvpActive = PvpBossBarTracker.isInPvp();
        boolean hasMedia = LexoraMediaUtils.hasMedia();
        float target = (pvpActive && hasMedia) ? 1.0f : 0.0f;
        float speed = target > animPvpMusicBubble ? 0.14f : 0.22f;
        animPvpMusicBubble += (target - animPvpMusicBubble) * speed;

        if (animPvpMusicBubble <= 0.005f) {
            pvpBubbleW = 0f;
            return;
        }

        float alphaFactor = MathHelper.clamp(animPvpMusicBubble * 1.5f, 0f, 1f) * fadeFactor * opacity;
        int alpha = (int)(255 * alphaFactor);
        if (alpha <= 5) return;

        String title = LexoraMediaUtils.getTitle();
        if (title == null || title.isEmpty()) title = "Unknown Track";
        String artist = LexoraMediaUtils.getAuthor();
        if (artist == null) artist = "";
        long progress = LexoraMediaUtils.getProgress();
        long duration = LexoraMediaUtils.getDuration();

        try {
            LyricsManager.update(title, artist, duration);
        } catch (Throwable ignored) {}

        boolean islandLyrics = isIslandLyricsEnabled();
        String displayLine;
        boolean isKaraoke = false;
        float karaokeProgress = 0f;

        if (islandLyrics) {
            boolean isSinging = LyricsManager.hasLyrics() && LyricsManager.isSinging(progress);
            if (isSinging) {
                String lyric = LyricsManager.getCurrentLine(progress);
                if (lyric != null && !lyric.trim().isEmpty()) {
                    displayLine = lyric;
                    isKaraoke = true;
                    karaokeProgress = LyricsManager.getLineProgress(progress);
                } else {
                    displayLine = title;
                }
            } else if (LyricsManager.isFetching()) {
                displayLine = "♪ Загрузка текста...";
            } else {
                displayLine = title + (artist.isEmpty() ? "" : " - " + artist);
            }
        } else {
            // Если тумблер выключен — "просто название музыки пишет"
            displayLine = title + (artist.isEmpty() ? "" : " - " + artist);
        }

        float textSize = 7.5f;
        float textW = width(displayLine, textSize);
        float bubbleH = 18f;
        float minW = 120f;
        float maxW = Math.max(160f, Math.min(240f, islandW + 40f));
        float targetBubbleW = Math.max(minW, Math.min(maxW, textW + 36f));

        if (pvpBubbleW == 0f) pvpBubbleW = targetBubbleW;
        pvpBubbleW += (targetBubbleW - pvpBubbleW) * 0.22f;

        float bubbleW = pvpBubbleW;
        float bubbleX = islandX + (islandW - bubbleW) / 2f;

        // Плавная анимация опускания пузыря:
        // Пузырь мягко выпадает из-под нижней кромки основного островка
        float t = animPvpMusicBubble;
        float eased = 1.0f - (float)Math.pow(1.0f - t, 3.0);
        float startY = islandY + islandH - bubbleH + 2f;
        float finalY = islandY + islandH + 4f;
        float bubbleY = MathHelper.lerp(eased, startY, finalY);

        pvpBubbleX = bubbleX;
        pvpBubbleY = bubbleY;
        pvpBubbleH = bubbleH;

        ms.push();

        // 1. Аккуратная подложка панели как у других худов (без полоски сверху)
        boolean blurEnabled = LexoraGui.moduleStates.getOrDefault("Watermark Blur", true);
        float radius = bubbleH / 2.0f;
        HudThemeHelper.drawHudPanel(context, bubbleX, bubbleY, bubbleW, bubbleH, radius, (int)(255 * alphaFactor), blurEnabled);

        // 2. Мини-эквалайзер слева (3 прыгающие полоски в такт)
        float eqX = bubbleX + 7f;
        float eqCenterY = bubbleY + bubbleH / 2f;
        boolean playing = LexoraMediaUtils.isPlaying();
        float h1 = playing ? 3.0f + (float)Math.sin((time % 5000L) * 0.020) * 2.5f : 2f;
        float h2 = playing ? 3.8f + (float)Math.cos((time % 5000L) * 0.016 + 1.0) * 3.0f : 2f;
        float h3 = playing ? 3.0f + (float)Math.sin((time % 5000L) * 0.024 + 2.0) * 2.5f : 2f;
        int eqColor = withAlphaLocal(HudThemeHelper.isDark() ? 0xFFFFFF : 0x0C0C10, (int)(220 * alphaFactor));
        drawSmoothRect(context, (int)eqX,     (int)(eqCenterY - h1 / 2f), 2, (int)h1, 1f, eqColor);
        drawSmoothRect(context, (int)eqX + 3, (int)(eqCenterY - h2 / 2f), 2, (int)h2, 1f, eqColor);
        drawSmoothRect(context, (int)eqX + 6, (int)(eqCenterY - h3 / 2f), 2, (int)h3, 1f, eqColor);

        // 3. Бегущая строка / субтитры с отсечением (scissor)
        float textStartX = bubbleX + 19f;
        float textEndX = bubbleX + bubbleW - 7f;
        float availW = textEndX - textStartX;

        if (availW > 10f) {
            context.enableScissor((int)textStartX, (int)bubbleY, (int)textEndX, (int)(bubbleY + bubbleH));
            try {
                float lineTextW = width(displayLine, textSize);
                float scrollOffset = 0f;
                if (lineTextW > availW) {
                    if (isKaraoke) {
                        float activeX = lineTextW * karaokeProgress;
                        float center = availW / 2f;
                        scrollOffset = -(activeX - center);
                        scrollOffset = Math.min(0, scrollOffset);
                        scrollOffset = Math.max(-(lineTextW - availW), scrollOffset);
                    } else {
                        float maxScroll = lineTextW - availW;
                        float cycle = (time % 8000L) / 8000f;
                        float wave = (float)(Math.sin(cycle * Math.PI * 2.0) * 0.5 + 0.5);
                        scrollOffset = -maxScroll * wave;
                    }
                } else {
                    scrollOffset = (availW - lineTextW) / 2f;
                }

                int colorDone    = (alpha << 24) | (HudThemeHelper.isDark() ? 0xF1F1F6 : 0x0C0C10);
                int colorPending = (alpha << 24) | (HudThemeHelper.isDark() ? 0x777788 : 0x888899);
                float textY = bubbleY + (bubbleH - textSize) / 2f - 0.5f;

                if (isKaraoke) {
                    float dt = 0.016f;
                    drawDynamicKaraokeLine(context, displayLine, textStartX, textY, textSize, colorDone, colorPending, karaokeProgress, availW, dt);
                } else {
                    getFont().draw(context, displayLine, textStartX + scrollOffset, textY, textSize, colorDone);
                }
            } finally {
                context.disableScissor();
            }
        }

        ms.pop();
    }

    // =========================================================================
    //  ПАТИ-УВЕДОМЛЕНИЕ
    // =========================================================================
    private static void renderPartyInviteIsland(DrawContext context, int screenWidth,
                                                boolean blurEnabled, int bgColor,
                                                double mx, double my, boolean isFreeMouse, float scale) {

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

        float cardW = 210f;
        float cardH = 50f;
        float cardX = (screenWidth - cardW) / 2f;
        float cardY = islandY + islandH + 6f;

        float sc = 0.90f + 0.10f * animPartyNotif;
        float cx = screenWidth / 2f;
        float pivotY = cardY + cardH / 2f;

        float btnW = 98f;
        float btnH = 16f;
        float gap  = 6f;
        float btnsY = cardY + 28f;
        float acceptX = cardX + 4f;
        float declineX = acceptX + btnW + gap;

        // Точные экранные координаты для мыши с учётом обеих матриц
        float acceptScreenX = cx + (acceptX - cx) * sc * scale;
        float acceptScreenY = islandY + (pivotY + (btnsY - pivotY) * sc - islandY) * scale;
        float acceptScreenW = btnW * sc * scale;
        float acceptScreenH = btnH * sc * scale;

        float declineScreenX = cx + (declineX - cx) * sc * scale;
        float declineScreenY = islandY + (pivotY + (btnsY - pivotY) * sc - islandY) * scale;
        float declineScreenW = btnW * sc * scale;
        float declineScreenH = btnH * sc * scale;

        PARTY_ACCEPT_BOUNDS  = new float[]{ acceptScreenX,  acceptScreenY,  acceptScreenW,  acceptScreenH };
        PARTY_DECLINE_BOUNDS = new float[]{ declineScreenX, declineScreenY, declineScreenW, declineScreenH };

        boolean overAccept = isFreeMouse
                && mx >= acceptScreenX && mx <= acceptScreenX + acceptScreenW
                && my >= acceptScreenY && my <= acceptScreenY + acceptScreenH;
        boolean overDecline = isFreeMouse
                && mx >= declineScreenX && mx <= declineScreenX + declineScreenW
                && my >= declineScreenY && my <= declineScreenY + declineScreenH;

        animPartyBtnHoverAccept  += ((overAccept  ? 1f : 0f) - animPartyBtnHoverAccept)  * 0.35f;
        animPartyBtnHoverDecline += ((overDecline ? 1f : 0f) - animPartyBtnHoverDecline) * 0.35f;

        partyVisualHeight = (cardH + 6f) * sc * scale;

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(cx, pivotY, 0);
        ms.scale(sc, sc, 1f);
        ms.translate(-cx, -pivotY, 0);

        int bgA = (int)(alpha * 230);
        int cardBg = (bgA << 24) | 0x0E0E14;

        if (blurEnabled) {
            com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(
                    context, cardX, cardY, cardW, cardH, 7.5f, 16f, cardBg);
        } else {
            drawSmoothRect(context, (int)cardX, (int)cardY, (int)cardW, (int)cardH, 7.5f, cardBg);
        }
        // Тонкая стильная рамка
        drawSmoothRect(context, (int)cardX, (int)cardY, (int)cardW, 1, 0f, (Math.min(60, alphaI) << 24) | 0xFFFFFF);

        // Индикатор (зеленая точка)
        drawSmoothRect(context, (int)(cardX + 7), (int)(cardY + 8), 6, 6, 3f, (alphaI << 24) | 0x10B981);

        // Заголовок
        String title = "Запрос в пати";
        drawString(context, title, cardX + 17f, cardY + 7f, 7.5f, (alphaI << 24) | 0xFFFFFF);

        // Таймер обратного отсчета
        if (notif != null) {
            String secStr = Math.max(1, (int)Math.ceil(notif.secondsLeft())) + "с";
            float secW = width(secStr, 6.5f);
            float tagW = secW + 8f;
            float tagX = cardX + cardW - tagW - 6f;
            drawSmoothRect(context, (int)tagX, (int)(cardY + 6), (int)tagW, 11, 3.5f, (Math.min(45, alphaI) << 24) | 0xFFFFFF);
            drawString(context, secStr, tagX + 4f, cardY + 7.5f, 6.5f, (alphaI << 24) | 0xA0A0B0);
        }

        // Никнейм заявителя
        String reqName = notif != null ? notif.requesterName : "Игрок";
        float nameW = width(reqName, 8.0f);
        drawString(context, reqName, cardX + 7f, cardY + 18f, 8.0f, (alphaI << 24) | 0xFFFFFF);
        drawString(context, "хочет вступить в пати", cardX + 7f + nameW + 4f, cardY + 18.5f, 7.0f, (alphaI << 24) | 0x888899);

        // Кнопка [✓ Принять]
        int aR = (int)(0x10 + (0x34 - 0x10) * animPartyBtnHoverAccept);
        int aG = (int)(0xB9 + (0xD3 - 0xB9) * animPartyBtnHoverAccept);
        int aB = (int)(0x81 + (0x99 - 0x81) * animPartyBtnHoverAccept);
        int acceptCol = (alphaI << 24) | (aR << 16) | (aG << 8) | aB;
        drawSmoothRect(context, (int)acceptX, (int)btnsY, (int)btnW, (int)btnH, 4.0f, acceptCol);
        String acceptLabel = "✓ Принять";
        float acW = width(acceptLabel, 7.0f);
        drawString(context, acceptLabel, acceptX + (btnW - acW) / 2f, btnsY + (btnH - 7.0f) / 2f, 7.0f, (alphaI << 24) | 0xFFFFFF);

        // Кнопка [✗ Отклонить]
        int dR = (int)(0xEF + (0xF8 - 0xEF) * animPartyBtnHoverDecline);
        int dG = (int)(0x44 + (0x71 - 0x44) * animPartyBtnHoverDecline);
        int dB = (int)(0x44 + (0x71 - 0x44) * animPartyBtnHoverDecline);
        int declineCol = (alphaI << 24) | (dR << 16) | (dG << 8) | dB;
        drawSmoothRect(context, (int)declineX, (int)btnsY, (int)btnW, (int)btnH, 4.0f, declineCol);
        String declineLabel = "✗ Отклонить";
        float dcW = width(declineLabel, 7.0f);
        drawString(context, declineLabel, declineX + (btnW - dcW) / 2f, btnsY + (btnH - 7.0f) / 2f, 7.0f, (alphaI << 24) | 0xFFFFFF);

        ms.pop();
    }

    public static boolean handlePartyNotifClick(double mx, double my, int button) {
        if (button != 0) return false;
        var notif = com.lexoravisauls.client.party.LexoraPartyManager.activeInviteNotif;
        if (notif == null) return false;

        float[] ab = PARTY_ACCEPT_BOUNDS;
        float[] db = PARTY_DECLINE_BOUNDS;

        if (ab != null && mx >= ab[0] && mx <= ab[0] + ab[2] && my >= ab[1] && my <= ab[1] + ab[3]) {
            if (notif.onAccept != null) notif.onAccept.run();
            com.lexoravisauls.client.party.LexoraPartyManager.activeInviteNotif = null;
            PARTY_ACCEPT_BOUNDS = null;
            PARTY_DECLINE_BOUNDS = null;
            return true;
        }
        if (db != null && mx >= db[0] && mx <= db[0] + db[2] && my >= db[1] && my <= db[1] + db[3]) {
            if (notif.onDecline != null) notif.onDecline.run();
            com.lexoravisauls.client.party.LexoraPartyManager.activeInviteNotif = null;
            PARTY_ACCEPT_BOUNDS = null;
            PARTY_DECLINE_BOUNDS = null;
            return true;
        }
        return false;
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

    // ═══════════════════════════════════════════════════════════════════════
    //  APPLE MUSIC STYLE: ВЫДЕЛЕНИЕ АКТИВНОГО СЛОВА С ЦЕНТРИРОВАНИЕМ
    // ═══════════════════════════════════════════════════════════════════════

    private static void drawDynamicKaraokeLine(DrawContext context, String line, float baseX, float baseY, float fontSize,
                                               int colorDone, int colorPending, float progress, float maxViewW, float dt) {
        if (line == null || line.trim().isEmpty()) return;
        try {
            MsdfFont font = getFont();
            String[] words = line.split("\\s+");
            if (words.length == 0) return;

            float spaceW = 0.25f * fontSize;
            float[] wordWidths = new float[words.length];
            float totalW = 0f;
            for (int i = 0; i < words.length; i++) {
                wordWidths[i] = font.getWidth(words[i], fontSize);
                totalW += wordWidths[i] + (i > 0 ? spaceW : 0f);
            }

            float progressX = font.computeNaturalProgressX(line, fontSize, progress);

            // Находим активное слово, которое поётся прямо сейчас
            int activeIdx = -1;
            float curWordX = 0f;
            float activeFactor = 0f;

            for (int i = 0; i < words.length; i++) {
                float wStart = curWordX;
                float wEnd = curWordX + wordWidths[i];
                if (progressX >= wStart && progressX <= wEnd) {
                    activeIdx = i;
                    float inWord = (wEnd > wStart) ? ((progressX - wStart) / (wEnd - wStart)) : 0.5f;
                    activeFactor = (float) Math.sin(inWord * Math.PI);
                    break;
                } else if (progressX > wEnd && (i == words.length - 1 || progressX < wEnd + spaceW)) {
                    activeIdx = i;
                    activeFactor = 0.3f;
                }
                curWordX += wordWidths[i] + spaceW;
            }

            // Плавное следование камеры (непрерывное скольжение без рывков по словам)
            float targetScrollOffset = 0f;
            if (totalW > maxViewW) {
                targetScrollOffset = -(progressX - (maxViewW * 0.35f));
                targetScrollOffset = Math.min(0f, Math.max(-(totalW - maxViewW), targetScrollOffset));
            }

            if (Math.abs(smoothKaraokeScrollOffset - targetScrollOffset) > 60f) {
                smoothKaraokeScrollOffset = targetScrollOffset;
            } else {
                smoothKaraokeScrollOffset = MathHelper.lerp(dt * 8.0f, smoothKaraokeScrollOffset, targetScrollOffset);
            }

            curWordX = 0f;
            MatrixStack matrices = context.getMatrices();

            for (int i = 0; i < words.length; i++) {
                float wStart = curWordX;
                float wEnd = curWordX + wordWidths[i];
                float drawX = baseX + smoothKaraokeScrollOffset + curWordX;
                float drawY = baseY;

                boolean isActive = (i == activeIdx);
                float scale = isActive ? (1.0f + 0.08f * activeFactor) : 1.0f;

                float wordProgress;
                if (progressX >= wEnd) {
                    wordProgress = 1.0f;
                } else if (progressX <= wStart) {
                    wordProgress = 0.0f;
                } else {
                    wordProgress = (wEnd > wStart) ? ((progressX - wStart) / (wEnd - wStart)) : 0.5f;
                }

                if (scale > 1.001f) {
                    matrices.push();
                    float pivotX = drawX + wordWidths[i] / 2f;
                    float pivotY = drawY + 3.5f;
                    matrices.translate(pivotX, pivotY, 0);
                    matrices.scale(scale, scale, 1.0f);
                    matrices.translate(-pivotX, -pivotY, 0);

                    try {
                        font.drawKaraoke(context, words[i], drawX, drawY - (0.4f * activeFactor), fontSize, colorDone, colorPending, wordProgress);
                    } finally {
                        matrices.pop();
                    }
                } else {
                    font.drawKaraoke(context, words[i], drawX, drawY, fontSize, colorDone, colorPending, wordProgress);
                }

                curWordX += wordWidths[i] + spaceW;
            }
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  🪜 АНИМАЦИЯ «ЛЕСЕНКА»: ПОСЛЕДОВАТЕЛЬНЫЙ УХОД СЛОВ ВВЕРХ
    // ═══════════════════════════════════════════════════════════════════════

    private static void drawLadderExit(DrawContext context, String line, float baseX, float baseY, float fontSize,
                                       int baseAlpha, float transitionProgress, boolean wasKaraoke) {
        if (line == null || line.trim().isEmpty()) return;
        try {
            MsdfFont font = getFont();
            String[] words = line.split("\\s+");
            if (words.length == 0) return;

            float spaceW = 0.25f * fontSize;
            float curWordX = 0f;
            int n = words.length;

            for (int i = 0; i < n; i++) {
                float wordW = font.getWidth(words[i], fontSize);
                float drawX = baseX + curWordX;

                // Каждое следующее слово начинает взлетать чуть позже предыдущего (лесенка)
                float staggerStart = (float) i / (float) n * 0.40f;
                float p = Math.max(0f, Math.min(1f, (transitionProgress - staggerStart) / (1f - staggerStart)));

                float easeY = p * p * (3f - 2f * p);
                float drawY = baseY - (10f * easeY);

                int wordAlpha = (int)(baseAlpha * (1f - p));
                if (wordAlpha > 5) {
                    int color = (wordAlpha << 24) | 0xFFFFFF;
                    font.draw(context, words[i], drawX, drawY, fontSize, color);
                }

                curWordX += wordW + spaceW;
            }
        } catch (Throwable ignored) {}
    }
}