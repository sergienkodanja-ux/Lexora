package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

public class FastSwap {
    public static final Map<String, Item> SWAP_ITEMS = new LinkedHashMap<>();

    static {
        // ФТ Версия
        SWAP_ITEMS.put("Дезка", Items.ENDER_EYE);
        SWAP_ITEMS.put("Явная пыль", Items.SUGAR);
        SWAP_ITEMS.put("Божья аура", Items.PHANTOM_MEMBRANE);
        SWAP_ITEMS.put("Пласт", Items.DRIED_KELP);
        SWAP_ITEMS.put("Трапка ФТ", Items.COBWEB);

        // ХВ Версия
        SWAP_ITEMS.put("Стан", Items.NETHER_STAR);
        SWAP_ITEMS.put("Взр. штучка", Items.FIRE_CHARGE);
        SWAP_ITEMS.put("Трапка ХВ", Items.POPPED_CHORUS_FRUIT);
        SWAP_ITEMS.put("Взр. трапка", Items.PRISMARINE_SHARD);
        SWAP_ITEMS.put("Ком снега", Items.SNOWBALL);

        // Другое
        SWAP_ITEMS.put("Хорус", Items.CHORUS_FRUIT);
        SWAP_ITEMS.put("Эндер перл", Items.ENDER_PEARL);
        SWAP_ITEMS.put("Исцеление", Items.SPLASH_POTION);
    }

    private static final Map<String, Boolean> wasPressed = new LinkedHashMap<>();

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Если открыт чат или инвентарь — свап не работает
        if (mc.player == null || mc.currentScreen != null) return;
        if (!LexoraGui.moduleStates.getOrDefault("Fast Swap", false)) return;

        for (Map.Entry<String, Item> entry : SWAP_ITEMS.entrySet()) {
            String name = entry.getKey();
            Item targetItem = entry.getValue();

            int key = LexoraGui.numSettings.getOrDefault("Bind_" + name, -1f).intValue();
            if (key == -1) continue;

            // Используем исправленный метод проверки
            boolean isPressed = isKeyPressed(mc, key);
            boolean wasPr = wasPressed.getOrDefault(name, false);

            if (isPressed && !wasPr) {
                switchToItem(mc, targetItem);
            }
            wasPressed.put(name, isPressed);
        }
    }

    /**
     * Исправленный метод проверки клавиш и кнопок мыши (M4, M5 и т.д.)
     */
    private static boolean isKeyPressed(MinecraftClient mc, int key) {
        if (mc.currentScreen != null) return false;

        long window = mc.getWindow().getHandle();

        try {
            if (key < 0) {
                // ОБРАБОТКА МЫШИ
                // В твоей системе отрицательные числа — это мышь.
                // Обычно -100 это Mouse 1, -101 Mouse 2 и т.д.
                // Если система просто передает -1, -2, берем модуль.
                int mouseButton = Math.abs(key);

                // Если бинд идет в формате -101, -102 (стандарт для многих GUI)
                if (mouseButton >= 100) {
                    mouseButton -= 100;
                } else {
                    // Если бинд идет в формате -1, -2 (смещение на 1)
                    mouseButton -= 1;
                }

                // GLFW поддерживает кнопки от 0 до 7 (GLFW_MOUSE_BUTTON_1 до 8)
                // Mouse 4 — это индекс 3, Mouse 5 — это индекс 4.
                if (mouseButton >= 0 && mouseButton <= 7) {
                    return GLFW.glfwGetMouseButton(window, mouseButton) == GLFW.GLFW_PRESS;
                }
            } else {
                // ОБРАБОТКА КЛАВИАТУРЫ
                return InputUtil.isKeyPressed(window, key);
            }
        } catch (Exception ignored) {}

        return false;
    }

    private static void switchToItem(MinecraftClient mc, Item targetItem) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == targetItem) {
                mc.player.getInventory().selectedSlot = i;
                break;
            }
        }
    }
}