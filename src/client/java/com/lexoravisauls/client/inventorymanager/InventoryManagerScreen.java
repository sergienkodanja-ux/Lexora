package com.lexoravisauls.client.inventorymanager;

import com.lexoravisauls.client.events.HudThemeHelper;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.LexoraIcons;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.lexoravisauls.client.gui.modern.ModernGuiIcons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Современный и красивый менеджер раскладок инвентаря.
 * Полностью согласован со стилем ModernClickGui, использует MSDF-шрифты и иконки,
 * поддерживает тёмную/светлую тему, плавные анимации и интерактивную сетку слотов.
 */
public class InventoryManagerScreen extends Screen {

    private static final int GUI_W = 660;
    private static final int GUI_H = 380;
    private static final int HEADER_H = 32;

    private static final int CELL_SIZE = 22;
    private static final int CELL_GAP = 3;
    private static final int CELL_STEP = CELL_SIZE + CELL_GAP; // 25px

    private static String savedSelectedName = null;

    private InventoryLoadout selectedLoadout;
    private Map<Integer, LoadoutSlotData> selectedBySlot = new HashMap<>();

    private float openAnim = 0.0f;
    private boolean closing = false;

    private float listScroll = 0f;
    private float targetListScroll = 0f;

    private String nameInput = "";
    private boolean nameFocused = false;

    private final Map<String, float[]> cardHoverAnims = new HashMap<>();
    private final Map<String, Float> btnHoverAnims = new HashMap<>();

    private final Map<String, int[]> clickBounds = new HashMap<>();

    public InventoryManagerScreen() {
        super(Text.literal("Менеджер инвентарей"));
        LoadoutManager.ensureLoaded();
        if (savedSelectedName != null) {
            LoadoutManager.findByName(savedSelectedName).ifPresent(this::selectLoadout);
        }
        if (selectedLoadout == null && !LoadoutManager.loadouts.isEmpty()) {
            selectLoadout(LoadoutManager.loadouts.get(0));
        }
    }

    private static MsdfFont font() {
        return ModernClickGui.SFUI;
    }

    private boolean isDark() {
        return HudThemeHelper.isDark();
    }

    @Override
    protected void init() {
        super.init();
        openAnim = 0.0f;
        closing = false;
    }

    private void backToGame() {
        closing = true;
    }

