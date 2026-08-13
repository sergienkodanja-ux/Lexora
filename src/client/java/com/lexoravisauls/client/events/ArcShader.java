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

/**
 * ArcShader — рисует идеальную дугу/кольцо через SDF-шейдер.
 * Один draw call. Никаких pill-сегментов, никакой пиксельности.
 *
 * assets/lexoravisauls/shaders/core/arc_lexora.json / .vsh / .fsh
 */
public final class ArcShader {

    private ArcShader() {}

    private static final ShaderProgramKey ARC_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/arc_lexora"),
                    VertexFormats.POSITION,
                    Defines.EMPTY
            );

    // =========================================================================
    //  Публичный API
    // =========================================================================

    /**
     * Рисует дугу от startDeg до endDeg по часовой (0 = 12 часов).
     *
     * @param ctx      DrawContext
     * @param cx       центр X в GUI пикселях
     * @param cy       центр Y в GUI пикселях
     * @param outerR   внешний радиус в GUI пикселях
     * @param innerR   внутренний радиус (outerR - thickness)
     * @param startDeg начало дуги в градусах
     * @param endDeg   конец дуги в градусах
     * @param color    ARGB цвет
     */
    public static void drawArc(DrawContext ctx,
                               float cx, float cy,
                               float outerR, float innerR,
                               float startDeg, float endDeg,
                               int color) {
        int a = (color >>> 24) & 0xFF;
        if (a <= 3 || outerR <= 0f || innerR < 0f || outerR <= innerR) return;

        ctx.draw();

        MinecraftClient mc = MinecraftClient.getInstance();
        float ws  = (float) mc.getWindow().getScaleFactor();
        float fbH = (float) mc.getWindow().getFramebufferHeight();

        // Перевод через матрицу — учитывает scale/translate анимации желе и userScale
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        org.joml.Vector4f pc  = new org.joml.Vector4f(cx,          cy, 0f, 1f).mul(mat);
        org.joml.Vector4f pr  = new org.joml.Vector4f(cx + outerR,  cy, 0f, 1f).mul(mat);
        org.joml.Vector4f pri = new org.joml.Vector4f(cx + innerR,  cy, 0f, 1f).mul(mat);

        float fcx = pc.x  * ws;
        float fcy = fbH - pc.y * ws;
        // Реальные радиусы в fb-пикселях = расстояние от центра до точки на радиусе
        float fro = Math.abs(pr.x  - pc.x) * ws;
        float fri = Math.abs(pri.x - pc.x) * ws;

        // Защита: innerR должен быть строго меньше outerR и > 0
        if (fro <= 0f || fri >= fro) return;

        // ФИКС ПИКСЕЛЕЙ: увеличенный margin (запас в 6px), чтобы сглаживанию (SDF)
        // хватало места и края круга не срезались на мелком худе
        float margin = outerR + 6f;
        float qx = cx - margin;
        float qy = cy - margin;
        float qw = margin * 2f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ShaderProgram prog = RenderSystem.setShader(ARC_SHADER);
        if (prog == null) {
            RoundedRectShader.draw(ctx, cx - outerR, cy - outerR,
                    outerR * 2f, outerR * 2f, outerR, color);
            RenderSystem.enableDepthTest();
            return;
        }

        float r  = ((color >>> 16) & 0xFF) / 255f;
        float g  = ((color >>>  8) & 0xFF) / 255f;
        float b  = ( color         & 0xFF) / 255f;
        float al = a / 255f;

        float a0 = (float) Math.toRadians(startDeg);
        float a1 = (float) Math.toRadians(endDeg);

        set2f(prog, "Center",    fcx, fcy);
        set2f(prog, "Radii",     fri, fro);
        set2f(prog, "Angles",    a0,  a1);
        set4f(prog, "FillColor", r, g, b, al);
        set1f(prog, "Softness",  1.0f);

        drawQuad(mat, qx, qy, qw, qw);

        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Полный круг (360°). */
    public static void drawRing(DrawContext ctx,
                                float cx, float cy,
                                float outerR, float innerR,
                                int color) {
        drawArc(ctx, cx, cy, outerR, innerR, 0f, 360f, color);
    }

    /** Дуга от 12 часов на fraction*360° по часовой. fraction = 0..1 */
    public static void drawArcFraction(DrawContext ctx,
                                       float cx, float cy,
                                       float outerR, float innerR,
                                       float fraction,
                                       int color) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        if (fraction < 0.003f) return;
        if (fraction > 0.997f) { drawRing(ctx, cx, cy, outerR, innerR, color); return; }
        drawArc(ctx, cx, cy, outerR, innerR, 0f, fraction * 360f, color);
    }

    // =========================================================================
    //  Внутренние утилиты
    // =========================================================================

    private static void drawQuad(Matrix4f mat, float x, float y, float w, float h) {
        BufferBuilder buf = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buf.vertex(mat, x,     y,     0f);
        buf.vertex(mat, x,     y + h, 0f);
        buf.vertex(mat, x + w, y + h, 0f);
        buf.vertex(mat, x + w, y,     0f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void set1f(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name); if (u != null) u.set(v);
    }
    private static void set2f(ShaderProgram p, String name, float v1, float v2) {
        GlUniform u = p.getUniform(name); if (u != null) u.set(v1, v2);
    }
    private static void set4f(ShaderProgram p, String name,
                              float v1, float v2, float v3, float v4) {
        GlUniform u = p.getUniform(name); if (u != null) u.set(v1, v2, v3, v4);
    }
}