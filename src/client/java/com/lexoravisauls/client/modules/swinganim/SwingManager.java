package com.lexoravisauls.client.modules.swinganim;

import com.lexoravisauls.client.modules.VMAnimations;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

import java.util.ArrayList;
import java.util.List;

public class SwingManager {
    private static final SwingManager INSTANCE = new SwingManager();

    public static SwingManager getInstance() {
        return INSTANCE;
    }

    private final List<SwingPreset> builtInPresets = new ArrayList<>();
    private final SwingPresetManager presetManager = new SwingPresetManager();

    private Vec2f bezierStart = new Vec2f(0.5f, 1.0f);
    private Vec2f bezierEnd = new Vec2f(0.5f, 0.0f);
    private boolean swingBack = true;
    private float speed = 2.0f;

    private final SwingPhase startPhase = new SwingPhase();
    private final SwingPhase endPhase = new SwingPhase();

    private String currentPresetName = "swings.standard";

    public SwingManager() {
        initializePresets();
        presetManager.scanPresetDirectory();

        SwingPresetFile autosave = presetManager.getPreset("autosave");
        if (autosave != null && autosave.getFile().exists()) {
            autosave.load(this);
        } else {
            applyBuiltInPreset(builtInPresets.get(0));
        }
    }

    private void initializePresets() {
        builtInPresets.add(new SwingPreset(
                "swings.standard",
                "Стандарт",
                new Vec2f(0.4F, 0.8F),
                new Vec2f(0.2F, 0.2F),
                true,
                1.0F,
                new SwingTransformations(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                new SwingTransformations(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -35.0F, 0.0F, 0.0F)
        ));

        builtInPresets.add(new SwingPreset(
                "swings.block_hit",
                "Под наклоном",
                new Vec2f(0.5F, 1.0F),
                new Vec2f(0.5F, 0.0F),
                true,
                2.0F,
                new SwingTransformations(0.0F, -0.05F, -0.7F, 1.0500001F, -0.7F, -1.1F, -120.0F, -135.0F, -60.0F),
                new SwingTransformations(0.0F, -0.05F, -0.7F, 1.0500001F, -0.7F, -1.1F, -120.0F, -180.0F, -60.0F)
        ));

        builtInPresets.add(new SwingPreset(
                "swings.bonk",
                "Боньк",
                new Vec2f(0.40131578F, 0.53543305F),
                new Vec2f(0.0F, -0.24409449F),
                true,
                2.0F,
                new SwingTransformations(0.0F, -0.4F, -0.65000004F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                new SwingTransformations(0.0F, -0.4F, -0.65000004F, 0.0F, 0.0F, 0.0F, -45.0F, 0.0F, 0.0F)
        ));

        builtInPresets.add(new SwingPreset(
                "swings.rotate_360",
                "Вращение на 360",
                new Vec2f(0.43421054F, 0.61417323F),
                new Vec2f(0.04605263F, -0.26771653F),
                false,
                2.0F,
                new SwingTransformations(0.0F, -0.4F, -0.65000004F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                new SwingTransformations(0.0F, -0.4F, -0.65000004F, 0.0F, 0.0F, 0.0F, -360.0F, 0.0F, 0.0F)
        ));

        builtInPresets.add(new SwingPreset(
                "swings.from_me",
                "От себя",
                new Vec2f(0.42105263F, 0.87401575F),
                new Vec2f(0.3881579F, -0.4566929F),
                true,
                2.0F,
                new SwingTransformations(0.0F, 0.0F, -1.1F, 0.2F, 0.0F, -0.1F, -135.0F, 45.0F, 60.0F),
                new SwingTransformations(0.0F, 0.0F, -1.1F, 0.2F, 0.0F, -0.3F, -180.0F, 45.0F, 60.0F)
        ));
    }

    public void applyBuiltInPreset(SwingPreset preset) {
        if (preset == null) return;
        this.bezierStart = preset.getBezierStart();
        this.bezierEnd = preset.getBezierEnd();
        this.swingBack = preset.isSwingBack();
        this.speed = preset.getSpeed();
        this.startPhase.setFrom(preset.getFrom());
        this.endPhase.setFrom(preset.getTo());
        this.currentPresetName = preset.getName();
        this.presetManager.setCurrent(null);
    }

    public SwingTransformations transformations(float swingProgress) {
        float progress = VMAnimations.easeBezier(
                swingProgress, bezierStart.x, 1.0f - bezierStart.y, bezierEnd.x, 1.0f - bezierEnd.y
        );

        if (this.swingBack) {
            progress = MathHelper.sin(MathHelper.sqrt(MathHelper.clamp(progress, 0.0f, 1.0f)) * (float) Math.PI);
        }

        return new SwingTransformations(
                lerp(startPhase.anchorX, endPhase.anchorX, progress),
                lerp(startPhase.anchorY, endPhase.anchorY, progress),
                lerp(startPhase.anchorZ, endPhase.anchorZ, progress),
                lerp(startPhase.moveX, endPhase.moveX, progress),
                lerp(startPhase.moveY, endPhase.moveY, progress),
                lerp(startPhase.moveZ, endPhase.moveZ, progress),
                lerp(startPhase.rotateX, endPhase.rotateX, progress),
                lerp(startPhase.rotateY, endPhase.rotateY, progress),
                lerp(startPhase.rotateZ, endPhase.rotateZ, progress)
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public List<SwingPreset> getBuiltInPresets() {
        return builtInPresets;
    }

    public SwingPresetManager getPresetManager() {
        return presetManager;
    }

    public Vec2f getBezierStart() {
        return bezierStart;
    }

    public void setBezierStart(Vec2f bezierStart) {
        this.bezierStart = bezierStart;
    }

    public Vec2f getBezierEnd() {
        return bezierEnd;
    }

    public void setBezierEnd(Vec2f bezierEnd) {
        this.bezierEnd = bezierEnd;
    }

    public boolean isSwingBack() {
        return swingBack;
    }

    public void setSwingBack(boolean swingBack) {
        this.swingBack = swingBack;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
        com.lexoravisauls.client.core.ClientData.numSettings.put("VM Speed", speed);
        com.lexoravisauls.client.gui.LexoraGui.numSettings.put("VM Speed", speed);
    }

    public void resetToStandard() {
        startPhase.setAnchor(0.0f, 0.0f, 0.0f);
        endPhase.setAnchor(0.0f, 0.0f, 0.0f);
        startPhase.setMove(0.0f, 0.0f, 0.0f);
        endPhase.setMove(0.0f, 0.0f, 0.0f);
        startPhase.setRotate(0.0f, 0.0f, 0.0f);
        endPhase.setRotate(-35.0f, 0.0f, 0.0f);
        bezierStart = new Vec2f(0.4f, 0.8f);
        bezierEnd = new Vec2f(0.2f, 0.2f);
        swingBack = true;
        setSpeed(1.0f);
        currentPresetName = "swings.standard";
        presetManager.setCurrent(null);
        new SwingPresetFile("autosave").save(this);
    }

    public String generateRandomAnimation() {
        java.util.Random rnd = new java.util.Random();
        int style = rnd.nextInt(6);
        String styleName;

        switch (style) {
            case 0 -> {
                styleName = "Рубящий слэш";
                startPhase.setAnchor(0.0f, -0.1f + rndF(rnd, -0.05f, 0.05f), -0.6f + rndF(rnd, -0.1f, 0.1f));
                endPhase.setAnchor(0.0f, -0.1f + rndF(rnd, -0.05f, 0.05f), -0.6f + rndF(rnd, -0.1f, 0.1f));
                startPhase.setMove(0.0f, 0.15f + rndF(rnd, -0.05f, 0.05f), -0.1f);
                endPhase.setMove(0.1f + rndF(rnd, -0.05f, 0.05f), -0.35f + rndF(rnd, -0.05f, 0.05f), -0.4f + rndF(rnd, -0.1f, 0.1f));
                startPhase.setRotate(20f + rndF(rnd, -5f, 5f), -15f + rndF(rnd, -5f, 5f), 10f);
                endPhase.setRotate(-85f + rndF(rnd, -10f, 10f), -40f + rndF(rnd, -10f, 10f), 30f + rndF(rnd, -5f, 5f));
                bezierStart = new Vec2f(0.2f + rndF(rnd, -0.05f, 0.05f), 0.85f + rndF(rnd, -0.05f, 0.05f));
                bezierEnd = new Vec2f(0.1f + rndF(rnd, -0.05f, 0.05f), -0.15f + rndF(rnd, -0.05f, 0.05f));
                swingBack = true;
                setSpeed(Math.round((1.4f + rndF(rnd, 0.0f, 0.6f)) * 10f) / 10f);
            }
            case 1 -> {
                styleName = "Колющий выпад";
                startPhase.setAnchor(0.0f, 0.0f, -0.8f + rndF(rnd, -0.1f, 0.1f));
                endPhase.setAnchor(0.0f, 0.0f, -0.8f + rndF(rnd, -0.1f, 0.1f));
                startPhase.setMove(0.0f, 0.0f, 0.2f + rndF(rnd, 0.0f, 0.1f));
                endPhase.setMove(0.05f, -0.05f, -0.85f + rndF(rnd, -0.15f, 0.1f));
                startPhase.setRotate(15f + rndF(rnd, -5f, 5f), 0.0f, 5.0f);
                endPhase.setRotate(-10f + rndF(rnd, -5f, 5f), 5.0f, -15f + rndF(rnd, -5f, 5f));
                bezierStart = new Vec2f(0.25f, 0.95f);
                bezierEnd = new Vec2f(0.05f, 0.1f);
                swingBack = true;
                setSpeed(Math.round((1.8f + rndF(rnd, 0.0f, 0.5f)) * 10f) / 10f);
            }
            case 2 -> {
                styleName = "Боковой свип";
                startPhase.setAnchor(0.0f, -0.2f, -0.7f);
                endPhase.setAnchor(0.0f, -0.2f, -0.7f);
                startPhase.setMove(-0.25f + rndF(rnd, -0.05f, 0.05f), 0.0f, 0.0f);
                endPhase.setMove(0.35f + rndF(rnd, -0.05f, 0.05f), -0.1f, -0.3f);
                startPhase.setRotate(0.0f, 55f + rndF(rnd, -5f, 5f), -20f);
                endPhase.setRotate(-45f + rndF(rnd, -5f, 5f), -85f + rndF(rnd, -10f, 10f), 35f);
                bezierStart = new Vec2f(0.35f, 0.75f);
                bezierEnd = new Vec2f(0.15f, 0.05f);
                swingBack = true;
                setSpeed(Math.round((1.3f + rndF(rnd, 0.0f, 0.4f)) * 10f) / 10f);
            }
            case 3 -> {
                styleName = "Вращение";
                startPhase.setAnchor(0.0f, -0.4f, -0.65f);
                endPhase.setAnchor(0.0f, -0.4f, -0.65f);
                startPhase.setMove(0.0f, 0.0f, 0.0f);
                endPhase.setMove(0.0f, 0.0f, 0.0f);
                startPhase.setRotate(0.0f, 0.0f, 0.0f);
                endPhase.setRotate(-360.0f, 0.0f, 0.0f);
                bezierStart = new Vec2f(0.43f, 0.61f);
                bezierEnd = new Vec2f(0.05f, -0.25f);
                swingBack = false;
                setSpeed(Math.round((2.0f + rndF(rnd, 0.0f, 0.5f)) * 10f) / 10f);
            }
            case 4 -> {
                styleName = "Тяжелый молот";
                startPhase.setAnchor(0.0f, -0.3f, -0.6f);
                endPhase.setAnchor(0.0f, -0.3f, -0.6f);
                startPhase.setMove(0.0f, 0.35f, -0.1f);
                endPhase.setMove(0.0f, -0.4f, -0.5f);
                startPhase.setRotate(45f, 0.0f, 0.0f);
                endPhase.setRotate(-110f, 0.0f, 0.0f);
                bezierStart = new Vec2f(0.4f, 0.8f);
                bezierEnd = new Vec2f(0.0f, -0.3f);
                swingBack = true;
                setSpeed(Math.round((1.2f + rndF(rnd, 0.0f, 0.3f)) * 10f) / 10f);
            }
            default -> {
                styleName = "Плавный взмах";
                startPhase.setAnchor(0.0f, -0.05f, -0.5f);
                endPhase.setAnchor(0.0f, -0.05f, -0.5f);
                startPhase.setMove(0.0f, 0.05f, 0.0f);
                endPhase.setMove(0.1f, -0.2f, -0.25f);
                startPhase.setRotate(10f, 0.0f, 0.0f);
                endPhase.setRotate(-60f, -25f, 15f);
                bezierStart = new Vec2f(0.4f, 0.8f);
                bezierEnd = new Vec2f(0.2f, 0.2f);
                swingBack = true;
                setSpeed(Math.round((1.4f + rndF(rnd, 0.0f, 0.3f)) * 10f) / 10f);
            }
        }

        this.currentPresetName = "Кастом (" + styleName + ")";
        this.presetManager.setCurrent(null);
        new SwingPresetFile("autosave").save(this);
        return styleName;
    }

    private static float rndF(java.util.Random rnd, float min, float max) {
        return min + rnd.nextFloat() * (max - min);
    }

    public SwingPhase getStartPhase() {
        return startPhase;
    }

    public SwingPhase getEndPhase() {
        return endPhase;
    }

    public String getCurrentPresetName() {
        return currentPresetName;
    }

    public void setCurrentPresetName(String currentPresetName) {
        this.currentPresetName = currentPresetName;
    }
}
