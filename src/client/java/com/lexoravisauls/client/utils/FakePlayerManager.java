package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

public class FakePlayerManager {

    private static final int FAKE_ENTITY_ID = -1337;
    private static final float MAX_TURN_PER_TICK = 20.0f;

    private static final int KB_DURATION = 8;
    private static final double KB_GRAVITY = 0.08;
    private static final double KB_FRICTION = 0.6;

    public static OtherClientPlayerEntity fakePlayer;
    private static boolean wasEnabled = false;
    private static PlayerEntity lastKnownPlayer = null;

    private static double anchorX, anchorY, anchorZ;

    private static double kbOffX = 0, kbOffY = 0, kbOffZ = 0;
    private static double kbVelX = 0, kbVelY = 0, kbVelZ = 0;
    private static int kbTicksLeft = 0;

    private static int lastHandledAttackTick = -1;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> forceDeactivate());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            boolean deactivateOnLogout = LexoraGui.moduleStates.getOrDefault("Fake Player Deactivate On Logout", true);
            if (deactivateOnLogout) forceDeactivate();
        });
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            if (fakePlayer != null) forceDeactivate();
            lastKnownPlayer = null;
            return;
        }

        if (lastKnownPlayer != null && lastKnownPlayer != mc.player && (wasEnabled || fakePlayer != null)) {
            forceDeactivate();
        }
        lastKnownPlayer = mc.player;

        boolean isEnabled = LexoraGui.moduleStates.getOrDefault("Fake Player", false);

        if (isEnabled && !wasEnabled) {
            activate(mc);
        } else if (!isEnabled && wasEnabled) {
            deactivate();
        }

        if (fakePlayer != null) {
            updateFakePlayer(mc);
        }
    }

    private static void activate(MinecraftClient mc) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "LexoraDummy");
        fakePlayer = new OtherClientPlayerEntity(mc.world, profile);
        fakePlayer.setId(FAKE_ENTITY_ID);

        anchorX = mc.player.getX() + mc.player.getRotationVector().x * 3.0;
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ() + mc.player.getRotationVector().z * 3.0;

        fakePlayer.refreshPositionAndAngles(anchorX, anchorY, anchorZ, mc.player.getYaw() + 180, mc.player.getPitch());
        fakePlayer.setHeadYaw(mc.player.headYaw + 180);
        fakePlayer.bodyYaw = mc.player.headYaw + 180;
        fakePlayer.calculateDimensions();

        boolean copyInventory = LexoraGui.moduleStates.getOrDefault("Fake Player Copy Inventory", true);
        if (copyInventory) {
            fakePlayer.getInventory().clone(mc.player.getInventory());
        } else {
            fakePlayer.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            fakePlayer.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            fakePlayer.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
            fakePlayer.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
            fakePlayer.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            fakePlayer.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }

        fakePlayer.setHealth(20.0f);
        kbOffX = kbOffY = kbOffZ = 0;
        kbVelX = kbVelY = kbVelZ = 0;
        kbTicksLeft = 0;
        mc.world.addEntity(fakePlayer);
        wasEnabled = true;
    }

    private static void deactivate() {
        if (fakePlayer != null) {
            fakePlayer.discard();
            fakePlayer = null;
        }
        wasEnabled = false;
    }

    public static void forceDeactivate() {
        deactivate();
        LexoraGui.moduleStates.put("Fake Player", false);
    }

    private static void updateFakePlayer(MinecraftClient mc) {
        if (fakePlayer.hurtTime > 0) fakePlayer.hurtTime--;

        // Затухающий отскок — чистая кинематика оффсета от anchor, без setVelocity/move().
        if (kbTicksLeft > 0) {
            kbTicksLeft--;
            kbVelY -= KB_GRAVITY;
            kbOffX += kbVelX;
            kbOffY += kbVelY;
            kbOffZ += kbVelZ;
            kbVelX *= KB_FRICTION;
            kbVelZ *= KB_FRICTION;
            if (kbOffY < 0) {
                kbOffY = 0;
                kbVelY = 0;
            }
        } else if (kbOffX != 0 || kbOffY != 0 || kbOffZ != 0) {
            kbOffX = kbOffY = kbOffZ = 0;
        }

        double dX = mc.player.getX() - fakePlayer.getX();
        double dZ = mc.player.getZ() - fakePlayer.getZ();
        double dY = (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose())) - (fakePlayer.getY() + fakePlayer.getEyeHeight(fakePlayer.getPose()));
        double dist = Math.sqrt(dX * dX + dZ * dZ);
        float targetYaw = (float) (Math.atan2(dZ, dX) * 180.0D / Math.PI) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(dY, dist) * 180.0D / Math.PI));

        float yawDelta = MathHelper.wrapDegrees(targetYaw - fakePlayer.getYaw());
        yawDelta = MathHelper.clamp(yawDelta, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        float newYaw = MathHelper.wrapDegrees(fakePlayer.getYaw() + yawDelta);

        float bodyDelta = MathHelper.wrapDegrees(newYaw - fakePlayer.bodyYaw);
        float newBodyYaw = MathHelper.wrapDegrees(fakePlayer.bodyYaw + bodyDelta * 0.35f);

        fakePlayer.refreshPositionAndAngles(anchorX + kbOffX, anchorY + kbOffY, anchorZ + kbOffZ, newYaw, MathHelper.clamp(targetPitch, -90.0f, 90.0f));
        fakePlayer.setHeadYaw(newYaw);
        fakePlayer.bodyYaw = newBodyYaw;
    }

    public static void handleAttack(PlayerEntity attacker) {
        if (fakePlayer == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        // Защита от повторного вызова за один тик — иначе resetLastAttackedTicks() дёргается
        // дважды подряд и cooldown никогда не доходит до 0.9 (крит физически невозможен).
        int currentTick = attacker.age;
        if (currentTick == lastHandledAttackTick) return;
        lastHandledAttackTick = currentTick;

        float cooldown = attacker.getAttackCooldownProgress(0.5f);
        attacker.resetLastAttackedTicks();

        boolean isCrit = cooldown > 0.9f && attacker.fallDistance > 0.0f
                && !attacker.isOnGround() && !attacker.isClimbing()
                && !attacker.isTouchingWater() && !attacker.hasVehicle();

        // Временный дебаг — по нему в логе будет видно, что именно блокирует крит.
        System.out.println("[LexoraVisuals][FakePlayer] cooldown=" + cooldown + " fallDistance=" + attacker.fallDistance
                + " onGround=" + attacker.isOnGround() + " isCrit=" + isCrit);

        boolean isSweep = false;
        if (cooldown > 0.9f && !isCrit && !attacker.isSprinting()) {
            if (attacker.getMainHandStack().getItem() instanceof SwordItem) isSweep = true;
        }

        float baseDamage = 8.0f;
        float damage = baseDamage * (0.2f + cooldown * cooldown * 0.8f);
        if (isCrit) damage *= 1.5f;

        damage *= 0.25f;

        attacker.swingHand(Hand.MAIN_HAND);
        fakePlayer.hurtTime = 10;
        fakePlayer.maxHurtTime = 10;
        fakePlayer.setHealth(fakePlayer.getHealth() - damage);

        float kb = (cooldown > 0.9f && attacker.isSprinting()) ? 0.6f : 0.4f;
        kbVelX = Math.sin(Math.toRadians(attacker.getYaw())) * -kb;
        kbVelZ = Math.cos(Math.toRadians(attacker.getYaw())) * kb;
        kbVelY = 0.35;
        kbTicksLeft = KB_DURATION;

        if (isCrit) {
            mc.world.playSound(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
            for (int i = 0; i < 15; i++) mc.world.addParticle(ParticleTypes.CRIT, fakePlayer.getParticleX(0.5), fakePlayer.getRandomBodyY(), fakePlayer.getParticleZ(0.5), 0, 0, 0);
        } else if (isSweep) {
            mc.world.playSound(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
            double d = -MathHelper.sin(attacker.getYaw() * 0.017453292F);
            double e = MathHelper.cos(attacker.getYaw() * 0.017453292F);
            mc.world.addParticle(ParticleTypes.SWEEP_ATTACK, attacker.getX() + d, attacker.getBodyY(0.5), attacker.getZ() + e, 0, 0, 0);
        } else if (cooldown > 0.9f) {
            mc.world.playSound(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
        } else {
            mc.world.playSound(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
        }

        mc.world.playSound(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f, false);

        if (cooldown > 0.9f && attacker.isSprinting()) attacker.setSprinting(false);

        if (fakePlayer.getHealth() <= 0) {
            fakePlayer.setHealth(20.0f);
            mc.world.playSound(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
            ParticleSystem.spawnCustomTotemEffect(fakePlayer);
        }
    }

    public static OtherClientPlayerEntity getFakeEntity() {
        return fakePlayer;
    }
}