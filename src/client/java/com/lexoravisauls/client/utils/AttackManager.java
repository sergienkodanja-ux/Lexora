package com.lexoravisauls.client.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.UUID;

public class AttackManager {
    public static boolean lastHitWasCrit = false;
    public static UUID lastTargetId = null;
    public static long lastHitTime = 0;
    private static final java.util.Map<UUID, Long> RECENT_HITS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerHit(Entity target, boolean isCrit) {
        if (target != null) {
            lastTargetId = target.getUuid();
            lastHitWasCrit = isCrit;
            lastHitTime = System.currentTimeMillis();
            RECENT_HITS.put(target.getUuid(), lastHitTime);

            if (target instanceof LivingEntity living) {
                AuraParticles.onHit(living);
            }
        }
    }

    public static boolean wasRecentlyHitByMe(UUID uuid, long windowMs) {
        if (uuid == null) return false;
        Long time = RECENT_HITS.get(uuid);
        return time != null && (System.currentTimeMillis() - time <= windowMs);
    }

    public static boolean isRecentCrit(Entity entity) {
        return lastHitWasCrit
                && lastTargetId != null
                && lastTargetId.equals(entity.getUuid())
                && (System.currentTimeMillis() - lastHitTime < 1000);
    }
}