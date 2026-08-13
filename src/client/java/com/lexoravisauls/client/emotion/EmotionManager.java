package com.lexoravisauls.client.emotion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.auth.AuthManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class EmotionManager {
    public static String[] myRadialSlots = new String[]{"wave", "bow", "none", "none", "none", "none"};
    public static Map<String, ActiveEmotion> playingEmotions = new HashMap<>();

    private static int tickCounter = 0;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            tickCounter++;
            if (tickCounter >= 15) {
                tickCounter = 0;
                pollEmotions();
            }

            cleanupExpired();
            checkSelfMovementInterrupt(client);
        });
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ActiveEmotion>> it = playingEmotions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveEmotion> entry = it.next();
            ActiveEmotion active = entry.getValue();

            LexoraAnimation anim = AnimationLoader.getAnimation(active.animationId);
            if (anim == null) {
                it.remove();
                continue;
            }

            float elapsedSeconds = (now - active.startTime) / 1000f;
            if (!anim.isLoop && elapsedSeconds > (anim.length + 0.25f)) {
                it.remove();
            }
        }
    }

    public static ActiveEmotion getActiveEmotion(String playerName) {
        return playingEmotions.get(playerName);
    }

    public static void fetchRadialSlots() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;

        String ign = mc.getSession().getUsername();

        try {
            String data = "ign=" + URLEncoder.encode(ign, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://lexoravisuals.fun/api_get_radial.php"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8))
                    .build();

            HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                                if (obj.get("status").getAsString().equals("success")) {
                                    JsonArray slotsArr = obj.getAsJsonArray("slots");
                                    for (int i = 0; i < slotsArr.size() && i < 6; i++) {
                                        myRadialSlots[i] = slotsArr.get(i).getAsString();
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void playEmotion(String animId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;

        if (AnimationLoader.getAnimation(animId) == null) {
            System.err.println("[Lexora Emotions] Попытка воспроизвести незагруженную анимацию: " + animId);
            return;
        }

        String ign = mc.getSession().getUsername();
        long startTime = System.currentTimeMillis();

        recentlyCancelled.remove(ign);
        playingEmotions.put(ign, new ActiveEmotion(animId, startTime));

        if (mc.player != null) {
            movementStartX = mc.player.getX();
            movementStartY = mc.player.getY();
            movementStartZ = mc.player.getZ();
            movementCheckActive = true;
        }

        if (AuthManager.sessionToken == null || AuthManager.sessionToken.isEmpty()) {
            return;
        }

        try {
            String data = "token=" + URLEncoder.encode(AuthManager.sessionToken, "UTF-8") +
                    "&ign=" + URLEncoder.encode(ign, "UTF-8") +
                    "&anim=" + URLEncoder.encode(animId, "UTF-8");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://lexoravisuals.fun/api_set_emotion.php"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8))
                    .build();

            HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static double movementStartX, movementStartY, movementStartZ;
    private static boolean movementCheckActive = false;
    private static final double MOVEMENT_THRESHOLD = 0.15;

    private static final Map<String, Long> recentlyCancelled = new HashMap<>();
    private static final long CANCEL_GRACE_MS = 2000;

    private static void checkSelfMovementInterrupt(MinecraftClient mc) {
        if (!movementCheckActive || mc.player == null) return;

        String ign = mc.getSession() != null ? mc.getSession().getUsername() : null;
        if (ign == null || !playingEmotions.containsKey(ign)) {
            movementCheckActive = false;
            return;
        }

        double dx = mc.player.getX() - movementStartX;
        double dy = mc.player.getY() - movementStartY;
        double dz = mc.player.getZ() - movementStartZ;

        boolean moved = (dx * dx + dz * dz) > MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD
                || Math.abs(dy) > 0.15;

        if (moved) {
            cancelEmotion(ign);
        }
    }

    public static void cancelEmotion(String ign) {
        playingEmotions.remove(ign);
        movementCheckActive = false;
        recentlyCancelled.put(ign, System.currentTimeMillis());

        MinecraftClient mc = MinecraftClient.getInstance();
        String myIgn = mc.getSession() != null ? mc.getSession().getUsername() : null;
        if (myIgn == null || !myIgn.equals(ign)) return;
        if (AuthManager.sessionToken == null || AuthManager.sessionToken.isEmpty()) return;

        try {
            String data = "token=" + URLEncoder.encode(AuthManager.sessionToken, "UTF-8") +
                    "&ign=" + URLEncoder.encode(ign, "UTF-8") +
                    "&anim=none";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://lexoravisuals.fun/api_set_emotion.php"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8))
                    .build();

            HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void pollEmotions() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        StringBuilder visiblePlayers = new StringBuilder();
        for (PlayerEntity player : mc.world.getPlayers()) {
            visiblePlayers.append(player.getName().getString()).append(",");
        }

        if (visiblePlayers.isEmpty()) return;

        long now = System.currentTimeMillis();
        recentlyCancelled.entrySet().removeIf(e -> now - e.getValue() > CANCEL_GRACE_MS);

        try {
            String data = "players=" + URLEncoder.encode(visiblePlayers.toString(), "UTF-8");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://lexoravisuals.fun/api_get_emotions.php"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8))
                    .build();

            HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
                                mc.execute(() -> {
                                    for (JsonElement element : array) {
                                        JsonObject obj = element.getAsJsonObject();
                                        String player = obj.get("ign").getAsString();
                                        String anim = obj.get("anim").getAsString();
                                        long start = obj.get("start").getAsLong();

                                        Long cancelledAt = recentlyCancelled.get(player);
                                        if (cancelledAt != null && (now - cancelledAt) <= CANCEL_GRACE_MS) {
                                            continue;
                                        }

                                        if (!anim.equals("none")) {
                                            ActiveEmotion current = playingEmotions.get(player);
                                            // ФИКС СБОЯ 0.75s: Если анимация УЖЕ идет и совпадает — НЕ перезаписываем startTime!
                                            if (current == null || !current.animationId.equals(anim)) {
                                                playingEmotions.put(player, new ActiveEmotion(anim, start));
                                            }
                                        } else {
                                            playingEmotions.remove(player);
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                System.out.println("[Lexora Emotions] JSON Parse error");
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class ActiveEmotion {
        public String animationId;
        public long startTime;

        public ActiveEmotion(String animationId, long startTime) {
            this.animationId = animationId;
            this.startTime = startTime;
        }
    }
}