package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.LexoraIcons;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.lexoravisauls.client.utils.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class InfoHudRenderer {

    public static float WIDTH = 260f;
    public static float HEIGHT = 46f;

    public static final float FONT_SIZE = 9.0f;
    public static final float ICON_SIZE = 11.0f;

    private static final Identifier FONT_TEX = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    private static final double BPS_SMOOTHING = 0.05;
    private static final double DISPLAY_SMOOTHING = 0.03;

    private static final float SIZE_ANIMATION_SPEED = 14.0f;

    private static final float ISLAND_HEIGHT = 20.0f;
    private static final float ISLAND_GAP = 4.0f;
    private static final float SNAP_DISTANCE = 12.0f;

    private static final long NUMBER_ANIM_DURATION = 150L;
    private static final float NUMBER_ANIM_OFFSET = 6.0f;

    private static float introAnim = 0.0f;

    private static double lastX = 0.0;
    private static double lastZ = 0.0;
    private static double currentBps = 0.0;
    private static double displayBps = 0.0;
    private static double targetBps = 0.0;
    private static long lastBpsUpdateTime = 0L;

    private static long lastSizeAnimationTime = System.currentTimeMillis();

    private enum IslandType {
        WATERMARK,
        INFO
    }

    private static final class IslandState {
        final IslandType type;
        float x, y, width, height, targetWidth, targetHeight;

        IslandState(IslandType type, float x, float y, float width, float height) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.targetWidth = width;
            this.targetHeight = height;
        }
    }

    private static final class MetricAnimState {
        String lastText = "";
        String oldText = "";
        long animStart = 0;

        void update(String newText) {
            if (!lastText.equals(newText)) {
                oldText = lastText;
                lastText = newText;
                animStart = System.currentTimeMillis();
            }
        }
    }

    private static final Map<IslandType, IslandState> ISLANDS = new EnumMap<>(IslandType.class);
    private static final Map<String, MetricAnimState> METRICS = new HashMap<>();

    private static boolean layoutLoaded = false;
    private static boolean wasMouseDown = false;

    private static IslandType draggingIsland = null;
    private static float dragOffsetX = 0.0f;
    private static float dragOffsetY = 0.0f;

    private InfoHudRenderer() {}

    private static MsdfFont getFont() {
        if (msdfFont == null) {
            msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        }
        return msdfFont;
    }

    private static MetricAnimState getMetric(String key) {
        return METRICS.computeIfAbsent(key, k -> new MetricAnimState());
    }

    public static void render(DrawContext context, float tickDelta) {
        if (!isInfoEnabled()) {
            introAnim = 0.0f;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.options.hudHidden) {
            return;
        }

        loadLayout();
        updateAnimatedValues(mc);
        updateIslandTargets(mc);
        updateMouseDrag(mc);

        float baseScale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f);
        introAnim += (1.0f - introAnim) * 0.12f;

        float scale = baseScale * introAnim;

        // Высчитываем глобальную прозрачность из анимации (0-255)
        int alphaInt = (int) (introAnim * 255);

        int baseX = getBaseX(mc);
        int baseY = getBaseY(mc);

        context.getMatrices().push();
        context.getMatrices().translate(baseX, baseY, 0);
        context.getMatrices().scale(scale, scale, 1.0f);

        float maxX = 0.0f;
        float maxY = 0.0f;

        for (IslandType type : IslandType.values()) {
            if (!isVisible(type)) continue;
            if (draggingIsland == type) continue;

            IslandState state = ISLANDS.get(type);
            if (state == null) continue;

            drawIsland(context, mc, state, alphaInt);

            maxX = Math.max(maxX, state.x + state.width);
            maxY = Math.max(maxY, state.y + state.height);
        }

        if (draggingIsland != null && isVisible(draggingIsland)) {
            IslandState state = ISLANDS.get(draggingIsland);
            if (state != null) {
                drawIsland(context, mc, state, alphaInt);
                maxX = Math.max(maxX, state.x + state.width);
                maxY = Math.max(maxY, state.y + state.height);
            }
        }

        WIDTH = Math.max(220.0f, maxX);
        HEIGHT = Math.max(44.0f, maxY);

        context.getMatrices().pop();
    }

    public static int getBaseX(MinecraftClient mc) {
        return com.lexoravisauls.client.gui.HudManager.infoX;
    }

    public static int getBaseY(MinecraftClient mc) {
        return com.lexoravisauls.client.gui.HudManager.infoY;
    }

    private static boolean isInfoEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Info", LexoraGui.moduleStates.getOrDefault("Info HUD", true));
    }

    private static void loadLayout() {
        if (layoutLoaded) return;
        layoutLoaded = true;

        putDefault(IslandType.WATERMARK, 0.0f, 0.0f, 120.0f, ISLAND_HEIGHT);
        putDefault(IslandType.INFO, 0.0f, 24.0f, 210.0f, ISLAND_HEIGHT);
    }

    private static void putDefault(IslandType type, float defaultX, float defaultY, float defaultW, float defaultH) {
        float x = getSavedFloat("InfoHud." + type.name() + ".x", defaultX);
        float y = getSavedFloat("InfoHud." + type.name() + ".y", defaultY);

        if (Float.isNaN(x) || Float.isInfinite(x) || x < -50 || x > 1000) x = defaultX;
        if (Float.isNaN(y) || Float.isInfinite(y) || y < -50 || y > 1000) y = defaultY;

        ISLANDS.put(type, new IslandState(type, x, y, defaultW, defaultH));
    }

    private static float getSavedFloat(String key, float fallback) {
        if (ClientData.numSettings.containsKey(key)) return ClientData.numSettings.get(key);
        if (LexoraGui.numSettings.containsKey(key)) return LexoraGui.numSettings.get(key);
        return fallback;
    }

    private static void saveIsland(IslandState state) {
        String xKey = "InfoHud." + state.type.name() + ".x";
        String yKey = "InfoHud." + state.type.name() + ".y";

        ClientData.numSettings.put(xKey, state.x);
        ClientData.numSettings.put(yKey, state.y);
        LexoraGui.numSettings.put(xKey, state.x);
        LexoraGui.numSettings.put(yKey, state.y);

        ConfigManager.saveConfig();
    }

    private static void updateAnimatedValues(MinecraftClient mc) {
        getMetric("fps").update(String.valueOf(mc.getCurrentFps()));
        getMetric("tps").update(String.format("%.1f", getClientTps()).replace(",", "."));

        updateBps(mc);

        boolean streamerMode = LexoraGui.moduleStates.getOrDefault("Streamer Mode", false);
        boolean hideCoords = streamerMode && LexoraGui.moduleStates.getOrDefault("Hide Coords", true);

        if (mc.player != null) {
            getMetric("x").update(hideCoords ? "???" : String.valueOf((int) mc.player.getX()));
            getMetric("y").update(hideCoords ? "???" : String.valueOf((int) mc.player.getY()));
            getMetric("z").update(hideCoords ? "???" : String.valueOf((int) mc.player.getZ()));
        }

        String bpsValue = String.format("%.2f", roundToStep(displayBps, 0.50)).replace(",", ".");
        getMetric("bps").update(bpsValue);
    }

    private static void updateIslandTargets(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        float animDelta = Math.min((now - lastSizeAnimationTime) / 1000.0f, 0.1f);
        lastSizeAnimationTime = now;

        for (IslandState state : ISLANDS.values()) {
            switch (state.type) {
                case WATERMARK -> {
                    state.targetWidth = getWatermarkWidth(mc);
                    state.targetHeight = ISLAND_HEIGHT;
                }
                case INFO -> {
                    state.targetWidth = getInfoWidth(mc);
                    state.targetHeight = ISLAND_HEIGHT;
                }
            }

            state.width = lerp(state.width, state.targetWidth, animDelta);
            state.height = lerp(state.height, state.targetHeight, animDelta);

            if (Math.abs(state.width - state.targetWidth) < 0.3f) state.width = state.targetWidth;
            if (Math.abs(state.height - state.targetHeight) < 0.3f) state.height = state.targetHeight;
        }
    }

    private static void updateMouseDrag(MinecraftClient mc) {
        if (!canEdit(mc)) {
            draggingIsland = null;
            wasMouseDown = false;
            return;
        }

        long window = mc.getWindow().getHandle();
        boolean mouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        float scale = Math.max(0.01f, LexoraGui.numSettings.getOrDefault("Info HUD Scale", 1.0f) * Math.max(0.05f, introAnim));

        float scaledMouseX = (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth());
        float scaledMouseY = (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight());

        float localX = (scaledMouseX - getBaseX(mc)) / scale;
        float localY = (scaledMouseY - getBaseY(mc)) / scale;

        if (mouseDown && !wasMouseDown) {
            IslandType hit = getIslandAt(localX, localY);
            if (hit != null) {
                draggingIsland = hit;
                IslandState state = ISLANDS.get(hit);
                dragOffsetX = localX - state.x;
                dragOffsetY = localY - state.y;
            }
        }

        if (mouseDown && draggingIsland != null) {
            IslandState state = ISLANDS.get(draggingIsland);
            if (state != null) {
                state.x = localX - dragOffsetX;
                state.y = localY - dragOffsetY;
            }
        }

        if (!mouseDown && wasMouseDown && draggingIsland != null) {
            IslandState state = ISLANDS.get(draggingIsland);
            if (state != null) {
                if (LexoraGui.moduleStates.getOrDefault("Info Magnetic Snap", true)) snapIsland(state);
                saveIsland(state);
            }
            draggingIsland = null;
        }

        wasMouseDown = mouseDown;
    }

    private static boolean canEdit(MinecraftClient mc) {
        return mc.currentScreen instanceof ModernClickGui || mc.currentScreen instanceof LexoraGui;
    }

    private static IslandType getIslandAt(float mouseX, float mouseY) {
        IslandType[] values = IslandType.values();
        for (int i = values.length - 1; i >= 0; i--) {
            IslandType type = values[i];
            if (!isVisible(type)) continue;

            IslandState state = ISLANDS.get(type);
            if (state == null) continue;

            if (mouseX >= state.x && mouseX <= state.x + state.width && mouseY >= state.y && mouseY <= state.y + state.height) {
                return type;
            }
        }
        return null;
    }

    private static void snapIsland(IslandState moving) {
        IslandState bestTarget = null;
        float bestDistance = SNAP_DISTANCE + 1.0f;
        int bestMode = -1;

        for (IslandState other : ISLANDS.values()) {
            if (other == moving || !isVisible(other.type)) continue;

            float rightSnap = distance(moving.x, other.x + other.width + ISLAND_GAP, moving.y, other.y);
            if (rightSnap < bestDistance) { bestDistance = rightSnap; bestTarget = other; bestMode = 0; }

            float leftSnap = distance(moving.x + moving.width + ISLAND_GAP, other.x, moving.y, other.y);
            if (leftSnap < bestDistance) { bestDistance = leftSnap; bestTarget = other; bestMode = 1; }

            float bottomSnap = distance(moving.x, other.x, moving.y, other.y + other.height + ISLAND_GAP);
            if (bottomSnap < bestDistance) { bestDistance = bottomSnap; bestTarget = other; bestMode = 2; }

            float topSnap = distance(moving.x, other.x, moving.y + moving.height + ISLAND_GAP, other.y);
            if (topSnap < bestDistance) { bestDistance = topSnap; bestTarget = other; bestMode = 3; }
        }

        if (bestTarget == null || bestDistance > SNAP_DISTANCE) return;

        switch (bestMode) {
            case 0 -> { moving.x = bestTarget.x + bestTarget.width + ISLAND_GAP; moving.y = bestTarget.y; }
            case 1 -> { moving.x = bestTarget.x - moving.width - ISLAND_GAP; moving.y = bestTarget.y; }
            case 2 -> { moving.x = bestTarget.x; moving.y = bestTarget.y + bestTarget.height + ISLAND_GAP; }
            case 3 -> { moving.x = bestTarget.x; moving.y = bestTarget.y - moving.height - ISLAND_GAP; }
        }
    }

    private static float distance(float x1, float x2, float y1, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean isVisible(IslandType type) {
        return switch (type) {
            case WATERMARK -> LexoraGui.moduleStates.getOrDefault("Info Show Logo", true) ||
                    LexoraGui.moduleStates.getOrDefault("Info Show Name", true) ||
                    LexoraGui.moduleStates.getOrDefault("Info Show FPS", true) ||
                    LexoraGui.moduleStates.getOrDefault("Info Show TPS", false);
            case INFO -> LexoraGui.moduleStates.getOrDefault("Info Show Coords", true) ||
                    LexoraGui.moduleStates.getOrDefault("Info Show BPS", true) ||
                    LexoraGui.moduleStates.getOrDefault("Info Show Server", false);
        };
    }

    private static void drawIsland(DrawContext context, MinecraftClient mc, IslandState state, int alphaInt) {
        switch (state.type) {
            case WATERMARK -> drawWatermarkIsland(context, mc, state, alphaInt);
            case INFO -> drawInfoIsland(context, mc, state, alphaInt);
        }
    }

    private static void drawWatermarkIsland(DrawContext context, MinecraftClient mc, IslandState state, int alphaInt) {
        // Тот самый фирменный глубокий черный фон с правильным блюром
        drawBackground(context, state.x, state.y, state.width, state.height, 5.0f, alphaInt);

        boolean showName = LexoraGui.moduleStates.getOrDefault("Info Show Name", true);
        boolean showUuid = LexoraGui.moduleStates.getOrDefault("Info Show UUID", true);
        boolean showFps = LexoraGui.moduleStates.getOrDefault("Info Show FPS", true);
        boolean showTps = LexoraGui.moduleStates.getOrDefault("Info Show TPS", false);

        float x = state.x + 7.0f;
        float centerY = state.y + state.height / 2.0f;

        boolean first = true;

        if (showName) {
            LexoraIcons.draw(context, LexoraIcons.Icon.PLAYER, x, centerY - ICON_SIZE / 2.0f, ICON_SIZE, HudThemeHelper.getTextColor(alphaInt));
            x += ICON_SIZE + 4.0f;

            String username = getUsername(mc);
            drawString(context, username, x, centerY - FONT_SIZE / 2.0f, HudThemeHelper.getTextColor(alphaInt));
            x += width(username);

            if (showUuid) {
                String siteUid = System.getProperty("lexora.uid", com.lexoravisauls.client.badge.LexoraAccount.uuid != null ? com.lexoravisauls.client.badge.LexoraAccount.uuid : "1");
                if (siteUid == null || siteUid.isEmpty() || siteUid.equals("???")) siteUid = com.lexoravisauls.client.badge.LexoraAccount.uuid != null ? com.lexoravisauls.client.badge.LexoraAccount.uuid : "1";
                String drawStr = "UID: " + siteUid;

                x += 4.0f;
                context.getMatrices().push();
                context.getMatrices().scale(0.8f, 0.8f, 1.0f);
                float scaledX = x / 0.8f;
                float scaledY = (centerY - (FONT_SIZE * 0.8f) / 2.0f + 0.5f) / 0.8f;
                drawString(context, drawStr, scaledX, scaledY, HudThemeHelper.getSecondaryTextColor(alphaInt));
                context.getMatrices().pop();

                x += width(drawStr) * 0.8f;
            }

            first = false;
        }

        if (showFps) {
            if (!first) {
                x += 7.0f;
                drawString(context, "|", x, centerY - FONT_SIZE / 2.0f, HudThemeHelper.getSecondaryTextColor(alphaInt));
                x += separatorWidth() + 7.0f;
            }

            LexoraIcons.draw(context, LexoraIcons.Icon.MONITOR, x, centerY - ICON_SIZE / 2.0f, ICON_SIZE, HudThemeHelper.getTextColor(alphaInt));
            x += ICON_SIZE + 4.0f;

            x = drawAnimatedMetric(context, "fps", x, centerY - FONT_SIZE / 2.0f, HudThemeHelper.getTextColor(alphaInt));
            x += 2.0f;

            drawString(context, "fps", x, centerY - FONT_SIZE / 2.0f, HudThemeHelper.getSecondaryTextColor(alphaInt));
            x += width("fps");

            first = false;
        }

        if (showTps) {
            if (!first) {
                x += 7.0f;
                drawString(context, "|", x, centerY - FONT_SIZE / 2.0f, HudThemeHelper.getSecondaryTextColor(alphaInt));
                x += separatorWidth() + 7.0f;
            }

            LexoraIcons.draw(context, LexoraIcons.Icon.GEAR, x, centerY - ICON_SIZE / 2.0f, ICON_SIZE, HudThemeHelper.getTextColor(alphaInt));
            x += ICON_SIZE + 4.0f;

            x = drawAnimatedMetric(context, "tps", x, centerY - FONT_SIZE / 2.0f, HudThemeHelper.getTextColor(alphaInt));
            x += 2.0f;

            drawString(context, "tps", x, centerY - FONT_SIZE / 2.0f, HudThemeHelper.getSecondaryTextColor(alphaInt));
            x += width("tps");

            first = false;
        }
    }

    private static void drawInfoIsland(DrawContext context, MinecraftClient mc, IslandState state, int alphaInt) {
        drawBackground(context, state.x, state.y, state.width, state.height, 5.0f, alphaInt);

        boolean showCoords = LexoraGui.moduleStates.getOrDefault("Info Show Coords", true);
        boolean showBps = LexoraGui.moduleStates.getOrDefault("Info Show BPS", true);
        boolean showServer = LexoraGui.moduleStates.getOrDefault("Info Show Server", false);

        float x = state.x + 8.0f;
        float y = state.y + 4.5f;

        boolean first = true;

        if (showCoords) {
            LexoraIcons.draw(context, LexoraIcons.Icon.COMPASS, x, state.y + 5.0f, ICON_SIZE, HudThemeHelper.getTextColor(alphaInt));
            x += ICON_SIZE + 4.0f;

            x = drawAnimatedCoordsPart(context, x, y, "x", "x", alphaInt);
            drawString(context, "|", x, y, HudThemeHelper.getSecondaryTextColor(alphaInt));
            x += separatorWidth() + 5.0f;

            x = drawAnimatedCoordsPart(context, x, y, "y", "y", alphaInt);
            drawString(context, "|", x, y, HudThemeHelper.getSecondaryTextColor(alphaInt));
            x += separatorWidth() + 5.0f;

            x = drawAnimatedCoordsPart(context, x, y, "z", "z", alphaInt);

            first = false;
        }

        if (showBps) {
            if (!first) x += 10.0f;

            LexoraIcons.draw(context, LexoraIcons.Icon.LIGHTNING, x, state.y + 5.0f, ICON_SIZE, HudThemeHelper.getTextColor(alphaInt));
            x += ICON_SIZE + 4.0f;

            drawString(context, "|", x, y, HudThemeHelper.getSecondaryTextColor(alphaInt));
            x += separatorWidth() + 5.0f;

            x = drawAnimatedMetric(context, "bps", x, y, HudThemeHelper.getTextColor(alphaInt));
            x += 2.0f;

            drawString(context, "b/s", x, y, HudThemeHelper.getSecondaryTextColor(alphaInt));
            x += width("b/s");

            first = false;
        }

        if (showServer) {
            if (!first) x += 10.0f;

            LexoraIcons.draw(context, LexoraIcons.Icon.SIGNAL, x, state.y + 5.0f, ICON_SIZE, HudThemeHelper.getTextColor(alphaInt));
            x += ICON_SIZE + 4.0f;

            drawString(context, trimServer(getServerText(mc)), x, y, HudThemeHelper.getTextColor(alphaInt));
        }
    }

    private static float drawAnimatedCoordsPart(DrawContext context, float x, float y, String label, String metricKey, int alphaInt) {
        drawString(context, label, x, y, HudThemeHelper.getSecondaryTextColor(alphaInt));
        x += width(label) + 2.0f;
        x = drawAnimatedMetric(context, metricKey, x, y, HudThemeHelper.getTextColor(alphaInt));
        x += 6.0f;
        return x;
    }

    private static float drawAnimatedMetric(DrawContext context, String key, float x, float y, int color) {
        MetricAnimState state = getMetric(key);
        drawAnimatedMetricText(context, state.lastText, state.oldText, x, y, state.animStart, color);
        return x + getAnimatedMetricWidth(state.lastText, state.oldText);
    }

    private static float getMetricAnimWidth(String key) {
        MetricAnimState state = getMetric(key);
        return getAnimatedMetricWidth(state.lastText, state.oldText);
    }

    private static float getWatermarkWidth(MinecraftClient mc) {
        boolean showName = LexoraGui.moduleStates.getOrDefault("Info Show Name", true);
        boolean showUuid = LexoraGui.moduleStates.getOrDefault("Info Show UUID", true);
        boolean showFps = LexoraGui.moduleStates.getOrDefault("Info Show FPS", true);
        boolean showTps = LexoraGui.moduleStates.getOrDefault("Info Show TPS", false);

        float result = 7.0f + 7.0f;
        boolean first = true;

        if (showName) {
            float nameW = width(getUsername(mc));
            float uuidW = 0.0f;
            if (showUuid) {
                String siteUid = System.getProperty("lexora.uid", com.lexoravisauls.client.badge.LexoraAccount.uuid != null ? com.lexoravisauls.client.badge.LexoraAccount.uuid : "1");
                if (siteUid == null || siteUid.isEmpty() || siteUid.equals("???")) siteUid = com.lexoravisauls.client.badge.LexoraAccount.uuid != null ? com.lexoravisauls.client.badge.LexoraAccount.uuid : "1";
                uuidW = 4.0f + width("UID: " + siteUid) * 0.8f;
            }

            result += ICON_SIZE + 4.0f + nameW + uuidW;
            first = false;
        }

        if (showFps) {
            if (!first) result += 7.0f + separatorWidth() + 7.0f;
            result += ICON_SIZE + 4.0f + getMetricAnimWidth("fps") + 2.0f + width("fps");
            first = false;
        }

        if (showTps) {
            if (!first) result += 7.0f + separatorWidth() + 7.0f;
            result += ICON_SIZE + 4.0f + getMetricAnimWidth("tps") + 2.0f + width("tps");
            first = false;
        }

        return Math.max(32.0f, result);
    }

    private static float getInfoWidth(MinecraftClient mc) {
        boolean showCoords = LexoraGui.moduleStates.getOrDefault("Info Show Coords", true);
        boolean showBps = LexoraGui.moduleStates.getOrDefault("Info Show BPS", true);
        boolean showServer = LexoraGui.moduleStates.getOrDefault("Info Show Server", false);

        float result = 8.0f + 8.0f;
        boolean first = true;

        if (showCoords) {
            result += ICON_SIZE + 4.0f;
            result += width("x") + 2.0f + getMetricAnimWidth("x") + 6.0f;
            result += separatorWidth() + 5.0f;
            result += width("y") + 2.0f + getMetricAnimWidth("y") + 6.0f;
            result += separatorWidth() + 5.0f;
            result += width("z") + 2.0f + getMetricAnimWidth("z") + 6.0f;
            first = false;
        }

        if (showBps) {
            if (!first) result += 10.0f;
            result += ICON_SIZE + 4.0f + separatorWidth() + 5.0f + getMetricAnimWidth("bps") + 2.0f + width("b/s");
            first = false;
        }

        if (showServer) {
            if (!first) result += 10.0f;
            result += ICON_SIZE + 4.0f + width(trimServer(getServerText(mc)));
        }

        return Math.max(40.0f, result);
    }

    private static void updateBps(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        double deltaTime = (now - lastBpsUpdateTime) / 1000.0;

        if (lastBpsUpdateTime > 0L && deltaTime > 0.0) {
            double dx = mc.player.getX() - lastX;
            double dz = mc.player.getZ() - lastZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double instantBps = distance / deltaTime;

            currentBps += (instantBps - currentBps) * BPS_SMOOTHING;
            targetBps = roundToStep(currentBps, 0.50);
        }

        displayBps += (targetBps - displayBps) * DISPLAY_SMOOTHING;

        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        lastBpsUpdateTime = now;
    }

    private static String getUsername(MinecraftClient mc) {
        boolean streamerMode = LexoraGui.moduleStates.getOrDefault("Streamer Mode", false);
        boolean hideName = streamerMode && LexoraGui.moduleStates.getOrDefault("Hide Name", true);
        if (hideName) return "Protected";

        try {
            String accountName = com.lexoravisauls.client.badge.LexoraAccount.username;
            if (accountName != null && !accountName.trim().isEmpty() && !accountName.equals("null")) {
                return accountName;
            }
        } catch (Throwable ignored) {}

        return mc.getSession().getUsername();
    }

    private static float getClientTps() {
        return 20.0f;
    }

    private static String getServerText(MinecraftClient mc) {
        if (mc.isInSingleplayer()) return "Singleplayer";
        ServerInfo serverInfo = mc.getCurrentServerEntry();
        if (serverInfo == null || serverInfo.address == null || serverInfo.address.isBlank()) {
            return "Multiplayer";
        }
        return serverInfo.address;
    }

    private static String trimServer(String server) {
        if (server == null || server.isBlank()) return "Unknown";
        String value = server.trim().toLowerCase();
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(0, colon);
        if (value.startsWith("mc.")) value = value.substring(3);
        if (value.startsWith("play.")) value = value.substring(5);
        if (value.startsWith("join.")) value = value.substring(5);
        return value;
    }

    private static void drawString(DrawContext context, String text, float x, float y, int color) {
        if (text == null || text.trim().isEmpty()) return;
        getFont().draw(context.getMatrices(), text, x, y, FONT_SIZE, color);
    }

    private static float width(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0f;
        return getFont().getWidth(text, FONT_SIZE);
    }

    private static float separatorWidth() {
        return width("|");
    }

    // Тот самый метод отрисовки из Delta-стиля!
    private static void drawBackground(DrawContext context, float x, float y, float width, float height, float radius, int alpha) {
    boolean blurEnabled = ClientData.moduleStates.containsKey("Info HUD Blur") ? ClientData.moduleStates.get("Info HUD Blur") : LexoraGui.moduleStates.getOrDefault("Info HUD Blur", false);
    HudThemeHelper.drawHudPanel(context, x, y, width, height, radius, alpha, blurEnabled);
}

private static void drawAnimatedMetricText(DrawContext context, String newText, String oldText, float x, float y, long startTime, int color) {
        float progress = clamp01((System.currentTimeMillis() - startTime) / (float) NUMBER_ANIM_DURATION);

        if (oldText == null || oldText.isEmpty() || progress >= 1.0f) {
            drawString(context, newText, x, y, color);
            return;
        }

        float currentX = x;
        int maxLen = Math.max(newText.length(), oldText.length());

        for (int i = 0; i < maxLen; i++) {
            String newChar = i < newText.length() ? String.valueOf(newText.charAt(i)) : "";
            String oldChar = i < oldText.length() ? String.valueOf(oldText.charAt(i)) : "";

            float slotWidth = Math.max(width(newChar), width(oldChar));

            if (newChar.equals(oldChar)) {
                if (!newChar.isEmpty()) drawString(context, newChar, currentX, y, color);
            } else {
                float eased = easeOutCubic(progress);
                int alphaBase = (color >> 24) & 0xFF;
                int fadeOutAlpha = (int) (alphaBase * (1.0f - eased));
                int fadeInAlpha = (int) (alphaBase * eased);

                if (!oldChar.isEmpty()) drawString(context, oldChar, currentX, y + eased * NUMBER_ANIM_OFFSET, replaceAlpha(color, fadeOutAlpha));
                if (!newChar.isEmpty()) drawString(context, newChar, currentX, y + (1.0f - eased) * NUMBER_ANIM_OFFSET, replaceAlpha(color, fadeInAlpha));
            }
            currentX += slotWidth;
        }
    }

    private static float getAnimatedMetricWidth(String current, String previous) {
        int maxLen = Math.max(current.length(), previous.length());
        float total = 0;
        for (int i = 0; i < maxLen; i++) {
            String newChar = i < current.length() ? String.valueOf(current.charAt(i)) : "";
            String oldChar = i < previous.length() ? String.valueOf(previous.charAt(i)) : "";
            total += Math.max(width(newChar), width(oldChar));
        }
        return total;
    }

    private static double roundToStep(double value, double step) {
        return Math.round(value / step) * step;
    }

    private static float lerp(float current, float target, float deltaTime) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * SIZE_ANIMATION_SPEED));
        return current + (target - current) * factor;
    }

    private static int replaceAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3.0f);
    }
}