package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HitWave {

    private static final List<Wave> waves = new ArrayList<>();

    public static void addWave(Entity target, boolean isCrit) {
        if (!LexoraGui.moduleStates.getOrDefault("Hit Wave", false)) return;
        if (LexoraGui.moduleStates.getOrDefault("Wave Only Crit", true) && !isCrit) return;

        waves.add(new Wave(target.getPos()));
    }

    public static void render(MatrixStack matrices, Camera camera) {
        if (!LexoraGui.moduleStates.getOrDefault("Hit Wave", false) || waves.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        float maxSize = LexoraGui.numSettings.getOrDefault("Wave Size", 15.0f);
        float speed = LexoraGui.numSettings.getOrDefault("Wave Speed", 20.0f);

        int color = LexoraGui.getThemeColor(0);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        long currentTime = System.currentTimeMillis();
        Iterator<Wave> iterator = waves.iterator();

        while (iterator.hasNext()) {
            Wave wave = iterator.next();
            float ageInSeconds = (currentTime - wave.startTime) / 1000.0f;
            float currentRadius = ageInSeconds * speed;

            if (currentRadius > maxSize + 1.0f) {
                iterator.remove();
                continue;
            }

            int searchRadius = (int) Math.ceil(currentRadius);

            for (int dx = -searchRadius - 2; dx <= searchRadius + 2; dx++) {
                for (int dz = -searchRadius - 2; dz <= searchRadius + 2; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);

                    if (dist <= currentRadius && dist > currentRadius - 1.5) {
                        float fade = 1.0f - (currentRadius / maxSize);
                        float alpha = fade * 0.9f;
                        if (alpha <= 0.02f) continue;

                        RenderData rData = getHighestValidPoint(mc.world, wave.center, dx, dz);
                        if (rData != null) {
                            if (rData.isLadder) {
                                renderLadderFace(buffer, matrix, rData.pos, rData.facing, r, g, b, alpha);
                            } else {
                                renderCornerESP(buffer, matrix, rData.pos, rData.height, r, g, b, alpha);
                            }
                        }
                    }
                }
            }
        }

        BuiltBuffer builtBuffer = buffer.endNullable();
        if (builtBuffer != null) BufferRenderer.drawWithGlobalProgram(builtBuffer);

        matrices.pop();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static RenderData getHighestValidPoint(World world, Vec3d center, int dx, int dz) {
        int centerY = (int) Math.floor(center.y);
        // Максимальный подъем: строго +1 блок от эпицентра удара
        BlockPos.Mutable pos = new BlockPos.Mutable((int)Math.floor(center.x) + dx, centerY + 1, (int)Math.floor(center.z) + dz);

        // Спуск: падаем вниз до 100 блоков (чтобы красиво обволакивать обрывы и каньоны)
        for (int i = 0; i < 100; i++) {
            // Защита: не сканируем ниже самой карты
            if (pos.getY() < world.getBottomY()) break;

            var state = world.getBlockState(pos);

            if (state.getBlock() instanceof LadderBlock) {
                return new RenderData(pos.toImmutable(), 0, true, state.get(LadderBlock.FACING));
            }

            boolean isSnow = state.isOf(Blocks.SNOW);
            boolean hasCollision = !state.getCollisionShape(world, pos).isEmpty();

            // Если блок твердый ИЛИ это снег
            if (!state.isAir() && (hasCollision || isSnow)) {
                VoxelShape outline = state.getOutlineShape(world, pos);
                double maxY = outline.isEmpty() ? 1.0 : outline.getMax(Direction.Axis.Y);
                return new RenderData(pos.toImmutable(), maxY, false, null);
            }

            // Если тут воздух — падаем на 1 блок ниже и ищем землю там
            pos.move(Direction.DOWN);
        }

        return null;
    }

    private static void renderLadderFace(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, Direction facing, float r, float g, float b, float a) {
        float x = pos.getX(); float y = pos.getY(); float z = pos.getZ();
        float offset = 0.05f;

        if (facing == Direction.NORTH) drawVerticalQuad(buffer, matrix, x, y, z+offset, x+1, y+1, z+offset, r, g, b, a);
        if (facing == Direction.SOUTH) drawVerticalQuad(buffer, matrix, x, y, z+1-offset, x+1, y+1, z+1-offset, r, g, b, a);
        if (facing == Direction.WEST) drawVerticalQuad(buffer, matrix, x+offset, y, z, x+offset, y+1, z+1, r, g, b, a);
        if (facing == Direction.EAST) drawVerticalQuad(buffer, matrix, x+1-offset, y, z, x+1-offset, y+1, z+1, r, g, b, a);
    }

    private static void renderCornerESP(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, double height, float r, float g, float b, float a) {
        float minX = pos.getX();
        float minY = (float) (pos.getY() + height + 0.015f);
        float minZ = pos.getZ();
        float maxX = pos.getX() + 1.0f;
        float maxZ = pos.getZ() + 1.0f;

        float fillAlpha = a * 0.15f;
        drawQuad(buffer, matrix, minX, minY, minZ, maxX, maxZ, r, g, b, fillAlpha);

        float lineAlpha = a * 0.8f;
        float th = 0.015f; float len = 0.20f;

        drawQuad(buffer, matrix, minX, minY, minZ, minX + len, minZ + th, r, g, b, lineAlpha);
        drawQuad(buffer, matrix, minX, minY, minZ, minX + th, minZ + len, r, g, b, lineAlpha);
        drawQuad(buffer, matrix, maxX - len, minY, minZ, maxX, minZ + th, r, g, b, lineAlpha);
        drawQuad(buffer, matrix, maxX - th, minY, minZ, maxX, minZ + len, r, g, b, lineAlpha);
        drawQuad(buffer, matrix, minX, minY, maxZ - th, minX + len, maxZ, r, g, b, lineAlpha);
        drawQuad(buffer, matrix, minX, minY, maxZ - len, minX + th, maxZ, r, g, b, lineAlpha);
        drawQuad(buffer, matrix, maxX - len, minY, maxZ - th, maxX, maxZ, r, g, b, lineAlpha);
        drawQuad(buffer, matrix, maxX - th, minY, maxZ - len, maxX, maxZ, r, g, b, lineAlpha);
    }

    private static void drawQuad(BufferBuilder buffer, Matrix4f matrix, float x1, float y, float z1, float x2, float z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y, z1).color(r, g, b, a);
    }

    private static void drawVerticalQuad(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
    }

    private static class Wave {
        Vec3d center; long startTime;
        Wave(Vec3d center) { this.center = center; this.startTime = System.currentTimeMillis(); }
    }

    private static class RenderData {
        BlockPos pos; double height; boolean isLadder; Direction facing;
        RenderData(BlockPos pos, double height, boolean isLadder, Direction facing) {
            this.pos = pos; this.height = height; this.isLadder = isLadder; this.facing = facing;
        }
    }
}