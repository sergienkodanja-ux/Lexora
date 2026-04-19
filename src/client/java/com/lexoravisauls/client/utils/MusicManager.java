package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.events.DynamicIslandRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class MusicManager {
    private static Clip clip;
    private static final List<File> playlist = new ArrayList<>();
    private static int currentIndex = 0;
    private static boolean isInitialized = false;

    private static boolean wasPlayingBeforeForcedPause = false;
    private static boolean isForcedPaused = false;
    private static long lastScanTime = 0;

    public static float currentVolume = 0.5f;

    private static final Identifier DYNAMIC_COVER_ID = Identifier.of("lexoravisauls", "dynamic_music_cover");
    private static NativeImageBackedTexture currentTexture;

    public static void init() {
        scanFolder();

        DynamicIslandRenderer.MusicState.isPaused = true;
        DynamicIslandRenderer.MusicState.hasMusic = !playlist.isEmpty();
        DynamicIslandRenderer.MusicState.trackName = playlist.isEmpty()
                ? "Папка пуста (нужны .wav)"
                : "Нажми Play";

        isInitialized = true;
    }

    public static void scanFolder() {
        if (System.currentTimeMillis() - lastScanTime < 3000) return;
        lastScanTime = System.currentTimeMillis();

        File musicDir = new File(MinecraftClient.getInstance().runDirectory, "lexora/music");
        if (!musicDir.exists()) musicDir.mkdirs();

        File[] files = musicDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));
        if (files != null && files.length != playlist.size()) {
            File currentTrack = (!playlist.isEmpty() && currentIndex < playlist.size()) ? playlist.get(currentIndex) : null;

            playlist.clear();
            for (File f : files) playlist.add(f);

            if (playlist.isEmpty()) {
                DynamicIslandRenderer.MusicState.hasMusic = false;
                DynamicIslandRenderer.MusicState.trackName = "Папка пуста (нужны .wav)";
                DynamicIslandRenderer.MusicState.currentCover = null;

                if (clip != null && clip.isOpen()) {
                    clip.stop();
                    clip.close();
                }
                clip = null;
            } else {
                DynamicIslandRenderer.MusicState.hasMusic = true;

                if (currentTrack != null && playlist.contains(currentTrack)) {
                    currentIndex = playlist.indexOf(currentTrack);
                } else {
                    currentIndex = 0;
                }

                if (clip == null || !clip.isOpen()) {
                    DynamicIslandRenderer.MusicState.trackName = "Нажми Play";
                    DynamicIslandRenderer.MusicState.currentCover = null;
                }
            }
        }
    }

    private static void loadTrack(int index) {
        if (playlist.isEmpty()) return;

        try {
            if (clip != null && clip.isOpen()) {
                clip.stop();
                clip.close();
            }

            File track = playlist.get(index);
            DynamicIslandRenderer.MusicState.trackName = track.getName().replace(".wav", "");

            File imageFile = new File(track.getParent(), track.getName().replace(".wav", ".png"));

            if (currentTexture != null) {
                currentTexture.close();
                currentTexture = null;
            }

            if (imageFile.exists()) {
                try (FileInputStream fis = new FileInputStream(imageFile)) {
                    NativeImage image = NativeImage.read(fis);
                    currentTexture = new NativeImageBackedTexture(image);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(DYNAMIC_COVER_ID, currentTexture);
                    DynamicIslandRenderer.MusicState.currentCover = DYNAMIC_COVER_ID;
                } catch (Exception e) {
                    DynamicIslandRenderer.MusicState.currentCover = null;
                }
            } else {
                DynamicIslandRenderer.MusicState.currentCover = null;
            }

            AudioInputStream audioInput = AudioSystem.getAudioInputStream(track);
            clip = AudioSystem.getClip();
            clip.open(audioInput);

            setVolume(currentVolume);

            if (!DynamicIslandRenderer.MusicState.isPaused && !isForcedPaused) {
                clip.start();
            }
        } catch (Exception e) {
            System.out.println("Ошибка загрузки трека: " + e.getMessage());
            clip = null;
        }
    }

    public static void setVolume(float volume) {
        currentVolume = Math.max(0.0f, Math.min(1.0f, volume));
        if (clip != null && clip.isOpen()) {
            try {
                FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log(currentVolume <= 0.0f ? 0.0001f : currentVolume) / Math.log(10.0) * 20.0);
                volumeControl.setValue(dB);
            } catch (Exception ignored) {
            }
        }
    }

    public static void togglePlayPause() {
        if (playlist.isEmpty() || isForcedPaused) return;

        if (clip == null || !clip.isOpen()) {
            DynamicIslandRenderer.MusicState.isPaused = false;
            loadTrack(currentIndex);
            return;
        }

        if (clip.isRunning()) {
            clip.stop();
            DynamicIslandRenderer.MusicState.isPaused = true;
        } else {
            clip.start();
            DynamicIslandRenderer.MusicState.isPaused = false;
        }
    }

    public static void forcePause(boolean pause) {
        if (clip == null) return;

        if (pause) {
            if (!isForcedPaused) {
                wasPlayingBeforeForcedPause = clip.isRunning();
                clip.stop();
                isForcedPaused = true;
            }
        } else {
            if (isForcedPaused) {
                isForcedPaused = false;
                if (wasPlayingBeforeForcedPause && !DynamicIslandRenderer.MusicState.isPaused) {
                    clip.start();
                }
            }
        }
    }

    public static void repeatTrack() {
        if (clip != null && clip.isOpen()) {
            clip.setMicrosecondPosition(0);
            if (!DynamicIslandRenderer.MusicState.isPaused && !isForcedPaused) {
                clip.start();
            }
        }
    }

    public static void seek(float progress) {
        if (clip != null && clip.isOpen()) {
            long len = clip.getMicrosecondLength();
            clip.setMicrosecondPosition((long) (len * progress));
        }
    }

    public static void next() {
        if (playlist.isEmpty()) return;
        currentIndex++;
        if (currentIndex >= playlist.size()) currentIndex = 0;

        DynamicIslandRenderer.MusicState.isPaused = false;
        loadTrack(currentIndex);
    }

    public static void prev() {
        if (playlist.isEmpty()) return;
        currentIndex--;
        if (currentIndex < 0) currentIndex = playlist.size() - 1;

        DynamicIslandRenderer.MusicState.isPaused = false;
        loadTrack(currentIndex);
    }

    public static void tick() {
        scanFolder();

        if (!isInitialized) return;

        if ((clip == null || !clip.isOpen()) && !playlist.isEmpty()) {
            return;
        }

        if (clip == null) return;

        long microPos = clip.getMicrosecondPosition();
        long microLen = clip.getMicrosecondLength();

        if (microLen > 0) {
            if (DynamicIslandRenderer.isDraggingProgress) {
                long visualPos = (long) (DynamicIslandRenderer.MusicState.progress * microLen);
                String currentStr = formatTime(visualPos);
                String totalStr = formatTime(microLen);
                DynamicIslandRenderer.MusicState.timeString = currentStr + " / " + totalStr;
            } else {
                DynamicIslandRenderer.MusicState.progress = (float) microPos / microLen;
                String currentStr = formatTime(microPos);
                String totalStr = formatTime(microLen);
                DynamicIslandRenderer.MusicState.timeString = currentStr + " / " + totalStr;
            }

            if (microPos >= microLen && !DynamicIslandRenderer.MusicState.isPaused && !isForcedPaused) {
                next();
            }
        }
    }

    private static String formatTime(long microseconds) {
        long totalSeconds = microseconds / 1_000_000L;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}