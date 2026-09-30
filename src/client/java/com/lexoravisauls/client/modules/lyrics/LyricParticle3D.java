package com.lexoravisauls.client.modules.lyrics;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.MsdfFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class LyricParticle3D {
    private final String text;
    private final Vec3d basePosition;
    private final long spawnTimeMs;
    private long durationMs;
    private final float floatHeight;
    private final float tiltSign;

    private float karaokeProgress = 0.0f;
    private boolean isKaraoke = false;
    private boolean isCurrent = true;
    private boolean forceExpire = false;
    private long exitStartTimeMs = -1L;

    public LyricParticle3D(String text, Vec3d basePosition, long spawnTimeMs, long durationMs, float floatHeight) {
        this.text = text != null ? text.trim() : "";
        this.basePosition = basePosition;
        this.spawnTimeMs = spawnTimeMs;
        this.durationMs = durationMs;
        this.floatHeight = floatHeight;
        this.tiltSign = Math.random() > 0.5 ? 1.0f : -1.0f;
    }

    public Vec3d getBasePosition() {
        return basePosition;
    }

    public String getText() {
        return text;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public void setCurrent(boolean current) {
        if (this.isCurrent && !current) {
            this.isCurrent = false;
            if (this.exitStartTimeMs < 0) {
                this.exitStartTimeMs = System.currentTimeMillis();
            }
        } else {
            this.isCurrent = current;
        }
    }

    public void setKaraokeProgress(float progress) {
        this.karaokeProgress = MathHelper.clamp(progress, 0.0f, 1.0f);
    }

    public void setKaraoke(boolean karaoke) {
        this.isKaraoke = karaoke;
    }

    public void dismiss() {
        if (this.exitStartTimeMs < 0) {
            this.exitStartTimeMs = System.currentTimeMillis();
        }
        this.isCurrent = false;
    }

    public void forceKill() {
        this.forceExpire = true;
    }

    public boolean isDead(long nowMs) {
        if (forceExpire) return true;
        if (exitStartTimeMs > 0 && (nowMs - exitStartTimeMs >= 650L)) return true;
        if (isCurrent) return false;
        return (nowMs - spawnTimeMs >= durationMs + 3000L);
    }

    public void render(MatrixStack matrices, Camera camera, MsdfFont font, String animMode, int colorRgb, long nowMs, boolean depthOcclusion) {
        long elapsed = nowMs - spawnTimeMs;
        if (elapsed < 0 || forceExpire) return;
        if (exitStartTimeMs > 0 && (nowMs - exitStartTimeMs >= 650L)) return;

        float alpha = 1.0f;
        double offsetY = 0.0;
        float animScale = 1.0f;
        float rotationTilt = 0.0f;
        String displayText = this.text;

        if (exitStartTimeMs > 0) {
            float exitProgress = MathHelper.clamp((float) (nowMs - exitStartTimeMs) / 600.0f, 0.0f, 1.0f);
            alpha = 1.0f - easeInCubic(exitProgress);
            offsetY = floatHeight * 0.25 * easeInCubic(exitProgress);
            animScale = 1.0f - 0.05f * exitProgress;
        } else {
            float durationRatio = durationMs > 0 ? MathHelper.clamp((float) elapsed / (float) durationMs, 0.0f, 1.0f) : 0.0f;

            switch (animMode) {
                case "Typewriter" -> {
                    if (durationRatio < 0.25f) {
                        float typeProgress = durationRatio / 0.25f;
                        int visibleChars = Math.min(this.text.length(), (int) Math.ceil(this.text.length() * typeProgress));
                        displayText = this.text.substring(0, Math.max(1, visibleChars));
                        alpha = Math.min(1.0f, durationRatio / 0.06f);
                        offsetY = durationRatio * (floatHeight * 0.15);
                    } else {
                        displayText = this.text;
                        alpha = 1.0f;
                        offsetY = durationRatio * (floatHeight * 0.15);
                    }
                }
                case "PopScale" -> {
                    if (durationRatio < 0.18f) {
                        float t = durationRatio / 0.18f;
                        alpha = easeOutCubic(t);
                        animScale = 0.35f + 0.65f * easeOutBack(t);
                        offsetY = -0.04 * (1.0f - easeOutCubic(t));
                    } else {
                        alpha = 1.0f;
                        animScale = 1.0f;
                        offsetY = 0.0;
                    }
                }
                case "KineticSlide" -> {
                    if (durationRatio < 0.20f) {
                        float t = durationRatio / 0.20f;
                        alpha = easeOutCubic(t);
                        offsetY = -0.15 * (1.0f - easeOutCubic(t));
                        animScale = 0.92f + 0.08f * t;
                        rotationTilt = (1.0f - t) * 4.0f * tiltSign;
                    } else {
                        alpha = 1.0f;
                        offsetY = 0.0;
                        animScale = 1.0f;
                        rotationTilt = 0.0f;
                    }
                }
                case "Fade" -> {
                    if (durationRatio < 0.15f) {
                        alpha = durationRatio / 0.15f;
                    } else {
                        alpha = 1.0f;
                    }
                }
                default -> { // "LyricFlow"
                    if (durationRatio < 0.15f) {
                        float t = durationRatio / 0.15f;
                        alpha = easeOutCubic(t);
                        offsetY = -0.06 * (1.0f - easeOutCubic(t));
                        animScale = 0.93f + 0.07f * easeOutBack(t);
                    } else {
                        alpha = 1.0f;
                        offsetY = 0.0;
                        animScale = 1.0f;
                    }
                }
            }
        }

        if (!isCurrent && exitStartTimeMs <= 0) {
            alpha *= 0.65f;
        }

        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        if (alpha <= 0.005f || displayText == null || displayText.isBlank()) return;

        Vec3d camPos = camera.getPos();
        double renderX = basePosition.x - camPos.x;
        double renderY = (basePosition.y + offsetY) - camPos.y;
        double renderZ = basePosition.z - camPos.z;

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        // Strict Billboarding to face camera
        matrices.multiply(camera.getRotation());

        if (rotationTilt != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationTilt));
        }

        // World scale with user's Lyrics Scale slider
        float userScale = ClientData.numSettings.getOrDefault("Lyrics Scale", 1.0f);
        float staticScale = 0.02f * animScale * userScale;
        matrices.scale(staticScale, -staticScale, staticScale);

        int alphaInt = (int) (alpha * 255.0f);
        int finalColor = (alphaInt << 24) | (colorRgb & 0x00FFFFFF);

        MinecraftClient mc = MinecraftClient.getInstance();

        boolean allGlyphsSupported = (font != null);
        if (font != null) {
            for (int i = 0; i < displayText.length(); i++) {
                int cp = displayText.codePointAt(i);
                if (cp != 32 && !font.hasGlyph(cp)) {
                    allGlyphsSupported = false;
                    break;
                }
            }
        }

        if (font != null && allGlyphsSupported) {
            float fontSize = 24.0f;
            float textWidth = font.getWidth(displayText, fontSize);
            float xOffset = -textWidth / 2.0f;
            float yOffset = -fontSize * 0.35f;

            if (isKaraoke) {
                int doneColor = finalColor;
                int pendingColor = (alphaInt << 24) | 0x777788;
                font.drawKaraoke3D(matrices, displayText, xOffset, yOffset, fontSize, doneColor, pendingColor, karaokeProgress, depthOcclusion);
            } else {
                font.draw3D(matrices, displayText, xOffset, yOffset, fontSize, finalColor, depthOcclusion);
            }
        } else if (mc != null && mc.textRenderer != null) {
            matrices.push();
            matrices.scale(2.4f, 2.4f, 2.4f);
            float textWidth = mc.textRenderer.getWidth(displayText);
            float xOffset = -textWidth / 2.0f;
            float yOffset = -4.5f;

            Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
            mc.textRenderer.draw(
                    displayText,
                    xOffset,
                    yOffset,
                    finalColor,
                    true,
                    modelMatrix,
                    mc.getBufferBuilders().getEntityVertexConsumers(),
                    depthOcclusion ? TextRenderer.TextLayerType.NORMAL : TextRenderer.TextLayerType.SEE_THROUGH,
                    0,
                    0xF000F0
            );
            mc.getBufferBuilders().getEntityVertexConsumers().draw();
            matrices.pop();
        }

        matrices.pop();
    }

    private static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    private static float easeInCubic(float t) {
        return (float) Math.pow(t, 3);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3) + c1 * (float) Math.pow(t - 1.0f, 2);
    }
}
