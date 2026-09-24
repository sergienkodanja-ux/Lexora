package com.lexoravisauls.client.cosmetic.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;

public class GeoModelParser {
   public static GeoModel parse(String jsonString) {
      try {
         JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
         return parseModel(json);
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Failed to parse Bedrock model: " + e.getMessage());
         return null;
      }
   }

   private static GeoModel parseModel(JsonObject root) {
      if (root.has("model") && root.get("model").isJsonObject()) {
         root = root.getAsJsonObject("model");
      }
      JsonArray geoArray = root.getAsJsonArray("minecraft:geometry");
      if (geoArray != null && !geoArray.isEmpty()) {
         JsonObject geo = geoArray.get(0).getAsJsonObject();
         JsonObject desc = geo.getAsJsonObject("description");
         int texW = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
         int texH = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;
         GeoModel model = new GeoModel();
         model.textureWidth = texW;
         model.textureHeight = texH;
         JsonArray bonesArray = geo.getAsJsonArray("bones");
         if (bonesArray != null) {
            HashMap<String, GeoBone> boneMap = new HashMap<>();

            for (JsonElement elem : bonesArray) {
               JsonObject boneObj = elem.getAsJsonObject();
               GeoBone bone = parseBone(boneObj, texW, texH);
               boneMap.put(bone.name, bone);
            }

            for (JsonElement elem : bonesArray) {
               JsonObject boneObj = elem.getAsJsonObject();
               String name = boneObj.get("name").getAsString();
               GeoBone bone = boneMap.get(name);
               if (boneObj.has("parent")) {
                  String parentName = boneObj.get("parent").getAsString();
                  GeoBone parent = boneMap.get(parentName);
                  if (parent != null) {
                     parent.childBones.add(bone);
                     bone.parent = parent;
                  }
               } else {
                  model.topLevelBones.add(bone);
               }
            }
         }

         return model;
      } else {
         System.err.println("[Lexora Cosmetics] No minecraft:geometry found in model");
         return null;
      }
   }

   private static GeoBone parseBone(JsonObject boneObj, int texW, int texH) {
      String name = boneObj.get("name").getAsString();
      GeoBone bone = new GeoBone(name);
      if (boneObj.has("pivot")) {
         JsonArray pivotArr = boneObj.getAsJsonArray("pivot");
         bone.rotationPointX = -pivotArr.get(0).getAsFloat();
         bone.rotationPointY = pivotArr.get(1).getAsFloat();
         bone.rotationPointZ = pivotArr.get(2).getAsFloat();
      }

      if (boneObj.has("rotation")) {
         JsonArray rotArr = boneObj.getAsJsonArray("rotation");
         bone.setRotationX((float)Math.toRadians(-rotArr.get(0).getAsFloat()));
         bone.setRotationY((float)Math.toRadians(-rotArr.get(1).getAsFloat()));
         bone.setRotationZ((float)Math.toRadians(rotArr.get(2).getAsFloat()));
      }

      if (boneObj.has("cubes")) {
         for (JsonElement elem : boneObj.getAsJsonArray("cubes")) {
            JsonObject cubeObj = elem.getAsJsonObject();
            GeoCube cube = parseCube(cubeObj, texW, texH);
            bone.childCubes.add(cube);
         }
      }

      return bone;
   }

   private static GeoCube parseCube(JsonObject cubeObj, int texW, int texH) {
      float[] origin = parseFloatArray(cubeObj, "origin", new float[]{0.0F, 0.0F, 0.0F});
      float[] size = parseFloatArray(cubeObj, "size", new float[]{1.0F, 1.0F, 1.0F});
      float[] pivot = parseFloatArray(cubeObj, "pivot", (float[])origin.clone());
      float[] rot = parseFloatArray(cubeObj, "rotation", new float[]{0.0F, 0.0F, 0.0F});
      float inflate = cubeObj.has("inflate") ? cubeObj.get("inflate").getAsFloat() : 0.0F;
      boolean mirror = cubeObj.has("mirror") && cubeObj.get("mirror").getAsBoolean();
      GeoCube cube = new GeoCube(size[0], size[1], size[2]);
      cube.pivot = new Vec3F(-pivot[0], pivot[1], pivot[2]);
      cube.rotation = new Vec3F((float)Math.toRadians(-rot[0]), (float)Math.toRadians(-rot[1]), (float)Math.toRadians(rot[2]));
      cube.inflate = inflate;
      cube.mirror = mirror;
      buildCubeQuads(cube, origin, size, inflate, mirror, cubeObj, texW, texH);
      return cube;
   }

