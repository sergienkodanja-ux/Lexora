package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;

public class AutoSprint {
    public static void tick() {
        if (!LexoraGui.moduleStates.getOrDefault("Auto Sprint", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Блокируем автоспринт при инвентарном свапе
        if (ItemSwap.shouldSuppressMovement()) {
            return;
        }

        if (mc.options.forwardKey.isPressed()
                && !mc.player.isSneaking()
                && !mc.player.horizontalCollision) {
            mc.player.setSprinting(true);
            mc.options.sprintKey.setPressed(true);
        }
    }
}