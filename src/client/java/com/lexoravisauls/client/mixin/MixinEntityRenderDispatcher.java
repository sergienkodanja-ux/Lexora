package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.HitColorHandler;
import com.lexoravisauls.client.modules.Optimization;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(Entity entity, double x, double y, double z, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("Hit Color", false) && entity instanceof LivingEntity living) {
            if (living.hurtTime > 0 || living.deathTime > 0) {
                HitColorHandler.isHurt = true;
                if (living.hurtTime > 0) {
                    HitColorHandler.hurtPercent = (float) living.hurtTime / 10.0f;
                } else {
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

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void onRenderShadow(MatrixStack matrices, VertexConsumerProvider vertexConsumers, EntityRenderState state, float opacity, float tickDelta, WorldView world, float radius, CallbackInfo ci) {
        if (Optimization.isNoShadows()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void onShouldRender(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == mc.player) return;

        if (Optimization.isNoArmorStands() && entity instanceof ArmorStandEntity) {
            cir.setReturnValue(false);
            return;
        }

        if (Optimization.isFastItems() && entity instanceof ItemEntity) {
            if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
                double distSq = mc.gameRenderer.getCamera().getPos().squaredDistanceTo(entity.getPos());
                float itemDist = Optimization.getItemsCullDistance();
                if (distSq > (itemDist * itemDist)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        if (Optimization.isEntityCullingEnabled()) {
            if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
                Vec3d camPos = mc.gameRenderer.getCamera().getPos();
                double distSq = camPos.squaredDistanceTo(entity.getPos());
                if (distSq > Optimization.getEntityCullDistanceSq()) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
