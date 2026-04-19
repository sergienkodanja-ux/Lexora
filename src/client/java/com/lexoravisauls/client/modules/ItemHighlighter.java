package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;

public class ItemHighlighter {

    // Храним настройки каждого предмета: цвет (RGB) и включен ли он
    public static class ItemConfig {
        public boolean enabled = true;
        public int color; // Формат 0xRRGGBB (без альфы)

        public ItemConfig(int defaultColor) {
            this.color = defaultColor;
        }
    }

    public static final Map<Item, String> ITEM_NAMES = new HashMap<>();
    public static final Map<Item, ItemConfig> ITEM_CONFIGS = new HashMap<>();

    static {
        // Регистрируем все твои предметы и их дефолтные цвета
        addItem(Items.TOTEM_OF_UNDYING, "Тотем", 0xFFD700); // Золотой
        addItem(Items.CHORUS_FRUIT, "Хорус", 0x8A2BE2); // Фиолетовый
        addItem(Items.ENDER_PEARL, "Эндер Жемчуг", 0x00FFFF); // Бирюзовый
        addItem(Items.ENDER_EYE, "Дезориентация", 0x32CD32); // Зеленый
        addItem(Items.SUGAR, "Явная Пыль", 0xFFFFFF); // Белый
        addItem(Items.FIRE_CHARGE, "Огненный Смерч", 0xFF4500); // Оранжевый
        addItem(Items.EXPERIENCE_BOTTLE, "Пузырек Опыта", 0xADFF2F); // Салатовый
        addItem(Items.NETHERITE_SCRAP, "Трапка (Лом)", 0x8B4513); // Коричневый
        addItem(Items.DRIED_KELP, "Пласт", 0x006400); // Темно-зеленый
        addItem(Items.GOLDEN_APPLE, "Гепл", 0xFFD700);
        addItem(Items.ENCHANTED_GOLDEN_APPLE, "Чар. Гепл", 0xFF00FF); // Маджента
        addItem(Items.POPPED_CHORUS_FRUIT, "Трапка ХВ", 0x9932CC);
        addItem(Items.PRISMARINE_SHARD, "Взрывная Трапка", 0x00CED1);
        addItem(Items.CARVED_PUMPKIN, "Светильник Джейка", 0xFFA500);
        addItem(Items.NETHER_STAR, "Стан", 0xFFFFFF);
    }

    private static void addItem(Item item, String name, int defaultColor) {
        ITEM_NAMES.put(item, name);
        ITEM_CONFIGS.put(item, new ItemConfig(defaultColor));
    }

    // Главный метод отрисовки, который мы вызовем из Миксина
    public static void renderHighlight(DrawContext context, Item item, int x, int y) {
        if (!LexoraGui.moduleStates.getOrDefault("Item Highlighter", false)) return;

        ItemConfig config = ITEM_CONFIGS.get(item);
        if (config == null || !config.enabled) return; // Если предмета нет в списке или он выключен

        // Получаем настройки из GUI
        boolean pulsate = LexoraGui.moduleStates.getOrDefault("Highlighter Pulsation", true);
        int alpha = LexoraGui.numSettings.getOrDefault("Highlighter Alpha", 100f).intValue();

        // Логика мягкой пульсации
        if (pulsate) {
            float sine = (float) Math.sin(System.currentTimeMillis() / 300.0);
            alpha = (int) (alpha * (0.6f + 0.4f * sine)); // Плавно меняем прозрачность от 60% до 100% от заданного
        }

        // Защита от кривых значений
        alpha = Math.max(0, Math.min(255, alpha));

        // Склеиваем цвет и альфу
        int finalColor = (alpha << 24) | (config.color & 0xFFFFFF);

        // Рисуем квадрат поверх слота (размер слота 16x16)
        context.fill(x, y, x + 16, y + 16, finalColor);
    }
}