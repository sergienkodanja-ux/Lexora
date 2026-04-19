package com.lexoravisauls.client.utils;

public class DynamicIslandManager {

    private static String targetModuleName = "Lexora";
    private static NotificationState state = NotificationState.DEFAULT;
    private static float revertTimer = 0;

    public enum NotificationState {
        DEFAULT(0xFFB533FF), // Фиолетовая
        ENABLED(0xFF44FF44), // Зеленая
        DISABLED(0xFFFF3333); // Красная

        public final int color;
        NotificationState(int color) { this.color = color; }
    }

    public static void tick() {
        if (revertTimer > 0) {
            revertTimer--;
            if (revertTimer <= 0) {
                state = NotificationState.DEFAULT;
                targetModuleName = "Lexora"; // К какому тексту вернуться
            }
        }
    }

    public static void notifyModEnabled(String modName) {
        targetModuleName = modName + " Enabled";
        state = NotificationState.ENABLED;
        revertTimer = 60; // 3 секунды
    }

    public static void notifyModDisabled(String modName) {
        targetModuleName = modName + " Disabled";
        state = NotificationState.DISABLED;
        revertTimer = 60;
    }

    public static String getTargetText() { return targetModuleName; }
    public static NotificationState getState() { return state; }
}