package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class LootNotifier {
    // Храним ОБЩЕЕ количество каждого предмета во всем инвентаре
    private static final Map<String, Integer> lastTotalCounts = new HashMap<>();

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !LexoraGui.moduleStates.getOrDefault("Loot Notifier", false)) return;

        // Каждые 5 тиков проверяем инвентарь (чтобы не грузить ПК)
        if (mc.player.age % 5 != 0) return;

        Map<String, Integer> currentCounts = new HashMap<>();

        // 1. Считаем все предметы во всех слотах (включая хотбар и левую руку)
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            String name = stack.getName().getString();
            currentCounts.put(name, currentCounts.getOrDefault(name, 0) + stack.getCount());
        }

        // 2. Сравниваем с тем, что было 5 тиков назад
        for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
            String name = entry.getKey();
            int current = entry.getValue();
            int last = lastTotalCounts.getOrDefault(name, 0);

            // Если предмета стало БОЛЬШЕ (мы его подняли)
            if (current > last) {
                if (isImportantItem(name)) {
                    int added = current - last;
                    sendNotify(name, added);
                }
            }
        }

        // 3. Обновляем память для следующей проверки
        lastTotalCounts.clear();
        lastTotalCounts.putAll(currentCounts);
    }

    private static boolean isImportantItem(String name) {
        String n = name.toLowerCase();

        // --- ОРУЖИЕ, ИНСТРУМЕНТЫ И БРОНЯ ---
        if (n.contains("крушител") || n.contains("сатан") || n.contains("донат") ||
                n.contains("арбалет") || n.contains("лук") || n.contains("трезубец")) return true;

        // --- ПВП-АЙТЕМЫ, СПОСОБНОСТИ И РАСХОДНИКИ ---
        if (n.contains("божье касание") || n.contains("пласт") || n.contains("явная пыль") ||
                n.contains("божья аура") || n.contains("чарка") || n.contains("золотое яблоко") ||
                n.contains("святая вода") || n.contains("талисман") || n.contains("каратель") ||
                n.contains("тотем") || n.contains("трапка") || n.contains("дезориентация") ||
                n.contains("прогрузчик") || n.contains("сфера") || n.contains("мощный удар") ||
                n.contains("дамагер") || n.contains("снежок") || n.contains("заморозк") ||
                n.contains("блокиратор")) return true;

        // --- КАСТОМНЫЕ ДОНАТ-ЗЕЛЬЯ ---
        if (n.contains("сила") || n.contains("исцелени") || n.contains("гнев") ||
                n.contains("палладин") || n.contains("ассасин") || n.contains("снотворн") ||
                n.contains("радиаци") || n.contains("титан") || n.contains("танк") ||
                n.contains("вампиризм") || n.contains("очищени")) return true;

        return false;
    }

    private static void sendNotify(String itemName, int count) {
        String prefix = "§5[§dLexora§5]§f ";
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                Text.literal(prefix + "Вы подняли донат предмет: §d" + itemName + " §7x" + count)
        );
    }
}