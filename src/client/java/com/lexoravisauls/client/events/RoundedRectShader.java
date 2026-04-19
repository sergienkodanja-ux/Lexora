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

public final class RoundedRectShader {

    private RoundedRectShader() {}

    private static final ShaderProgramKey ROUNDED_RECT_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/rounded_rect_lexora"),
                    VertexFormats.POSITION,
                    Defines.EMPTY
            );

    public static void draw(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0.0f || height <= 0.0f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        float windowScale = (float) mc.getWindow().getScaleFactor();
        float framebufferHeight = (float) mc.getWindow().getFramebufferHeight();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        Vector4f p1 = new Vector4f(x, y, 0.0f, 1.0f).mul(matrix);
        Vector4f p2 = new Vector4f(x + width, y + height, 0.0f, 1.0f).mul(matrix);

        float sx = Math.min(p1.x, p2.x);
        float sy = Math.min(p1.y, p2.y);
        float sw = Math.abs(p2.x - p1.x);
        float sh = Math.abs(p2.y - p1.y);

        Vector4f pr = new Vector4f(x + radius, y, 0.0f, 1.0f).mul(matrix);
        float scaledRadius = Math.abs(pr.x - p1.x);

        float rx = sx * windowScale;
        float ry = framebufferHeight - ((sy + sh) * windowScale);
        float rw = sw * windowScale;
        float rh = sh * windowScale;
        float rr = Math.min(scaledRadius * windowScale, Math.min(rw, rh) * 0.5f);

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        context.draw();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ShaderProgram program = RenderSystem.setShader(ROUNDED_RECT_SHADER);
        if (program == null) {
            context.fill((int) sx, (int) sy, (int) (sx + sw), (int) (sy + sh), color);
            return;
        }

        setUniform4f(program, "Rect", rx, ry, rw, rh);
        setUniform1f(program, "Radius", rr);
        setUniform4f(program, "FillColor", r, g, b, a);
        setUniform1f(program, "Softness", 1.0f);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(matrix, x, y, 0.0f);
        buffer.vertex(matrix, x, y + height, 0.0f);
        buffer.vertex(matrix, x + width, y + height, 0.0f);
        buffer.vertex(matrix, x + width, y, 0.0f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
    }

    private static void setUniform1f(ShaderProgram program, String name, float value) {
        GlUniform uniform = program.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
            uniform.upload();
        }
    }

    private static void setUniform4f(ShaderProgram program, String name, float v1, float v2, float v3, float v4) {
        GlUniform uniform = program.getUniform(name);
        if (uniform != null) {
            uniform.set(v1, v2, v3, v4);
            uniform.upload();
        }
    }

    public static void register() {
    }
}