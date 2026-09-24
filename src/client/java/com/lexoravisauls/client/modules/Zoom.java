package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class Zoom {

    private static boolean toggleActive = false;
    private static boolean holdActive = false;
    private static boolean wasPressed = false;

    private static float currentZoom = 1.0f;
    private static float targetZoom = 1.0f;
    private static float zoomVelocity = 0.0f;

    private static long lastSmoothNs = 0L;

    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 12.0f;
    private static final float DEFAULT_ZOOM = 4.0f;

    private static final float MIN_SMOOTH_SPEED = 4.0f;
    private static final float MAX_SMOOTH_SPEED = 34.0f;

    private static final float SNAP_DISTANCE = 0.0008f;
    private static final float SNAP_VELOCITY = 0.0008f;

    public static int getZoomBindKey() {
        Integer mb = ClientData.moduleBinds.get("Zoom Action");
        if (mb != null && mb != GLFW.GLFW_KEY_UNKNOWN && mb != -1) return mb;

        Float num = ClientData.numSettings.get("Zoom Action");
        if (num != null && num.intValue() != GLFW.GLFW_KEY_UNKNOWN && num.intValue() != -1) return num.intValue();

        Float lgNum = LexoraGui.numSettings.get("Zoom Action");
        if (lgNum != null && lgNum.intValue() != GLFW.GLFW_KEY_UNKNOWN && lgNum.intValue() != -1) return lgNum.intValue();

        int bm = BindManager.getStoredBindValue("Zoom Action");
        if (bm != GLFW.GLFW_KEY_UNKNOWN && bm != -1) return bm;

        Integer modB = ClientData.moduleBinds.get("Zoom");
        if (modB != null && modB != GLFW.GLFW_KEY_UNKNOWN && modB != -1) return modB;

        return GLFW.GLFW_KEY_C;
    }

    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            reset();
            return;
        }

        int bindKey = getZoomBindKey();
        long window = mc.getWindow() != null ? mc.getWindow().getHandle() : 0L;
        boolean pressed = bindKey != GLFW.GLFW_KEY_UNKNOWN && bindKey != -1 && mc.currentScreen == null && window != 0L && BindManager.isBindDown(window, bindKey);

        String mode = ClientData.modeSettings.getOrDefault("Zoom Mode", LexoraGui.modeSettings.getOrDefault("Zoom Mode", "Hold"));

        if (mode.equalsIgnoreCase("Toggle")) {
            if (pressed && !wasPressed) {
                toggleActive = !toggleActive;
            }
            holdActive = false;
        } else {
            holdActive = pressed;
            if (!pressed) {
                toggleActive = false;
            }
        }

        wasPressed = pressed;

        if (isZoomActive()) {
            float saved = ClientData.numSettings.getOrDefault("Zoom Value", LexoraGui.numSettings.getOrDefault("Zoom Value", DEFAULT_ZOOM));
            targetZoom = clamp(saved, MIN_ZOOM, MAX_ZOOM);
        } else {
            targetZoom = 1.0f;
        }

        updateSmoothZoom();
    }

    public static boolean onScroll(double vertical) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen != null) return false;
        if (!isZoomActive()) return false;

        float zoom = ClientData.numSettings.getOrDefault("Zoom Value", LexoraGui.numSettings.getOrDefault("Zoom Value", DEFAULT_ZOOM));
        float step = ClientData.numSettings.getOrDefault("Zoom Scroll Step", LexoraGui.numSettings.getOrDefault("Zoom Scroll Step", 0.35f));

        step = clamp(step, 0.05f, 3.0f);

        if (vertical > 0.0) {
            zoom += step;
        } else if (vertical < 0.0) {
            zoom -= step;
        }

        zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);

        ClientData.numSettings.put("Zoom Value", zoom);
        LexoraGui.numSettings.put("Zoom Value", zoom);
        targetZoom = zoom;

        return true;
    }

    public static float modifyFov(float originalFov) {
        updateSmoothZoom();
        if (currentZoom <= 1.001f) {
            return originalFov;
        }
        return originalFov / currentZoom;
    }

    public static boolean isZoomActive() {
        String mode = ClientData.modeSettings.getOrDefault("Zoom Mode", LexoraGui.modeSettings.getOrDefault("Zoom Mode", "Hold"));
        if (mode.equalsIgnoreCase("Toggle")) {
            return toggleActive;
        }
        return holdActive;
    }

    public static float getCurrentZoom() {
        return currentZoom;
    }

    public static float getTargetZoom() {
        return targetZoom;
    }

    public static void reset() {
        toggleActive = false;
        holdActive = false;
        wasPressed = false;
        currentZoom = 1.0f;
        targetZoom = 1.0f;
        zoomVelocity = 0.0f;
        lastSmoothNs = 0L;
    }

    private static void updateSmoothZoom() {
        long now = System.nanoTime();
        if (lastSmoothNs == 0L) {
            lastSmoothNs = now;
            currentZoom = clamp(currentZoom, MIN_ZOOM, MAX_ZOOM);
            targetZoom = clamp(targetZoom, MIN_ZOOM, MAX_ZOOM);
            return;
        }

        float deltaSeconds = (now - lastSmoothNs) / 1_000_000_000.0f;
        lastSmoothNs = now;
        deltaSeconds = clamp(deltaSeconds, 0.0f, 0.08f);

        targetZoom = clamp(targetZoom, MIN_ZOOM, MAX_ZOOM);

        float smoothSetting = LexoraGui.numSettings.getOrDefault("Zoom Smooth", 0.18f);
        smoothSetting = clamp(smoothSetting, 0.01f, 1.0f);

        float smoothSpeed = lerp(MIN_SMOOTH_SPEED, MAX_SMOOTH_SPEED, smoothSetting);

        currentZoom = smoothDamp(currentZoom, targetZoom, deltaSeconds, smoothSpeed);
        currentZoom = clamp(currentZoom, MIN_ZOOM, MAX_ZOOM);

        if (Math.abs(currentZoom - targetZoom) < SNAP_DISTANCE && Math.abs(zoomVelocity) < SNAP_VELOCITY) {
            currentZoom = targetZoom;
            zoomVelocity = 0.0f;
        }
    }

    private static float smoothDamp(float current, float target, float deltaSeconds, float speed) {
        if (deltaSeconds <= 0.0f) return current;
        speed = Math.max(0.001f, speed);
        float omega = speed;
        float x = omega * deltaSeconds;
        float exp = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = current - target;
        float temp = (zoomVelocity + omega * change) * deltaSeconds;
        zoomVelocity = (zoomVelocity - omega * temp) * exp;
        return target + (change + temp) * exp;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
