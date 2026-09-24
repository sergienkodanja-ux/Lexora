package com.lexoravisauls.client.modules.weather;

import com.lexoravisauls.client.utils.ShaderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/**
 * Screen-Space Reflection (SSR) wet floor renderer.
 * Accurately reconstructs view and world geometry from depth buffer,
 * isolates upward-facing ground surfaces, and applies real-time SSR
 * reflections, darkening, Fresnel, and optional subtle ripples.
 */
public final class WetSurfaceRenderer implements AutoCloseable {
    private static final WetSurfaceRenderer INSTANCE = new WetSurfaceRenderer();
    private static final float EPSILON = 1.0E-4f;

    private final SceneTarget scene = new SceneTarget();

    private ShaderUtil shader;
    private int vertexArray;
    private int vertexBuffer;
    private boolean initialized;
    private boolean disabled;

    private WetSurfaceRenderer() {
    }

    public static WetSurfaceRenderer getInstance() {
        return INSTANCE;
    }

    public static final class Parameters {
        public float reflectionStrength = 0.85f;
        public float darkening = 0.30f;
        public int qualityIdx = 1; // 0: Performance, 1: Balanced, 2: High, 3: Ultra
        public boolean ripples = false;
        public float rippleSpeed = 1.0f;
    }

    private static final class SceneTarget {
        int texture;
        int width;
        int height;
    }

