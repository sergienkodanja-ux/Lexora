package com.lexoravisauls.client.render.cape;

// --- ТЕ САМЫЕ ПРАВИЛЬНЫЕ ИМПОРТЫ ФИЗИКИ ---
import com.lexoravisauls.client.render.cape.sim.StickSimulation;
import com.lexoravisauls.client.render.cape.sim.Vector3;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

public class WaveyCapeGeometry {
    private static final int PART_COUNT = 16;

    public void renderSmoothCape(MatrixStack matrices, VertexConsumer buffer, int light, AbstractClientPlayerEntity player, float delta, StickSimulation simulation) {
        Matrix4f oldPositionMatrix = null;

        for (int part = 0; part < PART_COUNT; part++) {
            matrices.push();

            applyPoseStackSimulation(matrices, player, delta, part, simulation);

            Matrix4f currentMatrix = new Matrix4f(matrices.peek().getPositionMatrix());

            if (oldPositionMatrix == null) {
                oldPositionMatrix = currentMatrix;
            }

            if (part == 0) {
                addTopVertex(buffer, currentMatrix, oldPositionMatrix, 0.3F, 0f, 0F, -0.3F, 0f, -0.06F, part, light);
            }
            if (part == PART_COUNT - 1) {
                addBottomVertex(buffer, currentMatrix, currentMatrix, 0.3F, (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, (part + 1) * (0.96F / PART_COUNT), -0.06F, part, light);
            }

            addLeftVertex(buffer, currentMatrix, oldPositionMatrix, -0.3F, (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, part * (0.96F / PART_COUNT), -0.06F, part, light);
            addRightVertex(buffer, currentMatrix, oldPositionMatrix, 0.3F, (part + 1) * (0.96F / PART_COUNT), 0F, 0.3F, part * (0.96F / PART_COUNT), -0.06F, part, light);
            addBackVertex(buffer, currentMatrix, oldPositionMatrix, 0.3F, (part + 1) * (0.96F / PART_COUNT), -0.06F, -0.3F, part * (0.96F / PART_COUNT), -0.06F, part, light);
            addFrontVertex(buffer, oldPositionMatrix, currentMatrix, 0.3F, (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, part * (0.96F / PART_COUNT), 0F, part, light);

            oldPositionMatrix = currentMatrix;
            matrices.pop();
        }
    }

    private void applyPoseStackSimulation(MatrixStack poseStack, AbstractClientPlayerEntity player, float delta, int part, StickSimulation simulation) {
        poseStack.translate(0.0, 0.0, 0.125);

        StickSimulation.Point capePoint = simulation.getPoints().get(0);
        float x = simulation.getPoints().get(part).getLerpX(delta) - capePoint.getLerpX(delta);
        if (x > 0.0f) x = 0.0f;

        float y = capePoint.getLerpY(delta) - part - simulation.getPoints().get(part).getLerpY(delta);
        float z = capePoint.getLerpZ(delta) - simulation.getPoints().get(part).getLerpZ(delta);

        float partRotation = getRotation(delta, part, simulation);
        float height = player.isSneaking() ? 25.0f : 0.0f;

        if (player.isSneaking()) poseStack.translate(0.0, 0.15, 0.0);

        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f + height));
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));

