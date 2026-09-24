package com.lexoravisauls.client.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.party.LexoraPartyManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * CalloutManager — "Позови на мету"
 *
 * ФИКС: раньше сигнал шёл через отдельную глобальную IRC-комнату
 * "party_callouts" БЕЗ проверки принадлежности к пати — из-за этого
 * метку получал вообще любой игрок с клиентом, даже если он не в пати
 * или состоит в другом пати.
 *
 * Теперь: сигнал отправляется и принимается ТОЛЬКО через party.php,
 * привязан к конкретному коду пати (LexoraPartyManager.partyCode),
 * и сервер сам проверяет, что отправитель/получатель реально состоят
 * в этом пати (action=send_signal / poll_signals).
 *
 * Если игрок не в пати — поллинг вообще не выполняется и кнопка
 * "Позвать на мету" не отправляет запрос.
 */
public final class CalloutManager {

    private static final String BASE_URL       = "https://lexoravisuals.fun/party.php";
    private static final long   POLL_MS         = 2_000L;
    private static final long   SEND_CD         = 3_000L;    // кулдаун отправки
    private static final long   NOTIF_LIFETIME  = 15_000L;   // синхронно с меткой на 15 сек

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static boolean registered   = false;
    private static long    lastPollMs   = 0;
    private static long    lastSendMs   = 0;
    private static int     lastSignalId = 0;

    // Код пати, для которого валиден lastSignalId. Если игрок сменил
    // пати (вышел/зашёл в другое) или перезашёл в мир — курсор
    // сбрасывается, чтобы не тащить id из чужой/старой сессии и не
    // словить дубли/пропуски при новом пати.
    private static String trackedPartyCode = null;

    /** Текущее активное уведомление (читается DynamicIslandRenderer) */
    public static volatile CalloutNotif activeNotif = null;

    private CalloutManager() {}

    // =========================================================================
    //  Data
    // =========================================================================
    public static final class CalloutNotif {
        public final String senderName;
        public final String senderUuid;
        public final long   createdAt;

        public CalloutNotif(String name, String uuid) {
            this.senderName = name;
            this.senderUuid = uuid;
            this.createdAt  = System.currentTimeMillis();
        }

        public float secondsLeft() {
            return Math.max(0, NOTIF_LIFETIME - (System.currentTimeMillis() - createdAt)) / 1000f;
        }

        public boolean expired() {
            return System.currentTimeMillis() - createdAt > NOTIF_LIFETIME;
        }
    }

    // =========================================================================
    //  Register
    // =========================================================================
    public static void register() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Авто-очистка истёкшего уведомления
            if (activeNotif != null && activeNotif.expired()) {
                activeNotif = null;
            }

            // Не в пати — вообще не поллим. Это и есть главный фикс:
            // без пати сигналы просто не запрашиваются и не приходят.
            if (!LexoraPartyManager.inParty()) {
                if (trackedPartyCode != null) {
                    trackedPartyCode = null;
                    lastSignalId = 0;
                }
                return;
            }

            String code = LexoraPartyManager.partyCode;

            // Сменилось пати (вышли/зашли в другое, либо это новый заход
            // в мир после ребута клиента) — сбрасываем курсор поллинга.
            if (!code.equals(trackedPartyCode)) {
                trackedPartyCode = code;
                lastSignalId = 0;
            }

