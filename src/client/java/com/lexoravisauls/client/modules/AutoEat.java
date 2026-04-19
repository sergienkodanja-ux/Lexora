package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.Hand;

public class AutoEat {
    private static boolean isEating = false;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        // Если модуль выключили, прекращаем есть
        if (!LexoraGui.moduleStates.getOrDefault("Auto Eat", false)) {
            if (isEating) stopEating(mc);
            return;
        }

        HungerManager hungerManager = mc.player.getHungerManager();
        int foodLevel = hungerManager.getFoodLevel();

        // Порог: при каком значении голода начинать кушать (14 = 7 окорочков)
        int threshold = LexoraGui.numSettings.getOrDefault("Eat Threshold", 14f).intValue();

        boolean hasFoodInMainHand = isFood(mc.player.getMainHandStack());
        boolean hasFoodInOffHand = isFood(mc.player.getOffHandStack());
        boolean hasFoodInHands = hasFoodInMainHand || hasFoodInOffHand;

        if (isEating) {
            // Если наелись (20/20) или убрали еду из рук - перестаем
            if (foodLevel >= 20 || !hasFoodInHands) {
                stopEating(mc);
            } else {
                // Продолжаем удерживать кнопку
                mc.options.useKey.setPressed(true);
            }
            return;
        }

        // Если проголодались И еда уже в руках
        if (foodLevel <= threshold && hasFoodInHands) {
            Hand handToUse = hasFoodInMainHand ? Hand.MAIN_HAND : Hand.OFF_HAND;

            // 🔥 ФИКС: Делаем первоначальный "клик", чтобы игра начала анимацию поедания
            mc.interactionManager.interactItem(mc.player, handToUse);

            // Зажимаем кнопку, чтобы доесть
            mc.options.useKey.setPressed(true);
            isEating = true;
        }
    }

    private static void stopEating(MinecraftClient mc) {
        mc.options.useKey.setPressed(false);
        isEating = false;
    }

    private static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // Проверяем, съедобный ли предмет (1.21.x+)
        return stack.contains(DataComponentTypes.FOOD) || stack.contains(DataComponentTypes.CONSUMABLE);
    }
}