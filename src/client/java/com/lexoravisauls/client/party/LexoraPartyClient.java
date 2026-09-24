package com.lexoravisauls.client.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.utils.CalloutManager;
import com.lexoravisauls.client.utils.GPS;
import com.lexoravisauls.client.utils.PartyWaypoint;
import com.lexoravisauls.client.utils.NotifManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lexora Party Client — HTTP-логика взаимодействия с party.php на lexoravisuals.fun
 */
public final class LexoraPartyClient {

    private static final String BASE_URL = "https://lexoravisuals.fun/party.php";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static boolean registered = false;

    // Тики для таймингов
    private static long lastHeartbeatMs  = 0;
    private static long lastPollOwnerMs  = 0;
    private static long lastPollJoinerMs = 0;

    private LexoraPartyClient() {}

    // ==============================================================
    // Регистрация тик-хука — вызвать один раз при инициализации мода
    // ==============================================================
    public static void register() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            long now = System.currentTimeMillis();

            // Если мы в пати — шлём heartbeat каждые 10 сек
            if (LexoraPartyManager.inParty()) {
                if (now - lastHeartbeatMs >= 10_000L) {
                    lastHeartbeatMs = now;
                    sendHeartbeat(client);
                }

                // Владелец проверяет входящие запросы каждые 3 сек
                if (LexoraPartyManager.isOwner && now - lastPollOwnerMs >= 3_000L) {
                    lastPollOwnerMs = now;
                    pollRequests(client);
                }
            }

            // Заявитель ожидает ответа — проверяем каждые 2 сек
            if (LexoraPartyManager.waitingForResponse && now - lastPollJoinerMs >= 2_000L) {
                lastPollJoinerMs = now;
                pollInviteResponse(client);
            }

