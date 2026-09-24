package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.events.*;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.LexoraIcons;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.modern.ModernTheme;
import com.lexoravisauls.client.utils.ConfigManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Mixin(ChatScreen.class)
public class MixinChatScreen extends Screen {

    private static final MsdfFont SFUI = new MsdfFont(
            Identifier.of("lexoravisauls", "msdf_data/font.png"),
            Identifier.of("lexoravisauls", "msdf_data/font.json")
    );

    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private final Map<String, Float> switcherAnimations = new HashMap<>();
    private final Map<String, Float> hudHoverAnimations = new HashMap<>();

    private String editingHud = null;
    private String targetEditingHud = null;
    private float panelAnim = 0f;

    private int pX = 0;
    private int pY = 0;
    private int pW = 140;
    private int pH = 90;

    private boolean draggingScale = false;
    private boolean particleDropdownOpen = false;

    // === ПЕРЕТАСКИВАНИЕ ХУДОВ С ШЕЙДЕРОМ СВЕЧЕНИЯ ВОКРУГ ХУДА ===
    private boolean draggingHudPos = false;
    private String draggingHudName = null;
    private float dragOffsetX = 0f;
    private float dragOffsetY = 0f;
    private float dragTargetX = 0f;
    private float dragTargetY = 0f;
    private float dragHudWidth = 100f;
    private float dragHudHeight = 40f;
    private float dragVisualAlpha = 0f;

    protected MixinChatScreen(Text title) {
        super(title);
    }

    private boolean isDark() {
        return HudThemeHelper.isDark();
    }

    private void startDraggingHud(String name, int visualOriginX, int visualOriginY, int visualWidth, int visualHeight, double mouseX, double mouseY) {
        draggingHudPos = true;
        draggingHudName = name;
        dragOffsetX = (float) mouseX - visualOriginX;
        dragOffsetY = (float) mouseY - visualOriginY;
        dragTargetX = visualOriginX;
        dragTargetY = visualOriginY;
        dragHudWidth = Math.max(20, visualWidth);
        dragHudHeight = Math.max(10, visualHeight);
        dragVisualAlpha = 0.05f;
    }

