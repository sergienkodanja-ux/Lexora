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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class HwHelper {

    private static final int IDX_STAN = 0;
    private static final int IDX_TRAPKA = 1;
    private static final int IDX_VSKRYV = 2;

    // Цвета предметов
    private static final int STAN_COLOR = 0xFF00E5FF;   // Голубая незер-звезда
    private static final int TRAPKA_COLOR = 0xFFC77DFF; // Фиолетовый жареный хорус
    private static final int VSKRYV_COLOR = 0xFF48D1CC; // Бирюзовый осколок призмарина
    private static final int HIT_COLOR = 0xFF00FF00;    // Зеленый при наличии цели

    private static int currentOutlineColor = 0;
    private static int targetOutlineColor = 0;
    private static float transitionTimer = 0.0f;
    private static boolean lastPlayersInRadius = false;
    private static int activeTransitionItem = -1;

    // Ожидание использования (нажатие ПКМ + уменьшение количества)
    private static Item pendingItem = null;
    private static long pendingUntil = 0L;
    private static int countBeforeUse = 0;
    private static boolean lastUsePressed = false;

    // Трекер инвентаря для серверных биндов HolyWorld (/bind и хоткеи сервера)
    private static int lastStanCount = -1;
    private static int lastTrapkaCount = -1;
    private static int lastVskryvCount = -1;
    private static long lastStanTrigger = 0L;
    private static long lastTrapkaTrigger = 0L;
    private static long lastVskryvTrigger = 0L;

    private static boolean isState(String key, boolean def) {
        return com.lexoravisauls.client.core.ClientData.moduleStates.getOrDefault(key, LexoraGui.moduleStates.getOrDefault(key, def));
    }

    public static void checkInventory(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            lastStanCount = -1;
            lastTrapkaCount = -1;
            lastVskryvCount = -1;
            return;
        }

        if (!isState("HW Helper", false)) {
            lastStanCount = -1;
            lastTrapkaCount = -1;
            lastVskryvCount = -1;
            return;
        }

        ClientPlayerEntity player = mc.player;
        int stanCount = countItem(player, Items.NETHER_STAR);
        int trapkaCount = countItem(player, Items.POPPED_CHORUS_FRUIT);
        int vskryvCount = countItem(player, Items.PRISMARINE_SHARD);

        if (lastStanCount == -1) {
            lastStanCount = stanCount;
            lastTrapkaCount = trapkaCount;
            lastVskryvCount = vskryvCount;
            return;
        }

        // Не триггерим если открыт GUI (сундук/инвентарь), зажат Q (выброс) или игрок мёртв
        boolean canTrigger = mc.currentScreen == null && !mc.options.dropKey.isPressed() && !player.isDead();

        if (canTrigger) {
            if (stanCount < lastStanCount && isState("HW Стан", true)) {
                triggerStan(player);
            }
            if (trapkaCount < lastTrapkaCount && isState("HW Трапка", true)) {
                triggerTrapka(player);
            }
            if (vskryvCount < lastVskryvCount && isState("HW Вскрывная", true)) {
                triggerVskryv(player);
            }
        }

        lastStanCount = stanCount;
        lastTrapkaCount = trapkaCount;
        lastVskryvCount = vskryvCount;
    }

    public static void triggerStan(ClientPlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - lastStanTrigger < 1000L) return;
        lastStanTrigger = now;

        float duration = 15.0f;
        if (isState("HW Таймер Стана", true)) {
            NotifManager.startTimer("hw_stan", "Стан (HW)", duration, NotifManager.NotifType.WARNING);
        }
        Box box = getStanBox(player, 1.0f);
        Vec3d center = box.getCenter();
        PvpMarkerManager.addMarker("hw_stan", "СТАН", "30x30",
                center.x, box.maxY + 0.5, center.z,
                new ItemStack(Items.NETHER_STAR), duration, STAN_COLOR, box);
    }

    public static void triggerTrapka(ClientPlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - lastTrapkaTrigger < 1000L) return;
        lastTrapkaTrigger = now;

        float duration = 15.0f;
        if (isState("HW Таймер Трапки", true)) {
            NotifManager.startTimer("hw_trapka", "Трапка (HW)", duration, NotifManager.NotifType.WARNING);
        }
        Box box = getTrapBox(player, 1.0f);
        Vec3d center = box.getCenter();
        PvpMarkerManager.addMarker("hw_trapka", "Трапка", "4x4",
                center.x, box.maxY + 0.3, center.z,
                new ItemStack(Items.POPPED_CHORUS_FRUIT), duration, TRAPKA_COLOR, box);
    }

    public static void triggerVskryv(ClientPlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - lastVskryvTrigger < 1000L) return;
        lastVskryvTrigger = now;

        float duration = 15.0f;
        if (isState("HW Таймер Вскрывной", true)) {
            NotifManager.startTimer("hw_vskryv", "Взрывная трапка", duration, NotifManager.NotifType.WARNING);
        }
        boolean onGround = isPlayerOnGround(player);
        Box box = getVskryvBox(player, 1.0f);
        Vec3d center = box.getCenter();
        String sizeLabel = onGround ? "7x7x4" : "5x5x4";
        PvpMarkerManager.addMarker("hw_vskryv", "Взрывная", sizeLabel,
                center.x, box.maxY + 0.3, center.z,
                new ItemStack(Items.PRISMARINE_SHARD), duration, VSKRYV_COLOR, box);
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (!isState("HW Helper", false)) {
            activeTransitionItem = -1;
            pendingItem = null;
            return;
        }

        ClientPlayerEntity player = mc.player;
        handleItemUse(mc, player);

        int activeItemIndex = resolveActiveItemIndex(player.getMainHandStack(), player.getOffHandStack());
        if (activeItemIndex == -1) {
            activeTransitionItem = -1;
            return;
        }

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        if (activeItemIndex == IDX_STAN) {
            // Стан на ХВ: Звезда Незера, куб 30х30х30
            Box stanBox = getStanBox(player, tickDelta);
            boolean highlight = hasPlayersInBox(mc, player, stanBox);
            updateTransition(IDX_STAN, highlight, STAN_COLOR, HIT_COLOR, tickDelta);

            renderBoxFill(matrices, stanBox, currentOutlineColor, 0.10f);
            renderBoxLines(mat, immediate, stanBox, currentOutlineColor);
        } else if (activeItemIndex == IDX_TRAPKA) {
            // Обычная трапка на ХВ: Жареный хорус, размер как на FT (4х4)
            Box trapBox = getTrapBox(player, tickDelta);
            boolean highlight = hasPlayersInBox(mc, player, trapBox);
            updateTransition(IDX_TRAPKA, highlight, TRAPKA_COLOR, HIT_COLOR, tickDelta);

            renderBoxFill(matrices, trapBox, currentOutlineColor, 0.15f);
            renderBoxLines(mat, immediate, trapBox, currentOutlineColor);
        } else if (activeItemIndex == IDX_VSKRYV) {
            // Взрывная трапка на ХВ: Осколок призмарина, комната 5х5х4
            Box vskryvBox = getVskryvBox(player, tickDelta);
            boolean highlight = hasPlayersInBox(mc, player, vskryvBox);
            updateTransition(IDX_VSKRYV, highlight, VSKRYV_COLOR, HIT_COLOR, tickDelta);

            renderBoxFill(matrices, vskryvBox, currentOutlineColor, 0.15f);
            renderBoxLines(mat, immediate, vskryvBox, currentOutlineColor);
        }

        RenderSystem.disableDepthTest();
        immediate.draw();
        RenderSystem.enableDepthTest();

        matrices.pop();
    }

    private static void handleItemUse(MinecraftClient mc, ClientPlayerEntity player) {
        long now = System.currentTimeMillis();
        boolean usePressed = mc.options.useKey.isPressed();

        Item heldItem = getHeldHelperItem(player);

        if (!lastUsePressed && usePressed && heldItem != null) {
            pendingItem = heldItem;
            pendingUntil = now + 500L;
            countBeforeUse = countItem(player, heldItem);
        }

        lastUsePressed = usePressed;

        if (pendingItem == null) return;

        if (now > pendingUntil) {
            pendingItem = null;
            return;
        }

        int currentCount = countItem(player, pendingItem);
        if (currentCount == countBeforeUse - 1) {
            if (pendingItem == Items.NETHER_STAR) {
                triggerStan(player);
            } else if (pendingItem == Items.POPPED_CHORUS_FRUIT) {
                triggerTrapka(player);
            } else if (pendingItem == Items.PRISMARINE_SHARD) {
                triggerVskryv(player);
            }

            pendingItem = null;
        }
    }

    private static Item getHeldHelperItem(ClientPlayerEntity player) {
        Item main = player.getMainHandStack().getItem();
        Item off = player.getOffHandStack().getItem();
        if (isHelperItem(main)) return main;
        if (isHelperItem(off)) return off;
        return null;
    }

    private static boolean isHelperItem(Item item) {
        if (item == Items.NETHER_STAR && isState("HW Стан", true)) return true;
        if (item == Items.POPPED_CHORUS_FRUIT && isState("HW Трапка", true)) return true;
        if (item == Items.PRISMARINE_SHARD && isState("HW Вскрывная", true)) return true;
        return false;
    }

    private static int resolveActiveItemIndex(ItemStack mainHand, ItemStack offHand) {
        int mainIndex = getEnabledItemIndex(mainHand.getItem());
        return mainIndex != -1 ? mainIndex : getEnabledItemIndex(offHand.getItem());
    }

    private static int getEnabledItemIndex(Item item) {
        if (item == Items.NETHER_STAR && isState("HW Стан", true)) return IDX_STAN;
        if (item == Items.POPPED_CHORUS_FRUIT && isState("HW Трапка", true)) return IDX_TRAPKA;
        if (item == Items.PRISMARINE_SHARD && isState("HW Вскрывная", true)) return IDX_VSKRYV;
        return -1;
    }

    // Стан: 30х30х30 блоков вокруг игрока
    private static Box getStanBox(ClientPlayerEntity player, float tickDelta) {
        Vec3d pos = interpolate(player, tickDelta);
        double cx = Math.floor(pos.x) + 0.5;
        double cy = Math.floor(pos.y) + 0.5;
        double cz = Math.floor(pos.z) + 0.5;
        return new Box(cx - 15.0, cy - 15.0, cz - 15.0, cx + 15.0, cy + 15.0, cz + 15.0);
    }

    // Обычная трапка на ХВ: такой же размер как и у обычной трапки ФТ (4х4, halfSize = 2.0f)
    private static Box getTrapBox(ClientPlayerEntity player, float tickDelta) {
        Vec3d position = interpolate(player, tickDelta);
        double cubeX = Math.floor(position.x) + 0.5;
        double cubeY = Math.floor(position.y) + 0.5 + 1.625;
        double cubeZ = Math.floor(position.z) + 0.5;
        float halfSize = 2.0f;

        return new Box(
                cubeX - halfSize, cubeY - halfSize, cubeZ - halfSize,
                cubeX + halfSize, cubeY + halfSize, cubeZ + halfSize
        );
    }

    // Взрывная трапка на ХВ: 5х5 в воздухе, 7х7 на земле (+2 блока шире), высота 4 (осколок призмарина)
    private static Box getVskryvBox(ClientPlayerEntity player, float tickDelta) {
        Vec3d pos = interpolate(player, tickDelta);
        double cx = Math.floor(pos.x) + 0.5;
        double cy = Math.floor(pos.y);
        double cz = Math.floor(pos.z) + 0.5;
        boolean onGround = isPlayerOnGround(player);
        double halfW = onGround ? 3.5 : 2.5; // На земле 7х7 (+2 блока шире), в воздухе 5х5
        return new Box(cx - halfW, cy, cz - halfW, cx + halfW, cy + 4.0, cz + halfW);
    }

    private static boolean isPlayerOnGround(ClientPlayerEntity player) {
        if (player.isOnGround()) return true;
        if (player.getWorld() != null) {
            BlockPos posBelow = BlockPos.ofFloored(player.getX(), player.getY() - 0.25, player.getZ());
            return !player.getWorld().isAir(posBelow);
        }
        return false;
    }

    private static void updateTransition(int itemIndex, boolean highlight, int baseColor, int highlightColor, float partialTicks) {
        if (activeTransitionItem != itemIndex) {
            activeTransitionItem = itemIndex;
            lastPlayersInRadius = false;
            transitionTimer = 0.0f;
            currentOutlineColor = (0xFF << 24) | (baseColor & 0xFFFFFF);
            targetOutlineColor = currentOutlineColor;
        }

        if (highlight != lastPlayersInRadius) {
            transitionTimer = 0.0f;
            lastPlayersInRadius = highlight;
        }

        targetOutlineColor = (0xFF << 24) | ((highlight ? highlightColor : baseColor) & 0xFFFFFF);
        transitionTimer = Math.min(transitionTimer + (partialTicks / 0.4f), 1.0f);
        currentOutlineColor = lerpColor(currentOutlineColor, targetOutlineColor, transitionTimer);
    }

    private static boolean hasPlayersInBox(MinecraftClient mc, ClientPlayerEntity player, Box box) {
        for (PlayerEntity entity : mc.world.getPlayers()) {
            if (entity == player || !entity.isAlive() || entity.isSpectator()) continue;
            if (entity.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    private static Vec3d interpolate(ClientPlayerEntity player, float tickDelta) {
        return new Vec3d(
                player.prevX + (player.getX() - player.prevX) * tickDelta,
                player.prevY + (player.getY() - player.prevY) * tickDelta,
                player.prevZ + (player.getZ() - player.prevZ) * tickDelta
        );
    }

    private static void renderBoxFill(MatrixStack matrices, Box box, int outlineColor, float alpha) {
        int r = (outlineColor >> 16) & 0xFF;
        int g = (outlineColor >> 8) & 0xFF;
        int b = outlineColor & 0xFF;

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

        buffer.vertex(mat, minX, minY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, minY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, minY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, minX, minY, maxZ).color(r, g, b, alpha);

        buffer.vertex(mat, minX, maxY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, minX, maxY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, maxY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, maxY, minZ).color(r, g, b, alpha);

        buffer.vertex(mat, minX, minY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, minX, maxY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, maxY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, minY, minZ).color(r, g, b, alpha);

        buffer.vertex(mat, minX, minY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, minY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, maxY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, minX, maxY, maxZ).color(r, g, b, alpha);

        buffer.vertex(mat, minX, minY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, minX, minY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, minX, maxY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, minX, maxY, minZ).color(r, g, b, alpha);

        buffer.vertex(mat, maxX, minY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, maxY, minZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, maxY, maxZ).color(r, g, b, alpha);
        buffer.vertex(mat, maxX, minY, maxZ).color(r, g, b, alpha);

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

    private static void drawLine(VertexConsumer buffer, Matrix4f mat,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 int r, int g, int b, int a) {
        buffer.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(1, 0, 0);
        buffer.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(1, 0, 0);
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

    private static int lerpColor(int start, int end, float t) {
        int a = (int) (((start >> 24) & 0xFF) + (((end >> 24) & 0xFF) - ((start >> 24) & 0xFF)) * t);
        int r = (int) (((start >> 16) & 0xFF) + (((end >> 16) & 0xFF) - ((start >> 16) & 0xFF)) * t);
        int g = (int) (((start >> 8) & 0xFF) + (((end >> 8) & 0xFF) - ((start >> 8) & 0xFF)) * t);
        int b = (int) ((start & 0xFF) + ((end & 0xFF) - (start & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
