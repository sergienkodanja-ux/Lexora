package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.NotifManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ItemSwap {

    // --- ТАЙМИНГИ ДЛЯ "OLD-SCHOOL" СВАПА ---
    private static final int GUI_OPEN_DELAY = 3;  // Ждем 3 тика с открытым инвентарем (гасим инерцию и имитируем реакцию)
    private static final int SWAP_DELAY     = 2;  // Ждем 2 тика после клика, прежде чем закрыть окно
    private static final int HOTBAR_DELAY   = 1;  // Задержки для свапа с хотбара (его открывать не нужно)

    private static int  step               = 0;
    private static int  delayTimer         = 0;
    private static int  targetSlot         = -1;
    private static int  previousHotbarSlot = -1;
    private static boolean wasKeyPressed   = false;
    private static boolean isHotbarSwap    = false;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (!LexoraGui.moduleStates.getOrDefault("Item Swap", false)) { reset(); return; }

        int bindKey = LexoraGui.numSettings.getOrDefault("Item Swap Action", -1f).intValue();
        boolean isPressed = bindKey != -1 && InputUtil.isKeyPressed(mc.getWindow().getHandle(), bindKey);

        // ── Старт свапа ──
        if (isPressed && !wasKeyPressed && step == 0 && mc.currentScreen == null) {
            String typeA = LexoraGui.modeSettings.getOrDefault("Swap From", "Тотем");
            String typeB = LexoraGui.modeSettings.getOrDefault("Swap To",   "Шар");

            ItemStack offhand    = mc.player.getOffHandStack();
            boolean   offhandIsA = checkItem(offhand, typeA);
            boolean   offhandIsB = checkItem(offhand, typeB);

            if      (offhandIsA) targetSlot = findItem(typeB);
            else if (offhandIsB) targetSlot = findItem(typeA);
            else {
                targetSlot = findItem(typeA);
                if (targetSlot == -1) targetSlot = findItem(typeB);
            }

            if (targetSlot != -1) {
                ItemStack target   = mc.player.playerScreenHandler.getSlot(targetSlot).getStack();
                String    itemName = target.getName().getString().replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s\\-]", "").trim();
                if (itemName.isEmpty()) itemName = target.getItem().getName().getString();

                NotifManager.show("Свапнул на " + itemName, "Успешно!", NotifManager.NotifType.SUCCESS);

                isHotbarSwap       = targetSlot >= 36 && targetSlot <= 44;
                previousHotbarSlot = mc.player.getInventory().selectedSlot;

                // Сбрасываем спринт
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                mc.player.setSprinting(false);

                if (!isHotbarSwap) {
                    // ФИЗИЧЕСКИ ОТКРЫВАЕМ ИНВЕНТАРЬ (OLD SCHOOL)
                    mc.setScreen(new InventoryScreen(mc.player));
                    step = 1;
                    delayTimer = GUI_OPEN_DELAY;
                } else {
                    // Хотбар свапается без открытия окна
                    step = 10;
                    delayTimer = HOTBAR_DELAY;
                }
            }
        }

        wasKeyPressed = isPressed;
        if (step == 0) return;
        if (delayTimer > 0) { delayTimer--; return; }

        // =========================================================================
        //  НЕ-хотбар (Открытый Инвентарь)
        // =========================================================================
        if (!isHotbarSwap) {
            switch (step) {
                case 1 -> {
                    // Инвентарь уже открыт 3 тика. Инерция погасла, сервер видит легит. Делаем клик.
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, targetSlot, 40, SlotActionType.SWAP, mc.player);
                    step = 2;
                    delayTimer = SWAP_DELAY;
                }
                case 2 -> {
                    // Закрываем инвентарь по-настоящему
                    mc.player.closeHandledScreen();
                    if (mc.currentScreen instanceof InventoryScreen) {
                        mc.setScreen(null);
                    }
                    resumeSprint(mc);
                    reset();
                }
            }
        }
        // =========================================================================
        //  Хотбар (Без инвентаря)
        // =========================================================================
        else {
            switch (step) {
                case 10 -> {
                    int hotbarIndex = targetSlot - 36;
                    mc.player.getInventory().selectedSlot = hotbarIndex;
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hotbarIndex));

                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                    step = 11;
                    delayTimer = HOTBAR_DELAY;
                }
                case 11 -> {
                    mc.player.getInventory().selectedSlot = previousHotbarSlot;
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousHotbarSlot));
                    resumeSprint(mc);
                    reset();
                }
            }
        }
    }

    // =========================================================================
    //  ХЕЛПЕРЫ
    // =========================================================================

    private static void resumeSprint(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.options.forwardKey.isPressed() && !mc.player.isSneaking()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            mc.player.setSprinting(true);
        }
    }

    /** * Блокировка движения для MixinKeyboardInput.
     * Работает, пока инвентарь открыт, чтобы игрок случайно не дернулся.
     */
    public static boolean isSwapping() {
        return step != 0;
    }

    private static void reset() {
        step               = 0;
        delayTimer         = 0;
        targetSlot         = -1;
        previousHotbarSlot = -1;
        isHotbarSwap       = false;
    }

    private static int findItem(String type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // В первую очередь ищем в хотбаре (это быстрее и без открытия GUI)
        for (int i = 36; i <= 44; i++)
            if (checkItem(mc.player.playerScreenHandler.getSlot(i).getStack(), type)) return i;
        for (int i = 9; i <= 35; i++)
            if (checkItem(mc.player.playerScreenHandler.getSlot(i).getStack(), type)) return i;
        return -1;
    }

    private static boolean checkItem(ItemStack stack, String type) {
        if (type.equals("Тотем")) return isTotem(stack);
        if (type.equals("Шар"))   return isSphere(stack);
        return false;
    }

    private static boolean isTotem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == Items.TOTEM_OF_UNDYING) return true;
        String name = stack.getName().getString().toLowerCase();
        return name.contains("тотем") || name.contains("totem")
                || name.contains("талисман") || name.contains("talisman")
                || name.contains("крест")    || name.contains("защит");
    }

    private static boolean isSphere(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getName().getString().toLowerCase();
        return name.contains("шар")   || name.contains("sphere")
                || name.contains("сфера") || name.contains("orb")
                || name.contains("амулет")
                || stack.getItem().getTranslationKey().contains("player_head");
    }
}