package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.ParticleSystem;
import com.lexoravisauls.client.utils.TotemSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public abstract class MixinSoundSystem {

    private boolean isPlayingCustomSound = false;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance sound, CallbackInfo ci) {
        if (sound == null || isPlayingCustomSound) return;

        Identifier soundId;
        try {
            soundId = sound.getId();
        } catch (Exception e) {
            return;
        }

        if (soundId == null) return;

        if (soundId.getNamespace().equals("minecraft") && soundId.getPath().equals("item.totem.use")) {

            // Безопасный спавн партиклов
            try {
                if (LexoraGui.moduleStates.getOrDefault("Particles", false) &&
                        LexoraGui.moduleStates.getOrDefault("Part. Totem", true)) {

                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null && mc.world != null) {
                        net.minecraft.entity.player.PlayerEntity closest = mc.world.getClosestPlayer(sound.getX(), sound.getY(), sound.getZ(), 3.0, false);
                        if (closest != null) {
                            ParticleSystem.spawnCustomTotemEffect(closest);
                        } else if (mc.player != null) {
                            ParticleSystem.spawnCustomTotemEffect(mc.player);
                        }
                    }
                }
            } catch (Exception ignored) {}

            SoundEvent customEvent = TotemSoundManager.getReplacementEvent();
            if (customEvent != null && customEvent != SoundEvents.ITEM_TOTEM_USE) {
                MinecraftClient mc = MinecraftClient.getInstance();

                if (mc != null && mc.getSoundManager() != null) {
                    // Отменяем стандартный звук тотема
                    ci.cancel();

                    try {
                        // Безопасное получение координат и категории без обращения к sound.getVolume()/getPitch()
                        double x = 0, y = 0, z = 0;
                        SoundCategory category = SoundCategory.PLAYERS;

                        try {
                            x = sound.getX();
                            y = sound.getY();
                            z = sound.getZ();
                            if (sound.getCategory() != null) {
                                category = sound.getCategory();
                            }
                        } catch (Exception ignored) {}

                        // Создаем кастомный звук без рискованных вызовов getVolume() у ванильного sound
                        PositionedSoundInstance customSound = new PositionedSoundInstance(
                                customEvent.id(),
                                category,
                                TotemSoundManager.getReplacementVolume(1.0f),
                                TotemSoundManager.getReplacementPitch(1.0f),
                                Random.create(),
                                false,
                                0,
                                SoundInstance.AttenuationType.LINEAR,
                                x, y, z,
                                false
                        );

                        isPlayingCustomSound = true;
                        mc.getSoundManager().play(customSound);
                    } catch (Exception e) {
                        System.err.println("[LexoraVisuals] Totem sound replace failed: " + e.getMessage());
                    } finally {
                        isPlayingCustomSound = false;
                    }
                }
            }
        }
    }
}