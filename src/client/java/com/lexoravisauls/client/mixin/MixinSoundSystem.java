package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.ParticleSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class MixinSoundSystem {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
    private void onPlaySound(SoundInstance sound, CallbackInfo ci) {
        // Проверяем, что звук не пустой и его ID - это звук использования тотема
        if (sound != null && sound.getId().getPath().equals("item.totem.use")) {

            // Проверяем, включены ли партиклы в твоем GUI меню
            if (LexoraGui.moduleStates.getOrDefault("Particles", false) &&
                    LexoraGui.moduleStates.getOrDefault("Part. Totem", true)) {

                // Берем координаты прямо из звука
                Vec3d pos = new Vec3d(sound.getX(), sound.getY(), sound.getZ());

                // Выполняем спавн партиклов в главном потоке (защита от крашей)
                MinecraftClient.getInstance().execute(() -> {
                    ParticleSystem.spawnCustomTotemEffect(pos);
                });
            }
        }
    }
}