    public void apply(MinecraftClient mc, Camera camera,
                      Matrix4f projectionMatrix, Parameters parameters) {
        if (this.disabled || mc == null || camera == null || projectionMatrix == null || parameters == null) {
            return;
        }
        if (mc.world == null || mc.player == null || !isWindowValid(mc) || parameters.reflectionStrength <= EPSILON) {
            return;
        }

        // Iris Shaderpack check: If an external shaderpack is loaded in Iris, skip SSR pass because shaders handle reflections natively
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try {
                Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
                boolean shaderInUse = (boolean) irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi);
                if (shaderInUse) {
                    return;
                }
            } catch (Throwable ignored) {}
        }

        Window window = mc.getWindow();
        int width = window.getFramebufferWidth();
        int height = window.getFramebufferHeight();
        if (width <= 1 || height <= 1) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        if (framebuffer == null) return;

        int colorTexture = framebuffer.getColorAttachment();
        int depthTexture = framebuffer.getDepthAttachment();
        if (colorTexture <= 0 || depthTexture <= 0) return;

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int prevTex1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(prevActiveTex);

        boolean depthDetached = false;
        try {
            ensureInitialized();
            if (this.disabled || !ensureScene(width, height)) {
                return;
            }

            // Copy currently rendered scene from active read framebuffer into this.scene.texture
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevFbo);
            GL11.glReadBuffer(prevFbo == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.scene.texture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            // Detach depthTexture from draw framebuffer to eliminate OpenGL pipeline hazard / texture feedback loop crash on AMD drivers
            if (prevFbo != 0) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
                depthDetached = true;
            }

            renderPass(
                    mc,
                    camera,
                    colorTexture,
                    depthTexture,
                    width,
                    height,
                    projectionMatrix,
                    parameters
            );
        } catch (Throwable throwable) {
            this.disabled = true;
            System.err.println("[Lexora] WetSurfaceRenderer disabled due to error: " + throwable.getMessage());
        } finally {
            if (depthDetached && prevFbo != 0) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
            }

            // Restore original FBO exactly without stomping Iris / Minecraft pipeline
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProgram);

            // Restore texture units without invalid RenderSystem calls
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex1);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex0);

            GL13.glActiveTexture(prevActiveTex);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderPass(MinecraftClient mc, Camera camera,
                            int colorTexture, int depthTexture, int width, int height,
                            Matrix4f projectionMatrix, Parameters parameters) {
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(false);

        Matrix4f invProjection = new Matrix4f(projectionMatrix).invert();
        Matrix4f viewToWorld = new Matrix4f().rotation(camera.getRotation());
        Matrix4f worldToView = new Matrix4f(viewToWorld).invert();
        Vec3d camPos = camera.getPos();
        float fov = mc.options != null ? (float) mc.options.getFov().getValue() : 70.0f;
        float time = (float) ((System.currentTimeMillis() % 120_000L) / 1000.0);

        float fogEnd = mc.options != null ? mc.options.getClampedViewDistance() * 16.0f : 256.0f;
        float fogStart = fogEnd * 0.75f;

        this.shader.bind();
        this.shader.setUniform1i("Sampler0", 0);
        this.shader.setUniform1i("Sampler1", 1);
        this.shader.setUniformMatrix4f("uInvProjection", invProjection);
        this.shader.setUniformMatrix4f("uProjection", projectionMatrix);
        this.shader.setUniformMatrix4f("uViewToWorld", viewToWorld);
        this.shader.setUniformMatrix4f("uWorldToView", worldToView);
        this.shader.setUniform4f("uCameraPos", (float) camPos.x, (float) camPos.y, (float) camPos.z, fov);
        this.shader.setUniform4f("uScreen", (float) width, (float) height, 0.0f, 0.0f);
        this.shader.setUniform4f("uParams", clamp(parameters.reflectionStrength, 0.0f, 3.0f), clamp(parameters.darkening, 0.0f, 1.0f), time, 0.0f);
        this.shader.setUniform4f("uQualityParams", (float) parameters.qualityIdx, parameters.ripples ? 1.0f : 0.0f, clamp(parameters.rippleSpeed, 0.1f, 5.0f), 1.0f);
        this.shader.setUniform4f("uFogParams", fogStart, fogEnd, 0.0f, 0.0f);
        this.shader.setUniform4f("uFogColor", 0.0f, 0.0f, 0.0f, 0.0f);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.scene.texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL30.glBindVertexArray(this.vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        this.shader.unbind();
    }

    private boolean ensureScene(int width, int height) {
        if (this.scene.texture != 0 && (this.scene.width != width || this.scene.height != height)) {
            deleteScene();
        }

        if (this.scene.texture == 0) {
            this.scene.texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.scene.texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL30.GL_RGBA8,
                    width,
                    height,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    (java.nio.ByteBuffer) null
            );
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        this.scene.width = width;
        this.scene.height = height;
        return true;
    }

    private void ensureInitialized() {
        if (this.initialized) return;

        this.vertexArray = GL30.glGenVertexArrays();
        this.vertexBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(this.vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vertexBuffer);

        float[] vertices = new float[]{
                -1.0f, -1.0f, 0.0f, 0.0f,
                 1.0f, -1.0f, 1.0f, 0.0f,
                 1.0f,  1.0f, 1.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 0.0f,
                 1.0f,  1.0f, 1.0f, 1.0f,
                -1.0f,  1.0f, 0.0f, 1.0f
        };
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8L);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        this.shader = new ShaderUtil("wet_surface.vsh", "wet_surface.fsh");
        if (!this.shader.isValid()) {
            this.disabled = true;
            System.err.println("[Lexora] WetSurfaceRenderer: shader failed to load");
            return;
        }

        this.initialized = true;
        System.out.println("[Lexora] WetSurfaceRenderer initialized successfully");
    }

    public void reset() {
        this.disabled = false;
    }

    private static boolean isWindowValid(MinecraftClient mc) {
        Window window = mc == null ? null : mc.getWindow();
        return window != null && window.getFramebufferWidth() > 0 && window.getFramebufferHeight() > 0;
    }

    private static float clamp(float value, float min, float max) {
        return !Float.isFinite(value) ? min : Math.max(min, Math.min(max, value));
    }

    private static boolean canModifyGlObjects() {
        return RenderSystem.isOnRenderThread() && GLFW.glfwGetCurrentContext() != 0L;
    }

    private void deleteScene() {
        if (this.scene.texture != 0 && canModifyGlObjects()) {
            GL11.glDeleteTextures(this.scene.texture);
        }
        this.scene.texture = 0;
        this.scene.width = 0;
        this.scene.height = 0;
    }

    @Override
    public void close() {
        if (!canModifyGlObjects()) {
            clearObjectIds();
            return;
        }

        deleteScene();
        if (this.vertexArray != 0) GL30.glDeleteVertexArrays(this.vertexArray);
        if (this.vertexBuffer != 0) GL15.glDeleteBuffers(this.vertexBuffer);
        if (this.shader != null) this.shader.delete();
        clearObjectIds();
    }

    private void clearObjectIds() {
        this.scene.texture = 0;
        this.scene.width = 0;
        this.scene.height = 0;
        this.vertexArray = 0;
        this.vertexBuffer = 0;
        this.shader = null;
        this.initialized = false;
        this.disabled = false;
    }
}
