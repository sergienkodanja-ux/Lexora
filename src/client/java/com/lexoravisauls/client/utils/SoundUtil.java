package com.lexoravisauls.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class SoundUtil {
    public static void playCustomSound(String name, float volume) {
        // Убрали проверку на игрока. Теперь звуки GUI будут работать всегда!
        Identifier soundId = Identifier.of("lexoravisauls", name);
        MinecraftClient.getInstance().getSoundManager().play(
                PositionedSoundInstance.master(SoundEvent.of(soundId), 1.0f, volume)
        );
    }
}