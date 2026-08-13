package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.events.*;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
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
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    private String editingHud = null;
    private String targetEditingHud = null;
    private float panelAnim = 0f;

    private int pX = 0;
    private int pY = 0;
    private int pW = 140;
    private int pH = 90;

    private boolean draggingScale = false;
    private boolean particleDropdownOpen = false; // открыт ли список выбора партикла (Target HUD)

    private boolean draggingHudPos = false;
    private String draggingHudName = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    protected MixinChatScreen(Text title) {
        super(title);
    }

    @Unique
    private int getTntX() {
        try {
            return HudManager.class.getField("tntX").getInt(null);
        } catch (Exception e) {
            return 10;
        }
    }

    @Unique
    private int getTntY() {
        try {
            return HudManager.class.getField("tntY").getInt(null);
        } catch (Exception e) {
            return 160;
        }
    }

    @Unique
    private void setTntPos(int x, int y) {
        try {
            HudManager.class.getField("tntX").setInt(null, x);
            HudManager.class.getField("tntY").setInt(null, y);
        } catch (Exception ignored) {}
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onHudSettingsClick(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir
    ) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (targetEditingHud != null && panelAnim > 0.1f) {
            if (!isClickInsideParticleDropdown(mx, my)
                    && (mx < pX - 5 || mx > pX + pW + 5 || my < pY - 5 || my > pY + pH + 5)) {
                targetEditingHud = null;
                particleDropdownOpen = false;
                cir.setReturnValue(true);
                return;
            }

            if (mx >= pX + pW - 24 && mx <= pX + pW && my >= pY && my <= pY + 24) {
                targetEditingHud = null;
                particleDropdownOpen = false;
                cir.setReturnValue(true);
                return;
            }

            if (targetEditingHud.equals("Info HUD")) {
                if (handleSwitcherClick(mx, my, "Info HUD Blur", pY + 30)) {
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 45, pW - 20, 18)) {
                    toggleDefaultTrue("Info Magnetic Snap");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 65, pW - 20, 18)) {
                    toggleDefaultTrue("Info Show Logo");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 85, pW - 20, 18)) {
                    toggleDefaultTrue("Info Show Name");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 105, pW - 20, 18)) {
                    toggleDefaultTrue("Info Show UUID");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 125, pW - 20, 18)) {
                    toggleDefaultTrue("Info Show FPS");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 145, pW - 20, 18)) {
                    toggle("Info Show TPS");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 165, pW - 20, 18)) {
                    toggleDefaultTrue("Info Show Coords");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 185, pW - 20, 18)) {
                    toggleDefaultTrue("Info Show BPS");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 205, pW - 20, 18)) {
                    toggle("Info Show Server");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 230, pW - 20, 20)) {
                    draggingScale = true;
                    cir.setReturnValue(true);
                    return;
                }

                cir.setReturnValue(true);
                return;
            } else if (targetEditingHud.equals("Watermark")) {
                if (handleSwitcherClick(mx, my, "Watermark Blur", pY + 30)) {
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 55, pW - 20, 20)) {
                    toggleDefaultTrue("Notif Swap");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 75, pW - 20, 20)) {
                    toggleDefaultTrue("Notif HP");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 95, pW - 20, 20)) {
                    toggleDefaultTrue("Notif Armor");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 115, pW - 20, 20)) {
                    toggleDefaultTrue("Notif Potions");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 140, pW - 20, 20)) {
                    draggingScale = true;
                    cir.setReturnValue(true);
                    return;
                }

                cir.setReturnValue(true);
                return;
            } else if (targetEditingHud.equals("Music Hud")) {
                if (inside(mx, my, pX + 10, pY + 25, pW - 20, 20)) {
                    toggleDefaultTrue("Music Hud Controls");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 55, pW - 20, 20)) {
                    draggingScale = true;
                    cir.setReturnValue(true);
                    return;
                }

                cir.setReturnValue(true);
                return;
            } else if (targetEditingHud.equals("Target HUD")) {
                if (handleSwitcherClick(mx, my, "Target HUD Blur", pY + 30)) {
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 50, pW - 20, 20)) {
                    boolean currentState = LexoraGui.moduleStates.getOrDefault("Damage Tint", true);
                    LexoraGui.moduleStates.put("Damage Tint", !currentState);
                    cir.setReturnValue(true);
                    return;
                }

                // Частицы: тумблер
                if (inside(mx, my, pX + 10, pY + 75, pW - 20, 20)) {
                    boolean currentState = LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true);
                    LexoraGui.moduleStates.put("Target HUD Particles", !currentState);
                    particleDropdownOpen = false;
                    pH = computeTargetHudPanelHeight();
                    cir.setReturnValue(true);
                    return;
                }

                boolean particlesOn = LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true);

                // Частицы: клик по полю выбора / по открытому списку вариантов
                if (particlesOn) {
                    int hit = TargetHudRenderer.hitTestParticleSelector(
                            mx, my, pX + 10, pY + 105, pW - 20, particleDropdownOpen);
                    if (hit != Integer.MIN_VALUE) {
                        if (hit == -2) {
                            particleDropdownOpen = !particleDropdownOpen;
                        } else if (hit >= 0) {
                            LexoraGui.numSettings.put("Target HUD Particle Type", (float) hit);
                            particleDropdownOpen = false;
                        } else {
                            particleDropdownOpen = false; // клик мимо списка — просто закрыть
                        }
                        cir.setReturnValue(true);
                        return;
                    }
                }

                int scaleDragY = pY + (particlesOn ? 125 : 95);
                if (inside(mx, my, pX + 10, scaleDragY, pW - 20, 30)) {
                    draggingScale = true;
                    cir.setReturnValue(true);
                    return;
                }

                cir.setReturnValue(true);
                return;
            } else if (targetEditingHud.equals("TNT Detect")) {
                if (handleSwitcherClick(mx, my, "TNT Detect Blur", pY + 30)) {
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 60, pW - 20, 20)) {
                    draggingScale = true;
                    cir.setReturnValue(true);
                    return;
                }

                cir.setReturnValue(true);
                return;
            } else if (targetEditingHud.equals("Scoreboard HUD")) {
                if (handleSwitcherClick(mx, my, "Scoreboard HUD Blur", pY + 30)) {
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 50, pW - 20, 18)) {
                    toggleDefaultTrue("Scoreboard HUD Home Indicator");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 75, pW - 20, 20)) {
                    draggingScale = true;
                    cir.setReturnValue(true);
                    return;
                }

                cir.setReturnValue(true);
                return;
            } else if (isHudWithHomeIndicator(targetEditingHud)) {
                if (handleSwitcherClick(mx, my, targetEditingHud + " Blur", pY + 30)) {
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 50, pW - 20, 20)) {
                    toggleDefaultTrue(targetEditingHud + " Home Indicator");
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 80, pW - 20, 20)) {
                    draggingScale = true;
                    cir.setReturnValue(true);
                    return;
                }

                cir.setReturnValue(true);
                return;
            } else {
                if (handleSwitcherClick(mx, my, targetEditingHud + " Blur", pY + 30)) {
                    cir.setReturnValue(true);
                    return;
                }

                if (inside(mx, my, pX + 10, pY + 60, pW - 20, 20)) {
                    draggingScale = true;
                    cir.setReturnValue(true);
                    return;
                }

                cir.setReturnValue(true);
                return;
            }
        }

        if (button == 1 && targetEditingHud == null) {
            MinecraftClient mc = MinecraftClient.getInstance();

            float infoScale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f);
            int infoBaseX = com.lexoravisauls.client.events.InfoHudRenderer.getBaseX(mc);
            int infoBaseY = com.lexoravisauls.client.events.InfoHudRenderer.getBaseY(mc);

            if (LexoraGui.moduleStates.getOrDefault("Info HUD", true)
                    && mx >= infoBaseX
                    && mx <= infoBaseX + com.lexoravisauls.client.events.InfoHudRenderer.WIDTH * infoScale
                    && my >= infoBaseY
                    && my <= infoBaseY + com.lexoravisauls.client.events.InfoHudRenderer.HEIGHT * infoScale) {
                targetEditingHud = "Info HUD";
                editingHud = "Info HUD";

                pW = 165;
                pH = 255;
                pX = mx + 10;
                pY = my + 10;
                clampPanelToScreen();

                cir.setReturnValue(true);
                return;
            }

            int wX = DynamicIslandRenderer.islandX;
            int wY = DynamicIslandRenderer.islandY;

            if (LexoraGui.moduleStates.getOrDefault("Watermark", true)
                    && mx >= wX && mx <= wX + DynamicIslandRenderer.islandW
                    && my >= wY && my <= wY + DynamicIslandRenderer.islandH) {
                targetEditingHud = "Watermark";
                editingHud = "Watermark";
                pW = 155;
                pH = 175; // Увеличен размер меню для настроек уведомлений
                pX = mx + 10;
                pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }

            float targetScale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            int tX = HudManager.targetX == -1 ? 150 : HudManager.targetX;
            int tY = HudManager.targetY == -1 ? 150 : HudManager.targetY;
            if (LexoraGui.moduleStates.getOrDefault("Target HUD", true)
                    && mx >= tX && mx <= tX + com.lexoravisauls.client.events.TargetHudRenderer.WIDTH * targetScale
                    && my >= tY && my <= tY + com.lexoravisauls.client.events.TargetHudRenderer.HEIGHT * targetScale) {
                targetEditingHud = "Target HUD";
                editingHud = "Target HUD";
                pW = 140; pH = computeTargetHudPanelHeight(); pX = mx + 10; pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }

            // TNT Detect (ПКМ клик)
            float tntScale = LexoraGui.numSettings.getOrDefault("TNT Detect Scale", 1.0f);
            int tntX = getTntX();
            int tntY = getTntY();
            if (LexoraGui.moduleStates.getOrDefault("TNT Detect", true)
                    && mx >= tntX && mx <= tntX + TntHudRenderer.WIDTH * tntScale
                    && my >= tntY && my <= tntY + TntHudRenderer.HEIGHT * tntScale) {
                targetEditingHud = "TNT Detect";
                editingHud = "TNT Detect";
                pW = 155; pH = 100; pX = mx + 10; pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }

            float keyScale = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
            if (LexoraGui.moduleStates.getOrDefault("Keybinds", false)
                    && mx >= HudManager.keybindsX && mx <= HudManager.keybindsX + ExtraHudsRenderer.keybindsW * keyScale
                    && my >= HudManager.keybindsY && my <= HudManager.keybindsY + ExtraHudsRenderer.keybindsH * keyScale) {
                targetEditingHud = "Keybinds";
                editingHud = "Keybinds";
                pW = 155; pH = 120; pX = mx + 10; pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }

            float armorScale = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
            if (LexoraGui.moduleStates.getOrDefault("Armor Status", false)
                    && mx >= HudManager.armorX && mx <= HudManager.armorX + ExtraHudsRenderer.armorW * armorScale
                    && my >= HudManager.armorY && my <= HudManager.armorY + ExtraHudsRenderer.armorH * armorScale) {
                targetEditingHud = "Armor Status";
                editingHud = "Armor Status";
                pW = 155; pH = 120; pX = mx + 10; pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }

            float invScale = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
            if (LexoraGui.moduleStates.getOrDefault("Inventory HUD", false)
                    && mx >= HudManager.invX && mx <= HudManager.invX + ExtraHudsRenderer.invW * invScale
                    && my >= HudManager.invY && my <= HudManager.invY + ExtraHudsRenderer.invH * invScale) {
                targetEditingHud = "Inventory HUD";
                editingHud = "Inventory HUD";
                pW = 155; pH = 120; pX = mx + 10; pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }

            float coolScale = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
            if (LexoraGui.moduleStates.getOrDefault("Cooldowns", false)
                    && mx >= HudManager.coolX && mx <= HudManager.coolX + ExtraHudsRenderer.coolW * coolScale
                    && my >= HudManager.coolY && my <= HudManager.coolY + ExtraHudsRenderer.coolH * coolScale) {
                targetEditingHud = "Cooldowns";
                editingHud = "Cooldowns";
                pW = 155; pH = 120; pX = mx + 10; pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }

            float potionScale = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
            int potionX = HudManager.potionX == -1 ? 10 : HudManager.potionX;
            int potionY = HudManager.potionY == -1 ? 100 : HudManager.potionY;
            if (LexoraGui.moduleStates.getOrDefault("Potions", true)
                    && mx >= potionX && mx <= potionX + PotionHudRenderer.WIDTH * potionScale
                    && my >= potionY && my <= potionY + PotionHudRenderer.HEIGHT * potionScale) {
                targetEditingHud = "Potions";
                editingHud = "Potions";
                pW = 155; pH = 120; pX = mx + 10; pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }

            int sbX = ScoreboardHudRenderer.lastRenderedX;
            int sbY = ScoreboardHudRenderer.lastRenderedY;
            int sbW = ScoreboardHudRenderer.lastRenderedW;
            int sbH = ScoreboardHudRenderer.lastRenderedH;
            if (LexoraGui.moduleStates.getOrDefault("Scoreboard HUD", false)
                    && sbW > 0
                    && mx >= sbX && mx <= sbX + sbW
                    && my >= sbY && my <= sbY + sbH) {
                targetEditingHud = "Scoreboard HUD";
                editingHud = "Scoreboard HUD";
                pW = 155; pH = 105; pX = mx + 10; pY = my + 10;
                clampPanelToScreen();
                cir.setReturnValue(true);
                return;
            }
        }

        // ЛКМ — начало перетаскивания позиции HUD'а
        if (button == 0 && targetEditingHud == null) {
            MinecraftClient mc = MinecraftClient.getInstance();

            // Info HUD
            float infoScale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f);
            int infoBaseX = com.lexoravisauls.client.events.InfoHudRenderer.getBaseX(mc);
            int infoBaseY = com.lexoravisauls.client.events.InfoHudRenderer.getBaseY(mc);
            if (LexoraGui.moduleStates.getOrDefault("Info HUD", true)
                    && mx >= infoBaseX
                    && mx <= infoBaseX + com.lexoravisauls.client.events.InfoHudRenderer.WIDTH * infoScale
                    && my >= infoBaseY
                    && my <= infoBaseY + com.lexoravisauls.client.events.InfoHudRenderer.HEIGHT * infoScale) {
                draggingHudPos = true;
                draggingHudName = "Info HUD";
                dragOffsetX = mx - infoBaseX;
                dragOffsetY = my - infoBaseY;
                cir.setReturnValue(true);
                return;
            }

            // Target HUD
            float targetScale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            int tX = HudManager.targetX == -1 ? 150 : HudManager.targetX;
            int tY = HudManager.targetY == -1 ? 150 : HudManager.targetY;
            if (LexoraGui.moduleStates.getOrDefault("Target HUD", true)
                    && mx >= tX && mx <= tX + com.lexoravisauls.client.events.TargetHudRenderer.WIDTH * targetScale
                    && my >= tY && my <= tY + com.lexoravisauls.client.events.TargetHudRenderer.HEIGHT * targetScale) {
                draggingHudPos = true;
                draggingHudName = "Target HUD";
                dragOffsetX = mx - tX;
                dragOffsetY = my - tY;
                cir.setReturnValue(true);
                return;
            }

            // TNT Detect (ЛКМ перетаскивание)
            float tntScale = LexoraGui.numSettings.getOrDefault("TNT Detect Scale", 1.0f);
            int tntX = getTntX();
            int tntY = getTntY();
            if (LexoraGui.moduleStates.getOrDefault("TNT Detect", true)
                    && mx >= tntX && mx <= tntX + TntHudRenderer.WIDTH * tntScale
                    && my >= tntY && my <= tntY + TntHudRenderer.HEIGHT * tntScale) {
                draggingHudPos = true;
                draggingHudName = "TNT Detect";
                dragOffsetX = mx - tntX;
                dragOffsetY = my - tntY;
                cir.setReturnValue(true);
                return;
            }

            // Keybinds
            float keyScale = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
            if (LexoraGui.moduleStates.getOrDefault("Keybinds", false)
                    && mx >= HudManager.keybindsX && mx <= HudManager.keybindsX + ExtraHudsRenderer.keybindsW * keyScale
                    && my >= HudManager.keybindsY && my <= HudManager.keybindsY + ExtraHudsRenderer.keybindsH * keyScale) {
                draggingHudPos = true;
                draggingHudName = "Keybinds";
                dragOffsetX = mx - HudManager.keybindsX;
                dragOffsetY = my - HudManager.keybindsY;
                cir.setReturnValue(true);
                return;
            }

            // Armor Status
            float armorScale = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
            if (LexoraGui.moduleStates.getOrDefault("Armor Status", false)
                    && mx >= HudManager.armorX && mx <= HudManager.armorX + ExtraHudsRenderer.armorW * armorScale
                    && my >= HudManager.armorY && my <= HudManager.armorY + ExtraHudsRenderer.armorH * armorScale) {
                draggingHudPos = true;
                draggingHudName = "Armor Status";
                dragOffsetX = mx - HudManager.armorX;
                dragOffsetY = my - HudManager.armorY;
                cir.setReturnValue(true);
                return;
            }

            // Inventory HUD
            float invScale = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
            if (LexoraGui.moduleStates.getOrDefault("Inventory HUD", false)
                    && mx >= HudManager.invX && mx <= HudManager.invX + ExtraHudsRenderer.invW * invScale
                    && my >= HudManager.invY && my <= HudManager.invY + ExtraHudsRenderer.invH * invScale) {
                draggingHudPos = true;
                draggingHudName = "Inventory HUD";
                dragOffsetX = mx - HudManager.invX;
                dragOffsetY = my - HudManager.invY;
                cir.setReturnValue(true);
                return;
            }

            // Cooldowns
            float coolScale = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
            if (LexoraGui.moduleStates.getOrDefault("Cooldowns", false)
                    && mx >= HudManager.coolX && mx <= HudManager.coolX + ExtraHudsRenderer.coolW * coolScale
                    && my >= HudManager.coolY && my <= HudManager.coolY + ExtraHudsRenderer.coolH * coolScale) {
                draggingHudPos = true;
                draggingHudName = "Cooldowns";
                dragOffsetX = mx - HudManager.coolX;
                dragOffsetY = my - HudManager.coolY;
                cir.setReturnValue(true);
                return;
            }

            // Potions
            float potionScale2 = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
            int potionX2 = HudManager.potionX == -1 ? 10 : HudManager.potionX;
            int potionY2 = HudManager.potionY == -1 ? 100 : HudManager.potionY;
            if (LexoraGui.moduleStates.getOrDefault("Potions", true)
                    && mx >= potionX2 && mx <= potionX2 + PotionHudRenderer.WIDTH * potionScale2
                    && my >= potionY2 && my <= potionY2 + PotionHudRenderer.HEIGHT * potionScale2) {
                draggingHudPos = true;
                draggingHudName = "Potions";
                dragOffsetX = mx - potionX2;
                dragOffsetY = my - potionY2;
                cir.setReturnValue(true);
                return;
            }

            // Scoreboard HUD — ФИКС: инициализируем реальные координаты из дефолта если -1
            int sbX2 = ScoreboardHudRenderer.lastRenderedX;
            int sbY2 = ScoreboardHudRenderer.lastRenderedY;
            int sbW2 = ScoreboardHudRenderer.lastRenderedW;
            int sbH2 = ScoreboardHudRenderer.lastRenderedH;
            if (LexoraGui.moduleStates.getOrDefault("Scoreboard HUD", false)
                    && sbW2 > 0
                    && mx >= sbX2 && mx <= sbX2 + sbW2
                    && my >= sbY2 && my <= sbY2 + sbH2) {
                HudManager.scoreboardX = sbX2;
                HudManager.scoreboardY = sbY2;
                draggingHudPos = true;
                draggingHudName = "Scoreboard HUD";
                dragOffsetX = mx - sbX2;
                dragOffsetY = my - sbY2;
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onHudSettingsRender(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        float targetAnim = targetEditingHud != null ? 1.0f : 0.0f;
        panelAnim += (targetAnim - panelAnim) * 0.15f;

        if (panelAnim < 0.01f) {
            editingHud = null;
            return;
        }

        clampPanelToScreen();

        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();

        if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_RELEASE) {
            draggingScale = false;
            draggingHudPos = false;
        }

        // Обновляем позицию перетаскиваемого HUD'а каждый кадр
        if (draggingHudPos && draggingHudName != null) {
            MinecraftClient mc2 = MinecraftClient.getInstance();
            int newX = mouseX - dragOffsetX;
            int newY = mouseY - dragOffsetY;
            switch (draggingHudName) {
                case "Scoreboard HUD" -> {
                    HudManager.scoreboardX = newX;
                    HudManager.scoreboardY = newY;
                }
                case "Target HUD" -> {
                    HudManager.targetX = newX;
                    HudManager.targetY = newY;
                }
                case "TNT Detect" -> setTntPos(newX, newY);
                case "Keybinds" -> {
                    HudManager.keybindsX = newX;
                    HudManager.keybindsY = newY;
                }
                case "Armor Status" -> {
                    HudManager.armorX = newX;
                    HudManager.armorY = newY;
                }
                case "Inventory HUD" -> {
                    HudManager.invX = newX;
                    HudManager.invY = newY;
                }
                case "Cooldowns" -> {
                    HudManager.coolX = newX;
                    HudManager.coolY = newY;
                }
                case "Potions" -> {
                    HudManager.potionX = newX;
                    HudManager.potionY = newY;
                }
            }
        }

        if (draggingScale && targetEditingHud != null) {
            float percent = Math.max(0f, Math.min(1f, (mouseX - (pX + 10)) / (float) (pW - 20)));
            float newScale = 0.5f + percent * 1.5f;

            String scaleKey = targetEditingHud + " Scale";
            LexoraGui.numSettings.put(scaleKey, (float) (Math.round(newScale * 100.0) / 100.0));
        }

        context.getMatrices().push();
        context.getMatrices().translate(pX + pW / 2f, pY + pH / 2f, 0);
        context.getMatrices().scale(panelAnim, panelAnim, 1.0f);
        context.getMatrices().translate(-(pX + pW / 2f), -(pY + pH / 2f), 0);

        int alphaInt = Math.max(5, Math.min(255, (int) (panelAnim * 255)));

        RoundedRectShader.draw(context, pX, pY, pW, pH, 6f, ((int) (panelAnim * 245) << 24) | 0x141416);

        String displayName = editingHud.equals("Watermark") ? "Dynamic Island" : editingHud + " Settings";
        SFUI.draw(context.getMatrices(), displayName, pX + 8, pY + 10, 8.5f, (alphaInt << 24) | 0xFFFFFF);

        boolean closeHover = mouseX >= pX + pW - 24 && mouseX <= pX + pW && mouseY >= pY && mouseY <= pY + 24;
        int closeColor = closeHover ? ((alphaInt << 24) | 0xFF5555) : ((alphaInt << 24) | 0x888888);
        SFUI.draw(context.getMatrices(), "X", pX + pW - 16, pY + 10, 9f, closeColor);

        if (editingHud.equals("Info HUD")) {
            drawBgSwitcher(context, "Фон", "Info HUD Blur", pY + 30, alphaInt);
            drawInfoToggle(context, "Магнит", "Info Magnetic Snap", pY + 50, alphaInt);
            drawInfoToggle(context, "Лого", "Info Show Logo", pY + 70, alphaInt);
            drawInfoToggle(context, "Ник", "Info Show Name", pY + 90, alphaInt);
            drawInfoToggle(context, "Показывать UUID", "Info Show UUID", pY + 110, alphaInt);
            drawInfoToggle(context, "FPS", "Info Show FPS", pY + 130, alphaInt);
            drawInfoToggle(context, "TPS", "Info Show TPS", pY + 150, alphaInt);
            drawInfoToggle(context, "Координаты", "Info Show Coords", pY + 170, alphaInt);
            drawInfoToggle(context, "Скорость b/s", "Info Show BPS", pY + 190, alphaInt);
            drawInfoToggle(context, "Сервер", "Info Show Server", pY + 210, alphaInt);

            float scale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f);
            SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", scale), pX + 8, pY + 233, 8.5f, (alphaInt << 24) | 0xDDDDDD);
            drawScaleSlider(context, scale, pY + 245, alphaInt);

        } else if (editingHud.equals("Watermark")) {
            drawBgSwitcher(context, "Фон", "Watermark Blur", pY + 30, alphaInt);
            drawToggleDefaultTrue(context, "Увед. о свапе", "Notif Swap", pY + 55, alphaInt);
            drawToggleDefaultTrue(context, "Увед. ХП", "Notif HP", pY + 75, alphaInt);
            drawToggleDefaultTrue(context, "Увед. Броня", "Notif Armor", pY + 95, alphaInt);
            drawToggleDefaultTrue(context, "Увед. Зелья", "Notif Potions", pY + 115, alphaInt);
            float scale = LexoraGui.numSettings.getOrDefault("Watermark Scale", 1.0f);
            SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", scale), pX + 8, pY + 140, 8.5f, (alphaInt << 24) | 0xDDDDDD);
            drawScaleSlider(context, scale, pY + 156, alphaInt);

        } else if (editingHud.equals("Music Hud")) {
            drawToggleDefaultTrue(context, "Кнопки управления", "Music Hud Controls", pY + 30, alphaInt);
            float scale = LexoraGui.numSettings.getOrDefault("Music Hud Scale", 1.0f);
            SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", scale), pX + 8, pY + 55, 8.5f, (alphaInt << 24) | 0xDDDDDD);
            drawScaleSlider(context, scale, pY + 71, alphaInt);

        } else if (editingHud.equals("Target HUD")) {
            drawBgSwitcher(context, "Фон", "Target HUD Blur", pY + 30, alphaInt);
            drawToggleDefaultTrue(context, "Урон красным", "Damage Tint", pY + 55, alphaInt);

            boolean particlesOn = LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true);
            drawToggleDefaultTrue(context, "Частицы", "Target HUD Particles", pY + 80, alphaInt);

            int scaleBase = pY + (particlesOn ? 125 : 95);
            float scale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", scale), pX + 8, scaleBase + 5, 8.5f, (alphaInt << 24) | 0xDDDDDD);
            drawScaleSlider(context, scale, scaleBase + 21, alphaInt);

            // Список партиклов рисуем ПОСЛЕДНИМ: если он открыт, он как отдельный
            // плавающий виджет ложится поверх шкалы Scale, а не наоборот.
            // Сама панель под него не раздвигается (см. computeTargetHudPanelHeight).
            if (particlesOn) {
                TargetHudRenderer.renderParticleSelector(
                        context, pX + 10, pY + 105, pW - 20, particleDropdownOpen, mouseX, mouseY);
            }

            pH = computeTargetHudPanelHeight();

        } else if (editingHud.equals("TNT Detect")) {
            drawBgSwitcher(context, "Фон", "TNT Detect Blur", pY + 30, alphaInt);
            float scale = LexoraGui.numSettings.getOrDefault("TNT Detect Scale", 1.0f);
            SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", scale), pX + 8, pY + 52, 8.5f, (alphaInt << 24) | 0xDDDDDD);
            drawScaleSlider(context, scale, pY + 68, alphaInt);

        } else if (editingHud.equals("Scoreboard HUD")) {
            // BUG FIX #4: отдельный render-блок для Scoreboard HUD
            drawBgSwitcher(context, "Фон", "Scoreboard HUD Blur", pY + 30, alphaInt);
            drawToggleDefaultTrue(context, "Полоска снизу", "Scoreboard HUD Home Indicator", pY + 55, alphaInt);
            float sbScale = LexoraGui.numSettings.getOrDefault("Scoreboard HUD Scale", 1.0f);
            SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", sbScale), pX + 8, pY + 80, 8.5f, (alphaInt << 24) | 0xDDDDDD);
            drawScaleSlider(context, sbScale, pY + 96, alphaInt);

        } else if (isHudWithHomeIndicator(editingHud)) {
            String blurKey = editingHud + " Blur";
            String homeKey = editingHud + " Home Indicator";
            String scaleKey = editingHud + " Scale";

            drawBgSwitcher(context, "Фон", blurKey, pY + 30, alphaInt);
            drawToggleDefaultTrue(context, "Полоска снизу", homeKey, pY + 55, alphaInt);
            float scale = LexoraGui.numSettings.getOrDefault(scaleKey, 1.0f);
            SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", scale), pX + 8, pY + 82, 8.5f, (alphaInt << 24) | 0xDDDDDD);
            drawScaleSlider(context, scale, pY + 98, alphaInt);

        } else {
            String blurKey = editingHud + " Blur";
            String scaleKey = editingHud + " Scale";

            drawBgSwitcher(context, "Фон", blurKey, pY + 30, alphaInt);
            float scale = LexoraGui.numSettings.getOrDefault(scaleKey, 1.0f);
            SFUI.draw(context.getMatrices(), "Масштаб: " + String.format(Locale.US, "%.2f", scale), pX + 8, pY + 52, 8.5f, (alphaInt << 24) | 0xDDDDDD);
            drawScaleSlider(context, scale, pY + 68, alphaInt);
        }

        context.getMatrices().pop();
    }

    /**
     * Высота панели настроек "Target HUD". Базово хватает на Blur/Damage Tint/
     * Частицы-тумблер/Scale. Если партиклы включены — плюс поле выбора, и если
     * список ещё и раскрыт — плюс место под все 11 вариантов.
     */
    /**
     * Не даёт панели Target HUD вылезти за границы экрана — если из-за раскрытого
     * списка партиклов (или просто открытия у самого края) панель не помещается,
     * подтягивает pX/pY внутрь видимой области (обычно "поднимает" её вверх).
     */
    /**
     * true, если клик попадает в виджет выбора партикла (поле или открытый список),
     * даже если тот сейчас визуально выходит за пределы pH основной панели — список
     * открывается как отдельный плавающий виджет и сам решает, вверх ему открыться
     * или вниз (см. TargetHudRenderer.renderParticleSelector).
     */
    private boolean isClickInsideParticleDropdown(int mx, int my) {
        if (!"Target HUD".equals(targetEditingHud)) return false;
        if (!LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true)) return false;
        return TargetHudRenderer.hitTestParticleSelector(
                mx, my, pX + 10, pY + 105, pW - 20, particleDropdownOpen) != Integer.MIN_VALUE;
    }

    /**
     * Не даёт ЛЮБОЙ открытой панели настроек HUD'а вылезти за границы экрана —
     * подтягивает pX/pY внутрь видимой области (обычно "поднимает" панель вверх
     * или сдвигает от края). Работает для всех модулей, не только для Target HUD.
     */
    private void clampPanelToScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        int margin = 6;
        pX = Math.max(margin, Math.min(pX, screenW - pW - margin));
        pY = Math.max(margin, Math.min(pY, screenH - pH - margin));
    }

    /**
     * Высота панели настроек "Target HUD". Зависит только от тумблера "Частицы"
     * (есть поле выбора или нет) — раскрытый список НЕ раздувает панель, он рисуется
     * как отдельный плавающий виджет поверх остального содержимого (см. renderParticleSelector).
     */
    private int computeTargetHudPanelHeight() {
        boolean particlesOn = LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true);
        return particlesOn ? 165 : 130;
    }

    private boolean isHudWithHomeIndicator(String hud) {
        return hud != null && (
                hud.equals("Keybinds")
                        || hud.equals("Armor Status")
                        || hud.equals("Inventory HUD")
                        || hud.equals("Cooldowns")
                        || hud.equals("Potions")
        );
    }

    private void drawBgSwitcher(DrawContext context, String label, String key, int y, int alphaInt) {
        SFUI.draw(context.getMatrices(), label, pX + 8, y + 2, 8.5f, (alphaInt << 24) | 0xDDDDDD);

        boolean isBlur = LexoraGui.moduleStates.getOrDefault(key, false);

        int activeBg = 0x44AA44;
        int inactiveBg = 0x444444;

        int stdBg = !isBlur ? activeBg : inactiveBg;
        int blurBg = isBlur ? activeBg : inactiveBg;

        RoundedRectShader.draw(context, pX + pW - 96, y - 2, 44, 14, 4f, (alphaInt << 24) | stdBg);
        SFUI.draw(context.getMatrices(), "Стандарт", pX + pW - 91, y + 1, 7.5f, (alphaInt << 24) | (!isBlur ? 0xFFFFFF : 0xAAAAAA));

        RoundedRectShader.draw(context, pX + pW - 48, y - 2, 38, 14, 4f, (alphaInt << 24) | blurBg);
        SFUI.draw(context.getMatrices(), "Блюр", pX + pW - 40, y + 1, 7.5f, (alphaInt << 24) | (isBlur ? 0xFFFFFF : 0xAAAAAA));
    }

    private boolean handleSwitcherClick(int mx, int my, String key, int y) {
        if (inside(mx, my, pX + pW - 96, y - 2, 44, 14)) {
            LexoraGui.moduleStates.put(key, false);
            return true;
        }
        if (inside(mx, my, pX + pW - 48, y - 2, 38, 14)) {
            LexoraGui.moduleStates.put(key, true);
            return true;
        }
        return false;
    }

    private void drawInfoToggle(DrawContext context, String label, String key, int y, int alphaInt) {
        boolean enabled = LexoraGui.moduleStates.getOrDefault(key, getInfoDefault(key));
        drawToggleVisual(context, label, key, enabled, y, alphaInt);
    }

    private void drawToggle(DrawContext context, String label, String key, int y, int alphaInt) {
        boolean enabled = LexoraGui.moduleStates.getOrDefault(key, false);
        drawToggleVisual(context, label, key, enabled, y, alphaInt);
    }

    private void drawToggleDefaultTrue(DrawContext context, String label, String key, int y, int alphaInt) {
        boolean enabled = LexoraGui.moduleStates.getOrDefault(key, true);
        drawToggleVisual(context, label, key, enabled, y, alphaInt);
    }

    private void drawToggleVisual(DrawContext context, String label, String key, boolean enabled, int y, int alphaInt) {
        SFUI.draw(context.getMatrices(), label, pX + 8, y + 2, 8.5f, (alphaInt << 24) | 0xDDDDDD);

        float target = enabled ? 1.0f : 0.0f;
        float current = toggleAnimations.getOrDefault(key, target);
        current += (target - current) * 0.25f;

        if (Math.abs(current - target) < 0.01f) {
            current = target;
        }

        toggleAnimations.put(key, current);

        int offColor = 0x444444;
        int onColor = 0x44AA44;
        int bgColor = lerpColor(offColor, onColor, current);

        RoundedRectShader.draw(context, pX + pW - 35, y - 2, 27, 12, 5f, (alphaInt << 24) | bgColor);

        int knobOffX = pX + pW - 33;
        int knobOnX = pX + pW - 18;
        int knobX = Math.round(knobOffX + (knobOnX - knobOffX) * current);

        RoundedRectShader.draw(context, knobX, y, 8, 8, 4f, (alphaInt << 24) | 0xFFFFFF);
    }

    private void drawScaleSlider(DrawContext context, float scale, int y, int alphaInt) {
        RoundedRectShader.draw(context, pX + 8, y, pW - 16, 4, 2f, (alphaInt << 24) | 0x444444);

        float pct = Math.max(0f, Math.min(1f, (scale - 0.5f) / 1.5f));
        int thumbX = pX + 8 + (int) (pct * (pW - 16));

        RoundedRectShader.draw(context, pX + 8, y, Math.max(2, thumbX - (pX + 8)), 4, 2f, (alphaInt << 24) | 0x8888FF);

        RoundedRectShader.draw(context, thumbX - 3, y - 4, 6, 12, 3f, (alphaInt << 24) | 0xFFFFFF);
    }

    private boolean getInfoDefault(String key) {
        return switch (key) {
            case "Info Magnetic Snap",
                 "Info Show Logo",
                 "Info Show Name",
                 "Info Show UUID",
                 "Info Show FPS",
                 "Info Show Coords",
                 "Info Show BPS" -> true;

            case "Info HUD Blur",
                 "Info Show TPS",
                 "Info Show Server" -> false;

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

        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;

        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;

        int r = (int) (fr + (tr - fr) * progress);
        int g = (int) (fg + (tg - fg) * progress);
        int b = (int) (fb + (tb - fb) * progress);

        return (r << 16) | (g << 8) | b;
    }

    private void drawTexQuad(
            DrawContext context,
            Identifier texture,
            float x,
            float y,
            float w,
            float h,
            float u0,
            float v0,
            float u1,
            float v1,
            int color
    ) {
        if (((color >> 24) & 0xFF) <= 5) {
            return;
        }

        context.draw();

        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );

        buffer.vertex(matrix, x, y, 0.0F).texture(u0, v0).color(r, g, b, a);
        buffer.vertex(matrix, x, y + h, 0.0F).texture(u0, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y + h, 0.0F).texture(u1, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y, 0.0F).texture(u1, v0).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}