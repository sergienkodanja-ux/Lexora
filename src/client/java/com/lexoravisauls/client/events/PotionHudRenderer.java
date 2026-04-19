package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class PotionHudRenderer {

    public static int WIDTH = 140;
    public static int HEIGHT = 25;

    private static final Identifier POTION_ICON = Identifier.of("lexoravisauls", "textures/gui/potion.png");
    private static final Identifier CUSTOM_FONT = Identifier.of("lexoravisauls", "sfui");

    private static float animHeight = 25;
    private static float animWidth = 140;
    private static float animAlpha = 0.0f;
    private static float animScale = 1.0f;

    private static class PotionData {
        String name;
        String time;
        Sprite sprite;
        StatusEffectCategory category;

        PotionData(String name, String time, Sprite sprite, StatusEffectCategory category) {
            this.name = name;
            this.time = time;
            this.sprite = sprite;
            this.category = category;
        }
    }

    public static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || !LexoraGui.moduleStates.getOrDefault("Potions", false)) return;

        int x = HudManager.potionX == -1 ? 10 : HudManager.potionX;
        int y = HudManager.potionY == -1 ? 100 : HudManager.potionY;

        List<PotionData> activePotions = new ArrayList<>();
        ClientPlayerEntity player = mc.player;
        boolean hasPotions = false;

        int targetWidth = 130;

        if (player != null && !player.getStatusEffects().isEmpty()) {
            hasPotions = true;
            for (StatusEffectInstance effectInstance : player.getStatusEffects()) {
                RegistryEntry<StatusEffect> effect = effectInstance.getEffectType();
                String name = Text.translatable(effect.value().getTranslationKey()).getString();
                if (effectInstance.getAmplifier() > 0) {
                    name += " " + (effectInstance.getAmplifier() + 1);
                }

                int seconds = effectInstance.getDuration() / 20;
                String timeStr = String.format("%02d:%02d", seconds / 60, seconds % 60);
                Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(effect);

                int currentWidth = 8 + 14 + 6 + getCustomTextWidth(name) + 15 + getCustomTextWidth(timeStr) + 8;
                if (currentWidth > targetWidth) targetWidth = currentWidth;

                activePotions.add(new PotionData(name, timeStr, sprite, effect.value().getCategory()));
            }
        }

        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        boolean shouldShow = hasPotions || showPreview;

        float smoothSpeed = 0.15f;
        animAlpha += ((shouldShow ? 1.0f : 0.0f) - animAlpha) * smoothSpeed;

        float targetScale = LexoraGui.numSettings.getOrDefault("Potions Scale", 1.0f);
        animScale += ((shouldShow ? targetScale : targetScale - 0.2f) - animScale) * smoothSpeed;

        if (animAlpha < 0.02f) return;

        int targetHeight = 25 + (hasPotions ? activePotions.size() * 20 + 4 : (showPreview ? 24 : 0));

        animHeight += (targetHeight - animHeight) * smoothSpeed;
        animWidth += (targetWidth - animWidth) * smoothSpeed;

        HEIGHT = Math.round(animHeight);
        WIDTH = Math.round(animWidth);
        int alphaInt = (int) (animAlpha * 255);

        RenderSystem.enableDepthTest();

        MatrixStack ms = context.getMatrices();
        ms.push();
        float cx = x + WIDTH / 2f;
        float cy = y + HEIGHT / 2f;
        ms.translate(cx, cy, -150);
        ms.scale(animScale, animScale, 1.0f);
        ms.translate(-cx, -cy, 0);

        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Potions Solid", false);
        int baseBgAlpha = isSolid ? 255 : 200;
        int bgAlpha = (int) (animAlpha * baseBgAlpha);

        drawSmoothRect(context, x, y, WIDTH, HEIGHT, (bgAlpha << 24) | 0x101015);
        drawSmoothRect(context, x, y, WIDTH, 20, (bgAlpha << 24) | 0x14141A);

        ms.push();
        ms.translate(0, 0, 10);

        long time = System.currentTimeMillis();

        float headerWave = (float) (Math.sin(time / 400.0) * 0.5 + 0.5);
        int headerColorVal = (int) (150 + 80 * headerWave);
        int headerShimmerRGB = (headerColorVal << 16) | (headerColorVal << 8) | headerColorVal;
        int headerColor = (alphaInt << 24) | headerShimmerRGB;

        drawTexQuad(context, POTION_ICON, x + 6, y + 5, 10, 10, 0, 0, 1, 1, headerColor);
        drawCustomText(context, "Active Potions", x + 22, y + 6, headerColor);

        int currentY = y + 24;

        if (hasPotions) {
            for (PotionData pot : activePotions) {
                if (pot.sprite != null) {
                    drawSpriteQuad(context, pot.sprite, x + 8, currentY + 1, 14, 14, alphaInt);
                }

                int textColor = 0xFFFFFF;
                if (pot.category == StatusEffectCategory.BENEFICIAL) textColor = 0x55FF55;
                else if (pot.category == StatusEffectCategory.HARMFUL) textColor = 0xFF5555;

                int itemColor = (alphaInt << 24) | textColor;
                int timeColor = (alphaInt << 24) | 0xAAAAAA;

                drawCustomText(context, pot.name, x + 28, currentY + 4, itemColor);

                int timeWidth = getCustomTextWidth(pot.time);
                drawCustomText(context, pot.time, x + WIDTH - 8 - timeWidth, currentY + 4, timeColor);

                currentY += 20;
            }
        } else if (showPreview) {
            int itemColor = (alphaInt << 24) | 0x55FF55;
            int timeColor = (alphaInt << 24) | 0xAAAAAA;

            drawTexQuad(context, POTION_ICON, x + 8, currentY + 1, 14, 14, 0, 0, 1, 1, itemColor);
            drawCustomText(context, "Speed II", x + 28, currentY + 4, itemColor);

            int tw = getCustomTextWidth("01:30");
            drawCustomText(context, "01:30", x + WIDTH - 8 - tw, currentY + 4, timeColor);
        }

        context.draw();
        ms.pop();
        ms.pop();

        RenderSystem.enableDepthTest();
    }

    private static void drawCustomText(DrawContext context, String text, int x, int y, int color) {
        context.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(text).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)),
                x, y, color, false
        );
    }

    private static int getCustomTextWidth(String text) {
        return MinecraftClient.getInstance().textRenderer.getWidth(
                Text.literal(text).setStyle(Style.EMPTY.withFont(CUSTOM_FONT))
        );
    }

    private static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;

        float radius = Math.min(6.0f, Math.min(width / 2.0f, height / 2.0f));
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }

    private static void drawTexQuad(DrawContext context, Identifier texture, float x, float y, float w, float h,
                                    float u0, float v0, float u1, float v1, int color) {
        if (((color >> 24) & 0xFF) <= 5) return;
        context.draw();

        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(u0, v0).color(r, g, b, a);
        buffer.vertex(matrix, x, y + h, 0.0F).texture(u0, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y + h, 0.0F).texture(u1, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y, 0.0F).texture(u1, v0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawSpriteQuad(DrawContext context, Sprite sprite, float x, float y, float w, float h, int a) {
        if (a <= 5) return;
        context.draw();

        float alpha = a / 255.0f;

        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, sprite.getAtlasId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(sprite.getMinU(), sprite.getMinV()).color(1.0f, 1.0f, 1.0f, alpha);
        buffer.vertex(matrix, x, y + h, 0.0F).texture(sprite.getMinU(), sprite.getMaxV()).color(1.0f, 1.0f, 1.0f, alpha);
        buffer.vertex(matrix, x + w, y + h, 0.0F).texture(sprite.getMaxU(), sprite.getMaxV()).color(1.0f, 1.0f, 1.0f, alpha);
        buffer.vertex(matrix, x + w, y, 0.0F).texture(sprite.getMaxU(), sprite.getMinV()).color(1.0f, 1.0f, 1.0f, alpha);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}