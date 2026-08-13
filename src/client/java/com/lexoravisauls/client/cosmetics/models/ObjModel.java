package com.lexoravisauls.client.cosmetics.models;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ObjModel {
    private final List<Vector3f> vertices = new ArrayList<>();
    private final List<Vector2f> uvs = new ArrayList<>();
    private final List<Vector3f> normals = new ArrayList<>();

    // Разделяем лица (полигоны) на левую и правую сторону по координате X
    private final List<int[][]> leftFaces = new ArrayList<>();
    private final List<int[][]> rightFaces = new ArrayList<>();

    public ObjModel(Identifier objLocation) {
        load(objLocation);
    }

    private void load(Identifier location) {
        try {
            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] split = line.split("\\s+");
                    vertices.add(new Vector3f(Float.parseFloat(split[1]), Float.parseFloat(split[2]), Float.parseFloat(split[3])));
                } else if (line.startsWith("vt ")) {
                    String[] split = line.split("\\s+");
                    uvs.add(new Vector2f(Float.parseFloat(split[1]), 1.0f - Float.parseFloat(split[2])));
                } else if (line.startsWith("vn ")) {
                    String[] split = line.split("\\s+");
                    normals.add(new Vector3f(Float.parseFloat(split[1]), Float.parseFloat(split[2]), Float.parseFloat(split[3])));
                } else if (line.startsWith("f ")) {
                    String[] split = line.split("\\s+");
                    int[][] face = new int[split.length - 1][3];

                    float sumX = 0f;
                    for (int i = 1; i < split.length; i++) {
                        String[] parts = split[i].split("/");
                        int vIdx = Integer.parseInt(parts[0]) - 1;
                        face[i - 1][0] = vIdx;
                        face[i - 1][1] = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1]) - 1 : -1;
                        face[i - 1][2] = (parts.length > 2 && !parts[2].isEmpty()) ? Integer.parseInt(parts[2]) - 1 : -1;

                        if (vIdx >= 0 && vIdx < vertices.size()) {
                            sumX += vertices.get(vIdx).x();
                        }
                    }

                    // Определяем, к какому крылу принадлежит полигон по среднему X
                    float avgX = sumX / (split.length - 1);
                    if (avgX < 0) {
                        leftFaces.add(face);
                    } else {
                        rightFaces.add(face);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void renderLeft(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        renderFaces(matrices, vertexConsumer, leftFaces, light, overlay, color);
    }

    public void renderRight(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        renderFaces(matrices, vertexConsumer, rightFaces, light, overlay, color);
    }

    // Для НЕ парных объектов (копьё, меч, любой цельный предмет, который не
    // нужно резать на левую/правую половину) — рендерит всю геометрию разом.
    // leftFaces/rightFaces всё ещё заполняются при загрузке (авто-разделение
    // по X происходит независимо от того, как модель потом рендерится), но
    // для непарных объектов это разделение не имеет смысла — renderAll()
    // просто рисует обе части вместе, что и составляет полную модель.
    public void renderAll(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        renderFaces(matrices, vertexConsumer, leftFaces, light, overlay, color);
        renderFaces(matrices, vertexConsumer, rightFaces, light, overlay, color);
    }

    private void renderFaces(MatrixStack matrices, VertexConsumer vertexConsumer, List<int[][]> facesToRender, int light, int overlay, int color) {
        if (facesToRender.isEmpty()) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a <= 0.01f) a = 1.0f;

        for (int[][] face : facesToRender) {
            if (face.length == 3) {
                renderVertex(vertexConsumer, matrix, entry, face[0], r, g, b, a, light, overlay);
                renderVertex(vertexConsumer, matrix, entry, face[1], r, g, b, a, light, overlay);
                renderVertex(vertexConsumer, matrix, entry, face[2], r, g, b, a, light, overlay);
                renderVertex(vertexConsumer, matrix, entry, face[2], r, g, b, a, light, overlay);
            } else if (face.length == 4) {
                for (int i = 0; i < 4; i++) {
                    renderVertex(vertexConsumer, matrix, entry, face[i], r, g, b, a, light, overlay);
                }
            }
        }
    }

    private void renderVertex(VertexConsumer vertexConsumer, Matrix4f matrix, MatrixStack.Entry entry, int[] vertexData, float r, float g, float b, float a, int light, int overlay) {
        Vector3f v = vertices.get(vertexData[0]);
        Vector2f vt = vertexData[1] != -1 ? uvs.get(vertexData[1]) : new Vector2f(0, 0);
        Vector3f vn = vertexData[2] != -1 ? normals.get(vertexData[2]) : new Vector3f(0, 1, 0);

        vertexConsumer.vertex(matrix, v.x(), v.y(), v.z())
                .color(r, g, b, a)
                .texture(vt.x(), vt.y())
                .overlay(overlay)
                .light(light)
                .normal(entry, vn.x(), vn.y(), vn.z());
    }
}