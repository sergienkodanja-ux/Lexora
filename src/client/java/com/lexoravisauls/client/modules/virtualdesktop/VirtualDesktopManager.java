package com.lexoravisauls.client.modules.virtualdesktop;

import com.lexoravisauls.client.core.ClientData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.event.InputEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal Virtual Desktop & Google Chrome Web Browser Manager.
 * Features:
 * - True multi-tab system: multiple independent tabs with real switching and closing.
 * - Universal web page support: browse ANY website on the internet with smooth wheel scrolling.
 * - Built-in YouTube support: browse real videos and watch video streams directly on the 3D monitor.
 * - Unlimited, scrollable search results without artificial caps.
 * - In-world typing without external modal windows.
 */
public final class VirtualDesktopManager {

    private static VirtualDesktopManager instance;

    // Virtual Screen Dimensions in Pixels (matches Chrome proportions)
    public static final float V_WIDTH = 760.0f;
    public static final float V_HEIGHT = 450.0f;

    // Outer frame physical tolerances (in meters)
    public static final float BORDER = 0.035f;
    public static final float VISOR_EXTRA = 0.065f;

    public enum BrowserPage {
        NEW_TAB,
        SEARCH_RESULTS,
        ARTICLE,
        LIVE_MIRROR
    }

    /**
     * Represents a single browser tab in Google Chrome.
     */
    public static class WebTab {
        public BrowserPage page = BrowserPage.NEW_TAB;
        public String title = "Google";
        public String url = "https://www.google.com";
        public String searchQuery = "";
        public List<WebSearchService.SearchResult> searchResults = new ArrayList<>(WebSearchService.getDefaultResults());
        public float searchScrollY = 0f;

        // Web Page Reader / YouTube data
        public String webPageTitle = "";
        public String webPageUrl = "";
        public String webPageContent = "";
        public List<String> webPageLines = new ArrayList<>();
        public float pageScrollY = 0f;

        // YouTube State
        public boolean isYouTube = false;
        public boolean isYouTubeWatch = false;
        public boolean isVideoPlaying = true;
        public float videoProgress = 0.12f;
        public List<WebSearchService.WebPageData.YouTubeVideo> videos = new ArrayList<>();

