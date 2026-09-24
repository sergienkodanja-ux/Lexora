package com.lexoravisauls.client.cosmetic.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public class RenderStack {
   private MatrixStack stack;

   public void update(MatrixStack stack) {
      this.stack = stack;
   }

   public void push() {
      this.stack.push();
   }

   public void pop() {
      this.stack.pop();
   }

   public MatrixStack get() {
      return this.stack;
   }

   public void translate(float x, float y, float z) {
      this.stack.translate(x, y, z);
   }

   public void scale(float x, float y, float z) {
      this.stack.scale(x, y, z);
   }

   public void rotateX(float degrees) {
      this.stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(degrees));
   }

   public void rotateY(float degrees) {
      this.stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degrees));
   }

   public void rotateZ(float degrees) {
      this.stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
   }

   public void rotate(float x, float y, float z) {
      if (z != 0.0F) {
         this.rotateZ(z);
      }

      if (y != 0.0F) {
         this.rotateY(y);
      }

      if (x != 0.0F) {
         this.rotateX(x);
      }
   }

   public void rotateDegrees(float x, float y, float z) {
      this.rotate(x, y, z);
   }

   public void rotateXDegrees(float degrees) {
      this.rotateX(degrees);
   }

   public void rotateYDegrees(float degrees) {
      this.rotateY(degrees);
   }

   public void rotateZDegrees(float degrees) {
      this.rotateZ(degrees);
   }
}
