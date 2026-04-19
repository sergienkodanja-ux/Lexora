package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.utils.ParticleSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinTotemNetwork {

    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (packet.getStatus() == 35) { // 35 - это серверный статус поппа тотема
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) {
                Entity entity = packet.getEntity(mc.world);
                if (entity != null) {
                    // Вызываем наши партиклы
                    ParticleSystem.spawnCustomTotemEffect(entity.getPos());
                }
            }
        }
    }
}