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
        sessionToken = "local_session";
        return new AuthResult(true, "Успешный вход!", "1");
    }

    /**
     * Тихая проверка: профили на сайте отключены, всегда возвращает успех локальной сессии.
     */
    public static HwidResult checkHwid() {
        sessionToken = "local_session";
        return new HwidResult(true, true, "Player", "1", "user", "static");
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