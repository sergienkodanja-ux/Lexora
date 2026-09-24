package com.lexoravisauls.client.cosmetic.geckolib;

import com.lexoravisauls.client.cosmetic.geo.GeoModel;
import com.lexoravisauls.client.cosmetic.geo.GeoModelParser;
import com.lexoravisauls.client.cosmetic.model.CosmeticModel;

public class GeckolibModelParser {
   public GeoModel parseModel(CosmeticModel cosmetic) {
      try {
         String json = cosmetic.getRawModelJson();
         if (json == null) {
            System.err.println("[Lexora Cosmetics] No raw model JSON for: " + cosmetic.getName());
            return null;
         } else {
            GeoModel model = GeoModelParser.parse(json);
            if (model == null) {
               System.err.println("[Lexora Cosmetics] Failed to parse GeoModel for: " + cosmetic.getName());
               return null;
            } else {
               return model;
            }
         }
      } catch (Exception e) {
         System.err.println("[Lexora Cosmetics] Error parsing model for: " + cosmetic.getName() + " -> " + e.getMessage());
         return null;
      }
   }
}
