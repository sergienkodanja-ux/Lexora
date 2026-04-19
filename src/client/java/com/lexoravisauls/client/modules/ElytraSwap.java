package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.NotifManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class ElytraSwap {

    private static int step = 0;
    private static int delayTimer = 0;
    private static int targetSlot = -1;
    private static int previousHotbarSlot = -1;
    private static boolean wasKeyPressed = false;
    private static boolean isHotbarSwap = false;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        if (!LexoraGui.moduleStates.getOrDefault("Elytra Swap", false)) {
            step = 0;
            return;
        }

        int bindKey = LexoraGui.numSettings.getOrDefault("Elytra Swap Action", -1f).intValue();
        boolean isPressed = bindKey != -1 && InputUtil.isKeyPressed(mc.getWindow().getHandle(), bindKey);

        if (isPressed && !wasKeyPressed && step == 0 && mc.currentScreen == null) {
            ItemStack currentChestItem = mc.player.getInventory().getArmorStack(2);

            boolean hasElytraEquipped = isElytra(currentChestItem);
            boolean hasArmorEquipped = isChestplate(currentChestItem);

            if (hasElytraEquipped) {
                targetSlot = findBestChestplate();
            } else if (hasArmorEquipped) {
                targetSlot = findElytra();
            } else {
                targetSlot = findElytra();
                if (targetSlot == -1) targetSlot = findBestChestplate();
            }

            if (targetSlot != -1) {
                ItemStack targetStack = mc.player.playerScreenHandler.getSlot(targetSlot).getStack();
                String actualName = targetStack.getName().getString();

                actualName = actualName.replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s\\-]", "").trim();
                if (actualName.isEmpty()) {
                    actualName = targetStack.getItem().getName().getString();
                }

                NotifManager.show("Свапнул на " + actualName, "Успешно!", NotifManager.NotifType.SUCCESS);

                if (targetSlot >= 36 && targetSlot <= 44) {
                    isHotbarSwap = true;
                    previousHotbarSlot = mc.player.getInventory().selectedSlot;
                    step = 1;
                    delayTimer = 0;
                } else {
                    isHotbarSwap = false;
                    step = 1;
                    delayTimer = 1; // Минимальная задержка перед кликом в инвентарь
                }
            }
        }
        wasKeyPressed = isPressed;

        if (step > 0) {
            if (delayTimer > 0) {
                delayTimer--;
                return;
            }

            if (isHotbarSwap) {
                if (step == 1) {
                    int targetHotbarIndex = targetSlot - 36;
                    mc.player.getInventory().selectedSlot = targetHotbarIndex;
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(targetHotbarIndex));
                    step = 2;
                    delayTimer = 2;
                } else if (step == 2) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    step = 3;
                    delayTimer = 2;
                } else if (step == 3) {
                    mc.player.getInventory().selectedSlot = previousHotbarSlot;
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousHotbarSlot));
                    step = 0;
                }
            } else {
                // 🔥 ТИХАЯ ЛОГИКА ДЛЯ ИНВЕНТАРЯ (Shift+клик без открытия экрана)
                if (step == 1) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, targetSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    step = 0;
                }
            }
        }
    }

    private static int findElytra() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 36; i <= 44; i++) {
            if (isElytra(mc.player.playerScreenHandler.getSlot(i).getStack())) return i;
        }
        for (int i = 9; i <= 35; i++) {
            if (isElytra(mc.player.playerScreenHandler.getSlot(i).getStack())) return i;
        }
        return -1;
    }

    private static int findBestChestplate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 36; i <= 44; i++) {
            if (isChestplate(mc.player.playerScreenHandler.getSlot(i).getStack())) return i;
        }
        for (int i = 9; i <= 35; i++) {
            if (isChestplate(mc.player.playerScreenHandler.getSlot(i).getStack())) return i;
        }
        return -1;
    }

    private static boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ELYTRA;
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) return false;
        String name = stack.getItem().getTranslationKey().toLowerCase();
        String displayName = stack.getName().getString().toLowerCase();
        return name.contains("chestplate") || name.contains("нагрудник") || name.contains("куртка") ||
                displayName.contains("chestplate") || displayName.contains("нагрудник") || displayName.contains("куртка");
    }
}