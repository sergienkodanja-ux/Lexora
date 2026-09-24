package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.awt.Color;

public class CrosshairRenderer {

    private static float animatedGap = 3.0f;
    private static float hoverAnim = 0.0f;
    private static long lastRenderTime = 0L;

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!ClientData.moduleStates.getOrDefault("Crosshair", false)) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;

        long now = System.currentTimeMillis();
        if (lastRenderTime == 0L) lastRenderTime = now;
        float dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        float cx = mc.getWindow().getScaledWidth() / 2f;
        float cy = mc.getWindow().getScaledHeight() / 2f;

        String type = ClientData.modeSettings.getOrDefault("Crosshair Type", "Classic");
        boolean dynamicGap = ClientData.moduleStates.getOrDefault("Crosshair Dynamic Gap", true);
        boolean highlightTarget = ClientData.moduleStates.getOrDefault("Crosshair Highlight Target", true);
        boolean centerDot = ClientData.moduleStates.getOrDefault("Crosshair Center Dot", false);

        float thickness = ClientData.numSettings.getOrDefault("Crosshair Thickness", 1.2f);
        float length = ClientData.numSettings.getOrDefault("Crosshair Length", 4.0f);
        float baseGap = ClientData.numSettings.getOrDefault("Crosshair Gap", 2.5f);
        float gapIncrease = ClientData.numSettings.getOrDefault("Crosshair Gap Increase", 5.0f);

        // Smooth dynamic gap interpolation
        float cooldown = dynamicGap ? (1.0f - mc.player.getAttackCooldownProgress(0.0f)) : 0.0f;
        float targetGap = baseGap + (dynamicGap ? gapIncrease * cooldown : 0.0f);
        animatedGap += (targetGap - animatedGap) * (1.0f - (float) Math.exp(-20.0 * dt));
        float currentGap = animatedGap;

        // Base color selection
        String colorMode = ClientData.modeSettings.getOrDefault("Crosshair Color Mode", "Theme");
        int baseColor;
        if (colorMode.equals("White")) {
            baseColor = 0xFFFFFFFF;
        } else if (colorMode.equals("Custom")) {
            float[] hsv = LexoraGui.colorSettings.getOrDefault("Crosshair Custom Color", new float[]{0f, 1f, 1f});
            baseColor = 0xFF000000 | Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        } else {
            baseColor = 0xFF000000 | LexoraGui.getThemeColor(0f);
        }

        // Smooth target highlight color fading
        boolean onEntity = mc.targetedEntity != null && mc.targetedEntity.isAlive();
        float targetHover = (highlightTarget && onEntity) ? 1.0f : 0.0f;
        hoverAnim += (targetHover - hoverAnim) * (1.0f - (float) Math.exp(-15.0 * dt));

        int finalColor;
        if (hoverAnim > 0.01f) {
            finalColor = blendColors(baseColor, 0xFFFF3333, hoverAnim);
        } else {
            finalColor = baseColor;
        }

        RenderSystem.enableBlend();

        switch (type) {
            case "Classic" -> {
                float rad = Math.min(1.2f, thickness * 0.5f);
                // Top
                RoundedRectShader.draw(context, (int) Math.floor(cx - thickness / 2f), (int) Math.floor(cy - currentGap - length), (int) Math.ceil(thickness), (int) Math.ceil(length), rad, finalColor);
                // Bottom
                RoundedRectShader.draw(context, (int) Math.floor(cx - thickness / 2f), (int) Math.floor(cy + currentGap), (int) Math.ceil(thickness), (int) Math.ceil(length), rad, finalColor);
                // Left
                RoundedRectShader.draw(context, (int) Math.floor(cx - currentGap - length), (int) Math.floor(cy - thickness / 2f), (int) Math.ceil(length), (int) Math.ceil(thickness), rad, finalColor);
                // Right
                RoundedRectShader.draw(context, (int) Math.floor(cx + currentGap), (int) Math.floor(cy - thickness / 2f), (int) Math.ceil(length), (int) Math.ceil(thickness), rad, finalColor);

                if (centerDot) {
                    float dotSize = Math.max(1.0f, thickness);
                    RoundedRectShader.draw(context, (int) Math.floor(cx - dotSize / 2f), (int) Math.floor(cy - dotSize / 2f), (int) Math.ceil(dotSize), (int) Math.ceil(dotSize), dotSize * 0.5f, finalColor);
                }
            }
            case "T-Shape" -> {
                float rad = Math.min(1.2f, thickness * 0.5f);
                // Left
                RoundedRectShader.draw(context, (int) Math.floor(cx - currentGap - length), (int) Math.floor(cy - thickness / 2f), (int) Math.ceil(length), (int) Math.ceil(thickness), rad, finalColor);
                // Right
                RoundedRectShader.draw(context, (int) Math.floor(cx + currentGap), (int) Math.floor(cy - thickness / 2f), (int) Math.ceil(length), (int) Math.ceil(thickness), rad, finalColor);
                // Bottom
                RoundedRectShader.draw(context, (int) Math.floor(cx - thickness / 2f), (int) Math.floor(cy + currentGap), (int) Math.ceil(thickness), (int) Math.ceil(length), rad, finalColor);

                if (centerDot) {
                    float dotSize = Math.max(1.0f, thickness);
                    RoundedRectShader.draw(context, (int) Math.floor(cx - dotSize / 2f), (int) Math.floor(cy - dotSize / 2f), (int) Math.ceil(dotSize), (int) Math.ceil(dotSize), dotSize * 0.5f, finalColor);
                }
            }
            case "Dot" -> {
                float dotSize = Math.max(1.5f, thickness * 1.6f);
                RoundedRectShader.draw(context, (int) Math.floor(cx - dotSize / 2f), (int) Math.floor(cy - dotSize / 2f), (int) Math.ceil(dotSize), (int) Math.ceil(dotSize), dotSize * 0.5f, finalColor);
            }
            case "Custom" -> {
                CustomCrosshairData.load();
                int size = CustomCrosshairData.GRID_SIZE;
                int center = size / 2; // 10
                // High-resolution: 0.5f size per pixel unit by default, scaled with thickness
                float pxScale = Math.max(0.5f, thickness * 0.5f);
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        if (CustomCrosshairData.MATRIX[y][x]) {
                            float px = cx + (x - center) * pxScale - pxScale / 2f;
                            float py = cy + (y - center) * pxScale - pxScale / 2f;
                            context.fill((int) Math.floor(px), (int) Math.floor(py), (int) Math.ceil(px + pxScale), (int) Math.ceil(py + pxScale), finalColor);
                        }
                    }
                }
            }
        }
    }

    private static int blendColors(int c1, int c2, float t) {
        float it = 1f - t;
        int a = (int) (((c1 >> 24) & 255) * it + ((c2 >> 24) & 255) * t);
        int r = (int) (((c1 >> 16) & 255) * it + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * it + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * it + (c2 & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