            // Авто-отклоняем уведомление если истекло
            if (LexoraPartyManager.activeInviteNotif != null &&
                    LexoraPartyManager.activeInviteNotif.expired()) {
                var expiredNotif = LexoraPartyManager.activeInviteNotif;
                LexoraPartyManager.activeInviteNotif = null;
                respondToRequest(client, expiredNotif.requesterUuid, false);
            }
        });
    }

    // ==============================================================
    // CREATE — создать пати
    // ==============================================================
    public static void createParty(MinecraftClient client) {
        if (client.player == null) return;

        String uuid = client.player.getUuid().toString();
        String name = client.player.getName().getString();

        JsonObject body = new JsonObject();
        body.addProperty("action", "create");
        body.addProperty("uuid", uuid);
        body.addProperty("name", name);

        post(body, resp -> {
            client.execute(() -> {
                try {
                    JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                    if (json.has("code")) {
                        String code = json.get("code").getAsString();
                        LexoraPartyManager.partyCode = code;
                        LexoraPartyManager.isOwner   = true;
                        LexoraPartyManager.ownerUuid = uuid;
                        LexoraPartyManager.members.clear();
                        LexoraPartyManager.members.add(
                                new LexoraPartyManager.PartyMember(uuid, name, "", true)
                        );
                        NotifManager.show("Пати создано", "Код: " + code, NotifManager.NotifType.SUCCESS);
                    }
                } catch (Exception ignored) {}
            });
        });
    }

    // ==============================================================
    // JOIN — отправить запрос на вступление
    // ==============================================================
    public static void requestJoin(MinecraftClient client, String code) {
        if (client.player == null) return;

        String uuid = client.player.getUuid().toString();
        String name = client.player.getName().getString();

        JsonObject body = new JsonObject();
        body.addProperty("action", "join");
        body.addProperty("code", code);
        body.addProperty("uuid", uuid);
        body.addProperty("name", name);

        post(body, resp -> {
            client.execute(() -> {
                try {
                    JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                    String status = json.has("error") ? json.get("error").getAsString()
                            : json.has("status") ? json.get("status").getAsString() : "error";

                    switch (status) {
                        case "pending" -> {
                            LexoraPartyManager.waitingForResponse = true;
                            LexoraPartyManager.waitingCode        = code;
                            LexoraPartyManager.isOwner            = false;
                            LexoraPartyManager.ownerUuid          = json.has("owner") ? json.get("owner").getAsString() : null;
                            NotifManager.show("Пати", "Запрос отправлен, ожидайте...", NotifManager.NotifType.WARNING);
                        }
                        case "already_member" -> {
                            LexoraPartyManager.partyCode = code;
                            NotifManager.show("Пати", "Вы уже в этом пати!", NotifManager.NotifType.WARNING);
                        }
                        case "not_found" -> NotifManager.show("Пати", "Неверный код!", NotifManager.NotifType.ERROR);
                        case "party_full" -> NotifManager.show("Пати", "Пати заполнено (макс. 10)!", NotifManager.NotifType.ERROR);
                        default -> NotifManager.show("Пати", "Ошибка запроса", NotifManager.NotifType.ERROR);
                    }
                } catch (Exception ignored) {}
            });
        });
    }

    // ==============================================================
    // RESPOND — владелец принимает / отклоняет
    // ==============================================================
    public static void respondToRequest(MinecraftClient client, String targetUuid, boolean accept) {
        if (client.player == null || LexoraPartyManager.partyCode == null) return;

        JsonObject body = new JsonObject();
        body.addProperty("action", "respond");
        body.addProperty("code", LexoraPartyManager.partyCode);
        body.addProperty("owner_uuid", client.player.getUuid().toString());
        body.addProperty("target_uuid", targetUuid);
        body.addProperty("accept", accept);

        post(body, resp -> {
            client.execute(() -> {
                // Удаляем из локального списка запросов
                LexoraPartyManager.pendingRequests.removeIf(r -> r.uuid.equals(targetUuid));
                // Закрываем уведомление если оно про этого игрока
                var notif = LexoraPartyManager.activeInviteNotif;
                if (notif != null && notif.requesterUuid.equals(targetUuid)) {
                    LexoraPartyManager.activeInviteNotif = null;
                }
                // Если есть ещё заявки в очереди, показываем следующую
                if (LexoraPartyManager.activeInviteNotif == null && !LexoraPartyManager.pendingRequests.isEmpty()) {
                    var next = LexoraPartyManager.pendingRequests.get(0);
                    LexoraPartyManager.activeInviteNotif = new LexoraPartyManager.PartyInviteNotif(
                            next.name, next.uuid, LexoraPartyManager.partyCode,
                            () -> respondToRequest(client, next.uuid, true),
                            () -> respondToRequest(client, next.uuid, false)
                    );
                }
            });
        });
    }

    // ==============================================================
    // LEAVE — выйти из пати
    // ==============================================================
    public static void leaveParty(MinecraftClient client) {
        if (client.player == null || !LexoraPartyManager.inParty()) return;

        JsonObject body = new JsonObject();
        body.addProperty("action", "leave");
        body.addProperty("code", LexoraPartyManager.partyCode);
        body.addProperty("uuid", client.player.getUuid().toString());

        post(body, resp -> client.execute(() -> {
            LexoraPartyManager.clear();
            NotifManager.show("Пати", "Вы вышли из пати", NotifManager.NotifType.MODULE_OFF);
        }));
    }

    // ==============================================================
    // HEARTBEAT — обновить свой статус, получить список участников
    // ==============================================================
    private static void sendHeartbeat(MinecraftClient client) {
        if (client.player == null) return;

        // Получаем название сервера
        String serverName = "";
        if (client.getCurrentServerEntry() != null) {
            serverName = client.getCurrentServerEntry().address;
        } else if (client.isInSingleplayer()) {
            serverName = "Singleplayer";
        }

        JsonObject body = new JsonObject();
        body.addProperty("action", "heartbeat");
        body.addProperty("code", LexoraPartyManager.partyCode);
        body.addProperty("uuid", client.player.getUuid().toString());
        body.addProperty("server", serverName);

        post(body, resp -> {
            client.execute(() -> {
                try {
                    JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                    if (!json.has("members")) return;

                    JsonArray arr = json.getAsJsonArray("members");
                    List<LexoraPartyManager.PartyMember> updated = new ArrayList<>();

                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject m = arr.get(i).getAsJsonObject();
                        updated.add(new LexoraPartyManager.PartyMember(
                                m.get("uuid").getAsString(),
                                m.get("name").getAsString(),
                                m.has("server") ? m.get("server").getAsString() : "",
                                m.has("online") && m.get("online").getAsBoolean()
                        ));
                    }

                    LexoraPartyManager.members.clear();
                    LexoraPartyManager.members.addAll(updated);
                } catch (Exception ignored) {}
            });
        });
    }

    // ==============================================================
    // POLL REQUESTS — владелец проверяет входящие заявки
    // ==============================================================
    private static void pollRequests(MinecraftClient client) {
        if (client.player == null) return;

        JsonObject body = new JsonObject();
        body.addProperty("action", "poll_requests");
        body.addProperty("code", LexoraPartyManager.partyCode);
        body.addProperty("owner_uuid", client.player.getUuid().toString());

        post(body, resp -> {
            client.execute(() -> {
                try {
                    JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                    JsonArray arr   = json.has("requests") ? json.getAsJsonArray("requests") : new JsonArray();

                    Set<String> serverUuids = new HashSet<>();
                    boolean addedNew = false;

                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject req  = arr.get(i).getAsJsonObject();
                        String reqUuid  = req.get("uuid").getAsString();
                        String reqName  = req.get("name").getAsString();
                        serverUuids.add(reqUuid);

                        // Уже есть в списке?
                        boolean alreadyHave = LexoraPartyManager.pendingRequests
                                .stream().anyMatch(r -> r.uuid.equals(reqUuid));

                        if (!alreadyHave) {
                            LexoraPartyManager.pendingRequests.add(
                                    new LexoraPartyManager.PendingRequest(reqUuid, reqName)
                            );
                            addedNew = true;
                        }
                    }

                    // Удаляем локальные заявки, которых больше нет на сервере
                    LexoraPartyManager.pendingRequests.removeIf(r -> !serverUuids.contains(r.uuid));
                    if (LexoraPartyManager.activeInviteNotif != null &&
                            !serverUuids.contains(LexoraPartyManager.activeInviteNotif.requesterUuid)) {
                        LexoraPartyManager.activeInviteNotif = null;
                    }

                    // Звуковой сигнал при новой заявке
                    if (addedNew && client.player != null) {
                        client.player.playSound(
                                SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.0f
                        );
                    }

                    // Показываем Dynamic Island уведомление (первый в очереди)
                    if (LexoraPartyManager.activeInviteNotif == null && !LexoraPartyManager.pendingRequests.isEmpty()) {
                        var first = LexoraPartyManager.pendingRequests.get(0);
                        LexoraPartyManager.activeInviteNotif =
                                new LexoraPartyManager.PartyInviteNotif(
                                        first.name, first.uuid, LexoraPartyManager.partyCode,
                                        // onAccept
                                        () -> respondToRequest(client, first.uuid, true),
                                        // onDecline
                                        () -> respondToRequest(client, first.uuid, false)
                                );
                    }
                } catch (Exception ignored) {}
            });
        });
    }

    // ==============================================================
    // POLL INVITE RESPONSE — заявитель ждёт ответа владельца
    // ==============================================================
    private static void pollInviteResponse(MinecraftClient client) {
        if (client.player == null || LexoraPartyManager.waitingCode == null) return;

        JsonObject body = new JsonObject();
        body.addProperty("action", "poll_invite");
        body.addProperty("code", LexoraPartyManager.waitingCode);
        body.addProperty("uuid", client.player.getUuid().toString());

        post(body, resp -> {
            client.execute(() -> {
                try {
                    JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                    String status   = json.has("status") ? json.get("status").getAsString() : "pending";

                    switch (status) {
                        case "accepted" -> {
                            LexoraPartyManager.partyCode          = LexoraPartyManager.waitingCode;
                            LexoraPartyManager.waitingForResponse = false;
                            LexoraPartyManager.waitingCode        = null;
                            NotifManager.show("Пати", "Вас приняли в пати!", NotifManager.NotifType.SUCCESS);
                            if (client.player != null) {
                                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.2f);
                            }
                        }
                        case "declined" -> {
                            LexoraPartyManager.waitingForResponse = false;
                            LexoraPartyManager.waitingCode        = null;
                            LexoraPartyManager.isOwner            = false;
                            NotifManager.show("Пати", "Вам отказали в пати", NotifManager.NotifType.ERROR);
                        }
                        // pending — продолжаем ждать
                    }
                } catch (Exception ignored) {}
            });
        });
    }

    // ==============================================================
    // Утилита — POST запрос (С ФИКСОМ ОБХОДА И СЕКРЕТНЫМ КОДОМ)
    // ==============================================================
    private static void post(JsonObject body, java.util.function.Consumer<String> onSuccess) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Lexora/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Lexora-Secret", "LexoraAdmin2026") // <--- НАШ VIP ПАРОЛЬ
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        onSuccess.accept(resp.body());
                    } else {
                        System.err.println("[LexoraParty] Сервер вернул ошибку: " + resp.statusCode());
                    }
                })
                .exceptionally(e -> {
                    System.err.println("[LexoraParty] Ошибка сети: " + e.getMessage());
                    return null;
                });
    }

    // ==============================================================
    // Отправка сигнала хелпы в пати (Единая точка вызова с кулдауном)
    // ==============================================================
    private static long lastHelpSendMs = 0L;
    private static final long HELP_COOLDOWN_MS = 2500L;

    public static void sendPartyHelp() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!LexoraPartyManager.inParty()) {
            NotifManager.show("Пати", "Вы не состоите в пати!", NotifManager.NotifType.ERROR);
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHelpSendMs < HELP_COOLDOWN_MS) {
            long sec = (HELP_COOLDOWN_MS - (now - lastHelpSendMs) + 999) / 1000;
            NotifManager.show("Хелпа", "Подождите " + sec + " сек!", NotifManager.NotifType.WARNING);
            return;
        }
        lastHelpSendMs = now;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        String myName = mc.player.getName().getString();

        // 1. Фиксируем свою метку в анти-эхо фильтре
        CalloutManager.recordPartyMark(myName, x, z);

        // 2. Отправляем сигнал сопартийцам (CalloutManager передаёт координаты и активирует Dynamic Island)
        CalloutManager.sendCallout();

        // 3. Ставим ровно одну локальную GPS-метку себе на экран
        GPS.addCalloutWaypoint("Моя метка (Хелпа)", x, y, z);

        if (mc.player != null) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.2f);
        }
        NotifManager.show("Хелпа в пати", "Метка помощи отправлена!", NotifManager.NotifType.SUCCESS);
    }
}