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
import org.joml.Matrix4f;

import java.awt.Color;

public class ChinaHatFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEX = Identifier.of("minecraft", "textures/misc/white.png");

    public static boolean isRenderingLocalPlayer = false;

    private static final int SEGMENTS = 96;
    private static final int STACKS = 22;

    private static final String SIZE_KEY = "China Hat Size";
    private static final String COLOR_SOURCE_KEY = "China Hat Color Source";
    private static final String CUSTOM_COLOR_MODE_KEY = "China Hat Custom Color Mode";
    private static final String COLOR_1_KEY = "China Hat Color 1";
    private static final String COLOR_2_KEY = "China Hat Color 2";

    private static final float Y_OFFSET = -0.52f;
    private static final float TOP_RADIUS = 0.012f;
    private static final float BASE_RADIUS = 0.72f;
    private static final float TOP_Y = -0.20f;
    private static final float BASE_Y = 0.00f;

    public ChinaHatFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        ensureDefaults();
    }

    @Override
    public void render(MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       PlayerEntityRenderState state,
                       float limbAngle,
                       float limbDistance) {

        if (!LexoraGui.moduleStates.getOrDefault("China Hat", false)) return;
        if (!isRenderingLocalPlayer) return;

        ensureDefaults();

        matrices.push();
        this.getContextModel().getHead().rotate(matrices);
        matrices.translate(0.0f, Y_OFFSET, 0.0f);

        float size = clamp(LexoraGui.numSettings.getOrDefault(SIZE_KEY, 1.0f), 0.45f, 2.20f);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX));
        int fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        boolean customSource = LexoraGui.modeSettings
                .getOrDefault(COLOR_SOURCE_KEY, "Client")
                .equals("Custom");

        boolean gradient = customSource
                ? LexoraGui.modeSettings.getOrDefault(CUSTOM_COLOR_MODE_KEY, "Single").equals("Gradient")
                : LexoraGui.moduleStates.getOrDefault("Gradient Theme", true);

        int color1 = customSource
                ? getColor(COLOR_1_KEY, new float[]{300f / 360f, 0.75f, 1.00f})
                : getColor("Theme Color 1", new float[]{300f / 360f, 0.75f, 1.00f});

        int color2 = gradient
                ? (customSource
                ? getColor(COLOR_2_KEY, new float[]{190f / 360f, 0.70f, 1.00f})
                : getColor("Theme Color 2", new float[]{190f / 360f, 0.70f, 1.00f}))
                : color1;

        int soft1 = softenColor(color1, 0.12f);
        int soft2 = softenColor(color2, 0.12f);

        drawConeShell(mat, buffer, fullLight, soft1, soft2, size);

        matrices.pop();
    }

    private void drawConeShell(Matrix4f mat, VertexConsumer buffer, int light, int color1, int color2, float size) {
        float scaledTopRadius = TOP_RADIUS * size;
        float scaledBaseRadius = BASE_RADIUS * size;
        float scaledTopY = TOP_Y * size;
        float scaledBaseY = BASE_Y;

        for (int stack = 0; stack < STACKS; stack++) {
            float t0 = stack / (float) STACKS;
            float t1 = (stack + 1) / (float) STACKS;

            float y0 = lerp(scaledTopY, scaledBaseY, t0);
            float y1 = lerp(scaledTopY, scaledBaseY, t1);

            float r0 = lerp(scaledTopRadius, scaledBaseRadius, t0);
            float r1 = lerp(scaledTopRadius, scaledBaseRadius, t1);

            for (int seg = 0; seg < SEGMENTS; seg++) {
                float a0 = (float) (seg * Math.PI * 2.0 / SEGMENTS);
                float a1 = (float) ((seg + 1) * Math.PI * 2.0 / SEGMENTS);

                float x00 = (float) Math.cos(a0) * r0;
                float z00 = (float) Math.sin(a0) * r0;

                float x01 = (float) Math.cos(a1) * r0;
                float z01 = (float) Math.sin(a1) * r0;

                float x10 = (float) Math.cos(a0) * r1;
                float z10 = (float) Math.sin(a0) * r1;

                float x11 = (float) Math.cos(a1) * r1;
                float z11 = (float) Math.sin(a1) * r1;

                int c0 = ringColor(color1, color2, a0);
                int c1 = ringColor(color1, color2, a1);

                put(buffer, mat, x00, y0, z00, c0, light);
                put(buffer, mat, x10, y1, z10, c0, light);
                put(buffer, mat, x11, y1, z11, c1, light);
                put(buffer, mat, x01, y0, z01, c1, light);
            }
        }
    }

    private void put(VertexConsumer buffer, Matrix4f mat, float x, float y, float z, int rgb, int light) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        buffer.vertex(mat, x, y, z)
                .color(r, g, b, 255)
                .texture(0.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0.0f, 1.0f, 0.0f);
    }

    private int getColor(String key, float[] def) {
        float[] hsv = LexoraGui.colorSettings.getOrDefault(key, def);
        return Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
    }

    private int ringColor(int color1, int color2, float angle) {
        float t = (float) ((Math.sin(angle) + 1.0) * 0.5);
        return blend(color1, color2, t);
    }

    private int softenColor(int color, float amountToWhite) {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;

        r = (int) (r + (255 - r) * amountToWhite);
        g = (int) (g + (255 - g) * amountToWhite);
        b = (int) (b + (255 - b) * amountToWhite);

        return (r << 16) | (g << 8) | b;
    }

    private int blend(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));

        int r1 = (from >> 16) & 255;
        int g1 = (from >> 8) & 255;
        int b1 = from & 255;

        int r2 = (to >> 16) & 255;
        int g2 = (to >> 8) & 255;
        int b2 = to & 255;

        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (r << 16) | (g << 8) | b;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void ensureDefaults() {
        if (!LexoraGui.numSettings.containsKey(SIZE_KEY)) {
            LexoraGui.numSettings.put(SIZE_KEY, 1.0f);
        }

        if (!LexoraGui.modeSettings.containsKey(COLOR_SOURCE_KEY)) {
            LexoraGui.modeSettings.put(COLOR_SOURCE_KEY, "Client");
        }

        if (!LexoraGui.modeSettings.containsKey(CUSTOM_COLOR_MODE_KEY)) {
            LexoraGui.modeSettings.put(CUSTOM_COLOR_MODE_KEY, "Single");
        }

        if (!LexoraGui.colorSettings.containsKey(COLOR_1_KEY)) {
            LexoraGui.colorSettings.put(COLOR_1_KEY, new float[]{300f / 360f, 0.75f, 1.00f});
        }

        if (!LexoraGui.colorSettings.containsKey(COLOR_2_KEY)) {
            LexoraGui.colorSettings.put(COLOR_2_KEY, new float[]{190f / 360f, 0.70f, 1.00f});
        }
    }
}