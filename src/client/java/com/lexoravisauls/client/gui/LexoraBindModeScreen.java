package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.core.BindManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Мини-окно выбора режима нового бинда: Удержание (Hold) или Однократно
 * (Toggle). Открывается при клике на свободную клавишу, либо при нажатии
 * "Изменить" в LexoraBindInfoScreen.
 */
public class LexoraBindModeScreen extends Screen {
    private final Screen bindsParent;
    private final int keyCode;
    private final String keyLabel;

    private int panelX, panelY;
    private static final int PANEL_W = 280;
    private static final int PANEL_H = 70;

    public LexoraBindModeScreen(Screen bindsParent, int keyCode, String keyLabel) {
        super(Text.literal("Новый бинд"));
        this.bindsParent = bindsParent;
        this.keyCode = keyCode;
        this.keyLabel = keyLabel;
    }

    @Override
    protected void init() {
        panelX = this.width / 2 - PANEL_W / 2;
        panelY = this.height / 2 - PANEL_H / 2 - 20;

        int btnY = panelY + PANEL_H + 14;
        int halfW = (PANEL_W - 8) / 2;

        this.addDrawableChild(new LexoraButtonWidget(panelX, btnY, halfW, 26,
                Text.literal("Удержание"), btn ->
                this.client.setScreen(new LexoraBindPickerScreen(bindsParent, keyCode, keyLabel, BindManager.BindMode.HOLD))));

        this.addDrawableChild(new LexoraButtonWidget(panelX + halfW + 8, btnY, halfW, 26,
                Text.literal("Однократно"), btn ->
                this.client.setScreen(new LexoraBindPickerScreen(bindsParent, keyCode, keyLabel, BindManager.BindMode.TOGGLE))));

        this.addDrawableChild(new LexoraButtonWidget(panelX, btnY + 32, PANEL_W, 22,
                Text.literal("← Назад"), btn -> this.client.setScreen(new LexoraBindsScreen(bindsParent))));
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean keyPressed(int code, int scanCode, int modifiers) {
        if (code == 256) {
            this.client.setScreen(new LexoraBindsScreen(bindsParent));
            return true;
        }
        return super.keyPressed(code, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        LexoraStyle.drawSmoothRect(context, panelX, panelY, PANEL_W, PANEL_H, 10f, LexoraStyle.COL_PANEL_BG);

        String title = "Новый бинд на: " + keyLabel;
        float tw = LexoraStyle.font().getWidth(title, LexoraStyle.SIZE_LABEL);
        LexoraStyle.font().draw(context.getMatrices(), title, panelX + PANEL_W / 2f - tw / 2f, panelY + 14,
                LexoraStyle.SIZE_LABEL, LexoraStyle.COL_TEXT_PRIMARY);

        String hint = "Выберите режим срабатывания:";
        float hw = LexoraStyle.font().getWidth(hint, LexoraStyle.SIZE_SMALL);
        LexoraStyle.font().draw(context.getMatrices(), hint, panelX + PANEL_W / 2f - hw / 2f, panelY + 36,
                LexoraStyle.SIZE_SMALL, LexoraStyle.COL_TEXT_SECONDARY);

        super.render(context, mouseX, mouseY, delta);
    }
}