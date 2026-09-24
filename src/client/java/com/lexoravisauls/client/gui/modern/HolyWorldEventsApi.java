package com.lexoravisauls.client.gui.modern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ивенты HolyWorld через официальное HTTP API вместо парсинга текста из тг-канала.
 * Токен не нужен (тех. админ сам убрал аутентификацию с этой ручки):
 *
 *   https://api.holyworld.me/v1/events            — все сервера сразу, объект {код: [ивенты]}
 *   https://api.holyworld.me/v1/events?server=ID  — один сервер, плоский массив ивентов
 *   https://api.holyworld.me/v1/servers            — код сервера -> человеческое название
 *
 * Опрашивается в фоновом потоке (демон), результат кладётся в volatile-поля — читать
 * getEvents()/hasData() с рендер-потока безопасно. Не трогает EventFetcher — просто
 * заменяет собой источник данных для вкладки Events в ModernClickGui.
 */
public final class HolyWorldEventsApi {

    public record HwEvent(String serverCode, String serverName, String typeId,
                           String displayName, String rarityTier, String rarityDisplay) {
    }

    private static final String EVENTS_URL  = "https://api.holyworld.me/v1/events";
    private static final String SERVERS_URL = "https://api.holyworld.me/v1/servers";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "lexora-hw-events");
                t.setDaemon(true);
                return t;
            });

    private static volatile List<HwEvent> events = List.of();
    private static volatile Map<String, String> serverNames = Map.of();
    private static volatile long lastSuccessMillis = 0L;
    private static volatile boolean started = false;

    private HolyWorldEventsApi() {
    }

    /** Вызвать один раз при старте клиента — рядом с MirageGlassShader.register() и т.п. */
    public static void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> start());
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        SCHEDULER.scheduleWithFixedDelay(HolyWorldEventsApi::refreshServers, 0, 10, TimeUnit.MINUTES);
        SCHEDULER.scheduleWithFixedDelay(HolyWorldEventsApi::refreshEvents, 0, 20, TimeUnit.SECONDS);
    }

    public static List<HwEvent> getEvents() {
        return events;
    }

    public static boolean hasData() {
        return lastSuccessMillis > 0L;
    }

    public static long secondsSinceUpdate() {
        return lastSuccessMillis == 0 ? -1 : (System.currentTimeMillis() - lastSuccessMillis) / 1000;
    }

    public static List<HwEvent> parseRawEvents(String raw) {
    if (raw == null || raw.trim().isEmpty() || raw.contains("Загрузка")) return List.of();
    List<HwEvent> list = new ArrayList<>();
    String[] lines = raw.split("\n");
    for (String line : lines) {
        line = line.trim();
        if (line.isEmpty()) continue;
        int colonIdx = line.indexOf(':');
        if (colonIdx == -1) {
            String clean = line.replace("*", "").trim();
            if (!clean.isEmpty()) {
                list.add(new HwEvent("hw", clean, "event", "Ивент", "rare", "Ивент"));
            }
            continue;
        }

        String eventName = line.substring(0, colonIdx).replace("*", "").trim();
        String serversPart = line.substring(colonIdx + 1).trim();

        String lowerName = eventName.toLowerCase();
        String tier = "normal";
        if (lowerName.contains("голос")) tier = "rare";
        else if (lowerName.contains("босс")) tier = "legendary";
        else if (lowerName.contains("мист") || lowerName.contains("смерт") || lowerName.contains("сундук")) tier = "epic";
        else if (lowerName.contains("полян") || lowerName.contains("аирдроп") || lowerName.contains("посылк")) tier = "normal";

        String rarityDisplay = switch (tier) {
            case "legendary" -> "Легендарный";
            case "epic" -> "Эпический";
            case "rare" -> "Голосование";
            case "normal" -> "Обычный";
            default -> "Ивент";
        };

        String[] servers = serversPart.split(",");
        for (String srv : servers) {
            String serverName = srv.replace("*", "").trim();
            if (serverName.isEmpty()) continue;

            String serverCode = serverName.replaceAll("[^0-9a-zA-Zа-яА-Я_#]", "");
            list.add(new HwEvent(serverCode, serverName, lowerName, eventName, tier, rarityDisplay));
        }
    }
    return list;
}

