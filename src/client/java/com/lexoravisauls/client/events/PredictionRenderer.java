package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.GPS;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.thrown.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class PredictionRenderer {

    private static final Identifier TARGET_TEX = Identifier.of("lexoravisauls", "textures/gui/prediction_target.png");

    private static class TargetMark {
        final Vec3d pos;
        final Direction side;
        final boolean isEntity;
        final boolean isFlying;

        TargetMark(Vec3d pos, Direction side, boolean isEntity, boolean isFlying) {
            this.pos = pos;
            this.side = side != null ? side : Direction.UP;
            this.isEntity = isEntity;
            this.isFlying = isFlying;
        }
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (mc.world == null || player == null) return;

        boolean moduleEnabled = LexoraGui.moduleStates.getOrDefault("Prediction", false);
        if (!moduleEnabled) return;

        List<TargetMark> marks = new ArrayList<>();

        // 1. Проверяем предмет в руках игрока (main hand, затем offhand)
        ItemStack stack = player.getMainHandStack();
        Item item = stack.getItem();
        if (!isProjectileItem(item)) {
            stack = player.getOffHandStack();
            item = stack.getItem();
        }

        if (isProjectileItem(item)) {
            boolean isCrossbow = item instanceof CrossbowItem;
            boolean hasMultishot = false;
            if (isCrossbow) {
                try {
                    hasMultishot = stack.getEnchantments().toString().toLowerCase(Locale.ROOT).contains("multishot");
                } catch (Exception ignored) {}
            }

            if (hasMultishot) {
                TargetMark m0 = simulateInHand(player, stack, item, 0.0f);
                if (m0 != null) marks.add(m0);
                TargetMark mLeft = simulateInHand(player, stack, item, -10.0f);
                if (mLeft != null) marks.add(mLeft);
                TargetMark mRight = simulateInHand(player, stack, item, 10.0f);
                if (mRight != null) marks.add(mRight);
            } else {
                TargetMark m = simulateInHand(player, stack, item, 0.0f);
                if (m != null) marks.add(m);
            }
        }

        // 2. Симуляция уже летящих снарядов в мире (эндер-жемчужины, стрелы, трезубцы и др.)
        try {
            Box searchBox = player.getBoundingBox().expand(96.0);
            for (Entity entity : mc.world.getOtherEntities(player, searchBox)) {
                if (entity == null || entity.isRemoved() || entity.isOnGround()) continue;
                if (entity.getVelocity().lengthSquared() < 0.001) continue;

                if (entity instanceof EnderPearlEntity || entity instanceof PersistentProjectileEntity
                        || entity instanceof SnowballEntity || entity instanceof EggEntity
                        || entity instanceof PotionEntity || entity instanceof ExperienceBottleEntity) {
                    TargetMark flyingMark = simulateFlyingEntity(entity);
                    if (flyingMark != null) {
                        marks.add(flyingMark);
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (marks.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        List<TargetMark> validMarks = new ArrayList<>();
        for (TargetMark m : marks) {
            if (m == null || m.pos == null) continue;
            double dist = camPos.distanceTo(m.pos);
            if (dist >= 0.05 && dist <= 256.0) {
                validMarks.add(m);
            }
        }
        if (validMarks.isEmpty()) return;

        try {
            // Включаем билинейную фильтрацию и мипмапы для четкости прицела
            GPS.ensureLinearFilter(TARGET_TEX);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TARGET_TEX);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

            matrices.push();
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);
            Matrix4f mat = matrices.peek().getPositionMatrix();

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            int quadsDrawn = 0;

            for (TargetMark mark : validMarks) {
                double dist = camPos.distanceTo(mark.pos);

                // Размер прицела на поверхности
                float size = (float) MathHelper.clamp(dist * 0.045, 0.5, 2.2);
                float hs = size / 2.0f;

                // Цветовая дифференциация:
                // Сущность (враг/моб): алый красный (1.00, 0.15, 0.20)
                // Летящий в мире снаряд (жемчуг/стрела): неоновый бирюзовый (0.25, 0.75, 1.00)
                // В руке (предикт броска): сочный зелёный (0.15, 0.95, 0.35)
                float r = mark.isEntity ? 1.00f : (mark.isFlying ? 0.25f : 0.15f);
                float g = mark.isEntity ? 0.15f : (mark.isFlying ? 0.75f : 0.95f);
                float b = mark.isEntity ? 0.20f : (mark.isFlying ? 1.00f : 0.35f);
                float a = 0.98f;

                // Нормаль поверхности контакта
                Vec3i normI = mark.side.getVector();
                Vec3d normal = new Vec3d(normI.getX(), normI.getY(), normI.getZ());

                // Сдвиг на 0.015 блока от поверхности для идеального прилегания без Z-файтинга
                Vec3d center = mark.pos.add(normal.multiply(0.015));

                Vec3d uVec; // Вектор "вверх" для текстуры
                Vec3d rVec; // Вектор "вправо" для текстуры

                if (mark.side == Direction.UP || mark.side == Direction.DOWN) {
                    // Горизонтальная плоскость (пол / потолок) — ложится плашмя
                    // Ориентируем по взгляду игрока (вперёд/вправо)
                    float yawRad = (float) Math.toRadians(player.getYaw());
                    Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));
                    Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0));

                    if (mark.side == Direction.UP) {
                        uVec = forward;
                        rVec = right;
                    } else {
                        uVec = forward;
                        rVec = right.multiply(-1);
                    }
                } else {
                    // Вертикальная стена (NORTH, SOUTH, EAST, WEST) — ложится на стену
                    uVec = new Vec3d(0, 1, 0);
                    rVec = uVec.crossProduct(normal);
                }

                // 4 вершины плоского квада, прилегающего к поверхности
                Vec3d v0 = center.subtract(rVec.multiply(hs)).subtract(uVec.multiply(hs));
                Vec3d v1 = center.add(rVec.multiply(hs)).subtract(uVec.multiply(hs));
                Vec3d v2 = center.add(rVec.multiply(hs)).add(uVec.multiply(hs));
                Vec3d v3 = center.subtract(rVec.multiply(hs)).add(uVec.multiply(hs));

                buffer.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).texture(0f, 1f).color(r, g, b, a);
                buffer.vertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).texture(1f, 1f).color(r, g, b, a);
                buffer.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).texture(1f, 0f).color(r, g, b, a);
                buffer.vertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).texture(0f, 0f).color(r, g, b, a);
                quadsDrawn++;
            }

            if (quadsDrawn > 0) {
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }

            matrices.pop();
        } catch (Throwable ignored) {
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
        }
    }

    private static boolean isProjectileItem(Item item) {
        if (item == null) return false;
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem
                || item instanceof EnderPearlItem || item instanceof SnowballItem || item instanceof EggItem
                || item instanceof SplashPotionItem || item instanceof LingeringPotionItem
                || item instanceof ExperienceBottleItem) {
            return true;
        }
        String path = Registries.ITEM.getId(item).getPath();
        return path.contains("wind_charge");
    }

    /**
     * Точная симуляция траектории предмета в руке игрока с учётом физики Minecraft 1.21.4:
     * - Инерция движения игрока (player.getMovement)
     * - Реальный порядок тика (гравитация и сопротивление перед сдвигом для ThrownEntity)
     * - Сопротивление воды (0.8 для бросков, 0.6 для стрел)
     * - Состояние натяжения лука и зарядки арбалета
     */
    private static TargetMark simulateInHand(PlayerEntity player, ItemStack stack, Item item, float yawOffset) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;

        boolean isBow = item instanceof BowItem;
        boolean isCrossbow = item instanceof CrossbowItem;
        boolean isTrident = item instanceof TridentItem;
        boolean isPotion = item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
        boolean isExpBottle = item instanceof ExperienceBottleItem;

        float yaw = player.getYaw() + yawOffset;
        float pitch = player.getPitch();
        if (isPotion || isExpBottle) pitch -= 20.0f;

        // В ванильном коде снаряд спавнится на (x, eyeY - 0.1, z)
        Vec3d pos = new Vec3d(player.getX(), player.getEyeY() - 0.10000000149011612D, player.getZ());

        float f = -MathHelper.sin(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
        float g = -MathHelper.sin(pitch * 0.017453292F);
        float h = MathHelper.cos(yaw * 0.017453292F) * MathHelper.cos(pitch * 0.017453292F);
        Vec3d dir = new Vec3d(f, g, h).normalize();

        float speed = 1.5f;
        float gravity = 0.03f;
        boolean isArrowOrTrident = false;
        boolean isWindCharge = false;

        if (isBow) {
            isArrowOrTrident = true;
            float charge = 1.0f;
            if (player.isUsingItem() && player.getActiveItem() == stack) {
                int useTicks = stack.getMaxUseTime(player) - player.getItemUseTimeLeft();
                charge = BowItem.getPullProgress(useTicks);
                if (charge < 0.1f) return null;
            }
            speed = charge * 3.0f;
            gravity = 0.05f;
        } else if (isCrossbow) {
            isArrowOrTrident = true;
            speed = 3.15f;
            gravity = 0.05f;
        } else if (isTrident) {
            isArrowOrTrident = true;
            if (player.isUsingItem() && player.getActiveItem() == stack) {
                int useTicks = stack.getMaxUseTime(player) - player.getItemUseTimeLeft();
                if (useTicks < 10) return null;
            }
            speed = 2.5f;
            gravity = 0.05f;
        } else if (isPotion) {
            speed = 0.5f;
            gravity = 0.05f;
        } else if (isExpBottle) {
            speed = 0.7f;
            gravity = 0.07f;
        } else {
            String path = Registries.ITEM.getId(item).getPath();
            if (path.contains("wind_charge")) {
                speed = 1.5f;
                gravity = 0.0f;
                isWindCharge = true;
            }
        }

        Vec3d vel = dir.multiply(speed);

        // КРИТИЧЕСКИ ВАЖНО: Добавляем инерцию стрелка в соответствии с ProjectileEntity.setVelocity()
        Vec3d movement = player.getMovement();
        vel = vel.add(movement.x, player.isOnGround() ? 0.0 : movement.y, movement.z);

        float waterDrag = isArrowOrTrident ? 0.6f : 0.8f;

        Vec3d hitPos = null;
        Direction hitSide = Direction.UP;
        boolean hitEntity = false;

        int maxSimTicks = (int) (ClientData.numSettings.getOrDefault("Predict Ticks", 30.0f) * 10);
        if (maxSimTicks < 50) maxSimTicks = 300;

        for (int i = 0; i < maxSimTicks; i++) {
            boolean inWater = mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);

            if (!isArrowOrTrident && !isWindCharge) {
                vel = vel.add(0, -gravity, 0);
                vel = vel.multiply(inWater ? waterDrag : 0.99);
            }

            Vec3d nextPos = pos.add(vel);

            HitResult blockHit = mc.world.raycast(new RaycastContext(
                    pos, nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            ));

            Vec3d endPos = nextPos;
            if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                endPos = blockHit.getPos();
                hitPos = endPos;
                if (blockHit instanceof BlockHitResult bHit) {
                    hitSide = bHit.getSide();
                }
            }

            Box box = new Box(pos.x, pos.y, pos.z, endPos.x, endPos.y, endPos.z).expand(1.0);
            double minDist = hitPos != null ? pos.squaredDistanceTo(hitPos) : Double.MAX_VALUE;

            for (Entity e : mc.world.getOtherEntities(player, box)) {
                if (e == player || e.isSpectator()) continue;
                if (e.canHit() || e instanceof LivingEntity) {
                    Box eBox = e.getBoundingBox().expand(0.3);
                    Optional<Vec3d> opt = eBox.raycast(pos, endPos);
                    if (opt.isPresent()) {
                        double dist = pos.squaredDistanceTo(opt.get());
                        if (dist < minDist) {
                            minDist = dist;
                            hitPos = opt.get();
                            hitEntity = true;
                            hitSide = determineHitSide(hitPos, eBox);
                        }
                    }
                }
            }

            if (hitPos != null) break;

            pos = nextPos;

            if (isArrowOrTrident) {
                vel = vel.multiply(inWater ? waterDrag : 0.99);
                vel = vel.add(0, -gravity, 0);
            }
        }

        if (hitPos == null) return null;
        return new TargetMark(hitPos, hitSide, hitEntity, false);
    }

    /**
     * Симуляция траектории снаряда, который уже находится в полёте в мире (EnderPearl, Arrow, Trident и др.).
     */
    private static TargetMark simulateFlyingEntity(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;

        Vec3d pos = entity.getPos();
        Vec3d vel = entity.getVelocity();

        boolean isArrowOrTrident = entity instanceof PersistentProjectileEntity;
        boolean isPotion = entity instanceof PotionEntity;
        boolean isExp = entity instanceof ExperienceBottleEntity;

        float gravity = 0.03f;
        if (isArrowOrTrident || isPotion) gravity = 0.05f;
        else if (isExp) gravity = 0.07f;

        float waterDrag = isArrowOrTrident ? 0.6f : 0.8f;

        Vec3d hitPos = null;
        Direction hitSide = Direction.UP;
        boolean hitEntity = false;

        int maxSimTicks = (int) (ClientData.numSettings.getOrDefault("Predict Ticks", 30.0f) * 10);
        if (maxSimTicks < 50) maxSimTicks = 300;

        for (int i = 0; i < maxSimTicks; i++) {
            boolean inWater = mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);

            if (!isArrowOrTrident) {
                vel = vel.add(0, -gravity, 0);
                vel = vel.multiply(inWater ? waterDrag : 0.99);
            }

            Vec3d nextPos = pos.add(vel);

            HitResult blockHit = mc.world.raycast(new RaycastContext(
                    pos, nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    entity
            ));

            Vec3d endPos = nextPos;
            if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                endPos = blockHit.getPos();
                hitPos = endPos;
                if (blockHit instanceof BlockHitResult bHit) {
                    hitSide = bHit.getSide();
                }
            }

            Box box = new Box(pos.x, pos.y, pos.z, endPos.x, endPos.y, endPos.z).expand(1.0);
            double minDist = hitPos != null ? pos.squaredDistanceTo(hitPos) : Double.MAX_VALUE;

            for (Entity e : mc.world.getOtherEntities(entity, box)) {
                if (e == entity || e.isSpectator()) continue;
                if (e.canHit() || e instanceof LivingEntity) {
                    Box eBox = e.getBoundingBox().expand(0.3);
                    Optional<Vec3d> opt = eBox.raycast(pos, endPos);
                    if (opt.isPresent()) {
                        double dist = pos.squaredDistanceTo(opt.get());
                        if (dist < minDist) {
                            minDist = dist;
                            hitPos = opt.get();
                            hitEntity = true;
                            hitSide = determineHitSide(hitPos, eBox);
                        }
                    }
                }
            }

            if (hitPos != null) break;

            pos = nextPos;

            if (isArrowOrTrident) {
                vel = vel.multiply(inWater ? waterDrag : 0.99);
                vel = vel.add(0, -gravity, 0);
            }
        }

        if (hitPos == null) return null;
        return new TargetMark(hitPos, hitSide, hitEntity, true);
    }

    private static Direction determineHitSide(Vec3d hitPos, Box eBox) {
        double dXMin = Math.abs(hitPos.x - eBox.minX);
        double dXMax = Math.abs(hitPos.x - eBox.maxX);
        double dYMin = Math.abs(hitPos.y - eBox.minY);
        double dYMax = Math.abs(hitPos.y - eBox.maxY);
        double dZMin = Math.abs(hitPos.z - eBox.minZ);
        double dZMax = Math.abs(hitPos.z - eBox.maxZ);

        double minFace = dYMax;
        Direction hitSide = Direction.UP;
        if (dYMin < minFace) { minFace = dYMin; hitSide = Direction.DOWN; }
        if (dXMin < minFace) { minFace = dXMin; hitSide = Direction.WEST; }
        if (dXMax < minFace) { minFace = dXMax; hitSide = Direction.EAST; }
        if (dZMin < minFace) { minFace = dZMin; hitSide = Direction.NORTH; }
        if (dZMax < minFace) { hitSide = Direction.SOUTH; }
        return hitSide;
    }

    public static void renderHud(DrawContext context, float tickDelta) {
    }
}