package com.lexoravisauls.client.cosmetic.render;

import com.lexoravisauls.client.cosmetic.geckolib.GeckolibCosmeticRenderer;
import com.lexoravisauls.client.cosmetic.geo.GeoModel;
import com.lexoravisauls.client.cosmetic.model.CosmeticModel;
import com.lexoravisauls.client.cosmetic.model.ModelPosition;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

public class CosmeticRenderer {
   private static CosmeticRenderer instance;
   private final GeckolibCosmeticRenderer geckolibRenderer = GeckolibCosmeticRenderer.getInstance();
   private final RenderStack stack = new RenderStack();

   public static CosmeticRenderer getInstance() {
      if (instance == null) {
         instance = new CosmeticRenderer();
      }

      return instance;
   }

   public void renderCosmetic(CosmeticModel cosmetic, AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntityModel playerModel, float tickDelta) {
      if (cosmetic == null || cosmetic.getTextureId() == null || playerModel == null) {
         return;
      }

      if (this.isPet(cosmetic)) {
         this.renderPet(cosmetic, matrices, consumers, null, light, playerModel);
         return;
      }

      this.stack.update(matrices);
      this.stack.push();
      float yOffset = this.transformToPosition(cosmetic, playerModel, matrices);
      this.stack.rotateZDegrees(180.0F);
      this.stack.translate(cosmetic.getX(), cosmetic.getY() + yOffset, cosmetic.getZ());
      this.stack.rotateYDegrees(cosmetic.getYaw());
      this.stack.rotateXDegrees(cosmetic.getPitch());
      this.stack.rotateZDegrees(cosmetic.getRoll());
      this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
      this.geckolibRenderer.renderCosmetic(cosmetic, matrices, consumers, light);
      this.stack.pop();
   }

   public void renderCosmetic(CosmeticModel cosmetic, AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumer vertexConsumer, int light, PlayerEntityModel playerModel, float tickDelta) {
      if (cosmetic == null || cosmetic.getTextureId() == null || vertexConsumer == null || playerModel == null) {
         return;
      }

      if (this.isPet(cosmetic)) {
         this.renderPet(cosmetic, matrices, null, vertexConsumer, light, playerModel);
         return;
      }

      this.stack.update(matrices);
      this.stack.push();
      float yOffset = this.transformToPosition(cosmetic, playerModel, matrices);
      this.stack.rotateZDegrees(180.0F);
      this.stack.translate(cosmetic.getX(), cosmetic.getY() + yOffset, cosmetic.getZ());
      this.stack.rotateYDegrees(cosmetic.getYaw());
      this.stack.rotateXDegrees(cosmetic.getPitch());
      this.stack.rotateZDegrees(cosmetic.getRoll());
      this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
      this.geckolibRenderer.renderCosmetic(cosmetic, matrices, vertexConsumer, light);
      this.stack.pop();
   }

   private boolean isPet(CosmeticModel cosmetic) {
      if (cosmetic == null) return false;
      if ("pet".equalsIgnoreCase(cosmetic.getType())) return true;
      if (cosmetic.getCategory() == 2) return true;
      String lower = cosmetic.getName().toLowerCase();
      return lower.contains("pet") || lower.contains("bee") || lower.contains("radish");
   }

   private void renderPet(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumerProvider consumers, VertexConsumer vertexConsumer, int light, PlayerEntityModel playerModel) {
      this.stack.update(matrices);
      this.stack.push();
      this.transformToModelPart(playerModel.body, matrices);
      this.stack.rotateZDegrees(180.0F);

      // Центрируем питомца относительно его геометрии
      GeoModel geoModel = this.geckolibRenderer.getOrParseModel(cosmetic);
      if (geoModel != null) {
         geoModel.computeBounds();
         this.stack.translate(-geoModel.centerX, -geoModel.centerY, -geoModel.centerZ);
      }

      // Размещаем питомца на правом плече:
      // X = +0.48F (сдвиг правее, на правое плечо)
      // Y = +0.60F + парение (над плечом)
      // Z = +0.08F (чуть назад вдоль плечевой оси, не на лице)
      float bob = (float) Math.sin((System.currentTimeMillis() % 2400) / 2400.0 * Math.PI * 2) * 0.035F;
      this.stack.translate(0.48F, 0.60F + bob, 0.08F);

      this.stack.rotateYDegrees(cosmetic.getYaw());
      this.stack.rotateXDegrees(cosmetic.getPitch());
      this.stack.rotateZDegrees(cosmetic.getRoll());
      this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());

      if (consumers != null) {
         this.geckolibRenderer.renderCosmetic(cosmetic, matrices, consumers, light);
      } else if (vertexConsumer != null) {
         this.geckolibRenderer.renderCosmetic(cosmetic, matrices, vertexConsumer, light);
      }

      this.stack.pop();
   }

   private boolean isAnimalHat(CosmeticModel cosmetic) {
      if (cosmetic == null || cosmetic.getName() == null) return false;
      String lower = cosmetic.getName().toLowerCase();
      return lower.contains("frog")
          || lower.contains("chicken")
          || lower.contains("camel")
          || lower.contains("sheep")
          || lower.contains("armadillo");
   }

   private float transformToPosition(CosmeticModel cosmetic, PlayerEntityModel playerModel, MatrixStack matrices) {
      float yOffset = 0.0F;
      if (playerModel == null) {
         return yOffset;
      }

      ModelPosition pos = cosmetic.getPosition();
      switch (pos) {
         case HEAD:
            this.transformToModelPart(playerModel.head, matrices);
            if (this.isAnimalHat(cosmetic)) {
               // Исходная высота для frog hat, chicken hat, camel hat, sheep hat, armadillo
               yOffset = 0.26F;
            } else {
               // Все остальные шапки опускаем еще чуть ниже
               yOffset = 0.04F;
            }
            break;
         case ABOVE_HEAD:
            this.transformToModelPart(playerModel.head, matrices);
            yOffset = 0.38F;
            break;
         case BODY:
            this.transformToModelPart(playerModel.body, matrices);
            yOffset = -0.30F;
            break;
         case RIGHT_ARM:
            this.transformToModelPart(playerModel.rightArm, matrices);
            yOffset = -0.25F;
            break;
         case LEFT_ARM:
            this.transformToModelPart(playerModel.leftArm, matrices);
            yOffset = -0.25F;
            break;
         case RIGHT_LEG:
            this.transformToModelPart(playerModel.rightLeg, matrices);
            yOffset = -0.35F;
            break;
         case LEFT_LEG:
            this.transformToModelPart(playerModel.leftLeg, matrices);
            yOffset = -0.35F;
            break;
         case FREE:
            break;
      }

      return yOffset;
   }

   private void transformToModelPart(ModelPart part, MatrixStack matrices) {
      if (part != null) {
         part.rotate(matrices);
      }
   }
}
