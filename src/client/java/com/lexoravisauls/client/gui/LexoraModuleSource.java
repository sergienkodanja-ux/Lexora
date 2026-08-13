package com.lexoravisauls.client.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реальный список модулей Lexora по категориям — используется для поиска
 * в LexoraBindPickerScreen.
 *
 * Если структура модулей у вас изменится (добавите/переименуете модуль
 * или категорию, либо захотите брать их прямо из вашего реестра вместо
 * хардкода) — правьте только этот файл, остальной код биндов его не
 * касается напрямую.
 */
public final class LexoraModuleSource {
    private LexoraModuleSource() {}

    private static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("HUD", List.of(
                "Armor Status", "Potions", "Inventory HUD", "Cooldowns", "Watermark",
                "Keybinds", "Target HUD", "Info HUD", "Saturation HUD", "GPS",
                "Scoreboard HUD", "Lexora IRC"
        ));
        CATEGORIES.put("Visual", List.of(
                "Target ESP", "Animations", "Aspect Ratio", "View Model", "Hit Sounds",
                "Ft Helper", "China Hat", "Particles", "Jump Circles", "Item Physics",
                "Hit Color", "Hit Wave", "Prediction", "Full Bright", "Block Overlay",
                "Hand Shaders", "Trails", "Custom Hitboxes", "Nimb", "World Customizer",
                "AuraParticles", "Motion Clones", "Kill Effect"
        ));
        CATEGORIES.put("Utils", List.of(
                "Auto Sprint", "Item Swap", "Elytra Swap", "Fake Player", "Fast EXP",
                "Auto Eat", "Free Look", "Auto Respawn", "Totem Indicator", "Loot Notifier",
                "Auto Leave", "Shift Tap", "Fast Swap", "Item Scroller", "PvP Save",
                "Lock Slot", "Item Highlighter", "Healing Helper", "Streamer Mode",
                "No Render", "Zoom", "Tape Mouse", "Self Nametags"
        ));
    }

    /** Категории в порядке отображения, с модулями внутри (для группировки в поиске). */
    public static Map<String, List<String>> getCategories() {
        return CATEGORIES;
    }

    /** Плоский список всех модулей, без категорий. */
    public static List<String> getAllModuleNames() {
        return CATEGORIES.values().stream()
                .flatMap(List::stream)
                .toList();
    }
}