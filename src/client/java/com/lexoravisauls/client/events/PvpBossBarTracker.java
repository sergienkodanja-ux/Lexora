package com.lexoravisauls.client.events;

import com.lexoravisauls.client.mixin.BossBarHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сканирует боссбары каждый тик.
 * Если находит PVP-бар — сохраняет секунды и флаг isInPvp.
 * DynamicIslandRenderer читает эти данные для отрисовки.
 *
 * ── Пересинхронизация при продлении кд ──
 * Многие серверы при продлении PVP не создают новый боссбар, а обновляют
 * ТОТ ЖЕ (тот же UUID), просто меняя текст/процент на большее значение.
 * Поэтому мы не полагаемся только на смену UUID: каждый тик сравниваем,
 * что "должно быть" по нашему плавному таймеру, с тем что реально прислал
 * сервер. Если сервер прислал заметно БОЛЬШЕ — значит кд продлили,
 * и мы стартуем плавный таймер заново от актуального значения.
 */
public class PvpBossBarTracker {

    // ── Паттерн: ищем любое число в тексте босс-бара ──
    private static final Pattern SECONDS_PATTERN = Pattern.compile("(\\d+)");

    // ── Ключевые слова PVP (регистронезависимо) ──
    // Добавляй сюда любые слова которые встречаются на твоих серверах
    private static final String[] PVP_KEYWORDS = {
            "pvp", "пвп", "бой", "combat", "fight",
            "не выходите", "не выходи", "сражение",
            "атака", "режим боя", "pvp mode", "in combat",
            "боевой", "битва"
    };

    // Если сервер прислал значение БОЛЬШЕ нашего расчёта больше чем на столько — это продление кд
    private static final float EXTEND_THRESHOLD = 1.5f;
    // Если сервер прислал значение МЕНЬШЕ нашего расчёта больше чем на столько — рассинхрон (лаги/оффсет), тоже ресинк
    private static final float DESYNC_THRESHOLD  = 3.0f;

    // ── Публичное состояние (читает DynamicIslandRenderer) ──
    private static boolean inPvp      = false;
    private static float   pvpSeconds = 30f;
    private static UUID    pvpBarUuid = null;

    // ── Плавный анимационный таймер (считается в реальном времени) ──
    private static long  pvpStartMs     = 0L;
    private static float pvpInitSeconds = 30f;

    // Момент последнего продления — для короткой визуальной вспышки в рендере
    private static long lastExtendMs = -10_000L;

    /**
     * Вызывается из ClientTickEvents в LexoravisaulsClient.
     * Сканирует боссбары и обновляет состояние.
     */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.inGameHud == null) return;

        Map<UUID, ClientBossBar> bars;
        try {
            bars = ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBars();
        } catch (Throwable t) {
            return;
        }

        // Ищем PVP-бар среди всех боссбаров
        UUID  foundUuid    = null;
        Float parsedNumber = null;   // null = число не найдено в тексте
        float foundPercent = 0f;

        for (Map.Entry<UUID, ClientBossBar> entry : bars.entrySet()) {
            ClientBossBar bar = entry.getValue();
            String rawText = bar.getName().getString();
            String lower   = rawText.toLowerCase();

            boolean isPvpBar = false;
            for (String kw : PVP_KEYWORDS) {
                if (lower.contains(kw)) { isPvpBar = true; break; }
            }
            if (!isPvpBar) continue;

            foundUuid    = entry.getKey();
            foundPercent = bar.getPercent();

            Matcher m = SECONDS_PATTERN.matcher(rawText);
            if (m.find()) {
                try {
                    parsedNumber = (float) Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    parsedNumber = null;
                }
            }
            break; // Берём первый найденный PVP-бар
        }

        long now = System.currentTimeMillis();

        if (foundUuid == null) {
            inPvp      = false;
            pvpBarUuid = null;
            return;
        }

        boolean isNewBar = !inPvp || !foundUuid.equals(pvpBarUuid);

        // ── "Авторитетное" значение секунд с сервера на этот тик ──
        float authoritative;
        if (parsedNumber != null) {
            authoritative = parsedNumber;
        } else if (foundPercent > 0f && foundPercent <= 1f) {
            // Числа в тексте нет — прикидываем через процент бара
            float assumedTotal = isNewBar ? 30f : pvpInitSeconds;
            authoritative = Math.max(1f, Math.round(assumedTotal * foundPercent));
        } else {
            authoritative = 30f;
        }

        if (isNewBar) {
            // Новый заход в PVP (другой боссбар) — стартуем таймер с нуля
            pvpStartMs     = now;
            pvpInitSeconds = authoritative;
            pvpBarUuid     = foundUuid;
        } else {
            // Тот же боссбар — проверяем, не продлили ли кд
            float expectedNow   = pvpInitSeconds - (now - pvpStartMs) / 1000f;
            boolean extended    = authoritative > expectedNow + EXTEND_THRESHOLD;
            boolean driftedDown = authoritative < expectedNow - DESYNC_THRESHOLD;

            if (extended || driftedDown) {
                // Кд продлили (или сильно разъехались с сервером) — пересинхронизируем
                pvpStartMs     = now;
                pvpInitSeconds = authoritative;
                if (extended) lastExtendMs = now;
            }
        }

        inPvp      = true;
        pvpSeconds = authoritative;
    }

    // ── Геттеры для DynamicIslandRenderer ──

    /** true если сейчас активен PVP режим */
    public static boolean isInPvp() {
        return inPvp;
    }

    /**
     * Возвращает ПЛАВНЫЕ секунды с учётом реального времени.
     * Даже если сервер обновляет бар раз в секунду — у нас тикает плавно.
     * Корректно пересинхронизируется при продлении кд.
     */
    public static float getSecondsSmooth() {
        if (!inPvp) return 0f;
        long elapsed = System.currentTimeMillis() - pvpStartMs;
        float remaining = pvpInitSeconds - elapsed / 1000f;
        return Math.max(0f, remaining);
    }

    /** Последнее значение секунд с сервера (целое, запасной вариант). */
    public static int getSeconds() {
        return Math.round(pvpSeconds);
    }

    /**
     * 0..1 — сила "вспышки" в момент продления кд, гаснет за ~400мс.
     * Используется рендером для лёгкого акцента на таблетке, чтобы
     * визуально подтвердить игроку что кд обновился (не просто тихо поменялось число).
     */
    public static float getExtendPulse() {
        long since = System.currentTimeMillis() - lastExtendMs;
        if (since < 0 || since > 400) return 0f;
        return 1f - (since / 400f);
    }
}