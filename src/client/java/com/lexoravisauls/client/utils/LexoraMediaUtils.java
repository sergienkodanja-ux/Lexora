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

    public static long getProgress() {
        MediaInfo info = getCurrentMedia();
        return info != null ? info.positionMs : 0L;
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

    public static void seek(float progress) {
        /*
         * MediaPlayerInfo 0.1.0 не даёт seek в IMediaSession.
         */
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