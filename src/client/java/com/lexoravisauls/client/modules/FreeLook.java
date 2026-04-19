package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;

public class FreeLook {
    public static boolean isPerspective = false;
    public static float cameraYaw = 0f;
    public static float cameraPitch = 0f;

    private static boolean wasPressed = false;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!LexoraGui.moduleStates.getOrDefault("Free Look", false)) {
            reset(mc);
            return;
        }

        // 🔥 Вот эта исправленная строка с "Action" вместо "Key" 🔥
        int bindKey = LexoraGui.numSettings.getOrDefault("Free Look Action", -1f).intValue();
        boolean isPressed = bindKey != -1 && InputUtil.isKeyPressed(mc.getWindow().getHandle(), bindKey);

        if (isPressed && !wasPressed) {
            // Включаем обзор
            isPerspective = true;
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
            previousPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!isPressed && wasPressed) {
            // Выключаем обзор
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
}