package com.lexoravisauls.client.modules.weather.winter;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.weather.WeatherFX;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Иней на броню игроков/мобов в зимнем режиме атмосферы.
 * 1. Тонирование текстуры брони в холодный морозный оттенок со снежным блеском.
 * 2. Полупрозрачный слой кристаллов инея поверх модели брони.
 */
public class WinterArmorFrost {

    private static boolean frostTinting = false;

    public static boolean isArmorFrostEnabled() {
        if (!WeatherFX.isModuleEnabled()) return false;
        String mode = LexoraGui.modeSettings.getOrDefault("Weather Visual Mode", "Rain");
        if (!mode.equals("Winter")) return false;
        return LexoraGui.moduleStates.getOrDefault("Winter Frost",
                LexoraGui.moduleStates.getOrDefault("Winter Armor Frost", true));
    }

    public static boolean isFrostTinting() {
        return frostTinting && isArmorFrostEnabled();
    }

    public static void beginFrost(ItemStack stack) {
        if (!isArmorFrostEnabled() || stack == null || stack.isEmpty()) {
            frostTinting = false;
            return;
        }
        frostTinting = true;
    }

    public static void endFrost(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, BipedEntityModel<?> armorModel) {
        frostTinting = false;
    }

    /**
     * Тонирование цвета брони в морозный серебристо-голубой оттенок инея.
     * Никаких наложенных текстур снега — только красивый холодный инеевый оттенок.
     */
    public static int applyFrostTint(int packedColor) {
        int a = (packedColor >> 24) & 0xFF;
        int r = (packedColor >> 16) & 0xFF;
        int g = (packedColor >> 8) & 0xFF;
        int b = packedColor & 0xFF;

        r = MathHelper.clamp((int) (r * 0.72f + 30), 0, 255);
        g = MathHelper.clamp((int) (g * 0.86f + 38), 0, 255);
        b = MathHelper.clamp((int) (b * 1.00f + 55), 0, 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
