package com.lexoravisauls.client.modules.killeffect;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.entity.player.PlayerEntity;

/**
 * TEMP: client-side-only test command — runs entirely locally, no server
 * involvement, bypasses every gate (module toggle, own-kill filter, dedup) so
 * you can preview effects instantly without an actual kill. Delete this file
 * (and its one registration line in KillEffectManager.init()) once done tuning.
 * <p>
 * /ket            -> plays whichever mode is currently selected in SETTINGS
 * /ket soul       -> forces Soul Ascension
 * /ket beam       -> forces Divine Beam
 * /ket void       -> forces Void Collapse
 */
final class KillEffectTestCommand {

    private KillEffectTestCommand() {
    }

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("ket")
                        .executes(context -> fire(context, null))
                        .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                .executes(context -> fire(context, StringArgumentType.getString(context, "mode"))))));
    }

    private static int fire(CommandContext<FabricClientCommandSource> context, String modeArg) {
        PlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }

        KillEffectType type = resolveType(modeArg);
        if (type == null) {
            KillEffectManager.debug("unknown mode '" + modeArg + "' — use: soul / beam / void");
            return 0;
        }

        KillEffectManager.debugTrigger(player, type);
        return 1;
    }

    private static KillEffectType resolveType(String modeArg) {
        if (modeArg == null) {
            String modeName = KillEffectSettings.mode("Kill Effect Mode", KillEffectType.SOUL_ASCENSION.getDisplayName());
            return KillEffectType.fromDisplayName(modeName);
        }
        return switch (modeArg.toLowerCase()) {
            case "soul", "ascension", "ghost" -> KillEffectType.SOUL_ASCENSION;
            case "void", "collapse" -> KillEffectType.VOID_COLLAPSE;
            default -> null;
        };
    }
}
