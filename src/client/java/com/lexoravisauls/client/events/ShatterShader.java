package com.lexoravisauls.client.events;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Voronoi-based shatter/reassembly fill mode, sibling to RoundedRectShader
 * rather than a mode bolted onto it — kept as its own class/shader/uniform
 * set because this effect has a materially different uniform contract
 * (Progress/ShardScale/FlyDistance/RandomSeed have no equivalent in the
 * plain rounded-rect fill, and this shader has no non-textured mode at all,
 * unlike RoundedRectShader's solid/gradient/textured three-way switch).
 * <p>
 * IMPORTANT — what this class does NOT do: it does not capture GUI content
 * (text, buttons, icons) into a texture for the shards to carry. Sampler0
 * must already be a real texture (a PNG resource, same as
 * RoundedRectShader.drawTextured's texture argument) — this project has no
 * render-to-texture/framebuffer capture of GUI content yet (ScreenCaptureManager
 * only grabs what's BEHIND the GUI, for the blur backdrop, not the GUI panel
 * itself). Calling this with a plain icon/background texture works today;
 * "real text and buttons on the shards" needs that separate render-to-texture
 * piece built first, which is intentionally not part of this class.
 */
public final class ShatterShader {

    private ShatterShader() {}

    private static final ShaderProgramKey SHATTER_SHADER =
            new ShaderProgramKey(
                    Identifier.of("lexoravisauls", "core/shatter_lexora"),
                    VertexFormats.POSITION,
                    Defines.EMPTY
            );

    private static float[] calcRect(Matrix4f matrix, float x, float y,
                                    float width, float height, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float ws = (float) mc.getWindow().getScaleFactor();
        float fbH = (float) mc.getWindow().getFramebufferHeight();

        Vector4f p1 = new Vector4f(x, y, 0f, 1f).mul(matrix);
        Vector4f p2 = new Vector4f(x + width, y + height, 0f, 1f).mul(matrix);
        Vector4f pr = new Vector4f(x + radius, y, 0f, 1f).mul(matrix);

        float sx = Math.min(p1.x, p2.x), sy = Math.min(p1.y, p2.y);
        float sw = Math.abs(p2.x - p1.x), sh = Math.abs(p2.y - p1.y);
        float sr = Math.abs(pr.x - p1.x);

        float rw = sw * ws, rh = sh * ws;
        float rr = Math.min(sr * ws, Math.min(rw, rh) * 0.5f);

        return new float[]{sx * ws, fbH - ((sy + sh) * ws), rw, rh, rr};
    }

    private static void drawQuad(Matrix4f matrix, float x, float y, float w, float h) {
        BufferBuilder buf = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buf.vertex(matrix, x, y, 0f);
        buf.vertex(matrix, x, y + h, 0f);
        buf.vertex(matrix, x + w, y + h, 0f);
        buf.vertex(matrix, x + w, y, 0f);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    /**
     * Draws {@code texture} into the rounded-rect area [x,y,width,height],
     * shattered into irregular Voronoi shards flying outward/inward based on
     * {@code progress}.
     *
     * @param progress    0.0 = fully shattered/flown apart, 1.0 = fully
     *                    assembled. Same value drives both opening (animate
     *                    0->1) and closing (animate 1->0) — this method
     *                    doesn't need to know which direction is happening,
     *                    only the current value.
     * @param shardScale  roughly how many shards across the shorter axis of
     *                    the rect. 6-10 is a reasonable starting range for a
     *                    GUI-panel-sized area — see the PERF NOTE in
     *                    shatter_lexora.fsh before pushing this much higher.
     * @param flyDistance how far (in the same pixel units as x/y/width/height,
     *                    pre-scale-factor) shards travel at progress == 0.
     *                    Large values make shards originate from well outside
     *                    the rect/screen edges rather than just jittering near
     *                    their rest position.
     * @param randomSeed  varies the shatter pattern between calls/panels; pass
     *                    a fixed value for a reproducible pattern, or
     *                    something like the panel's open-time for variety.
     * @param dripTime    continuously-growing seconds since assembly finished
     *                    (progress reached ~1.0) — drives the post-assembly
     *                    glow/droplet/fall/merge/spread sequence layered on
     *                    top of the shatter fill. Pass 0 for no drip effect
     *                    at all (e.g. while still mid-shatter or closing).
     */
    public static void drawShattered(DrawContext context,
                                     Identifier texture,
                                     float x, float y, float width, float height,
                                     float radius,
                                     float u0, float v0, float u1, float v1,
                                     int colorTint,
                                     float progress, float shardScale, float flyDistance, float randomSeed,
                                     float dripTime) {
        if (width <= 0f || height <= 0f) return;

        context.draw();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        float[] rc = calcRect(matrix, x, y, width, height, radius);
        float[] tint = toFloats(colorTint);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        RenderSystem.setShaderTexture(0, texture);

        ShaderProgram program = RenderSystem.setShader(SHATTER_SHADER);
        if (program == null) {
            // No silent fallback to context.fill/drawTexture here the way
            // RoundedRectShader's other methods do — a plain filled rect
            // would look nothing like "mid-shatter", so a missing shader is
            // more honestly represented as drawing nothing this frame than
            // as a misleading solid rectangle. Callers that need a guaranteed
            // fallback should check for this themselves before/instead of
            // calling this method.
            RenderSystem.enableDepthTest();
            return;
        }

        setUniform4f(program, "Rect", rc[0], rc[1], rc[2], rc[3]);
        setUniform1f(program, "Radius", rc[4]);
        setUniform4f(program, "FillColor", tint[0], tint[1], tint[2], tint[3]);
        setUniform1f(program, "Softness", 1f);
        setUniform2f(program, "TexMin", u0, v0);
        setUniform2f(program, "TexMax", u1, v1);
        setUniform1f(program, "Progress", progress);
        setUniform1f(program, "ShardScale", shardScale);
        setUniform1f(program, "FlyDistance", flyDistance);
        setUniform1f(program, "RandomSeed", randomSeed);
        setUniform1f(program, "DripTime", dripTime);

        drawQuad(matrix, x, y, width, height);

        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void setUniform1f(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setUniform2f(ShaderProgram p, String name, float v1, float v2) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v1, v2);
    }

    private static void setUniform4f(ShaderProgram p, String name,
                                     float v1, float v2, float v3, float v4) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v1, v2, v3, v4);
    }

    private static float[] toFloats(int color) {
        return new float[]{
                ((color >>> 16) & 0xFF) / 255f,
                ((color >>> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f
        };
    }
}