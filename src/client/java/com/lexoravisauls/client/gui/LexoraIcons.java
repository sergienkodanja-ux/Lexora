package com.lexoravisauls.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class LexoraIcons {

    private static final MsdfFont ICON_FONT = new MsdfFont(
            Identifier.of("lexoravisauls", "msdf_data/icons.png"),
            Identifier.of("lexoravisauls", "msdf_data/icons.json")
    );

    public enum Icon {
        // 🔥 Главный HUD
        PLAYER("\ue904"),      // Человечек (Профиль)
        MONITOR("\ue90b"),     // Монитор с пульсом (FPS)
        GEAR("\ue914"),        // Шестеренка (TPS / Настройки)
        COMPASS("\ue906"),     // Компас (Координаты)
        LIGHTNING("\ue902"),   // Молния (Скорость/BPS)
        SIGNAL("\ue901"),      // Антена (Сервер / Пинг)

        // 🔥 Dynamic Island
        AIRPLANE("\ue919"),    // Самолет
        WIFI_1("\ue90d"),      // WiFi (1 полоска)
        WIFI_2("\ue90e"),      // WiFi (2 полоски)
        WIFI_3("\ue90f"),      // WiFi (3 полоски)
        WIFI_FULL("\ue910"),   // WiFi (4 полоски)

        // 🔥 Extra HUDs
        APPS("\ue900"),        // 9 точек (Инвентарь)
        HOURGLASS("\ue909"),   // Песочные часы (Кулдауны)
        KEYBOARD("\ue90a"),    // Клавиатура (Кейбинды)
        SHIELD("\ue915"),      // Щит (Броня)

        // 🔥 Potion HUD
        POTION("\ue905"),      // Колба с зельем

        // 🌟 Дополнительные иконки (на будущее)
        CLOSE("\ue903"),       // Крестик (Закрыть)
        MAP("\ue907"),         // Карта
        GLOBE("\ue908"),       // Глобус
        CLOCK("\ue90c"),       // Часы
        PAUSE("\ue911"),       // Пауза (Для плеера)
        PLAY("\ue912"),        // Плей (Для плеера)
        EXCLAMATION("\ue913"), // Восклицательный знак (Предупреждения)
        SKIP_NEXT("\ue916"),   // Следующий трек
        SKIP_PREV("\ue917"),   // Предыдущий трек
        TEXT("\ue918");        // Текст / Сообщение

        public final String character;

        Icon(String character) {
            this.character = character;
        }
    }

    public static void draw(DrawContext context, Icon icon, float x, float y, float size, int color) {
        ICON_FONT.draw(context, icon.character, x, y, size, color);
    }

    public static float getWidth(Icon icon, float size) {
        return ICON_FONT.getWidth(icon.character, size);
    }
}
