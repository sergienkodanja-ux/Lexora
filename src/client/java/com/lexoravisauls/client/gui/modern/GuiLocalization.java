package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.core.ClientData;
import java.util.HashMap;
import java.util.Map;

public class GuiLocalization {

    public enum Language {
        RU("Русский", "RU"),
        EN("English", "EN");

        public final String name;
        public final String code;

        Language(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    private static Language currentLanguage = Language.RU;

    public static Language getLanguage() {
        String saved = ClientData.modeSettings.getOrDefault("GuiLanguage", "RU");
        if ("EN".equalsIgnoreCase(saved)) return Language.EN;
        return Language.RU;
    }

    public static void setLanguage(Language lang) {
        currentLanguage = lang;
        ClientData.modeSettings.put("GuiLanguage", lang.code);
    }

    public static void toggleLanguage() {
        if (getLanguage() == Language.RU) {
            setLanguage(Language.EN);
        } else {
            setLanguage(Language.RU);
        }
    }

    // ── Модули: Названия и Описания ──────────────────────────────────────────
    private static final Map<String, String[]> MODULE_INFO = new HashMap<>();

    static {
        // [RU Title, RU Desc, EN Title, EN Desc]
        // HUD
        MODULE_INFO.put("Information HUD", new String[]{"Инфо ХУД", "Отображает FPS, TPS, пинг, координаты и сервер", "Information HUD", "Displays FPS, TPS, ping, coordinates and server info"});
        MODULE_INFO.put("Armor Durability", new String[]{"Прочность брони", "Показывает состояние надетой брони и оружия", "Armor Durability", "Shows status of equipped armor and held items"});
        MODULE_INFO.put("Potion HUD", new String[]{"Эффекты зелий", "Список активных зелий с таймерами и иконками", "Potion HUD", "Active potion effects with timers and icons"});
        MODULE_INFO.put("Totem Indicator", new String[]{"Счетчик тотемов", "Количество тотемов бессмертия в инвентаре", "Totem Indicator", "Count of Totems of Undying in inventory"});
        MODULE_INFO.put("GPS", new String[]{"GPS Навигатор", "Указатели и метки точек на экране", "GPS Navigator", "Screen waypoints and navigation markers"});
        MODULE_INFO.put("Hit Indicator", new String[]{"Индикатор удара", "Визуальные всплывающие маркеры урона и комбо", "Hit Indicator", "Visual damage popups and combo indicators"});
        MODULE_INFO.put("Scoreboard", new String[]{"Таблица счёта", "Кастомизация и скругление ванильного скорборда", "Scoreboard", "Custom styling and rounding for vanilla scoreboard"});
        MODULE_INFO.put("Target HUD", new String[]{"Таргет ХУД", "Панель информации о выбранном противнике", "Target HUD", "Information panel for currently targeted enemy"});
        MODULE_INFO.put("TNT Timer", new String[]{"Таймер ТНТ", "Отображение времени до взрыва динамита", "TNT Timer", "Displays remaining fuse time before TNT explosion"});
        MODULE_INFO.put("Lexora IRC", new String[]{"IRC Чат", "Глобальный чат между пользователями клиента", "Lexora IRC", "Global cross-server chat between client users"});
        MODULE_INFO.put("Emotes", new String[]{"Эмоции", "Колесо кастомных анимаций и эмоций персонажа", "Emotes", "Custom animation and emote wheel for character"});

        // Visual
        MODULE_INFO.put("Aspect Ratio", new String[]{"Соотношение сторон", "Кастомное соотношение экрана и угла обзора", "Aspect Ratio", "Custom screen aspect ratio and FOV stretch"});
        MODULE_INFO.put("Crosshair", new String[]{"Прицел", "Кастомный анимированный векторный прицел", "Crosshair", "Custom animated vector crosshair"});
        MODULE_INFO.put("Damage Tilt", new String[]{"Наклон урона", "Реалистичный наклон камеры при получении урона", "Damage Tilt", "Realistic camera tilt upon receiving damage"});
        MODULE_INFO.put("Full Bright", new String[]{"Яркость", "Максимальная яркость в пещерах и ночью", "Full Bright", "Maximum gamma brightness in caves and at night"});
        MODULE_INFO.put("Hit Color", new String[]{"Цвет урона", "Кастомная вспышка при ударе по сущностям", "Hit Color", "Custom flash color when hitting entities"});
        MODULE_INFO.put("Item Physics", new String[]{"Физика предметов", "Реалистичное падение и вращение выброшенных вещей", "Item Physics", "Realistic physics and drop rotation for items"});
        MODULE_INFO.put("Jump Circles", new String[]{"Круги прыжка", "Эффектные расширяющиеся круги под ногами при прыжке", "Jump Circles", "Expanding shockwave circles under feet on jump"});
        MODULE_INFO.put("No Bad Effects", new String[]{"Убрать негатив", "Скрывает эффекты слепоты, тошноты и темноты", "No Bad Effects", "Hides blindness, nausea and darkness visual effects"});
        MODULE_INFO.put("No Hurt Cam", new String[]{"Без тряски камеры", "Отключает раздражающую тряску экрана от ударов", "No Hurt Cam", "Disables annoying screen shake on taking damage"});
        MODULE_INFO.put("No Particles", new String[]{"Скрыть частицы", "Оптимизация: скрывает выбранные частицы", "No Particles", "Optimization: disables selected visual particles"});
        MODULE_INFO.put("Particles", new String[]{"Кастомные частицы", "Красивые следы и частицы при ударах и беге", "Custom Particles", "Beautiful particle trails upon hitting and running"});
        MODULE_INFO.put("Self Nametags", new String[]{"Свой никнейм", "Отображение своего никнейма от 3-го лица", "Self Nametags", "Displays your own nametag in third-person view"});
        MODULE_INFO.put("Swing Animation", new String[]{"Анимация удара", "Кастомные стили взмаха мечом и предметов", "Swing Animation", "Custom sword and item swing styles"});
        MODULE_INFO.put("Trail", new String[]{"Шлейф за игроком", "Светящийся след за персонажем во время движения", "Player Trail", "Glowing trail behind character during movement"});
        MODULE_INFO.put("World Time", new String[]{"Время мира", "Установка постоянного времени суток на клиенте", "World Time", "Locks custom client-side time of day"});
        MODULE_INFO.put("Kinetic Lyrics", new String[]{"Кинетические субтитры", "Рендерит слова трека в 3D-пространстве мира и бегущую строку в Dynamic Island", "Kinetic Lyrics", "Renders song lyrics in the 3D world and ticker subtitles in Dynamic Island"});
        MODULE_INFO.put("KineticLyrics", new String[]{"Кинетические субтитры", "Рендерит слова трека в 3D-пространстве мира и бегущую строку в Dynamic Island", "Kinetic Lyrics", "Renders song lyrics in the 3D world and ticker subtitles in Dynamic Island"});

        // Utils
        MODULE_INFO.put("Auto Respawn", new String[]{"Авто Респавн", "Мгновенное возрождение после смерти", "Auto Respawn", "Instant automatic respawn after death"});
        MODULE_INFO.put("Auto Sprint", new String[]{"Авто Спринт", "Постоянный автоматический бег без зажатия Ctrl", "Auto Sprint", "Continuous sprint without holding down control key"});
        MODULE_INFO.put("Fake Player", new String[]{"Фейковый игрок", "Спавнит клона персонажа для тестов урона", "Fake Player", "Spawns player clone for damage and range testing"});
        MODULE_INFO.put("Fast EXP", new String[]{"Быстрый опыт", "Ускоренное бросание пузырьков опыта", "Fast EXP", "Rapid automatic throwing of experience bottles"});
        MODULE_INFO.put("Fast Swap", new String[]{"Быстрый свап", "Мгновенная смена предметов в слотах по хоткею", "Fast Swap", "Instant item hotkey swapping"});
        MODULE_INFO.put("Item Highlighter", new String[]{"Подсветка предметов", "Цветная подсветка ценного лута в инвентаре", "Item Highlighter", "Glowing colored highlight for valuable inventory loot"});
        MODULE_INFO.put("Item Swap", new String[]{"Свап предметов", "Быстрый перенос предметов в слоты щита/тотема", "Item Swap", "Fast item swap into offhand/totem slots"});
        MODULE_INFO.put("Lock Slot", new String[]{"Защита слотов", "Предотвращает случайный выброс важных вещей", "Lock Slot", "Prevents accidental dropping of locked hotbar items"});
    }

    public static String getModuleTitle(String module) {
        String[] data = MODULE_INFO.get(module);
        if (data == null) return module;
        return getLanguage() == Language.RU ? data[0] : data[2];
    }

    public static String getModuleDescription(String module) {
        String[] data = MODULE_INFO.get(module);
        if (data == null) return "";
        return getLanguage() == Language.RU ? data[1] : data[3];
    }

    // ── Настройки: Названия и Описания ─────────────────────────────────────────
    private static final Map<String, String[]> SETTING_INFO = new HashMap<>();

    static {
        // Crosshair
        SETTING_INFO.put("Crosshair Mode", new String[]{"Режим прицела", "Стиль и геометрия центрального прицела", "Crosshair Mode", "Style and geometry of crosshair"});
        SETTING_INFO.put("Crosshair Size", new String[]{"Размер", "Масштаб линий прицела", "Size", "Scale of crosshair lines"});
        SETTING_INFO.put("Crosshair Gap", new String[]{"Зазор", "Расстояние от центра до линий", "Gap", "Center gap distance"});
        SETTING_INFO.put("Crosshair Thickness", new String[]{"Толщина", "Толщина линий прицела", "Thickness", "Line stroke thickness"});
        SETTING_INFO.put("Crosshair Dot", new String[]{"Точка в центре", "Отображать точку по центру", "Center Dot", "Render dot in crosshair center"});
        SETTING_INFO.put("Crosshair Outline", new String[]{"Обводка", "Контурная темная обводка линий", "Outline", "Dark contour stroke around lines"});
        SETTING_INFO.put("Crosshair Dynamic", new String[]{"Динамический", "Реакция прицела на ходьбу и атаку", "Dynamic", "Expands on movement and attacks"});

        // Hit Indicator
        SETTING_INFO.put("Hit Indicator Mode", new String[]{"Тип индикатора", "Анимация всплытия урона", "Indicator Type", "Damage popup animation style"});
        SETTING_INFO.put("Hit Indicator Sound", new String[]{"Звук попадания", "Звуковой эффект при успешном ударе", "Hit Sound", "Audio effect on successful hit"});
        SETTING_INFO.put("Hit Indicator Scale", new String[]{"Размер текста", "Масштаб цифр полученного урона", "Text Scale", "Scale of floating damage numbers"});

        // Aspect Ratio
        SETTING_INFO.put("Aspect Ratio X", new String[]{"Ширина X", "Пропорция растяжения по горизонтали", "Aspect Width X", "Horizontal stretch ratio"});
        SETTING_INFO.put("Aspect Ratio Y", new String[]{"Высота Y", "Пропорция сжатия по вертикали", "Aspect Height Y", "Vertical compress ratio"});

        // Damage Tilt
        SETTING_INFO.put("Damage Tilt Multiplier", new String[]{"Сила наклона", "Интенсивность наклона камеры", "Tilt Multiplier", "Camera tilt intensity on damage"});

        // Swing Animation
        SETTING_INFO.put("Swing Mode", new String[]{"Стиль анимации", "Тип движения руки и предмета", "Swing Style", "Motion style for hand and item"});
        SETTING_INFO.put("Swing Speed", new String[]{"Скорость взмаха", "Скорость проигрывания анимации", "Swing Speed", "Animation playback speed"});
        SETTING_INFO.put("Swing Slowdown", new String[]{"Замедление", "Плавность в конце взмаха", "Slowdown", "Easing smoothness at stroke end"});

        // World Time
        SETTING_INFO.put("World Time Value", new String[]{"Время суток", "Фиксированное время игрового дня", "Time of Day", "Fixed game time value"});

        // Particles
        SETTING_INFO.put("Particles Mode", new String[]{"Тип эффекта", "Стиль генерируемых частиц", "Effect Type", "Generated particle visual style"});
        SETTING_INFO.put("Particles Amount", new String[]{"Количество", "Плотность появления частиц", "Amount", "Spawn density of particles"});
        SETTING_INFO.put("Particles Lifetime", new String[]{"Длительность", "Время жизни частиц до угасания", "Lifetime", "Duration before particles fade"});

        // Trail
        SETTING_INFO.put("Trail Mode", new String[]{"Стиль шлейфа", "Форма и вид световой линии", "Trail Style", "Shape and style of light trail"});
        SETTING_INFO.put("Trail Length", new String[]{"Длина", "Количество сегментов следа", "Length", "Number of trailing segments"});
        SETTING_INFO.put("Trail Width", new String[]{"Ширина", "Толщина линии шлейфа", "Width", "Line stroke width of trail"});

        // Hit Color
        SETTING_INFO.put("Hit Color Alpha", new String[]{"Прозрачность", "Яркость вспышки при ударе", "Flash Opacity", "Brightness of damage flash"});

        // GPS / Emotes / IRC
        SETTING_INFO.put("GPS Range", new String[]{"Радиус меток", "Максимальная дистанция отображения", "Waypoint Range", "Max rendering distance for pins"});
        SETTING_INFO.put("IRC Notification Sound", new String[]{"Звук сообщений", "Звук при получении нового сообщения", "Message Sound", "Audio alert on incoming message"});
    }

    public static String getSettingTitle(String key) {
        return getSettingTitle(key, key);
    }

    public static String getSettingTitle(String key, String fallback) {
        String[] data = SETTING_INFO.get(key);
        if (data != null) return getLanguage() == Language.RU ? data[0] : data[2];
        return fallback != null ? fallback : key;
    }

    public static String getSettingDescription(String key) {
        String[] data = SETTING_INFO.get(key);
        if (data != null) return getLanguage() == Language.RU ? data[1] : data[3];
        // Default intuitive subtitle based on key name
        if (key.toLowerCase().contains("color")) {
            return getLanguage() == Language.RU ? "Настройка цвета и прозрачности" : "Color and opacity settings";
        }
        if (key.toLowerCase().contains("mode")) {
            return getLanguage() == Language.RU ? "Выбор режима работы" : "Select operational mode";
        }
        if (key.toLowerCase().contains("size") || key.toLowerCase().contains("scale")) {
            return getLanguage() == Language.RU ? "Масштаб и размер элемента" : "Element scale and size";
        }
        return getLanguage() == Language.RU ? "Параметр настройки модуля" : "Module configuration setting";
    }

    // ── Категории и UI Тексты ────────────────────────────────────────────────
    public static String getCategoryText(String cat) {
        if (getLanguage() == Language.EN) {
            switch (cat) {
                case "Все":
                case "All": return "All";
                case "ХУД":
                case "HUD": return "HUD";
                case "Визуалы":
                case "Visual": return "Visual";
                case "Утилиты":
                case "Utils": return "Utils";
                case "Конфиги":
                case "Configs": return "Configs";
                case "Пати":
                case "Friends": return "Friends";
                case "Метки":
                case "Waypoints": return "Waypoints";
                case "Темы":
                case "Themes": return "Themes";
                case "Настройки":
                case "Settings": return "Settings";
                case "Поиск":
                case "Search": return "Search";
            }
        } else {
            switch (cat) {
                case "All": return "Все";
                case "HUD": return "ХУД";
                case "Visual": return "Визуалы";
                case "Utils": return "Утилиты";
                case "Configs": return "Конфиги";
                case "Friends": return "Пати";
                case "Waypoints": return "Метки";
                case "Themes": return "Темы";
                case "Settings": return "Настройки";
                case "Search": return "Поиск";
            }
        }
        return cat;
    }
}
