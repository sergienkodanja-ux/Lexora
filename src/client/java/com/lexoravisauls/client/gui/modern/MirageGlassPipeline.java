package com.lexoravisauls.client.gui.modern;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

public class MirageGlassPipeline {

    public static void draw(Matrix4f matrix, float x, float y, float width, float height, float radius, float blur, int tintColor, float alpha) {
        if (alpha <= 0.01f || width <= 0.5f || height <= 0.5f || !ScreenCaptureManager.hasCapture()) return;

        ShaderProgram shader = MirageGlassShader.program;
        if (shader == null) return;

        float scaleX = matrix.m00();
        float scaleY = matrix.m11();
        float tx = x * scaleX + matrix.m30();
        float ty = y * scaleY + matrix.m31();
        float tw = width * scaleX;
        float th = height * scaleY;
        float tr = radius * scaleX;

        float red = ((tintColor >> 16) & 0xFF) / 255.0f;
        float green = ((tintColor >> 8) & 0xFF) / 255.0f;
        float blue = (tintColor & 0xFF) / 255.0f;
        float tintAlpha = ((tintColor >> 24) & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        RenderSystem.setShader(shader);

        float targetBlur = blur <= 0.1f ? 15.0f : blur;
        float currentBlur = Math.max(0.001f, targetBlur * alpha);

        if (shader.getUniform("u_rect") != null) shader.getUniform("u_rect").set(tx, ty, tw, th);
        if (shader.getUniform("u_radius") != null) shader.getUniform("u_radius").set(tr);
        if (shader.getUniform("u_blur") != null) shader.getUniform("u_blur").set(currentBlur);
        if (shader.getUniform("u_tint") != null) shader.getUniform("u_tint").set(red, green, blue, tintAlpha * alpha);
        if (shader.getUniform("u_alpha") != null) shader.getUniform("u_alpha").set(alpha);

        // 🔥 ГЛАВНЫЙ ФИКС РАЗНОЦВЕТНОГО БЕСПРЕДЕЛА 🔥
        // Используем ТОЛЬКО этот метод. Он записывает наш FBO скриншот в слот 0 внутреннего кэша Blaze3D.
        // Теперь shader.bind() внутри BufferRenderer возьмет именно скриншот, а не атлас предметов.
        RenderSystem.setShaderTexture(0, ScreenCaptureManager.getTexture());

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(matrix, x, y + height, 0);
        buffer.vertex(matrix, x + width, y + height, 0);
        buffer.vertex(matrix, x + width, y, 0);
        buffer.vertex(matrix, x, y, 0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // --- ОЧИСТКА ---
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);

        // ФИКС: Возвращаем блендинг в рабочее состояние, чтобы текст и иконки не ломались
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(36008, 0);
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
    }
}