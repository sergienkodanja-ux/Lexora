package com.lexoravisauls.client.gui.main_menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.InputStream;

public final class MainMenuSoundHelper {

    public static final Identifier HELLO_SOUND_ID = Identifier.of("lexoravisauls", "hello");
    public static final SoundEvent HELLO_SOUND_EVENT = SoundEvent.of(HELLO_SOUND_ID);
    private static boolean soundPlayedThisSession = false;

    private MainMenuSoundHelper() {}

    public static void playHelloSound(boolean force) {
        if (soundPlayedThisSession && !force) return;
        soundPlayedThisSession = true;

        // 1. Воспроизведение через звуковой движок Minecraft
        boolean playedViaMc = false;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(PositionedSoundInstance.master(HELLO_SOUND_EVENT, 1.0f));
                playedViaMc = true;
            }
        } catch (Throwable ignored) {}

        if (playedViaMc) return;

        // 2. Резервный моментальный проигрыватель через Java AudioSystem (WAV)
        new Thread(() -> {
            try {
                InputStream is = MainMenuSoundHelper.class.getResourceAsStream("/assets/lexoravisauls/sounds/hellovs.wav");
                if (is == null) {
                    is = MainMenuSoundHelper.class.getClassLoader().getResourceAsStream("assets/lexoravisauls/sounds/hellovs.wav");
                }
                if (is == null) return;
                try (BufferedInputStream bis = new BufferedInputStream(is);
                     AudioInputStream ais = AudioSystem.getAudioInputStream(bis)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(ais);
                    clip.start();
                }
            } catch (Throwable ignored) {}
        }, "Lexora-HelloSound-Thread").start();
    }
}
