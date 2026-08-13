package com.lexoravisauls.client.inventorymanager;

/**
 * Один слот сохранённой раскладки: что и где должно лежать.
 * Простой POJO с публичными полями и пустым конструктором — специально, чтобы
 * Gson сериализовал/десериализовал его без сюрпризов (без record'ов и билдеров).
 */
public class LoadoutSlotData {

    /** Разделитель для matchKey — символ, который не может ввести игрок (не забивается в чат/наковальню). */
    private static final char KEY_SEP = '\u0001';

    public int handlerSlotId;
    public String itemId = "minecraft:air";
    public String customName; // null, если кастомного имени нет
    public String skinKey = ""; // отпечаток текстуры для СРАВНЕНИЯ (голов игрока с разными скинами без кастомного имени)
    public String potionKey = ""; // отпечаток содержимого зелья для СРАВНЕНИЯ (тип+цвет+кол-во кастомных эффектов)
    public String modelKey = ""; // отпечаток CustomModelData для СРАВНЕНИЯ (разные модели/ретекстуры на одном id)
    public String loreKey = ""; // отпечаток Lore для СРАВНЕНИЯ (текст-описание, часто "перки")
    public int count = 1;

    // Сырые данные профиля головы — для ВОССТАНОВЛЕНИЯ иконки с правильной текстурой
    // в превью (менеджер, призрак в инвентаре). skinKey сам по себе для этого не
    // годится — это уже свёрнутый отпечаток для сравнения, не сырые данные.
    public String skinName;             // профиль: name (может быть null)
    public String skinId;               // профиль: UUID как строка (может быть null)
    public String skinTextureValue;     // текстура: base64-значение (может быть null)
    public String skinTextureSignature; // текстура: подпись (может быть null)

    public LoadoutSlotData() {}

    public LoadoutSlotData(int handlerSlotId, String itemId, String customName, int count) {
        this(handlerSlotId, itemId, customName, count, "");
    }

    public LoadoutSlotData(int handlerSlotId, String itemId, String customName, int count, String skinKey) {
        this.handlerSlotId = handlerSlotId;
        this.itemId = itemId;
        this.customName = customName;
        this.count = count;
        this.skinKey = skinKey == null ? "" : skinKey;
    }

    /** Докладывает сырые данные профиля головы поверх уже созданного объекта (для восстановления иконки). */
    public void setSkinData(String skinName, String skinId, String textureValue, String textureSignature) {
        this.skinName = skinName;
        this.skinId = skinId;
        this.skinTextureValue = textureValue;
        this.skinTextureSignature = textureSignature;
    }

    /**
     * Ключ "типа" предмета для сравнения — id предмета + кастомное имя + отпечаток
     * скина (для голов игрока) + отпечаток содержимого зелья + отпечаток CustomModelData
     * + отпечаток Lore. Без этого разные декоративные головы/зелья/"перки" на мечах
     * без кастомных имён были бы неотличимы друг от друга.
     */
    public String matchKey() {
        return itemId + KEY_SEP + (customName == null ? "" : customName) + KEY_SEP + skinKey + KEY_SEP + potionKey
                + KEY_SEP + modelKey + KEY_SEP + loreKey;
    }
}