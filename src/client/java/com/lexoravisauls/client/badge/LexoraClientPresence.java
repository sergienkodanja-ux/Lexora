package com.lexoravisauls.client.badge;

import com.lexoravisauls.network.LexoraHelloC2SPayload;
import com.lexoravisauls.network.LexoraUsersSyncS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class LexoraClientPresence {
    private static boolean registered;
    private static boolean payloadsRegistered;

    private LexoraClientPresence() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        registerPayloadTypes();

        System.out.println("[Lexora Badge] Client presence registered");

        ClientPlayNetworking.registerGlobalReceiver(
                LexoraUsersSyncS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    LexoraModUsers.setServerUsers(payload.users());
                    System.out.println("[Lexora Badge] Server users loaded: " + payload.users().size());
                })
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // ВАЖНО: используем clearOnRejoin(), а не clear(). Некоторые серверы
            // (например Dexland с внутренними ареными переходами) шлют повторный
            // JOIN каждые 10-15 секунд, хотя игрок фактически не покидал сервер.
            // Полный clear() сносил BACKEND_NAMES/SELF_NAMES (данные из HTTP-
            // бэкенда, не связанные с Minecraft-соединением) на каждый такой
            // реконнект, из-за чего косметика других игроков мигала — рендер
            // терял hasName() ровно в момент очистки и до следующего успешного
            // обновления списка с бэкенда. clearOnRejoin() трогает только то,
            // что реально привязано к сетевому соединению (SERVER_USERS,
            // BACKEND_USERS, SELF_USERS через UUID).
            LexoraModUsers.clearOnRejoin();

            client.execute(() -> {
                if (client.player != null) {
                    // Передаём и UUID, и ник — раньше здесь был только UUID,
                    // из-за чего SELF_NAMES не пополнялось этим вызовом вообще
                    // и hasName(свой_ник) зависел исключительно от BACKEND_NAMES.
                    LexoraModUsers.addSelf(client.player.getUuid(), client.player.getName().getString());
                    System.out.println("[Lexora Badge] Self user added: " + client.player.getUuid());
                }
            });

            if (ClientPlayNetworking.canSend(LexoraHelloC2SPayload.ID)) {
                ClientPlayNetworking.send(new LexoraHelloC2SPayload());
                System.out.println("[Lexora Badge] Hello packet sent to server");
            } else {
                System.out.println("[Lexora Badge] Server does not support Lexora networking");
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Полный clear() — оправдан только здесь, при реальном разрыве
            // соединения с сервером целиком (вышел с сервера / закрыл игру /
            // сервер упал). Тут действительно всё устарело и должно обнулиться.
            LexoraModUsers.clear();
            System.out.println("[Lexora Badge] Users cleared");
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null && com.lexoravisauls.client.gui.main_menu.LexoraMainMenu.isAuthenticatedSession) {
                // При заходе на сервер железобетонно подтверждаем сайту свой текущий ник
                com.lexoravisauls.client.badge.LexoraAccount.syncCurrentIgn(client.player.getName().getString());
            }
        });

    }

    private static void registerPayloadTypes() {
        if (payloadsRegistered) return;
        payloadsRegistered = true;

        try {
            PayloadTypeRegistry.playC2S().register(
                    LexoraHelloC2SPayload.ID,
                    LexoraHelloC2SPayload.CODEC
            );

            System.out.println("[Lexora Badge] C2S payload registered");
        } catch (IllegalArgumentException ignored) {
            System.out.println("[Lexora Badge] C2S payload already registered");
        }

        try {
            PayloadTypeRegistry.playS2C().register(
                    LexoraUsersSyncS2CPayload.ID,
                    LexoraUsersSyncS2CPayload.CODEC
            );

            System.out.println("[Lexora Badge] S2C payload registered");
        } catch (IllegalArgumentException ignored) {
            System.out.println("[Lexora Badge] S2C payload already registered");
        }
    }
}