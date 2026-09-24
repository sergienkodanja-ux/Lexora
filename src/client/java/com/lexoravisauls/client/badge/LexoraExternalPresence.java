package com.lexoravisauls.client.badge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LexoraExternalPresence {

    private static final String BACKEND_URL = "https://lexoravisuals.fun/irc/api/presence";
    private static final String GLOBAL_ROOM = "global";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static boolean registered;
    private static long lastSyncMs;

    private LexoraExternalPresence() {
    }

    public static void triggerSync() {
        lastSyncMs = 0L;
    }

    public static void register() {
        if (registered) return;
        registered = true;

        System.out.println("[Lexora Backend] Global external presence registered");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                return;
            }

            long now = System.currentTimeMillis();

            if (now - lastSyncMs < 7_000L) {
                return;
            }

            lastSyncMs = now;
            sync(client);
        });
    }

    private static void sync(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        UUID uuid = client.player.getUuid();
        String name = client.player.getName().getString();

        LexoraModUsers.addSelf(uuid, name);

        JsonObject body = new JsonObject();
        body.addProperty("server", GLOBAL_ROOM);
        body.addProperty("uuid", uuid.toString());
        body.addProperty("name", name);
        body.addProperty("version", "1.0");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        System.out.println("[Lexora Backend] Bad response: " + response.statusCode());
                        return;
                    }

                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                        JsonArray usersArray = json.getAsJsonArray("users");
                        JsonArray namesArray = json.getAsJsonArray("names");

                        List<UUID> users = new ArrayList<>();
                        List<String> names = new ArrayList<>();

                        if (usersArray != null) {
                            for (int i = 0; i < usersArray.size(); i++) {
                                users.add(UUID.fromString(usersArray.get(i).getAsString()));
                            }
                        }

                        if (namesArray != null) {
                            for (int i = 0; i < namesArray.size(); i++) {
                                names.add(namesArray.get(i).getAsString());
                            }
                        }

                        // ВАЖНО — ЭТО БЫЛ ИСТОЧНИК МИГАНИЯ КОСМЕТИКИ:
                        // Раньше здесь вызывались setBackendUsers()/setBackendNames() —
                        // те же самые методы, что использует CosmeticsManager.fetchModUsers()
                        // для списка игроков КОНКРЕТНОГО Minecraft-сервера (mod_users.php).
                        // Этот класс синхронизируется раз в 7 секунд с ГЛОБАЛЬНЫМ IRC-чатом
                        // (/irc, GLOBAL_ROOM="global") — список участников чата и список
                        // игроков сервера это два разных набора людей. Оба цикла (7 сек
                        // здесь, ~10 сек в CosmeticsManager) независимо перезаписывали
                        // один и тот же BACKEND_NAMES, поэтому каждые несколько секунд
                        // список игроков сервера стирался списком участников чата (часто
                        // более коротким или вообще пустым) — из-за чего hasName() то
                        // true, то false для одного и того же игрока, и рендер косметики
                        // мигал. Теперь пишем в отдельные IRC_USERS/IRC_NAMES, которые
                        // используются только для отображения списка в LexoraIrcScreen
                        // и никак не участвуют в hasName()/has() для рендера косметики.
                        client.execute(() -> {
                            LexoraModUsers.setIrcUsers(users);
                            LexoraModUsers.setIrcNames(names);
                            for (int i = 0; i < users.size() && i < names.size(); i++) {
                                LexoraIrcClient.NAME_TO_UUID.put(names.get(i), users.get(i));
                                LexoraIrcClient.NAME_TO_UUID.put(names.get(i).toLowerCase(), users.get(i));
                            }
                        });
                    } catch (Exception error) {
                        System.out.println("[Lexora Backend] Parse error");
                        error.printStackTrace();
                    }
                })
                .exceptionally(error -> {
                    System.out.println("[Lexora Backend] Request error");
                    return null;
                });
    }
}