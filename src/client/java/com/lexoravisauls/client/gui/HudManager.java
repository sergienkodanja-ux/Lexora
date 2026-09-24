package com.lexoravisauls.client.gui;

import net.minecraft.item.Item;
import java.util.HashMap;
import java.util.Map;

public class HudManager {
    public static int infoX = 5, infoY = 5;
    public static int armorX = 10, armorY = 60;
    public static int potionX = 10, potionY = 100;
    public static int invX = 10, invY = 250;
    public static int keybindsX = 10, keybindsY = 10;
    public static int targetX = -1;
    public static int targetY = -1;
    public static int coolX = 200;
    public static int coolY = 300;
    public static int hearthX = -1;
    public static int hearthY = 50;
    public static int scoreboardX = -1;
    public static int scoreboardY = -1;
    public static int tntX = -1;
    public static int tntY = -1;

    public static final Map<Item, Float> lastProgressMap = new HashMap<>();
    public static final Map<Item, Integer> totalTicksMap = new HashMap<>();
}