package com.lexoravisauls.client;

import com.lexoravisauls.client.auth.LexoraSessionMonitor;
import com.lexoravisauls.client.badge.LexoraAccount;
import com.lexoravisauls.client.badge.LexoraClientPresence;
import com.lexoravisauls.client.badge.LexoraExternalPresence;
import com.lexoravisauls.client.badge.LexoraIrcClient;
import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.emotion.AnimationLoader;
import com.lexoravisauls.client.emotion.EmotionManager;
import com.lexoravisauls.client.emotion.RadialMenuScreen;
import com.lexoravisauls.client.events.*;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.main_menu.LexoraMainMenu;
import com.lexoravisauls.client.gui.modern.HolyWorldEventsApi;
import com.lexoravisauls.client.gui.modern.MirageGlassShader;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.lexoravisauls.client.gui.LexoraIrcScreen;
import com.lexoravisauls.client.modules.*;
import com.lexoravisauls.client.modules.killeffect.KillEffectManager;
import com.lexoravisauls.client.motionblur.MotionReBlur;
import com.lexoravisauls.client.party.LexoraPartyClient;
import com.lexoravisauls.client.utils.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.lwjgl.glfw.GLFW;
import com.lexoravisauls.client.emotion.RadialMenuModule;

import java.util.HashMap;
import java.util.Map;

public class LexoravisaulsClient implements ClientModInitializer {

    private static boolean wasMouseDown = false;

    private static boolean draggingPotion = false;
    private static boolean draggingTarget = false;
    private static boolean draggingHearth = false;
    private static boolean draggingArmor = false;
    private static boolean draggingInv = false;
    private static boolean draggingCool = false;
    private static boolean draggingKeys = false;

    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;

    // Трекер состояний для красивых уведомлений Dynamic Island
    private static final Map<String, Boolean> lastModuleStates = new HashMap<>();

    public static final int MOUSE_BIND_BASE = -1000;
    public static final int SCROLL_UP_BIND = -2001;
    public static final int SCROLL_DOWN_BIND = -2002;

    public static int encodeMouseBind(int mouseButton) {
        return MOUSE_BIND_BASE - mouseButton;
    }

    public static boolean isMouseBind(int bindCode) {
        return bindCode <= MOUSE_BIND_BASE && bindCode > SCROLL_UP_BIND;
    }

    public static int decodeMouseBind(int bindCode) {
        return MOUSE_BIND_BASE - bindCode;
    }

    public static boolean isBindDown(long window, int bindCode) {
        return BindManager.isBindDown(window, bindCode);
    }

