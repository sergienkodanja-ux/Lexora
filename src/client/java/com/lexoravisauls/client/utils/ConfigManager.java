package com.lexoravisauls.client.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.events.DynamicIslandRenderer;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configDir;

    public static String currentConfig = "default";

    private static void initDir() {
        if (configDir == null) {
            File mcDir = MinecraftClient.getInstance().runDirectory;
            configDir = new File(mcDir, "LexoraVisuals/configs");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
        }
    }

    public static void saveConfig() {
        saveConfig(currentConfig);
    }

    public static void saveConfig(String name) {
        initDir();
        File file = new File(configDir, name + ".json");

        try (FileWriter writer = new FileWriter(file)) {
            JsonObject root = new JsonObject();

            // Сохранение модулей (вкл/выкл)
            JsonObject modules = new JsonObject();
            for (Map.Entry<String, Boolean> entry : LexoraGui.moduleStates.entrySet()) {
                modules.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("Modules", modules);

            // Сохранение ползунков, масштабов, а также координат InfoHud и MusicHud (т.к. они лежат здесь)
            JsonObject sliders = new JsonObject();
            for (Map.Entry<String, Float> entry : LexoraGui.numSettings.entrySet()) {
                sliders.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("Sliders", sliders);

            // Сохранение режимов (Mode)
            JsonObject modes = new JsonObject();
            for (Map.Entry<String, String> entry : LexoraGui.modeSettings.entrySet()) {
                modes.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("Modes", modes);

            // Сохранение цветов
            JsonObject colors = new JsonObject();
            for (Map.Entry<String, float[]> entry : LexoraGui.colorSettings.entrySet()) {
                float[] value = entry.getValue();
                JsonObject color = new JsonObject();
                color.addProperty("h", value.length > 0 ? value[0] : 0f);
                color.addProperty("s", value.length > 1 ? value[1] : 1f);
                color.addProperty("v", value.length > 2 ? value[2] : 1f);
                colors.add(entry.getKey(), color);
            }
            root.add("Colors", colors);

            // Сохранение биндов клавиш
            JsonObject binds = new JsonObject();
            for (Map.Entry<String, Integer> entry : LexoraGui.moduleBinds.entrySet()) {
                binds.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("Binds", binds);

            // --- СОХРАНЕНИЕ ПОЗИЦИЙ СТАТИЧНЫХ ХУДОВ ---
            JsonObject positions = new JsonObject();


            // Вотермарка
            positions.addProperty("islandX", DynamicIslandRenderer.islandX);
            positions.addProperty("islandY", DynamicIslandRenderer.islandY);

            // Основные худы из HudManager
            positions.addProperty("targetX", HudManager.targetX);
            positions.addProperty("targetY", HudManager.targetY);
            positions.addProperty("keybindsX", HudManager.keybindsX);
            positions.addProperty("keybindsY", HudManager.keybindsY);
            positions.addProperty("armorX", HudManager.armorX);
            positions.addProperty("armorY", HudManager.armorY);
            positions.addProperty("invX", HudManager.invX);
            positions.addProperty("invY", HudManager.invY);
            positions.addProperty("coolX", HudManager.coolX);
            positions.addProperty("coolY", HudManager.coolY);
            positions.addProperty("potionX", HudManager.potionX);
            positions.addProperty("potionY", HudManager.potionY);
            positions.addProperty("hearthX", HudManager.hearthX);
            positions.addProperty("hearthY", HudManager.hearthY);
            positions.addProperty("tntX", HudManager.tntX);
            positions.addProperty("tntY", HudManager.tntY);
            // BUG FIX: scoreboardX/Y теперь тоже сохраняются (раньше только загружались)
            positions.addProperty("scoreboardX", HudManager.scoreboardX);
            positions.addProperty("scoreboardY", HudManager.scoreboardY);



            root.add("Positions", positions);

            // Сохранение кастомных тем
            JsonObject customThemes = new JsonObject();
            for (String themeName : LexoraGui.savedThemes) {
                JsonObject themeObj = new JsonObject();

                float[] c1 = LexoraGui.colorSettings.getOrDefault("ThemePreset_" + themeName + "_1", new float[]{0f, 1f, 1f});
                float[] c2 = LexoraGui.colorSettings.getOrDefault("ThemePreset_" + themeName + "_2", new float[]{0f, 1f, 1f});

                JsonObject color1 = new JsonObject();
                color1.addProperty("h", c1[0]);
                color1.addProperty("s", c1[1]);
                color1.addProperty("v", c1[2]);

                JsonObject color2 = new JsonObject();
                color2.addProperty("h", c2[0]);
                color2.addProperty("s", c2[1]);
                color2.addProperty("v", c2[2]);

                themeObj.add("color1", color1);
                themeObj.add("color2", color2);
                customThemes.add(themeName, themeObj);
            }
            root.add("CustomThemes", customThemes);

            GSON.toJson(root, writer);
            currentConfig = name;
            updateConfigList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadConfig(String name) {
        initDir();
        File file = new File(configDir, name + ".json");
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("Modules")) {
                JsonObject modules = root.getAsJsonObject("Modules");
                for (String key : modules.keySet()) {
                    LexoraGui.moduleStates.put(key, modules.get(key).getAsBoolean());
                }
            }

            if (root.has("Sliders")) {
                JsonObject sliders = root.getAsJsonObject("Sliders");
                for (String key : sliders.keySet()) {
                    LexoraGui.numSettings.put(key, sliders.get(key).getAsFloat());
                }
            }

            if (root.has("Modes")) {
                JsonObject modes = root.getAsJsonObject("Modes");
                for (String key : modes.keySet()) {
                    LexoraGui.modeSettings.put(key, modes.get(key).getAsString());
                }
            }

            if (root.has("Colors")) {
                JsonObject colors = root.getAsJsonObject("Colors");
                for (String key : colors.keySet()) {
                    JsonObject color = colors.getAsJsonObject(key);
                    float h = color.has("h") ? color.get("h").getAsFloat() : 0f;
                    float s = color.has("s") ? color.get("s").getAsFloat() : 1f;
                    float v = color.has("v") ? color.get("v").getAsFloat() : 1f;
                    LexoraGui.colorSettings.put(key, new float[]{h, s, v});
                }
            }

            if (root.has("Binds")) {
                JsonObject binds = root.getAsJsonObject("Binds");
                for (String key : binds.keySet()) {
                    LexoraGui.moduleBinds.put(key, binds.get(key).getAsInt());
                }
            }

            // --- ЗАГРУЗКА ПОЗИЦИЙ СТАТИЧНЫХ ХУДОВ ---
            if (root.has("Positions")) {
                JsonObject positions = root.getAsJsonObject("Positions");


                // Вотермарка
                if (positions.has("islandX")) DynamicIslandRenderer.islandX = positions.get("islandX").getAsInt();
                if (positions.has("islandY")) DynamicIslandRenderer.islandY = positions.get("islandY").getAsInt();
                if (positions.has("tntX")) HudManager.tntX = positions.get("tntX").getAsInt();
                if (positions.has("tntY")) HudManager.tntY = positions.get("tntY").getAsInt();
                // Основные худы из HudManager
                if (positions.has("targetX")) HudManager.targetX = positions.get("targetX").getAsInt();
                if (positions.has("targetY")) HudManager.targetY = positions.get("targetY").getAsInt();
                if (positions.has("keybindsX")) HudManager.keybindsX = positions.get("keybindsX").getAsInt();
                if (positions.has("keybindsY")) HudManager.keybindsY = positions.get("keybindsY").getAsInt();
                if (positions.has("armorX")) HudManager.armorX = positions.get("armorX").getAsInt();
                if (positions.has("armorY")) HudManager.armorY = positions.get("armorY").getAsInt();
                if (positions.has("invX")) HudManager.invX = positions.get("invX").getAsInt();
                if (positions.has("invY")) HudManager.invY = positions.get("invY").getAsInt();
                if (positions.has("coolX")) HudManager.coolX = positions.get("coolX").getAsInt();
                if (positions.has("coolY")) HudManager.coolY = positions.get("coolY").getAsInt();
                if (positions.has("potionX")) HudManager.potionX = positions.get("potionX").getAsInt();
                if (positions.has("potionY")) HudManager.potionY = positions.get("potionY").getAsInt();
                if (positions.has("hearthX")) HudManager.hearthX = positions.get("hearthX").getAsInt();
                if (positions.has("hearthY")) HudManager.hearthY = positions.get("hearthY").getAsInt();
                if (positions.has("scoreboardY")) HudManager.scoreboardY = positions.get("scoreboardY").getAsInt();
                if (positions.has("scoreboardX")) HudManager. scoreboardX = positions.get("scoreboardX").getAsInt();
            }

            LexoraGui.savedThemes.clear();
            if (root.has("CustomThemes")) {
                JsonObject themes = root.getAsJsonObject("CustomThemes");
                for (String themeName : themes.keySet()) {
                    JsonObject themeObj = themes.getAsJsonObject(themeName);

                    if (themeObj.has("color1")) {
                        JsonObject c1 = themeObj.getAsJsonObject("color1");
                        LexoraGui.colorSettings.put("ThemePreset_" + themeName + "_1", new float[]{
                                c1.has("h") ? c1.get("h").getAsFloat() : 0f,
                                c1.has("s") ? c1.get("s").getAsFloat() : 1f,
                                c1.has("v") ? c1.get("v").getAsFloat() : 1f
                        });
                    }

                    if (themeObj.has("color2")) {
                        JsonObject c2 = themeObj.getAsJsonObject("color2");
                        LexoraGui.colorSettings.put("ThemePreset_" + themeName + "_2", new float[]{
                                c2.has("h") ? c2.get("h").getAsFloat() : 0f,
                                c2.has("s") ? c2.get("s").getAsFloat() : 1f,
                                c2.has("v") ? c2.get("v").getAsFloat() : 1f
                        });
                    }

                    LexoraGui.savedThemes.add(themeName);
                }
            }

            currentConfig = name;
            updateConfigList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteConfig(String name) {
        initDir();
        File file = new File(configDir, name + ".json");
        if (file.exists()) {
            file.delete();
        }

        if (currentConfig.equals(name)) {
            currentConfig = "default";
        }

        updateConfigList();
    }

    public static void updateConfigList() {
        initDir();
        LexoraGui.savedConfigs.clear();

        File[] files = configDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".json")) {
                    LexoraGui.savedConfigs.add(file.getName().replace(".json", ""));
                }
            }
        }
    }

    public static void loadConfig() {
        initDir();
        updateConfigList();

        File defaultFile = new File(configDir, currentConfig + ".json");
        if (defaultFile.exists()) {
            loadConfig(currentConfig);
        }
    }
}