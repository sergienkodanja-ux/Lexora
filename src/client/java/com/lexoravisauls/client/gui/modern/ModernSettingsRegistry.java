package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lexoravisauls.client.gui.modern.ModernSetting.*;

public class ModernSettingsRegistry {

    private static final Map<String, List<ModernSetting>> SETTINGS = new HashMap<>();

    // Хелпер: true если выбран любой из 4 новых шейдерных типов неба
    // Добавь этот метод рядом с блоком SETTINGS (например, статик-методом того же класса)
    private static boolean isShaderSkyType() {
        String type = LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard");
        return type.equals("Water") || type.equals("Caustic") || type.equals("Thunder") || type.equals("Pulsar");
    }

    static {
        SETTINGS.put("Target ESP", List.of(
                mode("Target ESP Mode", "Режим", "Spirits", "Crystals", "Circle", "Skull", "Rhombus", "Round Rhombus"),
                toggle("Red On Damage", "Красный при уроне"),
                toggle("Only On Crit", "Только при крите"),
                slider("ESP Speed", "Скорость", 0.1f, 3.0f),

                slider("Spirits Count", "Кол-во духов", 1.0f, 10.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Spirits")),

                slider("Trail Length", "Длина следа", 5.0f, 100.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Spirits")),

                slider("Crystal Size", "Размер кристалов", 0.1f, 2.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Crystals")),

                slider("Crystal Count", "Кол-во кристалов", 8.0f, 30.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Crystals")),

                slider("Skull Size", "Размер черепа", 0.5f, 3.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Skull")),

                slider("Rhombus Size", "Размер ромба", 0.3f, 3.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Target ESP Mode", "Spirits").contains("Rhombus"))
        ));

        SETTINGS.put("Animations", List.of(
                toggle("Anim Hotbar", "Аним. хотбара"),
                toggle("Anim Chat", "Аним. чата"),
                toggle("Anim Tab", "Аним. таба")
        ));

        SETTINGS.put("Taksa", List.of(
                toggle("Taksa Attack Reaction", "Реакция на атаку")
        ));

        SETTINGS.put("Hit Indicator", List.of(
                slider("Hit Radius", "Радиус", 60.0f, 260.0f),
                slider("Hit Arc Length", "Длина дуги", 30.0f, 150.0f),
                slider("Hit Thickness", "Толщина", 1.0f, 7.0f),
                slider("Hit Duration", "Время показа", 250.0f, 1600.0f),
                slider("Hit Opacity", "Прозрачность", 0.2f, 1.0f),
                slider("Hit Source Range", "Дистанция", 6.0f, 64.0f),

                // Метод для чекбокса. Если у тебя он называется иначе, замени checkbox на toggle или bool
                toggle("Hit Glow", "Свечение")
        ));

        SETTINGS.put("AuraParticles", List.of(
                slider("Aura Particles Count", "Кол-во", 1.0f, 120.0f),
                slider("Aura Particles Size", "Размер", 0.05f, 0.60f),
                slider("Aura Particles Radius", "Радиус", 1.0f, 1.5f),
                slider("Aura Particles Delay", "Задержка", 0.1f, 2.0f),
                mode("Aura Particles Color Mode", "Режим Цвета", "Client", "Custom"),
                color("Aura Particles Custom Color", "Свой Цвет").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Aura Particles Color Mode", "Client").equals("Custom"))
        ));

        SETTINGS.put("Motion Blur", List.of(
                slider("Motion Blur Strength", "Сила", 0.0f, 4.0f),

                toggle("Motion Blur RRC", "Адаптация к частоте монитора"),

                mode("Motion Blur Quality", "Качество", "Низкое", "Среднее", "Высокое", "Ультра")
        ));

        SETTINGS.put("Kill Effect", List.of(
                mode("Kill Effect Mode", "Режим", "Soul Ascension", "Void Collapse"),
                toggle("Trigger On Any Death", "Эффект на любую смерть"),

                slider("Ascension Duration", "Длительность (сек)", 1.0f, 6.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Soul Ascension")),
                slider("Ascension Height", "Высота полёта", 1.0f, 5.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Soul Ascension")),
                slider("Ascension Spin Speed", "Скорость кручения", 0.0f, 5.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Soul Ascension")),

                slider("Collapse Seed Count", "Кол-во частиц", 6.0f, 40.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Void Collapse")),
                slider("Collapse Radius", "Радиус", 0.5f, 2.5f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Void Collapse")),
                slider("Collapse Duration", "Длительность (сек)", 0.5f, 2.5f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Void Collapse"))
        ));

        SETTINGS.put("Tape Mouse", List.of(
                mode("Tape Mouse Button", "Кнопка", "Left", "Right"),
                slider("Tape Mouse CPS", "Скорость CPS", 1.0f, 20.0f)
        ));

        SETTINGS.put("Loot Notifier", List.of(
                mode("Loot Notifier Mode", "Сервер", "FunTime", "HolyWorld")
        ));

        SETTINGS.put("GPS", List.of(
                slider("GPS HUD Distance", "Дистанция от центра", 30.0f, 150.0f),
                slider("GPS Arrow Size", "Размер стрелки", 16.0f, 64.0f),
                slider("GPS 3D Marker Size", "Размер метки 3D", 0.5f, 3.0f),

                toggle("GPS Show Distance", "Показывать дистанцию"),
                toggle("GPS Show 3D Marker", "Показывать 3D метку"),

                color("GPS Arrow Color", "Цвет стрелки HUD"),
                color("GPS Marker Color", "Цвет иконки метки"),

                toggle("GPS Auto", "АвтоGPS"),
                mode("GPS Mode", "Режим", "Funtime", "HolyWorld").visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("GPS Auto", false))
        ));

        SETTINGS.put("Motion Clones", List.of(
                toggle("Clone Normal", "Обычные клоны"),

                slider("Clone Delay", "Задержка (сек)", 1.5f, 4.0f).visibleIf(() ->
                        LexoraGui.moduleStates.getOrDefault("Clone Normal", true)),

                slider("Clone Life", "Жизнь", 6.0f, 60.0f),
                slider("Clone Totem Life", "Жизнь тотем", 10.0f, 200.0f).visibleIf(() ->
                        LexoraGui.moduleStates.getOrDefault("Clone Totem", true)),
                slider("Clone Scale", "Размер", 0.7f, 1.3f),
                slider("Clone Alpha", "Яркость", 0.1f, 1.0f),

                toggle("Clone Totem", "При тотеме"),
                toggle("Clone Totem Self", "Твой тотем").visibleIf(() ->
                        LexoraGui.moduleStates.getOrDefault("Clone Totem", true)),
                toggle("Clone Totem Players", "Игроки").visibleIf(() ->
                        LexoraGui.moduleStates.getOrDefault("Clone Totem", true)),
                toggle("Clone First Person", "От 1 лица"),

                mode("Clone Color Mode", "Режим Цвета", "Client", "Custom"),
                color("Clone Custom Color", "Свой Цвет").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Clone Color Mode", "Client").equals("Custom"))
        ));

        SETTINGS.put("Aspect Ratio", List.of(
                mode("Ratio Mode", "Режим", "Default", "Custom"),
                slider("Aspect Ratio Val", "Значение", 0.5f, 3.0f).visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Ratio Mode", "Default").equals("Custom"))
        ));

        SETTINGS.put("View Model", List.of(
                mode("VM Anim", "Режим анимации", "Standard", "Взмах", "Взмах 2", "Сдвиг", "Ломание", "Выпад"),
                toggle("VM Target Hit", "При таргете"),
                pad2d("Right Hand X", "Right Hand Y", "Правая рука XY", -2.5f, 2.5f, -2.0f, 2.0f),
                slider("Right Hand Z", "Правая рука Z", -1.5f, 1.5f),
                pad2d("Left Hand X", "Left Hand Y", "Левая рука XY", -2.5f, 2.5f, -2.0f, 2.0f),
                slider("Left Hand Z", "Левая рука Z", -1.5f, 1.5f),
                slider("Сила наклона", "Сила наклона", 20.0f, 75.0f),
                slider("Поворот", "Поворот", -7.5f, 35.0f),
                slider("VM Speed", "Скорость анимации", 0.1f, 5.0f)
        ));

        SETTINGS.put("Hit Sounds", List.of(
                toggle("Hit Sound Only Crit", "Только при крите"),
                mode("Hit Sound Mode", "Звук", "Crime", "Bubble", "Metallic", "Bell", "Bonk", "Hit 1", "Hit 2", "Hit 3", "UwU", "Moan 1", "Moan 2", "Moan 3", "Moan 4", "Pop"),
                slider("Hit Sound Volume", "Громкость", 0.0f, 100.0f)
        ));

        SETTINGS.put("China Hat", List.of(
                slider("China Hat Size", "Размер", 0.45f, 1.0f),

                mode("China Hat Color Source", "Цвет", "Client", "Custom"),

                mode("China Hat Custom Color Mode", "Режим", "Single", "Gradient").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("China Hat Color Source", "Client").equals("Custom")),

                color("China Hat Color 1", "Цвет 1").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("China Hat Color Source", "Client").equals("Custom")),

                color("China Hat Color 2", "Цвет 2").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("China Hat Color Source", "Client").equals("Custom")
                                && LexoraGui.modeSettings.getOrDefault("China Hat Custom Color Mode", "Single").equals("Gradient"))
        ));

        SETTINGS.put("Ft Helper", List.of(
                toggle("FT Дезка", "Дезка"),
                toggle("FT Явка", "Явка"),
                toggle("FT Огненный Шар", "Огненный шар"),
                toggle("FT Божья Аура", "Божья аура"),
                toggle("FT Трапка", "Трапка"),
                toggle("FT Пласт", "Пласт"),
                toggle("FT Снежок", "Снежок"),
                mode("FT Трапка Скин", "Скин трапки", "Обычный", "Драконий"),
                mode("FT Пласт Скин", "Скин пласта", "Обычный", "Драконий"),
                toggle("FT Таймер Трапки", "Таймер трапки")
        ));

        SETTINGS.put("Totem Sound", List.of(
                mode("Totem Sound Mode", "Звук",
                        "Звук 1", "Звук 2", "Звук 3", "Звук 4", "Звук 5", "Звук 6", "Звук 7", "Звук 8", "Звук 9", "Звук 10",
                        "Звук 11", "Звук 12", "Звук 13", "Звук 14", "Звук 15", "Звук 16", "Звук 17", "Звук 18", "Звук 19", "Звук 20",
                        "Звук 21", "Звук 22", "Звук 23", "Звук 24", "Звук 25", "Звук 26", "Звук 27", "Звук 28", "Звук 29", "Звук 30",
                        "Звук 31", "Звук 32", "Звук 33", "Звук 34", "Звук 35", "Звук 36", "Звук 37", "Звук 38", "Звук 39", "Звук 40",
                        "Звук 41"
                ),
                slider("Totem Sound Volume", "Громкость", 0.0f, 2.0f),
                slider("Totem Sound Pitch", "Тон", 0.5f, 2.0f)
        ));

        SETTINGS.put("Particles", List.of(
                mode("Part. Texture", "Текстура", "Star", "Skull", "Bucks", "Snow", "Blast", "Brich", "Core", "Show", "Snowbag", "Genshin", "Heart", "Cube"),

                header("Цвет частиц"),
                mode("Part. Color Mode", "Источник цвета", "Theme", "Custom"),
                mode("Part. Color Type", "Тип цвета", "Solid", "Gradient").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom")),
                color("Part. Custom Color", "Свой цвет").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom") &&
                                LexoraGui.modeSettings.getOrDefault("Part. Color Type", "Solid").equals("Solid")),
                color("Part. Custom Color 1", "Цвет 1 (Градиент)").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom") &&
                                LexoraGui.modeSettings.getOrDefault("Part. Color Type", "Solid").equals("Gradient")),
                color("Part. Custom Color 2", "Цвет 2 (Градиент)").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom") &&
                                LexoraGui.modeSettings.getOrDefault("Part. Color Type", "Solid").equals("Gradient")),

                header("Эмбиент"),
                toggle("Part. Ambient", "Эмбиент"),
                slider("Amb Chance", "Шанс", 1.0f, 100.0f),
                slider("Amb Size", "Размер", 0.2f, 3.0f),
                slider("Amb Spread", "Разброс", 0.1f, 5.0f),
                slider("Amb Life", "Жизнь", 10.0f, 200.0f),

                header("Ходьба"),
                toggle("Part. Walk", "Ходьба"),
                slider("Walk Count", "Кол-во", 1.0f, 15.0f),
                slider("Walk Size", "Размер", 0.2f, 3.0f),
                slider("Walk Spread", "Разброс", 0.1f, 5.0f),
                slider("Walk Life", "Жизнь", 5.0f, 80.0f),

                header("Удар"),
                toggle("Part. Hit", "Удар"),
                slider("Hit Size", "Размер", 0.2f, 15.0f),
                slider("Hit Count", "Кол-во", 1.0f, 20.0f),
                slider("Hit Spread", "Разброс", 0.1f, 5.0f),
                slider("Hit Life", "Жизнь", 5.0f, 100.0f),

                header("Крит"),
                toggle("Part. Crit", "Крит"),
                slider("Crit Size", "Размер", 0.2f, 15.0f),
                slider("Crit Count", "Кол-во", 1.0f, 20.0f),
                slider("Crit Spread", "Разброс", 0.1f, 5.0f),
                slider("Crit Life", "Жизнь", 5.0f, 100.0f),

                header("Снаряды"),
                toggle("Part. Projectiles", "Снаряды"),
                slider("Proj Count", "Кол-во", 1.0f, 25.0f),
                slider("Proj Size", "Размер", 0.2f, 3.0f),
                slider("Proj Spread", "Разброс", 0.1f, 5.0f),
                slider("Proj Life", "Жизнь", 5.0f, 100.0f),

                header("Тотем"),
                toggle("Part. Totem", "Тотем"),
                slider("Totem Size", "Размер", 0.2f, 4.0f),
                slider("Totem Count", "Кол-во", 5.0f, 100.0f),
                slider("Totem Spread", "Разброс", 0.1f, 5.0f),
                slider("Totem Life", "Жизнь", 10.0f, 150.0f),
                toggle("Disable Vanilla Totem", "Скрыть ванильный тотем")
        ));

        SETTINGS.put("Hit Color", List.of(
                mode("Hit Color Mode", "Источник цвета", "Theme", "Custom"),
                mode("Hit Target", "Куда красить", "All", "Body Only"),
                color("Hit Custom Color", "Свой цвет").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Hit Color Mode", "Theme").equals("Custom"))
        ));

        SETTINGS.put("Hit Wave", List.of(
                toggle("Wave Only Crit", "Только при крите"),
                slider("Wave Size", "Размер", 1.0f, 30.0f),
                slider("Wave Speed", "Скорость", 1.0f, 40.0f)
        ));

        SETTINGS.put("Prediction", List.of(
                slider("Predict Ticks", "Дальность", 1.0f, 30.0f)
        ));

        SETTINGS.put("Block Overlay", List.of(
                mode("Overlay Mode", "Стиль", "Web", "Plasma", "Grid", "Solid"),

                mode("Overlay Color Source", "Цвет", "Client", "Custom"),

                color("Overlay Custom Color", "Свой цвет").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Overlay Color Source", "Client").equals("Custom")),

                slider("Overlay Opacity", "Прозрачность", 0.1f, 1.0f)
        ));

        SETTINGS.put("Hand Shaders", List.of(
                mode("Hand Mode", "Эффект", "Smoke", "Snow", "Solid", "Stripes"),
                slider("Hand Glow %", "Свечение", 0.0f, 100.0f)
        ));

        SETTINGS.put("Trails", List.of(
                toggle("Trail Show 1st Person", "Вид от 1-го лица"),
                slider("Trail Length", "Длина", 10.0f, 100.0f),
                mode("Trail Color Mode", "Источник цвета", "Theme", "Custom"),
                color("Trail Custom Color", "Свой цвет").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Trail Color Mode", "Theme").equals("Custom"))
        ));

        SETTINGS.put("Custom Hitboxes", List.of(
                mode("Hitbox Style", "Стиль", "Solid", "Nebula", "Water", "Flame"),
                slider("Hitbox Alpha", "Прозрачность", 0.1f, 1.0f),
                color("Hitbox Color", "Цвет"),
                toggle("HB Players", "Игроки"),
                toggle("HB Mobs", "Мобы"),
                toggle("HB Items", "Предметы")
        ));

        SETTINGS.put("Nimb", List.of(
                toggle("Nimb Show 1st Person", "Вид от 1-го лица"),
                slider("Nimb Radius", "Радиус", 0.2f, 1.5f),

                mode("Nimb Color Source", "Цвет", "Client", "Custom"),

                mode("Nimb Custom Color Mode", "Режим", "Single", "Gradient").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Nimb Color Source", "Client").equals("Custom")),

                color("Nimb Color 1", "Цвет 1").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Nimb Color Source", "Client").equals("Custom")),

                color("Nimb Color 2", "Цвет 2").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Nimb Color Source", "Client").equals("Custom")
                                && LexoraGui.modeSettings.getOrDefault("Nimb Custom Color Mode", "Single").equals("Gradient"))
        ));

        SETTINGS.put("World Customizer", List.of(
                toggle("Sky Customizer", "Кастомное небо"),

                mode("Sky Type", "Тип неба", "Standard", "Water", "Caustic", "Thunder", "Pulsar")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                LexoraGui.moduleStates.getOrDefault("Sky Customizer", false)),

                // ── STANDARD ──────────────────────────────────────────
                mode("Sky Color Mode", "Источник цвета", "Theme", "Custom")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Standard")),
                color("Custom Sky Color", "Цвет неба")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Standard") &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Color Mode", "Theme").equals("Custom")),

                // ── WATER ──────────────────────────────────────────────
                slider("Water Speed", "Скорость", 0.1f, 5.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                slider("Water Scale", "Размер", 1.0f, 20.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                slider("Water Intensity", "Интенсивность", 0.001f, 0.05f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                slider("Water Alpha", "Прозрачность", 0.3f, 1.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                mode("Water Color Mode", "Цвет", "Theme", "Custom")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                color("Water Custom Color", "Свой цвет неба")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water") &&
                                        LexoraGui.modeSettings.getOrDefault("Water Color Mode", "Theme").equals("Custom")),

                // ── CAUSTIC ────────────────────────────────────────────
                slider("Caustic Speed", "Скорость", 0.1f, 5.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                slider("Caustic Scale", "Размер", 1.0f, 20.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                slider("Caustic Intensity", "Интенсивность", 0.001f, 0.05f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                slider("Caustic Alpha", "Прозрачность", 0.3f, 1.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                mode("Caustic Color Mode", "Цвет", "Theme", "Custom")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                color("Caustic Custom Color", "Свой цвет неба")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic") &&
                                        LexoraGui.modeSettings.getOrDefault("Caustic Color Mode", "Theme").equals("Custom")),

                // ── THUNDER ────────────────────────────────────────────
                slider("Thunder Speed", "Скорость", 0.1f, 5.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Scale", "Размер", 1.0f, 20.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Intensity", "Интенсивность", 0.001f, 0.05f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Alpha", "Прозрачность", 0.3f, 1.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                mode("Thunder Color Mode", "Цвет", "Theme", "Custom")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                color("Thunder Custom Color", "Свой цвет неба")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder") &&
                                        LexoraGui.modeSettings.getOrDefault("Thunder Color Mode", "Theme").equals("Custom")),
                slider("Thunder Interval", "Интервал грозы", 1.0f, 10.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Chance", "Шанс молнии", 0.0f, 1.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Glow", "Свечение молнии", 0.1f, 3.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),

                // ── PULSAR ─────────────────────────────────────────────
                slider("Pulsar Speed", "Скорость", 0.1f, 5.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                slider("Pulsar Scale", "Размер", 1.0f, 20.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                slider("Pulsar Intensity", "Интенсивность", 0.001f, 0.05f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                slider("Pulsar Alpha", "Прозрачность", 0.3f, 1.0f)
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                mode("Pulsar Color Mode", "Цвет", "Theme", "Custom")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                color("Pulsar Custom Color", "Свой цвет неба")
                        .visibleIf(() ->
                                ClientData.moduleStates.getOrDefault("World Customizer", false) &&
                                        LexoraGui.moduleStates.getOrDefault("Sky Customizer", false) &&
                                        LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar") &&
                                        LexoraGui.modeSettings.getOrDefault("Pulsar Color Mode", "Theme").equals("Custom")),

                // ── ОСТАЛЬНЫЕ НАСТРОЙКИ ───────────────────────────────
                toggle("Custom Time", "Своё время"),
                slider("World Time", "Время", 0.0f, 24000.0f),
                mode("Weather Mode", "Погода", "Clear", "Rain", "Thunder"),
                toggle("Fog Customizer", "Кастомный туман"),
                slider("Fog Distance", "Дальность тумана", 0.1f, 5.0f),
                mode("Fog Color Mode", "Источник цвета тумана", "Theme", "Custom"),
                color("Custom Fog Color", "Свой цвет тумана").visibleIf(() ->
                        LexoraGui.modeSettings.getOrDefault("Fog Color Mode", "Theme").equals("Custom"))
        ));

        SETTINGS.put("Item Swap", List.of(
                mode("Swap From", "Свапать с", "Тотем", "Шар"),
                mode("Swap To", "Свапать на", "Тотем", "Шар"),
                bind("Item Swap Action", "Кнопка свапа")
        ));

        SETTINGS.put("Lexora IRC", List.of(
                toggle("Lexora IRC DND", "Не беспокоить")
        ));

        SETTINGS.put("Emotes", List.of(
                bind("Radial Menu", "Круговое меню")
        ));

        SETTINGS.put("Elytra Swap", List.of(
                bind("Elytra Swap Action", "Кнопка элитр")
        ));

        SETTINGS.put("Auto Eat", List.of(
                slider("Eat Threshold", "Порог еды", 1.0f, 20.0f)
        ));

        SETTINGS.put("Auto Leave", List.of(
                mode("Leave Mode", "Режим", "Выход", "Команда"),
                slider("Near Cooldown", "Кд /near", 1.0f, 180.0f)
        ));

        SETTINGS.put("Free Look", List.of(
                bind("Free Look Action", "Кнопка обзора")
        ));

        SETTINGS.put("Shift Tap", List.of(
                mode("Shift Mode", "Режим", "Все удары", "Только криты")
        ));

        SETTINGS.put("Item Scroller", List.of(
                slider("Scroller Speed", "Скорость", 1.0f, 20.0f)
        ));

        SETTINGS.put("PvP Save", List.of(
                slider("PvP Time", "Время PvP", 5.0f, 30.0f)
        ));

        SETTINGS.put("Healing Helper", List.of(
                toggle("HH Pulsation", "Пульсация"),
                slider("HH Alpha", "Прозрачность", 10.0f, 255.0f),
                slider("Gapple HP", "HP для яблока", 1.0f, 20.0f),
                slider("E-Gapple HP", "HP для чарки", 1.0f, 20.0f),
                slider("Potion HP", "HP для зелья", 1.0f, 20.0f)
        ));

        SETTINGS.put("Streamer Mode", List.of(
                toggle("Hide Name", "Скрыть ник"),
                toggle("Hide Coords", "Скрыть координаты")
        ));

        SETTINGS.put("No Render", List.of(
                toggle("No Fire", "Убрать огонь"),
                toggle("No Portal", "Убрать портал"),
                toggle("No Totem Anim", "Убрать аним. тотема"),
                toggle("No Weather", "Убрать погоду"),
                toggle("No Grass", "Убрать траву"),
                toggle("No Bad Effects", "Убрать плохие эффекты"),
                toggle("No Hurt Cam", "Убрать тряску")
        ));

        SETTINGS.put("Zoom", List.of(
                mode("Zoom Mode", "Режим", "Hold", "Toggle"),
                slider("Zoom Value", "Сила зума", 1.0f, 12.0f),
                slider("Zoom Smooth", "Плавность", 0.01f, 1.0f),
                slider("Zoom Scroll Step", "Шаг колеса", 0.05f, 1.0f),
                bind("Zoom Action", "Кнопка зума")
        ));

        SETTINGS.put("Jump Circles", List.of(
                slider("Jump Size", "Размер", 0.2f, 4.0f),
                slider("Jump Speed", "Скорость", 0.1f, 3.0f),
                mode("Jump Circle Mode", "Тип эффекта", "Circle", "Wave"),   // <-- новая строка
                mode("Jump Circle Color Mode", "Источник цвета", "Theme", "Custom"),
                color("Jump Circle Custom Color", "Свой цвет").visibleIf(() -> LexoraGui.modeSettings.getOrDefault("Jump Circle Color Mode", "Theme").equals("Custom"))
        ));

        SETTINGS.put("Notifications", List.of(
        ));

        SETTINGS.put("Hearth", List.of(
        ));

        SETTINGS.put("Info", List.of(
        ));

        SETTINGS.put("Potions", List.of(
        ));

        SETTINGS.put("Keybinds", List.of(
        ));

        SETTINGS.put("Armor Status", List.of(
        ));

        SETTINGS.put("Inventory", List.of(
        ));

        SETTINGS.put("Cooldowns", List.of(
        ));

        SETTINGS.put("Target", List.of(
        ));

        SETTINGS.put("Watermark", List.of(
        ));

        SETTINGS.put("Gradient Theme", List.of(
                toggle("Gradient Theme", "Градиент"),
                color("Theme Color 1", "Цвет 1"),
                color("Theme Color 2", "Цвет 2").visibleIf(() ->
                        LexoraGui.moduleStates.getOrDefault("Gradient Theme", true))
        ));
    }

    public static List<ModernSetting> get(String module) {
        return SETTINGS.getOrDefault(module, List.of());
    }

    public static boolean hasSettings(String module) {
        return SETTINGS.containsKey(module) && !SETTINGS.get(module).isEmpty();
    }
}