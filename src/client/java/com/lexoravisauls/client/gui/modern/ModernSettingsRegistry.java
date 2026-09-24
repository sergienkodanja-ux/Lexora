package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lexoravisauls.client.gui.modern.ModernSetting.*;

public class ModernSettingsRegistry {

    private static final Map<String, List<ModernSetting>> SETTINGS = new HashMap<>();

    static {
        SETTINGS.put("Target ESP", List.of(
                mode("Target ESP Mode", "Режим", "Spirits", "Lightning", "Crystals", "Circle", "Skull", "Rhombus", "Round Rhombus"),
                toggle("Red On Damage", "Красный при уроне"),
                toggle("Only On Crit", "Только при крите"),
                slider("ESP Speed", "Скорость", 0.1f, 3.0f),
                slider("Spirits Count", "Кол-во духов", 1.0f, 10.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Spirits")),
                slider("Trail Length", "Длина следа", 5.0f, 100.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Spirits")),
                slider("Crystal Size", "Размер кристаллов", 0.1f, 2.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Crystals")),
                slider("Crystal Count", "Кол-во кристаллов", 8.0f, 30.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Crystals")),
                slider("Skull Size", "Размер черепа", 0.5f, 3.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Target ESP Mode", "Spirits").equals("Skull")),
                slider("Rhombus Size", "Размер ромба", 0.3f, 3.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Target ESP Mode", "Spirits").contains("Rhombus"))
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
                toggle("Hit Glow", "Свечение")
        ));

        SETTINGS.put("AuraParticles", List.of(
                slider("Aura Particles Count", "Кол-во", 1.0f, 120.0f),
                slider("Aura Particles Size", "Размер", 0.05f, 0.60f),
                slider("Aura Particles Radius", "Радиус", 1.0f, 1.5f),
                slider("Aura Particles Delay", "Задержка", 0.1f, 2.0f),
                mode("Aura Particles Color Mode", "Режим Цвета", "Client", "Custom"),
                color("Aura Particles Custom Color", "Свой Цвет").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Aura Particles Color Mode", "Client").equals("Custom"))
        ));

        SETTINGS.put("Motion Blur", List.of(
                slider("Motion Blur Strength", "Сила", 0.0f, 4.0f),
                toggle("Motion Blur RRC", "Адаптация к герцовке"),
                mode("Motion Blur Quality", "Качество", "Низкое", "Среднее", "Высокое", "Ультра")
        ));

        SETTINGS.put("Kill Effect", List.of(
                mode("Kill Effect Mode", "Режим", "Soul Ascension", "Cosmic Singularity", "Divine Wrath", "Blood Nova"),
                toggle("Trigger On Any Death", "Эффект на любую смерть"),
                slider("Ascension Duration", "Длительность (сек)", 1.0f, 6.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Soul Ascension")),
                slider("Ascension Height", "Высота полёта", 1.0f, 5.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Soul Ascension")),
                slider("Ascension Spin Speed", "Скорость кручения", 0.0f, 5.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Soul Ascension")),
                slider("Singularity Duration", "Длительность (сек)", 1.0f, 4.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Cosmic Singularity")),
                slider("Singularity Scale", "Масштаб", 0.5f, 2.5f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Cosmic Singularity")),
                slider("Divine Duration", "Длительность (сек)", 1.0f, 4.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Divine Wrath")),
                slider("Divine Beam Width", "Ширина луча", 0.5f, 2.5f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Divine Wrath")),
                slider("Nova Duration", "Длительность (сек)", 1.0f, 4.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Blood Nova")),
                slider("Nova Spikes", "Кол-во шипов", 6.0f, 20.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Blood Nova")),
                slider("Nova Radius", "Радиус разрыва", 1.0f, 4.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Kill Effect Mode", "Soul Ascension").equals("Blood Nova"))
        ));

        SETTINGS.put("Tape Mouse", List.of(
                mode("Tape Mouse Button", "Кнопка", "Left", "Right"),
                slider("Tape Mouse CPS", "Скорость CPS", 1.0f, 20.0f)
        ));

        SETTINGS.put("Loot Notifier", List.of(
                mode("Loot Notifier Mode", "Сервер", "FunTime", "HolyWorld")
        ));

        SETTINGS.put("GPS", List.of(
                slider("GPS 3D Marker Size", "Размер метки", 0.5f, 2.0f),
                toggle("GPS Show Distance", "Показывать дистанцию"),
                toggle("GPS Auto", "АвтоGPS"),
                mode("GPS Mode", "Сервер", "Funtime", "HolyWorld").visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("GPS Auto", false))
        ));

        SETTINGS.put("Motion Clones", List.of(
                toggle("Clone Normal", "Обычные клоны"),
                slider("Clone Delay", "Задержка (сек)", 1.5f, 4.0f).visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Clone Normal", true)),
                slider("Clone Life", "Жизнь", 6.0f, 60.0f),
                slider("Clone Totem Life", "Жизнь тотем", 10.0f, 200.0f).visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Clone Totem", true)),
                slider("Clone Scale", "Размер", 0.7f, 1.3f),
                slider("Clone Alpha", "Яркость", 0.1f, 1.0f),
                toggle("Clone Totem", "При тотеме"),
                toggle("Clone Totem Self", "Твой тотем").visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Clone Totem", true)),
                toggle("Clone Totem Players", "Игроки").visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Clone Totem", true)),
                toggle("Clone First Person", "От 1 лица"),
                mode("Clone Color Mode", "Режим Цвета", "Client", "Custom"),
                color("Clone Custom Color", "Свой Цвет").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Clone Color Mode", "Client").equals("Custom"))
        ));

        SETTINGS.put("Aspect Ratio", List.of(
                mode("Ratio Mode", "Режим", "Default", "Custom"),
                slider("Aspect Ratio Val", "Значение", 0.5f, 3.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Ratio Mode", "Default").equals("Custom"))
        ));

        SETTINGS.put("View Model", List.of(
                mode("VM Anim", "Режим анимации", "Standard", "Под наклоном", "Наклон", "Вращение на 360", "От себя", "Боньк"),
                toggle("VM Target Hit", "При таргете"),
                pad2d("Right Hand X", "Right Hand Y", "Правая рука XY", -2.5f, 2.5f, -2.0f, 2.0f),
                slider("Right Hand Z", "Правая рука Z", -1.5f, 1.5f),
                pad2d("Left Hand X", "Left Hand Y", "Левая рука XY", -2.5f, 2.5f, -2.0f, 2.0f),
                slider("Left Hand Z", "Левая рука Z", -1.5f, 1.5f),
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
                        ClientData.modeSettings.getOrDefault("China Hat Color Source", "Client").equals("Custom")),
                color("China Hat Color 1", "Цвет 1").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("China Hat Color Source", "Client").equals("Custom")),
                color("China Hat Color 2", "Цвет 2").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("China Hat Color Source", "Client").equals("Custom")
                                && ClientData.modeSettings.getOrDefault("China Hat Custom Color Mode", "Single").equals("Gradient"))
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
                mode("Part. Texture", "Текстура", "Bloom", "Star", "Heart", "Dollar", "Snow", "Star 2", "Kronex", "Random", "Cube"),
                slider("Part. Speed", "Скорость", 0.1f, 3.0f),
                slider("Part. Size", "Размер", 0.1f, 2.0f),
                toggle("Part. Glow", "Свечение"),
                toggle("Part. Rotation", "Вращение"),
                toggle("Part. Through Walls", "Сквозь стены"),
                toggle("Part. Strong Y", "Сильный Y разброс"),
                header("Цвет частиц"),
                mode("Part. Color Mode", "Источник цвета", "Theme", "Custom"),
                mode("Part. Color Type", "Тип цвета", "Solid", "Gradient").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom")),
                color("Part. Custom Color", "Свой цвет").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom") &&
                                ClientData.modeSettings.getOrDefault("Part. Color Type", "Solid").equals("Solid")),
                color("Part. Custom Color 1", "Цвет 1 (Градиент)").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom") &&
                                ClientData.modeSettings.getOrDefault("Part. Color Type", "Solid").equals("Gradient")),
                color("Part. Custom Color 2", "Цвет 2 (Градиент)").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Part. Color Mode", "Theme").equals("Custom") &&
                                ClientData.modeSettings.getOrDefault("Part. Color Type", "Solid").equals("Gradient")),
                header("Триггеры спавна"),
                toggle("Part. Attack", "При ударе"),
                slider("Part. Attack Count", "Кол-во (Удар)", 5.0f, 50.0f).visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Part. Attack", true)),
                toggle("Part. Totem", "При тотеме"),
                slider("Part. Totem Count", "Кол-во (Тотем)", 2.0f, 30.0f).visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Part. Totem", true)),
                toggle("Disable Vanilla Totem", "Скрыть ванильный тотем").visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Part. Totem", true)),
                toggle("Part. Move", "При ходьбе (3-е лицо)"),
                slider("Part. Move Count", "Кол-во (Ходьба)", 1.0f, 10.0f).visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Part. Move", false)),
                toggle("Part. Throw", "След снарядов"),
                slider("Part. Throw Count", "Кол-во (Снаряды)", 1.0f, 20.0f).visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Part. Throw", true)),
                toggle("Part. Idle", "Эмбиент (В покое)"),
                slider("Part. Idle Count", "Кол-во (Эмбиент)", 1.0f, 30.0f).visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Part. Idle", false)),
                slider("Part. Idle Range", "Радиус (Эмбиент)", 4.0f, 32.0f).visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Part. Idle", false))
        ));

        SETTINGS.put("Hit Color", List.of(
                mode("Hit Color Mode", "Источник цвета", "Theme", "Custom"),
                mode("Hit Target", "Куда красить", "All", "Body Only"),
                color("Hit Custom Color", "Свой цвет").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Hit Color Mode", "Theme").equals("Custom"))
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
                        ClientData.modeSettings.getOrDefault("Overlay Color Source", "Client").equals("Custom")),
                slider("Overlay Opacity", "Прозрачность", 0.1f, 1.0f)
        ));

        SETTINGS.put("Hand Shaders", List.of(
                mode("Hand Mode", "Эффект", "Standard", "Smoke", "Snow", "Solid", "Stripes"),
                slider("Hand Glow %", "Свечение", 0.0f, 100.0f)
        ));

        SETTINGS.put("Trails", List.of(
                toggle("Trail Show 1st Person", "Вид от 1-го лица"),
                slider("Trail Length", "Длина", 10.0f, 100.0f),
                mode("Trail Color Mode", "Источник цвета", "Theme", "Custom"),
                color("Trail Custom Color", "Свой цвет").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Trail Color Mode", "Theme").equals("Custom"))
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
                        ClientData.modeSettings.getOrDefault("Nimb Color Source", "Client").equals("Custom")),
                color("Nimb Color 1", "Цвет 1").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Nimb Color Source", "Client").equals("Custom")),
                color("Nimb Color 2", "Цвет 2").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Nimb Color Source", "Client").equals("Custom")
                                && ClientData.modeSettings.getOrDefault("Nimb Custom Color Mode", "Single").equals("Gradient"))
        ));
        SETTINGS.put("World Customizer", List.of(
                toggle("Sky Customizer", "Кастомное небо"),
                mode("Sky Type", "Тип неба", "Standard", "Water", "Caustic", "Thunder", "Pulsar")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false)),

                // Standard
                mode("Sky Color Mode", "Источник цвета", "Theme", "Custom")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Standard")),
                color("Custom Sky Color", "Цвет неба")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Standard") &&
                                ClientData.modeSettings.getOrDefault("Sky Color Mode", "Theme").equals("Custom")),

                // Water
                slider("Water Speed", "Скорость", 0.1f, 5.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                slider("Water Scale", "Размер", 1.0f, 20.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                slider("Water Intensity", "Интенсивность", 0.001f, 0.05f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                slider("Water Alpha", "Прозрачность", 0.3f, 1.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                mode("Water Color Mode", "Цвет", "Theme", "Custom")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water")),
                color("Water Custom Color", "Свой цвет неба")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Water") &&
                                ClientData.modeSettings.getOrDefault("Water Color Mode", "Theme").equals("Custom")),

                // Caustic
                slider("Caustic Speed", "Скорость", 0.1f, 5.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                slider("Caustic Scale", "Размер", 1.0f, 20.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                slider("Caustic Intensity", "Интенсивность", 0.001f, 0.05f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                slider("Caustic Alpha", "Прозрачность", 0.3f, 1.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                mode("Caustic Color Mode", "Цвет", "Theme", "Custom")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic")),
                color("Caustic Custom Color", "Свой цвет неба")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Caustic") &&
                                ClientData.modeSettings.getOrDefault("Caustic Color Mode", "Theme").equals("Custom")),

                // Thunder
                slider("Thunder Speed", "Скорость", 0.1f, 5.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Scale", "Размер", 1.0f, 20.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Intensity", "Интенсивность", 0.001f, 0.05f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Alpha", "Прозрачность", 0.3f, 1.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                mode("Thunder Color Mode", "Цвет", "Theme", "Custom")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                color("Thunder Custom Color", "Свой цвет неба")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder") &&
                                ClientData.modeSettings.getOrDefault("Thunder Color Mode", "Theme").equals("Custom")),
                slider("Thunder Interval", "Интервал грозы", 1.0f, 10.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Chance", "Шанс молнии", 0.0f, 1.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),
                slider("Thunder Glow", "Свечение молнии", 0.1f, 3.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Thunder")),

                // Pulsar
                slider("Pulsar Speed", "Скорость", 0.1f, 5.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                slider("Pulsar Scale", "Размер", 1.0f, 20.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                slider("Pulsar Intensity", "Интенсивность", 0.001f, 0.05f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                slider("Pulsar Alpha", "Прозрачность", 0.3f, 1.0f)
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                mode("Pulsar Color Mode", "Цвет", "Theme", "Custom")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar")),
                color("Pulsar Custom Color", "Свой цвет неба")
                        .visibleIf(() -> ClientData.moduleStates.getOrDefault("Sky Customizer", false) &&
                                ClientData.modeSettings.getOrDefault("Sky Type", "Standard").equals("Pulsar") &&
                                ClientData.modeSettings.getOrDefault("Pulsar Color Mode", "Theme").equals("Custom")),

                toggle("Custom Time", "Своё время"),
                slider("World Time", "Время", 0.0f, 24000.0f),
                mode("Weather Mode", "Погода", "Clear", "Rain", "Thunder"),
                toggle("Fog Customizer", "Кастомный туман"),
                slider("Fog Distance", "Дальность тумана", 0.1f, 5.0f),
                mode("Fog Color Mode", "Источник цвета тумана", "Theme", "Custom"),
                color("Custom Fog Color", "Свой цвет тумана").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Fog Color Mode", "Theme").equals("Custom"))
        ));
        SETTINGS.put("Crosshair", List.of(
                mode("Crosshair Type", "Тип", "Classic", "T-Shape", "Dot", "Custom"),
                toggle("Crosshair Outline", "Обводка"),
                toggle("Crosshair Dynamic Gap", "Динамический зазор"),
                toggle("Crosshair Highlight Target", "Подсветка цели"),
                toggle("Crosshair Center Dot", "Точка в центре"),
                mode("Crosshair Color Mode", "Режим цвета", "Theme", "Custom"),
                color("Crosshair Custom Color", "Свой цвет").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Crosshair Color Mode", "Theme").equals("Custom")),
                slider("Crosshair Thickness", "Толщина", 0.5f, 5.0f),
                slider("Crosshair Length", "Длина", 1.0f, 20.0f).visibleIf(() ->
                        !ClientData.modeSettings.getOrDefault("Crosshair Type", "Classic").equals("Custom")),
                slider("Crosshair Gap", "Зазор", 0.0f, 15.0f).visibleIf(() ->
                        !ClientData.modeSettings.getOrDefault("Crosshair Type", "Classic").equals("Custom")),
                slider("Crosshair Gap Increase", "Увеличение зазора", 0.0f, 20.0f).visibleIf(() ->
                        !ClientData.modeSettings.getOrDefault("Crosshair Type", "Classic").equals("Custom")),
                crosshairCanvas("CrosshairGrid", "Холст прицела").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Crosshair Type", "Classic").equals("Custom"))
        ));

        SETTINGS.put("Jump Circles", List.of(
                slider("Jump Size", "Размер", 0.2f, 4.0f),
                slider("Jump Speed", "Скорость", 0.1f, 3.0f),
                mode("Jump Circle Mode", "Тип эффекта", "Circle", "Wave"),
                mode("Jump Circle Color Mode", "Источник цвета", "Theme", "Custom"),
                color("Jump Circle Custom Color", "Свой цвет").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Jump Circle Color Mode", "Theme").equals("Custom"))
        ));

        SETTINGS.put("Item Swap", List.of(
                mode("Swap Mode", "Режим свапа", "Двойной", "Тройной"),
                mode("Swap From", "Свапать с", "Тотем", "Шар", "Щит").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Swap Mode", "Двойной").equals("Двойной")),
                mode("Swap To", "Свапать на", "Тотем", "Шар", "Щит").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Swap Mode", "Двойной").equals("Двойной")),
                toggle("Only Enchanted Totems", "Только зач. тотемы"),
                bind("Item Swap Action", "Кнопка свапа")
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

        SETTINGS.put("Optimization", List.of(
                header("Окружение и мир"),
                toggle("Opt No Clouds", "Отключить 3D облака"),
                toggle("Opt No Fog", "Отключить туман"),
                toggle("Opt Fast Weather", "Убрать дождь и снег"),

                header("Сущности и игроки"),
                toggle("Opt Entity Culling", "Ограничить дальность сущностей"),
                slider("Opt Entity Dist", "Дистанция сущностей", 12.0f, 96.0f),
                toggle("Opt No Armor", "Отключить рендер брони"),
                toggle("Opt No Nametags", "Отключить никнеймы"),
                toggle("Opt No Shadows", "Отключить тени сущностей"),
                toggle("Opt No Glint", "Отключить блеск чар (Glint)"),
                toggle("Opt No Armor Stands", "Скрыть стойки для брони"),
                toggle("Opt No Beacons", "Убрать лучи маяков"),

                header("Дроп и сундуки"),
                toggle("Opt Fast Items", "Ограничить выпавший лут"),
                slider("Opt Items Dist", "Дистанция лута", 8.0f, 48.0f),
                toggle("Opt Block Culling", "Оптимизация сундуков/блоков"),
                slider("Opt Block Dist", "Дистанция сундуков/блоков", 16.0f, 96.0f),

                header("Частицы"),
                toggle("Opt All Particles", "Отключить ВСЕ частицы"),
                toggle("Opt Rain Particles", "Частицы дождя и капель"),
                toggle("Opt Potion Particles", "Частицы зелий"),
                toggle("Opt Explosion Particles", "Частицы взрывов"),
                toggle("Opt Smoke Particles", "Частицы дыма и костров"),
                toggle("Opt Fire Particles", "Частицы огня и лавы"),
                toggle("Opt Totem Particles", "Частицы тотема"),

                header("Интерфейс и свет"),
                toggle("Opt Fast Lighting", "Быстрое освещение (30 FPS)"),
                toggle("Opt Fast HUD", "Убрать виньетку экрана")
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

        // ── ЧИСТЫЕ ХУДЫ БЕЗ ЛИШНИХ НАСТРОЕК ────────────────────────────────────
        SETTINGS.put("Info HUD", List.of(
                toggle("Info HUD Blur", "Размытие фона (Blur)"),
                slider("Info HUD Scale", "Масштаб", 0.5f, 2.0f),
                toggle("Info HUD FPS", "Показывать FPS"),
                toggle("Info HUD TPS", "Показывать TPS"),
                toggle("Info HUD Ping", "Показывать Ping"),
                toggle("Info HUD Speed", "Показывать Скорость (BPS)")
        ));

        SETTINGS.put("Target HUD", List.of(
                toggle("Target HUD Blur", "Размытие фона (Blur)"),
                slider("Target HUD Scale", "Масштаб", 0.5f, 2.0f)
        ));

        SETTINGS.put("TNT Detect", List.of(
                toggle("TNT Detect Blur", "Размытие фона (Blur)"),
                slider("TNT Detect Scale", "Масштаб", 0.5f, 2.0f)
        ));

        SETTINGS.put("Scoreboard HUD", List.of(
                toggle("Scoreboard HUD Blur", "Размытие фона (Blur)"),
                slider("Scoreboard HUD Scale", "Масштаб", 0.5f, 2.0f)
        ));

        SETTINGS.put("Keybinds", List.of(
                toggle("Keybinds Blur", "Размытие фона (Blur)"),
                slider("Keybinds Scale", "Масштаб", 0.5f, 2.0f)
        ));

        SETTINGS.put("Armor Status", List.of(
                toggle("Armor Status Blur", "Размытие фона (Blur)"),
                slider("Armor Status Scale", "Масштаб", 0.5f, 2.0f)
        ));

        SETTINGS.put("Inventory HUD", List.of(
                toggle("Inventory HUD Blur", "Размытие фона (Blur)"),
                slider("Inventory HUD Scale", "Масштаб", 0.5f, 2.0f)
        ));

        SETTINGS.put("Cooldowns", List.of(
                toggle("Cooldowns Blur", "Размытие фона (Blur)"),
                slider("Cooldowns Scale", "Масштаб", 0.5f, 2.0f)
        ));

        SETTINGS.put("Potions", List.of(
                toggle("Potions Blur", "Размытие фона (Blur)"),
                slider("Potions Scale", "Масштаб", 0.5f, 2.0f)
        ));

        SETTINGS.put("Fast Swap", List.of(
                toggle("Show Hotbar Binds", "Показывать бинды на хотбаре"),
                bind("Bind_Дезка", "Дезка"),
                bind("Bind_Явная пыль", "Явная пыль"),
                bind("Bind_Божья аура", "Божья аура"),
                bind("Bind_Пласт", "Пласт"),
                bind("Bind_Трапка ФТ", "Трапка ФТ"),
                bind("Bind_Стан", "Стан ХВ"),
                bind("Bind_Взр. штучка", "Взр. штучка"),
                bind("Bind_Трапка ХВ", "Трапка ХВ"),
                bind("Bind_Взр. трапка", "Взр. трапка"),
                bind("Bind_Ком снега", "Ком снега"),
                bind("Bind_Хорус", "Хорус"),
                bind("Bind_Эндер перл", "Эндер перл"),
                bind("Bind_Исцеление", "Зелье исцеления")
        ));

        SETTINGS.put("Item Highlighter", List.of(
                slider("Highlighter Alpha", "Прозрачность подсветки", 0.0f, 255.0f),
                toggle("Highlighter Pulsation", "Пульсация подсветки"),
                toggle("HL_Custom_Colors", "Кастомные цвета"),

                header("Предметы"),
                toggle("HL_Тотем", "Тотем"),
                color("HLC_Тотем", "Цвет: Тотем").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Хорус", "Хорус"),
                color("HLC_Хорус", "Цвет: Хорус").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Эндер Жемчуг", "Эндер Жемчуг (Перл)"),
                color("HLC_Эндер Жемчуг", "Цвет: Эндер Жемчуг").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Дезориентация", "Дезориентация (Дезка)"),
                color("HLC_Дезориентация", "Цвет: Дезка").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Явная Пыль", "Явная Пыль (Явка)"),
                color("HLC_Явная Пыль", "Цвет: Явка").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Божья аура", "Божья аура"),
                color("HLC_Божья аура", "Цвет: Божья аура").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Огненный Смерч", "Огненный Смерч"),
                color("HLC_Огненный Смерч", "Цвет: Смерч").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Пузырек Опыта", "Пузырек Опыта"),
                color("HLC_Пузырек Опыта", "Цвет: Опыт").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Трапка (Лом)", "Трапка (Лом)"),
                color("HLC_Трапка (Лом)", "Цвет: Трапка Лом").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Трапка ФТ", "Трапка ФТ (Паутина)"),
                color("HLC_Трапка ФТ", "Цвет: Трапка ФТ").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Пласт", "Пласт"),
                color("HLC_Пласт", "Цвет: Пласт").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Гепл", "Золотое яблоко (Гепл)"),
                color("HLC_Гепл", "Цвет: Гепл").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Чар. Гепл", "Чар. Гепл (Чарка)"),
                color("HLC_Чар. Гепл", "Цвет: Чар. Гепл").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Трапка ХВ", "Трапка ХВ"),
                color("HLC_Трапка ХВ", "Цвет: Трапка ХВ").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Взрывная Трапка", "Взрывная Трапка"),
                color("HLC_Взрывная Трапка", "Цвет: Взр. Трапка").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Светильник Джейка", "Светильник Джейка"),
                color("HLC_Светильник Джейка", "Цвет: Светильник").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false)),

                toggle("HL_Стан", "Стан (Звезда Незера)"),
                color("HLC_Стан", "Цвет: Стан").visibleIf(() -> ClientData.moduleStates.getOrDefault("HL_Custom_Colors", false))
        ));

        SETTINGS.put("Lock Slot", List.of(
                toggle("LockSlot_0", "Слот 1"),
                toggle("LockSlot_1", "Слот 2"),
                toggle("LockSlot_2", "Слот 3"),
                toggle("LockSlot_3", "Слот 4"),
                toggle("LockSlot_4", "Слот 5"),
                toggle("LockSlot_5", "Слот 6"),
                toggle("LockSlot_6", "Слот 7"),
                toggle("LockSlot_7", "Слот 8"),
                toggle("LockSlot_8", "Слот 9")
        ));

        SETTINGS.put("Lexora IRC", List.of(
                toggle("Lexora IRC DND", "Не беспокоить")
        ));

        SETTINGS.put("Emotes", List.of(
                bind("Radial Menu", "Круговое меню")
        ));

        SETTINGS.put("Gradient Theme", List.of(
                toggle("Gradient Theme", "Градиент"),
                color("Theme Color 1", "Цвет 1"),
                color("Theme Color 2", "Цвет 2").visibleIf(() ->
                        ClientData.moduleStates.getOrDefault("Gradient Theme", true))
        ));
        SETTINGS.put("Custom Swords", List.of(
                mode("Custom Swords Model", "Модель меча",
                        "Katana", "Abominable Blade", "Abominable Great Saber", "Abominable Scythe", "Acidic Cleaver",
                        "Amethyst Shuriken", "Ancient Royal Great Sword", "Aquatic Sacred Blade", "Arcanethyst", "Ashura's Blade",
                        "Awakened Lichblade", "Blood Edge", "Bloody Death", "Bramblethorn", "Brimstone Claymore",
                        "Carian Knight's Sword", "Chrono Blade", "Corrupted Mythic Blade", "Creation Splitter", "Crescent Rose",
                        "Cyber Katana", "Cyber Mantis Blade", "Cyber Sword", "Cybernetic Chainsaw Blade", "Cybernetic Katana",
                        "Cybernetic Knife", "Dainsleif", "Dark Blade", "Dark Cleaver", "Death Knight's Dagger",
                        "Death Knight's Sword", "Demigod's Unholy Blade", "Demigod's Unholy Halberd", "Demon Lord's Great Axe", "Demon Lord's Sword",
                        "Demonic Blade", "Demonic Cleaver", "Divine Axe Rhitta", "Divine Justice", "Divine Punisher",
                        "Divine Reaper", "Dragon Slaying blade", "Edge Of The Astral Plane", "Emberblade", "Enigma",
                        "Epic Sword", "Estoc", "Fallen God's Spear", "Fallen God's Sword", "Floral Longsword",
                        "Floral Sabre", "Forest Guardian's Glaive", "Frost Axe", "Frost Blade", "Frost Scythe",
                        "Hearthflame", "Hero Sword", "Holy Moonlight Sword", "Hornet's Needle", "Icewhisper",
                        "Jade Halberd", "Legendary Sword", "Longsword", "Magi Scythe", "Masamune",
                        "Mjolnir", "Molten Blade", "Molten Sword", "Muramasa", "Mystical Spellblade",
                        "Mythic Blade", "Ocean's Rage", "Partisan", "Pharaoh's Treasure", "Pheonix Grace",
                        "Plague Longsword", "Power Fuse Hammer", "Power Fuse Sword", "Requiem of the Ninth Abyss", "Ribbon Cleaver",
                        "Righteous Relic", "Rivers Of Blood", "Royal Chakram", "Royal Rapier", "Sabre",
                        "Scissor Blade", "Sculk Cleaver", "Sculk Scythe", "Sculk Sword", "Sentinel's Will",
                        "Silverine Blade", "Soul Claws", "Soul Collector", "Soul Devourer", "Soul Edge",
                        "Soul Harvester", "Soul Stealer", "Soulrender", "Star's Edge", "Steel Sword",
                        "Stop Sign", "Storm Bringer", "Storm's Edge", "Sunbreak", "Tengen's Blade",
                        "Terra Blade", "Thousand Demon Daggers", "Thunder Bringer", "Thunderbrand", "True Excalibur",
                        "Vampiric Needle", "Wakizashi", "Watcher Claymore", "Watching Warglaive", "Waxweaver",
                        "Whisperwind", "Wickpiercer", "Wraith Scythe", "Yoru"
                )
        ));

        List<ModernSetting> atmosphereSettings = List.of(
                mode("Weather Visual Mode", "Режим", "Rain", "Wet Floor"),
                toggle("Weather Rain Only", "Только при осадках в мире"),

                // --- Rain Settings ---
                mode("Rain Preset", "Пресет дождя", "Rain", "Drizzle", "Downpour", "Storm").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                slider("Rain Density", "Плотность дождя", 0.2f, 3.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                slider("Rain Radius", "Радиус отрисовки", 10.0f, 48.0f).visibleIf(() ->
                        !ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Wet Floor")),
                slider("Rain Altitude", "Высота спавна", 8.0f, 32.0f).visibleIf(() ->
                        !ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Wet Floor")),
                slider("Rain Drop Size", "Размер капель", 0.4f, 2.5f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                slider("Rain Fall Speed", "Скорость падения", 0.3f, 2.5f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                slider("Rain Wind", "Сила ветра", 0.0f, 3.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                slider("Rain Opacity", "Прозрачность дождя", 0.1f, 1.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                toggle("Rain Splashes", "Всплески на земле").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                toggle("Rain Droplets", "Брызги капель").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain") &&
                        ClientData.moduleStates.getOrDefault("Rain Splashes", true)),
                toggle("Rain Mist", "Туман на земле").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                toggle("Rain Lightning", "Вспышки молний").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                toggle("Rain Sky Check", "Не спавнить под крышей").visibleIf(() ->
                        !ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Wet Floor")),
                mode("Rain Color Mode", "Цвет дождя", "Realistic", "Client", "Custom").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain")),
                color("Rain Custom Color", "Свой цвет дождя").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Rain") &&
                        ClientData.modeSettings.getOrDefault("Rain Color Mode", "Realistic").equals("Custom")),

                // --- Wet Floor Settings ---
                slider("Wet Strength", "Сила отражений SSR", 0.1f, 1.8f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Wet Floor")),
                slider("Wet Darkening", "Потемнение блоков", 0.0f, 0.8f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Wet Floor")),
                mode("Wet Quality", "Качество SSR", "Balanced", "Performance", "High", "Ultra").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Wet Floor")),
                toggle("Wet Ripples", "Рябь на воде").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Wet Floor")),
                slider("Ripple Speed", "Скорость ряби", 0.2f, 3.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("Weather Visual Mode", "Rain").equals("Wet Floor") &&
                        ClientData.moduleStates.getOrDefault("Wet Ripples", false))
        );
        SETTINGS.put("Atmosphere", atmosphereSettings);
        SETTINGS.put("WeatherFX", atmosphereSettings);

        SETTINGS.put("Virtual Desktop", List.of(
                mode("VD Display Mode", "Режим экрана", "Cyber OS", "Windows Mirror"),
                slider("VD Scale", "Размер экрана", 0.6f, 4.0f),
                slider("VD Distance", "Дистанция от игрока", 1.2f, 6.0f),
                mode("VD Quality", "Качество (Mirror)", "720p HD", "1080p Full", "540p Fast", "360p Low").visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("VD Display Mode", "Cyber OS").equals("Windows Mirror")),
                slider("VD FPS", "FPS захвата", 10.0f, 60.0f).visibleIf(() ->
                        ClientData.modeSettings.getOrDefault("VD Display Mode", "Cyber OS").equals("Windows Mirror")),
                toggle("VD Crosshair Click", "Клик прицелом"),
                toggle("VD Crosshair Scroll", "Скролл прицелом")
        ));
    }

    public static List<ModernSetting> get(String module) {
        return SETTINGS.getOrDefault(module, List.of());
    }

    public static boolean hasSettings(String module) {
        return SETTINGS.containsKey(module) && !SETTINGS.get(module).isEmpty();
    }
}
