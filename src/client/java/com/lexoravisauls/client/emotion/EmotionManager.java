package com.lexoravisauls.client.emotion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.auth.AuthManager;
import com.lexoravisauls.client.modules.FreeLook;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
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
            if (tickCounter >= 30) {
                tickCounter = 0;
                pollEmotions();
            }

            cleanupExpired();
            checkSelfMovementInterrupt(client);
        });

        // ЗАЩИТА ОТ ЗАЛИПАНИЯ КАМЕРЫ: END_CLIENT_TICK выше прерывается на client.world == null,
        // значит если игрок дисконнектнется во время активной эмоции (playingEmotions не пуст),
        // cleanupExpired()/checkSelfMovementInterrupt() больше не вызовутся и savedPerspective
        // не восстановится сама — камера так и останется в 3rd person даже после выхода на сервер-лист.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            restorePerspectiveIfNeeded();
            playingEmotions.clear();
            movementCheckActive = false;
        });
    }

    private static void cleanupExpired() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String myIgn = mc.getSession() != null ? mc.getSession().getUsername() : null;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ActiveEmotion>> it = playingEmotions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveEmotion> entry = it.next();
            String player = entry.getKey();
            ActiveEmotion active = entry.getValue();

            LexoraAnimation anim = AnimationLoader.getAnimation(active.animationId);
            if (anim == null) {
                it.remove();
                if (player.equals(myIgn)) restorePerspectiveIfNeeded();
                continue;
            }

            float elapsedSeconds = (now - active.startTime) / 1000f;
            if (!anim.isLoop && elapsedSeconds > (anim.length + 0.25f)) {
                it.remove();
                // Восстанавливаем камеру ТОЛЬКО если это была НАША собственная эмоция —
                // playingEmotions хранит эмоции всех видимых игроков одновременно,
                // удаление чужой записи не должно трогать нашу локальную перспективу
                if (player.equals(myIgn)) restorePerspectiveIfNeeded();
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

        // FREE LOOK НА ВРЕМЯ ЭМОЦИИ: если игрок сейчас в FIRST_PERSON, просим FreeLook
        // включить обзор от 3-го лица со свободным вращением камеры мышью (не поворачивая
        // персонажа) — так видно свою анимацию, но управление обзором остаётся удобным.
        // Если игрок УЖЕ в каком-то виде 3rd person (сам включил, или уже идёт emotionный
        // free look от предыдущей эмоции) — НЕ трогаем вообще, оставляем его выбор как есть.
        // Реальное решение "кто победит" (эта эмоция или, например, зажатый игроком свой
        // Free Look bind) принимает сам FreeLook.tick() — см. приоритет там.
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            FreeLook.emotionRequestsFreeLook = true;
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

    // Сохранённая перспектива на момент старта СВОЕЙ эмоции — null означает "сейчас не сохранена"
    // (либо эмоция не идёт, либо уже восстановлена). Восстанавливаем именно то, что было ДО
    // эмоции, а не жёстко FIRST_PERSON, чтобы не выдёргивать игрока из 3rd person, если он уже
    // был там до того как начал эмоцию.
    private static void restorePerspectiveIfNeeded() {
        // Больше НЕ трогаем mc.options.setPerspective() напрямую — этим занимается
        // FreeLook.tick() сам, у него уже есть корректная previousPerspective и защита
        // от рывка камеры при смене владельца (см. activatedByEmotion в FreeLook.java).
        // Здесь только снимаем наш запрос; на следующем тике FreeLook сам увидит, что
        // ни клавиша не зажата, ни эмоционный запрос не активен, и сделает reset() сам.
        FreeLook.emotionRequestsFreeLook = false;
    }

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

        // Восстанавливаем камеру только если это была НАША эмоция — та же проверка,
        // что уже стоит ниже для сетевого запроса, но нужна раньше самого return
        if (myIgn != null && myIgn.equals(ign)) {
            restorePerspectiveIfNeeded();
        }

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

    private static volatile boolean isPolling = false;

    private static void pollEmotions() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (isPolling) return;

        // Если на сервере нет других пользователей мода — не опрашиваем сайт
        if (!com.lexoravisauls.client.badge.LexoraModUsers.hasOtherModUsersOnServer()) {
            return;
        }

        StringBuilder visiblePlayers = new StringBuilder();
        String myIgn = mc.getSession() != null ? mc.getSession().getUsername() : null;

        // Опрашиваем ТОЛЬКО реальных пользователей Lexora в зоне видимости (до 64 блоков)
        for (PlayerEntity player : mc.world.getPlayers()) {
            String pName = player.getName().getString();
            if (myIgn != null && myIgn.equalsIgnoreCase(pName)) continue; // себя опрашивать не нужно

            if (com.lexoravisauls.client.badge.LexoraModUsers.hasName(pName)) {
                if (player.squaredDistanceTo(mc.player) <= 4096.0) { // 64 * 64 блока
                    visiblePlayers.append(pName).append(",");
                }
            }
        }

        if (visiblePlayers.isEmpty()) return;

        isPolling = true;
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
                    .whenComplete((response, error) -> {
                        isPolling = false;
                        if (error != null || response == null || response.statusCode() != 200) {
                            return;
                        }
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
                                        if (current == null || !current.animationId.equals(anim)) {
                                            playingEmotions.put(player, new ActiveEmotion(anim, start));
                                        }
                                    } else {
                                        playingEmotions.remove(player);
                                        if (player.equals(mc.getSession() != null ? mc.getSession().getUsername() : null)) {
                                            restorePerspectiveIfNeeded();
                                        }
                                    }
                                }
                            });
                        } catch (Exception e) {
                            // Игнорируем ошибки парсинга
                        }
                    });
        } catch (Exception e) {
            isPolling = false;
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