package com.lexoravisauls.client.utils;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LexoraSpotifyMediaInfoHelper {
    private static final Identifier COVER_ID =
            Identifier.of("lexoravisauls", "dynamic/spotify_mediainfo_cover");

    private static final ExecutorService MEDIA_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Lexora Spotify MediaInfo");
                thread.setDaemon(true);
                return thread;
            });

    private static final AtomicBoolean UPDATE_LOCK = new AtomicBoolean(false);

    private static boolean initialized = false;

    private static volatile IMediaSession currentSession;
    private static volatile LexoraMediaUtils.MediaInfo currentMedia;

    private static NativeImageBackedTexture currentTexture;
    private static String lastCoverKey = "";

    private static long lastUpdateMs = 0L;
    private static long lastSeenMs = 0L;

    private static final long UPDATE_INTERVAL_MS = 350L;
    private static final long TTL_MS = 6500L;

    /*
     * true = показывать только сессии, где owner содержит spotify.
     * false = сначала ищет Spotify, но если не нашёл — берёт любую playing media session.
     */
    private static final boolean STRICT_SPOTIFY_ONLY = false;

    private LexoraSpotifyMediaInfoHelper() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        tick();
    }

    public static LexoraMediaUtils.MediaInfo getCurrentMedia() {
        if (!initialized) {
            init();
        }

        tick();

        LexoraMediaUtils.MediaInfo media = currentMedia;

        if (media == null) {
            return null;
        }

        if (System.currentTimeMillis() - lastSeenMs > TTL_MS) {
            return null;
        }

        return media;
    }

    public static boolean hasFreshSpotify() {
        return currentMedia != null
                && currentSession != null
                && System.currentTimeMillis() - lastSeenMs <= TTL_MS;
    }

    public static boolean playPause() {
        IMediaSession session = currentSession;

        if (session == null || !hasFreshSpotify()) {
            return false;
        }

        MEDIA_EXECUTOR.execute(() -> {
            try {
                session.playPause();
            } catch (Throwable throwable) {
                try {
                    MediaInfo media = session.getMedia();

                    if (media != null && media.getPlaying()) {
                        session.pause();
                    } else {
                        session.play();
                    }
                } catch (Throwable ignored) {
                }
            }
        });

        return true;
    }

    public static boolean next() {
        IMediaSession session = currentSession;

        if (session == null || !hasFreshSpotify()) {
            return false;
        }

        LexoraMediaUtils.resetTrackClock();

        MEDIA_EXECUTOR.execute(() -> {
            try {
                session.next();
                Thread.sleep(150L);
                updateMediaAsync();
            } catch (Throwable ignored) {
            }
        });

        return true;
    }

    public static boolean previous() {
        IMediaSession session = currentSession;

        if (session == null || !hasFreshSpotify()) {
            return false;
        }

        LexoraMediaUtils.resetTrackClock();

        MEDIA_EXECUTOR.execute(() -> {
            try {
                session.previous();
                Thread.sleep(150L);
                updateMediaAsync();
            } catch (Throwable ignored) {
            }
        });

        return true;
    }

    public static boolean seek(long positionMs) {
        return false;
    }

    private static void tick() {
        long now = System.currentTimeMillis();

        if (now - lastUpdateMs < UPDATE_INTERVAL_MS) {
            return;
        }

        lastUpdateMs = now;

        if (!UPDATE_LOCK.compareAndSet(false, true)) {
            return;
        }

        MEDIA_EXECUTOR.execute(() -> {
            try {
                updateMediaAsync();
            } catch (Throwable throwable) {
                clearMedia();
            } finally {
                UPDATE_LOCK.set(false);
            }
        });
    }

    private static void updateMediaAsync() {
        List<IMediaSession> sessions;

        try {
            sessions = MediaPlayerInfo.Instance.getMediaSessions();
        } catch (Throwable throwable) {
            clearMedia();
            return;
        }

        if (sessions == null || sessions.isEmpty()) {
            clearMedia();
            return;
        }

        IMediaSession spotifySession = null;
        MediaInfo spotifyMedia = null;

        IMediaSession fallbackSession = null;
        MediaInfo fallbackMedia = null;

        for (IMediaSession session : sessions) {
            if (session == null) {
                continue;
            }

            MediaInfo media;

            try {
                media = session.getMedia();
            } catch (Throwable ignored) {
                continue;
            }

            if (media == null) {
                continue;
            }

            if (isBlank(media.getTitle()) && isBlank(media.getArtist())) {
                continue;
            }

            boolean spotify = isSpotifySession(session);
            boolean playing = safePlaying(media);

            if (spotify) {
                if (spotifyMedia == null || playing) {
                    spotifySession = session;
                    spotifyMedia = media;
                }

                if (playing) {
                    break;
                }
            }

            if (!STRICT_SPOTIFY_ONLY && fallbackMedia == null) {
                fallbackSession = session;
                fallbackMedia = media;
            }

            if (!STRICT_SPOTIFY_ONLY && fallbackMedia != null && playing) {
                fallbackSession = session;
                fallbackMedia = media;
            }
        }

        IMediaSession foundSession = spotifySession;
        MediaInfo foundMedia = spotifyMedia;

        if (!STRICT_SPOTIFY_ONLY && foundMedia == null) {
            foundSession = fallbackSession;
            foundMedia = fallbackMedia;
        }

        if (foundSession == null || foundMedia == null) {
            clearMedia();
            return;
        }

        currentSession = foundSession;

        Snapshot snapshot = makeSnapshot(foundMedia);

        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null) {
            return;
        }

        client.execute(() -> applySnapshot(snapshot));
    }

    private static Snapshot makeSnapshot(MediaInfo media) {
        String title = safe(media.getTitle());
        String artist = safe(media.getArtist());

        if (title.isBlank()) {
            title = "Spotify";
        }

        if (artist.isBlank()) {
            artist = "Spotify";
        }

        boolean playing = safePlaying(media);

        long rawPos = 0L;
        long rawDur = 0L;
        byte[] artwork = new byte[0];

        try {
            rawPos = Math.max(0L, media.getPosition());
        } catch (Throwable ignored) {
        }

        try {
            rawDur = Math.max(0L, media.getDuration());
        } catch (Throwable ignored) {
        }

        try {
            artwork = media.getArtworkPng();
        } catch (Throwable ignored) {
        }

        long position;
        long duration;

        // Определяем единицы измерения (секунды vs миллисекунды)
        if (rawDur > 0L && rawDur < 10000L) {
            duration = rawDur * 1000L;
            position = rawPos * 1000L;
        } else if (rawDur == 0L && rawPos > 0L && rawPos < 10000L) {
            duration = 0L;
            position = rawPos * 1000L;
        } else {
            duration = rawDur;
            position = rawPos;
        }

        return new Snapshot(
                title,
                artist,
                playing,
                position,
                duration,
                artwork == null ? new byte[0] : artwork
        );
    }

    private static void applySnapshot(Snapshot snapshot) {
        String textureHash = "";

        if (snapshot.artwork.length > 0) {
            textureHash = updateCover(snapshot.artwork, snapshot.title + "|" + snapshot.artist);
        }

        if (textureHash.isBlank() && currentTexture != null) {
            textureHash = lastCoverKey;
        }

        currentMedia = new LexoraMediaUtils.MediaInfo(
                snapshot.title,
                snapshot.artist,
                textureHash,
                snapshot.playing,
                snapshot.position,
                snapshot.duration,
                textureHash.isBlank() ? null : COVER_ID,
                false
        );

        lastSeenMs = System.currentTimeMillis();
    }

    private static String updateCover(byte[] artworkPng, String mediaKey) {
        String coverKey = mediaKey + "|" + artworkPng.length + "|" + Arrays.hashCode(artworkPng);

        if (coverKey.equals(lastCoverKey) && currentTexture != null) {
            return coverKey;
        }

        lastCoverKey = coverKey;

        try (ByteArrayInputStream input = new ByteArrayInputStream(artworkPng)) {
            NativeImage image = NativeImage.read(input);

            if (currentTexture != null) {
                try {
                    currentTexture.close();
                } catch (Throwable ignored) {
                }

                currentTexture = null;
            }

            currentTexture = new NativeImageBackedTexture(image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(COVER_ID, currentTexture);

            return coverKey;
        } catch (Throwable throwable) {
            return "";
        }
    }

    private static boolean isSpotifySession(IMediaSession session) {
        try {
            String owner = safe(session.getOwner()).toLowerCase(Locale.ROOT);
            return owner.contains("spotify");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean safePlaying(MediaInfo media) {
        try {
            return media.getPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void clearMedia() {
        currentMedia = null;
        currentSession = null;
        lastSeenMs = 0L;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record Snapshot(
            String title,
            String artist,
            boolean playing,
            long position,
            long duration,
            byte[] artwork
    ) {
    }
}