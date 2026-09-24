package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.gui.MsdfFont;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class ModernGuiIcons {

    private static final MsdfFont GUI_ICON_FONT = new MsdfFont(
            Identifier.of("lexoravisauls", "msdf_data/gui_icons.png"),
            Identifier.of("lexoravisauls", "msdf_data/gui_icons.json")
    );

    public enum Icon {
        WRENCH("A"),          // 1: Ключ (Утилиты)
        WAND("B"),            // 2: Волшебная палочка (Визуалы)
        STAR_OUTLINE("C"),    // 3: Звезда контур (Пустое избранное)
        STAR_FILLED("D"),     // 4: Звезда закрашенная (В избранном)
        MONITOR("E"),         // 5: Монитор (ХУД)
        GRID("F"),            // 6: Таблица / Сетка (Все модули)
        SWAP("G"),            // 7: Стрелочки влево-вправо (Свап / Хит)
        SLIDERS("H"),         // 8: Ползунки горизонтальные
        TOGGLE("I"),          // 9: Тумблер переключатель
        SORT("J"),            // 10: Сортировка / Список
        SETTINGS("K"),        // 11: Шестерёнка (Настройки)
        SEARCH("L"),          // 12: Лупа (Поиск)
        KEYBOARD("M"),        // 13: Клавиатура (Бинды)
        PIPETTE("N"),         // 14: Пипетка (Темы / Палитра)
        FILE("O"),            // 15: Документ / Файл (Конфиги)
        PIN("P"),             // 16: Метка карты (GPS / Вейпоинты)
        USERS("Q"),           // 17: Пользователи (Друзья / Пати)
        MOON("R"),            // 18: Луна (Тёмная тема)
        SUN("S"),             // 19: Солнце (Светлая тема)
        SPARKLE("T");         // 20: 4-конечная звезда (Логотип / System)

        public final String character;

        Icon(String character) {
            this.character = character;
        }
    }

    public static void draw(DrawContext context, Icon icon, float x, float y, float size, int color) {
        GUI_ICON_FONT.draw(context, icon.character, x, y, size, color);
    }

    public static float getWidth(Icon icon, float size) {
        return GUI_ICON_FONT.getWidth(icon.character, size);
    }
}
