package com.lexoravisauls.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LexoraHelloC2SPayload() implements CustomPayload {
    public static final Id<LexoraHelloC2SPayload> ID =
            new Id<>(Identifier.of("lexoravisauls", "hello"));

    public static final PacketCodec<RegistryByteBuf, LexoraHelloC2SPayload> CODEC =
            PacketCodec.unit(new LexoraHelloC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}