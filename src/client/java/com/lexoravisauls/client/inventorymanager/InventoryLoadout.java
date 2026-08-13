package com.lexoravisauls.client.inventorymanager;

import java.util.ArrayList;
import java.util.List;

/** Сохранённая раскладка инвентаря целиком: имя + список занятых слотов. */
public class InventoryLoadout {
    public String name = "";
    public long createdAt = System.currentTimeMillis();
    public List<LoadoutSlotData> slots = new ArrayList<>();
}