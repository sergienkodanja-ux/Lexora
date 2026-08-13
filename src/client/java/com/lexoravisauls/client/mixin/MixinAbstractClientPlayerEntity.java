package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.render.cape.sim.CapeHolder;
import com.lexoravisauls.client.render.cape.sim.StickSimulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractClientPlayerEntity.class)
public class MixinAbstractClientPlayerEntity implements CapeHolder {

    private static final int PART_COUNT = 16;

    // ── Симуляция ─────────────────────────────────────────────────────────────
    @Unique private final StickSimulation lexora_simulation = new StickSimulation();
    @Unique private long lexora_tickCount = 0;

    // ── Эмуляция chasingPos (getCapeX/Z из старых версий Minecraft) ──────────
    // Плащ "тянется" за телом с инерцией — это создаёт волнение при движении.
    @Unique private double lexora_capeX = Double.MAX_VALUE;
    @Unique private double lexora_capeY = Double.MAX_VALUE;
    @Unique private double lexora_capeZ = Double.MAX_VALUE;

    // ── Оптимизация: пропускаем симуляцию если игрок далеко ──────────────────
    @Unique private static final double MAX_SIM_DISTANCE_SQ = 64.0 * 64.0;

    // =========================================================================
    //  CapeHolder interface
    // =========================================================================

    @Override
    public StickSimulation getSimulation() { return lexora_simulation; }

    // Переопределяем default-методы интерфейса чтобы вернуть реальные значения
    @Override
    public double getCapeX() {
        return lexora_capeX == Double.MAX_VALUE
                ? ((AbstractClientPlayerEntity)(Object)this).getX()
                : lexora_capeX;
    }

    @Override
    public double getCapeZ() {
        return lexora_capeZ == Double.MAX_VALUE
                ? ((AbstractClientPlayerEntity)(Object)this).getZ()
                : lexora_capeZ;
    }

    // =========================================================================
    //  Tick — обновляем позицию плаща и запускаем симуляцию
    // =========================================================================

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity)(Object)this;

        // ── Оптимизация: не симулируем далёких игроков ────────────────────────
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player != self) {
            double distSq = mc.player.squaredDistanceTo(self);
            if (distSq > MAX_SIM_DISTANCE_SQ) {
                // Сбрасываем симуляцию если игрок ушёл далеко —
                // чтобы при возврате плащ не "прыгал"
                if (!lexora_simulation.empty() && lexora_tickCount % 20 == 0) {
                    lexora_capeX = Double.MAX_VALUE;
                }
                return;
            }
        }

        // ── Обновляем "chasingPos" (инерция плаща) ────────────────────────────
        updateCapePosition(self);

        // ── Инициализируем и симулируем ───────────────────────────────────────
        updateSimulation(self, PART_COUNT);
        simulate(self, lexora_tickCount);

        lexora_tickCount++;
    }

    // =========================================================================
    //  Эмуляция chasingPos
    //
    //  В Minecraft 1.12-1.20 у AbstractClientPlayerEntity были поля:
    //    chasingPosX, chasingPosY, chasingPosZ
    //  которые обновлялись каждый тик с инерцией 0.25.
    //  В 1.21+ их убрали, поэтому воспроизводим вручную.
    // =========================================================================

    @Unique
    private void updateCapePosition(AbstractClientPlayerEntity player) {
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        // Первый тик — инициализируем без инерции
        if (lexora_capeX == Double.MAX_VALUE) {
            lexora_capeX = px;
            lexora_capeY = py;
            lexora_capeZ = pz;
            return;
        }

        double dx = px - lexora_capeX;
        double dy = py - lexora_capeY;
        double dz = pz - lexora_capeZ;

        // Если игрок телепортировался — сбрасываем без инерции
        double distSq = dx*dx + dy*dy + dz*dz;
        if (distSq > 100.0) {
            lexora_capeX = px;
            lexora_capeY = py;
            lexora_capeZ = pz;
            return;
        }

        // Инерция 0.25 за тик — точно как в оригинальном Minecraft
        lexora_capeX += dx * 0.25;
        lexora_capeY += dy * 0.25;
        lexora_capeZ += dz * 0.25;
    }
}