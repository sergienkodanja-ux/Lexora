package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class HealingHelper {

    public static Integer getHighlightColor(Item item) {
        if (!LexoraGui.moduleStates.getOrDefault("Healing Helper", false)) return null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;

        // 🔥 1. ПРОВЕРКА КУЛДАУНА (КД)
        // Если предмет сейчас в кулдауне (перезарядке) - сразу красим в красный
        if (mc.player.getItemCooldownManager().isCoolingDown(item.getDefaultStack())) {
            return 0xFF0000; // Красный (Использовать нельзя)
        }

        // 🔥 2. УЧЕТ ПОГЛОЩЕНИЯ (Желтые сердца)
        float hp = mc.player.getHealth();
        float absorption = mc.player.getAbsorptionAmount();
        float effectiveHp = hp + absorption; // Складываем красные и желтые сердца!

        int food = mc.player.getHungerManager().getFoodLevel();

        float gappleHp = LexoraGui.numSettings.getOrDefault("Gapple HP", 14.0f);
        float egappleHp = LexoraGui.numSettings.getOrDefault("E-Gapple HP", 8.0f);
        float potionHp = LexoraGui.numSettings.getOrDefault("Potion HP", 10.0f);

        int green = 0x00FF00;
        int yellow = 0xFFFF00;
        int red = 0xFF0000;

        // --- ЛОГИКА ДЛЯ ЗАЧАРОВАННОГО ЯБЛОКА (Чарка) ---
        if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            if (effectiveHp <= egappleHp) return green;       // 1 место: Спасет жизнь
            if (effectiveHp <= gappleHp) return yellow;      // 2 место: Можно, но лучше обычный гепл
            return null;                                      // ХП + Поглощения много — не трать
        }

        // --- ЛОГИКА ДЛЯ ОБЫЧНОГО ЗОЛОТОГО ЯБЛОКА (Гепл) ---
        else if (item == Items.GOLDEN_APPLE) {
            if (effectiveHp <= gappleHp && effectiveHp > egappleHp) return green; // 1 место
            if (effectiveHp <= egappleHp) return yellow;                          // 2 место
            return null;
        }

        // --- ЛОГИКА ДЛЯ ЗЕЛЬЯ ИСЦЕЛЕНИЯ (Сплешка) ---
        else if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            if (effectiveHp <= potionHp) return green;
            if (effectiveHp <= gappleHp) return yellow;
            return null;
        }

        // --- ЛОГИКА ДЛЯ ОБЫЧНОЙ ЕДЫ (Золотая морковь, Стейки) ---
        else if (item == Items.GOLDEN_CARROT || item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP) {
            // Если Эффективного ХП мало — есть морковку ОПАСНО (Красный)
            if (effectiveHp <= gappleHp) return red;

            // Если Эффективного ХП много и мы голодны — ИДЕАЛЬНО (Зеленый)
            if (food < 20) return green;

            return null;
        }

        return null;
    }

    public static void renderHighlight(DrawContext context, Item item, int x, int y) {
        Integer colorValue = getHighlightColor(item);
        if (colorValue == null) return;

        boolean pulsate = LexoraGui.moduleStates.getOrDefault("HH Pulsation", true);
        int alpha = LexoraGui.numSettings.getOrDefault("HH Alpha", 100f).intValue();

        if (pulsate) {
            // Чуть ускорил пульсацию, чтобы в файте было заметнее (было 200.0, стало 150.0)
            float sine = (float) Math.sin(System.currentTimeMillis() / 150.0);
            alpha = (int) (alpha * (0.6f + 0.4f * sine));
        }

        alpha = Math.max(0, Math.min(255, alpha));
        int finalColor = (alpha << 24) | (colorValue & 0xFFFFFF);

        context.fill(x, y, x + 16, y + 16, finalColor);
    }
}