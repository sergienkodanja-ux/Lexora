package com.lexoravisauls.client.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.UUID;

public class AttackManager {
    public static boolean lastHitWasCrit = false;
    public static UUID lastTargetId = null;
    public static long lastHitTime = 0;

    public static void registerHit(Entity target, boolean isCrit) {
        if (target != null) {
            lastTargetId = target.getUuid();
            lastHitWasCrit = isCrit;
            lastHitTime = System.currentTimeMillis();

            if (target instanceof LivingEntity living) {
                AuraParticles.onHit(living);
            }
        }
    }

    public static boolean isRecentCrit(Entity entity) {
        return lastHitWasCrit
                && lastTargetId != null
                && lastTargetId.equals(entity.getUuid())
                && (System.currentTimeMillis() - lastHitTime < 1000);
    }
}