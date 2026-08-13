package com.lexoravisauls.client.gui.modern;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

public class ModernGuiRender {

    public static void drawLiquidGlass(DrawContext context, float x, float y, float w, float h, float borderRadius, float blurStrength, int tintColor) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // ФИКС: Вытаскиваем альфу из цвета, чтобы стекло плавно исчезало вместе с худом!
        float rawAlpha = ((tintColor >> 24) & 0xFF) / 255.0f;
        // Максимальная альфа в твоих худах обычно около 160 (0.62).
        // Нормализуем её, чтобы при альфе 160 стекло было на 100% плотным, а при падении плавно растворялось.
        float globalAlpha = Math.min(1.0f, rawAlpha / 0.6f);

        MirageGlassPipeline.draw(matrix, x, y, w, h, borderRadius, blurStrength, tintColor, globalAlpha);
    }

    // --- ТВОИ ОСТАЛЬНЫЕ МЕТОДЫ БЕЗ ИЗМЕНЕНИЙ ---
    public static void drawRoundedRect(DrawContext context, float x, float y, float w, float h, float r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        float a = ((color >> 24) & 255) / 255.0f;
        float red = ((color >> 16) & 255) / 255.0f;
        float green = ((color >> 8) & 255) / 255.0f;
        float blue = (color & 255) / 255.0f;
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        buffer.vertex(matrix, cx, cy, 0).color(red, green, blue, a);
        addArc(buffer, matrix, x + r, y + r, r, 180, 270, red, green, blue, a);
        addArc(buffer, matrix, x + w - r, y + r, r, 270, 360, red, green, blue, a);
        addArc(buffer, matrix, x + w - r, y + h - r, r, 0, 90, red, green, blue, a);
        addArc(buffer, matrix, x + r, y + h - r, r, 90, 180, red, green, blue, a);
        float firstX = x;
        float firstY = y + r;
        buffer.vertex(matrix, firstX, firstY, 0).color(red, green, blue, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
    }

    public static void drawRoundedOutline(DrawContext context, float x, float y, float w, float h, float r, int color) {
        drawRoundedRect(context, x, y, w, h, r, color);
        drawRoundedRect(context, x + 1f, y + 1f, w - 2f, h - 2f, Math.max(0, r - 1f), 0x00000000);
    }

    public static void drawShadow(DrawContext context, float x, float y, float w, float h, float r) {
        drawRoundedRect(context, x - 2, y - 2, w + 4, h + 4, r + 2, 0x16000000);
        drawRoundedRect(context, x - 1, y - 1, w + 2, h + 2, r + 1, 0x22000000);
    }

    private static void addArc(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, float radius, int startDeg, int endDeg, float red, float green, float blue, float alpha) {
        int step = 8;
        for (int deg = startDeg; deg <= endDeg; deg += step) {
            double rad = Math.toRadians(deg);
            float px = (float) (cx + Math.cos(rad) * radius);
            float py = (float) (cy + Math.sin(rad) * radius);
            buffer.vertex(matrix, px, py, 0).color(red, green, blue, alpha);
        }
    }
}