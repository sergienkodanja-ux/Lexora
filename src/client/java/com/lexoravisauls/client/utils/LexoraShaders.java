package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.core.ClientData;
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
    public static ShaderUtil fireShader;
    public static ShaderUtil maskDiffShader;
    public static ShaderUtil handTrailShader;
    public static ShaderUtil handFireShader;

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
        String skyType = ClientData.modeSettings.containsKey("Sky Type") ? ClientData.modeSettings.get("Sky Type") : LexoraGui.modeSettings.getOrDefault("Sky Type", "Standard");
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
    public static ShaderUtil handShader;

    public static ShaderUtil getHandShader() {
        handShader = safeLoad(handShader, "hand_shader.vsh", "hand_shader.fsh");
        return handShader;
    }

    public static ShaderUtil getCurrentHandShader() {
        return getHandShader();
    }

    public static ShaderUtil getMaskDiffShader() {
        maskDiffShader = safeLoad(maskDiffShader, "hand_shader.vsh", "mask_diff.fsh");
        return maskDiffShader;
    }

    public static ShaderUtil getHandTrailShader() {
        handTrailShader = safeLoad(handTrailShader, "hand_shader.vsh", "hand_trail.fsh");
        return handTrailShader;
    }

    public static ShaderUtil getHandFireShader() {
        handFireShader = safeLoad(handFireShader, "hand_shader.vsh", "hand_fire.fsh");
        return handFireShader;
    }

    // ── Snowfall shader ───────────────────────────────────
    public static ShaderUtil snowfallShader;

    public static ShaderUtil getSnowfallShader() {
        snowfallShader = safeLoad(snowfallShader, "snowfall.vsh", "snowfall.fsh");
        return snowfallShader;
    }

    // ── Footprint shader ──────────────────────────────────
    public static ShaderUtil footprintShader;

    public static ShaderUtil getFootprintShader() {
        footprintShader = safeLoad(footprintShader, "footprint.vsh", "footprint.fsh");
        return footprintShader;
    }

    // ── Cleanup ───────────────────────────────────────────
    public static void cleanup() {
        deleteShader(snowfallShader);
        snowfallShader = null;
        com.lexoravisauls.client.modules.weather.winter.WinterSnowfall.getInstance().cleanupBuffers();

        deleteShader(footprintShader);
        footprintShader = null;
        com.lexoravisauls.client.modules.weather.winter.WinterFootprints.getInstance().cleanupBuffers();
        // Overlay
        deleteShader(webShader);
        deleteShader(spaceShader);
        deleteShader(orbShader);
        webShader = spaceShader = orbShader = null;

        // Hand
        deleteShader(handShader);
        deleteShader(snowShader);
        deleteShader(smokeShader);
        deleteShader(stripesShader);
        deleteShader(solidShader);
        deleteShader(fireShader);
        deleteShader(maskDiffShader);
        deleteShader(handTrailShader);
        deleteShader(handFireShader);
        handShader = snowShader = smokeShader = stripesShader = solidShader = fireShader = maskDiffShader = handTrailShader = handFireShader = null;

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