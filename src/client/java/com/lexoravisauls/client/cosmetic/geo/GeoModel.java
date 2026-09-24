package com.lexoravisauls.client.cosmetic.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GeoModel {
   public List<GeoBone> topLevelBones = new ArrayList<>();
   public int textureWidth = 64;
   public int textureHeight = 64;

   public Optional<GeoBone> getBone(String name) {
      for (GeoBone bone : this.topLevelBones) {
         GeoBone found = this.getBoneRecursively(name, bone);
         if (found != null) {
            return Optional.of(found);
         }
      }

      return Optional.empty();
   }

   private GeoBone getBoneRecursively(String name, GeoBone bone) {
      if (bone.name.equals(name)) {
         return bone;
      }

      for (GeoBone child : bone.childBones) {
         GeoBone found = this.getBoneRecursively(name, child);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   public float minX, maxX, minY, maxY, minZ, maxZ;
   public float centerX, centerY, centerZ;
   public float maxDimension = 1.0f;
   private boolean boundsComputed = false;

   public void computeBounds() {
      if (boundsComputed) return;
      minX = minY = minZ = Float.MAX_VALUE;
      maxX = maxY = maxZ = -Float.MAX_VALUE;
      for (GeoBone bone : topLevelBones) {
         computeBoneBounds(bone);
      }
      if (minX == Float.MAX_VALUE) {
         minX = -0.5f; maxX = 0.5f;
         minY = 0f; maxY = 1f;
         minZ = -0.5f; maxZ = 0.5f;
      }
      centerX = (minX + maxX) / 2.0f;
      centerY = (minY + maxY) / 2.0f;
      centerZ = (minZ + maxZ) / 2.0f;
      float dx = maxX - minX;
      float dy = maxY - minY;
      float dz = maxZ - minZ;
      maxDimension = Math.max(dx, Math.max(dy, dz));
      if (maxDimension <= 0.001f) maxDimension = 1.0f;
      boundsComputed = true;
   }

   private void computeBoneBounds(GeoBone bone) {
      for (GeoCube c : bone.childCubes) {
         for (GeoQuad q : c.quads) {
            if (q == null) continue;
            for (GeoVertex v : q.vertices) {
               minX = Math.min(minX, v.position.getX()); maxX = Math.max(maxX, v.position.getX());
               minY = Math.min(minY, v.position.getY()); maxY = Math.max(maxY, v.position.getY());
               minZ = Math.min(minZ, v.position.getZ()); maxZ = Math.max(maxZ, v.position.getZ());
            }
         }
      }
      for (GeoBone child : bone.childBones) {
         computeBoneBounds(child);
      }
   }
}
