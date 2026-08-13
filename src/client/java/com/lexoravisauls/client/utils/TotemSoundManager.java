package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.Resource;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TotemSoundManager {

    public static final Map<String, SoundEvent> CUSTOM_SOUNDS = new HashMap<>();
    private static final Map<String, Boolean> VALIDITY_CACHE = new HashMap<>();
    private static final Set<String> WARNED_BROKEN = new HashSet<>();

    private static final String[] SOUND_IDS = new String[]{
            "1", "11", "222", "333", "444", "52_s2bby3v", "555", "ahah", "ahoh", "aibla",
            "am-am-am", "anime-ahh_1", "apple-pay", "araara", "bax", "bliaiaiat", "bone-crack",
            "burp", "cat", "chicken-on-tree-scr", "eqed132", "grob", "hog-rider", "kat",
            "mugi", "nanax", "ne-nado-diada", "oi-oi-oe-oi-a-eye-e", "pikmi", "posiobam",
            "pozovi", "sebalos-chudishche_buztiif", "ser-da-ser_o6s3ouc", "spongebob-fail",
            "stoniland", "suda", "tuco-get-out", "use_totem", "zxc", "zxcghoul", "fluger"
    };

    private static String lastSelectedSound = null;

    public static void init() {
        for (String id : SOUND_IDS) {
            Identifier identifier = Identifier.of("lexoravisauls", id);
            SoundEvent event = SoundEvent.of(identifier);
            Registry.register(Registries.SOUND_EVENT, identifier, event);
            CUSTOM_SOUNDS.put(id, event);
        }
    }

    private static boolean isEnabled() {
        if (LexoraGui.moduleStates.containsKey("Totem Sound")) return LexoraGui.moduleStates.get("Totem Sound");
        return ClientData.moduleStates.getOrDefault("Totem Sound", false);
    }

    private static float getVolume() {
        if (LexoraGui.numSettings.containsKey("Totem Sound Volume")) return LexoraGui.numSettings.get("Totem Sound Volume");
        return ClientData.numSettings.getOrDefault("Totem Sound Volume", 1.0f);
    }

    private static float getPitch() {
        if (LexoraGui.numSettings.containsKey("Totem Sound Pitch")) return LexoraGui.numSettings.get("Totem Sound Pitch");
        return ClientData.numSettings.getOrDefault("Totem Sound Pitch", 1.0f);
    }

    private static String getMode() {
        if (LexoraGui.modeSettings.containsKey("Totem Sound Mode")) return LexoraGui.modeSettings.get("Totem Sound Mode");
        return ClientData.modeSettings.getOrDefault("Totem Sound Mode", "Звук 1");
    }

    public static void tick(MinecraftClient mc) {
        if (!isEnabled()) return;

        String currentSound = getMode();
        if (lastSelectedSound == null) {
            lastSelectedSound = currentSound;
            return;
        }

        if (!currentSound.equals(lastSelectedSound)) {
            lastSelectedSound = currentSound;
            SoundEvent customEvent = getReplacementEvent();

            if (customEvent != SoundEvents.ITEM_TOTEM_USE && mc.getSoundManager() != null) {
                mc.getSoundManager().play(PositionedSoundInstance.master(customEvent, getPitch(), getVolume()));
            }
        }
    }

    public static SoundEvent getReplacementEvent() {
        if (!isEnabled()) return SoundEvents.ITEM_TOTEM_USE;

        String id = resolveSoundId(getMode());
        if (id == null) return SoundEvents.ITEM_TOTEM_USE;

        SoundEvent event = CUSTOM_SOUNDS.get(id);
        if (event == null) return SoundEvents.ITEM_TOTEM_USE;

        if (!isPlayable(id)) {
            if (WARNED_BROKEN.add(id)) {
                System.err.println("[LexoraVisuals] Totem sound '" + id + "' has no valid audio file (sounds/" + id + ".ogg) — falling back to vanilla.");
            }
            return SoundEvents.ITEM_TOTEM_USE;
        }

        return event;
    }

    private static String resolveSoundId(String modeValue) {
        if (modeValue == null) return null;
        String trimmed = modeValue.trim();

        if (CUSTOM_SOUNDS.containsKey(trimmed)) return trimmed;

        int idx = parseSoundIndex(trimmed);
        if (idx >= 0 && idx < SOUND_IDS.length) return SOUND_IDS[idx];

        return null;
    }

    private static int parseSoundIndex(String s) {
        int end = -1, start = -1;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (Character.isDigit(s.charAt(i))) {
                if (end == -1) end = i;
                start = i;
            } else if (end != -1) {
                break;
            }
        }
        if (end == -1) return -1;
        try {
            int number = Integer.parseInt(s.substring(start, end + 1));
            return number > 0 ? number - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static float getReplacementVolume(float original) {
        return isEnabled() ? getVolume() : original;
    }

    public static float getReplacementPitch(float original) {
        return isEnabled() ? getPitch() : original;
    }

    private static boolean isPlayable(String id) {
        Boolean cached = VALIDITY_CACHE.get(id);
        if (cached != null) return cached;

        boolean valid;
        try {
            Identifier resource = Identifier.of("lexoravisauls", "sounds/" + id + ".ogg");
            Optional<Resource> found = MinecraftClient.getInstance().getResourceManager().getResource(resource);
            valid = found.isPresent();
        } catch (Exception e) {
            valid = false;
        }

        VALIDITY_CACHE.put(id, valid);
        return valid;
    }
}