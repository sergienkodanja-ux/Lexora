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

    public void renderSmoothCape(MatrixStack matrices, VertexConsumer buffer, int light, AbstractClientPlayerEntity player, float delta, StickSimulation simulation, net.minecraft.util.Identifier capeTexture) {
        Matrix4f oldPositionMatrix = null;
        com.lexoravisauls.client.cosmetic.CosmeticManager.CapeAnimationInfo anim =
            com.lexoravisauls.client.cosmetic.CosmeticManager.getInstance().getCapeAnimation(capeTexture);
        int frame = anim.getCurrentFrame();

        for (int part = 0; part < PART_COUNT; part++) {
            matrices.push();

            applyPoseStackSimulation(matrices, player, delta, part, simulation);

            Matrix4f currentMatrix = new Matrix4f(matrices.peek().getPositionMatrix());

            if (oldPositionMatrix == null) {
                oldPositionMatrix = currentMatrix;
            }

            if (part == 0) {
                addTopVertex(buffer, currentMatrix, oldPositionMatrix, 0.3F, 0f, 0F, -0.3F, 0f, -0.06F, part, light, anim, frame);
            }
            if (part == PART_COUNT - 1) {
                addBottomVertex(buffer, currentMatrix, currentMatrix, 0.3F, (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, (part + 1) * (0.96F / PART_COUNT), -0.06F, part, light, anim, frame);
            }

            addLeftVertex(buffer, currentMatrix, oldPositionMatrix, -0.3F, (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, part * (0.96F / PART_COUNT), -0.06F, part, light, anim, frame);
            addRightVertex(buffer, currentMatrix, oldPositionMatrix, 0.3F, (part + 1) * (0.96F / PART_COUNT), 0F, 0.3F, part * (0.96F / PART_COUNT), -0.06F, part, light, anim, frame);
            addBackVertex(buffer, currentMatrix, oldPositionMatrix, 0.3F, (part + 1) * (0.96F / PART_COUNT), -0.06F, -0.3F, part * (0.96F / PART_COUNT), -0.06F, part, light, anim, frame);
            addFrontVertex(buffer, oldPositionMatrix, currentMatrix, 0.3F, (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, part * (0.96F / PART_COUNT), 0F, part, light, anim, frame);

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

        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f));
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

    private static void addBackVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light, com.lexoravisauls.client.cosmetic.CosmeticManager.CapeAnimationInfo anim, int frame) {
        float i; Matrix4f k;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; k = matrix; matrix = oldMatrix; oldMatrix = k; }
        float minV = anim.getV(1.0F + part, frame);
        float maxV = anim.getV(1.0F + part + 1, frame);
        float u1 = anim.getU(11.0F);
        float u2 = anim.getU(1.0F);

        vertex(buffer, oldMatrix, x1, y2, z1, u1, minV, light, 0f, 0f, -1f);
        vertex(buffer, oldMatrix, x2, y2, z1, u2, minV, light, 0f, 0f, -1f);
        vertex(buffer, matrix, x2, y1, z2, u2, maxV, light, 0f, 0f, -1f);
        vertex(buffer, matrix, x1, y1, z2, u1, maxV, light, 0f, 0f, -1f);
    }

    private static void addFrontVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light, com.lexoravisauls.client.cosmetic.CosmeticManager.CapeAnimationInfo anim, int frame) {
        float i; Matrix4f k;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; k = matrix; matrix = oldMatrix; oldMatrix = k; }
        float minV = anim.getV(1.0F + part, frame);
        float maxV = anim.getV(1.0F + part + 1, frame);
        float u1 = anim.getU(22.0F);
        float u2 = anim.getU(12.0F);

        vertex(buffer, oldMatrix, x1, y1, z1, u1, maxV, light, 0f, 0f, 1f);
        vertex(buffer, oldMatrix, x2, y1, z1, u2, maxV, light, 0f, 0f, 1f);
        vertex(buffer, matrix, x2, y2, z2, u2, minV, light, 0f, 0f, 1f);
        vertex(buffer, matrix, x1, y2, z2, u1, minV, light, 0f, 0f, 1f);
    }

    private static void addLeftVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light, com.lexoravisauls.client.cosmetic.CosmeticManager.CapeAnimationInfo anim, int frame) {
        float i;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; }
        float minV = anim.getV(1.0F + part, frame);
        float maxV = anim.getV(1.0F + part + 1, frame);
        float u1 = anim.getU(1.0F);
        float u2 = anim.getU(0.0F);

        vertex(buffer, matrix, x2, y1, z1, u1, maxV, light, 1f, 0f, 0f);
        vertex(buffer, matrix, x2, y1, z2, u2, maxV, light, 1f, 0f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z2, u2, minV, light, 1f, 0f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z1, u1, minV, light, 1f, 0f, 0f);
    }

    private static void addRightVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light, com.lexoravisauls.client.cosmetic.CosmeticManager.CapeAnimationInfo anim, int frame) {
        float i;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; }
        float minV = anim.getV(1.0F + part, frame);
        float maxV = anim.getV(1.0F + part + 1, frame);
        float u1 = anim.getU(11.0F);
        float u2 = anim.getU(12.0F);

        vertex(buffer, matrix, x2, y1, z2, u1, maxV, light, -1f, 0f, 0f);
        vertex(buffer, matrix, x2, y1, z1, u2, maxV, light, -1f, 0f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z1, u2, minV, light, -1f, 0f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z2, u1, minV, light, -1f, 0f, 0f);
    }

    private static void addBottomVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light, com.lexoravisauls.client.cosmetic.CosmeticManager.CapeAnimationInfo anim, int frame) {
        float i;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; }
        float u1 = anim.getU(21.0F);
        float u2 = anim.getU(11.0F);
        float v0 = anim.getV(0.0F, frame);
        float v1 = anim.getV(1.0F, frame);

        vertex(buffer, oldMatrix, x1, y2, z2, u1, v0, light, 0f, -1f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z2, u2, v0, light, 0f, -1f, 0f);
        vertex(buffer, matrix, x2, y1, z1, u2, v1, light, 0f, -1f, 0f);
        vertex(buffer, matrix, x1, y1, z1, u1, v1, light, 0f, -1f, 0f);
    }

    private static void addTopVertex(VertexConsumer buffer, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light, com.lexoravisauls.client.cosmetic.CosmeticManager.CapeAnimationInfo anim, int frame) {
        float i;
        if (x1 < x2) { i = x1; x1 = x2; x2 = i; }
        if (y1 < y2) { i = y1; y1 = y2; y2 = i; }
        float u1 = anim.getU(11.0F);
        float u2 = anim.getU(1.0F);
        float v0 = anim.getV(0.0F, frame);
        float v1 = anim.getV(1.0F, frame);

        vertex(buffer, oldMatrix, x1, y2, z1, u1, v1, light, 0f, 1f, 0f);
        vertex(buffer, oldMatrix, x2, y2, z1, u2, v1, light, 0f, 1f, 0f);
        vertex(buffer, matrix, x2, y1, z2, u2, v0, light, 0f, 1f, 0f);
        vertex(buffer, matrix, x1, y1, z2, u1, v0, light, 0f, 1f, 0f);
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