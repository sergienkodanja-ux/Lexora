package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;

public class ShiftTap {
    private static int shiftTimer = 0;

    public static void onHit(boolean isCrit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (!LexoraGui.moduleStates.getOrDefault("Shift Tap", false)) return;

        String mode = LexoraGui.modeSettings.getOrDefault("Shift Mode", "Все удары");
        if (mode.equals("Только криты") && !isCrit) return;

        // Моментально зажимаем шифт
        mc.options.sneakKey.setPressed(true);

        // 🔥 Ставим таймер всего на 2 тика (очень быстрый микро-присед)
        shiftTimer = 2;
    }

    public static void tick() {
        if (shiftTimer > 0) {
            shiftTimer--;

            if (shiftTimer <= 0) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.options != null) {
                    // Резко отпускаем
                    mc.options.sneakKey.setPressed(false);
                }
            }
        }
    }
}