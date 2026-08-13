package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.core.BindManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Финальный шаг: выбор МОДУЛЯ (поиск по категориям из LexoraModuleSource,
 * с заголовками категорий и скроллом — модулей у вас ~57, без этого было
 * бы неудобно) или ввод произвольной КОМАНДЫ. Кнопка "Установить"
 * сохраняет бинд через BindManager и возвращает на LexoraBindsScreen.
 *
 * Один текстовый филд используется для обоих режимов (поиск модуля ИЛИ
 * текст команды) — переключение вкладки очищает поле и меняет подсказку.
 */
public class LexoraBindPickerScreen extends Screen {
    private final Screen bindsParent;
    private final int keyCode;
    private final String keyLabel;
    private final BindManager.BindMode mode;

    private boolean moduleTab = true;

    private LexoraTextFieldWidget inputField;
    private LexoraButtonWidget setButton;

    private String selectedModule = null;
    private int scrollOffset = 0;
    private static final int ROW_H = 18;

    private record Row(boolean header, String text) {}
    private final List<Row> rows = new ArrayList<>();

    private int panelX, panelY;
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 220;
    private int listY, listH;

    public LexoraBindPickerScreen(Screen bindsParent, int keyCode, String keyLabel, BindManager.BindMode mode) {
        super(Text.literal("Выбор бинда"));
        this.bindsParent = bindsParent;
        this.keyCode = keyCode;
        this.keyLabel = keyLabel;
        this.mode = mode;
    }

    @Override
    protected void init() {
        panelX = this.width / 2 - PANEL_W / 2;
        panelY = this.height / 2 - PANEL_H / 2 - 10;

        int tabW = (PANEL_W - 8) / 2;
        this.addDrawableChild(new LexoraButtonWidget(panelX, panelY + 30, tabW, 22,
                Text.literal("Модуль"), btn -> switchTab(true)));
        this.addDrawableChild(new LexoraButtonWidget(panelX + tabW + 8, panelY + 30, tabW, 22,
                Text.literal("Команда"), btn -> switchTab(false)));

        inputField = new LexoraTextFieldWidget(this.textRenderer, panelX + 12, panelY + 62, PANEL_W - 24, 20,
                Text.literal(""));
        inputField.setMaxLength(200);
        inputField.setChangedListener(s -> refreshFilter());
        this.addDrawableChild(inputField);

        setButton = new LexoraButtonWidget(panelX, panelY + PANEL_H + 14, PANEL_W, 24,
                Text.literal("Установить"), btn -> applyBind());
        this.addDrawableChild(setButton);

        this.addDrawableChild(new LexoraButtonWidget(panelX, panelY + PANEL_H + 44, PANEL_W, 22,
                Text.literal("← Назад"), btn -> this.client.setScreen(new LexoraBindsScreen(bindsParent))));

        switchTab(true);
    }

    private void switchTab(boolean module) {
        this.moduleTab = module;
        this.selectedModule = null;
        this.scrollOffset = 0;
        if (inputField != null) {
            inputField.setText("");
            inputField.setPlaceholder(Text.literal(module ? "Введите название модуля..." : "/команда аргументы..."));
        }
        refreshFilter();
    }

    private void refreshFilter() {
        rows.clear();
        scrollOffset = 0;
        if (!moduleTab) return;

        String q = inputField.getText().toLowerCase();
        for (Map.Entry<String, List<String>> cat : LexoraModuleSource.getCategories().entrySet()) {
            List<String> matches = new ArrayList<>();
            for (String name : cat.getValue()) {
                if (q.isEmpty() || name.toLowerCase().contains(q)) matches.add(name);
            }
            if (!matches.isEmpty()) {
                rows.add(new Row(true, cat.getKey()));
                for (String m : matches) rows.add(new Row(false, m));
            }
        }

        if (selectedModule != null) {
            boolean stillThere = rows.stream().anyMatch(r -> !r.header() && r.text().equals(selectedModule));
            if (!stillThere) selectedModule = null;
        }
    }

