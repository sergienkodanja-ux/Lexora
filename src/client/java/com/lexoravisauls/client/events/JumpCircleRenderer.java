package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class JumpCircleRenderer {
    private static final Identifier CIRCLE_TEX = Identifier.of("lexoravisauls", "textures/gui/jump_circles.png");
    private static final List<JumpEvent> activeJumps = new ArrayList<>();

    // Mode 1: плоский текстурный круг (оригинальный эффект)
    private static final float CIRCLE_START_RADIUS = 0.0f;
    private static final float CIRCLE_MAX_RADIUS = 1.5f;

    // Mode 2: расширяющаяся волна по блокам (портировано и адаптировано)
    private static final float WAVE_BASE_RADIUS = 1.0f;
    private static final float WAVE_MAX_RADIUS = 6.0f;
    private static final float WAVE_RING_WIDTH = 2.2f;
    private static final float WAVE_SIZE_CAP = 2.5f; // ограничивает влияние "Jump Size" на радиус волны, чтобы не убить fps
    private static final int WAVE_Y_RANGE = 2;

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Jump Circles", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || activeJumps.isEmpty()) return;

        activeJumps.removeIf(jump -> jump.isFinished);
        if (activeJumps.isEmpty()) return;

        float circleSize = LexoraGui.numSettings.getOrDefault("Jump Size", 1.8f);
        float circleSpeed = LexoraGui.numSettings.getOrDefault("Jump Speed", 1.2f);
        String effectMode = LexoraGui.modeSettings.getOrDefault("Jump Circle Mode", "Circle");
        long currentTime = System.currentTimeMillis();

        boolean needsDraw = false;
        for (JumpEvent jump : activeJumps) {
            float progress = (currentTime - jump.startTime) / (1000.0f / circleSpeed);
            if (progress < 1.0f) {
                needsDraw = true;
                break;
            }
        }
        if (!needsDraw) return;

        if (effectMode.equals("Wave")) {
            renderWaves(matrices, camera, mc, circleSize, circleSpeed, currentTime);
        } else {
            renderCircles(matrices, camera, circleSize, circleSpeed, currentTime);
        }
    }

    public static void onJump(double x, double y, double z) {
        if (!LexoraGui.moduleStates.getOrDefault("Jump Circles", false)) return;
        activeJumps.add(new JumpEvent(x, y + 0.05, z));
    }

    // ---------------------------------------------------------------------
    // Mode 1: плоский текстурный круг
    // ---------------------------------------------------------------------

    private static void renderCircles(MatrixStack matrices, Camera camera, float circleSize, float circleSpeed, long currentTime) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, CIRCLE_TEX);
        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);

        matrices.push();
        Vec3d camPos = camera.getPos();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        boolean hasVertices = false;

        for (JumpEvent jump : activeJumps) {
            float progress = (currentTime - jump.startTime) / (1000.0f / circleSpeed);
            if (progress >= 1.0f) {
                jump.isFinished = true;
                continue;
            }

            float radius = CIRCLE_START_RADIUS + (CIRCLE_MAX_RADIUS - CIRCLE_START_RADIUS) * progress * circleSize;
            float a = 1.0f - progress;

            int color = getCircleColor(progress);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            buffer.vertex(matrix, (float) (jump.x - radius), (float) jump.y, (float) (jump.z - radius)).texture(0, 0).color(r, g, b, a);
            buffer.vertex(matrix, (float) (jump.x - radius), (float) jump.y, (float) (jump.z + radius)).texture(0, 1).color(r, g, b, a);
            buffer.vertex(matrix, (float) (jump.x + radius), (float) jump.y, (float) (jump.z + radius)).texture(1, 1).color(r, g, b, a);
            buffer.vertex(matrix, (float) (jump.x + radius), (float) jump.y, (float) (jump.z - radius)).texture(1, 0).color(r, g, b, a);
            hasVertices = true;
        }

        var built = buffer.end();
        if (hasVertices) BufferRenderer.drawWithGlobalProgram(built);

        matrices.pop();
        RenderSystem.enableCull();
    }

    private static int getCircleColor(float progress) {
        String colorMode = LexoraGui.modeSettings.getOrDefault("Jump Circle Color Mode", "Theme");
        if (colorMode.equals("Custom")) {
            // ВАЖНО: colorSettings хранит цвета в формате HSV (hue, saturation, value),
            // как и везде в остальном GUI (см. ModernClickGui#getThemeColor). Раньше тут
            // компоненты трактовались как сырые RGB, из-за чего белый цвет (s=0, v=1)
            // превращался в синий/фиолетовый оттенок.
            float[] c = LexoraGui.colorSettings.getOrDefault("Jump Circle Custom Color", new float[]{0.2f, 1f, 0.93f});
            return Color.HSBtoRGB(c[0], c[1], c[2]) & 0xFFFFFF;
        }
        return LexoraGui.getThemeColor(progress * 0.5f);
    }

    // ---------------------------------------------------------------------
    // Mode 2: волна по блокам — мягкая заливка (свечение) + чёткий контур
    // ---------------------------------------------------------------------

    private record WaveBlock(BlockPos pos, float localAlpha, float r, float g, float b) {}

    private static void renderWaves(MatrixStack matrices, Camera camera, MinecraftClient mc, float circleSize, float circleSpeed, long currentTime) {
        float sizeScale = Math.min(circleSize, WAVE_SIZE_CAP);

        List<WaveBlock> blocks = new ArrayList<>();
        forEachWaveBlock(mc, sizeScale, circleSpeed, currentTime, blocks::add);

        if (blocks.isEmpty()) return; // нечего рисовать в этом кадре — иначе end() крашнется на пустом буфере

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);

        matrices.push();
        Vec3d camPos = camera.getPos();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // свечение — чуть тише
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        BufferBuilder fillBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (WaveBlock wb : blocks) {
            addBoxFill(fillBuffer, matrix, wb.pos(), wb.r(), wb.g(), wb.b(), wb.localAlpha() * 0.3f);
        }
        BufferRenderer.drawWithGlobalProgram(fillBuffer.end());

        // контур — чуть ярче
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.lineWidth(3.0f);

        BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
        for (WaveBlock wb : blocks) {
            addBoxOutline(lineBuffer, matrix, wb.pos(), wb.r(), wb.g(), wb.b(), Math.min(1.0f, wb.localAlpha() * 2.2f));
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        matrices.pop();
        RenderSystem.enableCull();
    }

    @FunctionalInterface
    private interface WaveBlockConsumer {
        void accept(WaveBlock block);
    }

    private static void forEachWaveBlock(MinecraftClient mc, float sizeScale, float circleSpeed, long currentTime, WaveBlockConsumer consumer) {
        // Mutable-курсор для проверки блока — не аллоцируем BlockPos на каждую
        // клетку перебора, только на те, что реально пойдут в рендер.
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (JumpEvent jump : activeJumps) {
            float progress = (currentTime - jump.startTime) / (1000.0f / circleSpeed);
            if (progress >= 1.0f) {
                jump.isFinished = true;
                continue;
            }

            float radius = (WAVE_BASE_RADIUS + (WAVE_MAX_RADIUS - WAVE_BASE_RADIUS) * progress) * sizeScale;
            float alpha = (float) Math.pow(1.0 - progress, 1.5);
            if (alpha < 0.02f) continue;

            int centerX = MathHelper.floor(jump.x);
            int centerY = MathHelper.floor(jump.y);
            int centerZ = MathHelper.floor(jump.z);

            float ringHalfWidth = WAVE_RING_WIDTH / 2.0f;
            float ringCenter = radius - ringHalfWidth;

            float outerR = radius;                       // внешний край кольца (с запасом falloff'а)
            float innerR = radius - WAVE_RING_WIDTH;      // внутренний край кольца (может быть < 0 в начале анимации)

            if (outerR <= 0f) continue;

            int ir = MathHelper.ceil(outerR);

            for (int dx = -ir; dx <= ir; dx++) {
                float adx = Math.abs(dx);
                if (adx > outerR) continue;

                float outerZ = (float) Math.sqrt(Math.max(0.0, (double) outerR * outerR - (double) dx * dx));

                // Вместо перебора всего квадрата (-ir..ir)x(-ir..ir) аналитически считаем,
                // в каких диапазонах dz вообще может лежать тонкое кольцо для данного dx.
                // На большом радиусе с узким кольцом это в разы сокращает число итераций.
                float[][] zRanges;
                if (adx >= innerR) {
                    // на этом dx кольцо непрерывно (внутренний край не пересекается)
                    zRanges = new float[][]{{-outerZ, outerZ}};
                } else {
                    float innerZ = (float) Math.sqrt(Math.max(0.0, (double) innerR * innerR - (double) dx * dx));
                    zRanges = new float[][]{{-outerZ, -innerZ}, {innerZ, outerZ}};
                }

                for (float[] range : zRanges) {
                    int zStart = MathHelper.ceil(range[0]);
                    int zEnd = MathHelper.floor(range[1]);

                    for (int dz = zStart; dz <= zEnd; dz++) {
                        double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                        float ringDist = (float) Math.abs(dist - ringCenter);
                        if (ringDist > ringHalfWidth) continue;

                        float falloff = 1.0f - (ringDist / ringHalfWidth); // плавный край вместо жёсткого обреза
                        float ringT = MathHelper.clamp((float) (dist / Math.max(radius, 0.01)), 0f, 1f);

                        // Цвет считаем один раз на (dx, dz) — раньше он пересчитывался
                        // на каждый Y-слой и отдельно для заливки и контура.
                        int color = getWaveColor(ringT, progress);
                        float r = ((color >> 16) & 0xFF) / 255.0f;
                        float g = ((color >> 8) & 0xFF) / 255.0f;
                        float b = (color & 0xFF) / 255.0f;

                        for (int dy = -WAVE_Y_RANGE; dy <= WAVE_Y_RANGE; dy++) {
                            float verticalFalloff = 1.0f - (Math.abs(dy) / (WAVE_Y_RANGE + 1.0f));
                            float localAlpha = alpha * falloff * verticalFalloff;
                            if (dy < 0) localAlpha *= 0.35f;
                            if (localAlpha < 0.02f) continue;

                            cursor.set(centerX + dx, centerY + dy, centerZ + dz);
                            BlockState state = mc.world.getBlockState(cursor);
                            if (shouldSkipBlock(mc.world, cursor, state)) continue;

                            // Аллоцируем настоящий BlockPos только для блока, который реально идёт в рендер
                            consumer.accept(new WaveBlock(cursor.toImmutable(), localAlpha, r, g, b));
                        }
                    }
                }
            }
        }
    }

    private static boolean shouldSkipBlock(BlockView world, BlockPos pos, BlockState state) {
        if (state.isAir()) return true;
        if (state.isOpaque()) return false; // быстрый путь: цельные блоки всегда считаем полом
        return state.getCollisionShape(world, pos).isEmpty();
    }

    private static int getWaveColor(float ringT, float progress) {
        String colorMode = LexoraGui.modeSettings.getOrDefault("Jump Circle Color Mode", "Theme");
        if (colorMode.equals("Custom")) {
            // См. комментарий в getCircleColor: цвет хранится как HSV, конвертируем через HSBtoRGB.
            float[] c = LexoraGui.colorSettings.getOrDefault("Jump Circle Custom Color", new float[]{0.2f, 1f, 0.93f});
            return Color.HSBtoRGB(c[0], c[1], c[2]) & 0xFFFFFF;
        }
        int start = LexoraGui.getThemeColor((progress * 0.4f) % 1.0f);
        int end = LexoraGui.getThemeColor((progress * 0.4f + 0.5f) % 1.0f);
        return lerpColor(start, end, ringT);
    }

    private static int lerpColor(int colorA, int colorB, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int ar = (colorA >> 16) & 0xFF, ag = (colorA >> 8) & 0xFF, ab = colorA & 0xFF;
        int br = (colorB >> 16) & 0xFF, bg = (colorB >> 8) & 0xFF, bb = colorB & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int b = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void addBoxFill(BufferBuilder buf, Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        float x0 = pos.getX() + 0.002f, x1 = pos.getX() + 0.998f;
        float y0 = pos.getY() + 0.002f, y1 = pos.getY() + 0.998f;
        float z0 = pos.getZ() + 0.002f, z1 = pos.getZ() + 0.998f;

        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a);
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a);
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a);
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a);

        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a);
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a);
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a);

        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a);
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a);
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a);
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a);

        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a);
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a);
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a);

        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a);
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a);
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a);
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a);

        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a);
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a);
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a);
    }

    private static void addBoxOutline(BufferBuilder buf, Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        float x0 = pos.getX() + 0.002f, x1 = pos.getX() + 0.998f;
        float y0 = pos.getY() + 0.002f, y1 = pos.getY() + 0.998f;
        float z0 = pos.getZ() + 0.002f, z1 = pos.getZ() + 0.998f;

        line(buf, matrix, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(buf, matrix, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(buf, matrix, x1, y0, z1, x0, y0, z1, r, g, b, a);
        line(buf, matrix, x0, y0, z1, x0, y0, z0, r, g, b, a);

        line(buf, matrix, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(buf, matrix, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(buf, matrix, x1, y1, z1, x0, y1, z1, r, g, b, a);
        line(buf, matrix, x0, y1, z1, x0, y1, z0, r, g, b, a);

        line(buf, matrix, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(buf, matrix, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(buf, matrix, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(buf, matrix, x0, y0, z1, x0, y1, z1, r, g, b, a);
    }

    private static void line(BufferBuilder buf, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a) {
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a);
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a);
    }

    private static class JumpEvent {
        final double x, y, z;
        final long startTime;
        boolean isFinished = false;

        JumpEvent(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.startTime = System.currentTimeMillis();
        }
    }
}