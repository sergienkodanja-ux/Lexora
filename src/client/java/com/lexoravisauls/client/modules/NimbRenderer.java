package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
// Импортируем твою ChinaHat, чтобы брать оттуда рабочий флаг
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

    // Текстура точь-в-точь как в твоей China Hat
    private static final Identifier TEX = Identifier.of("minecraft", "textures/misc/white.png");

    public NimbRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        // 1. Проверяем, включен ли Нимб в меню
        if (!LexoraGui.moduleStates.getOrDefault("Nimb", false)) return;

        // 2. 🔥 ГЛАВНЫЙ ФИКС: Читаем флаг из ChinaHat!
        // Раз шляпа работает только на тебе, значит и нимб будет работать только на тебе!
        if (!ChinaHatFeatureRenderer.isRenderingLocalPlayer) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        // 3. Проверка на 1-е лицо и инвентарь
        boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();
        boolean isGuiOpen = mc.currentScreen != null;
        boolean showInFirstPerson = LexoraGui.moduleStates.getOrDefault("Nimb Show 1st Person", false);

        if (isFirstPerson && !isGuiOpen && !showInFirstPerson) return;

        matrices.push();

        // Привязываем к вращению головы
        this.getContextModel().getHead().rotate(matrices);

        float radius = LexoraGui.numSettings.getOrDefault("Nimb Radius", 0.4f);
        float thick = 0.025f; // Толщина стенок
        float h = 0.02f;      // Высота

        // Левитация
        float floatAnim = (float) Math.sin(System.currentTimeMillis() / 400.0) * 0.04f;
        matrices.translate(0.0, -0.75 - floatAnim, 0.0);

        Matrix4f mat = matrices.peek().getPositionMatrix();

        // Слой точно как в China Hat
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEX));
        int brightLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        // Цвета
        boolean isCustom = LexoraGui.modeSettings.getOrDefault("Nimb Color Mode", "Theme").equals("Custom");
        int rgb = isCustom ?
                Color.HSBtoRGB(LexoraGui.colorSettings.getOrDefault("Nimb Custom Color", new float[]{0f, 1f, 1f})[0],
                        LexoraGui.colorSettings.getOrDefault("Nimb Custom Color", new float[]{0f, 1f, 1f})[1],
                        LexoraGui.colorSettings.getOrDefault("Nimb Custom Color", new float[]{0f, 1f, 1f})[2]) :
                LexoraGui.getGuiThemeColor();

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Строим обруч
        int points = 36;
        for (int i = 0; i < points; i++) {
            float rad1 = (float) (i * Math.PI * 2 / points);
            float rad2 = (float) ((i + 1) * Math.PI * 2 / points);

            float c1 = MathHelper.cos(rad1); float s1 = MathHelper.sin(rad1);
            float c2 = MathHelper.cos(rad2); float s2 = MathHelper.sin(rad2);

            float x1_out = c1 * (radius + thick); float z1_out = s1 * (radius + thick);
            float x1_in  = c1 * (radius - thick); float z1_in  = s1 * (radius - thick);
            float x2_out = c2 * (radius + thick); float z2_out = s2 * (radius + thick);
            float x2_in  = c2 * (radius - thick); float z2_in  = s2 * (radius - thick);

            // Верх
            addQuad(buffer, mat, x1_in, h, z1_in, x1_out, h, z1_out, x2_out, h, z2_out, x2_in, h, z2_in, r, g, b, 255, brightLight, 0, 1, 0);
            // Низ
            addQuad(buffer, mat, x1_in, -h, z1_in, x2_in, -h, z2_in, x2_out, -h, z2_out, x1_out, -h, z1_out, (int)(r*0.5), (int)(g*0.5), (int)(b*0.5), 255, brightLight, 0, -1, 0);
            // Внешняя стенка
            addQuad(buffer, mat, x1_out, h, z1_out, x1_out, -h, z1_out, x2_out, -h, z2_out, x2_out, h, z2_out, (int)(r*0.8), (int)(g*0.8), (int)(b*0.8), 255, brightLight, c1, 0, s1);
            // Внутренняя стенка
            addQuad(buffer, mat, x1_in, h, z1_in, x2_in, h, z2_in, x2_in, -h, z2_in, x1_in, -h, z1_in, (int)(r*0.6), (int)(g*0.6), (int)(b*0.6), 255, brightLight, -c1, 0, -s1);
        }
        matrices.pop();
    }

    private void addQuad(VertexConsumer buffer, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        buffer.vertex(mat, x3, y3, z3).color(r, g, b, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        buffer.vertex(mat, x4, y4, z4).color(r, g, b, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
    }
}