package com.lexoravisauls.client.cosmetic.geckolib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.lexoravisauls.client.cosmetic.geo.GeoBone;
import com.lexoravisauls.client.cosmetic.geo.GeoCube;
import com.lexoravisauls.client.cosmetic.geo.GeoModel;
import com.lexoravisauls.client.cosmetic.geo.GeoQuad;
import com.lexoravisauls.client.cosmetic.geo.GeoVertex;
import com.lexoravisauls.client.cosmetic.model.CosmeticModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GeckolibCosmeticRenderer {
   private static GeckolibCosmeticRenderer instance;
   private final Map<Integer, GeoModel> modelCache = new ConcurrentHashMap<>();
   private final Map<Integer, CosmeticAnimationData> animationCache = new ConcurrentHashMap<>();
   private final Set<Integer> noAnimationSet = ConcurrentHashMap.newKeySet();
   private final Map<Integer, Long> animationStartTime = new ConcurrentHashMap<>();
   private final Map<Integer, Map<String, float[]>> initialBoneTransforms = new ConcurrentHashMap<>();
   private final GeckolibModelParser modelParser = new GeckolibModelParser();

   public static GeckolibCosmeticRenderer getInstance() {
      if (instance == null) {
         instance = new GeckolibCosmeticRenderer();
      }

      return instance;
   }

   public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
      if (cosmetic != null && cosmetic.getTextureId() != null) {
         GeoModel model = this.getOrParseModel(cosmetic);
         if (model != null) {
            CosmeticAnimationData anim = this.getOrParseAnimation(cosmetic);
            if (anim != null) {
               this.applyAnimations(model, anim, cosmetic.getId());
            } else {
               this.resetToInitialPose(model, cosmetic.getId());
            }

            Identifier texture = cosmetic.getTextureId();
            RenderLayer layer = RenderLayer.getEntityCutoutNoCull(texture);
            VertexConsumer buffer = consumers.getBuffer(layer);
            RenderSystem.disableCull();

            for (GeoBone bone : model.topLevelBones) {
               this.renderBone(bone, matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
            }

            RenderSystem.enableCull();
         }
      }
   }

   public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
      if (cosmetic != null && cosmetic.getTextureId() != null && vertexConsumer != null) {
         GeoModel model = this.getOrParseModel(cosmetic);
         if (model != null) {
            CosmeticAnimationData anim = this.getOrParseAnimation(cosmetic);
            if (anim != null) {
               this.applyAnimations(model, anim, cosmetic.getId());
            } else {
               this.resetToInitialPose(model, cosmetic.getId());
            }

            RenderSystem.disableCull();

            for (GeoBone bone : model.topLevelBones) {
               this.renderBone(bone, matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
            }

            RenderSystem.enableCull();
         }
      }
   }

   /**
    * Рендерит 3D модель прямо в GUI карточки со сглаживанием, вращением и глубиной
    */
   public void renderModelInGui(CosmeticModel cosmetic, DrawContext context, float x, float y, float size, float rotY, float rotX) {
      if (cosmetic == null || cosmetic.getTextureId() == null) return;
      GeoModel model = this.getOrParseModel(cosmetic);
      if (model == null) return;

      model.computeBounds();

      CosmeticAnimationData anim = this.getOrParseAnimation(cosmetic);
      if (anim != null) {
         this.applyAnimations(model, anim, cosmetic.getId());
      } else {
         this.resetToInitialPose(model, cosmetic.getId());
      }

      context.draw(); // Сброс 2D батча

      MatrixStack matrices = context.getMatrices();
      matrices.push();
      matrices.translate(x, y, 150.0F);

      float effectiveScale = size / Math.max(0.7f, model.maxDimension);
      matrices.scale(effectiveScale, -effectiveScale, effectiveScale);
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
      matrices.translate(-model.centerX, -model.centerY, -model.centerZ);

      RenderSystem.enableDepthTest();
      RenderSystem.disableCull();

      VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
      RenderLayer layer = RenderLayer.getEntityCutoutNoCull(cosmetic.getTextureId());
      VertexConsumer buffer = immediate.getBuffer(layer);

      for (GeoBone bone : model.topLevelBones) {
         this.renderBone(bone, matrices, buffer, 0xF000F0, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
      }

      immediate.draw();
      RenderSystem.disableDepthTest();
      RenderSystem.enableCull();
      matrices.pop();

      context.draw(); // Возобновление 2D батча
   }

   public void renderCapeInGui(DrawContext context, float x, float y, Identifier texture, float rotY, float rotX) {
      if (texture == null) return;
      context.draw(); // Сброс 2D батча

      MatrixStack matrices = context.getMatrices();
      matrices.push();
      matrices.translate(x, y, 150.0F);

      float scale = 1.6F;
      matrices.scale(scale, -scale, scale);
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));

      RenderSystem.enableDepthTest();
      RenderSystem.disableCull();

      VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
      RenderLayer layer = RenderLayer.getEntityCutoutNoCull(texture);
      VertexConsumer buffer = immediate.getBuffer(layer);

      this.renderCapeCuboid(matrices, buffer, 0xF000F0, texture);

      immediate.draw();
      RenderSystem.disableDepthTest();
      RenderSystem.enableCull();
      matrices.pop();

      context.draw(); // Возобновление 2D батча
   }

   private void renderCapeCuboid(MatrixStack matrices, VertexConsumer buffer, int light, Identifier texture) {
      Matrix4f mat = matrices.peek().getPositionMatrix();
      int r = 255, g = 255, b = 255, a = 255;
      int ov = OverlayTexture.DEFAULT_UV;

      com.lexoravisauls.client.cosmetic.CosmeticManager.CapeAnimationInfo anim =
          com.lexoravisauls.client.cosmetic.CosmeticManager.getInstance().getCapeAnimation(texture);
      int frame = anim.getCurrentFrame();

      float uLeft0 = anim.getU(0.0F), uLeft1 = anim.getU(1.0F);
      float uBack0 = anim.getU(1.0F), uBack1 = anim.getU(11.0F);
      float uRight0 = anim.getU(11.0F), uRight1 = anim.getU(12.0F);
      float uFront0 = anim.getU(12.0F), uFront1 = anim.getU(22.0F);
      float uTop0 = anim.getU(1.0F), uTop1 = anim.getU(11.0F);
      float uBot0 = anim.getU(11.0F), uBot1 = anim.getU(21.0F);

      float vTop0 = anim.getV(0.0F, frame), vTop1 = anim.getV(1.0F, frame);
      float vBody0 = anim.getV(1.0F, frame), vBody1 = anim.getV(17.0F, frame);

      // Плащ: ширина 10 (-5..5), высота 16 (-8..8), толщина 1 (-0.5..0.5)
      // Лицевая сторона (к спине игрока): Z = +0.5F
      buffer.vertex(mat, -5.0F, -8.0F, 0.5F).color(r, g, b, a).texture(uFront0, vBody1).overlay(ov).light(light).normal(0f, 0f, 1f);
      buffer.vertex(mat,  5.0F, -8.0F, 0.5F).color(r, g, b, a).texture(uFront1, vBody1).overlay(ov).light(light).normal(0f, 0f, 1f);
      buffer.vertex(mat,  5.0F,  8.0F, 0.5F).color(r, g, b, a).texture(uFront1, vBody0).overlay(ov).light(light).normal(0f, 0f, 1f);
      buffer.vertex(mat, -5.0F,  8.0F, 0.5F).color(r, g, b, a).texture(uFront0, vBody0).overlay(ov).light(light).normal(0f, 0f, 1f);

      // Задняя сторона (внешняя, основной рисунок): Z = -0.5F
      buffer.vertex(mat,  5.0F, -8.0F, -0.5F).color(r, g, b, a).texture(uBack0, vBody1).overlay(ov).light(light).normal(0f, 0f, -1f);
      buffer.vertex(mat, -5.0F, -8.0F, -0.5F).color(r, g, b, a).texture(uBack1, vBody1).overlay(ov).light(light).normal(0f, 0f, -1f);
      buffer.vertex(mat, -5.0F,  8.0F, -0.5F).color(r, g, b, a).texture(uBack1, vBody0).overlay(ov).light(light).normal(0f, 0f, -1f);
      buffer.vertex(mat,  5.0F,  8.0F, -0.5F).color(r, g, b, a).texture(uBack0, vBody0).overlay(ov).light(light).normal(0f, 0f, -1f);

      // Левая грань: X = -5.0F
      buffer.vertex(mat, -5.0F, -8.0F, -0.5F).color(r, g, b, a).texture(uRight0, vBody1).overlay(ov).light(light).normal(-1f, 0f, 0f);
      buffer.vertex(mat, -5.0F, -8.0F,  0.5F).color(r, g, b, a).texture(uRight1, vBody1).overlay(ov).light(light).normal(-1f, 0f, 0f);
      buffer.vertex(mat, -5.0F,  8.0F,  0.5F).color(r, g, b, a).texture(uRight1, vBody0).overlay(ov).light(light).normal(-1f, 0f, 0f);
      buffer.vertex(mat, -5.0F,  8.0F, -0.5F).color(r, g, b, a).texture(uRight0, vBody0).overlay(ov).light(light).normal(-1f, 0f, 0f);

      // Правая грань: X = +5.0F
      buffer.vertex(mat, 5.0F, -8.0F,  0.5F).color(r, g, b, a).texture(uLeft0,  vBody1).overlay(ov).light(light).normal(1f, 0f, 0f);
      buffer.vertex(mat, 5.0F, -8.0F, -0.5F).color(r, g, b, a).texture(uLeft1,  vBody1).overlay(ov).light(light).normal(1f, 0f, 0f);
      buffer.vertex(mat, 5.0F,  8.0F, -0.5F).color(r, g, b, a).texture(uLeft1,  vBody0).overlay(ov).light(light).normal(1f, 0f, 0f);
      buffer.vertex(mat, 5.0F,  8.0F,  0.5F).color(r, g, b, a).texture(uLeft0,  vBody0).overlay(ov).light(light).normal(1f, 0f, 0f);

      // Верхняя грань: Y = +8.0F
      buffer.vertex(mat, -5.0F, 8.0F,  0.5F).color(r, g, b, a).texture(uTop0, vTop1).overlay(ov).light(light).normal(0f, 1f, 0f);
      buffer.vertex(mat,  5.0F, 8.0F,  0.5F).color(r, g, b, a).texture(uTop1, vTop1).overlay(ov).light(light).normal(0f, 1f, 0f);
      buffer.vertex(mat,  5.0F, 8.0F, -0.5F).color(r, g, b, a).texture(uTop1, vTop0).overlay(ov).light(light).normal(0f, 1f, 0f);
      buffer.vertex(mat, -5.0F, 8.0F, -0.5F).color(r, g, b, a).texture(uTop0, vTop0).overlay(ov).light(light).normal(0f, 1f, 0f);

      // Нижняя грань: Y = -8.0F
      buffer.vertex(mat, -5.0F, -8.0F, -0.5F).color(r, g, b, a).texture(uTop1, vTop0).overlay(ov).light(light).normal(0f, -1f, 0f);
      buffer.vertex(mat,  5.0F, -8.0F, -0.5F).color(r, g, b, a).texture(uBot1,  vTop0).overlay(ov).light(light).normal(0f, -1f, 0f);
      buffer.vertex(mat,  5.0F, -8.0F,  0.5F).color(r, g, b, a).texture(uBot1,  vTop1).overlay(ov).light(light).normal(0f, -1f, 0f);
      buffer.vertex(mat, -5.0F, -8.0F,  0.5F).color(r, g, b, a).texture(uTop1, vTop1).overlay(ov).light(light).normal(0f, -1f, 0f);
   }

   public GeoModel getOrParseModel(CosmeticModel cosmetic) {
      int id = cosmetic.getId();
      if (this.modelCache.containsKey(id)) {
         return this.modelCache.get(id);
      }

      GeoModel model = this.modelParser.parseModel(cosmetic);
      if (model != null) {
         this.modelCache.put(id, model);
         this.saveInitialBoneTransforms(id, model);
      }

      return model;
   }

   private void saveInitialBoneTransforms(int id, GeoModel model) {
      HashMap<String, float[]> map = new HashMap<>();

      for (GeoBone bone : model.topLevelBones) {
         this.saveBonesRecursive(bone, map);
      }

      this.initialBoneTransforms.put(id, map);
   }

   private void saveBonesRecursive(GeoBone bone, Map<String, float[]> map) {
      map.put(
         bone.name,
         new float[]{
            bone.getRotationX(),
            bone.getRotationY(),
            bone.getRotationZ(),
            bone.getPositionX(),
            bone.getPositionY(),
            bone.getPositionZ(),
            bone.getScaleX(),
            bone.getScaleY(),
            bone.getScaleZ()
         }
      );

      for (GeoBone child : bone.childBones) {
         this.saveBonesRecursive(child, map);
      }
   }

   private void resetToInitialPose(GeoModel model, int id) {
      Map<String, float[]> initial = this.initialBoneTransforms.get(id);
      if (initial != null) {
         for (GeoBone bone : model.topLevelBones) {
            this.resetBoneRecursive(bone, initial);
         }
      }
   }

   private void renderBone(GeoBone bone, MatrixStack matrices, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
      if (!bone.isHidden) {
         matrices.push();
         GeckoRenderHelper.translate(bone, matrices);
         GeckoRenderHelper.moveToPivot(bone, matrices);
         GeckoRenderHelper.rotate(bone, matrices);
         GeckoRenderHelper.scale(bone, matrices);
         GeckoRenderHelper.moveBackFromPivot(bone, matrices);

         for (GeoCube cube : bone.childCubes) {
            this.renderCube(cube, matrices, buffer, light, overlay, r, g, b, a);
         }

         for (GeoBone child : bone.childBones) {
            this.renderBone(child, matrices, buffer, light, overlay, r, g, b, a);
         }

         matrices.pop();
      }
   }

   private void renderCube(GeoCube cube, MatrixStack matrices, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
      matrices.push();
      GeckoRenderHelper.moveToPivot(cube, matrices);
      GeckoRenderHelper.rotate(cube, matrices);
      GeckoRenderHelper.moveBackFromPivot(cube, matrices);
      Matrix4f posMatrix = matrices.peek().getPositionMatrix();
      Matrix3f normMatrix = matrices.peek().getNormalMatrix();

      for (GeoQuad quad : cube.quads) {
         if (quad != null) {
            Vector3f normal = new Vector3f(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
            normMatrix.transform(normal);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            if ((cube.size.getY() == 0.0F || cube.size.getZ() == 0.0F) && nx < 0.0F) {
               nx = -nx;
            }

            if ((cube.size.getX() == 0.0F || cube.size.getZ() == 0.0F) && ny < 0.0F) {
               ny = -ny;
            }

            if ((cube.size.getX() == 0.0F || cube.size.getY() == 0.0F) && nz < 0.0F) {
               nz = -nz;
            }

            int red = (int) (r * 255.0F);
            int green = (int) (g * 255.0F);
            int blue = (int) (b * 255.0F);
            int alpha = (int) (a * 255.0F);

            for (GeoVertex vertex : quad.vertices) {
               buffer.vertex(posMatrix, vertex.position.getX(), vertex.position.getY(), vertex.position.getZ())
                  .color(red, green, blue, alpha)
                  .texture(vertex.textureU, vertex.textureV)
                  .overlay(overlay)
                  .light(light)
                  .normal(nx, ny, nz);
            }
         }
      }

      matrices.pop();
   }

   private CosmeticAnimationData getOrParseAnimation(CosmeticModel cosmetic) {
      int id = cosmetic.getId();
      if (this.animationCache.containsKey(id)) {
         return this.animationCache.get(id);
      }

      if (this.noAnimationSet.contains(id)) {
         return null;
      }

      JsonObject animJson = cosmetic.getAnimationJson();
      if (animJson == null) {
         this.noAnimationSet.add(id);
         return null;
      }

      try {
         CosmeticAnimationData data = this.parseAnimationData(animJson);
         if (data != null) {
            this.animationCache.put(id, data);
         } else {
            this.noAnimationSet.add(id);
         }

         return data;
      } catch (Exception e) {
         this.noAnimationSet.add(id);
         return null;
      }
   }

   private CosmeticAnimationData parseAnimationData(JsonObject root) {
      CosmeticAnimationData data = new CosmeticAnimationData();
      if (!root.has("animations")) {
         return null;
      }

      JsonObject anims = root.getAsJsonObject("animations");
      Iterator<Map.Entry<String, JsonElement>> it = anims.entrySet().iterator();
      if (it.hasNext()) {
         Map.Entry<String, JsonElement> entry = it.next();
         String animName = entry.getKey();
         JsonObject animObj = entry.getValue().getAsJsonObject();
         data.animationName = animName;
         data.loop = !animObj.has("loop") || animObj.get("loop").getAsBoolean();
         data.length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 1.0F;
         if (data.length <= 0.0F) data.length = 1.0F;

         if (animObj.has("bones")) {
            JsonObject bones = animObj.getAsJsonObject("bones");

            for (Map.Entry<String, JsonElement> bEntry : bones.entrySet()) {
               String boneName = bEntry.getKey();
               JsonObject boneAnim = bEntry.getValue().getAsJsonObject();
               BoneAnimationData bData = new BoneAnimationData();
               if (boneAnim.has("rotation")) {
                  bData.rotationKeyframes = this.parseKeyframes(boneAnim.get("rotation"));
               }

               if (boneAnim.has("position")) {
                  bData.positionKeyframes = this.parseKeyframes(boneAnim.get("position"));
               }

               if (boneAnim.has("scale")) {
                  bData.scaleKeyframes = this.parseKeyframes(boneAnim.get("scale"));
               }

               data.boneAnimations.put(boneName, bData);
            }
         }
      }

      return data;
   }

   private Map<Float, float[]> parseKeyframes(JsonElement elem) {
      HashMap<Float, float[]> map = new HashMap<>();
      if (elem.isJsonObject()) {
         JsonObject obj = elem.getAsJsonObject();

         for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            try {
               float time = Float.parseFloat(entry.getKey());
               JsonElement val = entry.getValue();
               float[] vec = new float[3];
               if (val.isJsonObject()) {
                  JsonObject vObj = val.getAsJsonObject();
                  if (vObj.has("vector")) {
                     JsonArray arr = vObj.getAsJsonArray("vector");
                     vec[0] = arr.get(0).getAsFloat();
                     vec[1] = arr.get(1).getAsFloat();
                     vec[2] = arr.get(2).getAsFloat();
                  }
               } else if (val.isJsonArray()) {
                  JsonArray arr = val.getAsJsonArray();
                  vec[0] = arr.get(0).getAsFloat();
                  vec[1] = arr.get(1).getAsFloat();
                  vec[2] = arr.get(2).getAsFloat();
               }

               map.put(time, vec);
            } catch (NumberFormatException ignored) {
            }
         }
      }

      return map;
   }

   private void applyAnimations(GeoModel model, CosmeticAnimationData animData, int id) {
      Map<String, float[]> initial = this.initialBoneTransforms.get(id);
      if (initial != null) {
         long start = this.animationStartTime.computeIfAbsent(id, k -> System.currentTimeMillis());
         float elapsed = (float)(System.currentTimeMillis() - start) / 1000.0F;
         float animLen = animData.length > 0.0F ? animData.length : 1.0F;
         float time = animData.loop ? (elapsed % animLen) : Math.min(elapsed, animLen);

         for (GeoBone bone : model.topLevelBones) {
            this.resetBoneRecursive(bone, initial);
         }

         for (Map.Entry<String, BoneAnimationData> entry : animData.boneAnimations.entrySet()) {
            String boneName = entry.getKey();
            BoneAnimationData bData = entry.getValue();
            GeoBone bone = this.findBone(model, boneName);
            if (bone != null) {
               float[] init = initial.get(boneName);
               if (init == null) {
                  init = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
               }

               if (!bData.rotationKeyframes.isEmpty()) {
                  float[] rot = this.interpolateKeyframes(bData.rotationKeyframes, time);
                  bone.setRotationX(init[0] + (float)Math.toRadians(-rot[0]));
                  bone.setRotationY(init[1] + (float)Math.toRadians(-rot[1]));
                  bone.setRotationZ(init[2] + (float)Math.toRadians(rot[2]));
               }

               if (!bData.positionKeyframes.isEmpty()) {
                  float[] pos = this.interpolateKeyframes(bData.positionKeyframes, time);
                  bone.setPositionX(init[3] + pos[0]);
                  bone.setPositionY(init[4] + pos[1]);
                  bone.setPositionZ(init[5] + pos[2]);
               }

               if (!bData.scaleKeyframes.isEmpty()) {
                  float[] sc = this.interpolateKeyframes(bData.scaleKeyframes, time);
                  bone.setScaleX(init[6] * sc[0]);
                  bone.setScaleY(init[7] * sc[1]);
                  bone.setScaleZ(init[8] * sc[2]);
               }
            }
         }
      }
   }

   private void resetBoneRecursive(GeoBone bone, Map<String, float[]> initial) {
      float[] vals = initial.get(bone.name);
      if (vals != null) {
         bone.setRotationX(vals[0]);
         bone.setRotationY(vals[1]);
         bone.setRotationZ(vals[2]);
         bone.setPositionX(vals[3]);
         bone.setPositionY(vals[4]);
         bone.setPositionZ(vals[5]);
         bone.setScaleX(vals[6]);
         bone.setScaleY(vals[7]);
         bone.setScaleZ(vals[8]);
      }

      for (GeoBone child : bone.childBones) {
         this.resetBoneRecursive(child, initial);
      }
   }

   private GeoBone findBone(GeoModel model, String name) {
      for (GeoBone bone : model.topLevelBones) {
         GeoBone found = this.findBoneRecursive(bone, name);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private GeoBone findBoneRecursive(GeoBone bone, String name) {
      if (bone.name.equals(name)) {
         return bone;
      }

      for (GeoBone child : bone.childBones) {
         GeoBone found = this.findBoneRecursive(child, name);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private float[] interpolateKeyframes(Map<Float, float[]> keyframes, float time) {
      if (keyframes.isEmpty()) {
         return new float[]{0.0F, 0.0F, 0.0F};
      }

      Float t1 = null;
      Float t2 = null;
      float[] v1 = null;
      float[] v2 = null;

      Float minT = null, maxT = null;
      float[] minV = null, maxV = null;

      for (Map.Entry<Float, float[]> entry : keyframes.entrySet()) {
         float t = entry.getKey();
         if (minT == null || t < minT) { minT = t; minV = entry.getValue(); }
         if (maxT == null || t > maxT) { maxT = t; maxV = entry.getValue(); }

         if (t <= time && (t1 == null || t > t1)) {
            t1 = t;
            v1 = entry.getValue();
         }

         if (t >= time && (t2 == null || t < t2)) {
            t2 = t;
            v2 = entry.getValue();
         }
      }

      if (v1 == null && v2 == null) {
         return minV != null ? minV : new float[]{0.0F, 0.0F, 0.0F};
      }

      if (v1 == null) {
         v1 = maxV;
         t1 = 0.0F;
      }

      if (v2 == null) {
         v2 = minV;
         t2 = (maxT != null && maxT > 0) ? maxT : 1.0F;
      }

      if (t1.equals(t2) || (t2 - t1) == 0.0F) {
         return v1;
      }

      float factor = Math.max(0.0F, Math.min(1.0F, (time - t1) / (t2 - t1)));
      return new float[]{
         v1[0] + factor * (v2[0] - v1[0]),
         v1[1] + factor * (v2[1] - v1[1]),
         v1[2] + factor * (v2[2] - v1[2])
      };
   }

   private static class BoneAnimationData {
      Map<Float, float[]> rotationKeyframes = new HashMap<>();
      Map<Float, float[]> positionKeyframes = new HashMap<>();
      Map<Float, float[]> scaleKeyframes = new HashMap<>();
   }

   private static class CosmeticAnimationData {
      String animationName;
      boolean loop;
      float length;
      Map<String, BoneAnimationData> boneAnimations = new HashMap<>();
   }
}
