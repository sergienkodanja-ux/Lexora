package com.lexoravisauls.client.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuthManager {
    public static String sessionToken = "";

    public static String getHWID() {
        try {
            String hwidData = System.getenv("COMPUTERNAME") + System.getProperty("user.name") + System.getProperty("os.name") + System.getProperty("os.arch");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(hwidData.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return "unknown-hwid";
        }
    }

    public static AuthResult login(String username, String password) {
        try {
            // ВАЖНО: Ошибка 301 означает, что здесь нужен точный протокол (https:// вместо http://)
            URL url = new URL("https://lexoravisuals.fun/auth.php");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false); // Запрещаем кривые редиректы
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String data = "username=" + URLEncoder.encode(username, "UTF-8") +
                    "&password=" + URLEncoder.encode(password, "UTF-8") +
                    "&hwid=" + URLEncoder.encode(getHWID(), "UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(data.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
                if (json.get("status").getAsString().equals("success")) {
                    sessionToken = json.has("token") ? json.get("token").getAsString() : "";
                    return new AuthResult(true, "Успешный вход!", json.has("uuid") ? json.get("uuid").getAsString() : "");
                } else {
                    return new AuthResult(false, json.get("message").getAsString(), "");
                }
            }
            return new AuthResult(false, "Ошибка сервера: " + responseCode + " (Проверь ссылку https://)", "");
        } catch (Exception e) {
            return new AuthResult(false, "Ошибка подключения к интернету", "");
        }
    }

    /**
     * Тихая проверка: привязан ли этот HWID к какому-то аккаунту.
     * Не требует логина/пароля - используется для автовхода при старте клиента.
     */
    public static HwidResult checkHwid() {
        try {
            URL url = new URL("https://lexoravisuals.fun/check_hwid.php");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String data = "hwid=" + URLEncoder.encode(getHWID(), "UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(data.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
                String status = json.get("status").getAsString();

                if (status.equals("success")) {
                    sessionToken = json.has("token") ? json.get("token").getAsString() : "";
                    return new HwidResult(true, true,
                            json.get("username").getAsString(),
                            json.has("uuid") ? json.get("uuid").getAsString() : "",
                            json.has("role") ? json.get("role").getAsString() : "user",
                            json.has("rank") ? json.get("rank").getAsString() : "static");
                } else if (status.equals("not_found")) {
                    return new HwidResult(true, false, "", "", "", "");
                }
            }
            return new HwidResult(false, false, "", "", "", "");
        } catch (Exception e) {
            return new HwidResult(false, false, "", "", "", "");
        }
    }

    public static class AuthResult {
        public boolean success;
        public String message;
        public String uuid;

        public AuthResult(boolean success, String message, String uuid) {
            this.success = success; this.message = message; this.uuid = uuid;
        }
    }

    public static class HwidResult {
        public boolean success; // сервер ответил, JSON разобрался
        public boolean found;   // true - нашёлся аккаунт с этим hwid
        public String username, uuid, role, rank;

        public HwidResult(boolean success, boolean found, String username, String uuid, String role, String rank) {
            this.success = success; this.found = found;
            this.username = username; this.uuid = uuid; this.role = role; this.rank = rank;
        }
    }
}