package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class ItemScroller {
    private static Slot lastSlot = null;
    private static long lastClickTime = 0;

    public static void handleDrag(HandledScreen<?> screen, Slot focusedSlot) {
        if (!LexoraGui.moduleStates.getOrDefault("Item Scroller", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        // Проверяем, зажат ли Shift и Левая Кнопка Мыши
        boolean isShift = Screen.hasShiftDown();
        boolean isLeftClick = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isShift && isLeftClick) {
            if (focusedSlot != null && focusedSlot.hasStack()) {
                int speedTicks = LexoraGui.numSettings.getOrDefault("Scroller Speed", 2f).intValue();
                long delayMs = speedTicks * 50L; // 1 тик = 50мс
                long currentTime = System.currentTimeMillis();

                // Кликаем, если навели на новый слот ИЛИ прошло время задержки
                if (focusedSlot != lastSlot || (currentTime - lastClickTime >= delayMs)) {
                    mc.interactionManager.clickSlot(
                            screen.getScreenHandler().syncId,
                            focusedSlot.id,
                            0, // ЛКМ
                            SlotActionType.QUICK_MOVE, // Shift-Click
                            mc.player
                    );
                    lastSlot = focusedSlot;
                    lastClickTime = currentTime;
                }
            }
        } else {
            lastSlot = null; // Сброс при отпускании кнопок
        }
    }

    public static void tick() {} // Заглушка, чтобы твой старый код не выдавал ошибку
}