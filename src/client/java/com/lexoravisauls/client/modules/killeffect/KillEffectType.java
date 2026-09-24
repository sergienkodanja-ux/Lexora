package com.lexoravisauls.client.modules.killeffect;

/**
 * The available kill-effect styles. {@link #getDisplayName()} doubles as the
 * exact option string used in the "Kill Effect Mode" dropdown in SETTINGS —
 * keep the two in sync if you rename anything.
 */
public enum KillEffectType {

    SOUL_ASCENSION("Soul Ascension", new SoulAscensionEffect()),
    COSMIC_SINGULARITY("Cosmic Singularity", new CosmicSingularityEffect()),
    DIVINE_WRATH("Divine Wrath", new DivineWrathEffect()),
    BLOOD_NOVA("Blood Nova", new BloodNovaEffect());

    private final String displayName;
    private final KillEffect<?> effect;

    KillEffectType(String displayName, KillEffect<?> effect) {
        this.displayName = displayName;
        this.effect = effect;
    }

    public String getDisplayName() {
        return displayName;
    }

    public KillEffect<?> getEffect() {
        return effect;
    }

    public static KillEffectType fromDisplayName(String name) {
        for (KillEffectType type : values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return SOUL_ASCENSION;
    }
}
