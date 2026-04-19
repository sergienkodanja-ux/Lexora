package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.main_menu.LexoraMainMenu;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(net.minecraft.client.MinecraftClient.class)
public class MainMenuMixin {

    // Безопасно подменяем аргумент перед открытием экрана
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen modifyScreen(Screen screen) {
        // Если игра пытается открыть ванильное главное меню...
        if (screen != null && screen.getClass() == TitleScreen.class) {
            return new LexoraMainMenu();
        }
        return screen;
    }
}