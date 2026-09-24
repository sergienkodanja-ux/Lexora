package com.lexoravisauls.client.modules.killeffect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Second fallback for duel/practice servers where even {@link KillEffectHealthWatcher}
 * never sees health reach 0 — some plugins intercept the killing blow entirely
 * server-side and teleport the loser away before a low-HP packet ever syncs to
 * nearby clients.
 * <p>
 * Base heuristic: if a nearby player was at very low HP and then simply
 * vanishes from the tracked player list a moment later, treat that as a kill.
 * <p>
 * Stronger signal on top: if freshly-spawned item entities (their dropped
 * inventory) appear right at their last known position the moment they
 * vanish, that's treated as a CONFIRMED kill regardless of the exact HP
 * reading — a real death dropping loot is a much harder thing to fake by
 * coincidence than a low health number. No items found there just falls back
 * to the plain low-HP check.
 * <p>
 * Explicitly guarded against the obvious false positive either way: a player
 * escaping at low HP via an ender pearl (or any teleport) doesn't get a new
 * entity — the same tracked entity just jumps position, and if that jump is
 * far enough to leave this client's entity-tracking range, they "vanish"
 * exactly like a kill would. If the last recorded position update before
 * vanishing was an abnormally large jump, this is treated as an escape, not a
 * kill, before either check above even runs.
 * <p>
 * Still just a heuristic, not a precise detector — e.g. disconnecting while
 * hurt (no position jump, no item drop) can still look identical to a kill
 * from here. Tell me about any other false triggers you notice.
 */
final class KillEffectDisappearWatcher {

    private static final float LOW_HP_THRESHOLD = 3.0f;
    private static final long WINDOW_MS = 1500L;
    private static final double TELEPORT_JUMP_THRESHOLD = 10.0; // blocks moved in one tick
    private static final double ITEM_SEARCH_RADIUS = 2.5;
    private static final int ITEM_MAX_AGE_TICKS = 10; // must have spawned very recently (0.5s)

    private static final Map<UUID, Snapshot> LAST_SEEN = new HashMap<>();

    private KillEffectDisappearWatcher() {
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(KillEffectDisappearWatcher::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            if (!LAST_SEEN.isEmpty()) {
                LAST_SEEN.clear();
            }
            return;
        }

        long now = System.currentTimeMillis();
        Set<UUID> seenNow = new HashSet<>();

        for (PlayerEntity player : client.world.getPlayers()) {
            UUID id = player.getUuid();
            seenNow.add(id);

            Vec3d pos = player.getPos();
            Snapshot previous = LAST_SEEN.get(id);
            boolean justTeleported = previous != null && previous.pos.distanceTo(pos) > TELEPORT_JUMP_THRESHOLD;

            LAST_SEEN.put(id, new Snapshot(player, player.getHealth(), now, pos, justTeleported));
        }

        for (UUID id : new HashSet<>(LAST_SEEN.keySet())) {
            if (seenNow.contains(id)) {
                continue;
            }
            Snapshot last = LAST_SEEN.remove(id);
            if (last == null) {
                continue;
            }

            boolean recentEnough = now - last.timestamp <= WINDOW_MS;
            if (!recentEnough) {
                continue;
            }

            // Items dropping is near-definitive proof of a real death — checked
            // FIRST and overrides the teleport guard below, because a
            // teleport-to-lobby respawn mechanic produces the exact same "big
            // jump then vanish" signature as an ender-pearl escape. Position
            // data alone can't tell them apart; a loot drop can.
            boolean itemsDropped = itemsDroppedNear(client.world, last.pos);
            boolean wasHitRecently = com.lexoravisauls.client.utils.AttackManager.wasRecentlyHitByMe(id, 6000L);

            if (itemsDropped) {
                KillEffectManager.triggerAt(last.player, last.pos.x, last.pos.y, last.pos.z, last.player.getRecentDamageSource());
                continue;
            }

            if (last.justTeleported && !wasHitRecently) {
                continue;
            }

            boolean lowHealth = last.health > 0.0f && last.health <= LOW_HP_THRESHOLD;
            if (lowHealth || wasHitRecently) {
                DamageSource source = last.player.getRecentDamageSource();
                KillEffectManager.triggerAt(last.player, last.pos.x, last.pos.y, last.pos.z, source);
            }
        }
    }

    private static boolean itemsDroppedNear(ClientWorld world, Vec3d pos) {
        Box box = new Box(pos.x - ITEM_SEARCH_RADIUS, pos.y - ITEM_SEARCH_RADIUS, pos.z - ITEM_SEARCH_RADIUS,
                pos.x + ITEM_SEARCH_RADIUS, pos.y + ITEM_SEARCH_RADIUS, pos.z + ITEM_SEARCH_RADIUS);
        List<ItemEntity> freshItems = world.getEntitiesByClass(ItemEntity.class, box,
                item -> item.age <= ITEM_MAX_AGE_TICKS);
        return !freshItems.isEmpty();
    }

    private static final class Snapshot {
        final PlayerEntity player;
        final float health;
        final long timestamp;
        final Vec3d pos;
        final boolean justTeleported;

        Snapshot(PlayerEntity player, float health, long timestamp, Vec3d pos, boolean justTeleported) {
            this.player = player;
            this.health = health;
            this.timestamp = timestamp;
            this.pos = pos;
            this.justTeleported = justTeleported;
        }
    }
}
