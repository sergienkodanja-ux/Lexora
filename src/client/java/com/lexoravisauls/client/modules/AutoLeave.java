package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;

public class AutoLeave {
    private static int nearTimer = 0;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!LexoraGui.moduleStates.getOrDefault("Auto Leave", false)) {
            nearTimer = 0;
            return;
        }

        // Таймер для /near max
        int cooldownSec = LexoraGui.numSettings.getOrDefault("Near Cooldown", 10f).intValue();
        nearTimer++;

        if (nearTimer >= cooldownSec * 20) {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendChatCommand("near max");
            }
            nearTimer = 0;
        }
    }

    public static void executeLeave(MinecraftClient mc) {
        String mode = LexoraGui.modeSettings.getOrDefault("Leave Mode", "Disconnect");

        // Сразу выключаем модуль, чтобы не ливнуло при перезаходе
        LexoraGui.moduleStates.put("Auto Leave", false);

        if (mode.equals("Hub")) {
            mc.getNetworkHandler().sendChatCommand("hub");
        } else {
            // Полный выход с сервера в меню
            if (mc.world != null) {
                mc.world.disconnect();
            }
            mc.disconnect(new MultiplayerScreen(new TitleScreen()));
        }
    }
}