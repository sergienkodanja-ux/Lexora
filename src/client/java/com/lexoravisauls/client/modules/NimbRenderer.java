package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.ChinaHatFeatureRenderer;
import net.minecraft.client.MinecraftClient;
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

public class NimbRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEX = Identifier.of("minecraft", "textures/misc/white.png");

    private static final String FIRST_PERSON_KEY = "Nimb Show 1st Person";
    private static final String RADIUS_KEY = "Nimb Radius";

    private static final String COLOR_SOURCE_KEY = "Nimb Color Source";
    private static final String CUSTOM_COLOR_MODE_KEY = "Nimb Custom Color Mode";
    private static final String COLOR_1_KEY = "Nimb Color 1";
    private static final String COLOR_2_KEY = "Nimb Color 2";

    public NimbRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
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
        if (!LexoraGui.moduleStates.getOrDefault("Nimb", false)) return;
        if (!ChinaHatFeatureRenderer.isRenderingLocalPlayer) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();
        boolean isGuiOpen = mc.currentScreen != null;
        boolean showInFirstPerson = LexoraGui.moduleStates.getOrDefault(FIRST_PERSON_KEY, false);

        if (isFirstPerson && !isGuiOpen && !showInFirstPerson) return;

        ensureDefaults();

        matrices.push();
        this.getContextModel().getHead().rotate(matrices);

        float radius = clamp(LexoraGui.numSettings.getOrDefault(RADIUS_KEY, 0.4f), 0.2f, 1.5f);
        float thick = 0.025f;
        float h = 0.02f;

        float floatAnim = (float) Math.sin(System.currentTimeMillis() / 400.0) * 0.04f;
        matrices.translate(0.0, -0.75 - floatAnim, 0.0);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEX));
        int brightLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

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

        int points = 36;
        for (int i = 0; i < points; i++) {
            float rad1 = (float) (i * Math.PI * 2 / points);
            float rad2 = (float) ((i + 1) * Math.PI * 2 / points);

            float c1 = MathHelper.cos(rad1);
            float s1 = MathHelper.sin(rad1);
            float c2 = MathHelper.cos(rad2);
            float s2 = MathHelper.sin(rad2);

            float x1Out = c1 * (radius + thick);
            float z1Out = s1 * (radius + thick);
            float x1In = c1 * (radius - thick);
            float z1In = s1 * (radius - thick);

            float x2Out = c2 * (radius + thick);
            float z2Out = s2 * (radius + thick);
            float x2In = c2 * (radius - thick);
            float z2In = s2 * (radius - thick);

            int top1 = ringColor(color1, color2, rad1);
            int top2 = ringColor(color1, color2, rad2);

            int topR1 = (top1 >> 16) & 0xFF;
            int topG1 = (top1 >> 8) & 0xFF;
            int topB1 = top1 & 0xFF;

            int topR2 = (top2 >> 16) & 0xFF;
            int topG2 = (top2 >> 8) & 0xFF;
            int topB2 = top2 & 0xFF;

            int bottom1 = darken(top1, 0.50f);
            int bottom2 = darken(top2, 0.50f);

            int bottomR1 = (bottom1 >> 16) & 0xFF;
            int bottomG1 = (bottom1 >> 8) & 0xFF;
            int bottomB1 = bottom1 & 0xFF;

            int bottomR2 = (bottom2 >> 16) & 0xFF;
            int bottomG2 = (bottom2 >> 8) & 0xFF;
            int bottomB2 = bottom2 & 0xFF;

            int outer1 = darken(top1, 0.80f);
            int outer2 = darken(top2, 0.80f);

            int outerR1 = (outer1 >> 16) & 0xFF;
            int outerG1 = (outer1 >> 8) & 0xFF;
            int outerB1 = outer1 & 0xFF;

            int outerR2 = (outer2 >> 16) & 0xFF;
            int outerG2 = (outer2 >> 8) & 0xFF;
            int outerB2 = outer2 & 0xFF;

            int inner1 = darken(top1, 0.60f);
            int inner2 = darken(top2, 0.60f);

            int innerR1 = (inner1 >> 16) & 0xFF;
            int innerG1 = (inner1 >> 8) & 0xFF;
            int innerB1 = inner1 & 0xFF;

            int innerR2 = (inner2 >> 16) & 0xFF;
            int innerG2 = (inner2 >> 8) & 0xFF;
            int innerB2 = inner2 & 0xFF;

            addQuad(
                    buffer, mat,
                    x1In,  h, z1In,
                    x1Out, h, z1Out,
                    x2Out, h, z2Out,
                    x2In,  h, z2In,
                    topR1, topG1, topB1,
                    topR1, topG1, topB1,
                    topR2, topG2, topB2,
                    topR2, topG2, topB2,
                    255, brightLight,
                    0, 1, 0
            );

            addQuad(
                    buffer, mat,
                    x1In,  -h, z1In,
                    x2In,  -h, z2In,
                    x2Out, -h, z2Out,
                    x1Out, -h, z1Out,
                    bottomR1, bottomG1, bottomB1,
                    bottomR2, bottomG2, bottomB2,
                    bottomR2, bottomG2, bottomB2,
                    bottomR1, bottomG1, bottomB1,
                    255, brightLight,
                    0, -1, 0
            );

            addQuad(
                    buffer, mat,
                    x1Out,  h, z1Out,
                    x1Out, -h, z1Out,
                    x2Out, -h, z2Out,
                    x2Out,  h, z2Out,
                    outerR1, outerG1, outerB1,
                    outerR1, outerG1, outerB1,
                    outerR2, outerG2, outerB2,
                    outerR2, outerG2, outerB2,
                    255, brightLight,
                    c1, 0, s1
            );

            addQuad(
                    buffer, mat,
                    x1In,  h, z1In,
                    x2In,  h, z2In,
                    x2In, -h, z2In,
                    x1In, -h, z1In,
                    innerR1, innerG1, innerB1,
                    innerR2, innerG2, innerB2,
                    innerR2, innerG2, innerB2,
                    innerR1, innerG1, innerB1,
                    255, brightLight,
                    -c1, 0, -s1
            );
        }

        matrices.pop();
    }

    private void addQuad(VertexConsumer buffer,
                         Matrix4f mat,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float x4, float y4, float z4,
                         int r1, int g1, int b1,
                         int r2, int g2, int b2,
                         int r3, int g3, int b3,
                         int r4, int g4, int b4,
                         int a,
                         int light,
                         float nx, float ny, float nz) {
        buffer.vertex(mat, x1, y1, z1).color(r1, g1, b1, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        buffer.vertex(mat, x2, y2, z2).color(r2, g2, b2, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        buffer.vertex(mat, x3, y3, z3).color(r3, g3, b3, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        buffer.vertex(mat, x4, y4, z4).color(r4, g4, b4, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
    }

    private int getColor(String key, float[] def) {
        float[] hsv = LexoraGui.colorSettings.getOrDefault(key, def);
        return Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
    }

    private int ringColor(int color1, int color2, float angle) {
        float t = (float) ((Math.sin(angle) + 1.0) * 0.5);
        return blend(color1, color2, t);
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

    private int darken(int color, float factor) {
        int r = Math.max(0, Math.min(255, (int) (((color >> 16) & 255) * factor)));
        int g = Math.max(0, Math.min(255, (int) (((color >> 8) & 255) * factor)));
        int b = Math.max(0, Math.min(255, (int) ((color & 255) * factor)));
        return (r << 16) | (g << 8) | b;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void ensureDefaults() {
        if (!LexoraGui.numSettings.containsKey(RADIUS_KEY)) {
            LexoraGui.numSettings.put(RADIUS_KEY, 0.4f);
        }

        if (!LexoraGui.moduleStates.containsKey(FIRST_PERSON_KEY)) {
            LexoraGui.moduleStates.put(FIRST_PERSON_KEY, false);
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