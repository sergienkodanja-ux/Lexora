package com.lexoravisauls.client.gui.modern;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

public class ScreenCaptureManager {

    private static SimpleFramebuffer captureFbo = null;
    private static boolean hasCapture = false;

    public static void captureScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        Framebuffer mainFbo = client.getFramebuffer();
        if (mainFbo == null) return;
        int w = mainFbo.textureWidth;
        int h = mainFbo.textureHeight;
        if (w <= 0 || h <= 0) return;

        RenderSystem.assertOnRenderThread();

        if (captureFbo == null || captureFbo.textureWidth != w || captureFbo.textureHeight != h) {
            if (captureFbo != null) captureFbo.delete();
            captureFbo = new SimpleFramebuffer(w, h, false);
        }

        GlStateManager._glBindFramebuffer(36008, mainFbo.fbo);
        GlStateManager._glBindFramebuffer(36009, captureFbo.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, h, w, 0, 16384, 9728);

        mainFbo.beginWrite(false);
        hasCapture = true;
    }

    public static int getTexture() {
        return captureFbo != null ? captureFbo.getColorAttachment() : -1;
    }

    public static boolean hasCapture() {
        return hasCapture;
    }
}
