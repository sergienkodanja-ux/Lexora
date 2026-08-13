package com.lexoravisauls.client.gui;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.render.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MsdfFont {

    public static final ShaderProgramKey MSDF_KEY = new ShaderProgramKey(
            Identifier.of("lexoravisauls", "core/msdf"),
            VertexFormats.POSITION_TEXTURE_COLOR,
            Defines.EMPTY
    );

    private final Identifier textureId;
    private final Map<Integer, GlyphData> glyphs = new HashMap<>();
    private FontData fontData;

    private static Method setShaderMethod = null;
    private boolean filterSet = false; // Флаг для легальной настройки сглаживания

    public MsdfFont(Identifier textureId, Identifier jsonId) {
        this.textureId = textureId;
        loadFont(jsonId);
    }

    private void loadFont(Identifier jsonId) {
        try {
            var res = MinecraftClient.getInstance().getResourceManager().getResource(jsonId);
            if (res.isEmpty()) return;
            try (InputStreamReader reader = new InputStreamReader(res.get().getInputStream())) {
                fontData = new Gson().fromJson(reader, FontData.class);
                if (fontData != null && fontData.glyphs != null) {
                    for (GlyphData g : fontData.glyphs) glyphs.put(g.unicode, g);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 🛡 Безопасный метод для DrawContext
    public void draw(DrawContext context, String text, float x, float y, float size, int color) {
        if (fontData == null || text == null || text.trim().isEmpty()) return;
        context.draw(); // Сброс очереди до
        draw(context.getMatrices(), text, x, y, size, color);
        context.draw(); // Сброс очереди после
    }

    // 🛡 Безопасный метод для MatrixStack (БЕЗ использования GL11)
    public void draw(MatrixStack matrices, String text, float x, float y, float size, int color) {
        if (fontData == null || text == null || text.trim().isEmpty()) return;

        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // 🔥 ЛЕГАЛЬНОЕ СГЛАЖИВАНИЕ ЧЕРЕЗ ДВИЖОК ИГРЫ 🔥
        // Выполняется только 1 раз при первой отрисовке шрифта
        if (!filterSet) {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
            if (texture != null) {
                texture.setFilter(true, false); // Майнкрафт сам всё настроит и сохранит в кэш!
                filterSet = true;
            }
        }

        // Рендер строго через ванильный RenderSystem
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, textureId);

        applyShaderObfuscationProof();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float cx = x;
        float cy = y + (fontData.metrics.ascender * size);
        boolean hasVertices = false;

        for (int i = 0; i < text.length(); i++) {
            int code = text.codePointAt(i);
            if (code == 32) { cx += 0.25f * size; continue; }

            GlyphData glyph = glyphs.get(code);
            if (glyph == null || glyph.planeBounds == null) continue;

            float u0 = glyph.atlasBounds.left / fontData.atlas.width;
            float v0 = 1f - (glyph.atlasBounds.top / fontData.atlas.height);
            float u1 = glyph.atlasBounds.right / fontData.atlas.width;
            float v1 = 1f - (glyph.atlasBounds.bottom / fontData.atlas.height);

            float x0 = cx + glyph.planeBounds.left * size;
            float y0 = cy - glyph.planeBounds.top * size;
            float x1 = cx + glyph.planeBounds.right * size;
            float y1 = cy - glyph.planeBounds.bottom * size;

            buf.vertex(mat, x0, y0, 0.0f).texture(u0, v0).color(r, g, b, a);
            buf.vertex(mat, x0, y1, 0.0f).texture(u0, v1).color(r, g, b, a);
            buf.vertex(mat, x1, y1, 0.0f).texture(u1, v1).color(r, g, b, a);
            buf.vertex(mat, x1, y0, 0.0f).texture(u1, v0).color(r, g, b, a);

            cx += glyph.advance * size;
            hasVertices = true;
        }

        if (hasVertices) {
            try {
                BufferRenderer.drawWithGlobalProgram(buf.end());
            } catch (Exception ignored) {}
        }

        // Мягко возвращаем состояния
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void applyShaderObfuscationProof() {
        try {
            if (setShaderMethod == null) {
                for (Method m : RenderSystem.class.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == MSDF_KEY.getClass()) {
                        m.setAccessible(true);
                        setShaderMethod = m;
                        break;
                    }
                }
            }
            if (setShaderMethod != null) {
                setShaderMethod.invoke(null, MSDF_KEY);
            }
        } catch (Exception ignored) {}
    }

    /** Есть ли в кастомном MSDF-атласе глиф для этого юникод-кодпоинта (пробел считается всегда "своим"). */
    public boolean hasGlyph(int codepoint) {
        return codepoint == 32 || glyphs.containsKey(codepoint);
    }

    public float getWidth(String text, float size) {
        if (fontData == null || text == null || text.trim().isEmpty()) return 0f;
        float w = 0f;
        for (int i = 0; i < text.length(); i++) {
            int code = text.codePointAt(i);
            if (code == 32) { w += 0.25f * size; continue; }
            GlyphData g = glyphs.get(code);
            if (g != null) w += g.advance * size;
        }
        return w;
    }

    private static class FontData { AtlasData atlas; MetricsData metrics; List<GlyphData> glyphs; }
    private static class AtlasData { float width, height; }
    private static class MetricsData { float ascender; }
    private static class GlyphData { int unicode; float advance; BoundsData planeBounds; BoundsData atlasBounds; }
    private static class BoundsData { float left, top, right, bottom; }
}