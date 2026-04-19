package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.NotifManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ItemSwap {

    // Всего 4 тика: 2 до свапа + 2 до закрытия
    private static final int SWAP_DELAY_TICKS = 2;
    private static final int CLOSE_DELAY_TICKS = 2;

    private static int step = 0;
    private static int delayTimer = 0;
    private static int targetSlot = -1;
    private static int previousHotbarSlot = -1;
    private static boolean wasKeyPressed = false;
    private static boolean isHotbarSwap = false;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        if (!LexoraGui.moduleStates.getOrDefault("Item Swap", false)) {
            reset();
            return;
        }

        int bindKey = LexoraGui.numSettings.getOrDefault("Item Swap Action", -1f).intValue();
        boolean isPressed = bindKey != -1 && InputUtil.isKeyPressed(mc.getWindow().getHandle(), bindKey);

        String typeA = LexoraGui.modeSettings.getOrDefault("Swap From", "Тотем");
        String typeB = LexoraGui.modeSettings.getOrDefault("Swap To", "Шар");

        if (isPressed && !wasKeyPressed && step == 0 && mc.currentScreen == null) {
            ItemStack offhandItem = mc.player.getOffHandStack();

            boolean offhandIsA = checkItem(offhandItem, typeA);
            boolean offhandIsB = checkItem(offhandItem, typeB);

            if (offhandIsA) {
                targetSlot = findItem(typeB);
            } else if (offhandIsB) {
                targetSlot = findItem(typeA);
            } else {
                targetSlot = findItem(typeA);
                if (targetSlot == -1) targetSlot = findItem(typeB);
            }

            if (targetSlot != -1) {
                ItemStack targetStack = mc.player.playerScreenHandler.getSlot(targetSlot).getStack();
                String actualName = targetStack.getName().getString();
                actualName = actualName.replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s\\-]", "").trim();
                if (actualName.isEmpty()) actualName = targetStack.getItem().getName().getString();

                NotifManager.show("Свапнул на " + actualName, "Успешно!", NotifManager.NotifType.SUCCESS);

                isHotbarSwap = targetSlot >= 36 && targetSlot <= 44;
                previousHotbarSlot = mc.player.getInventory().selectedSlot;

                if (isHotbarSwap) {
                    step = 10;
                    delayTimer = 0;
                } else {
                    step = 1;
                    delayTimer = 0;
                }
            }
        }

        wasKeyPressed = isPressed;

        if (step == 0) return;

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        if (!isHotbarSwap) {
            if (step == 1) {
                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
                step = 2;
                delayTimer = SWAP_DELAY_TICKS;
                return;
            }

            if (step == 2) {
                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    reset();
                    return;
                }

                // Не берет предмет в курсор, а сразу свапает с offhand
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        targetSlot,
                        40,
                        SlotActionType.SWAP,
                        mc.player
                );

                step = 3;
                delayTimer = CLOSE_DELAY_TICKS;
                return;
            }

            if (step == 3) {
                if (mc.currentScreen != null) {
                    mc.setScreen(null);
                }
                reset();
                return;
            }
        }

        if (isHotbarSwap) {
            if (step == 10) {
                int targetHotbarIndex = targetSlot - 36;
                mc.player.getInventory().selectedSlot = targetHotbarIndex;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(targetHotbarIndex));
                step = 11;
                delayTimer = 1;
                return;
            }

            if (step == 11) {
                mc.getNetworkHandler().sendPacket(
                        new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                BlockPos.ORIGIN,
                                Direction.DOWN
                        )
                );
                step = 12;
                delayTimer = 1;
                return;
            }

            if (step == 12) {
                mc.player.getInventory().selectedSlot = previousHotbarSlot;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousHotbarSlot));
                reset();
            }
        }
    }

    private static void reset() {
        step = 0;
        delayTimer = 0;
        targetSlot = -1;
        previousHotbarSlot = -1;
        isHotbarSwap = false;
    }

    private static int findItem(String type) {
        MinecraftClient mc = MinecraftClient.getInstance();

        for (int i = 36; i <= 44; i++) {
            if (checkItem(mc.player.playerScreenHandler.getSlot(i).getStack(), type)) return i;
        }
        for (int i = 9; i <= 35; i++) {
            if (checkItem(mc.player.playerScreenHandler.getSlot(i).getStack(), type)) return i;
        }

        return -1;
    }

    private static boolean checkItem(ItemStack stack, String type) {
        if (type.equals("Тотем")) return isTotem(stack);
        if (type.equals("Шар")) return isSphere(stack);
        return false;
    }

    private static boolean isTotem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == Items.TOTEM_OF_UNDYING) return true;

        String name = stack.getName().getString().toLowerCase();
        return name.contains("тотем")
                || name.contains("totem")
                || name.contains("талисман")
                || name.contains("talisman")
                || name.contains("крест")
                || name.contains("защит");
    }

    private static boolean isSphere(ItemStack stack) {
        if (stack.isEmpty()) return false;

        String name = stack.getName().getString().toLowerCase();
        return name.contains("шар")
                || name.contains("sphere")
                || name.contains("сфера")
                || name.contains("orb")
                || name.contains("амулет")
                || stack.getItem().getTranslationKey().contains("player_head");
    }
}