    @Override
    public void close() {
        if (selectedLoadout != null) {
            savedSelectedName = selectedLoadout.name;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.setScreen(new InventoryScreen(client.player));
        } else {
            super.close();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    private void selectLoadout(InventoryLoadout l) {
        selectedLoadout = l;
        selectedBySlot = new HashMap<>();
        if (l != null) {
            savedSelectedName = l.name;
            for (LoadoutSlotData d : l.slots) {
                selectedBySlot.put(d.handlerSlotId, d);
            }
        }
    }

    private void saveCurrent() {
        String name = nameInput.trim();
        if (name.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        InventoryLoadout loadout = LoadoutManager.captureCurrent(client.player.playerScreenHandler, name);
        LoadoutManager.upsert(loadout);
        selectLoadout(loadout);
        nameInput = "";
        playClickSound();
    }

    private void startLoad(InventoryLoadout loadout) {
        if (loadout == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        LoadoutRestoreExecutor.startRestore(client.player.playerScreenHandler, loadout);
        playClickSound();
        client.setScreen(new InventoryScreen(client.player));
    }

    private void deleteLoadout(InventoryLoadout loadout) {
        if (loadout == null) return;
        LoadoutManager.delete(loadout);
        playClickSound();
        if (selectedLoadout == loadout) {
            selectLoadout(LoadoutManager.loadouts.isEmpty() ? null : LoadoutManager.loadouts.get(0));
        }
    }

    private void refreshGhosts() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        LoadoutRestoreExecutor.tryHealGhosts(client.player.playerScreenHandler);
        playClickSound();
    }

    private void playClickSound() {
        try {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        } catch (Throwable ignored) {}
    }

    // ─── RENDER ──────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (closing) {
            openAnim -= 0.12f;
            if (openAnim <= 0.0f) {
                close();
                return;
            }
        } else {
            if (openAnim < 1.0f) openAnim = Math.min(1.0f, openAnim + 0.10f);
        }

        listScroll += (targetListScroll - listScroll) * 0.25f;

        clickBounds.clear();

        boolean dark = isDark();

        // 1. Затемнённый фон
        int backdropA = (int) (150 * openAnim);
        context.fill(0, 0, this.width, this.height, backdropA << 24);

        float guiX = (this.width - GUI_W) / 2.0f;
        float guiY = (this.height - GUI_H) / 2.0f;

        float scale = 0.94f + 0.06f * openAnim;
        float scaledX = guiX + (GUI_W * (1.0f - scale) / 2.0f);
        float scaledY = guiY + (GUI_H * (1.0f - scale) / 2.0f);

        int winBg = dark ? 0xF00D0D11 : 0xF4F5F6FA;

        // 2. Главная плашка окна
        RoundedRectShader.draw(context, scaledX, scaledY, GUI_W * scale, GUI_H * scale, 12.0f, withAlpha(winBg, openAnim));

        // 3. Шапка окна
        drawHeader(context, scaledX, scaledY, GUI_W * scale, HEADER_H * scale, dark, openAnim, mouseX, mouseY);

        // 4. Две колонки
        float contentX = scaledX + 8;
        float contentY = scaledY + (HEADER_H * scale) + 6;
        float contentW = (GUI_W * scale) - 16;
        float contentH = (GUI_H * scale) - (HEADER_H * scale) - 14;

        float leftColW = 240.0f;
        float rightColW = contentW - leftColW - 8;

        drawLeftColumn(context, contentX, contentY, leftColW, contentH, dark, openAnim, mouseX, mouseY);
        drawRightColumn(context, contentX + leftColW + 8, contentY, rightColW, contentH, dark, openAnim, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    // ─── ХЕДЕР ───────────────────────────────────────────────────────────────

    private void drawHeader(DrawContext context, float x, float y, float w, float h, boolean dark, float anim, int mx, int my) {
        int sepCol = dark ? 0x25FFFFFF : 0x20000000;
        RoundedRectShader.draw(context, x, y + h - 1, w, 1, 0.0f, withAlpha(sepCol, anim));

        ModernGuiIcons.draw(context, ModernGuiIcons.Icon.WRENCH, x + 12, y + (h - 11.0f) / 2.0f, 11.0f, withAlpha(dark ? 0xFFFFFFFF : 0xFF141418, anim));
        font().draw(context.getMatrices(), "|", x + 26, y + (h - 10.0f) / 2.0f, 9.5f, withAlpha(dark ? 0xFF454555 : 0xFFB5B5C5, anim));
        font().draw(context.getMatrices(), "Менеджер инвентарей", x + 34, y + (h - 10.0f) / 2.0f, 9.5f, withAlpha(dark ? 0xFFEEEEF5 : 0xFF141418, anim));

        font().draw(context.getMatrices(), "Сохранение и быстрая смена сетов", x + 165, y + (h - 8.0f) / 2.0f + 0.5f, 7.5f, withAlpha(dark ? 0xFF7A7A8A : 0xFF8A8A9A, anim));

        // Кнопка закрыть (✕ с использованием LexoraIcons.Icon.CLOSE)
        float btnX = x + w - 24;
        float btnY = y + (h - 18) / 2.0f;
        boolean hClose = inside(mx, my, btnX, btnY, 18, 18);
        float hCloseAnim = updateBtnHover("btn:close", hClose);

        int closeBg = interpolateColor(0x00000000, dark ? 0x40EF4444 : 0x30EF4444, hCloseAnim);
        if (hCloseAnim > 0.01f) {
            RoundedRectShader.draw(context, btnX, btnY, 18, 18, 4.0f, withAlpha(closeBg, anim));
        }

        int closeCol = interpolateColor(dark ? 0xFF8A8A9A : 0xFF656575, 0xFFFF4444, hCloseAnim);
        LexoraIcons.draw(context, LexoraIcons.Icon.CLOSE, btnX + 4.5f, btnY + 4.5f, 9.0f, withAlpha(closeCol, anim));
        clickBounds.put("action:close", new int[]{(int) btnX, (int) btnY, 18, 18});
    }

    // ─── ЛЕВАЯ КОЛОНКА (СПИСОК РАСКЛАДОК) ────────────────────────────────────

    private void drawLeftColumn(DrawContext context, float x, float y, float w, float h, boolean dark, float anim, int mx, int my) {
        int colBg = dark ? 0xC0111115 : 0xD5FFFFFF;
        RoundedRectShader.draw(context, x, y, w, h, 8.0f, withAlpha(colBg, anim));

        // Поле ввода имени + кнопка "+ Сохранить"
        float inX = x + 8;
        float inY = y + 8;
        float btnW = 68.0f;
        float inW = w - 16 - btnW - 6;
        float inH = 22.0f;

        int inBg = nameFocused ? (dark ? 0xFF22222E : 0xFFD8DCE8) : (dark ? 0xFF181820 : 0xFFE5E8F0);
        RoundedRectShader.draw(context, inX, inY, inW, inH, 4.5f, withAlpha(inBg, anim));

        String displayTxt = nameInput.isEmpty() && !nameFocused ? "Название сета..." : nameInput;
        if (nameFocused && ((System.currentTimeMillis() / 500) % 2 == 0)) displayTxt += "|";
        int txtCol = nameInput.isEmpty() && !nameFocused ? (dark ? 0xFF555566 : 0xFF9999AA) : (dark ? 0xFFFFFFFF : 0xFF141418);
        font().draw(context.getMatrices(), displayTxt, inX + 7, inY + 6.5f, 7.5f, withAlpha(txtCol, anim));
        clickBounds.put("input:name", new int[]{(int) inX, (int) inY, (int) inW, (int) inH});

        // Кнопка "+ Сохранить"
        float sBtnX = inX + inW + 6;
        boolean canSave = !nameInput.trim().isEmpty();
        boolean hSave = canSave && inside(mx, my, sBtnX, inY, btnW, inH);
        float hSaveAnim = updateBtnHover("btn:save", hSave);

        int saveBg = canSave
                ? interpolateColor(dark ? 0xFFFFFFFF : 0xFF141418, dark ? 0xFFE0E0EC : 0xFF2A2A38, hSaveAnim)
                : (dark ? 0xFF22222C : 0xFFD2D6E0);
        int saveTextCol = canSave
                ? (dark ? 0xFF0E0E12 : 0xFFFFFFFF)
                : (dark ? 0xFF666677 : 0xFF888899);

        RoundedRectShader.draw(context, sBtnX, inY, btnW, inH, 4.5f, withAlpha(saveBg, anim));
        font().draw(context.getMatrices(), "+ Сохранить", sBtnX + 8, inY + 6.5f, 7.0f, withAlpha(saveTextCol, anim));
        if (canSave) {
            clickBounds.put("action:save", new int[]{(int) sBtnX, (int) inY, (int) btnW, (int) inH});
        }

        // Заголовок списка сетов
        float listTitleY = inY + inH + 9;
        String countStr = "Сохранённые сеты (" + LoadoutManager.loadouts.size() + ")";
        font().draw(context.getMatrices(), countStr, inX, listTitleY, 7.5f, withAlpha(dark ? 0xFF7A7A8A : 0xFF8A8A9A, anim));

        // Область скроллируемых карточек
        float listY = listTitleY + 12;
        float listH = h - (listY - y) - 8;

        List<InventoryLoadout> loadouts = LoadoutManager.loadouts;
        if (loadouts.isEmpty()) {
            String emptyStr = "Нет сохранённых сетов";
            float ew = font().getWidth(emptyStr, 8.0f);
            font().draw(context.getMatrices(), emptyStr, x + (w - ew) / 2.0f, listY + listH / 2.0f - 4, 8.0f,
                    withAlpha(dark ? 0xFF585868 : 0xFF9999AA, anim));
            return;
        }

        float cardH = 44.0f;
        float cardGap = 5.0f;
        float totalH = loadouts.size() * (cardH + cardGap) - cardGap;
        float maxScroll = Math.max(0, totalH - listH + 4);
        if (targetListScroll < -maxScroll) targetListScroll = -maxScroll;
        if (targetListScroll > 0) targetListScroll = 0;

        context.enableScissor((int) x, (int) listY, (int) (x + w), (int) (listY + listH));

        float curY = listY + listScroll;
        for (int i = 0; i < loadouts.size(); i++) {
            InventoryLoadout l = loadouts.get(i);
            if (curY + cardH >= listY - cardH && curY <= listY + listH + cardH) {
                drawLoadoutCard(context, inX, curY, w - 16, cardH, l, dark, anim, mx, my);
            }
            curY += cardH + cardGap;
        }

        context.disableScissor();

        // Скроллбар справа
        if (totalH > listH && maxScroll > 0) {
            float sbTrackX = x + w - 3.0f;
            float sbTrackY = listY + 2;
            float sbTrackH = listH - 4;
            RoundedRectShader.draw(context, sbTrackX, sbTrackY, 2.0f, sbTrackH, 1.0f, withAlpha(dark ? 0x20FFFFFF : 0x15000000, anim));

            float thumbRatio = Math.max(0.15f, Math.min(1.0f, listH / totalH));
            float thumbH = sbTrackH * thumbRatio;
            float scrollRatio = Math.max(0.0f, Math.min(1.0f, -listScroll / maxScroll));
            float thumbY = sbTrackY + (sbTrackH - thumbH) * scrollRatio;
            int thumbCol = dark ? 0x60FFFFFF : 0x50000000;
            RoundedRectShader.draw(context, sbTrackX, thumbY, 2.0f, thumbH, 1.0f, withAlpha(thumbCol, anim));
        }
    }

    private void drawLoadoutCard(DrawContext context, float x, float y, float w, float h,
                                 InventoryLoadout l, boolean dark, float anim, int mx, int my) {
        boolean isSel = l == selectedLoadout;
        boolean isHover = inside(mx, my, x, y, w, h);
        float[] hState = cardHoverAnims.computeIfAbsent(l.name, k -> new float[]{0f});
        hState[0] += ((isHover ? 1f : 0f) - hState[0]) * 0.22f;

        int cardBg = isSel
                ? (dark ? 0xFF22222E : 0xFFD8DCE8)
                : interpolateColor(dark ? 0x80181820 : 0x80E8ECF5, dark ? 0xFF20202A : 0xFFDEE2EB, hState[0]);

        RoundedRectShader.draw(context, x, y, w, h, 5.0f, withAlpha(cardBg, anim));

        // Индикатор выбора (полоска слева)
        if (isSel) {
            int pillCol = dark ? 0xFFFFFFFF : 0xFF141418;
            RoundedRectShader.draw(context, x + 1.5f, y + 6, 2.5f, h - 12, 1.25f, withAlpha(pillCol, anim));
        }

        // Название сета
        int nameCol = isSel ? (dark ? 0xFFFFFFFF : 0xFF141418) : (dark ? 0xFFEEEEF2 : 0xFF2A2A35);
        font().draw(context.getMatrices(), l.name, x + 9, y + 8.5f, 8.5f, withAlpha(nameCol, anim));

        // Кол-во предметов
        String itemsCount = l.slots.size() + " предм.";
        font().draw(context.getMatrices(), itemsCount, x + 9, y + 23.0f, 7.0f, withAlpha(dark ? 0xFF7A7A8A : 0xFF8A8A9A, anim));

        // Кнопка "Загрузить" (Pill)
        float loadW = 58.0f;
        float loadH = 20.0f;
        float loadX = x + w - loadW - 24;
        float loadY = y + (h - loadH) / 2.0f;

        boolean hLoad = inside(mx, my, loadX, loadY, loadW, loadH);
        float hLoadAnim = updateBtnHover("load:" + l.name, hLoad);
        int loadBg = interpolateColor(dark ? 0xFF2A2A38 : 0xFFD0D5E0, dark ? 0xFFFFFFFF : 0xFF141418, hLoadAnim);
        int loadTxtCol = interpolateColor(dark ? 0xFFEEEEF5 : 0xFF141418, dark ? 0xFF0E0E12 : 0xFFFFFFFF, hLoadAnim);

        RoundedRectShader.draw(context, loadX, loadY, loadW, loadH, 4.0f, withAlpha(loadBg, anim));
        font().draw(context.getMatrices(), "Загрузить", loadX + 9, loadY + 5.5f, 6.8f, withAlpha(loadTxtCol, anim));
        clickBounds.put("load:" + l.name, new int[]{(int) loadX, (int) loadY, (int) loadW, (int) loadH});

        // Кнопка удаления (используем LexoraIcons.Icon.CLOSE)
        float delSize = 18.0f;
        float delX = x + w - delSize - 4;
        float delY = y + (h - delSize) / 2.0f;
        boolean hDel = inside(mx, my, delX, delY, delSize, delSize);
        float hDelAnim = updateBtnHover("del:" + l.name, hDel);

        if (hDelAnim > 0.01f) {
            int delBg = interpolateColor(0x00000000, dark ? 0x40EF4444 : 0x30EF4444, hDelAnim);
            RoundedRectShader.draw(context, delX, delY, delSize, delSize, 3.5f, withAlpha(delBg, anim));
        }

        int delCol = interpolateColor(dark ? 0xFF7A7A8A : 0xFF9090A0, 0xFFFF4444, hDelAnim);
        LexoraIcons.draw(context, LexoraIcons.Icon.CLOSE, delX + 5.0f, delY + 5.0f, 8.0f, withAlpha(delCol, anim));
        clickBounds.put("del:" + l.name, new int[]{(int) delX, (int) delY, (int) delSize, (int) delSize});

        // Клик по самой карточке для выбора
        clickBounds.put("select:" + l.name, new int[]{(int) x, (int) y, (int) (w - loadW - 30), (int) h});
    }

    // ─── ПРАВАЯ КОЛОНКА (СЕТКА ПРОСМОТРА ИНВЕНТАРЯ) ──────────────────────────

    private void drawRightColumn(DrawContext context, float x, float y, float w, float h, boolean dark, float anim, int mx, int my) {
        int colBg = dark ? 0xC0111115 : 0xD5FFFFFF;
        RoundedRectShader.draw(context, x, y, w, h, 8.0f, withAlpha(colBg, anim));

        // Шапка превью
        float pHeadY = y + 8;
        String title = selectedLoadout != null ? "Просмотр: " + selectedLoadout.name : "Просмотр инвентаря";
        font().draw(context.getMatrices(), title, x + 12, pHeadY + 3.0f, 9.0f, withAlpha(dark ? 0xFFFFFFFF : 0xFF141418, anim));

        // Кнопка "Обновить оверлей"
        float refW = 95.0f;
        float refH = 20.0f;
        float refX = x + w - refW - 12;
        boolean hRef = inside(mx, my, refX, pHeadY, refW, refH);
        float hRefAnim = updateBtnHover("btn:refresh", hRef);
        int refBg = interpolateColor(dark ? 0x60252535 : 0x60D5DAE5, dark ? 0xFF2A2A38 : 0xFFC0C5D5, hRefAnim);
        RoundedRectShader.draw(context, refX, pHeadY, refW, refH, 4.0f, withAlpha(refBg, anim));
        font().draw(context.getMatrices(), "Обновить оверлей", refX + 9, pHeadY + 6.0f, 6.5f, withAlpha(dark ? 0xFFEEEEF2 : 0xFF141418, anim));
        clickBounds.put("action:refresh", new int[]{(int) refX, (int) pHeadY, (int) refW, (int) refH});

        if (selectedLoadout == null) {
            String noSel = "Выберите раскладку слева для предпросмотра";
            float nw = font().getWidth(noSel, 8.5f);
            font().draw(context.getMatrices(), noSel, x + (w - nw) / 2.0f, y + h / 2.0f, 8.5f,
                    withAlpha(dark ? 0xFF666677 : 0xFF9999AA, anim));
            return;
        }

        // Контейнер сетки
        float gridBoxY = pHeadY + refH + 10;
        float gridBoxH = h - (gridBoxY - y) - 42;
        int boxBg = dark ? 0x600B0B0E : 0x60E8EBF2;
        RoundedRectShader.draw(context, x + 10, gridBoxY, w - 20, gridBoxH, 6.0f, withAlpha(boxBg, anim));

        float matrixW = 9 * CELL_STEP - CELL_GAP; // 9 * 25 - 3 = 222px
        float gridStartX = x + 10 + ((w - 20) - matrixW) / 2.0f;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerInventory playerInv = mc.player != null ? mc.player.getInventory() : null;

        LoadoutSlotData hoveredData = null;

        // 1. Броня & Офхенд (верхний ряд)
        float armorY = gridBoxY + 14;
        font().draw(context.getMatrices(), "Броня & Офхенд", gridStartX, armorY - 9, 6.5f, withAlpha(dark ? 0xFF7A7A8A : 0xFF8A8A9A, anim));

        // Броня: слоты 5 (шлем), 6 (нагрудник), 7 (штаны), 8 (ботинки)
        for (int i = 0; i < 4; i++) {
            int slotId = InventorySlotIds.ARMOR_START + i;
            float sx = gridStartX + i * CELL_STEP;
            float sy = armorY;
            LoadoutSlotData data = selectedBySlot.get(slotId);
            drawSlotCell(context, sx, sy, data, playerInv, dark, anim);
            if (inside(mx, my, sx, sy, CELL_SIZE, CELL_SIZE)) {
                hoveredData = data;
            }
        }

        // Офхенд: слот 45 (справа в ряду)
        float offhandX = gridStartX + 8 * CELL_STEP;
        LoadoutSlotData offhandData = selectedBySlot.get(InventorySlotIds.OFFHAND);
        drawSlotCell(context, offhandX, armorY, offhandData, playerInv, dark, anim);
        if (inside(mx, my, offhandX, armorY, CELL_SIZE, CELL_SIZE)) {
            hoveredData = offhandData;
        }

        // 2. Основной инвентарь 3x9 (слоты 9..35)
        float mainInvY = armorY + CELL_STEP + 16;
        font().draw(context.getMatrices(), "Инвентарь (3x9)", gridStartX, mainInvY - 9, 6.5f, withAlpha(dark ? 0xFF7A7A8A : 0xFF8A8A9A, anim));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotId = InventorySlotIds.STORAGE_START + row * 9 + col;
                float sx = gridStartX + col * CELL_STEP;
                float sy = mainInvY + row * CELL_STEP;
                LoadoutSlotData data = selectedBySlot.get(slotId);
                drawSlotCell(context, sx, sy, data, playerInv, dark, anim);
                if (inside(mx, my, sx, sy, CELL_SIZE, CELL_SIZE)) {
                    hoveredData = data;
                }
            }
        }

        // 3. Хотбар 1x9 (слоты 36..44)
        float hotbarY = mainInvY + 3 * CELL_STEP + 10;
        int sepLineCol = dark ? 0x25FFFFFF : 0x20000000;
        RoundedRectShader.draw(context, gridStartX, hotbarY - 5, matrixW, 1, 0.0f, withAlpha(sepLineCol, anim));

        for (int col = 0; col < 9; col++) {
            int slotId = InventorySlotIds.HOTBAR_START + col;
            float sx = gridStartX + col * CELL_STEP;
            float sy = hotbarY;
            LoadoutSlotData data = selectedBySlot.get(slotId);
            drawSlotCell(context, sx, sy, data, playerInv, dark, anim);
            if (inside(mx, my, sx, sy, CELL_SIZE, CELL_SIZE)) {
                hoveredData = data;
            }
        }

        // Нижняя строка действий
        float botY = gridBoxY + gridBoxH + 8;
        int ghostCount = LoadoutRestoreExecutor.ghosts().size();
        String ghostStatus = ghostCount == 0
                ? "● Оверлей нехватки: выключен"
                : "● Оверлей активен (" + ghostCount + " предм.)";
        int statusColor = ghostCount == 0 ? (dark ? 0xFF7A7A8A : 0xFF8A8A9A) : 0xFFEF4444;
        font().draw(context.getMatrices(), ghostStatus, x + 12, botY + 7.0f, 7.0f, withAlpha(statusColor, anim));

        // Большая кнопка "Применить раскладку"
        float appW = 135.0f;
        float appH = 24.0f;
        float appX = x + w - appW - 12;
        boolean hApp = inside(mx, my, appX, botY, appW, appH);
        float hAppAnim = updateBtnHover("btn:apply", hApp);
        int appBg = interpolateColor(dark ? 0xFFFFFFFF : 0xFF141418, dark ? 0xFFD8D8E5 : 0xFF2A2A38, hAppAnim);
        int appTxt = dark ? 0xFF0E0E12 : 0xFFFFFFFF;
        RoundedRectShader.draw(context, appX, botY, appW, appH, 5.0f, withAlpha(appBg, anim));
        font().draw(context.getMatrices(), "Применить раскладку", appX + 15, botY + 7.5f, 7.5f, withAlpha(appTxt, anim));
        clickBounds.put("action:apply", new int[]{(int) appX, (int) botY, (int) appW, (int) appH});

        // Всплывающий тултип предмета
        if (hoveredData != null) {
            boolean has = playerInv != null && playerHasItem(playerInv, hoveredData.matchKey());
            String titleName = ItemIdentity.displayName(hoveredData.itemId, hoveredData.customName);
            String status = has ? "✓ В наличии" : "✗ Отсутствует";
            int statusCol = has ? 0xFF22C55E : 0xFFEF4444;

            float tw1 = font().getWidth(titleName, 7.5f);
            float tw2 = font().getWidth(status, 6.5f);
            float tipW = Math.max(tw1, tw2) + 16;
            float tipH = 28.0f;

            float tipX = mx + 12;
            float tipY = my - 15;
            if (tipX + tipW > this.width - 10) tipX = mx - tipW - 12;
            if (tipY + tipH > this.height - 10) tipY = my - tipH - 5;

            int tipBg = dark ? 0xF5111116 : 0xF5FFFFFF;
            RoundedRectShader.draw(context, tipX, tipY, tipW, tipH, 5.0f, withAlpha(tipBg, anim));
            RoundedRectShader.draw(context, tipX - 1, tipY - 1, tipW + 2, tipH + 2, 5.5f, withAlpha(dark ? 0x30FFFFFF : 0x20000000, anim));

            font().draw(context.getMatrices(), titleName, tipX + 8, tipY + 6.0f, 7.5f, withAlpha(dark ? 0xFFFFFFFF : 0xFF141418, anim));
            font().draw(context.getMatrices(), status, tipX + 8, tipY + 17.0f, 6.5f, withAlpha(statusCol, anim));
        }
    }

    private void drawSlotCell(DrawContext context, float x, float y, LoadoutSlotData data, PlayerInventory inv, boolean dark, float anim) {
        int slotBg = dark ? 0x701E1E28 : 0x70D2D7E5;
        RoundedRectShader.draw(context, x, y, CELL_SIZE, CELL_SIZE, 4.0f, withAlpha(slotBg, anim));

        if (data == null) return;

        boolean present = inv != null && playerHasItem(inv, data.matchKey());

        ItemStack stack = ItemIdentity.templateStack(data);
        context.drawItem(stack, (int) x + 3, (int) y + 3);

        // Красная подсветка, если предмета нет в инвентаре
        if (!present) {
            RoundedRectShader.draw(context, x, y, CELL_SIZE, CELL_SIZE, 4.0f, withAlpha(0x50EF4444, anim));
            RoundedRectShader.draw(context, x + CELL_SIZE - 5.0f, y + 2.0f, 3.5f, 3.5f, 1.75f, withAlpha(0xFFEF4444, anim));
        }

        // Кол-во предмета
        if (data.count > 1) {
            String countText = String.valueOf(data.count);
            float cw = font().getWidth(countText, 6.5f);
            font().draw(context.getMatrices(), countText, x + CELL_SIZE - cw - 2.0f, y + CELL_SIZE - 7.5f, 6.5f, withAlpha(0xFFFFFFFF, anim));
        }
    }

    private static boolean playerHasItem(PlayerInventory inv, String matchKey) {
        if (inv == null) return false;
        for (ItemStack s : inv.main) {
            if (!s.isEmpty() && ItemIdentity.matchKeyOf(s).equals(matchKey)) return true;
        }
        for (ItemStack s : inv.armor) {
            if (!s.isEmpty() && ItemIdentity.matchKeyOf(s).equals(matchKey)) return true;
        }
        if (!inv.offHand.isEmpty()) {
            ItemStack s = inv.offHand.get(0);
            if (!s.isEmpty() && ItemIdentity.matchKeyOf(s).equals(matchKey)) return true;
        }
        return false;
    }

    // ─── ОБРАБОТКА МЫШИ И КЛАВИАТУРЫ ──────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Map.Entry<String, int[]> e : clickBounds.entrySet()) {
                String key = e.getKey();
                int[] b = e.getValue();
                if (inside((float) mx, (float) my, b[0], b[1], b[2], b[3])) {
                    handleClickAction(key);
                    return true;
                }
            }
            nameFocused = false;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void handleClickAction(String key) {
        if (key.equals("action:close")) {
            backToGame();
        } else if (key.equals("input:name")) {
            nameFocused = true;
            playClickSound();
        } else if (key.equals("action:save")) {
            saveCurrent();
        } else if (key.startsWith("select:")) {
            String name = key.substring(7);
            LoadoutManager.findByName(name).ifPresent(this::selectLoadout);
            playClickSound();
        } else if (key.startsWith("load:")) {
            String name = key.substring(5);
            LoadoutManager.findByName(name).ifPresent(this::startLoad);
        } else if (key.startsWith("del:")) {
            String name = key.substring(4);
            LoadoutManager.findByName(name).ifPresent(this::deleteLoadout);
        } else if (key.equals("action:refresh")) {
            refreshGhosts();
        } else if (key.equals("action:apply")) {
            if (selectedLoadout != null) {
                startLoad(selectedLoadout);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        targetListScroll += (float) (verticalAmount * 24.0);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (nameFocused) {
            if (chr >= 32 && nameInput.length() < 24) {
                nameInput += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !nameInput.isEmpty()) {
                nameInput = nameInput.substring(0, nameInput.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                saveCurrent();
                nameFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nameFocused = false;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            backToGame();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─── ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ───────────────────────────────────────────────

    private float updateBtnHover(String key, boolean hovered) {
        float current = btnHoverAnims.getOrDefault(key, 0.0f);
        float target = hovered ? 1.0f : 0.0f;
        current += (target - current) * 0.25f;
        btnHoverAnims.put(key, current);
        return current;
    }

    private boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int interpolateColor(int c1, int c2, float ratio) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
