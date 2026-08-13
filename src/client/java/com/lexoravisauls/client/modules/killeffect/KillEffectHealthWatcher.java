package com.lexoravisauls.client.modules.killeffect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fallback death detection for servers that replace vanilla death with their
 * own instant-respawn/teleport logic (common on duel/practice servers) — on
 * those, {@code LivingEntity#onDeath} may simply never fire, because the
 * server never lets a real death happen in the first place.
 * <p>
 * Instead, this watches every visible player's synced health every tick and
 * calls the same {@link KillEffectManager#trigger} the instant it crosses
 * from above 0 down to 0 or below — that value is still synced normally even
 * on servers with custom "kill" logic, since health display doesn't work
 * otherwise. {@link KillEffectManager}'s own cooldown stops this from
 * double-firing on servers where a real death ALSO happens in the same moment.
 */
final class KillEffectHealthWatcher {

    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

    private KillEffectHealthWatcher() {
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(KillEffectHealthWatcher::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            if (!LAST_HEALTH.isEmpty()) {
                LAST_HEALTH.clear();
            }
            return;
        }

        Set<UUID> seen = new HashSet<>();

        for (PlayerEntity player : client.world.getPlayers()) {
            UUID id = player.getUuid();
            seen.add(id);

            float current = player.getHealth();
            Float previous = LAST_HEALTH.put(id, current);

            if (previous != null && previous > 0.0f && current <= 0.0f) {
                KillEffectManager.debug(player.getName().getString()
                        + ": health-watcher caught 0 HP (previous=" + previous + ")");
                DamageSource source = player.getRecentDamageSource();
                KillEffectManager.trigger(player, source);
            }
        }

        // Drop anyone no longer tracked (left render distance, disconnected, etc.)
        // so their old health doesn't linger and confuse a later comparison.
        LAST_HEALTH.keySet().retainAll(seen);
    }
}
