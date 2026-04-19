package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class JumpCircleRenderer {
    private static final Identifier CIRCLE_TEX = Identifier.of("lexoravisauls", "textures/gui/jump_circles.png");
    private static final List<JumpCircle> activeCircles = new ArrayList<>();

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Jump Circles", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || activeCircles.isEmpty()) return;

        float circleSize = LexoraGui.numSettings.getOrDefault("Jump Size", 1.8f);
        float circleSpeed = LexoraGui.numSettings.getOrDefault("Jump Speed", 1.2f);
        long currentTime = System.currentTimeMillis();


        activeCircles.removeIf(circle -> circle.isFinished);

        boolean needsDraw = false;
        for (JumpCircle circle : activeCircles) {
            float progress = (currentTime - circle.startTime) / (1000.0f / circleSpeed);
            if (progress < 1.0f) {
                needsDraw = true;
                break;
            }
        }

        if (!needsDraw) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, CIRCLE_TEX);
        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);

        matrices.push();
        net.minecraft.util.math.Vec3d camPos = camera.getPos();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (JumpCircle circle : activeCircles) {
            float progress = (currentTime - circle.startTime) / (1000.0f / circleSpeed);

            if (progress >= 1.0f) {
                circle.isFinished = true;
                continue;
            }

            float radius = circle.startRadius + (circle.maxRadius - circle.startRadius) * progress * circleSize;
            float a = 1.0f - progress;

            int color = LexoraGui.getThemeColor(progress * 0.5f);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            buffer.vertex(matrix, (float)(circle.x - radius), (float)circle.y, (float)(circle.z - radius)).texture(0, 0).color(r, g, b, a);
            buffer.vertex(matrix, (float)(circle.x - radius), (float)circle.y, (float)(circle.z + radius)).texture(0, 1).color(r, g, b, a);
            buffer.vertex(matrix, (float)(circle.x + radius), (float)circle.y, (float)(circle.z + radius)).texture(1, 1).color(r, g, b, a);
            buffer.vertex(matrix, (float)(circle.x + radius), (float)circle.y, (float)(circle.z - radius)).texture(1, 0).color(r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
        RenderSystem.enableCull();
    }

    public static void onJump(double x, double y, double z) {
        if (!LexoraGui.moduleStates.getOrDefault("Jump Circles", false)) return;

        activeCircles.add(new JumpCircle(x, y + 0.05, z, 0.0f, 1.5f));
    }

    private static class JumpCircle {
        double x, y, z;
        long startTime;
        float startRadius, maxRadius;
        boolean isFinished = false;

        public JumpCircle(double x, double y, double z, float startRadius, float maxRadius) {
            this.x = x; this.y = y; this.z = z;
            this.startTime = System.currentTimeMillis();
            this.startRadius = startRadius;
            this.maxRadius = maxRadius;
        }
    }
}