package com.lexoravisauls.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

/**
 * Кнопка Lexora для собственных экранов (LexoraBindsScreen, меню паузы и
 * т.д.). Визуал — тот же стиль, что и у главного меню, через LexoraStyle:
 * MSDF-шрифт, скруглённый фон, hover-анимация и плавное появление при
 * первом кадре рендера.
 *
 * (Кнопки самого Mojang/ваниль — список серверов, создание мира, настройки
 * и т.д. — получают тот же стиль отдельно, через ButtonWidgetMixin.)
 */
public class LexoraButtonWidget extends PressableWidget {
    private final PressAction onPress;

    private float hoverAnim     = 0f;
    private long  firstRenderMs = -1L;

    public LexoraButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // пусто — кастомный худ без озвучки
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (firstRenderMs < 0) firstRenderMs = now;
        float appear = LexoraStyle.easeOutCubic(Math.min(1f, (now - firstRenderMs) / 180f));

        float targetHover = (this.isHovered() && this.active) ? 1f : 0f;
        hoverAnim += (targetHover - hoverAnim) * 0.18f;
        if (hoverAnim < 0.005f) hoverAnim = 0f;

        int x = getX(), y = getY();
        float cx = x + width / 2f, cy = y + height / 2f;
        float scale = (1f + hoverAnim * 0.02f) * LexoraStyle.lerp(0.85f, 1f, appear);

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-cx, -cy + (1f - appear) * 6f, 0);

        int bg = this.active
                ? LexoraStyle.blendColors(LexoraStyle.COL_BTN_NORMAL, LexoraStyle.COL_BTN_HOVER, hoverAnim)
                : LexoraStyle.COL_BTN_DISABLED;
        LexoraStyle.drawSmoothRect(context, x, y, width, height, LexoraStyle.adjustAlpha(bg, appear));

        int textBase = this.active
                ? LexoraStyle.blendColors(LexoraStyle.COL_TEXT_PRIMARY, LexoraStyle.COL_TEXT_WHITE, hoverAnim)
                : LexoraStyle.COL_TEXT_DISABLED;
        int textColor = LexoraStyle.adjustAlpha(textBase, appear);

        String text = getMessage().getString();
        float tw = LexoraStyle.font().getWidth(text, LexoraStyle.SIZE_LABEL);
        LexoraStyle.font().draw(context.getMatrices(), text,
                x + (width - tw) / 2f, y + (height - LexoraStyle.SIZE_LABEL) / 2f - 0.5f,
                LexoraStyle.SIZE_LABEL, textColor);

        context.getMatrices().pop();
    }

    public interface PressAction {
        void onPress(LexoraButtonWidget button);
    }
}