        poseStack.translate(-z / PART_COUNT, y / PART_COUNT, x / PART_COUNT);
        poseStack.translate(0.0, 0.03, -0.03);
        poseStack.translate(0.0, part * 1.0f / PART_COUNT, 0);
        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-partRotation));
        poseStack.translate(0.0, -part * 1.0f / PART_COUNT, 0);
        poseStack.translate(0.0, -0.03, 0.03);
    }

    private float getRotation(float delta, int part, StickSimulation simulation) {
        if (part == PART_COUNT - 1) return getRotation(delta, part - 1, simulation);
        Vector3 p1 = simulation.getPoints().get(part).getLerpedPos(delta);
        Vector3 p2 = simulation.getPoints().get(part + 1).getLerpedPos(delta);
        return (float) (Math.toDegrees(Math.atan2(p2.x - p1.x, p2.y - p1.y)) + 180.0);
    }

    private static void addBackVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i; Matrix4f k;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; k = matrix; matrix = oldMatrix; oldMatrix = k; }
        float vPerPart = 0.5f / PART_COUNT;
        float minV = 0.03125F + (vPerPart * part), maxV = 0.03125F + (vPerPart * (part + 1));

        vertex(buffer, oldMatrix, x1, y2, z1, 0.171875F, minV, light, 0f, 0f, -1f);
        vertex(buffer, oldMatrix, x2, y2, z1, 0.015625F, minV, light, 0f, 0f, -1f);
        vertex(buffer, matrix, x2, y1, z2, 0.015625F, maxV, light, 0f, 0f, -1f);
        vertex(buffer, matrix, x1, y1, z2, 0.171875F, maxV, light, 0f, 0f, -1f);
    }

    private static void addFrontVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i; Matrix4f k;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; k = matrix; matrix = oldMatrix; oldMatrix = k; }
        float vPerPart = 0.5f / PART_COUNT;
        float minV = 0.03125F + (vPerPart * part), maxV = 0.03125F + (vPerPart * (part + 1));

        vertex(buffer, oldMatrix, x1, y1, z1, 0.34375F, maxV, light, 0f, 0f, 1f);
        vertex(buffer, oldMatrix, x2, y1, z1, 0.1875F, maxV, light, 0f, 0f, 1f);
        vertex(buffer, matrix, x2, y2, z2, 0.1875F, minV, light, 0f, 0f, 1f);
        vertex(buffer, matrix, x1, y2, z2, 0.34375F, minV, light, 0f, 0f, 1f);
    }

    private static void addLeftVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; }
        float vPerPart = 0.5f / PART_COUNT;
        float minV = 0.03125F + (vPerPart * part), maxV = 0.03125F + (vPerPart * (part + 1));

        vertex(buffer, matrix, x2, y1, z1, 0.015625F, maxV, light, 1f, 0f, 0f);
        vertex(buffer, matrix, x2, y1, z2, 0f, maxV, light, 1f, 0f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z2, 0f, minV, light, 1f, 0f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z1, 0.015625F, minV, light, 1f, 0f, 0f);
    }

    private static void addRightVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; }
        float vPerPart = 0.5f / PART_COUNT;
        float minV = 0.03125F + (vPerPart * part), maxV = 0.03125F + (vPerPart * (part + 1));

        vertex(buffer, matrix, x2, y1, z2, 0.171875F, maxV, light, -1f, 0f, 0f);
        vertex(buffer, matrix, x2, y1, z1, 0.1875F, maxV, light, -1f, 0f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z1, 0.1875F, minV, light, -1f, 0f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z2, 0.171875F, minV, light, -1f, 0f, 0f);
    }

    private static void addBottomVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; }

        vertex(buffer, oldMatrix, x1, y2, z2, 0.328125F, 0f, light, 0f, -1f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z2, 0.171875F, 0f, light, 0f, -1f, 0f);
        vertex(buffer, matrix, x2, y1, z1, 0.171875F, 0.03125F, light, 0f, -1f, 0f);
        vertex(buffer, matrix, x1, y1, z1, 0.328125F, 0.03125F, light, 0f, -1f, 0f);
    }

    private static void addTopVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; }

        vertex(buffer, oldMatrix, x1, y2, z1, 0.171875F, 0.03125F, light, 0f, 1f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z1, 0.015625F, 0.03125F, light, 0f, 1f, 0f);
        vertex(buffer, matrix, x2, y1, z2, 0.015625F, 0f, light, 0f, 1f, 0f);
        vertex(buffer, matrix, x1, y1, z2, 0.171875F, 0f, light, 0f, 1f, 0f);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v, int light, float nx, float ny, float nz) {
        consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(nx, ny, nz);
    }
}