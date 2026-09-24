package com.lexoravisauls.client.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CrystalRenderer {
    private static final Vector3f[] VERTICES = new Vector3f[]{
            new Vector3f(0.0F, 1.5F, 0.0F),
            new Vector3f(0.0F, -1.5F, 0.0F),
            new Vector3f(1.0F, 0.0F, 0.0F),
            new Vector3f(-1.0F, 0.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, 1.0F),
            new Vector3f(0.0F, 0.0F, -1.0F)
    };

    private static final int[][] FACES = new int[][]{
            {0, 2, 4},
            {0, 4, 3},
            {0, 3, 5},
            {0, 5, 2},
            {1, 4, 2},
            {1, 3, 4},
            {1, 5, 3},
            {1, 2, 5}
    };

    private static final float[] FACE_BRIGHTNESS = new float[]{
            1.0F, 0.8F, 0.6F, 0.9F, 0.7F, 0.5F, 0.4F, 0.6F
    };

    public static void render(MatrixStack matrices, BufferBuilder buffer, float x, float y, float z, float size, int color) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(size, size, size);
        Matrix4f transformationMatrix = matrices.peek().getPositionMatrix();

        int alpha = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int i = 0; i < FACES.length; i++) {
            int[] face = FACES[i];
            float brightness = FACE_BRIGHTNESS[i];
            Vector3f v1 = VERTICES[face[0]];
            Vector3f v2 = VERTICES[face[1]];
            Vector3f v3 = VERTICES[face[2]];

            int shadedR = Math.min(255, Math.max(0, (int) (r * brightness)));
            int shadedG = Math.min(255, Math.max(0, (int) (g * brightness)));
            int shadedB = Math.min(255, Math.max(0, (int) (b * brightness)));

            buffer.vertex(transformationMatrix, v1.x, v1.y, v1.z).color(shadedR, shadedG, shadedB, alpha);
            buffer.vertex(transformationMatrix, v2.x, v2.y, v2.z).color(shadedR, shadedG, shadedB, alpha);
            buffer.vertex(transformationMatrix, v3.x, v3.y, v3.z).color(shadedR, shadedG, shadedB, alpha);
        }

        matrices.pop();
    }

    public static BufferBuilder createBuffer() {
        setupRenderState();
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
    }

    private static void setupRenderState() {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawBloomQuad(MatrixStack matrices, BufferBuilder builder, float x, float y, float z, float width, float height, int color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        builder.vertex(matrix, x, y + height, z).texture(0.0F, 1.0F).color(r, g, b, a);
        builder.vertex(matrix, x + width, y + height, z).texture(1.0F, 1.0F).color(r, g, b, a);
        builder.vertex(matrix, x + width, y, z).texture(1.0F, 0.0F).color(r, g, b, a);
        builder.vertex(matrix, x, y, z).texture(0.0F, 0.0F).color(r, g, b, a);
    }

    private CrystalRenderer() {
    }
}
