package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.awt.Color;

public class BlockOverlayRenderer {

    private static double animX, animY, animZ;
    private static double animW, animH, animD;
    private static long lastTime = System.currentTimeMillis();
    private static BlockPos lastPos = null;

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Block Overlay", false)) {
            lastPos = null;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            lastPos = null;
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos, ShapeContext.of(mc.player));
        if (shape.isEmpty()) return;

        Box box = shape.getBoundingBox();

        double targetX = pos.getX() + box.minX;
        double targetY = pos.getY() + box.minY;
        double targetZ = pos.getZ() + box.minZ;
        double targetW = box.maxX - box.minX;
        double targetH = box.maxY - box.minY;
        double targetD = box.maxZ - box.minZ;

        long now = System.currentTimeMillis();
        double dt = Math.min((now - lastTime) / 1000.0, 0.05);
        lastTime = now;

        if (lastPos == null || pos.getSquaredDistance(lastPos) > 10) {
            animX = targetX; animY = targetY; animZ = targetZ;
            animW = targetW; animH = targetH; animD = targetD;
        } else {
            double factor = Math.max(0.0, Math.min(1.0, 25.0 * dt));
            animX += (targetX - animX) * factor;
            animY += (targetY - animY) * factor;
            animZ += (targetZ - animZ) * factor;
            animW += (targetW - animW) * factor;
            animH += (targetH - animH) * factor;
            animD += (targetD - animD) * factor;
        }
        lastPos = pos;

        Vec3d camPos = camera.getPos();
        float expand = 0.002f;
        float minX = (float) (animX - camPos.x - expand);
        float minY = (float) (animY - camPos.y - expand);
        float minZ = (float) (animZ - camPos.z - expand);
        float maxX = (float) (animX + animW - camPos.x + expand);
        float maxY = (float) (animY + animH - camPos.y + expand);
        float maxZ = (float) (animZ + animD - camPos.z + expand);

        String mode = LexoraGui.modeSettings.getOrDefault("Overlay Mode", "Solid");
        boolean customColor = "Custom".equals(LexoraGui.modeSettings.getOrDefault("Overlay Color Source", "Client"));
        float opacity = LexoraGui.numSettings.getOrDefault("Overlay Opacity", 0.4f);
        opacity = Math.max(0.05f, Math.min(1.0f, opacity));

        float[] hsv = customColor
                ? LexoraGui.colorSettings.getOrDefault("Overlay Custom Color", new float[]{300f / 360f, 0.75f, 1f})
                : LexoraGui.colorSettings.getOrDefault("Theme Color 1", new float[]{300f / 360f, 0.75f, 1f});

        int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        try {
            if (!mode.equalsIgnoreCase("Outline")) {
                drawSolidFaces(mat, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, opacity * 0.35f);
            }
            drawBoxLines(mat, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, Math.min(1.0f, opacity + 0.3f));
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }

    private static void drawSolidFaces(Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Front
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a);

        // Back
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a);

        // Left
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a);

        // Right
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a);

        // Top
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a);

        // Bottom
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawBoxLines(Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Bottom
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a); buffer.vertex(mat, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a); buffer.vertex(mat, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a); buffer.vertex(mat, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a); buffer.vertex(mat, x1, y1, z1).color(r, g, b, a);

        // Top
        buffer.vertex(mat, x1, y2, z1).color(r, g, b, a); buffer.vertex(mat, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z1).color(r, g, b, a); buffer.vertex(mat, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a); buffer.vertex(mat, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(mat, x1, y2, z2).color(r, g, b, a); buffer.vertex(mat, x1, y2, z1).color(r, g, b, a);

        // Pillars
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a); buffer.vertex(mat, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y1, z1).color(r, g, b, a); buffer.vertex(mat, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(mat, x2, y1, z2).color(r, g, b, a); buffer.vertex(mat, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(mat, x1, y1, z2).color(r, g, b, a); buffer.vertex(mat, x1, y2, z2).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
