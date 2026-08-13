package com.lexoravisauls.client.inventorymanager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Эмулятор "Живого игрока".
 * Физически двигает курсор мыши (через GLFW) по координатам слотов и кликает с человеческими задержками.
 * Обходит любые античиты, так как сервер видит абсолютно легитимные действия с правильными таймингами.
 */
public final class LoadoutRestoreExecutor {

    private static final Deque<ScheduledAction> actionQueue = new ArrayDeque<>();
    private static final Map<Integer, LoadoutSlotData> activeGhosts = new HashMap<>();

    private static boolean tickerRegistered = false;
    private static InventoryLoadout activeLoadout = null;

    private static long nextActionTime = 0L;
    private static int passesRemaining = 0;
    private static final int MAX_PASSES = 30; // 30 проверок инвентаря

    private LoadoutRestoreExecutor() {}

    public static Map<Integer, LoadoutSlotData> ghosts() { return activeGhosts; }
    public static void clearGhosts() { activeGhosts.clear(); }
    public static void dismissGhost(int slotId) { activeGhosts.remove(slotId); }
    public static boolean isBusy() { return !actionQueue.isEmpty() || activeLoadout != null; }

    private static void ensureTicker() {
        if (tickerRegistered) return;
        tickerRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();

        // Предохранитель: если инвентарь закрыт - жестко обрываем процесс
        if (client.player == null || client.interactionManager == null ||
                !(client.currentScreen instanceof HandledScreen<?>)) {
            if (isBusy()) {
                actionQueue.clear();
                activeLoadout = null;
                passesRemaining = 0;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return; // Ждем окончания паузы между кликами

        // Если в очереди есть макро-действия (движение мыши или клик) — выполняем
        if (!actionQueue.isEmpty()) {
            ScheduledAction action = actionQueue.poll();
            action.task.run();
            nextActionTime = System.currentTimeMillis() + action.delayAfterMs;
            return;
        }

        // Очередь пуста, значит предыдущий перенос завершен.
        // Планируем СЛЕДУЮЩИЙ один предмет.
        if (activeLoadout != null) {
            planNextMove(client.player.playerScreenHandler);
        }
    }

    public static void startRestore(PlayerScreenHandler handler, InventoryLoadout loadout) {
        ensureTicker();
        actionQueue.clear();
        activeGhosts.clear();
        activeLoadout = loadout;
        passesRemaining = MAX_PASSES;
        nextActionTime = 0L;
        planNextMove(handler);
    }

    public static void tryHealGhosts(PlayerScreenHandler handler) {
        if (activeGhosts.isEmpty() || isBusy()) return;
        // Для призраков просто обновляем статус, не запуская мышь
        Map<Integer, LoadoutSlotData> targets = new HashMap<>(activeGhosts);
        activeGhosts.clear();
        activeGhosts.putAll(simulatePass(handler, targets, false));
    }

    /**
     * Сканирует инвентарь, находит ОДИН неправильный предмет и ставит в очередь физическое движение мыши.
     */
    private static void planNextMove(PlayerScreenHandler handler) {
        if (passesRemaining <= 0) {
            activeLoadout = null;
            return;
        }
        passesRemaining--;

        Map<Integer, LoadoutSlotData> targets = extractTargets(activeLoadout);
        Map<Integer, LoadoutSlotData> missing = simulatePass(handler, targets, true);

        activeGhosts.clear();
        activeGhosts.putAll(missing);

        // Если очередь действий так и не пополнилась, значит всё на своих местах
        if (actionQueue.isEmpty()) {
            activeLoadout = null;
            passesRemaining = 0;
        }
    }

    private static Map<Integer, LoadoutSlotData> extractTargets(InventoryLoadout loadout) {
        Map<Integer, LoadoutSlotData> targets = new HashMap<>();
        for (LoadoutSlotData d : loadout.slots) {
            if (InventorySlotIds.isManaged(d.handlerSlotId) && d.itemId != null && !"minecraft:air".equals(d.itemId)) {
                targets.put(d.handlerSlotId, d);
            }
        }
        return targets;
    }

    private static Map<Integer, LoadoutSlotData> simulatePass(PlayerScreenHandler handler,
                                                              Map<Integer, LoadoutSlotData> targets,
                                                              boolean allowQueueing) {
        Map<Integer, String> simKey = new HashMap<>();
        for (int slotId = InventorySlotIds.MANAGED_START; slotId <= InventorySlotIds.MANAGED_END; slotId++) {
            ItemStack st = handler.getSlot(slotId).getStack();
            if (!st.isEmpty()) simKey.put(slotId, ItemIdentity.matchKeyOf(st));
        }

        List<Integer> orderedTargets = new ArrayList<>(targets.keySet());
        Collections.sort(orderedTargets);

        Map<Integer, LoadoutSlotData> stillMissing = new HashMap<>();
        Map<String, Deque<Integer>> pool = new HashMap<>();

        for (int slotId = InventorySlotIds.MANAGED_START; slotId <= InventorySlotIds.MANAGED_END; slotId++) {
            String key = simKey.get(slotId);
            if (key != null) pool.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(slotId);
        }

        boolean queuedAnyMoveThisPass = false;

        for (int targetSlot : orderedTargets) {
            LoadoutSlotData need = targets.get(targetSlot);
            String needKey = need.matchKey();

            if (needKey.equals(simKey.get(targetSlot))) {
                Deque<Integer> already = pool.get(needKey);
                if (already != null) already.remove((Integer) targetSlot);
                continue;
            }

            Deque<Integer> candidates = pool.get(needKey);
            Integer sourceSlot = candidates != null ? candidates.pollFirst() : null;

            if (sourceSlot == null) {
                stillMissing.put(targetSlot, need);
                continue;
            }

            // Нашли предмет! Запускаем мышку только один раз за проход
            if (allowQueueing && !queuedAnyMoveThisPass) {
                boolean targetWasEmpty = !simKey.containsKey(targetSlot);
                queueHumanMove(sourceSlot, targetSlot, targetWasEmpty);
                queuedAnyMoveThisPass = true;
            }

            // Симулируем перемещение, чтобы правильно отрисовать красные слоты (призраки)
            String oldTargetKey = simKey.get(targetSlot);
            if (oldTargetKey == null) simKey.remove(sourceSlot);
            else {
                simKey.put(sourceSlot, oldTargetKey);
                pool.computeIfAbsent(oldTargetKey, k -> new ArrayDeque<>()).addLast(sourceSlot);
            }
            simKey.put(targetSlot, needKey);
        }

        return stillMissing;
    }

    /**
     * Создает макро-последовательность: навестись -> кликнуть -> навестись -> кликнуть
     */
    private static void queueHumanMove(int sourceSlot, int targetSlot, boolean targetWasEmpty) {
        // 1. Едем на исходный предмет и берем его
        queueMouseMove(sourceSlot, 50L);
        queueClick(sourceSlot, 0, 100L); // 100мс на поднятие

        // 2. Едем на целевой слот и кладем/свапаем
        queueMouseMove(targetSlot, 50L);
        queueClick(targetSlot, 0, 150L); // 150мс на то, чтобы сервер принял пакет

        if (!targetWasEmpty) {
            // 3. Если в цели что-то мешало, оно сейчас у нас в руке. Возвращаем на исходное место.
            queueMouseMove(sourceSlot, 50L);
            queueClick(sourceSlot, 0, 150L);
        }

        // 4. Глобальная пауза перед следующим проходом, чтобы сервер обновил инвентарь
        actionQueue.add(new ScheduledAction(() -> {}, 250L));
    }

    private static void queueMouseMove(int slotId, long delayMs) {
        actionQueue.add(new ScheduledAction(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!(client.currentScreen instanceof HandledScreen<?> screen)) return;

            Slot slot = screen.getScreenHandler().slots.get(slotId);

            // Вычисляем координаты слота на мониторе. (Работает корректно при закрытой книге рецептов)
            int guiLeft = (screen.width - 176) / 2;
            int guiTop = (screen.height - 166) / 2;

            int slotCenterX = guiLeft + slot.x + 8;
            int slotCenterY = guiTop + slot.y + 8;

            // GLFW использует пиксели окна, поэтому умножаем на масштаб GUI
            double scale = client.getWindow().getScaleFactor();
            double glfwX = slotCenterX * scale;
            double glfwY = slotCenterY * scale;

            // Физически телепортируем мышь ОС
            GLFW.glfwSetCursorPos(client.getWindow().getHandle(), glfwX, glfwY);
        }, delayMs));
    }

    private static void queueClick(int slotId, int button, long delayMs) {
        actionQueue.add(new ScheduledAction(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.interactionManager != null) {
                client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId, slotId, button, SlotActionType.PICKUP, client.player);
            }
        }, delayMs));
    }

    private static class ScheduledAction {
        final Runnable task;
        final long delayAfterMs;
        ScheduledAction(Runnable task, long delayAfterMs) {
            this.task = task;
            this.delayAfterMs = delayAfterMs;
        }
    }
}