public static int tierColor(String tier) {
        return switch (tier) {
            case "legendary" -> 0xFFFFA500;
            case "epic" -> 0xFFB55CFF;
            case "rare" -> 0xFF55AAFF;
            case "normal" -> 0xFFBFBFBF;
            default -> 0xFF8A8A8A;
        };
    }

    private static void refreshServers() {
        try {
            JsonObject obj = JsonParser.parseString(get(SERVERS_URL)).getAsJsonObject();
            Map<String, String> map = new ConcurrentHashMap<>();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                map.put(e.getKey(), e.getValue().getAsString());
            }
            serverNames = map;
        } catch (Exception ignored) {
            // Названия серверов не критичны — при неудаче остаёмся на кодах (LITE_ANARCHY_17
            // и т.п.) до следующей попытки через 10 минут, вкладку это не ломает.
        }
    }

    private static void refreshEvents() {
        try {
            JsonObject obj = JsonParser.parseString(get(EVENTS_URL)).getAsJsonObject();
            Map<String, String> names = serverNames;

            List<HwEvent> parsed = new ArrayList<>();
            for (Map.Entry<String, JsonElement> serverEntry : obj.entrySet()) {
                if (!serverEntry.getValue().isJsonArray()) continue;
                String serverCode = serverEntry.getKey();
                String serverName = names.getOrDefault(serverCode, serverCode);
                for (JsonElement el : serverEntry.getValue().getAsJsonArray()) {
                    JsonObject ev = el.getAsJsonObject();
                    JsonObject meta = ev.has("metadata") && ev.get("metadata").isJsonObject()
                            ? ev.getAsJsonObject("metadata") : new JsonObject();
                    String typeId = str(ev, "id", "?");
                    String displayName = str(meta, "displayName", typeId);
                    String tier = normalizeRarityTier(str(meta, "rare", ""));
                    parsed.add(new HwEvent(serverCode, serverName, typeId, displayName, tier, rarityDisplayName(tier)));
                }
            }
            parsed.sort(Comparator.comparingInt(ev -> rarityRank(ev.rarityTier())));
            events = parsed;
            lastSuccessMillis = System.currentTimeMillis();
        } catch (Exception ignored) {
            // Данные не трогаем — на вкладке остаётся последний успешно полученный список,
            // чтобы секундный обрыв связи не мигал пустой вкладкой.
        }
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "Lexoravisauls-Client")
                .GET()
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode());
        return resp.body();
    }

    private static String str(JsonObject obj, String key, String fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : fallback;
    }

    // API отдаёт rare в разнобой: "Эпический"/"EPIC"/"legendary_desert"/"default" и т.п. —
    // приводим к одному из 5 тиров по вхождению подстроки (работает и для составных вроде
    // "rare_plains"/"legendary_desert" у посылок).
    private static String normalizeRarityTier(String raw) {
        String r = raw.toLowerCase();
        if (r.contains("legend") || r.contains("легенд")) return "legendary";
        if (r.contains("epic") || r.contains("эпич")) return "epic";
        if (r.contains("rare") || r.contains("редк")) return "rare";
        if (r.contains("normal") || r.contains("обычн")) return "normal";
        return "default";
    }

    private static String rarityDisplayName(String tier) {
        return switch (tier) {
            case "legendary" -> "Легендарный";
            case "epic" -> "Эпический";
            case "rare" -> "Редкий";
            case "normal" -> "Обычный";
            default -> "";
        };
    }

    private static int rarityRank(String tier) {
        return switch (tier) {
            case "legendary" -> 0;
            case "epic" -> 1;
            case "rare" -> 2;
            case "normal" -> 3;
            default -> 4;
        };
    }
}
