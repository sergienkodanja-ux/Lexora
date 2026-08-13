package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.cosmetics.WingsFeatureRenderer;
import com.lexoravisauls.client.cosmetics.HornsFeatureRenderer; // <-- Не забудь этот импорт!
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {

    public PlayerRendererMixin(EntityRendererFactory.Context ctx, PlayerEntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        // Теперь addFeature вызывается напрямую через this, так как класс унаследован
        this.addFeature(new WingsFeatureRenderer((PlayerEntityRenderer) (Object) this));
        this.addFeature(new HornsFeatureRenderer((PlayerEntityRenderer) (Object) this)); // <-- ДОБАВИЛИ РОГА
    }
}