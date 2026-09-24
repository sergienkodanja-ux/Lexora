package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TntHudRenderer {

    private static final Identifier FONT_TEX = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static final Identifier TNT_BLOCK_TEX = Identifier.of("minecraft", "textures/block/tnt_side.png");
    private static MsdfFont msdfFont = null;

    private static float animAlpha = 0f, animScale = 0f, animW = 115f, animH = 42f;
    private static float scaleV = 0f, wV = 0f, hV = 0f;

    public static int WIDTH = 115;
    public static int HEIGHT = 42;

    public static MsdfFont getFont() {
        if (msdfFont == null) {
            msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        }
        return msdfFont;
    }

    private static float spring(float current, float target, float[] velocity, float tension, float friction) {
        velocity[0] += (target - current) * tension;
        velocity[0] *= friction;
        return current + velocity[0];
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        boolean enabled = isModuleEnabled("TNT Detect", true);
        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof ChatScreen;

        // Ищем зажжённые ТНТ в радиусе 64 блоков
        TntEntity targetTnt = null;
        double searchRadius = 64.0;

        List<TntEntity> tnts = mc.world.getEntitiesByClass(
                TntEntity.class,
                mc.player.getBoundingBox().expand(searchRadius),
                TntEntity::isAlive
        );

        if (!tnts.isEmpty()) {
            targetTnt = tnts.stream()
                    .min(Comparator.comparingInt(TntEntity::getFuse))
                    .orElse(null);
        }

        boolean hasTnt = targetTnt != null;
        boolean shouldShow = enabled && (hasTnt || showPreview);

        float fuseSeconds = 4.0f;
        if (hasTnt) {
            fuseSeconds = Math.max(0f, (targetTnt.getFuse() - tickDelta) / 20.0f);
        } else if (showPreview) {
            fuseSeconds = 3.5f;
        }

        float targetW = 115f;
        float targetH = 42f;

        animAlpha += ((shouldShow ? 1.0f : 0.0f) - animAlpha) * 0.15f;
        float baseScale = getNum("TNT Detect Scale", 1.0f);
        float targetScaleValue = shouldShow ? baseScale : baseScale * 0.7f;

        float[] vScale = {scaleV}, vW = {wV}, vH = {hV};
        animScale = spring(animScale, targetScaleValue, vScale, 0.2f, 0.65f);
        animW = spring(animW, targetW, vW, 0.2f, 0.65f);
        animH = spring(animH, targetH, vH, 0.2f, 0.65f);
        scaleV = vScale[0]; wV = vW[0]; hV = vH[0];

        WIDTH = Math.round(animW);
        HEIGHT = Math.round(animH);

        if (animAlpha < 0.02f) return;

        float x = HudManager.tntX == -1 ? 10 : HudManager.tntX;
        float y = HudManager.tntY == -1 ? 160 : HudManager.tntY;

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        context.getMatrices().push();
        context.getMatrices().translate(x + WIDTH / 2f, y + HEIGHT / 2f, 0);
        context.getMatrices().scale(animScale, animScale, 1.0f);
        context.getMatrices().translate(-(x + WIDTH / 2f), -(y + HEIGHT / 2f), 0);

        int alphaInt = (int) (animAlpha * 255);

        float pulse = (float) (Math.sin(System.currentTimeMillis() * 0.007) * 0.5 + 0.5);
        drawRedGlow(context, x, y, WIDTH, HEIGHT, alphaInt, pulse);

        drawPanel(context, x, y, WIDTH, HEIGHT, alphaInt);

        drawSprite(context, TNT_BLOCK_TEX, x + 6, y + 5.5f, 13, 13, alphaInt);

        drawString(context, "TNT", x + 23, y + 7.5f, 8.0f, HudThemeHelper.getTextColor(alphaInt));

        String timerText = String.format(Locale.US, "%.2fs", fuseSeconds);
        int timerColor;

        if (fuseSeconds <= 1.0f) {
            timerColor = (alphaInt << 24) | 0xFF3333;
        } else if (fuseSeconds <= 2.5f) {
            timerColor = (alphaInt << 24) | 0xFFAA00;
        } else {
            timerColor = (alphaInt << 24) | 0x55FF55;
        }

        drawString(context, "Detonation:", x + 8, y + 24f, 7.0f, HudThemeHelper.getSecondaryTextColor(alphaInt));
        drawString(context, timerText, x + WIDTH - 8 - width(timerText, 8.0f), y + 23.5f, 8.0f, timerColor);

        context.getMatrices().pop();

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void drawRedGlow(DrawContext context, float x, float y, float width, float height, int alphaInt, float pulse) {
        float baseAlpha = (alphaInt / 255.0f) * (0.15f + pulse * 0.40f);
        if (baseAlpha <= 0.005f) return;

        for (int i = 6; i >= 1; i--) {
            float expand = i * 2.2f;
            float layerAlpha = baseAlpha * (1.0f - (i / 7.0f)) * 0.35f;
            int redGlowColor = ((int) (layerAlpha * 255) << 24) | 0xFF1515;

            RoundedRectShader.draw(context,
                    x - expand,
                    y - expand,
                    width + expand * 2.0f,
                    height + expand * 2.0f,
                    6.0f + expand * 0.6f,
                    redGlowColor);
        }
    }

    public static void drawPanel(DrawContext context, float x, float y, float width, float height, int alpha) {
    boolean blurEnabled = isModuleEnabled("TNT Detect Blur", true);
    HudThemeHelper.drawHudPanel(context, x, y, width, height, 6f, alpha, blurEnabled);
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