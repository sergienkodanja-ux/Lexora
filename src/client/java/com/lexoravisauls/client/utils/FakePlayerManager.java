package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;

import java.util.UUID;

public class FakePlayerManager {

    public static OtherClientPlayerEntity fakePlayer;
    private static boolean wasEnabled = false;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> forceDeactivate());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forceDeactivate());
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            if (fakePlayer != null) forceDeactivate();
            return;
        }

        boolean isEnabled = LexoraGui.moduleStates.getOrDefault("Fake Player", false);

        if (isEnabled && !wasEnabled) {
            activate(mc);
        } else if (!isEnabled && wasEnabled) {
            deactivate(mc);
        }
    }

    private static void activate(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        fakePlayer = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "trfxcvii_GHOST"));
        fakePlayer.copyFrom(mc.player);
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            fakePlayer.getInventory().setStack(i, mc.player.getInventory().getStack(i).copy());
        }
        fakePlayer.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch());

        mc.world.addEntity(fakePlayer);
        wasEnabled = true;
    }

    private static void deactivate(MinecraftClient mc) {
        if (mc.world != null && fakePlayer != null) {
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakePlayer = null;
        wasEnabled = false;
    }

    public static void forceDeactivate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        deactivate(mc);
        LexoraGui.moduleStates.put("Fake Player", false);
    }

    public static OtherClientPlayerEntity getFakeEntity() {
        return fakePlayer;
    }
}