    private void applyHudPositionFromVisual(String name, int visualX, int visualY) {
        if (name == null) return;
        switch (name) {
            case "Info HUD" -> { HudManager.infoX = visualX; HudManager.infoY = visualY; }
            case "Target HUD" -> {
                float scale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
                int baseW = TargetHudRenderer.WIDTH, baseH = TargetHudRenderer.HEIGHT;
                int visualW = Math.round(baseW * scale), visualH = Math.round(baseH * scale);
                HudManager.targetX = Math.round(visualX - (baseW - visualW) / 2.0f);
                HudManager.targetY = Math.round(visualY - (baseH - visualH) / 2.0f);
            }
            case "TNT Detect" -> {
                float scale = LexoraGui.numSettings.getOrDefault("TNT Detect Scale", 1.0f);
                int baseW = TntHudRenderer.WIDTH <= 0 ? 115 : TntHudRenderer.WIDTH;
                int baseH = TntHudRenderer.HEIGHT <= 0 ? 42 : TntHudRenderer.HEIGHT;
                int visualW = Math.round(baseW * scale), visualH = Math.round(baseH * scale);
                HudManager.tntX = Math.round(visualX - (baseW - visualW) / 2.0f);
                HudManager.tntY = Math.round(visualY - (baseH - visualH) / 2.0f);
            }
            case "Keybinds" -> {
                float scale = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
                int baseW = ExtraHudsRenderer.keybindsW <= 0 ? 100 : ExtraHudsRenderer.keybindsW;
                int baseH = ExtraHudsRenderer.keybindsH <= 0 ? 22 : ExtraHudsRenderer.keybindsH;
                int visualW = Math.round(baseW * scale), visualH = Math.round(baseH * scale);
                HudManager.keybindsX = Math.round(visualX - (baseW - visualW) / 2.0f);
                HudManager.keybindsY = Math.round(visualY - (baseH - visualH) / 2.0f);
            }
            case "Armor Status" -> {
                float scale = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
                int baseW = ExtraHudsRenderer.armorW <= 0 ? 82 : ExtraHudsRenderer.armorW;
                int baseH = ExtraHudsRenderer.armorH <= 0 ? 22 : ExtraHudsRenderer.armorH;
                int visualW = Math.round(baseW * scale), visualH = Math.round(baseH * scale);
                HudManager.armorX = Math.round(visualX - (baseW - visualW) / 2.0f);
                HudManager.armorY = Math.round(visualY - (baseH - visualH) / 2.0f);
            }
            case "Inventory HUD" -> {
                float scale = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
                int baseW = ExtraHudsRenderer.invW <= 0 ? 174 : ExtraHudsRenderer.invW;
                int baseH = ExtraHudsRenderer.invH <= 0 ? 80 : ExtraHudsRenderer.invH;
                int visualW = Math.round(baseW * scale), visualH = Math.round(baseH * scale);
                HudManager.invX = Math.round(visualX - (baseW - visualW) / 2.0f);
                HudManager.invY = Math.round(visualY - (baseH - visualH) / 2.0f);
            }
            case "Cooldowns" -> {
                float scale = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
                int baseW = ExtraHudsRenderer.coolW <= 0 ? 105 : ExtraHudsRenderer.coolW;
                int baseH = ExtraHudsRenderer.coolH <= 0 ? 22 : ExtraHudsRenderer.coolH;
                int visualW = Math.round(baseW * scale), visualH = Math.round(baseH * scale);
                HudManager.coolX = Math.round(visualX - (baseW - visualW) / 2.0f);
                HudManager.coolY = Math.round(visualY - (baseH - visualH) / 2.0f);
            }
            case "Potions" -> {
                float scale = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
                int baseW = PotionHudRenderer.WIDTH <= 0 ? 120 : PotionHudRenderer.WIDTH;
                int baseH = PotionHudRenderer.HEIGHT <= 0 ? 22 : PotionHudRenderer.HEIGHT;
                int visualW = Math.round(baseW * scale), visualH = Math.round(baseH * scale);
                HudManager.potionX = Math.round(visualX - (baseW - visualW) / 2.0f);
                HudManager.potionY = Math.round(visualY - (baseH - visualH) / 2.0f);
            }
            case "Scoreboard HUD" -> {
                float scale = LexoraGui.numSettings.getOrDefault("Scoreboard HUD Scale", 1.0f);
                int baseW = ScoreboardHudRenderer.lastRenderedW <= 0 ? 110 : ScoreboardHudRenderer.lastRenderedW;
                int baseH = ScoreboardHudRenderer.lastRenderedH <= 0 ? 60 : ScoreboardHudRenderer.lastRenderedH;
                int visualW = Math.round(baseW * scale), visualH = Math.round(baseH * scale);
                HudManager.scoreboardX = Math.round(visualX - (baseW - visualW) / 2.0f);
                HudManager.scoreboardY = Math.round(visualY - (baseH - visualH) / 2.0f);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onHudSettingsClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (targetEditingHud != null && panelAnim > 0.1f) {
            if (!isClickInsideParticleDropdown(mx, my) && (mx < pX - 6 || mx > pX + pW + 6 || my < pY - 6 || my > pY + pH + 6)) {
                targetEditingHud = null; particleDropdownOpen = false; cir.setReturnValue(true); return;
            }
            if (mx >= pX + pW - 22 && mx <= pX + pW && my >= pY && my <= pY + 20) {
                targetEditingHud = null; particleDropdownOpen = false; cir.setReturnValue(true); return;
            }
            if (targetEditingHud.equals("Info HUD")) {
                if (handleSwitcherClick(mx, my, "Info HUD Blur", pY + 24)) { cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 42, pW - 14, 16)) { toggleDefaultTrue("Info Magnetic Snap"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 60, pW - 14, 16)) { toggleDefaultTrue("Info Show Logo"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 78, pW - 14, 16)) { toggleDefaultTrue("Info Show Name"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 96, pW - 14, 16)) { toggleDefaultTrue("Info Show UUID"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 114, pW - 14, 16)) { toggleDefaultTrue("Info Show FPS"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 132, pW - 14, 16)) { toggle("Info Show TPS"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 150, pW - 14, 16)) { toggleDefaultTrue("Info Show Coords"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 168, pW - 14, 16)) { toggleDefaultTrue("Info Show BPS"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 186, pW - 14, 16)) { toggle("Info Show Server"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 204, pW - 14, 24)) { draggingScale = true; cir.setReturnValue(true); return; }
                cir.setReturnValue(true); return;
            } else if (targetEditingHud.equals("Watermark")) {
                if (handleSwitcherClick(mx, my, "Watermark Blur", pY + 24)) { cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 44, pW - 14, 16)) { toggleDefaultTrue("Notif Swap"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 62, pW - 14, 16)) { toggleDefaultTrue("Notif HP"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 80, pW - 14, 16)) { toggleDefaultTrue("Notif Armor"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 98, pW - 14, 16)) { toggleDefaultTrue("Notif Potions"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 118, pW - 14, 24)) { draggingScale = true; cir.setReturnValue(true); return; }
                cir.setReturnValue(true); return;
            } else if (targetEditingHud.equals("Music Hud")) {
                if (inside(mx, my, pX + 7, pY + 24, pW - 14, 16)) { toggleDefaultTrue("Music Hud Controls"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 46, pW - 14, 24)) { draggingScale = true; cir.setReturnValue(true); return; }
                cir.setReturnValue(true); return;
            } else if (targetEditingHud.equals("Target HUD")) {
                if (handleSwitcherClick(mx, my, "Target HUD Blur", pY + 24)) { cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 44, pW - 14, 16)) {
                    boolean st = LexoraGui.moduleStates.getOrDefault("Damage Tint", true);
                    LexoraGui.moduleStates.put("Damage Tint", !st); cir.setReturnValue(true); return;
                }
                if (inside(mx, my, pX + 7, pY + 64, pW - 14, 16)) {
                    boolean st = LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true);
                    LexoraGui.moduleStates.put("Target HUD Particles", !st); particleDropdownOpen = false; pH = computeTargetHudPanelHeight(); cir.setReturnValue(true); return;
                }
                boolean particlesOn = LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true);
                if (particlesOn) {
                    int hit = TargetHudRenderer.hitTestParticleSelector(mx, my, pX + 7, pY + 84, pW - 14, particleDropdownOpen);
                    if (hit != Integer.MIN_VALUE) {
                        if (hit == -2) particleDropdownOpen = !particleDropdownOpen;
                        else if (hit >= 0) { LexoraGui.numSettings.put("Target HUD Particle Type", (float) hit); particleDropdownOpen = false; }
                        else particleDropdownOpen = false;
                        cir.setReturnValue(true); return;
                    }
                }
                int scaleDragY = pY + (particlesOn ? 116 : 92);
                if (inside(mx, my, pX + 7, scaleDragY, pW - 14, 24)) { draggingScale = true; cir.setReturnValue(true); return; }
                cir.setReturnValue(true); return;
            } else if (targetEditingHud.equals("TNT Detect")) {
                if (handleSwitcherClick(mx, my, "TNT Detect Blur", pY + 24)) { cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 46, pW - 14, 24)) { draggingScale = true; cir.setReturnValue(true); return; }
                cir.setReturnValue(true); return;
            } else if (targetEditingHud.equals("Scoreboard HUD")) {
                if (handleSwitcherClick(mx, my, "Scoreboard HUD Blur", pY + 24)) { cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 44, pW - 14, 16)) { toggleDefaultTrue("Scoreboard HUD Home Indicator"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 66, pW - 14, 24)) { draggingScale = true; cir.setReturnValue(true); return; }
                cir.setReturnValue(true); return;
            } else if (isHudWithHomeIndicator(targetEditingHud)) {
                if (handleSwitcherClick(mx, my, targetEditingHud + " Blur", pY + 24)) { cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 44, pW - 14, 16)) { toggleDefaultTrue(targetEditingHud + " Home Indicator"); cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 66, pW - 14, 24)) { draggingScale = true; cir.setReturnValue(true); return; }
                cir.setReturnValue(true); return;
            } else {
                if (handleSwitcherClick(mx, my, targetEditingHud + " Blur", pY + 24)) { cir.setReturnValue(true); return; }
                if (inside(mx, my, pX + 7, pY + 46, pW - 14, 24)) { draggingScale = true; cir.setReturnValue(true); return; }
                cir.setReturnValue(true); return;
            }
        }

        // ПКМ КЛИКИ: ОТКРЫТИЕ НАСТРОЕК
        if (button == 1 && targetEditingHud == null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            float infoScale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f);
            int infoBaseX = com.lexoravisauls.client.events.InfoHudRenderer.getBaseX(mc), infoBaseY = com.lexoravisauls.client.events.InfoHudRenderer.getBaseY(mc);
            if (LexoraGui.moduleStates.getOrDefault("Info HUD", true) && mx >= infoBaseX && mx <= infoBaseX + com.lexoravisauls.client.events.InfoHudRenderer.WIDTH * infoScale && my >= infoBaseY && my <= infoBaseY + com.lexoravisauls.client.events.InfoHudRenderer.HEIGHT * infoScale) {
                targetEditingHud = "Info HUD"; editingHud = "Info HUD"; pW = 140; pH = 232; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
            int wX = DynamicIslandRenderer.islandX, wY = DynamicIslandRenderer.islandY;
            if (LexoraGui.moduleStates.getOrDefault("Watermark", true) && mx >= wX && mx <= wX + DynamicIslandRenderer.islandW && my >= wY && my <= wY + DynamicIslandRenderer.islandH) {
                if (!DynamicIslandRenderer.isMusicExpanded()) { targetEditingHud = "Watermark"; editingHud = "Watermark"; pW = 140; pH = 142; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return; }
            }
            float targetScale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            int tBaseX = HudManager.targetX == -1 ? 150 : HudManager.targetX, tBaseY = HudManager.targetY == -1 ? 150 : HudManager.targetY;
            int tVisualW = Math.round(TargetHudRenderer.WIDTH * targetScale), tVisualH = Math.round(TargetHudRenderer.HEIGHT * targetScale);
            int tVisualX = Math.round(tBaseX + (TargetHudRenderer.WIDTH - tVisualW) / 2.0f), tVisualY = Math.round(tBaseY + (TargetHudRenderer.HEIGHT - tVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Target HUD", true) && mx >= tVisualX && mx <= tVisualX + tVisualW && my >= tVisualY && my <= tVisualY + tVisualH) {
                targetEditingHud = "Target HUD"; editingHud = "Target HUD"; pW = 140; pH = computeTargetHudPanelHeight(); pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
            float tntScale = LexoraGui.numSettings.getOrDefault("TNT Detect Scale", 1.0f);
            int tntBaseX = HudManager.tntX == -1 ? 10 : HudManager.tntX, tntBaseY = HudManager.tntY == -1 ? 160 : HudManager.tntY;
            int tntBaseW = TntHudRenderer.WIDTH <= 0 ? 115 : TntHudRenderer.WIDTH, tntBaseH = TntHudRenderer.HEIGHT <= 0 ? 42 : TntHudRenderer.HEIGHT;
            int tntVisualW = Math.round(tntBaseW * tntScale), tntVisualH = Math.round(tntBaseH * tntScale);
            int tntVisualX = Math.round(tntBaseX + (tntBaseW - tntVisualW) / 2.0f), tntVisualY = Math.round(tntBaseY + (tntBaseH - tntVisualH) / 2.0f);
            if (TntHudRenderer.isModuleEnabled("TNT Detect", true) && mx >= tntVisualX && mx <= tntVisualX + tntVisualW && my >= tntVisualY && my <= tntVisualY + tntVisualH) {
                targetEditingHud = "TNT Detect"; editingHud = "TNT Detect"; pW = 140; pH = 74; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
            float keyScale = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
            int keyBaseW = ExtraHudsRenderer.keybindsW <= 0 ? 100 : ExtraHudsRenderer.keybindsW, keyBaseH = ExtraHudsRenderer.keybindsH <= 0 ? 22 : ExtraHudsRenderer.keybindsH;
            int keyVisualW = Math.round(keyBaseW * keyScale), keyVisualH = Math.round(keyBaseH * keyScale);
            int keyVisualX = Math.round(HudManager.keybindsX + (keyBaseW - keyVisualW) / 2.0f), keyVisualY = Math.round(HudManager.keybindsY + (keyBaseH - keyVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Keybinds", false) && mx >= keyVisualX && mx <= keyVisualX + keyVisualW && my >= keyVisualY && my <= keyVisualY + keyVisualH) {
                targetEditingHud = "Keybinds"; editingHud = "Keybinds"; pW = 140; pH = 92; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
            float armorScale = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
            int armBaseW = ExtraHudsRenderer.armorW <= 0 ? 82 : ExtraHudsRenderer.armorW, armBaseH = ExtraHudsRenderer.armorH <= 0 ? 22 : ExtraHudsRenderer.armorH;
            int armVisualW = Math.round(armBaseW * armorScale), armVisualH = Math.round(armBaseH * armorScale);
            int armVisualX = Math.round(HudManager.armorX + (armBaseW - armVisualW) / 2.0f), armVisualY = Math.round(HudManager.armorY + (armBaseH - armVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Armor Status", false) && mx >= armVisualX && mx <= armVisualX + armVisualW && my >= armVisualY && my <= armVisualY + armVisualH) {
                targetEditingHud = "Armor Status"; editingHud = "Armor Status"; pW = 140; pH = 92; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
            float invScale = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
            int invBaseW = ExtraHudsRenderer.invW <= 0 ? 174 : ExtraHudsRenderer.invW, invBaseH = ExtraHudsRenderer.invH <= 0 ? 80 : ExtraHudsRenderer.invH;
            int invVisualW = Math.round(invBaseW * invScale), invVisualH = Math.round(invBaseH * invScale);
            int invVisualX = Math.round(HudManager.invX + (invBaseW - invVisualW) / 2.0f), invVisualY = Math.round(HudManager.invY + (invBaseH - invVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Inventory HUD", false) && mx >= invVisualX && mx <= invVisualX + invVisualW && my >= invVisualY && my <= invVisualY + invVisualH) {
                targetEditingHud = "Inventory HUD"; editingHud = "Inventory HUD"; pW = 140; pH = 92; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
            float coolScale = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
            int coolBaseW = ExtraHudsRenderer.coolW <= 0 ? 105 : ExtraHudsRenderer.coolW, coolBaseH = ExtraHudsRenderer.coolH <= 0 ? 22 : ExtraHudsRenderer.coolH;
            int coolVisualW = Math.round(coolBaseW * coolScale), coolVisualH = Math.round(coolBaseH * coolScale);
            int coolVisualX = Math.round(HudManager.coolX + (coolBaseW - coolVisualW) / 2.0f), coolVisualY = Math.round(HudManager.coolY + (coolBaseH - coolVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Cooldowns", false) && mx >= coolVisualX && mx <= coolVisualX + coolVisualW && my >= coolVisualY && my <= coolVisualY + coolVisualH) {
                targetEditingHud = "Cooldowns"; editingHud = "Cooldowns"; pW = 140; pH = 92; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
            float potScale = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
            int potBaseX = HudManager.potionX == -1 ? 10 : HudManager.potionX, potBaseY = HudManager.potionY == -1 ? 100 : HudManager.potionY;
            int potBaseW = PotionHudRenderer.WIDTH <= 0 ? 120 : PotionHudRenderer.WIDTH, potBaseH = PotionHudRenderer.HEIGHT <= 0 ? 22 : PotionHudRenderer.HEIGHT;
            int potVisualW = Math.round(potBaseW * potScale), potVisualH = Math.round(potBaseH * potScale);
            int potVisualX = Math.round(potBaseX + (potBaseW - potVisualW) / 2.0f), potVisualY = Math.round(potBaseY + (potBaseH - potVisualH) / 2.0f);
            if (PotionHudRenderer.isModuleEnabled("Potions", true) && mx >= potVisualX && mx <= potVisualX + potVisualW && my >= potVisualY && my <= potVisualY + potVisualH) {
                targetEditingHud = "Potions"; editingHud = "Potions"; pW = 140; pH = 92; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
            float sbScale = LexoraGui.numSettings.getOrDefault("Scoreboard HUD Scale", 1.0f);
            int sbBaseW = ScoreboardHudRenderer.lastRenderedW <= 0 ? 110 : ScoreboardHudRenderer.lastRenderedW, sbBaseH = ScoreboardHudRenderer.lastRenderedH <= 0 ? 60 : ScoreboardHudRenderer.lastRenderedH;
            int sbBaseX = (HudManager.scoreboardX < 0) ? mc.getWindow().getScaledWidth() - sbBaseW - 4 : HudManager.scoreboardX, sbBaseY = (HudManager.scoreboardY < 0) ? 4 : HudManager.scoreboardY;
            int sbVisualW = Math.round(sbBaseW * sbScale), sbVisualH = Math.round(sbBaseH * sbScale);
            int sbVisualX = Math.round(sbBaseX + (sbBaseW - sbVisualW) / 2.0f), sbVisualY = Math.round(sbBaseY + (sbBaseH - sbVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Scoreboard HUD", false) && mx >= sbVisualX && mx <= sbVisualX + sbVisualW && my >= sbVisualY && my <= sbVisualY + sbVisualH) {
                targetEditingHud = "Scoreboard HUD"; editingHud = "Scoreboard HUD"; pW = 140; pH = 92; pX = mx + 10; pY = my + 10; clampPanelToScreen(); cir.setReturnValue(true); return;
            }
        }

        // ЛКМ КЛИКИ: ДРАГ ХУДОВ (ВАТЕРМАРКА НЕ ДВИГАЕТСЯ)
        if (button == 0 && targetEditingHud == null) {
            if (DynamicIslandRenderer.mouseClicked(mouseX, mouseY, button)) { cir.setReturnValue(true); return; }
            MinecraftClient mc = MinecraftClient.getInstance();
            float infoScale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f);
            int infoBaseX = HudManager.infoX, infoBaseY = HudManager.infoY;
            int infoVisualW = Math.max(120, Math.round(InfoHudRenderer.WIDTH * infoScale));
            int infoVisualH = Math.max(30, Math.round(InfoHudRenderer.HEIGHT * infoScale));
            if (LexoraGui.moduleStates.getOrDefault("Info HUD", true) && mx >= infoBaseX && mx <= infoBaseX + infoVisualW && my >= infoBaseY && my <= infoBaseY + infoVisualH) {
                startDraggingHud("Info HUD", infoBaseX, infoBaseY, infoVisualW, infoVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
            float targetScale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            int tBaseX = HudManager.targetX == -1 ? 150 : HudManager.targetX, tBaseY = HudManager.targetY == -1 ? 150 : HudManager.targetY;
            int tVisualW = Math.round(TargetHudRenderer.WIDTH * targetScale), tVisualH = Math.round(TargetHudRenderer.HEIGHT * targetScale);
            int tVisualX = Math.round(tBaseX + (TargetHudRenderer.WIDTH - tVisualW) / 2.0f), tVisualY = Math.round(tBaseY + (TargetHudRenderer.HEIGHT - tVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Target HUD", true) && mx >= tVisualX && mx <= tVisualX + tVisualW && my >= tVisualY && my <= tVisualY + tVisualH) {
                startDraggingHud("Target HUD", tVisualX, tVisualY, tVisualW, tVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
            float tntScale = LexoraGui.numSettings.getOrDefault("TNT Detect Scale", 1.0f);
            int tntBaseX = HudManager.tntX == -1 ? 10 : HudManager.tntX, tntBaseY = HudManager.tntY == -1 ? 160 : HudManager.tntY;
            int tntBaseW = TntHudRenderer.WIDTH <= 0 ? 115 : TntHudRenderer.WIDTH, tntBaseH = TntHudRenderer.HEIGHT <= 0 ? 42 : TntHudRenderer.HEIGHT;
            int tntVisualW = Math.round(tntBaseW * tntScale), tntVisualH = Math.round(tntBaseH * tntScale);
            int tntVisualX = Math.round(tntBaseX + (tntBaseW - tntVisualW) / 2.0f), tntVisualY = Math.round(tntBaseY + (tntBaseH - tntVisualH) / 2.0f);
            if (TntHudRenderer.isModuleEnabled("TNT Detect", true) && mx >= tntVisualX && mx <= tntVisualX + tntVisualW && my >= tntVisualY && my <= tntVisualY + tntVisualH) {
                startDraggingHud("TNT Detect", tVisualX, tVisualY, tVisualW, tVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
            float keyScale = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
            int keyBaseW = ExtraHudsRenderer.keybindsW <= 0 ? 100 : ExtraHudsRenderer.keybindsW, keyBaseH = ExtraHudsRenderer.keybindsH <= 0 ? 22 : ExtraHudsRenderer.keybindsH;
            int keyVisualW = Math.round(keyBaseW * keyScale), keyVisualH = Math.round(keyBaseH * keyScale);
            int keyVisualX = Math.round(HudManager.keybindsX + (keyBaseW - keyVisualW) / 2.0f), keyVisualY = Math.round(HudManager.keybindsY + (keyBaseH - keyVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Keybinds", false) && mx >= keyVisualX && mx <= keyVisualX + keyVisualW && my >= keyVisualY && my <= keyVisualY + keyVisualH) {
                startDraggingHud("Keybinds", keyVisualX, keyVisualY, keyVisualW, keyVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
            float armorScale = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
            int armBaseW = ExtraHudsRenderer.armorW <= 0 ? 82 : ExtraHudsRenderer.armorW, armBaseH = ExtraHudsRenderer.armorH <= 0 ? 22 : ExtraHudsRenderer.armorH;
            int armVisualW = Math.round(armBaseW * armorScale), armVisualH = Math.round(armBaseH * armorScale);
            int armVisualX = Math.round(HudManager.armorX + (armBaseW - armVisualW) / 2.0f), armVisualY = Math.round(HudManager.armorY + (armBaseH - armVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Armor Status", false) && mx >= armVisualX && mx <= armVisualX + armVisualW && my >= armVisualY && my <= armVisualY + armVisualH) {
                startDraggingHud("Armor Status", armVisualX, armVisualY, armVisualW, armVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
            float invScale = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
            int invBaseW = ExtraHudsRenderer.invW <= 0 ? 174 : ExtraHudsRenderer.invW, invBaseH = ExtraHudsRenderer.invH <= 0 ? 80 : ExtraHudsRenderer.invH;
            int invVisualW = Math.round(invBaseW * invScale), invVisualH = Math.round(invBaseH * invScale);
            int invVisualX = Math.round(HudManager.invX + (invBaseW - invVisualW) / 2.0f), invVisualY = Math.round(HudManager.invY + (invBaseH - invVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Inventory HUD", false) && mx >= invVisualX && mx <= invVisualX + invVisualW && my >= invVisualY && my <= invVisualY + invVisualH) {
                startDraggingHud("Inventory HUD", invVisualX, invVisualY, invVisualW, invVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
            float coolScale = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
            int coolBaseW = ExtraHudsRenderer.coolW <= 0 ? 105 : ExtraHudsRenderer.coolW, coolBaseH = ExtraHudsRenderer.coolH <= 0 ? 22 : ExtraHudsRenderer.coolH;
            int coolVisualW = Math.round(coolBaseW * coolScale), coolVisualH = Math.round(coolBaseH * coolScale);
            int coolVisualX = Math.round(HudManager.coolX + (coolBaseW - coolVisualW) / 2.0f), coolVisualY = Math.round(HudManager.coolY + (coolBaseH - coolVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Cooldowns", false) && mx >= coolVisualX && mx <= coolVisualX + coolVisualW && my >= coolVisualY && my <= coolVisualY + coolVisualH) {
                startDraggingHud("Cooldowns", coolVisualX, coolVisualY, coolVisualW, coolVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
            float potScale = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
            int potBaseX = HudManager.potionX == -1 ? 10 : HudManager.potionX, potBaseY = HudManager.potionY == -1 ? 100 : HudManager.potionY;
            int potBaseW = PotionHudRenderer.WIDTH <= 0 ? 120 : PotionHudRenderer.WIDTH, potBaseH = PotionHudRenderer.HEIGHT <= 0 ? 22 : PotionHudRenderer.HEIGHT;
            int potVisualW = Math.round(potBaseW * potScale), potVisualH = Math.round(potBaseH * potScale);
            int potVisualX = Math.round(potBaseX + (potBaseW - potVisualW) / 2.0f), potVisualY = Math.round(potBaseY + (potBaseH - potVisualH) / 2.0f);
            if (PotionHudRenderer.isModuleEnabled("Potions", true) && mx >= potVisualX && mx <= potVisualX + potVisualW && my >= potVisualY && my <= potVisualY + potVisualH) {
                startDraggingHud("Potions", potVisualX, potVisualY, potVisualW, potVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
            float sbScale = LexoraGui.numSettings.getOrDefault("Scoreboard HUD Scale", 1.0f);
            int sbBaseW = ScoreboardHudRenderer.lastRenderedW <= 0 ? 110 : ScoreboardHudRenderer.lastRenderedW, sbBaseH = ScoreboardHudRenderer.lastRenderedH <= 0 ? 60 : ScoreboardHudRenderer.lastRenderedH;
            int sbBaseX = (HudManager.scoreboardX < 0) ? mc.getWindow().getScaledWidth() - sbBaseW - 4 : HudManager.scoreboardX, sbBaseY = (HudManager.scoreboardY < 0) ? 4 : HudManager.scoreboardY;
            int sbVisualW = Math.round(sbBaseW * sbScale), sbVisualH = Math.round(sbBaseH * sbScale);
            int sbVisualX = Math.round(sbBaseX + (sbBaseW - sbVisualW) / 2.0f), sbVisualY = Math.round(sbBaseY + (sbBaseH - sbVisualH) / 2.0f);
            if (LexoraGui.moduleStates.getOrDefault("Scoreboard HUD", false) && mx >= sbVisualX && mx <= sbVisualX + sbVisualW && my >= sbVisualY && my <= sbVisualY + sbVisualH) {
                startDraggingHud("Scoreboard HUD", sbVisualX, sbVisualY, sbVisualW, sbVisualH, mouseX, mouseY); cir.setReturnValue(true); return;
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onHudSettingsRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        if (!isMouseDown) {
            if (draggingHudPos && draggingHudName != null) {
                applyHudPositionFromVisual(draggingHudName, Math.round(dragTargetX), Math.round(dragTargetY));
                ConfigManager.saveConfig();
                draggingHudPos = false;
                draggingHudName = null;
            }
            draggingScale = false;
        }

        if (draggingHudPos && draggingHudName != null) {
            dragTargetX = (float) mouseX - dragOffsetX;
            dragTargetY = (float) mouseY - dragOffsetY;
            dragVisualAlpha += (1.0f - dragVisualAlpha) * 0.35f;
            applyHudPositionFromVisual(draggingHudName, Math.round(dragTargetX), Math.round(dragTargetY));
            renderHudDragVisuals(context, 0.0f);
        } else {
            dragVisualAlpha += (0.0f - dragVisualAlpha) * 0.40f;
            if (dragVisualAlpha > 0.01f) renderHudDragVisuals(context, 1.0f - dragVisualAlpha);
        }

        // ПЛАВНЫЙ ТЕКСТ ПОД ХУДОМ ПРИ НАВЕДЕНИИ (КРОМЕ ВАТЕРМАРКИ)
        renderHudHoverTooltips(context, mouseX, mouseY);

        // ПАНЕЛЬ НАСТРОЕК С ПОДДЕРЖКОЙ ТЕМ (DARK / LIGHT)
        float targetAnim = targetEditingHud != null ? 1.0f : 0.0f;
        panelAnim += (targetAnim - panelAnim) * 0.18f;
        if (panelAnim < 0.01f) { editingHud = null; return; }
        clampPanelToScreen();

        boolean dark = isDark();
        if (draggingScale && targetEditingHud != null) {
            float percent = Math.max(0f, Math.min(1f, (mouseX - (pX + 7)) / (float) (pW - 14)));
            float newScale = 0.5f + percent * 1.5f;
            String scaleKey = targetEditingHud + " Scale";
            LexoraGui.numSettings.put(scaleKey, (float) (Math.round(newScale * 100.0) / 100.0));
        }

        context.getMatrices().push();
        context.getMatrices().translate(pX + pW / 2f, pY + pH / 2f, 0);
        context.getMatrices().scale(panelAnim, panelAnim, 1.0f);
        context.getMatrices().translate(-(pX + pW / 2f), -(pY + pH / 2f), 0);

        int alphaInt = Math.max(5, Math.min(255, (int) (panelAnim * 255)));
        int cardBg = dark ? (((int) (panelAnim * 248) << 24) | 0x0c0c0e) : (((int) (panelAnim * 248) << 24) | 0xF0F2F6);
        RoundedRectShader.draw(context, pX, pY, pW, pH, 7.5f, cardBg);

        renderHeaderWithIcon(context, pX + 7, pY + 8, alphaInt, dark);
        boolean closeHover = mouseX >= pX + pW - 22 && mouseX <= pX + pW && mouseY >= pY && mouseY <= pY + 20;
        int closeColor = closeHover ? (dark ? ((alphaInt << 24) | 0xFFFFFF) : ((alphaInt << 24) | 0x111116)) : (dark ? ((alphaInt << 24) | 0x77777E) : ((alphaInt << 24) | 0x888894));
        LexoraIcons.draw(context, LexoraIcons.Icon.CLOSE, pX + pW - 15, pY + 7, 8.5f, closeColor);

        if (editingHud.equals("Info HUD")) {
            drawBgSwitcher(context, "Фон", "Info HUD Blur", pY + 24, alphaInt, dark);
            drawInfoToggle(context, "Магнит", "Info Magnetic Snap", pY + 42, alphaInt, dark);
            drawInfoToggle(context, "Лого", "Info Show Logo", pY + 60, alphaInt, dark);
            drawInfoToggle(context, "Ник", "Info Show Name", pY + 78, alphaInt, dark);
            drawInfoToggle(context, "UUID", "Info Show UUID", pY + 96, alphaInt, dark);
            drawInfoToggle(context, "FPS", "Info Show FPS", pY + 114, alphaInt, dark);
            drawInfoToggle(context, "TPS", "Info Show TPS", pY + 132, alphaInt, dark);
            drawInfoToggle(context, "Координаты", "Info Show Coords", pY + 150, alphaInt, dark);
            drawInfoToggle(context, "Скорость b/s", "Info Show BPS", pY + 168, alphaInt, dark);
            drawInfoToggle(context, "Сервер", "Info Show Server", pY + 186, alphaInt, dark);
            float scale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f);
            drawScaleSlider(context, scale, pY + 214, alphaInt, dark);
        } else if (editingHud.equals("Watermark")) {
            drawBgSwitcher(context, "Фон", "Watermark Blur", pY + 24, alphaInt, dark);
            drawToggleDefaultTrue(context, "Увед. о свапе", "Notif Swap", pY + 44, alphaInt, dark);
            drawToggleDefaultTrue(context, "Увед. ХП", "Notif HP", pY + 62, alphaInt, dark);
            drawToggleDefaultTrue(context, "Увед. Броня", "Notif Armor", pY + 80, alphaInt, dark);
            drawToggleDefaultTrue(context, "Увед. Зелья", "Notif Potions", pY + 98, alphaInt, dark);
            float scale = LexoraGui.numSettings.getOrDefault("Watermark Scale", 1.0f);
            drawScaleSlider(context, scale, pY + 126, alphaInt, dark);
        } else if (editingHud.equals("Music Hud")) {
            drawToggleDefaultTrue(context, "Кнопки", "Music Hud Controls", pY + 24, alphaInt, dark);
            float scale = LexoraGui.numSettings.getOrDefault("Music Hud Scale", 1.0f);
            drawScaleSlider(context, scale, pY + 54, alphaInt, dark);
        } else if (editingHud.equals("Target HUD")) {
            drawBgSwitcher(context, "Фон", "Target HUD Blur", pY + 24, alphaInt, dark);
            drawToggleDefaultTrue(context, "Урон красным", "Damage Tint", pY + 44, alphaInt, dark);
            boolean particlesOn = LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true);
            drawToggleDefaultTrue(context, "Частицы", "Target HUD Particles", pY + 64, alphaInt, dark);
            if (particlesOn) {
                TargetHudRenderer.renderParticleSelector(context, pX + 7, pY + 84, pW - 14, particleDropdownOpen, mouseX, mouseY);
            }
            int scaleBase = pY + (particlesOn ? 116 : 92);
            float scale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            drawScaleSlider(context, scale, scaleBase, alphaInt, dark);
            pH = computeTargetHudPanelHeight();
        } else if (editingHud.equals("TNT Detect")) {
            drawBgSwitcher(context, "Фон", "TNT Detect Blur", pY + 24, alphaInt, dark);
            float scale = LexoraGui.numSettings.getOrDefault("TNT Detect Scale", 1.0f);
            drawScaleSlider(context, scale, pY + 54, alphaInt, dark);
        } else if (editingHud.equals("Scoreboard HUD")) {
            drawBgSwitcher(context, "Фон", "Scoreboard HUD Blur", pY + 24, alphaInt, dark);
            drawToggleDefaultTrue(context, "Полоска снизу", "Scoreboard HUD Home Indicator", pY + 44, alphaInt, dark);
            float sbScale = LexoraGui.numSettings.getOrDefault("Scoreboard HUD Scale", 1.0f);
            drawScaleSlider(context, sbScale, pY + 74, alphaInt, dark);
        } else if (isHudWithHomeIndicator(editingHud)) {
            String blurKey = editingHud + " Blur", homeKey = editingHud + " Home Indicator", scaleKey = editingHud + " Scale";
            drawBgSwitcher(context, "Фон", blurKey, pY + 24, alphaInt, dark);
            drawToggleDefaultTrue(context, "Полоска снизу", homeKey, pY + 44, alphaInt, dark);
            float scale = LexoraGui.numSettings.getOrDefault(scaleKey, 1.0f);
            drawScaleSlider(context, scale, pY + 74, alphaInt, dark);
        } else {
            String blurKey = editingHud + " Blur", scaleKey = editingHud + " Scale";
            drawBgSwitcher(context, "Фон", blurKey, pY + 24, alphaInt, dark);
            float scale = LexoraGui.numSettings.getOrDefault(scaleKey, 1.0f);
            drawScaleSlider(context, scale, pY + 54, alphaInt, dark);
        }
        context.getMatrices().pop();
    }

    private void renderHeaderWithIcon(DrawContext context, float x, float y, int alphaInt, boolean dark) {
        int titleColor = dark ? ((alphaInt << 24) | 0xFFFFFF) : ((alphaInt << 24) | 0x111116);
        int iconColor = dark ? ((alphaInt << 24) | 0x9999A5) : ((alphaInt << 24) | 0x666670);
        LexoraIcons.Icon icon = switch (editingHud) {
            case "Info HUD" -> LexoraIcons.Icon.MONITOR;
            case "Target HUD" -> LexoraIcons.Icon.PLAYER;
            case "Watermark" -> LexoraIcons.Icon.AIRPLANE;
            case "TNT Detect" -> LexoraIcons.Icon.EXCLAMATION;
            case "Keybinds" -> LexoraIcons.Icon.KEYBOARD;
            case "Armor Status" -> LexoraIcons.Icon.SHIELD;
            case "Inventory HUD" -> LexoraIcons.Icon.APPS;
            case "Cooldowns" -> LexoraIcons.Icon.HOURGLASS;
            case "Potions" -> LexoraIcons.Icon.POTION;
            case "Scoreboard HUD" -> LexoraIcons.Icon.TEXT;
            default -> LexoraIcons.Icon.GEAR;
        };
        LexoraIcons.draw(context, icon, x, y + 0.5f, 7.5f, iconColor);
        String displayName = editingHud.equals("Watermark") ? "Dynamic Island" : editingHud + " Settings";
        SFUI.draw(context.getMatrices(), displayName, x + 10, y, 7.5f, titleColor);
    }

    private void renderHudHoverTooltips(DrawContext context, int mouseX, int mouseY) {
        if (targetEditingHud != null || draggingHudPos) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;

        String hoveredHud = null;
        float hX = 0, hY = 0, hW = 0, hH = 0;

        if (LexoraGui.moduleStates.getOrDefault("Info HUD", true)) {
            float sc = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f);
            int bx = HudManager.infoX, by = HudManager.infoY;
            int bw = Math.max(120, Math.round(InfoHudRenderer.WIDTH * sc));
            int bh = Math.max(30, Math.round(InfoHudRenderer.HEIGHT * sc));
            if (inside(mouseX, mouseY, bx, by, bw, bh)) { hoveredHud = "Info HUD"; hX = bx; hY = by; hW = bw; hH = bh; }
        }
        if (hoveredHud == null && LexoraGui.moduleStates.getOrDefault("Target HUD", true)) {
            float sc = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            int tBaseX = HudManager.targetX == -1 ? 150 : HudManager.targetX, tBaseY = HudManager.targetY == -1 ? 150 : HudManager.targetY;
            int bw = Math.round(TargetHudRenderer.WIDTH * sc), bh = Math.round(TargetHudRenderer.HEIGHT * sc);
            int bx = Math.round(tBaseX + (TargetHudRenderer.WIDTH - bw) / 2.0f), by = Math.round(tBaseY + (TargetHudRenderer.HEIGHT - bh) / 2.0f);
            if (inside(mouseX, mouseY, bx, by, bw, bh)) { hoveredHud = "Target HUD"; hX = bx; hY = by; hW = bw; hH = bh; }
        }
        if (hoveredHud == null && TntHudRenderer.isModuleEnabled("TNT Detect", true)) {
            float sc = LexoraGui.numSettings.getOrDefault("TNT Detect Scale", 1.0f);
            int tBaseX = HudManager.tntX == -1 ? 10 : HudManager.tntX, tBaseY = HudManager.tntY == -1 ? 160 : HudManager.tntY;
            int baseW = TntHudRenderer.WIDTH <= 0 ? 115 : TntHudRenderer.WIDTH, baseH = TntHudRenderer.HEIGHT <= 0 ? 42 : TntHudRenderer.HEIGHT;
            int bw = Math.round(baseW * sc), bh = Math.round(baseH * sc);
            int bx = Math.round(tBaseX + (baseW - bw) / 2.0f), by = Math.round(tBaseY + (baseH - bh) / 2.0f);
            if (inside(mouseX, mouseY, bx, by, bw, bh)) { hoveredHud = "TNT Detect"; hX = bx; hY = by; hW = bw; hH = bh; }
        }
        if (hoveredHud == null && LexoraGui.moduleStates.getOrDefault("Keybinds", false)) {
            float sc = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
            int baseW = ExtraHudsRenderer.keybindsW <= 0 ? 100 : ExtraHudsRenderer.keybindsW, baseH = ExtraHudsRenderer.keybindsH <= 0 ? 22 : ExtraHudsRenderer.keybindsH;
            int bw = Math.round(baseW * sc), bh = Math.round(baseH * sc);
            int bx = Math.round(HudManager.keybindsX + (baseW - bw) / 2.0f), by = Math.round(HudManager.keybindsY + (baseH - bh) / 2.0f);
            if (inside(mouseX, mouseY, bx, by, bw, bh)) { hoveredHud = "Keybinds"; hX = bx; hY = by; hW = bw; hH = bh; }
        }
        if (hoveredHud == null && LexoraGui.moduleStates.getOrDefault("Armor Status", false)) {
            float sc = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
            int baseW = ExtraHudsRenderer.armorW <= 0 ? 82 : ExtraHudsRenderer.armorW, baseH = ExtraHudsRenderer.armorH <= 0 ? 22 : ExtraHudsRenderer.armorH;
            int bw = Math.round(baseW * sc), bh = Math.round(baseH * sc);
            int bx = Math.round(HudManager.armorX + (baseW - bw) / 2.0f), by = Math.round(HudManager.armorY + (baseH - bh) / 2.0f);
            if (inside(mouseX, mouseY, bx, by, bw, bh)) { hoveredHud = "Armor Status"; hX = bx; hY = by; hW = bw; hH = bh; }
        }
        if (hoveredHud == null && LexoraGui.moduleStates.getOrDefault("Inventory HUD", false)) {
            float sc = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
            int baseW = ExtraHudsRenderer.invW <= 0 ? 174 : ExtraHudsRenderer.invW, baseH = ExtraHudsRenderer.invH <= 0 ? 80 : ExtraHudsRenderer.invH;
            int bw = Math.round(baseW * sc), bh = Math.round(baseH * sc);
            int bx = Math.round(HudManager.invX + (baseW - bw) / 2.0f), by = Math.round(HudManager.invY + (baseH - bh) / 2.0f);
            if (inside(mouseX, mouseY, bx, by, bw, bh)) { hoveredHud = "Inventory HUD"; hX = bx; hY = by; hW = bw; hH = bh; }
        }
        if (hoveredHud == null && LexoraGui.moduleStates.getOrDefault("Cooldowns", false)) {
            float sc = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
            int baseW = ExtraHudsRenderer.coolW <= 0 ? 105 : ExtraHudsRenderer.coolW, baseH = ExtraHudsRenderer.coolH <= 0 ? 22 : ExtraHudsRenderer.coolH;
            int bw = Math.round(baseW * sc), bh = Math.round(baseH * sc);
            int bx = Math.round(HudManager.coolX + (baseW - bw) / 2.0f), by = Math.round(HudManager.coolY + (baseH - bh) / 2.0f);
            if (inside(mouseX, mouseY, bx, by, bw, bh)) { hoveredHud = "Cooldowns"; hX = bx; hY = by; hW = bw; hH = bh; }
        }
        if (hoveredHud == null && PotionHudRenderer.isModuleEnabled("Potions", true)) {
            float sc = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
            int potBaseX = HudManager.potionX == -1 ? 10 : HudManager.potionX, potBaseY = HudManager.potionY == -1 ? 100 : HudManager.potionY;
            int potBaseW = PotionHudRenderer.WIDTH <= 0 ? 120 : PotionHudRenderer.WIDTH, potBaseH = PotionHudRenderer.HEIGHT <= 0 ? 22 : PotionHudRenderer.HEIGHT;
            int potVisualW = Math.round(potBaseW * sc), potVisualH = Math.round(potBaseH * sc);
            int potVisualX = Math.round(potBaseX + (potBaseW - potVisualW) / 2.0f), potVisualY = Math.round(potBaseY + (potBaseH - potVisualH) / 2.0f);
            if (inside(mouseX, mouseY, potVisualX, potVisualY, potVisualW, potVisualH)) { hoveredHud = "Potions"; hX = potVisualX; hY = potVisualY; hW = potVisualW; hH = potVisualH; }
        }
        if (hoveredHud == null && LexoraGui.moduleStates.getOrDefault("Scoreboard HUD", false)) {
            float sc = LexoraGui.numSettings.getOrDefault("Scoreboard HUD Scale", 1.0f);
            int baseW = ScoreboardHudRenderer.lastRenderedW <= 0 ? 110 : ScoreboardHudRenderer.lastRenderedW, baseH = ScoreboardHudRenderer.lastRenderedH <= 0 ? 60 : ScoreboardHudRenderer.lastRenderedH;
            int sbBaseX = (HudManager.scoreboardX < 0) ? mc.getWindow().getScaledWidth() - baseW - 4 : HudManager.scoreboardX, sbBaseY = (HudManager.scoreboardY < 0) ? 4 : HudManager.scoreboardY;
            int bw = Math.round(baseW * sc), bh = Math.round(baseH * sc);
            int bx = Math.round(sbBaseX + (baseW - bw) / 2.0f), by = Math.round(sbBaseY + (baseH - bh) / 2.0f);
            if (inside(mouseX, mouseY, bx, by, bw, bh)) { hoveredHud = "Scoreboard HUD"; hX = bx; hY = by; hW = bw; hH = bh; }
        }

        String[] allHuds = new String[]{"Info HUD", "Target HUD", "TNT Detect", "Keybinds", "Armor Status", "Inventory HUD", "Cooldowns", "Potions", "Scoreboard HUD"};
        for (String hud : allHuds) {
            float target = (hud.equals(hoveredHud)) ? 1.0f : 0.0f;
            float cur = hudHoverAnimations.getOrDefault(hud, 0f);
            cur += (target - cur) * 0.12f;
            if (Math.abs(cur - target) < 0.005f) cur = target;
            hudHoverAnimations.put(hud, cur);
        }

        if (hoveredHud != null) {
            float alpha = hudHoverAnimations.getOrDefault(hoveredHud, 0f);
            if (alpha > 0.02f) {
                boolean dark = isDark();
                String tipText = "ЛКМ — двигать  •  ПКМ — настройки";
                float tipFontSize = 6.5f;
                float textW = SFUI.getWidth(tipText, tipFontSize);
                float textX = hX + (hW - textW) / 2f;
                float textY = hY + hH + (hoveredHud.equals("Target HUD") ? 18f : 4f);
                if (textY + 10f > mc.getWindow().getScaledHeight() - 4) textY = hY - 10f;

                int textCol = dark ? (((int) (alpha * 240) << 24) | 0xEEEEEE) : (((int) (alpha * 240) << 24) | 0x1E1E24);
                SFUI.draw(context.getMatrices(), tipText, textX, textY, tipFontSize, textCol);
            }
        }
    }

    private void renderHudDragVisuals(DrawContext context, float extinguish) {
        if (dragVisualAlpha <= 0.01f || draggingHudName == null) return;
        float alpha = Math.max(0f, Math.min(1f, dragVisualAlpha));
        int whiteWithAlpha = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        float curX = dragTargetX, curY = dragTargetY, w = dragHudWidth, h = dragHudHeight;
        HudFireShader.drawFireAura(context, curX, curY, w, h, 6f, 14f, whiteWithAlpha, alpha * 1.30f, extinguish);
    }

    private boolean isClickInsideParticleDropdown(int mx, int my) {
        if (!"Target HUD".equals(targetEditingHud)) return false;
        if (!LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true)) return false;
        return TargetHudRenderer.hitTestParticleSelector(mx, my, pX + 7, pY + 84, pW - 14, particleDropdownOpen) != Integer.MIN_VALUE;
    }

    private void clampPanelToScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth(), screenH = mc.getWindow().getScaledHeight();
        int margin = 6;
        pX = Math.max(margin, Math.min(pX, screenW - pW - margin));
        pY = Math.max(margin, Math.min(pY, screenH - pH - margin));
    }

    private int computeTargetHudPanelHeight() {
        boolean particlesOn = LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true);
        return particlesOn ? 142 : 118;
    }

    private boolean isHudWithHomeIndicator(String hud) {
        return hud != null && (hud.equals("Keybinds") || hud.equals("Armor Status") || hud.equals("Inventory HUD") || hud.equals("Cooldowns") || hud.equals("Potions"));
    }

    private void drawBgSwitcher(DrawContext context, String label, String key, int y, int alphaInt, boolean dark) {
        int labelColor = dark ? ((alphaInt << 24) | 0xEEEEEE) : ((alphaInt << 24) | 0x1E1E24);
        SFUI.draw(context.getMatrices(), label, pX + 7, y + 2, 7.5f, labelColor);
        boolean isBlur = LexoraGui.moduleStates.getOrDefault(key, false);

        float boxW = 76f, boxH = 14f;
        float boxX = pX + pW - 7f - boxW, boxY = y - 0.5f;
        int trackBg = dark ? ((alphaInt << 24) | 0x1c1c20) : ((alphaInt << 24) | 0xDCDEE6);
        RoundedRectShader.draw(context, boxX, boxY, boxW, boxH, 4.5f, trackBg);

        float pillW = 36f, pillH = 11.5f;
        float targetPos = isBlur ? 1.0f : 0.0f;
        float curPos = switcherAnimations.getOrDefault(key, targetPos);
        curPos += (targetPos - curPos) * 0.28f;
        if (Math.abs(curPos - targetPos) < 0.005f) curPos = targetPos;
        switcherAnimations.put(key, curPos);

        float pillMinX = boxX + 1.25f, pillMaxX = boxX + boxW - pillW - 1.25f;
        float pillX = pillMinX + (pillMaxX - pillMinX) * curPos;
        float pillY = boxY + 1.25f;
        int activeBg = dark ? 0xFFFFFF : 0x111116;
        RoundedRectShader.draw(context, pillX, pillY, pillW, pillH, 3.5f, (alphaInt << 24) | activeBg);

        // Текст "Стандарт"
        float w1 = SFUI.getWidth("Стандарт", 5.8f);
        float t1X = pillMinX + (pillW - w1) / 2f, t1Y = boxY + 2.5f;
        int c1Active = dark ? 0x0C0C0E : 0xFFFFFF, c1Inactive = dark ? 0x888890 : 0x6B6E78;
        int col1 = lerpColor(c1Active, c1Inactive, curPos);
        SFUI.draw(context.getMatrices(), "Стандарт", t1X, t1Y, 5.8f, (alphaInt << 24) | col1);

        // Текст "Блюр"
        float w2 = SFUI.getWidth("Блюр", 5.8f);
        float t2X = pillMaxX + (pillW - w2) / 2f, t2Y = boxY + 2.5f;
        int c2Active = dark ? 0x0C0C0E : 0xFFFFFF, c2Inactive = dark ? 0x888890 : 0x6B6E78;
        int col2 = lerpColor(c2Inactive, c2Active, curPos);
        SFUI.draw(context.getMatrices(), "Блюр", t2X, t2Y, 5.8f, (alphaInt << 24) | col2);
    }

    private boolean handleSwitcherClick(int mx, int my, String key, int y) {
        float boxW = 76f, boxH = 14f;
        float boxX = pX + pW - 7f - boxW, boxY = y - 0.5f;
        if (mx >= boxX && mx <= boxX + boxW / 2f && my >= boxY && my <= boxY + boxH) { LexoraGui.moduleStates.put(key, false); return true; }
        if (mx > boxX + boxW / 2f && mx <= boxX + boxW && my >= boxY && my <= boxY + boxH) { LexoraGui.moduleStates.put(key, true); return true; }
        return false;
    }

    private void drawInfoToggle(DrawContext context, String label, String key, int y, int alphaInt, boolean dark) {
        boolean enabled = LexoraGui.moduleStates.getOrDefault(key, getInfoDefault(key));
        drawToggleVisual(context, label, key, enabled, y, alphaInt, dark);
    }

    private void drawToggle(DrawContext context, String label, String key, int y, int alphaInt, boolean dark) {
        boolean enabled = LexoraGui.moduleStates.getOrDefault(key, false);
        drawToggleVisual(context, label, key, enabled, y, alphaInt, dark);
    }

    private void drawToggleDefaultTrue(DrawContext context, String label, String key, int y, int alphaInt, boolean dark) {
        boolean enabled = LexoraGui.moduleStates.getOrDefault(key, true);
        drawToggleVisual(context, label, key, enabled, y, alphaInt, dark);
    }

    private void drawToggleVisual(DrawContext context, String label, String key, boolean enabled, int y, int alphaInt, boolean dark) {
        int labelColor = dark ? ((alphaInt << 24) | 0xEEEEEE) : ((alphaInt << 24) | 0x1E1E24);
        SFUI.draw(context.getMatrices(), label, pX + 7, y + 2, 7.5f, labelColor);
        float target = enabled ? 1.0f : 0.0f;
        float current = toggleAnimations.getOrDefault(key, target);
        current += (target - current) * 0.28f;
        if (Math.abs(current - target) < 0.01f) current = target;
        toggleAnimations.put(key, current);

        int offColor = dark ? 0x232328 : 0xD0D3DC;
        int onColor = dark ? 0xFFFFFF : 0x111116;
        int bgColor = lerpColor(offColor, onColor, current);

        int switchW = 22, switchH = 12, switchX = pX + pW - 7 - switchW, switchY = y;
        RoundedRectShader.draw(context, switchX, switchY, switchW, switchH, 6f, (alphaInt << 24) | bgColor);

        int knobOffColor = 0x888890;
        int knobOnColor = dark ? 0x111114 : 0xFFFFFF;
        int knobColor = lerpColor(knobOffColor, knobOnColor, current);
        int knobOffX = switchX + 2, knobOnX = switchX + switchW - 10, knobX = Math.round(knobOffX + (knobOnX - knobOffX) * current);
        RoundedRectShader.draw(context, knobX, switchY + 2, 8, 8, 4f, (alphaInt << 24) | knobColor);
    }

    private void drawScaleSlider(DrawContext context, float scale, int y, int alphaInt, boolean dark) {
        int labelColor = dark ? ((alphaInt << 24) | 0xEEEEEE) : ((alphaInt << 24) | 0x1E1E24);
        SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", scale), pX + 7, y - 8, 7.5f, labelColor);
        int sliderW = pW - 14, sliderX = pX + 7, sliderY = y + 4;
        int trackBg = dark ? 0x222226 : 0xD2D6E0;
        RoundedRectShader.draw(context, sliderX, sliderY, sliderW, 3, 1.5f, (alphaInt << 24) | trackBg);
        float pct = Math.max(0f, Math.min(1f, (scale - 0.5f) / 1.5f));
        int thumbX = sliderX + (int) (pct * sliderW);
        int fillCol = dark ? 0xFFFFFF : 0x111116;
        RoundedRectShader.draw(context, sliderX, sliderY, Math.max(2, thumbX - sliderX), 3, 1.5f, (alphaInt << 24) | fillCol);
        RoundedRectShader.draw(context, thumbX - 2.5f, sliderY - 3.5f, 5, 10, 2.5f, (alphaInt << 24) | (dark ? 0xFFFFFF : 0x111116));
    }

    private boolean getInfoDefault(String key) {
        return switch (key) {
            case "Info Magnetic Snap", "Info Show Logo", "Info Show Name", "Info Show UUID", "Info Show FPS", "Info Show Coords", "Info Show BPS" -> true;
            case "Info HUD Blur", "Info Show TPS", "Info Show Server" -> false;
            default -> false;
        };
    }

    private void toggle(String key) {
        boolean currentState = LexoraGui.moduleStates.getOrDefault(key, false);
        LexoraGui.moduleStates.put(key, !currentState);
    }

    private void toggleDefaultTrue(String key) {
        boolean currentState = LexoraGui.moduleStates.getOrDefault(key, true);
        LexoraGui.moduleStates.put(key, !currentState);
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int lerpColor(int from, int to, float progress) {
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = (int) (fr + (tr - fr) * progress);
        int g = (int) (fg + (tg - fg) * progress);
        int b = (int) (fb + (tb - fb) * progress);
        return (r << 16) | (g << 8) | b;
    }
}
