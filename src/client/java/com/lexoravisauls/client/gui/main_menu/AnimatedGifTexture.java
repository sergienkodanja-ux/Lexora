package com.lexoravisauls.client.gui.main_menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public final class AnimatedGifTexture {
    private final Identifier gifId;
    private final List<Identifier> frames = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>();

    private boolean loaded = false;
    private long startTime = 0L;

    public AnimatedGifTexture(Identifier gifId) {
        this.gifId = gifId;
    }

    public Identifier getFrame() {
        if (!loaded) {
            load();
        }

        if (frames.isEmpty()) {
            return null;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        int total = 0;

        for (int delay : delays) {
            total += delay;
        }

        if (total <= 0) {
            return frames.get(0);
        }

        long time = elapsed % total;
        int cursor = 0;

        for (int i = 0; i < frames.size(); i++) {
            cursor += delays.get(i);

            if (time <= cursor) {
                return frames.get(i);
            }
        }

        return frames.get(0);
    }

    public void reset() {
        startTime = System.currentTimeMillis();
    }

    private void load() {
        loaded = true;
        startTime = System.currentTimeMillis();

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Optional<Resource> resourceOptional = mc.getResourceManager().getResource(gifId);

            if (resourceOptional.isEmpty()) {
                return;
            }

            try (InputStream inputStream = resourceOptional.get().getInputStream();
                 ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {

                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");

                if (!readers.hasNext()) {
                    return;
                }

                ImageReader reader = readers.next();
                reader.setInput(imageInputStream, false);

                int count = reader.getNumImages(true);

                for (int i = 0; i < count; i++) {
                    BufferedImage image = reader.read(i);
                    int delay = readDelay(reader.getImageMetadata(i));

                    Identifier frameId = uploadFrame(mc, image, i);

                    frames.add(frameId);
                    delays.add(Math.max(delay, 35));
                }

                reader.dispose();
            }
        } catch (Exception e) {
            System.err.println("Lexora gif load error: " + e.getMessage());
        }
    }

    private Identifier uploadFrame(MinecraftClient mc, BufferedImage image, int index) {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                nativeImage.setColorArgb(x, y, argb);
            }
        }

        NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);

        Identifier id = Identifier.of(
                "lexoravisauls",
                "dynamic/hello_gif_" + index
        );

        mc.getTextureManager().registerTexture(id, texture);

        return id;
    }

    private int readDelay(IIOMetadata metadata) {
        try {
            String format = metadata.getNativeMetadataFormatName();
            org.w3c.dom.Node root = metadata.getAsTree(format);
            org.w3c.dom.Node node = root.getFirstChild();

            while (node != null) {
                if ("GraphicControlExtension".equals(node.getNodeName())) {
                    org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
                    org.w3c.dom.Node delayNode = attrs.getNamedItem("delayTime");

                    if (delayNode != null) {
                        return Integer.parseInt(delayNode.getNodeValue()) * 10;
                    }
                }

                node = node.getNextSibling();
            }
        } catch (Exception ignored) {
        }

        return 70;
    }
}