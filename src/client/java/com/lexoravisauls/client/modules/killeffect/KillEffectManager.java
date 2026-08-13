package com.lexoravisauls.client.modules.killeffect;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Central dispatcher for kill effects. Everything is hand-drawn per frame by
 * {@link KillEffectRenderer} — this class only decides WHEN an effect starts
 * and holds the live list of active instances.
 * <p>
 * Two independent things can call {@link #trigger}: the {@code onDeath} mixin
 * (real vanilla death) and {@link KillEffectHealthWatcher} (health drops to 0
 * even if the server never fires a real death — common on duel/practice
 * servers that instant-respawn players). Dedup is based on "does this player
 * already have an active effect playing" rather than a fixed time window —
 * that's what stops a brief HP flicker around a respawn from spawning two
 * overlapping instances (which was the "ghost keeps walking" bug: two ghosts
 * frozen a few frames apart, not one ghost actually moving).
 * <p>
 * Call {@link #init()} once from your ClientModInitializer.
 */
public final class KillEffectManager {

    /** Flip to true if you need to re-diagnose the trigger chain again. */
    private static final boolean DEBUG = true;

    /** How close you need to be to the victim for the "attacker unknown" proximity fallback to count as your kill. */
    private static final double PROXIMITY_FALLBACK_RANGE = 8.0;

    static final List<ActiveEffect<?>> ACTIVE = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static boolean initialized = false;

    private KillEffectManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        KillEffectRenderer.register();
        KillEffectHealthWatcher.register();
        KillEffectDisappearWatcher.register();
        KillEffectChatDebug.register(); // TEMP — see its javadoc, remove once we've got the duel-result text
        KillEffectTestCommand.register(); // TEMP — /ket, remove once you're done tuning
    }

    public static void trigger(LivingEntity victim, DamageSource source) {
        if (victim == null) {
            return;
        }

        if (!(victim instanceof PlayerEntity)) {
            return; // players only — all three effects assume a player model/skin
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (victim == client.player) {
            return; // never use the local player's own skin/armor/cape as the effect source
        }

        UUID victimId = victim.getUuid();
        for (ActiveEffect<?> active : ACTIVE) {
            if (active.victimId.equals(victimId)) {
                debug(victim.getName().getString() + ": ignored, this player already has an effect playing");
                return;
            }
        }

        boolean moduleOn = KillEffectSettings.moduleEnabled();
        debug(victim.getName().getString() + " died | Kill Effect module enabled = " + moduleOn);
        if (!moduleOn) {
            return;
        }

        if (victim.getWorld() != client.world) {
            debug("not the client world (this fired on the logical server)");
            return;
        }

        boolean anyDeath = KillEffectSettings.bool("Trigger On Any Death", false);
        Entity attacker = source != null ? source.getAttacker() : null;
        boolean attackerMatched = attacker == client.player;
        boolean attackerUnknown = attacker == null;
        boolean nearby = client.player.squaredDistanceTo(victim) <= PROXIMITY_FALLBACK_RANGE * PROXIMITY_FALLBACK_RANGE;
        // Fallback: DamageSource#getAttacker() isn't reliably attributed on
        // every server (seen attacker=null on plainly-your kills before, e.g.
        // creeper explosions). Only apply the fallback when the attacker is
        // genuinely unresolved — if it clearly names someone else, that's
        // trusted over proximity.
        boolean isOwnKill = attackerMatched || (attackerUnknown && nearby);
        debug("anyDeath=" + anyDeath + " isOwnKill=" + isOwnKill
                + " (attackerMatched=" + attackerMatched + ", attackerUnknown=" + attackerUnknown + ", nearby=" + nearby + ")");

        if (!anyDeath && !isOwnKill) {
            return;
        }

        String modeName = KillEffectSettings.mode("Kill Effect Mode", KillEffectType.SOUL_ASCENSION.getDisplayName());
        debug("playing: " + modeName);
        KillEffectType type = KillEffectType.fromDisplayName(modeName);

        start(type, victim, client.player.age);
    }

    private static <T> void start(KillEffectType type, LivingEntity victim, int startAge) {
        @SuppressWarnings("unchecked")
        KillEffect<T> effect = (KillEffect<T>) type.getEffect();

        // Feet-level position, not the bounding-box center — using the center
        // (roughly +0.9 blocks above the feet for a standing player) was why
        // every effect spawned noticeably too high; the effects' own internal
        // offsets (e.g. "+0.9" for a chest-height flash point) already assume
        // a feet-level baseline to add on top of.
        T state = effect.onStart(victim, victim.getX(), victim.getY(), victim.getZ(), RANDOM);
        int duration = Math.max(1, effect.getDurationTicks(state));
        ACTIVE.add(new ActiveEffect<>(effect, state, duration, startAge, victim.getUuid()));
    }

    /**
     * TEMP: bypasses every gate (module toggle, own-kill filter, dedup) —
     * used only by the /ket test command so you can preview effects instantly
     * without an actual kill. Not called from onDeath or either watcher.
     */
    public static void debugTrigger(LivingEntity victim, KillEffectType type) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        start(type, victim, client.player.age);
    }

    static void debug(String message) {
        if (!DEBUG) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§7[KillEffect] " + message), false);
        }
    }

    static final class ActiveEffect<T> {
        final KillEffect<T> effect;
        final T state;
        final int durationTicks;
        final int startAge;
        final UUID victimId;

        ActiveEffect(KillEffect<T> effect, T state, int durationTicks, int startAge, UUID victimId) {
            this.effect = effect;
            this.state = state;
            this.durationTicks = durationTicks;
            this.startAge = startAge;
            this.victimId = victimId;
        }

        void render(MatrixStack matrices, Camera camera, VertexConsumerProvider consumers, Vec3d camPos,
                    float progress, float ageWithDelta, Random random) {
            effect.render(state, matrices, camera, consumers, camPos, progress, ageWithDelta, random);
        }
    }
}
