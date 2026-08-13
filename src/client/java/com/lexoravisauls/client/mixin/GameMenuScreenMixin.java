package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraBindsScreen;
import com.lexoravisauls.client.gui.LexoraButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addBindsButton(CallbackInfo ci) {
        // Находим самую нижнюю уже существующую кнопку (это будет "Выйти из
        // игры"/"Сохранить и выйти") и ставим "Бинды" под ней с отступом.
        // Так кнопка не налезает ни на что независимо от того, сколько
        // строк в меню паузы (singleplayer/multiplayer отличаются).
        int maxBottom = this.height / 4 + 96; // запасной вариант, если вдруг ничего не нашли
        boolean found = false;
        for (var element : this.children()) {
            if (element instanceof ClickableWidget widget) {
                int bottom = widget.getY() + widget.getHeight();
                if (!found || bottom > maxBottom) {
                    maxBottom = bottom;
                    found = true;
                }
            }
        }

        this.addDrawableChild(new LexoraButtonWidget(
                this.width / 2 - 102, maxBottom + 8, 204, 20,
                Text.literal("КейБинды"),
                btn -> MinecraftClient.getInstance().setScreen(new LexoraBindsScreen(this))
        ));
    }
}