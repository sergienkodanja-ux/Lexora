package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.ShaderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.awt.Color;

public class CustomHitbox {

    private static ShaderUtil nebulaShader;
    private static ShaderUtil waterShader;
    private static ShaderUtil flameShader;

    private static int vaoID = -1;
    private static int vboID = -1;

    private static void ensureBuffers() {
        if (vaoID == -1) {
            vaoID = GL30.glGenVertexArrays();
        }
        if (vboID == -1) {
            vboID = GL15.glGenBuffers();
        }
    }

    private static void drawPerfectShaderBox(MatrixStack matrices, Box box) {
        ensureBuffers();

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        float[] vertices = {
                // front
                x1, y1, z2,  x2, y1, z2,  x2, y2, z2,   x1, y1, z2,  x2, y2, z2,  x1, y2, z2,
                // back
                x2, y1, z1,  x1, y1, z1,  x1, y2, z1,   x2, y1, z1,  x1, y2, z1,  x2, y2, z1,
                // left
                x1, y1, z1,  x1, y1, z2,  x1, y2, z2,   x1, y1, z1,  x1, y2, z2,  x1, y2, z1,
                // right
                x2, y1, z2,  x2, y1, z1,  x2, y2, z1,   x2, y1, z2,  x2, y2, z1,  x2, y2, z2,
                // top
                x1, y2, z2,  x2, y2, z2,  x2, y2, z1,   x1, y2, z2,  x2, y2, z1,  x1, y2, z1,
                // bottom
                x1, y1, z1,  x2, y1, z1,  x2, y1, z2,   x1, y1, z1,  x2, y1, z2,  x1, y1, z2
        };

        Matrix4f stackMat = matrices.peek().getPositionMatrix();
        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            vertices[i]     = stackMat.m00() * x + stackMat.m10() * y + stackMat.m20() * z + stackMat.m30();
            vertices[i + 1] = stackMat.m01() * x + stackMat.m11() * y + stackMat.m21() * z + stackMat.m31();
            vertices[i + 2] = stackMat.m02() * x + stackMat.m12() * y + stackMat.m22() * z + stackMat.m32();
        }

        int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevVBO = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        GL30.glBindVertexArray(vaoID);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);

        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);

        GL20.glDisableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        GL30.glBindVertexArray(prevVAO);
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Custom Hitboxes", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        // ОГРАНИЧЕНИЕ ПО ЦЕЛИ: рисуем хитбокс только той сущности, на которую
        // игрок реально смотрит через стандартный raycast прицела (crosshairTarget).
        // Raycast у самого Minecraft прерывается на непрозрачных блоках/стенах,
        // поэтому если между игроком и противником есть преграда — этот блок
        // просто не сработает, и хитбокс не нарисуется. Это та же логика,
        // что используется в TargetESPRenderer для подсветки цели.
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
        if (entity == mc.player || entity.isInvisible()) return;

        boolean shouldRender = false;
        if (entity instanceof PlayerEntity && LexoraGui.moduleStates.getOrDefault("HB Players", true)) shouldRender = true;
        else if (entity instanceof ItemEntity && LexoraGui.moduleStates.getOrDefault("HB Items", false)) shouldRender = true;
        else if (entity instanceof HostileEntity && LexoraGui.moduleStates.getOrDefault("HB Mobs", false)) shouldRender = true;

        if (!shouldRender) return;

        if (nebulaShader == null) nebulaShader = new ShaderUtil("nebula.vsh", "nebula.fsh");
        if (waterShader == null) waterShader = new ShaderUtil("water.vsh", "water.fsh");
        if (flameShader == null) flameShader = new ShaderUtil("flame.vsh", "flame.fsh");

        String style = LexoraGui.modeSettings.getOrDefault("Hitbox Style", "Solid");

        float[] hsv = LexoraGui.colorSettings.getOrDefault("Hitbox Color", new float[]{0f, 1f, 1f});
        int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float alpha = LexoraGui.numSettings.getOrDefault("Hitbox Alpha", 0.5f);

        Vec3d cameraPos = camera.getPos();

        matrices.push();
        try {
            double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta - cameraPos.x;
            double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta - cameraPos.y;
            double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta - cameraPos.z;

            matrices.translate(x, y, z);

            Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            // ВАЖНО: depthMask(true), а не false. С true бокс честно пишет
            // свою глубину в depth buffer, поэтому геометрия мира (стены,
            // блоки), отрисованная до этого момента кадра, нормально
            // перекрывает полупрозрачный бокс — никакого просвечивания
            // сквозь препятствия.
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            if (style.equals("Solid")) {
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                drawSolidBox(matrices, box, r, g, b, alpha);
            } else {
                ShaderUtil currentShader = nebulaShader;
                if (style.equals("Water")) currentShader = waterShader;
                else if (style.equals("Flame")) currentShader = flameShader;

                if (currentShader != null && currentShader.isValid()) {
                    int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                    int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                    int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
                    int prevVBO = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

                    currentShader.bind();

                    if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0) {
                        currentShader.setUniform3f("u_Color", r, g, b);
                        currentShader.setUniform1f("u_Alpha", alpha);
                        currentShader.setUniform1f("u_Time", (System.currentTimeMillis() % 100000L) / 1000f);
                        currentShader.setUniform2f(
                                "u_Resolution",
                                mc.getWindow().getFramebufferWidth(),
                                mc.getWindow().getFramebufferHeight()
                        );

                        currentShader.setUniformMatrix4f("u_ProjMat", RenderSystem.getProjectionMatrix());
                        currentShader.setUniformMatrix4f("u_ModelViewMat", RenderSystem.getModelViewMatrix());

                        drawPerfectShaderBox(matrices, box);
                    }

                    currentShader.unbind();

                    GL20.glUseProgram(prevProgram);
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
                    GL30.glBindVertexArray(prevVAO);
                    GL13.glActiveTexture(prevActiveTexture);

                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }
            }

            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            drawBoxOutline(matrices, box, r, g, b, 1.0f);

        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            matrices.pop();
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    private static void drawBoxOutline(MatrixStack matrices, Box box, float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);

        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawSolidBox(MatrixStack matrices, Box box, float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a); buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);

        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a); buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a); buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a); buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a); buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public static void cleanup() {
        if (nebulaShader != null) {
            nebulaShader.delete();
            nebulaShader = null;
        }
        if (waterShader != null) {
            waterShader.delete();
            waterShader = null;
        }
        if (flameShader != null) {
            flameShader.delete();
            flameShader = null;
        }

        if (vboID != -1) {
            GL15.glDeleteBuffers(vboID);
            vboID = -1;
        }
        if (vaoID != -1) {
            GL30.glDeleteVertexArrays(vaoID);
            vaoID = -1;
        }
    }
}