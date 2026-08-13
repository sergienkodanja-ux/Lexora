package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.NotifManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class ElytraSwap {

    private static final int CHEST_SLOT = 6;

    private static int     step                   = 0;
    private static int     delayTimer             = 0;
    private static int     targetSlot             = -1;
    private static int     previousHotbarSlot     = -1;
    private static boolean wasKeyPressed          = false;
    private static boolean isHotbarSwap           = false;
    private static boolean needReturnOldChestItem = false;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        if (!LexoraGui.moduleStates.getOrDefault("Elytra Swap", false)) { resetState(); return; }

        int     bindKey   = LexoraGui.numSettings.getOrDefault("Elytra Swap Action", -1f).intValue();
        boolean isPressed = bindKey != -1 && InputUtil.isKeyPressed(mc.getWindow().getHandle(), bindKey);

        if (isPressed && !wasKeyPressed && step == 0 && mc.currentScreen == null) {
            if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
                wasKeyPressed = isPressed;
                return;
            }

            ItemStack currentChestItem = mc.player.getInventory().getArmorStack(2);
            boolean hasElytraEquipped = isElytra(currentChestItem);
            boolean hasArmorEquipped  = isChestplate(currentChestItem);

            if (hasElytraEquipped)     targetSlot = findBestChestplate();
            else if (hasArmorEquipped) targetSlot = findElytra();
            else {
                targetSlot = findElytra();
                if (targetSlot == -1) targetSlot = findBestChestplate();
            }

            if (targetSlot != -1) {
                ItemStack targetStack = mc.player.playerScreenHandler.getSlot(targetSlot).getStack();
                String actualName = targetStack.getName().getString()
                        .replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s\\-]", "").trim();
                if (actualName.isEmpty()) actualName = targetStack.getItem().getName().getString();

                NotifManager.show("Свапнул на " + actualName, "Успешно!", NotifManager.NotifType.SUCCESS);

                if (targetSlot >= 36 && targetSlot <= 44) {
                    isHotbarSwap       = true;
                    previousHotbarSlot = mc.player.getInventory().selectedSlot;
                    step               = 1;
                } else {
                    isHotbarSwap           = false;
                    needReturnOldChestItem = !mc.player.playerScreenHandler.getSlot(CHEST_SLOT).getStack().isEmpty();
                    step                   = 9; // начинаем со сброса спринта
                }
                delayTimer = 0;
            }
        }

        wasKeyPressed = isPressed;
        if (step == 0) return;
        if (delayTimer > 0) { delayTimer--; return; }

        if (isHotbarSwap) handleHotbarSwap(mc);
        else              handleInventorySwap(mc);
    }

    // =========================================================================
    //  Хотбар — без инвентаря (interactItem)
    // =========================================================================

    private static void handleHotbarSwap(MinecraftClient mc) {
        switch (step) {
            case 1 -> {
                int idx = targetSlot - 36;
                mc.player.getInventory().selectedSlot = idx;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(idx));
                step = 2; delayTimer = 2;
            }
            case 2 -> {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                step = 3; delayTimer = 2;
            }
            case 3 -> {
                mc.player.getInventory().selectedSlot = previousHotbarSlot;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousHotbarSlot));
                resetState();
            }
        }
    }

    // =========================================================================
    //  Инвентарь — оригинальная логика, добавлен сброс спринта (шаг 9)
    //  перед открытием инвентаря чтобы AC не ругался
    // =========================================================================

    private static void handleInventorySwap(MinecraftClient mc) {
        int syncId = mc.player.playerScreenHandler.syncId;

        switch (step) {

            // ── Шаг 9: сброс спринта ─────────────────────────────────────────
            case 9 -> {
                mc.getNetworkHandler().sendPacket(
                        new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                mc.player.setSprinting(false);
                step = 10; delayTimer = 1;
            }

            // ── Шаг 10: открываем инвентарь ──────────────────────────────────
            case 10 -> {
                mc.getNetworkHandler().sendPacket(
                        new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.OPEN_INVENTORY));
                step = 11; delayTimer = 2;
            }

            // ── Шаг 11: берём нужный предмет с курсор ────────────────────────
            case 11 -> {
                mc.interactionManager.clickSlot(syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                step = 12; delayTimer = 1;
            }

            // ── Шаг 12: кладём в слот нагрудника ─────────────────────────────
            case 12 -> {
                mc.interactionManager.clickSlot(syncId, CHEST_SLOT, 0, SlotActionType.PICKUP, mc.player);
                step = needReturnOldChestItem ? 13 : 14;
                delayTimer = 1;
            }

            // ── Шаг 13: возвращаем старую вещь обратно ───────────────────────
            case 13 -> {
                mc.interactionManager.clickSlot(syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                step = 14; delayTimer = 1;
            }

            // ── Шаг 14: закрываем инвентарь ──────────────────────────────────
            case 14 -> {
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
                resetState();
            }

            default -> resetState();
        }
    }

    // =========================================================================
    //  Публичный метод для AutoSprint
    // =========================================================================

    /** true пока идёт свап — AutoSprint паузирует спринт в это время */
    public static boolean isSwapping() { return step != 0; }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private static void resetState() {
        step = 0; delayTimer = 0; targetSlot = -1;
        previousHotbarSlot = -1; isHotbarSwap = false;
        needReturnOldChestItem = false;
    }

    private static int findElytra() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 36; i <= 44; i++)
            if (isElytra(mc.player.playerScreenHandler.getSlot(i).getStack())) return i;
        for (int i = 9;  i <= 35; i++)
            if (isElytra(mc.player.playerScreenHandler.getSlot(i).getStack())) return i;
        return -1;
    }

    private static int findBestChestplate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 36; i <= 44; i++)
            if (isChestplate(mc.player.playerScreenHandler.getSlot(i).getStack())) return i;
        for (int i = 9;  i <= 35; i++)
            if (isChestplate(mc.player.playerScreenHandler.getSlot(i).getStack())) return i;
        return -1;
    }

    private static boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ELYTRA;
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) return false;
        String name        = stack.getItem().getTranslationKey().toLowerCase();
        String displayName = stack.getName().getString().toLowerCase();
        return name.contains("chestplate")        || name.contains("нагрудник")        || name.contains("куртка")
                || displayName.contains("chestplate") || displayName.contains("нагрудник") || displayName.contains("куртка");
    }
}