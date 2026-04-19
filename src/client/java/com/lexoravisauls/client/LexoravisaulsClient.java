package com.lexoravisauls.client;

import com.lexoravisauls.client.events.*;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.*;
import com.lexoravisauls.client.utils.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class LexoravisaulsClient implements ClientModInitializer {

    public static KeyBinding menuKeybind;
    private static boolean wasMouseDown = false;

    // 🔥 ДОБАВЛЕНА ПЕРЕМЕННАЯ draggingHearth 🔥
    private static boolean draggingPotion = false, draggingTarget = false, draggingHearth = false;
    private static boolean draggingArmor = false, draggingInv = false, draggingCool = false, draggingKeys = false;
    private static int dragOffsetX = 0, dragOffsetY = 0;

    private static final Map<Integer, Boolean> keyStates = new HashMap<>();

    @Override
    public void onInitializeClient() {
        System.out.println("Lexora Visuals: Инициализация...");

        ConfigManager.loadConfig();
        DiscordRPCManager.init();
        MusicManager.init();

        menuKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Open Lexora Menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "Lexora Visuals"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FakePlayerManager.tick();

            ParticleSystem.tick();
            TrailManager.onTick();
            com.lexoravisauls.client.modules.ItemSwap.tick();
            com.lexoravisauls.client.modules.ElytraSwap.tick();
            AutoEat.tick();
            FreeLook.tick();
            AutoRespawn.tick();
            LootNotifier.tick();
            MusicManager.tick();
            AutoLeave.tick();
            ShiftTap.tick();
            FastSwap.tick();
            ItemScroller.tick();
            RoundedRectShader.register();
            com.lexoravisauls.client.modules.PvPSave.tick();

            boolean isEscMenu = client.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen;
            boolean shouldPlay = client.world != null && client.isWindowFocused() && !client.isPaused() && !isEscMenu && LexoraGui.moduleStates.getOrDefault("Music Player", false);
            MusicManager.forcePause(!shouldPlay);

            while (menuKeybind.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new LexoraGui());
                }
            }

            if (client.currentScreen == null) {
                long window = client.getWindow().getHandle();
                for (Map.Entry<String, Integer> entry : LexoraGui.moduleBinds.entrySet()) {
                    int key = entry.getValue();
                    if (key != GLFW.GLFW_KEY_UNKNOWN) {
                        boolean isDown = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
                        boolean wasDown = keyStates.getOrDefault(key, false);

                        if (isDown && !wasDown) {
                            String modName = entry.getKey();
                            boolean currentState = LexoraGui.moduleStates.getOrDefault(modName, false);
                            LexoraGui.moduleStates.put(modName, !currentState);

                            if (!currentState) DynamicIslandManager.notifyModEnabled(modName);
                            else DynamicIslandManager.notifyModDisabled(modName);

                            ConfigManager.saveConfig();
                        }
                        keyStates.put(key, isDown);
                    }
                }
            }

            if (client.world != null) {
                DynamicIslandManager.tick();
                NotifManager.tick(client); // 🔥 Добавил вызов тика для NotifManager, чтобы работали уведомления ХП/Брони
            }

            if (client.currentScreen instanceof ChatScreen || client.currentScreen instanceof LexoraGui) {
                long window = client.getWindow().getHandle();
                boolean isMouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

                double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
                double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();

                if (isMouseDown && !wasMouseDown) {
                    float potScale = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
                    int px = HudManager.potionX == -1 ? 10 : HudManager.potionX;
                    int py = HudManager.potionY == -1 ? 100 : HudManager.potionY;
                    if (mouseX >= px && mouseX <= px + PotionHudRenderer.WIDTH * potScale && mouseY >= py && mouseY <= py + PotionHudRenderer.HEIGHT * potScale) {
                        draggingPotion = true; dragOffsetX = (int) mouseX - px; dragOffsetY = (int) mouseY - py;
                    }

                    float tarScale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
                    int tx = HudManager.targetX == -1 ? 150 : HudManager.targetX;
                    int ty = HudManager.targetY == -1 ? 150 : HudManager.targetY;
                    if (mouseX >= tx && mouseX <= tx + TargetHudRenderer.WIDTH * tarScale && mouseY >= ty && mouseY <= ty + TargetHudRenderer.HEIGHT * tarScale) {
                        draggingTarget = true; dragOffsetX = (int) mouseX - tx; dragOffsetY = (int) mouseY - ty;
                    }

                    // 🔥 ДОБАВЛЕН КЛИК ДЛЯ HEARTH HUD 🔥
                    float hearthScale = LexoraGui.numSettings.getOrDefault("Hearth Hud Scale", 1.0f);
                    int hx = HudManager.hearthX == -1 ? 10 : HudManager.hearthX;
                    int hy = HudManager.hearthY == -1 ? 50 : HudManager.hearthY;
                    if (LexoraGui.moduleStates.getOrDefault("Hearth Hud", false) && mouseX >= hx && mouseX <= hx + HearthHudRenderer.WIDTH * hearthScale && mouseY >= hy && mouseY <= hy + HearthHudRenderer.HEIGHT * hearthScale) {
                        draggingHearth = true; dragOffsetX = (int) mouseX - hx; dragOffsetY = (int) mouseY - hy;
                    }

                    float keyScale = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
                    if (LexoraGui.moduleStates.getOrDefault("Keybinds", false) && mouseX >= HudManager.keybindsX && mouseX <= HudManager.keybindsX + ExtraHudsRenderer.keybindsW * keyScale && mouseY >= HudManager.keybindsY && mouseY <= HudManager.keybindsY + ExtraHudsRenderer.keybindsH * keyScale) {
                        draggingKeys = true; dragOffsetX = (int) mouseX - HudManager.keybindsX; dragOffsetY = (int) mouseY - HudManager.keybindsY;
                    }

                    float armScale = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
                    if (LexoraGui.moduleStates.getOrDefault("Armor Status", false) && mouseX >= HudManager.armorX && mouseX <= HudManager.armorX + ExtraHudsRenderer.armorW * armScale && mouseY >= HudManager.armorY && mouseY <= HudManager.armorY + ExtraHudsRenderer.armorH * armScale) {
                        draggingArmor = true; dragOffsetX = (int) mouseX - HudManager.armorX; dragOffsetY = (int) mouseY - HudManager.armorY;
                    }

                    float invScale = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
                    if (LexoraGui.moduleStates.getOrDefault("Inventory HUD", false) && mouseX >= HudManager.invX && mouseX <= HudManager.invX + ExtraHudsRenderer.invW * invScale && mouseY >= HudManager.invY && mouseY <= HudManager.invY + ExtraHudsRenderer.invH * invScale) {
                        draggingInv = true; dragOffsetX = (int) mouseX - HudManager.invX; dragOffsetY = (int) mouseY - HudManager.invY;
                    }

                    float coolScale = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
                    if (LexoraGui.moduleStates.getOrDefault("Cooldowns", false) && mouseX >= HudManager.coolX && mouseX <= HudManager.coolX + ExtraHudsRenderer.coolW * coolScale && mouseY >= HudManager.coolY && mouseY <= HudManager.coolY + ExtraHudsRenderer.coolH * coolScale) {
                        draggingCool = true; dragOffsetX = (int) mouseX - HudManager.coolX; dragOffsetY = (int) mouseY - HudManager.coolY;
                    }

                    if (com.lexoravisauls.client.events.DynamicIslandRenderer.MusicState.isPlayingMusic()) {

                        int[] pBounds = com.lexoravisauls.client.events.DynamicIslandRenderer.progressBarBounds;
                        if (mouseX >= pBounds[0] && mouseX <= pBounds[0] + pBounds[2] && mouseY >= pBounds[1] && mouseY <= pBounds[1] + pBounds[3]) {
                            com.lexoravisauls.client.events.DynamicIslandRenderer.isDraggingProgress = true;
                        }

                        int[] vBounds = com.lexoravisauls.client.events.DynamicIslandRenderer.volumeBarBounds;
                        if (mouseX >= vBounds[0] && mouseX <= vBounds[0] + vBounds[2] && mouseY >= vBounds[1] && mouseY <= vBounds[1] + vBounds[3]) {
                            com.lexoravisauls.client.events.DynamicIslandRenderer.isDraggingVolume = true;
                        }

                        int islandX = com.lexoravisauls.client.events.DynamicIslandRenderer.islandX;
                        int islandY = com.lexoravisauls.client.events.DynamicIslandRenderer.islandY;
                        int islandW = com.lexoravisauls.client.events.DynamicIslandRenderer.islandW;

                        int btnSize = 12;
                        int centerBtnX = islandX + islandW / 2;
                        int btnY = islandY + 22;

                        if (mouseX >= centerBtnX - btnSize/2f && mouseX <= centerBtnX + btnSize/2f && mouseY >= btnY && mouseY <= btnY + btnSize) {
                            MusicManager.togglePlayPause();
                        }
                        if (mouseX >= centerBtnX - 25 - btnSize && mouseX <= centerBtnX - 25 && mouseY >= btnY && mouseY <= btnY + btnSize) {
                            MusicManager.prev();
                        }
                        if (mouseX >= centerBtnX + 25 && mouseX <= centerBtnX + 25 + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                            MusicManager.next();
                        }

                        int padding = 12;
                        if (mouseX >= islandX + islandW - padding - 15 && mouseX <= islandX + islandW - padding - 5 && mouseY >= btnY + 1 && mouseY <= btnY + 11) {
                            MusicManager.repeatTrack();
                        }
                    }

                } else if (isMouseDown) {
                    if (draggingPotion) { HudManager.potionX = (int) mouseX - dragOffsetX; HudManager.potionY = (int) mouseY - dragOffsetY; }
                    if (draggingTarget) { HudManager.targetX = (int) mouseX - dragOffsetX; HudManager.targetY = (int) mouseY - dragOffsetY; }
                    if (draggingKeys) { HudManager.keybindsX = (int) mouseX - dragOffsetX; HudManager.keybindsY = (int) mouseY - dragOffsetY; }
                    if (draggingArmor) { HudManager.armorX = (int) mouseX - dragOffsetX; HudManager.armorY = (int) mouseY - dragOffsetY; }
                    if (draggingInv) { HudManager.invX = (int) mouseX - dragOffsetX; HudManager.invY = (int) mouseY - dragOffsetY; }
                    if (draggingCool) { HudManager.coolX = (int) mouseX - dragOffsetX; HudManager.coolY = (int) mouseY - dragOffsetY; }
                    // 🔥 ДОБАВЛЕНО ДВИЖЕНИЕ ДЛЯ HEARTH HUD 🔥
                    if (draggingHearth) { HudManager.hearthX = (int) mouseX - dragOffsetX; HudManager.hearthY = (int) mouseY - dragOffsetY; }
                } else if (!isMouseDown && wasMouseDown) {
                    draggingPotion = draggingTarget = draggingKeys = draggingArmor = draggingInv = draggingCool = draggingHearth = false; // 🔥 Добавил draggingHearth

                    if (com.lexoravisauls.client.events.DynamicIslandRenderer.isDraggingProgress) {
                        MusicManager.seek(com.lexoravisauls.client.events.DynamicIslandRenderer.MusicState.progress);
                    }

                    com.lexoravisauls.client.events.DynamicIslandRenderer.isDraggingProgress = false;
                    com.lexoravisauls.client.events.DynamicIslandRenderer.isDraggingVolume = false;
                    ConfigManager.saveConfig();
                }
                wasMouseDown = isMouseDown;
            } else {
                draggingPotion = draggingTarget = draggingKeys = draggingArmor = draggingInv = draggingCool = draggingHearth = wasMouseDown = false;
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof ChatScreen && Screen.hasControlDown()) {
                for (int i = 0; i < mc.getWindow().getScaledWidth(); i += 10) context.fill(i, 0, i + 1, mc.getWindow().getScaledHeight(), 0x22FFFFFF);
                for (int i = 0; i < mc.getWindow().getScaledHeight(); i += 10) context.fill(0, i, mc.getWindow().getScaledWidth(), i + 1, 0x22FFFFFF);
            }

            float tickDelta = tickCounter.getTickDelta(true);

            PotionHudRenderer.render(context);
            TargetHudRenderer.render(context, tickDelta);
            DynamicIslandRenderer.render(context, tickDelta);
            ExtraHudsRenderer.render(context, tickDelta);
            InfoHudRenderer.render(context, tickDelta);
            NotifManager.render(context, tickDelta);
            HearthHudRenderer.render(context, tickDelta); // 🔥 ДОБАВЛЕН РЕНДЕР ХУДА 🔥
            com.lexoravisauls.client.events.SaturationHudRenderer.render(context, tickCounter.getTickDelta(true));
        });

        WorldRenderEvents.LAST.register(context -> {
            float tickDelta = context.tickCounter().getTickDelta(true);

            HitWave.render(context.matrixStack(), context.camera());
            TargetESPRenderer.render(context.matrixStack(), context.camera(), tickDelta);
            com.lexoravisauls.client.events.JumpCircleRenderer.render(context.matrixStack(), context.camera(), tickDelta);
            com.lexoravisauls.client.events.PredictionRenderer.render(context.matrixStack(), context.camera(), tickDelta);
            ParticleSystem.render(context.matrixStack(), context.camera(), tickDelta);

            com.lexoravisauls.client.events.CustomHitbox.render(context.matrixStack(), context.camera(), tickDelta);

            TrailManager.onRender(context.matrixStack(), context.camera(), tickDelta);

            com.lexoravisauls.client.modules.FtHelper.render(context.matrixStack(), context.camera(), tickDelta);

            net.minecraft.client.render.VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            consumers.draw();
        });
    }
}