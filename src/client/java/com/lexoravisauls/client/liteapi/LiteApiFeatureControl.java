package com.lexoravisauls.client.liteapi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.LexoraModuleSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Интеграция с LiteAPI "Feature Control" HolyWorld (wiki.holyworld.me/api).
 * Поддерживает автоматический повторный опрос, постоянный контроль (enforceBlocklist)
 * и отправку ВСЕХ известных модулей (как включённых, так и выключенных).
 */
public final class LiteApiFeatureControl {

    public static final Identifier CHANNEL = Identifier.of("liteapi", "feature-control");
    private static final String CLIENT_ID = "lexora";
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger("Lexora-LiteAPI");
    private static final long MIN_REQUEST_INTERVAL_MS = 10_500L;

    private static long lastRequestSentAt = 0L;
    private static volatile boolean needsFeatureCheck = false;

    private record PendingCheck(Set<String> requestedFeatures, RequestCallback callback) {}
    private static final Map<String, PendingCheck> pendingRequests = new ConcurrentHashMap<>();

    /** Модули, запрещённые сервером HolyWorld */
    private static volatile Set<String> blockedModules = Collections.emptySet();

    /** Состояние модулей до блокировки для восстановления при выходе */
    private static final Map<String, Boolean> preBlockStates = new ConcurrentHashMap<>();

    private LiteApiFeatureControl() {}

    public record FeatureControlPayload(String json) implements CustomPayload {
        public static final CustomPayload.Id<FeatureControlPayload> ID = new CustomPayload.Id<>(CHANNEL);

