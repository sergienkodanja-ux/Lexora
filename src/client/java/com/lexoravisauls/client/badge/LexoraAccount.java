package com.lexoravisauls.client.badge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class LexoraAccount {

    public static String username = "Player";
    public static String role = "USER";
    public static String rank = "STATIC";
    public static Identifier avatarTexture = null;

    // Ссылка на API твоего сайта
    private static final String API_URL = "https://lexoravisuals.fun/api.php?username=";

    /**
     * Запускает асинхронный запрос к сайту для получения данных профиля.
     * Рекомендуется вызывать в ClientModInitializer (например, в onInitializeClient).
     */
    public static void fetchProfileData() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSession() != null) {
            username = client.getSession().getUsername();
        }

        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(API_URL + username);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                conn.setConnectTimeout(5000); // Таймаут на подключение 5 сек
                conn.setReadTimeout(5000);    // Таймаут на чтение 5 сек

                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    String json = new String(is.readAllBytes());
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

                    if (obj.has("status") && obj.get("status").getAsString().equals("success")) {
                        if (obj.has("role")) role = obj.get("role").getAsString();
                        if (obj.has("rank")) rank = obj.get("rank").getAsString();

                        if (obj.has("avatar_url")) {
                            String avatarUrl = obj.get("avatar_url").getAsString();
                            downloadAndRegisterAvatar(avatarUrl);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[Lexora Visuals] Ошибка загрузки профиля с сайта: " + e.getMessage());
            }
        });
    }

    /**
     * Отправляет на сайт текущий игровой ник для привязки косметики.
     * Вызывай этот метод каждый раз после смены ника в Альт Менеджере!
     */
    public static void syncCurrentIgn(String currentName) {
        // Если мы не авторизованы или нет токена — ничего не делаем
        String token = com.lexoravisauls.client.auth.AuthManager.sessionToken;
        if (token == null || token.isEmpty() || currentName == null || currentName.isEmpty()) return;

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://lexoravisuals.fun/update_ign.php");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                // Отправляем ту самую логику: Токен = 43, Ник = ogorod23
                String data = "token=" + token + "&ign=" + currentName;
                conn.getOutputStream().write(data.getBytes());

                if (conn.getResponseCode() == 200) {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(
                            new java.io.InputStreamReader(conn.getInputStream())
                    ).getAsJsonObject();

                    if (json.has("status") && json.get("status").getAsString().equals("success")) {
                        // Читаем все слоты косметики!
                        String back = json.has("back") ? json.get("back").getAsString() : "none";
                        String head = json.has("head") ? json.get("head").getAsString() : "none";
                        String shoulder = json.has("shoulder") ? json.get("shoulder").getAsString() : "none";

                        com.lexoravisauls.client.cosmetics.CosmeticsManager.clearCache();
                        // Передаем все три параметра в менеджер
                        com.lexoravisauls.client.cosmetics.CosmeticsManager.setCosmetics(currentName, back, head, shoulder);

                        System.out.println("[Lexora] Ник привязан: " + currentName + " | Спина: " + back + " | Голова: " + head + " | Плечо: " + shoulder);
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    /**
     * Скачивает картинку и регистрирует её как текстуру в основном потоке игры.
     */
    private static void downloadAndRegisterAvatar(String avatarUrl) {
        try {
            URL url = new URL(avatarUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            InputStream is = conn.getInputStream();
            NativeImage image = NativeImage.read(is);

            // Текстуры в Minecraft ДОЛЖНЫ регистрироваться в основном потоке рендера!
            RenderSystem.recordRenderCall(() -> {
                try {
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                    // Генерируем уникальный ID для текстуры
                    avatarTexture = Identifier.of("lexoravisauls", "avatar_" + username.toLowerCase());
                    MinecraftClient.getInstance().getTextureManager().registerTexture(avatarTexture, texture);
                } catch (Exception ex) {
                    System.out.println("[Lexora Visuals] Ошибка регистрации текстуры аватара: " + ex.getMessage());
                }
            });
        } catch (Exception e) {
            System.out.println("[Lexora Visuals] Ошибка скачивания аватара: " + e.getMessage());
        }
    }

    /**
     * Возвращает цвет текста (в формате ARGB) в зависимости от роли или подписки игрока.
     */
    public static int getRoleColor() {
        if ("ADMIN".equalsIgnoreCase(role)) return 0xFFFF4B4B; // Красный для Админов
        if ("MEDIA".equalsIgnoreCase(rank)) return 0xFFFFAA00; // Оранжево-золотой для Ютуберов
        if ("BETA".equalsIgnoreCase(rank)) return 0xFF8B5CF6;  // Фиолетовый для Премиума/Тестеров
        return 0xFFA0A0B8; // Серый для обычных пользователей (Статик)
    }
}