package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn {
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (LexoraGui.moduleStates.getOrDefault("Auto Respawn", false)) {
            // Если открыт экран смерти
            if (mc.currentScreen instanceof DeathScreen) {
                mc.player.requestRespawn(); // Посылаем пакет о возрождении
                mc.setScreen(null); // Закрываем экран смерти
            }
        }
    }
}