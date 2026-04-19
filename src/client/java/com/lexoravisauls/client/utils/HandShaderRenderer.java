package com.lexoravisauls.client.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public class HandShaderRenderer {
    private static int vaoID = -1;
    private static int vboID = -1;
    private static int dummyDepthTex = -1;

    private static void setupQuad() {
        if (vaoID != -1) return;

        vaoID = GL30.glGenVertexArrays();
        vboID = GL15.glGenBuffers();

        float[] vertices = {
                -1.0f, -1.0f, 0.0f,  0.0f, 0.0f,
                1.0f, -1.0f, 0.0f,  1.0f, 0.0f,
                -1.0f,  1.0f, 0.0f,  0.0f, 1.0f,
                1.0f,  1.0f, 0.0f,  1.0f, 1.0f
        };

        GL30.glBindVertexArray(vaoID);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static void drawPerfectQuad() {
        setupQuad();

        int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vaoID);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GL30.glBindVertexArray(prevVAO);
    }

    private static void ensureDummyDepthTexture() {
        if (dummyDepthTex != -1) return;

        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        dummyDepthTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, dummyDepthTex);

        ByteBuffer pixel = BufferUtils.createByteBuffer(1);
        pixel.put((byte) 0);
        pixel.flip();

        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_R8,
                1,
                1,
                0,
                GL11.GL_RED,
                GL11.GL_UNSIGNED_BYTE,
                pixel
        );

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
    }

    public static int getDummyDepthTexture() {
        ensureDummyDepthTexture();
        return dummyDepthTex;
    }

    public static void render(ShaderUtil shader) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || shader == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        drawPerfectQuad();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    public static void cleanup() {
        if (dummyDepthTex != -1) {
            GL11.glDeleteTextures(dummyDepthTex);
            dummyDepthTex = -1;
        }

        if (vboID != -1) {
            GL15.glDeleteBuffers(vboID);
            vboID = -1;
        }

        if (vaoID != -1) {
            GL30.glDeleteVertexArrays(vaoID);
            vaoID = -1;
        }
    }
}