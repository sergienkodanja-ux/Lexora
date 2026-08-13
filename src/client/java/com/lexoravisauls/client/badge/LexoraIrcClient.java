package com.lexoravisauls.client.badge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.NotifManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LexoraIrcClient {

    // ----------------------------------------------------------------
    // НОВЫЙ URL: IRC теперь на твоём VPS через Nginx reverse-proxy
    // В Nginx добавь:
    //   location /irc/ {
    //       proxy_pass http://127.0.0.1:3000/;
    //       proxy_set_header Host $host;
    //       proxy_set_header X-Real-IP $remote_addr;
    //   }
    // ----------------------------------------------------------------
    private static final String BACKEND_BASE_URL = "https://lexoravisuals.fun/irc";
    private static final String GLOBAL_ROOM      = "global";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final long SEND_COOLDOWN_MS = 1_500L;

    private static boolean registered;
    public  static boolean enabled = true;
    public  static boolean shouldOpenGuiFromChat = false;

    private static long lastPollMs;
    private static long lastSendMs;
    private static int  lastMessageId;

    public static final Map<String, UUID> NAME_TO_UUID = new HashMap<>();

    // --- Система каналов и ЛС ---
    public static String currentChannel = "Global";
    public static final Map<String, List<IrcMessage>> CHANNELS     = new HashMap<>();
    public static final Map<String, Integer>          UNREAD_COUNTS = new HashMap<>();

    public static class IrcMessage {
        public final String  name;
        public final String  text;
        public final boolean isSelf;

        public IrcMessage(String name, String text, boolean isSelf) {
            this.name   = name;
            this.text   = text;
            this.isSelf = isSelf;
        }
    }

    public static List<IrcMessage> getMessages(String channel) {
        return CHANNELS.computeIfAbsent(channel, k -> new ArrayList<>());
    }

    private LexoraIrcClient() {}

    // =====================================================
    // РЕГИСТРАЦИЯ
    // =====================================================
    public static void register() {
        if (registered) return;
        registered = true;

        // Команда .irc — открыть GUI из чата
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message == null) return true;
            if (message.toLowerCase().equals(".irc")) {
                shouldOpenGuiFromChat = true;
                return false;
            }
            return true;
        });

        // Тик-хук — опрос сервера каждые 2 секунды
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!enabled || client.player == null || client.world == null) return;
            long now = System.currentTimeMillis();
            if (now - lastPollMs < 2_000L) return;
            lastPollMs = now;
            poll(client);
        });
    }

    // =====================================================
    // ОТПРАВКА СООБЩЕНИЯ ИЗ GUI
    // =====================================================
    public static boolean sendFromGui(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return false;

        long now = System.currentTimeMillis();
        if (now - lastSendMs < SEND_COOLDOWN_MS) return false;

        lastSendMs = now;
        UUID   uuid = client.player.getUuid();
        String name = client.player.getName().getString();
        NAME_TO_UUID.put(name, uuid);

        String targetChannel = currentChannel;
        String textToSend    = text;

        // Если мы в ЛС — добавляем пометку получателя
        if (!targetChannel.equals("Global")) {
            textToSend = "[PM:" + targetChannel + "] " + text;
        }

        if (textToSend.length() > 200) textToSend = textToSend.substring(0, 200);

        JsonObject body = new JsonObject();
        body.addProperty("server", GLOBAL_ROOM);
        body.addProperty("uuid",   uuid.toString());
        body.addProperty("name",   name);
        body.addProperty("text",   textToSend);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_BASE_URL + "/api/irc/send"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        String finalVisualText = text;
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 429) {
                        // Rate-limited — показываем уведомление
                        client.execute(() -> NotifManager.show("IRC", "Слишком быстро!", NotifManager.NotifType.WARNING));
                        return;
                    }
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        client.execute(() -> {
                            List<IrcMessage> msgs = getMessages(targetChannel);
                            msgs.add(new IrcMessage("Вы", finalVisualText, true));
                            if (msgs.size() > 100) msgs.remove(0);
                        });
                    }
                });

        return true;
    }

    // =====================================================
    // ОПРОС НОВЫХ СООБЩЕНИЙ
    // =====================================================
    private static void poll(MinecraftClient client) {
        String url = BACKEND_BASE_URL + "/api/irc/poll"
                + "?server=" + URLEncoder.encode(GLOBAL_ROOM, StandardCharsets.UTF_8)
                + "&after=" + lastMessageId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) return;
                    try {
                        JsonObject json     = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonArray  messages = json.getAsJsonArray("messages");
                        if (messages == null) return;

                        client.execute(() -> {
                            if (!enabled || client.player == null) return;

                            UUID   selfUuid = client.player.getUuid();
                            String myName   = client.player.getName().getString();

                            for (int i = 0; i < messages.size(); i++) {
                                JsonObject object    = messages.get(i).getAsJsonObject();
                                int    id            = object.get("id").getAsInt();
                                String uuidString    = object.get("uuid").getAsString();
                                String name          = object.get("name").getAsString();
                                String rawText       = object.get("text").getAsString();

                                NAME_TO_UUID.put(name, UUID.fromString(uuidString));
                                if (id > lastMessageId) lastMessageId = id;

                                // Свои сообщения уже добавлены при отправке
                                if (selfUuid.toString().equalsIgnoreCase(uuidString)) continue;

                                String targetChannel = "Global";
                                String actualText    = rawText;

                                // Разбираем ЛС
                                if (rawText.startsWith("[PM:")) {
                                    int closeBracket = rawText.indexOf("]");
                                    if (closeBracket != -1) {
                                        String intendedTarget = rawText.substring(4, closeBracket);
                                        actualText = rawText.substring(closeBracket + 1).trim();

                                        if (intendedTarget.equalsIgnoreCase(myName)) {
                                            targetChannel = name; // Пишут нам
                                        } else {
                                            continue; // Пишут другому
                                        }
                                    }
                                }

                                List<IrcMessage> msgs = getMessages(targetChannel);
                                msgs.add(new IrcMessage(name, actualText, false));
                                if (msgs.size() > 100) msgs.remove(0);

                                // DND-режим
                                boolean isDnd = ClientData.moduleStates.getOrDefault("Lexora IRC DND", false)
                                        || LexoraGui.moduleStates.getOrDefault("Lexora IRC DND", false);

                                if (!currentChannel.equalsIgnoreCase(targetChannel)) {
                                    UNREAD_COUNTS.put(targetChannel,
                                            UNREAD_COUNTS.getOrDefault(targetChannel, 0) + 1);

                                    if (!isDnd) {
                                        if (targetChannel.equals("Global") && actualText.contains("@" + myName)) {
                                            NotifManager.show("IRC Чат", "Вам ответил " + name, NotifManager.NotifType.SUCCESS);
                                            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.2f);
                                        } else if (!targetChannel.equals("Global")) {
                                            NotifManager.show("Личное сообщение", "Новое ЛС от " + name, NotifManager.NotifType.SUCCESS);
                                            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.2f);
                                        }
                                    }
                                } else if (!isDnd && actualText.contains("@" + myName)) {
                                    NotifManager.show("IRC Чат", "Вам ответил " + name, NotifManager.NotifType.SUCCESS);
                                    client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.2f);
                                }
                            }
                        });
                    } catch (Exception ignored) {}
                });
    }
}