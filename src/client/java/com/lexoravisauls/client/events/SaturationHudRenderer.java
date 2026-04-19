package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public class SaturationHudRenderer {


    private static final Identifier FOOD_EMPTY = Identifier.of("minecraft", "hud/food_empty");
    private static final Identifier FOOD_HALF = Identifier.of("minecraft", "hud/food_half");
    private static final Identifier FOOD_FULL = Identifier.of("minecraft", "hud/food_full");

    public static void render(DrawContext context, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Saturation HUD", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;

        if (player == null || player.isCreative() || player.isSpectator()) return;

        float saturation = player.getHungerManager().getSaturationLevel();
        if (saturation <= 0) return;

        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();

        int y = scaledHeight - 49;


        if (player.getAir() < player.getMaxAir()) {
            y -= 10;
        }


        context.draw();

        RenderSystem.setShaderColor(1.0f, 0.8f, 0.1f, 0.9f);

        for (int i = 0; i < 10; i++) {
            int x = scaledWidth / 2 + 82 - i * 8;

            context.drawGuiTexture(RenderLayer::getGuiTextured, FOOD_EMPTY, x, y, 9, 9);

            if (saturation >= (i * 2 + 2)) {
                context.drawGuiTexture(RenderLayer::getGuiTextured, FOOD_FULL, x, y, 9, 9);
            } else if (saturation > i * 2) {
                context.drawGuiTexture(RenderLayer::getGuiTextured, FOOD_HALF, x, y, 9, 9);
            }
        }

        context.draw();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}