        public static final PacketCodec<PacketByteBuf, FeatureControlPayload> CODEC = PacketCodec.of(
                (payload, buf) -> writeUtf8(buf, payload.json()),
                buf -> new FeatureControlPayload(readUtf8(buf))
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(FeatureControlPayload.ID, FeatureControlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FeatureControlPayload.ID, FeatureControlPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(FeatureControlPayload.ID, (payload, context) -> {
            handleIncoming(payload.json());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            needsFeatureCheck = true;
            trySendPendingCheck();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            restoreAllBlocked();
            needsFeatureCheck = false;
            lastRequestSentAt = 0L;
        });
    }

    /**
     * Вызывается каждый тик клиента из LexoravisaulsClient.
     * 1. Гарантирует постоянный контроль (enforceBlocklist) — запрещённые функции невозможно включить.
     * 2. Автоматически повторяет запрос checkFeatures, если канал был временно недоступен или действовал кулдаун.
     */
    public static void tick() {
        enforceBlocklist();

        if (needsFeatureCheck) {
            trySendPendingCheck();
        }
    }

    /**
     * Постоянный контроль: выключает любые заблокированные сервером модули,
     * даже если их попытались включить через бинд, конфиг или чит.
     */
    public static void enforceBlocklist() {
        if (blockedModules.isEmpty()) return;

        for (String blocked : blockedModules) {
            if (Boolean.TRUE.equals(ClientData.moduleStates.get(blocked))) {
                ClientData.moduleStates.put(blocked, false);
            }
            if (Boolean.TRUE.equals(LexoraGui.moduleStates.get(blocked))) {
                LexoraGui.moduleStates.put(blocked, false);
            }
        }
    }

    /**
     * Собрать полный список ВСЕХ функций Lexora (включённых и выключенных).
     * Это решает главную проблему: HolyWorld должен знать обо ВСЕХ доступных
     * клиенту модулях, чтобы вернуть запрещённые.
     */
    public static Set<String> getAllKnownFeatures() {
        Set<String> all = new LinkedHashSet<>();
        if (LexoraGui.categories != null) {
            for (List<String> list : LexoraGui.categories.values()) {
                if (list != null) all.addAll(list);
            }
        }
        for (List<String> list : LexoraModuleSource.getCategoriesRaw().values()) {
            if (list != null) all.addAll(list);
        }
        all.addAll(ClientData.moduleStates.keySet());
        all.addAll(LexoraGui.moduleStates.keySet());
        return all;
    }

    private static void trySendPendingCheck() {
        if (!needsFeatureCheck) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();
        if (now - lastRequestSentAt < MIN_REQUEST_INTERVAL_MS) {
            return; // Дождёмся окончания кулдауна в tick()
        }

        if (!ClientPlayNetworking.canSend(FeatureControlPayload.ID)) {
            return; // Дождёмся готовности канала в tick()
        }

        Set<String> allFeatures = getAllKnownFeatures();
        if (allFeatures.isEmpty()) return;

        requestFeatureCheck(allFeatures, null);
    }

    public static void requestFeatureCheck(Collection<String> moduleNames, RequestCallback callback) {
        long now = System.currentTimeMillis();
        if (now - lastRequestSentAt < MIN_REQUEST_INTERVAL_MS) {
            needsFeatureCheck = true;
            LOGGER.debug("checkFeatures отложен: троттлинг ({} мс с прошлого запроса)", now - lastRequestSentAt);
            if (callback != null) callback.onError("CLIENT_THROTTLED", "Слишком частые запросы, повтор запланирован");
            return;
        }

        if (!ClientPlayNetworking.canSend(FeatureControlPayload.ID)) {
            needsFeatureCheck = true;
            LOGGER.debug("checkFeatures отложен: канал {} пока недоступен на бэкенде", CHANNEL);
            if (callback != null) callback.onError("CHANNEL_UNAVAILABLE", "Канал liteapi:feature-control пока недоступен");
            return;
        }

        if (moduleNames == null || moduleNames.isEmpty()) return;

        String requestId = UUID.randomUUID().toString();
        Set<String> requestedFeatures = new HashSet<>(moduleNames);

        JsonObject payload = new JsonObject();
        payload.addProperty("client", CLIENT_ID);
        JsonArray featuresArray = new JsonArray();
        for (String name : requestedFeatures) featuresArray.add(name);
        payload.add("features", featuresArray);

        JsonObject request = new JsonObject();
        request.addProperty("id", requestId);
        request.addProperty("method", "checkFeatures");
        request.add("payload", payload);

        pendingRequests.put(requestId, new PendingCheck(requestedFeatures, callback));

        lastRequestSentAt = now;
        needsFeatureCheck = false; // Запрос успешно отправлен в сеть

        String json = GSON.toJson(request);
        ClientPlayNetworking.send(new FeatureControlPayload(json));
        LOGGER.info("Отправлен checkFeatures на HolyWorld ({} функций)", requestedFeatures.size());
    }

    private static void handleIncoming(String json) {
        JsonObject obj;
        try {
            obj = GSON.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            return;
        }
        if (obj == null || !obj.has("id") || !obj.has("ok")) {
            return;
        }

        String id = obj.get("id").getAsString();
        boolean ok = obj.get("ok").getAsBoolean();
        PendingCheck pending = pendingRequests.remove(id);
        Set<String> requestedFeatures = pending != null ? pending.requestedFeatures() : Collections.emptySet();
        RequestCallback cb = pending != null ? pending.callback() : null;

        if (ok) {
            JsonObject payload = obj.getAsJsonObject("payload");
            Set<String> blocklist = new HashSet<>();
            if (payload != null && payload.has("blocklist")) {
                JsonArray arr = payload.getAsJsonArray("blocklist");
                for (int i = 0; i < arr.size(); i++) {
                    blocklist.add(arr.get(i).getAsString());
                }
            }
            applyBlocklist(requestedFeatures, blocklist);

            if (cb != null) cb.onSuccess(blocklist);
        } else {
            String error = obj.has("error") ? obj.get("error").getAsString() : "UNKNOWN";
            String message = obj.has("message") ? obj.get("message").getAsString() : null;
            if (cb != null) cb.onError(error, message);
            LOGGER.warn("checkFeatures error={} message={}", error, message);
        }
    }

    private static void applyBlocklist(Set<String> requestedFeatures, Set<String> newBlocklist) {
        Set<String> stillBlocked = new HashSet<>();

        for (String name : blockedModules) {
            if (requestedFeatures.contains(name) && !newBlocklist.contains(name)) {
                Boolean prevState = preBlockStates.remove(name);
                if (prevState != null) {
                    ClientData.moduleStates.put(name, prevState);
                    LexoraGui.moduleStates.put(name, prevState);
                }
            } else {
                stillBlocked.add(name);
            }
        }

        for (String name : newBlocklist) {
            if (!stillBlocked.contains(name)) {
                Boolean current = ClientData.moduleStates.get(name);
                preBlockStates.put(name, current != null && current);
            }
            ClientData.moduleStates.put(name, false);
            LexoraGui.moduleStates.put(name, false);
        }

        stillBlocked.addAll(newBlocklist);
        blockedModules = Collections.unmodifiableSet(stillBlocked);
        enforceBlocklist();

        LOGGER.info("Применён блок-лист HolyWorld: заблокировано {}", newBlocklist);
    }

    private static void restoreAllBlocked() {
        for (Map.Entry<String, Boolean> e : preBlockStates.entrySet()) {
            ClientData.moduleStates.put(e.getKey(), e.getValue());
            LexoraGui.moduleStates.put(e.getKey(), e.getValue());
        }
        preBlockStates.clear();
        blockedModules = Collections.emptySet();
        needsFeatureCheck = false;
        lastRequestSentAt = 0L;
    }

    /** Модуль запрещён на текущем сервере? */
    public static boolean isBlocked(String moduleName) {
        return moduleName != null && blockedModules.contains(moduleName);
    }

    public static Set<String> getBlockedModules() {
        return blockedModules;
    }

    private static String readUtf8(PacketByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUtf8(PacketByteBuf buf, String s) {
        buf.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    public interface RequestCallback {
        void onSuccess(Set<String> blocklist);
        void onError(String errorCode, String message);
    }
}