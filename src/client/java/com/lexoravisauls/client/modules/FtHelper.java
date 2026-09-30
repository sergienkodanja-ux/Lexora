package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.NotifManager;
import com.lexoravisauls.client.utils.PvpMarkerManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class FtHelper {

    private static final int IDX_DEZKA = 0;
    private static final int IDX_YAVKA = 1;
    private static final int IDX_FIRE_CHARGE = 2;
    private static final int IDX_GOD_AURA = 3;
    private static final int IDX_TRAPKA = 4;
    private static final int IDX_PLAST = 5;
    private static final int IDX_SNOWBALL = 6;

    private static final int OUTLINE_ALPHA = 0xFF;
    private static final float TRANSITION_DURATION = 0.5f;
    private static final float CIRCLE_STEP_DEGREES = 5.0f;
    private static final double PLAST_EXTRA_EXPAND = 0.5d;
    private static final double PLAST_SURFACE_OFFSET = 0.01d;
    private static final double DEZKA_RADIUS = 10.0d;
    private static final double YAVKA_RADIUS = 10.0d;
    private static final double GOD_AURA_RADIUS = 2.0d;

    private static final double DRAGON_SKIN_SIZE = 7.0d;
    private static final double DRAGON_PLAST_DEPTH = 2.0d;

    private static final double SNOWBALL_RADIUS = 7.0d;
    private static final double SNOWBALL_SPEED = 1.5d;
    private static final double SNOWBALL_GRAVITY = 0.03d;
    private static final double SNOWBALL_DRAG = 0.99d;
    private static final int SNOWBALL_MAX_STEPS = 160;
    private static final int SNOWBALL_SUBSTEPS = 6;

    private static final int DEZKA_COLOR = 0xFF005500;
    private static final int YAVKA_COLOR = 0xFF999999;
    private static final int FIRE_CHARGE_COLOR = 0xFF550000;
    private static final int GOD_AURA_COLOR = 0xFF009999;
    private static final int TRAPKA_COLOR = 0xFF8B4513;
    private static final int PLAST_COLOR = 0xFF333333;
    private static final int SNOWBALL_COLOR = 0xFFA0DCFF;

    private static final int HIT_COLOR = 0xFF00FF00;

    private static int currentOutlineColor = 0;
    private static int targetOutlineColor = 0;
    private static float transitionTimer = 0.0f;
    private static boolean lastPlayersInRadius = false;
    private static int activeTransitionItem = -1;

    // Логика таймеров трапки и пласта
    private static boolean trapUsePending = false;
    private static boolean lastUsePressed = false;
    private static long trapUsePendingUntil = 0L;
    private static int trapCountBeforeUse = 0;

    private static boolean plastUsePending = false;
    private static boolean lastPlastUsePressed = false;
    private static long plastUsePendingUntil = 0L;
    private static int plastCountBeforeUse = 0;

    private static class SnowballPrediction {
        List<Vec3d> trajectory;
        Vec3d landingPos;

        SnowballPrediction(List<Vec3d> t, Vec3d l) {
            trajectory = t;
            landingPos = l;
        }
    }

    private static boolean isState(String key, boolean def) {
        return com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault(key, LexoraGui.moduleStates.getOrDefault(key, def));
    }

    private static String getMode(String key, String def) {
        return com.lexoravisauls.client.core.ClientData.modeSettings.getOrDefault(key, LexoraGui.modeSettings.getOrDefault(key, def));
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (!isState("Ft Helper", false)) {
            activeTransitionItem = -1;
            trapUsePending = false;
            lastUsePressed = false;
            trapUsePendingUntil = 0L;
            trapCountBeforeUse = 0;
            plastUsePending = false;
            lastPlastUsePressed = false;
            plastUsePendingUntil = 0L;
            plastCountBeforeUse = 0;
            return;
        }

        ClientPlayerEntity player = mc.player;

        // Обработка таймеров трапки и пласта
        handleTrapkaUse(mc, player);
        handlePlastUse(mc, player);

        int activeItemIndex = resolveActiveItemIndex(player.getMainHandStack(), player.getOffHandStack());
        if (activeItemIndex == -1) {
            activeTransitionItem = -1;
            return;
        }

        Vec3d centerPos = interpolate(player, tickDelta).add(0.0, player.getHeight() - 1.4, 0.0);
        Vec3d camPos = camera.getPos();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        if (activeItemIndex == IDX_DEZKA) {
            renderRadiusPreset(mat, immediate, player, centerPos, DEZKA_RADIUS, IDX_DEZKA, DEZKA_COLOR, tickDelta);
        } else if (activeItemIndex == IDX_YAVKA) {
            renderRadiusPreset(mat, immediate, player, centerPos, YAVKA_RADIUS, IDX_YAVKA, YAVKA_COLOR, tickDelta);
        } else if (activeItemIndex == IDX_FIRE_CHARGE) {
            renderRadiusPreset(mat, immediate, player, centerPos, 10.0, IDX_FIRE_CHARGE, FIRE_CHARGE_COLOR, tickDelta);
        } else if (activeItemIndex == IDX_GOD_AURA) {
            renderRadiusPreset(mat, immediate, player, centerPos, GOD_AURA_RADIUS, IDX_GOD_AURA, GOD_AURA_COLOR, tickDelta);
        } else if (activeItemIndex == IDX_SNOWBALL) {
            renderSnowballPrediction(mat, immediate, player, IDX_SNOWBALL, SNOWBALL_COLOR, tickDelta);
        } else if (activeItemIndex == IDX_TRAPKA) {
            boolean isDragon = getMode("FT Трапка Скин", "Обычный").equals("Драконий");
            Box cube = getTrapkaBox(player, tickDelta, isDragon);
            boolean highlight = hasPlayersInBox(mc, player, cube);
            updateTransition(IDX_TRAPKA, highlight, TRAPKA_COLOR, HIT_COLOR, tickDelta);

            renderBoxFill(matrices, cube, currentOutlineColor);
            renderBox(mat, immediate, cube, currentOutlineColor);
        } else if (activeItemIndex == IDX_PLAST) {
            boolean isDragon = getMode("FT Пласт Скин", "Обычный").equals("Драконий");
            Box plane = getPlastBox(mc, player, tickDelta, isDragon);
            boolean highlight = hasPlayersInBox(mc, player, plane);
            updateTransition(IDX_PLAST, highlight, PLAST_COLOR, HIT_COLOR, tickDelta);

            renderBoxFill(matrices, plane, currentOutlineColor);
            renderBox(mat, immediate, plane, currentOutlineColor);
        }

        RenderSystem.disableDepthTest();
        immediate.draw();
        RenderSystem.enableDepthTest();

        matrices.pop();
    }

    private static void handleTrapkaUse(MinecraftClient mc, ClientPlayerEntity player) {
        long now = System.currentTimeMillis();

        boolean usePressed = mc.options.useKey.isPressed();
        boolean holdingTrapka = isHoldingItem(player, Items.NETHERITE_SCRAP);

        if (!lastUsePressed && usePressed && holdingTrapka) {
            trapUsePending = true;
            trapUsePendingUntil = now + 500L;
            trapCountBeforeUse = countItem(player, Items.NETHERITE_SCRAP);
        }

        lastUsePressed = usePressed;

        if (!trapUsePending) return;

        if (now > trapUsePendingUntil) {
            trapUsePending = false;
            return;
        }

        int currentCount = countItem(player, Items.NETHERITE_SCRAP);

        if (currentCount == trapCountBeforeUse - 1) {
            boolean isDragon = getMode("FT Трапка Скин", "Обычный").equals("Драконий");
            float seconds = isDragon ? 27.4f : 15.0f;
            if (isState("FT Таймер Трапки", true)) {
                NotifManager.startTimer("ft_trapka", "Трапка", seconds, NotifManager.NotifType.WARNING);
            }
            Box cube = getTrapkaBox(player, 1.0f, isDragon);
            Vec3d center = cube.getCenter();
            PvpMarkerManager.addMarker("ft_trapka", "Трапка", isDragon ? "Драконья" : "Обычная",
                    center.x, cube.maxY + 0.3, center.z,
                    new ItemStack(Items.NETHERITE_SCRAP), seconds, TRAPKA_COLOR, cube);
            trapUsePending = false;
        }
    }

    private static void handlePlastUse(MinecraftClient mc, ClientPlayerEntity player) {
        long now = System.currentTimeMillis();

        boolean usePressed = mc.options.useKey.isPressed();
        boolean holdingPlast = isHoldingItem(player, Items.DRIED_KELP);

        if (!lastPlastUsePressed && usePressed && holdingPlast) {
            plastUsePending = true;
            plastUsePendingUntil = now + 500L;
            plastCountBeforeUse = countItem(player, Items.DRIED_KELP);
        }

        lastPlastUsePressed = usePressed;

        if (!plastUsePending) return;

        if (now > plastUsePendingUntil) {
            plastUsePending = false;
            return;
        }

        int currentCount = countItem(player, Items.DRIED_KELP);

        if (currentCount == plastCountBeforeUse - 1) {
            boolean isDragon = getMode("FT Пласт Скин", "Обычный").equals("Драконий");
            float seconds = isDragon ? 30.0f : 45.0f;
            if (isState("FT Таймер Пласта", true)) {
                NotifManager.startTimer("ft_plast", "Пласт", seconds, NotifManager.NotifType.WARNING);
            }
            Box plane = getPlastBox(mc, player, 1.0f, isDragon);
            Vec3d center = plane.getCenter();
            PvpMarkerManager.addMarker("ft_plast", "Пласт", isDragon ? "Драконий" : "Обычный",
                    center.x, plane.maxY + 0.3, center.z,
                    new ItemStack(Items.DRIED_KELP), seconds, PLAST_COLOR, plane);
            plastUsePending = false;
        }
    }

    private static boolean isHoldingItem(ClientPlayerEntity player, Item item) {
        return player.getMainHandStack().getItem() == item
                || player.getOffHandStack().getItem() == item;
    }

    private static int countItem(ClientPlayerEntity player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private static int resolveActiveItemIndex(ItemStack mainHand, ItemStack offHand) {
        int mainIndex = getEnabledItemIndex(mainHand.getItem());
        return mainIndex != -1 ? mainIndex : getEnabledItemIndex(offHand.getItem());
    }

    private static int getEnabledItemIndex(Item item) {
        if (item == Items.ENDER_EYE && isState("FT Дезка", true)) return IDX_DEZKA;
        if (item == Items.SUGAR && isState("FT Явка", true)) return IDX_YAVKA;
        if (item == Items.FIRE_CHARGE && isState("FT Огненный Шар", true)) return IDX_FIRE_CHARGE;
        if (item == Items.PHANTOM_MEMBRANE && isState("FT Божья Аура", true)) return IDX_GOD_AURA;
        if (item == Items.NETHERITE_SCRAP && isState("FT Трапка", true)) return IDX_TRAPKA;
        if (item == Items.DRIED_KELP && isState("FT Пласт", true)) return IDX_PLAST;
        if (item == Items.SNOWBALL && isState("FT Снежок", true)) return IDX_SNOWBALL;
        return -1;
    }

    private static void renderRadiusPreset(Matrix4f mat, VertexConsumerProvider.Immediate immediate, ClientPlayerEntity player, Vec3d centerPos, double radius, int itemIndex, int baseColor, float tickDelta) {
        boolean highlight = hasPlayersInRadius(MinecraftClient.getInstance(), player, centerPos, radius);
        updateTransition(itemIndex, highlight, baseColor, HIT_COLOR, tickDelta);
        renderRadiusCircle(mat, immediate, centerPos, radius, currentOutlineColor);
    }

    private static void renderSnowballPrediction(Matrix4f mat, VertexConsumerProvider.Immediate immediate, ClientPlayerEntity player, int itemIndex, int baseColor, float tickDelta) {
        SnowballPrediction prediction = predictSnowballPrediction(MinecraftClient.getInstance(), player, tickDelta);
        if (prediction == null || prediction.trajectory.size() < 2) return;

        Vec3d landingCenter = prediction.landingPos.add(0.0, 0.03, 0.0);
        boolean highlight = hasPlayersInRadius(MinecraftClient.getInstance(), player, landingCenter, SNOWBALL_RADIUS);
        updateTransition(itemIndex, highlight, baseColor, HIT_COLOR, tickDelta);

        renderTrajectory(mat, immediate, prediction.trajectory, currentOutlineColor);
        renderRadiusCircle(mat, immediate, landingCenter, SNOWBALL_RADIUS, currentOutlineColor);
    }

    private static SnowballPrediction predictSnowballPrediction(MinecraftClient mc, ClientPlayerEntity player, float tickDelta) {
        List<Vec3d> points = new ArrayList<>();
        Vec3d position = interpolate(player, tickDelta).add(0, player.getEyeHeight(player.getPose()), 0);
        Vec3d velocity = player.getRotationVec(tickDelta).normalize().multiply(SNOWBALL_SPEED);
        points.add(position);

        Vec3d landing = null;
        outer:
        for (int i = 0; i < SNOWBALL_MAX_STEPS; i++) {
            for (int stepIndex = 0; stepIndex < SNOWBALL_SUBSTEPS; stepIndex++) {
                Vec3d prev = position;
                Vec3d step = velocity.multiply(1.0 / SNOWBALL_SUBSTEPS);
                position = position.add(step);

                HitResult hit = mc.world.raycast(new RaycastContext(prev, position, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, player));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    landing = ((BlockHitResult) hit).getPos();
                    points.add(landing);
                    break outer;
                }

                points.add(position);
                if (position.y < mc.world.getBottomY() - 8.0) {
                    landing = position;
                    break outer;
                }

                double drag = Math.pow(SNOWBALL_DRAG, 1.0 / SNOWBALL_SUBSTEPS);
                velocity = velocity.subtract(0.0, SNOWBALL_GRAVITY / SNOWBALL_SUBSTEPS, 0.0).multiply(drag);
            }
        }

        if (points.size() < 2) return null;
        if (landing == null) landing = points.get(points.size() - 1);
        return new SnowballPrediction(points, landing);
    }

    private static void updateTransition(int itemIndex, boolean highlight, int baseColor, int highlightColor, float partialTicks) {
        if (activeTransitionItem != itemIndex) {
            activeTransitionItem = itemIndex;
            lastPlayersInRadius = false;
            transitionTimer = 0.0f;
            currentOutlineColor = withAlpha(baseColor, OUTLINE_ALPHA);
            targetOutlineColor = currentOutlineColor;
        }

        if (highlight != lastPlayersInRadius) {
            transitionTimer = 0.0f;
            lastPlayersInRadius = highlight;
        }

        targetOutlineColor = withAlpha(highlight ? highlightColor : baseColor, OUTLINE_ALPHA);

        transitionTimer = Math.min(transitionTimer + (partialTicks / TRANSITION_DURATION), 1.0f);
        currentOutlineColor = lerpColor(currentOutlineColor, targetOutlineColor, transitionTimer);
    }

    private static boolean hasPlayersInRadius(MinecraftClient mc, ClientPlayerEntity player, Vec3d centerPos, double radius) {
        double radiusSq = radius * radius;
        for (PlayerEntity entity : mc.world.getPlayers()) {
            if (entity == player || !entity.isAlive() || entity.isSpectator()) continue;
            if (entity.getPos().squaredDistanceTo(centerPos) <= radiusSq) return true;
        }
        return false;
    }

    private static boolean hasPlayersInBox(MinecraftClient mc, ClientPlayerEntity player, Box box) {
        for (PlayerEntity entity : mc.world.getPlayers()) {
            if (entity == player || !entity.isAlive() || entity.isSpectator()) continue;
            if (entity.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    private static Box getTrapkaBox(ClientPlayerEntity player, float tickDelta, boolean isDragon) {
        Vec3d position = interpolate(player, tickDelta);
        double cubeX = Math.floor(position.x) + 0.5;
        double cubeY = Math.floor(position.y) + 0.5 + 1.625;
        double cubeZ = Math.floor(position.z) + 0.5;
        float halfSize = isDragon ? (float) (DRAGON_SKIN_SIZE / 2.0) : 2.0f;

        return new Box(
                cubeX - halfSize, cubeY - halfSize, cubeZ - halfSize,
                cubeX + halfSize, cubeY + halfSize, cubeZ + halfSize
        );
    }

    private static Box getPlastBox(MinecraftClient mc, ClientPlayerEntity player, float tickDelta, boolean isDragon) {
        float width = isDragon ? (float) DRAGON_SKIN_SIZE : 4.0f;
        float height = isDragon ? (float) DRAGON_SKIN_SIZE : 4.0f;
        float thickness = isDragon ? (float) DRAGON_PLAST_DEPTH : 1.5f;
        float halfWidth = width / 2.0f;
        float halfHeight = height / 2.0f;
        float halfThickness = thickness / 2.0f;

        Vec3d lookVec = player.getRotationVec(tickDelta);
        Vec3d eyePos = interpolate(player, tickDelta).add(0, player.getEyeHeight(player.getPose()), 0);
        Direction normal = getDominantLookDirection(lookVec);
        double shift = halfThickness + PLAST_SURFACE_OFFSET;
        Vec3d center = eyePos.add(lookVec.multiply(4.0)).add(normal.getOffsetX() * shift, normal.getOffsetY() * shift, normal.getOffsetZ() * shift);

        Box plane = switch (normal.getAxis()) {
            case X -> new Box(center.x - halfThickness, center.y - halfHeight, center.z - halfWidth, center.x + halfThickness, center.y + halfHeight, center.z + halfWidth);
            case Y -> new Box(center.x - halfWidth, center.y - halfThickness, center.z - halfHeight, center.x + halfWidth, center.y + halfThickness, center.z + halfHeight);
            case Z -> new Box(center.x - halfWidth, center.y - halfHeight, center.z - halfThickness, center.x + halfWidth, center.y + halfHeight, center.z + halfThickness);
        };

        return switch (normal.getAxis()) {
            case X -> new Box(plane.minX, plane.minY - PLAST_EXTRA_EXPAND, plane.minZ - PLAST_EXTRA_EXPAND, plane.maxX, plane.maxY + PLAST_EXTRA_EXPAND, plane.maxZ + PLAST_EXTRA_EXPAND);
            case Y -> new Box(plane.minX - PLAST_EXTRA_EXPAND, plane.minY, plane.minZ - PLAST_EXTRA_EXPAND, plane.maxX + PLAST_EXTRA_EXPAND, plane.maxY, plane.maxZ + PLAST_EXTRA_EXPAND);
            case Z -> new Box(plane.minX - PLAST_EXTRA_EXPAND, plane.minY - PLAST_EXTRA_EXPAND, plane.minZ, plane.maxX + PLAST_EXTRA_EXPAND, plane.maxY + PLAST_EXTRA_EXPAND, plane.maxZ);
        };
    }

    private static Direction getDominantLookDirection(Vec3d lookVec) {
        double ax = Math.abs(lookVec.x);
        double ay = Math.abs(lookVec.y);
        double az = Math.abs(lookVec.z);

        if (ay >= ax && ay >= az) return lookVec.y >= 0.0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return lookVec.x >= 0.0 ? Direction.EAST : Direction.WEST;
        return lookVec.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static Vec3d interpolate(ClientPlayerEntity player, float tickDelta) {
        return new Vec3d(
                player.prevX + (player.getX() - player.prevX) * tickDelta,
                player.prevY + (player.getY() - player.prevY) * tickDelta,
                player.prevZ + (player.getZ() - player.prevZ) * tickDelta
        );
    }

    private static void renderRadiusCircle(Matrix4f mat, VertexConsumerProvider.Immediate immediate, Vec3d center, double radius, int outlineColor) {
        VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getLines());
        int rO = (outlineColor >> 16) & 0xFF;
        int gO = (outlineColor >> 8) & 0xFF;
        int bO = outlineColor & 0xFF;
        int aO = (outlineColor >> 24) & 0xFF;

        Vec3d prev = null;
        for (float angle = 0; angle <= 360; angle += CIRCLE_STEP_DEGREES) {
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

    private static void renderTrajectory(Matrix4f mat, VertexConsumerProvider.Immediate immediate, List<Vec3d> points, int color) {
        VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getLines());
        int rO = (color >> 16) & 0xFF;
        int gO = (color >> 8) & 0xFF;
        int bO = color & 0xFF;
        int aO = (color >> 24) & 0xFF;

        for (int i = 1; i < points.size(); i++) {
            Vec3d p1 = points.get(i - 1);
            Vec3d p2 = points.get(i);
            lineBuffer.vertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).color(rO, gO, bO, aO).normal(1, 0, 0);
            lineBuffer.vertex(mat, (float) p2.x, (float) p2.y, (float) p2.z).color(rO, gO, bO, aO).normal(1, 0, 0);
        }
    }

    private static void renderBoxFill(MatrixStack matrices, Box box, int outlineColor) {
        int r = (outlineColor >> 16) & 0xFF;
        int g = (outlineColor >> 8) & 0xFF;
        int b = outlineColor & 0xFF;
        float a = 0.2f;

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

    private static void renderBox(Matrix4f mat, VertexConsumerProvider.Immediate immediate, Box box, int outlineColor) {
        VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getLines());
        int rO = (outlineColor >> 16) & 0xFF;
        int gO = (outlineColor >> 8) & 0xFF;
        int bO = outlineColor & 0xFF;
        int aO = (outlineColor >> 24) & 0xFF;

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

    private static void drawLine(VertexConsumer buffer, Matrix4f mat,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 int r, int g, int b, int a) {
        buffer.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(1, 0, 0);
        buffer.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(1, 0, 0);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpColor(int start, int end, float t) {
        int a = (int) (((start >> 24) & 0xFF) + (((end >> 24) & 0xFF) - ((start >> 24) & 0xFF)) * t);
        int r = (int) (((start >> 16) & 0xFF) + (((end >> 16) & 0xFF) - ((start >> 16) & 0xFF)) * t);
        int g = (int) (((start >> 8) & 0xFF) + (((end >> 8) & 0xFF) - ((start >> 8) & 0xFF)) * t);
        int b = (int) ((start & 0xFF) + ((end & 0xFF) - (start & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}