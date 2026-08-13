package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.core.BindManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Мини-окно "что забинджено на эту клавишу". Открывается при клике на
 * уже занятую клавишу/кнопку мыши в LexoraBindsScreen.
 */
public class LexoraBindInfoScreen extends Screen {
    private final Screen bindsParent; // экран, который был ДО LexoraBindsScreen (пауза/гл.меню)
    private final int keyCode;
    private final String keyLabel;

    private int panelX, panelY;
    private static final int PANEL_W = 280;
    private static final int PANEL_H = 86;

    public LexoraBindInfoScreen(Screen bindsParent, int keyCode, String keyLabel) {
        super(Text.literal("Бинд"));
        this.bindsParent = bindsParent;
        this.keyCode = keyCode;
        this.keyLabel = keyLabel;
    }

    @Override
    protected void init() {
        panelX = this.width / 2 - PANEL_W / 2;
        panelY = this.height / 2 - PANEL_H / 2 - 20;

        int btnY = panelY + PANEL_H + 14;

        this.addDrawableChild(new LexoraButtonWidget(panelX, btnY, PANEL_W, 24,
                Text.literal("Изменить"), btn -> {
            BindManager.clearAnyBindOnKey(keyCode);
            this.client.setScreen(new LexoraBindModeScreen(bindsParent, keyCode, keyLabel));
        }));

        int halfW = (PANEL_W - 8) / 2;
        this.addDrawableChild(new LexoraButtonWidget(panelX, btnY + 30, halfW, 24,
                Text.literal("Удалить"), btn -> {
            BindManager.clearAnyBindOnKey(keyCode);
            this.client.setScreen(new LexoraBindsScreen(bindsParent));
        }));
        this.addDrawableChild(new LexoraButtonWidget(panelX + halfW + 8, btnY + 30, halfW, 24,
                Text.literal("Закрыть"), btn -> this.client.setScreen(new LexoraBindsScreen(bindsParent))));
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean keyPressed(int code, int scanCode, int modifiers) {
        if (code == 256) { // ESC
            this.client.setScreen(new LexoraBindsScreen(bindsParent));
            return true;
        }
        return super.keyPressed(code, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // фон рисуем сами ниже
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        LexoraStyle.drawSmoothRect(context, panelX, panelY, PANEL_W, PANEL_H, 10f, LexoraStyle.COL_PANEL_BG);

        String title = "Клавиша: " + keyLabel;
        float tw = LexoraStyle.font().getWidth(title, LexoraStyle.SIZE_LABEL);
        LexoraStyle.font().draw(context.getMatrices(), title, panelX + PANEL_W / 2f - tw / 2f, panelY + 14,
                LexoraStyle.SIZE_LABEL, LexoraStyle.COL_TEXT_PRIMARY);

        String module = BindManager.findModuleOnKey(keyCode);
        String command = BindManager.getCommandForKey(keyCode);

        String typeLine, modeLine;
        if (module != null) {
            typeLine = "Модуль: " + module;
            modeLine = "Режим: " + (BindManager.getModuleBindMode(module) == BindManager.BindMode.HOLD ? "Удержание" : "Однократно");
        } else if (command != null) {
            typeLine = "Команда: " + command;
            modeLine = "Режим: " + (BindManager.getCommandBindMode(keyCode) == BindManager.BindMode.HOLD ? "Удержание" : "Однократно");
        } else {
            typeLine = "Бинд не найден";
            modeLine = "";
        }

        float w1 = LexoraStyle.font().getWidth(typeLine, LexoraStyle.SIZE_SMALL);
        LexoraStyle.font().draw(context.getMatrices(), typeLine, panelX + PANEL_W / 2f - w1 / 2f, panelY + 40,
                LexoraStyle.SIZE_SMALL, LexoraStyle.COL_TEXT_PRIMARY);

        if (!modeLine.isEmpty()) {
            float w2 = LexoraStyle.font().getWidth(modeLine, LexoraStyle.SIZE_SMALL);
            LexoraStyle.font().draw(context.getMatrices(), modeLine, panelX + PANEL_W / 2f - w2 / 2f, panelY + 58,
                    LexoraStyle.SIZE_SMALL, LexoraStyle.COL_TEXT_SECONDARY);
        }

        super.render(context, mouseX, mouseY, delta);
    }
}