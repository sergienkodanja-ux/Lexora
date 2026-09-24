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
        net.minecraft.client.MinecraftClient mc = (net.minecraft.client.MinecraftClient) (Object) this;
        // Если игра пытается открыть ванильное главное меню или закрывает экран в главном меню (screen == null)
        if (screen != null && (screen.getClass() == TitleScreen.class || screen instanceof TitleScreen)) {
            return new LexoraMainMenu();
        }
        if (screen == null && mc.world == null) {
            return new LexoraMainMenu();
        }
        return screen;
    }
}