    @Override
    public void onInitializeClient() {
        System.out.println("Lexora Visuals: Инициализация...");

        EventFetcher.startFetching();
        LexoraAccount.fetchProfileData();
        LexoraMainMenu.registerGifPreloader();
        LexoraIrcClient.register();
        LexoraClientPresence.register();
        LexoraExternalPresence.register();
        LexoraSpotifyMediaInfoHelper.init();
        LexoraMediaUtils.init();
        LexoraPartyClient.register();
        MirageGlassShader.register();
        PartyWaypoint.register();
        CalloutManager.register();
        ConfigManager.loadConfig();
        DiscordRPCManager.init();
        KillEffectManager.init();
        MotionReBlur.init();
        com.lexoravisauls.client.liteapi.LiteApiFeatureControl.init();
        HolyWorldEventsApi.register();
        TaksaEvents.register();
        TotemSoundManager.init();
        EmotionManager.init();
        com.lexoravisauls.client.cosmetic.CosmeticManager.getInstance().init();
        RoundedRectShader.register();
        LexoraSkyRenderer.ensureRegistered();
        LexoraSessionMonitor.register();
        GpsCommand.register();
        com.lexoravisauls.client.modules.virtualdesktop.VirtualDesktopCommand.register();

        ClientData.moduleBinds.putIfAbsent("Lexora IRC", GLFW.GLFW_KEY_UNKNOWN);

        // ФИКС: Устанавливаем бинд GUI по дефолту на Правый Шифт (если конфиг пустой)
        ClientData.numSettings.putIfAbsent("ClickGuiBind", (float) GLFW.GLFW_KEY_RIGHT_SHIFT);

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            AnimationLoader.loadAll();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GPS.tick();
            PvpBossBarTracker.tick();
            FakePlayerManager.tick();
            ParticleSystem.tick();
            TrailManager.onTick();
            ItemSwap.tick();
            ElytraSwap.tick();
            AutoEat.tick();
            FreeLook.tick();
            AutoRespawn.tick();
            LootNotifier.tick();
            AutoLeave.tick();
            ShiftTap.tick();
            FastSwap.tick();
            ItemScroller.tick();
            PvPSave.tick();
            Zoom.tick(client);
            com.lexoravisauls.client.modules.EngineOptimizer.tick();
            AuraParticles.tick();
            MotionClones.tick();
            TapeMouse.tick(client);
            com.lexoravisauls.client.gui.modern.ModernClickGui.HolyWorldJoiner.tick();
            HitIndicatorRenderer.onTick(client);
            TotemSoundManager.tick(client);
            RadialMenuModule.tick();
            com.lexoravisauls.client.modules.weather.WeatherFX.tick();
            com.lexoravisauls.client.modules.virtualdesktop.VirtualDesktopManager.getInstance().tick(client);



            if (LexoraIrcClient.shouldOpenGuiFromChat) {
                LexoraIrcClient.shouldOpenGuiFromChat = false;
                client.setScreen(new LexoraIrcScreen());
            }

            // Обработчик биндов и звуков
            BindManager.handleInputEvents(client);

            // Обработка открытия IRC по бинду
            if (ClientData.moduleStates.getOrDefault("Lexora IRC", false)) {
                client.setScreen(new LexoraIrcScreen());
                ClientData.moduleStates.put("Lexora IRC", false); // Сбрасываем обратно
            }

            // Умная синхронизация: отслеживаем изменения для вывода Dynamic Island
            for (Map.Entry<String, Boolean> entry : ClientData.moduleStates.entrySet()) {
                String modName = entry.getKey();
                boolean currentState = entry.getValue();
                boolean lastState = lastModuleStates.getOrDefault(modName, false);

                if (currentState != lastState) {
                    LexoraGui.moduleStates.put(modName, currentState); // Синхрон со старым GUI
                    if (currentState) {
                        DynamicIslandManager.notifyModEnabled(modName);
                    } else {
                        DynamicIslandManager.notifyModDisabled(modName);
                    }
                    lastModuleStates.put(modName, currentState);
                }
            }

            if (client.world != null) {
                DynamicIslandManager.tick();
                NotifManager.tick(client);
            }

            handleHudDragging(client);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.currentScreen instanceof ChatScreen && Screen.hasControlDown()) {
                for (int i = 0; i < mc.getWindow().getScaledWidth(); i += 10) {
                    context.fill(i, 0, i + 1, mc.getWindow().getScaledHeight(), 0x22FFFFFF);
                }

                for (int i = 0; i < mc.getWindow().getScaledHeight(); i += 10) {
                    context.fill(0, i, mc.getWindow().getScaledWidth(), i + 1, 0x22FFFFFF);
                }
            }

            float tickDelta = tickCounter.getTickDelta(true);