   private static void buildCubeQuads(GeoCube cube, float[] origin, float[] size, float inflate, boolean mirror, JsonObject cubeObj, int texW, int texH) {
      float ox = origin[0] - inflate;
      float oy = origin[1] - inflate;
      float oz = origin[2] - inflate;
      float sx = size[0] + inflate * 2.0F;
      float sy = size[1] + inflate * 2.0F;
      float sz = size[2] + inflate * 2.0F;
      float u = 0.0F;
      float v = 0.0F;
      boolean perFace = false;
      JsonObject uvObj = null;
      if (cubeObj.has("uv")) {
         JsonElement uvElem = cubeObj.get("uv");
         if (uvElem.isJsonArray()) {
            JsonArray arr = uvElem.getAsJsonArray();
            u = arr.get(0).getAsFloat();
            v = arr.get(1).getAsFloat();
         } else if (uvElem.isJsonObject()) {
            perFace = true;
            uvObj = uvElem.getAsJsonObject();
         }
      }

      float minX = -(ox + sx) / 16.0F;
      float minY = oy / 16.0F;
      float minZ = oz / 16.0F;
      float maxX = -ox / 16.0F;
      float maxY = (oy + sy) / 16.0F;
      float maxZ = (oz + sz) / 16.0F;
      if (perFace && uvObj != null) {
         cube.quads[0] = buildQuadPerFace(uvObj, "west", minX, minY, minZ, minX, maxY, maxZ, -1.0F, 0.0F, 0.0F, texW, texH);
         cube.quads[1] = buildQuadPerFace(uvObj, "east", maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, texW, texH);
         cube.quads[2] = buildQuadPerFace(uvObj, "down", minX, minY, minZ, maxX, minY, maxZ, 0.0F, -1.0F, 0.0F, texW, texH);
         cube.quads[3] = buildQuadPerFace(uvObj, "up", minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, texW, texH);
         cube.quads[4] = buildQuadPerFace(uvObj, "north", minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, texW, texH);
         cube.quads[5] = buildQuadPerFace(uvObj, "south", minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, texW, texH);
      } else {
         cube.quads[0] = buildQuadBox(minX, minY, minZ, minX, maxY, maxZ, -1.0F, 0.0F, 0.0F, u, v, sz, sy, sx, (float)texW, (float)texH, "west");
         cube.quads[1] = buildQuadBox(maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, u, v, sz, sy, sx, (float)texW, (float)texH, "east");
         cube.quads[2] = buildQuadBox(minX, minY, minZ, maxX, minY, maxZ, 0.0F, -1.0F, 0.0F, u, v, sz, sy, sx, (float)texW, (float)texH, "down");
         cube.quads[3] = buildQuadBox(minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, u, v, sz, sy, sx, (float)texW, (float)texH, "up");
         cube.quads[4] = buildQuadBox(minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, u, v, sz, sy, sx, (float)texW, (float)texH, "north");
         cube.quads[5] = buildQuadBox(minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, u, v, sz, sy, sx, (float)texW, (float)texH, "south");
      }
   }

   private static GeoQuad buildQuadPerFace(
      JsonObject uvObj,
      String face,
      float x1, float y1, float z1,
      float x2, float y2, float z2,
      float nx, float ny, float nz,
      int texW, int texH
   ) {
      if (!uvObj.has(face)) {
         return null;
      }

      JsonObject faceObj = uvObj.getAsJsonObject(face);
      JsonArray uvArr = faceObj.getAsJsonArray("uv");
      JsonArray uvSizeArr = faceObj.getAsJsonArray("uv_size");
      float u = uvArr.get(0).getAsFloat() / texW;
      float v = uvArr.get(1).getAsFloat() / texH;
      float uw = uvSizeArr.get(0).getAsFloat() / texW;
      float vh = uvSizeArr.get(1).getAsFloat() / texH;
      GeoVertex[] vertices = buildFaceVertices(face, x1, y1, z1, x2, y2, z2, u, v, uw, vh);
      return new GeoQuad(vertices, nx, ny, nz);
   }

