package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.ClientData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class TapeMouse {
    private static long lastClickMs = 0L;

    private TapeMouse() {
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        if (client.currentScreen != null) {
            return;
        }

        if (!ClientData.moduleStates.getOrDefault("Tape Mouse", false)) {
            return;
        }

        float cps = ClientData.numSettings.getOrDefault("Tape Mouse CPS", 10.0f);
        cps = clamp(cps, 1.0f, 20.0f);

        long intervalMs = Math.max(50L, Math.round(1000.0f / cps));
        long now = System.currentTimeMillis();

        if (now - lastClickMs < intervalMs) {
            return;
        }

        lastClickMs = now;

        String buttonMode = ClientData.modeSettings.getOrDefault("Tape Mouse Button", "Left");

        int mouseButton = buttonMode.equalsIgnoreCase("Right")
                ? GLFW.GLFW_MOUSE_BUTTON_RIGHT
                : GLFW.GLFW_MOUSE_BUTTON_LEFT;

        InputUtil.Key key = InputUtil.Type.MOUSE.createFromCode(mouseButton);

        KeyBinding.onKeyPressed(key);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}