package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.cosmetic.CosmeticManager;
import com.lexoravisauls.client.cosmetic.geckolib.GeckolibCosmeticRenderer;
import com.lexoravisauls.client.cosmetic.model.CosmeticModel;
import com.lexoravisauls.client.events.CustomCrosshairData;
import com.lexoravisauls.client.events.DynamicIslandRenderer;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.events.ShockwaveShader;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.main_menu.LexoraMainMenu;
import com.lexoravisauls.client.party.LexoraPartyClient;
import com.lexoravisauls.client.party.LexoraPartyManager;
import com.lexoravisauls.client.utils.ConfigManager;
import com.lexoravisauls.client.utils.EventFetcher;
import com.lexoravisauls.client.utils.GPS;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ModernClickGui extends Screen {
   public static final MsdfFont SFUI = new MsdfFont(Identifier.of("lexoravisauls", "msdf_data/font.png"), Identifier.of("lexoravisauls", "msdf_data/font.json"));
   private static final Identifier CHECKMARK_TEX = Identifier.of("lexoravisauls", "textures/gui/checkmark.png");
   private static final Identifier CLOSE_BOLD_TEX = Identifier.of("lexoravisauls", "textures/gui/close_bold.png");
   public static String activeIslandPanel = null;
   public static int lastMouseX = 0;
   public static int lastMouseY = 0;
   private static final int GUI_W = 660;
   private static final int GUI_H = 360;
   private static final int SIDEBAR_W = 135;
   private static final int HEADER_H = 30;
   private static final int COL_GAP = 8;
   private static final int NUM_COLS = 3;
   public static final List<String> HUD_MODULES = List.of("Armor Status", "Potions", "Inventory HUD", "Cooldowns", "Watermark", "Keybinds", "Target HUD", "Info HUD", "Saturation HUD", "GPS", "Scoreboard HUD", "Lexora IRC", "Emotes", "Hit Indicator", "TNT Detect", "Notifications");
   public static final List<String> VISUAL_MODULES = List.of("Crosshair", "Target ESP", "Animations", "Aspect Ratio", "View Model", "Hit Sounds", "Ft Helper", "HW Helper", "China Hat", "Particles", "Jump Circles", "Item Physics", "Hit Color", "Hit Wave", "Prediction", "Full Bright", "Block Overlay", "Hand Shaders", "Trails", "Custom Hitboxes", "Nimb", "World Customizer", "AuraParticles", "Motion Clones", "Kill Effect", "Motion Blur", "Taksa", "Custom Swords", "Atmosphere", "Virtual Desktop", "Kinetic Lyrics");
   public static final List<String> UTILS_MODULES = List.of("Auto Sprint", "Item Swap", "Elytra Swap", "Fake Player", "Fast EXP", "Auto Eat", "Free Look", "Auto Respawn", "Totem Indicator", "Loot Notifier", "Auto Leave", "Shift Tap", "Fast Swap", "Item Scroller", "PvP Save", "Lock Slot", "Item Highlighter", "Healing Helper", "Streamer Mode", "No Render", "Optimization", "Zoom", "Tape Mouse", "Self Nametags", "Armor Durability", "Totem Sound");
   public static final String TAB_ALL = "All";
   public static final String TAB_HUD = "HUD";
   public static final String TAB_VISUAL = "Visual";
   public static final String TAB_UTILS = "Utils";
   public static final String TAB_COSMETICS = "Cosmetics";
   public static final String TAB_EVENTS = "Events";
   public static final String TAB_CONFIGS = "Configs";
   public static final String TAB_FRIENDS = "Friends";
   public static final String TAB_WAYPOINTS = "Waypoints";
   public static final String TAB_THEMES = "Themes";
   public static final String TAB_SETTINGS = "Settings";
   public static final String TAB_SEARCH = "Search";
   private static String savedActiveTab = "HUD";
   private static final Map<String, float[]> savedTabColScrolls = new HashMap();
   private static final Map<String, float[]> savedTabTargetColScrolls = new HashMap();
   private static final Map<String, Float> savedTabSecondaryScrolls = new HashMap();
   private static final Map<String, Float> savedTabTargetSecondaryScrolls = new HashMap();
   private String activeTab;
   private String prevTab;
   private float tabSwitchAnim;
   private float sidebarPillY;
   private float targetSidebarPillY;
   private final float[] colScrolls;
   private final float[] targetColScrolls;
   private float secondaryScroll;
   private float targetSecondaryScroll;
   private String searchInput;
   private boolean searchFocused;
   private String configInput;
   private boolean configInputFocused;
   private String selectedConfig;
   private String partyCodeInput;
   private boolean partyCodeInputFocused;
   private static GPS.GpsWaypoint wpSelected = null;
   private static String wpEditName = "";
   private static String wpEditX = "";
   private static String wpEditY = "";
   private static String wpEditZ = "";
   private static int wpFocusedField = 0;
   private String eventsFilter;
   private String activeDropdownKey;
   private List<String> activeDropdownOptions;
   private float dropdownX;
   private float dropdownY;
   private float dropdownW;
   private float dropdownAnim;
   private float dropdownScroll;
   private float targetDropdownScroll;
   private String activeColorPickerKey;
   private float colorPickerX;
   private float colorPickerY;
   private float colorPickerAnim;
   private boolean draggingSv;
   private boolean draggingHue;
   private boolean isErasingCrosshair;
   private boolean draggingCrosshair;
   private final Map<String, Float> starAnimations;
   private float langSwitchAnim;
   private GuiLocalization.Language oldLang;
   private GuiLocalization.Language targetLang;
   private float openAnim;
   private boolean closing;
   private float currentMouseX;
   private float currentMouseY;
   private final Map<String, Float> hoverAnimations;
   private final Map<String, Float> toggleAnimations;
   private final Map<String, int[]> clickBounds;
   private final Map<String, float[]> sliderBounds;
   private final Map<String, float[]> padBounds;
   private final Map<String, String> padKeyYMap;
   private final Map<String, float[]> crosshairBounds;
   private final Map<String, Runnable> buttonActions;
   private String bindingTarget;
   private String draggingSlider;
   private String draggingPad;
   private final Screen parent;
   private static int cosmeticSubTab = 0;
   private static final String[] COSMETIC_TABS = new String[]{"Все", "Плащи", "Крылья", "Тело", "Питомцы", "Шапки"};
   private static final String[] COSMETIC_TYPES = new String[]{"all", "cape", "wings", "bodywear", "pet", "hat"};

   public void setActiveTab(String tab) {
      if (tab != null) {
         this.activeTab = tab;
         savedActiveTab = tab;
         this.tabSwitchAnim = 0.0F;
      }

   }

   private void selectWaypointForEdit(GPS.GpsWaypoint wp) {
      wpSelected = wp;
      if (wp != null) {
         wpEditName = wp.name != null ? wp.name : "";
         wpEditX = String.format(Locale.ROOT, "%.0f", wp.x);
         wpEditY = String.format(Locale.ROOT, "%.0f", wp.y);
         wpEditZ = String.format(Locale.ROOT, "%.0f", wp.z);
      } else {
         wpEditName = "";
         wpEditX = "";
         wpEditY = "";
         wpEditZ = "";
      }

      wpFocusedField = 0;
   }

   private void commitWaypointEdits() {
      if (wpSelected != null) {
         if (!wpEditName.isEmpty()) {
            wpSelected.name = wpEditName;
         }

         try {
            wpSelected.x = Double.parseDouble(wpEditX.replace(",", "."));
         } catch (Exception var4) {
         }

         try {
            wpSelected.y = Double.parseDouble(wpEditY.replace(",", "."));
         } catch (Exception var3) {
         }

         try {
            wpSelected.z = Double.parseDouble(wpEditZ.replace(",", "."));
         } catch (Exception var2) {
         }

         ConfigManager.saveConfig();
      }
   }

   public ModernClickGui() {
      this((Screen)null);
   }

   public ModernClickGui(Screen parent) {
      super(Text.literal("Modern Lexora GUI"));
      this.activeTab = savedActiveTab;
      this.prevTab = savedActiveTab;
      this.tabSwitchAnim = 1.0F;
      this.sidebarPillY = 0.0F;
      this.targetSidebarPillY = 0.0F;
      this.colScrolls = new float[3];
      this.targetColScrolls = new float[3];
      this.secondaryScroll = 0.0F;
      this.targetSecondaryScroll = 0.0F;
      float[] s = (float[])savedTabColScrolls.get(savedActiveTab);
      if (s != null) {
         System.arraycopy(s, 0, this.colScrolls, 0, 3);
      }

      float[] ts = (float[])savedTabTargetColScrolls.get(savedActiveTab);
      if (ts != null) {
         System.arraycopy(ts, 0, this.targetColScrolls, 0, 3);
      }

      this.secondaryScroll = (Float)savedTabSecondaryScrolls.getOrDefault(savedActiveTab, 0.0F);
      this.targetSecondaryScroll = (Float)savedTabTargetSecondaryScrolls.getOrDefault(savedActiveTab, 0.0F);
      this.searchInput = "";
      this.searchFocused = false;
      this.configInput = "";
      this.configInputFocused = false;
      this.selectedConfig = null;
      this.partyCodeInput = "";
      this.partyCodeInputFocused = false;
      this.eventsFilter = "HolyWorld";
      this.activeDropdownKey = null;
      this.activeDropdownOptions = null;
      this.dropdownX = 0.0F;
      this.dropdownY = 0.0F;
      this.dropdownW = 0.0F;
      this.dropdownAnim = 0.0F;
      this.dropdownScroll = 0.0F;
      this.targetDropdownScroll = 0.0F;
      this.activeColorPickerKey = null;
      this.colorPickerX = 0.0F;
      this.colorPickerY = 0.0F;
      this.colorPickerAnim = 0.0F;
      this.draggingSv = false;
      this.draggingHue = false;
      this.isErasingCrosshair = false;
      this.draggingCrosshair = false;
      this.starAnimations = new HashMap();
      this.langSwitchAnim = 1.0F;
      this.oldLang = GuiLocalization.Language.RU;
      this.targetLang = GuiLocalization.Language.RU;
      this.openAnim = 0.0F;
      this.closing = false;
      this.currentMouseX = 0.0F;
      this.currentMouseY = 0.0F;
      this.hoverAnimations = new HashMap();
      this.toggleAnimations = new HashMap();
      this.clickBounds = new HashMap();
      this.sliderBounds = new HashMap();
      this.padBounds = new HashMap();
      this.padKeyYMap = new HashMap();
      this.crosshairBounds = new HashMap();
      this.buttonActions = new HashMap();
      this.bindingTarget = null;
      this.draggingSlider = null;
      this.draggingPad = null;
      this.parent = parent;
   }

   private boolean isDarkTheme() {
      return (Boolean)ClientData.moduleStates.getOrDefault("DarkTheme", true);
   }

   private void toggleTheme(float clickX, float clickY) {
      boolean dark = this.isDarkTheme();
      ClientData.moduleStates.put("DarkTheme", !dark);
      LexoraGui.moduleStates.put("DarkTheme", !dark);
      ConfigManager.saveConfig();
      playSound("click");
      ShockwaveShader.trigger(clickX, clickY, !dark);
   }

   public static void playSound(String name) {
      if (!"Off".equalsIgnoreCase((String)ClientData.modeSettings.getOrDefault("GuiSounds", "On"))) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.world != null) {
            try {
               Identifier id = Identifier.of("lexoravisauls", name);
               client.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(id), 1.0F, 1.0F));
            } catch (Exception var3) {
            }
         }
      }

   }

   public static void playModuleToggleSound(boolean enabled) {
      String mode = (String)ClientData.modeSettings.getOrDefault("ModuleSoundMode", "Default");
      String prefix = enabled ? "enable" : "disable";
      String var10000;
      switch (mode) {
         case "Sound 1" -> var10000 = "1";
         case "Sound 2" -> var10000 = "2";
         case "Sound 3" -> var10000 = "3";
         case "Sound 4" -> var10000 = "4";
         case "Sound 5" -> { playSound(enabled ? "module_enable" : "module_disable"); return; }
         default -> var10000 = "";
      }

      String suffix = var10000;
      playSound(prefix + suffix);
   }

   protected void init() {
      super.init();
      ConfigManager.updateConfigList();
      this.closing = false;
      this.openAnim = 0.0F;
      this.activeDropdownKey = null;
      this.activeColorPickerKey = null;
      this.searchFocused = false;
      this.configInputFocused = false;
      this.partyCodeInputFocused = false;
      this.bindingTarget = null;
      this.draggingSlider = null;
      this.draggingPad = null;
      this.draggingCrosshair = false;
      this.draggingSv = false;
      this.draggingHue = false;
   }

   public boolean shouldPause() {
      return false;
   }

   public void close() {
      if (!this.closing) {
         this.closing = true;
         this.bindingTarget = null;
         this.draggingSlider = null;
         this.draggingPad = null;
         this.draggingCrosshair = false;
         this.draggingSv = false;
         this.draggingHue = false;
         this.activeDropdownKey = null;
         this.activeColorPickerKey = null;
         activeIslandPanel = null;
      }

   }

   private float sanitize(float v) {
      return !Float.isNaN(v) && !Float.isInfinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : 0.0F;
   }

   private float updateHover(String key, boolean isHovered) {
      float t = (Float)this.hoverAnimations.getOrDefault(key, 0.0F);
      t += ((isHovered ? 1.0F : 0.0F) - t) * 0.25F;
      t = this.sanitize(t);
      this.hoverAnimations.put(key, t);
      return t;
   }

   private float updateToggleAnim(String key, boolean state) {
      float t = (Float)this.toggleAnimations.getOrDefault(key, state ? 1.0F : 0.0F);
      t += ((state ? 1.0F : 0.0F) - t) * 0.22F;
      t = this.sanitize(t);
      this.toggleAnimations.put(key, t);
      return t;
   }

   private float updateStarAnim(String mod) {
      float t = (Float)this.starAnimations.getOrDefault(mod, 1.0F);
      if (t < 1.0F) {
         t += (1.0F - t) * 0.2F;
         if (t > 0.99F) {
            t = 1.0F;
         }

         this.starAnimations.put(mod, t);
      }

      return t;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.currentMouseX = (float)mouseX;
      this.currentMouseY = (float)mouseY;
      lastMouseX = mouseX;
      lastMouseY = mouseY;
      if (!this.closing) {
         this.openAnim += (1.0F - this.openAnim) * 0.18F;
         if (this.openAnim > 0.999F) {
            this.openAnim = 1.0F;
         }
      } else {
         this.openAnim += (0.0F - this.openAnim) * 0.22F;
         if (this.openAnim < 0.01F) {
            if (this.parent != null) {
               this.client.setScreen(this.parent);
            } else if (this.client != null && this.client.world == null) {
               this.client.setScreen(new LexoraMainMenu());
            } else {
               this.client.setScreen((Screen)null);
            }

            return;
         }
      }

      if (this.tabSwitchAnim < 1.0F) {
         this.tabSwitchAnim += (1.0F - this.tabSwitchAnim) * 0.18F;
         if (this.tabSwitchAnim > 0.99F) {
            this.tabSwitchAnim = 1.0F;
         }
      }

      if (this.langSwitchAnim < 1.0F) {
         this.langSwitchAnim += (1.0F - this.langSwitchAnim) * 0.14F;
         if (this.langSwitchAnim > 0.99F) {
            this.langSwitchAnim = 1.0F;
         }
      }

      if (this.activeDropdownKey != null) {
         this.dropdownAnim += (1.0F - this.dropdownAnim) * 0.25F;
         this.dropdownScroll += (this.targetDropdownScroll - this.dropdownScroll) * 0.25F;
      } else {
         this.dropdownAnim += (0.0F - this.dropdownAnim) * 0.25F;
      }

      if (this.activeColorPickerKey != null) {
         this.colorPickerAnim += (1.0F - this.colorPickerAnim) * 0.25F;
      } else {
         this.colorPickerAnim += (0.0F - this.colorPickerAnim) * 0.25F;
      }

      for(int i = 0; i < 3; ++i) {
         float[] var10000 = this.colScrolls;
         var10000[i] += (this.targetColScrolls[i] - this.colScrolls[i]) * 0.25F;
      }

      this.secondaryScroll += (this.targetSecondaryScroll - this.secondaryScroll) * 0.25F;
      this.sidebarPillY += (this.targetSidebarPillY - this.sidebarPillY) * 0.25F;
      this.clickBounds.clear();
      this.sliderBounds.clear();
      this.padBounds.clear();
      this.crosshairBounds.clear();
      this.buttonActions.clear();
      int screenW = this.width;
      int screenH = this.height;
      int overlayAlpha = (int)(140.0F * this.openAnim);
      context.fill(0, 0, screenW, screenH, overlayAlpha << 24);
      float guiX = (float)(screenW - 660) / 2.0F;
      float guiY = (float)(screenH - 360) / 2.0F;
      float scale = 0.94F + 0.06F * this.openAnim;
      float scaledX = guiX + 660.0F * (1.0F - scale) / 2.0F;
      float scaledY = guiY + 360.0F * (1.0F - scale) / 2.0F;
      boolean dark = this.isDarkTheme();
      int winBg = dark ? -267580143 : -185207046;
      RoundedRectShader.draw(context, scaledX, scaledY, 660.0F * scale, 360.0F * scale, 12.0F, this.withAlpha(winBg, this.openAnim));
      this.drawSidebar(context, scaledX, scaledY, 135.0F * scale, 360.0F * scale, dark, this.openAnim);
      float contentX = scaledX + 135.0F * scale;
      float contentW = 525.0F * scale;
      this.drawTopHeader(context, contentX, scaledY, contentW, 30.0F * scale, dark, this.openAnim);
      float mainY = scaledY + 30.0F * scale;
      float mainH = 330.0F * scale;
      context.enableScissor((int)contentX, (int)mainY, (int)(contentX + contentW), (int)(mainY + mainH));
      if (!this.activeTab.equals("All") && !this.activeTab.equals("HUD") && !this.activeTab.equals("Visual") && !this.activeTab.equals("Utils") && !this.activeTab.equals("Search")) {
         if (this.activeTab.equals("Cosmetics")) {
            this.drawCosmeticsScreen(context, contentX + 12.0F, mainY + 8.0F, contentW - 24.0F, mainH - 16.0F, dark, this.openAnim);
         } else if (this.activeTab.equals("Events")) {
            this.drawEventsScreen(context, contentX + 12.0F, mainY + 8.0F, contentW - 24.0F, mainH - 16.0F, dark, this.openAnim);
         } else if (this.activeTab.equals("Themes")) {
            this.drawThemesScreen(context, contentX + 12.0F, mainY + 8.0F, contentW - 24.0F, mainH - 16.0F, dark, this.openAnim);
         } else if (this.activeTab.equals("Settings")) {
            this.drawGuiSettingsScreen(context, contentX + 12.0F, mainY + 8.0F, contentW - 24.0F, mainH - 16.0F, dark, this.openAnim);
         } else if (this.activeTab.equals("Configs")) {
            this.drawConfigsScreen(context, contentX + 12.0F, mainY + 8.0F, contentW - 24.0F, mainH - 16.0F, dark, this.openAnim);
         } else if (this.activeTab.equals("Friends")) {
            this.drawFriendsScreen(context, contentX + 12.0F, mainY + 8.0F, contentW - 24.0F, mainH - 16.0F, dark, this.openAnim);
         } else if (this.activeTab.equals("Waypoints")) {
            this.drawWaypointsScreen(context, contentX + 12.0F, mainY + 8.0F, contentW - 24.0F, mainH - 16.0F, dark, this.openAnim);
         }
      } else {
         this.drawThreeColumnModules(context, contentX + 8.0F, mainY + 6.0F, contentW - 16.0F, mainH - 12.0F, dark, this.openAnim);
      }

      context.disableScissor();
      if (this.activeDropdownKey != null && this.activeDropdownOptions != null && this.dropdownAnim > 0.01F) {
         this.drawDropdownModal(context, dark, this.openAnim * this.dropdownAnim);
      }

      if (this.activeColorPickerKey != null && this.colorPickerAnim > 0.01F) {
         this.drawColorPickerHud(context, dark, this.openAnim * this.colorPickerAnim);
      }

      if (ShockwaveShader.isRunning()) {
         ShockwaveShader.render(context, (float)screenW, (float)screenH);
      }

      if (LexoraPartyManager.activeInviteNotif != null && !this.activeTab.equals("Friends")) {
         DynamicIslandRenderer.render(context, 1.0F);
      }

   }

   private void drawSidebar(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      int barBg = dark ? -804648435 : -705958414;
      RoundedRectShader.draw(context, x, y, w, h, 12.0F, this.withAlpha(barBg, anim));
      ModernGuiIcons.draw(context, ModernGuiIcons.Icon.SPARKLE, x + 12.0F, y + 12.0F, 12.0F, this.withAlpha(dark ? -1 : -15461352, anim));
      SFUI.draw(context, "Lexora", x + 28.0F, y + 13.0F, 11.0F, this.withAlpha(dark ? -1 : -15461352, anim));
      float curY = y + 35.0F;
      int secTitleCol = dark ? -10987418 : -7697767;
      SFUI.draw(context, "Категории", x + 12.0F, curY, 8.0F, this.withAlpha(secTitleCol, anim));
      curY += 10.0F;
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "All", ModernGuiIcons.Icon.GRID, dark, anim);
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "HUD", ModernGuiIcons.Icon.MONITOR, dark, anim);
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "Visual", ModernGuiIcons.Icon.WAND, dark, anim);
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "Utils", ModernGuiIcons.Icon.WRENCH, dark, anim);
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "Cosmetics", ModernGuiIcons.Icon.SPARKLE, dark, anim);
      curY += 4.0F;
      SFUI.draw(context, "Функции", x + 12.0F, curY, 8.0F, this.withAlpha(secTitleCol, anim));
      curY += 10.0F;
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "Events", ModernGuiIcons.Icon.SPARKLE, dark, anim);
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "Configs", ModernGuiIcons.Icon.FILE, dark, anim);
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "Friends", ModernGuiIcons.Icon.USERS, dark, anim);
      curY = this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "Waypoints", ModernGuiIcons.Icon.PIN, dark, anim);
      this.drawSidebarItem(context, x + 6.0F, curY, w - 12.0F, 19.5F, "Themes", ModernGuiIcons.Icon.PIPETTE, dark, anim);
      float bottomY = y + h - 68.0F;
      this.drawSidebarItem(context, x + 6.0F, bottomY, w - 12.0F, 19.0F, "Settings", ModernGuiIcons.Icon.SETTINGS, dark, anim);
      this.drawSidebarItem(context, x + 6.0F, bottomY + 20.5F, w - 12.0F, 19.0F, "Search", ModernGuiIcons.Icon.SEARCH, dark, anim);
      float profY = y + h - 25.0F;
      String name = this.client.player != null ? this.client.player.getName().getString() : "Player";
      SFUI.draw(context, name, x + 12.0F, profY + 2.0F, 8.0F, this.withAlpha(dark ? -1118478 : -15461352, anim));
      String server = this.client.getCurrentServerEntry() != null ? this.client.getCurrentServerEntry().address : "Singleplayer";
      SFUI.draw(context, server, x + 12.0F, profY + 11.0F, 6.5F, this.withAlpha(dark ? -9408384 : -8355696, anim));
   }

   private float drawSidebarItem(DrawContext context, float x, float y, float w, float h, String tab, ModernGuiIcons.Icon icon, boolean dark, float anim) {
      boolean active = this.activeTab.equals(tab);
      boolean hovered = this.inside(this.currentMouseX, this.currentMouseY, x, y, w, h);
      float hAnim = this.updateHover("sb:" + tab, hovered);
      if (active) {
         this.targetSidebarPillY = y;
         int activePill = dark ? -1876811220 : -788529153;
         RoundedRectShader.draw(context, x, y, w, h, 5.0F, this.withAlpha(activePill, anim));
      } else if (hAnim > 0.01F) {
         int hoverPill = dark ? 1075584036 : 1356915949;
         RoundedRectShader.draw(context, x, y, w, h, 5.0F, this.withAlpha(hoverPill, anim * hAnim));
      }

      int itemTextCol = active ? (dark ? -1 : -15856110) : (dark ? -6381906 : -10592658);
      int itemIconCol = active ? (dark ? -1 : -15856110) : (dark ? -7434594 : -9539970);
      ModernGuiIcons.draw(context, icon, x + 7.0F, y + (h - 10.0F) / 2.0F, 10.0F, this.withAlpha(itemIconCol, anim));
      String label = this.getAnimatedText(tab, GuiLocalization.getCategoryText(tab));
      SFUI.draw(context, label, x + 21.0F, y + (h - 8.0F) / 2.0F + 0.5F, 8.5F, this.withAlpha(itemTextCol, anim));
      this.clickBounds.put("tab:" + tab, new int[]{(int)x, (int)y, (int)w, (int)h});
      return y + h + 1.5F;
   }

   private void drawTopHeader(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      int sepCol = dark ? 637534207 : 536870912;
      RoundedRectShader.draw(context, x, y + h - 1.0F, w, 1.0F, 0.0F, this.withAlpha(sepCol, anim));
      ModernGuiIcons.Icon catIcon = this.getTabIcon(this.activeTab);
      ModernGuiIcons.draw(context, catIcon, x + 12.0F, y + (h - 11.0F) / 2.0F, 11.0F, this.withAlpha(dark ? -1 : -15461352, anim));
      SFUI.draw(context, "|", x + 26.0F, y + (h - 10.0F) / 2.0F, 9.5F, this.withAlpha(dark ? -12237483 : -4868667, anim));
      SFUI.draw(context, this.getAnimatedText(this.activeTab, GuiLocalization.getCategoryText(this.activeTab)), x + 34.0F, y + (h - 10.0F) / 2.0F, 9.5F, this.withAlpha(dark ? -1118475 : -15461352, anim));
      float sX = x + 120.0F;
      float sY = y + 6.0F;
      float sW = 150.0F;
      float sH = 17.0F;
      int sBg = this.searchFocused ? (dark ? -14408654 : -2235925) : (dark ? 1612191778 : 1625680112);
      RoundedRectShader.draw(context, sX, sY, sW, sH, 4.0F, this.withAlpha(sBg, anim));
      ModernGuiIcons.draw(context, ModernGuiIcons.Icon.SEARCH, sX + 5.0F, sY + 4.0F, 8.5F, this.withAlpha(dark ? -8750454 : -7697766, anim));
      String sDisplay = this.searchInput.isEmpty() && !this.searchFocused ? "Поиск модулей..." : this.searchInput;
      if (this.searchFocused && System.currentTimeMillis() % 900L < 450L) {
         sDisplay = sDisplay + "|";
      }

      int sCol = this.searchInput.isEmpty() && !this.searchFocused ? (dark ? -10987416 : -7303008) : (dark ? -1 : -15461352);
      SFUI.draw(context, sDisplay, sX + 17.0F, sY + 4.5F, 7.5F, this.withAlpha(sCol, anim));
      if (!this.searchInput.isEmpty()) {
         SFUI.draw(context, "✕", sX + sW - 12.0F, sY + 4.5F, 7.0F, this.withAlpha(dark ? -7829352 : -10066314, anim));
         this.clickBounds.put("action:clear_search", new int[]{(int)(sX + sW - 14.0F), (int)sY, 14, (int)sH});
      }

      this.clickBounds.put("input:search", new int[]{(int)sX, (int)sY, (int)sW, (int)sH});
      float btnX = x + w - 22.0F;
      boolean hClose = this.inside(this.currentMouseX, this.currentMouseY, btnX, y + 6.0F, 16.0F, 16.0F);
      float hCloseAnim = this.updateHover("btn:close", hClose);
      int closeCol = this.interpolateColor(dark ? -7697766 : -10132107, -48060, hCloseAnim);
      SFUI.draw(context, "✕", btnX + 4.0F, y + 10.0F, 8.5F, this.withAlpha(closeCol, anim));
      this.clickBounds.put("action:close", new int[]{(int)btnX, (int)y + 6, 16, 16});
      btnX -= 28.0F;
      boolean hLang = this.inside(this.currentMouseX, this.currentMouseY, btnX, y + 6.0F, 24.0F, 15.0F);
      float hLangAnim = this.updateHover("btn:lang", hLang);
      int langBg = this.interpolateColor(dark ? 806885408 : 820045036, dark ? 1881482549 : 1893062882, hLangAnim);
      RoundedRectShader.draw(context, btnX, y + 6.0F, 24.0F, 15.0F, 3.5F, this.withAlpha(langBg, anim));
      String langText = GuiLocalization.getLanguage().code;
      SFUI.draw(context, langText, btnX + 5.5F, y + 9.5F, 8.0F, this.withAlpha(dark ? -1 : -15461352, anim));
      this.clickBounds.put("action:lang", new int[]{(int)btnX, (int)y + 6, 24, 15});
      btnX -= 24.0F;
      boolean hTheme = this.inside(this.currentMouseX, this.currentMouseY, btnX, y + 6.0F, 19.0F, 15.0F);
      float hThemeAnim = this.updateHover("btn:theme", hTheme);
      int themeBg = this.interpolateColor(dark ? 806885408 : 820045036, dark ? 1881482549 : 1893062882, hThemeAnim);
      RoundedRectShader.draw(context, btnX, y + 6.0F, 19.0F, 15.0F, 3.5F, this.withAlpha(themeBg, anim));
      ModernGuiIcons.Icon themeIcon = dark ? ModernGuiIcons.Icon.SUN : ModernGuiIcons.Icon.MOON;
      ModernGuiIcons.draw(context, themeIcon, btnX + 4.5F, y + 8.0F, 10.0F, this.withAlpha(dark ? -1 : -15461352, anim));
      this.clickBounds.put("action:theme", new int[]{(int)btnX, (int)y + 6, 19, 15});
      btnX -= 24.0F;
      boolean hFav = this.inside(this.currentMouseX, this.currentMouseY, btnX, y + 6.0F, 19.0F, 15.0F);
      float hFavAnim = this.updateHover("btn:fav", hFav);
      int favBg = ClientData.onlyFavoritesFilter ? (dark ? -13290171 : -4143659) : this.interpolateColor(dark ? 806885408 : 820045036, dark ? 1881482549 : 1893062882, hFavAnim);
      RoundedRectShader.draw(context, btnX, y + 6.0F, 19.0F, 15.0F, 3.5F, this.withAlpha(favBg, anim));
      ModernGuiIcons.Icon favIcon = ClientData.onlyFavoritesFilter ? ModernGuiIcons.Icon.STAR_FILLED : ModernGuiIcons.Icon.STAR_OUTLINE;
      int favCol = ClientData.onlyFavoritesFilter ? -10496 : (dark ? -1 : -15461352);
      ModernGuiIcons.draw(context, favIcon, btnX + 4.5F, y + 8.0F, 10.0F, this.withAlpha(favCol, anim));
      this.clickBounds.put("action:favorites", new int[]{(int)btnX, (int)y + 6, 19, 15});
   }

   private ModernGuiIcons.Icon getTabIcon(String tab) {
      ModernGuiIcons.Icon var10000;
      switch (tab) {
         case "All" -> var10000 = ModernGuiIcons.Icon.GRID;
         case "HUD" -> var10000 = ModernGuiIcons.Icon.MONITOR;
         case "Visual" -> var10000 = ModernGuiIcons.Icon.WAND;
         case "Utils" -> var10000 = ModernGuiIcons.Icon.WRENCH;
         case "Cosmetics" -> var10000 = ModernGuiIcons.Icon.SPARKLE;
         case "Events" -> var10000 = ModernGuiIcons.Icon.SPARKLE;
         case "Configs" -> var10000 = ModernGuiIcons.Icon.FILE;
         case "Friends" -> var10000 = ModernGuiIcons.Icon.USERS;
         case "Waypoints" -> var10000 = ModernGuiIcons.Icon.PIN;
         case "Themes" -> var10000 = ModernGuiIcons.Icon.PIPETTE;
         case "Settings" -> var10000 = ModernGuiIcons.Icon.SETTINGS;
         case "Search" -> var10000 = ModernGuiIcons.Icon.SEARCH;
         default -> var10000 = ModernGuiIcons.Icon.MONITOR;
      }

      return var10000;
   }

   private void drawThreeColumnModules(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      List<String> modules = this.getFilteredModules();
      if (modules.isEmpty()) {
         SFUI.draw(context, "Ничего не найдено", x + w / 2.0F - 38.0F, y + h / 2.0F, 9.0F, this.withAlpha(dark ? -9408384 : -7303008, anim));
      } else {
         float switchOffset = (1.0F - this.tabSwitchAnim) * 14.0F;
         float switchAlpha = this.tabSwitchAnim;
         float colW = (w - 16.0F) / 3.0F;
         List<List<String>> columns = new ArrayList();

         for(int i = 0; i < 3; ++i) {
            columns.add(new ArrayList());
         }

         for(int i = 0; i < modules.size(); ++i) {
            columns.get(i % 3).add(modules.get(i));
         }

         for(int col = 0; col < 3; ++col) {
            float colX = x + (float)col * (colW + 8.0F);
            float scroll = this.colScrolls[col];
            float curY = y + scroll + switchOffset;
            context.enableScissor((int)colX, (int)y, (int)(colX + colW), (int)(y + h));

            for(String mod : columns.get(col)) {
               curY = this.drawModuleCard(context, colX, curY, colW, mod, dark, anim * switchAlpha);
               curY += 8.0F;
            }

            float totalHeight = curY - (y + scroll + switchOffset);
            float maxScroll = Math.max(0.0F, totalHeight - h + 16.0F);
            if (this.targetColScrolls[col] < -maxScroll) {
               this.targetColScrolls[col] = -maxScroll;
            }

            if (this.targetColScrolls[col] > 0.0F) {
               this.targetColScrolls[col] = 0.0F;
            }

            context.disableScissor();
            if (totalHeight > h && maxScroll > 0.0F) {
               float sbTrackX = colX + colW - 2.5F;
               float sbTrackY = y + 2.0F;
               float sbTrackH = h - 4.0F;
               RoundedRectShader.draw(context, sbTrackX, sbTrackY, 2.0F, sbTrackH, 1.0F, this.withAlpha(dark ? 553648127 : 352321536, anim * switchAlpha));
               float thumbRatio = Math.max(0.15F, Math.min(1.0F, h / totalHeight));
               float thumbH = sbTrackH * thumbRatio;
               float scrollRatio = Math.max(0.0F, Math.min(1.0F, -scroll / maxScroll));
               float thumbY = sbTrackY + (sbTrackH - thumbH) * scrollRatio;
               int thumbCol = dark ? 1627389951 : 1342177280;
               RoundedRectShader.draw(context, sbTrackX, thumbY, 2.0F, thumbH, 1.0F, this.withAlpha(thumbCol, anim * switchAlpha));
            }
         }

      }
   }

   private float drawModuleCard(DrawContext context, float x, float y, float w, String mod, boolean dark, float anim) {
      boolean enabled = (Boolean)ClientData.moduleStates.getOrDefault(mod, false);
      float tAnim = this.updateToggleAnim("mod:" + mod, enabled);
      List<ModernSetting> settings = ModernSettingsRegistry.get(mod);
      float cardH = 26.0F;
      if (settings != null) {
         for(ModernSetting s : settings) {
            if (s != null && s.isVisible()) {
               cardH += this.getSettingHeight(s);
            }
         }
      }

      int cardBg = dark ? -1072623339 : -704643073;
      RoundedRectShader.draw(context, x, y, w, cardH, 7.0F, this.withAlpha(cardBg, anim));
      float iconSize = 10.0F;
      ModernGuiIcons.Icon modIcon = this.getModuleIcon(mod);
      int iconCol = enabled ? (dark ? -1 : -15461352) : (dark ? -8750454 : -7697766);
      ModernGuiIcons.draw(context, modIcon, x + 7.0F, y + 8.0F, iconSize, this.withAlpha(iconCol, anim));
      float rightX = x + w - 6.0F;
      float switchW = 18.0F;
      float switchH = 9.5F;
      rightX -= switchW;
      float switchY = y + 8.25F;
      int switchTrack = this.interpolateColor(dark ? -14540244 : -2959649, dark ? -1 : -15461352, tAnim);
      RoundedRectShader.draw(context, rightX, switchY, switchW, switchH, 4.75F, this.withAlpha(switchTrack, anim));
      float thumbSize = 6.5F;
      float thumbX = rightX + 1.5F + (switchW - thumbSize - 3.0F) * tAnim;
      int thumbCol = this.interpolateColor(dark ? -7697766 : -1, dark ? -15856110 : -1, tAnim);
      RoundedRectShader.draw(context, thumbX, switchY + 1.5F, thumbSize, thumbSize, thumbSize / 2.0F, this.withAlpha(thumbCol, anim));
      this.clickBounds.put("toggle:" + mod, new int[]{(int)rightX - 2, (int)switchY - 2, (int)switchW + 4, (int)switchH + 4});
      rightX -= 13.0F;
      boolean isFav = this.isModuleFavorite(mod);
      float sAnim = this.updateStarAnim(mod);
      int starCol = isFav ? -10496 : (dark ? -11184794 : -5197632);
      ModernGuiIcons.Icon starIcon = isFav ? ModernGuiIcons.Icon.STAR_FILLED : ModernGuiIcons.Icon.STAR_OUTLINE;
      float starScale = 1.0F + 0.35F * (float)Math.sin((double)sAnim * 3.141592653589793);
      ModernGuiIcons.draw(context, starIcon, rightX, y + 8.5F, 9.0F * starScale, this.withAlpha(starCol, anim));
      this.clickBounds.put("fav:" + mod, new int[]{(int)rightX - 2, (int)y + 6, 13, 13});
      rightX -= 16.0F;
      Integer bindKey = (Integer)ClientData.moduleBinds.get(mod);
      if (bindKey == null || bindKey == -1) {
         bindKey = BindManager.getStoredBindValue(mod);
      }

      String bindText = this.bindingTarget != null && this.bindingTarget.equals(mod) ? "..." : (bindKey != null && bindKey != -1 && bindKey != -1 ? BindManager.formatBindName(bindKey) : null);
      if (bindText != null) {
         bindText = bindText.toUpperCase();
         float bW = Math.max(13.0F, SFUI.getWidth(bindText, 7.0F) + 5.0F);
         rightX -= bW - 13.0F;
         int bindBg = dark ? 1881482544 : 1893589224;
         RoundedRectShader.draw(context, rightX, y + 7.5F, bW, 11.0F, 2.5F, this.withAlpha(bindBg, anim));
         int bindTextCol = dark ? -4473915 : -14013899;
         SFUI.draw(context, bindText, rightX + (bW - SFUI.getWidth(bindText, 7.0F)) / 2.0F, y + 9.5F, 7.0F, this.withAlpha(bindTextCol, anim));
         this.clickBounds.put("bind:" + mod, new int[]{(int)rightX, (int)y + 7, (int)bW, 11});
      } else {
         boolean hBind = this.inside(this.currentMouseX, this.currentMouseY, rightX, y + 7.0F, 13.0F, 11.0F);
         int bindIconBg = hBind ? (dark ? 1613047088 : 1625153768) : (dark ? 806885408 : 820045036);
         RoundedRectShader.draw(context, rightX, y + 7.5F, 13.0F, 11.0F, 2.5F, this.withAlpha(bindIconBg, anim));
         int bindIconCol = dark ? (hBind ? -1 : -7829352) : (hBind ? -15461352 : -10132107);
         ModernGuiIcons.draw(context, ModernGuiIcons.Icon.KEYBOARD, rightX + 2.0F, y + 8.0F, 8.5F, this.withAlpha(bindIconCol, anim));
         this.clickBounds.put("bind:" + mod, new int[]{(int)rightX, (int)y + 7, 13, 11});
      }

      String title = this.getAnimatedText(mod, GuiLocalization.getModuleTitle(mod));
      int titleCol = enabled ? (dark ? -1118478 : -15461352) : (dark ? -6250320 : -9803142);
      float titleX = x + 20.0F;
      float maxTitleW = Math.max(10.0F, rightX - titleX - 4.0F);
      float textW = SFUI.getWidth(title, 8.5F);
      context.enableScissor((int)titleX, (int)y, (int)(titleX + maxTitleW), (int)(y + 26.0F));
      if (textW > maxTitleW && this.inside(this.currentMouseX, this.currentMouseY, x, y, w, cardH)) {
         float overflow = textW - maxTitleW;
         float crawl = (float)(Math.sin((double)System.currentTimeMillis() / 450.0) * 0.5 + 0.5) * overflow;
         SFUI.draw(context, title, titleX - crawl, y + 9.0F, 8.5F, this.withAlpha(titleCol, anim));
      } else {
         SFUI.draw(context, title, titleX, y + 9.0F, 8.5F, this.withAlpha(titleCol, anim));
      }

      context.disableScissor();
      float setY = y + 24.0F;
      if (settings != null) {
         for(ModernSetting s : settings) {
            if (s != null && s.isVisible()) {
               setY = this.drawSettingRow(context, x + 6.0F, setY, w - 12.0F, mod, s, dark, anim);
            }
         }
      }

      return y + cardH;
   }

   private float getSettingHeight(ModernSetting s) {
      if (s == null) {
         return 0.0F;
      } else {
         float var10000;
         switch (s.type) {
            case CROSSHAIR_CANVAS -> var10000 = 128.0F;
            case PAD2D -> var10000 = 46.0F;
            case HEADER -> var10000 = 16.0F;
            case SLIDER -> var10000 = 28.0F;
            case MODE -> var10000 = 32.0F;
            case COLOR -> var10000 = 24.0F;
            case TOGGLE -> var10000 = 20.0F;
            case BIND -> var10000 = 20.0F;
            case BUTTON -> var10000 = 22.0F;
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         return var10000;
      }
   }

   private float drawSettingRow(DrawContext context, float x, float y, float w, String mod, ModernSetting s, boolean dark, float anim) {
      if (s == null) {
         return y;
      } else if (s.type == ModernSetting.Type.HEADER) {
         SFUI.draw(context, s.label != null ? s.label : "", x, y + 3.0F, 7.5F, this.withAlpha(dark ? -9671555 : -7697764, anim));
         return y + 16.0F;
      } else {
         String title = this.getAnimatedText(s.key, GuiLocalization.getSettingTitle(s.key, s.label));
         String var10001 = s.key;
         String desc = this.getAnimatedText(var10001 + "_desc", GuiLocalization.getSettingDescription(s.key));
         int titleCol = dark ? -2565920 : -14671832;
         int descCol = dark ? -10132107 : -7697766;
         switch (s.type) {
            case CROSSHAIR_CANVAS:
               CustomCrosshairData.load();
               SFUI.draw(context, "Холст прицела (21x21)", x, y + 1.0F, 7.5F, this.withAlpha(titleCol, anim));
               float btnY = y + 11.0F;
               float btnGap = 3.0F;
               float btnW = (w - btnGap * 3.0F) / 4.0F;
               float btnH = 12.0F;
               this.drawMiniButton(context, x, btnY, btnW, btnH, "Дефолт", "ch_act:default", dark, anim);
               this.drawMiniButton(context, x + btnW + btnGap, btnY, btnW, btnH, "Точка", "ch_act:dot", dark, anim);
               this.drawMiniButton(context, x + (btnW + btnGap) * 2.0F, btnY, btnW, btnH, "Уголки", "ch_act:corners", dark, anim);
               this.drawMiniButton(context, x + (btnW + btnGap) * 3.0F, btnY, btnW, btnH, "Сброс", "ch_act:clear", dark, anim);
               int size = 21;
               float cellSize = 4.0F;
               float gridW = (float)size * cellSize;
               float gridH = (float)size * cellSize;
               float gx = x + (w - gridW) / 2.0F;
               float gy = btnY + btnH + 5.0F;
               RoundedRectShader.draw(context, gx - 2.0F, gy - 2.0F, gridW + 4.0F, gridH + 4.0F, 3.0F, this.withAlpha(dark ? -15461350 : -2038548, anim));
               String colorMode = (String)ClientData.modeSettings.getOrDefault("Crosshair Color Mode", "Theme");
               int chCol;
               if (colorMode.equals("Custom")) {
                  float[] hsv = (float[])ClientData.colorSettings.getOrDefault("Crosshair Custom Color", new float[]{0.0F, 1.0F, 1.0F});
                  chCol = -16777216 | Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
               } else {
                  chCol = -16777216 | LexoraGui.getThemeColor(0.0F);
               }

               int center = size / 2;

               for(int cy = 0; cy < size; ++cy) {
                  for(int cx = 0; cx < size; ++cx) {
                     float px = gx + (float)cx * cellSize;
                     float py = gy + (float)cy * cellSize;
                     boolean filled = CustomCrosshairData.MATRIX[cy][cx];
                     if (filled) {
                        context.fill((int)px, (int)py, (int)(px + cellSize), (int)(py + cellSize), this.withAlpha(chCol, anim));
                     } else {
                        int bgCell = cx != center && cy != center ? (dark ? -15263968 : -1512462) : (dark ? -14803418 : -2564891);
                        context.fill((int)px, (int)py, (int)(px + cellSize - 0.5F), (int)(py + cellSize - 0.5F), this.withAlpha(bgCell, anim));
                     }
                  }
               }

               this.crosshairBounds.put("canvas", new float[]{gx, gy, gridW, gridH, cellSize});
               return y + 128.0F;
            case PAD2D:
               SFUI.draw(context, title, x, y + 1.0F, 7.5F, this.withAlpha(titleCol, anim));
               float padH = 34.0F;
               float py = y + 10.0F;
               RoundedRectShader.draw(context, x, py, w, padH, 4.0F, this.withAlpha(dark ? -15066590 : -2038548, anim));
               String var10000;
               if (s.keyY != null) {
                  var10000 = s.keyY;
               } else if (s.key.endsWith("X")) {
                  var10000 = s.key;
                  var10000 = var10000.substring(0, s.key.length() - 1) + "Y";
               } else {
                  var10000 = s.key + "Y";
               }

               String yKey = var10000;
               this.padKeyYMap.put(s.key, yKey);
               float vx = (Float)ClientData.numSettings.getOrDefault(s.key, (s.min + s.max) / 2.0F);
               float vy = (Float)ClientData.numSettings.getOrDefault(yKey, (s.minY + s.maxY) / 2.0F);
               float tx = Math.max(0.0F, Math.min(1.0F, (vx - s.min) / Math.max(0.001F, s.max - s.min)));
               float ty = Math.max(0.0F, Math.min(1.0F, (s.maxY - vy) / Math.max(0.001F, s.maxY - s.minY)));
               RoundedRectShader.draw(context, x + w / 2.0F, py, 1.0F, padH, 0.0F, this.withAlpha(dark ? 822083583 : 536870912, anim));
               RoundedRectShader.draw(context, x, py + padH / 2.0F, w, 1.0F, 0.0F, this.withAlpha(dark ? 822083583 : 536870912, anim));
               RoundedRectShader.draw(context, x + tx * w - 3.0F, py + ty * padH - 3.0F, 6.0F, 6.0F, 3.0F, this.withAlpha(dark ? -1 : -15461352, anim));
               this.padBounds.put(s.key, new float[]{x, py, w, padH, s.min, s.max, s.minY, s.maxY});
               return y + 46.0F;
            case HEADER:
            default:
               return y + 18.0F;
            case SLIDER:
               SFUI.draw(context, title, x, y + 1.0F, 7.5F, this.withAlpha(titleCol, anim));
               SFUI.draw(context, desc, x, y + 9.0F, 6.2F, this.withAlpha(descCol, anim));
               float val = (Float)ClientData.numSettings.getOrDefault(s.key != null ? s.key : "", s.min);
               String valStr = String.format(Locale.US, "%.1f", val);
               SFUI.draw(context, valStr, x + w - SFUI.getWidth(valStr, 7.0F), y + 2.0F, 7.0F, this.withAlpha(titleCol, anim));
               float barY = y + 18.0F;
               float barH = 3.0F;
               int trackBg = dark ? -14408660 : -2564891;
               RoundedRectShader.draw(context, x, barY, w, barH, 1.5F, this.withAlpha(trackBg, anim));
               float norm = (val - s.min) / (s.max - s.min);
               norm = Math.max(0.0F, Math.min(1.0F, norm));
               float fillW = w * norm;
               int fillCol = dark ? -1 : -15461352;
               RoundedRectShader.draw(context, x, barY, fillW, barH, 1.5F, this.withAlpha(fillCol, anim));
               RoundedRectShader.draw(context, x + fillW - 3.0F, barY - 1.5F, 6.0F, 6.0F, 3.0F, this.withAlpha(fillCol, anim));
               if (s.key != null) {
                  this.sliderBounds.put(s.key, new float[]{x, barY - 4.0F, w, 11.0F, s.min, s.max});
               }

               return y + 28.0F;
            case MODE:
               SFUI.draw(context, title, x, y + 1.0F, 7.5F, this.withAlpha(titleCol, anim));
               SFUI.draw(context, desc, x, y + 9.0F, 6.2F, this.withAlpha(descCol, anim));
               String curMode = (String)ClientData.modeSettings.getOrDefault(s.key != null ? s.key : "", s.modes != null && !s.modes.isEmpty() ? (String)s.modes.get(0) : "Default");
               float dropY = y + 17.0F;
               float dropH = 13.0F;
               boolean isDropOpen = this.activeDropdownKey != null && this.activeDropdownKey.equals(s.key);
               int dropBg = isDropOpen ? (dark ? -13290171 : -3090976) : (dark ? -1876811222 : -1595611154);
               RoundedRectShader.draw(context, x, dropY, w, dropH, 3.5F, this.withAlpha(dropBg, anim));
               SFUI.draw(context, curMode, x + 5.0F, dropY + 3.0F, 7.0F, this.withAlpha(titleCol, anim));
               SFUI.draw(context, isDropOpen ? "▴" : "▾", x + w - 10.0F, dropY + 3.0F, 7.0F, this.withAlpha(descCol, anim));
               if (s.key != null) {
                  this.clickBounds.put("dropdown:" + s.key + ":" + (s.modes != null ? String.join(",", s.modes) : ""), new int[]{(int)x, (int)dropY, (int)w, (int)dropH});
               }

               return y + 32.0F;
            case COLOR:
               SFUI.draw(context, title, x, y + 1.0F, 7.5F, this.withAlpha(titleCol, anim));
               SFUI.draw(context, desc, x, y + 9.0F, 6.2F, this.withAlpha(descCol, anim));
               float[] hsv = (float[])ClientData.colorSettings.getOrDefault(s.key != null ? s.key : "", new float[]{0.58F, 0.8F, 1.0F});
               int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
               float swatchW = 20.0F;
               float swatchH = 10.0F;
               float swatchX = x + w - swatchW;
               float swatchY = y + 2.0F;
               RoundedRectShader.draw(context, swatchX, swatchY, swatchW, swatchH, 2.5F, this.withAlpha(-16777216 | rgb & 16777215, anim));
               RoundedRectShader.draw(context, swatchX - 1.0F, swatchY - 1.0F, swatchW + 2.0F, swatchH + 2.0F, 3.5F, this.withAlpha(dark ? 1090519039 : 536870912, anim));
               if (s.key != null) {
                  this.clickBounds.put("color:" + s.key, new int[]{(int)swatchX - 2, (int)swatchY - 2, (int)swatchW + 4, (int)swatchH + 4});
               }

               return y + 22.0F;
            case TOGGLE:
               boolean state = (Boolean)ClientData.moduleStates.getOrDefault(s.key != null ? s.key : "", false);
               var10001 = s.key;
               float bAnim = this.updateToggleAnim("set:" + var10001, state);
               SFUI.draw(context, title, x, y + 1.0F, 7.5F, this.withAlpha(titleCol, anim));
               SFUI.draw(context, desc, x, y + 9.0F, 6.2F, this.withAlpha(descCol, anim));
               float chkW = 13.0F;
               float chkH = 13.0F;
               float chkX = x + w - chkW;
               float chkY = y + 1.0F;
               int chkBg = this.interpolateColor(dark ? -14408660 : -2564891, dark ? -1 : -15461352, bAnim);
               RoundedRectShader.draw(context, chkX, chkY, chkW, chkH, 3.5F, this.withAlpha(chkBg, anim));
               if (bAnim > 0.05F) {
                  int markCol = dark ? -15856110 : -1;
                  RenderSystem.enableBlend();
                  RenderSystem.defaultBlendFunc();
                  context.drawTexture(RenderLayer::getGuiTextured, CHECKMARK_TEX, (int)(chkX + 1.5F), (int)(chkY + 1.5F), 0.0F, 0.0F, 10, 10, 10, 10, this.withAlpha(markCol, anim * bAnim));
               }

               if (s.key != null) {
                  this.clickBounds.put("bool:" + s.key, new int[]{(int)chkX - 2, (int)chkY - 2, (int)chkW + 4, (int)chkH + 4});
               }

               return y + 20.0F;
            case BIND:
               SFUI.draw(context, title, x, y + 1.0F, 7.5F, this.withAlpha(titleCol, anim));
               SFUI.draw(context, desc, x, y + 9.0F, 6.2F, this.withAlpha(descCol, anim));
               int bKey = BindManager.getStoredBindValue(s.key);
               if (bKey == -1 || bKey == -1) {
                  bKey = (Integer)ClientData.moduleBinds.getOrDefault(s.key, -1);
               }

               String bStr = this.bindingTarget != null && this.bindingTarget.equals(s.key) ? "..." : (bKey != -1 && bKey != -1 ? BindManager.formatBindName(bKey) : "NONE");
               float bW = Math.max(30.0F, SFUI.getWidth(bStr.toUpperCase(), 6.5F) + 10.0F);
               float bX = x + w - bW;
               RoundedRectShader.draw(context, bX, y + 1.0F, bW, 12.0F, 2.5F, this.withAlpha(dark ? -1876613840 : -1864507160, anim));
               SFUI.draw(context, bStr.toUpperCase(), bX + (bW - SFUI.getWidth(bStr.toUpperCase(), 6.5F)) / 2.0F, y + 4.0F, 6.5F, this.withAlpha(dark ? -1 : -15461352, anim));
               if (s.key != null) {
                  this.clickBounds.put("bind:" + s.key, new int[]{(int)bX, (int)y + 1, (int)bW, 12});
               }

               return y + 20.0F;
            case BUTTON: {
               float buttonW = w;
               float buttonH = 16.0F;
               float buttonY = y + 1.0F;
               boolean bHov = this.inside(this.currentMouseX, this.currentMouseY, x, buttonY, buttonW, buttonH);
               int bBg = bHov ? (dark ? -13290171 : -2564888) : (dark ? -14540244 : -1709840);
               RoundedRectShader.draw(context, x, buttonY, buttonW, buttonH, 3.5F, this.withAlpha(bBg, anim));
               SFUI.draw(context, s.label, x + (buttonW - SFUI.getWidth(s.label, 6.8F)) / 2.0F, buttonY + 4.5F, 6.8F, this.withAlpha(dark ? -1118478 : -15461352, anim));
               if (s.action != null) {
                  this.buttonActions.put(s.label, s.action);
                  this.clickBounds.put("btn:" + s.label, new int[]{(int)x, (int)buttonY, (int)buttonW, (int)buttonH});
               }
               return y + 22.0F;
            }
         }
      }
   }

   private void drawMiniButton(DrawContext context, float x, float y, float w, float h, String text, String boundKey, boolean dark, float anim) {
      boolean hov = this.inside(this.currentMouseX, this.currentMouseY, x, y, w, h);
      int bg = hov ? (dark ? -13290171 : -2564888) : (dark ? -14540244 : -1709840);
      RoundedRectShader.draw(context, x, y, w, h, 2.5F, this.withAlpha(bg, anim));
      SFUI.draw(context, text, x + (w - SFUI.getWidth(text, 6.0F)) / 2.0F, y + 3.0F, 6.0F, this.withAlpha(dark ? -1118478 : -15461352, anim));
      this.clickBounds.put(boundKey, new int[]{(int)x, (int)y, (int)w, (int)h});
   }

   private void drawDropdownModal(DrawContext context, boolean dark, float anim) {
      if (this.activeDropdownKey != null && this.activeDropdownOptions != null && !this.activeDropdownOptions.isEmpty()) {
         float modalW = Math.max(120.0F, this.dropdownW);
         float itemH = 17.0F;
         float totalItemsH = (float)this.activeDropdownOptions.size() * itemH;
         float maxModalH = 110.0F;
         float modalH = Math.min(maxModalH, totalItemsH + 8.0F);
         float mX = Math.min(this.dropdownX, (float)this.width - modalW - 20.0F);
         float mY = Math.min(this.dropdownY, (float)this.height - modalH - 20.0F);
         int modalBg = dark ? -99544810 : -83886081;
         RoundedRectShader.draw(context, mX, mY, modalW, modalH, 6.0F, this.withAlpha(modalBg, anim));
         RoundedRectShader.draw(context, mX - 1.0F, mY - 1.0F, modalW + 2.0F, modalH + 2.0F, 7.0F, this.withAlpha(dark ? 1090519039 : 620756992, anim));
         String currentVal = (String)ClientData.modeSettings.getOrDefault(this.activeDropdownKey, (String)this.activeDropdownOptions.get(0));
         float maxScroll = Math.max(0.0F, totalItemsH - (modalH - 8.0F));
         if (this.targetDropdownScroll < -maxScroll) {
            this.targetDropdownScroll = -maxScroll;
         }

         if (this.targetDropdownScroll > 0.0F) {
            this.targetDropdownScroll = 0.0F;
         }

         context.enableScissor((int)mX, (int)(mY + 4.0F), (int)(mX + modalW), (int)(mY + modalH - 4.0F));
         float curY = mY + 4.0F + this.dropdownScroll;

         for(String opt : this.activeDropdownOptions) {
            boolean isSel = opt.equals(currentVal);
            boolean hItem = this.inside(this.currentMouseX, this.currentMouseY, mX + 4.0F, curY, modalW - 8.0F, itemH) && curY >= mY && curY + itemH <= mY + modalH;
            float hAnim = this.updateHover("drop_opt:" + opt, hItem);
            int optBg = isSel ? (dark ? -14342862 : -2038546) : this.interpolateColor(0, dark ? 891955768 : 1089006837, hAnim);
            if (optBg != 0) {
               RoundedRectShader.draw(context, mX + 4.0F, curY, modalW - 8.0F, itemH, 4.0F, this.withAlpha(optBg, anim));
            }

            int textCol = isSel ? (dark ? -1 : -15856110) : (dark ? -5197632 : -11908518);
            SFUI.draw(context, opt, mX + 8.0F, curY + 4.5F, 7.2F, this.withAlpha(textCol, anim));
            if (isSel) {
               SFUI.draw(context, "✓", mX + modalW - 14.0F, curY + 4.5F, 7.2F, this.withAlpha(textCol, anim));
            }

            this.clickBounds.put("set_mode:" + this.activeDropdownKey + ":" + opt, new int[]{(int)(mX + 4.0F), (int)curY, (int)(modalW - 8.0F), (int)itemH});
            curY += itemH;
         }

         context.disableScissor();
         if (maxScroll > 0.0F) {
            float sbX = mX + modalW - 3.0F;
            float sbY = mY + 4.0F;
            float sbH = modalH - 8.0F;
            RoundedRectShader.draw(context, sbX, sbY, 2.0F, sbH, 1.0F, this.withAlpha(dark ? 553648127 : 352321536, anim));
            float thumbRatio = Math.max(0.2F, Math.min(1.0F, (modalH - 8.0F) / totalItemsH));
            float thumbH = sbH * thumbRatio;
            float scrollRatio = Math.max(0.0F, Math.min(1.0F, -this.dropdownScroll / maxScroll));
            float thumbY = sbY + (sbH - thumbH) * scrollRatio;
            RoundedRectShader.draw(context, sbX, thumbY, 2.0F, thumbH, 1.0F, this.withAlpha(dark ? 1895825407 : 1342177280, anim));
         }

      }
   }

   private void drawColorPickerHud(DrawContext context, boolean dark, float anim) {
      if (this.activeColorPickerKey != null) {
         float hudW = 140.0F;
         float hudH = 125.0F;
         float hX = Math.max(10.0F, Math.min(this.colorPickerX, (float)this.width - hudW - 15.0F));
         float hY = Math.max(10.0F, Math.min(this.colorPickerY, (float)this.height - hudH - 15.0F));
         int hudBg = dark ? -99544810 : -83886081;
         RoundedRectShader.draw(context, hX, hY, hudW, hudH, 8.0F, this.withAlpha(hudBg, anim));
         RoundedRectShader.draw(context, hX - 1.0F, hY - 1.0F, hudW + 2.0F, hudH + 2.0F, 9.0F, this.withAlpha(dark ? 1090519039 : 620756992, anim));
         float[] hsv = (float[])ClientData.colorSettings.getOrDefault(this.activeColorPickerKey, new float[]{0.58F, 0.8F, 1.0F});
         float hue = hsv[0];
         float sat = hsv[1];
         float val = hsv[2];
         float svX = hX + 8.0F;
         float svY = hY + 8.0F;
         float svW = hudW - 16.0F;
         float svH = 62.0F;
         RoundedRectShader.draw(context, svX - 1.0F, svY - 1.0F, svW + 2.0F, svH + 2.0F, 4.0F, this.withAlpha(dark ? 822083583 : 536870912, anim));
         context.enableScissor((int)svX, (int)svY, (int)(svX + svW), (int)(svY + svH));
         int svStep = 3;

         for(int px = 0; px < (int)svW; px += svStep) {
            for(int py = 0; py < (int)svH; py += svStep) {
               float s = (float)px / svW;
               float v = 1.0F - (float)py / svH;
               int rgb = -16777216 | Color.HSBtoRGB(hue, s, v) & 16777215;
               context.fill((int)(svX + (float)px), (int)(svY + (float)py), (int)(svX + (float)px + (float)svStep), (int)(svY + (float)py + (float)svStep), this.withAlpha(rgb, anim));
            }
         }

         context.disableScissor();
         float dotX = svX + sat * svW;
         float dotY = svY + (1.0F - val) * svH;
         RoundedRectShader.draw(context, dotX - 2.5F, dotY - 2.5F, 5.0F, 5.0F, 2.5F, this.withAlpha(-1, anim));
         RoundedRectShader.draw(context, dotX - 3.5F, dotY - 3.5F, 7.0F, 7.0F, 3.5F, this.withAlpha(-16777216, anim * 0.6F));
         float hueX = svX;
         float hueY = svY + svH + 6.0F;
         float hueW = svW;
         float hueH = 9.0F;
         RoundedRectShader.draw(context, svX - 1.0F, hueY - 1.0F, svW + 2.0F, hueH + 2.0F, 3.5F, this.withAlpha(dark ? 822083583 : 536870912, anim));
         context.enableScissor((int)svX, (int)hueY, (int)(svX + svW), (int)(hueY + hueH));

         for(int px = 0; px < (int)hueW; ++px) {
            float h = (float)px / hueW;
            int rgb = -16777216 | Color.HSBtoRGB(h, 1.0F, 1.0F) & 16777215;
            context.fill((int)(hueX + (float)px), (int)hueY, (int)(hueX + (float)px + 1.0F), (int)(hueY + hueH), this.withAlpha(rgb, anim));
         }

         context.disableScissor();
         float hIndX = hueX + hue * hueW;
         RoundedRectShader.draw(context, hIndX - 2.0F, hueY - 1.0F, 4.0F, hueH + 2.0F, 2.0F, this.withAlpha(-1, anim));
         int curRgb = -16777216 | Color.HSBtoRGB(hue, sat, val) & 16777215;
         float previewY = hueY + hueH + 7.0F;
         RoundedRectShader.draw(context, svX, previewY, 16.0F, 16.0F, 4.0F, this.withAlpha(curRgb, anim));
         String hex = String.format("#%06X", curRgb & 16777215);
         SFUI.draw(context, hex, svX + 22.0F, previewY + 4.5F, 7.5F, this.withAlpha(dark ? -1118478 : -15461352, anim));
         float doneW = 42.0F;
         float doneX = hX + hudW - doneW - 8.0F;
         RoundedRectShader.draw(context, doneX, previewY, doneW, 16.0F, 4.0F, this.withAlpha(dark ? -14013896 : -2170133, anim));
         SFUI.draw(context, "Готово", doneX + 7.0F, previewY + 4.5F, 7.0F, this.withAlpha(dark ? -1 : -15461352, anim));
         this.clickBounds.put("palette_done", new int[]{(int)doneX, (int)previewY, (int)doneW, 16});
      }
   }

   private void updateSvFromMouse(double mx, double my, float svX, float svY, float svW, float svH) {
      if (this.activeColorPickerKey != null) {
         float sat = Math.max(0.0F, Math.min(1.0F, (float)((mx - (double)svX) / (double)svW)));
         float val = Math.max(0.0F, Math.min(1.0F, 1.0F - (float)((my - (double)svY) / (double)svH)));
         float[] hsv = (float[])ClientData.colorSettings.getOrDefault(this.activeColorPickerKey, new float[]{0.58F, 0.8F, 1.0F});
         ClientData.colorSettings.put(this.activeColorPickerKey, new float[]{hsv[0], sat, val});
         if (this.activeColorPickerKey.startsWith("gps_wp:")) {
            try {
               int idx = Integer.parseInt(this.activeColorPickerKey.substring("gps_wp:".length()));
               if (idx >= 0 && idx < GPS.waypoints.size()) {
                  ((GPS.GpsWaypoint)GPS.waypoints.get(idx)).color = -16777216 | Color.HSBtoRGB(hsv[0], sat, val) & 16777215;
               }
            } catch (Exception var13) {
            }
         }

      }
   }

   private void updateHueFromMouse(double mx, float hueX, float hueW) {
      if (this.activeColorPickerKey != null) {
         float hue = Math.max(0.0F, Math.min(1.0F, (float)((mx - (double)hueX) / (double)hueW)));
         float[] hsv = (float[])ClientData.colorSettings.getOrDefault(this.activeColorPickerKey, new float[]{0.58F, 0.8F, 1.0F});
         ClientData.colorSettings.put(this.activeColorPickerKey, new float[]{hue, hsv[1], hsv[2]});
         if (this.activeColorPickerKey.startsWith("gps_wp:")) {
            try {
               int idx = Integer.parseInt(this.activeColorPickerKey.substring("gps_wp:".length()));
               if (idx >= 0 && idx < GPS.waypoints.size()) {
                  ((GPS.GpsWaypoint)GPS.waypoints.get(idx)).color = -16777216 | Color.HSBtoRGB(hue, hsv[1], hsv[2]) & 16777215;
               }
            } catch (Exception var8) {
            }
         }

      }
   }

   private void drawCosmeticsScreen(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      float switchOffset = (1.0F - this.tabSwitchAnim) * 14.0F;
      float switchAlpha = this.tabSwitchAnim;
      float curY = y + switchOffset;
      int textColor = dark ? -1 : -15461352;
      int subTextColor = dark ? -8487282 : -8750454;
      int themeCol = -16777216 | LexoraGui.getThemeColor(0.0F);
      SFUI.draw(context, "Косметика и Плащи", x, curY + 1.0F, 11.0F, this.withAlpha(textColor, anim * switchAlpha));
      SFUI.draw(context, "3D аксессуары, крылья, питомцы и кастомные плащи", x, curY + 13.0F, 7.5F, this.withAlpha(subTextColor, anim * switchAlpha));
      curY += 26.0F;
      float topBarH = 26.0F;
      int barBg = dark ? -1072425959 : -704643073;
      RoundedRectShader.draw(context, x, curY, w, topBarH, 6.0F, this.withAlpha(barBg, anim * switchAlpha));
      int equippedCount = CosmeticManager.getInstance().getEquippedCount();
      String countText = equippedCount + "/5 надето";
      float badgeW = SFUI.getWidth(countText, 7.5F) + 10.0F;
      float badgeX = x + w - badgeW - 5.0F;
      RoundedRectShader.draw(context, badgeX, curY + 4.0F, badgeW, 18.0F, 4.0F, this.withAlpha(themeCol, anim * switchAlpha * 0.25F));
      SFUI.draw(context, countText, badgeX + 5.0F, curY + 9.0F, 7.5F, this.withAlpha(textColor, anim * switchAlpha));
      float availableW = badgeX - x - 8.0F;
      float btnGap = 3.0F;
      float btnW = Math.max(38.0F, (availableW - (float)(COSMETIC_TABS.length - 1) * btnGap - 4.0F) / (float)COSMETIC_TABS.length);
      float btnH = 18.0F;
      float btnX = x + 4.0F;

      for(int i = 0; i < COSMETIC_TABS.length; ++i) {
         boolean active = cosmeticSubTab == i;
         float btnY = curY + 4.0F;
         if (active) {
            RoundedRectShader.draw(context, btnX, btnY, btnW, btnH, 4.0F, this.withAlpha(themeCol, anim * switchAlpha));
         } else if (this.inside(this.currentMouseX, this.currentMouseY, btnX, btnY, btnW, btnH)) {
            int hCol = dark ? 1090519039 : 620756992;
            RoundedRectShader.draw(context, btnX, btnY, btnW, btnH, 4.0F, this.withAlpha(hCol, anim * switchAlpha));
         }

         int tCol = active ? -1 : subTextColor;
         float labelW = SFUI.getWidth(COSMETIC_TABS[i], 7.5F);
         SFUI.draw(context, COSMETIC_TABS[i], btnX + (btnW - labelW) / 2.0F, btnY + 5.0F, 7.5F, this.withAlpha(tCol, anim * switchAlpha));
         this.clickBounds.put("cosmetic_sub:" + i, new int[]{(int)btnX, (int)btnY, (int)btnW, (int)btnH});
         btnX += btnW + btnGap;
      }

      curY += 30.0F;
      float optBarH = 24.0F;
      RoundedRectShader.draw(context, x, curY, w, optBarH, 6.0F, this.withAlpha(barBg, anim * switchAlpha));
      boolean capeOn = CosmeticManager.getInstance().isCustomCapeEnabled();
      this.drawTogglePill(context, "Плащ: " + (capeOn ? "ВКЛ" : "ВЫКЛ"), capeOn, x + 6.0F, curY + 3.0F, 94.0F, 18.0F, dark, anim * switchAlpha, "cosmetic_cape_toggle");
      this.drawTogglePill(context, "Сбросить всё", false, x + 106.0F, curY + 3.0F, 90.0F, 18.0F, dark, anim * switchAlpha, "cosmetic_reset_all");
      this.drawTogglePill(context, "Открыть папку", false, x + 202.0F, curY + 3.0F, 96.0F, 18.0F, dark, anim * switchAlpha, "cosmetic_open_folder");
      curY += 28.0F;
      float listH = y + h - curY;
      this.secondaryScroll += (this.targetSecondaryScroll - this.secondaryScroll) * 0.25F;
      this.drawCosmeticsGridScreen(context, x, curY, w, listH, dark, anim * switchAlpha);
   }

   private void drawTogglePill(DrawContext context, String label, boolean active, float x, float y, float w, float h, boolean dark, float anim, String clickId) {
      int themeCol = -16777216 | LexoraGui.getThemeColor(0.0F);
      int bg = active ? themeCol : (dark ? 1076176176 : 820044012);
      boolean hovered = this.inside(this.currentMouseX, this.currentMouseY, x, y, w, h);
      if (hovered && !active) {
         bg = dark ? 1614099781 : 1355862236;
      }

      RoundedRectShader.draw(context, x, y, w, h, 4.0F, this.withAlpha(bg, anim));
      int textCol = active ? -1 : (dark ? -6381906 : -10592658);
      float tw = SFUI.getWidth(label, 7.5F);
      SFUI.draw(context, label, x + (w - tw) / 2.0F, y + (h - 7.5F) / 2.0F + 0.5F, 7.5F, this.withAlpha(textCol, anim));
      this.clickBounds.put(clickId, new int[]{(int)x, (int)y, (int)w, (int)h});
   }

   private void drawCosmeticsGridScreen(DrawContext context, float x, float listY, float w, float listH, boolean dark, float anim) {
      CosmeticManager cm = CosmeticManager.getInstance();
      String currentCat = COSMETIC_TYPES[cosmeticSubTab];
      List<CosmeticManager.CosmeticEntry> items = cm.getFilteredEntries(currentCat);
      if (this.searchInput != null && !this.searchInput.isBlank()) {
         String query = this.searchInput.toLowerCase().trim();
         List<CosmeticManager.CosmeticEntry> filtered = new ArrayList();

         for(CosmeticManager.CosmeticEntry e : items) {
            if (e.name.toLowerCase().contains(query) || e.type.toLowerCase().contains(query)) {
               filtered.add(e);
            }
         }

         items = filtered;
      }

      if (items.isEmpty()) {
         String emptyMsg = "Косметика не найдена";
         float ew = SFUI.getWidth(emptyMsg, 9.0F);
         SFUI.draw(context, emptyMsg, x + (w - ew) / 2.0F, listY + 50.0F, 9.0F, this.withAlpha(dark ? -8487282 : -8750454, anim));
      } else {
         int themeCol = -16777216 | LexoraGui.getThemeColor(0.0F);
         float cardW = (w - 24.0F) / 4.0F;
         float cardH = 88.0F;
         float gap = 8.0F;
         int cols = 4;
         int rows = (items.size() + cols - 1) / cols;
         float totalH = (float)rows * (cardH + gap);
         float maxScroll = Math.max(0.0F, totalH - listH + 10.0F);
         if (this.targetSecondaryScroll < -maxScroll) {
            this.targetSecondaryScroll = -maxScroll;
         }

         if (this.targetSecondaryScroll > 0.0F) {
            this.targetSecondaryScroll = 0.0F;
         }

         if (this.secondaryScroll < -maxScroll) {
            this.secondaryScroll = -maxScroll;
         }

         if (this.secondaryScroll > 0.0F) {
            this.secondaryScroll = 0.0F;
         }

         float startY = listY + this.secondaryScroll;
         context.enableScissor((int)x, (int)listY, (int)(x + w), (int)(listY + listH));
         float rotTime = (float)System.currentTimeMillis() / 20.0F % 360.0F;

         for(int i = 0; i < items.size(); ++i) {
            CosmeticManager.CosmeticEntry entry = (CosmeticManager.CosmeticEntry)items.get(i);
            int col = i % cols;
            int row = i / cols;
            float cx = x + (float)col * (cardW + gap);
            float cy = startY + (float)row * (cardH + gap);
            if (!(cy + cardH < listY) && !(cy > listY + listH)) {
               boolean isEquipped = cm.isEquipped(entry);
               boolean hovered = this.inside(this.currentMouseX, this.currentMouseY, cx, cy, cardW, cardH);
               int cardBg = isEquipped ? this.withAlpha(themeCol, 0.22F * anim) : (hovered ? (dark ? -535028700 : -386861828) : (dark ? -1072425959 : -704643073));
               RoundedRectShader.draw(context, cx, cy, cardW, cardH, 6.0F, this.withAlpha(cardBg, anim));
               float stageW = cardW - 12.0F;
               float stageH = 44.0F;
               float stageX = cx + 6.0F;
               float stageY = cy + 6.0F;
               int stageBg = dark ? 1074794518 : 636152564;
               RoundedRectShader.draw(context, stageX, stageY, stageW, stageH, 4.0F, this.withAlpha(stageBg, anim));
               float previewCenterX = stageX + stageW / 2.0F;
               float previewCenterY = stageY + stageH / 2.0F;
               if (entry.isCape) {
                  Identifier capeTex = entry.getTexture();
                  if (capeTex != null) {
                     GeckolibCosmeticRenderer.getInstance().renderCapeInGui(context, previewCenterX, previewCenterY, capeTex, rotTime, 12.0F);
                  }
               } else {
                  CosmeticModel model = cm.getModel(entry.index);
                  if (model != null) {
                     GeckolibCosmeticRenderer.getInstance().renderModelInGui(model, context, previewCenterX, previewCenterY, 20.0F, rotTime, 12.0F);
                  }
               }

               String displayName = entry.name;
               if (displayName.length() > 14) {
                  displayName = displayName.substring(0, 13) + "..";
               }

               float nw = SFUI.getWidth(displayName, 7.5F);
               SFUI.draw(context, displayName, cx + (cardW - nw) / 2.0F, cy + 53.0F, 7.5F, this.withAlpha(dark ? -1118478 : -15461352, anim));
               String action = isEquipped ? "НАДЕТО" : "НАДЕТЬ";
               int actBg = isEquipped ? themeCol : (dark ? 1076176176 : 820044012);
               if (hovered && !isEquipped) {
                  actBg = dark ? 1614099781 : 1355862236;
               }

               float btnH = 16.0F;
               float btnW = cardW - 12.0F;
               float btnY = cy + 65.0F;
               RoundedRectShader.draw(context, cx + 6.0F, btnY, btnW, btnH, 3.5F, this.withAlpha(actBg, anim));
               float actW = SFUI.getWidth(action, 7.5F);
               SFUI.draw(context, action, cx + (cardW - actW) / 2.0F, btnY + 4.5F, 7.5F, this.withAlpha(isEquipped ? -1 : (dark ? -7434594 : -9539970), anim));
               this.clickBounds.put("cosmetic_item:" + entry.index, new int[]{(int)cx, (int)cy, (int)cardW, (int)cardH});
            }
         }

         context.disableScissor();
         if (totalH > listH && maxScroll > 0.0F) {
            float sbTrackX = x + w - 2.5F;
            float sbTrackY = listY + 2.0F;
            float sbTrackH = listH - 4.0F;
            RoundedRectShader.draw(context, sbTrackX, sbTrackY, 2.0F, sbTrackH, 1.0F, this.withAlpha(dark ? 553648127 : 352321536, anim));
            float thumbRatio = Math.max(0.15F, Math.min(1.0F, listH / totalH));
            float thumbH = sbTrackH * thumbRatio;
            float scrollRatio = Math.max(0.0F, Math.min(1.0F, -this.secondaryScroll / maxScroll));
            float thumbY = sbTrackY + (sbTrackH - thumbH) * scrollRatio;
            int thumbCol = dark ? 1627389951 : 1342177280;
            RoundedRectShader.draw(context, sbTrackX, thumbY, 2.0F, thumbH, 1.0F, this.withAlpha(thumbCol, anim));
         }

      }
   }

   private void drawEventsScreen(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      float switchOffset = (1.0F - this.tabSwitchAnim) * 14.0F;
      float switchAlpha = this.tabSwitchAnim;
      SFUI.draw(context, "Игровые Ивенты HolyWorld", x, y + switchOffset + 1.0F, 11.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      SFUI.draw(context, "Расписание спавна мистических сундуков, боссов, аирдропов и голосований", x, y + switchOffset + 13.0F, 7.5F, this.withAlpha(dark ? -8487282 : -8750454, anim * switchAlpha));
      float listY = y + switchOffset + 28.0F;
      float listH = h - 28.0F - switchOffset;
      List<HolyWorldEventsApi.HwEvent> list = HolyWorldEventsApi.getEvents();
      if (list == null || list.isEmpty()) {
         list = HolyWorldEventsApi.parseRawEvents(EventFetcher.holyWorldEvents);
      }

      if (list != null && !list.isEmpty()) {
         this.secondaryScroll += (this.targetSecondaryScroll - this.secondaryScroll) * 0.25F;
         float totalH = (float)list.size() * 36.0F;
         float maxScroll = Math.max(0.0F, totalH - listH + 10.0F);
         if (this.targetSecondaryScroll < -maxScroll) {
            this.targetSecondaryScroll = -maxScroll;
         }

         if (this.targetSecondaryScroll > 0.0F) {
            this.targetSecondaryScroll = 0.0F;
         }

         float curY = listY + this.secondaryScroll;
         context.enableScissor((int)x, (int)listY, (int)(x + w), (int)(listY + listH));

         for(HolyWorldEventsApi.HwEvent evt : list) {
            if (curY + 32.0F >= listY - 32.0F && curY <= listY + listH + 32.0F) {
               int cardBg = dark ? -1072425959 : -704643073;
               RoundedRectShader.draw(context, x, curY, w, 32.0F, 6.0F, this.withAlpha(cardBg, anim * switchAlpha));
               int tierCol = HolyWorldEventsApi.tierColor(evt.rarityTier());
               RoundedRectShader.draw(context, x + 8.0F, curY + 7.0F, 4.0F, 18.0F, 2.0F, this.withAlpha(tierCol, anim * switchAlpha));
               SFUI.draw(context, evt.displayName(), x + 18.0F, curY + 7.0F, 8.5F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
               SFUI.draw(context, evt.serverName(), x + 18.0F, curY + 18.0F, 7.0F, this.withAlpha(dark ? -8750454 : -7697766, anim * switchAlpha));
               float rW = SFUI.getWidth(evt.rarityDisplay(), 7.0F) + 10.0F;
               float rX = x + w - rW - 10.0F;
               RoundedRectShader.draw(context, rX, curY + 8.0F, rW, 15.0F, 3.5F, this.withAlpha(tierCol, anim * switchAlpha * 0.25F));
               SFUI.draw(context, evt.rarityDisplay(), rX + 5.0F, curY + 12.0F, 7.0F, this.withAlpha(tierCol, anim * switchAlpha));
               this.clickBounds.put("hw_join:" + evt.serverCode(), new int[]{(int)x, (int)curY, (int)w, 32});
            }

            curY += 36.0F;
         }

         context.disableScissor();
         if (totalH > listH && maxScroll > 0.0F) {
            float sbTrackX = x + w - 2.5F;
            float sbTrackY = listY + 2.0F;
            float sbTrackH = listH - 4.0F;
            RoundedRectShader.draw(context, sbTrackX, sbTrackY, 2.0F, sbTrackH, 1.0F, this.withAlpha(dark ? 553648127 : 352321536, anim * switchAlpha));
            float thumbRatio = Math.max(0.15F, Math.min(1.0F, listH / totalH));
            float thumbH = sbTrackH * thumbRatio;
            float scrollRatio = Math.max(0.0F, Math.min(1.0F, -this.secondaryScroll / maxScroll));
            float thumbY = sbTrackY + (sbTrackH - thumbH) * scrollRatio;
            int thumbCol = dark ? 1627389951 : 1342177280;
            RoundedRectShader.draw(context, sbTrackX, thumbY, 2.0F, thumbH, 1.0F, this.withAlpha(thumbCol, anim * switchAlpha));
         }

      } else {
         String raw = EventFetcher.holyWorldEvents;
         int cardBg = dark ? -1072425959 : -704643073;
         RoundedRectShader.draw(context, x, listY, w, 70.0F, 7.0F, this.withAlpha(cardBg, anim * switchAlpha));
         SFUI.draw(context, raw != null ? raw : "Загрузка ивентов HolyWorld...", x + 12.0F, listY + 12.0F, 8.0F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
      }
   }

   private void drawThemesScreen(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      float switchOffset = (1.0F - this.tabSwitchAnim) * 14.0F;
      float switchAlpha = this.tabSwitchAnim;
      float curY = y + switchOffset;
      SFUI.draw(context, "Палитра и Темы Интерфейса", x, curY + 1.0F, 11.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      SFUI.draw(context, "Выберите готовый пресет или настройте градиент темы", x, curY + 13.0F, 7.5F, this.withAlpha(dark ? -8487282 : -8750454, anim * switchAlpha));
      curY += 25.0F;
      float topBarH = 34.0F;
      int topBarBg = dark ? -1072425959 : -704643073;
      RoundedRectShader.draw(context, x, curY, w, topBarH, 8.0F, this.withAlpha(topBarBg, anim * switchAlpha));
      float[] hsv1 = (float[])ClientData.colorSettings.getOrDefault("Theme Color 1", new float[]{0.58F, 0.8F, 1.0F});
      float[] hsv2 = (float[])ClientData.colorSettings.getOrDefault("Theme Color 2", new float[]{0.85F, 0.7F, 1.0F});
      int c1 = Color.HSBtoRGB(hsv1[0], hsv1[1], hsv1[2]);
      int c2 = Color.HSBtoRGB(hsv2[0], hsv2[1], hsv2[2]);
      float gradX = x + 10.0F;
      float gradY = curY + 9.0F;
      float gradW = w - 175.0F;
      float gradH = 16.0F;
      RoundedRectShader.drawGradient(context, gradX, gradY, gradW, gradH, 5.0F, this.withAlpha(c1, anim * switchAlpha), this.withAlpha(c2, anim * switchAlpha));
      RoundedRectShader.draw(context, gradX - 1.0F, gradY - 1.0F, gradW + 2.0F, gradH + 2.0F, 6.0F, this.withAlpha(dark ? 637534207 : 352321536, anim * switchAlpha));
      float swW = 72.0F;
      float sw1X = x + w - swW * 2.0F - 10.0F;
      float sw2X = x + w - swW - 6.0F;
      float swY = curY + 7.0F;
      float swH = 20.0F;
      RoundedRectShader.draw(context, sw1X, swY, swW, swH, 4.0F, this.withAlpha(dark ? -14540244 : -2038548, anim * switchAlpha));
      RoundedRectShader.draw(context, sw1X + 5.0F, swY + 5.0F, 10.0F, 10.0F, 5.0F, this.withAlpha(c1, anim * switchAlpha));
      SFUI.draw(context, "Цвет 1", sw1X + 18.0F, swY + 6.5F, 7.0F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
      this.clickBounds.put("open_palette:Theme Color 1", new int[]{(int)sw1X, (int)swY, (int)swW, (int)swH});
      RoundedRectShader.draw(context, sw2X, swY, swW, swH, 4.0F, this.withAlpha(dark ? -14540244 : -2038548, anim * switchAlpha));
      RoundedRectShader.draw(context, sw2X + 5.0F, swY + 5.0F, 10.0F, 10.0F, 5.0F, this.withAlpha(c2, anim * switchAlpha));
      SFUI.draw(context, "Цвет 2", sw2X + 18.0F, swY + 6.5F, 7.0F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
      this.clickBounds.put("open_palette:Theme Color 2", new int[]{(int)sw2X, (int)swY, (int)swW, (int)swH});
      curY += topBarH + 12.0F;
      SFUI.draw(context, "Дизайнерские пресеты (12 вариантов)", x, curY, 8.5F, this.withAlpha(dark ? -7697766 : -10132107, anim * switchAlpha));
      curY += 10.0F;
      String[][] presets = new String[][]{{"Lexora Purple", "#8A2BE2", "#DA70D6"}, {"Ocean Wave", "#00C6FF", "#0072FF"}, {"Emerald Matrix", "#00F260", "#0575E6"}, {"Sunset Fire", "#FF4E50", "#F9D423"}, {"Rose Sakura", "#F857A6", "#FF5858"}, {"Monochrome", "#4B6CB7", "#182848"}, {"Cyber Neon", "#00F5D4", "#7B2CBF"}, {"Blood Moon", "#FF0844", "#FFB199"}, {"Golden Hour", "#F6D365", "#FDA085"}, {"Arctic Frost", "#89F7FE", "#66A6FF"}, {"Toxic Lime", "#11998E", "#38EF7D"}, {"Vaporwave", "#F72585", "#7209B7"}};
      float pW = (w - 14.0F) / 3.0F;
      float pH = 24.0F;

      for(int i = 0; i < presets.length; ++i) {
         float px = x + (float)(i % 3) * (pW + 7.0F);
         float py = curY + (float)(i / 3) * (pH + 5.0F);
         int pc1 = this.parseHex(presets[i][1]);
         int pc2 = this.parseHex(presets[i][2]);
         boolean hPreset = this.inside(this.currentMouseX, this.currentMouseY, px, py, pW, pH);
         float hAnim = this.updateHover("preset:" + presets[i][0], hPreset);
         int pBg = this.interpolateColor(dark ? -1072425959 : -704643073, dark ? -14540244 : -1512459, hAnim);
         RoundedRectShader.draw(context, px, py, pW, pH, 5.0F, this.withAlpha(pBg, anim * switchAlpha));
         float circleX = px + 8.0F;
         float circleY = py + 7.0F;
         RoundedRectShader.drawGradient(context, circleX, circleY, 10.0F, 10.0F, 5.0F, this.withAlpha(pc1, anim * switchAlpha), this.withAlpha(pc2, anim * switchAlpha));
         SFUI.draw(context, presets[i][0], px + 24.0F, py + 8.5F, 7.2F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
         this.clickBounds.put("apply_preset:" + presets[i][1] + ":" + presets[i][2], new int[]{(int)px, (int)py, (int)pW, (int)pH});
      }

   }

   private int parseHex(String hex) {
      try {
         return -16777216 | Integer.parseInt(hex.replace("#", ""), 16);
      } catch (Exception var3) {
         return -7722014;
      }
   }

   private void drawGuiSettingsScreen(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      float switchOffset = (1.0F - this.tabSwitchAnim) * 14.0F;
      float switchAlpha = this.tabSwitchAnim;
      float curY = y + switchOffset;
      SFUI.draw(context, "Настройки Интерфейса", x, curY + 1.0F, 11.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      SFUI.draw(context, "Кастомизация звуков, горячих клавиш и поведения меню", x, curY + 13.0F, 7.5F, this.withAlpha(dark ? -8487282 : -8750454, anim * switchAlpha));
      curY += 28.0F;
      int cardBg = dark ? -1072425959 : -704643073;
      float card1H = 92.0F;
      RoundedRectShader.draw(context, x, curY, w, card1H, 7.0F, this.withAlpha(cardBg, anim * switchAlpha));
      SFUI.draw(context, "Звуковые эффекты", x + 12.0F, curY + 9.0F, 8.5F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      this.drawSettingRow(context, x + 12.0F, curY + 22.0F, w - 24.0F, "Settings", ModernSetting.mode("GuiSounds", "Звуки интерфейса", "On", "Off"), dark, anim * switchAlpha);
      this.drawSettingRow(context, x + 12.0F, curY + 54.0F, w - 24.0F, "Settings", ModernSetting.mode("ModuleSoundMode", "Стиль щелчка", "Default", "Sound 1", "Sound 2", "Sound 3", "Sound 4", "Sound 5"), dark, anim * switchAlpha);
      curY += card1H + 12.0F;
      float card2H = 55.0F;
      RoundedRectShader.draw(context, x, curY, w, card2H, 7.0F, this.withAlpha(cardBg, anim * switchAlpha));
      SFUI.draw(context, "Горячие клавиши", x + 12.0F, curY + 9.0F, 8.5F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      Integer guiBind = (Integer)ClientData.moduleBinds.get("GUI");
      String guiBindText = this.bindingTarget != null && this.bindingTarget.equals("GUI") ? "..." : (guiBind != null && guiBind > 0 ? GLFW.glfwGetKeyName(guiBind, 0) : "RSHIFT");
      SFUI.draw(context, "Открыть ClickGUI", x + 12.0F, curY + 26.0F, 7.5F, this.withAlpha(dark ? -2565920 : -14671832, anim * switchAlpha));
      SFUI.draw(context, "Клавиша для вызова меню клиента", x + 12.0F, curY + 36.0F, 6.5F, this.withAlpha(dark ? -10132107 : -7697766, anim * switchAlpha));
      float bW = 46.0F;
      float bX = x + w - bW - 12.0F;
      RoundedRectShader.draw(context, bX, curY + 25.0F, bW, 16.0F, 3.5F, this.withAlpha(dark ? -1876613840 : -1864507160, anim * switchAlpha));
      SFUI.draw(context, guiBindText.toUpperCase(), bX + (bW - SFUI.getWidth(guiBindText.toUpperCase(), 7.0F)) / 2.0F, curY + 29.5F, 7.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      this.clickBounds.put("bind:GUI", new int[]{(int)bX, (int)curY + 25, (int)bW, 16});
   }

   private void drawConfigsScreen(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      float switchOffset = (1.0F - this.tabSwitchAnim) * 14.0F;
      float switchAlpha = this.tabSwitchAnim;
      float curY = y + switchOffset;
      SFUI.draw(context, "Конфигурации", x, curY + 1.0F, 11.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      SFUI.draw(context, "Управление сохранениями и профилями настроек", x, curY + 13.0F, 7.5F, this.withAlpha(dark ? -8487282 : -8750454, anim * switchAlpha));
      curY += 28.0F;
      RoundedRectShader.draw(context, x, curY, w - 80.0F, 22.0F, 5.0F, this.withAlpha(dark ? -1876942808 : -1864309523, anim * switchAlpha));
      String text = this.configInput.isEmpty() && !this.configInputFocused ? "Имя нового конфига..." : this.configInput;
      int tCol = this.configInput.isEmpty() && !this.configInputFocused ? (dark ? -11184794 : -6710870) : (dark ? -1 : -15461352);
      SFUI.draw(context, text, x + 8.0F, curY + 6.0F, 7.5F, this.withAlpha(tCol, anim * switchAlpha));
      this.clickBounds.put("input:config", new int[]{(int)x, (int)curY, (int)w - 80, 22});
      boolean hBtn = this.inside(this.currentMouseX, this.currentMouseY, x + w - 70.0F, curY, 70.0F, 22.0F);
      float hBtnAnim = this.updateHover("btn:cfg_create", hBtn);
      int btnBg = this.interpolateColor(dark ? -1 : -15461352, dark ? -2039572 : -14013896, hBtnAnim);
      RoundedRectShader.draw(context, x + w - 70.0F, curY, 70.0F, 22.0F, 5.0F, this.withAlpha(btnBg, anim * switchAlpha));
      SFUI.draw(context, "Создать", x + w - 56.0F, curY + 6.0F, 7.5F, this.withAlpha(dark ? -15856110 : -1, anim * switchAlpha));
      this.clickBounds.put("btn:create_config", new int[]{(int)(x + w - 70.0F), (int)curY, 70, 22});
      curY += 30.0F;

      for(String cfg : LexoraGui.savedConfigs) {
         boolean sel = cfg.equals(this.selectedConfig);
         boolean hRow = this.inside(this.currentMouseX, this.currentMouseY, x, curY, w, 26.0F);
         float hRowAnim = this.updateHover("cfg_row:" + cfg, hRow);
         int rowBg = sel ? (dark ? -14013896 : -2564888) : this.interpolateColor(dark ? 1880627232 : 1894444271, dark ? -1876811220 : -1864375573, hRowAnim);
         RoundedRectShader.draw(context, x, curY, w, 26.0F, 5.0F, this.withAlpha(rowBg, anim * switchAlpha));
         SFUI.draw(context, cfg, x + 8.0F, curY + 8.0F, 8.0F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
         float bX = x + w - 145.0F;
         RoundedRectShader.draw(context, bX, curY + 4.0F, 42.0F, 18.0F, 3.5F, this.withAlpha(dark ? -1875890112 : -1865888552, anim * switchAlpha));
         SFUI.draw(context, "Загрузить", bX + 3.0F, curY + 8.5F, 7.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
         this.clickBounds.put("cfg_load:" + cfg, new int[]{(int)bX, (int)curY + 4, 42, 18});
         bX += 46.0F;
         RoundedRectShader.draw(context, bX, curY + 4.0F, 46.0F, 18.0F, 3.5F, this.withAlpha(dark ? -1875890112 : -1865888552, anim * switchAlpha));
         SFUI.draw(context, "Сохранить", bX + 3.0F, curY + 8.5F, 7.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
         this.clickBounds.put("cfg_save:" + cfg, new int[]{(int)bX, (int)curY + 4, 46, 18});
         bX += 50.0F;
         RoundedRectShader.draw(context, bX, curY + 4.0F, 40.0F, 18.0F, 3.5F, this.withAlpha(-1862319036, anim * switchAlpha));
         SFUI.draw(context, "Удалить", bX + 5.0F, curY + 8.5F, 7.0F, this.withAlpha(-1, anim * switchAlpha));
         this.clickBounds.put("cfg_del:" + cfg, new int[]{(int)bX, (int)curY + 4, 40, 18});
         curY += 32.0F;
      }

   }

   private void drawFriendsScreen(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      float switchOffset = (1.0F - this.tabSwitchAnim) * 14.0F;
      float switchAlpha = this.tabSwitchAnim;
      float curY = y + switchOffset;
      SFUI.draw(context, "Пати и Друзья", x, curY + 1.0F, 11.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      SFUI.draw(context, "Совместная игра, статус участников и общий чат", x, curY + 13.0F, 7.5F, this.withAlpha(dark ? -8487282 : -8750454, anim * switchAlpha));
      curY += 26.0F;
      int cardBg = dark ? -1072425959 : -704643073;
      RoundedRectShader.draw(context, x, curY, w, 32.0F, 5.0F, this.withAlpha(cardBg, anim * switchAlpha));
      SFUI.draw(context, "Горячая клавиша хелпы (GPS метка в пати):", x + 12.0F, curY + 11.0F, 7.5F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
      float bindW = 75.0F;
      float bindX = x + w - bindW - 10.0F;
      float bindY = curY + 7.0F;
      boolean hBind = this.inside(this.currentMouseX, this.currentMouseY, bindX, bindY, bindW, 18.0F);
      float hBindAnim = this.updateHover("btn:party_bind", hBind);
      int bKeyVal = BindManager.getStoredBindValue("PartyHelpBind");
      if (bKeyVal == -1) {
         bKeyVal = BindManager.getStoredBindValue("PartyWaypointBind");
      }

      String bindText = this.bindingTarget != null && this.bindingTarget.equals("PartyHelpBind") ? "..." : (bKeyVal != -1 && bKeyVal != -1 ? BindManager.formatBindName(bKeyVal) : "NONE");
      int bindBg = this.interpolateColor(dark ? -14540242 : -2038804, dark ? -13487550 : -1, hBindAnim);
      RoundedRectShader.draw(context, bindX, bindY, bindW, 18.0F, 4.0F, this.withAlpha(bindBg, anim * switchAlpha));
      float txtW = SFUI.getWidth(bindText, 7.0F);
      SFUI.draw(context, bindText, bindX + (bindW - txtW) / 2.0F, bindY + 5.0F, 7.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      this.clickBounds.put("bind:PartyHelpBind", new int[]{(int)bindX, (int)bindY, (int)bindW, 18});
      curY += 38.0F;
      List<LexoraPartyManager.PendingRequest> pending = LexoraPartyManager.pendingRequests;
      if (!pending.isEmpty()) {
         SFUI.draw(context, "Входящие запросы (" + pending.size() + "):", x, curY, 8.5F, this.withAlpha(-15681151, anim * switchAlpha));
         curY += 13.0F;

         for(LexoraPartyManager.PendingRequest req : pending) {
            RoundedRectShader.draw(context, x, curY, w, 36.0F, 5.5F, this.withAlpha(dark ? -535160284 : -436207617, anim * switchAlpha));
            RoundedRectShader.draw(context, x, curY, 3.0F, 36.0F, 1.5F, this.withAlpha(-15681151, anim * switchAlpha));
            SFUI.draw(context, req.name, x + 12.0F, curY + 7.0F, 8.5F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
            SFUI.draw(context, "Хочет присоединиться к вашей команде", x + 12.0F, curY + 19.0F, 7.0F, this.withAlpha(dark ? -7829351 : -8750454, anim * switchAlpha));
            float acW = 68.0F;
            float btnH = 20.0F;
            float acX = x + w - acW * 2.0F - 18.0F;
            float btnY = curY + 8.0F;
            boolean hAc = this.inside(this.currentMouseX, this.currentMouseY, acX, btnY, acW, btnH);
            float hAcA = this.updateHover("btn:req_ac:" + req.uuid, hAc);
            int acCol = this.interpolateColor(-15681151, -13315175, hAcA);
            RoundedRectShader.draw(context, acX, btnY, acW, btnH, 4.0F, this.withAlpha(acCol, anim * switchAlpha));
            float lAcW = SFUI.getWidth("Принять", 7.0F);
            SFUI.draw(context, "Принять", acX + (acW - lAcW) / 2.0F, btnY + 5.5F, 7.0F, this.withAlpha(-1, anim * switchAlpha));
            this.clickBounds.put("party:accept:" + req.uuid, new int[]{(int)acX, (int)btnY, (int)acW, (int)btnH});
            float decX = acX + acW + 6.0F;
            boolean hDec = this.inside(this.currentMouseX, this.currentMouseY, decX, btnY, acW, btnH);
            float hDecA = this.updateHover("btn:req_dec:" + req.uuid, hDec);
            int decCol = this.interpolateColor(-1096636, -495247, hDecA);
            RoundedRectShader.draw(context, decX, btnY, acW, btnH, 4.0F, this.withAlpha(decCol, anim * switchAlpha));
            float lDecW = SFUI.getWidth("Отклонить", 7.0F);
            SFUI.draw(context, "Отклонить", decX + (acW - lDecW) / 2.0F, btnY + 5.5F, 7.0F, this.withAlpha(-1, anim * switchAlpha));
            this.clickBounds.put("party:decline:" + req.uuid, new int[]{(int)decX, (int)btnY, (int)acW, (int)btnH});
            curY += 42.0F;
         }

         curY += 4.0F;
      }

      RoundedRectShader.draw(context, x, curY, w, 44.0F, 6.0F, this.withAlpha(cardBg, anim * switchAlpha));
      SFUI.draw(context, "Код команды:", x + 12.0F, curY + 8.0F, 7.5F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
      String code = LexoraPartyManager.partyCode;
      SFUI.draw(context, code != null && !code.isEmpty() ? code : "Вы не состоите в пати", x + 12.0F, curY + 22.0F, 9.5F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      float bX = x + w - 85.0F;
      boolean hParty = this.inside(this.currentMouseX, this.currentMouseY, bX, curY + 9.0F, 75.0F, 24.0F);
      float hPartyAnim = this.updateHover("btn:party", hParty);
      int partyBg = this.interpolateColor(dark ? -1 : -15461352, dark ? -2039572 : -14013896, hPartyAnim);
      RoundedRectShader.draw(context, bX, curY + 9.0F, 75.0F, 24.0F, 4.5F, this.withAlpha(partyBg, anim * switchAlpha));
      String bText = LexoraPartyManager.inParty() ? "Покинуть" : "Создать";
      float bTextW = SFUI.getWidth(bText, 7.5F);
      SFUI.draw(context, bText, bX + (75.0F - bTextW) / 2.0F, curY + 17.0F, 7.5F, this.withAlpha(dark ? -15856110 : -1, anim * switchAlpha));
      this.clickBounds.put("party:toggle", new int[]{(int)bX, (int)curY + 9, 75, 24});
      curY += 52.0F;
      if (LexoraPartyManager.inParty()) {
         List<LexoraPartyManager.PartyMember> members = LexoraPartyManager.members;
         MsdfFont var10000 = SFUI;
         int var10002 = members.size();
         var10000.draw(context, "Участники (" + var10002 + "/10):", x, curY, 8.5F, this.withAlpha(dark ? -3355444 : -13421773, anim * switchAlpha));
         curY += 13.0F;

         for(LexoraPartyManager.PartyMember m : members) {
            RoundedRectShader.draw(context, x, curY, w, 28.0F, 4.5F, this.withAlpha(cardBg, anim * switchAlpha));
            int dotCol = m.online ? -15681151 : -9342598;
            RoundedRectShader.draw(context, x + 12.0F, curY + 11.0F, 6.0F, 6.0F, 3.0F, this.withAlpha(dotCol, anim * switchAlpha));
            SFUI.draw(context, m.name, x + 24.0F, curY + 9.0F, 8.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
            if (m.server != null && !m.server.isEmpty()) {
               float sW = SFUI.getWidth(m.server, 7.0F);
               SFUI.draw(context, m.server, x + w - sW - 12.0F, curY + 9.5F, 7.0F, this.withAlpha(dark ? -8947832 : -7829351, anim * switchAlpha));
            }

            curY += 32.0F;
         }

         curY += 6.0F;
      }

      RoundedRectShader.draw(context, x, curY, w, 44.0F, 6.0F, this.withAlpha(cardBg, anim * switchAlpha));
      SFUI.draw(context, "Присоединиться к чужой пати:", x + 12.0F, curY + 8.0F, 7.5F, this.withAlpha(dark ? -1118478 : -15461352, anim * switchAlpha));
      float inputW = w - 110.0F;
      float inputX = x + 12.0F;
      float inputY = curY + 19.0F;
      int inBg = this.partyCodeInputFocused ? (dark ? -14540242 : -2564888) : (dark ? -15066590 : -1709840);
      RoundedRectShader.draw(context, inputX, inputY, inputW, 18.0F, 3.5F, this.withAlpha(inBg, anim * switchAlpha));
      String pText = this.partyCodeInput.isEmpty() && !this.partyCodeInputFocused ? "Введите 10-значный код..." : this.partyCodeInput;
      int pCol = this.partyCodeInput.isEmpty() && !this.partyCodeInputFocused ? (dark ? -11184794 : -6710870) : (dark ? -1 : -15461352);
      SFUI.draw(context, pText, inputX + 6.0F, inputY + 5.0F, 7.0F, this.withAlpha(pCol, anim * switchAlpha));
      this.clickBounds.put("input:party_code", new int[]{(int)inputX, (int)inputY, (int)inputW, 18});
      float jbX = x + w - 85.0F;
      boolean hJoin = this.inside(this.currentMouseX, this.currentMouseY, jbX, inputY, 75.0F, 18.0F);
      float hJoinAnim = this.updateHover("btn:party_join", hJoin);
      int joinBg = this.interpolateColor(dark ? -14013896 : -3090976, dark ? -1 : -15461352, hJoinAnim);
      RoundedRectShader.draw(context, jbX, inputY, 75.0F, 18.0F, 3.5F, this.withAlpha(joinBg, anim * switchAlpha));
      String jText = LexoraPartyManager.waitingForResponse ? "Ожидание..." : "Присоединиться";
      float jTextW = SFUI.getWidth(jText, 6.5F);
      SFUI.draw(context, jText, jbX + (75.0F - jTextW) / 2.0F, inputY + 5.0F, 6.5F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      this.clickBounds.put("party:join", new int[]{(int)jbX, (int)inputY, 75, 18});
   }

   private void drawWaypointsScreen(DrawContext context, float x, float y, float w, float h, boolean dark, float anim) {
      float switchOffset = (1.0F - this.tabSwitchAnim) * 14.0F;
      float switchAlpha = this.tabSwitchAnim;
      float curY = y + switchOffset;
      List<GPS.GpsWaypoint> waypoints = GPS.getWaypoints();
      SFUI.draw(context, "GPS Метки и Редактор", x, curY + 1.0F, 11.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
      SFUI.draw(context, "Создание, редактирование координат и выбор кастомных иконок", x, curY + 13.0F, 7.5F, this.withAlpha(dark ? -8487282 : -8750454, anim * switchAlpha));
      float clrW = 72.0F;
      float btnH = 18.0F;
      float btnY = curY + 2.0F;
      float clrX = x + w - clrW;
      boolean hClr = this.inside(this.currentMouseX, this.currentMouseY, clrX, btnY, clrW, btnH);
      float hClrA = this.updateHover("btn:gps_clear_all", hClr);
      int clrBg = this.interpolateColor(dark ? 1090470980 : 637486148, -2130754492, hClrA);
      RoundedRectShader.draw(context, clrX, btnY, clrW, btnH, 4.0F, this.withAlpha(clrBg, anim * switchAlpha));
      float clrTxtW = SFUI.getWidth("Очистить все", 7.0F);
      SFUI.draw(context, "Очистить все", clrX + (clrW - clrTxtW) / 2.0F, btnY + 5.0F, 7.0F, this.withAlpha(-1, anim * switchAlpha));
      this.clickBounds.put("btn:gps_clear_all", new int[]{(int)clrX, (int)btnY, (int)clrW, (int)btnH});
      float addW = 90.0F;
      float addX = clrX - addW - 6.0F;
      boolean hAdd = this.inside(this.currentMouseX, this.currentMouseY, addX, btnY, addW, btnH);
      float hAddA = this.updateHover("wp:add_new", hAdd);
      int addBg = this.interpolateColor(dark ? 1611708801 : 1074837889, -1877952127, hAddA);
      RoundedRectShader.draw(context, addX, btnY, addW, btnH, 4.0F, this.withAlpha(addBg, anim * switchAlpha));
      float addTxtW = SFUI.getWidth("+ Новая метка", 7.0F);
      SFUI.draw(context, "+ Новая метка", addX + (addW - addTxtW) / 2.0F, btnY + 5.0F, 7.0F, this.withAlpha(-1, anim * switchAlpha));
      this.clickBounds.put("wp:add_new", new int[]{(int)addX, (int)btnY, (int)addW, (int)btnH});
      curY += 26.0F;
      float panelsH = h - (curY - y);
      float col1W = 185.0F;
      float col2X = x + col1W + 10.0F;
      float col2W = w - col1W - 10.0F;
      int cardBg = dark ? -1072425959 : -704643073;
      RoundedRectShader.draw(context, x, curY, col1W, panelsH, 8.0F, this.withAlpha(cardBg, anim * switchAlpha));
      MsdfFont var10000 = SFUI;
      int var10002 = waypoints.size();
      var10000.draw(context, "СПИСОК МЕТОК (" + var10002 + ")", x + 10.0F, curY + 8.0F, 7.0F, this.withAlpha(dark ? -8750454 : -7697766, anim * switchAlpha));
      float listStartY = curY + 22.0F;
      float listAreaH = panelsH - 26.0F;
      if (waypoints.isEmpty()) {
         SFUI.draw(context, "Нет созданных меток", x + 12.0F, listStartY + 12.0F, 8.0F, this.withAlpha(dark ? -10132107 : -6974043, anim * switchAlpha));
         SFUI.draw(context, "Нажмите «+ Новая метка»", x + 12.0F, listStartY + 24.0F, 7.0F, this.withAlpha(dark ? -12237483 : -5197632, anim * switchAlpha));
      } else {
         this.secondaryScroll += (this.targetSecondaryScroll - this.secondaryScroll) * 0.25F;
         float rowH = 34.0F;
         float totalH = (float)waypoints.size() * rowH;
         float maxScroll = Math.max(0.0F, totalH - listAreaH + 6.0F);
         if (this.targetSecondaryScroll < -maxScroll) {
            this.targetSecondaryScroll = -maxScroll;
         }

         if (this.targetSecondaryScroll > 0.0F) {
            this.targetSecondaryScroll = 0.0F;
         }

         float rowDrawY = listStartY + this.secondaryScroll;
         context.enableScissor((int)x, (int)listStartY, (int)(x + col1W), (int)(listStartY + listAreaH));
         PlayerEntity player = MinecraftClient.getInstance().player;
         if (wpSelected == null || !waypoints.contains(wpSelected)) {
            this.selectWaypointForEdit((GPS.GpsWaypoint)waypoints.get(0));
         }

         for(int i = 0; i < waypoints.size(); ++i) {
            GPS.GpsWaypoint wp = (GPS.GpsWaypoint)waypoints.get(i);
            if (rowDrawY + rowH >= listStartY - rowH && rowDrawY <= listStartY + listAreaH + rowH) {
               boolean isSel = wp == wpSelected;
               boolean isHov = this.inside(this.currentMouseX, this.currentMouseY, x + 5.0F, rowDrawY, col1W - 10.0F, rowH - 4.0F);
               float hovA = this.updateHover("wp_row:" + i, isHov);
               int rowColor = isSel ? (dark ? -534502348 : -522000654) : this.interpolateColor(dark ? 1612191778 : 1626337784, dark ? -1876942802 : -1863915020, hovA);
               RoundedRectShader.draw(context, x + 5.0F, rowDrawY, col1W - 10.0F, rowH - 4.0F, 5.0F, this.withAlpha(rowColor, anim * switchAlpha));
               int accent = wp.color;
               RoundedRectShader.draw(context, x + 5.0F, rowDrawY, 2.5F, rowH - 4.0F, 1.2F, this.withAlpha(accent, anim * switchAlpha));
               int iconIdx = wp.iconBackgroundIndex % GPS.MARKER_TEXTURES.length;
               Identifier iconTex = GPS.MARKER_TEXTURES[iconIdx];
               int iconBoxSize = 22;
               float iconX = x + 11.0F;
               float iconY = rowDrawY + (rowH - 4.0F - (float)iconBoxSize) / 2.0F;
               int ibBg = dark ? -1877929704 : -1864835864;
               RoundedRectShader.draw(context, iconX, iconY, (float)iconBoxSize, (float)iconBoxSize, 4.5F, this.withAlpha(ibBg, anim * switchAlpha));
               int innerSz = 16;
               float inX = iconX + (float)(iconBoxSize - innerSz) / 2.0F;
               float inY = iconY + (float)(iconBoxSize - innerSz) / 2.0F;
               GPS.ensureLinearFilter(iconTex);
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, anim * switchAlpha);
               context.drawTexture(GPS::getSmoothGuiTextured, iconTex, (int)inX, (int)inY, 0.0F, 0.0F, innerSz, innerSz, innerSz, innerSz);
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
               String nameText = wp.name != null ? wp.name : "Метка";
               if (SFUI.getWidth(nameText, 8.0F) > col1W - 75.0F) {
                  String var130 = nameText.substring(0, Math.min(nameText.length(), 10));
                  nameText = var130 + "..";
               }

               SFUI.draw(context, nameText, iconX + (float)iconBoxSize + 7.0F, rowDrawY + 6.5F, 8.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
               if (player != null) {
                  double dist = Math.sqrt(player.squaredDistanceTo(wp.x, wp.y, wp.z));
                  String distStr = dist > 1000.0 ? String.format(Locale.ROOT, "%.1f км", dist / 1000.0) : String.format(Locale.ROOT, "%d м", (int)dist);
                  SFUI.draw(context, distStr, iconX + (float)iconBoxSize + 7.0F, rowDrawY + 17.0F, 6.8F, this.withAlpha(dark ? -8750454 : -7697766, anim * switchAlpha));
               }

               float delBtnX = x + col1W - 24.0F;
               float delBtnY = rowDrawY + 6.0F;
               boolean hDel = this.inside(this.currentMouseX, this.currentMouseY, delBtnX, delBtnY, 15.0F, 15.0F);
               float hDelA = this.updateHover("wp_del_quick:" + i, hDel);
               int delCol = this.interpolateColor(dark ? -10461072 : -7697766, -48060, hDelA);
               SFUI.draw(context, "✕", delBtnX + 3.5F, delBtnY + 3.5F, 7.5F, this.withAlpha(delCol, anim * switchAlpha));
               this.clickBounds.put("wp_del_row:" + i, new int[]{(int)delBtnX, (int)delBtnY, 15, 15});
               this.clickBounds.put("wp_select:" + i, new int[]{(int)(x + 5.0F), (int)rowDrawY, (int)(col1W - 32.0F), (int)(rowH - 4.0F)});
            }

            rowDrawY += rowH;
         }

         context.disableScissor();
      }

      RoundedRectShader.draw(context, col2X, curY, col2W, panelsH, 8.0F, this.withAlpha(cardBg, anim * switchAlpha));
      if (wpSelected == null) {
         float midX = col2X + col2W / 2.0F;
         float midY = curY + panelsH / 2.0F;
         ModernGuiIcons.draw(context, ModernGuiIcons.Icon.PIN, midX - 12.0F, midY - 24.0F, 24.0F, this.withAlpha(dark ? -12237483 : -5197632, anim * switchAlpha));
         String emptyHint = "Выберите метку слева для редактирования";
         float hintW = SFUI.getWidth(emptyHint, 8.5F);
         SFUI.draw(context, emptyHint, midX - hintW / 2.0F, midY + 8.0F, 8.5F, this.withAlpha(dark ? -10132107 : -7697766, anim * switchAlpha));
      } else {
         float edPad = 14.0F;
         float edY = curY + edPad;
         int curIconIdx = wpSelected.iconBackgroundIndex % GPS.MARKER_TEXTURES.length;
         Identifier curIconTex = GPS.MARKER_TEXTURES[curIconIdx];
         int headBoxSz = 24;
         float headBoxX = col2X + edPad;
         RoundedRectShader.draw(context, headBoxX, edY, (float)headBoxSz, (float)headBoxSz, 5.0F, this.withAlpha(dark ? -15000792 : -2170387, anim * switchAlpha));
         int headAccent = curIconIdx < GPS.MARKER_COLORS.length ? GPS.MARKER_COLORS[curIconIdx] : -11755777;
         RoundedRectShader.drawOutline(context, headBoxX, edY, (float)headBoxSz, (float)headBoxSz, 5.0F, 1.0F, this.withAlpha(headAccent, anim * switchAlpha));
         int innerHeadSz = 18;
         float inHX = headBoxX + (float)(headBoxSz - innerHeadSz) / 2.0F;
         float inHY = edY + (float)(headBoxSz - innerHeadSz) / 2.0F;
         GPS.ensureLinearFilter(curIconTex);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, anim * switchAlpha);
         context.drawTexture(GPS::getSmoothGuiTextured, curIconTex, (int)inHX, (int)inHY, 0.0F, 0.0F, innerHeadSz, innerHeadSz, innerHeadSz, innerHeadSz);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         String var132 = wpSelected.name != null ? wpSelected.name : "";
         SFUI.draw(context, "Настройки метки: " + var132, col2X + edPad + 30.0F, edY + 3.0F, 9.0F, this.withAlpha(dark ? -1 : -15461352, anim * switchAlpha));
         String var131 = (new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())).format(new Date(wpSelected.createdAt));
         String createdDate = "Создана: " + var131;
         SFUI.draw(context, createdDate, col2X + edPad + 30.0F, edY + 14.0F, 6.8F, this.withAlpha(dark ? -9539970 : -7697766, anim * switchAlpha));
         edY += 28.0F;
         SFUI.draw(context, "НАЗВАНИЕ МЕТКИ", col2X + edPad, edY, 6.8F, this.withAlpha(dark ? -8750454 : -7697766, anim * switchAlpha));
         edY += 10.0F;
         float nameFieldW = col2W - edPad * 2.0F;
         float fieldH = 19.0F;
         int nameFieldBg = wpFocusedField == 1 ? (dark ? -14540238 : -2564888) : (dark ? -2145904606 : -2132219150);
         RoundedRectShader.draw(context, col2X + edPad, edY, nameFieldW, fieldH, 4.0F, this.withAlpha(nameFieldBg, anim * switchAlpha));
         if (wpFocusedField == 1) {
            RoundedRectShader.draw(context, col2X + edPad - 1.0F, edY - 1.0F, nameFieldW + 2.0F, fieldH + 2.0F, 5.0F, this.withAlpha(-11755777, 0.5F * anim * switchAlpha));
         }

         String shownName = wpEditName;
         if (wpFocusedField == 1 && System.currentTimeMillis() % 900L < 450L) {
            shownName = shownName + "|";
         }

         SFUI.draw(context, shownName.isEmpty() ? "Введите название..." : shownName, col2X + edPad + 8.0F, edY + 5.0F, 7.5F, this.withAlpha(shownName.isEmpty() ? (dark ? -10461072 : -6645080) : (dark ? -1 : -15461352), anim * switchAlpha));
         this.clickBounds.put("wp_field:name", new int[]{(int)(col2X + edPad), (int)edY, (int)nameFieldW, (int)fieldH});
         edY += fieldH + 12.0F;
         SFUI.draw(context, "КООРДИНАТЫ (X / Y / Z)", col2X + edPad, edY, 6.8F, this.withAlpha(dark ? -8750454 : -7697766, anim * switchAlpha));
         float fillBtnW = 75.0F;
         float fillBtnH = 13.0F;
         float fillBtnX = col2X + col2W - edPad - fillBtnW;
         boolean hFill = this.inside(this.currentMouseX, this.currentMouseY, fillBtnX, edY - 2.0F, fillBtnW, fillBtnH);
         float hFillA = this.updateHover("wp_fill_cur", hFill);
         int fillCol = this.interpolateColor(dark ? -2145049291 : -2133468955, dark ? -11755777 : -13796640, hFillA);
         RoundedRectShader.draw(context, fillBtnX, edY - 2.0F, fillBtnW, fillBtnH, 3.0F, this.withAlpha(fillCol, anim * switchAlpha));
         float fillTxtW = SFUI.getWidth("Моя позиция", 6.5F);
         SFUI.draw(context, "Моя позиция", fillBtnX + (fillBtnW - fillTxtW) / 2.0F, edY + 1.5F, 6.5F, this.withAlpha(-1, anim * switchAlpha));
         this.clickBounds.put("wp_fill_current", new int[]{(int)fillBtnX, (int)(edY - 2.0F), (int)fillBtnW, (int)fillBtnH});
         edY += 10.0F;
         float coordGap = 6.0F;
         float coordW = (nameFieldW - coordGap * 2.0F) / 3.0F;
         this.drawCoordEditField(context, "X: ", wpEditX, col2X + edPad, edY, coordW, fieldH, wpFocusedField == 2, "wp_field:x", dark, anim * switchAlpha);
         this.drawCoordEditField(context, "Y: ", wpEditY, col2X + edPad + coordW + coordGap, edY, coordW, fieldH, wpFocusedField == 3, "wp_field:y", dark, anim * switchAlpha);
         this.drawCoordEditField(context, "Z: ", wpEditZ, col2X + edPad + (coordW + coordGap) * 2.0F, edY, coordW, fieldH, wpFocusedField == 4, "wp_field:z", dark, anim * switchAlpha);
         edY += fieldH + 12.0F;
         SFUI.draw(context, "ВЫБЕРИТЕ ИКОНКУ МЕТКИ", col2X + edPad, edY, 6.8F, this.withAlpha(dark ? -8750454 : -7697766, anim * switchAlpha));
         edY += 11.0F;
         int tileSz = 24;
         int tileGap = 6;
         int cols = 5;
         String hoveredMarkerName = null;
         int hoverTooltipX = 0;
         int hoverTooltipY = 0;

         for(int i = 0; i < GPS.MARKER_TEXTURES.length; ++i) {
            int tc = i % cols;
            int tr = i / cols;
            float tx = col2X + edPad + (float)(tc * (tileSz + tileGap));
            float ty = edY + (float)(tr * (tileSz + tileGap));
            boolean isCurrentIcon = wpSelected.iconBackgroundIndex == i;
            boolean isHovIcon = this.inside(this.currentMouseX, this.currentMouseY, tx, ty, (float)tileSz, (float)tileSz);
            int iconAccent = i < GPS.MARKER_COLORS.length ? GPS.MARKER_COLORS[i] : -11755777;
            int tileBg = isCurrentIcon ? (dark ? -14540236 : -2959644) : (isHovIcon ? (dark ? -14934998 : -2038544) : (dark ? -15461346 : -1381132));
            RoundedRectShader.draw(context, tx, ty, (float)tileSz, (float)tileSz, 5.0F, this.withAlpha(tileBg, anim * switchAlpha));
            if (isCurrentIcon) {
               RoundedRectShader.drawOutline(context, tx, ty, (float)tileSz, (float)tileSz, 5.0F, 1.2F, this.withAlpha(iconAccent, anim * switchAlpha));
            } else if (isHovIcon) {
               RoundedRectShader.drawOutline(context, tx, ty, (float)tileSz, (float)tileSz, 5.0F, 0.8F, this.withAlpha(1627389951, anim * switchAlpha));
               if (i < GPS.MARKER_NAMES.length) {
                  hoveredMarkerName = GPS.MARKER_NAMES[i];
                  hoverTooltipX = (int)this.currentMouseX + 8;
                  hoverTooltipY = (int)this.currentMouseY - 14;
               }
            }

            int pad = 3;
            int innerTileSz = tileSz - pad * 2;
            GPS.ensureLinearFilter(GPS.MARKER_TEXTURES[i]);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, anim * switchAlpha);
            context.drawTexture(GPS::getSmoothGuiTextured, GPS.MARKER_TEXTURES[i], (int)(tx + (float)pad), (int)(ty + (float)pad), 0.0F, 0.0F, innerTileSz, innerTileSz, innerTileSz, innerTileSz);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            this.clickBounds.put("wp_icon:" + i, new int[]{(int)tx, (int)ty, tileSz, tileSz});
         }

         if (hoveredMarkerName != null) {
            float tw = SFUI.getWidth(hoveredMarkerName, 7.0F);
            RoundedRectShader.draw(context, (float)(hoverTooltipX - 4), (float)(hoverTooltipY - 2), (float)((int)tw + 8), 13.0F, 4.0F, this.withAlpha(-267382760, anim * switchAlpha));
            SFUI.draw(context, hoveredMarkerName, (float)hoverTooltipX, (float)hoverTooltipY + 2.5F, 7.0F, this.withAlpha(-1, anim * switchAlpha));
         }

         edY += (float)(2 * (tileSz + tileGap)) + 12.0F;
         float actH = 22.0F;
         float actW = 95.0F;
         float applyX = col2X + edPad;
         boolean hApply = this.inside(this.currentMouseX, this.currentMouseY, applyX, edY, actW, actH);
         float hApplyA = this.updateHover("wp_apply", hApply);
         int applyCol = this.interpolateColor(-15681151, -13315175, hApplyA);
         RoundedRectShader.draw(context, applyX, edY, actW, actH, 4.5F, this.withAlpha(applyCol, anim * switchAlpha));
         float appTxtW = SFUI.getWidth("Применить", 7.5F);
         SFUI.draw(context, "Применить", applyX + (actW - appTxtW) / 2.0F, edY + 6.5F, 7.5F, this.withAlpha(-1, anim * switchAlpha));
         this.clickBounds.put("wp_apply", new int[]{(int)applyX, (int)edY, (int)actW, (int)actH});
         float delX = applyX + actW + 8.0F;
         float delW = 85.0F;
         boolean hDelAct = this.inside(this.currentMouseX, this.currentMouseY, delX, edY, delW, actH);
         float hDelActA = this.updateHover("wp_del_selected", hDelAct);
         int delActCol = this.interpolateColor(dark ? -2130754492 : 1627341892, -570473404, hDelActA);
         RoundedRectShader.draw(context, delX, edY, delW, actH, 4.5F, this.withAlpha(delActCol, anim * switchAlpha));
         float delTxtW = SFUI.getWidth("Удалить", 7.5F);
         SFUI.draw(context, "Удалить", delX + (delW - delTxtW) / 2.0F, edY + 6.5F, 7.5F, this.withAlpha(-1, anim * switchAlpha));
         this.clickBounds.put("wp_delete_selected", new int[]{(int)delX, (int)edY, (int)delW, (int)actH});
      }
   }

   private void drawCoordEditField(DrawContext context, String prefix, String val, float x, float y, float w, float h, boolean focused, String clickKey, boolean dark, float anim) {
      int bg = focused ? (dark ? -14540238 : -2564888) : (dark ? -2145904606 : -2132219150);
      RoundedRectShader.draw(context, x, y, w, h, 4.0F, this.withAlpha(bg, anim));
      if (focused) {
         RoundedRectShader.draw(context, x - 1.0F, y - 1.0F, w + 2.0F, h + 2.0F, 5.0F, this.withAlpha(-11755777, 0.5F * anim));
      }

      SFUI.draw(context, prefix, x + 6.0F, y + 5.0F, 7.5F, this.withAlpha(dark ? -8750454 : -7697766, anim));
      float prefW = SFUI.getWidth(prefix, 7.5F);
      String shown = val;
      if (focused && System.currentTimeMillis() % 900L < 450L) {
         shown = val + "|";
      }

      SFUI.draw(context, shown, x + 6.0F + prefW, y + 5.0F, 7.5F, this.withAlpha(dark ? -1 : -15461352, anim));
      this.clickBounds.put(clickKey, new int[]{(int)x, (int)y, (int)w, (int)h});
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.bindingTarget != null) {
         if (button == 0) {
            this.bindingTarget = null;
            playSound("click");
            return true;
         } else if (button == 1) {
            BindManager.setStoredBindValue(this.bindingTarget, -1);
            ClientData.moduleBinds.remove(this.bindingTarget);
            this.bindingTarget = null;
            playSound("click");
            ConfigManager.saveConfig();
            return true;
         } else {
            int mouseBindCode = BindManager.encodeMouseBind(button);
            BindManager.setStoredBindValue(this.bindingTarget, mouseBindCode);
            ClientData.moduleBinds.put(this.bindingTarget, mouseBindCode);
            this.bindingTarget = null;
            playSound("click");
            ConfigManager.saveConfig();
            return true;
         }
      } else {
         if (button == 1) {
            for(Map.Entry<String, int[]> e : this.clickBounds.entrySet()) {
               String key = (String)e.getKey();
               int[] b = (int[])e.getValue();
               if (this.inside((float)mouseX, (float)mouseY, (float)b[0], (float)b[1], (float)b[2], (float)b[3]) && key.startsWith("bind:")) {
                  String target = key.substring(5);
                  BindManager.setStoredBindValue(target, -1);
                  ClientData.moduleBinds.remove(target);
                  playSound("click");
                  ConfigManager.saveConfig();
                  return true;
               }
            }
         }

         if (button == 0) {
            if (DynamicIslandRenderer.handlePartyNotifClick(mouseX, mouseY, button)) {
               playSound("click");
               return true;
            }

            if (this.activeColorPickerKey != null) {
               float hudW = 140.0F;
               float hudH = 125.0F;
               float hX = Math.max(10.0F, Math.min(this.colorPickerX, (float)this.width - hudW - 15.0F));
               float hY = Math.max(10.0F, Math.min(this.colorPickerY, (float)this.height - hudH - 15.0F));
               float svX = hX + 8.0F;
               float svY = hY + 8.0F;
               float svW = hudW - 16.0F;
               float svH = 62.0F;
               float hueY = svY + svH + 6.0F;
               float hueH = 9.0F;
               if (this.inside((float)mouseX, (float)mouseY, svX, svY, svW, svH)) {
                  this.draggingSv = true;
                  this.updateSvFromMouse(mouseX, mouseY, svX, svY, svW, svH);
                  return true;
               }

               if (this.inside((float)mouseX, (float)mouseY, svX, hueY - 2.0F, svW, hueH + 4.0F)) {
                  this.draggingHue = true;
                  this.updateHueFromMouse(mouseX, svX, svW);
                  return true;
               }

               int[] doneB = (int[])this.clickBounds.get("palette_done");
               if (doneB != null && this.inside((float)mouseX, (float)mouseY, (float)doneB[0], (float)doneB[1], (float)doneB[2], (float)doneB[3])) {
                  this.activeColorPickerKey = null;
                  playSound("click");
                  return true;
               }

               if (!this.inside((float)mouseX, (float)mouseY, hX - 5.0F, hY - 5.0F, hudW + 10.0F, hudH + 10.0F)) {
                  this.activeColorPickerKey = null;
                  playSound("click");
               }

               return true;
            }

            if (this.activeDropdownKey != null) {
               boolean clickedInside = false;

               for(Map.Entry<String, int[]> e : this.clickBounds.entrySet()) {
                  if (((String)e.getKey()).startsWith("set_mode:")) {
                     int[] b = (int[])e.getValue();
                     if (this.inside((float)mouseX, (float)mouseY, (float)b[0], (float)b[1], (float)b[2], (float)b[3])) {
                        this.handleClickAction((String)e.getKey(), (float)mouseX, (float)mouseY);
                        clickedInside = true;
                        break;
                     }
                  }
               }

               if (!clickedInside) {
                  this.activeDropdownKey = null;
                  playSound("click");
               }

               return true;
            }

            float[] chb = (float[])this.crosshairBounds.get("canvas");
            if (chb != null && this.inside((float)mouseX, (float)mouseY, chb[0], chb[1], chb[2], chb[3])) {
               float cellSize = chb[4];
               int cx = (int)((mouseX - (double)chb[0]) / (double)cellSize);
               int cy = (int)((mouseY - (double)chb[1]) / (double)cellSize);
               if (cx >= 0 && cx < 21 && cy >= 0 && cy < 21) {
                  this.isErasingCrosshair = CustomCrosshairData.MATRIX[cy][cx];
                  CustomCrosshairData.set(cx, cy, !this.isErasingCrosshair);
                  this.draggingCrosshair = true;
                  playSound("click");
                  return true;
               }
            }

            for(Map.Entry<String, int[]> e : this.clickBounds.entrySet()) {
               String key = (String)e.getKey();
               int[] b = (int[])e.getValue();
               if (this.inside((float)mouseX, (float)mouseY, (float)b[0], (float)b[1], (float)b[2], (float)b[3])) {
                  this.handleClickAction(key, (float)mouseX, (float)mouseY);
                  return true;
               }
            }

            for(Map.Entry<String, float[]> e : this.sliderBounds.entrySet()) {
               String key = (String)e.getKey();
               float[] b = (float[])e.getValue();
               if (this.inside((float)mouseX, (float)mouseY, b[0], b[1], b[2], b[3])) {
                  this.draggingSlider = key;
                  this.updateSliderValue(key, (float)mouseX, b);
                  return true;
               }
            }

            for(Map.Entry<String, float[]> e : this.padBounds.entrySet()) {
               String key = (String)e.getKey();
               float[] b = (float[])e.getValue();
               if (this.inside((float)mouseX, (float)mouseY, b[0], b[1], b[2], b[3])) {
                  this.draggingPad = key;
                  this.updatePadValue(key, (float)mouseX, (float)mouseY, b);
                  return true;
               }
            }

            this.searchFocused = false;
            this.partyCodeInputFocused = false;
            this.configInputFocused = false;
         }

         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   private void handleClickAction(String key, float clickX, float clickY) {
      if (key.startsWith("tab:")) {
         String newTab = key.substring(4);
         if (!newTab.equals(this.activeTab)) {
            savedTabColScrolls.put(this.activeTab, (float[])this.colScrolls.clone());
            savedTabTargetColScrolls.put(this.activeTab, (float[])this.targetColScrolls.clone());
            savedTabSecondaryScrolls.put(this.activeTab, this.secondaryScroll);
            savedTabTargetSecondaryScrolls.put(this.activeTab, this.targetSecondaryScroll);
            this.prevTab = this.activeTab;
            this.activeTab = newTab;
            savedActiveTab = newTab;
            this.tabSwitchAnim = 0.0F;
            this.activeDropdownKey = null;
            this.activeColorPickerKey = null;
            float[] s = (float[])savedTabColScrolls.get(newTab);
            if (s != null) {
               System.arraycopy(s, 0, this.colScrolls, 0, 3);
            } else {
               Arrays.fill(this.colScrolls, 0.0F);
            }

            float[] ts = (float[])savedTabTargetColScrolls.get(newTab);
            if (ts != null) {
               System.arraycopy(ts, 0, this.targetColScrolls, 0, 3);
            } else {
               Arrays.fill(this.targetColScrolls, 0.0F);
            }

            this.secondaryScroll = (Float)savedTabSecondaryScrolls.getOrDefault(newTab, 0.0F);
            this.targetSecondaryScroll = (Float)savedTabTargetSecondaryScrolls.getOrDefault(newTab, 0.0F);
            if (this.activeTab.equals("Search")) {
               this.searchFocused = true;
            }

            playSound("click");
         }
      } else if (key.equals("input:search")) {
         this.searchFocused = true;
         this.partyCodeInputFocused = false;
         this.configInputFocused = false;
         playSound("click");
      } else if (key.equals("action:clear_search")) {
         this.searchInput = "";
         this.searchFocused = false;
         this.tabSwitchAnim = 0.0F;
         playSound("click");
      } else if (key.equals("action:theme")) {
         this.toggleTheme(clickX, clickY);
      } else if (key.equals("action:lang")) {
         this.oldLang = GuiLocalization.getLanguage();
         GuiLocalization.toggleLanguage();
         this.targetLang = GuiLocalization.getLanguage();
         this.langSwitchAnim = 0.0F;
         playSound("click");
      } else if (key.equals("action:favorites")) {
         ClientData.onlyFavoritesFilter = !ClientData.onlyFavoritesFilter;
         this.tabSwitchAnim = 0.0F;
         playSound("click");
      } else if (key.equals("action:close")) {
         this.close();
      } else if (key.startsWith("cosmetic_sub:")) {
         cosmeticSubTab = Integer.parseInt(key.substring("cosmetic_sub:".length()));
         this.targetSecondaryScroll = 0.0F;
         this.secondaryScroll = 0.0F;
         this.tabSwitchAnim = 0.0F;
         if (cosmeticSubTab == 1) {
            CosmeticManager.getInstance().reloadCustomCapes();
         }

         playSound("click");
      } else if (key.equals("cosmetic_cape_toggle")) {
         CosmeticManager.getInstance().setCustomCapeEnabled(!CosmeticManager.getInstance().isCustomCapeEnabled());
         playSound("click");
      } else if (key.equals("cosmetic_reset_all")) {
         CosmeticManager.getInstance().clearAllEquipped();
         playSound("click");
      } else if (key.equals("cosmetic_open_folder")) {
         CosmeticManager.getInstance().openCustomCapesFolder();
         playSound("click");
      } else if (key.startsWith("cosmetic_item:")) {
         int idx = Integer.parseInt(key.substring("cosmetic_item:".length()));

         for(CosmeticManager.CosmeticEntry entry : CosmeticManager.getInstance().getEntries()) {
            if (entry.index == idx) {
               CosmeticManager.getInstance().toggle(entry);
               playSound("click");
               break;
            }
         }
      } else if (key.startsWith("evt_tab:")) {
         this.eventsFilter = key.substring(8);
         playSound("click");
      } else if (key.startsWith("hw_join:")) {
         String code = key.substring(8);
         ModernClickGui.HolyWorldJoiner.join(code);
         playSound("click");
      } else if (key.startsWith("ch_act:")) {
         switch (key.substring(7)) {
            case "clear" -> CustomCrosshairData.clear();
            case "corners" -> CustomCrosshairData.loadCorners();
            case "dot" -> CustomCrosshairData.loadDot();
            case "default" -> CustomCrosshairData.loadDefault();
         }

         CustomCrosshairData.save();
         playSound("click");
      } else if (key.startsWith("apply_preset:")) {
         String[] parts = key.split(":");
         if (parts.length >= 3) {
            int c1 = this.parseHex(parts[1]);
            int c2 = this.parseHex(parts[2]);
            float[] hsv1 = new float[3];
            float[] hsv2 = new float[3];
            Color.RGBtoHSB(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, hsv1);
            Color.RGBtoHSB(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, hsv2);
            ClientData.colorSettings.put("Theme Color 1", hsv1);
            ClientData.colorSettings.put("Theme Color 2", hsv2);
            playSound("click");
         }
      } else if (key.startsWith("open_palette:")) {
         this.activeColorPickerKey = key.substring(13);
         this.colorPickerX = clickX;
         this.colorPickerY = clickY;
         playSound("click");
      } else if (key.startsWith("toggle:")) {
         String mod = key.substring(7);
         if (com.lexoravisauls.client.liteapi.LiteApiFeatureControl.isBlocked(mod)) {
            ClientData.moduleStates.put(mod, false);
            LexoraGui.moduleStates.put(mod, false);
            com.lexoravisauls.client.utils.NotifManager.show("HolyWorld", "Функция '" + mod + "' запрещена сервером!", com.lexoravisauls.client.utils.NotifManager.NotifType.ERROR);
            playSound("click");
            return;
         }
         boolean current = (Boolean)ClientData.moduleStates.getOrDefault(mod, false);
         ClientData.moduleStates.put(mod, !current);
         LexoraGui.moduleStates.put(mod, !current);
         playModuleToggleSound(!current);
         ConfigManager.saveConfig();
      } else if (key.startsWith("fav:")) {
         String mod = key.substring(4);
         this.toggleModuleFavorite(mod);
         this.starAnimations.put(mod, 0.0F);
         playSound("click");
      } else if (key.startsWith("bind:")) {
         String target = key.substring(5);
         this.bindingTarget = target;
         playSound("click");
      } else if (key.startsWith("btn:")) {
         String bLabel = key.substring(4);
         Runnable act = (Runnable)this.buttonActions.get(bLabel);
         if (act != null) {
            act.run();
            playSound("click");
         }
      } else if (key.startsWith("bool:")) {
         String sKey = key.substring(5);
         if (com.lexoravisauls.client.liteapi.LiteApiFeatureControl.isBlocked(sKey)) {
            ClientData.moduleStates.put(sKey, false);
            LexoraGui.moduleStates.put(sKey, false);
            com.lexoravisauls.client.utils.NotifManager.show("HolyWorld", "Функция '" + sKey + "' запрещена сервером!", com.lexoravisauls.client.utils.NotifManager.NotifType.ERROR);
            playSound("click");
            return;
         }
         boolean cur = (Boolean)ClientData.moduleStates.getOrDefault(sKey, false);
         ClientData.moduleStates.put(sKey, !cur);
         LexoraGui.moduleStates.put(sKey, !cur);
         playSound("click");
         ConfigManager.saveConfig();
      } else if (key.startsWith("dropdown:")) {
         String[] parts = key.substring(9).split(":");
         String sKey = parts[0];
         String[] opts = parts.length > 1 ? parts[1].split(",") : new String[0];
         if (this.activeDropdownKey != null && this.activeDropdownKey.equals(sKey)) {
            this.activeDropdownKey = null;
         } else {
            this.activeDropdownKey = sKey;
            this.activeDropdownOptions = Arrays.asList(opts);
            this.dropdownScroll = 0.0F;
            this.targetDropdownScroll = 0.0F;
            int[] b = (int[])this.clickBounds.get(key);
            if (b != null) {
               this.dropdownX = (float)b[0];
               this.dropdownY = (float)(b[1] + b[3] + 2);
               this.dropdownW = (float)b[2];
            }
         }

         playSound("click");
      } else if (key.startsWith("set_mode:")) {
         String[] parts = key.substring(9).split(":");
         if (parts.length >= 2) {
            ClientData.modeSettings.put(parts[0], parts[1]);
            this.activeDropdownKey = null;
            playSound("click");
            ConfigManager.saveConfig();
         }
      } else if (key.startsWith("color:")) {
         this.activeColorPickerKey = key.substring(6);
         this.colorPickerX = clickX;
         this.colorPickerY = clickY;
         playSound("click");
      } else if (key.equals("party:toggle")) {
         if (LexoraPartyManager.inParty()) {
            LexoraPartyClient.leaveParty(this.client);
         } else {
            LexoraPartyClient.createParty(this.client);
         }

         playSound("click");
      } else if (key.equals("input:party_code")) {
         this.partyCodeInputFocused = true;
         this.configInputFocused = false;
         this.searchFocused = false;
         playSound("click");
      } else if (key.equals("party:join")) {
         if (this.partyCodeInput.length() >= 6) {
            LexoraPartyClient.requestJoin(this.client, this.partyCodeInput);
            this.partyCodeInput = "";
            this.partyCodeInputFocused = false;
            playSound("click");
         }
      } else if (key.startsWith("party:accept:")) {
         String targetUuid = key.substring("party:accept:".length());
         LexoraPartyClient.respondToRequest(this.client, targetUuid, true);
         playSound("click");
      } else if (key.startsWith("party:decline:")) {
         String targetUuid = key.substring("party:decline:".length());
         LexoraPartyClient.respondToRequest(this.client, targetUuid, false);
         playSound("click");
      } else if (key.equals("input:config")) {
         this.configInputFocused = true;
         this.partyCodeInputFocused = false;
         this.searchFocused = false;
         playSound("click");
      } else if (key.equals("btn:create_config")) {
         if (!this.configInput.isEmpty()) {
            ConfigManager.saveConfig(this.configInput);
            this.selectedConfig = this.configInput;
            this.configInput = "";
            this.configInputFocused = false;
            ConfigManager.updateConfigList();
            playSound("click");
         }
      } else if (key.startsWith("cfg_load:")) {
         String cfg = key.substring(9);
         ConfigManager.loadConfig(cfg);
         this.selectedConfig = cfg;
         playSound("click");
      } else if (key.startsWith("cfg_save:")) {
         String cfg = key.substring(9);
         ConfigManager.saveConfig(cfg);
         this.selectedConfig = cfg;
         playSound("click");
      } else if (key.startsWith("cfg_del:")) {
         String cfg = key.substring(8);
         ConfigManager.deleteConfig(cfg);
         ConfigManager.updateConfigList();
         playSound("click");
      } else if (key.equals("btn:gps_clear_all")) {
         GPS.clearAll();
         wpSelected = null;
         ConfigManager.saveConfig();
         playSound("click");
      } else if (key.equals("wp:add_new")) {
         if (this.client != null && this.client.player != null) {
            double px = (double)Math.round(this.client.player.getX() * 10.0) / 10.0;
            double py = (double)Math.round(this.client.player.getY() * 10.0) / 10.0;
            double pz = (double)Math.round(this.client.player.getZ() * 10.0) / 10.0;
            int var10000 = GPS.waypoints.size();
            String wpName = "Точка " + (var10000 + 1);
            GPS.GpsWaypoint newWp = new GPS.GpsWaypoint(wpName, px, py, pz);
            GPS.waypoints.add(newWp);
            this.selectWaypointForEdit(newWp);
            wpFocusedField = 1;
            ConfigManager.saveConfig();
         }

         playSound("click");
      } else if (key.startsWith("wp_select:")) {
         int idx = Integer.parseInt(key.substring("wp_select:".length()));
         List<GPS.GpsWaypoint> waypoints = GPS.getWaypoints();
         if (idx >= 0 && idx < waypoints.size()) {
            this.selectWaypointForEdit((GPS.GpsWaypoint)waypoints.get(idx));
         }

         playSound("click");
      } else if (!key.startsWith("wp_del_row:") && !key.startsWith("gps_del:")) {
         if (key.equals("wp_field:name")) {
            wpFocusedField = 1;
            this.configInputFocused = false;
            this.partyCodeInputFocused = false;
            this.searchFocused = false;
            playSound("click");
         } else if (key.equals("wp_field:x")) {
            wpFocusedField = 2;
            this.configInputFocused = false;
            this.partyCodeInputFocused = false;
            this.searchFocused = false;
            playSound("click");
         } else if (key.equals("wp_field:y")) {
            wpFocusedField = 3;
            this.configInputFocused = false;
            this.partyCodeInputFocused = false;
            this.searchFocused = false;
            playSound("click");
         } else if (key.equals("wp_field:z")) {
            wpFocusedField = 4;
            this.configInputFocused = false;
            this.partyCodeInputFocused = false;
            this.searchFocused = false;
            playSound("click");
         } else if (key.equals("wp_fill_current")) {
            if (this.client != null && this.client.player != null) {
               wpEditX = String.format(Locale.ROOT, "%.0f", this.client.player.getX());
               wpEditY = String.format(Locale.ROOT, "%.0f", this.client.player.getY());
               wpEditZ = String.format(Locale.ROOT, "%.0f", this.client.player.getZ());
            }

            playSound("click");
         } else if (key.startsWith("wp_icon:")) {
            int idx = Integer.parseInt(key.substring("wp_icon:".length()));
            if (wpSelected != null && idx >= 0 && idx < GPS.MARKER_TEXTURES.length) {
               wpSelected.iconBackgroundIndex = idx;
               if (idx < GPS.MARKER_COLORS.length) {
                  wpSelected.color = GPS.MARKER_COLORS[idx];
               }

               ConfigManager.saveConfig();
            }

            playSound("click");
         } else if (key.equals("wp_apply")) {
            this.commitWaypointEdits();
            playSound("click");
         } else if (key.equals("wp_delete_selected")) {
            if (wpSelected != null) {
               GPS.waypoints.remove(wpSelected);
               wpSelected = GPS.waypoints.isEmpty() ? null : (GPS.GpsWaypoint)GPS.waypoints.get(0);
               this.selectWaypointForEdit(wpSelected);
               ConfigManager.saveConfig();
            }

            playSound("click");
         }
      } else {
         String prefix = key.startsWith("wp_del_row:") ? "wp_del_row:" : "gps_del:";
         int idx = Integer.parseInt(key.substring(prefix.length()));
         if (idx >= 0 && idx < GPS.waypoints.size()) {
            GPS.GpsWaypoint removed = (GPS.GpsWaypoint)GPS.waypoints.remove(idx);
            if (removed == wpSelected) {
               wpSelected = GPS.waypoints.isEmpty() ? null : (GPS.GpsWaypoint)GPS.waypoints.get(0);
               this.selectWaypointForEdit(wpSelected);
            }

            ConfigManager.saveConfig();
         }

         playSound("click");
      }

   }

   private void updateSliderValue(String key, float mouseX, float[] b) {
      float min = b[4];
      float max = b[5];
      float norm = Math.max(0.0F, Math.min(1.0F, (mouseX - b[0]) / b[2]));
      float val = min + norm * (max - min);
      ClientData.numSettings.put(key, val);
   }

   private void updatePadValue(String key, float mouseX, float mouseY, float[] b) {
      float minX = b[4];
      float maxX = b[5];
      float minY = b[6];
      float maxY = b[7];
      float tx = Math.max(0.0F, Math.min(1.0F, (mouseX - b[0]) / b[2]));
      float ty = Math.max(0.0F, Math.min(1.0F, (mouseY - b[1]) / b[3]));
      ClientData.numSettings.put(key, minX + tx * (maxX - minX));
      String keyY = (String)this.padKeyYMap.getOrDefault(key, key.endsWith("X") ? key.substring(0, key.length() - 1) + "Y" : key + "Y");
      ClientData.numSettings.put(keyY, maxY - ty * (maxY - minY));
   }

   public boolean charTyped(char chr, int modifiers) {
      if (this.searchFocused && chr >= ' ' && this.searchInput.length() < 30) {
         this.searchInput = this.searchInput + chr;
         this.tabSwitchAnim = 0.0F;
         return true;
      } else if (this.partyCodeInputFocused && Character.isLetterOrDigit(chr) && this.partyCodeInput.length() < 10) {
         this.partyCodeInput = this.partyCodeInput + chr;
         return true;
      } else if (this.configInputFocused && chr >= ' ' && this.configInput.length() < 24) {
         this.configInput = this.configInput + chr;
         return true;
      } else {
         if (this.activeTab.equals("Waypoints") && wpSelected != null && wpFocusedField > 0) {
            if (wpFocusedField == 1 && wpEditName.length() < 24) {
               wpEditName = wpEditName + chr;
               return true;
            }

            if (wpFocusedField == 2 && (Character.isDigit(chr) || chr == '-' || chr == '.') && wpEditX.length() < 12) {
               wpEditX = wpEditX + chr;
               return true;
            }

            if (wpFocusedField == 3 && (Character.isDigit(chr) || chr == '-' || chr == '.') && wpEditY.length() < 12) {
               wpEditY = wpEditY + chr;
               return true;
            }

            if (wpFocusedField == 4 && (Character.isDigit(chr) || chr == '-' || chr == '.') && wpEditZ.length() < 12) {
               wpEditZ = wpEditZ + chr;
               return true;
            }
         }

         return super.charTyped(chr, modifiers);
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (button == 0) {
         if (this.activeColorPickerKey != null) {
            float hudW = 140.0F;
            float hudH = 125.0F;
            float hX = Math.max(10.0F, Math.min(this.colorPickerX, (float)this.width - hudW - 15.0F));
            float hY = Math.max(10.0F, Math.min(this.colorPickerY, (float)this.height - hudH - 15.0F));
            float svX = hX + 8.0F;
            float svY = hY + 8.0F;
            float svW = hudW - 16.0F;
            float svH = 62.0F;
            if (this.draggingSv) {
               this.updateSvFromMouse(mouseX, mouseY, svX, svY, svW, svH);
               return true;
            }

            if (this.draggingHue) {
               this.updateHueFromMouse(mouseX, svX, svW);
               return true;
            }
         }

         if (this.draggingCrosshair) {
            float[] chb = (float[])this.crosshairBounds.get("canvas");
            if (chb != null && this.inside((float)mouseX, (float)mouseY, chb[0], chb[1], chb[2], chb[3])) {
               float cellSize = chb[4];
               int cx = (int)((mouseX - (double)chb[0]) / (double)cellSize);
               int cy = (int)((mouseY - (double)chb[1]) / (double)cellSize);
               if (cx >= 0 && cx < 21 && cy >= 0 && cy < 21) {
                  CustomCrosshairData.set(cx, cy, !this.isErasingCrosshair);
                  return true;
               }
            }
         }

         if (this.draggingSlider != null) {
            float[] b = (float[])this.sliderBounds.get(this.draggingSlider);
            if (b != null) {
               this.updateSliderValue(this.draggingSlider, (float)mouseX, b);
               return true;
            }
         }

         if (this.draggingPad != null) {
            float[] b = (float[])this.padBounds.get(this.draggingPad);
            if (b != null) {
               this.updatePadValue(this.draggingPad, (float)mouseX, (float)mouseY, b);
               return true;
            }
         }
      }

      return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0) {
         this.draggingSlider = null;
         this.draggingPad = null;
         this.draggingCrosshair = false;
         this.draggingSv = false;
         this.draggingHue = false;
         ConfigManager.saveConfig();
      }

      return super.mouseReleased(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.activeDropdownKey != null) {
         this.targetDropdownScroll += (float)(verticalAmount * 18.0);
         return true;
      } else if (!this.activeTab.equals("Events") && !this.activeTab.equals("Configs") && !this.activeTab.equals("Themes") && !this.activeTab.equals("Settings") && !this.activeTab.equals("Friends") && !this.activeTab.equals("Waypoints") && !this.activeTab.equals("Cosmetics")) {
         if (this.activeTab.equals("All") || this.activeTab.equals("HUD") || this.activeTab.equals("Visual") || this.activeTab.equals("Utils") || this.activeTab.equals("Search")) {
            float guiX = (float)(this.width - 660) / 2.0F;
            float contentX = guiX + 135.0F;
            float contentW = 525.0F;
            float colW = (contentW - 16.0F) / 3.0F;

            for(int i = 0; i < 3; ++i) {
               float colX = contentX + 8.0F + (float)i * (colW + 8.0F);
               if (this.inside((float)mouseX, (float)mouseY, colX, (float)(this.height - 360) / 2.0F + 30.0F, colW, 330.0F)) {
                  float[] var10000 = this.targetColScrolls;
                  var10000[i] += (float)(verticalAmount * 24.0);
                  return true;
               }
            }
         }

         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      } else {
         this.targetSecondaryScroll += (float)(verticalAmount * 28.0);
         return true;
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.bindingTarget != null) {
         if (keyCode != 256 && keyCode != 261) {
            BindManager.setStoredBindValue(this.bindingTarget, keyCode);
            ClientData.moduleBinds.put(this.bindingTarget, keyCode);
         } else {
            BindManager.setStoredBindValue(this.bindingTarget, -1);
            ClientData.moduleBinds.remove(this.bindingTarget);
         }

         this.bindingTarget = null;
         playSound("click");
         ConfigManager.saveConfig();
         return true;
      } else {
         if (this.searchFocused) {
            if (keyCode == 259 && !this.searchInput.isEmpty()) {
               this.searchInput = this.searchInput.substring(0, this.searchInput.length() - 1);
               this.tabSwitchAnim = 0.0F;
               return true;
            }

            if (keyCode == 257 || keyCode == 256) {
               this.searchFocused = false;
               return true;
            }
         }

         if (this.partyCodeInputFocused) {
            if (keyCode == 259 && !this.partyCodeInput.isEmpty()) {
               this.partyCodeInput = this.partyCodeInput.substring(0, this.partyCodeInput.length() - 1);
               return true;
            }

            if (keyCode == 257) {
               this.partyCodeInputFocused = false;
               if (this.partyCodeInput.length() >= 6) {
                  LexoraPartyClient.requestJoin(this.client, this.partyCodeInput);
                  this.partyCodeInput = "";
               }

               return true;
            }

            if (keyCode == 256) {
               this.partyCodeInputFocused = false;
               return true;
            }
         }

         if (this.configInputFocused) {
            if (keyCode == 259 && !this.configInput.isEmpty()) {
               this.configInput = this.configInput.substring(0, this.configInput.length() - 1);
               return true;
            }

            if (keyCode == 257) {
               this.configInputFocused = false;
               if (!this.configInput.isEmpty()) {
                  ConfigManager.saveConfig(this.configInput);
                  this.selectedConfig = this.configInput;
                  this.configInput = "";
                  ConfigManager.updateConfigList();
               }

               return true;
            }

            if (keyCode == 256) {
               this.configInputFocused = false;
               return true;
            }
         }

         if (this.activeTab.equals("Waypoints") && wpSelected != null && wpFocusedField > 0) {
            if (keyCode == 259) {
               if (wpFocusedField == 1 && !wpEditName.isEmpty()) {
                  wpEditName = wpEditName.substring(0, wpEditName.length() - 1);
                  return true;
               }

               if (wpFocusedField == 2 && !wpEditX.isEmpty()) {
                  wpEditX = wpEditX.substring(0, wpEditX.length() - 1);
                  return true;
               }

               if (wpFocusedField == 3 && !wpEditY.isEmpty()) {
                  wpEditY = wpEditY.substring(0, wpEditY.length() - 1);
                  return true;
               }

               if (wpFocusedField == 4 && !wpEditZ.isEmpty()) {
                  wpEditZ = wpEditZ.substring(0, wpEditZ.length() - 1);
                  return true;
               }
            }

            if (keyCode == 257) {
               this.commitWaypointEdits();
               wpFocusedField = 0;
               return true;
            }

            if (keyCode == 256) {
               wpFocusedField = 0;
               return true;
            }
         }

         if (keyCode == 256) {
            this.close();
            return true;
         } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
         }
      }
   }

   private String getAnimatedText(String key, String targetText) {
      if (targetText == null) {
         return "";
      } else if (this.langSwitchAnim >= 1.0F) {
         return targetText;
      } else {
         int len = targetText.length();
         int visibleChars = Math.max(1, (int)((float)len * this.langSwitchAnim));
         return targetText.substring(0, Math.min(len, visibleChars));
      }
   }

   private List<String> getFilteredModules() {
      List<String> list = new ArrayList();
      if (!this.searchInput.isEmpty()) {
         list.addAll(HUD_MODULES);
         list.addAll(VISUAL_MODULES);
         list.addAll(UTILS_MODULES);
         list.removeIf((m) -> !m.toLowerCase().contains(this.searchInput.toLowerCase()) && !GuiLocalization.getModuleTitle(m).toLowerCase().contains(this.searchInput.toLowerCase()));
      } else {
         switch (this.activeTab) {
            case "All":
               list.addAll(HUD_MODULES);
               list.addAll(VISUAL_MODULES);
               list.addAll(UTILS_MODULES);
               break;
            case "HUD":
               list.addAll(HUD_MODULES);
               break;
            case "Visual":
               list.addAll(VISUAL_MODULES);
               break;
            case "Utils":
               list.addAll(UTILS_MODULES);
               break;
            case "Search":
               list.addAll(HUD_MODULES);
               list.addAll(VISUAL_MODULES);
               list.addAll(UTILS_MODULES);
         }
      }

      if (ClientData.onlyFavoritesFilter) {
         list.removeIf((m) -> !this.isModuleFavorite(m));
      }

      return list;
   }

   private boolean isModuleFavorite(String mod) {
      return ClientData.favorites.contains(mod);
   }

   private void toggleModuleFavorite(String mod) {
      if (ClientData.favorites.contains(mod)) {
         ClientData.favorites.remove(mod);
      } else {
         ClientData.favorites.add(mod);
      }

   }

   private ModernGuiIcons.Icon getModuleIcon(String mod) {
      if (VISUAL_MODULES.contains(mod)) {
         return ModernGuiIcons.Icon.WAND;
      } else if (HUD_MODULES.contains(mod)) {
         return ModernGuiIcons.Icon.MONITOR;
      } else {
         return UTILS_MODULES.contains(mod) ? ModernGuiIcons.Icon.WRENCH : ModernGuiIcons.Icon.WRENCH;
      }
   }

   private boolean inside(float mx, float my, float x, float y, float w, float h) {
      return mx >= x && mx <= x + w && my >= y && my <= y + h;
   }

   private int withAlpha(int color, float alpha) {
      int a = Math.max(0, Math.min(255, (int)((float)(color >>> 24 & 255) * alpha)));
      return a << 24 | color & 16777215;
   }

   private int interpolateColor(int c1, int c2, float ratio) {
      ratio = Math.max(0.0F, Math.min(1.0F, ratio));
      int a1 = c1 >>> 24 & 255;
      int r1 = c1 >>> 16 & 255;
      int g1 = c1 >>> 8 & 255;
      int b1 = c1 & 255;
      int a2 = c2 >>> 24 & 255;
      int r2 = c2 >>> 16 & 255;
      int g2 = c2 >>> 8 & 255;
      int b2 = c2 & 255;
      int a = (int)((float)a1 + (float)(a2 - a1) * ratio);
      int r = (int)((float)r1 + (float)(r2 - r1) * ratio);
      int g = (int)((float)g1 + (float)(g2 - g1) * ratio);
      int b = (int)((float)b1 + (float)(b2 - b1) * ratio);
      return a << 24 | r << 16 | g << 8 | b;
   }

   @Environment(EnvType.CLIENT)
   public static class HolyWorldJoiner {
      private static String targetServer = null;
      private static int delayTicks = 0;

      public static void join(String serverCode) {
         targetServer = serverCode;
         delayTicks = 5;
      }

      public static void tick() {
         if (targetServer != null && delayTicks > 0) {
            --delayTicks;
            if (delayTicks <= 0) {
               MinecraftClient mc = MinecraftClient.getInstance();
               if (mc.player != null && mc.getNetworkHandler() != null) {
                  mc.getNetworkHandler().sendChatCommand("anarchy " + targetServer);
               }

               targetServer = null;
            }
         }

      }
   }
}
