package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

public class FastSwap {
    public static final Map<String, Item> SWAP_ITEMS = new LinkedHashMap<>();

    static {
        // ФТ Версия
        SWAP_ITEMS.put("Дезка", Items.ENDER_EYE);
        SWAP_ITEMS.put("Явная пыль", Items.SUGAR);
        SWAP_ITEMS.put("Божья аура", Items.PHANTOM_MEMBRANE);
        SWAP_ITEMS.put("Пласт", Items.DRIED_KELP);
        SWAP_ITEMS.put("Трапка ФТ", Items.COBWEB);

        // ХВ Версия
        SWAP_ITEMS.put("Стан", Items.NETHER_STAR);
        SWAP_ITEMS.put("Взр. штучка", Items.FIRE_CHARGE);
        SWAP_ITEMS.put("Трапка ХВ", Items.POPPED_CHORUS_FRUIT);
        SWAP_ITEMS.put("Взр. трапка", Items.PRISMARINE_SHARD);
        SWAP_ITEMS.put("Ком снега", Items.SNOWBALL);

        // Другое
        SWAP_ITEMS.put("Хорус", Items.CHORUS_FRUIT);
        SWAP_ITEMS.put("Эндер перл", Items.ENDER_PEARL);
        SWAP_ITEMS.put("Исцеление", Items.SPLASH_POTION);
    }

    private static final Map<String, Boolean> wasPressed = new LinkedHashMap<>();

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || mc.getWindow() == null) return;

        boolean isEnabled = ClientData.moduleStates.getOrDefault("Fast Swap", false)
                || LexoraGui.moduleStates.getOrDefault("Fast Swap", false);
        if (!isEnabled) {
            wasPressed.clear();
            return;
        }

        for (Map.Entry<String, Item> entry : SWAP_ITEMS.entrySet()) {
            String name = entry.getKey();
            Item targetItem = entry.getValue();

            int key = BindManager.getStoredBindValue("Bind_" + name);
            if (key == GLFW.GLFW_KEY_UNKNOWN || key == -1) {
                key = ClientData.moduleBinds.getOrDefault("Bind_" + name,
                        LexoraGui.numSettings.getOrDefault("Bind_" + name, -1f).intValue());
            }
            if (key == GLFW.GLFW_KEY_UNKNOWN || key == -1) continue;

            boolean isPressed = BindManager.isBindDown(mc.getWindow().getHandle(), key);
            boolean wasPr = wasPressed.getOrDefault(name, false);

            if (isPressed && !wasPr) {
                switchToItem(mc, targetItem);
            }
            wasPressed.put(name, isPressed);
        }
    }

    private static void switchToItem(MinecraftClient mc, Item targetItem) {
        if (mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == targetItem) {
                mc.player.getInventory().selectedSlot = i;
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(i));
                }
                break;
            }
        }
    }
}