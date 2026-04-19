package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;

public class LexoraShaders {
    public static ShaderUtil webShader;
    public static ShaderUtil spaceShader;
    public static ShaderUtil orbShader;

    public static ShaderUtil snowShader;
    public static ShaderUtil smokeShader;
    public static ShaderUtil stripesShader;
    public static ShaderUtil solidShader;

    public static void init() {
        webShader = safeLoad(webShader, "rendertype_lexora_web.vsh", "rendertype_lexora_web.fsh");
        spaceShader = safeLoad(spaceShader, "rendertype_lexora_web.vsh", "rendertype_lexora_space.fsh");
        orbShader = safeLoad(orbShader, "rendertype_lexora_web.vsh", "rendertype_lexora_orb.fsh");
    }

    private static ShaderUtil safeLoad(ShaderUtil current, String vsh, String fsh) {
        if (current != null && current.isValid()) {
            return current;
        }

        if (current != null) {
            current.delete();
        }

        try {
            ShaderUtil shader = new ShaderUtil(vsh, fsh);
            return shader.isValid() ? shader : null;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public static ShaderUtil getCurrentShader() {
        init();

        String mode = LexoraGui.modeSettings.getOrDefault("Overlay Mode", "Web");
        switch (mode) {
            case "Plasma":
                return spaceShader;
            case "Grid":
                return orbShader;
            case "Web":
            default:
                return webShader;
        }
    }

    public static ShaderUtil getCurrentHandShader() {
        init();

        String mode = LexoraGui.modeSettings.getOrDefault("Hand Mode", "Snow");

        switch (mode) {
            case "Smoke":
                smokeShader = safeLoad(smokeShader, "hand.vsh", "smoke.fsh");
                return smokeShader;
            case "Stripes":
                stripesShader = safeLoad(stripesShader, "hand.vsh", "stripes.fsh");
                return stripesShader;
            case "Solid":
                solidShader = safeLoad(solidShader, "hand.vsh", "solid.fsh");
                return solidShader;
            case "Snow":
            default:
                snowShader = safeLoad(snowShader, "hand.vsh", "snow.fsh");
                return snowShader;
        }
    }

    public static void cleanup() {
        deleteShader(webShader);
        deleteShader(spaceShader);
        deleteShader(orbShader);

        deleteShader(snowShader);
        deleteShader(smokeShader);
        deleteShader(stripesShader);
        deleteShader(solidShader);

        webShader = null;
        spaceShader = null;
        orbShader = null;

        snowShader = null;
        smokeShader = null;
        stripesShader = null;
        solidShader = null;

        HandShaderCopy.cleanup();
        HandShaderRenderer.cleanup();
        HandShaderStencil.cleanup();
    }

    private static void deleteShader(ShaderUtil shader) {
        if (shader != null) {
            shader.delete();
        }
    }
}