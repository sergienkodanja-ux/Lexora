package com.lexoravisauls.client.modules.killeffect;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

/**
 * TEMPORARY diagnostic — NOT a real detection method. Logs every chat/game
 * message this client receives to the console/latest.log (never to in-game
 * chat, since sending a chat message would itself re-trigger these same
 * listeners and loop). GAME covers server broadcasts (death messages,
 * join/leave, advancements — confirmed by Fabric's own docs to be exactly
 * this category); CHAT covers player-sent messages.
 * <p>
 * Use this to capture the EXACT text a duel server sends when someone wins —
 * fight a duel, then search latest.log for "[KillEffectChatDebug]" and send
 * me the relevant line(s). Once we have the real text, I'll replace this with
 * a proper KillEffectChatWatcher that matches it and calls
 * KillEffectManager.trigger(...) — then this whole file gets deleted.
 */
final class KillEffectChatDebug {

    private KillEffectChatDebug() {
    }

    static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) { // skip actionbar spam (usually HP/cooldown HUDs), keep real messages
                log("GAME", message);
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                log("CHAT", message));
    }

    private static void log(String channel, Text message) {
        System.out.println("[KillEffectChatDebug][" + channel + "] " + message.getString());
    }
}
