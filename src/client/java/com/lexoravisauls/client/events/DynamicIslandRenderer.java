package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.DynamicIslandManager;
import com.lexoravisauls.client.utils.TabAnimState;
import com.lexoravisauls.client.mixin.BossBarHudAccessor;
import com.lexoravisauls.client.utils.MusicManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class DynamicIslandRenderer {

    private static final Identifier CORNERS_TEX = Identifier.of("lexoravisauls", "textures/gui/smooth_corners.png");
    private static final Identifier WORLD_TEX = Identifier.of("lexoravisauls", "textures/gui/world.png");
    private static final Identifier OTHER_TEX = Identifier.of("lexoravisauls", "textures/gui/clock.png");
    private static final Identifier CUSTOM_FONT = Identifier.of("lexoravisauls", "sfui");

    private static final Identifier TEX_NO_IMAGE = Identifier.of("lexoravisauls", "textures/gui/no_image.png");
    private static final Identifier TEX_PLAY = Identifier.of("lexoravisauls", "textures/gui/play.png");
    private static final Identifier TEX_PAUSE = Identifier.of("lexoravisauls", "textures/gui/pause.png");
    private static final Identifier TEX_PREV = Identifier.of("lexoravisauls", "textures/gui/previous.png");
    private static final Identifier TEX_NEXT = Identifier.of("lexoravisauls", "textures/gui/next.png");
    private static final Identifier TEX_BARS = Identifier.of("lexoravisauls", "textures/gui/text.png");
    private static final Identifier TEX_REPEAT = Identifier.of("lexoravisauls", "textures/gui/repeat.png");
    private static final Identifier TEX_VOLUME = Identifier.of("lexoravisauls", "textures/gui/volume.png");

    private static final int HEIGHT = 20;

    private static float animTotalW = 0, animHeight = 20, animY = 5;
    private static float animR = -1, animG = -1, animB = -1;

    private static String currentMidText = "Lexora", lastMidText = "Lexora";
    private static float textTransitionProgress = 1.0f;

    public static int islandX, islandY, islandW, islandH;
    public static int[] progressBarBounds = new int[4];
    public static int[] volumeBarBounds = new int[4];
    public static boolean isDraggingProgress = false;
    public static boolean isDraggingVolume = false;

    public static class MusicState {
        public static Identifier currentCover;
        public static boolean isPlayingMusic() { return LexoraGui.moduleStates.getOrDefault("Music Player", false); }
        public static boolean hasMusic = false;
        public static boolean isPaused = true;
        public static String trackName = "Музыка не найдена";
        public static float progress = 0.0f;
        public static String timeString = "0:00 / 0:00";
    }

    public static void render(DrawContext context, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Watermark", true)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        float fadeFactor = 1.0f;
        if (LexoraGui.moduleStates.getOrDefault("Animations", true) && LexoraGui.moduleStates.getOrDefault("Anim Tab", true)) {
            fadeFactor = MathHelper.clamp(Math.abs(TabAnimState.animY) / 50.0f, 0.0f, 1.0f);
        } else {
            if (mc.options.playerListKey.isPressed()) fadeFactor = 0.0f;
        }

        if (fadeFactor < 0.02f) return;

        TextRenderer tr = mc.textRenderer;
        int screenWidth = mc.getWindow().getScaledWidth();
        float scale = LexoraGui.numSettings.getOrDefault("Watermark Scale", 1.0f);

        int mouseX = 0, mouseY = 0;
        boolean isHovered = false;
        if (mc.currentScreen != null) {
            mouseX = (int) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth());
            mouseY = (int) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight());
            if (mouseX >= islandX && mouseX <= islandX + islandW && mouseY >= islandY && mouseY <= islandY + islandH) {
                isHovered = true;
            }
        }

        String timeStr = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        int ping = mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency() : 0;
        String pingStr = ping + " ms";

        Text timeText = Text.literal(timeStr).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));
        Text pingText = Text.literal(pingStr).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));

        String targetTextStr = DynamicIslandManager.getTargetText();
        if (!targetTextStr.equals(currentMidText)) {
            lastMidText = currentMidText;
            currentMidText = targetTextStr;
            textTransitionProgress = 0.0f;
        }
        textTransitionProgress += (1.0f - textTransitionProgress) * 0.35f;

        int activeBossBars = ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBars().size();
        float targetY = 5 + (activeBossBars > 0 ? activeBossBars * 19 : 0);

        int dotSize = 7, gap = 4, padding = 12;
        int targetTotalW, targetH;
        int targetMidW = dotSize + gap + tr.getWidth(Text.literal(currentMidText).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)));

        int maxTextW = 150;
        String textToDraw = MusicState.hasMusic ? MusicState.trackName : "Музыка не найдена";
        int actualTextW = tr.getWidth(Text.literal(textToDraw).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)));
        int displayW = Math.min(actualTextW, maxTextW);

        if (MusicState.isPlayingMusic()) {
            targetTotalW = padding + 14 + 6 + 8 + 4 + displayW + padding;
            if (isHovered) {
                targetTotalW = Math.max(targetTotalW, 200);
                targetH = 70;
            } else {
                targetH = 20;
            }
        } else {
            targetTotalW = padding + targetMidW + padding;
            targetH = 20;
        }

        if (animTotalW == 0) {
            animTotalW = targetTotalW;
            animY = targetY;
            animHeight = targetH;
            int c = DynamicIslandManager.getState().color;
            animR = ((c >> 16) & 0xFF) / 255f;
            animG = ((c >> 8) & 0xFF) / 255f;
            animB = (c & 0xFF) / 255f;
        }

        float smoothSpeed = 0.25f;
        animTotalW += (targetTotalW - animTotalW) * smoothSpeed;
        animHeight += (targetH - animHeight) * smoothSpeed;
        animY += (targetY - animY) * 0.15f;

        int targetColor = DynamicIslandManager.getState().color;
        animR += (((targetColor >> 16) & 0xFF) / 255f - animR) * smoothSpeed;
        animG += (((targetColor >> 8) & 0xFF) / 255f - animG) * smoothSpeed;
        animB += ((targetColor & 0xFF) / 255f - animB) * smoothSpeed;
        int currentColor = (0xFF << 24) | ((int)(animR * 255) << 16) | ((int)(animG * 255) << 8) | (int)(animB * 255);

        islandW = (int) animTotalW;
        islandH = (int) animHeight;
        islandX = (screenWidth - islandW) / 2;
        islandY = (int) animY;

        long time = System.currentTimeMillis();

        float wave = (float) (Math.sin(time / 400.0) * 0.5 + 0.5);
        int colorVal = (int) (150 + 80 * wave);
        int shimmerRGB = (colorVal << 16) | (colorVal << 8) | colorVal;

        int textBaseAlpha = (int)(255 * fadeFactor);

        float timePingCycle = (time % 5000L) / 5000.0f;
        float timePingFactor = 1.0f;

        if (timePingCycle < 0.4f) {
            timePingFactor = timePingCycle / 0.4f;
        } else if (timePingCycle > 0.9f) {
            timePingFactor = 1.0f - ((timePingCycle - 0.9f) / 0.1f);
        } else {
            timePingFactor = 1.0f;
        }

        int tpColorVal = (int) (40 + 215 * timePingFactor);
        int timePingRGB = (tpColorVal << 16) | (tpColorVal << 8) | tpColorVal;
        int timePingColorWithAlpha = (textBaseAlpha << 24) | timePingRGB;

        RenderSystem.enableDepthTest();

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(screenWidth / 2f, islandY, -150);
        ms.scale(scale, scale, 1.0f);
        ms.translate(-screenWidth / 2f, -islandY, 0);

        context.draw();

        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Watermark Solid", false);
        int baseBgAlpha = isSolid ? 255 : 230;
        int bgAlpha = (int)(baseBgAlpha * fadeFactor);
        int bgColor = (bgAlpha << 24) | 0x1A1A20;

        drawSmoothRect(context, islandX, islandY, islandW, islandH, bgColor);

        ms.push();
        ms.translate(0, 0, 10);

        float outScale = 0.8f;
        int smallIconSize = 8;
        int outGap = 3;

        int timeTextW = (int) (tr.getWidth(timeText) * outScale);
        int timeFullW = smallIconSize + outGap + timeTextW;
        int timeStartX = islandX - 5 - timeFullW;

        int pingTextW = (int) (tr.getWidth(pingText) * outScale);
        int pingStartX = islandX + islandW + 5;

        int outY = islandY + (HEIGHT - smallIconSize) / 2;

        drawTexQuad(context, OTHER_TEX, timeStartX, outY, smallIconSize, smallIconSize, 0, 0, 1, 1, timePingColorWithAlpha);
        ms.push();
        ms.translate(timeStartX + smallIconSize + outGap, islandY + (HEIGHT - 8 * outScale) / 2f, 0);
        ms.scale(outScale, outScale, 1.0f);
        context.drawText(tr, timeText, 0, 0, timePingColorWithAlpha, false);
        ms.pop();

        drawTexQuad(context, WORLD_TEX, pingStartX, outY, smallIconSize, smallIconSize, 0, 0, 1, 1, timePingColorWithAlpha);
        ms.push();
        ms.translate(pingStartX + smallIconSize + outGap, islandY + (HEIGHT - 8 * outScale) / 2f, 0);
        ms.scale(outScale, outScale, 1.0f);
        context.drawText(tr, pingText, 0, 0, timePingColorWithAlpha, false);
        ms.pop();

        if (MusicState.isPlayingMusic()) {
            int coverSize = 14;
            Identifier coverToDraw = MusicState.currentCover != null ? MusicState.currentCover : TEX_NO_IMAGE;
            drawTexQuad(context, coverToDraw, islandX + padding, islandY + 3, coverSize, coverSize, 0, 0, 1, 1, (textBaseAlpha << 24) | 0xFFFFFF);
            drawTexQuad(context, TEX_BARS, islandX + padding + coverSize + 6, islandY + 6, 8, 8, 0, 0, 1, 1, (textBaseAlpha << 24) | (currentColor & 0xFFFFFF));

            int textX = islandX + padding + coverSize + 6 + 8 + 4;
            int inTextY = islandY + 6;
            double scroll = 0;

            if (actualTextW > maxTextW) {
                double overflow = actualTextW - maxTextW;
                double cycle = 4000 + overflow * 30;
                double t = (System.currentTimeMillis() % (long)cycle) / cycle;

                if (t < 0.15) scroll = 0;
                else if (t < 0.45) scroll = overflow * ((t - 0.15) / 0.30);
                else if (t < 0.60) scroll = overflow;
                else if (t < 0.90) scroll = overflow * (1.0 - ((t - 0.60) / 0.30));
                else scroll = 0;
            }

            int scX = (int) ((textX - screenWidth / 2f) * scale + screenWidth / 2f);
            int scY = (int) ((inTextY - 4 - islandY) * scale + islandY);
            int scW = (int) ((maxTextW + 5) * scale);
            int scH = (int) (20 * scale);

            if (actualTextW > maxTextW) context.enableScissor(scX, scY, scX + scW, scY + scH);

            int trackTextColor = (textBaseAlpha << 24) | shimmerRGB;
            context.drawText(tr, Text.literal(textToDraw).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)), textX - (int)scroll, inTextY, trackTextColor, false);

            if (actualTextW > maxTextW) context.disableScissor();

            float expandProgress = (animHeight - 20) / 50.0f;
            expandProgress = MathHelper.clamp(expandProgress, 0.0f, 1.0f);
            float uiAlphaFactor = MathHelper.clamp((expandProgress - 0.7f) / 0.3f, 0.0f, 1.0f);

            if (uiAlphaFactor > 0.01f) {
                int uiAlpha = (int)(uiAlphaFactor * 255 * fadeFactor);
                int uiColor = (uiAlpha << 24) | 0xFFFFFF;

                int btnSize = 12;
                int centerBtnX = islandX + islandW / 2;
                int btnY = islandY + 22;

                drawTexQuad(context, TEX_PREV, centerBtnX - 25 - btnSize, btnY, btnSize, btnSize, 0,0,1,1, uiColor);
                drawTexQuad(context, MusicState.isPaused ? TEX_PLAY : TEX_PAUSE, centerBtnX - btnSize/2f, btnY, btnSize, btnSize, 0,0,1,1, uiColor);
                drawTexQuad(context, TEX_NEXT, centerBtnX + 25, btnY, btnSize, btnSize, 0,0,1,1, uiColor);
                drawTexQuad(context, TEX_REPEAT, islandX + islandW - padding - 15, btnY + 1, 10, 10, 0,0,1,1, (uiAlpha << 24) | 0xAAAAAA);

                int barX = islandX + padding;
                int barY = islandY + 42;
                int barW = islandW - (padding * 2);
                int barH = 4;

                progressBarBounds[0] = barX;
                progressBarBounds[1] = barY - 3;
                progressBarBounds[2] = barW;
                progressBarBounds[3] = 10;

                if (isDraggingProgress && MusicState.hasMusic) {
                    float newProg = (float)(mouseX - barX) / barW;
                    MusicState.progress = MathHelper.clamp(newProg, 0.0f, 1.0f);
                }

                drawSmoothRect(context, barX, barY, barW, barH, (uiAlpha << 24) | 0x33333A);
                drawSmoothRect(context, barX, barY, (int)(barW * MusicState.progress), barH, (uiAlpha << 24) | (currentColor & 0xFFFFFF));
                int dotX = barX + (int)(barW * MusicState.progress);
                drawSmoothRect(context, dotX - 3, barY - 1, 6, 6, uiColor);

                int volY = islandY + 54;
                drawTexQuad(context, TEX_VOLUME, barX, volY - 3, 10, 10, 0,0,1,1, (uiAlpha << 24) | 0xAAAAAA);

                int volBarX = barX + 15;
                int volBarW = barW - 15 - 28;
                int volBarH = 4;

                volumeBarBounds[0] = volBarX;
                volumeBarBounds[1] = volY - 3;
                volumeBarBounds[2] = volBarW;
                volumeBarBounds[3] = 10;

                if (isDraggingVolume) {
                    float newVol = (float)(mouseX - volBarX) / volBarW;
                    MusicManager.setVolume(MathHelper.clamp(newVol, 0.0f, 1.0f));
                }

                drawSmoothRect(context, volBarX, volY, volBarW, volBarH, (uiAlpha << 24) | 0x33333A);
                drawSmoothRect(context, volBarX, volY, (int)(volBarW * MusicManager.currentVolume), volBarH, (uiAlpha << 24) | 0xAAAAAA);
                int volDotX = volBarX + (int)(volBarW * MusicManager.currentVolume);
                drawSmoothRect(context, volDotX - 2, volY - 1, 4, 6, uiColor);

                String volText = (int)(MusicManager.currentVolume * 100) + "%";
                context.getMatrices().push();
                context.getMatrices().translate(volBarX + volBarW + 6, volY - 3, 0);
                context.getMatrices().scale(0.8f, 0.8f, 1.0f);
                context.drawText(tr, Text.literal(volText).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)), 0, 0, (uiAlpha << 24) | 0xAAAAAA, false);
                context.getMatrices().pop();

                Text tText = Text.literal(MusicState.timeString).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));
                context.getMatrices().push();
                context.getMatrices().translate(islandX + padding, islandY + 24, 0);
                context.getMatrices().scale(0.8f, 0.8f, 1.0f);
                context.drawText(tr, tText, 0, 0, (uiAlpha << 24) | 0xAAAAAA, false);
                context.getMatrices().pop();
            }

        } else {
            int midContentX = islandX + padding;
            int dotY = islandY + (20 - dotSize) / 2;
            int textY = islandY + (20 - 8) / 2;

            float widthDiff = Math.abs(targetTotalW - animTotalW);
            int textAlphaFactor = (int) MathHelper.clamp((255 - (widthDiff * 5)) * fadeFactor, 0, 255);

            int midTextColor = (textAlphaFactor << 24) | shimmerRGB;

            if (textAlphaFactor > 10) {
                drawTexQuad(context, CORNERS_TEX, midContentX, dotY, dotSize, dotSize, 0, 0, 1, 1, (textAlphaFactor << 24) | (currentColor & 0xFFFFFF));
                midContentX += dotSize + gap;

                if (textTransitionProgress < 0.99f) {
                    ms.push();
                    float moveUp = (1.0f - textTransitionProgress) * 2.0f;
                    ms.translate(0, -moveUp, 0);
                    int alphaOld = (int) MathHelper.clamp((1.0f - textTransitionProgress) * textAlphaFactor, 0, 255);
                    if (alphaOld > 5) {
                        context.drawText(tr, Text.literal(lastMidText).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)), midContentX, textY, (alphaOld << 24) | shimmerRGB, false);
                    }
                    ms.pop();

                    int alphaNew = (int) MathHelper.clamp(textTransitionProgress * textAlphaFactor, 0, 255);
                    if (alphaNew > 5) {
                        context.drawText(tr, Text.literal(currentMidText).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)), midContentX, textY, (alphaNew << 24) | shimmerRGB, false);
                    }
                } else {
                    context.drawText(tr, Text.literal(currentMidText).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)), midContentX, textY, midTextColor, false);
                }
            }
        }

        context.draw();
        ms.pop();
        ms.pop();
    }

    private static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;

        float radius = Math.min(6.0f, Math.min(width / 2.0f, height / 2.0f));
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }

    private static void drawTexQuad(DrawContext context, Identifier texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int color) {
        if (((color >> 24) & 0xFF) <= 5) return;
        context.draw();

        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(u0, v0).color(r, g, b, a);
        buffer.vertex(matrix, x, y + h, 0.0F).texture(u0, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y + h, 0.0F).texture(u1, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y, 0.0F).texture(u1, v0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}