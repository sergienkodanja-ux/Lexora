package com.lexoravisauls.client.utils;

import net.minecraft.entity.Entity;
import java.util.UUID;

public class AttackManager {
    public static boolean lastHitWasCrit = false;
    public static UUID lastTargetId = null; // Теперь используем UUID!
    public static long lastHitTime = 0;

    // Запоминаем удар
    public static void registerHit(Entity target, boolean isCrit) {
        if (target != null) {
            lastTargetId = target.getUuid();
            lastHitWasCrit = isCrit;
            lastHitTime = System.currentTimeMillis();
        }
    }

    // Спрашиваем: был ли этот моб кританут за последнюю секунду?
    public static boolean isRecentCrit(Entity entity) {
        return lastHitWasCrit
                && lastTargetId != null
                && lastTargetId.equals(entity.getUuid()) // Сравниваем по UUID
                && (System.currentTimeMillis() - lastHitTime < 1000);
    }
}