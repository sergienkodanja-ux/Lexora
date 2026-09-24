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

public final class HudFireShader {

    private HudFireShader() {}

    private static final ShaderProgramKey HUD_FIRE_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/hud_fire"),
                    VertexFormats.POSITION,
                    Defines.EMPTY
            );

    private static final long START_TIME = System.currentTimeMillis();

    private static float[] calcRect(Matrix4f matrix, float x, float y, float width, float height, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float ws = (float) mc.getWindow().getScaleFactor();
        float fbH = (float) mc.getWindow().getFramebufferHeight();

        Vector4f p1 = new Vector4f(x, y, 0f, 1f).mul(matrix);
        Vector4f p2 = new Vector4f(x + width, y + height, 0f, 1f).mul(matrix);
        Vector4f pr = new Vector4f(x + radius, y, 0f, 1f).mul(matrix);

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
        buf.vertex(matrix, x, y, 0f);
        buf.vertex(matrix, x, y + h, 0f);
        buf.vertex(matrix, x + w, y + h, 0f);
        buf.vertex(matrix, x + w, y, 0f);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    /**
     * Отрисовка огненной ауры вокруг прямоугольника ХУДа с поддержкой затухания (extinguish)
     */
    public static void drawFireAura(DrawContext context, float x, float y, float width, float height,
                                    float radius, float auraPadding, int color, float intensity, float extinguish) {
        if (width <= 0f || height <= 0f) return;

        context.draw();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        float[] rc = calcRect(matrix, x, y, width, height, radius);

        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >>> 16) & 0xFF) / 255f;
        float g = ((color >>> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        if (a <= 0.01f || intensity <= 0.01f || extinguish >= 0.99f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ShaderProgram program = RenderSystem.setShader(HUD_FIRE_SHADER);
        if (program == null) {
            RoundedRectShader.draw(context, x, y, width, height, radius, color);
            RenderSystem.enableDepthTest();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float ws = (float) mc.getWindow().getScaleFactor();

        GlUniform uRect = program.getUniform("Rect");
        if (uRect != null) uRect.set(rc[0], rc[1], rc[2], rc[3]);

        GlUniform uRadius = program.getUniform("Radius");
        if (uRadius != null) uRadius.set(rc[4]);

        GlUniform uTime = program.getUniform("Time");
        if (uTime != null) {
            float elapsedSec = (System.currentTimeMillis() - START_TIME) / 1000.0f;
            uTime.set(elapsedSec);
        }

        GlUniform uTheme = program.getUniform("ThemeColor");
        if (uTheme != null) uTheme.set(r, g, b, a);

        GlUniform uParams = program.getUniform("FireParams");
        if (uParams != null) {
            uParams.set(intensity, 1.0f, auraPadding * ws, extinguish);
        }

        GlUniform uAngle = program.getUniform("Angle");
        if (uAngle != null) uAngle.set(0.0f);

        drawQuad(matrix, x - auraPadding, y - auraPadding, width + auraPadding * 2f, height + auraPadding * 2f);

        RenderSystem.enableDepthTest();
    }

    /**
     * Отрисовка огненной черточки пунктира с точным вращением по градусу траектории
     */
    public static void drawRotatedFireDash(DrawContext context, float centerX, float centerY,
                                           float dashLength, float dashThickness, float angleRad,
                                           float auraPadding, int color, float intensity, float extinguish) {
        if (dashLength <= 0f || dashThickness <= 0f) return;

        context.draw();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        MinecraftClient mc = MinecraftClient.getInstance();
        float ws = (float) mc.getWindow().getScaleFactor();
        float fbH = (float) mc.getWindow().getFramebufferHeight();

        // Преобразование координат центра в фреймбуфер
        Vector4f pc = new Vector4f(centerX, centerY, 0f, 1f).mul(matrix);
        float fcx = pc.x * ws;
        float fcy = fbH - pc.y * ws;
        float fw = dashLength * ws;
        float fh = dashThickness * ws;
        float fr = (dashThickness * 0.5f) * ws;

        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >>> 16) & 0xFF) / 255f;
        float g = ((color >>> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        if (a <= 0.01f || intensity <= 0.01f || extinguish >= 0.99f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ShaderProgram program = RenderSystem.setShader(HUD_FIRE_SHADER);
        if (program == null) {
            RenderSystem.enableDepthTest();
            return;
        }

        GlUniform uRect = program.getUniform("Rect");
        if (uRect != null) uRect.set(fcx - fw * 0.5f, fcy - fh * 0.5f, fw, fh);

        GlUniform uRadius = program.getUniform("Radius");
        if (uRadius != null) uRadius.set(fr);

        GlUniform uTime = program.getUniform("Time");
        if (uTime != null) {
            float elapsedSec = (System.currentTimeMillis() - START_TIME) / 1000.0f;
            uTime.set(elapsedSec);
        }

        GlUniform uTheme = program.getUniform("ThemeColor");
        if (uTheme != null) uTheme.set(r, g, b, a);

        GlUniform uParams = program.getUniform("FireParams");
        if (uParams != null) {
            uParams.set(intensity, 1.2f, auraPadding * ws, extinguish);
        }

        // В OpenGL FB координатах ось Y инвертирована по отношению к GUI, поэтому угол = -angleRad
        GlUniform uAngle = program.getUniform("Angle");
        if (uAngle != null) uAngle.set(-angleRad);

        // Размер квадрата отрисовки с запасом под вращение и ауру
        float drawSize = Math.max(dashLength, dashThickness) + auraPadding * 2f;
        drawQuad(matrix, centerX - drawSize * 0.5f, centerY - drawSize * 0.5f, drawSize, drawSize);

        RenderSystem.enableDepthTest();
    }
}
