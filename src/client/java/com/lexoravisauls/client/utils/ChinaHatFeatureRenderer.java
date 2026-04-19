package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.awt.Color;

public class ChinaHatFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEX = Identifier.of("minecraft", "textures/misc/white.png");


    public static boolean isRenderingLocalPlayer = false;

    public ChinaHatFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!LexoraGui.moduleStates.getOrDefault("China Hat", false)) return;

        // --- ГЛАВНЫЙ ФИКС: РИСУЕМ ТОЛЬКО ЕСЛИ ЭТО ТЫ ---
        if (!isRenderingLocalPlayer) return;

        matrices.push();
        this.getContextModel().getHead().rotate(matrices);

        int points = 40;
        float radius = 0.58f;
        float height = -0.18f;

        matrices.translate(0.0, -0.55, 0.0);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEX));
        int brightLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        boolean useGradient = LexoraGui.moduleStates.getOrDefault("Gradient Theme", true);
        float[] hsv1 = LexoraGui.colorSettings.getOrDefault("Theme Color 1", new float[]{200f / 360f, 1f, 1f});

        int r1, g1, b1, r2, g2, b2;
        int color1 = Color.HSBtoRGB(hsv1[0], hsv1[1], hsv1[2]);
        r1 = (color1 >> 16) & 0xFF; g1 = (color1 >> 8) & 0xFF; b1 = color1 & 0xFF;

        if (useGradient) {
            float[] hsv2 = LexoraGui.colorSettings.getOrDefault("Theme Color 2", new float[]{280f / 360f, 1f, 1f});
            int color2 = Color.HSBtoRGB(hsv2[0], hsv2[1], hsv2[2]);
            r2 = (color2 >> 16) & 0xFF; g2 = (color2 >> 8) & 0xFF; b2 = color2 & 0xFF;
        } else {
            r2 = r1; g2 = g1; b2 = b1;
        }

        float[] xs = new float[points + 1];
        float[] zs = new float[points + 1];
        for (int i = 0; i < points; i++) {
            float angle = (float) (i * Math.PI * 2 / points);
            xs[i] = MathHelper.cos(angle) * radius;
            zs[i] = MathHelper.sin(angle) * radius;
        }
        xs[points] = xs[0];
        zs[points] = zs[0];

        for (int i = 0; i < points; i++) {
            float x1 = xs[i], z1 = zs[i], x2 = xs[i + 1], z2 = zs[i + 1];

            buffer.vertex(mat, 0, height, 0).color(r1, g1, b1, 255).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(brightLight).normal(0, 1, 0);
            buffer.vertex(mat, x1, 0, z1).color(r2, g2, b2, 80).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(brightLight).normal(0, 1, 0);
            buffer.vertex(mat, x2, 0, z2).color(r2, g2, b2, 80).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(brightLight).normal(0, 1, 0);
            buffer.vertex(mat, 0, height, 0).color(r1, g1, b1, 255).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(brightLight).normal(0, 1, 0);

            buffer.vertex(mat, 0, height, 0).color(r1, g1, b1, 255).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(brightLight).normal(0, -1, 0);
            buffer.vertex(mat, x2, 0, z2).color(r2, g2, b2, 80).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(brightLight).normal(0, -1, 0);
            buffer.vertex(mat, x1, 0, z1).color(r2, g2, b2, 80).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(brightLight).normal(0, -1, 0);
            buffer.vertex(mat, 0, height, 0).color(r1, g1, b1, 255).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(brightLight).normal(0, -1, 0);
        }
        matrices.pop();
    }
}