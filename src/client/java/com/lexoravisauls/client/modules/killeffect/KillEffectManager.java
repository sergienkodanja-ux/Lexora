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
    private static final boolean DEBUG = false;

    private static final java.util.Map<UUID, Long> LAST_TRIGGERED = new java.util.HashMap<>();
    private static final double PROXIMITY_FALLBACK_RANGE = 16.0;

    static final List<ActiveEffect<?>> ACTIVE = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        KillEffectRenderer.register();
        KillEffectHealthWatcher.register();
        KillEffectDisappearWatcher.register();
        KillEffectTestCommand.register();
    }

    public static void trigger(LivingEntity victim, DamageSource source) {
        if (victim == null) return;
        triggerAt(victim, victim.getX(), victim.getY(), victim.getZ(), source);
    }

    public static void triggerAt(LivingEntity victim, double x, double y, double z, DamageSource source) {
        if (victim == null) {
            return;
        }

        if (!(victim instanceof PlayerEntity)) {
            return; // players only
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (victim == client.player) {
            return;
        }

        UUID victimId = victim.getUuid();
        long now = System.currentTimeMillis();
        Long lastTime = LAST_TRIGGERED.get(victimId);
        if (lastTime != null && now - lastTime < 2500L) {
            return;
        }

        for (ActiveEffect<?> active : ACTIVE) {
            if (active.victimId.equals(victimId)) {
                return;
            }
        }

        boolean moduleOn = KillEffectSettings.moduleEnabled();
        if (!moduleOn) {
            return;
        }

        boolean anyDeath = KillEffectSettings.bool("Trigger On Any Death", false);
        Entity attacker = source != null ? source.getAttacker() : null;
        boolean attackerMatched = attacker == client.player;
        boolean recentlyHit = com.lexoravisauls.client.utils.AttackManager.wasRecentlyHitByMe(victimId, 7000L);
        boolean attackerUnknown = attacker == null;
        double distSq = client.player.squaredDistanceTo(x, y, z);
        boolean nearby = distSq <= PROXIMITY_FALLBACK_RANGE * PROXIMITY_FALLBACK_RANGE;

        boolean isOwnKill = attackerMatched || recentlyHit || (attackerUnknown && nearby);
        if (!anyDeath && !isOwnKill) {
            return;
        }

        LAST_TRIGGERED.put(victimId, now);

        // Ground snapping check: ensures ghost stands cleanly on ground level
        double groundY = y;
        net.minecraft.util.math.BlockPos basePos = net.minecraft.util.math.BlockPos.ofFloored(x, y, z);
        for (int dy = 0; dy <= 2; dy++) {
            net.minecraft.util.math.BlockPos check = basePos.down(dy);
            if (!client.world.getBlockState(check).isAir()) {
                groundY = check.getY() + 1.0;
                break;
            }
        }

        String modeName = KillEffectSettings.mode("Kill Effect Mode", KillEffectType.SOUL_ASCENSION.getDisplayName());
        KillEffectType type = KillEffectType.fromDisplayName(modeName);

        startAt(type, victim, x, groundY, z, client.player.age);
    }

    private static <T> void start(KillEffectType type, LivingEntity victim, int startAge) {
        triggerAt(victim, victim.getX(), victim.getY(), victim.getZ(), null);
    }

    public static <T> void startAt(KillEffectType type, LivingEntity victim, double x, double y, double z, int startAge) {
        @SuppressWarnings("unchecked")
        KillEffect<T> effect = (KillEffect<T>) type.getEffect();

        T state = effect.onStart(victim, x, y, z, RANDOM);
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
