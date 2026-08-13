package com.lexoravisauls.client.liteapi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lexoravisauls.client.core.ClientData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
 * Написано под Minecraft 1.21.4 / Fabric API 0.110.x, где весь обмен идёт
 * через CustomPayload + PayloadTypeRegistry (старый "сырой PacketByteBuf"
 * API в registerGlobalReceiver в этой версии больше не существует).
 *
 * Протокол HolyWorld (payload = обычная UTF-8 JSON строка):
 *  - Канал: liteapi:feature-control
 *  - Метод: checkFeatures
 *  - Запрос:  {"id","method":"checkFeatures","payload":{"client","features":[...]}}
 *  - Ответ:   {"id","ok":true,"payload":{"blocklist":[...]}}
 *          или {"id","ok":false,"error","message"}
 *  - Rate limit: 1 запрос / 10 сек на игрока.
 *
 * Модуль НЕ хранит своё состояние отдельно — он читает/пишет напрямую
 * в ClientData.moduleStates (тот же Map<String, Boolean>, который
 * использует и LexoraGui, и ModernClickGui через ModernGuiRegistry).
 */
public final class LiteApiFeatureControl {

    public static final Identifier CHANNEL = Identifier.of("liteapi", "feature-control");

    /**
     * Стабильный ID клиента для сервера. НЕ менять между версиями мода —
     * по этой строке HolyWorld хранит персональные блокировки для Lexora.
     * Сверить с Зако/HolyWorld перед боевым релизом.
     */
    private static final String CLIENT_ID = "lexora";

    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger("Lexora-LiteAPI");
    private static final long MIN_REQUEST_INTERVAL_MS = 10_500L;

    private static long lastRequestSentAt = 0L;

    /**
     * Помимо callback'а храним набор модулей, про которые реально спросили сервер в этом
     * запросе. Нужно в applyBlocklist(), чтобы не снимать блокировку с модуля, который в
     * конкретно ЭТОМ запросе не участвовал — раньше это и приводило к произвольному
     * включению/выключению функций.
     */
    private record PendingCheck(Set<String> requestedFeatures, RequestCallback callback) {}
    private static final Map<String, PendingCheck> pendingRequests = new ConcurrentHashMap<>();

    /** Модули (те же строки, что ключи ClientData.moduleStates), которые сейчас запрещены сервером. */
    private static volatile Set<String> blockedModules = Collections.emptySet();

    /** Состояние модулей до блокировки, чтобы вернуть его при выходе с сервера. */
    private static final Map<String, Boolean> preBlockStates = new ConcurrentHashMap<>();

    private LiteApiFeatureControl() {}

    // =========================================================================
    // Определение пакета (CustomPayload). Один класс используется и для
    // запроса, и для ответа — оба направления шлют просто "сырую" JSON-строку,
    // разбор происходит вручную через Gson внутри handleIncoming/send.
    // =========================================================================

    public record FeatureControlPayload(String json) implements CustomPayload {
        public static final CustomPayload.Id<FeatureControlPayload> ID =
                new CustomPayload.Id<>(CHANNEL);

        public static final PacketCodec<PacketByteBuf, FeatureControlPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> writeUtf8(buf, payload.json()),
                        buf -> new FeatureControlPayload(readUtf8(buf))
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Вызывать один раз при инициализации мода (в LexoravisaulsClient.onInitializeClient()).
     */
    public static void init() {
        // Регистрация типа пакета обязательна на обоих направлениях (C2S — мы отправляем,
        // S2C — мы получаем), иначе canSend/registerGlobalReceiver выбросят IllegalArgumentException.
        PayloadTypeRegistry.playC2S().register(FeatureControlPayload.ID, FeatureControlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FeatureControlPayload.ID, FeatureControlPayload.CODEC);

        // Новая сигнатура: обработчик получает готовый payload-объект и Context,
        // вызывается уже на render thread — client.execute(...) не нужен.
        ClientPlayNetworking.registerGlobalReceiver(FeatureControlPayload.ID, (payload, context) -> {
            handleIncoming(payload.json());
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // ВАЖНО: на прокси-сети HolyWorld (хаб <-> арены <-> анархия) JOIN срабатывает
            // на КАЖДОМ переходе между бэкенд-серверами, а не только при первом входе в сеть
            // (в логе это видно по повторяющимся "Loaded X advancements"/"Stopping worker
            // threads" по нескольку раз за сессию, с разным числом advancements на разных
            // бэкендах). Раньше здесь стоял restoreAllBlocked(), поэтому заблокированная
            // функция на долю секунды включалась обратно на каждом таком переходе, пока не
            // приходил ответ новой проверки — это и есть "то пропадали то возвращались".
            // Теперь состояние модулей не трогаем на JOIN: они остаются как есть, пока свежая
            // проверка явно не подтвердит снятие блокировки (см. applyBlocklist).
            requestFeatureCheck(currentlyActiveModuleNames(), null);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // А это уже настоящий выход из сети HolyWorld (не переход между бэкендами) —
            // здесь безопасно снять все блокировки и обнулить троттлинг для следующего входа.
            restoreAllBlocked();
            lastRequestSentAt = 0L;
        });
    }

