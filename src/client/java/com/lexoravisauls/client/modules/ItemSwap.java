package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.ItemSwapWheelScreen;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.inventorymanager.ItemIdentity;
import com.lexoravisauls.client.utils.NotifManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * ItemSwap — быстрый и безопасный свап предметов в левую руку (оффхенд).
 *
 * Полное устранение детектов BadPackets (GrimAC, Vulcan, Matrix, FunTime, HolyWorld):
 * 1. БЕЗ ОТКРЫТИЯ ИНВЕНТАРЯ:
 *    - Ни в одном режиме экран инвентаря (InventoryScreen) физически НЕ открывается перед игроком.
 * 2. ХОТБАР СВАП:
 *    - Строго распределен по отдельным игровым тикам (1 действие = 1 тик, 50 мс):
 *      Тик 1: выбор слота (UpdateSelectedSlot) + обновление selectedSlot на клиенте;
 *      Тик 2: ванильный свап клавишей F (SWAP_ITEM_WITH_OFFHAND);
 *      Тик 3: возврат на исходный слот хотбара.
 *    - Если предмет УЖЕ в руке — свап происходит мгновенно за 0 тиков (1 пакет F).
 *    - Игрок бежит на полной скорости (спринт НЕ прерывается).
 * 3. ИНВЕНТАРНЫЙ СВАП (слоты 9..35):
 *    - Тихий клик на syncId 0 без всплывания окна на экране.
 *    - Спринт аккуратно глушится через MixinPlayerSprint без спама сырыми пакетами STOP_SPRINTING.
 *    - По завершении отправляется штатное закрытие контейнера через closeHandledScreen().
 * 4. РЕЖИМЫ:
 *    - "Двойной": быстрое переключение между двумя предметами (Swap From / Swap To).
 *    - "Тройной": открытие кругового селектора (ItemSwapWheelScreen) на 3 предмета.
 */
public class ItemSwap {

    private enum SwapPhase {
        IDLE,
        // Хотбар свап
        HOTBAR_SWITCH,
        HOTBAR_SWAP,
        HOTBAR_RESTORE,
        // Инвентарный свап (без открытия GUI)
        INV_SILENT_WAIT,
        INV_SILENT_CLICK,
        INV_SILENT_CLOSE
    }

    private static boolean wasKeyPressed = false;
    private static long lastSwapTime = 0L;
    private static String targetItemType = "";
    private static ItemSwapWheelScreen.WheelSlotData activeTargetSlotData = null;
    private static String targetDisplayName = "";
    private static int lastPlayerAge = -1;

    private static SwapPhase phase = SwapPhase.IDLE;
    private static int waitTicks = 0;
    private static int targetHotbarSlot = -1;
    private static int prevSelectedSlot = -1;
    private static int pendingInvSlot = -1;

    public static boolean isSprintLocked() {
        return phase == SwapPhase.INV_SILENT_WAIT
                || phase == SwapPhase.INV_SILENT_CLICK
                || phase == SwapPhase.INV_SILENT_CLOSE;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) {
            resetState();
            return;
        }

        boolean isEnabled = ClientData.moduleStates.getOrDefault("Item Swap", false)
                || LexoraGui.moduleStates.getOrDefault("Item Swap", false);

        if (!isEnabled || mc.player.isDead()) {
            resetState();
            return;
        }

        // Прогрессия фаз свапа строго один раз за игровой тик клиента (50 мс)
        int currentAge = mc.player.age;
        if (currentAge != lastPlayerAge) {
            lastPlayerAge = currentAge;
            handlePhaseTick(mc);
        }

