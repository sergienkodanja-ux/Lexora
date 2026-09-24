package com.lexoravisauls.client.modules.virtualdesktop;

import com.lexoravisauls.client.core.ClientData;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous background screen capture service using java.awt.Robot.
 * Captures Windows desktop into an OpenGL dynamic texture with zero impact on Minecraft FPS.
 */
public final class ScreenCaptureService {

    private static ScreenCaptureService instance;

    private Robot robot;
    private Rectangle screenRect;
    private Thread captureThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<BufferedImage> pendingFrame = new AtomicReference<>(null);

    // OpenGL texture state (accessed only on render thread)
    private int textureId = -1;
    private int texWidth = 0;
    private int texHeight = 0;
    private ByteBuffer rawByteBuffer;

    // Telemetry
    private volatile int capturedFps = 0;
    private int frameCounter = 0;
    private long lastFpsCalcTime = System.currentTimeMillis();

    private ScreenCaptureService() {
        try {
            System.setProperty("java.awt.headless", "false");
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            this.screenRect = gd.getDefaultConfiguration().getBounds();
            this.robot = new Robot(gd);
        } catch (Throwable t) {
            System.err.println("[VirtualDesktop] Failed to initialize java.awt.Robot: " + t.getMessage());
            try {
                this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                this.robot = new Robot();
            } catch (Throwable t2) {
                System.err.println("[VirtualDesktop] Fallback Robot creation failed: " + t2.getMessage());
            }
        }

        if (this.screenRect == null || this.screenRect.width <= 0) {
            this.screenRect = new Rectangle(0, 0, 1920, 1080);
        }
    }

    public static synchronized ScreenCaptureService getInstance() {
        if (instance == null) {
            instance = new ScreenCaptureService();
        }
        return instance;
    }

    public Rectangle getScreenRect() {
        return screenRect;
    }

    public Robot getRobot() {
        return robot;
    }

    public int getCapturedFps() {
        return capturedFps;
    }

    public int getTextureId() {
        return textureId;
    }

    public int getTexWidth() {
        return texWidth;
    }

    public int getTexHeight() {
        return texHeight;
    }

    public synchronized void start() {
        if (running.get()) return;
        if (robot == null) {
            System.err.println("[VirtualDesktop] Cannot start capture: Robot is null");
            return;
        }

        running.set(true);
        captureThread = new Thread(this::captureLoop, "Lexora-VirtualDesktop-Capture");
        captureThread.setDaemon(true);
        captureThread.setPriority(Thread.NORM_PRIORITY - 1);
        captureThread.start();
    }

    public synchronized void stop() {
        if (!running.get()) return;
        running.set(false);
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        pendingFrame.set(null);
    }

    private void captureLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                long startTime = System.currentTimeMillis();

                // Determine target capture dimensions
                String quality = ClientData.modeSettings.getOrDefault("VD Quality", "720p HD");
                int targetW = screenRect.width;
                int targetH = screenRect.height;

                switch (quality) {
                    case "360p Low" -> {
                        targetW = 640;
                        targetH = 360;
                    }
                    case "540p Fast" -> {
                        targetW = 960;
                        targetH = 540;
                    }
                    case "720p HD" -> {
                        targetW = 1280;
                        targetH = 720;
                    }
                    case "1080p Full" -> {
                        // Full native screen size
                    }
                }

                // Capture raw screen
                BufferedImage rawCapture = robot.createScreenCapture(screenRect);

                BufferedImage finalImage;
                if (targetW != screenRect.width || targetH != screenRect.height) {
                    // Fast downscaled image
                    finalImage = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = finalImage.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.drawImage(rawCapture, 0, 0, targetW, targetH, null);
                    g2d.dispose();
                } else {
                    if (rawCapture.getType() == BufferedImage.TYPE_INT_RGB || rawCapture.getType() == BufferedImage.TYPE_INT_ARGB) {
                        finalImage = rawCapture;
                    } else {
                        finalImage = new BufferedImage(rawCapture.getWidth(), rawCapture.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2d = finalImage.createGraphics();
                        g2d.drawImage(rawCapture, 0, 0, null);
                        g2d.dispose();
                    }
                }

                pendingFrame.set(finalImage);

                // FPS counter
                frameCounter++;
                long now = System.currentTimeMillis();
                if (now - lastFpsCalcTime >= 1000L) {
                    capturedFps = frameCounter;
                    frameCounter = 0;
                    lastFpsCalcTime = now;
                }

                // Sleep according to desired target FPS
                float targetFps = ClientData.numSettings.getOrDefault("VD FPS", 30.0f);
                targetFps = Math.max(5.0f, Math.min(60.0f, targetFps));
                long targetDelay = (long) (1000.0f / targetFps);

                long duration = System.currentTimeMillis() - startTime;
                long sleepMs = Math.max(2L, targetDelay - duration);
                Thread.sleep(sleepMs);

            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }

    /**
     * Uploads the latest pending frame to OpenGL texture.
     * Must be called on Minecraft render thread.
     */
    public void updateTexture() {
        BufferedImage frame = pendingFrame.getAndSet(null);
        if (frame == null) return;

        int w = frame.getWidth();
        int h = frame.getHeight();

        if (textureId == -1) {
            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }

        DataBufferInt dataBuffer = (DataBufferInt) frame.getRaster().getDataBuffer();
        int[] pixels = dataBuffer.getData();

        if (rawByteBuffer == null || rawByteBuffer.capacity() < pixels.length * 4) {
            rawByteBuffer = ByteBuffer.allocateDirect(pixels.length * 4)
                    .order(ByteOrder.nativeOrder());
        }

        rawByteBuffer.clear();
        rawByteBuffer.asIntBuffer().put(pixels);
        rawByteBuffer.position(0);
        rawByteBuffer.limit(pixels.length * 4);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        if (texWidth != w || texHeight != h) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, rawByteBuffer);
            texWidth = w;
            texHeight = h;
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, rawByteBuffer);
        }
    }

    public void destroyTexture() {
        if (textureId != -1) {
            GL11.glDeleteTextures(textureId);
            textureId = -1;
            texWidth = 0;
            texHeight = 0;
        }
        rawByteBuffer = null;
    }
}
