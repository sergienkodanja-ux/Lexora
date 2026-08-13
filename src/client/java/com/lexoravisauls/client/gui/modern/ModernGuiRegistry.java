package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.liteapi.LiteApiFeatureControl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModernGuiRegistry {

    public static List<String> getUtilsModules() {
        return filterBlocked(LexoraGui.categories.getOrDefault("Utils", List.of()));
    }

    public static List<String> getVisualModules() {
        return filterBlocked(LexoraGui.categories.getOrDefault("Visual", List.of()));
    }

    public static List<String> getHudModules() {
        return filterBlocked(LexoraGui.categories.getOrDefault("HUD", List.of()));
    }

    /**
     * Убирает из списка модули, которые сервер (HolyWorld LiteAPI feature-control)
     * прислал в blocklist. Единая точка фильтрации: раз getUtilsModules/getVisualModules/
     * getHudModules — это единственный способ, которым ModernClickGui получает список
     * модулей для рендера и для обработки кликов, фильтрация здесь гарантированно
     * применяется везде, без риска забыть продублировать её в другом месте GUI.
     */
    private static List<String> filterBlocked(List<String> source) {
        List<String> result = new ArrayList<>(source.size());
        for (String module : source) {
            if (!LiteApiFeatureControl.isBlocked(module)) {
                result.add(module);
            }
        }
        return result;
    }

    public static List<String> getDefaultThemes() {
        return Arrays.asList(
                "Прост белый",
                "Ягодный пунш",
                "Осенний лес",
                "Безупречный",
                "Сладкие мечты",
                "Космос",
                "Закат"
        );
    }
}