package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.item.ItemStack;

/**
 * Тонирование брони по проценту оставшейся прочности: белый (100%) -> зелёный -> жёлтый -> красный (~0%).
 * Сама таблица цветов перенесена 1:1 со старой базы — это чистая Java-логика, версия/лоадер тут ни при чём.
 * Единственное отличие от message.txt: используются актуальные методы ItemStack (они не менялись годами).
 */
public class ArmorDurabilityColor {

    private static final String MODULE_NAME = "Armor Durability";

    // Стейт-флаг ровно как у HitColorHandler.isHurt: ставится в HEAD рендера брони,
    // сбрасывается в RETURN. MixinModelPart читает его и красит нужный ModelPart.
    public static boolean isTinting = false;
    private static float[] currentTint = {1.0f, 1.0f, 1.0f};

    public static boolean isEnabled() {
        return LexoraGui.moduleStates.getOrDefault(MODULE_NAME, false);
    }

    /** Вызывается из MixinArmorFeatureRenderer в @At("HEAD") renderArmor(...). */
    public static void beginTint(ItemStack stack) {
        float[] tint = getDurabilityTint(stack);
        if (tint[0] == 1.0f && tint[1] == 1.0f && tint[2] == 1.0f) {
            isTinting = false; // 100% прочности или модуль выключен — красить нечего
            return;
        }
        currentTint = tint;
        isTinting = true;
    }

    /** Вызывается из MixinArmorFeatureRenderer в @At("RETURN") renderArmor(...). */
    public static void endTint() {
        isTinting = false;
    }

    /**
     * Перемножает ARGB packed-color (тот самый int color из ModelPart.render) на текущий тон.
     * Альфа не трогается.
     */
    public static int applyTint(int packedColor) {
        int a = (packedColor >> 24) & 0xFF;
        int r = (packedColor >> 16) & 0xFF;
        int g = (packedColor >> 8) & 0xFF;
        int b = packedColor & 0xFF;

        r = (int) (r * currentTint[0]);
        g = (int) (g * currentTint[1]);
        b = (int) (b * currentTint[2]);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * RGB-множитель для тонирования модели брони.
     * {1f, 1f, 1f} = без изменений (модуль выключен, предмет не повреждаемый или прочность 100%).
     */
    public static float[] getDurabilityTint(ItemStack stack) {
        if (!isEnabled() || stack.isEmpty() || !stack.isDamageable()) {
            return new float[]{1.0f, 1.0f, 1.0f};
        }

        float durability = 1.0f;
        if (stack.isDamaged()) {
            int maxDamage = stack.getMaxDamage();
            int damage = stack.getDamage();
            durability = (float) (maxDamage - damage) / maxDamage;
        }

        int interval = (int) (durability * 100 / 5); // 0..20, шаг по 5%

        switch (interval) {
            case 19: return new float[]{0.8f, 1.0f, 0.8f};
            case 18: return new float[]{0.6f, 1.0f, 0.6f};
            case 17: return new float[]{0.4f, 1.0f, 0.4f};
            case 16: return new float[]{0.2f, 1.0f, 0.2f};
            case 15: return new float[]{0.0f, 1.0f, 0.0f};
            case 14: return new float[]{0.0f, 0.9f, 0.0f};
            case 13: return new float[]{0.0f, 0.8f, 0.0f};
            case 12: return new float[]{0.0f, 0.7f, 0.0f};
            case 11: return new float[]{0.0f, 0.6f, 0.0f};
            case 10: return new float[]{0.5f, 1.0f, 0.0f};
            case 9:  return new float[]{0.7f, 1.0f, 0.0f};
            case 8:  return new float[]{0.9f, 1.0f, 0.0f};
            case 7:  return new float[]{1.0f, 1.0f, 0.0f};
            case 6:  return new float[]{1.0f, 0.8f, 0.0f};
            case 5:  return new float[]{1.0f, 0.6f, 0.0f};
            case 4:  return new float[]{1.0f, 0.4f, 0.0f};
            case 3:  return new float[]{1.0f, 0.2f, 0.0f};
            case 2:  return new float[]{1.0f, 0.0f, 0.0f};
            case 1:  return new float[]{0.8f, 0.0f, 0.0f};
            case 0:  return new float[]{0.6f, 0.0f, 0.0f};
            default: return new float[]{1.0f, 1.0f, 1.0f}; // 20 (100%) и край
        }
    }
}
