package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.CubeRenderer;
import com.lexoravisauls.client.utils.LexoraShaders;
import com.lexoravisauls.client.utils.ShaderUtil; // 🔥 Добавили импорт
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager; // 🔥 Добавили для погоды
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    // ПРИМЕЧАНИЕ: Если IDE ругается на "Method signature does not match",
    // просто удали "int color," из списка аргументов (в 1.21.4 его убрали).
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onDrawBlockOutline(
            net.minecraft.client.util.math.MatrixStack matrices,
            VertexConsumer vertexConsumer,
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            BlockPos pos,
            BlockState state,
            int color, // Оставлено как в твоем коде
            CallbackInfo ci
    ) {
        if (!LexoraGui.moduleStates.getOrDefault("Block Overlay", false)) {
            return;
        }

        try {
            LexoraShaders.init();
        } catch (Exception e) {
            return;
        }

        // 🔥 1. ПЕРЕКЛЮЧАТЕЛЬ ШЕЙДЕРОВ 🔥
        ShaderUtil currentShader = LexoraShaders.getCurrentShader();
        if (currentShader == null) return;

        VoxelShape shape = state.getOutlineShape(MinecraftClient.getInstance().world, pos, ShapeContext.of(entity));
        if (shape.isEmpty()) {
            ci.cancel();
            return;
        }

        ci.cancel();

        Box box = shape.getBoundingBox();
        float expand = 0.005f;

        float tx = (float)(pos.getX() + box.minX - cameraX - expand);
        float ty = (float)(pos.getY() + box.minY - cameraY - expand);
        float tz = (float)(pos.getZ() + box.minZ - cameraZ - expand);

        float sx = (float)(box.maxX - box.minX + expand * 2.0f);
        float sy = (float)(box.maxY - box.minY + expand * 2.0f);
        float sz = (float)(box.maxZ - box.minZ + expand * 2.0f);

        Matrix4f modelMatrix = new Matrix4f()
                .translate(tx, ty, tz)
                .scale(sx, sy, sz);

        Matrix4f viewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f modelView = new Matrix4f(viewMatrix).mul(modelMatrix);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

        // 🔥 Используем выбранный шейдер вместо жесткого webShader 🔥
        currentShader.bind();
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        float[] modelViewArray = new float[16];
        modelView.get(modelViewArray);
        GL20.glUniformMatrix4fv(
                GL20.glGetUniformLocation(program, "u_LexoraModelView"),
                false,
                modelViewArray
        );

        float[] projectionArray = new float[16];
        projection.get(projectionArray);
        GL20.glUniformMatrix4fv(
                GL20.glGetUniformLocation(program, "u_LexoraProj"),
                false,
                projectionArray
        );

        float[] hsv = LexoraGui.colorSettings.getOrDefault(
                "Overlay Color",
                new float[]{280f / 360f, 1f, 1f}
        );
        int colorRGB = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);

        // 🔥 2. ФИКС СКАЧКА АНИМАЦИИ (увеличили число до 10 млн) 🔥
        GL20.glUniform1f(
                GL20.glGetUniformLocation(program, "GameTime"),
                (float) (System.currentTimeMillis() % 10000000L) / 1000.0f
        );

        GL20.glUniform4f(
                GL20.glGetUniformLocation(program, "OverlayColor"),
                ((colorRGB >> 16) & 0xFF) / 255.0f,
                ((colorRGB >> 8) & 0xFF) / 255.0f,
                (colorRGB & 0xFF) / 255.0f,
                1.0f
        );

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 🔥 3. ФИКС ПРОЗРАЧНЫХ СТЕНОК (enableCull вместо disableCull) 🔥
        RenderSystem.enableCull();
        RenderSystem.depthMask(false);

        CubeRenderer.drawBox();

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        currentShader.unbind();
    }

    // ==========================================
    // 🔥 NO RENDER: УБИРАЕМ ПОГОДУ (Дождь/Снег) 🔥
    // ==========================================
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void onRenderWeather(net.minecraft.client.render.FrameGraphBuilder frameGraphBuilder, net.minecraft.util.math.Vec3d pos, float tickDelta, net.minecraft.client.render.Fog fog, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("No Render", false) && LexoraGui.moduleStates.getOrDefault("No Weather", true)) {
            ci.cancel();
        }
    }
}