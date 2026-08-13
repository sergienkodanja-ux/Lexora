package com.lexoravisauls.client.events;

import java.util.*;

public final class InfoHudLayout {

    public enum ItemType {
        LOGO,
        NAME,
        FPS,
        PING,
        COORDS,
        SPEED,
        SERVER
    }

    public static final class ItemState {
        public final ItemType type;
        public boolean enabled = true;

        public int row = 0;
        public int order = 0;

        public boolean detached = false;
        public float detachedX = 0f;
        public float detachedY = 0f;

        public boolean attachLeft = false;
        public boolean attachRight = false;

        public ItemState(ItemType type, int row, int order) {
            this.type = type;
            this.row = row;
            this.order = order;
        }
    }

    private static final EnumMap<ItemType, ItemState> ITEMS = new EnumMap<>(ItemType.class);

    static {
        resetDefaults();
    }

    private InfoHudLayout() {
    }

    public static void resetDefaults() {
        ITEMS.clear();

        ITEMS.put(ItemType.LOGO,   new ItemState(ItemType.LOGO,   0, 0));
        ITEMS.put(ItemType.NAME,   new ItemState(ItemType.NAME,   0, 1));
        ITEMS.put(ItemType.FPS,    new ItemState(ItemType.FPS,    0, 2));
        ITEMS.put(ItemType.PING,   new ItemState(ItemType.PING,   0, 3));
        ITEMS.put(ItemType.COORDS, new ItemState(ItemType.COORDS, 1, 0));
        ITEMS.put(ItemType.SPEED,  new ItemState(ItemType.SPEED,  1, 1));
        ITEMS.put(ItemType.SERVER, new ItemState(ItemType.SERVER, 1, 2));

        // Склейка по умолчанию:
        attach(ItemType.FPS, ItemType.PING);
    }

    public static Collection<ItemState> all() {
        return ITEMS.values();
    }

    public static ItemState get(ItemType type) {
        return ITEMS.get(type);
    }

    public static List<ItemState> getRow(int row) {
        List<ItemState> list = new ArrayList<>();
        for (ItemState state : ITEMS.values()) {
            if (state.enabled && !state.detached && state.row == row) {
                list.add(state);
            }
        }
        list.sort(Comparator.comparingInt(s -> s.order));
        return list;
    }

    public static void attach(ItemType left, ItemType right) {
        ItemState a = ITEMS.get(left);
        ItemState b = ITEMS.get(right);
        if (a == null || b == null) return;

        a.attachRight = true;
        b.attachLeft = true;
    }

    public static void detach(ItemType type) {
        ItemState s = ITEMS.get(type);
        if (s == null) return;
        s.attachLeft = false;
        s.attachRight = false;
    }

    public static void setEnabled(ItemType type, boolean enabled) {
        ItemState s = ITEMS.get(type);
        if (s != null) {
            s.enabled = enabled;
        }
    }

    public static void moveToRow(ItemType type, int row, int order) {
        ItemState s = ITEMS.get(type);
        if (s == null) return;

        s.row = row;
        s.order = order;
        s.detached = false;
    }
}