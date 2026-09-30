package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.MsdfFont;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class PvpMarkerManager {

    private static final Identifier FONT_TEX = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    private static MsdfFont getFont() {
        if (msdfFont == null) {
            msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        }
        return msdfFont;
    }

    public static final class PvpActiveMarker {
        public final String id;
        public final String title;
        public final String subText;
        public final double x, y, z;
        public final ItemStack stack;
        public final long startTime;
        public final long maxTime; // ms
        public final int color;
        public final Box box;
        public final double circleRadius;

        public float floatOffset = -15f;
        public float floatAlpha = 0f;
        public float screenAlpha = 0f;

        public PvpActiveMarker(String id, String title, String subText, double x, double y, double z,
                               ItemStack stack, float durationSec, int color, Box box, double circleRadius) {
            this.id = id;
            this.title = title;
            this.subText = subText;
            this.x = x;
            this.y = y;
            this.z = z;
            this.stack = stack;
            this.startTime = System.currentTimeMillis();
            this.maxTime = (long) (durationSec * 1000L);
            this.color = color;
            this.box = box;
            this.circleRadius = circleRadius;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - startTime >= maxTime;
        }

        public float getRemainingSeconds() {
            long remaining = Math.max(0, maxTime - (System.currentTimeMillis() - startTime));
            return remaining / 1000.0f;
        }
    }

    private static final List<PvpActiveMarker> markers = new CopyOnWriteArrayList<>();

    public static void addMarker(String id, String title, String subText, double x, double y, double z,
                                 ItemStack stack, float durationSec, int color, Box box) {
        addMarker(id, title, subText, x, y, z, stack, durationSec, color, box, 0.0);
    }

    public static void addMarker(String id, String title, String subText, double x, double y, double z,
                                 ItemStack stack, float durationSec, int color, Box box, double circleRadius) {
        markers.removeIf(m -> m.id.equalsIgnoreCase(id));
        PvpActiveMarker marker = new PvpActiveMarker(id, title, subText, x, y, z, stack, durationSec, color, box, circleRadius);
        markers.add(marker);
    }

    public static void clear() {
        markers.clear();
    }

    public static void tick() {
        markers.removeIf(PvpActiveMarker::isExpired);

        for (PvpActiveMarker m : markers) {
            m.floatOffset += (0f - m.floatOffset) * 0.10f;
            m.floatAlpha += (1f - m.floatAlpha) * 0.12f;
        }
    }

    public static void render3D(MatrixStack matrices, Camera camera, float tickDelta) {
        if (markers.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        for (PvpActiveMarker m : markers) {
            float remain = m.getRemainingSeconds();
            if (remain <= 0) continue;

            // Проверка игроков внутри зоны (подсветка зеленым при наличии цели)
            boolean enemyInside = false;
            if (m.box != null) {
                for (PlayerEntity entity : mc.world.getPlayers()) {
                    if (entity == mc.player || !entity.isAlive() || entity.isSpectator()) continue;
                    if (entity.getBoundingBox().intersects(m.box)) {
                        enemyInside = true;
                        break;
                    }
                }
            } else if (m.circleRadius > 0.1) {
                double rSq = m.circleRadius * m.circleRadius;
                Vec3d c = new Vec3d(m.x, m.y, m.z);
                for (PlayerEntity entity : mc.world.getPlayers()) {
                    if (entity == mc.player || !entity.isAlive() || entity.isSpectator()) continue;
                    if (entity.getPos().squaredDistanceTo(c) <= rSq) {
                        enemyInside = true;
                        break;
                    }
                }
            }

            int activeColor = enemyInside ? 0xFF00FF00 : m.color;

            if (m.box != null) {
                renderBoxFill(matrices, m.box, activeColor);
                renderBoxLines(mat, immediate, m.box, activeColor);
            } else if (m.circleRadius > 0.1) {
                renderCircle(mat, immediate, new Vec3d(m.x, m.y + 0.05, m.z), m.circleRadius, activeColor);
            }
        }

        RenderSystem.disableDepthTest();
        immediate.draw();
        RenderSystem.enableDepthTest();

        matrices.pop();
    }

    public static void renderHud(DrawContext ctx, Camera camera, float tickDelta) {
        if (markers.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d camPos = camera.getPos();
        float guiW = mc.getWindow().getScaledWidth();
        float guiH = mc.getWindow().getScaledHeight();
        float aspect = guiW / guiH;
        float tan = (float) Math.tan(Math.toRadians(mc.options.getFov().getValue()) * 0.5);

        for (PvpActiveMarker m : markers) {
            double posX = m.x;
            double posY = m.box != null ? (m.box.maxY + 0.6) : (m.y + 1.8);
            double posZ = m.z;

            Vec3d targetPos = new Vec3d(posX, posY, posZ);
            double dist = camPos.distanceTo(targetPos);

            Vector3f rel = new Vector3f(
                    (float) (targetPos.x - camPos.x),
                    (float) (targetPos.y - camPos.y),
                    (float) (targetPos.z - camPos.z)
            );
            new Quaternionf(camera.getRotation()).conjugate().transform(rel);

            boolean inFront = rel.z() < -0.05f;
            float ndcX = inFront ? (rel.x() / (-rel.z() * tan * aspect)) : 0f;
            float ndcY = inFront ? (rel.y() / (-rel.z() * tan)) : 0f;

            float targetAlpha;
            if (!inFront) {
                targetAlpha = 0f;
            } else {
                float absX = Math.abs(ndcX);
                float absY = Math.abs(ndcY);
                float fadeX = absX < 0.78f ? 1f : absX > 0.92f ? 0f : (0.92f - absX) / (0.92f - 0.78f);
                float fadeY = absY < 0.74f ? 1f : absY > 0.88f ? 0f : (0.88f - absY) / (0.88f - 0.74f);
                targetAlpha = Math.min(fadeX, fadeY);
            }

            m.screenAlpha += (targetAlpha - m.screenAlpha) * 0.20f;
            if (m.screenAlpha < 0.005f) {
                m.screenAlpha = 0f;
                continue;
            }
            if (m.screenAlpha > 0.995f) m.screenAlpha = 1f;

            float finalAlpha = MathHelper.clamp(m.floatAlpha * m.screenAlpha, 0f, 1f);
            if (finalAlpha < 0.02f) continue;

            float sx = (ndcX * 0.5f + 0.5f) * guiW;
            float sy = (0.5f - ndcY * 0.5f) * guiH;

            drawMarkerPill(ctx, m, sx, sy, dist, finalAlpha);
        }
    }

    private static void drawMarkerPill(DrawContext ctx, PvpActiveMarker m, float sx, float sy, double dist, float alpha) {
        float scale = MathHelper.clamp(1.0f - (float) (dist / 140.0), 0.75f, 1.05f);

        float nameSize = 8.5f * scale;
        float subSize = 7.5f * scale;

        String name = m.title;
        float remSec = m.getRemainingSeconds();
        String timerText = String.format(Locale.US, "%.1fс", remSec);
        if (m.subText != null && !m.subText.isEmpty()) {
            timerText += " • " + m.subText;
        }

        float nameW = getFont().getWidth(name, nameSize);
        float subW = getFont().getWidth(timerText, subSize);
        float textW = Math.max(nameW, subW);

        float iconSize = 16f * scale;
        float padX = 6.0f * scale;
        float padY = 4.0f * scale;
        float gap = 5.0f * scale;

        float panelH = Math.max(22f * scale, iconSize + padY * 2f);
        float panelW = padX + iconSize + gap + textW + padX + 2f * scale;
        float radius = 6f * scale;

        float floatY = m.floatOffset * scale;
        float panelX = sx - panelW / 2f;
        float panelY = sy - panelH / 2f + floatY;

        int alphaI = MathHelper.clamp((int) (255 * alpha), 0, 255);
        if (alphaI < 6) return;

        // Фон градиент
        int topBg = (((int) (225 * alpha)) << 24) | 0x181822;
        int botBg = (((int) (240 * alpha)) << 24) | 0x0E0E14;
        RoundedRectShader.drawVerticalGradient(ctx, panelX, panelY, panelW, panelH, radius, topBg, botBg);

        // Акцентная обводка (мерцает, если осталось меньше 3 секунд)
        int accent = m.color & 0xFFFFFF;
        if (remSec < 3.0f && ((System.currentTimeMillis() / 200) % 2 == 0)) {
            accent = 0xFF3333; // Красный пульс перед окончанием
        }
        int borderA = (int) (80 * alpha);
        int borderCol = (borderA << 24) | accent;
        RoundedRectShader.drawOutline(ctx, panelX, panelY, panelW, panelH, radius, 0.8f, borderCol);

        // Иконка предмета
        float iconX = panelX + padX;
        float iconY = panelY + (panelH - iconSize) / 2f;
        if (m.stack != null && !m.stack.isEmpty()) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(iconX, iconY, 0);
            ctx.getMatrices().scale(iconSize / 16f, iconSize / 16f, 1f);
            ctx.drawItem(m.stack, 0, 0);
            ctx.getMatrices().pop();
        }

        // Текст справа от иконки
        float textX = iconX + iconSize + gap;
        float textBlockH = nameSize + 2.0f * scale + subSize;
        float nameY = panelY + (panelH - textBlockH) / 2f;
        float distY = nameY + nameSize + 2.0f * scale;

        int shadowA = MathHelper.clamp((int) (alphaI * 0.6f), 0, 180);
        getFont().draw(ctx.getMatrices(), name, textX + 0.6f, nameY + 0.6f, nameSize, (shadowA << 24) | 0x000000);
        getFont().draw(ctx.getMatrices(), name, textX, nameY, nameSize, (alphaI << 24) | 0xFFEDEDF2);

        getFont().draw(ctx.getMatrices(), timerText, textX + 0.6f, distY + 0.6f, subSize, (shadowA << 24) | 0x000000);
        getFont().draw(ctx.getMatrices(), timerText, textX, distY, subSize, (alphaI << 24) | accent);
    }

    private static void renderBoxFill(MatrixStack matrices, Box box, int outlineColor) {
        int r = (outlineColor >> 16) & 0xFF;
        int g = (outlineColor >> 8) & 0xFF;
        int b = outlineColor & 0xFF;
        float a = 0.15f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        buffer.vertex(mat, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, minX, minY, maxZ).color(r, g, b, a);

        buffer.vertex(mat, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(mat, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, maxY, minZ).color(r, g, b, a);

        buffer.vertex(mat, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(mat, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, minY, minZ).color(r, g, b, a);

        buffer.vertex(mat, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, minX, maxY, maxZ).color(r, g, b, a);

        buffer.vertex(mat, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(mat, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, minX, maxY, minZ).color(r, g, b, a);

        buffer.vertex(mat, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(mat, maxX, minY, maxZ).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void renderBoxLines(Matrix4f mat, VertexConsumerProvider.Immediate immediate, Box box, int outlineColor) {
        VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getLines());
        int rO = (outlineColor >> 16) & 0xFF;
        int gO = (outlineColor >> 8) & 0xFF;
        int bO = outlineColor & 0xFF;
        int aO = 0xFF;

        drawLine(lineBuffer, mat, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, rO, gO, bO, aO);

        drawLine(lineBuffer, mat, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, rO, gO, bO, aO);

        drawLine(lineBuffer, mat, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, rO, gO, bO, aO);
        drawLine(lineBuffer, mat, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, rO, gO, bO, aO);
    }

    private static void renderCircle(Matrix4f mat, VertexConsumerProvider.Immediate immediate, Vec3d center, double radius, int outlineColor) {
        VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getLines());
        int rO = (outlineColor >> 16) & 0xFF;
        int gO = (outlineColor >> 8) & 0xFF;
        int bO = outlineColor & 0xFF;
        int aO = 0xFF;

        Vec3d prev = null;
        for (float angle = 0; angle <= 360; angle += 6.0f) {
            double x = center.x + Math.sin(Math.toRadians(angle)) * radius;
            double z = center.z + Math.cos(Math.toRadians(angle)) * radius;
            Vec3d curr = new Vec3d(x, center.y, z);

            if (prev != null) {
                lineBuffer.vertex(mat, (float) prev.x, (float) prev.y, (float) prev.z).color(rO, gO, bO, aO).normal(1, 0, 0);
                lineBuffer.vertex(mat, (float) curr.x, (float) curr.y, (float) curr.z).color(rO, gO, bO, aO).normal(1, 0, 0);
            }
            prev = curr;
        }
    }

    private static void drawLine(VertexConsumer buffer, Matrix4f mat,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 int r, int g, int b, int a) {
        buffer.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(1, 0, 0);
        buffer.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(1, 0, 0);
    }
}
