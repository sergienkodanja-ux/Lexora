package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.modules.ItemHighlighter;
import com.lexoravisauls.client.utils.ConfigManager;
import com.lexoravisauls.client.utils.DynamicIslandManager;
import com.lexoravisauls.client.utils.SoundUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LexoraGui extends Screen {

    private static final Identifier SMOOTH_CORNERS = Identifier.of("lexoravisauls", "textures/gui/smooth_corners.png");
    private static final Identifier LOGO = Identifier.of("lexoravisauls", "textures/gui/logo.png");
    private static final Identifier ICON_HUD = Identifier.of("lexoravisauls", "textures/gui/other.png");
    private static final Identifier ICON_VISUAL = Identifier.of("lexoravisauls", "textures/gui/visuals.png");
    private static final Identifier ICON_UTILS = Identifier.of("lexoravisauls", "textures/gui/quit.png");
    private static final Identifier ICON_CLOSE = Identifier.of("lexoravisauls", "textures/gui/error.png");
    private static final Identifier ICON_GEAR = Identifier.of("lexoravisauls", "textures/gui/keybinds.png");

    public static Map<String, Boolean> moduleStates = ClientData.moduleStates;
    public static Map<String, Float> numSettings = ClientData.numSettings;
    public static Map<String, String> modeSettings = ClientData.modeSettings;
    public static Map<String, float[]> colorSettings = ClientData.colorSettings;

    public static Map<String, Integer> moduleBinds = ClientData.moduleBinds;
    public static String bindingModule = null;

    public static List<String> savedConfigs = ClientData.savedConfigs;
    public static List<String> savedThemes = ClientData.savedThemes;
    public static String configInputText = "";
    public static boolean isTypingConfig = false;


    private float paletteOpenAnim = 0.0f; // 🔥 Переменная для анимации палитры

    public static Map<String, List<String>> categories = new HashMap<>();
    private final String[] mainCategories = {"HUD", "Visual", "Utils"};
    private final String[] settingsCategories = {"Color", "Config Manager"};
    private String currentCategory = "Visual";
    // expandedModule теперь будет отвечать за то, какое отдельное окно открыто
    private String expandedModule = null;
    private String draggingSlider = null;
    private String draggingColorPicker = null;
    private String draggingHuePicker = null;
    private String draggingPad = null;

    private boolean draggingItemSV = false;
    private boolean draggingItemHue = false;
    private float[] tempItemHSV = new float[]{0f, 1f, 1f};

    private final int guiWidth = 600;
    private final int guiHeight = 400;

    private float openAnimation = 0.0f;
    private static float scrollY = 0.0f;
    private static float targetScrollY = 0.0f;
    private static float maxScrollHeight = 0.0f;

    // --- НОВЫЕ ПЕРЕМЕННЫЕ ДЛЯ ВСПЛЫВАЮЩЕГО ОКНА ---
    private float settingsOpenAnim = 0.0f;
    private static float settingsScrollY = 0.0f;
    private static float targetSettingsScrollY = 0.0f;
    private static float maxSettingsScroll = 0.0f;

    // Переменные для мини-палитры
    private Item activeColorPickerItem = null;
    private int colorPickerX = 0;
    private int colorPickerY = 0;

    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private final Map<String, int[]> clickBounds = new HashMap<>();
    private final Map<String, int[]> sliderDimensions = new HashMap<>();
    private final Map<String, float[]> sliderMathBounds = new HashMap<>();
    private final Map<String, int[]> colorPickerBounds = new HashMap<>();
    private final Map<String, int[]> huePickerBounds = new HashMap<>();

    static {
        categories.put("HUD", Arrays.asList("Armor Status", "Potions", "Inventory HUD", "Cooldowns", "Watermark", "Keybinds", "Target HUD", "Info HUD", "Saturation HUD", "GPS", "Scoreboard HUD", "Lexora IRC","Emotes","Hit Indicator","TNT Detect", "Notifications"));
        categories.put("Visual", Arrays.asList("Crosshair", "Target ESP", "Animations", "Aspect Ratio", "View Model", "Hit Sounds", "Ft Helper", "HW Helper", "China Hat", "Particles", "Jump Circles", "Item Physics", "Hit Color", "Hit Wave", "Prediction", "Full Bright", "Block Overlay", "Hand Shaders", "Trails", "Custom Hitboxes", "Nimb", "World Customizer", "AuraParticles", "Motion Clones","Kill Effect","Motion Blur", "Taksa", "Atmosphere", "Virtual Desktop", "Kinetic Lyrics"));
        categories.put("Utils", Arrays.asList("Auto Sprint", "Item Swap", "Elytra Swap", "Fake Player", "Fast EXP", "Auto Eat", "Free Look", "Auto Respawn", "Totem Indicator", "Loot Notifier", "Auto Leave", "Shift Tap", "Fast Swap", "Item Scroller", "PvP Save", "Lock Slot", "Item Highlighter","Healing Helper", "Streamer Mode", "No Render", "Optimization", "Zoom", "Tape Mouse","Self Nametags","Armor Durability","Totem Sound"));
        categories.put("Color", Arrays.asList("Gradient Theme"));
        categories.put("Config Manager", new ArrayList<>());

        if (!moduleStates.containsKey("Crosshair")) moduleStates.put("Crosshair", false);
        if (!modeSettings.containsKey("Crosshair Type")) modeSettings.put("Crosshair Type", "Classic");
        if (!moduleStates.containsKey("Crosshair Outline")) moduleStates.put("Crosshair Outline", true);
        if (!moduleStates.containsKey("Crosshair Dynamic Gap")) moduleStates.put("Crosshair Dynamic Gap", true);
        if (!moduleStates.containsKey("Crosshair Highlight Target")) moduleStates.put("Crosshair Highlight Target", true);
        if (!moduleStates.containsKey("Crosshair Center Dot")) moduleStates.put("Crosshair Center Dot", false);
        if (!modeSettings.containsKey("Crosshair Color Mode")) modeSettings.put("Crosshair Color Mode", "Theme");
        if (!colorSettings.containsKey("Crosshair Custom Color")) colorSettings.put("Crosshair Custom Color", new float[]{0f, 1f, 1f});
        if (!numSettings.containsKey("Crosshair Thickness")) numSettings.put("Crosshair Thickness", 1.5f);
        if (!numSettings.containsKey("Crosshair Length")) numSettings.put("Crosshair Length", 4.0f);
        if (!numSettings.containsKey("Crosshair Gap")) numSettings.put("Crosshair Gap", 3.0f);
        if (!numSettings.containsKey("Crosshair Gap Increase")) numSettings.put("Crosshair Gap Increase", 6.0f);

        if (!moduleStates.containsKey("Trails")) moduleStates.put("Trails", false);
        if (!moduleStates.containsKey("Trail Show 1st Person")) moduleStates.put("Trail Show 1st Person", false);
        if (!modeSettings.containsKey("Trail Color Mode")) modeSettings.put("Trail Color Mode", "Theme");
        if (!colorSettings.containsKey("Trail Custom Color")) colorSettings.put("Trail Custom Color", new float[]{0f, 1f, 1f});
        if (!moduleStates.containsKey("Music Player")) moduleStates.put("Music Player", false);
        if (!moduleStates.containsKey("Target ESP")) moduleStates.put("Target ESP", false);
        if (!modeSettings.containsKey("Target ESP Mode")) modeSettings.put("Target ESP Mode", "Spirits");
        if (!numSettings.containsKey("ESP Speed")) numSettings.put("ESP Speed", 1.0f);
        if (!numSettings.containsKey("Cool Soul Rotate Speed")) numSettings.put("Cool Soul Rotate Speed", 1.0f);
        if (!numSettings.containsKey("Cool Soul Circles")) numSettings.put("Cool Soul Circles", 2.0f);
        if (!numSettings.containsKey("Cool Soul Radius")) numSettings.put("Cool Soul Radius", 1.15f);
        if (!numSettings.containsKey("Cool Soul Glow Width")) numSettings.put("Cool Soul Glow Width", 0.7f);
        if (!numSettings.containsKey("Cool Soul Speed Y")) numSettings.put("Cool Soul Speed Y", 1.0f);
        if (!numSettings.containsKey("Lightning Count")) numSettings.put("Lightning Count", 3.0f);
        if (!numSettings.containsKey("Lightning Width")) numSettings.put("Lightning Width", 1.0f);
        if (!moduleStates.containsKey("Target HUD")) moduleStates.put("Target HUD", true);
        if (!moduleStates.containsKey("Red On Damage")) moduleStates.put("Red On Damage", true);
        if (!moduleStates.containsKey("Only On Crit")) moduleStates.put("Only On Crit", false);
        if (!moduleStates.containsKey("Watermark")) moduleStates.put("Watermark", true);
        if (!moduleStates.containsKey("Gradient Theme")) moduleStates.put("Gradient Theme", true);
        if (!moduleStates.containsKey("Kinetic Lyrics")) moduleStates.put("Kinetic Lyrics", true);
        if (!moduleStates.containsKey("Lyrics In Island")) moduleStates.put("Lyrics In Island", true);
        if (!moduleStates.containsKey("Lyrics In 3D")) moduleStates.put("Lyrics In 3D", true);
        if (!modeSettings.containsKey("Lyrics Animation")) modeSettings.put("Lyrics Animation", "LyricFlow");
        if (!modeSettings.containsKey("Lyrics Color Mode")) modeSettings.put("Lyrics Color Mode", "Theme");
        if (!modeSettings.containsKey("Lyrics Split Mode")) modeSettings.put("Lyrics Split Mode", "SmartSplit");
        if (!numSettings.containsKey("Lyrics Max Lines")) numSettings.put("Lyrics Max Lines", 3.0f);
        if (!numSettings.containsKey("Lyrics Distance")) numSettings.put("Lyrics Distance", 5.0f);
        if (!numSettings.containsKey("Lyrics Scale")) numSettings.put("Lyrics Scale", 1.0f);
        if (!numSettings.containsKey("Lyrics Arc Spread")) numSettings.put("Lyrics Arc Spread", 70.0f);
        if (!numSettings.containsKey("Lyrics Float Height")) numSettings.put("Lyrics Float Height", 0.5f);
        if (!numSettings.containsKey("Lyrics Time Offset")) numSettings.put("Lyrics Time Offset", 0.0f);
        if (!moduleStates.containsKey("Lyrics Depth Occlusion")) moduleStates.put("Lyrics Depth Occlusion", true);

        if (!moduleStates.containsKey("Saturation HUD")) moduleStates.put("Saturation HUD", true);

        if (!moduleStates.containsKey("AuraParticles")) moduleStates.put("AuraParticles", false);

        if (!numSettings.containsKey("Aura Particles Count")) numSettings.put("Aura Particles Count", 24.0f);
        if (!numSettings.containsKey("Aura Particles Size")) numSettings.put("Aura Particles Size", 0.18f);
        if (!numSettings.containsKey("Aura Particles Radius")) numSettings.put("Aura Particles Radius", 1.45f);
        if (!numSettings.containsKey("Aura Particles Delay")) numSettings.put("Aura Particles Delay", 2.0f);

        if (!modeSettings.containsKey("Aura Particles Color Mode")) modeSettings.put("Aura Particles Color Mode", "Client");
        if (!colorSettings.containsKey("Aura Particles Custom Color")) colorSettings.put("Aura Particles Custom Color", new float[]{0f, 1f, 1f});

        if (!moduleStates.containsKey("China Hat Show 1st Person")) moduleStates.put("China Hat Show 1st Person", false);
        if (!modeSettings.containsKey("China Hat Color Mode")) modeSettings.put("China Hat Color Mode", "Theme");
        if (!colorSettings.containsKey("China Hat Custom Color")) colorSettings.put("China Hat Custom Color", new float[]{0f, 1f, 1f});

        if (!modeSettings.containsKey("Jump Circle Color Mode")) modeSettings.put("Jump Circle Color Mode", "Theme");
        if (!colorSettings.containsKey("Jump Circle Custom Color")) colorSettings.put("Jump Circle Custom Color", new float[]{0f, 1f, 1f});

        if (!numSettings.containsKey("Zoom Action")) { numSettings.put("Zoom Action", (float) GLFW.GLFW_KEY_C); ClientData.moduleBinds.putIfAbsent("Zoom Action", GLFW.GLFW_KEY_C); }

        if (!ClientData.moduleStates.containsKey("Markers")) ClientData.moduleStates.put("Markers", false);
        if (!ClientData.moduleStates.containsKey("Death Marker")) ClientData.moduleStates.put("Death Marker", true);
        if (!ClientData.moduleStates.containsKey("Manual Marker")) ClientData.moduleStates.put("Manual Marker", true);
        if (!ClientData.moduleStates.containsKey("Event Marker")) ClientData.moduleStates.put("Event Marker", true);
        if (!ClientData.moduleStates.containsKey("Marker Show Distance")) ClientData.moduleStates.put("Marker Show Distance", true);

        if (!ClientData.numSettings.containsKey("Marker Size")) ClientData.numSettings.put("Marker Size", 1.2f);

        if (!moduleStates.containsKey("Notifications")) moduleStates.put("Notifications", true);
        if (!moduleStates.containsKey("Notif Swap")) moduleStates.put("Notif Swap", true);
        if (!moduleStates.containsKey("Notif HP")) moduleStates.put("Notif HP", true);
        if (!moduleStates.containsKey("Notif Armor")) moduleStates.put("Notif Armor", true);
        if (!moduleStates.containsKey("Notif Potions")) moduleStates.put("Notif Potions", true);
        if (!moduleStates.containsKey("Notif Solid Bg")) moduleStates.put("Notif Solid Bg", false);
        if (!moduleStates.containsKey("Notif Icons")) moduleStates.put("Notif Icons", true);
        if (!moduleStates.containsKey("Notif Sound")) moduleStates.put("Notif Sound", true);
        if (!numSettings.containsKey("Notif Scale")) numSettings.put("Notif Scale", 1.0f);
        if (!modeSettings.containsKey("Notif Sound Mode")) modeSettings.put("Notif Sound Mode", "Звук 1");

        if (!moduleStates.containsKey("Hearth Hud")) moduleStates.put("Hearth Hud", false);
        if (!moduleStates.containsKey("Hearth Hud Solid")) moduleStates.put("Hearth Hud Solid", false);
        if (!numSettings.containsKey("Hearth Hud Scale")) numSettings.put("Hearth Hud Scale", 1.0f);

        if (!moduleStates.containsKey("Auto Leave")) moduleStates.put("Auto Leave", false);
        if (!modeSettings.containsKey("Leave Mode")) modeSettings.put("Leave Mode", "Выход");
        if (!numSettings.containsKey("Near Cooldown")) numSettings.put("Near Cooldown", 180.0f);

        if (!moduleStates.containsKey("Auto Respawn")) moduleStates.put("Auto Respawn", false);
        if (!moduleStates.containsKey("Totem Indicator")) moduleStates.put("Totem Indicator", false);

        if (!moduleStates.containsKey("Streamer Mode")) moduleStates.put("Streamer Mode", false);
        if (!moduleStates.containsKey("Hide Name")) moduleStates.put("Hide Name", true);
        if (!moduleStates.containsKey("Hide Coords")) moduleStates.put("Hide Coords", true);

                if (!moduleStates.containsKey("Optimization")) moduleStates.put("Optimization", false);
                        if (!moduleStates.containsKey("Opt No Clouds")) moduleStates.put("Opt No Clouds", true);
        if (!moduleStates.containsKey("Opt No Fog")) moduleStates.put("Opt No Fog", true);
        if (!moduleStates.containsKey("Opt Fast Items")) moduleStates.put("Opt Fast Items", true);
        if (!numSettings.containsKey("Opt Items Dist")) numSettings.put("Opt Items Dist", 20.0f);
        if (!moduleStates.containsKey("Opt Block Culling")) moduleStates.put("Opt Block Culling", true);
        if (!numSettings.containsKey("Opt Block Dist")) numSettings.put("Opt Block Dist", 48.0f);
        if (!moduleStates.containsKey("Opt Fast Lighting")) moduleStates.put("Opt Fast Lighting", true);
        if (!moduleStates.containsKey("Opt Fast HUD")) moduleStates.put("Opt Fast HUD", true);
        if (!moduleStates.containsKey("Opt All Particles")) moduleStates.put("Opt All Particles", false);
        if (!moduleStates.containsKey("Opt Rain Particles")) moduleStates.put("Opt Rain Particles", true);
        if (!moduleStates.containsKey("Opt Potion Particles")) moduleStates.put("Opt Potion Particles", false);
        if (!moduleStates.containsKey("Opt Explosion Particles")) moduleStates.put("Opt Explosion Particles", false);
        if (!moduleStates.containsKey("Opt Smoke Particles")) moduleStates.put("Opt Smoke Particles", false);
        if (!moduleStates.containsKey("Opt Fire Particles")) moduleStates.put("Opt Fire Particles", false);
        if (!moduleStates.containsKey("Opt Totem Particles")) moduleStates.put("Opt Totem Particles", false);
        if (!moduleStates.containsKey("Opt No Armor")) moduleStates.put("Opt No Armor", false);
        if (!moduleStates.containsKey("Opt No Nametags")) moduleStates.put("Opt No Nametags", false);
        if (!moduleStates.containsKey("Opt No Shadows")) moduleStates.put("Opt No Shadows", true);
        if (!moduleStates.containsKey("Opt No Glint")) moduleStates.put("Opt No Glint", false);
        if (!moduleStates.containsKey("Opt No Armor Stands")) moduleStates.put("Opt No Armor Stands", false);
        if (!moduleStates.containsKey("Opt No Beacons")) moduleStates.put("Opt No Beacons", false);
        if (!moduleStates.containsKey("Opt Fast Weather")) moduleStates.put("Opt Fast Weather", true);
        if (!moduleStates.containsKey("Opt Entity Culling")) moduleStates.put("Opt Entity Culling", false);
        if (!numSettings.containsKey("Opt Entity Dist")) numSettings.put("Opt Entity Dist", 48.0f);
        if (!moduleStates.containsKey("No Render")) moduleStates.put("No Render", false);
        if (!moduleStates.containsKey("No Fire")) moduleStates.put("No Fire", true);
        if (!moduleStates.containsKey("No Portal")) moduleStates.put("No Portal", true);
        if (!moduleStates.containsKey("No Totem Anim")) moduleStates.put("No Totem Anim", true);
        if (!moduleStates.containsKey("No Weather")) moduleStates.put("No Weather", true);
        if (!moduleStates.containsKey("No Grass")) moduleStates.put("No Grass", false);
        if (!moduleStates.containsKey("No Bad Effects")) moduleStates.put("No Bad Effects", true);
        if (!moduleStates.containsKey("No Hurt Cam")) moduleStates.put("No Hurt Cam", true);

        if (!moduleStates.containsKey("Zoom")) moduleStates.put("Zoom", true);
        if (!modeSettings.containsKey("Zoom Mode")) modeSettings.put("Zoom Mode", "Hold");
        if (!numSettings.containsKey("Zoom Value")) numSettings.put("Zoom Value", 4.0f);
        if (!numSettings.containsKey("Zoom Smooth")) numSettings.put("Zoom Smooth", 0.18f);
        if (!numSettings.containsKey("Zoom Scroll Step")) numSettings.put("Zoom Scroll Step", 0.35f);
        // Zoom module itself has no default bind; inner Zoom Action handles key C

        if (!moduleStates.containsKey("Healing Helper")) moduleStates.put("Healing Helper", false);
        if (!moduleStates.containsKey("HH Pulsation")) moduleStates.put("HH Pulsation", true);
        if (!numSettings.containsKey("HH Alpha")) numSettings.put("HH Alpha", 100.0f);
        if (!numSettings.containsKey("Gapple HP")) numSettings.put("Gapple HP", 14.0f);
        if (!numSettings.containsKey("E-Gapple HP")) numSettings.put("E-Gapple HP", 8.0f);
        if (!numSettings.containsKey("Potion HP")) numSettings.put("Potion HP", 10.0f);

        if (!moduleStates.containsKey("Shift Tap")) moduleStates.put("Shift Tap", false);
        if (!modeSettings.containsKey("Shift Mode")) modeSettings.put("Shift Mode", "Все удары");

        if (!moduleStates.containsKey("Loot Notifier")) moduleStates.put("Loot Notifier", true);

        if (!moduleStates.containsKey("Info HUD")) moduleStates.put("Info HUD", true);
        if (!numSettings.containsKey("Info HUD Scale")) numSettings.put("Info HUD Scale", 1.0f);

        if (!moduleStates.containsKey("PvP Save")) moduleStates.put("PvP Save", false);
        if (!numSettings.containsKey("PvP Time")) numSettings.put("PvP Time", 15.0f);
        if (!moduleStates.containsKey("Lock Slot")) moduleStates.put("Lock Slot", false);
        for(int i = 0; i < 9; i++) if (!moduleStates.containsKey("LockSlot_" + i)) moduleStates.put("LockSlot_" + i, false);

        if (!moduleStates.containsKey("Auto Sprint")) moduleStates.put("Auto Sprint", false);
        if (!moduleStates.containsKey("Aspect Ratio")) moduleStates.put("Aspect Ratio", false);
        if (!moduleStates.containsKey("View Model")) moduleStates.put("View Model", false);

        if (!moduleStates.containsKey("Hit Sounds")) moduleStates.put("Hit Sounds", false);
        if (!moduleStates.containsKey("Hit Sound Only Crit")) moduleStates.put("Hit Sound Only Crit", false);

        if (!moduleStates.containsKey("Fast Swap")) moduleStates.put("Fast Swap", false);
        if (!moduleStates.containsKey("Item Scroller")) moduleStates.put("Item Scroller", false);
        if (!numSettings.containsKey("Scroller Speed")) numSettings.put("Scroller Speed", 2.0f);
        if (!moduleStates.containsKey("Show Hotbar Binds")) moduleStates.put("Show Hotbar Binds", true);

        if (!moduleStates.containsKey("Ft Helper")) moduleStates.put("Ft Helper", false);
        if (!moduleStates.containsKey("China Hat")) moduleStates.put("China Hat", false);
        if (!moduleStates.containsKey("Fake Player")) moduleStates.put("Fake Player", false);
        if (!moduleStates.containsKey("Item Physics")) moduleStates.put("Item Physics", false);
        if (!moduleStates.containsKey("Jump Circles")) moduleStates.put("Jump Circles", false);
        if (!moduleStates.containsKey("Fast EXP")) moduleStates.put("Fast EXP", false);

        if (!moduleStates.containsKey("Auto Eat")) moduleStates.put("Auto Eat", false);
        if (!numSettings.containsKey("Eat Threshold")) numSettings.put("Eat Threshold", 14.0f);

        if (!moduleStates.containsKey("Free Look")) moduleStates.put("Free Look", false);
        if (!numSettings.containsKey("Free Look Action")) numSettings.put("Free Look Action", -1f);

        if (!moduleStates.containsKey("Hit Color")) moduleStates.put("Hit Color", false);
        if (!moduleStates.containsKey("Prediction")) moduleStates.put("Prediction", false);
        if (!moduleStates.containsKey("Full Bright")) moduleStates.put("Full Bright", false);

        if (!moduleStates.containsKey("Custom Hitboxes")) moduleStates.put("Custom Hitboxes", false);
        if (!modeSettings.containsKey("Hitbox Style")) modeSettings.put("Hitbox Style", "Solid");
        if (!modeSettings.containsKey("Hitbox Color Mode")) modeSettings.put("Hitbox Color Mode", "Custom");
        if (!colorSettings.containsKey("Hitbox Color")) colorSettings.put("Hitbox Color", new float[]{280f/360f, 1f, 1f});
        if (!numSettings.containsKey("Hitbox Alpha")) numSettings.put("Hitbox Alpha", 0.45f);
        if (!numSettings.containsKey("Hitbox Line Width")) numSettings.put("Hitbox Line Width", 2.0f);
        if (!numSettings.containsKey("HB Range")) numSettings.put("HB Range", 32.0f);
        if (!moduleStates.containsKey("HB Only Target")) moduleStates.put("HB Only Target", false);
        if (!moduleStates.containsKey("HB Hurt Pulse")) moduleStates.put("HB Hurt Pulse", true);
        if (!moduleStates.containsKey("HB Players")) moduleStates.put("HB Players", true);
        if (!moduleStates.containsKey("HB Mobs")) moduleStates.put("HB Mobs", false);
        if (!moduleStates.containsKey("HB Items")) moduleStates.put("HB Items", false);

        if (!moduleStates.containsKey("Hit Wave")) moduleStates.put("Hit Wave", false);
        if (!moduleStates.containsKey("Wave Only Crit")) moduleStates.put("Wave Only Crit", true);
        if (!numSettings.containsKey("Wave Size")) numSettings.put("Wave Size", 15.0f);
        if (!numSettings.containsKey("Wave Speed")) numSettings.put("Wave Speed", 20.0f);

        if (!modeSettings.containsKey("Hit Color Mode")) modeSettings.put("Hit Color Mode", "Theme");
        if (!modeSettings.containsKey("Hit Target")) modeSettings.put("Hit Target", "All");
        if (!colorSettings.containsKey("Hit Custom Color")) colorSettings.put("Hit Custom Color", new float[]{0f, 1f, 1f});
        if (!numSettings.containsKey("Predict Ticks")) numSettings.put("Predict Ticks", 10.0f);

        if (!moduleStates.containsKey("Nimb")) moduleStates.put("Nimb", false);
        if (!moduleStates.containsKey("Nimb Show 1st Person")) moduleStates.put("Nimb Show 1st Person", false);
        if (!modeSettings.containsKey("Nimb Color Mode")) modeSettings.put("Nimb Color Mode", "Theme");
        if (!colorSettings.containsKey("Nimb Custom Color")) colorSettings.put("Nimb Custom Color", new float[]{0f, 1f, 1f});
        if (!numSettings.containsKey("Nimb Radius")) numSettings.put("Nimb Radius", 0.4f);

        if (!modeSettings.containsKey("VM Anim")) modeSettings.put("VM Anim", "Standard");
        if (!numSettings.containsKey("Сила наклона")) numSettings.put("Сила наклона", 20.0f);
        if (!numSettings.containsKey("Поворот")) numSettings.put("Поворот", 0.0f);
        if (!moduleStates.containsKey("Item360")) moduleStates.put("Item360", false);
        if (!numSettings.containsKey("VM Speed")) numSettings.put("VM Speed", 1.0f);

        if (!moduleStates.containsKey("FT Дезка")) moduleStates.put("FT Дезка", true);
        if (!moduleStates.containsKey("FT Трапка")) moduleStates.put("FT Трапка", true);
        if (!moduleStates.containsKey("FT Явка")) moduleStates.put("FT Явка", true);
        if (!moduleStates.containsKey("FT Пласт")) moduleStates.put("FT Пласт", true);
        if (!moduleStates.containsKey("FT Огненный Шар")) moduleStates.put("FT Огненный Шар", true);
        if (!moduleStates.containsKey("FT Снежок")) moduleStates.put("FT Снежок", true);
        if (!moduleStates.containsKey("FT Божья Аура")) moduleStates.put("FT Божья Аура", true);

        if (!modeSettings.containsKey("FT Трапка Скин")) modeSettings.put("FT Трапка Скин", "Обычный");
        if (!modeSettings.containsKey("FT Пласт Скин")) modeSettings.put("FT Пласт Скин", "Обычный");
        if (!moduleStates.containsKey("FT Таймер Трапки")) moduleStates.put("FT Таймер Трапки", true);
        if (!moduleStates.containsKey("FT Таймер Пласта")) moduleStates.put("FT Таймер Пласта", true);

        if (!moduleStates.containsKey("HW Helper")) moduleStates.put("HW Helper", false);
        if (!moduleStates.containsKey("HW Стан")) moduleStates.put("HW Стан", true);
        if (!moduleStates.containsKey("HW Трапка")) moduleStates.put("HW Трапка", true);
        if (!moduleStates.containsKey("HW Вскрывная")) moduleStates.put("HW Вскрывная", true);
        if (!moduleStates.containsKey("HW Таймер Стана")) moduleStates.put("HW Таймер Стана", true);
        if (!moduleStates.containsKey("HW Таймер Трапки")) moduleStates.put("HW Таймер Трапки", true);
        if (!moduleStates.containsKey("HW Таймер Вскрывной")) moduleStates.put("HW Таймер Вскрывной", true);
        if (!moduleStates.containsKey("HW Подсветка Области")) moduleStates.put("HW Подсветка Области", true);
        if (!moduleStates.containsKey("HW Реакция на Цель")) moduleStates.put("HW Реакция на Цель", true);

        if (!moduleStates.containsKey("Notifications")) moduleStates.put("Notifications", true);
        if (!moduleStates.containsKey("Notifications Blur")) moduleStates.put("Notifications Blur", true);
        if (!numSettings.containsKey("Notifications Scale")) numSettings.put("Notifications Scale", 1.0f);
        if (!moduleStates.containsKey("Notif Icons")) moduleStates.put("Notif Icons", true);
        if (!moduleStates.containsKey("Notif Sound")) moduleStates.put("Notif Sound", true);
        if (!modeSettings.containsKey("Notif Sound Mode")) modeSettings.put("Notif Sound Mode", "Звук 1");

        if (!moduleStates.containsKey("Particles")) moduleStates.put("Particles", false);
        if (!moduleStates.containsKey("Part. Ambient")) moduleStates.put("Part. Ambient", true);
        if (!moduleStates.containsKey("Part. Walk")) moduleStates.put("Part. Walk", true);
        if (!moduleStates.containsKey("Part. Hit")) moduleStates.put("Part. Hit", true);
        if (!moduleStates.containsKey("Part. Crit")) moduleStates.put("Part. Crit", true);
        if (!moduleStates.containsKey("Part. Totem")) moduleStates.put("Part. Totem", true);
        if (!moduleStates.containsKey("Part. Projectiles")) moduleStates.put("Part. Projectiles", true);
        if (!moduleStates.containsKey("Disable Vanilla Totem")) moduleStates.put("Disable Vanilla Totem", true);

        if (!moduleStates.containsKey("Part. Attack")) moduleStates.put("Part. Attack", true);
        if (!moduleStates.containsKey("Part. Move")) moduleStates.put("Part. Move", false);
        if (!moduleStates.containsKey("Part. Throw")) moduleStates.put("Part. Throw", true);
        if (!moduleStates.containsKey("Part. Idle")) moduleStates.put("Part. Idle", false);
        if (!moduleStates.containsKey("Part. Glow")) moduleStates.put("Part. Glow", true);
        if (!moduleStates.containsKey("Part. Rotation")) moduleStates.put("Part. Rotation", false);
        if (!moduleStates.containsKey("Part. Through Walls")) moduleStates.put("Part. Through Walls", false);
        if (!moduleStates.containsKey("Part. Strong Y")) moduleStates.put("Part. Strong Y", false);

        if (!modeSettings.containsKey("Part. Texture")) modeSettings.put("Part. Texture", "Bloom");

        if (!numSettings.containsKey("Part. Speed")) numSettings.put("Part. Speed", 1.5f);
        if (!numSettings.containsKey("Part. Size")) numSettings.put("Part. Size", 0.3f);
        if (!numSettings.containsKey("Part. Attack Count")) numSettings.put("Part. Attack Count", 30.0f);
        if (!numSettings.containsKey("Part. Totem Count")) numSettings.put("Part. Totem Count", 8.0f);
        if (!numSettings.containsKey("Part. Move Count")) numSettings.put("Part. Move Count", 2.0f);
        if (!numSettings.containsKey("Part. Throw Count")) numSettings.put("Part. Throw Count", 6.0f);
        if (!numSettings.containsKey("Part. Idle Count")) numSettings.put("Part. Idle Count", 5.0f);
        if (!numSettings.containsKey("Part. Idle Range")) numSettings.put("Part. Idle Range", 16.0f);

        if (!numSettings.containsKey("Amb Chance")) numSettings.put("Amb Chance", 8.0f);
        if (!numSettings.containsKey("Amb Size")) numSettings.put("Amb Size", 1.0f);
        if (!numSettings.containsKey("Amb Life")) numSettings.put("Amb Life", 80.0f);

        if (!numSettings.containsKey("Walk Count")) numSettings.put("Walk Count", 1.0f);
        if (!numSettings.containsKey("Walk Size")) numSettings.put("Walk Size", 0.6f);
        if (!numSettings.containsKey("Walk Life")) numSettings.put("Walk Life", 20.0f);

        if (!numSettings.containsKey("Hit Size")) numSettings.put("Hit Size", 1.0f);
        if (!numSettings.containsKey("Hit Count")) numSettings.put("Hit Count", 8.0f);
        if (!numSettings.containsKey("Hit Life")) numSettings.put("Hit Life", 40.0f);

        if (!numSettings.containsKey("Crit Size")) numSettings.put("Crit Size", 1.3f);
        if (!numSettings.containsKey("Crit Count")) numSettings.put("Crit Count", 12.0f);
        if (!numSettings.containsKey("Crit Life")) numSettings.put("Crit Life", 50.0f);

        if (!moduleStates.containsKey("World Customizer")) moduleStates.put("World Customizer", false);
        if (!moduleStates.containsKey("Sky Customizer")) moduleStates.put("Sky Customizer", true);
        if (!modeSettings.containsKey("Sky Color Mode")) modeSettings.put("Sky Color Mode", "Theme");
        if (!colorSettings.containsKey("Custom Sky Color")) colorSettings.put("Custom Sky Color", new float[]{0.6f, 1f, 1f});

        if (!moduleStates.containsKey("Custom Time")) moduleStates.put("Custom Time", false);
        if (!numSettings.containsKey("World Time")) numSettings.put("World Time", 18000f);
        if (!modeSettings.containsKey("Weather Mode")) modeSettings.put("Weather Mode", "Clear");

        if (!moduleStates.containsKey("Fog Customizer")) moduleStates.put("Fog Customizer", false);
        if (!numSettings.containsKey("Fog Distance")) numSettings.put("Fog Distance", 0.5f);
        if (!modeSettings.containsKey("Fog Color Mode")) modeSettings.put("Fog Color Mode", "Theme");
        if (!colorSettings.containsKey("Custom Fog Color")) colorSettings.put("Custom Fog Color", new float[]{0.8f, 1f, 1f});

        if (!numSettings.containsKey("Proj Count")) numSettings.put("Proj Count", 1.0f);
        if (!numSettings.containsKey("Proj Size")) numSettings.put("Proj Size", 0.6f);
        if (!numSettings.containsKey("Proj Life")) numSettings.put("Proj Life", 30.0f);

        if (!numSettings.containsKey("Totem Size")) numSettings.put("Totem Size", 1.5f);
        if (!numSettings.containsKey("Totem Count")) numSettings.put("Totem Count", 50.0f);
        if (!numSettings.containsKey("Totem Life")) numSettings.put("Totem Life", 80.0f);

        if (!numSettings.containsKey("Jump Size")) numSettings.put("Jump Size", 1.8f);
        if (!numSettings.containsKey("Jump Speed")) numSettings.put("Jump Speed", 1.2f);

        if (!moduleStates.containsKey("Anim Hotbar")) moduleStates.put("Anim Hotbar", true);
        if (!moduleStates.containsKey("Anim Chat")) moduleStates.put("Anim Chat", true);
        if (!moduleStates.containsKey("Anim Tab")) moduleStates.put("Anim Tab", true);

        if (!colorSettings.containsKey("Theme Color 1")) colorSettings.put("Theme Color 1", new float[]{200f / 360f, 1f, 1f});
        if (!colorSettings.containsKey("Theme Color 2")) colorSettings.put("Theme Color 2", new float[]{280f / 360f, 1f, 1f});

        if (!numSettings.containsKey("ESP Speed")) numSettings.put("ESP Speed", 1.0f);
        if (!numSettings.containsKey("Trail Length")) numSettings.put("Trail Length", 20.0f);
        if (!numSettings.containsKey("Spirits Count")) numSettings.put("Spirits Count", 3.0f);
        if (!numSettings.containsKey("Rhombus Size")) numSettings.put("Rhombus Size", 1.0f);

        if (!numSettings.containsKey("Potions Scale")) numSettings.put("Potions Scale", 1.0f);
        if (!numSettings.containsKey("Keybinds Scale")) numSettings.put("Keybinds Scale", 1.0f);
        if (!numSettings.containsKey("Armor Status Scale")) numSettings.put("Armor Status Scale", 1.0f);
        if (!numSettings.containsKey("Inventory HUD Scale")) numSettings.put("Inventory HUD Scale", 1.0f);
        if (!numSettings.containsKey("Cooldowns Scale")) numSettings.put("Cooldowns Scale", 1.0f);
        if (!numSettings.containsKey("Target HUD Scale")) numSettings.put("Target HUD Scale", 1.0f);
        if (!numSettings.containsKey("Watermark Scale")) numSettings.put("Watermark Scale", 1.0f);
        if (!numSettings.containsKey("Item Swap Action")) numSettings.put("Item Swap Action", -1f);
        if (!modeSettings.containsKey("Swap Mode")) modeSettings.put("Swap Mode", "Двойной");
        if (!modeSettings.containsKey("Item Swap Wheel 0")) modeSettings.put("Item Swap Wheel 0", "Тотем");
        if (!modeSettings.containsKey("Item Swap Wheel 1")) modeSettings.put("Item Swap Wheel 1", "Шар");
        if (!modeSettings.containsKey("Item Swap Wheel 2")) modeSettings.put("Item Swap Wheel 2", "Щит");

        if (!moduleStates.containsKey("Motion Clones")) moduleStates.put("Motion Clones", false);

        if (!numSettings.containsKey("Clone Count")) numSettings.put("Clone Count", 6.0f);
        if (!numSettings.containsKey("Clone Delay")) numSettings.put("Clone Delay", 2.0f);
        if (!numSettings.containsKey("Clone Life")) numSettings.put("Clone Life", 14.0f);
        if (!numSettings.containsKey("Clone Scale")) numSettings.put("Clone Scale", 1.0f);
        if (!numSettings.containsKey("Clone Alpha")) numSettings.put("Clone Alpha", 0.85f);

        if (!modeSettings.containsKey("Clone Color Mode")) modeSettings.put("Clone Color Mode", "Client");
        if (!colorSettings.containsKey("Clone Custom Color")) colorSettings.put("Clone Custom Color", new float[]{0.33f, 0.85f, 1.0f});

        if (!moduleStates.containsKey("Elytra Swap")) moduleStates.put("Elytra Swap", false);
        if (!numSettings.containsKey("Elytra Swap Action")) numSettings.put("Elytra Swap Action", -1f);

        if (!numSettings.containsKey("Aspect Ratio Val")) numSettings.put("Aspect Ratio Val", 1.33f);

        if (!numSettings.containsKey("Right Hand X")) numSettings.put("Right Hand X", 0.0f);
        if (!numSettings.containsKey("Right Hand Y")) numSettings.put("Right Hand Y", 0.0f);
        if (!numSettings.containsKey("Right Hand Z")) numSettings.put("Right Hand Z", 0.0f);
        if (!numSettings.containsKey("Right Hand Scale")) numSettings.put("Right Hand Scale", 1.0f);

        if (!numSettings.containsKey("Left Hand X")) numSettings.put("Left Hand X", 0.0f);
        if (!numSettings.containsKey("Left Hand Y")) numSettings.put("Left Hand Y", 0.0f);
        if (!numSettings.containsKey("Left Hand Z")) numSettings.put("Left Hand Z", 0.0f);
        if (!numSettings.containsKey("Left Hand Scale")) numSettings.put("Left Hand Scale", 1.0f);

        if (!numSettings.containsKey("Hit Sound Volume")) numSettings.put("Hit Sound Volume", 50.0f);

        if (!modeSettings.containsKey("Target ESP Mode")) modeSettings.put("Target ESP Mode", "Spirits");
        if (!modeSettings.containsKey("Swap From")) modeSettings.put("Swap From", "Тотем");
        if (!modeSettings.containsKey("Swap To")) modeSettings.put("Swap To", "Шар");
        if (!modeSettings.containsKey("Ratio Mode")) modeSettings.put("Ratio Mode", "Default");
        if (!modeSettings.containsKey("Hit Sound Mode")) modeSettings.put("Hit Sound Mode", "Crime");

        if (!moduleStates.containsKey("Block Overlay")) moduleStates.put("Block Overlay", false);
        if (!modeSettings.containsKey("Overlay Mode")) modeSettings.put("Overlay Mode", "Web");
        if (!colorSettings.containsKey("Overlay Color")) colorSettings.put("Overlay Color", new float[]{280f / 360f, 1f, 1f});

        if (!moduleStates.containsKey("Hand Shaders")) moduleStates.put("Hand Shaders", false);
        if (!modeSettings.containsKey("Hand Mode")) modeSettings.put("Hand Mode", "Nebula");
        if (!numSettings.containsKey("Hand Glow %")) numSettings.put("Hand Glow %", 80.0f);
        if (!numSettings.containsKey("Hand Shader Speed")) numSettings.put("Hand Shader Speed", 1.0f);
        if (!moduleStates.containsKey("Hand Shader Only")) moduleStates.put("Hand Shader Only", false);
        if (!modeSettings.containsKey("Hand Color Mode")) modeSettings.put("Hand Color Mode", "Theme");

        if (!moduleStates.containsKey("Atmosphere")) moduleStates.put("Atmosphere", false);
        if (!moduleStates.containsKey("WeatherFX")) moduleStates.put("WeatherFX", false);
        if (!modeSettings.containsKey("Weather Visual Mode")) modeSettings.put("Weather Visual Mode", "Rain");
        if (!moduleStates.containsKey("Weather Rain Only")) moduleStates.put("Weather Rain Only", false);
        if (!modeSettings.containsKey("Rain Preset")) modeSettings.put("Rain Preset", "Rain");
        if (!numSettings.containsKey("Rain Density")) numSettings.put("Rain Density", 1.0f);
        if (!numSettings.containsKey("Rain Radius")) numSettings.put("Rain Radius", 26.0f);
        if (!numSettings.containsKey("Rain Altitude")) numSettings.put("Rain Altitude", 16.0f);
        if (!numSettings.containsKey("Rain Drop Size")) numSettings.put("Rain Drop Size", 1.0f);
        if (!numSettings.containsKey("Rain Fall Speed")) numSettings.put("Rain Fall Speed", 1.0f);
        if (!numSettings.containsKey("Rain Wind")) numSettings.put("Rain Wind", 1.0f);
        if (!numSettings.containsKey("Rain Opacity")) numSettings.put("Rain Opacity", 1.0f);
        if (!moduleStates.containsKey("Rain Splashes")) moduleStates.put("Rain Splashes", true);
        if (!moduleStates.containsKey("Rain Droplets")) moduleStates.put("Rain Droplets", true);
        if (!moduleStates.containsKey("Rain Mist")) moduleStates.put("Rain Mist", true);
        if (!moduleStates.containsKey("Rain Lightning")) moduleStates.put("Rain Lightning", false);
        if (!moduleStates.containsKey("Rain Sky Check")) moduleStates.put("Rain Sky Check", true);
        if (!modeSettings.containsKey("Rain Color Mode")) modeSettings.put("Rain Color Mode", "Realistic");
        if (!colorSettings.containsKey("Rain Custom Color")) colorSettings.put("Rain Custom Color", new float[]{0.6f, 0.75f, 0.9f});
        if (!numSettings.containsKey("Wet Strength")) numSettings.put("Wet Strength", 0.85f);
        if (!numSettings.containsKey("Wet Darkening")) numSettings.put("Wet Darkening", 0.30f);
        if (!modeSettings.containsKey("Wet Quality")) modeSettings.put("Wet Quality", "Balanced");
        if (!moduleStates.containsKey("Wet Ripples")) moduleStates.put("Wet Ripples", false);
        if (!numSettings.containsKey("Ripple Speed")) numSettings.put("Ripple Speed", 1.0f);

        if (!numSettings.containsKey("Winter Density")) numSettings.put("Winter Density", 1.25f);
        if (!numSettings.containsKey("Winter Blizzard")) numSettings.put("Winter Blizzard", 0.0f);
        if (!moduleStates.containsKey("Winter Ground Snow")) moduleStates.put("Winter Ground Snow", true);
        if (!moduleStates.containsKey("Winter Wall Snow")) moduleStates.put("Winter Wall Snow", true);
        if (!moduleStates.containsKey("Winter Snowfall")) moduleStates.put("Winter Snowfall", true);
        if (!moduleStates.containsKey("Winter Footprints")) moduleStates.put("Winter Footprints", true);
        if (!moduleStates.containsKey("Winter Frost")) moduleStates.put("Winter Frost", true);
        if (!modeSettings.containsKey("Winter Color Mode")) modeSettings.put("Winter Color Mode", "Realistic");
        if (!colorSettings.containsKey("Winter Custom Color")) colorSettings.put("Winter Custom Color", new float[]{0.58f, 0.15f, 0.98f});
    }

    public LexoraGui() {
        super(Text.literal("Lexora Visuals Menu"));
        try { ConfigManager.updateConfigList(); } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        super.init();
        SoundUtil.playCustomSound("gui_open", 1.0f);
        scrollY = 0.0f;
        targetScrollY = 0.0f;
        settingsScrollY = 0.0f;
        targetSettingsScrollY = 0.0f;
    }
    public static int getGuiThemeColor() {
        float time = (System.currentTimeMillis() % 3000L) / 3000.0f;
        float blend = (float) (Math.sin(time * Math.PI * 2) * 0.5 + 0.5);
        float hue = (270f + 20f * blend) / 360f;
        return Color.HSBtoRGB(hue, 0.7f, 1.0f) & 0xFFFFFF;
    }

    public static int getThemeColor(float offset) {
        boolean gradient = com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault("Gradient Theme",
                moduleStates.getOrDefault("Gradient Theme", true));
        float[] hsv1 = com.lexoravisauls.client.core.ClientData.colorSettings.getOrDefault("Theme Color 1",
                colorSettings.getOrDefault("Theme Color 1", new float[]{200f / 360f, 1f, 1f}));

        int c1 = Color.HSBtoRGB(hsv1[0], hsv1[1], hsv1[2]) & 0xFFFFFF;
        if (!gradient) return c1;

        float[] hsv2 = com.lexoravisauls.client.core.ClientData.colorSettings.getOrDefault("Theme Color 2",
                colorSettings.getOrDefault("Theme Color 2", new float[]{280f / 360f, 1f, 1f}));
        int c2 = Color.HSBtoRGB(hsv2[0], hsv2[1], hsv2[2]) & 0xFFFFFF;

        float time = (System.currentTimeMillis() % 3000L) / 3000.0f;
        float mixed = (time + offset) % 1.0f;
        float blend = (float) (Math.sin(mixed * Math.PI * 2) * 0.5 + 0.5);

        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * blend);
        int g = (int) (g1 + (g2 - g1) * blend);
        int b = (int) (b1 + (b2 - b1) * blend);

        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

private String t(String key) {
        switch (key) {
            case "Target ESP Mode": return "Режим ЕСП";
            case "Red On Damage": return "Красный при Уроне";
            case "Only On Crit": return "Только при Крите";
            case "ESP Speed": return "Скорость ЕСП";
            case "Spirits Count": return "Кол-во Духов";
            case "Trail Length": return "Длина Следа";
            case "Rhombus Size": return "Размер Ромба";

            case "Anim Hotbar": return "Аним. Хотбара";
            case "Anim Chat": return "Аним. Чата";
            case "Anim Tab": return "Аним. Таба";

            case "Hide Name": return "Скрыть Ник (Protected)";
            case "Hide Coords": return "Скрыть Координаты (???)";

            case "HH Pulsation": return "Пульсация";
            case "HH Alpha": return "Прозрачность";
            case "Gapple HP": return "ХП для Зол. Яблока";
            case "E-Gapple HP": return "ХП для Чарки";
            case "Potion HP": return "ХП для Зелья";

            case "Motion Clones": return "Клоны";
            case "Clone Count": return "Кол-во";
            case "Clone Delay": return "Задержка";
            case "Clone Life": return "Жизнь";
            case "Clone Scale": return "Размер";
            case "Clone Alpha": return "Яркость";
            case "Clone Color Mode": return "Режим Цвета";
            case "Clone Custom Color": return "Свой Цвет";

            case "Notifications": return "Уведомления";
            case "Notif Swap": return "Увед. о свапе";
            case "Notif HP": return "Увед. о ХП";
            case "Notif Armor": return "Увед. о Броне";
            case "Notif Potions": return "Увед. о Зельях";
            case "Notif Solid Bg": return "Сплошной фон";
            case "Notif Scale": return "Масштаб";

            case "AuraParticles": return "Аура Частицы";
            case "Aura Particles Count": return "Кол-во";
            case "Aura Particles Size": return "Размер";
            case "Aura Particles Radius": return "Радиус";
            case "Aura Particles Delay": return "Задержка";
            case "Aura Particles Color Mode": return "Режим Цвета";
            case "Aura Particles Custom Color": return "Свой Цвет";

            case "No Render": return "Анти-Рендер";
            case "No Fire": return "Убрать Огонь";
            case "No Portal": return "Убрать Портал";
            case "No Totem Anim": return "Убрать Аним. Тотема";
            case "No Weather": return "Убрать Погоду (Дождь/Снег)";
            case "No Grass": return "Убрать Траву/Цветы";
            case "No Bad Effects": return "Убрать Слепоту/Тьму";
            case "No Hurt Cam": return "Убрать Тряску (Урон)";

            case "Sky Customizer": return "Кастомное Небо";
            case "Sky Color Mode": return "Цвет Неба";
            case "Custom Sky Color": return "Свой Цвет Неба";
            case "Custom Time": return "Свое Время";
            case "World Time": return "Время Суток";
            case "Weather Mode": return "Погода";
            case "Fog Customizer": return "Кастомный Туман";
            case "Fog Distance": return "Дальность Тумана";
            case "Fog Color Mode": return "Цвет Тумана";
            case "Custom Fog Color": return "Свой Цвет Тумана";

            case "Nimb": return "Нимб";
            case "Nimb Show 1st Person": return "Вид от 1-го лица";
            case "Nimb Color Mode": return "Режим Цвета";
            case "Nimb Custom Color": return "Свой Цвет";
            case "Nimb Radius": return "Радиус Нимба";

            case "Eat Threshold": return "Голод (из 20)";

            case "Shift Mode": return "Режим";

            case "Leave Mode": return "Режим Лива";
            case "Near Cooldown": return "КД /near (сек)";

            case "Ratio Mode": return "Режим";
            case "Aspect Ratio Val": return "Значение";

            case "Zoom Mode": return "Режим Зума";
            case "Zoom Value": return "Сила Зума";
            case "Zoom Smooth": return "Плавность";
            case "Zoom Scroll Step": return "Шаг Колеса";
            case "Zoom Action": return "Кнопка Зума";

            case "Right Hand Z": return "Правая рука Z";
            case "Left Hand Z": return "Левая рука Z";
            case "Right Hand X": return "Правая рука X";
            case "Right Hand Y": return "Правая рука Y";
            case "Left Hand X": return "Левая рука X";
            case "Left Hand Y": return "Левая рука Y";
            case "Сила наклона": return "Сила наклона";
            case "Поворот": return "Поворот";

            case "Hitbox Style": return "Стиль Хитбокса";
            case "Hitbox Color Mode": return "Режим Цвета";
            case "Hitbox Color": return "Цвет Хитбокса";
            case "Hitbox Alpha": return "Прозрачность";
            case "Hitbox Line Width": return "Толщина Линий";
            case "HB Range": return "Дистанция (Блоки)";
            case "HB Only Target": return "Только прицел";
            case "HB Hurt Pulse": return "Вспышка от Урона";
            case "HB Players": return "Игроки";
            case "HB Mobs": return "Мобы";
            case "HB Items": return "Предметы";

            case "Hit Sound Mode": return "Звук";
            case "Hit Sound Volume": return "Громкость";
            case "Hit Sound Only Crit": return "Только при Крите";

            case "Swap Mode": return "Режим Свапа";
            case "Swap From": return "Свапать С";
            case "Swap To": return "Свапать На";
            case "Only Enchanted Totems": return "Только зач. тотемы";

            case "Part. Texture": return "Текстура";


            case "Part. Ambient": return "Эмбиент";
            case "Amb Chance": return "Шанс";
            case "Amb Size": return "Размер";
            case "Amb Life": return "Жизнь";

            case "Part. Walk": return "При Ходьбе";
            case "Walk Count": return "Кол-во";
            case "Walk Size": return "Размер";
            case "Walk Life": return "Жизнь";

            case "Part. Hit": return "При Ударе";
            case "Hit Size": return "Размер";
            case "Hit Count": return "Кол-во";
            case "Hit Life": return "Жизнь";

            case "Part. Crit": return "При Крите";
            case "Crit Size": return "Размер";
            case "Crit Count": return "Кол-во";
            case "Crit Life": return "Жизнь";

            case "Part. Projectiles": return "Снаряды";
            case "Proj Count": return "Кол-во";
            case "Proj Size": return "Размер";
            case "Proj Life": return "Жизнь";

            case "Part. Totem": return "Тотем";
            case "Totem Size": return "Размер";
            case "Totem Count": return "Кол-во";
            case "Totem Life": return "Жизнь";

            case "Disable Vanilla Totem": return "Скрыть Ван. Тотем";

            case "Jump Size": return "Размер Кругов";
            case "Jump Speed": return "Скорость";

            case "Hit Color Mode": return "Режим Цвета";
            case "Hit Target": return "Цель Цвета";
            case "Hit Custom Color": return "Свой Цвет";

            case "Predict Ticks": return "Дальность (Тики)";

            case "Theme Color 1": return "Главный Цвет 1";
            case "Theme Color 2": return "Главный Цвет 2";
            case "Full Bright": return "Фулл Брайт";

            case "VM Anim": return "Режим Анимации";
            case "VM Speed": return "Скорость анимации";

            case "FT Трапка Скин": return "Скин Трапки";
            case "FT Пласт Скин": return "Скин Пласта";
            case "FT Таймер Трапки": return "Таймер Трапки";

            case "Wave Only Crit": return "Только при Крите";
            case "Wave Size": return "Размер Волны";
            case "Wave Speed": return "Скорость Расширения";

            case "Overlay Mode": return "Стиль Блока";
            case "Overlay Color": return "Цвет Оверлея";

            case "Trail Show 1st Person": return "Вид от 1-го лица";
            case "Trail Color Mode": return "Режим Цвета";
            case "Trail Custom Color": return "Свой Цвет";

            case "Hand Mode": return "Эффект Рук";
            case "Hand Glow %": return "Свечение %";
        }
        if (key.contains(" Scale")) return "Масштаб";
        return key;
    }

    private String tMode(String mode) {
        switch (mode) {
            case "Spirits": return "Духи";
            case "Crystals": return "Кристаллы";
            case "Rhombus": return "Ромб";
            case "Round Rhombus": return "Круглый Ромб";
            case "Theme": return "Из Темы";
            case "Custom": return "Свой Цвет";
            case "All": return "Игрок + Броня";
            case "Body Only": return "Только Тело";

            case "Standard": return "Стандарт";
            case "Под наклоном": return "Под наклоном";
            case "Наклон": return "Наклон";
            case "Вращение на 360": return "Вращение на 360";
            case "От себя": return "От себя";
            case "Боньк": return "Боньк";

            case "Обычный": return "Обычный";
            case "Драконий": return "Драконий";

            case "Hold": return "Зажатие";
            case "Toggle": return "Переключение";

            case "Все удары": return "Все удары";
            case "Только криты": return "Только криты";

            case "Web": return "Паутина";
            case "Plasma": return "Плазма";
            case "Grid": return "Сетка";

            case "Smoke": return "Дым";
            case "Snow": return "Лава";
            case "Solid": return "Облака";
            case "Stripes": return "Звёздный тоннель";

            case "Nebula": return "Космос (Nebula)";
            case "Water": return "Вода (Искажение)";
            case "Flame": return "Плазма (Огонь)";

        }
        return mode;
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);

        float currentScale = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
        float targetScale = 2.0f;
        float scaleFactor = currentScale / targetScale;
        float renderScale = 1.0f / scaleFactor;

        int mx = (int) (mouseX * scaleFactor);
        int my = (int) (mouseY * scaleFactor);

        int scaledWidth = (int) (this.width * scaleFactor);
        int scaledHeight = (int) (this.height * scaleFactor);
        int x = (scaledWidth - guiWidth) / 2;
        int y = (scaledHeight - guiHeight) / 2;

        openAnimation += (1.0f - openAnimation) * 0.15f;

        targetScrollY = Math.max(-maxScrollHeight, Math.min(0f, targetScrollY));
        scrollY += (targetScrollY - scrollY) * 0.2f;

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.scale(renderScale, renderScale, 1.0f);
        matrices.translate(x + guiWidth / 2f, y + guiHeight / 2f, 0);
        matrices.scale(openAnimation, openAnimation, 1.0f);
        matrices.translate(-(x + guiWidth / 2f), -(y + guiHeight / 2f), 0);

        clickBounds.clear();
        colorPickerBounds.clear();
        huePickerBounds.clear();

        drawSmoothRect(context, x, y, guiWidth, guiHeight, 0xFF141416);
        drawSmoothRect(context, x, y, 150, guiHeight, 0xFF1A1A1E);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        drawColoredTexturedQuad(context, LOGO, x + 55, y + 15, 40, 40, 0.0f, 0.0f, 1.0f, 1.0f, 255, 255, 255, 255);

        int catY = y + 70;
        Text mainTitle = Text.literal("MAIN").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
        context.drawText(tr, mainTitle, x + 15, catY, 0xFF555566, false);
        catY += 20;

        for (String cat : mainCategories) catY = drawCategory(context, tr, cat, x, catY, mx, my, true);

        catY += 15;
        Text settingsTitle = Text.literal("SETTINGS").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
        context.drawText(tr, settingsTitle, x + 15, catY, 0xFF555566, false);
        catY += 20;

        for (String cat : settingsCategories) catY = drawCategory(context, tr, cat, x, catY, mx, my, false);

        if (currentCategory.equals("Config Manager")) {
            drawConfigMenu(context, tr, x + 170, y + 25, guiWidth - 190, mx, my);
        } else {
            context.enableScissor(x + 160, y + 20, x + guiWidth - 10, y + guiHeight - 20);
            matrices.push();
            matrices.translate(0, scrollY, 0);

            List<String> modules = categories.get(currentCategory);
            if (modules != null && !modules.isEmpty()) {
                int startX = x + 170;
                int[] colY = new int[]{y + 25, y + 25};

                for (int i = 0; i < modules.size(); i++) {
                    String modName = modules.get(i);
                    boolean isEnabled = moduleStates.getOrDefault(modName, false);
                    boolean isBinding = modName.equals(bindingModule);

                    int col = i % 2;
                    int btnX = startX + col * 205;
                    int btnY = colY[col];
                    int btnW = 190;
                    int btnH = 45;

                    // Блокируем ховер эффект, если открыто модальное окно настроек
                    boolean isHovered = expandedModule == null && mx >= btnX && mx <= btnX + btnW && my >= btnY + scrollY && my <= btnY + scrollY + btnH;

                    float hoverTarget = isHovered ? 1.0f : 0.0f;
                    float currentHover = hoverAnimations.getOrDefault("mod_hover_" + modName, 0.0f);
                    hoverAnimations.put("mod_hover_" + modName, currentHover + (hoverTarget - currentHover) * 0.2f);

                    float activeTarget = isEnabled ? 1.0f : 0.0f;
                    float currentActive = hoverAnimations.getOrDefault("mod_active_" + modName, 0.0f);
                    hoverAnimations.put("mod_active_" + modName, currentActive + (activeTarget - currentActive) * 0.2f);

                    int slideX = (int) (currentHover * 4.0f);
                    int finalX = btnX + slideX;

                    // Кликать по модулям можно только когда закрыты настройки
                    if (expandedModule == null) {
                        clickBounds.put("mod_" + modName, new int[]{finalX, (int)(btnY + scrollY), btnW, btnH});
                    }

                    // В основном цикле отрисовки модулей
                    int baseColor = blendColors(0xFF1E1E24, 0xFF2A2A38, currentHover);
                    int finalBgColor = blendColors(baseColor, 0xFF35354A, currentActive);

                    if (isBinding) {
                        float pulse = (float) (Math.sin(System.currentTimeMillis() / 150.0) * 0.5 + 0.5);
                        // 🔥 ФИКС: принудительно ставим 0xFF в начале, чтобы фон не стал прозрачным
                        int pulseColor = getGuiThemeColor() & 0xFFFFFF;
                        finalBgColor = 0xFF000000 | blendColors(finalBgColor, pulseColor, pulse * 0.6f);
                    }

                    drawSmoothRect(context, finalX, btnY, btnW, btnH, finalBgColor);
                    context.drawText(tr, Text.literal(modName).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), finalX + 15, btnY + 18, isEnabled ? 0xFFFFFFFF : 0xFFCCCCCC, false);

                    if (isBinding) {
                        int pulseAlpha = (int) (155 + 100 * Math.sin(System.currentTimeMillis() / 150.0));
                        int pulseColor = (Math.max(0, Math.min(255, pulseAlpha)) << 24) | getGuiThemeColor();
                        Text bindText = Text.literal("Ожидание...").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
                        context.drawText(tr, bindText, finalX + btnW - 55 - tr.getWidth(bindText), btnY + 18, pulseColor, false);
                    } else if (moduleBinds.containsKey(modName)) {
                        int keyCode = moduleBinds.get(modName);
                        if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                            String keyName = "[" + InputUtil.fromKeyCode(keyCode, -1).getLocalizedText().getString().toUpperCase() + "]";
                            Text keyText = Text.literal(keyName).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
                            context.drawText(tr, keyText, finalX + btnW - 55 - tr.getWidth(keyText), btnY + 18, 0xFF777777, false);
                        }
                    }

                    if (isHovered || isEnabled) {
                        int gearCol = isHovered ? 255 : 180;
                        // Сдвинули сильно левее (-65) и сделали размер 12x12 (было 16x16)
                        drawColoredTexturedQuad(context, ICON_GEAR, finalX + btnW - 65, btnY + 16, 12, 12, 0.0f, 0.0f, 1.0f, 1.0f, gearCol, gearCol, gearCol, 255);
                    }

                    int toggleBg = blendColors(0xFF101015, getGuiThemeColor() | 0xFF000000, currentActive);
                    drawSmoothRect(context, finalX + btnW - 45, btnY + 14, 30, 16, toggleBg);
                    drawSmoothRect(context, finalX + btnW - 43 + (int) (currentActive * 14.0f), btnY + 16, 12, 12, 0xFFFFFFFF);

                    colY[col] += btnH + 10;
                }

                maxScrollHeight = Math.max(0, Math.max(colY[0], colY[1]) - y - (guiHeight - 150));
            }
            matrices.pop();
            context.disableScissor();
        }
        matrices.pop(); // Завершаем скейл главного окна

        // --- ЛОГИКА ВСПЛЫВАЮЩЕГО ОКНА НАСТРОЕК ---
        if (expandedModule != null || settingsOpenAnim > 0.01f) {
            float targetAnim = (expandedModule != null) ? 1.0f : 0.0f;
            settingsOpenAnim += (targetAnim - settingsOpenAnim) * 0.2f;

            if (settingsOpenAnim > 0.01f) {
                // Затемнение заднего фона
                int dimAlpha = (int) (settingsOpenAnim * 180);
                context.fill(0, 0, this.width, this.height, (dimAlpha << 24) | 0x000000);

                matrices.push();
                matrices.scale(renderScale, renderScale, 1.0f);

                // Анимация вылета из центра
                matrices.translate(scaledWidth / 2f, scaledHeight / 2f, 0);
                matrices.scale(settingsOpenAnim, settingsOpenAnim, 1.0f);
                matrices.translate(-scaledWidth / 2f, -scaledHeight / 2f, 0);

                int pW = 320;
                int pH = 340;
                int pX = (scaledWidth - pW) / 2;
                int pY = (scaledHeight - pH) / 2;

                drawSmoothRect(context, pX, pY, pW, pH, 0xFF141416); // Фон окна
                drawSmoothRect(context, pX, pY, pW, 35, 0xFF1A1A1E); // Шапка

                String title = expandedModule != null ? expandedModule : "";
                Text titleText = Text.literal(title + " Settings").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
                context.drawText(tr, titleText, pX + 15, pY + 13, 0xFFFFFFFF, false);

                boolean closeHovered = mx >= pX + pW - 35 && mx <= pX + pW - 10 && my >= pY + 5 && my <= pY + 30;
                clickBounds.put("close_popup", new int[]{pX + pW - 35, pY + 5, 25, 25});
                int crossCol = closeHovered ? 255 : 180;
                // Делаем ярче при наведении
                drawColoredTexturedQuad(context, ICON_CLOSE, pX + pW - 30, pY + 8, 18, 18, 0.0f, 0.0f, 1.0f, 1.0f, crossCol, crossCol, crossCol, 255);

                // Настройки внутри окна (со скроллом)
                context.enableScissor(pX, pY + 35, pX + pW, pY + pH - 5);

                targetSettingsScrollY = Math.max(-maxSettingsScroll, Math.min(0f, targetSettingsScrollY));
                settingsScrollY += (targetSettingsScrollY - settingsScrollY) * 0.2f;

                matrices.push();
                matrices.translate(0, settingsScrollY, 0);

                int sY = pY + 45;
                int sX = pX + 15;
                int sW = pW - 30;
                float fadeAlpha = settingsOpenAnim;

                String modName = title;
                boolean isHudMod = Arrays.asList("Armor Status", "Potions", "Inventory HUD", "Cooldowns", "Watermark", "Keybinds", "Target HUD", "Info HUD", "Notifications").contains(modName);

                // --- РЕНДЕР НАСТРОЕК ВЫБРАННОГО МОДУЛЯ ---
                if (modName.equals("Custom Hitboxes")) {
                    sY = drawMiniMode(context, tr, "Hitbox Style", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Hitbox Alpha", 0.1f, 1.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawColorPicker(context, tr, "Hitbox Color", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "HB Players", "Игроки", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "HB Mobs", "Мобы", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "HB Items", "Предметы", sX, sY, sW, fadeAlpha);
                } else if (modName.equals("Target ESP")) {
                    String currentMode = modeSettings.getOrDefault("Target ESP Mode", "Spirits");
                    sY = drawTargetESPSettings(context, tr, sX, sY, sW, mx, my, fadeAlpha, currentMode.equals("Spirits"), currentMode.contains("Rhombus"));
                } else if (modName.equals("Optimization")) {
                    sY = drawMiniToggle(context, tr, "Opt No Clouds", "Отключить 3D облака", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt No Fog", "Отключить туман", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Fast Weather", "Убрать дождь и снег", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Fast Items", "Ограничить лут", sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Opt Items Dist", 8.0f, 48.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Block Culling", "Оптимизация сундуков", sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Opt Block Dist", 16.0f, 96.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Entity Culling", "Дальность сущностей", sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Opt Entity Dist", 12.0f, 96.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt All Particles", "Отключить ВСЕ частицы", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Fast Lighting", "Быстрое освещение", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Fast HUD", "Убрать виньетку", sX, sY, sW, fadeAlpha);
                } else if (modName.equals("Gradient Theme")) {
                    sY = drawColorPicker(context, tr, "Theme Color 1", sX, sY, sW, mx, my, fadeAlpha);
                    if (moduleStates.getOrDefault(modName, false)) {
                        sY = drawColorPicker(context, tr, "Theme Color 2", sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else if (modName.equals("Item Scroller")) {
                    sY = drawMiniSlider(context, tr, "Scroller Speed", 1.0f, 20.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("No Render")) {
                    sY = drawMiniToggle(context, tr, "No Fire", t("No Fire"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "No Portal", t("No Portal"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "No Totem Anim", t("No Totem Anim"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "No Weather", t("No Weather"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "No Grass", t("No Grass"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "No Bad Effects", t("No Bad Effects"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "No Hurt Cam", t("No Hurt Cam"), sX, sY, sW, fadeAlpha);
                } else if (modName.equals("Fast Swap")) {
                    int alphaInt = (int)(fadeAlpha * 255);
                    sY = drawMiniToggle(context, tr, "Show Hotbar Binds", "Бинды на хотбаре", sX, sY, sW, fadeAlpha);

                    // --- ФТ ВЕРСИЯ ---
                    context.drawText(tr, Text.literal("ФТ Версия").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), sX + 15, sY, (alphaInt << 24) | 0xFF55FF, false);
                    sY += 15;
                    sY = drawBindButton(context, tr, "Bind_Дезка", "Дезка", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Явная пыль", "Явная пыль", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Божья аура", "Божья аура", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Пласт", "Пласт", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Трапка ФТ", "Трапка", sX, sY, sW, mx, my, fadeAlpha);

                    // --- ХВ ВЕРСИЯ ---
                    context.drawText(tr, Text.literal("ХВ Версия").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), sX + 15, sY, (alphaInt << 24) | 0xFF55FF, false);
                    sY += 15;
                    sY = drawBindButton(context, tr, "Bind_Стан", "Стан", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Взр. штучка", "Взр. штучка", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Трапка ХВ", "Трапка ХВ", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Взр. трапка", "Взр. трапка", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Ком снега", "Ком снега", sX, sY, sW, mx, my, fadeAlpha);

                    // --- ДРУГОЕ ---
                    context.drawText(tr, Text.literal("Другое").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), sX + 15, sY, (alphaInt << 24) | 0xFF55FF, false);
                    sY += 15;
                    sY = drawBindButton(context, tr, "Bind_Хорус", "Хорус", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Эндер перл", "Эндер перл", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Bind_Исцеление", "Зелье", sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Item Highlighter")) {
                    // Рисуем список предметов для подсветки
                    int alphaInt = (int)(fadeAlpha * 255);

                    // Рисуем ползунок прозрачности и пульсацию
                    sY = drawMiniSlider(context, tr, "Highlighter Alpha", 0f, 255f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Highlighter Pulsation", "Пульсация подсветки", sX, sY, sW, fadeAlpha);
                    sY += 5; // Небольшой отступ

                    // Запускаем перебор всех предметов
                    for (Map.Entry<Item, ItemHighlighter.ItemConfig> entry : ItemHighlighter.ITEM_CONFIGS.entrySet()) {
                        Item item = entry.getKey();
                        ItemHighlighter.ItemConfig config = entry.getValue();
                        String name = ItemHighlighter.ITEM_NAMES.get(item);

                        // Рисуем иконку предмета и текст
                        context.drawItem(item.getDefaultStack(), sX, sY - 4);
                        context.drawText(tr, name, sX + 20, sY, (alphaInt << 24) | 0xFFFFFFFF, false);

                        // 👇 ВОТ СЮДА ВСТАЛ ТВОЙ КОД С КРУГЛЫМИ ТУМБЛЕРАМИ 👇
                        // 🔥 КРАСИВЫЙ АНИМИРОВАННЫЙ ТУМБЛЕР
                        boolean isItemOn = ItemHighlighter.isItemEnabled(item);
                        float animTarget = isItemOn ? 1.0f : 0.0f;
                        float currentAnim = toggleAnimations.getOrDefault("tog_hl_" + name, animTarget);
                        currentAnim += (animTarget - currentAnim) * 0.2f;
                        toggleAnimations.put("tog_hl_" + name, currentAnim);

                        int togX = sX + 160;
                        int togY = sY - 2;
                        clickBounds.put("highlighter_tog_" + name, new int[]{togX, (int)(togY + settingsScrollY), 30, 14});

                        // Рисуем фон тумблера
                        int togBgColor = blendColors(0xFF1A1A20, getGuiThemeColor() | 0xFF000000, currentAnim);
                        drawSmoothRect(context, togX, togY, 30, 14, (alphaInt << 24) | (togBgColor & 0xFFFFFF));

                        // 🔥 БЕЛЫЙ ПОЛЗУНОК: Идеальный круг
                        drawCircle(context, togX + 2 + (int)(currentAnim * 16), togY + 2, 5, (alphaInt << 24) | 0xFFFFFFFF);

                        // 🔥 КНОПКА ЦВЕТА: Цветной кружочек!
                        int colBoxX = sX + 200;
                        clickBounds.put("highlighter_col_" + name, new int[]{colBoxX, (int)(togY + settingsScrollY), 14, 14});

                        int itemCol = ItemHighlighter.getItemColor(item);
                        drawCircle(context, colBoxX, togY, 7, (alphaInt << 24) | itemCol | 0xFF000000);

                        sY += 18; // Сдвигаемся вниз для следующего предмета
                    }
                    // Конец блока Item Highlighter
                } else if (modName.equals("PvP Save")) {
                    sY = drawMiniSlider(context, tr, "PvP Time", 5.0f, 30.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Streamer Mode")) {
                    sY = drawMiniToggle(context, tr, "Hide Name", t("Hide Name"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Hide Coords", t("Hide Coords"), sX, sY, sW, fadeAlpha);
                } else if (modName.equals("Healing Helper")) {
                    int alphaInt = (int)(fadeAlpha * 255);

                    sY = drawMiniToggle(context, tr, "HH Pulsation", t("HH Pulsation"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "HH Alpha", 10.0f, 255.0f, sX, sY, sW, mx, my, fadeAlpha);

                    sY += 10;
                    context.drawText(tr, Text.literal("Пороги здоровья (из 20 HP):").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), sX + 10, sY, (alphaInt << 24) | 0xFFDDDDDD, false);
                    sY += 15;

                    sY = drawMiniSlider(context, tr, "Gapple HP", 1.0f, 20.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "E-Gapple HP", 1.0f, 20.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Potion HP", 1.0f, 20.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("World Customizer")) {
                    sY = drawMiniToggle(context, tr, "Sky Customizer", t("Sky Customizer"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Sky Color Mode", sX, sY, sW, mx, my, fadeAlpha);
                    if (modeSettings.getOrDefault("Sky Color Mode", "Theme").equals("Custom")) {
                        sY = drawColorPicker(context, tr, "Custom Sky Color", sX, sY, sW, mx, my, fadeAlpha);
                    }
                    sY = drawMiniToggle(context, tr, "Custom Time", t("Custom Time"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "World Time", 0f, 24000f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Weather Mode", sX, sY, sW, mx, my, fadeAlpha);

                    sY = drawMiniToggle(context, tr, "Fog Customizer", t("Fog Customizer"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Fog Distance", 0.1f, 5.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Fog Color Mode", sX, sY, sW, mx, my, fadeAlpha);
                    if (modeSettings.getOrDefault("Fog Color Mode", "Theme").equals("Custom")) {
                        sY = drawColorPicker(context, tr, "Custom Fog Color", sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else if (modName.equals("Lock Slot")) {
                    int alphaInt = (int)(fadeAlpha * 255);
                    context.drawText(tr, Text.literal("Заблокированные слоты (Хотбар):").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), sX + 10, sY, (alphaInt << 24) | 0xFFDDDDDD, false);
                    sY += 15;

                    int padX = sX + 15;
                    int btnW = 28;
                    int btnH = 28;

                    // Рисуем красивый островок из 9 кнопочек (1-5 сверху, 6-9 снизу)
                    for (int i = 0; i < 9; i++) {
                        String slotName = "LockSlot_" + i;
                        boolean isLocked = moduleStates.getOrDefault(slotName, false);

                        int bx = padX + (i % 5) * (btnW + 8);
                        int by = sY + (i / 5) * (btnH + 8);

                        clickBounds.put("minitog_" + slotName, new int[]{bx, (int)(by + settingsScrollY), btnW, btnH});

                        int color = isLocked ? (getGuiThemeColor() | 0xFF000000) : 0xFF2A2A38;
                        drawSmoothRect(context, bx, by, btnW, btnH, (alphaInt << 24) | (color & 0xFFFFFF));

                        Text t = Text.literal(String.valueOf(i + 1)).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
                        context.drawText(tr, t, bx + (btnW - tr.getWidth(t))/2, by + 10, (alphaInt << 24) | 0xFFFFFFFF, false);
                    }
                    sY += (2 * (btnH + 8)) + 10;
                } else if (modName.equals("Animations")) {
                    sY = drawMiniToggle(context, tr, "Anim Hotbar", t("Anim Hotbar"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Anim Chat", t("Anim Chat"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Anim Tab", t("Anim Tab"), sX, sY, sW, fadeAlpha);
                } else if (modName.equals("Aspect Ratio")) {
                    sY = drawMiniMode(context, tr, "Ratio Mode", sX, sY, sW, mx, my, fadeAlpha);
                    if (modeSettings.getOrDefault("Ratio Mode", "Default").equals("Custom")) {
                        sY = drawMiniSlider(context, tr, "Aspect Ratio Val", 0.5f, 3.0f, sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else if (modName.equals("Zoom")) {
                    sY = drawMiniMode(context, tr, "Zoom Mode", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Zoom Value", 1.0f, 12.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Zoom Smooth", 0.01f, 1.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Zoom Scroll Step", 0.05f, 1.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawBindButton(context, tr, "Zoom Action", "Кнопка Зума", sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("View Model")) {
                    sY = drawMiniMode(context, tr, "VM Anim", sX, sY, sW, mx, my, fadeAlpha);
                    sY = draw2DPad(context, tr, "Right Hand X", "Right Hand Y", "Позиция правой руки", -2.5f, 2.5f, -2.0f, 2.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Right Hand Z", -1.5f, 1.5f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = draw2DPad(context, tr, "Left Hand X", "Left Hand Y", "Позиция левой руки", -2.5f, 2.5f, -2.0f, 2.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Left Hand Z", -1.5f, 1.5f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "VM Speed", 0.1f, 5.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Shift Tap")) {
                    sY = drawMiniMode(context, tr, "Shift Mode", sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Hit Sounds")) {
                    sY = drawMiniToggle(context, tr, "Hit Sound Only Crit", t("Hit Sound Only Crit"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Hit Sound Mode", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Hit Sound Volume", 0.0f, 100.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Item Swap")) {
                    sY = drawMiniMode(context, tr, "Swap Mode", sX, sY, sW, mx, my, fadeAlpha);
                    if (modeSettings.getOrDefault("Swap Mode", "Двойной").equals("Двойной")) {
                        sY = drawMiniMode(context, tr, "Swap From", sX, sY, sW, mx, my, fadeAlpha);
                        sY = drawMiniMode(context, tr, "Swap To", sX, sY, sW, mx, my, fadeAlpha);
                    }
                    sY = drawMiniToggle(context, tr, "Only Enchanted Totems", t("Only Enchanted Totems"), sX, sY, sW, fadeAlpha);
                    sY = drawBindButton(context, tr, modName + " Action", "Кнопка Свапа", sX, sY, sW, mx, my, fadeAlpha);
                    if (!moduleStates.containsKey("Zoom")) moduleStates.put("Zoom", true);
                    if (!modeSettings.containsKey("Zoom Mode")) modeSettings.put("Zoom Mode", "Hold");
                    if (!numSettings.containsKey("Zoom Value")) numSettings.put("Zoom Value", 4.0f);
                    if (!numSettings.containsKey("Zoom Smooth")) numSettings.put("Zoom Smooth", 0.18f);
                    if (!numSettings.containsKey("Zoom Scroll Step")) numSettings.put("Zoom Scroll Step", 0.35f);
                    if (!numSettings.containsKey("Zoom Action")) { numSettings.put("Zoom Action", (float) GLFW.GLFW_KEY_C); ClientData.moduleBinds.putIfAbsent("Zoom Action", GLFW.GLFW_KEY_C); }
                } else if (modName.equals("Elytra Swap")) {
                    sY = drawBindButton(context, tr, modName + " Action", "Кнопка Элитр", sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Auto Eat")) {
                    sY = drawMiniSlider(context, tr, "Eat Threshold", 1.0f, 20.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("AuraParticles")) {
                    sY = drawMiniSlider(context, tr, "Aura Particles Count", 1.0f, 80.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Aura Particles Size", 0.05f, 0.60f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Aura Particles Radius", 1.0f, 2.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Aura Particles Delay", 1.0f, 8.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Aura Particles Color Mode", sX, sY, sW, mx, my, fadeAlpha);

                    if (modeSettings.getOrDefault("Aura Particles Color Mode", "Client").equals("Custom")) {
                        sY = drawColorPicker(context, tr, "Aura Particles Custom Color", sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else if (modName.equals("Auto Leave")) {
                    sY = drawMiniMode(context, tr, "Leave Mode", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Near Cooldown", 1.0f, 180.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Free Look")) {
                    sY = drawBindButton(context, tr, "Free Look Action", "Кнопка обзора", sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Ft Helper")) {
                    int halfW = sW / 2;
                    int y1 = sY;
                    y1 = drawMicroToggle(context, tr, "FT Дезка", "Дезка", sX, y1, halfW, fadeAlpha);
                    y1 = drawMicroToggle(context, tr, "FT Явка", "Явка", sX, y1, halfW, fadeAlpha);
                    y1 = drawMicroToggle(context, tr, "FT Огненный Шар", "Огн. Шар", sX, y1, halfW, fadeAlpha);
                    y1 = drawMicroToggle(context, tr, "FT Божья Аура", "Божья Аура", sX, y1, halfW, fadeAlpha);
                    int y2 = sY;
                    y2 = drawMicroToggle(context, tr, "FT Трапка", "Трапка", sX + halfW, y2, halfW, fadeAlpha);
                    y2 = drawMicroToggle(context, tr, "FT Пласт", "Пласт", sX + halfW, y2, halfW, fadeAlpha);
                    y2 = drawMicroToggle(context, tr, "FT Снежок", "Снежок", sX + halfW, y2, halfW, fadeAlpha);
                    sY = Math.max(y1, y2);
                    sY = drawMiniMode(context, tr, "FT Трапка Скин", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniMode(context, tr, "FT Пласт Скин", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "FT Таймер Трапки", t("FT Таймер Трапки"), sX, sY, sW, fadeAlpha);
                } else if (modName.equals("Particles")) {
                    int halfW = sW / 2;
                    int nextY = drawMiniMode(context, tr, "Part. Texture", sX, sY, sW, mx, my, fadeAlpha);
                    int y1 = nextY;
                    y1 = drawMicroToggle(context, tr, "Part. Ambient", t("Part. Ambient"), sX, y1, halfW, fadeAlpha);
                    if (moduleStates.getOrDefault("Part. Ambient", true)) {
                        y1 = drawMicroSlider(context, tr, "Amb Chance", t("Amb Chance"), 1.0f, 20.0f, sX, y1, halfW, mx, my, fadeAlpha);
                        y1 = drawMicroSlider(context, tr, "Amb Size", t("Amb Size"), 0.1f, 5.0f, sX, y1, halfW, mx, my, fadeAlpha);
                        y1 = drawMicroSlider(context, tr, "Amb Life", t("Amb Life"), 10.0f, 200.0f, sX, y1, halfW, mx, my, fadeAlpha);
                    }
                    y1 = drawMicroToggle(context, tr, "Part. Walk", t("Part. Walk"), sX, y1, halfW, fadeAlpha);
                    if (moduleStates.getOrDefault("Part. Walk", true)) {
                        y1 = drawMicroSlider(context, tr, "Walk Count", t("Walk Count"), 1.0f, 10.0f, sX, y1, halfW, mx, my, fadeAlpha);
                        y1 = drawMicroSlider(context, tr, "Walk Size", t("Walk Size"), 0.1f, 5.0f, sX, y1, halfW, mx, my, fadeAlpha);
                        y1 = drawMicroSlider(context, tr, "Walk Life", t("Walk Life"), 10.0f, 100.0f, sX, y1, halfW, mx, my, fadeAlpha);
                    }
                    y1 = drawMicroToggle(context, tr, "Part. Hit", t("Part. Hit"), sX, y1, halfW, fadeAlpha);
                    if (moduleStates.getOrDefault("Part. Hit", true)) {
                        y1 = drawMicroSlider(context, tr, "Hit Size", t("Hit Size"), 0.1f, 5.0f, sX, y1, halfW, mx, my, fadeAlpha);
                        y1 = drawMicroSlider(context, tr, "Hit Count", t("Hit Count"), 1.0f, 30.0f, sX, y1, halfW, mx, my, fadeAlpha);
                        y1 = drawMicroSlider(context, tr, "Hit Life", t("Hit Life"), 10.0f, 100.0f, sX, y1, halfW, mx, my, fadeAlpha);
                    }
                    int y2 = nextY;
                    y2 = drawMicroToggle(context, tr, "Part. Crit", t("Part. Crit"), sX + halfW, y2, halfW, fadeAlpha);
                    if (moduleStates.getOrDefault("Part. Crit", true)) {
                        y2 = drawMicroSlider(context, tr, "Crit Size", t("Crit Size"), 0.1f, 5.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                        y2 = drawMicroSlider(context, tr, "Crit Count", t("Crit Count"), 1.0f, 30.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                        y2 = drawMicroSlider(context, tr, "Crit Life", t("Crit Life"), 10.0f, 100.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                    }
                    y2 = drawMicroToggle(context, tr, "Part. Projectiles", t("Part. Projectiles"), sX + halfW, y2, halfW, fadeAlpha);
                    if (moduleStates.getOrDefault("Part. Projectiles", true)) {
                        y2 = drawMicroSlider(context, tr, "Proj Count", t("Proj Count"), 1.0f, 10.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                        y2 = drawMicroSlider(context, tr, "Proj Size", t("Proj Size"), 0.1f, 5.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                        y2 = drawMicroSlider(context, tr, "Proj Life", t("Proj Life"), 10.0f, 100.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                    }
                    y2 = drawMicroToggle(context, tr, "Part. Totem", t("Part. Totem"), sX + halfW, y2, halfW, fadeAlpha);
                    if (moduleStates.getOrDefault("Part. Totem", true)) {
                        y2 = drawMicroSlider(context, tr, "Totem Size", t("Totem Size"), 0.1f, 5.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                        y2 = drawMicroSlider(context, tr, "Totem Count", t("Totem Count"), 10.0f, 100.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                        y2 = drawMicroSlider(context, tr, "Totem Life", t("Totem Life"), 10.0f, 200.0f, sX + halfW, y2, halfW, mx, my, fadeAlpha);
                    }
                    int finalY = Math.max(y1, y2);
                    sY = drawMiniToggle(context, tr, "Disable Vanilla Totem", t("Disable Vanilla Totem"), sX, sY, sW, fadeAlpha);
                } else if (modName.equals("Jump Circles")) {
                    sY = drawMiniSlider(context, tr, "Jump Size", 0.5f, 3.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Jump Speed", 0.1f, 2.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Hit Color")) {
                    sY = drawMiniMode(context, tr, "Hit Color Mode", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Hit Target", sX, sY, sW, mx, my, fadeAlpha);
                    if (modeSettings.getOrDefault("Hit Color Mode", "Theme").equals("Custom")) {
                        sY = drawColorPicker(context, tr, "Hit Custom Color", sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else if (modName.equals("Hit Wave")) {
                    sY = drawMiniToggle(context, tr, "Wave Only Crit", t("Wave Only Crit"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Wave Size", 1.0f, 30.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Wave Speed", 1.0f, 40.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Prediction")) {
                    sY = drawMiniSlider(context, tr, "Predict Ticks", 1.0f, 30.0f, sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Block Overlay")) {
                    sY = drawMiniMode(context, tr, "Overlay Mode", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawColorPicker(context, tr, "Overlay Color", sX, sY, sW, mx, my, fadeAlpha);
                } else if (modName.equals("Hand Shaders")) {
                    sY = drawMiniMode(context, tr, "Hand Mode", sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Hand Glow %", 0.0f, 100.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Hand Shader Speed", 0.1f, 3.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Hand Shader Only", "Только шейдер", sX, sY, sW, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Hand Color Mode", sX, sY, sW, mx, my, fadeAlpha);
                    if (modeSettings.getOrDefault("Hand Color Mode", "Theme").equals("Custom")) {
                        sY = drawColorPicker(context, tr, "Hand Custom Color", sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else if (modName.equals("Trails")) {
                    sY = drawMiniToggle(context, tr, "Trail Show 1st Person", t("Trail Show 1st Person"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Trail Length", 10.0f, 100.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Trail Color Mode", sX, sY, sW, mx, my, fadeAlpha);
                    if (modeSettings.getOrDefault("Trail Color Mode", "Theme").equals("Custom")) {
                        sY = drawColorPicker(context, tr, "Trail Custom Color", sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else if (modName.equals("Nimb")) {
                    sY = drawMiniToggle(context, tr, "Nimb Show 1st Person", t("Nimb Show 1st Person"), sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Nimb Radius", 0.2f, 1.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniMode(context, tr, "Nimb Color Mode", sX, sY, sW, mx, my, fadeAlpha);

                    if (modeSettings.getOrDefault("Nimb Color Mode", "Theme").equals("Custom")) {
                        sY = drawColorPicker(context, tr, "Nimb Custom Color", sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else if (isHudMod) {
                    int alphaInt = (int) (fadeAlpha * 255);
                    context.drawText(tr, Text.literal("Настройки этого худа").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), sX + 10, sY, (alphaInt << 24) | 0xFFDDDDDD, false);
                    context.drawText(tr, Text.literal("доступны по ПКМ в чате!").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), sX + 10, sY + 15, (alphaInt << 24) | 0xFF55FF55, false);
                    sY += 40;
                } else if (modName.equals("Optimization")) {
                    sY = drawMiniToggle(context, tr, "Opt No Clouds", "Отключить 3D облака", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt No Fog", "Отключить туман", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Fast Weather", "Убрать дождь и снег", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Fast Items", "Ограничить лут", sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Opt Items Dist", 8.0f, 48.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Block Culling", "Оптимизация сундуков", sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Opt Block Dist", 16.0f, 96.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Entity Culling", "Дальность сущностей", sX, sY, sW, fadeAlpha);
                    sY = drawMiniSlider(context, tr, "Opt Entity Dist", 12.0f, 96.0f, sX, sY, sW, mx, my, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt All Particles", "Отключить ВСЕ частицы", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Fast Lighting", "Быстрое освещение", sX, sY, sW, fadeAlpha);
                    sY = drawMiniToggle(context, tr, "Opt Fast HUD", "Убрать виньетку", sX, sY, sW, fadeAlpha);
                } else if (modName.equals("Gradient Theme")) {
                    sY = drawColorPicker(context, tr, "Theme Color 1", sX, sY, sW, mx, my, fadeAlpha);
                    if (moduleStates.getOrDefault(modName, false)) {
                        sY = drawColorPicker(context, tr, "Theme Color 2", sX, sY, sW, mx, my, fadeAlpha);
                    }
                } else {
                    context.drawText(tr, Text.literal("У этого модуля нет настроек.").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), sX + 10, sY, 0xFF888888, false);
                    sY += 30;
                }

                // Расчет высоты скролла для окна
                maxSettingsScroll = Math.max(0, sY - pY - pH + 15);

                matrices.pop();
                context.disableScissor();
                matrices.pop(); // Закрываем scale окна
            }
        }

        // --- ОТРИСОВКА ПОЛНОЙ HSV-ПАЛИТРЫ ПОВЕРХ ВСЕГО ---

        // 1. Определяем цель: 1.0f если палитра открыта, 0.0f если мы её закрыли
        float targetPaletteAnim = (activeColorPickerItem != null) ? 1.0f : 0.0f;
        // 2. Плавно меняем текущую анимацию к цели (Анимация открытия И закрытия)
        paletteOpenAnim += (targetPaletteAnim - paletteOpenAnim) * 0.15f;

        // 3. Рисуем палитру, если она открыта ИЛИ если она еще в процессе закрытия (анимация > 0)
        if (paletteOpenAnim > 0.01f) {
            float animScale = Math.max(0.01f, paletteOpenAnim);

            // Считаем прозрачность для плавного затухания (от 0 до 255)
            int alphaInt = (int) (paletteOpenAnim * 255);
            alphaInt = Math.max(5, Math.min(255, alphaInt));

            // Размеры окна палитры: 120x140
            int px = Math.min(colorPickerX, this.width - 120);
            int py = Math.min(colorPickerY, this.height - 140);

            // 🔥 Включаем анимацию масштаба из центра окна
            context.getMatrices().push();
            context.getMatrices().translate(px + 60, py + 70, 0);
            context.getMatrices().scale(animScale, animScale, 1.0f);
            context.getMatrices().translate(-(px + 60), -(py + 70), 0);

            // Темный фон (теперь с прозрачностью alphaInt)
            context.fill(px, py, px + 120, py + 140, (alphaInt << 24) | 0x141416);

            // 🔥 КАСТОМНЫЙ ШРИФТ ДЛЯ ТЕКСТА
            Text titleText = Text.literal("Выбор цвета").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
            context.drawText(tr, titleText, px + 5, py + 5, (alphaInt << 24) | 0xFFFFFF, false);

            int svX = px + 10;
            int svY = py + 20;
            int svW = 100;
            int svH = 100;

            // 1. Рисуем большой квадрат с градиентом (Saturation / Value)
            for (int i = 0; i < svW; i++) {
                for (int j = 0; j < svH; j++) {
                    float sat = i / (float) svW;
                    float val = 1.0f - (j / (float) svH);
                    int color = java.awt.Color.HSBtoRGB(tempItemHSV[0], sat, val);
                    context.fill(svX + i, svY + j, svX + i + 1, svY + j + 1, (alphaInt << 24) | (color & 0xFFFFFF));
                }
            }

            // 🔥 ИДЕАЛЬНО КРУГЛЫЙ КУРСОР НА ГРАДИЕНТЕ
            int pointX = svX + (int) (tempItemHSV[1] * svW);
            int pointY = svY + (int) ((1.0f - tempItemHSV[2]) * svH);
            drawCircle(context, pointX - 3, pointY - 3, 3, (alphaInt << 24) | 0xFFFFFF); // Белый контур
            drawCircle(context, pointX - 2, pointY - 2, 2, (alphaInt << 24) | 0x000000); // Черная сердцевина

            // 2. Рисуем нижнюю радужную полоску (Hue)
            int hueY = py + 125;
            int hueH = 10;
            for (int i = 0; i < svW; i++) {
                float hue = i / (float) svW;
                int color = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
                context.fill(svX + i, hueY, svX + i + 1, hueY + hueH, (alphaInt << 24) | (color & 0xFFFFFF));
            }

            // 🔥 ИДЕАЛЬНО КРУГЛЫЙ КУРСОР НА ПОЛОСКЕ HUE
            int huePointX = svX + (int) (tempItemHSV[0] * svW);
            drawCircle(context, huePointX - 3, hueY + 2, 3, (alphaInt << 24) | 0xFFFFFF); // Белый кружок ползунка

            // 3. Обработка перетаскивания (ТОЛЬКО если палитра полностью открыта)
            if (activeColorPickerItem != null) {
                if (draggingItemSV) {
                    float sat = Math.max(0.0f, Math.min(1.0f, (mx - svX) / (float) svW));
                    float val = 1.0f - Math.max(0.0f, Math.min(1.0f, (my - svY) / (float) svH));
                    tempItemHSV[1] = sat;
                    tempItemHSV[2] = val;
                    int rgb = java.awt.Color.HSBtoRGB(tempItemHSV[0], sat, val) & 0xFFFFFF;
                    ItemHighlighter.ITEM_CONFIGS.get(activeColorPickerItem).color = rgb;
                    String itemName = ItemHighlighter.ITEM_NAMES.get(activeColorPickerItem);
                    if (itemName != null) {
                        ClientData.colorSettings.put("HLC_" + itemName, tempItemHSV.clone());
                    }
                }
                if (draggingItemHue) {
                    float hue = Math.max(0.0f, Math.min(1.0f, (mx - svX) / (float) svW));
                    tempItemHSV[0] = hue;
                    int rgb = java.awt.Color.HSBtoRGB(hue, tempItemHSV[1], tempItemHSV[2]) & 0xFFFFFF;
                    ItemHighlighter.ITEM_CONFIGS.get(activeColorPickerItem).color = rgb;
                    String itemName = ItemHighlighter.ITEM_NAMES.get(activeColorPickerItem);
                    if (itemName != null) {
                        ClientData.colorSettings.put("HLC_" + itemName, tempItemHSV.clone());
                    }
                }

                // Регистрируем границы для кликов мышки
                clickBounds.put("palette_sv", new int[]{svX, svY, svW, svH});
                clickBounds.put("palette_hue", new int[]{svX, hueY, svW, hueH});
                clickBounds.put("palette_bg", new int[]{px, py, 120, 140});
            }

            context.getMatrices().pop(); // 🔥 ВАЖНО: Выключаем масштаб
        }

        handleDragging(mx, my);
    } // Конец метода render
    // Метод для понимания, какой скролл использовать (для главного окна или для модального)
    private float getScroll() {
        return expandedModule != null ? settingsScrollY : scrollY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (expandedModule != null) {
            targetSettingsScrollY += verticalAmount * 40.0f;
        } else {
            targetScrollY += verticalAmount * 40.0f;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // 🔥 НОВЫЙ МЕТОД: Рисует идеальный круг из твоей текстуры
    private static void drawCircle(DrawContext context, int x, int y, int radius, int color) {
        int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Собираем 4 закругленных угла вместе = Идеальный круг!
        drawColoredTexturedQuad(context, SMOOTH_CORNERS, x, y, radius, radius, 0.0F, 0.0F, 0.5F, 0.5F, r, g, b, a); // Левый верх
        drawColoredTexturedQuad(context, SMOOTH_CORNERS, x + radius, y, radius, radius, 0.5F, 0.0F, 1.0F, 0.5F, r, g, b, a); // Правый верх
        drawColoredTexturedQuad(context, SMOOTH_CORNERS, x, y + radius, radius, radius, 0.0F, 0.5F, 0.5F, 1.0F, r, g, b, a); // Левый низ
        drawColoredTexturedQuad(context, SMOOTH_CORNERS, x + radius, y + radius, radius, radius, 0.5F, 0.5F, 1.0F, 1.0F, r, g, b, a); // Правый низ
    }

    private void handleDragging(int mx, int my) {
        if (draggingSlider != null) {
            int[] dims = sliderDimensions.get(draggingSlider);
            float[] boundsInfo = sliderMathBounds.get(draggingSlider);
            if (dims != null && boundsInfo != null) {
                float percent = Math.max(0.0f, Math.min(1.0f, (mx - dims[0]) / (float) dims[1]));
                float val = boundsInfo[0] + percent * (boundsInfo[1] - boundsInfo[0]);

                if (draggingSlider.contains("Count") || draggingSlider.contains("Life") || draggingSlider.contains("Length") || draggingSlider.contains("Volume") || draggingSlider.contains("Chance") || draggingSlider.contains("Ticks") || draggingSlider.contains("Таймер")) {
                    numSettings.put(draggingSlider, (float) Math.round(val));
                } else {
                    numSettings.put(draggingSlider, Math.round(val * 10.0f) / 10.0f);
                }
            }
        }

        if (draggingPad != null) {
            String[] parts = draggingPad.split("\\|");
            String nameX = parts[0];
            String nameY = parts[1];
            int[] bounds = clickBounds.get("pad_" + draggingPad);
            if (bounds != null) {
                float minX = -2.5f, maxX = 2.5f;
                float minY = -2.0f, maxY = 2.0f;
                float pctX = Math.max(0.0f, Math.min(1.0f, (mx - bounds[0]) / (float) bounds[2]));
                float pctY = Math.max(0.0f, Math.min(1.0f, (my - bounds[1]) / (float) bounds[3]));

                float valX = minX + pctX * (maxX - minX);
                float valY = maxY - pctY * (maxY - minY);

                numSettings.put(nameX, Math.round(valX * 100f) / 100f);
                numSettings.put(nameY, Math.round(valY * 100f) / 100f);
            }
        }

        if (draggingColorPicker != null) {
            int[] bounds = colorPickerBounds.get(draggingColorPicker);
            if (bounds != null) {
                float[] hsv = colorSettings.getOrDefault(draggingColorPicker, new float[]{0f, 1f, 1f});
                float sat = Math.max(0.0f, Math.min(1.0f, (mx - bounds[0]) / (float) bounds[2]));
                float val = 1.0f - Math.max(0.0f, Math.min(1.0f, (my - bounds[1]) / (float) bounds[3]));
                colorSettings.put(draggingColorPicker, new float[]{hsv[0], sat, val});
            }
        }

        if (draggingHuePicker != null) {
            int[] bounds = huePickerBounds.get(draggingHuePicker);
            if (bounds != null) {
                float[] hsv = colorSettings.getOrDefault(draggingHuePicker, new float[]{0f, 1f, 1f});
                float hue = Math.max(0.0f, Math.min(1.0f, (mx - bounds[0]) / (float) bounds[2]));
                colorSettings.put(draggingHuePicker, new float[]{hue, hsv[1], hsv[2]});
            }
        }
    }

    private int drawBindButton(DrawContext context, TextRenderer tr, String actionBindName, String label, int x, int y, int width, int mx, int my, float fadeAlpha) {
        int alphaInt = (int)(fadeAlpha * 255);
        if (alphaInt < 5) return y + 25;

        float scroll = getScroll();
        clickBounds.put("bindclick_" + actionBindName, new int[]{x + 10, (int)(y + scroll), width - 20, 20});
        boolean bindHovered = mx >= x + 10 && mx <= x + width - 10 && my >= y + scroll && my <= y + scroll + 20;

        drawSmoothRect(context, x + 10, y, width - 20, 20, (alphaInt << 24) | (bindHovered ? 0x2A2A38 : 0x1A1A20));

        String bindTextStr = label + ": ";
        if (bindingModule != null && bindingModule.equals(actionBindName)) {
            bindTextStr += "Ожидание...";
        } else {
            int keyCode = numSettings.getOrDefault(actionBindName, -1f).intValue();
            if (keyCode != -1) {
                if (keyCode < 0) {
                    // Это кнопка мыши!
                    bindTextStr += "[M" + Math.abs(keyCode) + "]";
                } else {
                    // Это кнопка клавиатуры
                    bindTextStr += "[" + InputUtil.fromKeyCode(keyCode, -1).getLocalizedText().getString().toUpperCase() + "]";
                }
            } else {
                bindTextStr += "[НЕТ]";
            }
        }
        context.drawText(tr, Text.literal(bindTextStr).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), x + 15, y + 6, (alphaInt << 24) | 0xDDDDDD, false);

        return y + 25;
    }

    private int draw2DPad(DrawContext context, TextRenderer tr, String nameX, String nameY, String label, float minX, float maxX, float minY, float maxY, int x, int y, int width, int mx, int my, float fadeAlpha) {
        int alphaInt = (int) (fadeAlpha * 255);
        if (alphaInt < 5) return y + 70;

        int padSize = 60;
        int padX = x + width - padSize - 10;
        int padY = y;

        context.drawText(tr, Text.literal(label).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), x + 12, y + 10, (alphaInt << 24) | 0xDDDDDD, false);

        float valX = numSettings.getOrDefault(nameX, 0.0f);
        float valY = numSettings.getOrDefault(nameY, 0.0f);

        String padKey = nameX + "|" + nameY;
        clickBounds.put("pad_" + padKey, new int[]{padX, (int)(padY + getScroll()), padSize, padSize});

        drawSmoothRect(context, padX, padY, padSize, padSize, (alphaInt << 24) | 0x22222A);

        int gridCol = (alphaInt << 24) | 0x444455;
        context.fill(padX + padSize/2, padY, padX + padSize/2 + 1, padY + padSize, gridCol);
        context.fill(padX, padY + padSize/2, padX + padSize, padY + padSize/2 + 1, gridCol);

        float curPctX = (valX - minX) / (maxX - minX);
        float curPctY = (maxY - valY) / (maxY - minY);
        int pointX = padX + (int)(curPctX * padSize);
        int pointY = padY + (int)(curPctY * padSize);

        int dotColor = (alphaInt << 24) | (getGuiThemeColor() & 0xFFFFFF);
        context.fill(pointX - 1, pointY - 2, pointX + 2, pointY + 3, dotColor);
        context.fill(pointX - 2, pointY - 1, pointX + 3, pointY + 2, dotColor);

        String vStr = String.format("X: %.2f Y: %.2f", valX, valY);
        context.drawText(tr, Text.literal(vStr).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), x + 12, y + 25, (alphaInt << 24) | 0xAAAAAA, false);

        return y + padSize + 10;
    }

    private int drawMiniMode(DrawContext context, TextRenderer tr, String name, int x, int y, int width, int mx, int my, float fadeAlpha) {
        int alphaInt = (int) (fadeAlpha * 255);
        if (alphaInt < 5) return y + 25;

        String currentVal = modeSettings.getOrDefault(name, "Default");
        if (name.equals("Swap Mode")) currentVal = modeSettings.getOrDefault(name, "Двойной");
        if (name.equals("Swap From")) currentVal = modeSettings.getOrDefault(name, "Тотем");
        if (name.equals("Swap To")) currentVal = modeSettings.getOrDefault(name, "Шар");
        if (name.equals("Edit Hand")) currentVal = modeSettings.getOrDefault(name, "Right");
        if (name.equals("Hit Sound Mode")) currentVal = modeSettings.getOrDefault(name, "Bubble");
        if (name.equals("Part. Texture")) currentVal = modeSettings.getOrDefault(name, "Star");
        if (name.equals("Hit Color Mode")) currentVal = modeSettings.getOrDefault(name, "Theme");
        if (name.equals("Hit Target")) currentVal = modeSettings.getOrDefault(name, "All");
        if (name.equals("Overlay Mode")) currentVal = modeSettings.getOrDefault(name, "Web");
        if (name.equals("Hand Mode")) currentVal = modeSettings.getOrDefault(name, "Snow");
        if (name.equals("VM Anim")) currentVal = modeSettings.getOrDefault(name, "Standard");
        if (name.equals("Zoom Mode")) currentVal = modeSettings.getOrDefault(name, "Hold");

        if (name.equals("FT Трапка Скин") || name.equals("FT Пласт Скин")) {
            currentVal = modeSettings.getOrDefault(name, "Обычный");
        }

        String text = t(name).replace("Part. ", "") + ": " + tMode(currentVal);

        clickBounds.put("minimode_" + name, new int[]{x + 10, (int)(y + getScroll()), width - 20, 20});
        boolean isHovered = mx >= x + 10 && mx <= x + width - 10 && my >= y + getScroll() && my <= y + getScroll() + 20;

        drawSmoothRect(context, x + 10, y, width - 20, 20, (alphaInt << 24) | (isHovered ? 0x2A2A38 : 0x1A1A20));
        context.drawText(tr, Text.literal(text).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), x + 15, y + 6, (alphaInt << 24) | 0xDDDDDD, false);

        return y + 25;
    }

    private int drawMiniToggle(DrawContext context, TextRenderer tr, String rawName, String displayName, int x, int y, int width, float fadeAlpha) {
        int alphaInt = (int) (fadeAlpha * 255);
        boolean state = moduleStates.getOrDefault(rawName, true);
        float anim = toggleAnimations.getOrDefault("tog_" + rawName, state ? 1.0f : 0.0f);
        anim += ((state ? 1.0f : 0.0f) - anim) * 0.2f;
        toggleAnimations.put("tog_" + rawName, anim);

        clickBounds.put("minitog_" + rawName, new int[]{x + 10, (int)(y + getScroll()), width - 20, 20});
        context.drawText(tr, Text.literal(displayName).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), x + 12, y + 6, (alphaInt << 24) | 0xDDDDDD, false);

        int togColor = blendColors(0xFF1A1A20, getGuiThemeColor() | 0xFF000000, anim);
        drawSmoothRect(context, x + width - 40, y + 2, 30, 16, (alphaInt << 24) | (togColor & 0xFFFFFF));
        drawSmoothRect(context, x + width - 38 + (int) (anim * 14), y + 4, 12, 12, (alphaInt << 24) | 0xFFFFFFFF);
        return y + 25;
    }

    private int drawMiniSlider(DrawContext context, TextRenderer tr, String name, float min, float max, int x, int y, int width, int mx, int my, float fadeAlpha) {
        float val = numSettings.getOrDefault(name, min);
        float percent = Math.max(0.0f, Math.min(1.0f, (val - min) / (max - min)));
        int alphaInt = (int) (fadeAlpha * 255);

        String displayVal;
        if (name.contains("Count") || name.contains("Life") || name.contains("Length") || name.contains("Volume") || name.contains("Chance") || name.contains("Ticks") || name.contains("Таймер")) {
            displayVal = String.valueOf((int) val);
        } else {
            displayVal = String.valueOf(Math.round(val * 10.0) / 10.0);
        }

        context.drawText(tr, Text.literal(t(name).replace("Part. ", "") + ": " + displayVal).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), x + 12, y, (alphaInt << 24) | 0xDDDDDD, false);

        int sliderY = y + 15;
        int sliderW = width - 24;

        clickBounds.put("slider_click_" + name, new int[]{x, (int)(y + getScroll()), width, 30});
        sliderDimensions.put(name, new int[]{x + 12, sliderW});
        sliderMathBounds.put(name, new float[]{min, max});

        drawSmoothRect(context, x + 12, sliderY, sliderW, 6, (alphaInt << 24) | 0x1A1A20);
        drawSmoothRect(context, x + 12, sliderY, (int) (sliderW * percent), 6, (alphaInt << 24) | (getGuiThemeColor() & 0xFFFFFF));
        drawSmoothRect(context, x + 12 + (int) (sliderW * percent) - 6, sliderY - 3, 12, 12, (alphaInt << 24) | 0xFFFFFFFF);

        return y + 30;
    }

    private int drawMicroToggle(DrawContext context, TextRenderer tr, String rawName, String displayName, int x, int y, int width, float fadeAlpha) {
        int alphaInt = (int) (fadeAlpha * 255);
        boolean state = moduleStates.getOrDefault(rawName, true);
        float anim = toggleAnimations.getOrDefault("tog_" + rawName, state ? 1.0f : 0.0f);
        anim += ((state ? 1.0f : 0.0f) - anim) * 0.2f;
        toggleAnimations.put("tog_" + rawName, anim);

        clickBounds.put("minitog_" + rawName, new int[]{x + 5, (int)(y + getScroll()), width - 10, 20});

        context.getMatrices().push();
        context.getMatrices().translate(x + 8, y + 6, 0);
        context.getMatrices().scale(0.8f, 0.8f, 1.0f);
        context.drawText(tr, Text.literal(displayName).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), 0, 0, (alphaInt << 24) | 0xDDDDDD, false);
        context.getMatrices().pop();

        int togColor = blendColors(0xFF1A1A20, getGuiThemeColor() | 0xFF000000, anim);
        drawSmoothRect(context, x + width - 26, y + 4, 22, 12, (alphaInt << 24) | (togColor & 0xFFFFFF));
        drawSmoothRect(context, x + width - 24 + (int) (anim * 10), y + 6, 8, 8, (alphaInt << 24) | 0xFFFFFFFF);
        return y + 25;
    }

    private int drawMicroSlider(DrawContext context, TextRenderer tr, String name, String shortName, float min, float max, int x, int y, int width, int mx, int my, float fadeAlpha) {
        float val = numSettings.getOrDefault(name, min);
        float percent = Math.max(0.0f, Math.min(1.0f, (val - min) / (max - min)));
        int alphaInt = (int) (fadeAlpha * 255);

        String displayVal;
        if (name.contains("Count") || name.contains("Life") || name.contains("Length") || name.contains("Volume") || name.contains("Chance")) {
            displayVal = String.valueOf((int) val);
        } else {
            displayVal = String.valueOf(Math.round(val * 10.0) / 10.0);
        }

        context.getMatrices().push();
        context.getMatrices().translate(x + 8, y + 2, 0);
        context.getMatrices().scale(0.8f, 0.8f, 1.0f);
        context.drawText(tr, Text.literal(shortName + ": " + displayVal).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), 0, 0, (alphaInt << 24) | 0xDDDDDD, false);
        context.getMatrices().pop();

        int sliderY = y + 13;
        int sliderW = width - 16;

        clickBounds.put("slider_click_" + name, new int[]{x, (int)(y + getScroll()), width, 25});
        sliderDimensions.put(name, new int[]{x + 8, sliderW});
        sliderMathBounds.put(name, new float[]{min, max});

        drawSmoothRect(context, x + 8, sliderY, sliderW, 5, (alphaInt << 24) | 0x1A1A20);
        drawSmoothRect(context, x + 8, sliderY, (int) (sliderW * percent), 5, (alphaInt << 24) | (getGuiThemeColor() & 0xFFFFFF));
        drawSmoothRect(context, x + 8 + (int) (sliderW * percent) - 4, sliderY - 2, 8, 9, (alphaInt << 24) | 0xFFFFFFFF);

        return y + 25;
    }

    private int drawTargetESPSettings(DrawContext context, TextRenderer tr, int x, int y, int width, int mx, int my, float fadeAlpha, boolean isSpirits, boolean isRhombus) {
        int currentY = y;
        int alphaInt = (int) (fadeAlpha * 255);
        if (alphaInt < 5) return currentY;

        List<String> modes = Arrays.asList("Spirits", "Crystals", "Rhombus", "Round Rhombus");
        String currentMode = modeSettings.getOrDefault("Target ESP Mode", "Spirits");
        int btnW = (width - 30) / 2;
        int btnX = x + 10;

        for (int i = 0; i < modes.size(); i++) {
            String mode = modes.get(i);
            boolean isSelected = mode.equals(currentMode);
            clickBounds.put("gridmode_" + mode, new int[]{btnX, (int)(currentY + getScroll()), btnW, 20});
            boolean isHovered = mx >= btnX && mx <= btnX + btnW && my >= currentY + getScroll() && my <= currentY + getScroll() + 20;

            int bgColor = isSelected ? (getGuiThemeColor() | 0xFF000000) : (isHovered ? 0xFF35354A : 0xFF1A1A20);
            drawSmoothRect(context, btnX, currentY, btnW, 20, (alphaInt << 24) | (bgColor & 0xFFFFFF));

            Text modeText = Text.literal(tMode(mode)).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));

            context.getMatrices().push();
            context.getMatrices().translate(btnX + btnW / 2f, currentY + 6, 0);
            context.getMatrices().scale(0.85f, 0.85f, 1.0f);
            context.drawText(tr, modeText, -MinecraftClient.getInstance().textRenderer.getWidth(modeText) / 2, 0, (alphaInt << 24) | (isSelected ? 0xFF101010 : 0xFFFFFFFF), false);
            context.getMatrices().pop();

            btnX += btnW + 10;
            if ((i + 1) % 2 == 0) {
                btnX = x + 10;
                currentY += 25;
            }
        }
        currentY += 10;

        currentY = drawMiniToggle(context, tr, "Red On Damage", t("Red On Damage"), x, currentY, width, fadeAlpha);

        if (moduleStates.getOrDefault("Red On Damage", true)) {
            currentY = drawMiniToggle(context, tr, "Only On Crit", "  > " + t("Only On Crit"), x, currentY, width, fadeAlpha);
        }

        currentY = drawMiniSlider(context, tr, "ESP Speed", 0.5f, 3.0f, x, currentY, width, mx, my, fadeAlpha);

        if (isSpirits) {
            currentY = drawMiniSlider(context, tr, "Spirits Count", 1.0f, 10.0f, x, currentY, width, mx, my, fadeAlpha);
            currentY = drawMiniSlider(context, tr, "Trail Length", 20.0f, 60.0f, x, currentY, width, mx, my, fadeAlpha);
        } else if (isRhombus) {
            currentY = drawMiniSlider(context, tr, "Rhombus Size", 0.5f, 3.0f, x, currentY, width, mx, my, fadeAlpha);
        }
        return currentY;
    }

    private int drawColorPicker(DrawContext context, TextRenderer tr, String name, int x, int y, int width, int mx, int my, float fadeAlpha) {
        int alphaInt = (int) (fadeAlpha * 255);
        if (alphaInt < 5) return y + 100;

        context.drawText(tr, Text.literal(t(name)).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), x + 12, y, (alphaInt << 24) | 0xDDDDDD, false);

        int pickerY = y + 15;
        int pickerW = width - 24;
        int pickerH = 60;
        int hueH = 8;

        float[] hsv = colorSettings.getOrDefault(name, new float[]{0f, 1f, 1f});
        colorPickerBounds.put(name, new int[]{x + 12, (int)(pickerY + getScroll()), pickerW, pickerH});

        for (int i = 0; i < pickerW; i++) {
            for (int j = 0; j < pickerH; j++) {
                float sat = i / (float) pickerW;
                float val = 1.0f - (j / (float) pickerH);
                int color = Color.HSBtoRGB(hsv[0], sat, val);
                context.fill(x + 12 + i, pickerY + j, x + 12 + i + 1, pickerY + j + 1, (alphaInt << 24) | (color & 0xFFFFFF));
            }
        }

        int pointX = x + 12 + (int) (hsv[1] * pickerW);
        int pointY = pickerY + (int) ((1.0f - hsv[2]) * pickerH);
        context.fill(pointX - 2, pointY - 2, pointX + 2, pointY + 2, (alphaInt << 24) | 0xFFFFFFFF);
        context.fill(pointX - 1, pointY - 1, pointX + 1, pointY + 1, (alphaInt << 24) | 0xFF000000);

        int hueY = pickerY + pickerH + 5;
        huePickerBounds.put(name, new int[]{x + 12, (int)(hueY + getScroll()), pickerW, hueH});

        for (int i = 0; i < pickerW; i++) {
            float hue = i / (float) pickerW;
            int color = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            context.fill(x + 12 + i, hueY, x + 12 + i + 1, hueY + hueH, (alphaInt << 24) | (color & 0xFFFFFF));
        }

        int huePointX = x + 12 + (int) (hsv[0] * pickerW);
        context.fill(huePointX - 2, hueY - 1, huePointX + 2, hueY + hueH + 1, (alphaInt << 24) | 0xFFFFFFFF);

        return hueY + hueH + 10;
    }

    private int drawCategory(DrawContext context, TextRenderer tr, String cat, int x, int catY, int mx, int my, boolean isMain) {
        boolean isSelected = cat.equals(currentCategory);
        boolean isHovered = expandedModule == null && mx >= x + 15 && mx <= x + 135 && my >= catY && my <= catY + 35;

        float hoverTarget = isHovered || isSelected ? 1.0f : 0.0f;
        float currentHover = hoverAnimations.getOrDefault("cat_" + cat, 0.0f);
        hoverAnimations.put("cat_" + cat, currentHover + (hoverTarget - currentHover) * 0.2f);

        int finalX = x + 15 + (int) (currentHover * 5.0f);

        if (expandedModule == null) {
            clickBounds.put("cat_" + cat, new int[]{finalX, catY, 120, 35});
        }

        drawSmoothRect(context, finalX, catY, 120, 35, blendColors(0xFF1A1A1E, 0xFF282835, currentHover));

        Identifier catIcon = null;
        if (cat.equals("HUD")) catIcon = ICON_HUD;
        else if (cat.equals("Visual")) catIcon = ICON_VISUAL;
        else if (cat.equals("Utils")) catIcon = ICON_UTILS;

        int textColor = isSelected || isHovered ? 0xFFFFFFFF : (isMain ? 0xFFDDDDDD : 0xFF888888);
        int textX = finalX + 15;

        if (catIcon != null) {
            drawColoredTexturedQuad(context, catIcon, finalX + 10, catY + 12, 11, 11, 0.0f, 0.0f, 1.0f, 1.0f, 255, 255, 255, (textColor >> 24) & 0xFF);
            textX += 18;
        }

        context.drawText(tr, Text.literal(cat).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), textX, catY + 13, textColor, false);

        return catY + 40;
    }

    private void drawConfigMenu(DrawContext context, TextRenderer tr, int startX, int startY, int width, int mx, int my) {
        int currentY = startY;

        context.drawText(tr, Text.literal("Создать Новый Конфиг").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), startX, currentY, 0xFFDDDDDD, false);
        currentY += 15;

        int inputW = 220;
        int inputH = 30;
        boolean inputHovered = mx >= startX && mx <= startX + inputW && my >= currentY && my <= currentY + inputH;
        clickBounds.put("config_input", new int[]{startX, currentY, inputW, inputH});
        drawSmoothRect(context, startX, currentY, inputW, inputH, inputHovered || isTypingConfig ? 0xFF2A2A38 : 0xFF1E1E24);

        String displayText = configInputText + (isTypingConfig && (System.currentTimeMillis() % 1000 > 500) ? "_" : "");
        context.drawText(tr, Text.literal(displayText).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), startX + 10, currentY + 11, 0xFFFFFFFF, false);

        int btnX = startX + inputW + 10;
        int btnW = 80;
        boolean btnHovered = mx >= btnX && mx <= btnX + btnW && my >= currentY && my <= currentY + inputH;
        clickBounds.put("config_create", new int[]{btnX, currentY, btnW, inputH});

        drawSmoothRect(context, btnX, currentY, btnW, inputH, btnHovered ? getGuiThemeColor() | 0xFF000000 : 0xFF2A2A38);
        context.drawText(tr, Text.literal("Создать").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), btnX + 22, currentY + 11, 0xFFFFFFFF, false);

        currentY += 50;
        context.drawText(tr, Text.literal("Сохраненные Конфиги (" + savedConfigs.size() + ")").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), startX, currentY, 0xFFDDDDDD, false);
        currentY += 15;

        for (String cfg : savedConfigs) {
            drawSmoothRect(context, startX, currentY, width - 20, 35, 0xFF1E1E24);
            context.drawText(tr, Text.literal(cfg).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), startX + 15, currentY + 14, 0xFFFFFFFF, false);

            int loadX = startX + width - 150;
            boolean loadHov = mx >= loadX && mx <= loadX + 60 && my >= currentY + 5 && my <= currentY + 30;
            clickBounds.put("config_load_" + cfg, new int[]{loadX, currentY + 5, 60, 25});
            drawSmoothRect(context, loadX, currentY + 5, 60, 25, loadHov ? 0xFF44AA44 : 0xFF2A2A38);
            context.drawText(tr, Text.literal("Загрузить").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), loadX + 7, currentY + 14, 0xFFFFFFFF, false);

            int delX = startX + width - 80;
            boolean delHov = mx >= delX && mx <= delX + 60 && my >= currentY + 5 && my <= currentY + 30;
            clickBounds.put("config_del_" + cfg, new int[]{delX, currentY + 5, 60, 25});
            drawSmoothRect(context, delX, currentY + 5, 60, 25, delHov ? 0xFFAA4444 : 0xFF2A2A38);
            context.drawText(tr, Text.literal("Удалить").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), delX + 12, currentY + 14, 0xFFFFFFFF, false);

            currentY += 45;
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (isTypingConfig) {
            if (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-' || chr == ' ') {
                configInputText += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTypingConfig) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !configInputText.isEmpty()) {
                configInputText = configInputText.substring(0, configInputText.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isTypingConfig = false;
            }
            return true;
        }

        if (bindingModule != null) {
            // Если нажали Esc, Backspace или Delete — сбрасываем бинд
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                if (bindingModule.endsWith(" Action") || bindingModule.startsWith("Bind_")) {
                    numSettings.put(bindingModule, -1f); // -1 значит НЕТ бинда
                } else {
                    moduleBinds.remove(bindingModule);
                }
            } else {
                // Если нажали любую другую кнопку клавиатуры — сохраняем её
                if (bindingModule.endsWith(" Action") || bindingModule.startsWith("Bind_")) {
                    numSettings.put(bindingModule, (float) keyCode);
                } else {
                    moduleBinds.put(bindingModule, keyCode);
                }
            }
            bindingModule = null;
            ConfigManager.saveConfig();
            return true;
        }

        // Закрытие окна по Esc, если открыты настройки
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && expandedModule != null) {
            expandedModule = null;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 🔥 ФИКС: Записываем нажатия мыши (ЛКМ, ПКМ, боковые кнопки M4, M5 и т.д.)
        if (bindingModule != null) {
            // Сохраняем кнопки мыши как отрицательные числа, чтобы отличать от клавиатуры.
            // GLFW: ЛКМ = 0, ПКМ = 1, СКМ = 2, M4 = 3, M5 = 4.
            // Делаем -(button + 1), получаем: ЛКМ = -1, ПКМ = -2, M4 = -4 и т.д.
            int bindCode = -(button + 1);

            if (bindingModule.endsWith(" Action") || bindingModule.startsWith("Bind_")) {
                numSettings.put(bindingModule, (float) bindCode);
            } else {
                moduleBinds.put(bindingModule, bindCode);
            }
            bindingModule = null;
            ConfigManager.saveConfig();
            return true; // Завершаем клик, чтобы не нажать ничего лишнего
        }

        float scaleFactor = (float) MinecraftClient.getInstance().getWindow().getScaleFactor() / 2.0f;
        int mx = (int) (mouseX * scaleFactor);
        int my = (int) (mouseY * scaleFactor);

        if (button == 0) {
            boolean clickedInput = false;

            for (Map.Entry<String, int[]> entry : clickBounds.entrySet()) {
                if (entry.getKey().startsWith("pad_")) {
                    int[] b = entry.getValue();
                    if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                        draggingPad = entry.getKey().replace("pad_", "");
                        isTypingConfig = false;
                        return true;
                    }
                }
            }

            for (Map.Entry<String, int[]> entry : huePickerBounds.entrySet()) {
                int[] b = entry.getValue();
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    draggingHuePicker = entry.getKey();
                    isTypingConfig = false;
                    return true;
                }
            }

            int[] closeB = clickBounds.get("close_popup");
            if (closeB != null && mx >= closeB[0] && mx <= closeB[0] + closeB[2] && my >= closeB[1] && my <= closeB[1] + closeB[3]) {
                expandedModule = null;
                activeColorPickerItem = null;
                isTypingConfig = false;
                return true;
            }

            // Обработка кликов новой HSV палитры (Если она открыта)
            if (activeColorPickerItem != null) {
                int[] sv = clickBounds.get("palette_sv");
                if (sv != null && mx >= sv[0] && mx <= sv[0] + sv[2] && my >= sv[1] && my <= sv[1] + sv[3]) {
                    draggingItemSV = true; return true;
                }
                int[] hue = clickBounds.get("palette_hue");
                if (hue != null && mx >= hue[0] && mx <= hue[0] + hue[2] && my >= hue[1] && my <= hue[1] + hue[3]) {
                    draggingItemHue = true; return true;
                }
                int[] bg = clickBounds.get("palette_bg");
                if (bg != null && mx >= bg[0] && mx <= bg[0] + bg[2] && my >= bg[1] && my <= bg[1] + bg[3]) {
                    return true; // Кликнули просто по фону палитры, не закрываем
                }
                // Если кликнули вообще мимо палитры - закрываем её
                activeColorPickerItem = null;
                return true;
            }

            // Обработка кликов по тумблерам списка предметов
            for (Map.Entry<String, int[]> entry : clickBounds.entrySet()) {
                if (entry.getKey().startsWith("highlighter_")) {
                    int[] b = entry.getValue();
                    if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                        String name = entry.getKey().replace("highlighter_tog_", "").replace("highlighter_col_", "");
                        Item targetItem = null;

                        for (Map.Entry<Item, String> itemEntry : ItemHighlighter.ITEM_NAMES.entrySet()) {
                            if (itemEntry.getValue().equals(name)) { targetItem = itemEntry.getKey(); break; }
                        }

                        if (targetItem != null) {
                            if (entry.getKey().startsWith("highlighter_tog_")) {
                                boolean cur = ItemHighlighter.isItemEnabled(targetItem);
                                boolean newVal = !cur;
                                ClientData.moduleStates.put("HL_" + name, newVal);
                                ItemHighlighter.ItemConfig config = ItemHighlighter.ITEM_CONFIGS.get(targetItem);
                                if (config != null) config.enabled = newVal;
                                ConfigManager.saveConfig();
                            } else if (entry.getKey().startsWith("highlighter_col_")) {
                                // Открываем палитру и конвертируем текущий цвет в HSV для ползунков
                                activeColorPickerItem = targetItem;
                                colorPickerX = mx;
                                colorPickerY = my;
                                int c = ItemHighlighter.getItemColor(targetItem);
                                java.awt.Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, tempItemHSV);
                            }
                        }
                        return true;
                    }
                }
            }

            for (Map.Entry<String, int[]> entry : colorPickerBounds.entrySet()) {
                int[] b = entry.getValue();
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    draggingColorPicker = entry.getKey();
                    isTypingConfig = false;
                    return true;
                }
            }

            for (Map.Entry<String, int[]> entry : huePickerBounds.entrySet()) {
                int[] b = entry.getValue();
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    draggingHuePicker = entry.getKey();
                    isTypingConfig = false;
                    return true;
                }
            }

            for (Map.Entry<String, int[]> entry : clickBounds.entrySet()) {
                int[] b = entry.getValue();
                boolean inside = mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];

                if (inside) {
                    if (entry.getKey().equals("config_input")) {
                        clickedInput = true;
                        isTypingConfig = true;
                        return true;
                    }
                    if (entry.getKey().equals("config_create")) {
                        if (!configInputText.isEmpty() && !savedConfigs.contains(configInputText)) {
                            savedConfigs.add(configInputText);
                            ConfigManager.saveConfig(configInputText);
                            configInputText = "";
                            isTypingConfig = false;
                        }
                        return true;
                    }
                    if (entry.getKey().startsWith("config_load_")) {
                        String cfg = entry.getKey().replace("config_load_", "");
                        ConfigManager.loadConfig(cfg);
                        return true;
                    }
                    if (entry.getKey().startsWith("config_del_")) {
                        String cfg = entry.getKey().replace("config_del_", "");
                        ConfigManager.deleteConfig(cfg);
                        return true;
                    }
                    if (entry.getKey().startsWith("slider_click_")) {
                        draggingSlider = entry.getKey().replace("slider_click_", "");
                        isTypingConfig = false;
                        return true;
                    }
                    if (entry.getKey().startsWith("gridmode_")) {
                        modeSettings.put("Target ESP Mode", entry.getKey().replace("gridmode_", ""));
                        ConfigManager.saveConfig();
                        isTypingConfig = false;
                        return true;
                    }
                    if (entry.getKey().startsWith("minimode_")) {
                        String name = entry.getKey().replace("minimode_", "");

                        if (name.equals("Swap Mode")) {
                            String current = modeSettings.getOrDefault(name, "Двойной");
                            String next = current.equals("Двойной") ? "Тройной" : "Двойной";
                            modeSettings.put(name, next);
                            ClientData.modeSettings.put(name, next);
                            ConfigManager.saveConfig();
                        }
                        if (name.equals("Swap From") || name.equals("Swap To")) {
                            String current = modeSettings.getOrDefault(name, "Тотем");
                            String next = current.equals("Тотем") ? "Шар" : (current.equals("Шар") ? "Щит" : "Тотем");
                            modeSettings.put(name, next);
                            ClientData.modeSettings.put(name, next);
                            ConfigManager.saveConfig();
                        }
                        if (name.equals("Nimb Color Mode")) {
                            String current = modeSettings.getOrDefault(name, "Theme");
                            modeSettings.put(name, current.equals("Theme") ? "Custom" : "Theme");
                        }
                        if (name.equals("Shift Mode")) {
                            String current = modeSettings.getOrDefault(name, "Все удары");
                            modeSettings.put(name, current.equals("Все удары") ? "Только криты" : "Все удары");
                        }

                        if (name.equals("Leave Mode")) {
                            String current = modeSettings.getOrDefault(name, "Disconnect");
                            modeSettings.put(name, current.equals("Disconnect") ? "Hub" : "Disconnect");
                        }
                        if (name.equals("Hitbox Style")) {
                            String[] arr = {"Solid", "Nebula", "Water", "Flame"};
                            int idx = Arrays.asList(arr).indexOf(modeSettings.getOrDefault(name, "Solid"));
                            modeSettings.put(name, arr[(idx + 1) % arr.length]);
                        }
                        if (name.equals("Sky Color Mode") || name.equals("Fog Color Mode")) {
                            String current = modeSettings.getOrDefault(name, "Theme");
                            modeSettings.put(name, current.equals("Theme") ? "Custom" : "Theme");
                        }
                        if (name.equals("Weather Mode")) {
                            String[] arr = {"Clear", "Rain", "Thunder"};
                            int idx = Arrays.asList(arr).indexOf(modeSettings.getOrDefault(name, "Clear"));
                            modeSettings.put(name, arr[(idx + 1) % arr.length]);
                        }
                        if (name.equals("VM Anim")) {
                            String[] arr = {
                                    "Standard",
                                    "Под наклоном",
                                    "Наклон",
                                    "Вращение на 360",
                                    "От себя",
                                    "Боньк"
                            };
                            int idx = Arrays.asList(arr).indexOf(modeSettings.getOrDefault(name, "Standard"));
                            if (idx < 0) idx = 0;
                            modeSettings.put(name, arr[(idx + 1) % arr.length]);
                        }

                        if (name.equals("Overlay Mode")) {
                            String[] arr = {"Web", "Plasma", "Grid"};
                            int idx = Arrays.asList(arr).indexOf(modeSettings.getOrDefault(name, "Web"));
                            modeSettings.put(name, arr[(idx + 1) % arr.length]);
                        }

                        if (name.equals("Hand Mode")) {
                            String[] arr = {"Nebula", "Stars", "Web", "Plasma", "Fire", "Smoke", "Snow", "Stripes", "Solid"};
                            int idx = Arrays.asList(arr).indexOf(modeSettings.getOrDefault(name, "Nebula"));
                            if (idx < 0) idx = 0;
                            modeSettings.put(name, arr[(idx + 1) % arr.length]);
                        }

                        if (name.equals("Hand Color Mode")) {
                            String current = modeSettings.getOrDefault(name, "Theme");
                            modeSettings.put(name, current.equals("Theme") ? "Custom" : "Theme");
                        }

                        if (name.equals("FT Трапка Скин") || name.equals("FT Пласт Скин")) {
                            String current = modeSettings.getOrDefault(name, "Обычный");
                            modeSettings.put(name, current.equals("Обычный") ? "Драконий" : "Обычный");
                        }

                        if (name.equals("Ratio Mode")) {
                            String current = modeSettings.getOrDefault(name, "Default");
                            if (current.equals("Default")) modeSettings.put(name, "4:3");
                            else if (current.equals("4:3")) modeSettings.put(name, "16:9");
                            else if (current.equals("16:9")) modeSettings.put(name, "16:10");
                            else if (current.equals("16:10")) modeSettings.put(name, "Custom");
                            else if (current.equals("Custom")) modeSettings.put(name, "Default");
                        }

                        if (name.equals("Edit Hand")) {
                            String current = modeSettings.getOrDefault(name, "Right");
                            modeSettings.put(name, current.equals("Right") ? "Left" : "Right");
                        }

                        if (name.equals("Hit Sound Mode")) {
                            String[] hitSounds = {
                                    "Crime",
                                    "Bubble",
                                    "Metallic",
                                    "Bell",
                                    "Bonk"
                            };

                            String current = modeSettings.getOrDefault(name, hitSounds[0]);
                            int index = 0;

                            for (int i = 0; i < hitSounds.length; i++) {
                                if (hitSounds[i].equals(current)) {
                                    index = i;
                                    break;
                                }
                            }

                            modeSettings.put(name, hitSounds[(index + 1) % hitSounds.length]);
                        }

                        if (name.equals("Part. Texture")) {
                            String[] arr = {"Bloom", "Star", "Heart", "Dollar", "Snow", "Star 2", "Kronex", "Random", "Cube"};
                            int idx = Arrays.asList(arr).indexOf(modeSettings.getOrDefault(name, "Bloom"));
                            if (idx < 0) idx = 0;
                            modeSettings.put(name, arr[(idx + 1) % arr.length]);
                        }

                        if (name.equals("Hit Color Mode")) {
                            String current = modeSettings.getOrDefault(name, "Theme");
                            modeSettings.put(name, current.equals("Theme") ? "Custom" : "Theme");
                        }

                        if (name.equals("Trail Color Mode")) {
                            String current = modeSettings.getOrDefault(name, "Theme");
                            modeSettings.put(name, current.equals("Theme") ? "Custom" : "Theme");
                        }

                        if (name.equals("Hit Target")) {
                            String current = modeSettings.getOrDefault(name, "All");
                            modeSettings.put(name, current.equals("All") ? "Body Only" : "All");
                        }

                        ConfigManager.saveConfig();
                        isTypingConfig = false;
                        return true;
                    }
                    if (entry.getKey().startsWith("bindclick_")) {
                        bindingModule = entry.getKey().replace("bindclick_", "");
                        isTypingConfig = false;
                        return true;
                    }

                    if (entry.getKey().startsWith("minitog_")) {
                        String name = entry.getKey().replace("minitog_", "");
                        if (com.lexoravisauls.client.liteapi.LiteApiFeatureControl.isBlocked(name)) {
                            moduleStates.put(name, false);
                            com.lexoravisauls.client.core.ClientData.moduleStates.put(name, false);
                            com.lexoravisauls.client.utils.NotifManager.show("HolyWorld", "Функция '" + name + "' запрещена сервером!", com.lexoravisauls.client.utils.NotifManager.NotifType.ERROR);
                            com.lexoravisauls.client.utils.SoundUtil.playCustomSound("click", 1.0f);
                            return true;
                        }
                        moduleStates.put(name, !moduleStates.getOrDefault(name, false));
                        if (name.equals("No Grass") && MinecraftClient.getInstance().worldRenderer != null) {
                            MinecraftClient.getInstance().worldRenderer.reload();
                        }
                        ConfigManager.saveConfig();
                        isTypingConfig = false;
                        return true;
                    }

                    // Клик по категории слева (только если закрыто окно)
                    if (expandedModule == null && entry.getKey().startsWith("cat_")) {
                        currentCategory = entry.getKey().replace("cat_", "");
                        isTypingConfig = false;
                        targetScrollY = 0.0f;
                        scrollY = 0.0f;
                        return true;
                    }

                    // Левый клик по модулю = Включение/Выключение
                    if (expandedModule == null && entry.getKey().startsWith("mod_")) {
                        String modName = entry.getKey().replace("mod_", "");
                        if (com.lexoravisauls.client.liteapi.LiteApiFeatureControl.isBlocked(modName)) {
                            moduleStates.put(modName, false);
                            com.lexoravisauls.client.core.ClientData.moduleStates.put(modName, false);
                            com.lexoravisauls.client.utils.NotifManager.show("HolyWorld", "Функция '" + modName + "' запрещена сервером!", com.lexoravisauls.client.utils.NotifManager.NotifType.ERROR);
                            com.lexoravisauls.client.utils.SoundUtil.playCustomSound("click", 1.0f);
                            return true;
                        }
                        boolean state = !moduleStates.getOrDefault(modName, false);
                        moduleStates.put(modName, state);

                        if (state) {
                            DynamicIslandManager.notifyModEnabled(modName);
                            SoundUtil.playCustomSound("enable", 1.0f);
                        } else {
                            DynamicIslandManager.notifyModDisabled(modName);
                            SoundUtil.playCustomSound("disable", 1.0f);
                        }

                        ConfigManager.saveConfig();
                        isTypingConfig = false;
                        return true;
                    }
                }
            }
            if (!clickedInput) isTypingConfig = false;

        } else if (button == 1) {
            // Правый клик = Открытие/закрытие настроек
            for (Map.Entry<String, int[]> entry : clickBounds.entrySet()) {
                if (entry.getKey().startsWith("mod_")) {
                    int[] b = entry.getValue();
                    if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                        String modName = entry.getKey().replace("mod_", "");
                        // Открываем модальное окно для этого модуля
                        expandedModule = modName;
                        settingsScrollY = 0; // Сбрасываем скролл внутри нового окна
                        targetSettingsScrollY = 0;
                        return true;
                    }
                }
            }
        } else if (button == 2) {
            for (Map.Entry<String, int[]> entry : clickBounds.entrySet()) {
                if (entry.getKey().startsWith("mod_")) {
                    int[] b = entry.getValue();
                    if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                        bindingModule = entry.getKey().replace("mod_", "");
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (draggingSlider != null || draggingColorPicker != null || draggingHuePicker != null || draggingPad != null || draggingItemSV || draggingItemHue) {
                draggingSlider = null;
                draggingColorPicker = null;
                draggingHuePicker = null;
                draggingPad = null;
                // 🔥 СБРАСЫВАЕМ ДРАГГИНГ МИНИ-ПАЛИТРЫ
                draggingItemSV = false;
                draggingItemHue = false;

                ConfigManager.saveConfig();
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() { super.close(); ConfigManager.saveConfig(); }

    @Override
    public boolean shouldPause() { return false; }

    private int blendColors(int c1, int c2, float r) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * r) << 24) | ((int) (r1 + (r2 - r1) * r) << 16) | ((int) (g1 + (g2 - g1) * r) << 8) | (int) (b1 + (b2 - b1) * r);
    }

    private static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int color) {
        int radius = Math.min(6, Math.min(width / 2, height / 2));

        // 1. Сначала сбрасываем цвет, чтобы фон не покрасился в цвет предыдущей иконки
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 2. Рисуем тело (прямоугольники)
        context.fill(x + radius, y, x + width - radius, y + height, color);
        context.fill(x, y + radius, x + radius, y + height - radius, color);
        context.fill(x + width - radius, y + radius, x + width, y + height - radius, color);

        // 3. Рисуем закругленные углы
        if (radius > 0) {
            int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            drawColoredTexturedQuad(context, SMOOTH_CORNERS, x, y, radius, radius, 0.0F, 0.0F, 0.5F, 0.5F, r, g, b, a);
            drawColoredTexturedQuad(context, SMOOTH_CORNERS, x + width - radius, y, radius, radius, 0.5F, 0.0F, 1.0F, 0.5F, r, g, b, a);
            drawColoredTexturedQuad(context, SMOOTH_CORNERS, x, y + height - radius, radius, radius, 0.0F, 0.5F, 0.5F, 1.0F, r, g, b, a);
            drawColoredTexturedQuad(context, SMOOTH_CORNERS, x + width - radius, y + height - radius, radius, radius, 0.5F, 0.5F, 1.0F, 1.0F, r, g, b, a);
        }
    }

    private static void drawColoredTexturedQuad(DrawContext context, Identifier texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int r, int g, int b, int a) {
        if (a <= 0) return;

        // 🔥 ГЛАВНЫЙ ФИКС: Сбрасываем очередь отрисовки контекста перед рисованием текстуры
        context.draw();

        float alpha = a / 255.0f;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        // Устанавливаем цвет для текстуры
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r / 255f, g / 255f, b / 255f, alpha);

        org.joml.Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        net.minecraft.client.render.VertexConsumerProvider.Immediate bufferSource = net.minecraft.client.MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        net.minecraft.client.render.VertexConsumer vertexConsumer = bufferSource.getBuffer(net.minecraft.client.render.RenderLayer.getGuiTextured(texture));

        vertexConsumer.vertex(matrix, (float) x, (float) y, 0.0F).color(1f, 1f, 1f, 1f).texture(u0, v0);
        vertexConsumer.vertex(matrix, (float) x, (float) (y + height), 0.0F).color(1f, 1f, 1f, 1f).texture(u0, v1);
        vertexConsumer.vertex(matrix, (float) (x + width), (float) (y + height), 0.0F).color(1f, 1f, 1f, 1f).texture(u1, v1);
        vertexConsumer.vertex(matrix, (float) (x + width), (float) y, 0.0F).color(1f, 1f, 1f, 1f).texture(u1, v0);

        // Сразу просим нарисовать этот слой
        bufferSource.draw();

        // Сбрасываем цвет в белый, чтобы не испортить текст и другие элементы
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}