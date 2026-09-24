package com.lexoravisauls.client.emotion;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.modern.ModernGuiRender;
import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class RadialMenuScreen extends Screen {

    private final List<String> activeEmotions = new ArrayList<>();
    private int selectedIndex = -1;

    private static final String BIND_KEY = "Radial Menu";

    private static final int COLOR_BG_RING = 0xB8151A22;
    private static final int COLOR_BG_INNER = 0xD90D1117;
    private static final int COLOR_ACCENT = 0xFF10B981;
    private static final int COLOR_ACCENT_FILL = 0x3810B981;
    private static final int COLOR_TEXT_NORMAL = 0xFFE8EDF2;
    private static final int COLOR_TEXT_MUTED = 0xFF8A95A3;
    private static final int COLOR_DIVIDER = 0x24FFFFFF;
    private static final int COLOR_INNER_BORDER = 0x8C7FB3D5;

    private static final int RADIUS_OUTER = 80;
    private static final int RADIUS_INNER = 41;
    private static final int RADIUS_ICON_RING = 58;
    private static final int ICON_CIRCLE_R = 7;

    private static final float GLOW_WIDTH = 14f;
    private static final int GLOW_MAX_ALPHA = 110;

    private static final float FONT_SIZE_LABEL = 6.5f;
    private static final float FONT_SIZE_TITLE = 6.0f;
    private static final float FONT_SIZE_SUBTITLE = 5.0f;

    private static final int LABEL_COLLAPSED_WIDTH = 26;

    private static final float LABEL_EXPAND_SPEED = 0.25f;

    private static final float SECTOR_GLOW_SPEED = 0.4f;

    private float[] labelExpandAnim = new float[0];

    private float[] sectorGlowAnim = new float[0];

    // АНИМАЦИЯ ОТКРЫТИЯ/ЗАКРЫТИЯ: openProgress идёт от 0 (полностью закрыто/невидимо)
    // до 1 (полностью открыто). Домножается на все радиусы через MatrixStack.scale()
    // вокруг всего рисования кольца — так не нужно вручную домножать каждую координату.
    // closing=true означает "клавиша уже отпущена, доигрываем анимацию закрытия перед
    // реальным setScreen(null)" — выбор (playEmotion) уже произошёл В МОМЕНТ отпускания,
    // а не откладывается до конца анимации, иначе была бы заметная задержка перед стартом
    // самой анимации эмоции.
    private float openProgress = 0f;
    private boolean closing = false;
    private static final float OPEN_SPEED = 0.35f;
    private static final float CLOSE_SPEED = 0.45f; // закрытие чуть быстрее открытия

    public RadialMenuScreen() {
        super(Text.literal("Круговое меню эмоций"));
        for (String anim : EmotionManager.myRadialSlots) {
            if (!anim.equals("none")) {
                activeEmotions.add(anim);
            }
        }
        labelExpandAnim = new float[activeEmotions.size()];
        sectorGlowAnim = new float[activeEmotions.size()];
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Клавиша отпущена и мы ещё НЕ в режиме закрытия — фиксируем выбор СРАЗУ (не после
        // анимации) и переключаемся в режим "доигрываем закрытие". confirmSelectionAndClose()
        // раньше сразу звал setScreen(null); теперь она только помечает closing=true —
        // реальное закрытие происходит ниже, когда openProgress дойдёт до 0.
        if (!closing && !isBindStillHeld()) {
            confirmSelectionAndClose();
        }

        float target = closing ? 0f : 1f;
        float speed = closing ? CLOSE_SPEED : OPEN_SPEED;
        openProgress += (target - openProgress) * speed;
        if (Math.abs(openProgress - target) < 0.01f) openProgress = target;

        if (closing && openProgress <= 0.01f) {
            if (this.client != null) {
                this.client.setScreen(null);
            }
            return;
        }

        // ПРИТЕМНЕНИЕ УБРАНО: renderBackground() — ванильный метод Screen, рисующий
        // затемняющий оверлей/vignette позади любого GUI-экрана. Раньше он вызывался здесь
        // и давал видимое затемнение вокруг колеса даже не смотря на то, что колесо само
        // рисует свой собственный фон (liquid glass кольцо+внутренний круг) — то есть
        // затемнение накладывалось ДВАЖДЫ: сначала общий vignette от Screen, потом наш
        // собственный тёмный фон кольца поверх. Убрано полностью по запросу — теперь вокруг
        // колеса виден чистый игровой мир без дополнительного затемнения.

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        // Масштабируем вокруг центра колеса, а не вокруг (0,0) экрана — иначе scale
        // сдвигал бы весь рисунок к углу экрана вместо роста/схлопывания из своего центра
        matrices.translate(centerX, centerY, 0);
        matrices.scale(openProgress, openProgress, 1f);
        matrices.translate(-centerX, -centerY, 0);

        if (activeEmotions.isEmpty()) {
            drawRingAndInner(context, centerX, centerY, 0, 0, sectorGlowAnim);
            drawCenteredMsdf(context, "Нет добавленных анимаций!", centerX, centerY - 5, FONT_SIZE_SUBTITLE, 0xFFFF5555);
            drawCenteredMsdf(context, "Настрой на сайте.", centerX, centerY + 4, FONT_SIZE_SUBTITLE, COLOR_TEXT_MUTED);
            matrices.pop();
            return;
        }

        int count = activeEmotions.size();
        float angleStep = 360f / count;

        // Наведение мышью считаем ПО РЕАЛЬНЫМ (немасштабированным) координатам курсора,
        // но сравниваем дистанцию с радиусами, УМНОЖЕННЫМИ на openProgress — иначе во время
        // анимации открытия/закрытия можно было бы навести на сектор, который ещё визуально
        // не появился (или уже исчез при закрытии), но чей хитбокс молча остаётся на полный
        // размер. mouseX/mouseY игра всегда даёт в физических координатах окна, поэтому сам
        // курсор не масштабируем — масштабируем только радиусы сравнения.
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        double mouseAngle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        if (mouseAngle < 0) mouseAngle += 360;

        selectedIndex = -1;
        // Во время анимации закрытия (closing=true) выбор больше не должен меняться —
        // playEmotion() для выбранного сектора уже вызван в confirmSelectionAndClose(),
        // пересчёт selectedIndex здесь только сбил бы то, что уже было подтверждено
        double scaledInner = RADIUS_INNER * openProgress;
        double scaledOuterHit = (RADIUS_OUTER + 12) * openProgress;
        if (!closing && distance > scaledInner && distance < scaledOuterHit) {
            selectedIndex = (int) ((mouseAngle + (angleStep / 2)) / angleStep) % count;
        }

        for (int i = 0; i < count; i++) {
            float t = (i == selectedIndex) ? 1f : 0f;
            sectorGlowAnim[i] += (t - sectorGlowAnim[i]) * SECTOR_GLOW_SPEED;
            if (Math.abs(sectorGlowAnim[i] - t) < 0.01f) sectorGlowAnim[i] = t;
        }

        drawRingAndInner(context, centerX, centerY, angleStep, count, sectorGlowAnim);

        for (int i = 0; i < count; i++) {
            if (i == selectedIndex) continue;
            tickAndDrawNode(context, i, count, angleStep, centerX, centerY, false);
        }
        if (selectedIndex != -1) {
            tickAndDrawNode(context, selectedIndex, count, angleStep, centerX, centerY, true);
        }

        drawCenteredMsdf(context, "МЕНЮ ЭМОЦИЙ", centerX, centerY - 5, FONT_SIZE_TITLE, COLOR_TEXT_NORMAL);
        drawCenteredMsdf(context, count + " " + pluralizeAnimations(count), centerX, centerY + 4, FONT_SIZE_SUBTITLE, COLOR_TEXT_MUTED);

        matrices.pop();
    }

    private boolean isBindStillHeld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return false;

        int bindKey = ClientData.moduleBinds.getOrDefault(BIND_KEY, InputUtil.UNKNOWN_KEY.getCode());
        if (bindKey == -1 || bindKey == GLFW.GLFW_KEY_UNKNOWN) return false;

        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), bindKey);
    }

    /**
     * Клавиша отпущена — подтверждаем выбор СРАЗУ (playEmotion вызывается здесь, не после
     * анимации закрытия) и переключаемся в режим closing. Реальное setScreen(null)
     * происходит в render() выше, когда openProgress доиграет до 0.
     */
    private void confirmSelectionAndClose() {
        if (selectedIndex != -1 && selectedIndex < activeEmotions.size()) {
            String selectedAnim = activeEmotions.get(selectedIndex);
            EmotionManager.playEmotion(selectedAnim);
        }
        closing = true;
    }

    private void tickAndDrawNode(DrawContext context, int i, int count, float angleStep, int centerX, int centerY, boolean isHovered) {
        float angle = (i * angleStep - 90);
        double rad = Math.toRadians(angle);
        int x = centerX + (int) (Math.cos(rad) * RADIUS_ICON_RING);
        int y = centerY + (int) (Math.sin(rad) * RADIUS_ICON_RING);

        float target = isHovered ? 1f : 0f;
        labelExpandAnim[i] += (target - labelExpandAnim[i]) * LABEL_EXPAND_SPEED;
        if (Math.abs(labelExpandAnim[i] - target) < 0.01f) labelExpandAnim[i] = target;

        String animName = getLocalizedName(activeEmotions.get(i));

        drawEmotionNode(context, x, y, centerX, centerY, animName, isHovered, labelExpandAnim[i]);
    }

    private void drawRingAndInner(DrawContext context, int cx, int cy, float angleStep, int count, float[] glowAnim) {
        context.draw();

        MatrixStack matrices = context.getMatrices();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder ringBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int segments = 120;
        float segAngle = 360f / segments;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) Math.toRadians(i * segAngle - 90);
            float a1 = (float) Math.toRadians((i + 1) * segAngle - 90);

            float x0i = cx + (float) (Math.cos(a0) * RADIUS_INNER);
            float y0i = cy + (float) (Math.sin(a0) * RADIUS_INNER);
            float x0o = cx + (float) (Math.cos(a0) * RADIUS_OUTER);
            float y0o = cy + (float) (Math.sin(a0) * RADIUS_OUTER);
            float x1o = cx + (float) (Math.cos(a1) * RADIUS_OUTER);
            float y1o = cy + (float) (Math.sin(a1) * RADIUS_OUTER);
            float x1i = cx + (float) (Math.cos(a1) * RADIUS_INNER);
            float y1i = cy + (float) (Math.sin(a1) * RADIUS_INNER);

            float glowT = 0f;
            if (angleStep > 0 && glowAnim.length == count) {
                float midAngle = (i + 0.5f) * segAngle; // уже в mouseAngle-системе, не atan2
                float normMid = (midAngle % 360 + 360) % 360;

                int sectorIdx = ((int) ((normMid + angleStep / 2f) / angleStep)) % count;
                if (sectorIdx < 0) sectorIdx += count;

                glowT = glowAnim[sectorIdx];
            }

            int fillColor = glowT > 0.001f ? blendColors(COLOR_BG_RING, COLOR_ACCENT_FILL, glowT) : COLOR_BG_RING;
            float[] c = argbToFloats(fillColor);

            addVertex(ringBuf, mat, x0i, y0i, c);
            addVertex(ringBuf, mat, x0o, y0o, c);
            addVertex(ringBuf, mat, x1o, y1o, c);

            addVertex(ringBuf, mat, x0i, y0i, c);
            addVertex(ringBuf, mat, x1o, y1o, c);
            addVertex(ringBuf, mat, x1i, y1i, c);
        }

        var built = ringBuf.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        if (angleStep > 0) {
            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);

            BufferBuilder glowBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            boolean anyGlow = false;

            for (int i = 0; i < count; i++) {
                float glowT = glowAnim[i];
                if (glowT <= 0.02f) continue;
                anyGlow = true;

                float sectorStart = (float) Math.toRadians(i * angleStep - 90 - angleStep / 2f);
                float sectorEnd = (float) Math.toRadians(i * angleStep - 90 + angleStep / 2f);

                int glowSegs = Math.max(4, (int) (angleStep / 6f));
                for (int s = 0; s < glowSegs; s++) {
                    float a0 = sectorStart + (sectorEnd - sectorStart) * (s / (float) glowSegs);
                    float a1 = sectorStart + (sectorEnd - sectorStart) * ((s + 1) / (float) glowSegs);

                    float x0o = cx + (float) (Math.cos(a0) * RADIUS_OUTER);
                    float y0o = cy + (float) (Math.sin(a0) * RADIUS_OUTER);
                    float x1o = cx + (float) (Math.cos(a1) * RADIUS_OUTER);
                    float y1o = cy + (float) (Math.sin(a1) * RADIUS_OUTER);
                    float xGo = cx + (float) (Math.cos(a0) * (RADIUS_OUTER + GLOW_WIDTH));
                    float yGo = cy + (float) (Math.sin(a0) * (RADIUS_OUTER + GLOW_WIDTH));
                    float x1Go = cx + (float) (Math.cos(a1) * (RADIUS_OUTER + GLOW_WIDTH));
                    float y1Go = cy + (float) (Math.sin(a1) * (RADIUS_OUTER + GLOW_WIDTH));

                    float[] glowInner = argbToFloats(withAlpha(COLOR_ACCENT, (int) (GLOW_MAX_ALPHA * glowT)));
                    float[] glowOuter = argbToFloats(withAlpha(COLOR_ACCENT, 0));

                    addVertex(glowBuf, mat, x0o, y0o, glowInner);
                    addVertex(glowBuf, mat, xGo, yGo, glowOuter);
                    addVertex(glowBuf, mat, x1Go, y1Go, glowOuter);

                    addVertex(glowBuf, mat, x0o, y0o, glowInner);
                    addVertex(glowBuf, mat, x1Go, y1Go, glowOuter);
                    addVertex(glowBuf, mat, x1o, y1o, glowInner);
                }
            }

            if (anyGlow) {
                var builtGlow = glowBuf.endNullable();
                if (builtGlow != null) {
                    BufferRenderer.drawWithGlobalProgram(builtGlow);
                }
            }

            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
        }

        BufferBuilder lineBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        float[] borderColor = argbToFloats(COLOR_INNER_BORDER);
        addCircleOutline(lineBuf, mat, cx, cy, RADIUS_OUTER, borderColor, 120);

        if (angleStep > 0) {
            float[] divColor = argbToFloats(COLOR_DIVIDER);
            for (int i = 0; i < count; i++) {
                float a = (float) Math.toRadians(i * angleStep - 90 - angleStep / 2f);
                addDashedRadialLine(lineBuf, mat, cx, cy, RADIUS_INNER, RADIUS_OUTER, a, divColor);
            }
        }

        var builtLines = lineBuf.endNullable();
        if (builtLines != null) {
            BufferRenderer.drawWithGlobalProgram(builtLines);
        }

        RenderSystem.disableBlend();
        RenderSystem.enableCull();

        context.draw();

        float innerDiameter = RADIUS_INNER * 2f;
        ModernGuiRender.drawLiquidGlass(
                context,
                cx - RADIUS_INNER, cy - RADIUS_INNER,
                innerDiameter, innerDiameter,
                (float) RADIUS_INNER,
                12f,
                COLOR_BG_INNER
        );

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder innerBorderBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        addCircleOutline(innerBorderBuf, mat, cx, cy, RADIUS_INNER, borderColor, 90);
        var builtInnerBorder = innerBorderBuf.endNullable();
        if (builtInnerBorder != null) {
            BufferRenderer.drawWithGlobalProgram(builtInnerBorder);
        }
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        context.draw();
    }

    private void drawEmotionNode(DrawContext context, int x, int y, int centerX, int centerY, String label, boolean hovered, float expandProgress) {
        MatrixStack matrices = context.getMatrices();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        int fillArgb = blendColors(0x18FFFFFF, 0x2210B981, expandProgress);
        float iconDiameter = ICON_CIRCLE_R * 2f;

        ModernGuiRender.drawLiquidGlass(
                context,
                x - ICON_CIRCLE_R, y - ICON_CIRCLE_R,
                iconDiameter, iconDiameter,
                (float) ICON_CIRCLE_R,
                8f,
                fillArgb
        );

        context.draw();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder lineBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int circleArgb = blendColors(0x40FFFFFF, COLOR_ACCENT, expandProgress);
        addCircleOutline(lineBuf, mat, x, y, ICON_CIRCLE_R, argbToFloats(circleArgb), 24);
        var builtLine = lineBuf.endNullable();
        if (builtLine != null) {
            BufferRenderer.drawWithGlobalProgram(builtLine);
        }

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        context.draw();

        int textColor = blendColors(COLOR_TEXT_NORMAL, COLOR_ACCENT, expandProgress);
        drawLabelWithSideExpand(context, label, x, y, centerX, centerY, textColor, expandProgress);
    }

    private void drawLabelWithSideExpand(DrawContext context, String label, int nodeX, int nodeY, int centerX, int centerY, int color, float expandProgress) {
        float fullWidth = getFont().getWidth(label, FONT_SIZE_LABEL);

        if (fullWidth <= LABEL_COLLAPSED_WIDTH) {
            drawCenteredMsdf(context, label, nodeX, nodeY + ICON_CIRCLE_R + 3, FONT_SIZE_LABEL, color);
            return;
        }

        double dx = nodeX - centerX;
        double dy = nodeY - centerY;
        double len = Math.sqrt(dx * dx + dy * dy);
        float dirX = len > 0.01 ? (float) (dx / len) : 0f;
        float dirY = len > 0.01 ? (float) (dy / len) : 1f;

        float extraWidthNeeded = fullWidth - LABEL_COLLAPSED_WIDTH;
        float offset = extraWidthNeeded * 0.5f * expandProgress;

        float labelCenterX = nodeX + dirX * offset;
        float labelCenterY = nodeY + ICON_CIRCLE_R + 3 + dirY * offset * 0.3f;

        float visibleWidth = LABEL_COLLAPSED_WIDTH + extraWidthNeeded * expandProgress;

        if (expandProgress > 0.05f) {
            int bgAlpha = (int) (0x60 * expandProgress);
            int bgColor = (bgAlpha << 24) | 0x000000;
            context.fill(
                    Math.round(labelCenterX - visibleWidth / 2f - 3),
                    Math.round(labelCenterY - 1),
                    Math.round(labelCenterX + visibleWidth / 2f + 3),
                    Math.round(labelCenterY + 8),
                    bgColor
            );
        }

        drawCenteredMsdf(context, label, Math.round(labelCenterX), Math.round(labelCenterY), FONT_SIZE_LABEL, color);
    }

    private void drawCenteredMsdf(DrawContext context, String text, int centerX, int y, float size, int color) {
        if (hasAllGlyphs(text)) {
            float width = getFont().getWidth(text, size);
            getFont().draw(context, text, centerX - width / 2f, y, size, color);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, text, centerX, y, color);
        }
    }

    private boolean hasAllGlyphs(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!getFont().hasGlyph(text.codePointAt(i))) return false;
        }
        return true;
    }

    private static final Identifier FONT_TEX = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    private static MsdfFont getFont() {
        if (msdfFont == null) {
            msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        }
        return msdfFont;
    }

    private void addVertex(BufferBuilder buf, Matrix4f mat, float x, float y, float[] rgba) {
        buf.vertex(mat, x, y, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private void addCircleOutline(BufferBuilder buf, Matrix4f mat, float cx, float cy, float radius, float[] rgba, int segments) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) Math.toRadians(i * 360f / segments);
            float a1 = (float) Math.toRadians((i + 1) * 360f / segments);
            float x0 = cx + (float) (Math.cos(a0) * radius);
            float y0 = cy + (float) (Math.sin(a0) * radius);
            float x1 = cx + (float) (Math.cos(a1) * radius);
            float y1 = cy + (float) (Math.sin(a1) * radius);
            buf.vertex(mat, x0, y0, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
            buf.vertex(mat, x1, y1, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        }
    }

    private void addDashedRadialLine(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rInner, float rOuter, float angle, float[] rgba) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        int dashLen = 2, gapLen = 2;
        float total = rOuter - rInner;
        float pos = 0;
        while (pos < total) {
            float r0 = rInner + pos;
            float r1 = Math.min(rInner + pos + dashLen, rOuter);
            float x0 = cx + cos * r0;
            float y0 = cy + sin * r0;
            float x1 = cx + cos * r1;
            float y1 = cy + sin * r1;
            buf.vertex(mat, x0, y0, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
            buf.vertex(mat, x1, y1, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
            pos += dashLen + gapLen;
        }
    }

    private float[] argbToFloats(int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    private int withAlpha(int argb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private int blendColors(int base, int overlay, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ba = (base >> 24) & 0xFF, br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int oa = (overlay >> 24) & 0xFF, or_ = (overlay >> 16) & 0xFF, og = (overlay >> 8) & 0xFF, ob = overlay & 0xFF;
        int a = (int) (ba + (oa - ba) * t);
        int r = (int) (br + (or_ - br) * t);
        int g = (int) (bg + (og - bg) * t);
        int b = (int) (bb + (ob - bb) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String pluralizeAnimations(int count) {
        int n = count % 100;
        int n1 = n % 10;
        if (n >= 11 && n <= 14) return "анимаций";
        if (n1 == 1) return "анимация";
        if (n1 >= 2 && n1 <= 4) return "анимации";
        return "анимаций";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private String getLocalizedName(String id) {
        return switch (id) {
            case "wave", "waving" -> "Приветствие";
            case "bow" -> "Поклон";
            case "backflip" -> "Сальто";
            case "clap" -> "Аплодисменты";
            case "crying" -> "Плач";
            case "here" -> "Я тут!";
            case "palm" -> "Фейспалм";
            case "point" -> "Указать пальцем";
            case "roblox_potion_dance" -> "Roblox Танец";
            case "twerk" -> "Тверк";
            default -> id;
        };
    }
}