    /** Имена модулей, которые сейчас включены (true) в ClientData.moduleStates. */
    private static Collection<String> currentlyActiveModuleNames() {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : ClientData.moduleStates.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                names.add(e.getKey());
            }
        }
        return names;
    }

    /** Запросить проверку конкретного набора имён модулей. Уважает rate limit сервера. */
    public static void requestFeatureCheck(Collection<String> moduleNames, RequestCallback callback) {
        long now = System.currentTimeMillis();
        if (now - lastRequestSentAt < MIN_REQUEST_INTERVAL_MS) {
            // Раньше при callback == null (именно так уходит автопроверка на JOIN) этот
            // выход был абсолютно безмолвным — ни строчки в лог. Судя по логу, вместе с
            // CHANNEL_UNAVAILABLE ниже это и есть причина, почему за 5+ срабатываний JOIN
            // за сессию в логе есть только один успешный "Применён блок-лист".
            LOGGER.debug("checkFeatures пропущен: троттлинг ({} мс с прошлого запроса)", now - lastRequestSentAt);
            if (callback != null) callback.onError("CLIENT_THROTTLED", "Слишком частые запросы, подождите");
            return;
        }
        if (!ClientPlayNetworking.canSend(FeatureControlPayload.ID)) {
            // Тоже теперь видно в логе. На HolyWorld это ожидаемо: не все бэкенды сети
            // регистрируют канал liteapi:feature-control — в логе рядом видно тот же эффект
            // на соседнем канале: "[Lexora Badge] Server does not support Lexora networking".
            LOGGER.debug("checkFeatures пропущен: канал {} недоступен на этом бэкенде", CHANNEL);
            if (callback != null) callback.onError("CHANNEL_UNAVAILABLE", "Канал liteapi:feature-control недоступен");
            return;
        }
        if (moduleNames.isEmpty()) return;

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
        String json = GSON.toJson(request);
        ClientPlayNetworking.send(new FeatureControlPayload(json));
    }

    private static void handleIncoming(String json) {
        JsonObject obj;
        try {
            obj = GSON.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            return;
        }
        if (obj == null || !obj.has("id") || !obj.has("ok")) {
            if (obj != null && obj.has("event")) {
                LOGGER.debug("Получено push-событие {} по каналу {} — сейчас не обрабатывается", obj.get("event"), CHANNEL);
            }
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
                for (int i = 0; i < arr.size(); i++) blocklist.add(arr.get(i).getAsString());
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

    /**
     * @param requestedFeatures модули, о которых реально спросили сервер в этом запросе.
     *                          Сервер отвечает блок-листом только по ним ("сервер возвращает
     *                          только те функции из вашего списка, которые заблокированы") —
     *                          значит и снимать блокировку мы вправе только с модулей из
     *                          этого же списка. Раньше сравнение шло со ВСЕМ набором
     *                          blockedModules, а в запрос попадают только активные модули
     *                          (currentlyActiveModuleNames()) — то есть заблокированный и
     *                          потому выключенный модуль физически не мог туда попасть.
     *                          Любой последующий запрос из-за этого выглядел так, будто
     *                          сервер его больше не блокирует, и модуль включался обратно сам.
     * @param newBlocklist модули из requestedFeatures, которые сервер считает заблокированными.
     */
    private static void applyBlocklist(Set<String> requestedFeatures, Set<String> newBlocklist) {
        Set<String> stillBlocked = new HashSet<>();

        for (String name : blockedModules) {
            if (requestedFeatures.contains(name) && !newBlocklist.contains(name)) {
                Boolean prevState = preBlockStates.remove(name);
                if (prevState != null) {
                    ClientData.moduleStates.put(name, prevState);
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
        }

        stillBlocked.addAll(newBlocklist);
        blockedModules = Collections.unmodifiableSet(stillBlocked);
        LOGGER.info("Применён блок-лист HolyWorld (проверяли {}): заблокировано {}", requestedFeatures, newBlocklist);
    }

    private static void restoreAllBlocked() {
        for (Map.Entry<String, Boolean> e : preBlockStates.entrySet()) {
            ClientData.moduleStates.put(e.getKey(), e.getValue());
        }
        preBlockStates.clear();
        blockedModules = Collections.emptySet();
    }

    /** Модуль запрещён на текущем сервере? Используется в ModernGuiRegistry. */
    public static boolean isBlocked(String moduleName) {
        return blockedModules.contains(moduleName);
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