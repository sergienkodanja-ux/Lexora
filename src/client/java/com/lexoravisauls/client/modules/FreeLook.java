package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

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

        // Получаем бинд
        int bindKey = LexoraGui.numSettings.getOrDefault("Free Look Action", -1f).intValue();

        // ИСПРАВЛЕНИЕ: Используем наш БЕЗОПАСНЫЙ метод проверки кнопок
        boolean isPressed = bindKey != -1 && mc.currentScreen == null && isBindPressed(mc, bindKey);

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

    // 🔥 БЕЗОПАСНЫЙ МЕТОД ПРОВЕРКИ (Убирает лаги и ошибку -1002) 🔥
    private static boolean isBindPressed(MinecraftClient mc, int key) {
        if (key == -1) return false;
        long window = mc.getWindow().getHandle();

        try {
            if (key < 0) {
                int mouseButton = Math.abs(key);

                // Переводим -1002 в нормальный индекс кнопки мыши
                if (mouseButton >= 100) {
                    mouseButton -= 100;
                } else {
                    mouseButton -= 1;
                }

                // GLFW поддерживает только мыши от 0 до 7 (защита от краша)
                if (mouseButton >= 0 && mouseButton <= 7) {
                    return GLFW.glfwGetMouseButton(window, mouseButton) == GLFW.GLFW_PRESS;
                }
                return false;
            }

            // Если это обычная кнопка клавиатуры (положительное число)
            return InputUtil.isKeyPressed(window, key);

        } catch (Exception e) {
            return false;
        }
    }
}