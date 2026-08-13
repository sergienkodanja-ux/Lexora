package com.lexoravisauls.server;

import com.lexoravisauls.network.LexoraUsersSyncS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LexoraPresenceManager {
    private static final Set<UUID> USERS = ConcurrentHashMap.newKeySet();

    private LexoraPresenceManager() {
    }

    public static void mark(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        USERS.add(player.getUuid());
        sync(server);
    }

    public static void unmark(ServerPlayerEntity player, MinecraftServer server) {
        USERS.remove(player.getUuid());
        sync(server);
    }

    public static void syncTo(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, LexoraUsersSyncS2CPayload.ID)) {
            return;
        }

        ServerPlayNetworking.send(
                player,
                new LexoraUsersSyncS2CPayload(List.copyOf(USERS))
        );
    }

    public static void sync(MinecraftServer server) {
        LexoraUsersSyncS2CPayload payload =
                new LexoraUsersSyncS2CPayload(List.copyOf(USERS));

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, LexoraUsersSyncS2CPayload.ID)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}