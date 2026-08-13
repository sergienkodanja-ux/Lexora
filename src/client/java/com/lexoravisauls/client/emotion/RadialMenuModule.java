package com.lexoravisauls.client.emotion;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.emotion.RadialMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public class RadialMenuModule {

    private static boolean wasKeyPressed = false;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getWindow() == null) return;

        int bindKey = ClientData.moduleBinds.getOrDefault("Radial Menu", net.minecraft.client.util.InputUtil.UNKNOWN_KEY.getCode());

        boolean isPressed = bindKey != -1
                && bindKey != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN
                && InputUtil.isKeyPressed(mc.getWindow().getHandle(), bindKey);

        if (isPressed && !wasKeyPressed && mc.currentScreen == null) {
            mc.setScreen(new RadialMenuScreen());
        }

        wasKeyPressed = isPressed;
    }
}