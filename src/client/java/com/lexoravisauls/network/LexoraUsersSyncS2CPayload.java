package com.lexoravisauls.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record LexoraUsersSyncS2CPayload(List<UUID> users) implements CustomPayload {
    public static final Id<LexoraUsersSyncS2CPayload> ID =
            new Id<>(Identifier.of("lexoravisauls", "users_sync"));

    public static final PacketCodec<RegistryByteBuf, LexoraUsersSyncS2CPayload> CODEC =
            PacketCodec.ofStatic(LexoraUsersSyncS2CPayload::write, LexoraUsersSyncS2CPayload::read);

    public LexoraUsersSyncS2CPayload {
        users = List.copyOf(users);
    }

    private static void write(RegistryByteBuf buf, LexoraUsersSyncS2CPayload payload) {
        buf.writeVarInt(payload.users.size());

        for (UUID uuid : payload.users) {
            buf.writeUuid(uuid);
        }
    }

    private static LexoraUsersSyncS2CPayload read(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<UUID> users = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            users.add(buf.readUuid());
        }

        return new LexoraUsersSyncS2CPayload(users);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}