package com.lexoravisauls.client.inventorymanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Хранилище сохранённых раскладок. Файл — один JSON со списком раскладок,
 * лежит рядом с папкой игры (аналогично lexora_accounts.txt у аккаунтов).
 */
public final class LoadoutManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<InventoryLoadout>>() {}.getType();
    private static final String FILE_NAME = "lexora_inventory_loadouts.json";

    public static final List<InventoryLoadout> loadouts = new ArrayList<>();
    private static boolean loaded = false;

    private LoadoutManager() {}

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(FILE_NAME);
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = file();
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    List<InventoryLoadout> parsed = GSON.fromJson(r, LIST_TYPE);
                    loadouts.clear();
                    if (parsed != null) loadouts.addAll(parsed);
                }
            }
        } catch (Exception e) {
            System.err.println("Lexora: ошибка загрузки раскладок инвентаря: " + e.getMessage());
        }
    }

    public static void saveAll() {
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            GSON.toJson(loadouts, LIST_TYPE, w);
        } catch (IOException e) {
            System.err.println("Lexora: ошибка сохранения раскладок инвентаря: " + e.getMessage());
        }
    }

    public static Optional<InventoryLoadout> findByName(String name) {
        return loadouts.stream().filter(l -> l.name.equalsIgnoreCase(name)).findFirst();
    }

    public static void delete(InventoryLoadout loadout) {
        loadouts.remove(loadout);
        saveAll();
    }

    /** Заменяет раскладку с таким же именем (без учёта регистра) либо добавляет новую, и сохраняет на диск. */
    public static void upsert(InventoryLoadout loadout) {
        ensureLoaded();
        loadouts.removeIf(l -> l.name.equalsIgnoreCase(loadout.name));
        loadouts.add(loadout);
        saveAll();
    }

    /**
     * Снимает "слепок" текущего инвентаря игрока (броня+сумка+хотбар+офхенд) в новую раскладку.
     * Крафт-сетку (id 0-4) намеренно игнорируем.
     */
    public static InventoryLoadout captureCurrent(PlayerScreenHandler handler, String name) {
        InventoryLoadout loadout = new InventoryLoadout();
        loadout.name = name;
        loadout.createdAt = System.currentTimeMillis();

        for (int slotId = InventorySlotIds.MANAGED_START; slotId <= InventorySlotIds.MANAGED_END; slotId++) {
            ItemStack stack = handler.getSlot(slotId).getStack();
            if (stack == null || stack.isEmpty()) continue;
            LoadoutSlotData data = new LoadoutSlotData(
                    slotId,
                    ItemIdentity.idOf(stack),
                    ItemIdentity.customNameOf(stack),
                    stack.getCount(),
                    ItemIdentity.skinFingerprint(stack)
            );
            data.potionKey = ItemIdentity.potionFingerprint(stack);
            data.modelKey = ItemIdentity.customModelFingerprint(stack);
            data.loreKey = ItemIdentity.loreFingerprint(stack);
            data.setSkinData(
                    ItemIdentity.skinName(stack),
                    ItemIdentity.skinId(stack),
                    ItemIdentity.skinTextureValue(stack),
                    ItemIdentity.skinTextureSignature(stack)
            );
            loadout.slots.add(data);
        }
        return loadout;
    }
}