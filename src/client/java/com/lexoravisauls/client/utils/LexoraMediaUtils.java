package com.lexoravisauls.client.utils;

import net.minecraft.util.Identifier;

public final class LexoraMediaUtils {
    private static boolean initialized = false;

    private LexoraMediaUtils() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        LexoraSpotifyMediaInfoHelper.init();
    }

    public static MediaInfo getCurrentMedia() {
        if (!initialized) {
            init();
        }

        return LexoraSpotifyMediaInfoHelper.getCurrentMedia();
    }

    // ==========================================================
    // === НОВЫЕ МЕТОДЫ ДЛЯ DYNAMIC ISLAND (Обертки данных) ===
    // ==========================================================

    public static boolean hasMedia() {
        MediaInfo info = getCurrentMedia();
        // Музыка активна, если есть хотя бы название или имя автора
        return info != null && (!info.title.isEmpty() || !info.artist.isEmpty());
    }

    public static String getTitle() {
        MediaInfo info = getCurrentMedia();
        return info != null ? info.title : "";
    }

    public static String getAuthor() {
        MediaInfo info = getCurrentMedia();
        return info != null ? info.artist : "";
    }

    public static Identifier getThumbnail() {
        MediaInfo info = getCurrentMedia();
        return info != null ? info.coverId : null;
    }

    public static boolean isPlaying() {
        MediaInfo info = getCurrentMedia();
        return info != null && info.playing;
    }

    // ── НЕПРЕРЫВНЫЙ ТРЕКЕР ВОСПРОИЗВЕДЕНИЯ (Без дёрганий и сбросов) ──
    private static String lastCanonicalTrackKey = "";
    private static long lastReportedPositionMs = -1L;
    private static long lastReportedTimestampMs = 0L;
    private static long trackPlayStartMs = 0L;

    public static synchronized void resetTrackClock() {
        lastCanonicalTrackKey = "";
        lastReportedPositionMs = -1L;
        lastReportedTimestampMs = 0L;
        trackPlayStartMs = System.currentTimeMillis();
    }

    public static synchronized long getProgress() {
        MediaInfo info = getCurrentMedia();
        if (info == null) return 0L;

        String key = LyricsManager.getCanonicalKey(info.title, info.artist);
        long now = System.currentTimeMillis();

        if (!key.equals(lastCanonicalTrackKey)) {
            // Смена трека
            lastCanonicalTrackKey = key;
            lastReportedPositionMs = info.positionMs;
            lastReportedTimestampMs = now;
            trackPlayStartMs = (info.positionMs > 0) ? (now - info.positionMs) : now;
        } else {
            // Тот же трек. Проверяем, изменилась ли секунда от Windows SMTC
            if (info.positionMs != lastReportedPositionMs) {
                lastReportedPositionMs = info.positionMs;
                lastReportedTimestampMs = now;
            }
        }

        long calculated;
        if (lastReportedPositionMs > 0) {
            // Точная непрерывная интерполяция от момента, когда SMTC зафиксировал секунду
            long elapsed = info.playing ? Math.max(0L, now - lastReportedTimestampMs) : 0L;
            calculated = lastReportedPositionMs + elapsed;
        } else {
            // Фолбэк по локальному времени трека
            long elapsed = info.playing ? Math.max(0L, now - trackPlayStartMs) : 0L;
            calculated = elapsed;
        }

        if (info.durationMs > 0) {
            calculated = Math.min(info.durationMs, calculated);
        }
        return Math.max(0L, calculated);
    }

    public static long getDuration() {
        MediaInfo info = getCurrentMedia();
        return info != null ? info.durationMs : 0L;
    }

    // ==========================================================
    // === МЕТОДЫ УПРАВЛЕНИЯ ПЛЕЕРОМ ===
    // ==========================================================

    public static void playPause() {
        LexoraSpotifyMediaInfoHelper.playPause();
    }

    public static void togglePlay() {
        playPause();
    }

    public static void next() {
        LexoraSpotifyMediaInfoHelper.next();
    }

    public static void previous() {
        LexoraSpotifyMediaInfoHelper.previous();
    }

    public static void prev() {
        previous(); // Исправил: раньше этот метод был пустым
    }

    public static synchronized void seek(float progress) {
        MediaInfo info = getCurrentMedia();
        if (info != null && info.durationMs > 0) {
            long targetMs = (long) (info.durationMs * progress);
            long now = System.currentTimeMillis();
            trackPlayStartMs = now - targetMs;
            lastReportedPositionMs = targetMs;
            lastReportedTimestampMs = now;
        }
    }

    public static void seekThrottled(float progress) {
        seek(progress);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==========================================================
    // === ВНУТРЕННИЙ КЛАСС (Без изменений) ===
    // ==========================================================

    public static final class MediaInfo {
        public final String title;
        public final String artist;
        public final String textureHash;
        public final boolean playing;
        public final long positionMs;
        public final long durationMs;
        public final Identifier coverId;
        public final boolean fallbackOnly;
        public final long timestampMs;

        public MediaInfo(
                String title,
                String artist,
                String textureHash,
                boolean playing,
                long positionMs,
                long durationMs,
                Identifier coverId,
                boolean fallbackOnly
        ) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.textureHash = textureHash == null ? "" : textureHash;
            this.playing = playing;
            this.positionMs = Math.max(0L, positionMs);
            this.durationMs = Math.max(0L, durationMs);
            this.coverId = coverId;
            this.fallbackOnly = fallbackOnly;
            this.timestampMs = System.currentTimeMillis();
        }

        public String getLabel() {
            if (artist.isBlank()) {
                return title;
            }

            return title + " - " + artist;
        }

        public float getProgress() {
            if (durationMs <= 0L) {
                return 0.0f;
            }

            return clamp(positionMs / (float) durationMs, 0.0f, 1.0f);
        }
    }
}