        public WebTab(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    // Fixed 3D World Transform (Floating monitor sits stably in world space)
    private double posX = 0.0;
    private double posY = 0.0;
    private double posZ = 0.0;
    private float yaw = 0.0f;
    private float pitch = 0.0f;
    private boolean isPlaced = false;

    // Computed Basis Vectors
    private Vec3d normal = new Vec3d(0, 0, 1);
    private Vec3d right = new Vec3d(1, 0, 0);
    private Vec3d up = new Vec3d(0, 1, 0);

    // Crosshair Raycast Interaction State
    private boolean isAimingAtScreen = false;
    private double currentU = 0.5;
    private double currentV = 0.5;
    private float virtualMouseX = 380.0f;
    private float virtualMouseY = 225.0f;
    private Vec3d worldHitPoint = null;
    private String hoveredAction = null;
    private boolean isHoveringHandle = false;

    // Smooth Dragging State (Hold top header or subtle corners to reposition)
    private boolean isDragging = false;
    private double dragDistance = 2.5;

    // Multi-Tab System
    private final List<WebTab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;

    // Live autocomplete suggestions for active input
    private List<String> searchSuggestions = new ArrayList<>();

    // In-World Typing State (отключает ходьбу и слушает ввод без всплывающих окон)
    private boolean isTypingOmnibox = false;
    private boolean isPageFocused = false;
    private String typingQuery = "";
    private long typingStartTime = 0;
    private long lastBrowserStartAttempt = 0L;
    private boolean wasEnabledLastTick = false;

    private VirtualDesktopManager() {
        // Initialize with default Google homepage tab
        tabs.add(new WebTab("Google", "https://www.google.com"));
    }

    public static synchronized VirtualDesktopManager getInstance() {
        if (instance == null) {
            instance = new VirtualDesktopManager();
        }
        return instance;
    }

    public boolean isEnabled() {
        return ClientData.moduleStates.getOrDefault("Virtual Desktop", false);
    }

    // --- Multi-Tab Management ---

    public List<WebTab> getTabs() {
        return tabs;
    }

    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    public WebTab getActiveTab() {
        if (tabs.isEmpty()) {
            tabs.add(new WebTab("Google", "https://www.google.com"));
            activeTabIndex = 0;
        }
        if (activeTabIndex >= tabs.size()) {
            activeTabIndex = tabs.size() - 1;
        }
        return tabs.get(activeTabIndex);
    }

    public void addNewTab() {
        if (tabs.size() < 7) {
            WebTab newTab = new WebTab("Новая вкладка", "https://www.google.com");
            tabs.add(newTab);
            activeTabIndex = tabs.size() - 1;
            setTyping(false);
            setPageFocused(false);
            HeadlessBrowserService.getInstance().navigate(newTab.url);
            playClickSound();
            feedback("§d[Google Chrome] §fОткрыта новая вкладка (" + tabs.size() + ")");
        } else {
            feedback("§d[Google Chrome] §7Максимальное число вкладок: 7.");
        }
    }

    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        playClickSound();

        if (tabs.size() > 1) {
            tabs.remove(index);
            if (activeTabIndex >= tabs.size()) {
                activeTabIndex = tabs.size() - 1;
            }
            WebTab tab = getActiveTab();
            HeadlessBrowserService.getInstance().navigate(tab.url);
            feedback("§d[Google Chrome] §fВкладка закрыта.");
        } else {
            WebTab t = tabs.get(0);
            t.title = "Google";
            t.url = "https://www.google.com";
            HeadlessBrowserService.getInstance().navigate(t.url);
            feedback("§d[Google Chrome] §fВкладка сброшена на главную страницу.");
        }
    }

