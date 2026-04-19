package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.PvPSave;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class MixinGameMenuScreen extends Screen {

    protected MixinGameMenuScreen(Text title) {
        super(title);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        boolean inPvP = PvPSave.isInPvP();
        int timeLeft = PvPSave.getRemainingSeconds(); // Получаем секунды из босс-бара

        for (Object element : this.children()) {
            if (element instanceof ClickableWidget widget) {
                String text = widget.getMessage().getString().toLowerCase();

                // Ищем кнопку отключения
                if (text.contains("отключиться") || text.contains("выйти") ||
                        text.contains("disconnect") || text.contains("menu.disconnect") || text.contains("в бою")) {

                    if (inPvP) {
                        widget.active = false;
                        // Пишем точное время КД, которое взяли из Босс-Бара сервера!
                        widget.setMessage(Text.literal("§cВ БОЮ! (" + timeLeft + "с)"));
                    } else {
                        // Если КД спало - моментально возвращаем нормальную кнопку
                        if (!widget.active || text.contains("Вы в пвп режиме!Выход запрещен!")) {
                            widget.active = true;
                            widget.setMessage(Text.translatable("menu.disconnect"));
                        }
                    }
                }
            }
        }
    }
}