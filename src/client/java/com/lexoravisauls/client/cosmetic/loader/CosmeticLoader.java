package com.lexoravisauls.client.cosmetic.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.cosmetic.model.CosmeticModel;
import com.lexoravisauls.client.cosmetic.model.ModelPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticLoader {
   private static CosmeticLoader instance;
   private final Map<Integer, CosmeticModel> loadedCosmetics = new ConcurrentHashMap<>();
   private final Map<String, Identifier> textureCache = new ConcurrentHashMap<>();

   public static CosmeticLoader getInstance() {
      if (instance == null) {
         instance = new CosmeticLoader();
      }

      return instance;
   }

   public CosmeticModel loadFromJson(String jsonString) {
      return this.loadFromJson(jsonString, null, -1);
   }

   public CosmeticModel loadFromJson(String jsonString, Identifier textureOverride, int idOverride) {
      try {
         JsonObject obj = JsonParser.parseString(jsonString).getAsJsonObject();
         return this.loadFromJson(obj, textureOverride, idOverride);
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Error loading cosmetic from JSON string: " + e.getMessage());
         return null;
      }
   }

   public CosmeticModel loadFromJson(JsonObject root) {
      return this.loadFromJson(root, null, -1);
   }

   public CosmeticModel loadFromJson(JsonObject root, Identifier textureOverride, int idOverride) {
      try {
         if (root.has("name") && root.has("model")) {
            String name = root.get("name").getAsString();
            int id = idOverride >= 0 ? idOverride : (root.has("id") ? root.get("id").getAsInt() : name.hashCode());
            int category = root.has("category") ? root.get("category").getAsInt() : 1;
            CosmeticModel cosmetic = new CosmeticModel(name, id, category);
            if (root.has("type")) cosmetic.setType(root.get("type").getAsString());
            JsonObject modelObj = root.getAsJsonObject("model");
            cosmetic.setRawModelJson(modelObj.toString());

            if (textureOverride != null) {
               cosmetic.setTextureId(textureOverride);
            } else if (root.has("texture") && !root.get("texture").getAsString().isBlank()) {
               String texBase64 = root.get("texture").getAsString();
               Identifier tex = this.loadTextureFromBase64(name, id, texBase64);
               cosmetic.setTextureId(tex);
            } else if (idOverride >= 0) {
               cosmetic.setTextureId(Identifier.of("lexoravisauls", "textures/cosmetics/cosmetic_" + idOverride + ".png"));
            }

            this.parseModelPosition(root, cosmetic);

            if (root.has("animation")) {
               cosmetic.setAnimationJson(root.getAsJsonObject("animation"));
            }

            this.loadedCosmetics.put(id, cosmetic);
            return cosmetic;
         } else {
            return null;
         }
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Error loading cosmetic: " + e.getMessage());
         return null;
      }
   }

   private void parseModelPosition(JsonObject root, CosmeticModel cosmetic) {
      JsonObject cfg = (root.has("config") && root.get("config").isJsonObject()) ? root.getAsJsonObject("config") : root;

      if (cfg.has("pos")) {
         cosmetic.setPosition(ModelPosition.getById(cfg.get("pos").getAsInt()));
      } else if (root.has("pos")) {
         cosmetic.setPosition(ModelPosition.getById(root.get("pos").getAsInt()));
      }

      if (cfg.has("scale")) {
         cosmetic.setScale(cfg.get("scale").getAsFloat());
      } else if (root.has("scale")) {
         cosmetic.setScale(root.get("scale").getAsFloat());
      }

      if (cfg.has("height")) {
         cosmetic.setHeight(cfg.get("height").getAsFloat());
      } else if (root.has("height")) {
         cosmetic.setHeight(root.get("height").getAsFloat());
      }

      if (cfg.has("x")) {
         cosmetic.setX(cfg.get("x").getAsFloat());
      } else if (root.has("x")) {
         cosmetic.setX(root.get("x").getAsFloat());
      }

      if (cfg.has("y")) {
         cosmetic.setY(cfg.get("y").getAsFloat());
      } else if (root.has("y")) {
         cosmetic.setY(root.get("y").getAsFloat());
      }

      if (cfg.has("z")) {
         cosmetic.setZ(cfg.get("z").getAsFloat());
      } else if (root.has("z")) {
         cosmetic.setZ(root.get("z").getAsFloat());
      }

      if (cfg.has("yaw")) {
         cosmetic.setYaw(cfg.get("yaw").getAsFloat());
      } else if (root.has("yaw")) {
         cosmetic.setYaw(root.get("yaw").getAsFloat());
      }

      if (cfg.has("pitch")) {
         cosmetic.setPitch(cfg.get("pitch").getAsFloat());
      } else if (root.has("pitch")) {
         cosmetic.setPitch(root.get("pitch").getAsFloat());
      }

      if (cfg.has("roll")) {
         cosmetic.setRoll(cfg.get("roll").getAsFloat());
      } else if (root.has("roll")) {
         cosmetic.setRoll(root.get("roll").getAsFloat());
      }

      if (cfg.has("previewScale")) {
         cosmetic.setPreviewScale(cfg.get("previewScale").getAsFloat());
      } else if (root.has("previewScale")) {
         cosmetic.setPreviewScale(root.get("previewScale").getAsFloat());
      }

      if (cfg.has("previewY")) {
         cosmetic.setPreviewY(cfg.get("previewY").getAsFloat());
      } else if (root.has("previewY")) {
         cosmetic.setPreviewY(root.get("previewY").getAsFloat());
      }
   }

   public Identifier loadTextureFromBase64(String name, int id, String base64) {
      try {
         String key = name.replace(" ", "").toLowerCase() + "_" + id;
         String cacheKey = "cosmetic_" + key;
         if (this.textureCache.containsKey(cacheKey)) {
            return this.textureCache.get(cacheKey);
         }

         byte[] bytes = Base64.getDecoder().decode(base64);
         ByteArrayInputStream in = new ByteArrayInputStream(bytes);
         NativeImage image = NativeImage.read(in);
         Identifier idRes = Identifier.of("lexoravisauls", "cosmetics/" + key);
         Runnable reg = () -> {
            try {
               NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
               MinecraftClient.getInstance().getTextureManager().registerTexture(idRes, tex);
               this.textureCache.put(cacheKey, idRes);
            } catch (Exception ex) {
               System.err.println("[Lexora Cosmetics] Texture register error: " + ex.getMessage());
            }
         };
         if (MinecraftClient.getInstance().isOnThread()) {
            reg.run();
         } else {
            MinecraftClient.getInstance().execute(reg);
         }

         this.textureCache.put(cacheKey, idRes);
         return idRes;
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Failed to load texture for: " + name + " -> " + e.getMessage());
         return null;
      }
   }

   public CosmeticModel getCosmetic(int id) {
      return this.loadedCosmetics.get(id);
   }

   public Map<Integer, CosmeticModel> getAllCosmetics() {
      return this.loadedCosmetics;
   }

   public void loadFromInputStream(InputStream in) {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         byte[] buf = new byte[1024];
         int r;
         while ((r = in.read(buf)) != -1) {
            baos.write(buf, 0, r);
         }
         String json = baos.toString(StandardCharsets.UTF_8.name());
         this.loadFromJson(json);
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Error loading cosmetic stream: " + e.getMessage());
      }
   }
}
