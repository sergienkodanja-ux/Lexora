package com.lexoravisauls.network;

import com.lexoravisauls.server.LexoraPresenceManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class LexoraNetworking {
    private static boolean registered;

    private LexoraNetworking() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        PayloadTypeRegistry.playC2S().register(
                LexoraHelloC2SPayload.ID,
                LexoraHelloC2SPayload.CODEC
        );

        PayloadTypeRegistry.playS2C().register(
                LexoraUsersSyncS2CPayload.ID,
                LexoraUsersSyncS2CPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                LexoraHelloC2SPayload.ID,
                (payload, context) -> LexoraPresenceManager.mark(context.player())
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LexoraPresenceManager.syncTo(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LexoraPresenceManager.unmark(handler.player, server);
        });
    }
}