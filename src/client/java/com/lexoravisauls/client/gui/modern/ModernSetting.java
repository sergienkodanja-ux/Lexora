package com.lexoravisauls.client.gui.modern;

import java.util.List;
import java.util.function.BooleanSupplier;

public class ModernSetting {

    public enum Type {
        TOGGLE,
        SLIDER,
        MODE,
        COLOR,
        BIND,
        HEADER,
        PAD2D
    }

    public final Type type;
    public final String key;
    public final String label;

    public float min;
    public float max;

    public float minY;
    public float maxY;
    public String keyY;

    public List<String> modes;

    public BooleanSupplier visibleRule = () -> true;

    private ModernSetting(Type type, String key, String label) {
        this.type = type;
        this.key = key;
        this.label = label;
    }

    public static ModernSetting toggle(String key, String label) {
        return new ModernSetting(Type.TOGGLE, key, label);
    }

    public static ModernSetting slider(String key, String label, float min, float max) {
        ModernSetting s = new ModernSetting(Type.SLIDER, key, label);
        s.min = min;
        s.max = max;
        return s;
    }

    public static ModernSetting mode(String key, String label, String... modes) {
        ModernSetting s = new ModernSetting(Type.MODE, key, label);
        s.modes = List.of(modes);
        return s;
    }

    public static ModernSetting color(String key, String label) {
        return new ModernSetting(Type.COLOR, key, label);
    }

    public static ModernSetting bind(String key, String label) {
        return new ModernSetting(Type.BIND, key, label);
    }

    public static ModernSetting header(String label) {
        return new ModernSetting(Type.HEADER, null, label);
    }

    public static ModernSetting pad2d(String keyX, String keyY, String label,
                                      float minX, float maxX,
                                      float minY, float maxY) {
        ModernSetting s = new ModernSetting(Type.PAD2D, keyX, label);
        s.keyY = keyY;
        s.min = minX;
        s.max = maxX;
        s.minY = minY;
        s.maxY = maxY;
        return s;
    }

    public ModernSetting visibleIf(BooleanSupplier rule) {
        this.visibleRule = rule;
        return this;
    }

    public boolean isVisible() {
        return visibleRule == null || visibleRule.getAsBoolean();
    }
}