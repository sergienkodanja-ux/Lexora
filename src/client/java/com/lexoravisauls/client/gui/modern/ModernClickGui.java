package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.events.DynamicIslandRenderer;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.modules.ItemHighlighter;
import com.lexoravisauls.client.utils.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;

import java.awt.Color;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lexoravisauls.client.party.LexoraPartyManager;
import com.lexoravisauls.client.party.LexoraPartyClient;

/**
 * ModernClickGui — новая раскладка "3 башни" (Utils / Visual / Themes),
 * всегда видимые одновременно, в стиле референса (скруглённые колонки).
 *
 * Party / Events / GUI Settings / Configs больше не вкладки внутри этого
 * окна — они переехали в мини-панели Dynamic Island'а (см. bridge-блок
 * ниже и DynamicIslandRenderer#renderGuiQuickAccess). Экран остаётся
 * открытым тем же ModernClickGui — просто поверх башен рисуется всплывающая
 * панель, когда activeIslandPanel != null.
 *
 * ПКМ по модулю больше не раскрывает карточку на месте (аккордеон) —
 * список модулей всей башни уезжает влево, а справа во всю колонку
 * въезжают настройки (см. drawTower / drawSettingsSlide).
 */
public class ModernClickGui extends Screen {

    public static final MsdfFont SFUI = new MsdfFont(
            Identifier.of("lexoravisauls", "msdf_data/font.png"),
            Identifier.of("lexoravisauls", "msdf_data/font.json")
    );

    // ── Геометрия ───────────────────────────────────────────────────────────
    private static final int TOWER_W   = 140;
    private static final int TOWER_GAP = 10;
    private static final int GUI_W     = TOWER_W * 4 + TOWER_GAP * 3;
    private static final int GUI_H     = 318;
    private static final int HEADER_H  = 28;

    private static final int BG_COLOR       = 0x95050505;
    private static final int PANEL_COLOR    = 0x60141414;
    private static final int ELEM_COLOR     = 0x80222222;
    private static final int TEXT_COLOR     = 0xFFEDEDED;
    private static final int SUBTEXT_COLOR  = 0xFF7A7A7A;
    private static final int DARK_THEME_BG  = 0xEE050505;

    // ── Именованные "слоты" скролла (раньше индексировались по activeTab) ──
    private static final int SCROLL_UTILS   = 0;
    private static final int SCROLL_VISUAL  = 1;
    private static final int SCROLL_HUD     = 2;
    private static final int SCROLL_THEMES  = 3;
    private static final int SCROLL_PARTY   = 4;
    private static final int SCROLL_EVENTS  = 5;
    private static final int SCROLL_GUI     = 6;
    private static final int SCROLL_CONFIGS = 7;
    private final float[] scrolls       = new float[8];
    private final float[] targetScrolls = new float[8];

    private float openAnim = 0.0f;
    private boolean closing = false;

    private int currentMouseX = 0;
    private int currentMouseY = 0;
    // Публично для DynamicIslandRenderer: чтобы наведение на "Пати/Ивенты/..." в островке
    // проверялось по ТЕМ ЖЕ координатам, что реально ловит mouseClicked этого экрана, а не по
    // отдельно вычисленным в другом классе — иначе наведение и клик расходятся по пикселям.
    public static int lastMouseX = 0;
    public static int lastMouseY = 0;

    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final Map<String, Float> particlesSectionAnimations = new HashMap<>();

    // ── ПКМ-переход "список → настройки" (по каждой башне независимо) ──────
    private final Map<String, String> towerOpenModule   = new HashMap<>(); // "Utils"/"Visual" -> модуль
    private final Map<String, Float>  towerSettingsAnim = new HashMap<>(); // 0 = список, 1 = настройки
    private final Map<String, Float>  towerSettingsScroll = new HashMap<>();

    private String selectedConfig = null;
    private String configInput = "";
    private boolean configInputFocused = false;

    private static final String CALLOUT_BIND_KEY = "CalloutBind";
    private static float animCalloutBtn = 0f;

    private String partyCodeInput = "";
    private boolean partyCodeInputFocused = false;
    private int[] partyCodeInputBounds = null;

    private String gpsInputName = "";
    private String gpsInputX = "";
    private String gpsInputZ = "";
    private int focusedGpsInput = 0;

    private final Map<String, int[]> settingClickBounds = new HashMap<>();
    private final Map<String, float[]> sliderBounds = new HashMap<>();

    private final Map<String, float[]> padBounds = new HashMap<>();
    private final Map<String, String> padKeyYMap = new HashMap<>();

    private String draggingSettingSlider = null;
    private String draggingPadSetting = null;

    private String bindingTarget = null;
    private boolean isModuleBind = false;
    private float bindAnim = 0.0f;
    private float bindPopupX = 0f, bindPopupY = 0f;

    private String openModeDropdown = null;

    // ── Новая раскладная палитра (жмёшь свотч — плавно раскрывается на
    //    месте в аккуратную скруглённую палитру, отпустил мышь — сворачивается
    //    обратно и рисует выбранный цвет). Полностью заменяет старый
    //    popup-пикер (pickerOpen/pickerX/pickerY и т.д.) ──────────────────────
    private static class PickerState {
        float h = 0.0f, s = 1.0f, v = 1.0f;
    }

    // id свотча: "Theme Color 1" / любой ModernSetting.COLOR key как есть,
    // "ih:<название предмета>" для Item Highlighter, "gps:<имя метки>" для GPS.
    private String paletteAnimKey = null;                // какой свотч сейчас реально зажат (null = ни один)
    private int paletteDragMode = 0;                      // 0 = нет, 1 = SV-квадрат, 2 = полоса Hue
    private final PickerState paletteLive = new PickerState();
    private final Map<String, Float> paletteAnim = new HashMap<>();      // id -> 0..1 разворот
    private final Map<String, float[]> paletteAnchor = new HashMap<>();  // id -> [cx, cy] откуда растёт

    // ── Мост с Dynamic Island (Party/Events/GUI/Configs как мини-окна) ─────
    public static String activeIslandPanel = null; // "Party" | "Events" | "GUI" | "Configs" | null

    private static String lastRawFt = "";
    private static String lastRawHw = "";
    private static long lastUpdateTimeFt = 0;
    private static long lastUpdateTimeHw = 0;
    private static final Pattern TIME_PATTERN = Pattern.compile("(через|до конца) (?:(\\d+)\\s*мин)?\\s*(?:и\\s*)?(?:(\\d+)\\s*сек)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    public ModernClickGui() {
        super(Text.literal("Modern Lexora GUI"));
    }

    private int getThemeColor() {
        float[] hsv = ClientData.colorSettings.getOrDefault("Theme Color 1", new float[]{0.58f, 0.8f, 1f});
        return 0xFF000000 | (Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF);
    }

