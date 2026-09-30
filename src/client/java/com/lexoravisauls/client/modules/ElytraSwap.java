package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.NotifManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class ElytraSwap {

    private static final int CHEST_SLOT = 6;

    private static int     step                   = 0;
    private static int     delayTimer             = 0;
    private static int     targetSlot             = -1;
    private static int     previousHotbarSlot     = -1;
    private static boolean wasKeyPressed          = false;
    private static boolean isHotbarSwap           = false;
    private static boolean needReturnOldChestItem = false;
    private static String  targetItemName         = "";

    // Дебаг выключен
    private static final boolean DEBUG = false;

    private static void debug(String msg) {
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        boolean isEnabled = ClientData.moduleStates.getOrDefault("Elytra Swap", false)
                || LexoraGui.moduleStates.getOrDefault("Elytra Swap", false);

        if (!isEnabled) {
            resetState();
            return;
        }

        int bindKey = BindManager.getStoredBindValue("Elytra Swap Action");
        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) {
            bindKey = ClientData.moduleBinds.getOrDefault("Elytra Swap Action", GLFW.GLFW_KEY_UNKNOWN);
        }

        boolean isPressed = bindKey != GLFW.GLFW_KEY_UNKNOWN && bindKey != -1 && mc.getWindow() != null
                && BindManager.isBindDown(mc.getWindow().getHandle(), bindKey);

        // ── Нажатие клавиши → инициализация ──────────────────
        if (isPressed && !wasKeyPressed && step == 0) {
            // Блокируем если открыт чужой экран (сундук, верстак и т.д.)
            if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
                wasKeyPressed = isPressed;
                return;
            }

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
                targetItemName = targetStack.getName().getString()
                        .replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s\\-]", "").trim();
                if (targetItemName.isEmpty()) targetItemName = targetStack.getItem().getName().getString();

                debug("Цель: slot=" + targetSlot + " (" + targetItemName + ")");

                if (targetSlot >= 36 && targetSlot <= 44) {
                    // ─── ХОТБАР: interactItem на бегу ───
                    isHotbarSwap       = true;
                    previousHotbarSlot = mc.player.getInventory().selectedSlot;
                    int targetHotbarIdx = targetSlot - 36;

                    if (targetHotbarIdx == previousHotbarSlot) {
                        step = 10; // Уже в руке
                    } else {
                        step = 1;
                    }
                    delayTimer = 0;
                } else {
                    // ─── ИНВЕНТАРЬ: открыть инв → свап → закрыть ───
                    isHotbarSwap           = false;
                    needReturnOldChestItem = !mc.player.playerScreenHandler.getSlot(CHEST_SLOT).getStack().isEmpty();
                    step                   = 20;
                    delayTimer             = 0;
                    debug("Инвентарь: needReturn=" + needReturnOldChestItem);
                }
            } else {
                debug("§cЭлитра/Нагрудник не найдены!");
                NotifManager.show("Элитра/Нагрудник не найдены!", "Ошибка", NotifManager.NotifType.ERROR);
            }
        }

        wasKeyPressed = isPressed;

        // ── Обработка стадий ─────────────────────────────────
        if (step > 0) {
            if (delayTimer > 0) {
                delayTimer--;
                return;
            }

            if (isHotbarSwap) handleHotbarSwap(mc);
            else              handleInventorySwap(mc);
        }
    }

    // =========================================================================
    //  Хотбар — без открытия инвентаря (interactItem на бегу)
    // =========================================================================

    private static void handleHotbarSwap(MinecraftClient mc) {
        switch (step) {
            case 1 -> {
                int idx = targetSlot - 36;
                mc.player.getInventory().selectedSlot = idx;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(idx));
                delayTimer = 1;
                step = 2;
                debug("Хотбар: select slot " + idx);
            }
            case 2 -> {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                delayTimer = 1;
                step = 3;
                debug("Хотбар: interactItem");
            }
            case 3 -> {
                mc.player.getInventory().selectedSlot = previousHotbarSlot;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousHotbarSlot));
                debug("Хотбар: select back " + previousHotbarSlot);
                sendSuccessNotif();
                resetState();
            }
            case 10 -> {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                debug("Прямой interactItem");
                sendSuccessNotif();
                resetState();
            }
            default -> resetState();
        }
    }

    // =========================================================================
    //  Инвентарь: ОТКРЫТЬ → СВАП → ЗАКРЫТЬ (как в ItemSwap/Pulse)
    //  Открытие инвентаря само естественно сбрасывает спринт.
    //  Максимально быстро: 1 тик на фазу.
    // =========================================================================

    private static void handleInventorySwap(MinecraftClient mc) {
        int syncId = mc.player.playerScreenHandler.syncId;

        switch (step) {

            // ── Фаза 1: открываем инвентарь ──────────────────────────────────
            case 20 -> {
                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
                debug("Открыл InventoryScreen");
                delayTimer = 1;
                step = 21;
            }

            // ── Фаза 2: берём нужный предмет на курсор ───────────────────────
            case 21 -> {
                if (mc.currentScreen instanceof InventoryScreen) {
                    debug("clickSlot PICKUP: slot=" + targetSlot);
                    mc.interactionManager.clickSlot(syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                    delayTimer = 1;
                    step = 22;
                } else {
                    debug("§cИнвентарь закрылся — отмена");
                    resetState();
                }
            }

            // ── Фаза 3: кладём в слот нагрудника (слот 6) ─────────────────────
            case 22 -> {
                if (mc.currentScreen instanceof InventoryScreen) {
                    debug("clickSlot PICKUP: chest slot " + CHEST_SLOT);
                    mc.interactionManager.clickSlot(syncId, CHEST_SLOT, 0, SlotActionType.PICKUP, mc.player);
                    if (needReturnOldChestItem) {
                        delayTimer = 1;
                        step = 23;
                    } else {
                        delayTimer = 1;
                        step = 24;
                    }
                } else {
                    debug("§cИнвентарь закрылся — отмена");
                    resetState();
                }
            }

            // ── Фаза 4: возвращаем старую вещь обратно в слот ────────────────
            case 23 -> {
                if (mc.currentScreen instanceof InventoryScreen) {
                    debug("clickSlot PICKUP: return old item to slot " + targetSlot);
                    mc.interactionManager.clickSlot(syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                    delayTimer = 1;
                    step = 24;
                } else {
                    debug("§cИнвентарь закрылся — отмена");
                    resetState();
                }
            }

            // ── Фаза 5: закрываем инвентарь и завершаем ──────────────────────
            case 24 -> {
                if (mc.currentScreen instanceof InventoryScreen) {
                    mc.setScreen(null);
                }
                debug("Закрыл InventoryScreen");
                sendSuccessNotif();
                resetState();
            }

            default -> resetState();
        }
    }

    // =========================================================================
    //  Публичные методы
    // =========================================================================

    public static boolean isSwapping() {
        return step != 0;
    }

    public static boolean shouldSuppressMovement() {
        return false;
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private static void sendSuccessNotif() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemStack chest = mc.player != null ? mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST) : null;
        NotifManager.show("Свапнул на " + targetItemName, "Успешно!", NotifManager.NotifType.SWAP, chest != null && !chest.isEmpty() ? chest.copy() : null);
    }

    private static void resetState() {
        step = 0;
        delayTimer = 0;
        targetSlot = -1;
        previousHotbarSlot = -1;
        isHotbarSwap = false;
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