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

public final class ShockwaveShader {

    private ShockwaveShader() {}

    private static final ShaderProgramKey SHOCKWAVE_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/shockwave_lexora"),
                    VertexFormats.POSITION,
                    Defines.EMPTY
            );

    private static float waveCenterX = 0f;
    private static float waveCenterY = 0f;
    private static float waveProgress = 1.0f;
    private static long waveStartTime = 0;
    private static final long WAVE_DURATION = 750; // ms
    private static boolean waveToLight = false;
    private static boolean shaderAvailable = true;

    public static void trigger(float x, float y, boolean toLight) {
        waveCenterX = x;
        waveCenterY = y;
        waveProgress = 0.0f;
        waveStartTime = System.currentTimeMillis();
        waveToLight = toLight;
    }

    public static boolean isRunning() {
        return waveProgress < 1.0f;
    }

    public static float getProgress() {
        return waveProgress;
    }

    public static void render(DrawContext context, float screenW, float screenH) {
        if (waveProgress >= 1.0f) return;

        long elapsed = System.currentTimeMillis() - waveStartTime;
        float rawT = Math.min(1.0f, (float) elapsed / WAVE_DURATION);
        // Smooth quartic ease-out
        waveProgress = 1.0f - (float) Math.pow(1.0f - rawT, 4.0);

        context.draw();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        MinecraftClient mc = MinecraftClient.getInstance();
        float ws = (float) mc.getWindow().getScaleFactor();
        float fbH = (float) mc.getWindow().getFramebufferHeight();

        Vector4f c = new Vector4f(waveCenterX, waveCenterY, 0f, 1f).mul(matrix);
        float cx = c.x * ws;
        float cy = fbH - (c.y * ws);

        float maxRadius = (float) Math.hypot(screenW, screenH) * ws * 1.35f;
        float curRadius = waveProgress * maxRadius;

        float[] startCol = waveToLight ? new float[]{0.05f, 0.05f, 0.07f, 1.0f} : new float[]{0.96f, 0.96f, 0.98f, 1.0f};
        float[] endCol   = waveToLight ? new float[]{0.96f, 0.96f, 0.98f, 1.0f} : new float[]{0.05f, 0.05f, 0.07f, 1.0f};

        if (shaderAvailable) {
            try {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();

                ShaderProgram program = RenderSystem.setShader(SHOCKWAVE_SHADER);
                if (program != null) {
                    setUniform2f(program, "uCenter", cx, cy);
                    setUniform1f(program, "uRadius", curRadius);
                    setUniform1f(program, "uWaveWidth", 60.0f * ws);
                    setUniform1f(program, "uProgress", waveProgress);
                    setUniform4f(program, "uStartColor", startCol[0], startCol[1], startCol[2], startCol[3]);
                    setUniform4f(program, "uEndColor", endCol[0], endCol[1], endCol[2], endCol[3]);

                    drawQuad(matrix, 0f, 0f, screenW, screenH);
                }

                RenderSystem.enableDepthTest();
            } catch (Throwable t) {
                shaderAvailable = false;
                t.printStackTrace();
            }
        }
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

    private static void setUniform1f(ShaderProgram program, String name, float v0) {
        GlUniform u = program.getUniform(name);
        if (u != null) u.set(v0);
    }

    private static void setUniform2f(ShaderProgram program, String name, float v0, float v1) {
        GlUniform u = program.getUniform(name);
        if (u != null) u.set(v0, v1);
    }

    private static void setUniform4f(ShaderProgram program, String name, float v0, float v1, float v2, float v3) {
        GlUniform u = program.getUniform(name);
        if (u != null) u.set(v0, v1, v2, v3);
    }
}
