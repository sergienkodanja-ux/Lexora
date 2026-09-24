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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.Random;

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
        beginGlow(false);
    }

    /** Call once before drawing with optional depth testing. */
    static void beginGlow(boolean depthTest) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    /** Call once after the batch is done. */
    static void endBlend() {
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    static void endGlow() {
        endBlend();
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

    /**
     * Draws a smooth 3D glowing ring with soft radial fade at the inner/outer edges.
     */
    static void drawRing(MatrixStack matrices, double relX, double relY, double relZ,
                         float radius, float thickness, float pitchDeg, float yawDeg, float rollDeg,
                         float r, float g, float b, float a, int segments) {
        if (a <= 0.005f || radius <= 0.001f) return;
        matrices.push();
        matrices.translate(relX, relY, relZ);
        if (yawDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
        if (pitchDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));
        if (rollDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDeg));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float halfT = thickness * 0.5f;
        float rInner = Math.max(0.0f, radius - halfT);
        float rOuter = radius + halfT;

        float step = (float) (Math.PI * 2.0 / segments);
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            // Inner fade band (rInner -> radius)
            v(buffer, mat, cos1 * rInner, 0.0f, sin1 * rInner, r, g, b, 0.0f);
            v(buffer, mat, cos1 * radius, 0.0f, sin1 * radius, r, g, b, a);
            v(buffer, mat, cos2 * radius, 0.0f, sin2 * radius, r, g, b, a);
            v(buffer, mat, cos2 * rInner, 0.0f, sin2 * rInner, r, g, b, 0.0f);

            // Outer fade band (radius -> rOuter)
            v(buffer, mat, cos1 * radius, 0.0f, sin1 * radius, r, g, b, a);
            v(buffer, mat, cos1 * rOuter, 0.0f, sin1 * rOuter, r, g, b, 0.0f);
            v(buffer, mat, cos2 * rOuter, 0.0f, sin2 * rOuter, r, g, b, 0.0f);
            v(buffer, mat, cos2 * radius, 0.0f, sin2 * radius, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * Draws a filled circular disk with radial gradient from center to edge.
     */
    static void drawDisk(MatrixStack matrices, double relX, double relY, double relZ,
                         float radius, float pitchDeg, float yawDeg,
                         float r, float g, float b, float centerAlpha, float edgeAlpha, int segments) {
        if (centerAlpha <= 0.005f && edgeAlpha <= 0.005f) return;
        matrices.push();
        matrices.translate(relX, relY, relZ);
        if (yawDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
        if (pitchDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float step = (float) (Math.PI * 2.0 / segments);
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            v(buffer, mat, 0.0f, 0.0f, 0.0f, r, g, b, centerAlpha);
            v(buffer, mat, cos1 * radius, 0.0f, sin1 * radius, r, g, b, edgeAlpha);
            v(buffer, mat, cos2 * radius, 0.0f, sin2 * radius, r, g, b, edgeAlpha);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * Volumetric 3D cylindrical beam with vertical gradient and additive optical thickness.
     */
    static void drawVolumetricCylinder(MatrixStack matrices, double relX, double relY, double relZ,
                                       float radiusBottom, float radiusTop, float height, float yawDeg,
                                       float r, float g, float b, float alphaBottom, float alphaTop, int segments) {
        if (alphaBottom <= 0.005f && alphaTop <= 0.005f) return;
        matrices.push();
        matrices.translate(relX, relY, relZ);
        if (yawDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float step = (float) (Math.PI * 2.0 / segments);
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            v(buffer, mat, cos1 * radiusBottom, 0.0f, sin1 * radiusBottom, r, g, b, alphaBottom);
            v(buffer, mat, cos2 * radiusBottom, 0.0f, sin2 * radiusBottom, r, g, b, alphaBottom);
            v(buffer, mat, cos2 * radiusTop, height, sin2 * radiusTop, r, g, b, alphaTop);
            v(buffer, mat, cos1 * radiusTop, height, sin1 * radiusTop, r, g, b, alphaTop);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * Draws a 3D line segment with volumetric 3D cross-planes.
     */
    static void drawLine3D(MatrixStack matrices, double x1, double y1, double z1,
                           double x2, double y2, double z2, float width,
                           float r, float g, float b, float a) {
        if (a <= 0.005f) return;
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001) return;

        double nx = dx / len, ny = dy / len, nz = dz / len;
        // First perpendicular
        double px, py, pz;
        if (Math.abs(ny) < 0.95) {
            px = -nz; py = 0; pz = nx;
        } else {
            px = 1; py = 0; pz = 0;
        }
        double plen = Math.sqrt(px * px + py * py + pz * pz);
        px /= plen; py /= plen; pz /= plen;

        // Second perpendicular (nx,ny,nz) x (px,py,pz)
        double qx = ny * pz - nz * py;
        double qy = nz * px - nx * pz;
        double qz = nx * py - ny * px;

        float h = width * 0.5f;
        float hpx = (float) (px * h), hpy = (float) (py * h), hpz = (float) (pz * h);
        float hqx = (float) (qx * h), hqy = (float) (qy * h), hqz = (float) (qz * h);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Plane 1
        v(buffer, mat, (float) (x1 - hpx), (float) (y1 - hpy), (float) (z1 - hpz), r, g, b, a);
        v(buffer, mat, (float) (x1 + hpx), (float) (y1 + hpy), (float) (z1 + hpz), r, g, b, a);
        v(buffer, mat, (float) (x2 + hpx), (float) (y2 + hpy), (float) (z2 + hpz), r, g, b, a);
        v(buffer, mat, (float) (x2 - hpx), (float) (y2 - hpy), (float) (z2 - hpz), r, g, b, a);

        // Plane 2
        v(buffer, mat, (float) (x1 - hqx), (float) (y1 - hqy), (float) (z1 - hqz), r, g, b, a);
        v(buffer, mat, (float) (x1 + hqx), (float) (y1 + hqy), (float) (z1 + hqz), r, g, b, a);
        v(buffer, mat, (float) (x2 + hqx), (float) (y2 + hqy), (float) (z2 + hqz), r, g, b, a);
        v(buffer, mat, (float) (x2 - hqx), (float) (y2 - hqy), (float) (z2 - hqz), r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /**
     * Draws an intricate sacred geometric runic seal on the ground.
     */
    static void drawRunicSeal(MatrixStack matrices, double relX, double relY, double relZ,
                              float radius, float spinOuter, float spinInner,
                              float r, float g, float b, float a) {
        if (a <= 0.005f) return;

        // Ground offset to avoid z-fighting
        double y = relY + 0.02;

        // 1. Outer glowing border ring
        drawRing(matrices, relX, y, relZ, radius, radius * 0.14f, 0, 0, 0, r, g, b, a * 0.85f, 48);

        // 2. Outer thin sharp circle
        drawRing(matrices, relX, y, relZ, radius * 1.05f, 0.03f, 0, 0, 0, 1.0f, 1.0f, 1.0f, a * 0.9f, 48);

        // 3. Concentric inner ring
        float innerRadius = radius * 0.65f;
        drawRing(matrices, relX, y, relZ, innerRadius, 0.04f, 0, 0, 0, r, g, b, a, 36);

        // 4. Center luminous core
        drawDisk(matrices, relX, y, relZ, radius * 0.25f, 0, 0, r, g, b, a * 0.6f, 0.0f, 24);

        // 5. Rotating outer tick marks / runic dashes
        int ticks = 24;
        float tickStep = (float) (Math.PI * 2.0 / ticks);
        float tickOuter = radius * 1.04f;
        float tickInner = radius * 0.94f;
        float radOuterSpin = (float) Math.toRadians(spinOuter);
        for (int i = 0; i < ticks; i++) {
            float ang = i * tickStep + radOuterSpin;
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            drawLine3D(matrices,
                    relX + cos * tickInner, y, relZ + sin * tickInner,
                    relX + cos * tickOuter, y, relZ + sin * tickOuter,
                    0.035f, 1.0f, 0.95f, 0.7f, a * 0.95f);
        }

        // 6. Inscribed 8-pointed star (octagram: two intertwined rotating squares)
        float radInnerSpin = (float) Math.toRadians(spinInner);
        float[] starX = new float[8];
        float[] starZ = new float[8];
        for (int i = 0; i < 8; i++) {
            float ang = (float) (i * (Math.PI / 4.0)) + radInnerSpin;
            starX[i] = (float) (Math.cos(ang) * innerRadius);
            starZ[i] = (float) (Math.sin(ang) * innerRadius);
        }

        // Square 1: vertices 0, 2, 4, 6
        int[] sq1 = {0, 2, 4, 6, 0};
        for (int i = 0; i < 4; i++) {
            drawLine3D(matrices,
                    relX + starX[sq1[i]], y, relZ + starZ[sq1[i]],
                    relX + starX[sq1[i + 1]], y, relZ + starZ[sq1[i + 1]],
                    0.03f, r, g, b, a * 0.85f);
        }
        // Square 2: vertices 1, 3, 5, 7
        int[] sq2 = {1, 3, 5, 7, 1};
        for (int i = 0; i < 4; i++) {
            drawLine3D(matrices,
                    relX + starX[sq2[i]], y, relZ + starZ[sq2[i]],
                    relX + starX[sq2[i + 1]], y, relZ + starZ[sq2[i + 1]],
                    0.03f, r, g, b, a * 0.85f);
        }

        // 7. Radiating rays from star tips to outer ring
        for (int i = 0; i < 8; i++) {
            float ang = (float) (i * (Math.PI / 4.0)) + radInnerSpin;
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            drawLine3D(matrices,
                    relX + starX[i], y, relZ + starZ[i],
                    relX + cos * radius, y, relZ + sin * radius,
                    0.025f, 1.0f, 0.9f, 0.6f, a * 0.75f);
        }
    }

    /**
     * Procedural recursive branching lightning bolt.
     */
    static void drawBranchingLightning(MatrixStack matrices,
                                       double x1, double y1, double z1,
                                       double x2, double y2, double z2,
                                       int depth, Random random,
                                       float r, float g, float b, float a, float width) {
        if (depth <= 0) {
            // Core white line
            drawLine3D(matrices, x1, y1, z1, x2, y2, z2, width * 0.5f, 1.0f, 1.0f, 1.0f, a);
            // Outer colored glow line
            drawLine3D(matrices, x1, y1, z1, x2, y2, z2, width * 1.5f, r, g, b, a * 0.55f);
            return;
        }

        double midX = (x1 + x2) * 0.5;
        double midY = (y1 + y2) * 0.5;
        double midZ = (z1 + z2) * 0.5;

        double dist = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
        double jitter = dist * 0.22;

        midX += (random.nextDouble() - 0.5) * jitter;
        midY += (random.nextDouble() - 0.5) * jitter;
        midZ += (random.nextDouble() - 0.5) * jitter;

        drawBranchingLightning(matrices, x1, y1, z1, midX, midY, midZ, depth - 1, random, r, g, b, a, width);
        drawBranchingLightning(matrices, midX, midY, midZ, x2, y2, z2, depth - 1, random, r, g, b, a, width);

        // 35% chance of child fork
        if (depth >= 2 && random.nextFloat() < 0.35f) {
            double branchX = midX + (midX - x1) * 0.65 + (random.nextDouble() - 0.5) * jitter * 1.5;
            double branchY = midY + (midY - y1) * 0.65 + (random.nextDouble() - 0.5) * jitter * 1.5;
            double branchZ = midZ + (midZ - z1) * 0.65 + (random.nextDouble() - 0.5) * jitter * 1.5;
            drawBranchingLightning(matrices, midX, midY, midZ, branchX, branchY, branchZ, depth - 2, random, r, g, b, a * 0.7f, width * 0.65f);
        }
    }

    /**
     * 3D faceted crystal obelisk with shaded polygonal faces and glowing edge ridges.
     */
    static void drawCrystalSpike(MatrixStack matrices, double relX, double relY, double relZ,
                                 float baseWidth, float height, float tiltX, float tiltZ, float yawDeg,
                                 float r, float g, float b, float a) {
        if (a <= 0.005f || height <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        if (yawDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));

        Matrix4f mat = matrices.peek().getPositionMatrix();

        int sides = 5;
        float[] bx = new float[sides];
        float[] bz = new float[sides];
        float step = (float) (Math.PI * 2.0 / sides);
        for (int i = 0; i < sides; i++) {
            bx[i] = (float) (Math.cos(i * step) * baseWidth);
            bz[i] = (float) (Math.sin(i * step) * baseWidth);
        }

        float tipX = tiltX * height;
        float tipY = height;
        float tipZ = tiltZ * height;

        // 1. Shaded triangular crystal side faces
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            // Approximate face normal for light shading
            float midX = (bx[i] + bx[next]) * 0.5f;
            float midZ = (bz[i] + bz[next]) * 0.5f;
            float lightDot = Math.max(0.35f, 0.65f + 0.35f * (midX * 0.6f + midZ * 0.8f) / Math.max(0.01f, baseWidth));

            float cr = Math.min(1.0f, r * lightDot);
            float cg = Math.min(1.0f, g * lightDot);
            float cb = Math.min(1.0f, b * lightDot);
            float ca = a * 0.80f;

            v(buffer, mat, bx[i], 0.0f, bz[i], cr, cg, cb, ca);
            v(buffer, mat, bx[next], 0.0f, bz[next], cr, cg, cb, ca);
            v(buffer, mat, tipX, tipY, tipZ, cr * 1.2f, cg * 1.2f, cb * 1.2f, ca);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // 2. Glowing wireframe edge ridges from base to tip and around base
        for (int i = 0; i < sides; i++) {
            drawLine3D(matrices, bx[i], 0.0, bz[i], tipX, tipY, tipZ, 0.035f,
                    Math.min(1.0f, r * 1.3f), Math.min(1.0f, g * 1.3f), Math.min(1.0f, b * 1.3f), a);
            int next = (i + 1) % sides;
            drawLine3D(matrices, bx[i], 0.0, bz[i], bx[next], 0.0, bz[next], 0.025f, r, g, b, a * 0.6f);
        }

        matrices.pop();
    }

    /**
     * 3D spiraling ribbon (helix) ascending around Y axis.
     */
    static void drawHelix(MatrixStack matrices, double relX, double relY, double relZ,
                          float radius, float height, float startAngle, float totalTurns,
                          float ribbonWidth, float r, float g, float b, float a, int segments) {
        if (a <= 0.005f || height <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float halfW = ribbonWidth * 0.5f;
        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;

            float ang1 = startAngle + t1 * totalTurns * (float) (Math.PI * 2.0);
            float ang2 = startAngle + t2 * totalTurns * (float) (Math.PI * 2.0);

            float x1 = (float) Math.cos(ang1) * radius;
            float z1 = (float) Math.sin(ang1) * radius;
            float y1 = t1 * height;

            float x2 = (float) Math.cos(ang2) * radius;
            float z2 = (float) Math.sin(ang2) * radius;
            float y2 = t2 * height;

            // Fade in at bottom, peak in middle, fade at top
            float a1 = a * (float) Math.sin(t1 * Math.PI);
            float a2 = a * (float) Math.sin(t2 * Math.PI);

            v(buffer, mat, x1, y1 - halfW, z1, r, g, b, a1);
            v(buffer, mat, x2, y2 - halfW, z2, r, g, b, a2);
            v(buffer, mat, x2, y2 + halfW, z2, r, g, b, a2);
            v(buffer, mat, x1, y1 + halfW, z1, r, g, b, a1);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * Translucent expanding shockwave dome / hemisphere with glowing latitude & longitude lines.
     */
    static void drawShockwaveDome(MatrixStack matrices, double relX, double relY, double relZ,
                                  float radius, float heightFactor, float r, float g, float b, float a,
                                  int latSegments, int lonSegments) {
        if (a <= 0.005f || radius <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        // 1. Translucent dome faces
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int lat = 0; lat < latSegments; lat++) {
            float p1 = (float) lat / latSegments;
            float p2 = (float) (lat + 1) / latSegments;

            float phi1 = p1 * (float) (Math.PI * 0.5);
            float phi2 = p2 * (float) (Math.PI * 0.5);

            float r1 = (float) Math.cos(phi1) * radius;
            float y1 = (float) Math.sin(phi1) * radius * heightFactor;
            float r2 = (float) Math.cos(phi2) * radius;
            float y2 = (float) Math.sin(phi2) * radius * heightFactor;

            float a1 = a * 0.25f * (1.0f - p1 * 0.7f);
            float a2 = a * 0.25f * (1.0f - p2 * 0.7f);

            for (int lon = 0; lon < lonSegments; lon++) {
                float theta1 = (float) (lon * Math.PI * 2.0 / lonSegments);
                float theta2 = (float) ((lon + 1) * Math.PI * 2.0 / lonSegments);

                float c1 = (float) Math.cos(theta1), s1 = (float) Math.sin(theta1);
                float c2 = (float) Math.cos(theta2), s2 = (float) Math.sin(theta2);

                v(buffer, mat, c1 * r1, y1, s1 * r1, r, g, b, a1);
                v(buffer, mat, c2 * r1, y1, s2 * r1, r, g, b, a1);
                v(buffer, mat, c2 * r2, y2, s2 * r2, r, g, b, a2);
                v(buffer, mat, c1 * r2, y2, s1 * r2, r, g, b, a2);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        // 2. Glowing latitude wireframe rings
        drawRing(matrices, relX, relY + 0.02, relZ, radius, 0.06f, 0, 0, 0, r, g, b, a * 0.9f, lonSegments);
        float midR = (float) Math.cos(Math.PI * 0.25) * radius;
        float midY = (float) Math.sin(Math.PI * 0.25) * radius * heightFactor;
        drawRing(matrices, relX, relY + midY, relZ, midR, 0.04f, 0, 0, 0, r, g, b, a * 0.6f, lonSegments);
    }

    /**
     * Tumbling 3D faceted crystal diamond (octahedron).
     */
    static void drawDiamondShard(MatrixStack matrices, double relX, double relY, double relZ,
                                 float size, float yaw, float pitch, float roll,
                                 float r, float g, float b, float a) {
        if (a <= 0.005f || size <= 0.001f) return;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        if (yaw != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        if (pitch != 0.0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        if (roll != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));

        Matrix4f mat = matrices.peek().getPositionMatrix();

        float h = size;
        float w = size * 0.55f;

        // 8 triangular faces (top pyramid + bottom pyramid)
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        float[][] pts = {
                {w, 0, 0}, {0, 0, w}, {-w, 0, 0}, {0, 0, -w}
        };

        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            float shade = 0.65f + 0.35f * (i % 2 == 0 ? 1.0f : 0.7f);
            float cr = Math.min(1.0f, r * shade);
            float cg = Math.min(1.0f, g * shade);
            float cb = Math.min(1.0f, b * shade);

            // Top pyramid face
            v(buffer, mat, pts[i][0], 0, pts[i][2], cr, cg, cb, a * 0.85f);
            v(buffer, mat, pts[next][0], 0, pts[next][2], cr, cg, cb, a * 0.85f);
            v(buffer, mat, 0, h, 0, cr * 1.2f, cg * 1.2f, cb * 1.2f, a);

            // Bottom pyramid face
            v(buffer, mat, pts[next][0], 0, pts[next][2], cr * 0.8f, cg * 0.8f, cb * 0.8f, a * 0.85f);
            v(buffer, mat, pts[i][0], 0, pts[i][2], cr * 0.8f, cg * 0.8f, cb * 0.8f, a * 0.85f);
            v(buffer, mat, 0, -h, 0, cr * 0.7f, cg * 0.7f, cb * 0.7f, a);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        matrices.pop();
    }

    /**
     * Draws a solid 3D Nimb with mass (top, bottom, outer wall, inner wall)
     * exactly as structured in NimbRenderer.java, without animation.
     */
    static void drawSolidNimb(MatrixStack matrices, double relX, double relY, double relZ,
                              float radius, float thick, float h,
                              float r, float g, float b, float a) {
        if (a <= 0.005f || radius <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        int rTop = (int) (r * 255.0f);
        int gTop = (int) (g * 255.0f);
        int bTop = (int) (b * 255.0f);

        int rBottom = (int) (r * 0.50f * 255.0f);
        int gBottom = (int) (g * 0.50f * 255.0f);
        int bBottom = (int) (b * 0.50f * 255.0f);

        int rOuter = (int) (r * 0.80f * 255.0f);
        int gOuter = (int) (g * 0.80f * 255.0f);
        int bOuter = (int) (b * 0.80f * 255.0f);

        int rInner = (int) (r * 0.60f * 255.0f);
        int gInner = (int) (g * 0.60f * 255.0f);
        int bInner = (int) (b * 0.60f * 255.0f);

        int alphaInt = (int) (Math.min(1.0f, a) * 255.0f);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int points = 36;
        for (int i = 0; i < points; i++) {
            float rad1 = (float) (i * Math.PI * 2.0 / points);
            float rad2 = (float) ((i + 1) * Math.PI * 2.0 / points);

            float c1 = MathHelper.cos(rad1);
            float s1 = MathHelper.sin(rad1);
            float c2 = MathHelper.cos(rad2);
            float s2 = MathHelper.sin(rad2);

            float x1Out = c1 * (radius + thick);
            float z1Out = s1 * (radius + thick);
            float x1In  = c1 * (radius - thick);
            float z1In  = s1 * (radius - thick);

            float x2Out = c2 * (radius + thick);
            float z2Out = s2 * (radius + thick);
            float x2In  = c2 * (radius - thick);
            float z2In  = s2 * (radius - thick);

            // 1. Top Face (+h)
            buffer.vertex(mat, x1In,  h, z1In ).color(rTop, gTop, bTop, alphaInt);
            buffer.vertex(mat, x1Out, h, z1Out).color(rTop, gTop, bTop, alphaInt);
            buffer.vertex(mat, x2Out, h, z2Out).color(rTop, gTop, bTop, alphaInt);
            buffer.vertex(mat, x2In,  h, z2In ).color(rTop, gTop, bTop, alphaInt);

            // 2. Bottom Face (-h)
            buffer.vertex(mat, x1In,  -h, z1In ).color(rBottom, gBottom, bBottom, alphaInt);
            buffer.vertex(mat, x2In,  -h, z2In ).color(rBottom, gBottom, bBottom, alphaInt);
            buffer.vertex(mat, x2Out, -h, z2Out).color(rBottom, gBottom, bBottom, alphaInt);
            buffer.vertex(mat, x1Out, -h, z1Out).color(rBottom, gBottom, bBottom, alphaInt);

            // 3. Outer Wall
            buffer.vertex(mat, x1Out,  h, z1Out).color(rOuter, gOuter, bOuter, alphaInt);
            buffer.vertex(mat, x1Out, -h, z1Out).color(rOuter, gOuter, bOuter, alphaInt);
            buffer.vertex(mat, x2Out, -h, z2Out).color(rOuter, gOuter, bOuter, alphaInt);
            buffer.vertex(mat, x2Out,  h, z2Out).color(rOuter, gOuter, bOuter, alphaInt);

            // 4. Inner Wall
            buffer.vertex(mat, x1In,  h, z1In ).color(rInner, gInner, bInner, alphaInt);
            buffer.vertex(mat, x2In,  h, z2In ).color(rInner, gInner, bInner, alphaInt);
            buffer.vertex(mat, x2In, -h, z2In ).color(rInner, gInner, bInner, alphaInt);
            buffer.vertex(mat, x1In, -h, z1In ).color(rInner, gInner, bInner, alphaInt);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }
}
