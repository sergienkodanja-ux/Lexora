package com.lexoravisauls.client.inventorymanager;

/**
 * Id слотов внутри PlayerScreenHandler (то, что открывается по клавише E) —
 * это НЕ индексы PlayerInventory.getStack(...), а именно id слотов ScreenHandler'а,
 * которые нужны для ClientPlayerInteractionManager.clickSlot(...).
 * Раскладка стабильна ещё с очень старых версий игры:
 * <p>
 * 0       — результат крафта (2x2)
 * 1-4     — сетка крафта 2x2
 * 5-8     — броня: 5=шлем, 6=нагрудник, 7=штаны, 8=ботинки
 * 9-35    — основной инвентарь (27 слотов)
 * 36-44   — хотбар (9 слотов)
 * 45      — офхенд
 * <p>
 * Менеджер раскладок управляет диапазоном 5-45 (броня+сумка+хотбар+офхенд) и
 * не трогает слоты крафта (0-4).
 */
public final class InventorySlotIds {

    public static final int ARMOR_START   = 5;
    public static final int ARMOR_END     = 8;
    public static final int STORAGE_START = 9;
    public static final int STORAGE_END   = 35;
    public static final int HOTBAR_START  = 36;
    public static final int HOTBAR_END    = 44;
    public static final int OFFHAND       = 45;

    public static final int MANAGED_START = ARMOR_START;
    public static final int MANAGED_END   = OFFHAND;

    private InventorySlotIds() {}

    public static boolean isManaged(int slotId) {
        return slotId >= MANAGED_START && slotId <= MANAGED_END;
    }

    public static boolean isArmor(int slotId)   { return slotId >= ARMOR_START && slotId <= ARMOR_END; }
    public static boolean isStorage(int slotId) { return slotId >= STORAGE_START && slotId <= STORAGE_END; }
    public static boolean isHotbar(int slotId)  { return slotId >= HOTBAR_START && slotId <= HOTBAR_END; }
    public static boolean isOffhand(int slotId) { return slotId == OFFHAND; }

    /** Короткая метка категории для UI (просмотр раскладки). */
    public static String categoryLabel(int slotId) {
        if (isArmor(slotId))   return "Броня";
        if (isHotbar(slotId))  return "Хотбар";
        if (isOffhand(slotId)) return "Офхенд";
        return "Инвентарь";
    }
}