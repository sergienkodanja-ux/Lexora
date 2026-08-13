package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
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

    /*
     * Чем больше значение, тем быстрее zoom доходит до цели.
     * Само значение берётся из GUI: "Zoom Smooth".
     */
    private static final float MIN_SMOOTH_SPEED = 4.0f;
    private static final float MAX_SMOOTH_SPEED = 34.0f;

    private static final float SNAP_DISTANCE = 0.0008f;
    private static final float SNAP_VELOCITY = 0.0008f;

    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            reset();
            return;
        }

        if (!LexoraGui.moduleStates.getOrDefault("Zoom", false)) {
            reset();

            /*
             * ФИКС: раньше reset() всегда обнулял wasPressed в false, пока модуль
             * выключен. Из-за этого если кнопка "Zoom Action" была уже физически
             * зажата в момент включения модуля (например, той же клавишей, что и
             * тумблер модуля), на следующем тике pressed=true и wasPressed=false
             * совпадали случайно -> zoom включался/близился мгновенно, одним и тем
             * же нажатием, которое включило сам модуль.
             *
             * Синхронизируем wasPressed с реальным физическим состоянием кнопки
             * даже когда модуль выключен, чтобы включение модуля само по себе
             * никогда не считалось "новым нажатием" бинда зума.
             */
            int bindKey = LexoraGui.numSettings.getOrDefault("Zoom Action", -1f).intValue();
            wasPressed = bindKey != -1 && mc.currentScreen == null && isBindPressed(mc, bindKey);
            return;
        }

        int bindKey = LexoraGui.numSettings.getOrDefault("Zoom Action", -1f).intValue();

        // Зум сработает только если закрыты все меню и чат
        boolean pressed = bindKey != -1 && mc.currentScreen == null && isBindPressed(mc, bindKey);

        String mode = LexoraGui.modeSettings.getOrDefault("Zoom Mode", "Hold");

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
            float saved = LexoraGui.numSettings.getOrDefault("Zoom Value", DEFAULT_ZOOM);
            targetZoom = clamp(saved, MIN_ZOOM, MAX_ZOOM);
        } else {
            targetZoom = 1.0f;
        }

        /*
         * Немного обновляем и в tick, чтобы состояние не стояло,
         * если FOV временно не запрашивается.
         */
        updateSmoothZoom();
    }

    public static boolean onScroll(double vertical) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // запрещаем скроллить зум, если открыт чат или меню
        if (mc != null && mc.currentScreen != null) {
            return false;
        }

        if (!LexoraGui.moduleStates.getOrDefault("Zoom", false)) {
            return false;
        }

        if (!isZoomActive()) {
            return false;
        }

        float zoom = LexoraGui.numSettings.getOrDefault("Zoom Value", DEFAULT_ZOOM);
        float step = LexoraGui.numSettings.getOrDefault("Zoom Scroll Step", 0.35f);

        step = clamp(step, 0.05f, 3.0f);

        if (vertical > 0.0) {
            zoom += step;
        } else if (vertical < 0.0) {
            zoom -= step;
        }

        zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);

        LexoraGui.numSettings.put("Zoom Value", zoom);
        targetZoom = zoom;

        return true;
    }

    public static float modifyFov(float originalFov) {
        if (!LexoraGui.moduleStates.getOrDefault("Zoom", false)) {
            return originalFov;
        }

        updateSmoothZoom();

        if (currentZoom <= 1.001f) {
            return originalFov;
        }

        return originalFov / currentZoom;
    }

    public static boolean isZoomActive() {
        String mode = LexoraGui.modeSettings.getOrDefault("Zoom Mode", "Hold");

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

        /*
         * Защита от огромного скачка после лаг-спайка / alt-tab.
         */
        deltaSeconds = clamp(deltaSeconds, 0.0f, 0.08f);

        targetZoom = clamp(targetZoom, MIN_ZOOM, MAX_ZOOM);

        float smoothSetting = LexoraGui.numSettings.getOrDefault("Zoom Smooth", 0.18f);
        smoothSetting = clamp(smoothSetting, 0.01f, 1.0f);

        float smoothSpeed = lerp(MIN_SMOOTH_SPEED, MAX_SMOOTH_SPEED, smoothSetting);

        currentZoom = smoothDamp(
                currentZoom,
                targetZoom,
                deltaSeconds,
                smoothSpeed
        );

        currentZoom = clamp(currentZoom, MIN_ZOOM, MAX_ZOOM);

        if (Math.abs(currentZoom - targetZoom) < SNAP_DISTANCE
                && Math.abs(zoomVelocity) < SNAP_VELOCITY) {
            currentZoom = targetZoom;
            zoomVelocity = 0.0f;
        }
    }

    /*
     * Critically damped spring.
     * Даёт плавное приближение без резкого дёргания и без overshoot.
     */
    private static float smoothDamp(float current, float target, float deltaSeconds, float speed) {
        if (deltaSeconds <= 0.0f) {
            return current;
        }

        speed = Math.max(0.001f, speed);

        float omega = speed;
        float x = omega * deltaSeconds;

        float exp = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);

        float change = current - target;
        float temp = (zoomVelocity + omega * change) * deltaSeconds;

        zoomVelocity = (zoomVelocity - omega * temp) * exp;

        return target + (change + temp) * exp;
    }

    // 🔥 ВОТ ТОТ САМЫЙ БЕЗОПАСНЫЙ МЕТОД 🔥
    private static boolean isBindPressed(MinecraftClient mc, int key) {
        if (key == -1) return false;
        long window = mc.getWindow().getHandle();
        try {
            if (key < 0) {
                int mouseButton = Math.abs(key);

                // Нормализация отрицательных биндов
                if (mouseButton >= 100) {
                    mouseButton -= 100;
                } else {
                    mouseButton -= 1;
                }

                // Строгая защита: GLFW поддерживает только кнопки от 0 до 7
                if (mouseButton >= 0 && mouseButton <= 7) {
                    return GLFW.glfwGetMouseButton(window, mouseButton) == GLFW.GLFW_PRESS;
                }
                return false;
            }

            // Если это кнопка клавиатуры
            return InputUtil.isKeyPressed(window, key);
        } catch (Exception e) {
            return false;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}