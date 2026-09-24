package com.lexoravisauls.client.utils;

public class DynamicIslandManager {

    private static String targetModuleName = "Lexora";
    private static NotificationState state = NotificationState.DEFAULT;
    private static float revertTimer = 0;

    // Общая длительность показа "какой худ включён" — сокращена в 2 раза (было 60 = 3 сек)
    private static final float REVERT_TICKS = 30f; // 1.5 сек
    // Окно "мига": сколько от начала показа текст модуля имеет право перебивать даже PVP-надпись.
    // Меньше REVERT_TICKS специально — после этого окна, если PVP всё ещё активен, надпись
    // должна вернуться к PVP, а не "доживать" оставшееся время поверх него.
    private static final float RECENT_WINDOW_TICKS = 10f; // ~0.5 сек

    // Тик (System.currentTimeMillis()-based нам тут не подходит, тикаем вместе с игрой),
    // считаем от начала последнего show — используем сам revertTimer как источник правды:
    // "недавно" = revertTimer всё ещё выше (REVERT_TICKS - RECENT_WINDOW_TICKS).

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
        revertTimer = REVERT_TICKS;
    }

    public static void notifyModDisabled(String modName) {
        targetModuleName = modName + " Disabled";
        state = NotificationState.DISABLED;
        revertTimer = REVERT_TICKS;
    }

    public static String getTargetText() { return targetModuleName; }
    public static NotificationState getState() { return state; }

    /**
     * true в первые ~0.5с после тоггла модуля (RECENT_WINDOW_TICKS от начала REVERT_TICKS).
     * DynamicIslandRenderer использует это, чтобы на короткий миг перебить даже PVP-надпись
     * названием только что включённого/выключенного модуля, а затем вернуться к PVP как ни в
     * чём не бывало — а не "доживать" на экране весь оставшийся revertTimer поверх PVP.
     */
    public static boolean isRecentlyTriggered() {
        return revertTimer > (REVERT_TICKS - RECENT_WINDOW_TICKS);
    }
}