    public static void playSound(String name) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            try {
                Identifier id = Identifier.of("lexoravisauls", name);
                client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvent.of(id), 1.0F, 1.0F));
            } catch (Exception ignored) {
            }
        }
    }

    public static void playModuleToggleSound(boolean enabled) {
        String mode = ClientData.modeSettings.getOrDefault("ModuleSoundMode", "Default");
        String prefix = enabled ? "enable" : "disable";
        String suffix = "";
        switch (mode) {
            case "Sound 1": suffix = "1"; break;
            case "Sound 2": suffix = "2"; break;
            case "Sound 3": suffix = "3"; break;
            case "Sound 4": suffix = "4"; break;
        }
        playSound(prefix + suffix);
    }

    @Override
    protected void init() {
        super.init();
        ConfigManager.updateConfigList();
        ensureThemeDefaults();
        this.closing = false;
        this.openAnim = 0.0f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (!this.closing) {
            this.closing = true;
            bindingTarget = null;
            paletteAnimKey = null;
            paletteDragMode = 0;
            focusedGpsInput = 0;
            draggingSettingSlider = null;
            draggingPadSetting = null;
            // Закрытие ClickGui должно гасить открытую в острове вкладку (Party/Events/GUI/
            // Configs) — так и задумано, возвращено по уточнению.
            activeIslandPanel = null;
        }
    }

    private float sanitize(float v) {
        return (Float.isNaN(v) || Float.isInfinite(v)) ? 0.0f : Math.max(0.0f, Math.min(1.0f, v));
    }

    private float updateHover(String key, boolean isHovered) {
        float t = hoverAnimations.getOrDefault(key, 0.0f);
        t += ((isHovered ? 1.0f : 0.0f) - t) * 0.3f;
        t = sanitize(t);
        hoverAnimations.put(key, t);
        return t;
    }

    // ── "Потрясти" текст модуля, если у него нет настроек (ПКМ по пустому) ──
    private final Map<String, Long> shakeStartTime = new HashMap<>();

    private float getShakeOffset(String key) {
        Long start = shakeStartTime.get(key);
        if (start == null) return 0f;
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > 500L) {
            shakeStartTime.remove(key);
            return 0f;
        }
        float t = elapsed / 500f;
        float decay = 1f - t;
        float wave = (float) Math.sin(t * Math.PI * 7.0);
        return wave * decay * 3.0f;
    }

    private boolean moduleHasSettings(String module) {
        if (module.equals("Lock Slot") || module.equals("Aspect Ratio") || module.equals("Fast Swap")
                || module.equals("Item Highlighter") || module.equals("Particles") || module.equals("Item Swap")
                || module.equals("GPS")) {
            return true;
        }
        return ModernSettingsRegistry.hasSettings(module);
    }

    // =========================================================================
    //  ФОН
    // =========================================================================
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.draw();
        ScreenCaptureManager.captureScreen();
        boolean useBlur = ClientData.moduleStates.getOrDefault("UseBlurTheme", true);
        // Блюр включён -> НЕ затемняем экран вообще, стекло само отделяет гуи от игры.
        // Блюр выключен -> обычное затемнение, чтобы плашки на чёрном фоне читались.
        if (!useBlur) {
            context.fill(0, 0, width, height, withAlpha(0xFF000000, sanitize(openAnim) * 0.35f));
        }
    }

    private void drawTowerBackground(DrawContext context, float x, float y, float w, float h, float alpha, boolean useBlur) {
        if (useBlur) {
            // ВАЖНО: у ModernGuiRender.drawLiquidGlass сила блюра берётся из альфа-канала tint-цвета
            // (globalAlpha = tintAlpha/0.6, и именно им умножается радиус блюра в шейдере). Если тут
            // поставить низкую альфу — блюр почти пропадает, а не просто "меньше темнит". Экран мы уже
            // не затемняем отдельным чёрным фулскрином (см. renderBackground) — поэтому здесь держим
            // альфу высокой, чтобы стекло реально размывало картинку, а не просто было прозрачным.
            ModernGuiRender.drawLiquidGlass(context, x, y, w, h, 14.0f, 15.0f, withAlpha(0x9A050505, alpha));
            return;
        }
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, (int) h, 14.0f, withAlpha(0xF2050505, alpha));
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, (int) h, 14.0f, withAlpha(0x1A1A20, alpha * 0.8f));
    }

    // =========================================================================
    //  ГЛАВНЫЙ RENDER
    // =========================================================================
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // Медленнее и плавнее, чем раньше — окно/башни ощутимо "долетают" до места, а не прыгают.
        if (!closing) {
            openAnim += (1.0f - openAnim) * 0.13f;
        } else {
            openAnim += (0.0f - openAnim) * 0.09f;
            if (openAnim <= 0.015f) {
                openAnim = 0.0f;
                if (client != null) client.setScreen(null);
                return;
            }
        }
        openAnim = sanitize(openAnim);
        float easedOpen = smoothT(openAnim);

        for (int i = 0; i < scrolls.length; i++) scrolls[i] += (targetScrolls[i] - scrolls[i]) * 0.20f;

        // Плавная анимация настроек-слайда по каждой открытой башне (медленнее прежнего) —
        // "Themes" сюда же: через неё теперь открываются Party/Events/GUI/Configs из островка.
        for (String tower : new String[]{"Utils", "Visual", "HUD"}) {
            float target = towerOpenModule.containsKey(tower) ? 1.0f : 0.0f;
            float cur = towerSettingsAnim.getOrDefault(tower, 0.0f);
            cur = sanitize(cur + (target - cur) * 0.15f);
            towerSettingsAnim.put(tower, cur);
        }
        {
            float target = activeIslandPanel != null ? 1.0f : 0.0f;
            float cur = towerSettingsAnim.getOrDefault("Themes", 0.0f);
            cur = sanitize(cur + (target - cur) * 0.15f);
            towerSettingsAnim.put("Themes", cur);
        }

        // Раскладная палитра: активный ключ тянем к 1, все остальные — плавно к 0 и убираем
        if (paletteAnimKey != null) {
            paletteAnim.merge(paletteAnimKey, 0f, (a, b) -> a);
            paletteAnim.put(paletteAnimKey, sanitize(paletteAnim.getOrDefault(paletteAnimKey, 0f) + (1f - paletteAnim.getOrDefault(paletteAnimKey, 0f)) * 0.4f));
        }
        Iterator<Map.Entry<String, Float>> pIt = paletteAnim.entrySet().iterator();
        while (pIt.hasNext()) {
            Map.Entry<String, Float> e = pIt.next();
            if (e.getKey().equals(paletteAnimKey)) continue;
            float v = e.getValue() * 0.72f;
            if (v < 0.01f) pIt.remove(); else e.setValue(v);
        }

        settingClickBounds.clear();
        sliderBounds.clear();
        padBounds.clear();

        renderBackground(context, mouseX, mouseY, delta);

        boolean useBlur = ClientData.moduleStates.getOrDefault("UseBlurTheme", true);
        float guiW = GUI_W;
        float guiH = GUI_H;
        float x = (width - guiW) / 2f;
        float y = (height - guiH) / 2f;

        float scale = (0.90f + easedOpen * 0.10f);
        float alpha = openAnim;
        float flyBase = (1f - easedOpen) * 24f;

        context.getMatrices().push();
        try {
            context.getMatrices().translate(width / 2f, height / 2f, 0f);
            context.getMatrices().scale(scale, scale, 1f);
            context.getMatrices().translate(-width / 2f, -height / 2f, 0f);

            // Башни "залетают" снизу вверх с лёгким разбегом по времени — не идеально синхронно,
            // от этого ощущается как несколько отдельных элементов, а не одна плашка.
            float tx = x;
            drawTower(context, "Utils", "Утилиты", 0, ModernGuiRegistry.getUtilsModules(), tx, y + flyBase * 1.00f, TOWER_W, guiH, alpha, useBlur);
            tx += TOWER_W + TOWER_GAP;
            drawTower(context, "Visual", "Визуалы", 1, ModernGuiRegistry.getVisualModules(), tx, y + flyBase * 1.10f, TOWER_W, guiH, alpha, useBlur);
            tx += TOWER_W + TOWER_GAP;
            drawTower(context, "HUD", "ХУД", 3, ModernGuiRegistry.getHudModules(), tx, y + flyBase * 1.20f, TOWER_W, guiH, alpha, useBlur);
            tx += TOWER_W + TOWER_GAP;
            drawThemesTower(context, tx, y + flyBase * 1.30f, TOWER_W, guiH, alpha, useBlur);

            for (Map.Entry<String, Float> e : paletteAnim.entrySet()) {
                if (e.getValue() > 0.01f) drawExpandedPalette(context, e.getKey(), e.getValue(), alpha);
            }
        } finally {
            context.getMatrices().pop();
        }

        if (bindingTarget != null) {
            bindAnim += (1.0f - bindAnim) * 0.35f;
        } else {
            bindAnim += (0.0f - bindAnim) * 0.40f;
        }
        bindAnim = sanitize(bindAnim);
        if (bindAnim > 0.01f) drawBindWindow(context, bindAnim);
    }

    // =========================================================================
    //  ИКОНКИ ЗАГОЛОВКОВ БАШЕН (простые векторные, без зависимости от шрифта)
    // =========================================================================
    private void drawTower(DrawContext context, String towerId, String title, int iconKind,
                           List<String> modules, float x, float y, float w, float h, float alpha, boolean useBlur) {
        drawTowerBackground(context, x, y, w, h, alpha, useBlur);

        drawSfuiCustom(context, title, x + 12f, y + HEADER_H / 2f - 4.2f, 9.5f, withAlpha(TEXT_COLOR, alpha));

        RoundedRectShader.draw(context, (int) (x + 10), (int) (y + HEADER_H), (int) (w - 20), 1, 0f, withAlpha(0xFFFFFFFF, alpha * 0.08f));

        float contentX = x + 8f;
        float contentY = y + HEADER_H + 8f;
        float contentW = w - 16f;
        float contentH = h - HEADER_H - 16f;

        float settingsAnim = towerSettingsAnim.getOrDefault(towerId, 0f);

        context.enableScissor((int) x, (int) (y + HEADER_H), (int) (x + w), (int) (y + h));
        try {
            if (settingsAnim < 0.995f) {
                float listAlpha = alpha * (1f - settingsAnim);
                float listOffsetX = -smoothT(settingsAnim) * w * 0.9f;
                if (listAlpha > 0.01f) {
                    if (modules.isEmpty()) {
                        drawSfuiTiny(context, "Нет модулей", contentX, contentY, withAlpha(SUBTEXT_COLOR, listAlpha));
                    } else {
                        drawTowerModuleList(context, towerId, modules, contentX + listOffsetX, contentY, contentW, contentH, listAlpha);
                    }
                }
            }
            if (settingsAnim > 0.005f) {
                float setAlpha = alpha * settingsAnim;
                float setOffsetX = (1f - smoothT(settingsAnim)) * w * 0.9f;
                String openModule = towerOpenModule.get(towerId);
                if (openModule != null) {
                    drawTowerSettingsSlide(context, towerId, openModule, contentX + setOffsetX, contentY, contentW, contentH, setAlpha);
                }
            }
        } finally {
            context.disableScissor();
        }
    }

    private float smoothT(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private void drawTowerModuleList(DrawContext context, String towerId, List<String> modules,
                                     float x, float y, float w, float h, float alpha) {
        int scrollIdx = switch (towerId) {
            case "Utils" -> SCROLL_UTILS;
            case "Visual" -> SCROLL_VISUAL;
            default -> SCROLL_HUD;
        };
        float rowH = 22f;
        float totalH = modules.size() * rowH;
        float maxScroll = Math.max(0f, totalH - h);
        targetScrolls[scrollIdx] = clamp(targetScrolls[scrollIdx], 0f, maxScroll);
        scrolls[scrollIdx] = clamp(scrolls[scrollIdx], 0f, maxScroll);

        float cy = y - scrolls[scrollIdx];

        // Список рисуем единым куском — без карточек и зазоров между модулями,
        // разделяет их только подсветка при наведении (ничего не "рвёт" список на части).
        for (String module : modules) {
            if (cy + rowH >= y - 4 && cy <= y + h + 4) {
                boolean enabled = ClientData.moduleStates.getOrDefault(module, false);
                boolean isHovered = !closing && inside(currentMouseX, currentMouseY, x, cy, w, rowH);
                float hoverT = updateHover("row:" + towerId + ":" + module, isHovered);

                if (hoverT > 0.01f) {
                    RoundedRectShader.draw(context, (int) x, (int) cy, (int) w, (int) rowH, 5f, withAlpha(0x10FFFFFF, alpha * hoverT));
                }

                int textColor = enabled ? TEXT_COLOR : blendColors(SUBTEXT_COLOR, TEXT_COLOR, hoverT * 0.6f);
                String shown = clipToWidth(module, w - 28f, 7.5f);
                float shakeOff = getShakeOffset(towerId + ":" + module);
                drawSfuiCustom(context, shown, x + 9f + shakeOff, cy + (rowH - 6.5f) / 2f, 7.5f, withAlpha(textColor, alpha));

                float cmSize = 9f;
                drawCheckmarkAnimated(context, "modchk:" + towerId + ":" + module, enabled, x + w - cmSize - 8f, cy + (rowH - cmSize) / 2f, cmSize, alpha);

                settingClickBounds.put("modulerow:" + towerId + ":" + module, new int[]{(int) x, (int) cy, (int) w, (int) rowH});
            }
            cy += rowH;
        }
    }

    private void drawTowerSettingsSlide(DrawContext context, String towerId, String module,
                                        float x, float y, float w, float h, float alpha) {
        float backW = 20f;
        boolean backHovered = !closing && inside(currentMouseX, currentMouseY, x, y, backW, 18f);
        float backHoverT = updateHover("back:" + towerId, backHovered);
        drawBackArrow(context, x + 1f, y + 3f, 12f, alpha * (0.7f + backHoverT * 0.3f));
        settingClickBounds.put("tower_back:" + towerId, new int[]{(int) x, (int) y, (int) backW, 18});

        float nameMaxW = w - backW - 8f;
        String shownName = clipToWidth(module, nameMaxW, 9f);
        float nameW = getSfuiWidth(shownName, 9f);
        drawSfuiCustom(context, shownName, x + w - nameW, y + 4.5f, 9f, withAlpha(TEXT_COLOR, alpha));

        float sy = y + 26f;
        float sh = h - 26f;
        if (sh <= 4f) return;

        float maxScroll = Math.max(0f, getExpandedTargetHeight(module) - sh);
        float scroll = clamp(towerSettingsScroll.getOrDefault(towerId, 0f), 0f, maxScroll);
        towerSettingsScroll.put(towerId, scroll);

        // Настройки при открытии панели плавно "опускаются" на место сверху — чисто визуальный
        // сдвиг матрицей, высоты/layout не трогает вообще, поэтому не может ничего сдвинуть/наложить
        // друг на друга (в отличие от прошлого бага в Партиклах).
        float revealT = smoothT(towerSettingsAnim.getOrDefault(towerId, 1f));
        float settleOffset = (revealT - 1f) * 14f;

        context.enableScissor((int) x, (int) sy, (int) (x + w), (int) (sy + sh));
        try {
            // ФИКС "съехавшего хитбокса": раньше сдвиг применялся только матрицей ниже, а
            // settingClickBounds (см. drawKronexSetting/drawExpandedModuleContent) сохраняет
            // тот Y, что ему передали, — матрицу он не читает. Пока towerSettingsScroll был
            // всегда 0 (до фикса скролла настроек), несовпадение было незаметно; как только
            // скролл заработал по-настоящему, клик-зоны и картинка разъехались на его величину.
            // Теперь сдвиг закладываем прямо в Y — картинка и клики снова смотрят в одну точку.
            drawExpandedModuleContent(context, module, x + 2f, sy + 6f - scroll + settleOffset, (int) w - 4, alpha);
        } finally {
            context.disableScissor();
        }
    }

    // =========================================================================
    //  ГАЛОЧКА И СТРЕЛКА НАЗАД — твои PNG (уже белые), без всякого тинта
    // =========================================================================
    private static final Identifier ACCEPT_TEX  = Identifier.of("lexoravisauls", "textures/gui/accept.png");
    private static final Identifier GUISWAP_TEX = Identifier.of("lexoravisauls", "textures/gui/guiswap.png");

    private void drawCheckmark(DrawContext context, float x, float y, float size, float alpha) {
        RoundedRectShader.drawTextured(context, ACCEPT_TEX, x, y, size, size, 0f, 0f, 0f, 1f, 1f, withAlpha(0xFFFFFFFF, alpha));
    }

    private final Map<String, Float> checkmarkAnim = new HashMap<>();

    /** Галочка, которая плавно появляется/исчезает (fade + лёгкий "поп" по размеру), а не мигает. */
    private void drawCheckmarkAnimated(DrawContext context, String key, boolean visible, float x, float y, float size, float alpha) {
        float target = visible ? 1f : 0f;
        float cur = checkmarkAnim.getOrDefault(key, target);
        cur = sanitize(cur + (target - cur) * 0.25f);
        checkmarkAnim.put(key, cur);
        if (cur < 0.02f) return;
        float scale = 0.7f + 0.3f * smoothT(cur);
        float sizeAnim = size * scale;
        float offset = (size - sizeAnim) / 2f;
        drawCheckmark(context, x + offset, y + offset, sizeAnim, alpha * cur);
    }

    private void drawBackArrow(DrawContext context, float x, float y, float size, float alpha) {
        RoundedRectShader.drawTextured(context, GUISWAP_TEX, x, y, size, size, 0f, 0f, 0f, 1f, 1f, withAlpha(0xFFFFFFFF, alpha));
    }

    /** Для toggle-настроек внутри панелей: маленький скруглённый квадрат, галочка появляется внутри при включении. */
    private void drawCheckbox(DrawContext context, float x, float y, boolean enabled, String key, float alpha) {
        float size = 9f;
        RoundedRectShader.draw(context, (int) x, (int) y, (int) size, (int) size, 3.0f,
                withAlpha(enabled ? blendColors(ELEM_COLOR, getThemeColor(), 0.35f) : ELEM_COLOR, alpha));
        float cmSize = 7f;
        drawCheckmarkAnimated(context, "cbchk:" + key, enabled, x + (size - cmSize) / 2f, y + (size - cmSize) / 2f, cmSize, alpha);
        settingClickBounds.put("toggle:" + key, new int[]{(int) x - 5, (int) y - 5, (int) size + 10, (int) size + 10});
    }

    // =========================================================================
    //  БАШНЯ ТЕМ: пресеты + раскладная палитра для 2 своих цветов
    // =========================================================================
    private void drawThemesTower(DrawContext context, float x, float y, float w, float h, float alpha, boolean useBlur) {
        drawTowerBackground(context, x, y, w, h, alpha, useBlur);

        drawSfuiCustom(context, "Темы", x + 12f, y + HEADER_H / 2f - 4.2f, 9.5f, withAlpha(TEXT_COLOR, alpha));
        RoundedRectShader.draw(context, (int) (x + 10), (int) (y + HEADER_H), (int) (w - 20), 1, 0f, withAlpha(0xFFFFFFFF, alpha * 0.08f));

        float contentX = x + 10f;
        float contentY = y + HEADER_H + 8f;
        float contentW = w - 20f;
        float contentH = h - HEADER_H - 16f;

        float panelAnim = towerSettingsAnim.getOrDefault("Themes", 0f);

        context.enableScissor((int) x, (int) (y + HEADER_H), (int) (x + w), (int) (y + h));
        try {
            if (panelAnim < 0.995f) {
                float listAlpha = alpha * (1f - panelAnim);
                float listOffsetX = -smoothT(panelAnim) * w * 0.9f;
                if (listAlpha > 0.01f) {
                    drawThemesNormalContent(context, contentX + listOffsetX, contentY, contentW, contentH, listAlpha);
                }
            }
            if (panelAnim > 0.005f && activeIslandPanel != null) {
                float setAlpha = alpha * panelAnim;
                float setOffsetX = (1f - smoothT(panelAnim)) * w * 0.9f;
                drawThemesRedirectedPanel(context, activeIslandPanel, contentX + setOffsetX, contentY, contentW, contentH, setAlpha);
            }
        } finally {
            context.disableScissor();
        }
    }

    private void drawThemesNormalContent(DrawContext context, float cx, float cy0, float cw, float ch, float alpha) {
        float cy = cy0;
        drawSfuiTiny(context, "ПРЕСЕТЫ", cx, cy, withAlpha(SUBTEXT_COLOR, alpha));
        cy += 13f;

        for (String preset : ModernGuiRegistry.getDefaultThemes()) {
            boolean isHovered = !closing && inside(currentMouseX, currentMouseY, cx, cy, cw, 24f);
            float hoverT = updateHover("theme:" + preset, isHovered);
            boolean isActive = isPresetActive(preset);
            int rowBg = isActive ? blendColors(PANEL_COLOR, getThemeColor(), 0.16f) : blendColors(PANEL_COLOR, 0xFFFFFFFF, hoverT * 0.05f);
            RoundedRectShader.draw(context, (int) cx, (int) cy, (int) cw, 24, 7f, withAlpha(rowBg, alpha));
            // Круглый "мазок краски" — две половинки цвета, смешивающиеся по центру, как на палитре.
            drawPresetPaintBlob(context, preset, cx + 5f, cy + 3f, 18f, alpha);
            drawMarqueeText(context, "presetname:" + preset, preset, cx + 30f, cy + 8.5f, cw - 30f - 20f, 7.5f, withAlpha(TEXT_COLOR, alpha), isHovered);
            drawCheckmarkAnimated(context, "themechk:" + preset, isActive, cx + cw - 18f, cy + 7f, 10f, alpha);
            settingClickBounds.put("theme:" + preset, new int[]{(int) cx, (int) cy, (int) cw, 24});
            cy += 27f;
        }

        cy += 1f;
        RoundedRectShader.draw(context, (int) cx, (int) cy, (int) cw, 1, 0f, withAlpha(0xFFFFFFFF, alpha * 0.08f));
        cy += 13f;

        drawSfuiTiny(context, "СВОИ ЦВЕТА", cx, cy, withAlpha(SUBTEXT_COLOR, alpha));
        cy += 15f;

        float rectH = 38f;
        float rectW = (cw - 8f) / 2f;
        drawThemePaletteRect(context, "Theme Color 1", "Цвет 1", cx, cy, rectW, rectH, alpha);
        drawThemePaletteRect(context, "Theme Color 2", "Цвет 2", cx + rectW + 8f, cy, rectW, rectH, alpha);
        cy += rectH + 14f;

        drawSfuiTiny(context, "Превью", cx, cy, withAlpha(SUBTEXT_COLOR, alpha));
        cy += 10f;
        drawThemeGradientPreview(context, cx, cy, cw, 10f, alpha);
    }

    /** Пункт 6: клик на Party/Events/GUI/Configs в островке разворачивает их прямо в башне Тем —
     *  тем же приёмом, что и ПКМ по модулю: назад — слева сверху, имя панели — справа сверху. */
    private void drawThemesRedirectedPanel(DrawContext context, String panel, float x, float y, float w, float h, float alpha) {
        float backW = 18f;
        boolean backHovered = !closing && inside(currentMouseX, currentMouseY, x, y, backW, 16f);
        float backHoverT = updateHover("back:Themes", backHovered);
        drawBackArrow(context, x + 1f, y + 2f, 11f, alpha * (0.7f + backHoverT * 0.3f));
        settingClickBounds.put("tower_back:Themes", new int[]{(int) x, (int) y, (int) backW, 16});

        String displayName = switch (panel) {
            case "Party" -> "Пати";
            case "Events" -> "Ивенты";
            case "GUI" -> "Настройки";
            case "Configs" -> "Конфиги";
            default -> panel;
        };
        float nameW = getSfuiWidth(displayName, 9f);
        drawSfuiCustom(context, displayName, x + w - nameW, y + 3.5f, 9f, withAlpha(TEXT_COLOR, alpha));

        float sy = y + 22f;
        float sh = h - 22f;
        if (sh <= 4f) return;

        context.enableScissor((int) x, (int) sy, (int) (x + w), (int) (sy + sh));
        try {
            switch (panel) {
                case "Party" -> drawPartyTab(context, x + 1f, sy + 4f, w - 2f, sh - 4f, alpha);
                case "Events" -> drawEventsTab(context, x + 1f, sy + 4f, w - 2f, alpha);
                case "GUI" -> drawGuiTab(context, x + 1f, sy + 4f, w - 2f, alpha);
                case "Configs" -> drawConfigsTab(context, x + 1f, sy + 4f, w - 2f, alpha);
                default -> {
                }
            }
        } finally {
            context.disableScissor();
        }
    }

    /** Пункт 5: вместо кружков — 2 скруглённых прямоугольника-карточки с настройкой темы внутри. */
    private void drawThemePaletteRect(DrawContext context, String colorKey, String label, float x, float y, float w, float h, float alpha) {
        float[] hsv = getPaletteHsv(colorKey);
        int rgb = 0xFF000000 | (Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF);

        boolean pressed = colorKey.equals(paletteAnimKey);
        boolean isHovered = !closing && inside(currentMouseX, currentMouseY, x, y, w, h);
        float hoverT = updateHover("swatchhover:" + colorKey, isHovered);

        if (hoverT > 0.01f || pressed) {
            float ringA = pressed ? 0.5f : hoverT * 0.2f;
            RoundedRectShader.draw(context, (int) (x - 2), (int) (y - 2), (int) (w + 4), (int) (h + 4), 11f, withAlpha(getThemeColor(), alpha * ringA));
        }
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, (int) h, 9f, withAlpha(rgb, alpha));
        RoundedRectShader.draw(context, (int) x, (int) (y + h - 15f), (int) w, 15, 6f, withAlpha(0x60000000, alpha));
        drawSfuiTiny(context, label, x + 7f, y + h - 11f, withAlpha(0xFFFFFFFF, alpha));

        paletteAnchor.put(colorKey, new float[]{x + w / 2f, y + h / 2f});
        settingClickBounds.put("palette:" + colorKey, new int[]{(int) x, (int) y, (int) w, (int) h});
    }

    private void drawThemeGradientPreview(DrawContext context, float x, float y, float w, float h, float alpha) {
        float[] hsv1 = getPaletteHsv("Theme Color 1");
        float[] hsv2 = getPaletteHsv("Theme Color 2");
        int rgb1 = Color.HSBtoRGB(hsv1[0], hsv1[1], hsv1[2]) & 0xFFFFFF;
        int rgb2 = Color.HSBtoRGB(hsv2[0], hsv2[1], hsv2[2]) & 0xFFFFFF;
        int r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
        int step = 2;
        for (int ix = 0; ix < w; ix += step) {
            float t = ix / w;
            int r = (int) (r1 + (r2 - r1) * t), g = (int) (g1 + (g2 - g1) * t), b = (int) (b1 + (b2 - b1) * t);
            context.fill((int) (x + ix), (int) y, (int) (x + ix + step), (int) (y + h), withAlpha(0xFF000000 | (r << 16) | (g << 8) | b, alpha));
        }
    }

    // =========================================================================
    //  РАСКЛАДНАЯ ПАЛИТРА — общий виджет для тем и любых COLOR-настроек
    // =========================================================================
    private float[] getPaletteHsv(String id) {
        if (id.startsWith("ih:")) {
            String name = id.substring(3);
            for (Map.Entry<Item, ItemHighlighter.ItemConfig> e : ItemHighlighter.ITEM_CONFIGS.entrySet()) {
                String itemName = ItemHighlighter.ITEM_NAMES.getOrDefault(e.getKey(), e.getKey().getName().getString());
                if (itemName.equals(name)) {
                    int rgb = e.getValue().color;
                    return Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
                }
            }
            return new float[]{0f, 1f, 1f};
        }
        if (id.startsWith("gps:")) {
            String name = id.substring(4);
            try {
                for (com.lexoravisauls.client.utils.GPS.GpsWaypoint wp : com.lexoravisauls.client.utils.GPS.waypoints) {
                    if (wp.name.equals(name)) {
                        int rgb = wp.color;
                        return Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
                    }
                }
            } catch (Throwable ignored) {
            }
            return new float[]{0f, 1f, 1f};
        }
        return ClientData.colorSettings.getOrDefault(id, new float[]{0f, 1f, 1f});
    }

    private void commitPaletteHsv(String id, float h, float s, float v) {
        if (id.startsWith("ih:")) {
            String name = id.substring(3);
            for (Map.Entry<Item, ItemHighlighter.ItemConfig> e : ItemHighlighter.ITEM_CONFIGS.entrySet()) {
                String itemName = ItemHighlighter.ITEM_NAMES.getOrDefault(e.getKey(), e.getKey().getName().getString());
                if (itemName.equals(name)) {
                    e.getValue().color = Color.HSBtoRGB(h, s, v) & 0xFFFFFF;
                    return;
                }
            }
            return;
        }
        if (id.startsWith("gps:")) {
            String name = id.substring(4);
            try {
                for (com.lexoravisauls.client.utils.GPS.GpsWaypoint wp : com.lexoravisauls.client.utils.GPS.waypoints) {
                    if (wp.name.equals(name)) {
                        wp.color = Color.HSBtoRGB(h, s, v) & 0xFFFFFF;
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
            return;
        }
        ClientData.colorSettings.put(id, new float[]{h, s, v});
    }

    /** Рисует свотч + регистрирует его как точку роста палитры. Переиспользуется в панелях настроек. */
    private void drawColorSwatch(DrawContext context, String id, float x, float y, float w2, float h2, float alpha) {
        float[] hsv = getPaletteHsv(id);
        int rgb = 0xFF000000 | (Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF);
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w2, (int) h2, 4.0f, withAlpha(rgb, alpha));
        paletteAnchor.put(id, new float[]{x + w2 / 2f, y + h2 / 2f});
        settingClickBounds.put("palette:" + id, new int[]{(int) x - 3, (int) y - 3, (int) w2 + 6, (int) h2 + 6});
    }

    /**
     * Чистая геометрия раскладной палитры для ключа: [svX,svY,svW,svH,hueY,hueH,boxX,boxY,boxW,boxH].
     * Не зависит от анимации — одна и та же формула используется и рендером, и обработкой драга,
     * поэтому даже в первый кадр нажатия (пока eased ещё маленький) перетаскивание уже попадает
     * в верные координаты.
     */
    private float[] computePaletteGeom(String key) {
        float[] anchor = paletteAnchor.get(key);
        if (anchor == null) return null;
        int svW = 108, svH = 60, hueH = 6;
        int boxW = svW + 16, boxH = svH + hueH + 16 + 14;
        float boxX = clamp(anchor[0] - boxW / 2f, 6f, width - boxW - 6f);
        float boxY = clamp(anchor[1] - boxH / 2f, 6f, height - boxH - 6f);
        float svX = boxX + 8, svY = boxY + 8;
        float hueY = svY + svH + 8;
        return new float[]{svX, svY, svW, svH, hueY, hueH, boxX, boxY, boxW, boxH};
    }

    private void drawExpandedPalette(DrawContext context, String key, float t, float alphaOuter) {
        float[] g = computePaletteGeom(key);
        if (g == null) return;
        float svX = g[0], svY = g[1], svW = g[2], svH = g[3], hueY = g[4], hueH = g[5];
        float boxX = g[6], boxY = g[7], boxW = g[8], boxH = g[9];
        float boxCx = boxX + boxW / 2f, boxCy = boxY + boxH / 2f;

        float eased = smoothT(t);
        float curW = 16f + (boxW - 16f) * eased;
        float curH = 16f + (boxH - 16f) * eased;
        float curX = boxCx - curW / 2f;
        float curY = boxCy - curH / 2f;
        float curR = 8f + (12f - 8f) * eased;

        RoundedRectShader.draw(context, (int) curX, (int) curY, (int) curW, (int) curH, curR, withAlpha(DARK_THEME_BG, alphaOuter * eased));
        if (eased < 0.5f) return;

        float contentAlpha = alphaOuter * clamp((eased - 0.5f) / 0.5f, 0f, 1f);
        float[] hsv = key.equals(paletteAnimKey) ? new float[]{paletteLive.h, paletteLive.s, paletteLive.v} : getPaletteHsv(key);

        drawSvArea(context, (int) svX, (int) svY, (int) svW, (int) svH, hsv[0], contentAlpha);
        int dotX = clampInt((int) (svX + hsv[1] * (svW - 1)), (int) svX + 2, (int) (svX + svW - 2));
        int dotY = clampInt((int) (svY + (1f - hsv[2]) * (svH - 1)), (int) svY + 2, (int) (svY + svH - 2));
        RoundedRectShader.draw(context, dotX - 2, dotY - 2, 5, 5, 2.5f, withAlpha(0xFFFFFFFF, contentAlpha));

        drawHueBar(context, (int) svX, (int) hueY, (int) svW, (int) hueH, contentAlpha);
        int hx = clampInt((int) (svX + hsv[0] * (svW - 1)), (int) svX + 2, (int) (svX + svW - 2));
        RoundedRectShader.draw(context, hx - 1, (int) hueY - 1, 3, (int) hueH + 2, 1.5f, withAlpha(0xFFFFFFFF, contentAlpha));
    }

    private void updatePaletteFromMouse(double mx, double my) {
        if (paletteAnimKey == null) return;
        float[] g = computePaletteGeom(paletteAnimKey);
        if (g == null) return;
        float svX = g[0], svY = g[1], svW = g[2], svH = g[3], hueY = g[4], hueH = g[5];
        if (paletteDragMode == 2) {
            paletteLive.h = clamp((float) ((mx - svX) / svW), 0f, 1f);
        } else {
            paletteLive.s = clamp((float) ((mx - svX) / svW), 0f, 1f);
            paletteLive.v = clamp(1f - (float) ((my - svY) / svH), 0f, 1f);
        }
        commitPaletteHsv(paletteAnimKey, paletteLive.h, paletteLive.s, paletteLive.v);
    }

    /** true, если клик по (mx,my) попал именно в полосу Hue раскрытой палитры paletteAnimKey. */
    private boolean isClickOnActiveHueBar(double mx, double my) {
        if (paletteAnimKey == null) return false;
        float[] g = computePaletteGeom(paletteAnimKey);
        if (g == null) return false;
        return inside(mx, my, g[0] - 3, g[4] - 3, g[2] + 6, g[5] + 6);
    }

    private void drawSvArea(DrawContext context, int x, int y, int w, int h, float hue, float alpha) {
        int step = 2;
        for (int ix = 0; ix < w; ix += step) {
            for (int iy = 0; iy < h; iy += step) {
                float s = clamp(ix / (float) w, 0.0f, 1.0f), v = clamp(1.0f - iy / (float) h, 0.0f, 1.0f);
                context.fill(x + ix, y + iy, x + ix + step, y + iy + step, withAlpha(0xFF000000 | (Color.HSBtoRGB(hue, s, v) & 0xFFFFFF), alpha));
            }
        }
    }

    private void drawHueBar(DrawContext context, int x, int y, int w, int h, float alpha) {
        int step = 2;
        for (int ix = 0; ix < w; ix += step) {
            float hue = clamp(ix / (float) w, 0.0f, 1.0f);
            context.fill(x + ix, y, x + ix + step, y + h, withAlpha(0xFF000000 | (Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF), alpha));
        }
    }

    // =========================================================================
    //  ОБЩИЕ РЕНДЕРЕРЫ НАСТРОЕК (toggle / slider / mode / color / bind / pad2d)
    // =========================================================================
    private float drawKronexSetting(DrawContext context, ModernSetting setting, float x, float y, float w, float alpha) {
        if (!setting.isVisible()) return 0f;
        return switch (setting.type) {
            case TOGGLE -> drawKronexSettingToggle(context, setting, x, y, w, alpha);
            case SLIDER -> drawKronexSlider(context, setting, x, y, w, alpha);
            case MODE -> drawKronexMode(context, setting, x, y, w, alpha);
            case COLOR -> drawKronexColor(context, setting, x, y, w, alpha);
            case BIND -> drawKronexBind(context, setting, x, y, w, alpha);
            case HEADER -> drawKronexHeader(context, setting, x, y, w, alpha);
            case PAD2D -> drawKronexPad2D(context, setting, x, y, w, alpha);
        };
    }

    private float drawKronexHeader(DrawContext context, ModernSetting setting, float x, float y, float w, float alpha) {
        drawSfuiTiny(context, setting.label.toUpperCase(Locale.ROOT), x, y + 3f, withAlpha(SUBTEXT_COLOR, alpha));
        return 11f;
    }

    private float drawKronexSettingToggle(DrawContext context, ModernSetting setting, float x, float y, float w, float alpha) {
        boolean enabled = ClientData.moduleStates.getOrDefault(setting.key, false);
        boolean isHovered = !closing && inside(currentMouseX, currentMouseY, x, y, w, 13f);
        float hoverT = updateHover("st:" + setting.key, isHovered);
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, 13, 4f, withAlpha(blendColors(ELEM_COLOR, 0xFFFFFFFF, hoverT * 0.05f), alpha));
        drawMarqueeText(context, "lbl:" + setting.key, setting.label, x + 6f, y + 3.2f, w - 25f, 7f, withAlpha(TEXT_COLOR, alpha), isHovered);
        drawCheckbox(context, x + w - 13f, y + 2f, enabled, setting.key, alpha);
        return 13f + 3f;
    }

    private float drawKronexSlider(DrawContext context, ModernSetting setting, float x, float y, float w, float alpha) {
        float value = ClientData.numSettings.getOrDefault(setting.key, setting.min);
        float t = clamp((value - setting.min) / Math.max(0.0001f, (setting.max - setting.min)), 0f, 1f);
        boolean isHovered = !closing && inside(currentMouseX, currentMouseY, x, y, w, 13f);

        String valStr = (Math.abs(value - Math.round(value)) < 0.001f) ? String.valueOf(Math.round(value)) : String.format(Locale.US, "%.2f", value);
        float valW = getSfuiWidth(valStr, 7f);
        drawMarqueeText(context, "lbl:" + setting.key, setting.label, x + 1f, y, w - valW - 8f, 7f, withAlpha(TEXT_COLOR, alpha), isHovered);
        drawSfuiCustom(context, valStr, x + w - valW, y, 7f, withAlpha(SUBTEXT_COLOR, alpha));

        float barY = y + 9f, barH = 3f;
        RoundedRectShader.draw(context, (int) x, (int) barY, (int) w, (int) barH, barH / 2f, withAlpha(ELEM_COLOR, alpha));
        float fillW = Math.max(barH, w * t);
        RoundedRectShader.draw(context, (int) x, (int) barY, (int) fillW, (int) barH, barH / 2f, withAlpha(getThemeColor(), alpha));
        RoundedRectShader.draw(context, (int) (x + fillW - 3f), (int) (barY - 1.5f), 6, 6, 3f, withAlpha(0xFFFFFFFF, alpha));

        sliderBounds.put(setting.key, new float[]{x, barY - 5f, w, 13f, setting.min, setting.max});
        return 9f + 3f + 6f;
    }

    private final Map<String, Float> modeDropdownAnim = new HashMap<>();

    private float getAnimatedModeDropdown(String key) {
        float target = key.equals(openModeDropdown) ? 1f : 0f;
        float current = modeDropdownAnim.getOrDefault(key, target);
        current += (target - current) * 0.16f;
        current = sanitize(current);
        modeDropdownAnim.put(key, current);
        return current;
    }

    private float drawKronexMode(DrawContext context, ModernSetting setting, float x, float y, float w, float alpha) {
        String current = ClientData.modeSettings.getOrDefault(setting.key, setting.modes.isEmpty() ? "" : setting.modes.get(0));
        boolean isOpen = setting.key.equals(openModeDropdown);
        boolean isHovered = !closing && inside(currentMouseX, currentMouseY, x, y, w, 13f);
        float hoverT = updateHover("md:" + setting.key, isHovered);

        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, 13, 4f, withAlpha(blendColors(ELEM_COLOR, 0xFFFFFFFF, hoverT * 0.05f), alpha));
        String shownCur = clipToWidth(current, w * 0.36f, 7f);
        float curW = getSfuiWidth(shownCur, 7f);
        drawMarqueeText(context, "lbl:" + setting.key, setting.label, x + 6f, y + 3.2f, w - curW - 25f, 7f, withAlpha(TEXT_COLOR, alpha), isHovered);
        drawSfuiCustom(context, shownCur, x + w - curW - 11f, y + 3.2f, 7f, withAlpha(SUBTEXT_COLOR, alpha));
        drawSfuiControlCentered(context, isOpen ? "▲" : "▼", x + w - 6f, y + 3.2f, withAlpha(SUBTEXT_COLOR, alpha));
        settingClickBounds.put("mode_open:" + setting.key, new int[]{(int) x, (int) y, (int) w, 13});

        float dropAnim = getAnimatedModeDropdown(setting.key);
        float total = 13f + 3f;
        if (isOpen || dropAnim > 0.02f) {
            // Место под опции резервируем сразу целиком (без анимации самого layout — это опасно
            // трогать сейчас, см. фикс со scissor у партиклов), а вот сами строки красиво и медленно
            // проявляются и слегка сползают вниз на месте — не мешая соседям.
            float dropAlpha = alpha * clamp(dropAnim * 1.3f, 0f, 1f);
            float slide = (1f - smoothT(dropAnim)) * 5f;
            for (String mode : setting.modes) {
                float rowY = y + total - slide;
                boolean rowHover = isOpen && !closing && inside(currentMouseX, currentMouseY, x, rowY, w, 12f);
                float rowT = updateHover("mdopt:" + setting.key + ":" + mode, rowHover);
                boolean isCur = mode.equals(current);
                int rowBg = isCur ? blendColors(ELEM_COLOR, getThemeColor(), 0.30f) : blendColors(PANEL_COLOR, 0xFFFFFFFF, rowT * 0.06f);
                RoundedRectShader.draw(context, (int) x, (int) rowY, (int) w, 12, 3f, withAlpha(rowBg, dropAlpha));
                drawMarqueeText(context, "mdopt:" + setting.key + ":" + mode, mode, x + 6f, rowY + 2.8f, w - 10f, 7f, withAlpha(isCur ? TEXT_COLOR : SUBTEXT_COLOR, dropAlpha), rowHover);
                if (isOpen) {
                    settingClickBounds.put("mode_pick:" + setting.key + ":" + mode, new int[]{(int) x, (int) rowY, (int) w, 12});
                }
                total += 13f;
            }
        }
        return total;
    }

    private float drawKronexColor(DrawContext context, ModernSetting setting, float x, float y, float w, float alpha) {
        boolean isHovered = !closing && inside(currentMouseX, currentMouseY, x, y, w, 13f);
        float hoverT = updateHover("cl:" + setting.key, isHovered);
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, 13, 4f, withAlpha(blendColors(ELEM_COLOR, 0xFFFFFFFF, hoverT * 0.05f), alpha));
        drawMarqueeText(context, "lbl:" + setting.key, setting.label, x + 6f, y + 3.2f, w - 32f, 7f, withAlpha(TEXT_COLOR, alpha), isHovered);
        drawColorSwatch(context, setting.key, x + w - 20f, y + 2.5f, 14f, 8f, alpha);
        return 13f + 3f;
    }

    private float drawKronexBind(DrawContext context, ModernSetting setting, float x, float y, float w, float alpha) {
        boolean isHovered = !closing && inside(currentMouseX, currentMouseY, x, y, w, 13f);
        float hoverT = updateHover("bnd:" + setting.key, isHovered);
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, 13, 4f, withAlpha(blendColors(ELEM_COLOR, 0xFFFFFFFF, hoverT * 0.05f), alpha));

        String bindName = formatBindName(getStoredBindValue(setting.key));
        float pillW = Math.min(getSfuiWidth(bindName, 7f) + 10f, 44f);
        float pillX = x + w - pillW - 6f, pillY = y + 2f, pillH = 9f;
        RoundedRectShader.draw(context, (int) pillX, (int) pillY, (int) pillW, (int) pillH, pillH / 2f, withAlpha(0x50000000, alpha));
        drawMarqueeText(context, "bindname:" + setting.key, bindName, pillX + 4f, y + 3.2f, pillW - 8f, 7f, withAlpha(SUBTEXT_COLOR, alpha), isHovered);

        drawMarqueeText(context, "lbl:" + setting.key, setting.label, x + 6f, y + 3.2f, w - pillW - 18f, 7f, withAlpha(TEXT_COLOR, alpha), isHovered);
        settingClickBounds.put("bind_open:" + setting.key, new int[]{(int) x, (int) y, (int) w, 13});
        return 13f + 3f;
    }

    private float drawKronexPad2D(DrawContext context, ModernSetting setting, float x, float y, float w, float alpha) {
        float padH = 28f;
        drawSfuiCustom(context, setting.label, x + 1f, y, 7f, withAlpha(TEXT_COLOR, alpha));
        float py = y + 9f;
        RoundedRectShader.draw(context, (int) x, (int) py, (int) w, (int) padH, 4f, withAlpha(ELEM_COLOR, alpha));

        float vx = ClientData.numSettings.getOrDefault(setting.key, (setting.min + setting.max) / 2f);
        float vy = ClientData.numSettings.getOrDefault(setting.keyY, (setting.minY + setting.maxY) / 2f);
        float tx = clamp((vx - setting.min) / Math.max(0.0001f, setting.max - setting.min), 0f, 1f);
        float ty = clamp((vy - setting.minY) / Math.max(0.0001f, setting.maxY - setting.minY), 0f, 1f);

        float dotX = x + tx * w;
        float dotY = py + ty * padH;
        RoundedRectShader.draw(context, (int) (x + w / 2f - 0.5f), (int) py, 1, (int) padH, 0f, withAlpha(0x30FFFFFF, alpha));
        RoundedRectShader.draw(context, (int) x, (int) (py + padH / 2f - 0.5f), (int) w, 1, 0f, withAlpha(0x30FFFFFF, alpha));
        RoundedRectShader.draw(context, (int) (dotX - 3f), (int) (dotY - 3f), 6, 6, 3f, withAlpha(getThemeColor(), alpha));

        padBounds.put(setting.key, new float[]{x, py, w, padH, setting.min, setting.max, setting.minY, setting.maxY});
        padKeyYMap.put(setting.key, setting.keyY);
        return 9f + padH + 3f;
    }

    // =========================================================================
    //  ДИСПЕТЧЕР СОДЕРЖИМОГО НАСТРОЕК МОДУЛЯ (для панели-слайда)
    // =========================================================================
    private void drawExpandedModuleContent(DrawContext context, String module, float x, float y, float w, float alpha) {
        if (module.equals("Lock Slot")) { drawLockSlotSettings(context, x, y, w, alpha); return; }
        if (module.equals("Aspect Ratio")) { drawAspectRatioSettings(context, x, y, w, alpha); return; }
        if (module.equals("Fast Swap")) { drawFastSwapSettings(context, x, y, w, alpha); return; }
        if (module.equals("Item Highlighter")) { drawItemHighlighterSettings(context, x, y, w, alpha); return; }
        if (module.equals("Particles")) { drawParticlesSettings(context, x, y, w, alpha); return; }
        if (module.equals("Item Swap")) { drawItemSwapCompactSettings(context, x, y, w, alpha); return; }
        if (module.equals("GPS")) { drawGPSSettings(context, x, y, w, alpha); return; }

        if (!ModernSettingsRegistry.hasSettings(module)) {
            drawSfuiTiny(context, "Настроек нет", x, y + 2f, withAlpha(SUBTEXT_COLOR, alpha));
            return;
        }
        float cy = y;
        for (ModernSetting setting : ModernSettingsRegistry.get(module)) {
            cy += drawKronexSetting(context, setting, x, cy, w, alpha);
        }
    }

    private void drawGPSSettings(DrawContext context, float x, float y, float w, float alpha) {
        float cy = y;
        if (ModernSettingsRegistry.hasSettings("GPS")) {
            for (ModernSetting setting : ModernSettingsRegistry.get("GPS")) {
                cy += drawKronexSetting(context, setting, x, cy, w, alpha);
            }
        }

        cy += 4f;
        drawSfuiTiny(context, "МЕТКИ (WAYPOINTS)", x, cy, withAlpha(SUBTEXT_COLOR, alpha));
        cy += 14f;

        float addW = 20f;
        float inputW = (w - addW - 12f) / 3f;
        drawGpsInput(context, "Имя", gpsInputName, x, cy, inputW, focusedGpsInput == 1, alpha);
        drawGpsInput(context, "X", gpsInputX, x + inputW + 4f, cy, inputW, focusedGpsInput == 2, alpha);
        drawGpsInput(context, "Z", gpsInputZ, x + inputW * 2f + 8f, cy, inputW, focusedGpsInput == 3, alpha);

        float addX = x + inputW * 3f + 12f;
        RoundedRectShader.draw(context, (int) addX, (int) cy, (int) addW, 18, 4.0f, withAlpha(getThemeColor(), alpha));
        drawSfuiControlCentered(context, "+", addX + addW / 2f, cy + 4f, withAlpha(0xFFFFFFFF, alpha));
        settingClickBounds.put("gps_add", new int[]{(int) addX, (int) cy, (int) addW, 18});

        cy += 26f;

        try {
            if (com.lexoravisauls.client.utils.GPS.waypoints != null) {
                for (com.lexoravisauls.client.utils.GPS.GpsWaypoint wp : com.lexoravisauls.client.utils.GPS.waypoints) {
                    RoundedRectShader.draw(context, (int) x, (int) cy, (int) w, 20, 5.0f, withAlpha(ELEM_COLOR, alpha));
                    String display = wp.name + " (" + (int) wp.x + ", " + (int) wp.z + ")";
                    drawSfuiTiny(context, clipToWidth(display, w - 54f, 7f), x + 5f, cy + 6.5f, withAlpha(TEXT_COLOR, alpha));

                    drawColorSwatch(context, "gps:" + wp.name, x + w - 46f, cy + 4f, 12f, 12f, alpha);

                    float dX = x + w - 16f;
                    boolean delHover = !closing && inside(currentMouseX, currentMouseY, dX, cy + 4f, 12f, 12f);
                    updateHover("gps_del_h:" + wp.name, delHover);
                    RoundedRectShader.draw(context, (int) dX, (int) cy + 4, 12, 12, 3.0f, withAlpha(0xFFFF4D4D, alpha));
                    drawSfuiControlCentered(context, "✗", dX + 6f, cy + 6.5f, withAlpha(0xFFFFFFFF, alpha));
                    settingClickBounds.put("gps_del:" + wp.name, new int[]{(int) dX, (int) cy + 4, 12, 12});

                    cy += 24f;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawGpsInput(DrawContext context, String placeholder, String value, float x, float y, float w, boolean focused, float alpha) {
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, 18, 4.0f, withAlpha(focused ? PANEL_COLOR : ELEM_COLOR, alpha));
        if (focused) {
            RoundedRectShader.draw(context, (int) x - 1, (int) y - 1, (int) w + 2, 20, 5.0f, withAlpha(getThemeColor(), 0.5f * alpha));
        }
        String text = value.isEmpty() ? placeholder : value;
        int color = value.isEmpty() ? SUBTEXT_COLOR : TEXT_COLOR;
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) text += "|";
        drawSfuiTiny(context, text, x + 4f, y + 6f, withAlpha(color, alpha));
        settingClickBounds.put("gps_in_" + placeholder, new int[]{(int) x, (int) y, (int) w, 18});
    }

    private void drawLockSlotSettings(DrawContext context, float x, float y, float w, float alpha) {
        int size = 17, gap = 4;
        int totalTopW = size * 4 + gap * 3;
        int totalBottomW = size * 5 + gap * 4;
        float startXTop = x + Math.max(0, (w - totalTopW) / 2f);
        float startXBottom = x + Math.max(0, (w - totalBottomW) / 2f);
        float row1Y = y + 2f, row2Y = y + 2f + size + gap;

        for (int i = 0; i < 4; i++) drawLockSlotBox(context, i, startXTop + i * (size + gap), row1Y, size, alpha);
        for (int i = 4; i < 9; i++) drawLockSlotBox(context, i, startXBottom + (i - 4) * (size + gap), row2Y, size, alpha);
    }

    private void drawLockSlotBox(DrawContext context, int slot, float x, float y, int size, float alpha) {
        boolean locked = ClientData.moduleStates.getOrDefault("LockSlot_" + slot, false);
        int bg = locked ? getThemeColor() : ELEM_COLOR;
        int fg = locked ? 0xFFFFFFFF : TEXT_COLOR;
        settingClickBounds.put("toggle:LockSlot_" + slot, new int[]{(int) x, (int) y, size, size});
        RoundedRectShader.draw(context, (int) x, (int) y, size, size, 5.0f, withAlpha(bg, alpha));
        drawSfuiControlCentered(context, String.valueOf(slot + 1), x + size / 2.0f, y + (size - 8f) / 2f, withAlpha(fg, alpha));
    }

    private void drawAspectRatioSettings(DrawContext context, float x, float y, float w, float alpha) {
        float cy = y;
        cy += drawKronexSetting(context, ModernSetting.mode("Ratio Mode", "Режим", "Default", "4:3", "16:9", "16:10", "Custom"), x, cy, w, alpha);
        if (ClientData.modeSettings.getOrDefault("Ratio Mode", "Default").equals("Custom")) {
            drawKronexSetting(context, ModernSetting.slider("Aspect Ratio Val", "Значение", 0.5f, 3.0f), x, cy, w, alpha);
        }
    }

    private void drawFastSwapSettings(DrawContext context, float x, float y, float w, float alpha) {
        float cy = y;
        cy += drawKronexSetting(context, ModernSetting.toggle("Show Hotbar Binds", "Бинды"), x, cy, w, alpha);
        drawSfuiTiny(context, "ФТ", x, cy, withAlpha(0xFFFF55FF, alpha));
        cy += 14f;
        cy = drawFastSwapBind(context, "Bind_Дезка", "Дезка", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Явная пыль", "Явная пыль", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Божья аура", "Божья аура", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Пласт", "Пласт", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Трапка ФТ", "Трапка", x, cy, w, alpha);
        drawSfuiTiny(context, "ХВ", x, cy, withAlpha(0xFFFF55FF, alpha));
        cy += 14f;
        cy = drawFastSwapBind(context, "Bind_Стан", "Стан", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Взр. штучка", "Взр. штучка", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Трапка ХВ", "Трапка ХВ", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Взр. трапка", "Взр. трапка", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Ком снега", "Ком снега", x, cy, w, alpha);
        drawSfuiTiny(context, "Другое", x, cy, withAlpha(0xFFFF55FF, alpha));
        cy += 14f;
        cy = drawFastSwapBind(context, "Bind_Хорус", "Хорус", x, cy, w, alpha);
        cy = drawFastSwapBind(context, "Bind_Эндер перл", "Эндер перл", x, cy, w, alpha);
        drawFastSwapBind(context, "Bind_Исцеление", "Зелье", x, cy, w, alpha);
    }

    private float drawFastSwapBind(DrawContext context, String key, String label, float x, float y, float w, float alpha) {
        return y + drawKronexSetting(context, ModernSetting.bind(key, label), x, y, w, alpha);
    }

    private void drawItemHighlighterSettings(DrawContext context, float x, float y, float w, float alpha) {
        float cy = y;
        cy += drawKronexSetting(context, ModernSetting.slider("Highlighter Alpha", "Прозрачность", 0f, 255f), x, cy, w, alpha);
        cy += drawKronexSetting(context, ModernSetting.toggle("Highlighter Pulsation", "Пульсация"), x, cy, w, alpha);
        cy += 4f;

        for (Map.Entry<Item, ItemHighlighter.ItemConfig> entry : ItemHighlighter.ITEM_CONFIGS.entrySet()) {
            Item item = entry.getKey();
            ItemHighlighter.ItemConfig cfg = entry.getValue();
            String name = ItemHighlighter.ITEM_NAMES.getOrDefault(item, item.getName().getString());

            RoundedRectShader.draw(context, (int) x, (int) cy, (int) w, 20, 5.0f, withAlpha(ELEM_COLOR, alpha));
            drawScaledItem(context, item, x + 1, cy + 2, 0.52f);
            drawSfuiTiny(context, clipToWidth(name, w - 66f, 7.5f), x + 13f, cy + 6.5f, withAlpha(TEXT_COLOR, alpha));

            drawCheckmarkAnimated(context, "ihchk:" + name, cfg.enabled, x + w - 38f, cy + 5f, 10f, alpha);
            settingClickBounds.put("ih_toggle:" + name, new int[]{(int) (x + w - 44f), (int) cy + 2, 21, 16});

            drawColorSwatch(context, "ih:" + name, x + w - 16f, cy + 4f, 12f, 12f, alpha);

            cy += 24f;
        }
    }

    private void drawParticlesSettings(DrawContext context, float x, float y, float w, float alpha) {
        float cy = y;
        cy += drawKronexSetting(context, ModernSetting.mode("Part. Texture", "Текстура", "Star", "Skull", "Bucks", "Snow", "Blast", "Brich", "Core", "Show", "Snowbag", "Genshin", "Heart", "Cube"), x, cy, w, alpha);

        cy += drawKronexSetting(context, ModernSetting.header("Цвет частиц"), x, cy, w, alpha);
        cy += drawKronexSetting(context, ModernSetting.mode("Part. Color Mode", "Источник цвета", "Theme", "Custom"), x, cy, w, alpha);
        if (ClientData.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom")) {
            cy += drawKronexSetting(context, ModernSetting.mode("Part. Color Type", "Тип цвета", "Solid", "Gradient"), x, cy, w, alpha);
            if (ClientData.modeSettings.getOrDefault("Part. Color Type", "Solid").equals("Solid")) {
                cy += drawKronexSetting(context, ModernSetting.color("Part. Custom Color", "Свой цвет"), x, cy, w, alpha);
            } else {
                cy += drawKronexSetting(context, ModernSetting.color("Part. Custom Color 1", "Цвет 1 (Градиент)"), x, cy, w, alpha);
                cy += drawKronexSetting(context, ModernSetting.color("Part. Custom Color 2", "Цвет 2 (Градиент)"), x, cy, w, alpha);
            }
        }

        // Возврат к простому мгновенному позиционированию: попытка плавно "подтягивать" соседние
        // секции лерпом одной и той же переменной 6 раз за кадр подряд создавала дрожание на
        // стыках (каждый вызов лерпил к уже сдвинутой на предыдущем шаге цели, из-за чего
        // сглаживание никогда толком не устаканивалось). Резерв места под секцию как был
        // мгновенным (см. комментарий в drawParticlesSection про наезд секций), так и остался —
        // это осознанный компромисс, отрыв при открытии секции есть, но без каши.
        cy += drawParticlesSection(context, x, cy, w, alpha, "Эмбиент", "Part. Ambient", new ModernSetting[]{ModernSetting.slider("Amb Chance", "Шанс", 1.0f, 100.0f), ModernSetting.slider("Amb Size", "Размер", 0.2f, 3.0f), ModernSetting.slider("Amb Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Amb Life", "Жизнь", 10.0f, 200.0f)});
        cy += drawParticlesSection(context, x, cy, w, alpha, "Ходьба", "Part. Walk", new ModernSetting[]{ModernSetting.slider("Walk Count", "Кол-во", 1.0f, 15.0f), ModernSetting.slider("Walk Size", "Размер", 0.2f, 3.0f), ModernSetting.slider("Walk Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Walk Life", "Жизнь", 5.0f, 80.0f)});
        cy += drawParticlesSection(context, x, cy, w, alpha, "Удар", "Part. Hit", new ModernSetting[]{ModernSetting.slider("Hit Size", "Размер", 0.2f, 15.0f), ModernSetting.slider("Hit Count", "Кол-во", 1.0f, 20.0f), ModernSetting.slider("Hit Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Hit Life", "Жизнь", 5.0f, 100.0f)});
        cy += drawParticlesSection(context, x, cy, w, alpha, "Крит", "Part. Crit", new ModernSetting[]{ModernSetting.slider("Crit Size", "Размер", 0.2f, 15.0f), ModernSetting.slider("Crit Count", "Кол-во", 1.0f, 20.0f), ModernSetting.slider("Crit Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Crit Life", "Жизнь", 5.0f, 100.0f)});
        cy += drawParticlesSection(context, x, cy, w, alpha, "Снаряды", "Part. Projectiles", new ModernSetting[]{ModernSetting.slider("Proj Count", "Кол-во", 1.0f, 25.0f), ModernSetting.slider("Proj Size", "Размер", 0.2f, 3.0f), ModernSetting.slider("Proj Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Proj Life", "Жизнь", 5.0f, 100.0f)});
        drawParticlesSection(context, x, cy, w, alpha, "Тотем", "Part. Totem", new ModernSetting[]{ModernSetting.slider("Totem Size", "Размер", 0.2f, 4.0f), ModernSetting.slider("Totem Count", "Кол-во", 5.0f, 100.0f), ModernSetting.slider("Totem Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Totem Life", "Жизнь", 10.0f, 150.0f), ModernSetting.toggle("Disable Vanilla Totem", "Скрыть ванильный тотем")});
    }

    private float drawParticlesSection(DrawContext context, float x, float y, float w, float alpha, String title, String toggleKey, ModernSetting[] settings) {
        float cy = y;
        cy += drawKronexSetting(context, ModernSetting.toggle(toggleKey, title), x, cy, w, alpha);
        float toggleH = cy - y;

        boolean sectionOn = ClientData.moduleStates.getOrDefault(toggleKey, false);
        float anim = getAnimatedParticlesSection(toggleKey);
        float fullHeight = 0.0f;
        for (ModernSetting s : settings) fullHeight += getKronexSettingHeight(s);

        if (sectionOn || anim > 0.02f) {
            // ВАЖНО: место резервируем ПОЛНОЕ сразу (fullHeight), а не fullHeight*anim — раньше
            // тут был реальный баг: строки рисовались в полный рост всегда (плавно менялась только
            // прозрачность), а возвращаемая высота была урезана анимацией — следующая секция
            // получала меньше места, чем реально занято, и наезжала на текущую.
            float contentAlpha = alpha * clamp(anim * 1.3f, 0.0f, 1.0f);
            float slide = (1f - smoothT(anim)) * 5f;
            float innerY = cy - slide;
            for (ModernSetting setting : settings) innerY += drawKronexSetting(context, setting, x, innerY, w, contentAlpha);
            return toggleH + fullHeight + 6.0f;
        }
        return toggleH + 2.0f;
    }

    private void drawItemSwapCompactSettings(DrawContext context, float x, float y, float w, float alpha) {
        float cy = y;
        for (ModernSetting setting : ModernSettingsRegistry.get("Item Swap")) {
            cy += drawKronexSetting(context, setting, x, cy, w, alpha);
        }
    }

    private float getAnimatedParticlesSection(String toggleKey) {
        float target = ClientData.moduleStates.getOrDefault(toggleKey, false) ? 1.0f : 0.0f;
        float current = particlesSectionAnimations.getOrDefault(toggleKey, target);
        current += (target - current) * 0.35f;
        particlesSectionAnimations.put(toggleKey, sanitize(current));
        return current;
    }

    private float getKronexSettingHeight(ModernSetting setting) {
        if (!setting.isVisible()) return 0f;
        // ФИКС "огромного отрыва" в партиклах: тут были придуманные числа (28/16/88), которые
        // разъехались с тем, что реально возвращают отрисовщики — drawKronexSettingToggle
        // (16f), drawKronexSlider (18f), drawKronexColor (16f), drawKronexBind (16f),
        // drawKronexHeader (11f), drawKronexPad2D (40f). А drawParticlesSection считает
        // fullHeight (сколько места зарезервировать под открытую секцию) именно через эту
        // функцию — для 4 слайдеров "Ходьбы" разница набегала 4×(28-18)=40px чистой пустоты
        // между последним слайдером и следующей секцией. Заодно это же завышало maxScroll
        // везде, где он считается через getExpandedTargetHeight, не только в партиклах.
        return switch (setting.type) {
            case TOGGLE, COLOR, BIND -> 16f;
            case SLIDER -> 18f;
            case HEADER -> 11f;
            case PAD2D -> 40f;
            case MODE -> modeSettingHeight(setting);
        };
    }

    private float modeSettingHeight(ModernSetting setting) {
        boolean open = setting.key.equals(openModeDropdown);
        float dropAnim = modeDropdownAnim.getOrDefault(setting.key, open ? 1f : 0f);
        float h = 16f;
        if (open || dropAnim > 0.02f) h += setting.modes.size() * 13f;
        return h;
    }

    private float getParticlesSettingsHeight() {
        float h = getKronexSettingHeight(ModernSetting.mode("Part. Texture", "Текстура", "Star", "Skull", "Bucks", "Snow", "Blast", "Brich", "Core", "Show", "Snowbag", "Genshin", "Heart"));

        h += getKronexSettingHeight(ModernSetting.header("Цвет частиц"));
        h += getKronexSettingHeight(ModernSetting.mode("Part. Color Mode", "Источник цвета", "Theme", "Custom"));
        if (ClientData.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom")) {
            h += getKronexSettingHeight(ModernSetting.mode("Part. Color Type", "Тип цвета", "Solid", "Gradient"));
            if (ClientData.modeSettings.getOrDefault("Part. Color Type", "Solid").equals("Solid")) {
                h += getKronexSettingHeight(ModernSetting.color("Part. Custom Color", "Свой цвет"));
            } else {
                h += getKronexSettingHeight(ModernSetting.color("Part. Custom Color 1", "Цвет 1 (Градиент)"));
                h += getKronexSettingHeight(ModernSetting.color("Part. Custom Color 2", "Цвет 2 (Градиент)"));
            }
        }

        h += getParticlesSectionHeight("Part. Ambient", new ModernSetting[]{ModernSetting.slider("Amb Chance", "Шанс", 1.0f, 100.0f), ModernSetting.slider("Amb Size", "Размер", 0.2f, 3.0f), ModernSetting.slider("Amb Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Amb Life", "Жизнь", 10.0f, 200.0f)});
        h += getParticlesSectionHeight("Part. Walk", new ModernSetting[]{ModernSetting.slider("Walk Count", "Кол-во", 1.0f, 15.0f), ModernSetting.slider("Walk Size", "Размер", 0.2f, 3.0f), ModernSetting.slider("Walk Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Walk Life", "Жизнь", 5.0f, 80.0f)});
        h += getParticlesSectionHeight("Part. Hit", new ModernSetting[]{ModernSetting.slider("Hit Size", "Размер", 0.2f, 15.0f), ModernSetting.slider("Hit Count", "Кол-во", 1.0f, 20.0f), ModernSetting.slider("Hit Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Hit Life", "Жизнь", 5.0f, 100.0f)});
        h += getParticlesSectionHeight("Part. Crit", new ModernSetting[]{ModernSetting.slider("Crit Size", "Размер", 0.2f, 15.0f), ModernSetting.slider("Crit Count", "Кол-во", 1.0f, 20.0f), ModernSetting.slider("Crit Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Crit Life", "Жизнь", 5.0f, 100.0f)});
        h += getParticlesSectionHeight("Part. Projectiles", new ModernSetting[]{ModernSetting.slider("Proj Count", "Кол-во", 1.0f, 25.0f), ModernSetting.slider("Proj Size", "Размер", 0.2f, 3.0f), ModernSetting.slider("Proj Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Proj Life", "Жизнь", 5.0f, 100.0f)});
        h += getParticlesSectionHeight("Part. Totem", new ModernSetting[]{ModernSetting.slider("Totem Size", "Размер", 0.2f, 4.0f), ModernSetting.slider("Totem Count", "Кол-во", 5.0f, 100.0f), ModernSetting.slider("Totem Spread", "Разброс", 0.1f, 5.0f), ModernSetting.slider("Totem Life", "Жизнь", 10.0f, 150.0f), ModernSetting.toggle("Disable Vanilla Totem", "Скрыть ванильный тотем")});
        return h;
    }

    private float getParticlesSectionHeight(String toggleKey, ModernSetting[] settings) {
        float toggleH = getKronexSettingHeight(ModernSetting.toggle(toggleKey, "")); // должно 1-в-1 совпадать с toggleH в drawParticlesSection
        boolean sectionOn = ClientData.moduleStates.getOrDefault(toggleKey, false);
        // Только читаем текущее значение анимации, НЕ тикаем его — иначе оно продвигается дважды
        // за кадр (тут и ещё раз в drawParticlesSection при рендере) и медленно рассинхронизируется.
        float anim = particlesSectionAnimations.getOrDefault(toggleKey, sectionOn ? 1f : 0f);
        if (!sectionOn && anim <= 0.02f) return toggleH + 2.0f;
        float fullH = 0.0f;
        for (ModernSetting setting : settings) fullH += getKronexSettingHeight(setting);
        return toggleH + fullH + 6.0f;
    }

    private float getExpandedTargetHeight(String m) {
        if (m.equals("Lock Slot")) return 48.0f;
        if (m.equals("Aspect Ratio")) {
            return ClientData.modeSettings.getOrDefault("Ratio Mode", "Default").equals("Custom") ? 85.0f : 55.0f;
        }
        if (m.equals("Fast Swap")) return 400.0f;
        if (m.equals("Item Highlighter")) return 60.0f + ItemHighlighter.ITEM_CONFIGS.size() * 24.0f;
        if (m.equals("Particles")) return getParticlesSettingsHeight();

        if (m.equals("GPS")) {
            float h = 18f;
            if (ModernSettingsRegistry.hasSettings(m)) {
                for (ModernSetting setting : ModernSettingsRegistry.get(m)) h += getKronexSettingHeight(setting);
            }
            h += 44f;
            try {
                if (com.lexoravisauls.client.utils.GPS.waypoints != null) {
                    h += com.lexoravisauls.client.utils.GPS.waypoints.size() * 24f;
                }
            } catch (Throwable ignored) {
            }
            return h;
        }

        if (m.equals("Item Swap")) {
            float h = 4f;
            for (ModernSetting setting : ModernSettingsRegistry.get("Item Swap")) h += getKronexSettingHeight(setting);
            return h;
        }

        if (!ModernSettingsRegistry.hasSettings(m)) return 20.0f;
        float h = 4f;
        for (ModernSetting setting : ModernSettingsRegistry.get(m)) h += getKronexSettingHeight(setting);
        return h;
    }

    // =========================================================================
    //  МИНИ-ПАНЕЛЬ: PARTY
    // =========================================================================
    private void drawPartyTab(DrawContext context, float x, float y, float w, float h, float alpha) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;

        int themeColor = getThemeColor();
        float cx = x + w / 2f;
        float curY = y - scrolls[SCROLL_PARTY];

        if (!LexoraPartyManager.inParty() && !LexoraPartyManager.waitingForResponse) {
            int btnW = (int) (w * 0.72f);
            int btnX = (int) (cx - btnW / 2f);
            boolean createHovered = !closing && inside(currentMouseX, currentMouseY, btnX, curY, btnW, 22);
            float createHoverT = updateHover("party_create", createHovered);
            RoundedRectShader.draw(context, btnX, (int) curY, btnW, 22, 6f, withAlpha(blendColors(themeColor, 0xFFFFFFFF, createHoverT * 0.12f), 0.85f * alpha));
            drawSfuiCustom(context, "✦ Создать пати", cx - getSfuiWidth("✦ Создать пати", 9f) / 2f, curY + 7f, 9f, withAlpha(0xFFFFFFFF, alpha));
            settingClickBounds.put("party_create", new int[]{btnX, (int) curY, btnW, 22});
            curY += 34f;

            drawSfuiCustom(context, "— или введите код —", cx - getSfuiWidth("— или введите код —", 7.5f) / 2f, curY, 7.5f, withAlpha(SUBTEXT_COLOR, alpha));
            curY += 18f;

            int inputW = (int) (w * 0.8f);
            int inputX = (int) (cx - inputW / 2f);

            boolean inputActive = partyCodeInputFocused;
            int inputBg = withAlpha(inputActive ? 0xFF1E1E2E : ELEM_COLOR, alpha);
            RoundedRectShader.draw(context, inputX, (int) curY, inputW, 22, 6f, inputBg);
            if (inputActive) {
                RoundedRectShader.draw(context, inputX - 1, (int) curY - 1, inputW + 2, 24, 7f, withAlpha(themeColor, 0.6f * alpha));
            }

            String displayCode = partyCodeInput.isEmpty() && !inputActive ? "Код из 10 цифр..." : partyCodeInput;
            int codeColor = partyCodeInput.isEmpty() ? withAlpha(SUBTEXT_COLOR, alpha) : withAlpha(TEXT_COLOR, alpha);
            if (inputActive) {
                boolean showCursor = (System.currentTimeMillis() / 500L) % 2L == 0L;
                drawSfuiCustom(context, partyCodeInput + (showCursor ? "|" : " "), inputX + 8f, curY + 7f, 8f, withAlpha(TEXT_COLOR, alpha));
            } else {
                drawSfuiCustom(context, displayCode, inputX + 8f, curY + 7f, 8f, codeColor);
            }

            partyCodeInputBounds = new int[]{inputX, (int) curY, inputW, 22};
            settingClickBounds.put("party_code_input", partyCodeInputBounds);
            curY += 30f;

            boolean canJoin = partyCodeInput.length() == 10;
            int joinColor = canJoin ? withAlpha(0xFF22C55E, alpha) : withAlpha(0xFF3A3A4A, alpha);
            RoundedRectShader.draw(context, inputX, (int) curY, inputW, 22, 6f, joinColor);
            drawSfuiCustom(context, "Вступить →", cx - getSfuiWidth("Вступить →", 9f) / 2f, curY + 7f, 9f, withAlpha(canJoin ? 0xFFFFFFFF : SUBTEXT_COLOR, alpha));
            if (canJoin) settingClickBounds.put("party_join", new int[]{inputX, (int) curY, inputW, 22});

        } else if (LexoraPartyManager.waitingForResponse) {
            float dotTime = (System.currentTimeMillis() % 1200) / 1200f;
            String dots = dotTime < 0.33f ? "." : dotTime < 0.66f ? ".." : "...";
            drawSfuiCustom(context, "Ожидание ответа лидера" + dots, cx - getSfuiWidth("Ожидание ответа лидера...", 9f) / 2f, curY + 8f, 9f, withAlpha(0xFFFFAA00, alpha));
            curY += 30f;
            String codeTxt = "Код: " + LexoraPartyManager.waitingCode;
            drawSfuiCustom(context, codeTxt, cx - getSfuiWidth(codeTxt, 8f) / 2f, curY, 8f, withAlpha(SUBTEXT_COLOR, alpha));
            curY += 28f;
            int cancelW = 100;
            int cancelX = (int) (cx - cancelW / 2f);
            RoundedRectShader.draw(context, cancelX, (int) curY, cancelW, 20, 6f, withAlpha(0xFF4A1A1A, alpha));
            drawSfuiCustom(context, "Отменить", cx - getSfuiWidth("Отменить", 8.5f) / 2f, curY + 6f, 8.5f, withAlpha(0xFFFF6666, alpha));
            settingClickBounds.put("party_cancel_wait", new int[]{cancelX, (int) curY, cancelW, 20});

        } else {
            String codeLabel = LexoraPartyManager.isOwner ? "Ваш код (поделитесь):" : "Код пати:";
            drawSfuiCustom(context, codeLabel, cx - getSfuiWidth(codeLabel, 7.5f) / 2f, curY, 7.5f, withAlpha(SUBTEXT_COLOR, alpha));
            curY += 14f;
            drawSfuiCustom(context, LexoraPartyManager.partyCode, cx - getSfuiWidth(LexoraPartyManager.partyCode, 13f) / 2f, curY, 13f, withAlpha(themeColor, alpha));
            curY += 24f;

            String membersLabel = "Участники (" + LexoraPartyManager.members.size() + "/10):";
            drawSfui(context, membersLabel, x + 2f, curY, withAlpha(TEXT_COLOR, alpha));
            curY += 16f;

            for (LexoraPartyManager.PartyMember m : LexoraPartyManager.members) {
                float cardH = 28f;
                RoundedRectShader.draw(context, (int) x, (int) curY, (int) w, (int) cardH, 6f, withAlpha(ELEM_COLOR, alpha));
                int dotColor = m.online ? withAlpha(0xFF22C55E, alpha) : withAlpha(0xFF555555, alpha);
                RoundedRectShader.draw(context, (int) (x + 9), (int) (curY + 10), 8, 8, 4f, dotColor);

                boolean isOwnerMember = m.uuid.equals(LexoraPartyManager.ownerUuid);
                String nameTxt = (isOwnerMember ? "★ " : "") + m.name;
                drawSfuiTiny(context, clipToWidth(nameTxt, w * 0.5f, 7.5f), x + 22f, curY + 10f, isOwnerMember ? withAlpha(themeColor, alpha) : withAlpha(TEXT_COLOR, alpha));

                String serverTxt = m.online ? (m.server.isEmpty() ? "Онлайн" : "► " + m.server) : "Офлайн";
                float serverW = getSfuiWidth(serverTxt, 6.5f);
                drawSfuiTiny(context, serverTxt, x + w - serverW - 7f, curY + 10.5f, m.online ? withAlpha(0xFF22C55E, alpha) : withAlpha(SUBTEXT_COLOR, alpha));

                curY += cardH + 4f;
                if (curY > y + h - 34f) break;
            }

            int leaveW = 96;
            int leaveX = (int) (cx - leaveW / 2f);
            float leaveY = y + h - 26f;
            RoundedRectShader.draw(context, leaveX, (int) leaveY, leaveW, 20, 6f, withAlpha(0xFF4A1A1A, alpha));
            drawSfuiCustom(context, "Выйти из пати", cx - getSfuiWidth("Выйти из пати", 8f) / 2f, leaveY + 6f, 8f, withAlpha(0xFFFF5555, alpha));
            settingClickBounds.put("party_leave", new int[]{leaveX, (int) leaveY, leaveW, 20});
        }
    }

    // =========================================================================
    //  МИНИ-ПАНЕЛЬ: EVENTS
    // =========================================================================
    private void drawEventsTab(DrawContext context, float x, float y, float w, float alpha) {
        final float visibleH = 300f;
        float cy = y - scrolls[SCROLL_EVENTS];

        if (!HolyWorldEventsApi.hasData()) {
            drawSfuiTiny(context, "🔄 Подключение к HW API...", x, cy + 10f, withAlpha(SUBTEXT_COLOR, alpha));
            targetScrolls[SCROLL_EVENTS] = 0f;
            return;
        }

        List<HolyWorldEventsApi.HwEvent> list = HolyWorldEventsApi.getEvents();
        float contentTop = cy;

        context.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + visibleH));
        try {
            if (list.isEmpty()) {
                drawSfuiTiny(context, "Сейчас на серверах спокойно — ивентов нет", x, cy + 10f, withAlpha(SUBTEXT_COLOR, alpha));
            } else {
                for (HolyWorldEventsApi.HwEvent ev : list) {
                    int accentColor = HolyWorldEventsApi.tierColor(ev.rarityTier());
                    cy = drawEventCard(context, x, cy, w, alpha, ev.displayName(), ev.rarityDisplay(), ev.serverName(), "", accentColor);
                }
            }
        } finally {
            context.disableScissor();
        }

        // ФИКС "не листает, хотя ивенты ещё есть": раньше максимум скролла считался как
        // totalHeight - 250, а видимая (scissor) область была 300 — несовпадающие числа в двух
        // местах. На малом количестве карточек разница не давала о себе знать, на большом —
        // скролл упирался в потолок, недодавая ~50px, и до части карточек было physически не
        // долистать. Теперь maxScroll и высота scissor берутся из одной и той же visibleH.
        float totalContentH = cy - contentTop;
        targetScrolls[SCROLL_EVENTS] = clamp(targetScrolls[SCROLL_EVENTS], 0f, Math.max(0f, totalContentH - visibleH));
    }

    private String decrementTimeInString(String status, long elapsedMillis) {
        if (status == null || status.isEmpty()) return status;
        long elapsedSecs = elapsedMillis / 1000;
        try {
            Matcher m = TIME_PATTERN.matcher(status);
            if (m.find()) {
                String prefix = m.group(1);
                int mins = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                int secs = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
                int totalSecs = mins * 60 + secs - (int) elapsedSecs;
                if (totalSecs <= 0) return status.toLowerCase().contains("до конца") ? "Завершено" : "Призван / Ожидается";
                int newMins = totalSecs / 60, newSecs = totalSecs % 60;
                String newTime = prefix + " " + (newMins > 0 ? newMins + "м " : "") + newSecs + "с";
                return status.replace(m.group(0), newTime.trim());
            }
        } catch (Exception ignored) {
        }
        return status;
    }

    private String extractAnarchyNumber(String text) {
        Matcher m = NUMBER_PATTERN.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private int getIconColorBasedOnNameOrRarity(String name, String rarity) {
        String combined = (name + " " + (rarity == null ? "" : rarity)).toLowerCase();
        if (combined.contains("элитный") || combined.contains("лайт")) return 0xFF55FFFF;
        if (combined.contains("легендарный") || combined.contains("мифический") || combined.contains("маяк")) return 0xFFFFAA00;
        if (combined.contains("смерти") || combined.contains("резня") || combined.contains("босс")) return 0xFFFF5555;
        if (combined.contains("обычный")) return 0xFFFFFFFF;
        return 0xFF22C55E;
    }

    private void drawSelectorButton(DrawContext context, String text, boolean active, float x, float y, float w, float h, float alpha, String clickId) {
        int bg = active ? getThemeColor() : ELEM_COLOR;
        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, (int) h, 5.0f, withAlpha(bg, alpha));
        drawSfuiControlCentered(context, text, x + w / 2f, y + h / 2f - 3f, withAlpha(active ? 0xFFFFFFFF : SUBTEXT_COLOR, alpha));
        settingClickBounds.put(clickId, new int[]{(int) x, (int) y, (int) w, (int) h});
    }

    private float drawEventCard(DrawContext context, float x, float y, float w, float alpha, String name, String rarity, String server, String status, int accentColor) {
        float padding = 8f, leftSpace = 4f, rightSpace = w * 0.42f;
        float maxNameW = w - leftSpace - rightSpace - 8f;

        List<String> wrappedName = wrapText(name, maxNameW, 8f);
        List<String> wrappedStatus = wrapText(status, rightSpace - 10f, 7f);
        float nameH = wrappedName.size() * 10f;
        float statH = wrappedStatus.size() * 9f;
        float bottomTagsH = 13f;

        float contentH = Math.max(nameH + bottomTagsH + 4f, statH);
        float h = contentH + padding * 2;

        RoundedRectShader.draw(context, (int) x, (int) y, (int) w, (int) h, 6.0f, withAlpha(PANEL_COLOR, alpha));

        float textY = y + padding;
        for (String line : wrappedName) {
            drawSfuiCustom(context, line, x + leftSpace, textY, 8f, withAlpha(TEXT_COLOR, alpha));
            textY += 10f;
        }

        float tagsY = textY + 1f, tagX = x + leftSpace;
        if (rarity != null && !rarity.isEmpty()) {
            // ФИКС "налезающего текста": в отличие от имени (wrapText) и сервера (clipToWidth
            // ниже), rarity рисовалась на полную естественную ширину без всякого предела —
            // если сервер иногда присылал длинную редкость, плашка вылезала на строку с
            // сервером и на статус-бейдж справа. Ограничиваем её половиной ширины карточки.
            String shownRarity = clipToWidth(rarity, w * 0.5f - 12f, 6.5f);
            float rarW = getSfuiWidth(shownRarity, 6.5f) + 8f;
            RoundedRectShader.draw(context, (int) tagX, (int) tagsY - 2, (int) rarW, 12, 3.0f, withAlpha(accentColor, 0.18f * alpha));
            drawSfuiTiny(context, shownRarity, tagX + 4f, tagsY + 1f, withAlpha(accentColor, alpha));
            tagX += rarW + 5f;
        }
        drawSfuiTiny(context, clipToWidth("📍 " + server, w - (tagX - x) - 4f, 6.5f), tagX, tagsY + 1f, withAlpha(SUBTEXT_COLOR, alpha));

        if (status != null && !status.isEmpty()) {
            boolean isSpawned = status.toLowerCase().contains("призван") || status.toLowerCase().contains("идет") || status.toLowerCase().contains("координаты") || status.toLowerCase().contains("завершено");
            int statusColor = isSpawned ? 0xFFFF4D4D : 0xFF22C55E;
            float maxW = 0;
            for (String s : wrappedStatus) maxW = Math.max(maxW, getSfuiWidth(s, 7f));
            float sW = maxW + 10f, sH = statH + 6f;
            float sX = x + w - sW - 6f, sY = y + padding;
            RoundedRectShader.draw(context, (int) sX, (int) sY, (int) sW, (int) sH, 5.0f, withAlpha(statusColor, 0.15f * alpha));
            float curSy = sY + 3f;
            for (String s : wrappedStatus) {
                drawSfuiTiny(context, s, sX + 5f, curSy, withAlpha(statusColor, alpha));
                curSy += 9f;
            }
        }

        return y + h + 5f;
    }

    // =========================================================================
    //  МИНИ-ПАНЕЛЬ: GUI SETTINGS
    // =========================================================================
    private void drawGuiTab(DrawContext context, float x, float y, float w, float alpha) {
        float cy = y - scrolls[SCROLL_GUI];

        ModernSetting blurToggle = ModernSetting.toggle("UseBlurTheme", "Жидкое стекло (Blur)");
        ModernSetting guiBind = ModernSetting.bind("ClickGuiBind", "Бинд открытия меню");
        float h1 = 6f + getKronexSettingHeight(blurToggle) + getKronexSettingHeight(guiBind);
        RoundedRectShader.draw(context, (int) x, (int) cy, (int) w, (int) h1, 7.0f, withAlpha(PANEL_COLOR, alpha));
        float iy = cy + 3f;
        iy += drawKronexSetting(context, blurToggle, x + 6, iy, w - 12, alpha);
        drawKronexSetting(context, guiBind, x + 6, iy, w - 12, alpha);
        cy += h1 + 6f;

        ModernSetting scrollSound = ModernSetting.toggle("ScrollSound", "Звук при скролле");
        ModernSetting soundMode = ModernSetting.mode("ModuleSoundMode", "Звуки модулей", "Default", "Sound 1", "Sound 2", "Sound 3", "Sound 4");
        float h2 = 6f + getKronexSettingHeight(scrollSound) + getKronexSettingHeight(soundMode);
        RoundedRectShader.draw(context, (int) x, (int) cy, (int) w, (int) h2, 7.0f, withAlpha(PANEL_COLOR, alpha));
        iy = cy + 3f;
        iy += drawKronexSetting(context, scrollSound, x + 6, iy, w - 12, alpha);
        drawKronexSetting(context, soundMode, x + 6, iy, w - 12, alpha);
        cy += h2 + 6f;

        ModernSetting calloutBind = ModernSetting.bind(CALLOUT_BIND_KEY, "Кнопка вызова");
        float bindH = getKronexSettingHeight(calloutBind);
        float h3 = 8f + 9f + 8f + bindH + 3f + 18f + 5f;
        RoundedRectShader.draw(context, (int) x, (int) cy, (int) w, (int) h3, 7.0f, withAlpha(PANEL_COLOR, alpha));
        drawSfuiCustom(context, "Позови на мету", x + 6f, cy + 6f, 8f, withAlpha(TEXT_COLOR, alpha));
        drawSfuiTiny(context, "Уведомит всех в пати", x + 6f, cy + 16f, withAlpha(SUBTEXT_COLOR, alpha));
        float bindY = cy + 25f;
        drawKronexSetting(context, calloutBind, x + 6, bindY, w - 12, alpha);

        float callBtnY = bindY + bindH + 3f;
        int callBtnW = (int) (w - 12f);
        int callBtnX = (int) (x + 6f);
        RoundedRectShader.draw(context, callBtnX, (int) callBtnY, callBtnW, 18, 5f, withAlpha(getThemeColor(), 0.85f * alpha));
        drawSfuiControlCentered(context, "📍 Позвать на мету", x + w / 2f, callBtnY + 5f, withAlpha(0xFFFFFFFF, alpha));
        settingClickBounds.put("callout_now", new int[]{callBtnX, (int) callBtnY, callBtnW, 18});

        float totalH = h1 + 6f + h2 + 6f + h3;
        targetScrolls[SCROLL_GUI] = clamp(targetScrolls[SCROLL_GUI], 0f, Math.max(0f, totalH - 260f));
    }

    // =========================================================================
    //  МИНИ-ПАНЕЛЬ: CONFIGS
    // =========================================================================
    private void drawConfigsTab(DrawContext context, float x, float y, float w, float alpha) {
        float cy = y - scrolls[SCROLL_CONFIGS];

        RoundedRectShader.draw(context, (int) x, (int) cy, (int) w, 20, 6.0f, withAlpha(configInputFocused ? ELEM_COLOR : PANEL_COLOR, alpha));
        String display = configInput.isEmpty() ? "Название конфига..." : configInput;
        drawSfuiTiny(context, display, x + 6f, cy + 6f, withAlpha(configInput.isEmpty() ? SUBTEXT_COLOR : TEXT_COLOR, alpha));
        settingClickBounds.put("cfg_input", new int[]{(int) x - 2, (int) cy - 2, (int) w + 4, 24});
        cy += 25f;

        int bw = (int) ((w - 8f) / 2f);
        settingClickBounds.put("cfg_new", new int[]{(int) x - 2, (int) cy - 2, bw + 4, 22});
        RoundedRectShader.draw(context, (int) x, (int) cy, bw, 18, 6.0f, withAlpha(PANEL_COLOR, alpha));
        drawSfuiControlCentered(context, "Создать", x + bw / 2f, cy + 5f, withAlpha(TEXT_COLOR, alpha));

        settingClickBounds.put("cfg_save", new int[]{(int) (x + bw + 8f) - 2, (int) cy - 2, bw + 4, 22});
        RoundedRectShader.draw(context, (int) (x + bw + 8f), (int) cy, bw, 18, 6.0f, withAlpha(PANEL_COLOR, alpha));
        drawSfuiControlCentered(context, "Сохранить", x + bw + 8f + bw / 2f, cy + 5f, withAlpha(TEXT_COLOR, alpha));
        cy += 26f;

        for (String cfg : ClientData.savedConfigs) {
            boolean selected = cfg.equals(selectedConfig);
            RoundedRectShader.draw(context, (int) x, (int) cy, (int) w, 20, 6.0f, withAlpha(selected ? ELEM_COLOR : PANEL_COLOR, alpha));
            drawSfuiTiny(context, clipToWidth(cfg, w - 44f, 7.5f), x + 6f, cy + 6f, withAlpha(TEXT_COLOR, alpha));

            settingClickBounds.put("cfg_sel:" + cfg, new int[]{(int) x - 2, (int) cy - 2, (int) (w - 40f) + 4, 24});
            settingClickBounds.put("cfg_load:" + cfg, new int[]{(int) (x + w - 34f) - 3, (int) cy + 3 - 3, 14 + 6, 14 + 6});
            drawSfuiTiny(context, "L", x + w - 30f, cy + 7f, withAlpha(getThemeColor(), alpha));
            settingClickBounds.put("cfg_del:" + cfg, new int[]{(int) (x + w - 18f) - 3, (int) cy + 3 - 3, 14 + 6, 14 + 6});
            drawSfuiTiny(context, "X", x + w - 14f, cy + 7f, withAlpha(0xFFFF4D4D, alpha));
            cy += 24f;
        }
        targetScrolls[SCROLL_CONFIGS] = clamp(targetScrolls[SCROLL_CONFIGS], 0f, Math.max(0f, 25f + 26f + ClientData.savedConfigs.size() * 24f - 180f));
    }

    // =========================================================================
    //  ОКНО БИНДА — маленькое, рядом с модулем/настройкой, не на весь экран
    // =========================================================================
    private void drawBindWindow(DrawContext context, float anim) {
        boolean useBlur = ClientData.moduleStates.getOrDefault("UseBlurTheme", true);

        float bw = 132f, bh = 70f;
        float bx = clamp(bindPopupX - bw / 2f, 6f, width - bw - 6f);
        float by = clamp(bindPopupY - bh - 16f, 6f, height - bh - 6f);
        float pivotX = bx + bw / 2f, pivotY = by + bh / 2f;
        float sc = 0.85f + 0.15f * smoothT(anim);

        context.getMatrices().push();
        context.getMatrices().translate(pivotX, pivotY, 0);
        context.getMatrices().scale(sc, sc, 1f);
        context.getMatrices().translate(-pivotX, -pivotY, 0);

        drawTowerBackground(context, bx, by, bw, bh, anim, useBlur);

        String shownName = bindingTarget == null ? "" : bindingTarget;
        drawMarqueeText(context, "bindpop_title", shownName, bx + 8f, by + 6f, bw - 16f, 8f, withAlpha(TEXT_COLOR, anim), true);

        float promptY;
        if (isModuleBind) {
            // Удержание / 1 клик — только для биндов модулей, у ClickGuiBind/CalloutBind/Bind_*
            // такого понятия нет (BindManager.handleInputEvents всегда триггерит их по фронту).
            boolean holdMode = bindingTarget != null && ClientData.moduleStates.getOrDefault("HoldMode_" + bindingTarget, false);
            float segY = by + 20f, segH = 15f, segGap = 3f, segW = (bw - 16f - segGap) / 2f;

            int holdBg = holdMode ? getThemeColor() : ELEM_COLOR;
            RoundedRectShader.draw(context, (int) (bx + 8f), (int) segY, (int) segW, (int) segH, 5f, withAlpha(holdBg, anim));
            drawSfuiControlCentered(context, "Удержание", bx + 8f + segW / 2f, segY + 4f, withAlpha(0xFFFFFFFF, anim));
            settingClickBounds.put("bind_hold_mode", new int[]{(int) (bx + 8f), (int) segY, (int) segW, (int) segH});

            int clickBg = !holdMode ? getThemeColor() : ELEM_COLOR;
            RoundedRectShader.draw(context, (int) (bx + 8f + segW + segGap), (int) segY, (int) segW, (int) segH, 5f, withAlpha(clickBg, anim));
            drawSfuiControlCentered(context, "1 клик", bx + 8f + segW + segGap + segW / 2f, segY + 4f, withAlpha(0xFFFFFFFF, anim));
            settingClickBounds.put("bind_click_mode", new int[]{(int) (bx + 8f + segW + segGap), (int) segY, (int) segW, (int) segH});
            promptY = segY + segH + 8f;
        } else {
            settingClickBounds.remove("bind_hold_mode");
            settingClickBounds.remove("bind_click_mode");
            promptY = by + 26f;
        }

        float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 150.0);
        drawSfuiControlCentered(context, "Нажми клавишу...", bx + bw / 2f, promptY, withAlpha(getThemeColor(), anim * pulse));
        drawSfuiTiny(context, "ESC отмена · DEL снять", bx + bw / 2f - getSfuiWidth("ESC отмена · DEL снять", 6f) / 2f, by + bh - 10f, withAlpha(0xFF8A8A96, anim));

        context.getMatrices().pop();
    }

    // =========================================================================
    //  ТЕМЫ И БИНДЫ — ВСПОМОГАТЕЛЬНЫЕ
    // =========================================================================
    private boolean isPresetActive(String preset) {
        float[] c1 = getPaletteHsv("Theme Color 1");
        float[] c2 = getPaletteHsv("Theme Color 2");
        float[] p1 = ClientData.colorSettings.getOrDefault("ThemePreset_" + preset + "_1", new float[]{-1f, -1f, -1f});
        float[] p2 = ClientData.colorSettings.getOrDefault("ThemePreset_" + preset + "_2", new float[]{-1f, -1f, -1f});
        return hsvClose(c1, p1) && hsvClose(c2, p2);
    }

    private boolean hsvClose(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) < 0.01f && Math.abs(a[1] - b[1]) < 0.01f && Math.abs(a[2] - b[2]) < 0.01f;
    }

    /** "Мазки краски" — два перекрывающихся кружка вместо квадрата с жёстким градиентом. */
    private void drawPresetPaintBlob(DrawContext context, String preset, float x, float y, float size, float alpha) {
        float[] hsv1 = ClientData.colorSettings.getOrDefault("ThemePreset_" + preset + "_1", new float[]{0f, 1f, 1f});
        float[] hsv2 = ClientData.colorSettings.getOrDefault("ThemePreset_" + preset + "_2", hsv1);
        int rgb1 = 0xFF000000 | (Color.HSBtoRGB(hsv1[0], hsv1[1], hsv1[2]) & 0xFFFFFF);
        int rgb2 = 0xFF000000 | (Color.HSBtoRGB(hsv2[0], hsv2[1], hsv2[2]) & 0xFFFFFF);

        float blobSize = size * 0.74f;
        float yOff = (size - blobSize) / 2f;
        RoundedRectShader.draw(context, (int) x, (int) (y + yOff), (int) blobSize, (int) blobSize, blobSize / 2f, withAlpha(rgb1, alpha));
        RoundedRectShader.draw(context, (int) (x + size - blobSize), (int) (y + yOff), (int) blobSize, (int) blobSize, blobSize / 2f, withAlpha(rgb2, alpha * 0.9f));
    }

    private void applyTheme(String n) {
        float[] c1 = ClientData.colorSettings.getOrDefault("ThemePreset_" + n + "_1", new float[]{0f, 1f, 1f});
        float[] c2 = ClientData.colorSettings.getOrDefault("ThemePreset_" + n + "_2", c1);
        ClientData.colorSettings.put("Theme Color 1", new float[]{c1[0], c1[1], c1[2]});
        ClientData.colorSettings.put("Theme Color 2", new float[]{c2[0], c2[1], c2[2]});
    }

    private void ensureThemeDefaults() {
        ensureThemeColor("ThemePreset_Прост белый_1", 0xFFF5F5F5);
        ensureThemeColor("ThemePreset_Прост белый_2", 0xFFD8D8D8);
        ensureThemeColor("ThemePreset_Ягодный пунш_1", 0xFFFF2D95);
        ensureThemeColor("ThemePreset_Ягодный пунш_2", 0xFFC724B1);
        ensureThemeColor("ThemePreset_Осенний лес_1", 0xFF4CAF50);
        ensureThemeColor("ThemePreset_Осенний лес_2", 0xFFFF9800);
        ensureThemeColor("ThemePreset_Безупречный_1", 0xFFFFC46B);
        ensureThemeColor("ThemePreset_Безупречный_2", 0xFFFFA726);
        ensureThemeColor("ThemePreset_Сладкие мечты_1", 0xFFFF4569);
        ensureThemeColor("ThemePreset_Сладкие мечты_2", 0xFF9C27B0);
        ensureThemeColor("ThemePreset_Космос_1", 0xFF2196F3);
        ensureThemeColor("ThemePreset_Космос_2", 0xFF7C4DFF);
        ensureThemeColor("ThemePreset_Закат_1", 0xFFFF7043);
        ensureThemeColor("ThemePreset_Закат_2", 0xFFFF5252);
        if (!ClientData.colorSettings.containsKey("Theme Color 1")) ensureThemeColor("Theme Color 1", 0xFFFF2D95);
        if (!ClientData.colorSettings.containsKey("Theme Color 2")) ensureThemeColor("Theme Color 2", 0xFFC724B1);
    }

    private void ensureThemeColor(String key, int rgb) {
        if (!ClientData.colorSettings.containsKey(key)) {
            float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
            ClientData.colorSettings.put(key, new float[]{hsb[0], hsb[1], hsb[2]});
        }
    }

    // ClickGuiBind/CalloutBind/PartyWaypointBind — единственные три ключа, которые
    // BindManager.handleInputEvents читает НАПРЯМУЮ из ClientData.numSettings в обход
    // getStoredBindValue (см. пункты 1-3 там же) — держим их тут так же, отдельно.
    private static final Set<String> RAW_NUM_BIND_KEYS = Set.of("ClickGuiBind", "CalloutBind", "PartyWaypointBind");

    private int getStoredBindValue(String key) {
        if (key.equals("ClickGuiBind")) {
            return ClientData.numSettings.getOrDefault(key, (float) GLFW.GLFW_KEY_RIGHT_SHIFT).intValue();
        }
        if (RAW_NUM_BIND_KEYS.contains(key)) {
            return ClientData.numSettings.getOrDefault(key, (float) GLFW.GLFW_KEY_UNKNOWN).intValue();
        }
        return BindManager.getStoredBindValue(key);
    }

    private void setStoredBindValue(String key, int bind) {
        if (RAW_NUM_BIND_KEYS.contains(key)) {
            ClientData.numSettings.put(key, (float) bind);
            return;
        }
        BindManager.setStoredBindValue(key, bind);
    }

    private String formatBindName(int b) {
        return BindManager.formatBindName(b);
    }

    // =========================================================================
    //  ОБЩИЕ УТИЛИТЫ ОТРИСОВКИ/ТЕКСТА
    // =========================================================================
    private void drawSfui(DrawContext c, String t, float x, float y, int clr) {
        if (t != null) SFUI.draw(c.getMatrices(), t, x, y, 8.5f, clr);
    }

    private void drawSfuiCustom(DrawContext c, String t, float x, float y, float size, int clr) {
        if (t != null) SFUI.draw(c.getMatrices(), t, x, y, size, clr);
    }

    private void drawSfuiTiny(DrawContext c, String t, float x, float y, int clr) {
        if (t != null) SFUI.draw(c.getMatrices(), t, x, y, 6.5f, clr);
    }

    private void drawSfuiControlCentered(DrawContext c, String t, float cx, float y, int clr) {
        if (t != null) SFUI.draw(c.getMatrices(), t, cx - getSfuiWidth(t, 8.5f) / 2.0f, y, 8.5f, clr);
    }

    private int getSfuiWidth(String t, float s) {
        return (int) SFUI.getWidth(t, s);
    }

    private String clipToWidth(String text, float maxWidth, float size) {
        if (text == null) return "";
        if (getSfuiWidth(text, size) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (getSfuiWidth(sb.toString() + text.charAt(i) + "…", size) > maxWidth) break;
            sb.append(text.charAt(i));
        }
        return sb + "…";
    }

    private boolean inside(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int withAlpha(int c, float a) {
        if (Float.isNaN(a) || a < 0.0f) a = 0.0f;
        else if (a > 1.0f) a = 1.0f;
        return (((int) (((c >> 24) & 255) * a)) << 24) | (c & 0x00FFFFFF);
    }

    // =========================================================================
    //  MARQUEE-ТЕКСТ: если строка не помещается — при наведении едет туда-обратно,
    //  на краю стоит секунду и разворачивается. Не наведено — стоит в начале.
    // =========================================================================
    private final Map<String, float[]> marqueeState = new HashMap<>(); // [offset, direction(+-1), holdUntilMillis(0=не ждём)]

    private float updateMarquee(String key, float overflow, boolean hovered) {
        if (overflow <= 0.5f || !hovered) {
            marqueeState.remove(key);
            return 0f;
        }
        float[] st = marqueeState.computeIfAbsent(key, k -> new float[]{0f, 1f, 0f});
        long now = System.currentTimeMillis();
        if (st[2] > 0f) {
            if (now >= st[2]) {
                st[1] = -st[1];
                st[2] = 0f;
            }
            return clamp(st[0], 0f, overflow);
        }
        float speed = 0.10f;
        st[0] += st[1] * speed;
        if (st[1] > 0 && st[0] >= overflow) {
            st[0] = overflow;
            st[2] = now + 1000L;
        } else if (st[1] < 0 && st[0] <= 0f) {
            st[0] = 0f;
            st[2] = now + 1000L;
        }
        return clamp(st[0], 0f, overflow);
    }

    /** Рисует текст с обрезкой по ширине; если не влезает и наведено — едет marquee'ем. */
    private void drawMarqueeText(DrawContext context, String key, String text, float x, float y, float maxW, float size, int color, boolean hovered) {
        if (text == null) return;
        float textW = getSfuiWidth(text, size);
        float overflow = textW - maxW;
        if (overflow <= 0.5f || maxW <= 4f) {
            drawSfuiCustom(context, text, x, y, size, color);
            return;
        }
        float offset = updateMarquee(key, overflow, hovered);
        context.enableScissor((int) x, (int) (y - 2f), (int) (x + maxW), (int) (y + size + 3f));
        drawSfuiCustom(context, text, x - offset, y, size, color);
        context.disableScissor();
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int blendColors(int c1, int c2, float t) {
        t = clamp(sanitize(t), 0.0f, 1.0f);
        float it = 1f - t;
        return (((int) (((c1 >> 24) & 255) * it + ((c2 >> 24) & 255) * t)) << 24)
                | (((int) (((c1 >> 16) & 255) * it + ((c2 >> 16) & 255) * t)) << 16)
                | (((int) (((c1 >> 8) & 255) * it + ((c2 >> 8) & 255) * t)) << 8)
                | ((int) ((c1 & 255) * it + (c2 & 255) * t));
    }

    private List<String> wrapText(String text, float maxWidth, float size) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder currentLine = new StringBuilder();
        for (String word : text.split(" ")) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (getSfuiWidth(testLine, size) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    private void drawScaledItem(DrawContext context, Item item, float x, float y, float scale) {
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawItem(item.getDefaultStack(), Math.round(x / scale), Math.round(y / scale));
        context.getMatrices().pop();
    }

    // =========================================================================
    //  HOLYWORLD JOINER — автонавигация по меню телепорта (без изменений)
    // =========================================================================
    public static class HolyWorldJoiner {
        public static boolean active = false;
        public static int targetAnarchy = -1;
        public static int waitTicks = 0;
        public static int state = 0;

        public static void start(int anNum) {
            targetAnarchy = anNum;
            active = true;
            state = 0;
            waitTicks = 5;
            net.minecraft.client.MinecraftClient.getInstance().player.networkHandler.sendCommand("hub");
        }

        public static void tick() {
            if (!active) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null) { active = false; return; }
            if (waitTicks > 0) { waitTicks--; return; }

            if (state == 0) {
                mc.player.getInventory().selectedSlot = 0;
                mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
                state = 1;
                waitTicks = 15;
            } else if (state == 1) {
                if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen) {
                    String title = screen.getTitle().getString();
                    if (title.contains("Выберите режим")) {
                        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 12, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                        state = 2;
                        waitTicks = 15;
                    }
                }
            } else if (state == 2) {
                if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen) {
                    String title = screen.getTitle().getString();
                    if (title.contains("Лайт") || title.contains("анархи")) {
                        int categorySlot = 0;
                        if (targetAnarchy >= 16 && targetAnarchy <= 31) categorySlot = 1;
                        else if (targetAnarchy >= 32 && targetAnarchy <= 47) categorySlot = 2;
                        else if (targetAnarchy >= 48) categorySlot = 3;
                        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, categorySlot, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                        state = 3;
                        waitTicks = 12;
                    }
                }
            } else if (state == 3) {
                if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen) {
                    int baseNum = 1;
                    if (targetAnarchy >= 16 && targetAnarchy <= 31) baseNum = 16;
                    else if (targetAnarchy >= 32 && targetAnarchy <= 47) baseNum = 32;
                    else if (targetAnarchy >= 48) baseNum = 48;
                    int clickSlotIndex = 18 + (targetAnarchy - baseNum);
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, clickSlotIndex, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    active = false;
                }
            }
        }
    }

    // =========================================================================
    //  МЫШЬ / КЛАВИАТУРА
    // =========================================================================
    private boolean isClickInsideOpenModeDropdown(double mx, double my) {
        if (openModeDropdown == null) return false;
        int[] b1 = settingClickBounds.get("mode_open:" + openModeDropdown);
        if (b1 != null && inside(mx, my, b1[0], b1[1], b1[2], b1[3])) return true;
        for (Map.Entry<String, int[]> e : settingClickBounds.entrySet()) {
            if (e.getKey().startsWith("mode_pick:" + openModeDropdown + ":")) {
                int[] b = e.getValue();
                if (inside(mx, my, b[0], b[1], b[2], b[3])) return true;
            }
        }
        return false;
    }

    private void updateDraggedSlider(double mouseX) {
        if (draggingSettingSlider == null) return;
        float[] b = sliderBounds.get(draggingSettingSlider);
        if (b == null) return;
        float t = clamp((float) ((mouseX - b[0]) / b[2]), 0.0f, 1.0f);
        ClientData.numSettings.put(draggingSettingSlider, b[4] + (b[5] - b[4]) * t);
    }

    private void updateDraggedPad(double mx, double my) {
        if (draggingPadSetting == null) return;
        float[] b = padBounds.get(draggingPadSetting);
        if (b == null) return;
        float tx = clamp((float) ((mx - b[0]) / b[2]), 0.0f, 1.0f);
        float ty = 1.0f - clamp((float) ((my - b[1]) / b[3]), 0.0f, 1.0f);
        String keyY = padKeyYMap.getOrDefault(draggingPadSetting, draggingPadSetting + "Y");
        ClientData.numSettings.put(draggingPadSetting, b[4] + (b[5] - b[4]) * tx);
        ClientData.numSettings.put(keyY, b[6] + (b[7] - b[6]) * ty);
    }

    private void updatePaletteDragMode(double mx, double my) {
        float[] g = computePaletteGeom(paletteAnimKey);
        if (g == null) return;
        float hueY = g[4], hueH = g[5];
        paletteDragMode = (my >= hueY - 4 && my <= hueY + hueH + 4) ? 2 : 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Бинды в этом клиенте — только с клавиатуры (см. keyPressed). Пока открыто окно
        // "нажми клавишу", клики мышью просто гасим, чтобы не тыкать в гуи под ним.
        if (bindingTarget != null) return true;

        if (openModeDropdown != null && !isClickInsideOpenModeDropdown(mouseX, mouseY)) {
            openModeDropdown = null;
        }

        // 0) Иконки расширенного острова (Party/Events/GUI/Configs) — работают, пока это окно активно,
        //    т.к. именно ModernClickGui сейчас ловит все клики мыши.
        Map<String, float[]> islandIcons = DynamicIslandRenderer.GUI_ICON_BOUNDS;
        if (islandIcons != null) {
            for (Map.Entry<String, float[]> e : islandIcons.entrySet()) {
                float[] b = e.getValue();
                if (inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                    activeIslandPanel = e.getKey().equals(activeIslandPanel) ? null : e.getKey();
                    playSound("click");
                    return true;
                }
            }
        }

        for (Map.Entry<String, int[]> e : new HashMap<>(settingClickBounds).entrySet()) {
            int[] b = e.getValue();
            if (!inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) continue;
            String id = e.getKey();

            if (id.startsWith("palette:")) {
                String pid = id.substring("palette:".length());
                float[] hsv = getPaletteHsv(pid);
                paletteLive.h = hsv[0];
                paletteLive.s = hsv[1];
                paletteLive.v = hsv[2];
                paletteAnimKey = pid;
                updatePaletteDragMode(mouseX, mouseY);
                updatePaletteFromMouse(mouseX, mouseY);
                return true;
            }

            if (id.equals("callout_now")) {
                com.lexoravisauls.client.utils.CalloutManager.sendCallout();
                return true;
            }
            if (id.equals("party_create")) {
                LexoraPartyClient.createParty(net.minecraft.client.MinecraftClient.getInstance());
                return true;
            }
            if (id.equals("party_join") && partyCodeInput.length() == 10) {
                LexoraPartyClient.requestJoin(net.minecraft.client.MinecraftClient.getInstance(), partyCodeInput);
                partyCodeInput = "";
                partyCodeInputFocused = false;
                return true;
            }
            if (id.equals("party_code_input")) {
                partyCodeInputFocused = true;
                return true;
            }
            if (id.equals("party_cancel_wait")) {
                LexoraPartyManager.waitingForResponse = false;
                LexoraPartyManager.waitingCode = null;
                return true;
            }
            if (id.equals("party_leave")) {
                LexoraPartyClient.leaveParty(net.minecraft.client.MinecraftClient.getInstance());
                return true;
            }

            if (id.startsWith("toggle:")) {
                String key = id.substring("toggle:".length());
                boolean state = !ClientData.moduleStates.getOrDefault(key, false);
                ClientData.moduleStates.put(key, state);
                ConfigManager.saveConfig();
                playModuleToggleSound(state);
                return true;
            }
            if (id.startsWith("mode_open:")) {
                String key = id.substring("mode_open:".length());
                openModeDropdown = key.equals(openModeDropdown) ? null : key;
                return true;
            }
            if (id.startsWith("mode_pick:")) {
                String rest = id.substring("mode_pick:".length());
                int sep = rest.indexOf(':');
                ClientData.modeSettings.put(rest.substring(0, sep), rest.substring(sep + 1));
                openModeDropdown = null;
                ConfigManager.saveConfig();
                return true;
            }
            if (id.startsWith("bind_open:")) {
                bindingTarget = id.substring("bind_open:".length());
                isModuleBind = false;
                bindAnim = 0f;
                bindPopupX = (float) mouseX;
                bindPopupY = (float) mouseY;
                return true;
            }
            if (id.equals("bind_hold_mode") && bindingTarget != null) {
                ClientData.moduleStates.put("HoldMode_" + bindingTarget, true);
                ConfigManager.saveConfig();
                return true;
            }
            if (id.equals("bind_click_mode") && bindingTarget != null) {
                ClientData.moduleStates.put("HoldMode_" + bindingTarget, false);
                ConfigManager.saveConfig();
                return true;
            }
            if (id.startsWith("theme:")) {
                applyTheme(id.substring(6));
                ConfigManager.saveConfig();
                return true;
            }

            if (id.startsWith("ih_toggle:")) {
                String name = id.substring("ih_toggle:".length());
                for (Map.Entry<Item, ItemHighlighter.ItemConfig> entry : ItemHighlighter.ITEM_CONFIGS.entrySet()) {
                    String itemName = ItemHighlighter.ITEM_NAMES.getOrDefault(entry.getKey(), entry.getKey().getName().getString());
                    if (itemName.equals(name)) {
                        entry.getValue().enabled = !entry.getValue().enabled;
                        ConfigManager.saveConfig();
                        playModuleToggleSound(entry.getValue().enabled);
                        return true;
                    }
                }
            }

            if (id.equals("gps_in_Имя")) { focusedGpsInput = 1; return true; }
            if (id.equals("gps_in_X")) { focusedGpsInput = 2; return true; }
            if (id.equals("gps_in_Z")) { focusedGpsInput = 3; return true; }
            if (id.equals("gps_add")) {
                try {
                    double pX = Double.parseDouble(gpsInputX);
                    double pZ = Double.parseDouble(gpsInputZ);
                    String pName = gpsInputName.isEmpty() ? "Метка" : gpsInputName;
                    double pY = net.minecraft.client.MinecraftClient.getInstance().player != null
                            ? net.minecraft.client.MinecraftClient.getInstance().player.getY() : 64.0;
                    com.lexoravisauls.client.utils.GPS.removeWaypoint(pName);
                    com.lexoravisauls.client.utils.GPS.addWaypoint(pName, pX, pY, pZ);
                    gpsInputName = "";
                    gpsInputX = "";
                    gpsInputZ = "";
                    focusedGpsInput = 0;
                } catch (Exception ignored) {
                }
                return true;
            }
            if (id.startsWith("gps_del:")) {
                com.lexoravisauls.client.utils.GPS.removeWaypoint(id.substring("gps_del:".length()));
                return true;
            }

            if (id.equals("cfg_input")) {
                configInputFocused = true;
                return true;
            }
            if (id.equals("cfg_new")) {
                if (!configInput.isEmpty()) {
                    ConfigManager.saveConfig(configInput);
                    selectedConfig = configInput;
                    ConfigManager.updateConfigList();
                }
                return true;
            }
            if (id.equals("cfg_save")) {
                String n = selectedConfig != null ? selectedConfig : configInput;
                if (!n.isEmpty()) {
                    ConfigManager.saveConfig(n);
                    ConfigManager.updateConfigList();
                }
                return true;
            }
            if (id.startsWith("cfg_sel:")) {
                selectedConfig = id.substring("cfg_sel:".length());
                configInput = selectedConfig;
                return true;
            }
            if (id.startsWith("cfg_load:")) {
                ConfigManager.loadConfig(id.substring("cfg_load:".length()));
                return true;
            }
            if (id.startsWith("cfg_del:")) {
                ConfigManager.deleteConfig(id.substring("cfg_del:".length()));
                selectedConfig = null;
                ConfigManager.updateConfigList();
                return true;
            }

            if (id.startsWith("tower_back:")) {
                String towerId = id.substring("tower_back:".length());
                if (towerId.equals("Themes")) {
                    activeIslandPanel = null;
                } else {
                    towerOpenModule.remove(towerId);
                }
                return true;
            }
            if (id.startsWith("modulerow:")) {
                String rest = id.substring("modulerow:".length());
                int sep = rest.indexOf(':');
                String towerId = rest.substring(0, sep);
                String mod = rest.substring(sep + 1);
                if (button == 0) {
                    boolean state = !ClientData.moduleStates.getOrDefault(mod, false);
                    ClientData.moduleStates.put(mod, state);
                    playModuleToggleSound(state);
                    ConfigManager.saveConfig();
                } else if (button == 1) {
                    if (moduleHasSettings(mod)) {
                        towerOpenModule.put(towerId, mod);
                        towerSettingsScroll.put(towerId, 0f);
                        openModeDropdown = null;
                    } else {
                        shakeStartTime.put(towerId + ":" + mod, System.currentTimeMillis());
                    }
                } else if (button == 2) {
                    bindingTarget = mod;
                    isModuleBind = true;
                    bindAnim = 0f;
                    bindPopupX = (float) mouseX;
                    bindPopupY = (float) mouseY;
                    ClientData.moduleStates.put("HoldMode_" + mod, BindManager.getModuleBindMode(mod) == BindManager.BindMode.HOLD);
                }
                return true;
            }
        }

        // Если дошли сюда, ни одно из трёх gps-полей не было нажато (иначе метод уже вернул true выше) — снимаем фокус.
        focusedGpsInput = 0;

        for (Map.Entry<String, float[]> e : sliderBounds.entrySet()) {
            if (inside(mouseX, mouseY, e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3])) {
                draggingSettingSlider = e.getKey();
                updateDraggedSlider(mouseX);
                return true;
            }
        }
        for (Map.Entry<String, float[]> e : padBounds.entrySet()) {
            float[] b = e.getValue();
            if (inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                draggingPadSetting = e.getKey();
                updateDraggedPad(mouseX, mouseY);
                return true;
            }
        }

        configInputFocused = false;
        partyCodeInputFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (paletteAnimKey != null) {
            updatePaletteDragMode(mouseX, mouseY);
            updatePaletteFromMouse(mouseX, mouseY);
            return true;
        }
        if (draggingSettingSlider != null) {
            updateDraggedSlider(mouseX);
            return true;
        }
        if (draggingPadSetting != null) {
            updateDraggedPad(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (paletteAnimKey != null) {
            paletteAnimKey = null;
            paletteDragMode = 0;
            ConfigManager.saveConfig();
            return true;
        }
        if (draggingSettingSlider != null || draggingPadSetting != null) {
            draggingSettingSlider = null;
            draggingPadSetting = null;
            ConfigManager.saveConfig();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (bindingTarget != null) return true;

        float guiW = GUI_W, guiH = GUI_H;
        float gx = (width - guiW) / 2f, gy = (height - guiH) / 2f;
        float hudX = gx + (TOWER_W + TOWER_GAP) * 2f;
        float themesX = gx + (TOWER_W + TOWER_GAP) * 3f;

        // ФИКС: если в наведённой башне открыт модуль (towerOpenModule) — колесо должно
        // крутить панель его настроек (towerSettingsScroll), а не список модулей. Раньше
        // сюда вообще не заходили: mouseScrolled знал только про targetScrolls (список
        // модулей), а towerSettingsScroll хоть и honestly читается и клэмпится в рендере
        // (drawTowerSettingsSlide), выставлять его значение было некому — отсюда "нельзя
        // пролистать настройки, если тумблеров много".
        String hoveredTower = null;
        if (inside(mouseX, mouseY, gx, gy, TOWER_W, guiH)) hoveredTower = "Utils";
        else if (inside(mouseX, mouseY, gx + TOWER_W + TOWER_GAP, gy, TOWER_W, guiH)) hoveredTower = "Visual";
        else if (inside(mouseX, mouseY, hudX, gy, TOWER_W, guiH)) hoveredTower = "HUD";

        if (hoveredTower != null && towerOpenModule.containsKey(hoveredTower)) {
            String openModule = towerOpenModule.get(hoveredTower);
            // Та же цепочка, что и в drawTower()->contentH и drawTowerSettingsSlide()->sh
            // (contentH = guiH - HEADER_H - 16, а внутри слайда из неё ещё вычитается 26 под
            // заголовок с кнопкой "назад") — считаем идентично, чтобы скролл и рендер не
            // могли разъехаться, как уже было с партиклами.
            float visibleH = GUI_H - HEADER_H - 16f - 26f;
            float maxScroll = Math.max(0f, getExpandedTargetHeight(openModule) - visibleH);
            float scroll = clamp(towerSettingsScroll.getOrDefault(hoveredTower, 0f) + (float) (-verticalAmount * 22.0), 0f, maxScroll);
            towerSettingsScroll.put(hoveredTower, scroll);
            return true;
        }

        int scrollIdx = -1;
        if ("Utils".equals(hoveredTower)) {
            scrollIdx = SCROLL_UTILS;
        } else if ("Visual".equals(hoveredTower)) {
            scrollIdx = SCROLL_VISUAL;
        } else if ("HUD".equals(hoveredTower)) {
            scrollIdx = SCROLL_HUD;
        } else if (inside(mouseX, mouseY, themesX, gy, TOWER_W, guiH) && activeIslandPanel != null) {
            scrollIdx = switch (activeIslandPanel) {
                case "Party" -> SCROLL_PARTY;
                case "Events" -> SCROLL_EVENTS;
                case "GUI" -> SCROLL_GUI;
                case "Configs" -> SCROLL_CONFIGS;
                default -> -1;
            };
        }

        if (scrollIdx == -1) return true;

        float oldTarget = targetScrolls[scrollIdx];
        targetScrolls[scrollIdx] = clamp(targetScrolls[scrollIdx] + (float) (-verticalAmount * 22.0), 0.0f, 2500f);
        if (oldTarget != targetScrolls[scrollIdx] && ClientData.moduleStates.getOrDefault("ScrollSound", false)) {
            playSound("scroll");
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (bindingTarget != null) return true;
        if (configInputFocused) {
            if (configInput.length() < 20) configInput += chr;
            return true;
        }
        if (partyCodeInputFocused && Character.isDigit(chr) && partyCodeInput.length() < 10) {
            partyCodeInput += chr;
            return true;
        }
        if (focusedGpsInput == 1) {
            if (gpsInputName.length() < 18) gpsInputName += chr;
            return true;
        }
        if (focusedGpsInput == 2 && (Character.isDigit(chr) || chr == '-')) {
            if (gpsInputX.length() < 8) gpsInputX += chr;
            return true;
        }
        if (focusedGpsInput == 3 && (Character.isDigit(chr) || chr == '-')) {
            if (gpsInputZ.length() < 8) gpsInputZ += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingTarget != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                bindingTarget = null;
            } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (isModuleBind) {
                    BindManager.setModuleBind(bindingTarget, GLFW.GLFW_KEY_UNKNOWN, BindManager.getModuleBindMode(bindingTarget));
                } else {
                    setStoredBindValue(bindingTarget, GLFW.GLFW_KEY_UNKNOWN);
                }
                bindingTarget = null;
            } else {
                // Снимаем этот физический код со всего, на чём он мог висеть раньше (другой модуль,
                // команда, экшн-бинд) — одна клавиша не должна тихо триггерить сразу два действия.
                BindManager.clearAnyBindOnKeyEverywhere(keyCode, bindingTarget);
                if (isModuleBind) {
                    boolean holdMode = ClientData.moduleStates.getOrDefault("HoldMode_" + bindingTarget, false);
                    BindManager.setModuleBind(bindingTarget, keyCode, holdMode ? BindManager.BindMode.HOLD : BindManager.BindMode.TOGGLE);
                } else {
                    setStoredBindValue(bindingTarget, keyCode);
                }
                bindingTarget = null;
            }
            ConfigManager.saveConfig();
            return true;
        }

        if (configInputFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !configInput.isEmpty()) {
            configInput = configInput.substring(0, configInput.length() - 1);
            return true;
        }
        if (partyCodeInputFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE && !partyCodeInput.isEmpty()) {
            partyCodeInput = partyCodeInput.substring(0, partyCodeInput.length() - 1);
            return true;
        }
        if (partyCodeInputFocused && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            partyCodeInputFocused = false;
            return true;
        }
        if (focusedGpsInput == 1 && keyCode == GLFW.GLFW_KEY_BACKSPACE && !gpsInputName.isEmpty()) {
            gpsInputName = gpsInputName.substring(0, gpsInputName.length() - 1);
            return true;
        }
        if (focusedGpsInput == 2 && keyCode == GLFW.GLFW_KEY_BACKSPACE && !gpsInputX.isEmpty()) {
            gpsInputX = gpsInputX.substring(0, gpsInputX.length() - 1);
            return true;
        }
        if (focusedGpsInput == 3 && keyCode == GLFW.GLFW_KEY_BACKSPACE && !gpsInputZ.isEmpty()) {
            gpsInputZ = gpsInputZ.substring(0, gpsInputZ.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closing = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}