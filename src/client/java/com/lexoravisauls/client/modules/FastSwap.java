package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
        SWAP_ITEMS.put("Трапка ФТ", Items.COBWEB); // Для ФТ обычно используют паутину

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
        if (mc.player == null || mc.currentScreen != null) return;
        if (!LexoraGui.moduleStates.getOrDefault("Fast Swap", false)) return;

        long window = mc.getWindow().getHandle();

        for (Map.Entry<String, Item> entry : SWAP_ITEMS.entrySet()) {
            String name = entry.getKey();
            Item targetItem = entry.getValue();

            // Получаем бинд из настроек
            int key = LexoraGui.numSettings.getOrDefault("Bind_" + name, -1f).intValue();
            if (key == -1) continue;

            boolean isPressed = isKeyPressed(window, key);
            boolean wasPr = wasPressed.getOrDefault(name, false);

            if (isPressed && !wasPr) {
                switchToItem(mc, targetItem);
            }
            wasPressed.put(name, isPressed);
        }
    }

    private static boolean isKeyPressed(long window, int key) {
        // 🔥 ФИКС ПАМЯТИ: Защита от вылета GLFW при проверке неизвестных кнопок
        try {
            if (key > 0 && key < 350) {
                // Клавиатура (коды GLFW до 350)
                return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            } else if (key < 0) {
                // Мышь (кнопки от 0 до 12)
                int mouseButton = Math.abs(key) - 1;
                if (mouseButton >= 0 && mouseButton < 12) {
                    return GLFW.glfwGetMouseButton(window, mouseButton) == GLFW.GLFW_PRESS;
                }
            }
        } catch (Exception ignored) {
            // Игнорируем любые сбои библиотеки
        }
        return false;
    }

    private static void switchToItem(MinecraftClient mc, Item targetItem) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == targetItem) {
                mc.player.getInventory().selectedSlot = i;
                break; // Предмет найден - меняем слот
            }
        }
    }
}