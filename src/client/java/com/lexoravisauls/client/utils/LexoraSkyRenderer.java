package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

public class LexoraSkyRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("LexoraSkyRenderer");
    private static long lastDiagLog = 0L;

    private static boolean registered = false;
    private static int vaoID = -1;
    private static int vboID = -1;

    public static void ensureRegistered() {
        if (!registered) {
            // Меняем LAST на AFTER_SETUP.
            // Теперь небо будет рисоваться сразу после настройки камеры и ванильного скайбокса,
            // но ДО рендера мира, энтити и любых ESP/Предиктов.
            // Это гарантирует, что оно навсегда останется на заднем фоне.
            WorldRenderEvents.AFTER_SETUP.register(LexoraSkyRenderer::renderSkyEvent);
            registered = true;
        }
    }

    private static boolean isEnabled(String key, boolean fallback) {
        if (LexoraGui.moduleStates.containsKey(key)) return LexoraGui.moduleStates.get(key);
        return ClientData.moduleStates.getOrDefault(key, fallback);
    }

    private static float getNum(String key, float fallback) {
        if (LexoraGui.numSettings.containsKey(key)) return LexoraGui.numSettings.get(key);
        return ClientData.numSettings.getOrDefault(key, fallback);
    }

    private static String getMode(String key, String fallback) {
        if (LexoraGui.modeSettings.containsKey(key)) return LexoraGui.modeSettings.get(key);
        return ClientData.modeSettings.getOrDefault(key, fallback);
    }

    private static float[] getColor(String key, float[] fallback) {
        if (LexoraGui.colorSettings.containsKey(key)) return LexoraGui.colorSettings.get(key);
        return ClientData.colorSettings.getOrDefault(key, fallback);
    }

    private static void setupQuad() {
        if (vaoID != -1) return;

        vaoID = GL30.glGenVertexArrays();
        vboID = GL15.glGenBuffers();

        float[] vertices = {
                -1.0f, -1.0f, 0.9999f,
                1.0f, -1.0f, 0.9999f,
                -1.0f,  1.0f, 0.9999f,
                1.0f,  1.0f, 0.9999f
        };

        GL30.glBindVertexArray(vaoID);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * 4, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private static void renderSkyEvent(WorldRenderContext context) {
        boolean worldCustomizerEnabled = isEnabled("World Customizer", false);
        boolean skyMod = isEnabled("Sky Customizer", false);
        String skyType = getMode("Sky Type", "Standard");

        long now = System.currentTimeMillis();
        boolean shouldLog = now - lastDiagLog > 1000L;
        if (shouldLog) {
            lastDiagLog = now;
            LOGGER.info("[LexoraSky-DIAG] LAST event fired. worldCustomizerEnabled={} skyMod={} skyType='{}'",
                    worldCustomizerEnabled, skyMod, skyType);
        }

        if (!worldCustomizerEnabled || !skyMod || skyType.equals("Standard")) {
            if (shouldLog) LOGGER.info("[LexoraSky-DIAG] early return: condition failed");
            return;
        }

        String shaderName = switch (skyType) {
            case "Water"   -> "water";
            case "Caustic" -> "caustic";
            case "Thunder" -> "thunder";
            case "Pulsar"  -> "pulsar";
            default -> null;
        };

        if (shaderName == null) return;

        LexoraShaders.initSky();
        ShaderUtil shader = LexoraShaders.getShader(shaderName);

        if (shader == null || !shader.isValid()) {
            if (shouldLog) LOGGER.info("[LexoraSky-DIAG] shader is {} for '{}'",
                    shader == null ? "NULL" : "INVALID", shaderName);
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float sw = (float) mc.getWindow().getFramebufferWidth();
        float sh = (float) mc.getWindow().getFramebufferHeight();

        Camera camera = context.camera();
        float yawRad = (float) Math.toRadians(-camera.getYaw());
        float pitchRad = (float) Math.toRadians(camera.getPitch());
        float fov = (float) mc.options.getFov().getValue().intValue();

        // 🔥 СОХРАНЯЕМ ТЕКУЩИЕ СОСТОЯНИЯ OPENGL (Главный фикс бага с рукой!) 🔥
        // Это предотвратит рассинхронизацию кэша RenderSystem и спасет рендер партиклов/руки при критах.
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevVBO = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevEBO = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        // ВАЖНО: Используем GL_LEQUAL. Так как Z у квада = 0.9999,
        // небо отрисуется ровно там, где нет блоков (у пустого неба depth = 1.0).
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);

        shader.bind();
        shader.setUniform1f("uTime", (float)(System.currentTimeMillis() % 10000000L) / 1000.0f);
        shader.setUniform2f("uResolution", sw, sh);
        shader.setUniform2f("uCameraDir", yawRad, pitchRad);
        shader.setUniform1f("uFov", fov);

        float speed = getNum(skyType + " Speed", 1.0f);
        float scale = getNum(skyType + " Scale", 5.0f);
        float intensity = getNum(skyType + " Intensity", 0.01f);
        float alpha = getNum(skyType + " Alpha", 1.0f);

        shader.setUniform1f("uSpeed", speed);
        shader.setUniform1f("uScale", scale);
        shader.setUniform1f("uIntensity", intensity);
        shader.setUniform1f("uAlpha", alpha);

        boolean useCustomColor = getMode(skyType + " Color Mode", "Theme").equals("Custom");
        float cr, cg, cb;
        if (useCustomColor) {
            float[] hsv = getColor(skyType + " Custom Color", new float[]{300f / 360f, 0.75f, 1f});
            int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
            cr = ((rgb >> 16) & 0xFF) / 255f;
            cg = ((rgb >> 8) & 0xFF) / 255f;
            cb = (rgb & 0xFF) / 255f;
        } else {
            float[] hsv = getColor("Theme Color 1", new float[]{300f / 360f, 0.75f, 1f});
            int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
            cr = ((rgb >> 16) & 0xFF) / 255f;
            cg = ((rgb >> 8) & 0xFF) / 255f;
            cb = (rgb & 0xFF) / 255f;
        }
        shader.setUniform3f("uColor", cr, cg, cb);

        if (skyType.equals("Thunder")) {
            shader.setUniform1f("uThunderInterval", getNum("Thunder Interval", 4.0f));
            shader.setUniform1f("uThunderChance", getNum("Thunder Chance", 0.65f));
            shader.setUniform1f("uThunderGlow", getNum("Thunder Glow", 1.0f));
        }

        setupQuad();
        GL30.glBindVertexArray(vaoID);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);

        // 🔥 ПРАВИЛЬНОЕ ВОССТАНОВЛЕНИЕ СОСТОЯНИЙ 🔥

        // Возвращаем сохраненные бинды ваниллы на место.
        GL30.glBindVertexArray(prevVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEBO);
        GL20.glUseProgram(prevProgram);

        // Возвращаем текстурный слот
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        // ВАЖНО: Возвращаем именно GL_LEQUAL (стандарт Майнкрафта), а не GL_LESS.
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }
}