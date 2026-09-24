package com.lexoravisauls.client.utils;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Real Brigadier "/gps" command, replacing the old ".gps ..." chat-message
 * interceptor (GPS#handleLocalCommand, now a deprecated no-op) — a raw chat
 * message has no tab-completion mechanism, a slash command does. Same
 * suggestion-provider pattern KillEffectTestCommand already uses for /ket.
 * <p>
 * /gps set <name> [x] [y] [z]  -> add/replace a waypoint. Typing the command
 *                                  suggests known event names for <name>, and
 *                                  your current rounded coordinates for
 *                                  x/y/z as you tab through them — omitted
 *                                  x/y/z default to your current position,
 *                                  same as the old ".gps set "name"" behaviour.
 * /gps clear [name]            -> clear one waypoint (suggests existing
 *                                  waypoint names) or every waypoint if omitted.
 * /gps list                    -> print all active waypoints (unchanged from
 *                                  the old GPS#printList()).
 * <p>
 * Call GpsCommand.register() once from wherever your ClientModInitializer
 * already wires up other client commands / GPS itself.
 */
public final class GpsCommand {

    /** Suggested names for "/gps set <name>" — same events getAccentColor() recognises. */
    private static final String[] KNOWN_EVENT_NAMES = {
            "Маяк убийца", "Метеоритный дождь", "Вулкан", "Адская резня", "Сундук смерти"
    };

    private GpsCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("gps")
                        .then(ClientCommandManager.literal("list")
                                .executes(GpsCommand::list))
                        .then(ClientCommandManager.literal("clear")
                                .executes(GpsCommand::clearAll)
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(GpsCommand::suggestExistingWaypointNames)
                                        .executes(GpsCommand::clearOne)))
                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                        .suggests(GpsCommand::suggestEventNames)
                                        .executes(ctx -> set(ctx, false, false, false))
                                        .then(ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
                                                .suggests(GpsCommand::suggestCurrentX)
                                                .executes(ctx -> set(ctx, true, false, false))
                                                .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg())
                                                        .suggests(GpsCommand::suggestCurrentY)
                                                        .executes(ctx -> set(ctx, true, true, false))
                                                        .then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
                                                                .suggests(GpsCommand::suggestCurrentZ)
                                                                .executes(ctx -> set(ctx, true, true, true)))))))));
    }

    private static int set(CommandContext<FabricClientCommandSource> context, boolean hasX, boolean hasY, boolean hasZ) {
        PlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }

        String name = StringArgumentType.getString(context, "name").trim();
        if (name.isEmpty()) {
            player.sendMessage(Text.literal("§cУкажи название метки"), false);
            return 0;
        }

        double x = hasX ? DoubleArgumentType.getDouble(context, "x") : player.getX();
        double y = hasY ? DoubleArgumentType.getDouble(context, "y") : Math.floor(player.getY());
        double z = hasZ ? DoubleArgumentType.getDouble(context, "z") : player.getZ();

        GPS.addWaypoint(name, x, y, z);
        player.sendMessage(Text.literal("§aGPS: §e" + name + " §7[" + (int) x + " " + (int) y + " " + (int) z + "]"), true);
        return 1;
    }

    private static int clearAll(CommandContext<FabricClientCommandSource> context) {
        GPS.clearAll();
        PlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            player.sendMessage(Text.literal("§cВсе GPS метки очищены"), true);
        }
        return 1;
    }

    private static int clearOne(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "name").trim();
        GPS.removeWaypoint(name);
        PlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            player.sendMessage(Text.literal("§cМетка §e" + name + " §cудалена"), true);
        }
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> context) {
        GPS.printList();
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestEventNames(CommandContext<FabricClientCommandSource> context,
                                                                    SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String eventName : KNOWN_EVENT_NAMES) {
            if (eventName.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                // StringArgumentType.string() parses either one bare word or a
                // "quoted string" — every known event name has spaces, so the
                // suggestion has to include the quotes for tab-complete to
                // insert something that argument type will actually accept.
                builder.suggest(eventName.contains(" ") ? "\"" + eventName + "\"" : eventName);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestExistingWaypointNames(CommandContext<FabricClientCommandSource> context,
                                                                               SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (GPS.GpsWaypoint waypoint : GPS.getWaypoints()) {
            // greedyString captures to end-of-input unquoted, so no quotes here.
            if (waypoint.name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(waypoint.name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCurrentX(CommandContext<FabricClientCommandSource> context,
                                                                  SuggestionsBuilder builder) {
        PlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            builder.suggest(String.format(Locale.US, "%.0f", player.getX()));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCurrentY(CommandContext<FabricClientCommandSource> context,
                                                                  SuggestionsBuilder builder) {
        PlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            builder.suggest(String.format(Locale.US, "%.0f", Math.floor(player.getY())));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCurrentZ(CommandContext<FabricClientCommandSource> context,
                                                                  SuggestionsBuilder builder) {
        PlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            builder.suggest(String.format(Locale.US, "%.0f", player.getZ()));
        }
        return builder.buildFuture();
    }
}