    public void selectTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            activeTabIndex = index;
            setTyping(false);
            setPageFocused(false);
            WebTab tab = tabs.get(index);
            HeadlessBrowserService.getInstance().navigate(tab.url);
            playClickSound();
        }
    }

    // --- State Accessors Delegated to Active Tab ---

    public boolean isLiveMirrorMode() {
        return getActiveTab().page == BrowserPage.LIVE_MIRROR;
    }

    public void setLiveMirrorMode(boolean live) {
        WebTab tab = getActiveTab();
        tab.page = live ? BrowserPage.LIVE_MIRROR : BrowserPage.NEW_TAB;
        tab.title = live ? "Экран ПК (Зеркало)" : "Google";
        tab.url = live ? "desktop://windows/display1" : "https://www.google.com";
    }

    public BrowserPage getCurrentPage() {
        return getActiveTab().page;
    }

    public String getTabTitle() {
        String t = HeadlessBrowserService.getInstance().getPageTitle();
        if (t != null && !t.isEmpty()) return t;
        return getActiveTab().title;
    }

    public String getCurrentUrl() {
        String u = HeadlessBrowserService.getInstance().getCurrentUrl();
        if (u != null && !u.isEmpty()) return u;
        return getActiveTab().url;
    }

    public void onBrowserNavigated(String url) {
        getActiveTab().url = url;
    }

    public void onBrowserTitleChanged(String title) {
        getActiveTab().title = title;
    }

    public String getSearchQuery() {
        return getActiveTab().searchQuery;
    }

    public List<WebSearchService.SearchResult> getSearchResults() {
        return getActiveTab().searchResults;
    }

    public float getSearchScrollY() {
        return getActiveTab().searchScrollY;
    }

    public float getArticleScrollY() {
        return getActiveTab().pageScrollY;
    }

    public String getArticleTitle() {
        return getActiveTab().webPageTitle;
    }

    public String getArticleUrl() {
        return getActiveTab().webPageUrl;
    }

    public String getArticleContent() {
        return getActiveTab().webPageContent;
    }

    public List<String> getArticleLines() {
        return getActiveTab().webPageLines;
    }

    public boolean isYouTube() {
        return getActiveTab().isYouTube;
    }

    public boolean isYouTubeWatch() {
        return getActiveTab().isYouTubeWatch;
    }

    public boolean isVideoPlaying() {
        return getActiveTab().isVideoPlaying;
    }

    public float getVideoProgress() {
        return getActiveTab().videoProgress;
    }

    public List<WebSearchService.WebPageData.YouTubeVideo> getYouTubeVideos() {
        return getActiveTab().videos;
    }

    public List<String> getSearchSuggestions() {
        return searchSuggestions;
    }

    // --- In-World Typing State ---

    public boolean isTyping() {
        return isTypingOmnibox || isPageFocused;
    }

    public boolean isTypingOmnibox() {
        return isTypingOmnibox;
    }

    public boolean isPageFocused() {
        return isPageFocused;
    }

    public void setTypingOmnibox(boolean typing) {
        this.isTypingOmnibox = typing;
        if (typing) {
            this.isPageFocused = false;
            this.typingQuery = getCurrentUrl();
            this.typingStartTime = System.currentTimeMillis();
            playSnapSound();
        }
    }

    public void setPageFocused(boolean focused) {
        this.isPageFocused = focused;
        if (focused) {
            this.isTypingOmnibox = false;
        }
    }

    public void setTyping(boolean typing) {
        this.isTypingOmnibox = typing;
        if (!typing) {
            this.isPageFocused = false;
            this.searchSuggestions.clear();
        }
    }

    public String getTypingQuery() {
        return typingQuery != null ? typingQuery : "";
    }

    public void submitOmniboxSearch() {
        String input = (typingQuery != null) ? typingQuery.trim() : "";
        setTyping(false);
        if (input.isEmpty()) return;

        playClickSound();
        String targetUrl;

        if (input.equalsIgnoreCase("ютуб") || input.equalsIgnoreCase("youtube") || input.equalsIgnoreCase("youtube.com")) {
            targetUrl = "https://www.youtube.com";
        } else if (input.equalsIgnoreCase("гугл") || input.equalsIgnoreCase("google") || input.equalsIgnoreCase("google.com")) {
            targetUrl = "https://www.google.com";
        } else if (input.equalsIgnoreCase("вк") || input.equalsIgnoreCase("vk") || input.equalsIgnoreCase("vk.com")) {
            targetUrl = "https://vk.com";
        } else if (input.startsWith("http://") || input.startsWith("https://")) {
            targetUrl = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            targetUrl = "https://" + input;
        } else {
            targetUrl = "https://www.google.com/search?q=" + URLEncoder.encode(input, StandardCharsets.UTF_8);
        }

        WebTab tab = getActiveTab();
        tab.url = targetUrl;
        tab.title = targetUrl;
        HeadlessBrowserService.getInstance().navigate(targetUrl);
        feedback("§d[Google Chrome] §fОткрываю сайт: §b" + targetUrl);
    }

    public boolean handleKeyboardKey(int key, int scancode, int action, int modifiers) {
        if (!isTyping()) return false;

        // Pass-through Minecraft functional keys (F1 HUD, F2 Screenshot, F3 Debug, F5 Perspective, F11 Fullscreen)
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) {
            return false;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (action == GLFW.GLFW_PRESS) {
                setTyping(false);
                setPageFocused(false);
                playSnapSound();
            }
            return true;
        }

        if (isTypingOmnibox) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (action == GLFW.GLFW_PRESS) {
                    submitOmniboxSearch();
                }
                return true;
            }

            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                        typingQuery = "";
                    } else if (!typingQuery.isEmpty()) {
                        typingQuery = typingQuery.substring(0, typingQuery.length() - 1);
                    }
                    return true;
                }
                if (key == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        typingQuery += clip.replace("\n", "").replace("\r", "");
                    }
                    return true;
                }
                if (key == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                    typingQuery = "";
                    return true;
                }
            }
            return true;
        }

        if (isPageFocused) {
            HeadlessBrowserService.getInstance().sendKey(key, scancode, action, modifiers);
            return true;
        }

        return true;
    }

    public boolean handleKeyboardChar(int codePoint, int modifiers) {
        if (!isTyping()) return false;

        if (isTypingOmnibox) {
            if (Character.isValidCodePoint(codePoint) && !Character.isISOControl(codePoint)) {
                typingQuery += new String(Character.toChars(codePoint));
                return true;
            }
        }

        if (isPageFocused) {
            if (Character.isValidCodePoint(codePoint)) {
                char[] chars = Character.toChars(codePoint);
                for (char c : chars) {
                    HeadlessBrowserService.getInstance().sendChar(c);
                }
                return true;
            }
        }

        return false;
    }

    public double getPosX() {
        return posX;
    }

    public double getPosY() {
        return posY;
    }

    public double getPosZ() {
        return posZ;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isAimingAtScreen() {
        return isAimingAtScreen;
    }

    public double getCurrentU() {
        return currentU;
    }

    public double getCurrentV() {
        return currentV;
    }

    public float getVirtualMouseX() {
        return virtualMouseX;
    }

    public float getVirtualMouseY() {
        return virtualMouseY;
    }

    public String getHoveredAction() {
        return hoveredAction;
    }

    public boolean isHoveringHandle() {
        return isHoveringHandle;
    }

    public boolean isDragging() {
        return isDragging;
    }

    public float getScreenWidth() {
        float scale = ClientData.numSettings.getOrDefault("VD Scale", 1.8f);
        return 2.2f * scale;
    }

    public float getScreenHeight() {
        float scale = ClientData.numSettings.getOrDefault("VD Scale", 1.8f);
        return 1.4465f * scale; // Exact 16:9 aspect ratio for web content viewport (2.2 * 9/16 / (385/450))
    }

    public float getVisorExtra() {
        return 38.0f * (getScreenHeight() / V_HEIGHT);
    }

    public void placeInFrontOfPlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        ClientPlayerEntity player = mc.player;
        this.dragDistance = ClientData.numSettings.getOrDefault("VD Distance", 2.5f);

        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);

        this.posX = eyePos.x + lookVec.x * dragDistance;
        this.posY = eyePos.y + lookVec.y * dragDistance;
        this.posZ = eyePos.z + lookVec.z * dragDistance;

        // Face player horizontally (level pitch = 0)
        this.yaw = player.getYaw() + 180.0f;
        this.pitch = 0.0f;

        this.isPlaced = true;
        updateBasisVectors();

        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§d[Google] §fБраузер размещен перед вами. §7(Потяните за верхнюю рамку для перемещения)"), false);
        }
    }

    public void updateBasisVectors() {
        float yawRad = (float) Math.toRadians(this.yaw);

        // Forward normal facing the viewer
        double nx = -MathHelper.sin(yawRad);
        double ny = 0.0;
        double nz = MathHelper.cos(yawRad);
        this.normal = new Vec3d(nx, ny, nz).normalize();

        // Right vector along screen width
        this.right = new Vec3d(MathHelper.cos(yawRad), 0, MathHelper.sin(yawRad)).normalize();

        // Up vector
        this.up = new Vec3d(0, 1, 0);
    }

    private float scaleFeedbackAnim = 0.0f;

    public float getScaleFeedbackAnim() {
        return scaleFeedbackAnim;
    }

    public void decayScaleFeedback() {
        if (scaleFeedbackAnim > 0.0f) {
            scaleFeedbackAnim = Math.max(0.0f, scaleFeedbackAnim - 0.015f);
        }
    }

    public void updateDragging(float tickDelta) {
        if (!isDragging) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            isDragging = false;
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(tickDelta);
        double targetX = eye.x + look.x * dragDistance;
        double targetY = eye.y + look.y * dragDistance;
        double targetZ = eye.z + look.z * dragDistance;
        float targetYaw = mc.player.getYaw(tickDelta) + 180.0f;

        // Buttery-smooth exponential interpolation running per-frame at 60/120/240+ FPS
        double factor = 0.16;
        posX += (targetX - posX) * factor;
        posY += (targetY - posY) * factor;
        posZ += (targetZ - posZ) * factor;
        yaw = MathHelper.lerpAngleDegrees((float) factor, yaw, targetYaw);
        pitch = 0.0f;
        updateBasisVectors();
    }

    public void tick(MinecraftClient client) {
        boolean enabled = isEnabled();

        if (enabled && !wasEnabledLastTick) {
            // Module just toggled ON: reset backoff to start immediately!
            lastBrowserStartAttempt = 0L;
        }
        wasEnabledLastTick = enabled;

        if (!enabled) {
            isAimingAtScreen = false;
            isDragging = false;
            hoveredAction = null;
            isHoveringHandle = false;
            HeadlessBrowserService.getInstance().stop();
            ScreenCaptureService.getInstance().stop();
            return;
        }

        // Auto-start headless Chrome when enabled and inside world (with 5-second backoff)
        long now = System.currentTimeMillis();
        if (!HeadlessBrowserService.getInstance().isRunning() && client.world != null && client.player != null) {
            if (now - lastBrowserStartAttempt > 5000L) {
                lastBrowserStartAttempt = now;
                HeadlessBrowserService.getInstance().start();
            }
        }

        // Place monitor in front of player once if not yet placed
        if (!isPlaced || (posX == 0.0 && posY == 0.0 && posZ == 0.0)) {
            if (client.player != null) {
                placeInFrontOfPlayer();
            }
        }

        // Visibility Culling: if player is farther than 18 blocks or facing away, pause decoding to save 100% FPS
        boolean isVisible = false;
        if (client.player != null && isPlaced) {
            Vec3d eye = client.player.getEyePos();
            double d2 = eye.squaredDistanceTo(posX, posY, posZ);
            if (d2 < 18.0 * 18.0) {
                if (isDragging) {
                    isVisible = true;
                } else {
                    Vec3d look = client.player.getRotationVec(1.0f);
                    Vec3d toMon = new Vec3d(posX - eye.x, posY - eye.y, posZ - eye.z).normalize();
                    double dot = look.dotProduct(toMon);
                    isVisible = (dot > -0.15); // Visible in front ~130 degree FOV
                }
            }
        }
        HeadlessBrowserService.getInstance().setCullActive(!isVisible);

        // Video playback progress ticker
        WebTab curTab = getActiveTab();
        if (curTab.page == BrowserPage.ARTICLE && curTab.isYouTubeWatch && curTab.isVideoPlaying) {
            curTab.videoProgress += 0.0006f;
            if (curTab.videoProgress > 1.0f) curTab.videoProgress = 0.0f;
        }

        // Dragging state verification (interpolation runs per-frame in updateDragging)
        if (isDragging) {
            if (client.player == null) {
                isDragging = false;
            } else {
                long window = client.getWindow().getHandle();
                boolean isMouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
                if (!isMouseDown) {
                    isDragging = false;
                    playSnapSound();
                }
            }
        }

        // Screen capture only active if Live Mirror is selected
        if (curTab.page == BrowserPage.LIVE_MIRROR) {
            ScreenCaptureService.getInstance().start();
        } else {
            ScreenCaptureService.getInstance().stop();
        }

        // Advance YouTube video playback progress
        if (curTab.isYouTubeWatch && curTab.isVideoPlaying) {
            curTab.videoProgress += 0.00035f;
            if (curTab.videoProgress > 1.0f) {
                curTab.videoProgress = 0.0f;
            }
        }

        // Raycast crosshair to screen
        performRaycast(client);
    }

    private void performRaycast(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) {
            isAimingAtScreen = false;
            hoveredAction = null;
            isHoveringHandle = false;
            return;
        }

        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0f).normalize();
        Vec3d center = new Vec3d(posX, posY, posZ);

        double denom = look.dotProduct(normal);

        // Not looking at front of screen
        if (denom >= -1e-4) {
            isAimingAtScreen = false;
            hoveredAction = null;
            isHoveringHandle = false;
            return;
        }

        double t = center.subtract(eye).dotProduct(normal) / denom;

        if (t <= 0.1 || t > 25.0) {
            isAimingAtScreen = false;
            hoveredAction = null;
            isHoveringHandle = false;
            return;
        }

        Vec3d hit = eye.add(look.multiply(t));
        Vec3d rel = hit.subtract(center);

        float w = getScreenWidth();
        float h = getScreenHeight();
        float hw = w / 2.0f;
        float hh = h / 2.0f;

        double dx = rel.dotProduct(right);
        double dy = rel.dotProduct(up);

        // Bounds cover full physical frame (screen + visor + casing)
        float visorExtra = getVisorExtra();
        double minX = -hw - BORDER;
        double maxX = hw + BORDER;
        double minY = -hh - BORDER;
        double maxY = hh + visorExtra;

        if (dx >= minX && dx <= maxX && dy >= minY && dy <= maxY) {
            isAimingAtScreen = true;
            worldHitPoint = hit;

            boolean isVisor = (dy >= hh);
            boolean isOuterBorder = (dx < -hw || dx > hw || dy < -hh);

            if (isVisor || isOuterBorder) {
                isHoveringHandle = true;
                hoveredAction = "WIN_DRAG";
                currentU = MathHelper.clamp((dx / w) + 0.5, 0.0, 1.0);
                currentV = MathHelper.clamp(0.5 - (dy / h), 0.0, 1.0);
                virtualMouseX = (float) (currentU * V_WIDTH);
                virtualMouseY = 0.0f;
            } else {
                currentU = (dx / w) + 0.5;
                currentV = 0.5 - (dy / h);
                virtualMouseX = (float) (currentU * V_WIDTH);
                virtualMouseY = (float) (currentV * V_HEIGHT);

                hoveredAction = detectAction(virtualMouseX, virtualMouseY);
                isHoveringHandle = "WIN_DRAG".equals(hoveredAction);

                if (virtualMouseY >= 65.0f) {
                    float normX = MathHelper.clamp(virtualMouseX / V_WIDTH, 0.0f, 1.0f);
                    float normY = MathHelper.clamp((virtualMouseY - 65.0f) / (V_HEIGHT - 65.0f), 0.0f, 1.0f);
                    HeadlessBrowserService.getInstance().sendMouseMove(normX, normY);
                }
            }
        } else {
            isAimingAtScreen = false;
            hoveredAction = null;
            isHoveringHandle = false;
        }
    }

    private String detectAction(float mx, float my) {
        // 1. Top Window Controls & Multi-Tab Strip (vy: 0 to 33)
        if (my >= 0.0f && my <= 33.0f) {
            // Window Close button [ ✕ ]
            if (mx >= 720.0f && mx <= 754.0f) return "WIN_CLOSE";

            // Dynamic Tab Detection across open tabs
            float tabW = Math.min(130.0f, 540.0f / Math.max(1, tabs.size()));
            for (int i = 0; i < tabs.size(); i++) {
                float tx1 = 12.0f + i * (tabW + 4.0f);
                float tx2 = tx1 + tabW;
                if (mx >= tx1 && mx <= tx2) {
                    if (mx >= tx2 - 20.0f && mx <= tx2 - 2.0f) {
                        return "TAB_CLOSE_" + i;
                    }
                    return "TAB_SELECT_" + i;
                }
            }

            // Add Tab Button '+'
            float plusX = 12.0f + tabs.size() * (tabW + 4.0f) + 4.0f;
            if (mx >= plusX && mx <= plusX + 24.0f) {
                return "TAB_ADD";
            }

            // Drag zone on tab bar
            if (mx >= plusX + 28.0f && mx <= 715.0f) {
                return "WIN_DRAG";
            }
        }

        // 2. Navigation Bar & Omnibox (vy: 33 to 65)
        if (my > 33.0f && my < 65.0f) {
            if (mx >= 12.0f && mx <= 34.0f) return "NAV_BACK";
            if (mx >= 38.0f && mx <= 60.0f) return "NAV_FORWARD";
            if (mx >= 64.0f && mx <= 86.0f) return "NAV_REFRESH";
            if (mx >= 90.0f && mx <= 112.0f) return "NAV_HOME";

            // Omnibox URL / Search input
            if (mx >= 118.0f && mx <= 660.0f) return "OMNIBOX";
        }

        // 3. Web Page Viewport Content (vy: 65 to 450)
        if (my >= 65.0f && my <= V_HEIGHT) {
            return "WEB_PAGE";
        }

        return null;
    }

    public static boolean handleMouseButton(int button, int action) {
        VirtualDesktopManager vdm = getInstance();
        if (!vdm.isEnabled()) {
            return false;
        }

        // If user is currently typing and clicks outside the monitor, exit typing mode
        if (vdm.isTyping() && !vdm.isAimingAtScreen()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS) {
                vdm.setTyping(false);
                return true;
            }
            return false;
        }

        if (!vdm.isAimingAtScreen()) {
            return false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (action == GLFW.GLFW_PRESS) {
                // Monitor reposition dragging
                if (vdm.isHoveringHandle || "WIN_DRAG".equals(vdm.hoveredAction)) {
                    vdm.isDragging = true;
                    if (vdm.isTyping()) vdm.setTyping(false);
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player != null) {
                        vdm.dragDistance = mc.player.getEyePos().distanceTo(new Vec3d(vdm.posX, vdm.posY, vdm.posZ));
                        vdm.dragDistance = Math.max(1.2, Math.min(8.0, vdm.dragDistance));
                    }
                    playClickSound();
                    return true;
                }

                String act = vdm.hoveredAction;
                // Click into live Web Page
                if ("WEB_PAGE".equals(act) || vdm.virtualMouseY >= 65.0f) {
                    if (vdm.isTypingOmnibox) vdm.setTypingOmnibox(false);
                    float normX = MathHelper.clamp(vdm.virtualMouseX / V_WIDTH, 0.0f, 1.0f);
                    float normY = MathHelper.clamp((vdm.virtualMouseY - 65.0f) / (V_HEIGHT - 65.0f), 0.0f, 1.0f);
                    HeadlessBrowserService.getInstance().sendMouseClick(normX, normY, 0, true);
                    vdm.setPageFocused(true);
                    return true;
                }

                // Top Bar Controls
                if (act != null) {
                    if (vdm.isTyping() && !"OMNIBOX".equals(act)) {
                        vdm.setTyping(false);
                    }
                    vdm.executeAction(act);
                    return true;
                } else if (vdm.isTyping()) {
                    vdm.setTyping(false);
                    return true;
                }
            } else if (action == GLFW.GLFW_RELEASE) {
                if (vdm.isDragging) {
                    vdm.isDragging = false;
                    playSnapSound();
                    return true;
                }
                if (vdm.virtualMouseY >= 65.0f) {
                    float normX = MathHelper.clamp(vdm.virtualMouseX / V_WIDTH, 0.0f, 1.0f);
                    float normY = MathHelper.clamp((vdm.virtualMouseY - 65.0f) / (V_HEIGHT - 65.0f), 0.0f, 1.0f);
                    HeadlessBrowserService.getInstance().sendMouseClick(normX, normY, 0, false);
                    return true;
                }
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_2) { // Right-Click
            if (vdm.virtualMouseY >= 65.0f) {
                float normX = MathHelper.clamp(vdm.virtualMouseX / V_WIDTH, 0.0f, 1.0f);
                float normY = MathHelper.clamp((vdm.virtualMouseY - 65.0f) / (V_HEIGHT - 65.0f), 0.0f, 1.0f);
                boolean isPress = (action == GLFW.GLFW_PRESS);
                HeadlessBrowserService.getInstance().sendMouseClick(normX, normY, 1, isPress);
                if (isPress) vdm.setPageFocused(true);
                return true;
            }
        }

        return true;
    }

    public static boolean handleMouseScroll(double vertical) {
        VirtualDesktopManager vdm = getInstance();
        if (!vdm.isEnabled() || !vdm.isAimingAtScreen()) {
            return false;
        }

        // 1. Mouse wheel scale / resize monitor:
        // Triggered when dragging the monitor, aiming at visor header / casing handle, or holding Shift / Ctrl
        boolean modifierHeld = Screen.hasShiftDown() || Screen.hasControlDown();

        if (vdm.isDragging || vdm.isHoveringHandle || vdm.virtualMouseY < 65.0f || modifierHeld) {
            float curScale = ClientData.numSettings.getOrDefault("VD Scale", 1.8f);
            float step = (float) (vertical * 0.10f);
            float newScale = MathHelper.clamp(curScale + step, 0.6f, 3.8f);
            ClientData.numSettings.put("VD Scale", newScale);
            vdm.scaleFeedbackAnim = 1.0f;
            playScaleSound();
            return true;
        }

        // 2. Live scroll through the real web page
        if (vdm.virtualMouseY >= 65.0f) {
            float normX = MathHelper.clamp(vdm.virtualMouseX / V_WIDTH, 0.0f, 1.0f);
            float normY = MathHelper.clamp((vdm.virtualMouseY - 65.0f) / (V_HEIGHT - 65.0f), 0.0f, 1.0f);
            HeadlessBrowserService.getInstance().sendMouseWheel(normX, normY, vertical);
            return true;
        }

        return true;
    }

    private static void playScaleSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.5f, 1.6f);
        }
    }

    private void executeAction(String action) {
        playClickSound();

        // 1. Multi-Tab Operations
        if ("TAB_ADD".equals(action)) {
            addNewTab();
            return;
        }

        if (action.startsWith("TAB_CLOSE_")) {
            try {
                int idx = Integer.parseInt(action.substring(10));
                closeTab(idx);
            } catch (Throwable ignored) {}
            return;
        }

        if (action.startsWith("TAB_SELECT_")) {
            try {
                int idx = Integer.parseInt(action.substring(11));
                selectTab(idx);
            } catch (Throwable ignored) {}
            return;
        }

        // 2. Browser Navigation & Controls
        switch (action) {
            case "WIN_CLOSE" -> {
                ClientData.moduleStates.put("Virtual Desktop", false);
                setTyping(false);
                feedback("§d[Google Chrome] §fБраузер закрыт.");
            }
            case "NAV_HOME" -> {
                setTyping(false);
                getActiveTab().url = "https://www.google.com";
                getActiveTab().title = "Google";
                HeadlessBrowserService.getInstance().navigate("https://www.google.com");
            }
            case "NAV_BACK" -> {
                setTyping(false);
                HeadlessBrowserService.getInstance().goBack();
            }
            case "NAV_FORWARD" -> {
                setTyping(false);
                HeadlessBrowserService.getInstance().goForward();
            }
            case "NAV_REFRESH" -> {
                playSnapSound();
                HeadlessBrowserService.getInstance().reload();
            }
            case "OMNIBOX" -> {
                setTypingOmnibox(true);
            }
        }
    }

    public static void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            url = "https://www.google.com";
        }
        getInstance().getActiveTab().url = url;
        getInstance().getActiveTab().title = url;
        HeadlessBrowserService.getInstance().navigate(url);
    }

    private static void playClickSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.6f, 1.4f);
        }
    }

    private static void playSnapSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.6f, 1.6f);
        }
    }

    private static void feedback(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }
}