        // Обработка бинда клавиши
        int bindKey = BindManager.getStoredBindValue("Item Swap Action");
        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) {
            bindKey = ClientData.moduleBinds.getOrDefault("Item Swap Action", GLFW.GLFW_KEY_UNKNOWN);
        }
        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) {
            bindKey = LexoraGui.moduleBinds.getOrDefault("Item Swap Action", GLFW.GLFW_KEY_UNKNOWN);
        }
        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) {
            Float f = ClientData.numSettings.get("Item Swap Action");
            if (f != null && f.intValue() != GLFW.GLFW_KEY_UNKNOWN && f.intValue() != -1) bindKey = f.intValue();
        }
        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) {
            Float f = LexoraGui.numSettings.get("Item Swap Action");
            if (f != null && f.intValue() != GLFW.GLFW_KEY_UNKNOWN && f.intValue() != -1) bindKey = f.intValue();
        }

        boolean isPressed = bindKey != GLFW.GLFW_KEY_UNKNOWN && bindKey != -1 && mc.getWindow() != null
                && BindManager.isBindDown(mc.getWindow().getHandle(), bindKey);

        if (isPressed && !wasKeyPressed) {
            String swapMode = ClientData.modeSettings.getOrDefault("Swap Mode",
                    LexoraGui.modeSettings.getOrDefault("Swap Mode", "Двойной"));

            if ("Тройной".equalsIgnoreCase(swapMode)) {
                // Тройной свап: открываем круговой селектор
                if (mc.currentScreen == null && phase == SwapPhase.IDLE) {
                    mc.setScreen(new ItemSwapWheelScreen(bindKey));
                }
            } else {
                // Двойной свап
                if (mc.currentScreen == null && phase == SwapPhase.IDLE) {
                    long now = System.currentTimeMillis();
                    if (now - lastSwapTime >= 250L) {
                        triggerSwap(mc);
                    }
                }
            }
        }

        wasKeyPressed = isPressed;
    }

    private static void handlePhaseTick(MinecraftClient mc) {
        if (phase == SwapPhase.IDLE) return;

        // Если открылся посторонний экран (чат, сундук и т.д.), прерываем свап
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ItemSwapWheelScreen)) {
            resetState();
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (phase) {
            // ── ХОТБАР СВАП: 3 такта без детекта BadPackets ──
            case HOTBAR_SWITCH -> {
                // Такт 1: Выбираем слот с нужным предметом
                mc.player.getInventory().selectedSlot = targetHotbarSlot;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(targetHotbarSlot));
                waitTicks = 1;
                phase = SwapPhase.HOTBAR_SWAP;
            }
            case HOTBAR_SWAP -> {
                // Такт 2: Ванильная перекладка в левую руку клавишей F
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ORIGIN, Direction.DOWN
                ));
                waitTicks = 1;
                phase = SwapPhase.HOTBAR_RESTORE;
            }
            case HOTBAR_RESTORE -> {
                // Такт 3: Возвращаем выбранный слот на исходный
                mc.player.getInventory().selectedSlot = prevSelectedSlot;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSelectedSlot));
                sendSuccessNotif(mc);
                lastSwapTime = System.currentTimeMillis();
                resetState();
            }

            // ── ИНВЕНТАРНЫЙ СВАП: Тихий клик без открытия GUI ──
            case INV_SILENT_WAIT -> {
                // Такт 1: спринт погашен через MixinPlayerSprint, ожидаем стабильный статус
                waitTicks = 1;
                phase = SwapPhase.INV_SILENT_CLICK;
            }
            case INV_SILENT_CLICK -> {
                // Такт 2: клик слота на syncId 0 без открытия интерфейса игроку
                int syncId = mc.player.playerScreenHandler.syncId;
                mc.interactionManager.clickSlot(syncId, pendingInvSlot, 40, SlotActionType.SWAP, mc.player);
                waitTicks = 1;
                phase = SwapPhase.INV_SILENT_CLOSE;
            }
            case INV_SILENT_CLOSE -> {
                // Такт 3: отправка штатного закрытия контейнера серверу
                mc.player.closeHandledScreen();
                sendSuccessNotif(mc);
                lastSwapTime = System.currentTimeMillis();
                resetState();
            }

            default -> resetState();
        }
    }

    private static void triggerSwap(MinecraftClient mc) {
        targetItemType = getTargetSwapType(mc);

        Optional<Integer> hotbar = findInHotbar(mc, targetItemType);
        if (hotbar.isPresent()) {
            startHotbarSwap(mc, hotbar.get());
            return;
        }

        Optional<Integer> inv = findInInventory(mc, targetItemType);
        if (inv.isPresent()) {
            startInventorySwap(mc, inv.get());
            return;
        }

        // Пробуем альтернативный тип предмета
        String altType = getAlternateSwapType(mc);
        if (!altType.equalsIgnoreCase(targetItemType)) {
            targetItemType = altType;
            hotbar = findInHotbar(mc, targetItemType);
            if (hotbar.isPresent()) {
                startHotbarSwap(mc, hotbar.get());
                return;
            }

            inv = findInInventory(mc, targetItemType);
            if (inv.isPresent()) {
                startInventorySwap(mc, inv.get());
                return;
            }
        }

        NotifManager.show("Предмет [" + targetItemType + "] не найден!",
                "Ошибка", NotifManager.NotifType.ERROR);
    }

    /**
     * Запуск свапа для конкретного сектора колеса (тройной свап).
     * Сохраняет и сравнивает как название, так и точный вид (Item ID + скин головы/NBT).
     */
    public static void triggerSwapToSector(int sector) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (phase != SwapPhase.IDLE) return;

        ItemSwapWheelScreen.WheelSlotData target = ItemSwapWheelScreen.getWheelSlot(sector);
        if (target == null || target.isEmpty()) return;

        // Если нужный предмет уже в левой руке
        if (matchesCandidate(mc.player.getOffHandStack(), target)) {
            return;
        }

        activeTargetSlotData = target;
        targetDisplayName = target.displayName;
        targetItemType = target.displayName;

        // 1. Поиск в хотбаре (слоты 0..8) — быстрый и безопасный путь
        Optional<Integer> hotbar = findInHotbar(mc, target);
        if (hotbar.isPresent()) {
            startHotbarSwap(mc, hotbar.get());
            return;
        }

        // 2. Поиск в основном инвентаре (слоты 9..35) — тихий клик без открытия GUI
        Optional<Integer> inv = findInInventory(mc, target);
        if (inv.isPresent()) {
            startInventorySwap(mc, inv.get());
            return;
        }

        NotifManager.show("Предмет [" + target.displayName + "] не найден!",
                "Ошибка", NotifManager.NotifType.ERROR);
    }

    /**
     * Публичный метод для запуска свапа на конкретный предмет (например, из кругового меню или бинда).
     */
    public static void triggerSwapToItem(String targetItem) {
        if (targetItem == null || targetItem.isEmpty() || targetItem.equalsIgnoreCase("Пусто")) return;

        // Проверяем, соответствует ли targetItem одному из 3 секторов колеса
        for (int i = 0; i < 3; i++) {
            ItemSwapWheelScreen.WheelSlotData slot = ItemSwapWheelScreen.getWheelSlot(i);
            if (slot != null && slot.displayName != null && slot.displayName.equalsIgnoreCase(targetItem)) {
                triggerSwapToSector(i);
                return;
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (phase != SwapPhase.IDLE) return;

        // Если нужный предмет уже в левой руке
        if (isMatchingItem(mc.player.getOffHandStack(), targetItem)) {
            return;
        }

        activeTargetSlotData = null;
        targetDisplayName = targetItem;
        targetItemType = targetItem;

        // 1. Поиск в хотбаре (слоты 0..8) — быстрый и безопасный путь
        Optional<Integer> hotbar = findInHotbar(mc, targetItem);
        if (hotbar.isPresent()) {
            startHotbarSwap(mc, hotbar.get());
            return;
        }

        // 2. Поиск в основном инвентаре (слоты 9..35) — тихий клик без открытия GUI
        Optional<Integer> inv = findInInventory(mc, targetItem);
        if (inv.isPresent()) {
            startInventorySwap(mc, inv.get());
            return;
        }

        NotifManager.show("Предмет [" + targetItem + "] не найден!",
                "Ошибка", NotifManager.NotifType.ERROR);
    }

    /**
     * Старт безопасного свапа из хотбара.
     */
    private static void startHotbarSwap(MinecraftClient mc, int slot) {
        int current = mc.player.getInventory().selectedSlot;

        // Если предмет уже в руках — мгновенный одиночный пакет клавиши F
        if (slot == current) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN, Direction.DOWN
            ));
            sendSuccessNotif(mc);
            lastSwapTime = System.currentTimeMillis();
            resetState();
            return;
        }

        // Предмет в другом слоте хотбара: запускаем потактовую машину смены слота
        prevSelectedSlot = current;
        targetHotbarSlot = slot;
        phase = SwapPhase.HOTBAR_SWITCH;
        waitTicks = 0;
    }

    /**
     * Старт инвентарного свапа (слоты 9..35) без открытия экрана.
     */
    private static void startInventorySwap(MinecraftClient mc, int invSlot) {
        pendingInvSlot = invSlot;
        phase = SwapPhase.INV_SILENT_WAIT;
        waitTicks = 1;
    }

    private static String getTargetSwapType(MinecraftClient mc) {
        ItemStack offhand = mc.player.getOffHandStack();
        String typeA = ClientData.modeSettings.getOrDefault("Swap From", LexoraGui.modeSettings.getOrDefault("Swap From", "Тотем"));
        String typeB = ClientData.modeSettings.getOrDefault("Swap To", LexoraGui.modeSettings.getOrDefault("Swap To", "Шар"));
        return isMatchingItem(offhand, typeA) ? typeB : typeA;
    }

    private static String getAlternateSwapType(MinecraftClient mc) {
        ItemStack offhand = mc.player.getOffHandStack();
        String typeA = ClientData.modeSettings.getOrDefault("Swap From", LexoraGui.modeSettings.getOrDefault("Swap From", "Тотем"));
        String typeB = ClientData.modeSettings.getOrDefault("Swap To", LexoraGui.modeSettings.getOrDefault("Swap To", "Шар"));
        return isMatchingItem(offhand, typeA) ? typeA : typeB;
    }

    private static Optional<Integer> findInHotbar(MinecraftClient mc, ItemSwapWheelScreen.WheelSlotData target) {
        for (int i = 0; i < 9; i++) {
            if (matchesCandidate(mc.player.getInventory().getStack(i), target)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> findInInventory(MinecraftClient mc, ItemSwapWheelScreen.WheelSlotData target) {
        for (int i = 9; i < 36; i++) {
            if (matchesCandidate(mc.player.playerScreenHandler.getSlot(i).getStack(), target)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> findInHotbar(MinecraftClient mc, String type) {
        for (int i = 0; i < 9; i++) {
            if (isValidSwapCandidate(mc.player.getInventory().getStack(i), type)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> findInInventory(MinecraftClient mc, String type) {
        for (int i = 9; i < 36; i++) {
            if (isValidSwapCandidate(mc.player.playerScreenHandler.getSlot(i).getStack(), type)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /**
     * Точное сопоставление кандидата со слотом колеса:
     * Проверяет не только обобщенный тип, но и Item ID, кастомное название, текстуру головы (скин сферы).
     */
    public static boolean matchesCandidate(ItemStack candidate, ItemSwapWheelScreen.WheelSlotData target) {
        if (candidate == null || candidate.isEmpty() || target == null || target.isEmpty()) {
            return false;
        }

        // 1. Пресеты (Тотем, Щит, Чар. яблоко, Эндер перл и т.д.)
        if (target.isPreset) {
            return isValidSwapCandidate(candidate, target.displayName);
        }

        // 2. Проверка Item ID (minecraft:player_head, minecraft:shield и т.д.)
        if (target.itemId != null && !target.itemId.isEmpty() && !target.itemId.equals("minecraft:air")) {
            String candId = ItemIdentity.idOf(candidate);
            if (!candId.equalsIgnoreCase(target.itemId)) {
                return false;
            }
        }

        // 3. Проверка кастомного / отображаемого имени
        String targetName = target.customName;
        if (targetName == null || targetName.isEmpty()) {
            targetName = target.displayName;
        }

        if (targetName != null && !targetName.isEmpty() && !targetName.equalsIgnoreCase("Пусто")) {
            String candName = candidate.getName().getString();
            String candClean = cleanItemName(candName);
            String targetClean = cleanItemName(targetName);

            if (!targetClean.isEmpty()) {
                boolean nameMatches = candClean.equalsIgnoreCase(targetClean)
                        || candName.equalsIgnoreCase(targetName);

                if (!nameMatches) {
                    if (candClean.length() >= 4 && targetClean.length() >= 4) {
                        if (candClean.contains(targetClean) || targetClean.contains(candClean)) {
                            nameMatches = true;
                        }
                    }
                }

                if (!nameMatches) {
                    return false;
                }
            }
        }

        // 4. Отпечаток скина головы для сфер / шаров / кастомных голов
        if (target.skinKey != null && !target.skinKey.isEmpty()) {
            String candSkin = ItemIdentity.skinFingerprint(candidate);
            if (!candSkin.isEmpty() && !candSkin.equals(target.skinKey)) {
                return false;
            }
        }

        // 5. CustomModelData
        if (target.modelKey != null && !target.modelKey.isEmpty()) {
            String candModel = ItemIdentity.customModelFingerprint(candidate);
            if (!candModel.equals(target.modelKey)) {
                return false;
            }
        }

        // 6. Зелья
        if (target.potionKey != null && !target.potionKey.isEmpty()) {
            String candPotion = ItemIdentity.potionFingerprint(candidate);
            if (!candPotion.equals(target.potionKey)) {
                return false;
            }
        }

        return true;
    }

    public static String cleanItemName(String s) {
        if (s == null) return "";
        String clean = s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        clean = clean.replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s\\-]", " ").trim().toLowerCase();
        clean = clean.replaceAll("\\s+", " ");
        return clean;
    }

    /**
     * Проверка типа предмета для поиска кандидата на свап.
     */
    private static boolean isValidSwapCandidate(ItemStack stack, String type) {
        if (stack == null || stack.isEmpty() || type == null || type.isEmpty() || type.equalsIgnoreCase("Пусто")) {
            return false;
        }

        if ("Тотем".equalsIgnoreCase(type)) {
            boolean isTotem = stack.getItem() == Items.TOTEM_OF_UNDYING;
            if (!isTotem) {
                String name = stack.getName().getString().toLowerCase();
                isTotem = name.contains("тотем") || name.contains("totem")
                        || name.contains("талисман") || name.contains("защит");
            }
            if (!isTotem) return false;

            boolean onlyEnchanted = ClientData.moduleStates.getOrDefault("Only Enchanted Totems",
                    LexoraGui.moduleStates.getOrDefault("Only Enchanted Totems", false));
            if (onlyEnchanted && !stack.hasEnchantments()) {
                return false;
            }
            return true;
        }

        if ("Шар".equalsIgnoreCase(type) || "Голова".equalsIgnoreCase(type)) {
            if (stack.getItem() == Items.PLAYER_HEAD) return true;
            String name = stack.getName().getString().toLowerCase();
            return name.contains("шар") || name.contains("sphere")
                    || name.contains("сфера") || name.contains("orb")
                    || name.contains("амулет") || stack.getItem().getTranslationKey().contains("player_head");
        }

        if ("Щит".equalsIgnoreCase(type)) {
            return stack.getItem() == Items.SHIELD;
        }

        if ("Золотое яблоко".equalsIgnoreCase(type) || "Яблоко".equalsIgnoreCase(type)) {
            return stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE || stack.getItem() == Items.GOLDEN_APPLE;
        }

        if ("Чар. яблоко".equalsIgnoreCase(type)) {
            return stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
        }

        if ("Эндер перл".equalsIgnoreCase(type) || "Перл".equalsIgnoreCase(type)) {
            return stack.getItem() == Items.ENDER_PEARL;
        }

        if ("Зелье".equalsIgnoreCase(type)) {
            return stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.POTION
                    || stack.getItem() == Items.LINGERING_POTION;
        }

        // Поиск по названию (для кастомных предметов из инвентаря)
        String cleanStackName = cleanItemName(stack.getName().getString());
        String cleanTarget = cleanItemName(type);
        if (!cleanTarget.isEmpty() && (cleanStackName.equalsIgnoreCase(cleanTarget)
                || cleanStackName.contains(cleanTarget) || cleanTarget.contains(cleanStackName))) {
            return true;
        }

        return stack.getItem().getName().getString().equalsIgnoreCase(type);
    }

    /**
     * Определение типа текущего предмета в левой руке.
     */
    private static boolean isMatchingItem(ItemStack stack, String type) {
        return isValidSwapCandidate(stack, type);
    }

    private static void sendSuccessNotif(MinecraftClient mc) {
        ItemStack offhand = mc.player.getOffHandStack();
        String name = !offhand.isEmpty() ? offhand.getName().getString() : (!targetDisplayName.isEmpty() ? targetDisplayName : targetItemType);
        NotifManager.show("Свапнул на " + name, "Успешно", NotifManager.NotifType.SWAP, offhand.isEmpty() ? null : offhand.copy());
    }

    public static boolean isSwapping() {
        return phase != SwapPhase.IDLE;
    }

    /**
     * Подавляет движение и спринт ТОЛЬКО при тихом инвентарном свапе, чтобы транзакция на сервере прошла легитно.
     * При хотбар-свапе спринт НЕ подавляется — игрок бежит на полной скорости.
     */
    public static boolean shouldSuppressMovement() {
        return phase == SwapPhase.INV_SILENT_WAIT || phase == SwapPhase.INV_SILENT_CLICK
                || phase == SwapPhase.INV_SILENT_CLOSE;
    }

    public static void resetState() {
        phase = SwapPhase.IDLE;
        waitTicks = 0;
        pendingInvSlot = -1;
        targetHotbarSlot = -1;
        prevSelectedSlot = -1;
        activeTargetSlotData = null;
        targetDisplayName = "";
    }
}