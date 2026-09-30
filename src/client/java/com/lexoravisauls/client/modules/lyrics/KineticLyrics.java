package com.lexoravisauls.client.modules.lyrics;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.events.DynamicIslandRenderer;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.utils.LexoraMediaUtils;
import com.lexoravisauls.client.utils.LyricsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class KineticLyrics {
    private static final KineticLyrics INSTANCE = new KineticLyrics();

    public static KineticLyrics getInstance() {
        return INSTANCE;
    }

    private final List<LyricParticle3D> activeParticles = new CopyOnWriteArrayList<>();
    private LyricParticle3D activeLyricParticle = null;

    private String lastTrackKey = "";
    private String lastRenderedLyric = "";
    private long lastSeekCheckMs = 0L;

    private KineticLyrics() {}

    public void resetPlaybackState() {
        activeParticles.clear();
        activeLyricParticle = null;
        lastRenderedLyric = "";
        lastSeekCheckMs = 0L;
    }

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        boolean moduleEnabled = ClientData.moduleStates.getOrDefault("Kinetic Lyrics",
                LexoraGui.moduleStates.getOrDefault("Kinetic Lyrics", true));
        if (!moduleEnabled) {
            if (!activeParticles.isEmpty()) activeParticles.clear();
            activeLyricParticle = null;
            lastRenderedLyric = "";
            return;
        }

        syncPlaybackTime();
    }

    public void syncPlaybackTime() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        long now = System.currentTimeMillis();

        if (LexoraMediaUtils.hasMedia()) {
            String title = LexoraMediaUtils.getTitle();
            String artist = LexoraMediaUtils.getAuthor();
            if (title == null) title = "";
            if (artist == null) artist = "";
            String key = (title + " - " + artist).toLowerCase();

            // Track change detection
            if (!key.equalsIgnoreCase(lastTrackKey)) {
                this.lastTrackKey = key;
                resetPlaybackState();
            }

            long progress = LexoraMediaUtils.getProgress();
            long duration = LexoraMediaUtils.getDuration();
            long offset = (long) (float) ClientData.numSettings.getOrDefault("Lyrics Time Offset", 0.0f);
            long effectiveProgress = Math.max(0, progress + offset);

            // Seek detection (jump > 1.8s)
            if (lastSeekCheckMs > 0 && Math.abs(effectiveProgress - lastSeekCheckMs) > 1800L) {
                for (LyricParticle3D p : activeParticles) {
                    p.dismiss();
                }
                activeLyricParticle = null;
                lastRenderedLyric = "";
            }
            lastSeekCheckMs = effectiveProgress;

            // Keep LyricsManager up to date
            try {
                LyricsManager.update(title, artist, duration);
            } catch (Throwable ignored) {}

            // Listen directly to watermark's singing state and current line
            boolean islandLyrics = DynamicIslandRenderer.isIslandLyricsEnabled();
            boolean isSinging = islandLyrics && LyricsManager.hasLyrics() && LyricsManager.isSinging(effectiveProgress);
            String targetLine = "";
            float karaokeProgress = 0.0f;
            boolean isKaraoke = false;

            if (isSinging) {
                String lyric = LyricsManager.getCurrentLine(effectiveProgress);
                if (lyric != null && !lyric.trim().isEmpty()) {
                    targetLine = lyric.trim();
                    karaokeProgress = LyricsManager.getLineProgress(effectiveProgress);
                    isKaraoke = true;
                } else {
                    targetLine = title.isEmpty() ? "" : (artist.isEmpty() ? "♪ " + title : "♪ " + title + " - " + artist);
                    isKaraoke = false;
                }
            } else if (LyricsManager.isFetching() && islandLyrics) {
                targetLine = "♪ Загрузка текста...";
                isKaraoke = false;
            } else if (LexoraMediaUtils.isPlaying()) {
                targetLine = title.isEmpty() ? "" : (artist.isEmpty() ? "♪ " + title : "♪ " + title + " - " + artist);
                isKaraoke = false;
            }

            // Sync with watermark line
            if (!targetLine.isEmpty()) {
                if (activeLyricParticle == null || !targetLine.equalsIgnoreCase(lastRenderedLyric)) {
                    lastRenderedLyric = targetLine;

                    // Transition out previous active particle
                    if (activeLyricParticle != null) {
                        activeLyricParticle.setCurrent(false);
                        int maxAllowed = (int) (float) ClientData.numSettings.getOrDefault("Lyrics Max Lines", 3.0f);
                        if (maxAllowed <= 1) {
                            activeLyricParticle.dismiss();
                        }
                    }

                    // Estimate duration for this line
                    long estDuration = isKaraoke ? 4000L : 12000L;
                    if (LyricsManager.hasLyrics() && isKaraoke) {
                        List<LyricsManager.LyricLine> lines = LyricsManager.getLines();
                        for (int i = 0; i < lines.size(); i++) {
                            LyricsManager.LyricLine l = lines.get(i);
                            if (l.text.equalsIgnoreCase(targetLine)) {
                                long nextStart = (i + 1 < lines.size()) ? lines.get(i + 1).timeMs : l.timeMs + 4000L;
                                estDuration = LyricsManager.calculateLineDuration(l.text, nextStart - l.timeMs);
                                break;
                            }
                        }
                    }

                    activeLyricParticle = spawnLyricParticle(targetLine, now, estDuration);
                    if (activeLyricParticle != null) {
                        activeLyricParticle.setKaraoke(isKaraoke);
                        activeLyricParticle.setKaraokeProgress(karaokeProgress);
                    }
                } else {
                    // Update active particle's karaoke progress
                    if (activeLyricParticle != null) {
                        activeLyricParticle.setKaraoke(isKaraoke);
                        activeLyricParticle.setKaraokeProgress(karaokeProgress);
                    }
                }
            } else {
                // Media paused or stopped: fade out active particle
                if (activeLyricParticle != null) {
                    activeLyricParticle.setCurrent(false);
                    activeLyricParticle = null;
                    lastRenderedLyric = "";
                }
            }

            // Enforce max lines
            int maxAllowed = (int) (float) ClientData.numSettings.getOrDefault("Lyrics Max Lines", 3.0f);
            while (activeParticles.size() > maxAllowed) {
                LyricParticle3D oldest = activeParticles.get(0);
                oldest.dismiss();
                activeParticles.remove(0);
            }
        } else {
            // Media paused/stopped: fade out particles
            if (!activeParticles.isEmpty()) {
                for (LyricParticle3D p : activeParticles) {
                    p.dismiss();
                }
            }
            activeLyricParticle = null;
            lastRenderedLyric = "";
            lastTrackKey = "";
        }

        // Clean up expired particles
        activeParticles.removeIf(p -> p.isDead(now));
        if (activeLyricParticle != null && activeLyricParticle.isDead(now)) {
            activeLyricParticle = null;
        }
    }

    private Vec3d findNonOverlappingSpawnPos(Vec3d headPos, float cameraYaw, float cameraPitch) {
        double spread = ClientData.numSettings.getOrDefault("Lyrics Arc Spread", 70.0f);
        double dist = ClientData.numSettings.getOrDefault("Lyrics Distance", 5.0f);
        double minD = Math.max(1.5, dist * 0.85);
        double maxD = dist * 1.15;

        Vec3d bestPos = null;
        double maxMinDistance = -1.0;

        for (int attempt = 0; attempt < 24; attempt++) {
            double randomYawOffset = ThreadLocalRandom.current().nextDouble(-spread, spread);
            double targetYawDeg = cameraYaw + randomYawOffset;
            double targetPitchDeg = MathHelper.clamp(cameraPitch * 0.5f + ThreadLocalRandom.current().nextDouble(-12.0, 12.0), -45.0, 45.0);
            double distance = ThreadLocalRandom.current().nextDouble(minD, maxD);
            double yOffset = ThreadLocalRandom.current().nextDouble(-0.15, 0.45);

            double yawRad = Math.toRadians(targetYawDeg);
            double pitchRad = Math.toRadians(targetPitchDeg);
            double cosPitch = Math.cos(pitchRad);
            double dirX = -Math.sin(yawRad) * cosPitch;
            double dirY = -Math.sin(pitchRad);
            double dirZ = Math.cos(yawRad) * cosPitch;

            double spawnX = headPos.x + (dirX * distance);
            double spawnY = headPos.y + (dirY * distance) + yOffset;
            double spawnZ = headPos.z + (dirZ * distance);
            Vec3d candidate = new Vec3d(spawnX, spawnY, spawnZ);

            if (activeParticles.isEmpty()) {
                return candidate;
            }

            double minDistanceToOthers = Double.MAX_VALUE;
            for (LyricParticle3D active : activeParticles) {
                double d = candidate.distanceTo(active.getBasePosition());
                if (d < minDistanceToOthers) {
                    minDistanceToOthers = d;
                }
            }

            double requiredSeparation = Math.max(1.5, dist * 0.20);
            if (minDistanceToOthers >= requiredSeparation) {
                return candidate;
            }

            if (minDistanceToOthers > maxMinDistance) {
                maxMinDistance = minDistanceToOthers;
                bestPos = candidate;
            }
        }

        if (bestPos != null) return bestPos;

        // Guaranteed fallback in front of camera
        double yawRad = Math.toRadians(cameraYaw);
        return headPos.add(-Math.sin(yawRad) * dist, 0.2, Math.cos(yawRad) * dist);
    }

    private LyricParticle3D spawnLyricParticle(String text, long spawnTimeMs, long durationMs) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || text == null || text.isBlank()) return null;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d headPos = mc.player.getEyePos();
        float cameraYaw = camera.getYaw();
        float cameraPitch = camera.getPitch();

        Vec3d spawnPos = findNonOverlappingSpawnPos(headPos, cameraYaw, cameraPitch);
        if (spawnPos == null) return null;

        float riseHeight = ClientData.numSettings.getOrDefault("Lyrics Float Height", 0.5f);
        LyricParticle3D particle = new LyricParticle3D(text, spawnPos, spawnTimeMs, durationMs, riseHeight);
        activeParticles.add(particle);
        return particle;
    }

    private int getActiveColorRgb() {
        String colorMode = ClientData.modeSettings.getOrDefault("Lyrics Color Mode", "Theme");
        switch (colorMode) {
            case "ThemeGradient" -> {
                int c1 = LexoraGui.getThemeColor(0);
                int c2 = LexoraGui.getThemeColor(1);
                float ratio = (float) (Math.sin(System.currentTimeMillis() / 450.0) * 0.5 + 0.5);
                int r = (int) (((c1 >> 16) & 0xFF) * (1 - ratio) + ((c2 >> 16) & 0xFF) * ratio);
                int g = (int) (((c1 >> 8) & 0xFF) * (1 - ratio) + ((c2 >> 8) & 0xFF) * ratio);
                int b = (int) ((c1 & 0xFF) * (1 - ratio) + (c2 & 0xFF) * ratio);
                return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            }
            case "Custom" -> {
                float[] rgb = ClientData.colorSettings.getOrDefault("Lyrics Custom Color", new float[]{1f, 1f, 1f});
                int r = (int) (rgb[0] * 255) & 0xFF;
                int g = (int) (rgb[1] * 255) & 0xFF;
                int b = (int) (rgb[2] * 255) & 0xFF;
                return (r << 16) | (g << 8) | b;
            }
            case "White" -> {
                return 0xFFFFFF;
            }
            default -> { // "Theme"
                return LexoraGui.getThemeColor(0) & 0x00FFFFFF;
            }
        }
    }

    public void render3D(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        boolean moduleEnabled = ClientData.moduleStates.getOrDefault("Kinetic Lyrics",
                LexoraGui.moduleStates.getOrDefault("Kinetic Lyrics", true));
        boolean in3D = ClientData.moduleStates.getOrDefault("Lyrics In 3D",
                LexoraGui.moduleStates.getOrDefault("Lyrics In 3D", true));
        if (!moduleEnabled || !in3D) return;

        syncPlaybackTime();

        if (activeParticles.isEmpty()) return;

        MsdfFont font = DynamicIslandRenderer.getFont();
        String animMode = ClientData.modeSettings.getOrDefault("Lyrics Animation", "LyricFlow");
        int colorRgb = getActiveColorRgb();
        boolean depthOcclusion = ClientData.moduleStates.getOrDefault("Lyrics Depth Occlusion", true);
        long now = System.currentTimeMillis();

        for (LyricParticle3D particle : activeParticles) {
            particle.render(matrices, camera, font, animMode, colorRgb, now, depthOcclusion);
        }
    }
}
