package com.lexoravisauls.client.motionblur;

import com.lexoravisauls.client.gui.LexoraGui;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.ladysnake.satin.api.managed.ManagedShaderEffect;
import org.ladysnake.satin.api.managed.ShaderEffectManager;

/**
 * Reprojection (velocity-buffer) motion blur: для каждого пикселя
 * восстанавливает его world-space позицию из depth-буфера, репроецирует
 * через матрицы прошлого кадра и размывает вдоль получившегося вектора
 * движения. Требует MixinLevelRenderer для передачи текущей/предыдущей
 * матрицы вида, проекции и позиции камеры каждый кадр.
 */
public class ShaderMotionBlur {
    private final MotionBlurModule config;
    private final ManagedShaderEffect motionBlurShader;

    public ShaderMotionBlur(MotionBlurModule config) {
        this.config = config;
        motionBlurShader = ShaderEffectManager.getInstance().manage(
                Identifier.of(MotionReBlur.MOD_ID, "motion_blur"),
                shader -> shader.setUniformValue("BlendFactor", config.getStrength())
        );
    }

    private long lastNano = System.nanoTime();
    private float currentBlur = 0.0f;
    private float currentFPS = 0.0f;

    public void registerShaderCallbacks() {
        WorldRenderEvents.END.register(context -> {
            syncSettingsFromGui();

            long now = System.nanoTime();
            float deltaTime = (now - lastNano) / 1_000_000_000.0f;
            float deltaTick = deltaTime * 20.0f;
            lastNano = now;

            if (deltaTime > 0 && deltaTime < 1.0f) {
                currentFPS = 1.0f / deltaTime;
            } else {
                currentFPS = 0.0f;
            }

            if (shouldRenderMotionBlur()) {
                applyMotionBlur(deltaTick);
            }
        });
    }

    /**
     * Читает состояние виджетов GUI и применяет их к MotionBlurModule —
     * тот же паттерн, что и AuraParticles.tick(), каждый кадр.
     */
    private void syncSettingsFromGui() {
        boolean enabled = LexoraGui.moduleStates.getOrDefault("Motion Blur", false);
        if (config.isEnabled() != enabled) {
            config.setEnabled(enabled);
        }

        float strength = LexoraGui.numSettings.getOrDefault("Motion Blur Strength", 0.8f);
        if (config.getStrength() != strength) {
            config.setStrength(strength);
        }

        boolean useRRC = LexoraGui.moduleStates.getOrDefault("Motion Blur RRC", true);
        if (config.isUseRRC() != useRRC) {
            config.setUseRRC(useRRC);
        }

        String qualityName = LexoraGui.modeSettings.getOrDefault("Motion Blur Quality", "Высокое");
        int quality = switch (qualityName) {
            case "Низкое" -> 0;
            case "Среднее" -> 1;
            case "Высокое" -> 2;
            case "Ультра" -> 3;
            default -> 2;
        };
        if (config.getQuality() != quality) {
            config.setQuality(quality);
        }
    }

    private boolean shouldRenderMotionBlur() {
        if (config.getStrength() == 0 || !config.isEnabled()) {
            return false;
        }
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            MotionReBlur.LOGGER.warn("Motion Blur cannot work with Iris Shaders!");
            config.setEnabled(false);
            return false;
        }
        return true;
    }

    private void applyMotionBlur(float deltaTick) {
        MinecraftClient client = MinecraftClient.getInstance();

        MonitorInfoProvider.updateDisplayInfo();
        int displayRefreshRate = MonitorInfoProvider.getRefreshRate();

        float baseStrength = config.getStrength();
        float scaledStrength = baseStrength;
        if (config.isUseRRC()) {
            float fpsOverRefresh = (displayRefreshRate > 0) ? currentFPS / displayRefreshRate : 1.0f;
            if (fpsOverRefresh < 1.0f) fpsOverRefresh = 1.0f;
            scaledStrength = baseStrength * fpsOverRefresh;
        }

        if (currentBlur != scaledStrength) {
            motionBlurShader.setUniformValue("BlendFactor", scaledStrength);
            currentBlur = scaledStrength;
        }

        int sampleAmount = getSampleAmountForFPS(currentFPS);
        int halfSampleAmount = sampleAmount / 2;
        float invSamples = 1.0f / sampleAmount;

        motionBlurShader.setUniformValue("view_res", (float) client.getFramebuffer().viewportWidth, (float) client.getFramebuffer().viewportHeight);
        motionBlurShader.setUniformValue("motionBlurSamples", sampleAmount);
        motionBlurShader.setUniformValue("halfSamples", halfSampleAmount);
        motionBlurShader.setUniformValue("inverseSamples", invSamples);
        motionBlurShader.setUniformValue("blurAlgorithm", MotionBlurModule.blurAlgorithm.ordinal());

        motionBlurShader.render(deltaTick);
    }

    private int getSampleAmountForFPS(float fps) {
        int quality = config.getQuality();

        int baseSamples = switch (quality) {
            case 0 -> 8;
            case 1 -> 12;
            case 2 -> 16;
            case 3 -> 24;
            default -> 12;
        };

        if (fps < 30) {
            return Math.max(6, baseSamples / 2);
        } else if (fps < 60) {
            return Math.max(8, (int) (baseSamples * 0.75f));
        } else if (fps > 144) {
            return Math.min(32, (int) (baseSamples * 1.25f));
        }

        return baseSamples;
    }

    private final Matrix4f tempPrevModelView = new Matrix4f();
    private final Matrix4f tempPrevProjection = new Matrix4f();
    private final Matrix4f tempProjInverse = new Matrix4f();
    private final Matrix4f tempMvInverse = new Matrix4f();

    /**
     * Вызывается из MixinLevelRenderer каждый кадр с текущей и предыдущей
     * матрицами вида/проекции/позиции камеры.
     */
    public void setFrameMotionBlur(Matrix4f modelView, Matrix4f prevModelView,
                                   Matrix4f projection, Matrix4f prevProjection,
                                   Vector3f cameraPos, Vector3f prevCameraPos) {
        motionBlurShader.setUniformValue("mvInverse", tempMvInverse.set(modelView).invert());
        motionBlurShader.setUniformValue("projInverse", tempProjInverse.set(projection).invert());
        motionBlurShader.setUniformValue("prevModelView", tempPrevModelView.set(prevModelView));
        motionBlurShader.setUniformValue("prevProjection", tempPrevProjection.set(prevProjection));
        motionBlurShader.setUniformValue("cameraPos", cameraPos.x, cameraPos.y, cameraPos.z);
        motionBlurShader.setUniformValue("prevCameraPos", prevCameraPos.x, prevCameraPos.y, prevCameraPos.z);
    }

    public void updateBlurStrength(float strength) {
        motionBlurShader.setUniformValue("BlendFactor", strength);
        currentBlur = strength;
    }
}