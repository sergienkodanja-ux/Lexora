package com.lexoravisauls.client.utils.models;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class DogModel {
   private static final Identifier TEXTURE = Identifier.of("lexoravisauls", "textures/dog/taksa.png");
   private static final float TW = 60.0F;
   private static final float TH = 36.0F;
   private static final float S = 0.0625F;
   private float headYaw;
   private float headPitch;
   private float bodyYaw;
   private float frontLeftLegX;
   private float frontRightLegX;
   private float backLeftLegX;
   private float backRightLegX;
   private float frontLeftLegY;
   private float frontRightLegY;
   private float backLeftLegY;
   private float backRightLegY;
   private float tailX;
   private float tailZ;
   private boolean lay;

   public void setRotationAngles(float ageInTicks, DogBrain brain) {
      this.headYaw = -brain.getYaw() * (float) (Math.PI / 180.0);
      this.headPitch = brain.getPitch() * (float) (Math.PI / 180.0);
      this.bodyYaw = brain.getBody();
      this.lay = brain.isLay();
      float swing = brain.limbSwing;
      float amount = brain.limbSwingAmount;
      this.frontLeftLegX = MathHelper.cos(swing * 0.6662F) * 1.4F * amount;
      this.frontRightLegX = MathHelper.cos(swing * 0.6662F + (float) Math.PI) * 1.4F * amount;
      this.backLeftLegX = MathHelper.cos(swing * 0.6662F + (float) Math.PI) * 1.4F * amount;
      this.backRightLegX = MathHelper.cos(swing * 0.6662F) * 1.4F * amount;
      if (this.lay) {
         this.frontLeftLegX = (float)Math.toRadians(-90.0);
         this.frontRightLegX = (float)Math.toRadians(-90.0);
         this.backLeftLegX = (float)Math.toRadians(90.0);
         this.backRightLegX = (float)Math.toRadians(90.0);
         this.frontLeftLegY = (float)Math.toRadians(-22.0);
         this.frontRightLegY = (float)Math.toRadians(22.0);
         this.backLeftLegY = (float)Math.toRadians(22.0);
         this.backRightLegY = (float)Math.toRadians(-22.0);
      } else {
         this.frontLeftLegY = this.frontRightLegY = this.backLeftLegY = this.backRightLegY = 0.0F;
      }

      this.tailX = (float)Math.toRadians(this.lay ? 45.0 : 22.0);
      this.tailZ = (float)Math.toRadians(-22.5) + (float)Math.toRadians(22.5) + (float)Math.cos(ageInTicks * 0.15F) * 0.3F;
   }

   public void render(MatrixStack ms, VertexConsumerProvider vcp, DogBrain brain, int light) {
      VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
      int overlay = OverlayTexture.DEFAULT_UV;
      ms.push();
      ms.translate(0.0, 1.4F - (this.lay ? 0.3F : 0.0F), 0.0);
      ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
      ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.bodyYaw));
      this.renderHead(ms, vc, light, overlay);
      this.renderNeck(ms, vc, light, overlay);
      this.renderBody(ms, vc, light, overlay);
      this.renderLegs(ms, vc, light, overlay);
      this.renderTail(ms, vc, light, overlay);
      ms.pop();
   }

   private void renderHead(MatrixStack ms, VertexConsumer vc, int light, int overlay) {
      ms.push();
      ms.translate(0.0F, 0.65625F, -0.425F);
      ms.multiply(RotationAxis.POSITIVE_Y.rotation(this.headYaw));
      ms.multiply(RotationAxis.POSITIVE_X.rotation(this.headPitch));
      this.cube(ms, vc, light, overlay, -3.0F, -3.0F, -4.0F, 6.0F, 6.0F, 4.0F, 0, 0, false);
      this.cube(ms, vc, light, overlay, -1.5F, 0.0F, -7.0F, 3.0F, 3.0F, 3.0F, 21, 0, false);
      ms.push();
      ms.translate(0.1875F, 0.1875F, -0.125F);
      this.cube(ms, vc, light, overlay, 0.0F, -5.0F, -1.5F, 1.0F, 3.0F, 3.0F, 32, 4, false);
      this.cube(ms, vc, light, overlay, 0.0F, -5.5F, -0.75F, 1.0F, 1.0F, 1.5F, 34, 1, false);
      ms.pop();
      ms.push();
      ms.translate(-0.1875F, 0.1875F, -0.125F);
      this.cube(ms, vc, light, overlay, -1.0F, -5.0F, -1.5F, 1.0F, 3.0F, 3.0F, 32, 4, true);
      this.cube(ms, vc, light, overlay, -1.0F, -5.5F, -0.75F, 1.0F, 1.0F, 1.5F, 34, 1, true);
      ms.pop();
      ms.pop();
   }

   private void renderNeck(MatrixStack ms, VertexConsumer vc, int light, int overlay) {
      ms.push();
      ms.translate(0.0F, 0.65625F, -0.3125F);
      ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-25.0F));
      this.cube(ms, vc, light, overlay, -2.95F, -1.0F, -4.0F, 5.9F, 5.0F, 6.0F, 15, 7, false);
      ms.pop();
   }

   private void renderBody(MatrixStack ms, VertexConsumer vc, int light, int overlay) {
      ms.push();
      ms.translate(0.0F, 0.84375F, -0.3125F);
      ms.push();
      ms.translate(0.0F, 0.0F, 0.1875F);
      this.cube(ms, vc, light, overlay, -4.0F, -3.5F, -3.0F, 8.0F, 7.0F, 6.0F, 32, 13, false);
      ms.pop();
      ms.push();
      ms.translate(0.0F, -0.03125F, 0.34375F);
      this.cube(ms, vc, light, overlay, -3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 11.0F, 3, 19, false);
      ms.pop();
      ms.pop();
   }

   private void renderLegs(MatrixStack ms, VertexConsumer vc, int light, int overlay) {
      ms.push();
      ms.translate(0.09375F, 1.0F, -0.1875F);
      ms.multiply(RotationAxis.POSITIVE_Y.rotation(this.frontLeftLegY));
      ms.multiply(RotationAxis.POSITIVE_X.rotation(this.frontLeftLegX));
      this.cube(ms, vc, light, overlay, -1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, 42, 0, false);
      ms.pop();
      ms.push();
      ms.translate(-0.09375F, 1.0F, -0.1875F);
      ms.multiply(RotationAxis.POSITIVE_Y.rotation(this.frontRightLegY));
      ms.multiply(RotationAxis.POSITIVE_X.rotation(this.frontRightLegX));
      this.cube(ms, vc, light, overlay, -1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, 42, 0, true);
      ms.pop();
      ms.push();
      ms.translate(0.09375F, 1.0F, 0.5625F);
      ms.multiply(RotationAxis.POSITIVE_Y.rotation(this.backLeftLegY));
      ms.multiply(RotationAxis.POSITIVE_X.rotation(this.backLeftLegX));
      this.cube(ms, vc, light, overlay, -1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, 52, 0, false);
      ms.pop();
      ms.push();
      ms.translate(-0.09375F, 1.0F, 0.5625F);
      ms.multiply(RotationAxis.POSITIVE_Y.rotation(this.backRightLegY));
      ms.multiply(RotationAxis.POSITIVE_X.rotation(this.backRightLegX));
      this.cube(ms, vc, light, overlay, -1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, 52, 0, true);
      ms.pop();
   }

   private void renderTail(MatrixStack ms, VertexConsumer vc, int light, int overlay) {
      ms.push();
      ms.translate(0.0F, 0.5625F, 0.625F);
      ms.multiply(RotationAxis.POSITIVE_X.rotation(this.tailX));
      ms.multiply(RotationAxis.POSITIVE_Z.rotation(this.tailZ));
      this.cube(ms, vc, light, overlay, -1.0F, 2.0F, -1.0F, 2.0F, 8.0F, 2.0F, 2, 12, false);
      ms.pop();
   }

   private void cube(
      MatrixStack ms, VertexConsumer vc, int light, int overlay, float ox, float oy, float oz, float w, float h, float d, int u, int v, boolean mirror
   ) {
      float x0 = ox * 0.0625F;
      float x1 = (ox + w) * 0.0625F;
      float y0 = oy * 0.0625F;
      float y1 = (oy + h) * 0.0625F;
      float z0 = oz * 0.0625F;
      float z1 = (oz + d) * 0.0625F;
      if (mirror) {
         float t = x0;
         x0 = x1;
         x1 = t;
      }

      Matrix4f m4 = ms.peek().getPositionMatrix();
      Entry entry = ms.peek();
      this.quad(
         vc,
         m4,
         entry,
         light,
         overlay,
         x1,
         y0,
         z1,
         (u + d) / 60.0F,
         (v + d) / 36.0F,
         x1,
         y0,
         z0,
         u / 60.0F,
         (v + d) / 36.0F,
         x1,
         y1,
         z0,
         u / 60.0F,
         (v + d + h) / 36.0F,
         x1,
         y1,
         z1,
         (u + d) / 60.0F,
         (v + d + h) / 36.0F,
         1.0F,
         0.0F,
         0.0F
      );
      this.quad(
         vc,
         m4,
         entry,
         light,
         overlay,
         x0,
         y0,
         z0,
         (u + 2.0F * d + w) / 60.0F,
         (v + d) / 36.0F,
         x0,
         y0,
         z1,
         (u + d + w) / 60.0F,
         (v + d) / 36.0F,
         x0,
         y1,
         z1,
         (u + d + w) / 60.0F,
         (v + d + h) / 36.0F,
         x0,
         y1,
         z0,
         (u + 2.0F * d + w) / 60.0F,
         (v + d + h) / 36.0F,
         -1.0F,
         0.0F,
         0.0F
      );
      this.quad(
         vc,
         m4,
         entry,
         light,
         overlay,
         x1,
         y0,
         z1,
         (u + d + w) / 60.0F,
         v / 36.0F,
         x0,
         y0,
         z1,
         (u + d) / 60.0F,
         v / 36.0F,
         x0,
         y0,
         z0,
         (u + d) / 60.0F,
         (v + d) / 36.0F,
         x1,
         y0,
         z0,
         (u + d + w) / 60.0F,
         (v + d) / 36.0F,
         0.0F,
         -1.0F,
         0.0F
      );
      this.quad(
         vc,
         m4,
         entry,
         light,
         overlay,
         x1,
         y1,
         z0,
         (u + d + 2.0F * w) / 60.0F,
         v / 36.0F,
         x0,
         y1,
         z0,
         (u + d + w) / 60.0F,
         v / 36.0F,
         x0,
         y1,
         z1,
         (u + d + w) / 60.0F,
         (v + d) / 36.0F,
         x1,
         y1,
         z1,
         (u + d + 2.0F * w) / 60.0F,
         (v + d) / 36.0F,
         0.0F,
         1.0F,
         0.0F
      );
      this.quad(
         vc,
         m4,
         entry,
         light,
         overlay,
         x1,
         y0,
         z0,
         (u + d + w) / 60.0F,
         (v + d) / 36.0F,
         x0,
         y0,
         z0,
         (u + d) / 60.0F,
         (v + d) / 36.0F,
         x0,
         y1,
         z0,
         (u + d) / 60.0F,
         (v + d + h) / 36.0F,
         x1,
         y1,
         z0,
         (u + d + w) / 60.0F,
         (v + d + h) / 36.0F,
         0.0F,
         0.0F,
         -1.0F
      );
      this.quad(
         vc,
         m4,
         entry,
         light,
         overlay,
         x0,
         y0,
         z1,
         (u + 2.0F * d + 2.0F * w) / 60.0F,
         (v + d) / 36.0F,
         x1,
         y0,
         z1,
         (u + 2.0F * d + w) / 60.0F,
         (v + d) / 36.0F,
         x1,
         y1,
         z1,
         (u + 2.0F * d + w) / 60.0F,
         (v + d + h) / 36.0F,
         x0,
         y1,
         z1,
         (u + 2.0F * d + 2.0F * w) / 60.0F,
         (v + d + h) / 36.0F,
         0.0F,
         0.0F,
         1.0F
      );
   }

   private void quad(
      VertexConsumer vc,
      Matrix4f m4,
      Entry entry,
      int light,
      int overlay,
      float x0,
      float y0,
      float z0,
      float u0,
      float v0,
      float x1,
      float y1,
      float z1,
      float u1,
      float v1,
      float x2,
      float y2,
      float z2,
      float u2,
      float v2,
      float x3,
      float y3,
      float z3,
      float u3,
      float v3,
      float nx,
      float ny,
      float nz
   ) {
      vc.vertex(m4, x0, y0, z0)
         .texture(u0, v0)
         .color(1.0F, 1.0F, 1.0F, 1.0F)
         .overlay(overlay)
         .light(light)
         .normal(entry, nx, ny, nz);
      vc.vertex(m4, x1, y1, z1)
         .texture(u1, v1)
         .color(1.0F, 1.0F, 1.0F, 1.0F)
         .overlay(overlay)
         .light(light)
         .normal(entry, nx, ny, nz);
      vc.vertex(m4, x2, y2, z2)
         .texture(u2, v2)
         .color(1.0F, 1.0F, 1.0F, 1.0F)
         .overlay(overlay)
         .light(light)
         .normal(entry, nx, ny, nz);
      vc.vertex(m4, x3, y3, z3)
         .texture(u3, v3)
         .color(1.0F, 1.0F, 1.0F, 1.0F)
         .overlay(overlay)
         .light(light)
         .normal(entry, nx, ny, nz);
   }
}
