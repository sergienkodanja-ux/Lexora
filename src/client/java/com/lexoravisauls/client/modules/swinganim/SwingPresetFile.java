package com.lexoravisauls.client.modules.swinganim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec2f;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SwingPresetFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String fileName;
    private final File file;

    public SwingPresetFile(String fileName) {
        this.fileName = fileName;
        File baseDir = MinecraftClient.getInstance().runDirectory;
        if (baseDir == null) {
            baseDir = new File(".");
        }
        File dir = new File(baseDir, "lexora/presets/swings");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.file = new File(dir, fileName + ".json");
    }

    public String getFileName() {
        return fileName;
    }

    public File getFile() {
        return file;
    }

    public void load(SwingManager manager) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (json.has("animation")) {
                JsonObject anim = json.getAsJsonObject("animation");
                float sx = anim.has("start_x") ? anim.get("start_x").getAsFloat() : 0.5f;
                float sy = anim.has("start_y") ? anim.get("start_y").getAsFloat() : 1.0f;
                float ex = anim.has("end_x") ? anim.get("end_x").getAsFloat() : 0.5f;
                float ey = anim.has("end_y") ? anim.get("end_y").getAsFloat() : 0.0f;
                manager.setBezierStart(new Vec2f(sx, sy));
                manager.setBezierEnd(new Vec2f(ex, ey));

                if (anim.has("swing_back")) {
                    manager.setSwingBack(anim.get("swing_back").getAsBoolean());
                }
                if (anim.has("speed")) {
                    manager.setSpeed(anim.get("speed").getAsFloat());
                }
            }

            if (json.has("startPhase")) {
                JsonObject start = json.getAsJsonObject("startPhase");
                loadPhase(manager.getStartPhase(), start);
            }

            if (json.has("endPhase")) {
                JsonObject end = json.getAsJsonObject("endPhase");
                loadPhase(manager.getEndPhase(), end);
            }

            manager.setCurrentPresetName(this.fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save(SwingManager manager) {
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            JsonObject json = new JsonObject();

            JsonObject anim = new JsonObject();
            anim.addProperty("start_x", manager.getBezierStart().x);
            anim.addProperty("start_y", manager.getBezierStart().y);
            anim.addProperty("end_x", manager.getBezierEnd().x);
            anim.addProperty("end_y", manager.getBezierEnd().y);
            anim.addProperty("swing_back", manager.isSwingBack());
            anim.addProperty("speed", manager.getSpeed());
            json.add("animation", anim);

            JsonObject start = new JsonObject();
            savePhase(manager.getStartPhase(), start);
            json.add("startPhase", start);

            JsonObject end = new JsonObject();
            savePhase(manager.getEndPhase(), end);
            json.add("endPhase", end);

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(GSON.toJson(json));
            }
            manager.setCurrentPresetName(this.fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean delete() {
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    private static void loadPhase(SwingPhase phase, JsonObject obj) {
        if (obj.has("anchorX")) phase.anchorX = obj.get("anchorX").getAsFloat();
        if (obj.has("anchorY")) phase.anchorY = obj.get("anchorY").getAsFloat();
        if (obj.has("anchorZ")) phase.anchorZ = obj.get("anchorZ").getAsFloat();
        if (obj.has("moveX")) phase.moveX = obj.get("moveX").getAsFloat();
        if (obj.has("moveY")) phase.moveY = obj.get("moveY").getAsFloat();
        if (obj.has("moveZ")) phase.moveZ = obj.get("moveZ").getAsFloat();
        if (obj.has("rotateX")) phase.rotateX = obj.get("rotateX").getAsFloat();
        if (obj.has("rotateY")) phase.rotateY = obj.get("rotateY").getAsFloat();
        if (obj.has("rotateZ")) phase.rotateZ = obj.get("rotateZ").getAsFloat();
    }

    private static void savePhase(SwingPhase phase, JsonObject obj) {
        obj.addProperty("anchorX", phase.anchorX);
        obj.addProperty("anchorY", phase.anchorY);
        obj.addProperty("anchorZ", phase.anchorZ);
        obj.addProperty("moveX", phase.moveX);
        obj.addProperty("moveY", phase.moveY);
        obj.addProperty("moveZ", phase.moveZ);
        obj.addProperty("rotateX", phase.rotateX);
        obj.addProperty("rotateY", phase.rotateY);
        obj.addProperty("rotateZ", phase.rotateZ);
    }
}
