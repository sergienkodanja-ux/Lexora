package com.lexoravisauls.client.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.cosmetic.loader.CosmeticLoader;
import com.lexoravisauls.client.cosmetic.model.CosmeticModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticManager {
   private static final CosmeticManager INSTANCE = new CosmeticManager();
   private static final int MAX_SCAN_INDEX = 256;
   private static final int MAX_EQUIPPED = 5;

   public static final Identifier DEFAULT_LEXORA_CAPE = Identifier.of("lexoravisauls", "textures/cape.png");

   public static class CapeAnimationInfo {
      public final int frameCount;
      public final int frameTime; // in ticks (1 tick = 50ms)
      public final boolean isTight; // true: 22x17 format, false: 64x32 format
      public final float baseWidth;
      public final float baseHeight;

      public CapeAnimationInfo(int frameCount, int frameTime, boolean isTight) {
         this.frameCount = Math.max(1, frameCount);
         this.frameTime = Math.max(1, frameTime);
         this.isTight = isTight;
         this.baseWidth = isTight ? 22.0f : 64.0f;
         this.baseHeight = isTight ? 17.0f : 32.0f;
      }

      public int getCurrentFrame() {
         if (this.frameCount <= 1) return 0;
         long durationMs = this.frameTime * 50L;
         return (int) ((System.currentTimeMillis() / durationMs) % this.frameCount);
      }

      public float getU(float x) {
         return x / this.baseWidth;
      }

      public float getV(float y, int frame) {
         return (frame + (y / this.baseHeight)) / (float) this.frameCount;
      }
   }

   public static class CosmeticEntry {
      public final int index;
      public final String name;
      public final String type;
      public final Identifier texture;
      public final boolean isCape;
      public final String fileName;

      public CosmeticEntry(int index, String name, String type, boolean isCape) {
         this(index, name, type, isCape,
              index == 999 ? DEFAULT_LEXORA_CAPE : Identifier.of("lexoravisauls", "textures/cosmetics/cosmetic_" + index + ".png"),
              null);
      }

      public CosmeticEntry(int index, String name, String type, boolean isCape, Identifier texture, String fileName) {
         this.index = index;
         this.name = name;
         this.type = type;
         this.isCape = isCape;
         this.texture = texture;
         this.fileName = fileName;
      }

      public Identifier getTexture() {
         return this.texture;
      }
   }

   private final List<CosmeticEntry> entries = new ArrayList<>();
   private final Map<String, Integer> selectedByType = new LinkedHashMap<>();
   private final Map<Integer, CosmeticModel> loadedModelCache = new ConcurrentHashMap<>();
   private final Map<Identifier, CapeAnimationInfo> capeAnimationCache = new ConcurrentHashMap<>();

   private boolean customCapeEnabled = true;
   private File configFile;

   private CosmeticManager() {
   }

   public static CosmeticManager getInstance() {
      return INSTANCE;
   }

   public void init() {
      this.scanAndLoadEntries();
      this.configFile = new File(MinecraftClient.getInstance().runDirectory, "config/lexora_cosmetics.json");
      this.loadConfig();
   }

   public File getCustomCapesDir() {
      File dir = new File(MinecraftClient.getInstance().runDirectory, "lexora/capes");
      if (!dir.exists()) {
         dir.mkdirs();
      }
      return dir;
   }

   public void openCustomCapesFolder() {
      File dir = this.getCustomCapesDir();
      try {
         net.minecraft.util.Util.getOperatingSystem().open(dir);
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Failed to open capes folder: " + e.getMessage());
      }
      this.reloadCustomCapes();
   }

   private void scanAndLoadEntries() {
      this.entries.clear();

      // Фирменный плащ Лексоры
      this.entries.add(new CosmeticEntry(999, "Lexora Cape", "cape", true));
      this.capeAnimationCache.put(DEFAULT_LEXORA_CAPE, new CapeAnimationInfo(1, 1, false));

      for (int i = 0; i < MAX_SCAN_INDEX; i++) {
         if (i == 2 || i == 21 || i == 25 || i == 44 || i == 50) continue; // Dark Cape, Easter Wings, Spider Wings, akvi4_Pet 2, Dark Hat removed
         String path = "/assets/lexoravisauls/cosmetics/models/cosmetic_" + i + ".json";
         try (InputStream in = CosmeticManager.class.getResourceAsStream(path)) {
            if (in != null) {
               String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
               JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
               String rawName = obj.has("name") ? obj.get("name").getAsString() : "Cosmetic " + (i + 1);
               String rawType = obj.has("type") ? obj.get("type").getAsString() : "";
               int pos = obj.has("pos") ? obj.get("pos").getAsInt() : -1;
               String classified = classifyType(i, rawName, rawType, pos);
               boolean isCape = "cape".equalsIgnoreCase(classified);
               CosmeticEntry entry = new CosmeticEntry(i, displayName(rawName, i), classified, isCape);
               this.entries.add(entry);

               if (isCape) {
                  if (obj.has("capeAnimation")) {
                     JsonObject anim = obj.getAsJsonObject("capeAnimation");
                     int frameCount = anim.has("frameCount") ? anim.get("frameCount").getAsInt() : 1;
                     int frameTime = anim.has("frameTime") ? anim.get("frameTime").getAsInt() : 2;
                     int frameW = anim.has("frameWidth") ? anim.get("frameWidth").getAsInt() : 64;
                     int frameH = anim.has("frameHeight") ? anim.get("frameHeight").getAsInt() : 32;
                     boolean isTight = (frameW % 22 == 0 && frameH % 17 == 0);
                     this.capeAnimationCache.put(entry.getTexture(), new CapeAnimationInfo(frameCount, frameTime, isTight));
                  } else {
                     // Known hardcoded animated capes check
                     if (i == 11) {
                        this.capeAnimationCache.put(entry.getTexture(), new CapeAnimationInfo(11, 3, true));
                     } else if (i == 12) {
                        this.capeAnimationCache.put(entry.getTexture(), new CapeAnimationInfo(14, 1, true));
                     } else if (i == 13) {
                        this.capeAnimationCache.put(entry.getTexture(), new CapeAnimationInfo(5, 4, true));
                     } else {
                        this.capeAnimationCache.put(entry.getTexture(), new CapeAnimationInfo(1, 1, false));
                     }
                  }
               }
            }
         } catch (Exception ignored) {
         }
      }

      this.loadCustomCapes();

      this.entries.sort(Comparator.comparingInt(e -> e.index));
      System.out.println("[Lexora Cosmetics] Loaded " + this.entries.size() + " cosmetic entries!");
   }

   public synchronized void loadCustomCapes() {
      File dir = this.getCustomCapesDir();
      File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
      if (files == null || files.length == 0) {
         return;
      }
      Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

      for (int i = 0; i < files.length; i++) {
         File file = files[i];
         try {
            String baseName = file.getName().substring(0, file.getName().length() - 4);
            String cleanId = baseName.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
            Identifier tex = Identifier.of("lexoravisauls", "custom_cape_" + cleanId);

            try (InputStream in = new FileInputStream(file)) {
               NativeImage image = NativeImage.read(in);
               int w = image.getWidth();
               int h = image.getHeight();

               boolean isTight = (w % 22 == 0) && (h % ((w / 22) * 17) == 0);
               int frameCount = 1;
               if (isTight) {
                  int frameH = (w / 22) * 17;
                  frameCount = Math.max(1, h / frameH);
               } else {
                  int frameH = w / 2;
                  if (frameH > 0 && h % frameH == 0) {
                     frameCount = Math.max(1, h / frameH);
                  } else if (h > w) {
                     frameCount = Math.max(1, h / (w / 2));
                  }
               }
               int frameTime = 2;

               File jsonFile = new File(dir, baseName + ".json");
               if (jsonFile.exists()) {
                  try {
                     JsonObject obj = JsonParser.parseString(Files.readString(jsonFile.toPath())).getAsJsonObject();
                     if (obj.has("capeAnimation")) {
                        JsonObject anim = obj.getAsJsonObject("capeAnimation");
                        if (anim.has("frameCount")) frameCount = anim.get("frameCount").getAsInt();
                        if (anim.has("frameTime")) frameTime = anim.get("frameTime").getAsInt();
                     }
                  } catch (Exception ignored) {}
               }

               CapeAnimationInfo animInfo = new CapeAnimationInfo(frameCount, frameTime, isTight);
               this.capeAnimationCache.put(tex, animInfo);

               Runnable reg = () -> {
                  try {
                     NativeImageBackedTexture dynamicTex = new NativeImageBackedTexture(image);
                     MinecraftClient.getInstance().getTextureManager().registerTexture(tex, dynamicTex);
                  } catch (Exception ex) {
                     System.err.println("[Lexora Cosmetics] Failed to register custom cape texture: " + ex.getMessage());
                  }
               };
               if (MinecraftClient.getInstance().isOnThread()) {
                  reg.run();
               } else {
                  MinecraftClient.getInstance().execute(reg);
               }
            }

            int customIndex = 10000 + i;
            String displayName = formatCustomName(baseName);
            CosmeticEntry entry = new CosmeticEntry(customIndex, displayName, "cape", true, tex, file.getName());
            this.entries.add(entry);
         } catch (Exception e) {
            System.err.println("[Lexora Cosmetics] Error loading custom cape " + file.getName() + ": " + e.getMessage());
         }
      }
   }

   public synchronized void reloadCustomCapes() {
      String equippedCustomFile = null;
      Integer currentCapeIdx = this.selectedByType.get("cape");
      if (currentCapeIdx != null) {
         for (CosmeticEntry e : this.entries) {
            if (e.index == currentCapeIdx && e.fileName != null) {
               equippedCustomFile = e.fileName;
               break;
            }
         }
      }

      this.entries.removeIf(e -> e.index >= 10000);
      this.loadCustomCapes();

      if (equippedCustomFile != null) {
         for (CosmeticEntry e : this.entries) {
            if (equippedCustomFile.equalsIgnoreCase(e.fileName)) {
               this.selectedByType.put("cape", e.index);
               break;
            }
         }
      }
   }

   public CapeAnimationInfo getCapeAnimation(Identifier texture) {
      if (texture == null) return new CapeAnimationInfo(1, 1, false);
      CapeAnimationInfo cached = this.capeAnimationCache.get(texture);
      if (cached != null) return cached;

      String path = texture.getPath();
      if (path.contains("cosmetic_11")) {
         cached = new CapeAnimationInfo(11, 3, true);
      } else if (path.contains("cosmetic_12")) {
         cached = new CapeAnimationInfo(14, 1, true);
      } else if (path.contains("cosmetic_13")) {
         cached = new CapeAnimationInfo(5, 4, true);
      } else {
         cached = new CapeAnimationInfo(1, 1, false);
      }
      this.capeAnimationCache.put(texture, cached);
      return cached;
   }

   private static String formatCustomName(String name) {
      if (name == null || name.isBlank()) return "Custom Cape";
      String clean = name.replace('_', ' ').replace('-', ' ').trim();
      StringBuilder sb = new StringBuilder();
      for (String part : clean.split("\\s+")) {
         if (!part.isEmpty()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
               sb.append(part.substring(1).toLowerCase());
            }
         }
      }
      return sb.toString();
   }

   public List<CosmeticEntry> getEntries() {
      return this.entries;
   }

   public List<CosmeticEntry> getFilteredEntries(String category) {
      if (category == null || category.isEmpty() || "all".equalsIgnoreCase(category)) {
         return this.entries;
      }
      List<CosmeticEntry> filtered = new ArrayList<>();
      for (CosmeticEntry e : this.entries) {
         if (e.type.equalsIgnoreCase(category)) {
            filtered.add(e);
         }
      }
      return filtered;
   }

   public boolean isEquipped(CosmeticEntry entry) {
      if (entry == null) return false;
      Integer eq = this.selectedByType.get(entry.type);
      return eq != null && eq == entry.index;
   }

   public void toggle(CosmeticEntry entry) {
      if (entry == null) return;
      Integer old = this.selectedByType.get(entry.type);
      if (old != null && old == entry.index) {
         if (entry.isCape) {
            this.selectedByType.put("cape", 999);
         } else {
            this.selectedByType.remove(entry.type);
         }
      } else {
         if (this.selectedByType.size() >= MAX_EQUIPPED && !this.selectedByType.containsKey(entry.type)) {
            Iterator<String> it = this.selectedByType.keySet().iterator();
            if (it.hasNext()) {
               it.next();
               it.remove();
            }
         }
         this.selectedByType.put(entry.type, entry.index);
         if (!entry.isCape) {
            this.getModel(entry.index);
         }
      }
      this.saveConfig();
      com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.onCosmeticsChanged();
   }

   public void clearAllEquipped() {
      this.selectedByType.clear();
      this.selectedByType.put("cape", 999);
      this.saveConfig();
      com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.onCosmeticsChanged();
   }

   public Map<String, Integer> getSelectedByType() {
      return Collections.unmodifiableMap(this.selectedByType);
   }

   public CosmeticModel getModel(int index) {
      if (index == 999) return null;
      return this.loadedModelCache.computeIfAbsent(index, k -> {
         String path = "/assets/lexoravisauls/cosmetics/models/cosmetic_" + k + ".json";
         try (InputStream in = CosmeticManager.class.getResourceAsStream(path)) {
            if (in == null) return null;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Identifier tex = Identifier.of("lexoravisauls", "textures/cosmetics/cosmetic_" + k + ".png");
            return CosmeticLoader.getInstance().loadFromJson(json, tex, k);
         } catch (Exception e) {
            System.err.println("[Lexora Cosmetics] Error loading model " + k + ": " + e.getMessage());
            return null;
         }
      });
   }

   public CosmeticModel getModelForEntry(CosmeticEntry entry) {
      if (entry == null || entry.isCape) return null;
      return this.getModel(entry.index);
   }

   public List<CosmeticModel> getEquipped3DModels() {
      List<CosmeticModel> list = new ArrayList<>();
      for (Map.Entry<String, Integer> entry : this.selectedByType.entrySet()) {
         if ("cape".equalsIgnoreCase(entry.getKey())) continue;
         CosmeticModel model = this.getModel(entry.getValue());
         if (model != null) {
            list.add(model);
         }
      }
      return list;
   }

   public Identifier getActiveCapeTexture() {
      if (!this.customCapeEnabled) return null;
      Integer capeIndex = this.selectedByType.get("cape");
      if (capeIndex == null || capeIndex == 999) {
         return DEFAULT_LEXORA_CAPE;
      }
      return this.getCapeTexture(capeIndex);
   }

   public Identifier getCapeTexture(int index) {
      if (index == 999) return DEFAULT_LEXORA_CAPE;
      for (CosmeticEntry e : this.entries) {
         if (e.index == index) return e.getTexture();
      }
      return Identifier.of("lexoravisauls", "textures/cosmetics/cosmetic_" + index + ".png");
   }

   public boolean isCustomCapeEnabled() {
      return this.customCapeEnabled;
   }

   public void setCustomCapeEnabled(boolean customCapeEnabled) {
      this.customCapeEnabled = customCapeEnabled;
      this.saveConfig();
      com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.onCosmeticsChanged();
   }

   public int getEquippedCount() {
      return this.selectedByType.size();
   }

   public int getMaxEquipped() {
      return MAX_EQUIPPED;
   }

   private static String displayName(String rawName, int index) {
      String name = rawName == null || rawName.isBlank() ? "Cosmetic " + (index + 1) : rawName;
      if (name.startsWith("pulse_")) {
         name = name.substring("pulse_".length());
      }
      return name.replace('_', ' ').trim();
   }

   private static String classifyType(int index, String name, String rawType, int pos) {
      if (rawType != null && !rawType.isBlank()) {
         return rawType.trim().toLowerCase();
      }
      String lower = name == null ? "" : name.toLowerCase();
      if (lower.contains("cape")) return "cape";
      if (lower.contains("wing")) return "wings";
      if (lower.contains("pet") || lower.contains("bee") || lower.contains("radish")) return "pet";
      if (lower.contains("hat") || lower.contains("nimb") || pos == 2) return "hat";
      if (index <= 13) return "cape";
      if (index <= 26) return "wings";
      if (index <= 37) return "bodywear";
      if (index <= 49) return "pet";
      return "bodywear";
   }

   public void saveConfig() {
      try {
         if (this.configFile == null) return;
         JsonObject obj = new JsonObject();
         obj.addProperty("customCape", this.customCapeEnabled);
         JsonObject eq = new JsonObject();
         for (Map.Entry<String, Integer> e : this.selectedByType.entrySet()) {
            eq.addProperty(e.getKey(), e.getValue());
         }
         obj.add("equipped", eq);

         Integer capeIdx = this.selectedByType.get("cape");
         if (capeIdx != null && capeIdx >= 10000) {
            for (CosmeticEntry e : this.entries) {
               if (e.index == capeIdx && e.fileName != null) {
                  obj.addProperty("customCapeFile", e.fileName);
                  break;
               }
            }
         }

         if (!this.configFile.getParentFile().exists()) {
            this.configFile.getParentFile().mkdirs();
         }
         Files.writeString(this.configFile.toPath(), obj.toString(), StandardCharsets.UTF_8);
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Failed to save cosmetics config: " + e.getMessage());
      }
   }

   public void loadConfig() {
      try {
         if (this.configFile == null || !this.configFile.exists()) {
            this.selectedByType.put("cape", 999);
            return;
         }
         String content = Files.readString(this.configFile.toPath(), StandardCharsets.UTF_8);
         JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
         if (obj.has("customCape")) this.customCapeEnabled = obj.get("customCape").getAsBoolean();
         this.selectedByType.clear();
         if (obj.has("equipped")) {
            JsonObject eq = obj.getAsJsonObject("equipped");
            for (String key : eq.keySet()) {
               int val = eq.get(key).getAsInt();
               this.selectedByType.put(key, val);
               if (!"cape".equalsIgnoreCase(key)) {
                  this.getModel(val);
               }
            }
         }

         if (obj.has("customCapeFile")) {
            String customFile = obj.get("customCapeFile").getAsString();
            for (CosmeticEntry e : this.entries) {
               if (e.fileName != null && customFile.equalsIgnoreCase(e.fileName)) {
                  this.selectedByType.put("cape", e.index);
                  break;
               }
            }
         }

         if (!this.selectedByType.containsKey("cape")) {
            this.selectedByType.put("cape", 999);
         }
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Failed to load cosmetics config: " + e.getMessage());
         this.selectedByType.put("cape", 999);
      }
   }
}
