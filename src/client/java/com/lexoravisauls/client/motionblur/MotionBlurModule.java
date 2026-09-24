package com.lexoravisauls.client.motionblur;

public class MotionBlurModule {
    private static final MotionBlurModule instance = new MotionBlurModule();
    public final ShaderMotionBlur shader;
    private boolean enabled = false;
    private float strength = 0.8f;
    private boolean useRRC = true;
    private int quality = 2;

    private MotionBlurModule() {
        shader = new ShaderMotionBlur(this);
        shader.registerShaderCallbacks();
    }

    public static MotionBlurModule getInstance() {
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getStrength() {
        return strength;
    }

    public void setStrength(float strength) {
        this.strength = Math.max(0.0f, Math.min(4.0f, strength));
        shader.updateBlurStrength(this.strength);
    }

    public boolean isUseRRC() {
        return useRRC;
    }

    public void setUseRRC(boolean useRRC) {
        this.useRRC = useRRC;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = Math.max(0, Math.min(3, quality));
    }

    public String getQualityName() {
        return switch (quality) {
            case 0 -> "Низкое";
            case 1 -> "Среднее";
            case 2 -> "Высокое";
            case 3 -> "Ультра";
            default -> "Среднее";
        };
    }

    public enum BlurAlgorithm {BACKWARDS, CENTERED}

    public static BlurAlgorithm blurAlgorithm = BlurAlgorithm.CENTERED;
}