            PredictionRenderer.renderHud(context, tickDelta);
            PotionHudRenderer.render(context, tickDelta);
            TargetHudRenderer.render(context, tickDelta);
            DynamicIslandRenderer.render(context, tickDelta);
            ExtraHudsRenderer.render(context, tickDelta);
            InfoHudRenderer.render(context, tickDelta);
            SaturationHudRenderer.render(context, tickCounter.getTickDelta(true));
            ScoreboardHudRenderer.render(context, tickDelta);
            GPS.renderWaypointPanels(context, MinecraftClient.getInstance().gameRenderer.getCamera(), tickCounter.getTickDelta(true));
            PartyWaypoint.render3D(context, MinecraftClient.getInstance().gameRenderer.getCamera(), tickCounter.getTickDelta(true));
            HitIndicatorRenderer.renderHud(context, tickDelta);
            TntHudRenderer.render(context, tickDelta);
        });

        WorldRenderEvents.LAST.register(context -> {
            net.minecraft.client.util.math.MatrixStack stack = context.matrixStack();
            float tickDelta = context.tickCounter().getTickDelta(true);
            net.minecraft.util.math.Vec3d camPos = context.camera().getPos();

            com.lexoravisauls.client.modules.weather.WeatherFX.render(context.matrixStack(), context.camera(), context.projectionMatrix(), tickDelta);
            HitWave.render(context.matrixStack(), context.camera());
            TargetESPRenderer.render(context.matrixStack(), context.camera(), tickDelta);
            JumpCircleRenderer.render(context.matrixStack(), context.camera(), tickDelta);
            PredictionRenderer.render(context.matrixStack(), context.camera(), tickDelta);
            ParticleSystem.render(context.matrixStack(), context.camera(), tickDelta);
            CustomHitbox.render(context.matrixStack(), context.camera(), tickDelta);
            com.lexoravisauls.client.events.BlockOverlayRenderer.render(context.matrixStack(), context.camera(), tickDelta);
            TrailManager.onRender(context.matrixStack(), context.camera(), tickDelta);
            FtHelper.render(context.matrixStack(), context.camera(), tickDelta);
            AuraParticles.render(context.matrixStack(), context.camera(), tickDelta);
            MotionClones.render(context.matrixStack(), context.camera(), tickDelta);
            GPS.render3D(context.matrixStack(), context.camera(), tickDelta);
            com.lexoravisauls.client.modules.virtualdesktop.VirtualDesktopRenderer.render(context.matrixStack(), context.camera(), context.consumers(), tickDelta);
        });
    }

    private static void handleHudDragging(MinecraftClient client) {
        if (!(client.currentScreen instanceof ChatScreen)
                && !(client.currentScreen instanceof LexoraGui)
                && !(client.currentScreen instanceof ModernClickGui)) {
            draggingPotion = false;
            draggingTarget = false;
            draggingKeys = false;
            draggingArmor = false;
            draggingInv = false;
            draggingCool = false;
            draggingHearth = false;
            wasMouseDown = false;
            return;
        }

        long window = client.getWindow().getHandle();
        boolean isMouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();

        if (isMouseDown && !wasMouseDown) {

            // КЛИКИ ПО МУЗЫКЕ В DYNAMIC ISLAND (Пауза, Трек Вперед/Назад)
            if (DynamicIslandRenderer.mouseClicked(mouseX, mouseY, 0)) {
                wasMouseDown = true;
                return;
            }

            float potScale = ClientData.numSettings.getOrDefault("Potions Scale", 1.0f);
            int px = HudManager.potionX == -1 ? 10 : HudManager.potionX;
            int py = HudManager.potionY == -1 ? 100 : HudManager.potionY;

            if (mouseX >= px && mouseX <= px + PotionHudRenderer.WIDTH * potScale
                    && mouseY >= py && mouseY <= py + PotionHudRenderer.HEIGHT * potScale) {
                draggingPotion = true;
                dragOffsetX = (int) mouseX - px;
                dragOffsetY = (int) mouseY - py;
            }

            float tarScale = ClientData.numSettings.getOrDefault("Target HUD Scale", 1.0f);
            int tx = HudManager.targetX == -1 ? 150 : HudManager.targetX;
            int ty = HudManager.targetY == -1 ? 150 : HudManager.targetY;

            if (mouseX >= tx && mouseX <= tx + TargetHudRenderer.WIDTH * tarScale
                    && mouseY >= ty && mouseY <= ty + TargetHudRenderer.HEIGHT * tarScale) {
                draggingTarget = true;
                dragOffsetX = (int) mouseX - tx;
                dragOffsetY = (int) mouseY - ty;
            }

            float keyScale = ClientData.numSettings.getOrDefault("Keybinds Scale", 1.0f);

            if (ClientData.moduleStates.getOrDefault("Keybinds", false)
                    && mouseX >= HudManager.keybindsX
                    && mouseX <= HudManager.keybindsX + ExtraHudsRenderer.keybindsW * keyScale
                    && mouseY >= HudManager.keybindsY
                    && mouseY <= HudManager.keybindsY + ExtraHudsRenderer.keybindsH * keyScale) {
                draggingKeys = true;
                dragOffsetX = (int) mouseX - HudManager.keybindsX;
                dragOffsetY = (int) mouseY - HudManager.keybindsY;
            }

            float armScale = ClientData.numSettings.getOrDefault("Armor Status Scale", 1.0f);

            if (ClientData.moduleStates.getOrDefault("Armor Status", false)
                    && mouseX >= HudManager.armorX
                    && mouseX <= HudManager.armorX + ExtraHudsRenderer.armorW * armScale
                    && mouseY >= HudManager.armorY
                    && mouseY <= HudManager.armorY + ExtraHudsRenderer.armorH * armScale) {
                draggingArmor = true;
                dragOffsetX = (int) mouseX - HudManager.armorX;
                dragOffsetY = (int) mouseY - HudManager.armorY;
            }

            float invScale = ClientData.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);

            if (ClientData.moduleStates.getOrDefault("Inventory HUD", false)
                    && mouseX >= HudManager.invX
                    && mouseX <= HudManager.invX + ExtraHudsRenderer.invW * invScale
                    && mouseY >= HudManager.invY
                    && mouseY <= HudManager.invY + ExtraHudsRenderer.invH * invScale) {
                draggingInv = true;
                dragOffsetX = (int) mouseX - HudManager.invX;
                dragOffsetY = (int) mouseY - HudManager.invY;
            }

            float coolScale = ClientData.numSettings.getOrDefault("Cooldowns Scale", 1.0f);

            if (ClientData.moduleStates.getOrDefault("Cooldowns", false)
                    && mouseX >= HudManager.coolX
                    && mouseX <= HudManager.coolX + ExtraHudsRenderer.coolW * coolScale
                    && mouseY >= HudManager.coolY
                    && mouseY <= HudManager.coolY + ExtraHudsRenderer.coolH * coolScale) {
                draggingCool = true;
                dragOffsetX = (int) mouseX - HudManager.coolX;
                dragOffsetY = (int) mouseY - HudManager.coolY;
            }
        } else if (isMouseDown) {

            if (draggingPotion) {
                HudManager.potionX = (int) mouseX - dragOffsetX;
                HudManager.potionY = (int) mouseY - dragOffsetY;
            }
            if (draggingTarget) {
                HudManager.targetX = (int) mouseX - dragOffsetX;
                HudManager.targetY = (int) mouseY - dragOffsetY;
            }
            if (draggingKeys) {
                HudManager.keybindsX = (int) mouseX - dragOffsetX;
                HudManager.keybindsY = (int) mouseY - dragOffsetY;
            }
            if (draggingArmor) {
                HudManager.armorX = (int) mouseX - dragOffsetX;
                HudManager.armorY = (int) mouseY - dragOffsetY;
            }
            if (draggingInv) {
                HudManager.invX = (int) mouseX - dragOffsetX;
                HudManager.invY = (int) mouseY - dragOffsetY;
            }
            if (draggingCool) {
                HudManager.coolX = (int) mouseX - dragOffsetX;
                HudManager.coolY = (int) mouseY - dragOffsetY;
            }
            if (draggingHearth) {
                HudManager.hearthX = (int) mouseX - dragOffsetX;
                HudManager.hearthY = (int) mouseY - dragOffsetY;
            }
        } else if (!isMouseDown && wasMouseDown) {
            draggingPotion = false;
            draggingTarget = false;
            draggingKeys = false;
            draggingArmor = false;
            draggingInv = false;
            draggingCool = false;
            draggingHearth = false;

            ConfigManager.saveConfig();
        }

        wasMouseDown = isMouseDown;
    }

    private static boolean isModuleEnabled(String key) {
        return ClientData.moduleStates.getOrDefault(key, false)
                || LexoraGui.moduleStates.getOrDefault(key, false);
    }
}