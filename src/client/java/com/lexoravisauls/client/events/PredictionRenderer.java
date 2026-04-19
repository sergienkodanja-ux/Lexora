package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.Optional;

public class PredictionRenderer {

    private static final Identifier TARGET_TEX = Identifier.of("lexoravisauls", "textures/gui/target.png");

    // Универсальный сдвиг для центрирования
    private static final float TEX_SHIFT = 0.16f;

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Prediction", false)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (mc.world == null || player == null) return;

        ItemStack stack = player.getMainHandStack();
        Item item = stack.getItem();

        boolean isBow = item instanceof BowItem;
        boolean isCrossbow = item instanceof CrossbowItem;
        boolean isTrident = item instanceof TridentItem;
        boolean isPotion = item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
        boolean isThrowable = item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderPearlItem || item instanceof ExperienceBottleItem;

        if (!isBow && !isCrossbow && !isTrident && !isPotion && !isThrowable) return;

        boolean hasMultishot = false;
        if (isCrossbow) {
            try {
                hasMultishot = stack.getEnchantments().toString().contains("multishot");
            } catch (Exception ignored) {}
        }

        if (hasMultishot) {
            simulateAndDraw(matrices, camera, tickDelta, player, stack, item, isBow, isCrossbow, isTrident, isPotion, 0.0f);
            simulateAndDraw(matrices, camera, tickDelta, player, stack, item, isBow, isCrossbow, isTrident, isPotion, -10.0f);
            simulateAndDraw(matrices, camera, tickDelta, player, stack, item, isBow, isCrossbow, isTrident, isPotion, 10.0f);
        } else {
            simulateAndDraw(matrices, camera, tickDelta, player, stack, item, isBow, isCrossbow, isTrident, isPotion, 0.0f);
        }
    }

    private static void simulateAndDraw(MatrixStack matrices, Camera camera, float tickDelta, PlayerEntity player, ItemStack stack, Item item, boolean isBow, boolean isCrossbow, boolean isTrident, boolean isPotion, float yawOffset) {
        MinecraftClient mc = MinecraftClient.getInstance();

        float yaw = MathHelper.lerp(tickDelta, player.prevYaw, player.getYaw()) + yawOffset;
        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        if (isPotion) pitch -= 20.0f;

        double lerpX = MathHelper.lerp(tickDelta, player.prevX, player.getX());
        double lerpY = MathHelper.lerp(tickDelta, player.prevY, player.getY());
        double lerpZ = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());

        double eyeOffset = player.getEyeY() - player.getY();

        double startX = lerpX - MathHelper.cos(yaw * 0.017453292F) * 0.16f;
        double startY = lerpY + eyeOffset - 0.1f;
        double startZ = lerpZ - MathHelper.sin(yaw * 0.017453292F) * 0.16f;
        Vec3d pos = new Vec3d(startX, startY, startZ);

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

            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                break;
            }

            pos = nextPos;
            vel = vel.multiply(0.99);
            vel = vel.add(0, -gravity, 0);
        }

        int color = LexoraGui.getThemeColor(0);
        float cR = ((color >> 16) & 0xFF) / 255f;
        float cG = ((color >> 8) & 0xFF) / 255f;
        float cB = (color & 0xFF) / 255f;

        matrices.push();
        Vec3d camPos = camera.getPos();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f defaultMatrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();

        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            Vec3d hitPos = hit.getPos();


            float yawRad = camera.getYaw() * 0.017453292F;
            double shiftX = MathHelper.cos(yawRad) * TEX_SHIFT;
            double shiftZ = MathHelper.sin(yawRad) * TEX_SHIFT;
            double shiftY = 0;


            if (hit instanceof BlockHitResult blockHit) {
                Direction side = blockHit.getSide();
                if (side != Direction.UP && side != Direction.DOWN) {
                    shiftY = 0.08f; // 🔥 Было TEX_SHIFT. Если все еще высоко - ставь 0.04f
                }
            } else if (hit instanceof EntityHitResult) {
                shiftY = 0.08f; // 🔥 Поднимаем на игроке
            }

            hitPos = hitPos.add(shiftX, shiftY, shiftZ);

            RenderSystem.disableDepthTest();

            if (isPotion) {
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                BufferBuilder fillBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                float fillAlpha = 0.5f;
                float radius = 4.0f;
                int segments = 30;
                for (int i = 0; i < segments; i++) {
                    float angle1 = (float) (i * Math.PI * 2 / segments);
                    float angle2 = (float) ((i + 1) * Math.PI * 2 / segments);
                    float x1 = (float) (hitPos.x + Math.sin(angle1) * radius);
                    float z1 = (float) (hitPos.z + Math.cos(angle1) * radius);
                    float x2 = (float) (hitPos.x + Math.sin(angle2) * radius);
                    float z2 = (float) (hitPos.z + Math.cos(angle2) * radius);

                    fillBuffer.vertex(defaultMatrix, (float)hitPos.x, (float)hitPos.y + 0.05f, (float)hitPos.z).color(cR, cG, cB, fillAlpha);
                    fillBuffer.vertex(defaultMatrix, x1, (float)hitPos.y + 0.05f, z1).color(cR, cG, cB, fillAlpha);
                    fillBuffer.vertex(defaultMatrix, x2, (float)hitPos.y + 0.05f, z2).color(cR, cG, cB, fillAlpha);
                    fillBuffer.vertex(defaultMatrix, (float)hitPos.x, (float)hitPos.y + 0.05f, (float)hitPos.z).color(cR, cG, cB, fillAlpha);
                }
                BufferRenderer.drawWithGlobalProgram(fillBuffer.end());
            } else {
                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                RenderSystem.setShaderTexture(0, TARGET_TEX);

                BufferBuilder texBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                float size = 0.4f;

                matrices.push();
                matrices.translate(hitPos.x, hitPos.y, hitPos.z);

                if (hit instanceof BlockHitResult blockHit) {
                    Direction side = blockHit.getSide();
                    matrices.translate(side.getOffsetX() * 0.02f, side.getOffsetY() * 0.02f, side.getOffsetZ() * 0.02f);

                    switch (side) {
                        case UP:
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-camera.getYaw()));
                            break;
                        case DOWN:
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(camera.getYaw()));
                            break;
                        case NORTH: matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180)); break;
                        case SOUTH: break;
                        case WEST: matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90)); break;
                        case EAST: matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90)); break;
                    }
                } else if (hit instanceof EntityHitResult) {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                    matrices.translate(0, 0, 0.1f);
                }

                Matrix4f quadMatrix = matrices.peek().getPositionMatrix();

                texBuffer.vertex(quadMatrix, -size, -size, 0).texture(0, 1).color(cR, cG, cB, 1.0f);
                texBuffer.vertex(quadMatrix, size, -size, 0).texture(1, 1).color(cR, cG, cB, 1.0f);
                texBuffer.vertex(quadMatrix, size, size, 0).texture(1, 0).color(cR, cG, cB, 1.0f);
                texBuffer.vertex(quadMatrix, -size, size, 0).texture(0, 0).color(cR, cG, cB, 1.0f);

                BufferRenderer.drawWithGlobalProgram(texBuffer.end());
                matrices.pop();
            }
            RenderSystem.enableDepthTest();
        }
        matrices.pop();
        RenderSystem.enableCull();
    }
}