   private static GeoQuad buildQuadBox(
      float x1, float y1, float z1,
      float x2, float y2, float z2,
      float nx, float ny, float nz,
      float u, float v,
      float sz, float sy, float sx,
      float texW, float texH,
      String face
   ) {
      float faceU;
      float faceV;
      float faceW;
      float faceH;
      switch (face) {
         case "north":
            faceU = (u + sx + sz) / texW;
            faceV = (v + sx) / texH;
            faceW = sz / texW;
            faceH = sy / texH;
            break;
         case "south":
            faceU = (u + sx + sz + sx) / texW;
            faceV = (v + sx) / texH;
            faceW = sz / texW;
            faceH = sy / texH;
            break;
         case "east":
            faceU = u / texW;
            faceV = (v + sx) / texH;
            faceW = sx / texW;
            faceH = sy / texH;
            break;
         case "west":
            faceU = (u + sx + sz) / texW;
            faceV = (v + sx) / texH;
            faceW = sx / texW;
            faceH = sy / texH;
            break;
         case "up":
            faceU = (u + sx) / texW;
            faceV = v / texH;
            faceW = sz / texW;
            faceH = sx / texH;
            break;
         case "down":
            faceU = (u + sx + sz) / texW;
            faceV = v / texH;
            faceW = sz / texW;
            faceH = sx / texH;
            break;
         default:
            faceU = 0.0F;
            faceV = 0.0F;
            faceW = 0.0F;
            faceH = 0.0F;
      }

      GeoVertex[] vertices = buildFaceVertices(face, x1, y1, z1, x2, y2, z2, faceU, faceV, faceW, faceH);
      return new GeoQuad(vertices, nx, ny, nz);
   }

   private static GeoVertex[] buildFaceVertices(
      String face, float x1, float y1, float z1, float x2, float y2, float z2, float u, float v, float uw, float vh
   ) {
      GeoVertex[] verts = new GeoVertex[4];
      float u2 = u + uw;
      float v2 = v + vh;
      switch (face) {
         case "north":
            verts[0] = new GeoVertex(x2, y2, z1, u, v);
            verts[1] = new GeoVertex(x1, y2, z1, u2, v);
            verts[2] = new GeoVertex(x1, y1, z1, u2, v2);
            verts[3] = new GeoVertex(x2, y1, z1, u, v2);
            break;
         case "south":
            verts[0] = new GeoVertex(x1, y2, z2, u, v);
            verts[1] = new GeoVertex(x2, y2, z2, u2, v);
            verts[2] = new GeoVertex(x2, y1, z2, u2, v2);
            verts[3] = new GeoVertex(x1, y1, z2, u, v2);
            break;
         case "east":
            verts[0] = new GeoVertex(x2, y2, z2, u, v);
            verts[1] = new GeoVertex(x2, y2, z1, u2, v);
            verts[2] = new GeoVertex(x2, y1, z1, u2, v2);
            verts[3] = new GeoVertex(x2, y1, z2, u, v2);
            break;
         case "west":
            verts[0] = new GeoVertex(x1, y2, z1, u, v);
            verts[1] = new GeoVertex(x1, y2, z2, u2, v);
            verts[2] = new GeoVertex(x1, y1, z2, u2, v2);
            verts[3] = new GeoVertex(x1, y1, z1, u, v2);
            break;
         case "up":
            verts[0] = new GeoVertex(x1, y2, z1, u, v);
            verts[1] = new GeoVertex(x1, y2, z2, u, v2);
            verts[2] = new GeoVertex(x2, y2, z2, u2, v2);
            verts[3] = new GeoVertex(x2, y2, z1, u2, v);
            break;
         case "down":
            verts[0] = new GeoVertex(x2, y1, z1, u, v);
            verts[1] = new GeoVertex(x2, y1, z2, u, v2);
            verts[2] = new GeoVertex(x1, y1, z2, u2, v2);
            verts[3] = new GeoVertex(x1, y1, z1, u2, v);
            break;
      }

      return verts;
   }

   private static float[] parseFloatArray(JsonObject obj, String name, float[] def) {
      if (!obj.has(name)) {
         return def;
      }

      JsonArray arr = obj.getAsJsonArray(name);
      float[] result = new float[arr.size()];

      for (int i = 0; i < arr.size(); i++) {
         result[i] = arr.get(i).getAsFloat();
      }

      return result;
   }
}