    private void applyBind() {
        if (moduleTab) {
            if (selectedModule == null) return;
            BindManager.setModuleBind(selectedModule, keyCode, mode);
        } else {
            String cmd = inputField.getText().trim();
            if (cmd.isEmpty()) return;
            BindManager.setCommandBind(keyCode, cmd, mode);
        }
        this.client.setScreen(new LexoraBindsScreen(bindsParent));
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean keyPressed(int code, int scanCode, int modifiers) {
        if (code == 256 && !inputField.isFocused()) {
            this.client.setScreen(new LexoraBindsScreen(bindsParent));
            return true;
        }
        return super.keyPressed(code, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (moduleTab && mx >= panelX + 10 && mx <= panelX + PANEL_W - 10 && my >= listY && my <= listY + listH) {
            int maxScroll = Math.max(0, rows.size() * ROW_H - listH);
            scrollOffset -= (int) (vAmt * 24);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && moduleTab) {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                int ry = listY + i * ROW_H - scrollOffset;
                if (ry + ROW_H < listY || ry > listY + listH) continue;
                if (r.header()) continue;
                if (mx >= panelX + 12 && mx <= panelX + PANEL_W - 12 && my >= ry && my <= ry + ROW_H) {
                    selectedModule = r.text();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        LexoraStyle.drawSmoothRect(context, panelX, panelY, PANEL_W, PANEL_H, 10f, LexoraStyle.COL_PANEL_BG);

        String title = "Бинд на " + keyLabel + " (" + (mode == BindManager.BindMode.HOLD ? "удержание" : "однократно") + ")";
        float tw = LexoraStyle.font().getWidth(title, LexoraStyle.SIZE_SMALL);
        LexoraStyle.font().draw(context.getMatrices(), title, panelX + PANEL_W / 2f - tw / 2f, panelY + 10,
                LexoraStyle.SIZE_SMALL, LexoraStyle.COL_TEXT_SECONDARY);

        if (moduleTab) {
            listY = panelY + 90;
            listH = PANEL_H - 100;

            context.enableScissor(panelX + 10, listY, panelX + PANEL_W - 10, listY + listH);
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                int ry = listY + i * ROW_H - scrollOffset;
                if (ry + ROW_H < listY || ry > listY + listH) continue;

                if (r.header()) {
                    LexoraStyle.font().draw(context.getMatrices(), r.text().toUpperCase(),
                            panelX + 14, ry + 4, LexoraStyle.SIZE_TINY, LexoraStyle.COL_TEXT_SECONDARY);
                    continue;
                }

                boolean sel = r.text().equals(selectedModule);
                boolean hov = mouseX >= panelX + 12 && mouseX <= panelX + PANEL_W - 12
                        && mouseY >= ry && mouseY <= ry + ROW_H;

                int bg = sel ? LexoraStyle.COL_BTN_HOVER : (hov ? LexoraStyle.COL_KEY_HOVER : LexoraStyle.COL_KEY_BG);
                LexoraStyle.drawSmoothRect(context, panelX + 16, ry + 1, PANEL_W - 28, ROW_H - 2, 4f, bg);

                int textCol = sel ? LexoraStyle.COL_TEXT_WHITE : LexoraStyle.COL_TEXT_PRIMARY;
                LexoraStyle.font().draw(context.getMatrices(), r.text(), panelX + 22, ry + 4,
                        LexoraStyle.SIZE_SMALL, textCol);
            }
            context.disableScissor();

            if (rows.isEmpty()) {
                String empty = "Ничего не найдено";
                float ew = LexoraStyle.font().getWidth(empty, LexoraStyle.SIZE_SMALL);
                LexoraStyle.font().draw(context.getMatrices(), empty, panelX + PANEL_W / 2f - ew / 2f, listY + 10,
                        LexoraStyle.SIZE_SMALL, LexoraStyle.COL_TEXT_MUTED);
            }
        }

        setButton.active = moduleTab ? (selectedModule != null) : !inputField.getText().isBlank();

        super.render(context, mouseX, mouseY, delta);
    }
}