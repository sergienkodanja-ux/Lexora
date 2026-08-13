package com.lexoravisauls.client.modules.killeffect;

/**
 * Set to true only for the exact duration of the ghost's render() call —
 * checked by RenderLayerGhostMixin to redirect the player-skin layer to a
 * translucent variant just for that one call, without affecting any other
 * entity rendered the same frame.
 */
public final class KillEffectGhostState {

    public static volatile boolean RENDERING = false;

    private KillEffectGhostState() {
    }
}
