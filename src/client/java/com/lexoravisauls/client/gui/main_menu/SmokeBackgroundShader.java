package com.lexoravisauls.client.gui.main_menu;

import com.mojang.blaze3d.systems.RenderSystem;
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

public final class SmokeBackgroundShader {

    private static final ShaderProgramKey SMOKE_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/smoke_menu"),
                    VertexFormats.POSITION,
                    Defines.EMPTY
            );

    private static final long START_TIME = System.currentTimeMillis();

    private SmokeBackgroundShader() {}

    public static void render(DrawContext context, float width, float height, float mouseX, float mouseY, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0.0f) return;

        context.draw();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ShaderProgram program = RenderSystem.setShader(SMOKE_SHADER);
        if (program != null) {
            float time = (System.currentTimeMillis() - START_TIME) / 1000.0f;

            setUniform1f(program, "uTime", time);
            setUniform2f(program, "uResolution", width, height);
            setUniform2f(program, "uMouse", mouseX, mouseY);
            setUniform1f(program, "uAlpha", alpha);

            drawQuad(matrix, 0, 0, width, height);

            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            // Резервный эстетичный градиент
            int a = (int) (alpha * 255) << 24;
            context.fillGradient(0, 0, (int) width, (int) height, a | 0x07080E, a | 0x14121F);
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawQuad(Matrix4f matrix, float x, float y, float w, float h) {
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buf.vertex(matrix, x, y, 0f);
        buf.vertex(matrix, x, y + h, 0f);
        buf.vertex(matrix, x + w, y + h, 0f);
        buf.vertex(matrix, x + w, y, 0f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void setUniform1f(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setUniform2f(ShaderProgram p, String name, float v1, float v2) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v1, v2);
    }
}
