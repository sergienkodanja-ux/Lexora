package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Глобально перерисовывает renderWidget() у ВСЕХ ButtonWidget в игре —
 * ваниль (список серверов, создание мира, настройки, меню паузы) и моды —
 * в стиле Lexora: MSDF-шрифт, скруглённые углы, hover-анимация и плавное
 * появление при первом рисовании кнопки.
 *
 * Благодаря этому отдельно переписывать каждый ваниль-экран не нужно —
 * как только на экране создаётся обычный ButtonWidget (через
 * ButtonWidget.builder(...).build()), он автоматически выглядит как
 * в главном меню.
 *
 * Таргетим именно PressableWidget, а не ButtonWidget — потому что
 * renderWidget() в актуальных маппингах объявлен (с телом) в
 * PressableWidget, а ButtonWidget его не переопределяет (отсюда и ошибка
 * "Cannot resolve method renderWidget in target class", если мискинить
 * прямо в ButtonWidget).
 *
 * Внутри стоит проверка instanceof ButtonWidget, чтобы не задеть другие
 * PressableWidget-наследники с другой логикой отрисовки (например,
 * CheckboxWidget) — для них метод просто отработает как раньше.
 *
 * ВАЖНО: этот класс нужно зарегистрировать в вашем *.mixins.json,
 * в секции "client", например:
 *   "com.lexoravisauls.client.mixin.ButtonWidgetMixin"
 * Без этого миксин просто не подключится фабриком.
 */
@Mixin(PressableWidget.class)
public abstract class ButtonWidgetMixin extends ClickableWidget {

    // Конструктор-заглушка — нужен только чтобы код компилировался
    // (реальные экземпляры создаёт конструктор ButtonWidget, этот
    // конструктор миксина никогда не вызывается напрямую в runtime).
    protected ButtonWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Unique private float lexora$hoverAnim = 0f;
    @Unique private long  lexora$firstRenderMs = -1L;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void lexora$renderWidget(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof ButtonWidget)) return; // не трогаем CheckboxWidget и прочих
        ci.cancel();

        long now = System.currentTimeMillis();
        if (lexora$firstRenderMs < 0) lexora$firstRenderMs = now;
        float appear = LexoraStyle.easeOutCubic(Math.min(1f, (now - lexora$firstRenderMs) / 180f));

        float targetHover = (this.isHovered() && this.active) ? 1f : 0f;
        lexora$hoverAnim += (targetHover - lexora$hoverAnim) * 0.18f;
        if (lexora$hoverAnim < 0.005f) lexora$hoverAnim = 0f;

        int x = this.getX(), y = this.getY();
        float cx = x + this.width / 2f, cy = y + this.height / 2f;
        float scale = (1f + lexora$hoverAnim * 0.02f) * LexoraStyle.lerp(0.85f, 1f, appear);

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-cx, -cy + (1f - appear) * 6f, 0);

        int bg = this.active
                ? LexoraStyle.blendColors(LexoraStyle.COL_BTN_NORMAL, LexoraStyle.COL_BTN_HOVER, lexora$hoverAnim)
                : LexoraStyle.COL_BTN_DISABLED;
        LexoraStyle.drawSmoothRect(context, x, y, this.width, this.height, LexoraStyle.adjustAlpha(bg, appear));

        int textBase = this.active
                ? LexoraStyle.blendColors(LexoraStyle.COL_TEXT_PRIMARY, LexoraStyle.COL_TEXT_WHITE, lexora$hoverAnim)
                : LexoraStyle.COL_TEXT_DISABLED;
        int textColor = LexoraStyle.adjustAlpha(textBase, appear);

        String text = this.getMessage().getString();
        float tw = LexoraStyle.font().getWidth(text, LexoraStyle.SIZE_LABEL);
        LexoraStyle.font().draw(context.getMatrices(), text,
                x + (this.width - tw) / 2f, y + (this.height - LexoraStyle.SIZE_LABEL) / 2f - 0.5f,
                LexoraStyle.SIZE_LABEL, textColor);

        context.getMatrices().pop();
    }
}