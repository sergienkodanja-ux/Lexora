package com.lexoravisauls.client.modules.killeffect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Custom GLSL (.vsh / .fsh) shader dispatch for kill effects in Minecraft 1.21.4.
 * Integrates directly with core shaders:
 * - killeffect_singularity: Gravitational lensing, logarithmic plasma swirl & photon sphere
 * - killeffect_divine: Sacred geometric runic seal, inscribed octagram & sunbeams
 * - killeffect_blood: Voronoi crystal fractures, shockwave ripple & blood veins
 */
public final class KillEffectShaders {

    private KillEffectShaders() {
    }

    private static final ShaderProgramKey SINGULARITY_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/killeffect_singularity"),
                    VertexFormats.POSITION_TEXTURE,
                    Defines.EMPTY
            );

    private static final ShaderProgramKey DIVINE_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/killeffect_divine"),
                    VertexFormats.POSITION_TEXTURE,
                    Defines.EMPTY
            );

    private static final ShaderProgramKey BLOOD_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/killeffect_blood"),
                    VertexFormats.POSITION_TEXTURE,
                    Defines.EMPTY
            );

    private static final ShaderProgramKey SPIRIT_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/killeffect_spirit"),
                    VertexFormats.POSITION_TEXTURE,
                    Defines.EMPTY
            );

    private static void setUniform1f(ShaderProgram program, String name, float v0) {
        GlUniform u = program.getUniform(name);
        if (u != null) u.set(v0);
    }

    private static void setUniform4f(ShaderProgram program, String name, float v0, float v1, float v2, float v3) {
        GlUniform u = program.getUniform(name);
        if (u != null) u.set(v0, v1, v2, v3);
    }

    /**
     * Renders the custom GLSL black hole accretion disk with gravitational lensing & photon sphere.
     */
    public static void renderSingularityDisk(MatrixStack matrices, double relX, double relY, double relZ,
                                             float radius, float pitchDeg, float yawDeg, float rollDeg,
                                             float time, float progress, float alpha,
                                             float r1, float g1, float b1, float r2, float g2, float b2) {
        if (alpha <= 0.005f || radius <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        if (yawDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
        if (pitchDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));
        if (rollDeg != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDeg));

        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        ShaderProgram program = RenderSystem.setShader(SINGULARITY_SHADER);
        if (program != null) {
            setUniform1f(program, "uTime", time);
            setUniform1f(program, "uProgress", progress);
            setUniform1f(program, "uAlpha", alpha);
            setUniform4f(program, "uColor1", r1, g1, b1, 1.0f);
            setUniform4f(program, "uColor2", r2, g2, b2, 1.0f);
        }

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(mat, -radius, 0.0f, -radius).texture(0.0f, 0.0f);
        buffer.vertex(mat, -radius, 0.0f, radius).texture(0.0f, 1.0f);
        buffer.vertex(mat, radius, 0.0f, radius).texture(1.0f, 1.0f);
        buffer.vertex(mat, radius, 0.0f, -radius).texture(1.0f, 0.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * Renders a camera-facing spherical black hole singularity with gravitational lensing & photon sphere.
     */
    public static void renderSingularitySphere(MatrixStack matrices, Camera camera,
                                               double relX, double relY, double relZ,
                                               float size, float time, float progress, float alpha,
                                               float r1, float g1, float b1, float r2, float g2, float b2) {
        if (alpha <= 0.005f || size <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw() + 180.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-camera.getPitch()));

        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        ShaderProgram program = RenderSystem.setShader(SINGULARITY_SHADER);
        if (program != null) {
            setUniform1f(program, "uTime", time);
            setUniform1f(program, "uProgress", progress);
            setUniform1f(program, "uAlpha", alpha);
            setUniform4f(program, "uColor1", r1, g1, b1, 1.0f);
            setUniform4f(program, "uColor2", r2, g2, b2, 1.0f);
        }

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        float hs = size * 0.5f;
        buffer.vertex(mat, -hs, -hs, 0.0f).texture(0.0f, 0.0f);
        buffer.vertex(mat,  hs, -hs, 0.0f).texture(1.0f, 0.0f);
        buffer.vertex(mat,  hs,  hs, 0.0f).texture(1.0f, 1.0f);
        buffer.vertex(mat, -hs,  hs, 0.0f).texture(0.0f, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * Renders the custom GLSL sacred geometric runic seal flat on the ground.
     */
    public static void renderDivineSeal(MatrixStack matrices, double relX, double relY, double relZ,
                                        float radius, float time, float progress, float alpha,
                                        float r1, float g1, float b1, float r2, float g2, float b2) {
        if (alpha <= 0.005f || radius <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY + 0.025, relZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        ShaderProgram program = RenderSystem.setShader(DIVINE_SHADER);
        if (program != null) {
            setUniform1f(program, "uTime", time);
            setUniform1f(program, "uProgress", progress);
            setUniform1f(program, "uAlpha", alpha);
            setUniform4f(program, "uColor1", r1, g1, b1, 1.0f);
            setUniform4f(program, "uColor2", r2, g2, b2, 1.0f);
        }

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(mat, -radius, 0.0f, -radius).texture(0.0f, 0.0f);
        buffer.vertex(mat, -radius, 0.0f, radius).texture(0.0f, 1.0f);
        buffer.vertex(mat, radius, 0.0f, radius).texture(1.0f, 1.0f);
        buffer.vertex(mat, radius, 0.0f, -radius).texture(1.0f, 0.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * Renders the custom GLSL Voronoi crystal shatter & expanding shockwave front.
     */
    public static void renderBloodNovaWave(MatrixStack matrices, double relX, double relY, double relZ,
                                          float radius, float time, float progress, float alpha,
                                          float r1, float g1, float b1, float r2, float g2, float b2) {
        if (alpha <= 0.005f || radius <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY + 0.03, relZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        ShaderProgram program = RenderSystem.setShader(BLOOD_SHADER);
        if (program != null) {
            setUniform1f(program, "uTime", time);
            setUniform1f(program, "uProgress", progress);
            setUniform1f(program, "uAlpha", alpha);
            setUniform4f(program, "uColor1", r1, g1, b1, 1.0f);
            setUniform4f(program, "uColor2", r2, g2, b2, 1.0f);
        }

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(mat, -radius, 0.0f, -radius).texture(0.0f, 0.0f);
        buffer.vertex(mat, -radius, 0.0f, radius).texture(0.0f, 1.0f);
        buffer.vertex(mat, radius, 0.0f, radius).texture(1.0f, 1.0f);
        buffer.vertex(mat, radius, 0.0f, -radius).texture(1.0f, 0.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    /**
     * Renders an ethereal glowing aura billboard facing the camera around the ascending spirit.
     */
    public static void renderSpiritAura(MatrixStack matrices, Camera camera,
                                        double relX, double relY, double relZ,
                                        float width, float height,
                                        float time, float progress, float alpha,
                                        float r1, float g1, float b1, float r2, float g2, float b2) {
        if (alpha <= 0.005f || width <= 0.005f || height <= 0.005f) return;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw() + 180.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-camera.getPitch()));

        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        ShaderProgram program = RenderSystem.setShader(SPIRIT_SHADER);
        if (program != null) {
            setUniform1f(program, "uTime", time);
            setUniform1f(program, "uProgress", progress);
            setUniform1f(program, "uAlpha", alpha);
            setUniform4f(program, "uColor1", r1, g1, b1, 1.0f);
            setUniform4f(program, "uColor2", r2, g2, b2, 1.0f);
        }

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        float hw = width * 0.5f;
        float hh = height * 0.5f;
        buffer.vertex(mat, -hw, -hh, 0.0f).texture(0.0f, 0.0f);
        buffer.vertex(mat,  hw, -hh, 0.0f).texture(1.0f, 0.0f);
        buffer.vertex(mat,  hw,  hh, 0.0f).texture(1.0f, 1.0f);
        buffer.vertex(mat, -hw,  hh, 0.0f).texture(0.0f, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }
}
