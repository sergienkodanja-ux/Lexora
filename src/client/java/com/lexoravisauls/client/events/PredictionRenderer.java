package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.modern.ModernGuiRender;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PredictionRenderer {

    // Сюда сохраняем только активные прогнозы (пока мы целимся).
    // Летящие снаряды после броска больше не отслеживаются, как ты и просил.
    private static final Map<String, PredictionSlot> predictionSlots = new HashMap<>();

    private static class PredictionSlot {
        List<Vec3d> trajectoryPoints;
        HitResult hit;
        Item icon;
        float secondsToImpact;
        boolean activeThisFrame = false;
    }

    private static PredictionSlot getSlot(String key) {
        return predictionSlots.computeIfAbsent(key, k -> new PredictionSlot());
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;

        if (mc.world == null || player == null) return;

        boolean moduleEnabled = LexoraGui.moduleStates.getOrDefault("Prediction", false);
        if (!moduleEnabled) {
            predictionSlots.clear();
            return;
        }

        // Сбрасываем флаг активности перед расчетом кадра
        for (PredictionSlot slot : predictionSlots.values()) {
            slot.activeThisFrame = false;
        }

        ItemStack stack = player.getMainHandStack();
        Item item = stack.getItem();
        boolean isBow = item instanceof BowItem;
        boolean isCrossbow = item instanceof CrossbowItem;
        boolean isTrident = item instanceof TridentItem;
        boolean isPotion = item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
        boolean isThrowable = item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderPearlItem || item instanceof ExperienceBottleItem;

        boolean holdingRelevantItem = isBow || isCrossbow || isTrident || isPotion || isThrowable;
        if (!holdingRelevantItem) return;

        boolean hasMultishot = false;
        if (isCrossbow) {
            try {
                hasMultishot = stack.getEnchantments().toString().contains("multishot");
            } catch (Exception ignored) {}
        }

        // Берём цвет темы (В сурсе: ColorUtils.getColor(0))
        float[] hsv = ClientData.colorSettings.getOrDefault("Theme Color 1", new float[]{300f / 360f, 0.75f, 1f});
        if (LexoraGui.colorSettings.containsKey("Theme Color 1")) {
            hsv = LexoraGui.colorSettings.get("Theme Color 1");
        }
        int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
        float cR = ((rgb >> 16) & 0xFF) / 255f;
        float cG = ((rgb >> 8) & 0xFF) / 255f;
        float cB = (rgb & 0xFF) / 255f;

        if (hasMultishot) {
            simulateAndDraw(matrices, camera, tickDelta, player, stack, item, isBow, isCrossbow, isTrident, isPotion, 0.0f, cR, cG, cB);
            simulateAndDraw(matrices, camera, tickDelta, player, stack, item, isBow, isCrossbow, isTrident, isPotion, -10.0f, cR, cG, cB);
            simulateAndDraw(matrices, camera, tickDelta, player, stack, item, isBow, isCrossbow, isTrident, isPotion, 10.0f, cR, cG, cB);
        } else {
            simulateAndDraw(matrices, camera, tickDelta, player, stack, item, isBow, isCrossbow, isTrident, isPotion, 0.0f, cR, cG, cB);
        }
    }

    private static void simulateAndDraw(MatrixStack matrices, Camera camera, float tickDelta, PlayerEntity player, ItemStack stack, Item item, boolean isBow, boolean isCrossbow, boolean isTrident, boolean isPotion, float yawOffset, float cR, float cG, float cB) {
        MinecraftClient mc = MinecraftClient.getInstance();

        float yaw = MathHelper.lerp(tickDelta, player.prevYaw, player.getYaw()) + yawOffset;
        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        if (isPotion) pitch -= 20.0f;

        double lerpX = MathHelper.lerp(tickDelta, player.prevX, player.getX());
        double lerpY = MathHelper.lerp(tickDelta, player.prevY, player.getY());
        double lerpZ = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());

        double eyeOffset = player.getEyeY() - player.getY();
        // В сурсе стартовая точка корректировалась, оставляем её чистой для точного расчёта
        Vec3d pos = new Vec3d(lerpX, lerpY + eyeOffset - 0.3f, lerpZ);

        float f = -MathHelper.sin(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
        float g = -MathHelper.sin(pitch * 0.017453292F);
        float h = MathHelper.cos(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
        Vec3d vel = new Vec3d(f, g, h).normalize();

        float velocityMulti = 1.5f;
        float gravity = 0.03f;

        if (isBow) {
            float charge = 1.0f;
            if (player.isUsingItem()) {
                int useTicks = stack.getMaxUseTime(player) - player.getItemUseTimeLeft();
                charge = useTicks / 20.0f;
                charge = (charge * charge + charge * 2.0f) / 3.0f;
                if (charge > 1.0f) charge = 1.0f;
            }
            velocityMulti = charge * 3.0f;
            gravity = 0.05f;
        } else if (isCrossbow) {
            velocityMulti = 3.15f;
            gravity = 0.05f;
        } else if (isTrident) {
            velocityMulti = 2.5f;
            gravity = 0.05f;
        } else if (isPotion) {
            velocityMulti = 0.5f;
            gravity = 0.05f;
        }

        vel = vel.multiply(velocityMulti);

        HitResult hit = null;
        int maxSimTicks = 300;
        int ticksElapsed = 0;

        List<Vec3d> trajectoryPoints = new ArrayList<>();
        trajectoryPoints.add(pos);

        for (int i = 0; i < maxSimTicks; i++) {
            Vec3d nextPos = pos.add(vel);

            HitResult blockHit = mc.world.raycast(new RaycastContext(pos, nextPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
            Vec3d endPos = nextPos;
            if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                endPos = blockHit.getPos();
                hit = blockHit;
            }

            Box box = new Box(pos.x, pos.y, pos.z, endPos.x, endPos.y, endPos.z).expand(1.0);
            double minDist = hit != null ? pos.squaredDistanceTo(hit.getPos()) : Double.MAX_VALUE;
            EntityHitResult entityHit = null;

            for (Entity e : mc.world.getOtherEntities(player, box)) {
                if (e.canHit() || e instanceof LivingEntity) {
                    Box eBox = e.getBoundingBox().expand(0.3);
                    Optional<Vec3d> opt = eBox.raycast(pos, endPos);
                    if (opt.isPresent()) {
                        double dist = pos.squaredDistanceTo(opt.get());
                        if (dist < minDist) {
                            minDist = dist;
                            entityHit = new EntityHitResult(e, opt.get());
                        }
                    }
                }
            }

            if (entityHit != null) {
                hit = entityHit;
            }

            ticksElapsed = i + 1;

            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                trajectoryPoints.add(hit.getPos());
                break;
            }

            pos = nextPos;
            trajectoryPoints.add(pos);
            vel = vel.multiply(0.99);
            vel = vel.add(0, -gravity, 0);
        }

        String slotKey = String.valueOf((int) yawOffset);
        PredictionSlot slot = getSlot(slotKey);
        slot.activeThisFrame = true;
        slot.trajectoryPoints = trajectoryPoints;
        slot.hit = hit;
        slot.icon = item;
        slot.secondsToImpact = ticksElapsed / 20.0f;

        if (hit == null || hit.getType() == HitResult.Type.MISS) return;

        drawTrajectoryRibbon(matrices, camera, trajectoryPoints, cR, cG, cB);
    }

    // =========================================================================
    // Визуал из сурса (3D): Рисуем аккуратную плавную линию цвета темы.
    // Убраны все квадраты, мишени, таргеты и зелья на конце.
    // =========================================================================
    private static void drawTrajectoryRibbon(MatrixStack matrices, Camera camera, List<Vec3d> points, float cR, float cG, float cB) {
        if (points.size() < 2) return;

        matrices.push();
        Vec3d camPos = camera.getPos();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        float halfWidth = 0.005f; // Максимально тонко (имитация glLineWidth = 1.5f из сурса)
        float alpha = 165f / 255f; // Значение ColorUtils.setAlpha(..., 165) из сурса

        List<double[]> quads = new ArrayList<>();
        int segments = points.size() - 1;
        for (int i = 0; i < segments; i++) {
            Vec3d a = points.get(i);
            Vec3d b = points.get(i + 1);

            Vec3d segDir = b.subtract(a);
            if (segDir.lengthSquared() < 1.0e-7) continue;

            Vec3d toSeg = a.add(b).multiply(0.5).subtract(camPos);
            if (toSeg.lengthSquared() < 1.0e-7) toSeg = new Vec3d(0, 1, 0);
            Vec3d perp = segDir.crossProduct(toSeg.normalize());
            double len = perp.length();
            if (len < 1.0e-7) continue;
            perp = perp.multiply(halfWidth / len);

            Vec3d a1v = a.add(perp), a2v = a.subtract(perp);
            Vec3d b1v = b.add(perp), b2v = b.subtract(perp);

            quads.add(new double[]{
                    a1v.x, a1v.y, a1v.z,
                    a2v.x, a2v.y, a2v.z,
                    b2v.x, b2v.y, b2v.z,
                    b1v.x, b1v.y, b1v.z
            });
        }

        if (!quads.isEmpty()) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (double[] q : quads) {
                buf.vertex(mat, (float) q[0], (float) q[1], (float) q[2]).color(cR, cG, cB, alpha);
                buf.vertex(mat, (float) q[3], (float) q[4], (float) q[5]).color(cR, cG, cB, alpha);
                buf.vertex(mat, (float) q[6], (float) q[7], (float) q[8]).color(cR, cG, cB, alpha);
                buf.vertex(mat, (float) q[9], (float) q[10], (float) q[11]).color(cR, cG, cB, alpha);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        matrices.pop();
    }

    // =========================================================================
    // Визуал из сурса (2D HUD): Карточка на экране с таймингом в формате "1.2 сек."
    // =========================================================================
    public static void renderHud(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Рисуем карточки только для тех снарядов, куда мы целимся ПРЯМО СЕЙЧАС
        for (PredictionSlot slot : predictionSlots.values()) {
            if (slot.activeThisFrame && slot.hit != null && slot.hit.getType() != HitResult.Type.MISS) {
                drawPredictionCard(context, mc, slot, tickDelta);
            }
        }
    }

    private static void drawPredictionCard(DrawContext context, MinecraftClient mc, PredictionSlot slot, float tickDelta) {
        // Из сурса: Vector2f projection = ProjectionUtil.project(pos.x, pos.y - 0.3F, pos.z);
        Vec3d hitPos = slot.hit.getPos().add(0, -0.3, 0);
        Vec3d screen = worldToScreen(hitPos, mc, tickDelta);
        if (screen == null) return;

        // Из сурса: String text = String.format("%.1f" + " сек.", time);
        String timeText = String.format(java.util.Locale.US, "%.1f сек.", slot.secondsToImpact);
        float fontSize = 5f; // Шрифт мельче, как в сурсе
        float textW = width(timeText, fontSize);

        // Из сурса: float textWidth = width + 8 + 8;
        float cardW = textW + 16f;
        float cardH = 9f; // 12 - 3 из сурса

        float posX = (float) screen.x - cardW / 2f;
        float posY = (float) screen.y;

        // Из сурса: ColorUtils.rgba(24, 24, 24, 80)
        int bgColor = 0x50181818;

        // Рисуем фон (сдвиги из сурса: posX + 2, posY + 2 - 3)
        RoundedRectShader.draw(context, (int) posX + 2, (int) posY - 1, (int) cardW - 4, (int) cardH, 2f, bgColor);

        // Рисуем иконку предмета
        if (slot.icon != null) {
            context.getMatrices().push();
            // Сдвиг иконки из сурса: posX + 4, posY + 2 + 1 - 4
            context.getMatrices().translate(posX + 4, posY - 1, 0);
            context.getMatrices().scale(0.5f, 0.5f, 1f); // Уменьшаем дефолтные 16x16 до 8x8
            context.drawItem(slot.icon.getDefaultStack(), 0, 0);
            context.getMatrices().pop();
        }

        // Рисуем текст (Сдвиг из сурса: posX + 14, posY + 4.5f - 4)
        PotionHudRenderer.getFont().draw(context.getMatrices(), timeText, posX + 14, posY + 0.5f, fontSize, 0xFFFFFFFF);
    }

    private static float width(String text, float size) {
        if (text == null || text.isBlank()) return 0f;
        return PotionHudRenderer.getFont().getWidth(text, size);
    }

    private static Vec3d worldToScreen(Vec3d worldPos, MinecraftClient mc, float tickDelta) {
        if (mc.gameRenderer == null) return null;
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        float fov = mc.options.getFov().getValue();
        Matrix4f projMat = mc.gameRenderer.getBasicProjectionMatrix(fov);

        Quaternionf rot = new Quaternionf(camera.getRotation());
        rot.conjugate();
        Matrix4f viewMat = new Matrix4f().rotation(rot);

        Vector4f pos = new Vector4f(
                (float) (worldPos.x - camPos.x),
                (float) (worldPos.y - camPos.y),
                (float) (worldPos.z - camPos.z),
                1.0f);

        pos.mul(viewMat);
        pos.mul(projMat);

        if (pos.w() <= 0.0001f) return null;

        float ndcX = pos.x() / pos.w();
        float ndcY = pos.y() / pos.w();

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        double sx = (ndcX * 0.5 + 0.5) * screenW;
        double sy = (1.0 - (ndcY * 0.5 + 0.5)) * screenH;

        return new Vec3d(sx, sy, pos.w());
    }
}