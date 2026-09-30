package com.lexoravisauls.client.modules.swinganim;

import net.minecraft.util.math.Vec2f;

public class SwingPreset {
    private final String name;
    private final String displayName;
    private final Vec2f bezierStart;
    private final Vec2f bezierEnd;
    private final boolean swingBack;
    private final float speed;
    private final SwingTransformations from;
    private final SwingTransformations to;

    public SwingPreset(String name, String displayName, Vec2f bezierStart, Vec2f bezierEnd, boolean swingBack, float speed, SwingTransformations from, SwingTransformations to) {
        this.name = name;
        this.displayName = displayName;
        this.bezierStart = bezierStart;
        this.bezierEnd = bezierEnd;
        this.swingBack = swingBack;
        this.speed = speed;
        this.from = from;
        this.to = to;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Vec2f getBezierStart() {
        return bezierStart;
    }

    public Vec2f getBezierEnd() {
        return bezierEnd;
    }

    public boolean isSwingBack() {
        return swingBack;
    }

    public float getSpeed() {
        return speed;
    }

    public SwingTransformations getFrom() {
        return from;
    }

    public SwingTransformations getTo() {
        return to;
    }
}
