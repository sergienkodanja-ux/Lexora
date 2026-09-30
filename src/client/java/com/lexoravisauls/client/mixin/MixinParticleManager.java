package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.Optimization;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class MixinParticleManager {

    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void onRenderParticles(Camera camera, float tickDelta, VertexConsumerProvider.Immediate vertexConsumers, CallbackInfo ci) {
        if (Optimization.isNoAllParticles()) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void onAddParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
        if (parameters == null) return;
        // КРИТИЧНО: Ванильный FireworksSparkParticle$FireworkParticle при взрыве
        // вызывает addParticle(ParticleTypes.FIREWORK, ...) и без проверки на null
        // немедленно кастует результат к Explosion и вызывает .setTrail().
        // Если вернуть null — происходит гарантированный краш клиента (NPE в ticking particle).
        if (parameters.getType() == ParticleTypes.FIREWORK || parameters.getType() == ParticleTypes.FLASH) {
            return;
        }
        if (Optimization.shouldCancelParticle(parameters, x, y, z)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void onTickParticle(Particle particle, CallbackInfo ci) {
        if (particle != null) {
            try {
                particle.tick();
            } catch (Throwable t) {
                // Защита от краша: если частица бросила исключение, тихо удаляем её
                particle.markDead();
            }
        }
        ci.cancel();
    }

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;)V", at = @At("HEAD"), cancellable = true)
    private void onAddEmitter(Entity entity, ParticleEffect parameters, CallbackInfo ci) {
        if (entity != null) {
            if (Optimization.shouldCancelParticle(parameters, entity.getX(), entity.getY(), entity.getZ())) {
                ci.cancel();
                return;
            }
        } else if (Optimization.shouldCancelParticle(parameters)) {
            ci.cancel();
            return;
        }
        if (parameters.getType() == ParticleTypes.TOTEM_OF_UNDYING) {
            if (LexoraGui.moduleStates.getOrDefault("Particles", false) &&
                    LexoraGui.moduleStates.getOrDefault("Disable Vanilla Totem", true)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V", at = @At("HEAD"), cancellable = true)
    private void onAddEmitterAge(Entity entity, ParticleEffect parameters, int maxAge, CallbackInfo ci) {
        if (entity != null) {
            if (Optimization.shouldCancelParticle(parameters, entity.getX(), entity.getY(), entity.getZ())) {
                ci.cancel();
            }
        } else if (Optimization.shouldCancelParticle(parameters)) {
            ci.cancel();
        }
    }
}
