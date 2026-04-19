package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.HitColorHandler;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(Entity entity, double x, double y, double z, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("Hit Color", false) && entity instanceof LivingEntity living) {
            if (living.hurtTime > 0 || living.deathTime > 0) {
                HitColorHandler.isHurt = true;
                if (living.hurtTime > 0) {
                    // Анимация удара
                    HitColorHandler.hurtPercent = (float) living.hurtTime / 10.0f;
                } else {
                    // Анимация смерти моба
                    HitColorHandler.hurtPercent = 1.0f - ((float) living.deathTime / 20.0f);
                }
                return;
            }
        }
        HitColorHandler.isHurt = false;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(Entity entity, double x, double y, double z, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        HitColorHandler.isHurt = false;
    }
}