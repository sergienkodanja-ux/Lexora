package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LexoraShaders {

    private static final Logger LOGGER = LoggerFactory.getLogger("LexoraShaders");

    // ── Overlay шейдеры ───────────────────────────────────
    public static ShaderUtil webShader;
    public static ShaderUtil spaceShader;
    public static ShaderUtil orbShader;

    // ── Hand шейдеры ──────────────────────────────────────
    public static ShaderUtil snowShader;
    public static ShaderUtil smokeShader;
    public static ShaderUtil stripesShader;
    public static ShaderUtil solidShader;

    // ── Sky шейдеры ───────────────────────────────────────
    public static ShaderUtil waterSkyShader;
    public static ShaderUtil causticSkyShader;
    public static ShaderUtil thunderSkyShader;
    public static ShaderUtil pulsarSkyShader;

    // ── Инициализация overlay шейдеров ───────────────────
    public static void init() {
        webShader   = safeLoad(webShader,   "rendertype_lexora_web.vsh", "rendertype_lexora_web.fsh");
        spaceShader = safeLoad(spaceShader, "rendertype_lexora_web.vsh", "rendertype_lexora_space.fsh");
        orbShader   = safeLoad(orbShader,   "rendertype_lexora_web.vsh", "rendertype_lexora_orb.fsh");
    }

    // ── Инициализация sky шейдеров (lazy) ─────────────────
    public static void initSky() {
        String skyType = LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard");
        switch (skyType) {
            case "Water" ->
                    waterSkyShader   = safeLoad(waterSkyShader,   "sky_base.vsh", "water.fsh");
            case "Caustic" ->
                    causticSkyShader = safeLoad(causticSkyShader, "sky_base.vsh", "caustic.fsh");
            case "Thunder" ->
                    thunderSkyShader = safeLoad(thunderSkyShader, "sky_base.vsh", "thunder.fsh");
            case "Pulsar" ->
                    pulsarSkyShader  = safeLoad(pulsarSkyShader,  "sky_base.vsh", "pulsar.fsh");
        }
    }

    // ── Получить нужный sky шейдер ────────────────────────
    public static ShaderUtil getShader(String name) {
        return switch (name) {
            case "water" -> {
                waterSkyShader = safeLoad(waterSkyShader, "sky_base.vsh", "water.fsh");
                yield waterSkyShader;
            }
            case "caustic" -> {
                causticSkyShader = safeLoad(causticSkyShader, "sky_base.vsh", "caustic.fsh");
                yield causticSkyShader;
            }
            case "thunder" -> {
                thunderSkyShader = safeLoad(thunderSkyShader, "sky_base.vsh", "thunder.fsh");
                yield thunderSkyShader;
            }
            case "pulsar" -> {
                pulsarSkyShader = safeLoad(pulsarSkyShader, "sky_base.vsh", "pulsar.fsh");
                yield pulsarSkyShader;
            }
            default -> null;
        };
    }

    // ── Overlay: текущий шейдер ───────────────────────────
    public static ShaderUtil getCurrentShader() {
        init();
        String mode = LexoraGui.modeSettings.getOrDefault("Overlay Mode", "Web");
        return switch (mode) {
            case "Plasma" -> spaceShader;
            case "Grid"   -> orbShader;
            default       -> webShader;
        };
    }

    // ── Hand: текущий шейдер ──────────────────────────────
    public static ShaderUtil getCurrentHandShader() {
        init();
        String mode = LexoraGui.modeSettings.getOrDefault("Hand Mode", "Snow");
        return switch (mode) {
            case "Smoke" -> {
                smokeShader = safeLoad(smokeShader, "hand.vsh", "smoke.fsh");
                yield smokeShader;
            }
            case "Stripes" -> {
                stripesShader = safeLoad(stripesShader, "hand.vsh", "stripes.fsh");
                yield stripesShader;
            }
            case "Solid" -> {
                solidShader = safeLoad(solidShader, "hand.vsh", "solid.fsh");
                yield solidShader;
            }
            default -> {
                snowShader = safeLoad(snowShader, "hand.vsh", "snow.fsh");
                yield snowShader;
            }
        };
    }

    // ── Cleanup ───────────────────────────────────────────
    public static void cleanup() {
        // Overlay
        deleteShader(webShader);
        deleteShader(spaceShader);
        deleteShader(orbShader);
        webShader = spaceShader = orbShader = null;

        // Hand
        deleteShader(snowShader);
        deleteShader(smokeShader);
        deleteShader(stripesShader);
        deleteShader(solidShader);
        snowShader = smokeShader = stripesShader = solidShader = null;

        // Sky
        deleteShader(waterSkyShader);
        deleteShader(causticSkyShader);
        deleteShader(thunderSkyShader);
        deleteShader(pulsarSkyShader);
        waterSkyShader = causticSkyShader = thunderSkyShader = pulsarSkyShader = null;

        HandShaderCopy.cleanup();
        HandShaderRenderer.cleanup();
        HandShaderStencil.cleanup();
    }

    // ── Helpers ───────────────────────────────────────────
    private static ShaderUtil safeLoad(ShaderUtil current, String vsh, String fsh) {
        if (current != null && current.isValid()) return current;
        if (current != null) current.delete();
        try {
            ShaderUtil shader = new ShaderUtil(vsh, fsh);
            return shader.isValid() ? shader : null;
        } catch (Throwable t) {
            LOGGER.error("[Lexora] safeLoad threw for vsh='{}' fsh='{}'", vsh, fsh, t);
            return null;
        }
    }

    private static void deleteShader(ShaderUtil shader) {
        if (shader != null) shader.delete();
    }
}