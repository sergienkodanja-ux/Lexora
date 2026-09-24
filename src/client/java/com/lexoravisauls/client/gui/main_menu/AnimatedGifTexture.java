package com.lexoravisauls.client.gui.main_menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public final class AnimatedGifTexture {
    private static final int MAX_GIF_FRAMES = 240;
    private static final int MAX_GIF_PIXELS = 12_000_000;

    private final Identifier gifId;
    private final List<Identifier> frames = new ArrayList<>();
    private final List<Integer>    delays = new ArrayList<>();

    private volatile boolean decodeStarted = false;
    private volatile boolean decodeDone    = false;
    private volatile boolean decodeFailed  = false;

    private volatile List<DecodedFrame> decodedFrames = List.of();
    private volatile int  decodedWidth      = 1;
    private volatile int  decodedHeight     = 1;
    private volatile long decodedDurationMs = 1000L;

    private int     uploadedIndex = 0;
    private boolean uploadDone    = false;

    private long startTime       = 0L;
    private int  width           = 1;
    private int  height          = 1;
    private long totalDurationMs = 1000L;

    public AnimatedGifTexture(Identifier id) {
        this.gifId = id;
    }

    public void reset() {
        startTime = System.currentTimeMillis();
    }

    public void startPreload() {
        if (decodeStarted || decodeDone || decodeFailed) return;
        decodeStarted = true;

        new Thread(() -> {
            try {
                byte[] bytes = null;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.getResourceManager() != null) {
                    try {
                        Optional<Resource> res = mc.getResourceManager().getResource(gifId);
                        if (res.isPresent()) {
                            try (InputStream is = res.get().getInputStream()) {
                                bytes = is.readAllBytes();
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (bytes == null) {
                    String path = "/assets/" + gifId.getNamespace() + "/" + gifId.getPath();
                    try (InputStream is = AnimatedGifTexture.class.getResourceAsStream(path)) {
                        if (is != null) {
                            bytes = is.readAllBytes();
                        }
                    } catch (Throwable ignored) {}
                }

                if (bytes == null) {
                    String relPath = "assets/" + gifId.getNamespace() + "/" + gifId.getPath();
                    try (InputStream is = AnimatedGifTexture.class.getClassLoader().getResourceAsStream(relPath)) {
                        if (is != null) {
                            bytes = is.readAllBytes();
                        }
                    } catch (Throwable ignored) {}
                }

                if (bytes == null) {
                    System.err.println("Lexora hello gif not found on classpath: " + gifId);
                    decodeFailed = true;
                    return;
                }

                decodeGif(bytes);
            } catch (Throwable t) {
                decodeFailed = true;
                t.printStackTrace();
            }
        }, "Lexora-GIF-Loader").start();
    }

    public void tickUpload(int max) {
        startPreload();
        if (!decodeDone || uploadDone || decodeFailed) return;
        List<DecodedFrame> lf = decodedFrames;
        if (lf.isEmpty()) {
            uploadDone = true;
            return;
        }
        int limit = Math.min(lf.size(), uploadedIndex + Math.max(16, max));
        MinecraftClient mc = MinecraftClient.getInstance();
        while (uploadedIndex < limit) {
            try {
                DecodedFrame df = lf.get(uploadedIndex);
                frames.add(uploadFrame(mc, df.image, uploadedIndex));
                delays.add(df.delayMs);
                uploadedIndex++;
            } catch (Throwable t) {
                decodeFailed = true;
                uploadDone = false;
                frames.clear();
                delays.clear();
                System.err.println("Lexora GIF upload failed");
                t.printStackTrace();
                return;
            }
        }
        if (uploadedIndex >= lf.size()) {
            width = decodedWidth;
            height = decodedHeight;
            totalDurationMs = Math.max(1000L, decodedDurationMs);
            uploadDone = true;
            reset();
        }
    }

    public boolean isReady() {
        tickUpload(16);
        return !frames.isEmpty();
    }

    public boolean isFailed() {
        return decodeFailed;
    }

    public Identifier getFrame() {
        tickUpload(16);
        if (frames.isEmpty()) return null;
        long elapsed = System.currentTimeMillis() - startTime;
        int total = delays.stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return frames.get(0);
        long time = (total > 0) ? (elapsed % total) : 0L;
        int cursor = 0;
        for (int i = 0; i < frames.size(); i++) {
            cursor += delays.get(i);
            if (time <= cursor) return frames.get(i);
        }
        return frames.get(0);
    }

    public int getWidth() {
        return (width > 1) ? width : Math.max(776, decodedWidth);
    }

    public int getHeight() {
        return (height > 1) ? height : Math.max(246, decodedHeight);
    }

    public long getTotalDurationMs() {
        if (!delays.isEmpty()) {
            int total = delays.stream().mapToInt(Integer::intValue).sum();
            if (total > 500) return total;
        }
        return Math.max(1000L, totalDurationMs);
    }

    private void decodeGif(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                decodeFailed = true;
                return;
            }
            ImageReader reader = readers.next();
            reader.setInput(iis, false);
            int count = reader.getNumImages(true);
            if (count <= 0 || count > MAX_GIF_FRAMES)
                throw new IllegalStateException("Bad frame count: " + count);
            int lw = 1, lh = 1;
            try {
                int[] sz = readLogicalSize(reader.getStreamMetadata());
                lw = sz[0];
                lh = sz[1];
            } catch (Throwable ignored) {}
            if (lw <= 1 || lh <= 1) {
                BufferedImage f = reader.read(0);
                lw = Math.max(1, f.getWidth());
                lh = Math.max(1, f.getHeight());
            }
            if ((long) lw * lh > MAX_GIF_PIXELS)
                throw new IllegalStateException("GIF too large");

            int cropW = (lw > 800) ? Math.min(lw, 776) : lw;
            int cropH = (lh > 300) ? Math.min(lh, 246) : lh;
            int cropX = Math.max(0, (lw - cropW) / 2);
            int cropY = Math.max(0, (lh - cropH) / 2);

            List<DecodedFrame> result = new ArrayList<>();
            BufferedImage canvas = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setComposite(AlphaComposite.SrcOver);
            int duration = 0;
            for (int i = 0; i < count; i++) {
                BufferedImage raw = toArgb(reader.read(i));
                FrameMeta meta = readFrameMeta(reader.getImageMetadata(i), raw);
                BufferedImage prev = "restoreToPrevious".equals(meta.disposalMethod) ? copyImage(canvas) : null;
                g.drawImage(raw, meta.left, meta.top, null);
                int delay = Math.max(meta.delayMs, 35);
                BufferedImage frameImg = (cropW < lw || cropH < lh) ? canvas.getSubimage(cropX, cropY, cropW, cropH) : canvas;
                result.add(new DecodedFrame(copyImage(frameImg), delay));
                duration += delay;
                if ("restoreToBackgroundColor".equals(meta.disposalMethod)) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(meta.left, meta.top, meta.width, meta.height);
                    g.setComposite(AlphaComposite.SrcOver);
                } else if ("restoreToPrevious".equals(meta.disposalMethod) && prev != null) {
                    g.dispose();
                    canvas = prev;
                    g = canvas.createGraphics();
                    g.setComposite(AlphaComposite.SrcOver);
                }
            }
            g.dispose();
            reader.dispose();
            decodedWidth = cropW;
            decodedHeight = cropH;
            decodedDurationMs = Math.max(1000L, duration);
            decodedFrames = List.copyOf(result);
            decodeDone = true;
        } catch (Throwable t) {
            decodeFailed = true;
            t.printStackTrace();
        }
    }

    private int[] readLogicalSize(IIOMetadata meta) {
        if (meta == null) return new int[]{1, 1};
        org.w3c.dom.Node root = meta.getAsTree(meta.getNativeMetadataFormatName());
        org.w3c.dom.Node node = findNode(root, "LogicalScreenDescriptor");
        if (node == null || node.getAttributes() == null) return new int[]{1, 1};
        return new int[]{
                Math.max(1, readIntAttr(node, "logicalScreenWidth", 1)),
                Math.max(1, readIntAttr(node, "logicalScreenHeight", 1))
        };
    }

    private FrameMeta readFrameMeta(IIOMetadata meta, BufferedImage img) {
        FrameMeta fm = new FrameMeta();
        fm.left = 0;
        fm.top = 0;
        fm.width = img.getWidth();
        fm.height = img.getHeight();
        fm.delayMs = 70;
        fm.disposalMethod = "none";
        try {
            org.w3c.dom.Node root = meta.getAsTree(meta.getNativeMetadataFormatName());
            org.w3c.dom.Node id = findNode(root, "ImageDescriptor");
            if (id != null) {
                fm.left   = readIntAttr(id, "imageLeftPosition", 0);
                fm.top    = readIntAttr(id, "imageTopPosition", 0);
                fm.width  = readIntAttr(id, "imageWidth", img.getWidth());
                fm.height = readIntAttr(id, "imageHeight", img.getHeight());
            }
            org.w3c.dom.Node gce = findNode(root, "GraphicControlExtension");
            if (gce != null) {
                fm.delayMs = readIntAttr(gce, "delayTime", 7) * 10;
                org.w3c.dom.Node d = gce.getAttributes().getNamedItem("disposalMethod");
                if (d != null) fm.disposalMethod = d.getNodeValue();
            }
        } catch (Throwable ignored) {}
        fm.width  = Math.max(1, fm.width);
        fm.height = Math.max(1, fm.height);
        return fm;
    }

    private int readIntAttr(org.w3c.dom.Node node, String name, int fallback) {
        try {
            if (node == null || node.getAttributes() == null) return fallback;
            org.w3c.dom.Node a = node.getAttributes().getNamedItem(name);
            return a == null ? fallback : Integer.parseInt(a.getNodeValue());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    private BufferedImage copyImage(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    private Identifier uploadFrame(MinecraftClient mc, BufferedImage img, int idx) {
        if (mc == null) throw new IllegalStateException("MC is null");
        int maxTex = 4096;
        try {
            maxTex = Math.max(1024, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE));
        } catch (Throwable ignored) {}
        if (img.getWidth() > maxTex || img.getHeight() > maxTex)
            throw new IllegalStateException("Frame too large");
        NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), true);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                ni.setColorArgb(x, y, img.getRGB(x, y));
            }
        }
        NativeImageBackedTexture tex = new NativeImageBackedTexture(ni);
        try {
            tex.upload();
        } catch (Throwable ignored) {}
        tex.setFilter(true, false);
        tex.setClamp(true);

        Identifier id = Identifier.of("lexoravisauls", "dynamic/hello_gif_" + idx);
        mc.getTextureManager().registerTexture(id, tex);
        try {
            int glTex = mc.getTextureManager().getTexture(id).getGlId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } catch (Throwable ignored) {}
        return id;
    }

    private org.w3c.dom.Node findNode(org.w3c.dom.Node root, String name) {
        if (root == null) return null;
        if (name.equals(root.getNodeName())) return root;
        org.w3c.dom.Node c = root.getFirstChild();
        while (c != null) {
            org.w3c.dom.Node f = findNode(c, name);
            if (f != null) return f;
            c = c.getNextSibling();
        }
        return null;
    }

    private static final class DecodedFrame {
        final BufferedImage image;
        final int delayMs;

        DecodedFrame(BufferedImage i, int d) {
            image = i;
            delayMs = d;
        }
    }

    private static final class FrameMeta {
        int left, top, width, height, delayMs;
        String disposalMethod;
    }
}