package com.lexoravisauls.client.badge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LexoraAccount {

    public static String username = "Player";
    public static String role = "USER";
    public static String rank = "STATIC";
    public static String uuid = System.getProperty("lexora.uid", "1");
    public static Identifier avatarTexture = null;

    public static void fetchProfileData() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSession() != null) {
            username = client.getSession().getUsername();
        }
    }

    public static void syncCurrentIgn(String currentName) {
        // Профили и старые аксессуары удалены
    }

    public static void downloadAndRegisterAvatar(String avatarUrl) {
        try {
            URL url = new URL(avatarUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            InputStream is = conn.getInputStream();
            NativeImage image = NativeImage.read(is);

            RenderSystem.recordRenderCall(() -> {
                try {
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
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

    public static int getRoleColor() {
        if ("ADMIN".equalsIgnoreCase(role)) return 0xFFFF4B4B; // Красный для Админов
        if ("MEDIA".equalsIgnoreCase(rank)) return 0xFFFFAA00; // Оранжево-золотой для Ютуберов
        if ("BETA".equalsIgnoreCase(rank)) return 0xFF8B5CF6;  // Фиолетовый для Премиума/Тестеров
        return 0xFFA0A0B8; // Серый для обычных пользователей (Статик)
    }
}