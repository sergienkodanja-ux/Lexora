package com.lexoravisauls.client.inventorymanager;

import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.main_menu.LexoraMainMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран менеджера раскладок — двухколоночный master-detail, без попапов.
 * Слева: шапка (заголовок, "+ Сохранить", счётчик), поле имени новой раскладки,
 * скроллируемый список карточек (Загрузить / ✕ прямо в строке).
 * Справа: постоянная панель "Просмотр" с сеткой инвентаря выбранной раскладки.
 * <p>
 * Размер панели АДАПТИВНЫЙ — считается от реального размера окна (computePanelSize),
 * а не жёстко зашит, чтобы не вылезать за пределы маленьких окон.
 */
public class InventoryManagerScreen extends Screen {

    private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;
    private static MsdfFont getFont() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    // ─── Палитра — монохром, без фиолетового ────────────────────────────────
    private static final int COL_PANEL_BG        = 0xF20A0A0A;
    private static final int COL_COLUMN_BG       = 0x66131313;
    private static final int COL_TEXT_PRIMARY    = 0xFFEDEDED;
    private static final int COL_TEXT_SECONDARY  = 0xFF757575;
    private static final int COL_TEXT_MUTED      = 0xFF3A3A3A;
    private static final int COL_CARD_BG         = 0x99101010;
    private static final int COL_CARD_HOVER      = 0xAA1E1E1E;
    private static final int COL_CARD_SELECTED   = 0xCC262626;
    private static final int COL_ACCENT          = 0xFFEDEDED;
    private static final int COL_SLOT_BG         = 0x99141414;
    private static final int COL_SLOT_EDGE       = 0x33FFFFFF;
    private static final int COL_STATUS_OK       = 0xFF4CAF6E;
    private static final int COL_STATUS_MISSING  = 0xFFCC5555;
    private static final int COL_FIELD_BG        = 0xAA050505;
    private static final int COL_FIELD_FOCUSED   = 0xAA1A1A1A;
    private static final int COL_BTN_NORMAL      = 0xCC181818;
    private static final int COL_BTN_HOVER       = 0xCC2C2C2C;
    private static final int COL_BTN_PRIMARY     = 0xE0424242;
    private static final int COL_BTN_PRIMARY_HOV = 0xF0585858;
    private static final int COL_BTN_DANGER_HOV  = 0xCC552222;

    private static final float SIZE_TITLE = 12.5f;
    private static final float SIZE_TEXT  = 9.5f;
    private static final float SIZE_SMALL = 8f;
    private static final float SIZE_TINY  = 7f;

    // ─── Геометрия — компактнее прежнего, плюс адаптивная под окно ──────────
    private static final int PANEL_W_PREF = 520;
    private static final int PANEL_H_PREF = 360;
    private static final int SCREEN_MARGIN = 28;
    private static final float LEFT_COL_FRACTION = 0.44f;
    private static final int PAD = 12;
    private static final int COL_GAP = 14;
    private static final int CARD_H = 40;
    private static final int CARD_GAP = 5;
    private static final int SLOT_STEP = 18; // 16px ячейка + 2px зазор

    private int panelW, panelH, leftColW;

    private InventoryLoadout selectedLoadout;
    private Map<Integer, LoadoutSlotData> selectedBySlot = new HashMap<>();

    private float listScrollAnim   = 0f;
    private float listScrollTarget = 0f;

    private final Map<InventoryLoadout, float[]> cardHoverAnim = new IdentityHashMap<>();

    private String  nameInput   = "";
    private int     cursorPos   = 0;
    private boolean nameFocused = false;

    private long  screenOpenStart = 0L;
    private float screenOpenRaw   = 0f; // линейный прогресс 0..1
    private float screenOpenAnim  = 0f; // eased, для панели целиком
    private static final long SCREEN_OPEN_MS = 280L;

    private static boolean onboardingDismissed = false;

