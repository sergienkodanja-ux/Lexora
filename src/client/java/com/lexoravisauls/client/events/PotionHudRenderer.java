package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.LexoraIcons; // Вернул импорт иконок
import com.lexoravisauls.client.gui.MsdfFont;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.screen.ChatScreen;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PotionHudRenderer {

    // --- ШРИФТЫ ---
    private static final Identifier FONT_TEX = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    // --- ФИЗИКА "ЖЕЛЕ" (Spring Animation) ---
    private static float animAlpha = 0f, animScale = 0f, animW = 120f, animH = 22f;
    private static float scaleV = 0f, wV = 0f, hV = 0f;

    // --- ПУБЛИЧНЫЕ РАЗМЕРЫ ДЛЯ ХИТБОКСА (HudManager / MixinChatScreen) ---
    public static int WIDTH = 120;
    public static int HEIGHT = 22;

    public static MsdfFont getFont() {
        if (msdfFont == null) {
            msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        }
        return msdfFont;
    }

    // Метод упругой анимации (Желе)
    private static float spring(float current, float target, float[] velocity, float tension, float friction) {
        velocity[0] += (target - current) * tension;
        velocity[0] *= friction;
        return current + velocity[0];
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        boolean enabled = isModuleEnabled("Potions", true);
        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof ChatScreen;

        Collection<StatusEffectInstance> activeEffects = mc.player.getStatusEffects();
        List<StatusEffectInstance> effectsToRender = new ArrayList<>(activeEffects);

        if (effectsToRender.isEmpty() && showPreview) {
            effectsToRender.add(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1));
            effectsToRender.add(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 0));
        }

        boolean shouldShow = enabled && !effectsToRender.isEmpty();

        float targetW = 120f;
        for (StatusEffectInstance effect : effectsToRender) {
            String name = getEffectName(effect);
            String duration = getDurationString(effect);
            float w = 32f + width(name, 7.5f) + 20f + width(duration, 7.5f);
            if (w > targetW) targetW = w;
        }
        float targetH = 22f + (effectsToRender.size() * 18f);

        animAlpha += ((shouldShow ? 1.0f : 0.0f) - animAlpha) * 0.15f;
        float baseScale = getNum("Potions Scale", 1.0f);
        float targetScaleValue = shouldShow ? baseScale : baseScale * 0.7f;

        float[] vScale = {scaleV}, vW = {wV}, vH = {hV};
        animScale = spring(animScale, targetScaleValue, vScale, 0.2f, 0.65f);
        animW = spring(animW, targetW, vW, 0.2f, 0.65f);
        animH = spring(animH, targetH, vH, 0.2f, 0.65f);
        scaleV = vScale[0]; wV = vW[0]; hV = vH[0];

        WIDTH = Math.round(animW);
        HEIGHT = Math.round(animH);

        if (animAlpha < 0.02f) return;

        float x = HudManager.potionX == -1 ? 10 : HudManager.potionX;
        float y = HudManager.potionY == -1 ? 100 : HudManager.potionY;

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        context.getMatrices().push();
        context.getMatrices().translate(x + WIDTH / 2f, y + HEIGHT / 2f, 0);
        context.getMatrices().scale(animScale, animScale, 1.0f);
        context.getMatrices().translate(-(x + WIDTH / 2f), -(y + HEIGHT / 2f), 0);

        int alphaInt = (int) (animAlpha * 255);

        drawPanel(context, x, y, WIDTH, HEIGHT, alphaInt);

        // Вернул иконку! (замени POTION на свое название из Enum, если оно отличается)
        LexoraIcons.draw(context, LexoraIcons.Icon.POTION, x + 6, y + 6, 9.0f, (alphaInt << 24) | 0xFFFFFF);
        drawString(context, "Potions", x + 18, y + 6.5f, 8.0f, (alphaInt << 24) | 0xFFFFFF);

        float currentY = y + 24;
        for (StatusEffectInstance effect : effectsToRender) {
            Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(effect.getEffectType());
            if (sprite != null) {
                drawSprite(context, sprite, x + 6, currentY - 3, 14, 14, alphaInt);
            }

            String name = getEffectName(effect);
            drawString(context, name, x + 26, currentY + 1, 7.5f, (alphaInt << 24) | 0xEEEEEE);

            String duration = getDurationString(effect);
            drawString(context, duration, x + WIDTH - 8 - width(duration, 7.5f), currentY + 1, 7.5f, (alphaInt << 24) | 0xAAAAAA);

            currentY += 18;
        }

        context.getMatrices().pop();

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // =========================================================================
    // УТИЛИТЫ И ОТРИСОВКА
    // =========================================================================

    public static void drawPanel(DrawContext context, float x, float y, float width, float height, int alpha) {
        boolean blurEnabled = LexoraGui.moduleStates.getOrDefault("Potions Blur", true);
        int bgColor = (Math.min(alpha, 160) << 24) | 0x050505;

        if (blurEnabled && alpha > 10) {
            context.draw(); // <--- ДОБАВИТЬ ЭТО
            com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(context, x, y, width, height, 6f, 15f, bgColor);
        } else {
            drawSmoothRect(context, (int)x, (int)y, (int)width, (int)height, 6f, bgColor);
        }
    }

    public static String getEffectName(StatusEffectInstance effect) {
        try {
            String baseName = effect.getEffectType().value().getName().getString();
            int amp = effect.getAmplifier();
            if (amp == 1) return baseName + " II";
            if (amp == 2) return baseName + " III";
            if (amp == 3) return baseName + " IV";
            if (amp == 4) return baseName + " V";
            if (amp > 4) return baseName + " " + (amp + 1);
            return baseName;
        } catch (Exception e) {
            return "Potion";
        }
    }

    public static String getDurationString(StatusEffectInstance effect) {
        if (effect.isInfinite()) return "∞";
        int seconds = effect.getDuration() / 20;
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", min, sec);
    }

    public static void drawSprite(DrawContext context, Sprite sprite, float x, float y, float w, float h, int alphaInt) {
        float a = alphaInt / 255.0F;
        RenderSystem.setShaderTexture(0, sprite.getAtlasId());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.enableBlend();

        Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buf.vertex(mat, x,     y,     0f).texture(sprite.getMinU(), sprite.getMinV()).color(1f, 1f, 1f, a);
        buf.vertex(mat, x,     y + h, 0f).texture(sprite.getMinU(), sprite.getMaxV()).color(1f, 1f, 1f, a);
        buf.vertex(mat, x + w, y + h, 0f).texture(sprite.getMaxU(), sprite.getMaxV()).color(1f, 1f, 1f, a);
        buf.vertex(mat, x + w, y,     0f).texture(sprite.getMaxU(), sprite.getMinV()).color(1f, 1f, 1f, a);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void drawSprite(DrawContext context, Identifier texture, float x, float y, float w, float h, int alphaInt) {
        float a = alphaInt / 255.0F;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.enableBlend();
        Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(mat, x,     y,     0f).texture(0f, 0f).color(1f, 1f, 1f, a);
        buf.vertex(mat, x,     y + h, 0f).texture(0f, 1f).color(1f, 1f, 1f, a);
        buf.vertex(mat, x + w, y + h, 0f).texture(1f, 1f).color(1f, 1f, 1f, a);
        buf.vertex(mat, x + w, y,     0f).texture(1f, 0f).color(1f, 1f, 1f, a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static boolean isModuleEnabled(String key, boolean fallback) {
        return LexoraGui.moduleStates.getOrDefault(key, ClientData.moduleStates.getOrDefault(key, fallback)) || ClientData.moduleStates.getOrDefault(key, fallback);
    }

    public static float getNum(String key, float fallback) {
        if (LexoraGui.numSettings.containsKey(key)) return LexoraGui.numSettings.get(key);
        return ClientData.numSettings.getOrDefault(key, fallback);
    }

    public static void drawString(DrawContext context, String text, float x, float y, float size, int color) {
        if (text == null || text.trim().isEmpty()) return;
        getFont().draw(context.getMatrices(), text, x, y, size, color);
    }

    public static float width(String text, float size) {
        if (text == null || text.trim().isEmpty()) return 0.0f;
        return getFont().getWidth(text, size);
    }

    public static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, float radius, int color) {
        if (width <= 0 || height <= 0) return;
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }
}