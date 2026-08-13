package com.lexoravisauls.client.events;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

// 🔥 Больше никаких импортов GL11! 🔥

public final class RoundedRectShader {

    private RoundedRectShader() {}

    private static final ShaderProgramKey ROUNDED_RECT_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/rounded_rect_lexora"),
                    VertexFormats.POSITION,
                    Defines.EMPTY
            );

    private static float[] calcRect(Matrix4f matrix, float x, float y,
                                    float width, float height, float radius) {
        MinecraftClient mc  = MinecraftClient.getInstance();
        float ws  = (float) mc.getWindow().getScaleFactor();
        float fbH = (float) mc.getWindow().getFramebufferHeight();

        Vector4f p1 = new Vector4f(x,         y,          0f, 1f).mul(matrix);
        Vector4f p2 = new Vector4f(x + width,  y + height, 0f, 1f).mul(matrix);
        Vector4f pr = new Vector4f(x + radius, y,          0f, 1f).mul(matrix);

        float sx = Math.min(p1.x, p2.x), sy = Math.min(p1.y, p2.y);
        float sw = Math.abs(p2.x - p1.x), sh = Math.abs(p2.y - p1.y);
        float sr = Math.abs(pr.x - p1.x);

        float rw = sw * ws, rh = sh * ws;
        float rr = Math.min(sr * ws, Math.min(rw, rh) * 0.5f);

        return new float[]{ sx * ws, fbH - ((sy + sh) * ws), rw, rh, rr };
    }

    private static void drawQuad(Matrix4f matrix, float x, float y, float w, float h) {
        BufferBuilder buf = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buf.vertex(matrix, x,     y,     0f);
        buf.vertex(matrix, x,     y + h, 0f);
        buf.vertex(matrix, x + w, y + h, 0f);
        buf.vertex(matrix, x + w, y,     0f);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    public static void draw(DrawContext context,
                            float x, float y, float width, float height,
                            float radius, int color) {
        if (width <= 0f || height <= 0f) return;

        context.draw(); // Сбрасываем очередь ванильного Майнкрафта

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        float[] rc = calcRect(matrix, x, y, width, height, radius);

        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >>> 16) & 0xFF) / 255f;
        float g = ((color >>>  8) & 0xFF) / 255f;
        float b = ( color         & 0xFF) / 255f;

        // Включаем настройки легально через RenderSystem
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ShaderProgram program = RenderSystem.setShader(ROUNDED_RECT_SHADER);
        if (program == null) {
            context.fill(Math.round(x), Math.round(y),
                    Math.round(x + width), Math.round(y + height), color);
            RenderSystem.enableDepthTest();
            return;
        }

        setUniform4f(program, "Rect",      rc[0], rc[1], rc[2], rc[3]);
        setUniform1f(program, "Radius",    rc[4]);
        setUniform4f(program, "FillColor", r, g, b, a);
        setUniform1f(program, "Softness",  1f);
        setUniform1i(program, "UseGradient", 0);
        setUniform1i(program, "UseTexture",  0);

        drawQuad(matrix, x, y, width, height);

        // Возвращаем базовые цвета и глубину
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void drawGradient(DrawContext context,
                                    float x, float y, float width, float height,
                                    float radius, int colorLeft, int colorRight) {
        if (width <= 0f || height <= 0f) return;

        context.draw();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        float[] rc = calcRect(matrix, x, y, width, height, radius);
        float[] cL = toFloats(colorLeft);
        float[] cR = toFloats(colorRight);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ShaderProgram program = RenderSystem.setShader(ROUNDED_RECT_SHADER);
        if (program == null) {
            context.fillGradient(Math.round(x), Math.round(y),
                    Math.round(x + width), Math.round(y + height),
                    colorLeft, colorRight);
            RenderSystem.enableDepthTest();
            return;
        }

        setUniform4f(program, "Rect",      rc[0], rc[1], rc[2], rc[3]);
        setUniform1f(program, "Radius",    rc[4]);
        setUniform4f(program, "FillColor", cL[0], cL[1], cL[2], cL[3]);
        setUniform1f(program, "Softness",  1f);
        setUniform4f(program, "ColorTL",   cL[0], cL[1], cL[2], cL[3]);
        setUniform4f(program, "ColorTR",   cR[0], cR[1], cR[2], cR[3]);
        setUniform4f(program, "ColorBL",   cL[0], cL[1], cL[2], cL[3]);
        setUniform4f(program, "ColorBR",   cR[0], cR[1], cR[2], cR[3]);
        setUniform1i(program, "UseGradient", 1);
        setUniform1i(program, "UseTexture",  0);

        drawQuad(matrix, x, y, width, height);

        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void drawTextured(DrawContext context,
                                    Identifier texture,
                                    float x, float y, float width, float height,
                                    float radius,
                                    float u0, float v0, float u1, float v1,
                                    int colorTint) {
        if (width <= 0f || height <= 0f) return;

        context.draw();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        float[] rc   = calcRect(matrix, x, y, width, height, radius);
        float[] tint = toFloats(colorTint);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        // Майнкрафт сам запомнит эту текстуру в кэше!
        RenderSystem.setShaderTexture(0, texture);

        ShaderProgram program = RenderSystem.setShader(ROUNDED_RECT_SHADER);
        if (program == null) {
            RenderSystem.enableDepthTest();
            return;
        }

        setUniform4f(program, "Rect",      rc[0], rc[1], rc[2], rc[3]);
        setUniform1f(program, "Radius",    rc[4]);
        setUniform4f(program, "FillColor", tint[0], tint[1], tint[2], tint[3]);
        setUniform1f(program, "Softness",  1f);
        setUniform1i(program, "UseGradient", 0);
        setUniform1i(program, "UseTexture",  1);
        setUniform2f(program, "TexMin", u0, v0);
        setUniform2f(program, "TexMax", u1, v1);

        drawQuad(matrix, x, y, width, height);

        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void setUniform1f(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setUniform1i(ShaderProgram p, String name, int v) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setUniform2f(ShaderProgram p, String name, float v1, float v2) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v1, v2);
    }

    private static void setUniform4f(ShaderProgram p, String name,
                                     float v1, float v2, float v3, float v4) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v1, v2, v3, v4);
    }

    private static float[] toFloats(int color) {
        return new float[]{
                ((color >>> 16) & 0xFF) / 255f,
                ((color >>>  8) & 0xFF) / 255f,
                ( color         & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f
        };
    }

    public static void register() {}
}