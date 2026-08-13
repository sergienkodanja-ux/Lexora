package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.NotifManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.*;

public class LootNotifier {
    private static final Map<String, Integer> lastTotalCounts = new HashMap<>();
    private static final Map<String, Integer> refundDebounce = new HashMap<>();

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !LexoraGui.moduleStates.getOrDefault("Loot Notifier", false)) {
            lastTotalCounts.clear();
            return;
        }

        if (mc.player.age % 5 != 0) return;

        Map<String, Integer> currentCounts = new HashMap<>();

        // 1. Сбор данных (Инвентарь + Шалкеры + Курсор)
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String name = stack.getName().getString();
                currentCounts.put(name, currentCounts.getOrDefault(name, 0) + stack.getCount());
                countInnerItems(stack, currentCounts);
            }
        }
        if (mc.player.currentScreenHandler != null) {
            ItemStack cursorStack = mc.player.currentScreenHandler.getCursorStack();
            if (cursorStack != null && !cursorStack.isEmpty()) {
                String name = cursorStack.getName().getString();
                currentCounts.put(name, currentCounts.getOrDefault(name, 0) + cursorStack.getCount());
                countInnerItems(cursorStack, currentCounts);
            }
        }

        // 2. Группировка новых предметов по категориям
        Map<String, List<String>> groupedLoot = new HashMap<>();

        for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
            String name = entry.getKey();
            int current = entry.getValue();
            int last = lastTotalCounts.getOrDefault(name, 0);

            if (current > last) {
                int lostTick = refundDebounce.getOrDefault(name, 0);
                if (lostTick == 0 || (mc.player.age - lostTick > 20)) {
                    String category = getItemCategory(name);
                    if (category != null) {
                        int added = current - last;
                        String label = name + (added > 1 ? " x" + added : "");
                        groupedLoot.computeIfAbsent(category, k -> new ArrayList<>()).add(label);
                    }
                }
                refundDebounce.remove(name);
            }
        }

        // 3. Отправка сгруппированных уведомлений
        for (Map.Entry<String, List<String>> group : groupedLoot.entrySet()) {
            String categoryName = group.getKey();
            String itemsList = String.join(", ", group.getValue());

            NotifManager.show(
                    "Поднято: " + categoryName,
                    itemsList,
                    NotifManager.NotifType.SUCCESS
            );
        }

        // 4. Обновление памяти потерь и счетчиков
        for (Map.Entry<String, Integer> entry : lastTotalCounts.entrySet()) {
            if (currentCounts.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                refundDebounce.put(entry.getKey(), mc.player.age);
            }
        }
        lastTotalCounts.clear();
        lastTotalCounts.putAll(currentCounts);
    }

    private static String getItemCategory(String name) {
        String n = name.toLowerCase();
        String mode = LexoraGui.modeSettings.getOrDefault("Loot Notifier Mode", "FunTime");

        if (mode.equals("FunTime")) {
            if (n.contains("шлем") || n.contains("нагрудник") || n.contains("поножи") || n.contains("ботинки"))
                return "Броня";

            if (n.contains("крушител") || n.contains("сатан") || n.contains("арбалет") || n.contains("лук") || n.contains("меч") || n.contains("топор"))
                return "Оружие";

            if (n.contains("дезориентация") || n.contains("трапка") || n.contains("сфера") || n.contains("пласт") ||
                    n.contains("явная пыль") || n.contains("аура") || n.contains("талисман") || n.contains("тотем") ||
                    n.contains("яблоко") || n.contains("святая вода") || n.contains("снежок") || n.contains("шар"))
                return "Расходники";

            if (n.contains("сила") || n.contains("исцелени") || n.contains("гнев") || n.contains("титан") || n.contains("вампиризм"))
                return "Зелья";
        }
        else if (mode.equals("HolyWorld")) {
            if (n.contains("шлем") || n.contains("нагрудник") || n.contains("поножи") || n.contains("ботинки") || n.contains("griefer"))
                return "Броня";

            if (n.contains("меч") || n.contains("цербера") || n.contains("eternity") || n.contains("infiniti") || n.contains("кирка"))
                return "Оружие/Инструменты";

            if (n.contains("руна") || n.contains("амулет") || n.contains("сфера") || n.contains("трапка") ||
                    n.contains("стан") || n.contains("стиллер") || n.contains("яблоко") || n.contains("тотем") || n.contains("ключ"))
                return "Расходники";
        }

        // Если предмет важный, но не попал в категории — кидаем в "Донат"
        if (isImportantFallback(n)) return "Донат предметы";

        return null;
    }

    private static boolean isImportantFallback(String n) {
        return n.contains("донат") || n.contains("аирдроп") || n.contains("прогрузчик") || n.contains("маяк") || n.contains("алтарь");
    }

    private static void countInnerItems(ItemStack stack, Map<String, Integer> counts) {
        if (stack.isEmpty()) return;
        if (stack.contains(DataComponentTypes.CONTAINER)) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                for (ItemStack inner : container.iterateNonEmpty()) {
                    counts.put(inner.getName().getString(), counts.getOrDefault(inner.getName().getString(), 0) + inner.getCount());
                    countInnerItems(inner, counts);
                }
            }
        }
        if (stack.contains(DataComponentTypes.BUNDLE_CONTENTS)) {
            BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundle != null) {
                bundle.iterate().forEach(inner -> {
                    counts.put(inner.getName().getString(), counts.getOrDefault(inner.getName().getString(), 0) + inner.getCount());
                    countInnerItems(inner, counts);
                });
            }
        }
        if (stack.contains(DataComponentTypes.CUSTOM_DATA)) {
            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                NbtCompound nbt = customData.copyNbt();
                NbtList itemsList = nbt.contains("Items", 9) ? nbt.getList("Items", 10) :
                        (nbt.contains("BlockEntityTag", 10) ? nbt.getCompound("BlockEntityTag").getList("Items", 10) : null);

                if (itemsList != null) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    RegistryWrapper.WrapperLookup registries = mc.world.getRegistryManager();
                    for (int i = 0; i < itemsList.size(); i++) {
                        ItemStack inner = ItemStack.fromNbtOrEmpty(registries, itemsList.getCompound(i));
                        if (!inner.isEmpty()) {
                            counts.put(inner.getName().getString(), counts.getOrDefault(inner.getName().getString(), 0) + inner.getCount());
                            countInnerItems(inner, counts);
                        }
                    }
                }
            }
        }
    }
}