package com.lexoravisauls.client.render.cape.sim;

import com.lexoravisauls.client.render.cape.sim.Vector2;
import com.lexoravisauls.client.render.cape.sim.Vector3;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public interface CapeHolder {

    StickSimulation getSimulation();

    default void updateSimulation(AbstractClientPlayerEntity player, int partCount) {
        StickSimulation sim = getSimulation();
        if (sim == null) return;
        boolean dirty = sim.init(partCount);
        if (dirty) {
            sim.applyMovement(new Vector3(1.0f, 1.0f, 0.0f));
            for (int i = 0; i < 5; i++) simulate(player, 0L);
        }
    }

    default void simulate(AbstractClientPlayerEntity player, long tickCount) {
        StickSimulation sim = getSimulation();
        if (sim == null || sim.empty()) return;

        float gravity   = 25.0f;
        float heightMul = 6.0f;
        float straveMul = 2.0f;

        double capeX = getCapeX();
        double capeZ = getCapeZ();
        double d = capeX - player.getX();
        double m = capeZ - player.getZ();

        float bodyYaw = player.prevBodyYaw + (player.bodyYaw - player.prevBodyYaw);
        double sinYaw = MathHelper.sin(bodyYaw * 0.017453292f);
        double cosYaw = -MathHelper.cos(bodyYaw * 0.017453292f);

        if (player.isTouchingWater()) {
            gravity   *= 0.1f;
            heightMul *= 2.0f;
        }

        double fallHack = MathHelper.clamp(
                (player.prevY - player.getY()) * 10.0, 0.0, 1.0);

        sim.setGravity(gravity);
        Vector3 gravDir = new Vector3(0.0f, -1.0f, 0.0f);

        Vector2 strave = new Vector2(
                (float)(player.getX() - player.prevX),
                (float)(player.getZ() - player.prevZ));
        strave.rotateDegrees(-player.bodyYaw);

        boolean wasSneaking = sim.isSneaking();
        double changeX = d * sinYaw + m * cosYaw + fallHack
                + ((player.isSneaking() && !wasSneaking) ? 3 : 0);
        double changeY = (player.getY() - player.prevY) * heightMul
                + ((player.isSneaking() && !wasSneaking) ? 1 : 0);
        double changeZ = -strave.x * straveMul;

        sim.setSneaking(player.isSneaking());
        Vector3 change = new Vector3((float)changeX, (float)changeY, (float)changeZ);

        if (player.isSwimming()) {
            float pitch = player.getPitch() + 90.0f;
            gravDir.rotateDegrees(pitch);
            change.rotateDegrees(pitch);
        }

        sim.setGravityDirection(gravDir);
        sim.applyMovement(change);
        sim.simulate();

        applyWindAfterSim(sim, player, tickCount);
    }

    default void applyWindAfterSim(StickSimulation sim,
                                   AbstractClientPlayerEntity player,
                                   long tickCount) {
        int n = sim.getPoints().size();

        // Ветер всегда активен — скорость движения не влияет на силу
        // Быстрее при движении, медленнее стоя — но всегда заметен
        double horizSpeed = Math.sqrt(
                (player.getX() - player.prevX) * (player.getX() - player.prevX) +
                        (player.getZ() - player.prevZ) * (player.getZ() - player.prevZ));
        float speed = (float) MathHelper.clamp(horizSpeed * 20.0, 0.0, 1.0);

        // Всегда сильный ветер — минимум 0.85 от максимума
        float amplitude = MathHelper.lerp(speed, 0.85f, 1.0f);
        // Скорость волны: стоя 0.12, бежим 0.18
        float timeScale = MathHelper.lerp(speed, 0.32f, 0.38f);
        // Фаза между точками — короче = длиннее волна = красивее
        float phaseStep = 0.18f;

        float t = tickCount * 0.07f;
        for (int i = 1; i < n; i++) {
            StickSimulation.Point point = sim.getPoints().get(i);

            float phase = t - i * phaseStep;

            // Большие амплитуды — плащ реально волнуется
            float wave = (float)(
                    Math.sin(phase)               * 0.28f +
                            Math.sin(phase * 0.63 + 1.1)  * 0.16f +
                            Math.sin(phase * 1.41 + 2.3)  * 0.10f +
                            Math.sin(phase * 2.20 + 0.9)  * 0.06f
            ) * amplitude;

            // Линейный + минимум 0.25 — верх тоже заметно двигается
            float depth = 0.25f + 0.75f * ((float) i / (n - 1));

            float dx = wave * depth;
            float dy = wave * depth * 0.45f;

            // Только position — prevPosition не трогаем!
            // Физика видит разницу position-prevPosition как скорость,
            // sticks тянут соседние точки — плавная волна без дёрганья
            point.position.x += dx;
            point.position.y += dy;
        }
    }

    default double getCapeX() { return 0.0; }
    default double getCapeZ() { return 0.0; }
}