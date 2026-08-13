package com.lexoravisauls.client.emotion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class AnimationLoader {
    private static final Map<String, LexoraAnimation> ANIMATIONS = new HashMap<>();

    public static void loadAll() {
        // Базовые (Bedrock / Blockbench)
        loadAnimation("wave", "friendly_wave.json");
        loadAnimation("bow", "respectful_bow.json");

        // Сторонние (Emotecraft / Bedrock)
        loadAnimation("clap", "clap.json");
        loadAnimation("club_penguin_dance", "club_penguin_dance.json");
        loadAnimation("crying", "crying.json");
        loadAnimation("here", "here.json");
        loadAnimation("kazotsky_kick", "kazotsky_kick.json");
        loadAnimation("palm", "palm.json");
        loadAnimation("point", "point.json");
        loadAnimation("roblox_potion_dance", "roblox_potion_dance.json");
        loadAnimation("waving", "waving.json");

        System.out.println("[Lexora Emotions] Успешно загружено анимаций: " + ANIMATIONS.size());
    }

    public static LexoraAnimation getAnimation(String id) {
        return ANIMATIONS.get(id);
    }

    private static void loadAnimation(String id, String fileName) {
        try {
            Identifier resourcePath = Identifier.of("lexoravisauls", "animations/" + fileName);
            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(resourcePath);

            if (resource.isEmpty()) {
                System.err.println("[Lexora Emotions] Файл анимации не найден: " + fileName);
                return;
            }

            try (InputStream stream = resource.get().getInputStream();
                 InputStreamReader reader = new InputStreamReader(stream)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                if (root.has("animations")) {
                    parseBedrockFormat(id, root);
                } else if (root.has("emote")) {
                    parseEmotecraftFormat(id, root);
                }
            }
        } catch (Exception e) {
            System.err.println("[Lexora Emotions] Ошибка парсинга анимации: " + fileName);
            e.printStackTrace();
        }
    }

    private static void parseBedrockFormat(String id, JsonObject root) {
        JsonObject animationsNode = root.getAsJsonObject("animations");
        if (animationsNode == null) return;

        String animKey = animationsNode.keySet().iterator().next();
        JsonObject animData = animationsNode.getAsJsonObject(animKey);

        LexoraAnimation lexAnim = new LexoraAnimation();
        lexAnim.length = animData.get("animation_length").getAsFloat();

        if (animData.has("loop")) {
            JsonElement loopElem = animData.get("loop");
            if (loopElem.isJsonPrimitive()) {
                if (loopElem.getAsJsonPrimitive().isBoolean()) {
                    lexAnim.isLoop = loopElem.getAsBoolean();
                } else if (loopElem.getAsJsonPrimitive().isString()) {
                    lexAnim.isLoop = loopElem.getAsString().equalsIgnoreCase("true");
                }
            }
        }

        JsonObject bonesNode = animData.getAsJsonObject("bones");
        if (bonesNode != null) {
            for (String boneName : bonesNode.keySet()) {
                JsonObject boneData = bonesNode.getAsJsonObject(boneName);
                LexoraAnimation.BoneAnimation boneAnim = new LexoraAnimation.BoneAnimation();

                boneAnim.rotationKeyframes = parseBedrockChannel(boneData, "rotation", false, boneName);
                boneAnim.positionKeyframes = parseBedrockChannel(boneData, "position", true, boneName);

                if (!boneAnim.rotationKeyframes.isEmpty() || !boneAnim.positionKeyframes.isEmpty()) {
                    lexAnim.bones.put(boneName, boneAnim);
                }
            }
        }
        ANIMATIONS.put(id, lexAnim);
    }

    private static List<LexoraAnimation.Keyframe> parseBedrockChannel(JsonObject boneData, String channelName, boolean isPos, String boneName) {
        TreeMap<Float, LexoraAnimation.Keyframe> sortedFrames = new TreeMap<>();
        if (boneData.has(channelName)) {
            JsonObject channelNode = boneData.getAsJsonObject(channelName);
            for (String timeStr : channelNode.keySet()) {
                float time = Float.parseFloat(timeStr);
                JsonArray vector = getVectorArray(channelNode.get(timeStr));
                if (vector == null) continue;

                float x = vector.get(0).getAsFloat();
                float y = vector.get(1).getAsFloat();
                float z = vector.get(2).getAsFloat();

                if (isPos) {
                    sortedFrames.put(time, new LexoraAnimation.Keyframe(time, -x, -y, z));
                } else {
                    boolean isLeg = boneName.equalsIgnoreCase("right_leg") || boneName.equalsIgnoreCase("left_leg");
                    if (isLeg) {
                        // Инверсия осей Y и Z применяются ИСКЛЮЧИТЕЛЬНО для ног
                        sortedFrames.put(time, new LexoraAnimation.Keyframe(time, x, -y, -z));
                    } else {
                        // Для тела, головы и рук сохраняются стандартные чистые углы Bedrock
                        sortedFrames.put(time, new LexoraAnimation.Keyframe(time, x, y, z));
                    }
                }
            }
        }
        return new ArrayList<>(sortedFrames.values());
    }

    private static JsonArray getVectorArray(JsonElement vecElem) {
        if (vecElem.isJsonObject() && vecElem.getAsJsonObject().has("vector")) {
            return vecElem.getAsJsonObject().getAsJsonArray("vector");
        } else if (vecElem.isJsonArray()) {
            return vecElem.getAsJsonArray();
        }
        return null;
    }

    private static class BoneChannelTimeline {
        TreeMap<Float, Float> pitch = new TreeMap<>();
        TreeMap<Float, Float> yaw   = new TreeMap<>();
        TreeMap<Float, Float> roll  = new TreeMap<>();
        TreeMap<Float, Float> x     = new TreeMap<>();
        TreeMap<Float, Float> y     = new TreeMap<>();
        TreeMap<Float, Float> z     = new TreeMap<>();
    }

    private static void parseEmotecraftFormat(String id, JsonObject root) {
        JsonObject emoteObj = root.getAsJsonObject("emote");
        boolean isDegrees = emoteObj.has("degrees") && (
                (emoteObj.get("degrees").isJsonPrimitive() && emoteObj.get("degrees").getAsBoolean()) ||
                        (emoteObj.get("degrees").isJsonPrimitive() && emoteObj.get("degrees").getAsString().equalsIgnoreCase("true"))
        );

        boolean isLoop = emoteObj.has("isLoop") && (
                (emoteObj.get("isLoop").isJsonPrimitive() && emoteObj.get("isLoop").getAsBoolean()) ||
                        (emoteObj.get("isLoop").isJsonPrimitive() && emoteObj.get("isLoop").getAsString().equalsIgnoreCase("true"))
        );

        float returnTime = 0f;
        if (emoteObj.has("returnTick")) {
            returnTime = emoteObj.get("returnTick").getAsFloat() * 0.05f;
        }

        float lengthInSeconds = 2.0f;
        if (emoteObj.has("stopTick")) {
            lengthInSeconds = emoteObj.get("stopTick").getAsFloat() * 0.05f;
        } else if (emoteObj.has("endTick")) {
            lengthInSeconds = emoteObj.get("endTick").getAsFloat() * 0.05f;
        }

        LexoraAnimation lexAnim = new LexoraAnimation();
        lexAnim.length = lengthInSeconds;
        lexAnim.isLoop = isLoop;
        lexAnim.returnTime = returnTime;

        Map<String, BoneChannelTimeline> boneTimelines = new HashMap<>();

        if (emoteObj.has("moves")) {
            JsonArray moves = emoteObj.getAsJsonArray("moves");
            for (JsonElement moveElem : moves) {
                if (!moveElem.isJsonObject()) continue;
                JsonObject move = moveElem.getAsJsonObject();
                if (!move.has("tick")) continue;

                float time = move.get("tick").getAsFloat() * 0.05f;

                for (String key : move.keySet()) {
                    if (key.equals("tick") || key.equals("easing") || key.equals("turn")) continue;
                    JsonElement boneElem = move.get(key);
                    if (!boneElem.isJsonObject()) continue;

                    String targetBone = mapBoneName(key);
                    if (targetBone == null) continue;

                    JsonObject boneObj = boneElem.getAsJsonObject();
                    BoneChannelTimeline timeline = boneTimelines.computeIfAbsent(targetBone, k -> new BoneChannelTimeline());

                    if (boneObj.has("pitch")) {
                        float val = boneObj.get("pitch").getAsFloat();
                        if (!isDegrees) val = (float) Math.toDegrees(val);
                        timeline.pitch.put(time, val);
                    }
                    if (boneObj.has("yaw")) {
                        float val = boneObj.get("yaw").getAsFloat();
                        if (!isDegrees) val = (float) Math.toDegrees(val);
                        timeline.yaw.put(time, val);
                    }
                    if (boneObj.has("roll")) {
                        float val = boneObj.get("roll").getAsFloat();
                        if (!isDegrees) val = (float) Math.toDegrees(val);
                        timeline.roll.put(time, val);
                    }

                    float defX = getEmotecraftDefaultX(targetBone);
                    float defY = getEmotecraftDefaultY(targetBone);
                    float defZ = getEmotecraftDefaultZ(targetBone);

                    if (boneObj.has("x")) {
                        float val = boneObj.get("x").getAsFloat();
                        timeline.x.put(time, val - defX);
                    }
                    if (boneObj.has("y")) {
                        float val = boneObj.get("y").getAsFloat();
                        timeline.y.put(time, val - defY);
                    }
                    if (boneObj.has("z")) {
                        float val = boneObj.get("z").getAsFloat();
                        timeline.z.put(time, val - defZ);
                    }
                }
            }
        }

        for (Map.Entry<String, BoneChannelTimeline> entry : boneTimelines.entrySet()) {
            String boneName = entry.getKey();
            BoneChannelTimeline timeline = entry.getValue();

            Set<Float> allTimes = new TreeSet<>();
            allTimes.add(0.0f);
            allTimes.add(lengthInSeconds);
            allTimes.addAll(timeline.pitch.keySet());
            allTimes.addAll(timeline.yaw.keySet());
            allTimes.addAll(timeline.roll.keySet());
            allTimes.addAll(timeline.x.keySet());
            allTimes.addAll(timeline.y.keySet());
            allTimes.addAll(timeline.z.keySet());

            LexoraAnimation.BoneAnimation boneAnim = new LexoraAnimation.BoneAnimation();

            for (float t : allTimes) {
                float p  = sampleChannel(timeline.pitch, t, 0f);
                float yw = sampleChannel(timeline.yaw,   t, 0f);
                float r  = sampleChannel(timeline.roll,  t, 0f);

                float px = sampleChannel(timeline.x, t, 0f);
                float py = sampleChannel(timeline.y, t, 0f);
                float pz = sampleChannel(timeline.z, t, 0f);

                boneAnim.rotationKeyframes.add(new LexoraAnimation.Keyframe(t, p, yw, r));
                boneAnim.positionKeyframes.add(new LexoraAnimation.Keyframe(t, px, py, pz));
            }

            lexAnim.bones.put(boneName, boneAnim);
        }

        ANIMATIONS.put(id, lexAnim);
    }

    private static float sampleChannel(TreeMap<Float, Float> channel, float t, float defaultVal) {
        if (channel.isEmpty()) return defaultVal;
        if (channel.containsKey(t)) return channel.get(t);
        Float floorKey = channel.floorKey(t);
        Float ceilKey  = channel.ceilingKey(t);

        if (floorKey == null) {
            return channel.get(ceilKey);
        }
        if (ceilKey == null) {
            return channel.get(floorKey);
        }

        float fVal = channel.get(floorKey);
        float cVal = channel.get(ceilKey);
        float delta = (t - floorKey) / (ceilKey - floorKey);

        return fVal + (cVal - fVal) * delta;
    }

    private static String mapBoneName(String name) {
        return switch (name) {
            case "torso", "body" -> "body";
            case "head" -> "head";
            case "rightArm" -> "right_arm";
            case "leftArm" -> "left_arm";
            case "rightLeg" -> "right_leg";
            case "leftLeg" -> "left_leg";
            default -> null;
        };
    }

    private static float getEmotecraftDefaultX(String bone) {
        return switch (bone) {
            case "right_arm" -> -5.0f;
            case "left_arm"  -> 5.0f;
            case "right_leg" -> -1.9f;
            case "left_leg"  -> 1.9f;
            default -> 0.0f;
        };
    }

    private static float getEmotecraftDefaultY(String bone) {
        return switch (bone) {
            case "right_arm", "left_arm" -> 2.0f;
            case "right_leg", "left_leg" -> 12.0f;
            default -> 0.0f;
        };
    }

    private static float getEmotecraftDefaultZ(String bone) {
        return 0.0f;
    }
}