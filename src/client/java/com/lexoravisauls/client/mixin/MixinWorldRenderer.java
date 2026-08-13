package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.CubeRenderer;
import com.lexoravisauls.client.utils.LexoraShaders;
import com.lexoravisauls.client.utils.LexoraSkyRenderer;
import com.lexoravisauls.client.utils.ShaderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    // Инициализируем наш отдельный независимый рендер при запуске!
    static {
        LexoraSkyRenderer.ensureRegistered();
    }

    private static double animX, animY, animZ;
    private static double animW, animH, animD;
    private static long lastTime = System.currentTimeMillis();
    private static BlockPos lastPos = null;

    // =====================================================
    // 🟦 BLOCK OVERLAY
    // =====================================================
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onDrawBlockOutline(
            net.minecraft.client.util.math.MatrixStack matrices,
            VertexConsumer vertexConsumer,
            Entity entity,
            double cameraX, double cameraY, double cameraZ,
            BlockPos pos,
            BlockState state,
            int color,
            CallbackInfo ci
    ) {
        if (!LexoraGui.moduleStates.getOrDefault("Block Overlay", false)) return;

        VoxelShape shape = state.getOutlineShape(
                MinecraftClient.getInstance().world, pos, ShapeContext.of(entity));
        if (shape.isEmpty()) { ci.cancel(); return; }

        ci.cancel();

        Box box = shape.getBoundingBox();

        double targetX = pos.getX() + box.minX;
        double targetY = pos.getY() + box.minY;
        double targetZ = pos.getZ() + box.minZ;
        double targetW = box.maxX - box.minX;
        double targetH = box.maxY - box.minY;
        double targetD = box.maxZ - box.minZ;

        long now = System.currentTimeMillis();
        double dt = Math.min((now - lastTime) / 1000.0, 0.05);
        lastTime = now;

        if (lastPos == null || pos.getSquaredDistance(lastPos) > 10) {
            animX = targetX; animY = targetY; animZ = targetZ;
            animW = targetW; animH = targetH; animD = targetD;
        } else {
            double factor = Math.max(0.0, Math.min(1.0, 25.0 * dt));
            animX += (targetX - animX) * factor;
            animY += (targetY - animY) * factor;
            animZ += (targetZ - animZ) * factor;
            animW += (targetW - animW) * factor;
            animH += (targetH - animH) * factor;
            animD += (targetD - animD) * factor;
        }
        lastPos = pos;

        float expand = 0.005f;
        float tx = (float)(animX - cameraX - expand);
        float ty = (float)(animY - cameraY - expand);
        float tz = (float)(animZ - cameraZ - expand);
        float sx = (float)(animW + expand * 2f);
        float sy = (float)(animH + expand * 2f);
        float sz = (float)(animD + expand * 2f);

        String mode = "Solid";
        boolean customColor = false;
        float opacity = 0.5f;

        try {
            if (LexoraGui.modeSettings != null) {
                mode = String.valueOf(LexoraGui.modeSettings.getOrDefault("Overlay Mode", "Solid"));
                customColor = String.valueOf(
                                LexoraGui.modeSettings.getOrDefault("Overlay Color Source", "Client"))
                        .equals("Custom");
            }
            if (LexoraGui.numSettings != null && LexoraGui.numSettings.containsKey("Overlay Opacity")) {
                opacity = Float.parseFloat(LexoraGui.numSettings.get("Overlay Opacity").toString());
            }
        } catch (Exception ignored) {}

        opacity = Math.max(0f, Math.min(1f, opacity));

        float[] hsv = customColor
                ? LexoraGui.colorSettings.getOrDefault("Overlay Custom Color",
                new float[]{300f/360f, 0.75f, 1f})
                : LexoraGui.colorSettings.getOrDefault("Theme Color 1",
                new float[]{300f/360f, 0.75f, 1f});

        int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8)  & 0xFF) / 255f;
        float b = (rgb & 0xFF)          / 255f;

        if ("Solid".equals(mode)) {
            int rI = (int)(r*255), gI = (int)(g*255), bI = (int)(b*255), aI = (int)(opacity*255);
            int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);

            VertexConsumerProvider.Immediate immediate =
                    MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            Identifier tex = Identifier.of("minecraft", "textures/misc/white.png");
            VertexConsumer buffer = immediate.getBuffer(RenderLayer.getEntityTranslucent(tex));

            matrices.push();
            matrices.translate(tx, ty, tz);
            matrices.scale(sx, sy, sz);
            Matrix4f mat = matrices.peek().getPositionMatrix();

            addSolidVertex(buffer,mat,0,0,0,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,0,0,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,0,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,0,0,1,rI,gI,bI,aI,light);
            addSolidVertex(buffer,mat,0,1,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,1,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,1,0,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,0,1,0,rI,gI,bI,aI,light);
            addSolidVertex(buffer,mat,0,1,0,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,1,0,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,0,0,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,0,0,0,rI,gI,bI,aI,light);
            addSolidVertex(buffer,mat,0,0,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,0,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,1,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,0,1,1,rI,gI,bI,aI,light);
            addSolidVertex(buffer,mat,0,0,0,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,0,0,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,0,1,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,0,1,0,rI,gI,bI,aI,light);
            addSolidVertex(buffer,mat,1,1,0,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,1,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,0,1,rI,gI,bI,aI,light);addSolidVertex(buffer,mat,1,0,0,rI,gI,bI,aI,light);

            matrices.pop();
            immediate.draw();

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();

        } else {
            try { LexoraShaders.init(); } catch (Exception e) { return; }
            ShaderUtil currentShader = LexoraShaders.getCurrentShader();
            if (currentShader == null) return;

            Matrix4f modelMatrix = new Matrix4f().translate(tx, ty, tz).scale(sx, sy, sz);
            Matrix4f viewMatrix  = new Matrix4f(RenderSystem.getModelViewMatrix());
            Matrix4f modelView   = new Matrix4f(viewMatrix).mul(modelMatrix);
            Matrix4f projection  = new Matrix4f(RenderSystem.getProjectionMatrix());

            currentShader.bind();
            currentShader.setUniformMatrix4f("u_LexoraModelView", modelView);
            currentShader.setUniformMatrix4f("u_LexoraProj", projection);
            currentShader.setUniform1f("GameTime", (float)(System.currentTimeMillis() % 10000000L) / 1000.0f);
            currentShader.setUniform4f("OverlayColor", r, g, b, opacity);
            currentShader.setUniform1f("Alpha", opacity);

            RenderSystem.enableBlend();
            GL14.glBlendColor(0f, 0f, 0f, opacity);
            RenderSystem.blendFunc(GL14.GL_CONSTANT_ALPHA, GL14.GL_ONE_MINUS_CONSTANT_ALPHA);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.enableCull();
            RenderSystem.depthMask(false);

            CubeRenderer.drawBox();

            RenderSystem.defaultBlendFunc();
            GL14.glBlendColor(0f, 0f, 0f, 0f);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            currentShader.unbind();
        }
    }

    private void addSolidVertex(VertexConsumer buf, Matrix4f mat,
                                float x, float y, float z,
                                int r, int g, int b, int a, int light) {
        buf.vertex(mat, x, y, z).color(r, g, b, a)
                .texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(0, 1, 0);
    }

    // =====================================================
    // 🌧 NO RENDER: ПОГОДА
    // =====================================================
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void onRenderWeather(
            FrameGraphBuilder frameGraphBuilder,
            net.minecraft.util.math.Vec3d pos,
            float tickDelta,
            Fog fog,
            CallbackInfo ci
    ) {
        if (LexoraGui.moduleStates.getOrDefault("No Render", false)
                && LexoraGui.moduleStates.getOrDefault("No Weather", true)) {
            ci.cancel();
        }
    }
}