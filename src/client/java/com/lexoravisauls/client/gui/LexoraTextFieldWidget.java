package com.lexoravisauls.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * TextFieldWidget со скруглённым тёмным фоном в стиле Lexora вместо
 * стандартного ваниль-квадрата. Сам текст/курсор всё ещё рисует ваниль
 * (шрифт набора остаётся обычным) — переписывать весь ввод текста на
 * MSDF рискованно и не требовалось, жалоба была именно на фон/рамку.
 */
public class LexoraTextFieldWidget extends TextFieldWidget {

    public LexoraTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
        super(textRenderer, x, y, width, height, message);
        this.setDrawsBackground(false);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int bg = this.isFocused() ? LexoraStyle.COL_FIELD_FOCUSED : LexoraStyle.COL_FIELD_BG;
        LexoraStyle.drawSmoothRect(context, getX(), getY(), width, height, 5f, bg);
        super.renderWidget(context, mouseX, mouseY, delta);
    }
}