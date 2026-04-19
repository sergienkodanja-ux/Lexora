package com.lexoravisauls.client.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configDir;

    // ДОБАВЛЕНО: Отслеживание текущего конфига
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
        saveConfig(currentConfig); // Теперь сохраняет в активный конфиг
    }

    public static void saveConfig(String name) {
        initDir();
        File file = new File(configDir, name + ".json");

        try (FileWriter writer = new FileWriter(file)) {
            JsonObject root = new JsonObject();

            JsonObject modules = new JsonObject();
            for (Map.Entry<String, Boolean> entry : LexoraGui.moduleStates.entrySet()) {
                modules.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("Modules", modules);

            JsonObject sliders = new JsonObject();
            for (Map.Entry<String, Float> entry : LexoraGui.numSettings.entrySet()) {
                sliders.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("Sliders", sliders);

            JsonObject modes = new JsonObject();
            for (Map.Entry<String, String> entry : LexoraGui.modeSettings.entrySet()) {
                modes.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("Modes", modes);

            GSON.toJson(root, writer);
            updateConfigList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadConfig(String name) {
        initDir();
        File file = new File(configDir, name + ".json");
        if (!file.exists()) return;

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
            // Устанавливаем загруженный конфиг как активный
            currentConfig = name;
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
        if (currentConfig.equals(name)) currentConfig = "default";
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
    }
}