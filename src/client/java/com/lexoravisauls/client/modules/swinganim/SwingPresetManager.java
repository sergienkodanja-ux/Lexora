package com.lexoravisauls.client.modules.swinganim;

import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SwingPresetManager {
    private final List<SwingPresetFile> presetFiles = new ArrayList<>();
    private SwingPresetFile current = null;

    public void scanPresetDirectory() {
        presetFiles.clear();
        File baseDir = MinecraftClient.getInstance().runDirectory;
        if (baseDir == null) {
            baseDir = new File(".");
        }
        File dir = new File(baseDir, "lexora/presets/swings");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName().substring(0, f.getName().length() - 5);
                presetFiles.add(new SwingPresetFile(name));
            }
        }
    }

    public List<SwingPresetFile> getPresetFiles() {
        return presetFiles;
    }

    public SwingPresetFile createPreset(String name, SwingManager manager) {
        SwingPresetFile file = new SwingPresetFile(name);
        file.save(manager);
        if (!hasPreset(name)) {
            presetFiles.add(file);
        }
        current = file;
        return file;
    }

    public boolean hasPreset(String name) {
        for (SwingPresetFile f : presetFiles) {
            if (f.getFileName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public SwingPresetFile getPreset(String name) {
        for (SwingPresetFile f : presetFiles) {
            if (f.getFileName().equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    public SwingPresetFile getCurrent() {
        return current;
    }

    public void setCurrent(SwingPresetFile current) {
        this.current = current;
    }
}
