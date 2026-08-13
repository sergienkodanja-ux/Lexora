package com.lexoravisauls.client.modules.killeffect;

import com.lexoravisauls.client.gui.LexoraGui;

/**
 * Thin bridge between the kill-effect code and LexoraGui's live setting values.
 * <p>
 * Confirmed straight from LexoraGui.java: {@code modeSettings} is
 * {@code Map<String, String>}, {@code moduleStates} is {@code Map<String, Boolean>}
 * (backs both toggle(...) settings and a module's own on/off state), and
 * {@code numSettings} is {@code Map<String, Float>} (backs slider(...) settings).
 */
final class KillEffectSettings {

    private KillEffectSettings() {
    }

    static String mode(String key, String def) {
        return LexoraGui.modeSettings.getOrDefault(key, def);
    }

    static boolean bool(String key, boolean def) {
        return LexoraGui.moduleStates.getOrDefault(key, def);
    }

    static float slider(String key, float def) {
        return LexoraGui.numSettings.getOrDefault(key, def);
    }

    /** Whether the "Kill Effect" module itself is toggled on in the module list. */
    static boolean moduleEnabled() {
        return LexoraGui.moduleStates.getOrDefault("Kill Effect", false);
    }
}
