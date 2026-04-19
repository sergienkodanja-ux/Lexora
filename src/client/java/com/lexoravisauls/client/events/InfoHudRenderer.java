package com.lexoravisauls.client.events;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lexoravisauls.client.gui.LexoraGui;

public class InfoHudRenderer {

    public static float WIDTH = 200f;
    public static float HEIGHT = 50f;

    private static final Identifier CUSTOM_FONT = Identifier.of("lexoravisauls", "sfui");
    private static final Identifier LOGO_TEX = Identifier.of("lexoravisauls", "textures/gui/logo.png");
    private static final Identifier ICON_PLAYER = Identifier.of("lexoravisauls", "textures/gui/playernazarlox.png");
    private static final Identifier ICON_FPS = Identifier.of("lexoravisauls", "textures/gui/airplane.png");
    private static final Identifier ICON_SERVER = Identifier.of("lexoravisauls", "textures/gui/trap.png");

    private static final Map<String, Float> animWidths = new HashMap<>();
    private static final Map<String, Float> animPosX = new HashMap<>();
    private static float introAnim = 0f;

    private static class InfoElement {
        Identifier icon;
        String text;

        InfoElement(Identifier icon, String text) {
            this.icon = icon;
            this.text = text;
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Info HUD", true)) {
            introAnim = 0f;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;

        float baseScale = LexoraGui.numSettings.getOrDefault("Info HUD Scale", 0.65f);

        introAnim += (1.0f - introAnim) * 0.1f;
        float finalScale = baseScale * introAnim;

        context.getMatrices().push();
        context.getMatrices().translate(5, 5, 0);
        context.getMatrices().scale(finalScale, finalScale, 1.0f);
        context.getMatrices().translate(-5, -5, 0);

        int startX = 5;
        int currentY = 5;
        int gapX = 4;
        int gapY = 4;
        float targetX = startX;

        float logoW = renderAnimatedIsland(context, tr, "logo", targetX, currentY, "", LOGO_TEX);
        targetX += logoW + gapX;

        List<InfoElement> line1 = new ArrayList<>();
        boolean streamerMode = LexoraGui.moduleStates.getOrDefault("Streamer Mode", false);
        boolean hideName = streamerMode && LexoraGui.moduleStates.getOrDefault("Hide Name", true);
        boolean hideCoords = streamerMode && LexoraGui.moduleStates.getOrDefault("Hide Coords", true);

        String playerName = hideName ? "Protected" : mc.getSession().getUsername();
        line1.add(new InfoElement(ICON_PLAYER, playerName));
        line1.add(new InfoElement(ICON_FPS, mc.getCurrentFps() + " FPS"));

        String coordsText;
        if (hideCoords) {
            coordsText = "X: ??? Y: ??? Z: ???";
        } else {
            int posX = (int) mc.player.getX();
            int posY = (int) mc.player.getY();
            int posZ = (int) mc.player.getZ();
            coordsText = String.format("X: %d Y: %d Z: %d", posX, posY, posZ);
        }
        line1.add(new InfoElement(null, coordsText));

        float line1W = renderCombinedIsland(context, tr, "line1", targetX, currentY, line1);

        currentY += 22 + gapY;
        float targetX2 = startX;

        List<InfoElement> line2 = new ArrayList<>();
        double dx = mc.player.getX() - mc.player.prevX;
        double dz = mc.player.getZ() - mc.player.prevZ;
        double speed = Math.sqrt(dx * dx + dz * dz) * 20.0D;
        line2.add(new InfoElement(null, String.format("Speed: %.1f b/s", speed).replace(",", ".")));

        String serverText = "Одиночная игра";
        if (!mc.isInSingleplayer()) {
            ServerInfo serverInfo = mc.getCurrentServerEntry();
            if (serverInfo != null) serverText = serverInfo.address;
            else serverText = "Сетевая игра";
        }
        line2.add(new InfoElement(ICON_SERVER, serverText));

        float line2W = renderCombinedIsland(context, tr, "line2", targetX2, currentY, line2);

        WIDTH = Math.max(targetX + line1W, targetX2 + line2W) - startX;
        HEIGHT = (currentY + 22) - 5;

        context.getMatrices().pop();
    }

    private static float renderCombinedIsland(DrawContext context, TextRenderer tr, String id, float targetX, int y, List<InfoElement> elements) {
        int padding = 6;
        int iconSize = 10;
        int gap = 4;
        String separator = " I ";
        int sepW = tr.getWidth(Text.literal(separator).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)));

