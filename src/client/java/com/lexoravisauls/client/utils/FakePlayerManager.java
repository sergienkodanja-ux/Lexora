package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;

import java.util.UUID;

public class FakePlayerManager {
    public static OtherClientPlayerEntity fakePlayer;
    private static boolean wasEnabled = false;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            fakePlayer = null;
            wasEnabled = false;
            return;
        }

        boolean isEnabled = LexoraGui.moduleStates.getOrDefault("Fake Player", false);

        // Спавн бота
        if (isEnabled && !wasEnabled) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "LexoraDummy");
            fakePlayer = new OtherClientPlayerEntity(mc.world, profile);

            // Ставим отрицательный ID, чтобы сервер не сошел с ума
            fakePlayer.setId(-1337);

            // Ставим его ровно в 3 блоках перед тобой
            double x = mc.player.getX() + mc.player.getRotationVector().x * 3.0;
            double y = mc.player.getY();
            double z = mc.player.getZ() + mc.player.getRotationVector().z * 3.0;

            fakePlayer.refreshPositionAndAngles(x, y, z, mc.player.getYaw() + 180, mc.player.getPitch());
            fakePlayer.setHeadYaw(mc.player.headYaw + 180);

            // ФИКС ХИТБОКСА: Принудительно рассчитываем размеры, чтобы ты мог по нему бить!
            fakePlayer.calculateDimensions();

            // Одеваем
            fakePlayer.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            fakePlayer.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            fakePlayer.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
            fakePlayer.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
            fakePlayer.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            fakePlayer.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

            fakePlayer.setHealth(20.0f);

            // Добавляем в мир
            mc.world.addEntity(fakePlayer);
            wasEnabled = true;
        }
        // Удаление бота
        else if (!isEnabled && wasEnabled) {
            if (fakePlayer != null) {
                fakePlayer.discard();
                fakePlayer = null;
            }
            wasEnabled = false;
        }

        // Плавное затухание красного цвета (анимация урона)
        if (fakePlayer != null) {
            if (fakePlayer.hurtTime > 0) {
                fakePlayer.hurtTime--;
            }
        }
    }

    // Обработка удара от твоей Киллауры или ЛКМ
    public static void handleAttack(PlayerEntity attacker) {
        if (fakePlayer == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        // Ты машешь рукой
        attacker.swingHand(Hand.MAIN_HAND);

        // Бот краснеет
        fakePlayer.hurtTime = 10;
        fakePlayer.maxHurtTime = 10;

        // Отнимаем здоровье
        fakePlayer.setHealth(fakePlayer.getHealth() - 4.0f);

        // Звук урона
        mc.world.playSound(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f, false);

        // Отбрасывание (Knockback) назад
        double dx = fakePlayer.getX() - attacker.getX();
        double dz = fakePlayer.getZ() - attacker.getZ();
        fakePlayer.takeKnockback(0.4f, dx, dz);

        // Спавн тотема, если убили
        if (fakePlayer.getHealth() <= 0) {
            fakePlayer.setHealth(20.0f);
            mc.world.playSound(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
            ParticleSystem.spawnCustomTotemEffect(fakePlayer.getPos());
        }
    }
}