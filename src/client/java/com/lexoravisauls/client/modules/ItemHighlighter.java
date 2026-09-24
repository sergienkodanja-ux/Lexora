package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.ClientData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
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

    public static final Map<Item, String> ITEM_NAMES = new LinkedHashMap<>();
    public static final Map<Item, ItemConfig> ITEM_CONFIGS = new LinkedHashMap<>();

    static {
        // Регистрируем все предметы и их дефолтные цвета
        addItem(Items.TOTEM_OF_UNDYING, "Тотем", 0xFFD700); // Золотой
        addItem(Items.CHORUS_FRUIT, "Хорус", 0x8A2BE2); // Фиолетовый
        addItem(Items.ENDER_PEARL, "Эндер Жемчуг", 0x00FFFF); // Бирюзовый
        addItem(Items.ENDER_EYE, "Дезориентация", 0x32CD32); // Зеленый (Дезка)
        addItem(Items.SUGAR, "Явная Пыль", 0xFFFFFF); // Белый (Явка)
        addItem(Items.PHANTOM_MEMBRANE, "Божья аура", 0x00FA9A); // Салатово-бирюзовый
        addItem(Items.FIRE_CHARGE, "Огненный Смерч", 0xFF4500); // Оранжевый (Штучка)
        addItem(Items.EXPERIENCE_BOTTLE, "Пузырек Опыта", 0xADFF2F); // Салатовый
        addItem(Items.NETHERITE_SCRAP, "Трапка (Лом)", 0x8B4513); // Коричневый
        addItem(Items.COBWEB, "Трапка ФТ", 0xDCDCDC); // Светло-серый
        addItem(Items.DRIED_KELP, "Пласт", 0x006400); // Темно-зеленый
        addItem(Items.GOLDEN_APPLE, "Гепл", 0xFFD700); // Золотой
        addItem(Items.ENCHANTED_GOLDEN_APPLE, "Чар. Гепл", 0xFF00FF); // Маджента
        addItem(Items.POPPED_CHORUS_FRUIT, "Трапка ХВ", 0x9932CC); // Пурпурный
        addItem(Items.PRISMARINE_SHARD, "Взрывная Трапка", 0x00CED1); // Бирюзовый
        addItem(Items.CARVED_PUMPKIN, "Светильник Джейка", 0xFFA500); // Оранжевый
        addItem(Items.NETHER_STAR, "Стан", 0xFFFFFF); // Белый
    }

    private static void addItem(Item item, String name, int defaultColor) {
        ITEM_NAMES.put(item, name);
        ITEM_CONFIGS.put(item, new ItemConfig(defaultColor));
    }

    public static void initDefaults() {
        for (Map.Entry<Item, String> entry : ITEM_NAMES.entrySet()) {
            String name = entry.getValue();
            ClientData.moduleStates.putIfAbsent("HL_" + name, true);
            ItemConfig cfg = ITEM_CONFIGS.get(entry.getKey());
            if (cfg != null) {
                float[] hsv = new float[3];
                java.awt.Color.RGBtoHSB((cfg.color >> 16) & 0xFF, (cfg.color >> 8) & 0xFF, cfg.color & 0xFF, hsv);
                ClientData.colorSettings.putIfAbsent("HLC_" + name, hsv);
            }
        }
        ClientData.numSettings.putIfAbsent("Highlighter Alpha", 100f);
        ClientData.moduleStates.putIfAbsent("Highlighter Pulsation", true);
        ClientData.moduleStates.putIfAbsent("HL_Custom_Colors", false);
    }

    public static boolean isItemEnabled(Item item) {
        String name = ITEM_NAMES.get(item);
        if (name == null) return false;
        ItemConfig config = ITEM_CONFIGS.get(item);
        boolean defaultVal = (config != null) ? config.enabled : true;
        return ClientData.moduleStates.getOrDefault("HL_" + name, defaultVal);
    }

    public static int getItemColor(Item item) {
        ItemConfig config = ITEM_CONFIGS.get(item);
        if (config == null) return 0xFFFFFF;
        String name = ITEM_NAMES.get(item);
        if (name != null) {
            float[] hsv = ClientData.colorSettings.get("HLC_" + name);
            if (hsv != null && hsv.length >= 3) {
                return java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
            }
        }
        return config.color;
    }

    // Главный метод отрисовки, который вызывается из Миксина
    public static void renderHighlight(DrawContext context, Item item, int x, int y) {
        if (!ClientData.moduleStates.getOrDefault("Item Highlighter", false)) return;

        ItemConfig config = ITEM_CONFIGS.get(item);
        if (config == null) return;

        if (!isItemEnabled(item)) return;

        // Получаем настройки из GUI
        boolean pulsate = ClientData.moduleStates.getOrDefault("Highlighter Pulsation", true);
        int alpha = ClientData.numSettings.getOrDefault("Highlighter Alpha", 100f).intValue();

        // Логика мягкой пульсации
        if (pulsate) {
            float sine = (float) Math.sin(System.currentTimeMillis() / 300.0);
            alpha = (int) (alpha * (0.6f + 0.4f * sine)); // Плавно меняем прозрачность от 60% до 100% от заданного
        }

        // Защита от кривых значений
        alpha = Math.max(0, Math.min(255, alpha));

        // Склеиваем цвет и альфу
        int finalColor = (alpha << 24) | (getItemColor(item) & 0xFFFFFF);

        // Рисуем квадрат поверх слота (размер слота 16x16)
        context.fill(x, y, x + 16, y + 16, finalColor);
    }
}