        int targetW = padding;
        for (int i = 0; i < elements.size(); i++) {
            InfoElement el = elements.get(i);
            int textW = tr.getWidth(Text.literal(el.text).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)));
            targetW += (el.icon != null ? iconSize + gap : 0) + textW;
            if (i < elements.size() - 1) {
                targetW += sepW;
            }
        }
        targetW += padding;

        int h = 22;

        float currentX = animPosX.getOrDefault(id, targetX);
        currentX += (targetX - currentX) * 0.15f;
        animPosX.put(id, currentX);

        float currentW = animWidths.getOrDefault(id, (float) targetW);
        currentW += (targetW - currentW) * 0.15f;
        animWidths.put(id, currentW);

        int drawX = (int) currentX;
        int drawW = (int) currentW;

        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Info HUD Solid", false);
        int bgAlpha = isSolid ? 255 : 180;

        drawSmoothRect(context, drawX, y, drawW, h, (bgAlpha << 24) | 0x101015);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 10);

        int contentX = drawX + padding;
        long time = System.currentTimeMillis();

        for (int i = 0; i < elements.size(); i++) {
            InfoElement el = elements.get(i);

            float wave = (float) (Math.sin((time / 400.0) + (i * 0.6)) * 0.5 + 0.5);
            int colorVal = (int) (150 + 80 * wave);
            int elementColor = 0xFF000000 | (colorVal << 16) | (colorVal << 8) | colorVal;

            if (el.icon != null) {
                drawTexQuad(context, el.icon, contentX, y + (h - iconSize) / 2f, iconSize, iconSize, 0, 0, 1, 1, elementColor);
                contentX += iconSize + gap;
            }

            Text formattedText = Text.literal(el.text).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));
            context.drawText(tr, formattedText, contentX, y + (h - 8) / 2, elementColor, false);
            contentX += tr.getWidth(formattedText);

            if (i < elements.size() - 1) {
                Text sepText = Text.literal(separator).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));
                context.drawText(tr, sepText, contentX, y + (h - 8) / 2, 0xFF666666, false);
                contentX += sepW;
            }
        }

        context.getMatrices().pop();
        return currentW;
    }

    private static float renderAnimatedIsland(DrawContext context, TextRenderer tr, String id, float targetX, int y, String text, Identifier icon) {
        int padding = 6;
        int iconSize = 10;
        int gap = 4;

        boolean hasText = text != null && !text.isEmpty();
        Text formattedText = hasText ? Text.literal(text).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)) : Text.empty();

        int textW = hasText ? tr.getWidth(formattedText) : 0;
        int currentGap = (icon != null && hasText) ? gap : 0;

        int targetW = padding + (icon != null ? iconSize : 0) + currentGap + textW + padding;
        int h = 22;

        float currentX = animPosX.getOrDefault(id, targetX);
        currentX += (targetX - currentX) * 0.15f;
        animPosX.put(id, currentX);

        float currentW = animWidths.getOrDefault(id, (float) targetW);
        currentW += (targetW - currentW) * 0.15f;
        animWidths.put(id, currentW);

        int drawX = (int) currentX;
        int drawW = (int) currentW;

        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Info HUD Solid", false);
        int bgAlpha = isSolid ? 255 : 180;

        drawSmoothRect(context, drawX, y, drawW, h, (bgAlpha << 24) | 0x101015);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 10);

        int contentX = drawX + padding;

        long time = System.currentTimeMillis();
        float wave = (float) (Math.sin((time / 400.0)) * 0.5 + 0.5);
        int colorVal = (int) (150 + 80 * wave);
        int elementColor = 0xFF000000 | (colorVal << 16) | (colorVal << 8) | colorVal;

        if (icon != null) {
            drawTexQuad(context, icon, contentX, y + (h - iconSize) / 2f, iconSize, iconSize, 0, 0, 1, 1, elementColor);
            contentX += iconSize + currentGap;
        }

        if (hasText) {
            context.drawText(tr, formattedText, contentX, y + (h - 8) / 2, elementColor, false);
        }

        context.getMatrices().pop();
        return currentW;
    }

    private static void drawSmoothRect(DrawContext context, float x, float y, float width, float height, int color) {
        float radius = Math.min(height / 2.0f, 8.0f);
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }

    private static void drawTexQuad(DrawContext context, Identifier texture, float x, float y, float w, float h,
                                    float u0, float v0, float u1, float v1, int color) {
        if (((color >> 24) & 0xFF) <= 5) return;

        context.draw();

        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(u0, v0).color(r, g, b, a);
        buffer.vertex(matrix, x, y + h, 0.0F).texture(u0, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y + h, 0.0F).texture(u1, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y, 0.0F).texture(u1, v0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}