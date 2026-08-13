package com.lexoravisauls.client.utils;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class HandShaderCopy {
    private static int beforeTex = -1;
    private static int afterTex = -1;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private static int createTexture(int width, int height) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                0L
        );

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        return tex;
    }

    private static void ensureTextures(int width, int height) {
        if (beforeTex != -1 && afterTex != -1 && width == lastWidth && height == lastHeight) {
            return;
        }

        cleanup();

        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        beforeTex = createTexture(width, height);
        afterTex = createTexture(width, height);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);

        lastWidth = width;
        lastHeight = height;
    }

    private static int copyToTexture(int tex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return 0;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return 0;

        ensureTextures(width, height);

        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);

        return tex;
    }

    public static int captureBefore() {
        return copyToTexture(beforeTex);
    }

    public static int captureAfter() {
        return copyToTexture(afterTex);
    }

    public static int getBeforeTex() {
        return beforeTex;
    }

    public static int getAfterTex() {
        return afterTex;
    }

    public static void cleanup() {
        if (beforeTex != -1) {
            GL11.glDeleteTextures(beforeTex);
            beforeTex = -1;
        }

        if (afterTex != -1) {
            GL11.glDeleteTextures(afterTex);
            afterTex = -1;
        }

        lastWidth = -1;
        lastHeight = -1;
    }
}