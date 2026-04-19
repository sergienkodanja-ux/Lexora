package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.events.DynamicIslandRenderer;
import com.lexoravisauls.client.events.ExtraHudsRenderer;
import com.lexoravisauls.client.events.PotionHudRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(ChatScreen.class)
public class MixinChatScreen extends Screen {

    private static final Identifier SMOOTH_CORNERS = Identifier.of("lexoravisauls", "textures/gui/smooth_corners.png");
    private static final Identifier ICON_CLOSE = Identifier.of("lexoravisauls", "textures/gui/error.png");

    private String editingHud = null;
    private String targetEditingHud = null;
    private float panelAnim = 0f;

    private int pX = 0, pY = 0;
    private int pW = 140, pH = 90;
    private boolean draggingScale = false;

    protected MixinChatScreen(Text title) { super(title); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onHudSettingsClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Если панель открыта
        if (targetEditingHud != null && panelAnim > 0.1f) {
            // Клик мимо панели - закрываем
            if (mx < pX - 5 || mx > pX + pW + 5 || my < pY - 5 || my > pY + pH + 5) {
                targetEditingHud = null; cir.setReturnValue(true); return;
            }
            // Клик по крестику
            if (mx >= pX + pW - 24 && mx <= pX + pW && my >= pY && my <= pY + 24) {
                targetEditingHud = null; cir.setReturnValue(true); return;
            }

            // Настройки для Watermark
            if (targetEditingHud.equals("Watermark")) {
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 25 && my <= pY + 45) {
                    boolean currentState = LexoraGui.moduleStates.getOrDefault("Watermark Solid", false);
                    LexoraGui.moduleStates.put("Watermark Solid", !currentState);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 45 && my <= pY + 65) {
                    boolean currentState = LexoraGui.moduleStates.getOrDefault("Music Player", false);
                    LexoraGui.moduleStates.put("Music Player", !currentState);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 75 && my <= pY + 95) {
                    draggingScale = true; cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 110 && my <= pY + 130) {
                    File lexoraDir = new File(MinecraftClient.getInstance().runDirectory, "lexora");
                    File musicDir = new File(lexoraDir, "music");
                    if (!musicDir.exists()) musicDir.mkdirs();
                    Util.getOperatingSystem().open(musicDir);
                    cir.setReturnValue(true); return;
                }
            }
            // Настройки для Target HUD
            else if (targetEditingHud.equals("Target HUD")) {
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 25 && my <= pY + 45) {
                    boolean currentState = LexoraGui.moduleStates.getOrDefault("Target HUD Solid", false);
                    LexoraGui.moduleStates.put("Target HUD Solid", !currentState);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 50 && my <= pY + 70) {
                    boolean currentState = LexoraGui.moduleStates.getOrDefault("Damage Tint", true);
                    LexoraGui.moduleStates.put("Damage Tint", !currentState);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 75 && my <= pY + 100) {
                    draggingScale = true; cir.setReturnValue(true); return;
                }
            }
            // 🔥 Настройки для Notifications 🔥
            else if (targetEditingHud.equals("Notifications")) {
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 25 && my <= pY + 45) {
                    boolean cur = LexoraGui.moduleStates.getOrDefault("Notif Swap", true);
                    LexoraGui.moduleStates.put("Notif Swap", !cur);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 45 && my <= pY + 65) {
                    boolean cur = LexoraGui.moduleStates.getOrDefault("Notif HP", true);
                    LexoraGui.moduleStates.put("Notif HP", !cur);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 65 && my <= pY + 85) {
                    boolean cur = LexoraGui.moduleStates.getOrDefault("Notif Armor", true);
                    LexoraGui.moduleStates.put("Notif Armor", !cur);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 85 && my <= pY + 105) {
                    boolean cur = LexoraGui.moduleStates.getOrDefault("Notif Potions", true);
                    LexoraGui.moduleStates.put("Notif Potions", !cur);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 105 && my <= pY + 125) {
                    boolean cur = LexoraGui.moduleStates.getOrDefault("Notif Solid Bg", false);
                    LexoraGui.moduleStates.put("Notif Solid Bg", !cur);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 130 && my <= pY + 150) {
                    draggingScale = true; cir.setReturnValue(true); return;
                }
            }
            // Универсальные настройки для остальных худов
            else {
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 25 && my <= pY + 45) {
                    String solidKey = targetEditingHud + " Solid";
                    boolean currentState = LexoraGui.moduleStates.getOrDefault(solidKey, false);
                    LexoraGui.moduleStates.put(solidKey, !currentState);
                    cir.setReturnValue(true); return;
                }
                if (mx >= pX + 10 && mx <= pX + pW - 10 && my >= pY + 60 && my <= pY + 80) {
                    draggingScale = true; cir.setReturnValue(true); return;
                }
            }

            cir.setReturnValue(true); return;
        }

        // Правый клик по худам для открытия меню
        if (button == 1 && targetEditingHud == null) {

            // 1. Info HUD
            float infoScale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 0.8f);
            if (mx >= 5 && mx <= 5 + com.lexoravisauls.client.events.InfoHudRenderer.WIDTH * infoScale &&
                    my >= 5 && my <= 5 + com.lexoravisauls.client.events.InfoHudRenderer.HEIGHT * infoScale) {
                targetEditingHud = "Info HUD"; editingHud = "Info HUD";
                pH = 90; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }

            // 2. Watermark
            int wX = DynamicIslandRenderer.islandX, wY = DynamicIslandRenderer.islandY;
            if (mx >= wX && mx <= wX + DynamicIslandRenderer.islandW && my >= wY && my <= wY + DynamicIslandRenderer.islandH) {
                targetEditingHud = "Watermark"; editingHud = "Watermark";
                pH = 140; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }

            // 3. Target HUD
            float targetScale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            int tX = HudManager.targetX == -1 ? 150 : HudManager.targetX;
            int tY = HudManager.targetY == -1 ? 150 : HudManager.targetY;
            if (mx >= tX && mx <= tX + com.lexoravisauls.client.events.TargetHudRenderer.WIDTH * targetScale &&
                    my >= tY && my <= tY + com.lexoravisauls.client.events.TargetHudRenderer.HEIGHT * targetScale) {
                targetEditingHud = "Target HUD"; editingHud = "Target HUD";
                pH = 110; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }

            // 4. Keybinds
            float keyScale = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
            if (mx >= HudManager.keybindsX && mx <= HudManager.keybindsX + ExtraHudsRenderer.keybindsW * keyScale &&
                    my >= HudManager.keybindsY && my <= HudManager.keybindsY + ExtraHudsRenderer.keybindsH * keyScale) {
                targetEditingHud = "Keybinds"; editingHud = "Keybinds";
                pH = 90; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }

            // 5. Armor Status
            float armorScale = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
            if (mx >= HudManager.armorX && mx <= HudManager.armorX + ExtraHudsRenderer.armorW * armorScale &&
                    my >= HudManager.armorY && my <= HudManager.armorY + ExtraHudsRenderer.armorH * armorScale) {
                targetEditingHud = "Armor Status"; editingHud = "Armor Status";
                pH = 90; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }

            // 6. Inventory HUD
            float invScale = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
            if (mx >= HudManager.invX && mx <= HudManager.invX + ExtraHudsRenderer.invW * invScale &&
                    my >= HudManager.invY && my <= HudManager.invY + ExtraHudsRenderer.invH * invScale) {
                targetEditingHud = "Inventory HUD"; editingHud = "Inventory HUD";
                pH = 90; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }

            // 7. Cooldowns
            float coolScale = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
            if (mx >= HudManager.coolX && mx <= HudManager.coolX + ExtraHudsRenderer.coolW * coolScale &&
                    my >= HudManager.coolY && my <= HudManager.coolY + ExtraHudsRenderer.coolH * coolScale) {
                targetEditingHud = "Cooldowns"; editingHud = "Cooldowns";
                pH = 90; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }

            // 8. Potion HUD
            float potionScale = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
            int pX_hud = HudManager.potionX == -1 ? 10 : HudManager.potionX;
            int pY_hud = HudManager.potionY == -1 ? 100 : HudManager.potionY;
            if (mx >= pX_hud && mx <= pX_hud + PotionHudRenderer.WIDTH * potionScale &&
                    my >= pY_hud && my <= pY_hud + PotionHudRenderer.HEIGHT * potionScale) {
                targetEditingHud = "Potions"; editingHud = "Potions";
                pH = 90; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }

            // 🔥 9. Notifications (ПКМ по центру снизу экрана) 🔥
            int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
            if (mx >= screenW / 2 - 70 && mx <= screenW / 2 + 70 && my >= screenH - 150 && my <= screenH - 50) {
                targetEditingHud = "Notifications"; editingHud = "Notifications";
                pH = 165; pW = 150; pX = mx + 10; pY = my - 165; // Окно появляется ВЫШЕ курсора, чтобы не обрезалось
                cir.setReturnValue(true); return;
            }

            // 10. Hearth Hud
            float hearthScale = LexoraGui.numSettings.getOrDefault("Hearth Hud Scale", 1.0f);
            int hX = HudManager.hearthX == -1 ? 10 : HudManager.hearthX;
            int hY = HudManager.hearthY == -1 ? 50 : HudManager.hearthY;
            if (mx >= hX && mx <= hX + com.lexoravisauls.client.events.HearthHudRenderer.WIDTH * hearthScale &&
                    my >= hY && my <= hY + com.lexoravisauls.client.events.HearthHudRenderer.HEIGHT * hearthScale) {
                targetEditingHud = "Hearth Hud"; editingHud = "Hearth Hud";
                pH = 90; pW = 140; pX = mx + 10; pY = my + 10;
                cir.setReturnValue(true); return;
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onHudSettingsRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        float targetAnim = (targetEditingHud != null) ? 1.0f : 0.0f;
        panelAnim += (targetAnim - panelAnim) * 0.15f;
        if (panelAnim < 0.01f) { editingHud = null; return; }

        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_RELEASE) draggingScale = false;

        // Обработка ползунка масштаба
        if (draggingScale && targetEditingHud != null) {
            float percent = Math.max(0f, Math.min(1f, (mouseX - (pX + 10)) / (float)(pW - 20)));
            float newScale = 0.5f + percent * 1.5f;
            // Учитываем специфичное имя ключа для уведомлений
            String scaleKey = targetEditingHud.equals("Notifications") ? "Notif Scale" : targetEditingHud + " Scale";
            LexoraGui.numSettings.put(scaleKey, (float)(Math.round(newScale * 100.0) / 100.0));
        }

        context.getMatrices().push();
        context.getMatrices().translate(pX + pW / 2f, pY + pH / 2f, 0);
        context.getMatrices().scale(panelAnim, panelAnim, 1.0f);
        context.getMatrices().translate(-(pX + pW / 2f), -(pY + pH / 2f), 0);

        int alphaInt = Math.max(5, Math.min(255, (int)(panelAnim * 255)));
        drawSmoothRect(context, pX, pY, pW, pH, 6, ((int)(panelAnim * 245) << 24) | 0x141416);

        Text title = Text.literal(editingHud + " Settings").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
        context.drawText(MinecraftClient.getInstance().textRenderer, title, pX + 8, pY + 10, (alphaInt << 24) | 0xFFFFFF, false);

        boolean closeHover = mouseX >= pX + pW - 24 && mouseX <= pX + pW && mouseY >= pY && mouseY <= pY + 24;
        drawTexQuad(context, ICON_CLOSE, pX + pW - 18, pY + 8, 10, 10, 0, 0, 1, 1, closeHover ? ((alphaInt << 24) | 0xFF5555) : ((alphaInt << 24) | 0x888888));

        if (editingHud.equals("Watermark")) {
            boolean isSolid = LexoraGui.moduleStates.getOrDefault("Watermark Solid", false);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Сплошной фон").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 30, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 28, 27, 12, 5, isSolid ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isSolid ? (pX + pW - 18) : (pX + pW - 33), pY + 30, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            boolean isMusic = LexoraGui.moduleStates.getOrDefault("Music Player", false);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Плеер").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 50, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 48, 27, 12, 5, isMusic ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isMusic ? (pX + pW - 18) : (pX + pW - 33), pY + 50, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            float scale = LexoraGui.numSettings.getOrDefault("Watermark Scale", 1.0f);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Масштаб: " + String.format("%.2f", scale).replace(",", ".")).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 75, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + 8, pY + 91, pW - 16, 4, 2, (alphaInt << 24) | 0x444444);
            int thumbX = pX + 8 + (int)(((scale - 0.5f) / 1.5f) * (pW - 16));
            drawSmoothRect(context, pX + 8, pY + 91, Math.max(2, thumbX - (pX + 8)), 4, 2, (alphaInt << 24) | 0x8888FF);
            drawSmoothRect(context, thumbX - 3, pY + 87, 6, 12, 3, (alphaInt << 24) | 0xFFFFFF);

            boolean hoverBtn = mouseX >= pX + 10 && mouseX <= pX + pW - 10 && mouseY >= pY + 110 && mouseY <= pY + 130;
            drawSmoothRect(context, pX + 10, pY + 110, pW - 20, 20, 4, (alphaInt << 24) | (hoverBtn ? 0x3A3A45 : 0x2A2A35));
            Text folderText = Text.literal("Папка с музыкой").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui")));
            context.drawText(MinecraftClient.getInstance().textRenderer, folderText, pX + pW / 2 - MinecraftClient.getInstance().textRenderer.getWidth(folderText) / 2, pY + 116, (alphaInt << 24) | 0xFFFFFF, false);

        } else if (editingHud.equals("Target HUD")) {
            boolean isSolid = LexoraGui.moduleStates.getOrDefault("Target HUD Solid", false);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Сплошной фон").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 30, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 28, 27, 12, 5, isSolid ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isSolid ? (pX + pW - 18) : (pX + pW - 33), pY + 30, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            boolean isTint = LexoraGui.moduleStates.getOrDefault("Damage Tint", true);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Урон красным").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 55, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 53, 27, 12, 5, isTint ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isTint ? (pX + pW - 18) : (pX + pW - 33), pY + 55, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            float scale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Масштаб: " + String.format("%.2f", scale).replace(",", ".")).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 80, (alphaInt << 24) | 0xDDDDDD, false);

            drawSmoothRect(context, pX + 8, pY + 96, pW - 16, 4, 2, (alphaInt << 24) | 0x444444);
            float pct = (scale - 0.5f) / 1.5f;
            int thumbX = pX + 8 + (int)(pct * (pW - 16));
            drawSmoothRect(context, pX + 8, pY + 96, Math.max(2, thumbX - (pX + 8)), 4, 2, (alphaInt << 24) | 0x8888FF);
            drawSmoothRect(context, thumbX - 3, pY + 92, 6, 12, 3, (alphaInt << 24) | 0xFFFFFF);

        }
        // 🔥 Отрисовка меню Notifications 🔥
        else if (editingHud.equals("Notifications")) {
            boolean isSwap = LexoraGui.moduleStates.getOrDefault("Notif Swap", true);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("О свапе").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 30, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 28, 27, 12, 5, isSwap ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isSwap ? (pX + pW - 18) : (pX + pW - 33), pY + 30, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            boolean isHP = LexoraGui.moduleStates.getOrDefault("Notif HP", true);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("О малом ХП").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 50, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 48, 27, 12, 5, isHP ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isHP ? (pX + pW - 18) : (pX + pW - 33), pY + 50, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            boolean isArmor = LexoraGui.moduleStates.getOrDefault("Notif Armor", true);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("О броне").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 70, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 68, 27, 12, 5, isArmor ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isArmor ? (pX + pW - 18) : (pX + pW - 33), pY + 70, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            boolean isPots = LexoraGui.moduleStates.getOrDefault("Notif Potions", true);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("О зельях").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 90, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 88, 27, 12, 5, isPots ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isPots ? (pX + pW - 18) : (pX + pW - 33), pY + 90, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            boolean isSolid = LexoraGui.moduleStates.getOrDefault("Notif Solid Bg", false);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Сплошной фон").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 110, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 108, 27, 12, 5, isSolid ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isSolid ? (pX + pW - 18) : (pX + pW - 33), pY + 110, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            float scale = LexoraGui.numSettings.getOrDefault("Notif Scale", 1.0f);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Масштаб: " + String.format("%.2f", scale).replace(",", ".")).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 135, (alphaInt << 24) | 0xDDDDDD, false);

            drawSmoothRect(context, pX + 8, pY + 151, pW - 16, 4, 2, (alphaInt << 24) | 0x444444);
            float pct = Math.max(0f, Math.min(1f, (scale - 0.5f) / 1.5f));
            int thumbX = pX + 8 + (int)(pct * (pW - 16));
            drawSmoothRect(context, pX + 8, pY + 151, Math.max(2, thumbX - (pX + 8)), 4, 2, (alphaInt << 24) | 0x8888FF);
            drawSmoothRect(context, thumbX - 3, pY + 147, 6, 12, 3, (alphaInt << 24) | 0xFFFFFF);

        } else {
            // Универсальное меню
            String solidKey = editingHud + " Solid";
            String scaleKey = editingHud + " Scale";

            boolean isSolid = LexoraGui.moduleStates.getOrDefault(solidKey, false);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Сплошной фон").setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 30, (alphaInt << 24) | 0xDDDDDD, false);
            drawSmoothRect(context, pX + pW - 35, pY + 28, 27, 12, 5, isSolid ? ((alphaInt << 24) | 0x44AA44) : ((alphaInt << 24) | 0x444444));
            drawSmoothRect(context, isSolid ? (pX + pW - 18) : (pX + pW - 33), pY + 30, 8, 8, 4, (alphaInt << 24) | 0xFFFFFF);

            float scale = LexoraGui.numSettings.getOrDefault(scaleKey, 1.0f);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("Масштаб: " + String.format("%.2f", scale).replace(",", ".")).setStyle(Style.EMPTY.withFont(Identifier.of("lexoravisauls", "sfui"))), pX + 8, pY + 52, (alphaInt << 24) | 0xDDDDDD, false);

            drawSmoothRect(context, pX + 8, pY + 68, pW - 16, 4, 2, (alphaInt << 24) | 0x444444);
            float pct = (scale - 0.5f) / 1.5f;
            int thumbX = pX + 8 + (int)(pct * (pW - 16));
            drawSmoothRect(context, pX + 8, pY + 68, Math.max(2, thumbX - (pX + 8)), 4, 2, (alphaInt << 24) | 0x8888FF);
            drawSmoothRect(context, thumbX - 3, pY + 64, 6, 12, 3, (alphaInt << 24) | 0xFFFFFF);
        }

        context.getMatrices().pop();
    }

    private void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        context.fill(x + radius, y, x + width - radius, y + height, color);
        context.fill(x, y + radius, x + radius, y + height - radius, color);
        context.fill(x + width - radius, y + radius, x + width, y + height - radius, color);
        drawTexQuad(context, SMOOTH_CORNERS, x, y, radius, radius, 0.0F, 0.0F, 0.5F, 0.5F, color);
        drawTexQuad(context, SMOOTH_CORNERS, x + width - radius, y, radius, radius, 0.5F, 0.0F, 1.0F, 0.5F, color);
        drawTexQuad(context, SMOOTH_CORNERS, x, y + height - radius, radius, radius, 0.0F, 0.5F, 0.5F, 1.0F, color);
        drawTexQuad(context, SMOOTH_CORNERS, x + width - radius, y + height - radius, radius, radius, 0.5F, 0.5F, 1.0F, 1.0F, color);
    }

    private void drawTexQuad(DrawContext context, Identifier texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int color) {
        if (((color >> 24) & 0xFF) <= 5) return;
        context.draw();
        float a = ((color >> 24) & 0xFF) / 255.0F, r = ((color >> 16) & 0xFF) / 255.0F, g = ((color >> 8) & 0xFF) / 255.0F, b = (color & 0xFF) / 255.0F;

        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(u0, v0).color(r, g, b, a);
        buffer.vertex(matrix, x, y + h, 0.0F).texture(u0, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y + h, 0.0F).texture(u1, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y, 0.0F).texture(u1, v0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableDepthTest();
    }
}