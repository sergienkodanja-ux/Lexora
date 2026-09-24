package com.lexoravisauls.client.emotion;

import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class RadialMenuModule {

    private static boolean wasKeyPressed = false;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getWindow() == null) return;

        int bindKey = BindManager.getStoredBindValue("Radial Menu");
        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) {
            bindKey = ClientData.moduleBinds.getOrDefault("Radial Menu", GLFW.GLFW_KEY_UNKNOWN);
        }

        boolean isPressed = bindKey != GLFW.GLFW_KEY_UNKNOWN
                && bindKey != -1
                && BindManager.isBindDown(mc.getWindow().getHandle(), bindKey);

        if (isPressed && !wasKeyPressed && mc.currentScreen == null) {
            mc.setScreen(new RadialMenuScreen());
        }

        wasKeyPressed = isPressed;
    }
}