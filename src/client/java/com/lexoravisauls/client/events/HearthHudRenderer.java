package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class HearthHudRenderer {
    public static int WIDTH = 100;
    public static int HEIGHT = 20;


    private static final Identifier CONTAINER = Identifier.of("minecraft", "hud/heart/container");
    private static final Identifier FULL = Identifier.of("minecraft", "hud/heart/full");
    private static final Identifier HALF = Identifier.of("minecraft", "hud/heart/half");
    private static final Identifier ABSORB_FULL = Identifier.of("minecraft", "hud/heart/absorbing_full");
    private static final Identifier ABSORB_HALF = Identifier.of("minecraft", "hud/heart/absorbing_half");

    private static float lastHealth = -1;
    private static long animationTime = 0;
    private static final long ANIMATION_DURATION = 500;

    public static void render(DrawContext context, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Hearth Hud", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        float scale = LexoraGui.numSettings.getOrDefault("Hearth Hud Scale", 1.0f);
        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Hearth Hud Solid", false);

        int x = HudManager.hearthX == -1 ? 10 : HudManager.hearthX;
        int y = HudManager.hearthY == -1 ? 50 : HudManager.hearthY;

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float absorb = player.getAbsorptionAmount();

        if (lastHealth == -1) {
            lastHealth = health;
        }

        if (health != lastHealth) {
            animationTime = System.currentTimeMillis();
            lastHealth = health;
        }

        long elapsed = System.currentTimeMillis() - animationTime;
        float animationScale = 1.0f;

        if (elapsed < ANIMATION_DURATION) {
            float progress = (float) elapsed / ANIMATION_DURATION;
            animationScale = 1.0f + (float) Math.sin(progress * Math.PI) * 0.1f;
        }

        int heartCount = MathHelper.ceil(maxHealth / 2.0f);
        int absorbCount = MathHelper.ceil(absorb / 2.0f);

        int totalHearts = Math.max(10, heartCount + absorbCount);
        WIDTH = 12 + (totalHearts * 8) + 1;
        HEIGHT = 20;

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1.0f);

        int alpha = 255;
        int bgAlpha = isSolid ? alpha : (int) (alpha * 0.6f);
        int bgColor = (bgAlpha << 24) | 0x141416;

        drawSmoothRect(context, 0, 0, WIDTH, HEIGHT, bgColor);

        int heartX = 6;
        int heartY = 5;

        for (int i = 0; i < heartCount; i++) {
            context.getMatrices().push();
            context.getMatrices().translate(heartX + i * 8 + 4.5f, heartY + 4.5f, 0);
            context.getMatrices().scale(animationScale, animationScale, 1.0f);
            context.getMatrices().translate(-(heartX + i * 8 + 4.5f), -(heartY + 4.5f), 0);

            context.drawGuiTexture(RenderLayer::getGuiTextured, CONTAINER, heartX + i * 8, heartY, 9, 9);

            if (health >= (i + 1) * 2) {
                context.drawGuiTexture(RenderLayer::getGuiTextured, FULL, heartX + i * 8, heartY, 9, 9);
            } else if (health > i * 2) {
                context.drawGuiTexture(RenderLayer::getGuiTextured, HALF, heartX + i * 8, heartY, 9, 9);
            }
            context.getMatrices().pop();
        }

        for (int i = 0; i < absorbCount; i++) {
            int idx = heartCount + i;

            context.getMatrices().push();
            context.getMatrices().translate(heartX + idx * 8 + 4.5f, heartY + 4.5f, 0);
            context.getMatrices().scale(animationScale, animationScale, 1.0f);
            context.getMatrices().translate(-(heartX + idx * 8 + 4.5f), -(heartY + 4.5f), 0);

            context.drawGuiTexture(RenderLayer::getGuiTextured, CONTAINER, heartX + idx * 8, heartY, 9, 9);

            if (absorb >= (i + 1) * 2) {
                context.drawGuiTexture(RenderLayer::getGuiTextured, ABSORB_FULL, heartX + idx * 8, heartY, 9, 9);
            } else if (absorb > i * 2) {
                context.drawGuiTexture(RenderLayer::getGuiTextured, ABSORB_HALF, heartX + idx * 8, heartY, 9, 9);
            }
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    private static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;

        float radius = Math.min(4.0f, Math.min(width / 2.0f, height / 2.0f));
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }
}