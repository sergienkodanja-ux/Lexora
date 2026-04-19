package com.lexoravisauls.client.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class TrailManager {

    public static boolean enabled = true;
    public static int trailLength = 30;
    public static boolean showInFirstPerson = false;
    public static boolean useClientColor = true;
    public static int customColor = 0xFF0000;
    public static int clientColor = 0x00FFCC;

    private static final List<TrailPoint> points = new ArrayList<>();

    private static class TrailPoint {
        Vec3d pos;
        float height;

        TrailPoint(Vec3d pos, float height) {
            this.pos = pos;
            this.height = height;
        }
    }

    public static void onTick() {
        // Подтягиваем настройки из GUI
        enabled = com.lexoravisauls.client.gui.LexoraGui.moduleStates.getOrDefault("Trails", false);
        trailLength = com.lexoravisauls.client.gui.LexoraGui.numSettings.getOrDefault("Trail Length", 30.0f).intValue();
        showInFirstPerson = com.lexoravisauls.client.gui.LexoraGui.moduleStates.getOrDefault("Trail Show 1st Person", false);

        boolean useTheme = com.lexoravisauls.client.gui.LexoraGui.modeSettings.getOrDefault("Trail Color Mode", "Theme").equals("Theme");
        if (useTheme) {
            clientColor = com.lexoravisauls.client.gui.LexoraGui.getThemeColor(0.0f);
            useClientColor = true;
        } else {
            float[] hsv = com.lexoravisauls.client.gui.LexoraGui.colorSettings.getOrDefault("Trail Custom Color", new float[]{0f, 1f, 1f});
            customColor = java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
            useClientColor = false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !enabled) {
            points.clear();
            return;
        }

        float currentHeight = (client.player.isGliding() || client.player.isSwimming()) ? 0.6f : client.player.getHeight();
        points.add(new TrailPoint(client.player.getPos(), currentHeight));

        while (points.size() > trailLength) {
            points.remove(0);
        }
    }

    public static void onRender(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!enabled || points.size() < 2) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (!showInFirstPerson && client.options.getPerspective().isFirstPerson()) {
            return;
        }

        int color = useClientColor ? clientColor : customColor;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        Vec3d camPos = camera.getPos();
        Vec3d playerPos = client.player.getLerpedPos(tickDelta);
        float currentHeight = (client.player.isGliding() || client.player.isSwimming()) ? 0.6f : client.player.getHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest(); // След внутри тела, не ломает стены
        RenderSystem.depthMask(false);
        RenderSystem.disableCull(); // Чтобы лист было видно с обеих сторон

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 🔥 Рисуем ЕДИНЫЙ гладкий лист 🔥
        for (int i = 0; i <= points.size(); i++) {
            TrailPoint point;
            if (i == points.size()) {
                point = new TrailPoint(playerPos, currentHeight); // Идеальное соединение с игроком
            } else {
                point = points.get(i);
            }

            float progress = (float) i / points.size();
            float alpha = progress * progress * 0.5f; // Затухание

            // 🔥 Хвост "всасывается" в центр персонажа 🔥
            float actualHeight = point.height * progress;
            float yOffset = (point.height - actualHeight) / 2.0f;

            float x = (float) (point.pos.x - camPos.x);
            float y = (float) (point.pos.y - camPos.y) + yOffset;
            float z = (float) (point.pos.z - camPos.z);

            // Нижняя точка листа
            buffer.vertex(matrix, x, y, z).color(r, g, b, alpha);
            // Верхняя точка листа
            buffer.vertex(matrix, x, y + actualHeight, z).color(r, g, b, alpha);
        }

        BuiltBuffer builtBuffer = buffer.endNullable();
        if (builtBuffer != null) {
            BufferRenderer.drawWithGlobalProgram(builtBuffer);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}