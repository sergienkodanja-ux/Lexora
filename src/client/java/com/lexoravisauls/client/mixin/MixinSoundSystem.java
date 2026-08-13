package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.ParticleSystem;
import com.lexoravisauls.client.utils.TotemSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public abstract class MixinSoundSystem {

    @Shadow public abstract void play(SoundInstance sound);

    private boolean isPlayingCustomSound = false;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance sound, CallbackInfo ci) {
        if (sound == null || isPlayingCustomSound) return;

        Identifier soundId = null;
        try {
            soundId = sound.getId();
        } catch (Exception e) {
            ci.cancel();
            return;
        }

        if (soundId == null) return;

        if (soundId.getNamespace().equals("minecraft") && soundId.getPath().equals("item.totem.use")) {

            if (LexoraGui.moduleStates.getOrDefault("Particles", false) &&
                    LexoraGui.moduleStates.getOrDefault("Part. Totem", true)) {

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world != null) {
                    net.minecraft.entity.player.PlayerEntity closest = mc.world.getClosestPlayer(sound.getX(), sound.getY(), sound.getZ(), 3.0, false);
                    if (closest != null) {
                        ParticleSystem.spawnCustomTotemEffect(closest);
                    } else if (mc.player != null) {
                        ParticleSystem.spawnCustomTotemEffect(mc.player);
                    }
                }
            }

            SoundEvent customEvent = TotemSoundManager.getReplacementEvent();
            if (customEvent != SoundEvents.ITEM_TOTEM_USE) {
                ci.cancel();

                try {
                    PositionedSoundInstance customSound = new PositionedSoundInstance(
                            customEvent,
                            sound.getCategory(),
                            TotemSoundManager.getReplacementVolume(sound.getVolume()),
                            TotemSoundManager.getReplacementPitch(sound.getPitch()),
                            Random.create(),
                            sound.getX(),
                            sound.getY(),
                            sound.getZ()
                    );

                    // Играем ЧЕРЕЗ SoundManager, а не this.play() напрямую.
                    // this.play() = SoundSystem.play() — низкоуровневый метод, который
                    // ожидает, что проверка "звук существует / не пустой" уже сделана
                    // выше по стеку. SoundManager.play() эту проверку делает сам и на
                    // пустой/битый SoundEvent пишет warning вместо NPE — именно поэтому
                    // превью в GUI не крашилось, а тут крашилось.
                    isPlayingCustomSound = true;
                    MinecraftClient.getInstance().getSoundManager().play(customSound);
                } catch (Exception e) {
                    System.err.println("[LexoraVisuals] Totem sound replace failed: " + e);
                } finally {
                    isPlayingCustomSound = false;
                }
            }
        }
    }
}