    public InventoryManagerScreen() {
        super(Text.literal("Менеджер инвентарей"));
        LoadoutManager.ensureLoaded();
        if (!LoadoutManager.loadouts.isEmpty()) {
            selectLoadout(LoadoutManager.loadouts.get(0));
        }
    }

    @Override
    protected void init() {
        super.init();
        screenOpenStart = System.currentTimeMillis();
    }

    private void backToGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.setScreen(new InventoryScreen(client.player));
        else client.setScreen(null);
    }

    @Override
    public void close() { backToGame(); }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    // ─── Действия ────────────────────────────────────────────────────────────

    private void selectLoadout(InventoryLoadout l) {
        selectedLoadout = l;
        selectedBySlot = new HashMap<>();
        if (l != null) for (LoadoutSlotData d : l.slots) selectedBySlot.put(d.handlerSlotId, d);
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
        cursorPos = 0;
    }

    private void startLoad(InventoryLoadout loadout) {
        if (loadout == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        LoadoutRestoreExecutor.startRestore(client.player.playerScreenHandler, loadout);
        client.setScreen(new InventoryScreen(client.player));
    }

    private void deleteLoadout(InventoryLoadout loadout) {
        LoadoutManager.delete(loadout);
        if (selectedLoadout == loadout) {
            selectLoadout(LoadoutManager.loadouts.isEmpty() ? null : LoadoutManager.loadouts.get(0));
        }
    }

    private void refreshGhosts() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        LoadoutRestoreExecutor.tryHealGhosts(client.player.playerScreenHandler);
    }

    // ─── Render ──────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        tickAnimations();
        computePanelSize();

        int backdropA = (int) (0xCC * screenOpenAnim);
        context.fill(0, 0, this.width, this.height, backdropA << 24);

        int px = panelX(), py = panelY();

        context.getMatrices().push();
        float s = lerp(0.95f, 1f, screenOpenAnim);
        float cx = px + panelW / 2f, cy = py + panelH / 2f;
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().scale(s, s, 1f);
        context.getMatrices().translate(-cx, -cy, 0);

        // Мягкая тень под панелью — несколько прямоугольников с падающей альфой.
        for (int i = 3; i >= 1; i--) {
            int a = (int) ((13 - i * 3) * screenOpenAnim);
            LexoraMainMenu.drawSmoothRect(context, px - i, py - i + 3, panelW + i * 2, panelH + i * 2, a << 24);
        }

        LexoraMainMenu.drawSmoothRect(context, px, py, panelW, panelH, adjustAlpha(COL_PANEL_BG, screenOpenAnim));
        context.fill(px + 2, py, px + panelW - 2, py + 1, adjustAlpha(0x14FFFFFF, screenOpenAnim));

        boolean backHover = inRect(mouseX, mouseY, px + PAD, py + 12, 56, 11);
        getFont().draw(context.getMatrices(), "← Назад", px + PAD, py + 12, SIZE_TEXT,
                adjustAlpha(backHover ? COL_TEXT_PRIMARY : COL_TEXT_SECONDARY, screenOpenAnim));

        int headerY = py + 32;
        int lcX = px + PAD;
        int rcX = lcX + leftColW + COL_GAP;
        int rcW = (px + panelW - PAD) - rcX;

        renderLeftColumn(context, mouseX, mouseY, lcX, headerY, py);
        renderRightColumn(context, mouseX, mouseY, rcX, rcW, headerY, py);

        context.getMatrices().pop();

        if (this.height > 480) renderOnboardingToast(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void tickAnimations() {
        float diff = listScrollTarget - listScrollAnim;
        if (Math.abs(diff) < 0.3f) listScrollAnim = listScrollTarget;
        else listScrollAnim += diff * 0.2f;

        long now = System.currentTimeMillis();
        screenOpenRaw = Math.min(1f, (now - screenOpenStart) / (float) SCREEN_OPEN_MS);
        screenOpenAnim = easeOutQuart(screenOpenRaw);
    }

    /** Пересчитывает размер панели от размера окна — никогда не вылезает за его пределы. */
    private void computePanelSize() {
        panelW = Math.min(PANEL_W_PREF, Math.max(300, this.width - SCREEN_MARGIN * 2));
        panelH = Math.min(PANEL_H_PREF, Math.max(240, this.height - SCREEN_MARGIN * 2));
        leftColW = (int) (panelW * LEFT_COL_FRACTION);
    }

    /** Стаггер-прогресс для элемента с индексом index (карточка/группа строк сетки), уже с easing. */
    private float staggered(int index, float perItemDelay, float span) {
        float p = (screenOpenRaw - index * perItemDelay) / span;
        return easeOutQuart(Math.max(0f, Math.min(1f, p)));
    }

    private int panelX() { return (this.width - panelW) / 2; }
    private int panelY() { return (this.height - panelH) / 2; }

    // ─── Левая колонка: шапка + поле имени + список ─────────────────────────

    private void renderLeftColumn(DrawContext context, int mouseX, int mouseY, int lcX, int headerY, int py) {
        getFont().draw(context.getMatrices(), "Менеджер инвентарей", lcX, headerY, SIZE_TITLE,
                adjustAlpha(COL_TEXT_PRIMARY, screenOpenAnim));

        int saveW = 92, saveH = 20;
        int saveX = lcX + leftColW - saveW, saveY = headerY - 3;
        boolean saveEnabled = !nameInput.trim().isEmpty();
        boolean saveHover = saveEnabled && inRect(mouseX, mouseY, saveX, saveY, saveW, saveH);
        drawButton(context, saveX, saveY, saveW, saveH, "+ Сохранить", saveEnabled, true, saveHover ? 1f : 0f, screenOpenAnim);

        getFont().draw(context.getMatrices(), pluralSaves(LoadoutManager.loadouts.size()), lcX, headerY + 14, SIZE_SMALL,
                adjustAlpha(COL_TEXT_SECONDARY, screenOpenAnim));

        int nameFieldY = headerY + 30, nameFieldH = 20;
        LexoraMainMenu.drawSmoothRect(context, lcX, nameFieldY, leftColW, nameFieldH,
                adjustAlpha(nameFocused ? COL_FIELD_FOCUSED : COL_FIELD_BG, screenOpenAnim));
        if (nameInput.isEmpty() && !nameFocused) {
            getFont().draw(context.getMatrices(), "Название раскладки...", lcX + 7, nameFieldY + 6, SIZE_SMALL,
                    adjustAlpha(COL_TEXT_MUTED, screenOpenAnim));
        } else {
            getFont().draw(context.getMatrices(), nameInput, lcX + 7, nameFieldY + 6, SIZE_SMALL,
                    adjustAlpha(COL_TEXT_PRIMARY, screenOpenAnim));
            boolean cursorVisible = ((System.currentTimeMillis() / 530) % 2) == 0;
            if (cursorVisible && nameFocused) {
                float cw = getFont().getWidth(nameInput.substring(0, Math.min(cursorPos, nameInput.length())), SIZE_SMALL);
                context.fill((int) (lcX + 7 + cw), nameFieldY + 4, (int) (lcX + 8 + cw), nameFieldY + nameFieldH - 4,
                        adjustAlpha(COL_TEXT_PRIMARY, screenOpenAnim));
            }
        }

        int listY = nameFieldY + nameFieldH + 8;
        int listBottom = py + panelH - PAD;
        int listH = listBottom - listY;
        LexoraMainMenu.drawSmoothRect(context, lcX, listY, leftColW, listH, adjustAlpha(COL_COLUMN_BG, screenOpenAnim));

        int contentX = lcX + 6, contentY = listY + 6, contentW = leftColW - 12, contentH = listH - 12;
        List<InventoryLoadout> loadouts = LoadoutManager.loadouts;

        if (loadouts.isEmpty()) {
            String msg = "Список пуст — введи имя выше.";
            float w = getFont().getWidth(msg, SIZE_SMALL);
            getFont().draw(context.getMatrices(), msg, contentX + (contentW - w) / 2f, contentY + contentH / 2f - 4,
                    SIZE_SMALL, adjustAlpha(COL_TEXT_SECONDARY, screenOpenAnim));
            return;
        }

        int maxScroll = Math.max(0, loadouts.size() * (CARD_H + CARD_GAP) - CARD_GAP - contentH);
        if (listScrollTarget > maxScroll) listScrollTarget = maxScroll;

        context.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        for (int i = 0; i < loadouts.size(); i++) {
            InventoryLoadout l = loadouts.get(i);
            int rowY = contentY + i * (CARD_H + CARD_GAP) - (int) listScrollAnim;
            if (rowY + CARD_H < contentY || rowY > contentY + contentH) continue;
            renderCard(context, l, contentX, rowY, contentW, mouseX, mouseY, staggered(i, 0.05f, 0.7f));
        }
        context.disableScissor();
    }

    private void renderCard(DrawContext context, InventoryLoadout l, int x, int y, int w, int mouseX, int mouseY, float entrance) {
        boolean selected = l == selectedLoadout;
        float[] hoverState = cardHoverAnim.computeIfAbsent(l, k -> new float[]{0f});
        boolean hoverNow = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + CARD_H;
        hoverState[0] += ((hoverNow ? 1f : 0f) - hoverState[0]) * 0.18f;
        if (hoverState[0] < 0.005f) hoverState[0] = 0f;

        float alphaMul = screenOpenAnim * entrance;

        int baseBg = selected ? COL_CARD_SELECTED : COL_CARD_BG;
        int hovBg  = selected ? COL_CARD_SELECTED : COL_CARD_HOVER;
        int bg = LexoraMainMenu.blendColors(baseBg, hovBg, hoverState[0]);
        LexoraMainMenu.drawSmoothRect(context, x, y, w, CARD_H, adjustAlpha(bg, alphaMul));

        float accentT = Math.max(selected ? 1f : 0f, hoverState[0]);
        if (accentT > 0.01f) {
            LexoraMainMenu.drawSmoothRect(context, x, y + 4, 3, CARD_H - 8, adjustAlpha(COL_ACCENT, alphaMul * accentT));
        }

        getFont().draw(context.getMatrices(), l.name, x + 12, y + 6, SIZE_TEXT, adjustAlpha(COL_TEXT_PRIMARY, alphaMul));
        getFont().draw(context.getMatrices(), pluralItems(l.slots.size()), x + 12, y + CARD_H - 14, SIZE_SMALL,
                adjustAlpha(COL_TEXT_SECONDARY, alphaMul));

        int[] delRect = cardDeleteButtonRect(x, y, w);
        int[] loadRect = cardLoadButtonRect(x, y, w);
        boolean loadHover = inRect(mouseX, mouseY, loadRect);
        boolean delHover  = inRect(mouseX, mouseY, delRect);

        drawButton(context, loadRect[0], loadRect[1], loadRect[2], loadRect[3], "Загрузить", true, true,
                loadHover ? 1f : 0f, alphaMul);

        int delBg = adjustAlpha(LexoraMainMenu.blendColors(COL_BTN_NORMAL, COL_BTN_DANGER_HOV, delHover ? 1f : 0f), alphaMul);
        LexoraMainMenu.drawSmoothRect(context, delRect[0], delRect[1], delRect[2], delRect[3], delBg);
        String cross = "✕";
        float cw = getFont().getWidth(cross, SIZE_SMALL);
        getFont().draw(context.getMatrices(), cross, delRect[0] + (delRect[2] - cw) / 2f, delRect[1] + (delRect[3] - SIZE_SMALL) / 2f,
                SIZE_SMALL, adjustAlpha(delHover ? 0xFFDD8888 : COL_TEXT_SECONDARY, alphaMul));
    }

    private int[] cardDeleteButtonRect(int cardX, int cardY, int cardW) {
        int size = 18;
        return new int[]{cardX + cardW - 8 - size, cardY + (CARD_H - size) / 2, size, size};
    }

    private int[] cardLoadButtonRect(int cardX, int cardY, int cardW) {
        int[] del = cardDeleteButtonRect(cardX, cardY, cardW);
        int w = 64, h = 20;
        return new int[]{del[0] - 5 - w, cardY + (CARD_H - h) / 2, w, h};
    }

    // ─── Правая колонка: "Просмотр" + сетка инвентаря ───────────────────────

    private void renderRightColumn(DrawContext context, int mouseX, int mouseY, int rcX, int rcW, int headerY, int py) {
        getFont().draw(context.getMatrices(), "Просмотр", rcX, headerY, SIZE_TITLE, adjustAlpha(COL_TEXT_PRIMARY, screenOpenAnim));
        String previewName = selectedLoadout != null ? selectedLoadout.name : "—";
        getFont().draw(context.getMatrices(), previewName, rcX, headerY + 14, SIZE_SMALL, adjustAlpha(COL_TEXT_SECONDARY, screenOpenAnim));

        int refreshW = 76, refreshH = 18;
        int refreshX = rcX + rcW - refreshW, refreshY = headerY - 2;
        boolean refreshHover = inRect(mouseX, mouseY, refreshX, refreshY, refreshW, refreshH);
        drawButton(context, refreshX, refreshY, refreshW, refreshH, "Обновить", true, false, refreshHover ? 1f : 0f, screenOpenAnim);

        int gridPanelY = headerY + 30 + 8;
        int gridPanelBottom = py + panelH - PAD;
        int gridPanelH = gridPanelBottom - gridPanelY;
        LexoraMainMenu.drawSmoothRect(context, rcX, gridPanelY, rcW, gridPanelH, adjustAlpha(COL_COLUMN_BG, screenOpenAnim));

        if (selectedLoadout == null) {
            String msg = "Выбери раскладку слева";
            float w = getFont().getWidth(msg, SIZE_TEXT);
            getFont().draw(context.getMatrices(), msg, rcX + (rcW - w) / 2f, gridPanelY + gridPanelH / 2f - 4, SIZE_TEXT,
                    adjustAlpha(COL_TEXT_SECONDARY, screenOpenAnim));
            return;
        }

        int contentBlockH = 16 + 8 + 3 * SLOT_STEP + 5 + 16 + 12 + 9;
        int gx = rcX + 12;
        int gy = gridPanelY + Math.max(12, (gridPanelH - contentBlockH) / 2);
        int mainGridY = gy + 24;
        int hotbarY = mainGridY + 3 * SLOT_STEP + 5;

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory inv = client.player != null ? client.player.getInventory() : null;

        LoadoutSlotData hoveredSlot = null;
        for (int slotId = InventorySlotIds.MANAGED_START; slotId <= InventorySlotIds.MANAGED_END; slotId++) {
            int[] pos = gridPosForSlot(slotId, gx, gy, mainGridY, hotbarY);
            float rowAnim = staggered(rowGroupOf(slotId), 0.08f, 0.7f);
            float slotAlpha = screenOpenAnim * rowAnim;

            LexoraMainMenu.drawSmoothRect(context, pos[0], pos[1], 16, 16, adjustAlpha(COL_SLOT_BG, slotAlpha));
            context.fill(pos[0], pos[1], pos[0] + 16, pos[1] + 1, adjustAlpha(COL_SLOT_EDGE, slotAlpha));

            LoadoutSlotData d = selectedBySlot.get(slotId);
            if (d == null) continue;

            boolean has = inv != null && playerHasItem(inv, d.matchKey());
            drawSlotItem(context, d, pos[0], pos[1], has, slotAlpha);

            if (mouseX >= pos[0] && mouseX < pos[0] + 16 && mouseY >= pos[1] && mouseY < pos[1] + 16) {
                hoveredSlot = d;
            }
        }

        int statusY = hotbarY + SLOT_STEP + 12;
        int ghostCount = LoadoutRestoreExecutor.ghosts().size();
        String statusText = ghostCount == 0 ? "Оверлей выключен" : ("Оверлей активен — " + pluralItems(ghostCount));
        int dotColor = ghostCount == 0 ? COL_TEXT_MUTED : COL_STATUS_MISSING;
        LexoraMainMenu.drawSmoothRect(context, gx, statusY + 2, 5, 5, adjustAlpha(dotColor, screenOpenAnim));
        getFont().draw(context.getMatrices(), statusText, gx + 10, statusY, SIZE_SMALL, adjustAlpha(COL_TEXT_SECONDARY, screenOpenAnim));

        if (hoveredSlot != null) {
            boolean has = inv != null && playerHasItem(inv, hoveredSlot.matchKey());
            String label = ItemIdentity.displayName(hoveredSlot.itemId, hoveredSlot.customName) + (has ? "  ✓" : "  ✗");
            float tw = getFont().getWidth(label, SIZE_SMALL);
            int tx = mouseX + 12, ty = mouseY - 4;
            LexoraMainMenu.drawSmoothRect(context, tx - 5, ty - 3, (int) tw + 10, 14, 0xF0050505);
            getFont().draw(context.getMatrices(), label, tx, ty, SIZE_SMALL, has ? COL_STATUS_OK : COL_STATUS_MISSING);
        }
    }

    private void drawSlotItem(DrawContext context, LoadoutSlotData d, int x, int y, boolean present, float alphaMul) {
        ItemStack stack = ItemIdentity.templateStack(d);
        context.drawItem(stack, x, y);
        if (!present) {
            context.fill(x, y, x + 16, y + 16, (((int) (0x66 * alphaMul)) << 24) | 0x771111);
        }
        if (d.count > 1) {
            String cs = String.valueOf(d.count);
            float cw = getFont().getWidth(cs, SIZE_TINY);
            float tx = x + 15 - cw, ty = y + 9;
            getFont().draw(context.getMatrices(), cs, tx + 1, ty + 1, SIZE_TINY, adjustAlpha(0xFF000000, alphaMul));
            getFont().draw(context.getMatrices(), cs, tx, ty, SIZE_TINY, adjustAlpha(0xFFFFFFFF, alphaMul));
        }
    }

    private int[] gridPosForSlot(int slotId, int gx, int gy, int mainGridY, int hotbarY) {
        if (InventorySlotIds.isArmor(slotId)) {
            int i = slotId - InventorySlotIds.ARMOR_START;
            return new int[]{gx + i * SLOT_STEP, gy};
        }
        if (InventorySlotIds.isStorage(slotId)) {
            int idx = slotId - InventorySlotIds.STORAGE_START;
            int row = idx / 9, col = idx % 9;
            return new int[]{gx + col * SLOT_STEP, mainGridY + row * SLOT_STEP};
        }
        if (InventorySlotIds.isHotbar(slotId)) {
            int col = slotId - InventorySlotIds.HOTBAR_START;
            return new int[]{gx + col * SLOT_STEP, hotbarY};
        }
        return new int[]{gx + 9 * SLOT_STEP + 8, hotbarY}; // офхенд
    }

    /** Группа строки для стаггера сетки: 0=броня, 1-3=сумка по рядам, 4=хотбар+офхенд (последняя волна). */
    private int rowGroupOf(int slotId) {
        if (InventorySlotIds.isArmor(slotId)) return 0;
        if (InventorySlotIds.isStorage(slotId)) return 1 + (slotId - InventorySlotIds.STORAGE_START) / 9;
        return 4;
    }

    private static boolean playerHasItem(PlayerInventory inv, String matchKey) {
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

    // ─── Приветственная плашка ───────────────────────────────────────────────

    private int[] toastRect() {
        int w = Math.min(460, this.width - 40), h = 56;
        return new int[]{(this.width - w) / 2, this.height - h - 20, w, h};
    }

    private void renderOnboardingToast(DrawContext context, int mouseX, int mouseY) {
        if (onboardingDismissed) return;
        int[] r = toastRect();
        int tx = r[0], ty = r[1], tw = r[2];

        LexoraMainMenu.drawSmoothRect(context, tx, ty, tw, r[3], adjustAlpha(0xF0121212, screenOpenAnim));
        getFont().draw(context.getMatrices(), "Добро пожаловать в «Менеджер инвентарей»", tx + 14, ty + 9, SIZE_TEXT,
                adjustAlpha(COL_TEXT_PRIMARY, screenOpenAnim));
        getFont().draw(context.getMatrices(), "Сохраняй раскладки и загружай одним кликом. Если предмета",
                tx + 14, ty + 25, SIZE_SMALL, adjustAlpha(COL_TEXT_SECONDARY, screenOpenAnim));
        getFont().draw(context.getMatrices(), "не хватит — на его месте появится красный значок в инвентаре.",
                tx + 14, ty + 37, SIZE_SMALL, adjustAlpha(COL_TEXT_SECONDARY, screenOpenAnim));

        boolean closeHover = inRect(mouseX, mouseY, tx + tw - 24, ty + 7, 16, 16);
        String cross = "✕";
        float cw = getFont().getWidth(cross, SIZE_SMALL);
        getFont().draw(context.getMatrices(), cross, tx + tw - 24 + (16 - cw) / 2f, ty + 11, SIZE_SMALL,
                adjustAlpha(closeHover ? 0xFFCC5555 : COL_TEXT_SECONDARY, screenOpenAnim));
    }

    // ─── Мелкие хелперы ───────────────────────────────────────────────────────

    private void drawButton(DrawContext context, int x, int y, int w, int h, String label,
                            boolean enabled, boolean primary, float hoverT, float alphaMul) {
        int normal = primary ? COL_BTN_PRIMARY : COL_BTN_NORMAL;
        int hoverC = primary ? COL_BTN_PRIMARY_HOV : COL_BTN_HOVER;
        float visualAlpha = (enabled ? 1f : 0.4f) * alphaMul;
        int bg = adjustAlpha(LexoraMainMenu.blendColors(normal, hoverC, hoverT), visualAlpha);
        LexoraMainMenu.drawSmoothRect(context, x, y, w, h, bg);
        context.fill(x + 2, y + h - 1, x + w - 2, y + h, adjustAlpha(0x33000000, visualAlpha));

        int textCol = adjustAlpha(hoverT > 0.4f ? COL_TEXT_PRIMARY : COL_TEXT_SECONDARY, visualAlpha);
        float lw = getFont().getWidth(label, SIZE_SMALL);
        getFont().draw(context.getMatrices(), label, x + (w - lw) / 2f, y + (h - SIZE_SMALL) / 2f, SIZE_SMALL, textCol);
    }

    private static int adjustAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0xFFFFFF);
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private float easeOutQuart(float x) { return 1f - (float) Math.pow(1f - x, 4f); }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean inRect(double mx, double my, int[] r) { return inRect(mx, my, r[0], r[1], r[2], r[3]); }

    private String pluralSaves(int n) {
        int m100 = n % 100, m10 = n % 10;
        if (m100 >= 11 && m100 <= 14) return n + " сохранений";
        if (m10 == 1) return n + " сохранение";
        if (m10 >= 2 && m10 <= 4) return n + " сохранения";
        return n + " сохранений";
    }

    private String pluralItems(int n) {
        int m100 = n % 100, m10 = n % 10;
        if (m100 >= 11 && m100 <= 14) return n + " предметов";
        if (m10 == 1) return n + " предмет";
        if (m10 >= 2 && m10 <= 4) return n + " предмета";
        return n + " предметов";
    }

    // ─── Ввод ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        computePanelSize();

        if (!onboardingDismissed && this.height > 480) {
            int[] r = toastRect();
            if (inRect(mx, my, r[0] + r[2] - 24, r[1] + 7, 16, 16)) { onboardingDismissed = true; return true; }
        }

        int px = panelX(), py = panelY();
        int headerY = py + 32;
        int lcX = px + PAD;
        int rcX = lcX + leftColW + COL_GAP;
        int rcW = (px + panelW - PAD) - rcX;

        if (inRect(mx, my, px + PAD, py + 12, 56, 11)) { backToGame(); return true; }

        int saveW = 92, saveH = 20;
        if (inRect(mx, my, lcX + leftColW - saveW, headerY - 3, saveW, saveH)) { saveCurrent(); return true; }

        int nameFieldY = headerY + 30, nameFieldH = 20;
        if (inRect(mx, my, lcX, nameFieldY, leftColW, nameFieldH)) { nameFocused = true; return true; }

        int listY = nameFieldY + nameFieldH + 8;
        int listBottom = py + panelH - PAD;
        int listH = listBottom - listY;
        int contentX = lcX + 6, contentY = listY + 6, contentW = leftColW - 12, contentH = listH - 12;
        List<InventoryLoadout> loadouts = LoadoutManager.loadouts;

        if (mx >= contentX && mx <= contentX + contentW && my >= contentY && my <= contentY + contentH) {
            int localY = (int) (my - contentY + listScrollAnim);
            int index = localY / (CARD_H + CARD_GAP);
            int within = localY % (CARD_H + CARD_GAP);
            if (index >= 0 && index < loadouts.size() && within <= CARD_H) {
                InventoryLoadout l = loadouts.get(index);
                int rowY = contentY + index * (CARD_H + CARD_GAP) - (int) listScrollAnim;
                if (inRect(mx, my, cardLoadButtonRect(contentX, rowY, contentW))) { startLoad(l); return true; }
                if (inRect(mx, my, cardDeleteButtonRect(contentX, rowY, contentW))) { deleteLoadout(l); return true; }
                selectLoadout(l);
                return true;
            }
        }

        int refreshW = 76, refreshH = 18;
        if (inRect(mx, my, rcX + rcW - refreshW, headerY - 2, refreshW, refreshH)) { refreshGhosts(); return true; }

        nameFocused = false;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        listScrollTarget -= (int) (vAmt * 22);
        if (listScrollTarget < 0) listScrollTarget = 0;
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (nameFocused) {
            if (nameInput.length() < 32 && !Character.isISOControl(c)) {
                nameInput = nameInput.substring(0, cursorPos) + c + nameInput.substring(cursorPos);
                cursorPos++;
            }
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameFocused) {
            if (keyCode == 259 && cursorPos > 0) {
                nameInput = nameInput.substring(0, cursorPos - 1) + nameInput.substring(cursorPos);
                cursorPos--;
                return true;
            }
            if (keyCode == 261 && cursorPos < nameInput.length()) {
                nameInput = nameInput.substring(0, cursorPos) + nameInput.substring(cursorPos + 1);
                return true;
            }
            if (keyCode == 263 && cursorPos > 0) { cursorPos--; return true; }
            if (keyCode == 262 && cursorPos < nameInput.length()) { cursorPos++; return true; }
            if (keyCode == 257 || keyCode == 335) { saveCurrent(); return true; }
            if (keyCode == 256) { nameFocused = false; return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}