            long now = System.currentTimeMillis();
            if (now - lastPollMs >= POLL_MS) {
                lastPollMs = now;
                poll(client, code);
            }
        });
    }

    // =========================================================================
    //  Дедупликация и защита от эхо (Recent Marks)
    // =========================================================================
    public static final class RecentMark {
        public final String name;
        public final int blockX, blockZ;
        public final long time;

        public RecentMark(String name, double x, double z, long time) {
            this.name = name != null ? name.toLowerCase(java.util.Locale.ROOT) : "";
            this.blockX = (int) Math.round(x);
            this.blockZ = (int) Math.round(z);
            this.time = time;
        }

        public boolean matches(String otherName, double ox, double oz, long now) {
            if (now - time > 12_000L) return false;
            String on = otherName != null ? otherName.toLowerCase(java.util.Locale.ROOT) : "";
            if (name.equals(on) || on.contains(name) || name.contains(on)) {
                int obx = (int) Math.round(ox);
                int obz = (int) Math.round(oz);
                return Math.abs(blockX - obx) <= 8 && Math.abs(blockZ - obz) <= 8;
            }
            return false;
        }
    }

    private static final java.util.List<RecentMark> recentMarks = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static boolean isRecentPartyMark(String name, double x, double z) {
        long now = System.currentTimeMillis();
        recentMarks.removeIf(m -> now - m.time > 15_000L);
        for (RecentMark m : recentMarks) {
            if (m.matches(name, x, z, now)) return true;
        }
        return false;
    }

    public static void recordPartyMark(String name, double x, double z) {
        long now = System.currentTimeMillis();
        recentMarks.removeIf(m -> now - m.time > 15_000L);
        recentMarks.add(new RecentMark(name, x, z, now));
    }

    // =========================================================================
    //  Отправить callout (вызывается из LexoraPartyClient.sendPartyHelp)
    // =========================================================================
    public static void sendCallout() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!LexoraPartyManager.inParty()) {
            NotifManager.show("Callout", "Вы не в пати!", NotifManager.NotifType.ERROR);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSendMs < SEND_CD) {
            return;
        }
        lastSendMs = now;

        String myUuid = mc.player.getUuid().toString();
        String myName = mc.player.getName().getString();
        String code   = LexoraPartyManager.partyCode;

        JsonObject body = new JsonObject();
        body.addProperty("action", "send_signal");
        body.addProperty("code",   code);
        body.addProperty("uuid",   myUuid);
        body.addProperty("name",   myName);
        body.addProperty("x", mc.player.getX());
        body.addProperty("y", mc.player.getY());
        body.addProperty("z", mc.player.getZ());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Lexora/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Lexora-Secret", "LexoraAdmin2026")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        System.err.println("[Callout] Сервер вернул ошибку: " + resp.statusCode() + " " + resp.body());
                    }
                })
                .exceptionally(e -> {
                    System.err.println("[Callout] Ошибка сети: " + e.getMessage());
                    return null;
                });
    }

    // =========================================================================
    //  Poll — только сигналы СВОЕГО пати (code передаём явно)
    // =========================================================================
    private static void poll(MinecraftClient mc, String code) {
        String myUuid = mc.player != null ? mc.player.getUuid().toString() : "";
        if (myUuid.isEmpty()) return;

        JsonObject body = new JsonObject();
        body.addProperty("action", "poll_signals");
        body.addProperty("code",   code);
        body.addProperty("uuid",   myUuid);
        body.addProperty("after",  lastSignalId);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Lexora/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Lexora-Secret", "LexoraAdmin2026")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) return;
                    try {
                        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (!json.has("signals")) return;
                        JsonArray arr = json.getAsJsonArray("signals");
                        if (arr.isEmpty()) return;

                        mc.execute(() -> {
                            // Пока ответ летел по сети, игрок мог выйти из пати
                            if (!LexoraPartyManager.inParty() || !code.equals(LexoraPartyManager.partyCode)) {
                                return;
                            }

                            for (int i = 0; i < arr.size(); i++) {
                                JsonObject obj = arr.get(i).getAsJsonObject();
                                int    id   = obj.get("id").getAsInt();
                                String name = obj.get("name").getAsString();
                                String senderUuid = obj.has("uuid") ? obj.get("uuid").getAsString() : "";

                                if (id > lastSignalId) lastSignalId = id;

                                // 1. Защита от эхо — пропускаем свой собственный сигнал
                                if (myUuid.equalsIgnoreCase(senderUuid) || (mc.player != null && mc.player.getName().getString().equalsIgnoreCase(name))) {
                                    continue;
                                }

                                double x = obj.get("x").getAsDouble();
                                double y = obj.get("y").getAsDouble();
                                double z = obj.get("z").getAsDouble();

                                // 2. Защита от дублирования меток
                                if (isRecentPartyMark(name, x, z)) {
                                    continue;
                                }
                                recordPartyMark(name, x, z);

                                // 3. Активируем Dynamic Island
                                activeNotif = new CalloutNotif(name, senderUuid);

                                // 4. Ставим ровно одну красивую GPS-метку
                                String markName = "Хелпа: " + name;
                                GPS.addCalloutWaypoint(markName, x, y, z);

                                // 5. Ровно один чистый звук колокольчика
                                if (mc.player != null) {
                                    mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1f, 1.0f);
                                }

                                // 6. Ровно одно понятное Toast-уведомление
                                NotifManager.show("Метка в пати", name + " просит о помощи!", NotifManager.NotifType.WARNING);
                            }
                        });
                    } catch (Exception ignored) {}
                })
                .exceptionally(e -> null);
    }
}