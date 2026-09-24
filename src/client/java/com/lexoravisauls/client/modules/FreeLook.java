package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

public class FreeLook {
    public static boolean isPerspective = false;
    public static float cameraYaw = 0f;
    public static float cameraPitch = 0f;

    private static boolean wasPressed = false;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;

    public static boolean emotionRequestsFreeLook = false;
    private static boolean activatedByEmotion = false;

    public static int getFreeLookBindKey() {
        int bindKey = BindManager.getStoredBindValue("Free Look Action");
        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) {
            bindKey = ClientData.moduleBinds.getOrDefault("Free Look Action", GLFW.GLFW_KEY_UNKNOWN);
        }
        return bindKey;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getWindow() == null) return;

        boolean moduleEnabled = ClientData.moduleStates.getOrDefault("Free Look", false)
                || LexoraGui.moduleStates.getOrDefault("Free Look", false);

        int bindKey = getFreeLookBindKey();
        boolean keyHeld = moduleEnabled && bindKey != GLFW.GLFW_KEY_UNKNOWN && bindKey != -1
                && mc.currentScreen == null && isBindPressed(mc, bindKey);

        boolean isPressed = keyHeld || emotionRequestsFreeLook;
        boolean thisActivationIsFromEmotion = !keyHeld && emotionRequestsFreeLook;

        if (isPressed && !wasPressed) {
            isPerspective = true;
            activatedByEmotion = thisActivationIsFromEmotion;
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
            previousPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (isPressed && wasPressed) {
            activatedByEmotion = thisActivationIsFromEmotion;
        } else if (!isPressed && wasPressed) {
            reset(mc);
        }
        wasPressed = isPressed;
    }

    private static void reset(MinecraftClient mc) {
        if (isPerspective) {
            isPerspective = false;
            mc.options.setPerspective(previousPerspective);
        }
    }

    private static boolean isBindPressed(MinecraftClient mc, int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == -1) return false;
        long window = mc.getWindow().getHandle();
        return BindManager.isBindDown(window, key);
    }
}