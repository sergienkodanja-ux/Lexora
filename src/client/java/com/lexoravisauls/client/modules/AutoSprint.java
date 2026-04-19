package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;

public class AutoSprint {
    public static void tick() {
        // Если модуль выключен - ничего не делаем
        if (!LexoraGui.moduleStates.getOrDefault("Auto Sprint", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        // Легитная проверка: если игрок существует, нажимает "Вперед", не крадется и не уперся в стену
        if (mc.player != null && mc.options.forwardKey.isPressed() && !mc.player.isSneaking() && !mc.player.horizontalCollision) {
            mc.player.setSprinting(true);
        }
    }
}