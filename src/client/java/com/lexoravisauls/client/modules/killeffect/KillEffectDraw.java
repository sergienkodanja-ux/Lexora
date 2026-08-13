package com.lexoravisauls.client.modules.killeffect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Hand-drawn geometry shared by the kill effects — same BufferBuilder/blend
 * technique as MotionClones.drawBox / TargetESPRenderer's quads. No vanilla
 * particle types anywhere. Shards are drawn as WIREFRAME cubes (edges only,
 * no filled faces) — reads as glowing/geometric instead of blocky.
 */
final class KillEffectDraw {

    private KillEffectDraw() {
    }

    /** Call once before drawing a batch of shapes this frame. */
    static void beginBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    /** Call once after the batch is done. */
    static void endBlend() {
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** A small camera-facing solid-color square — a hand-drawn "particle". */
    static void quad(MatrixStack matrices, Camera camera,
                      double relX, double relY, double relZ, float size,
                      float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float half = size * 0.5f;
        buffer.vertex(mat, -half, -half, 0.0f).color(r, g, b, a);
        buffer.vertex(mat, half, -half, 0.0f).color(r, g, b, a);
        buffer.vertex(mat, half, half, 0.0f).color(r, g, b, a);
        buffer.vertex(mat, -half, half, 0.0f).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * A tall vertical plane, rotated around Y — used a few times at different
     * yaws to build a "beam"/"pillar" that reads reasonably from any angle
     * without needing a full cylinder mesh. Alpha can differ top vs bottom for
     * a fade-toward-the-sky look.
     */
    static void verticalPlane(MatrixStack matrices, double relX, double relY, double relZ,
                               float width, float bottomY, float topY, float yawDeg,
                               float r, float g, float b, float alphaBottom, float alphaTop) {
        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float half = width * 0.5f;
        buffer.vertex(mat, -half, bottomY, 0.0f).color(r, g, b, alphaBottom);
        buffer.vertex(mat, half, bottomY, 0.0f).color(r, g, b, alphaBottom);
        buffer.vertex(mat, half, topY, 0.0f).color(r, g, b, alphaTop);
        buffer.vertex(mat, -half, topY, 0.0f).color(r, g, b, alphaTop);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /** A wireframe cube — 12 glowing edges only, no filled faces. */
    static void wireCube(MatrixStack matrices, double relX, double relY, double relZ,
                          float size, float thickness, float yawDeg, float pitchDeg,
                          float r, float g, float b, float a) {
        wireCubeStretched(matrices, relX, relY, relZ, size, 1.0f, thickness, yawDeg, pitchDeg, r, g, b, a);
    }

    /**
     * Wireframe box stretched along its local X axis by {@code stretch} — used
     * for a "spaghettification" look as shards whip around a singularity faster.
     */
    static void wireCubeStretched(MatrixStack matrices, double relX, double relY, double relZ,
                                   float size, float stretch, float thickness, float yawDeg, float pitchDeg,
                                   float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));

        float hx = size * 0.5f * stretch;
        float hy = size * 0.5f;
        float hz = size * 0.5f;
        float t = thickness * 0.5f;

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        edgeBox(buffer, mat, -hx, -hy, -hz, hx, -hy, -hz, t, r, g, b, a);
        edgeBox(buffer, mat, hx, -hy, -hz, hx, -hy, hz, t, r, g, b, a);
        edgeBox(buffer, mat, hx, -hy, hz, -hx, -hy, hz, t, r, g, b, a);
        edgeBox(buffer, mat, -hx, -hy, hz, -hx, -hy, -hz, t, r, g, b, a);

        edgeBox(buffer, mat, -hx, hy, -hz, hx, hy, -hz, t, r, g, b, a);
        edgeBox(buffer, mat, hx, hy, -hz, hx, hy, hz, t, r, g, b, a);
        edgeBox(buffer, mat, hx, hy, hz, -hx, hy, hz, t, r, g, b, a);
        edgeBox(buffer, mat, -hx, hy, hz, -hx, hy, -hz, t, r, g, b, a);

        edgeBox(buffer, mat, -hx, -hy, -hz, -hx, hy, -hz, t, r, g, b, a);
        edgeBox(buffer, mat, hx, -hy, -hz, hx, hy, -hz, t, r, g, b, a);
        edgeBox(buffer, mat, hx, -hy, hz, hx, hy, hz, t, r, g, b, a);
        edgeBox(buffer, mat, -hx, -hy, hz, -hx, hy, hz, t, r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /** Draws one cuboid edge as a thin box between two axis-aligned local points. */
    private static void edgeBox(BufferBuilder buffer, Matrix4f mat,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float t, float r, float g, float b, float a) {
        float minX = Math.min(x1, x2) - t, maxX = Math.max(x1, x2) + t;
        float minY = Math.min(y1, y2) - t, maxY = Math.max(y1, y2) + t;
        float minZ = Math.min(z1, z2) - t, maxZ = Math.max(z1, z2) + t;
        box(buffer, mat, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
    }

    private static void box(BufferBuilder buffer, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        v(buffer, mat, x1, y1, z2, r, g, b, a);
        v(buffer, mat, x2, y1, z2, r, g, b, a);
        v(buffer, mat, x2, y2, z2, r, g, b, a);
        v(buffer, mat, x1, y2, z2, r, g, b, a);

        v(buffer, mat, x2, y1, z1, r, g, b, a);
        v(buffer, mat, x1, y1, z1, r, g, b, a);
        v(buffer, mat, x1, y2, z1, r, g, b, a);
        v(buffer, mat, x2, y2, z1, r, g, b, a);

        v(buffer, mat, x1, y1, z1, r, g, b, a);
        v(buffer, mat, x1, y1, z2, r, g, b, a);
        v(buffer, mat, x1, y2, z2, r, g, b, a);
        v(buffer, mat, x1, y2, z1, r, g, b, a);

        v(buffer, mat, x2, y1, z2, r, g, b, a);
        v(buffer, mat, x2, y1, z1, r, g, b, a);
        v(buffer, mat, x2, y2, z1, r, g, b, a);
        v(buffer, mat, x2, y2, z2, r, g, b, a);

        v(buffer, mat, x1, y2, z2, r, g, b, a);
        v(buffer, mat, x2, y2, z2, r, g, b, a);
        v(buffer, mat, x2, y2, z1, r, g, b, a);
        v(buffer, mat, x1, y2, z1, r, g, b, a);

        v(buffer, mat, x1, y1, z1, r, g, b, a);
        v(buffer, mat, x2, y1, z1, r, g, b, a);
        v(buffer, mat, x2, y1, z2, r, g, b, a);
        v(buffer, mat, x1, y1, z2, r, g, b, a);
    }

    private static void v(BufferBuilder buffer, Matrix4f mat, float x, float y, float z,
                           float r, float g, float b, float a) {
        buffer.vertex(mat, x, y, z).color(r, g, b, a);
    }
}
