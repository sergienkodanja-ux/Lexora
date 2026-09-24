package com.lexoravisauls.client.cosmetic.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.cosmetic.CosmeticManager;
import com.lexoravisauls.client.cosmetic.model.CosmeticModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class PlayerCosmeticsSync {

    private static final String API_BASE = "https://lexoravisuals.fun/irc/api/cosmetics";
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "Lexora-CosmeticsSync");
        t.setDaemon(true);
        return t;
    });

    public static class PlayerData {
        public final boolean exists;
        public final long timestamp;
        public final long version;
        public final List<CosmeticModel> models;
        public final Identifier capeTexture;

        public PlayerData(boolean exists, long version, List<CosmeticModel> models, Identifier capeTexture) {
            this.exists = exists;
            this.timestamp = System.currentTimeMillis();
            this.version = version;
            this.models = models != null ? Collections.unmodifiableList(models) : Collections.emptyList();
            this.capeTexture = capeTexture;
        }
    }

    private static final Map<String, PlayerData> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_FETCH = ConcurrentHashMap.newKeySet();

    private static volatile String currentServer = "";
    private static volatile String lastKnownIgn = "";
    private static volatile long lastHeartbeatTime = 0L;
    private static volatile long lastActivePollTime = 0L;
    private static volatile boolean needInitialSync = true;

    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    private static final long ACTIVE_POLL_INTERVAL_MS = 2_500L;
    private static final long CACHE_TTL_MS = 15_000L;
    private static final long NEGATIVE_CACHE_TTL_MS = 5_000L;

    public static List<CosmeticModel> getEquippedModels(String playerName) {
        if (playerName == null || playerName.isBlank()) return Collections.emptyList();
        String clean = normalizeName(playerName);
        if (clean.isEmpty()) return Collections.emptyList();

        PlayerData data = CACHE.get(clean);
        long now = System.currentTimeMillis();

        if (data != null) {
            long ttl = data.exists ? CACHE_TTL_MS : NEGATIVE_CACHE_TTL_MS;
            if (now - data.timestamp > ttl && !PENDING_FETCH.contains(clean)) {
                triggerFetch(clean, playerName);
            }
            return data.models;
        }

        if (!PENDING_FETCH.contains(clean)) {
            triggerFetch(clean, playerName);
        }
        return Collections.emptyList();
    }

    public static Identifier getCapeTexture(String playerName) {
        if (playerName == null || playerName.isBlank()) return null;
        String clean = normalizeName(playerName);
        if (clean.isEmpty()) return null;

        PlayerData data = CACHE.get(clean);
        long now = System.currentTimeMillis();

        if (data != null) {
            long ttl = data.exists ? CACHE_TTL_MS : NEGATIVE_CACHE_TTL_MS;
            if (now - data.timestamp > ttl && !PENDING_FETCH.contains(clean)) {
                triggerFetch(clean, playerName);
            }
            return data.capeTexture;
        }

        if (!PENDING_FETCH.contains(clean)) {
            triggerFetch(clean, playerName);
        }
        return null;
    }

    public static void onClientTick(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) return;

        String server = resolveServer(client);
        String ign = client.player.getGameProfile() != null ? client.player.getGameProfile().getName() : client.player.getName().getString();

        // 1. Мгновенная синхронизация при первом появлении в мире / смене ника / смене сервера
        if (needInitialSync || !server.equals(currentServer) || !ign.equals(lastKnownIgn)) {
            needInitialSync = false;
            currentServer = server;
            lastKnownIgn = ign;
            lastHeartbeatTime = System.currentTimeMillis();
            lastActivePollTime = System.currentTimeMillis();
            CACHE.clear();
            PENDING_FETCH.clear();
            sendSyncAsync(server, ign);
            return;
        }

        long now = System.currentTimeMillis();

        // 2. Периодический Heartbeat (каждые 15 сек)
        if (now - lastHeartbeatTime >= HEARTBEAT_INTERVAL_MS) {
            lastHeartbeatTime = now;
            sendSyncAsync(server, ign);
        }

        // 3. Быстрый опрос активных игроков и версий их косметики (каждые 2.5 сек)
        if (now - lastActivePollTime >= ACTIVE_POLL_INTERVAL_MS) {
            lastActivePollTime = now;
            pollActivePlayersAsync(server);
        }
    }

    public static void onJoinServer(MinecraftClient client) {
        if (client == null || client.player == null) return;
        String server = resolveServer(client);
        String ign = client.player.getGameProfile() != null ? client.player.getGameProfile().getName() : client.player.getName().getString();
        currentServer = server;
        lastKnownIgn = ign;
        lastHeartbeatTime = System.currentTimeMillis();
        lastActivePollTime = System.currentTimeMillis();

        CACHE.clear();
        PENDING_FETCH.clear();

        sendSyncAsync(server, ign);
    }

    public static void onDisconnect() {
        needInitialSync = true;
        String srv = currentServer;
        String ign = lastKnownIgn;
        if (!srv.isEmpty() && !ign.isEmpty()) {
            sendLeaveAsync(srv, ign);
        }
        CACHE.clear();
        PENDING_FETCH.clear();
        currentServer = "";
        lastKnownIgn = "";
    }

    public static void onCosmeticsChanged() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            String server = currentServer.isEmpty() ? resolveServer(client) : currentServer;
            String ign = client.player.getGameProfile() != null ? client.player.getGameProfile().getName() : client.player.getName().getString();
            if (!server.isEmpty() && !ign.isEmpty()) {
                currentServer = server;
                lastKnownIgn = ign;
                sendSyncAsync(server, ign);
            }
        }
    }

    private static String resolveServer(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) {
            return normalizeServer(client.getCurrentServerEntry().address);
        }
        return "singleplayer";
    }

    private static void triggerFetch(String clean, String originalName) {
        if (clean.isEmpty() || currentServer.isEmpty()) return;
        PENDING_FETCH.add(clean);
        EXECUTOR.submit(() -> {
            try {
                fetchPlayerCosmetics(currentServer, clean, originalName);
            } catch (Exception e) {
                CACHE.put(clean, new PlayerData(false, 0L, Collections.emptyList(), null));
            } finally {
                PENDING_FETCH.remove(clean);
            }
        });
    }

    private static void fetchPlayerCosmetics(String server, String clean, String originalName) {
        try {
            // 1. Сначала ищем по текущему серверу
            PlayerData data = queryCosmetics(server, clean, originalName);
            if (data != null && data.exists) {
                CACHE.put(clean, data);
                com.lexoravisauls.client.badge.LexoraModUsers.addBackendName(originalName);
                System.out.println("[Lexora Sync] Loaded cosmetics for " + originalName + " from server " + server + " (v" + data.version + ", " + data.models.size() + " models, cape=" + (data.capeTexture != null) + ")");
                return;
            }

            // 2. Если на конкретном сервере не найден — пробуем глобальную комнату "global"
            if (!"global".equalsIgnoreCase(server)) {
                PlayerData globalData = queryCosmetics("global", clean, originalName);
                if (globalData != null && globalData.exists) {
                    CACHE.put(clean, globalData);
                    com.lexoravisauls.client.badge.LexoraModUsers.addBackendName(originalName);
                    System.out.println("[Lexora Sync] Loaded cosmetics for " + originalName + " from global (v" + globalData.version + ", " + globalData.models.size() + " models, cape=" + (globalData.capeTexture != null) + ")");
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        CACHE.put(clean, new PlayerData(false, 0L, Collections.emptyList(), null));
    }

    private static PlayerData queryCosmetics(String server, String clean, String originalName) {
        try {
            String urlStr = API_BASE + "/get?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8)
                    + "&ign=" + URLEncoder.encode(originalName, StandardCharsets.UTF_8);

            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "LexoraVisuals/1.0");

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream in = conn.getInputStream()) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    if (obj.has("found") && obj.get("found").getAsBoolean()) {
                        long version = obj.has("version") ? obj.get("version").getAsLong() : 1L;
                        List<CosmeticModel> models = new ArrayList<>();
                        if (obj.has("cosmetics") && obj.get("cosmetics").isJsonObject()) {
                            JsonObject cos = obj.getAsJsonObject("cosmetics");
                            for (Map.Entry<String, JsonElement> entry : cos.entrySet()) {
                                if ("cape".equalsIgnoreCase(entry.getKey())) continue;
                                if (entry.getValue().isJsonPrimitive()) {
                                    int modelIdx = entry.getValue().getAsInt();
                                    CosmeticModel model = CosmeticManager.getInstance().getModel(modelIdx);
                                    if (model != null) {
                                        models.add(model);
                                    }
                                }
                            }
                        }

                        Identifier capeTex = null;
                        if (obj.has("cape") && !obj.get("cape").isJsonNull()) {
                            int capeIdx = obj.get("cape").getAsInt();
                            capeTex = CosmeticManager.getInstance().getCapeTexture(capeIdx);
                        }

                        return new PlayerData(true, version, models, capeTex);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void pollActivePlayersAsync(String server) {
        EXECUTOR.submit(() -> {
            try {
                String urlStr = API_BASE + "/active?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8);
                URL url = URI.create(urlStr).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", "LexoraVisuals/1.0");

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                        if (obj.has("active")) {
                            Set<String> activeSet = new HashSet<>();

                            if (obj.get("active").isJsonObject()) {
                                JsonObject activeObj = obj.getAsJsonObject("active");
                                for (Map.Entry<String, JsonElement> entry : activeObj.entrySet()) {
                                    String activeClean = normalizeName(entry.getKey());
                                    activeSet.add(activeClean);
                                    long serverVer = entry.getValue().isJsonPrimitive() ? entry.getValue().getAsLong() : 0L;

                                    PlayerData cached = CACHE.get(activeClean);
                                    if (cached == null || (cached.version != serverVer && !PENDING_FETCH.contains(activeClean))) {
                                        triggerFetch(activeClean, entry.getKey());
                                    }
                                }
                            } else if (obj.get("active").isJsonArray()) {
                                for (JsonElement el : obj.getAsJsonArray("active")) {
                                    if (el.isJsonPrimitive()) {
                                        String activeName = el.getAsString();
                                        String activeClean = normalizeName(activeName);
                                        activeSet.add(activeClean);
                                        if (!CACHE.containsKey(activeClean) && !PENDING_FETCH.contains(activeClean)) {
                                            triggerFetch(activeClean, activeName);
                                        }
                                    }
                                }
                            }

                            com.lexoravisauls.client.badge.LexoraModUsers.setBackendNames(activeSet);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void sendSyncAsync(String server, String ign) {
        EXECUTOR.submit(() -> {
            try {
                CosmeticManager mgr = CosmeticManager.getInstance();
                Map<String, Integer> equipped = mgr.getSelectedByType();

                JsonObject root = new JsonObject();
                root.addProperty("server", server);
                root.addProperty("ign", ign);

                JsonObject cosObj = new JsonObject();
                Integer capeVal = null;

                for (Map.Entry<String, Integer> entry : equipped.entrySet()) {
                    if ("cape".equalsIgnoreCase(entry.getKey())) {
                        if (mgr.isCustomCapeEnabled()) {
                            capeVal = entry.getValue();
                        }
                    } else {
                        cosObj.addProperty(entry.getKey(), entry.getValue());
                    }
                }
                root.add("cosmetics", cosObj);
                if (capeVal != null) {
                    root.addProperty("cape", capeVal);
                } else {
                    root.add("cape", com.google.gson.JsonNull.INSTANCE);
                }

                postJson(API_BASE + "/sync", root.toString());

                // Также синхронизируем в глобальную комнату "global" для надёжности
                if (!"global".equalsIgnoreCase(server)) {
                    JsonObject globalRoot = root.deepCopy();
                    globalRoot.addProperty("server", "global");
                    postJson(API_BASE + "/sync", globalRoot.toString());
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void sendLeaveAsync(String server, String ign) {
        EXECUTOR.submit(() -> {
            try {
                JsonObject root = new JsonObject();
                root.addProperty("server", server);
                root.addProperty("ign", ign);
                postJson(API_BASE + "/leave", root.toString());

                if (!"global".equalsIgnoreCase(server)) {
                    JsonObject globalRoot = root.deepCopy();
                    globalRoot.addProperty("server", "global");
                    postJson(API_BASE + "/leave", globalRoot.toString());
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void postJson(String urlStr, String jsonBody) {
        try {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "LexoraVisuals/1.0");

            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    public static String normalizeServer(String value) {
        if (value == null || value.isBlank()) return "singleplayer";
        String server = value.trim().toLowerCase();
        server = server.replaceFirst("^https?://", "").replaceFirst("^wss?://", "");
        int slashIdx = server.indexOf('/');
        if (slashIdx >= 0) server = server.substring(0, slashIdx);
        int colonIdx = server.indexOf(':');
        if (colonIdx >= 0) server = server.substring(0, colonIdx);
        if (server.endsWith(".")) server = server.substring(0, server.length() - 1);

        if (server.contains("funtime") || server.contains("fun-time")) return "funtime";
        if (server.contains("spookytime") || server.contains("spooky")) return "spookytime";
        if (server.contains("dexland")) return "dexland";
        if (server.contains("holyworld") || server.contains("howorld")) return "holyworld";
        if (server.contains("reallyworld")) return "reallyworld";
        if (server.contains("mineblaze")) return "mineblaze";
        if (server.contains("prostocraft")) return "prostocraft";
        if (server.contains("hypixel")) return "hypixel";
        if (server.contains("vimeworld")) return "vimeworld";
        if (server.contains("gommehd")) return "gommehd";

        String[] prefixes = {"mc.", "play.", "join.", "go.", "connect.", "server.",
                "bedrock.", "msk.", "msk1.", "msk2.", "ru.", "s1.", "s2.", "s3.", "hub.", "lobby.",
                "link.", "proxy.", "anarchy.", "grief.", "duels.", "survival.", "bungee.", "eu.", "us."};
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String p : prefixes) {
                if (server.startsWith(p)) {
                    server = server.substring(p.length());
                    changed = true;
                    break;
                }
            }
        }

        String[] parts = server.split("\\.");
        if (parts.length >= 2) {
            // Для link.holyworld.me или domain.com берём смысловую часть домена
            return parts[parts.length - 2];
        }
        return server.isEmpty() ? "unknown" : server;
    }

    public static String normalizeName(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_\u0400-\u04FF]", "");
    }
}
