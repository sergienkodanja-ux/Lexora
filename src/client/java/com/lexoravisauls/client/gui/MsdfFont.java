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
    private final Identifier jsonId;
    private final Map<Integer, GlyphData> glyphs = new HashMap<>();
    private FontData fontData;

    private static Method setShaderMethod = null;
    private boolean filterSet = false; // Флаг для легальной настройки сглаживания

    public MsdfFont(Identifier textureId, Identifier jsonId) {
        this.textureId = textureId;
        this.jsonId = jsonId;
        loadFont(jsonId);
    }

    private void loadFont(Identifier jsonId) {
        try {
            if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getResourceManager() != null) {
                var res = MinecraftClient.getInstance().getResourceManager().getResource(jsonId);
                if (res.isPresent()) {
                    try (InputStreamReader reader = new InputStreamReader(res.get().getInputStream())) {
                        fontData = new Gson().fromJson(reader, FontData.class);
                        if (fontData != null && fontData.glyphs != null) {
                            glyphs.clear();
                            for (GlyphData g : fontData.glyphs) glyphs.put(g.unicode, g);
                        }
                        return;
                    }
                }
            }
            // Fallback: чтение напрямую из JAR/classpath, если resourceManager ещё перезагружается
            String path = "/assets/" + jsonId.getNamespace() + "/" + jsonId.getPath();
            java.io.InputStream is = MsdfFont.class.getResourceAsStream(path);
            if (is == null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream("assets/" + jsonId.getNamespace() + "/" + jsonId.getPath());
            }
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is)) {
                    fontData = new Gson().fromJson(reader, FontData.class);
                    if (fontData != null && fontData.glyphs != null) {
                        glyphs.clear();
                        for (GlyphData g : fontData.glyphs) glyphs.put(g.unicode, g);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 🛡 Безопасный метод для DrawContext
    public void draw(DrawContext context, String text, float x, float y, float size, int color) {
        if (fontData == null) loadFont(jsonId);
        if (fontData == null || text == null || text.trim().isEmpty()) return;
        context.draw(); // Сброс очереди до
        draw(context.getMatrices(), text, x, y, size, color);
        context.draw(); // Сброс очереди после
    }

    // 🛡 Безопасный метод для MatrixStack (БЕЗ использования GL11)
    public void draw(MatrixStack matrices, String text, float x, float y, float size, int color) {
        if (fontData == null) loadFont(jsonId);
        if (fontData == null || text == null || text.trim().isEmpty()) return;

        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // 🔥 ЛЕГАЛЬНОЕ СГЛАЖИВАНИЕ ЧЕРЕЗ ДВИЖОК ИГРЫ 🔥
        ensureTextureFilter();

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

    public void draw3D(MatrixStack matrices, String text, float x, float y, float size, int color, boolean depthTest) {
        if (fontData == null) loadFont(jsonId);
        if (fontData == null || text == null || text.trim().isEmpty()) return;

        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        ensureTextureFilter();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515); // GL_LEQUAL
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
        }
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

        if (depthTest) {
            RenderSystem.depthMask(true);
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void ensureTextureFilter() {
        if (!filterSet) {
            try {
                AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
                if (texture != null) {
                    texture.setFilter(true, false);
                    filterSet = true;
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void applyShaderObfuscationProof() {
        try {
            RenderSystem.setShader(MSDF_KEY);
            return;
        } catch (Throwable ignored) {}

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

    // ═══════════════════════════════════════════════════════════════════════
    //  КАРАОКЕ-РЕНДЕР: посимвольный переход цвета
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Рисует текст в стиле караоке: символы до позиции progress → colorDone,
     * после → colorPending, на стыке — плавная интерполяция.
     *
     * @param progress 0.0 – 1.0 — какая доля ширины текста уже "пропета"
     */
    public void drawKaraoke(DrawContext context, String text, float x, float y,
                            float size, int colorDone, int colorPending, float progress) {
        if (fontData == null || text == null || text.trim().isEmpty()) return;
        context.draw();
        drawKaraoke(context.getMatrices(), text, x, y, size, colorDone, colorPending, progress);
        context.draw();
    }

    public void drawKaraoke(MatrixStack matrices, String text, float x, float y,
                            float size, int colorDone, int colorPending, float progress) {
        if (fontData == null || text == null || text.trim().isEmpty()) return;
        progress = Math.max(0f, Math.min(1f, progress));

        // Считаем естественную X-позицию курсора караоке (с учётом слогов и тянущихся гласных)
        float progressX = computeNaturalProgressX(text, size, progress);

        // Разбираем цвета
        int aD = (colorDone >> 24) & 0xFF; if (aD == 0) aD = 255;
        int rD = (colorDone >> 16) & 0xFF;
        int gD = (colorDone >> 8) & 0xFF;
        int bD = colorDone & 0xFF;

        int aP = (colorPending >> 24) & 0xFF; if (aP == 0) aP = 255;
        int rP = (colorPending >> 16) & 0xFF;
        int gP = (colorPending >> 8) & 0xFF;
        int bP = colorPending & 0xFF;

        ensureTextureFilter();

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, textureId);
        applyShaderObfuscationProof();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float cx = 0; // Относительный X от начала текста
        float drawX = x;
        float cy = y + (fontData.metrics.ascender * size);
        boolean hasVertices = false;

        for (int i = 0; i < text.length(); i++) {
            int code = text.codePointAt(i);
            float charW;
            if (code == 32) {
                charW = 0.25f * size;
                cx += charW;
                drawX += charW;
                continue;
            }

            GlyphData glyph = glyphs.get(code);
            if (glyph == null || glyph.planeBounds == null) continue;
            charW = glyph.advance * size;

            // Вычисляем t для этого символа: 0 = полностью "спет", 1 = ещё не спет
            float charMid = cx + charW / 2f;
            float t;
            if (charMid <= progressX - charW) {
                t = 0f; // полностью пропет
            } else if (charMid >= progressX + charW) {
                t = 1f; // ещё не дошли
            } else {
                // Плавный переход в зоне ±charW вокруг progressX
                t = (charMid - (progressX - charW)) / (2f * charW);
                t = 1f - Math.max(0f, Math.min(1f, t));
                // Invert: t=0 когда спет, t=1 когда не спет  →  нам нужно наоборот
                // На самом деле: при charMid < progressX → t ближе к 0 (спет)
                // Пересчитаем:
                t = Math.max(0f, Math.min(1f, (charMid - progressX + charW) / (2f * charW)));
            }

            // Интерполируем цвет: t=0 → done, t=1 → pending
            int r = (int)(rD + (rP - rD) * t);
            int g = (int)(gD + (gP - gD) * t);
            int b = (int)(bD + (bP - bD) * t);
            int a = (int)(aD + (aP - aD) * t);

            float u0 = glyph.atlasBounds.left / fontData.atlas.width;
            float v0 = 1f - (glyph.atlasBounds.top / fontData.atlas.height);
            float u1 = glyph.atlasBounds.right / fontData.atlas.width;
            float v1 = 1f - (glyph.atlasBounds.bottom / fontData.atlas.height);

            float x0 = drawX + glyph.planeBounds.left * size;
            float y0 = cy - glyph.planeBounds.top * size;
            float x1 = drawX + glyph.planeBounds.right * size;
            float y1 = cy - glyph.planeBounds.bottom * size;

            buf.vertex(mat, x0, y0, 0.0f).texture(u0, v0).color(r, g, b, a);
            buf.vertex(mat, x0, y1, 0.0f).texture(u0, v1).color(r, g, b, a);
            buf.vertex(mat, x1, y1, 0.0f).texture(u1, v1).color(r, g, b, a);
            buf.vertex(mat, x1, y0, 0.0f).texture(u1, v0).color(r, g, b, a);

            cx += charW;
            drawX += charW;
            hasVertices = true;
        }

        if (hasVertices) {
            try {
                BufferRenderer.drawWithGlobalProgram(buf.end());
            } catch (Exception ignored) {}
        }

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void drawKaraoke3D(MatrixStack matrices, String text, float x, float y,
                              float size, int colorDone, int colorPending, float progress, boolean depthTest) {
        if (fontData == null) loadFont(jsonId);
        if (fontData == null || text == null || text.trim().isEmpty()) return;
        progress = Math.max(0f, Math.min(1f, progress));

        float progressX = computeNaturalProgressX(text, size, progress);

        int aD = (colorDone >> 24) & 0xFF; if (aD == 0) aD = 255;
        int rD = (colorDone >> 16) & 0xFF;
        int gD = (colorDone >> 8) & 0xFF;
        int bD = colorDone & 0xFF;

        int aP = (colorPending >> 24) & 0xFF; if (aP == 0) aP = 255;
        int rP = (colorPending >> 16) & 0xFF;
        int gP = (colorPending >> 8) & 0xFF;
        int bP = colorPending & 0xFF;

        ensureTextureFilter();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515); // GL_LEQUAL
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.setShaderTexture(0, textureId);
        applyShaderObfuscationProof();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float cx = 0;
        float drawX = x;
        float cy = y + (fontData.metrics.ascender * size);
        boolean hasVertices = false;

        for (int i = 0; i < text.length(); i++) {
            int code = text.codePointAt(i);
            float charW;
            if (code == 32) {
                charW = 0.25f * size;
                cx += charW;
                drawX += charW;
                continue;
            }

            GlyphData glyph = glyphs.get(code);
            if (glyph == null || glyph.planeBounds == null) continue;
            charW = glyph.advance * size;

            float charMid = cx + charW / 2f;
            float t;
            if (charMid <= progressX - charW) {
                t = 0f;
            } else if (charMid >= progressX + charW) {
                t = 1f;
            } else {
                t = Math.max(0f, Math.min(1f, (charMid - progressX + charW) / (2f * charW)));
            }

            int r = (int)(rD + (rP - rD) * t);
            int g = (int)(gD + (gP - gD) * t);
            int b = (int)(bD + (bP - bD) * t);
            int a = (int)(aD + (aP - aD) * t);

            float u0 = glyph.atlasBounds.left / fontData.atlas.width;
            float v0 = 1f - (glyph.atlasBounds.top / fontData.atlas.height);
            float u1 = glyph.atlasBounds.right / fontData.atlas.width;
            float v1 = 1f - (glyph.atlasBounds.bottom / fontData.atlas.height);

            float x0 = drawX + glyph.planeBounds.left * size;
            float y0 = cy - glyph.planeBounds.top * size;
            float x1 = drawX + glyph.planeBounds.right * size;
            float y1 = cy - glyph.planeBounds.bottom * size;

            buf.vertex(mat, x0, y0, 0.0f).texture(u0, v0).color(r, g, b, a);
            buf.vertex(mat, x0, y1, 0.0f).texture(u0, v1).color(r, g, b, a);
            buf.vertex(mat, x1, y1, 0.0f).texture(u1, v1).color(r, g, b, a);
            buf.vertex(mat, x1, y0, 0.0f).texture(u1, v0).color(r, g, b, a);

            cx += charW;
            drawX += charW;
            hasVertices = true;
        }

        if (hasVertices) {
            try {
                BufferRenderer.drawWithGlobalProgram(buf.end());
            } catch (Exception ignored) {}
        }

        if (depthTest) {
            RenderSystem.depthMask(true);
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Есть ли в кастомном MSDF-атласе глиф для этого юникод-кодпоинта (пробел считается всегда "своим"). */
    public boolean hasGlyph(int codepoint) {
        return codepoint == 32 || glyphs.containsKey(codepoint);
    }

    /**
     * Вычисляет X-координату курсора караоке с учётом слоговой плотности, тянущихся гласных и пауз.
     */
    public float computeNaturalProgressX(String text, float size, float progress) {
        float totalW = getWidth(text, size);
        if (totalW <= 0f) return 0f;
        if (progress <= 0f) return 0f;
        if (progress >= 0.98f) return totalW;

        String[] words = text.split("\\s+");
        if (words.length == 0) return totalW * progress;

        float[] wordWidths = new float[words.length];
        float[] weights = new float[words.length];
        float spaceW = 0.25f * size;
        float totalWeight = 0f;

        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            wordWidths[i] = getWidth(w, size);
            boolean isLastWord = (i == words.length - 1);
            float weight = calculateWordWeight(w, isLastWord);
            weights[i] = weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0f) return totalW * progress;

        float availableFrac = 0.96f;
        float currentFrac = 0.0f;
        float currentX = 0f;

        for (int i = 0; i < words.length; i++) {
            float wordFrac = (weights[i] / totalWeight) * availableFrac;
            float startF = currentFrac;
            float endF = currentFrac + wordFrac;

            if (progress >= startF && progress <= endF) {
                float inWordNorm = (endF > startF) ? ((progress - startF) / (endF - startF)) : 1f;
                float inWordPixelX = computeCharXWithinWord(words[i], size, wordWidths[i], inWordNorm);
                return currentX + inWordPixelX;
            }

            currentX += wordWidths[i] + spaceW;
            currentFrac = endF;
        }

        return totalW;
    }

    private static float calculateWordWeight(String w, boolean isLastWord) {
        if (w == null || w.isEmpty()) return 1.0f;
        float weight = 0f;
        String vowels = "aeiouyаеёиоуыэюя";
        char prev = ' ';
        int repeatCount = 0;

        for (int i = 0; i < w.length(); i++) {
            char c = Character.toLowerCase(w.charAt(i));
            boolean isVowel = vowels.indexOf(c) >= 0;

            if (isVowel) {
                if (c == prev) {
                    repeatCount++;
                    // Каждая повторяющаяся гласная (например, "ииии", "оооо", "аааа") растягивает звук
                    weight += 2.0f + (repeatCount * 0.6f);
                } else {
                    repeatCount = 0;
                    weight += 1.3f;
                }
            } else if (c == '-' || c == '~' || c == '—' || c == '–') {
                // Дефис / тире внутри слова указывает на растягивание звука вокалистом
                weight += 2.2f;
            } else {
                weight += 0.20f;
            }
            prev = c;
        }

        if (w.length() == 1) {
            weight = Math.max(weight, 1.6f);
        }

        // В вокальной музыке последнее слово фразы/рифмы всегда тянется на каденции (нота держится дольше)
        if (isLastWord) {
            weight *= 1.45f;
        }

        return Math.max(0.8f, weight);
    }

    private float computeCharXWithinWord(String w, float size, float wordW, float inWordProgress) {
        if (w.isEmpty() || wordW <= 0f) return 0f;
        if (inWordProgress <= 0f) return 0f;
        if (inWordProgress >= 1f) return wordW;

        float[] charWeights = new float[w.length()];
        float[] charWidths = new float[w.length()];
        float totalCharWeight = 0f;
        String vowels = "aeiouyаеёиоуыэюя";
        char prev = ' ';
        int repeatCount = 0;

        for (int i = 0; i < w.length(); i++) {
            char c = Character.toLowerCase(w.charAt(i));
            int code = w.codePointAt(i);
            GlyphData g = glyphs.get(code);
            charWidths[i] = (g != null) ? (g.advance * size) : (0.25f * size);

            float cw;
            if (vowels.indexOf(c) >= 0) {
                if (c == prev) {
                    repeatCount++;
                    cw = 1.6f + (repeatCount * 0.4f);
                } else {
                    repeatCount = 0;
                    cw = 1.2f;
                }
            } else if (c == '-' || c == '~' || c == '—' || c == '–') {
                cw = 1.8f;
            } else {
                cw = 0.25f;
            }
            charWeights[i] = cw;
            totalCharWeight += cw;
            prev = c;
        }

        if (totalCharWeight <= 0f) return wordW * inWordProgress;

        float curW = 0f;
        float curFrac = 0f;
        for (int i = 0; i < w.length(); i++) {
            float charFrac = charWeights[i] / totalCharWeight;
            float startF = curFrac;
            float endF = curFrac + charFrac;

            if (inWordProgress >= startF && inWordProgress <= endF) {
                float inChar = (endF > startF) ? ((inWordProgress - startF) / (endF - startF)) : 1f;
                return curW + (inChar * charWidths[i]);
            }
            curW += charWidths[i];
            curFrac = endF;
        }
        return wordW;
    }

    public float getWidth(String text, float size) {
        if (fontData == null) loadFont(jsonId);
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