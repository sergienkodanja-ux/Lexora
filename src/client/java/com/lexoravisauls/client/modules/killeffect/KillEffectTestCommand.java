package com.lexoravisauls.client.modules.killeffect;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Temporary test commands: /ket and /killeffect
 * Allows instant on-demand preview and debugging of all kill effects.
 *
 * Usage:
 *   /ket                       -> plays the currently selected mode from GUI
 *   /ket <mode>                -> plays a specific mode with tab-completion
 *   /killeffect [mode]         -> alias for /ket
 */
public final class KillEffectTestCommand {

    private KillEffectTestCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /ket
            dispatcher.register(ClientCommandManager.literal("ket")
                    .executes(ctx -> triggerTest(ctx, null))
                    .then(ClientCommandManager.argument("mode", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                for (KillEffectType type : KillEffectType.values()) {
                                    builder.suggest("\"" + type.getDisplayName() + "\"");
                                    builder.suggest(type.name().toLowerCase(Locale.ROOT));
                                }
                                builder.suggest("singularity");
                                builder.suggest("divine");
                                builder.suggest("blood");
                                builder.suggest("soul");
                                builder.suggest("void");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> triggerTest(ctx, StringArgumentType.getString(ctx, "mode")))
                    )
            );

            // /killeffect (alias)
            dispatcher.register(ClientCommandManager.literal("killeffect")
                    .executes(ctx -> triggerTest(ctx, null))
                    .then(ClientCommandManager.argument("mode", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                for (KillEffectType type : KillEffectType.values()) {
                                    builder.suggest("\"" + type.getDisplayName() + "\"");
                                }
                                builder.suggest("singularity");
                                builder.suggest("divine");
                                builder.suggest("blood");
                                builder.suggest("soul");
                                builder.suggest("void");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> triggerTest(ctx, StringArgumentType.getString(ctx, "mode")))
                    )
            );
        });
    }

    private static int triggerTest(CommandContext<FabricClientCommandSource> ctx, String requestedMode) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return 0;
        }

        KillEffectType type = resolveType(requestedMode);

        // Find target position: crosshair block, targeted entity, or 3.5m in front of player
        double targetX = player.getX();
        double targetY = player.getY();
        double targetZ = player.getZ();

        HitResult hit = player.raycast(15.0, 1.0f, false);
        if (client.targetedEntity != null) {
            targetX = client.targetedEntity.getX();
            targetY = client.targetedEntity.getY();
            targetZ = client.targetedEntity.getZ();
        } else if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bHit = (BlockHitResult) hit;
            Vec3d pos = bHit.getPos();
            targetX = pos.x;
            targetY = pos.y;
            targetZ = pos.z;
        } else {
            Vec3d look = player.getRotationVec(1.0f);
            targetX = player.getX() + look.x * 3.5;
            targetY = player.getY();
            targetZ = player.getZ() + look.z * 3.5;
        }

        KillEffectManager.startAt(type, player, targetX, targetY, targetZ, player.age);

        ctx.getSource().sendFeedback(Text.literal(
                "§d[Lexora] §f✦ Запущен тест эффекта: §b" + type.getDisplayName()
                + " §7[" + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", targetX, targetY, targetZ) + "]"
        ));

        return 1;
    }

    private static KillEffectType resolveType(String requested) {
        if (requested == null || requested.isBlank()) {
            String modeName = KillEffectSettings.mode("Kill Effect Mode", KillEffectType.COSMIC_SINGULARITY.getDisplayName());
            return KillEffectType.fromDisplayName(modeName);
        }

        String lower = requested.trim().toLowerCase(Locale.ROOT).replace("\"", "");

        if (lower.contains("singularity") || lower.contains("cosmic") || lower.contains("сингуляр")) {
            return KillEffectType.COSMIC_SINGULARITY;
        }
        if (lower.contains("divine") || lower.contains("wrath") || lower.contains("гнев") || lower.contains("бож")) {
            return KillEffectType.DIVINE_WRATH;
        }
        if (lower.contains("blood") || lower.contains("nova") || lower.contains("кров") || lower.contains("разрыв")) {
            return KillEffectType.BLOOD_NOVA;
        }
        if (lower.contains("soul") || lower.contains("ascension") || lower.contains("дух") || lower.contains("душ")) {
            return KillEffectType.SOUL_ASCENSION;
        }
        if (lower.contains("void") || lower.contains("collapse") || lower.contains("дыра")) {
            return KillEffectType.COSMIC_SINGULARITY;
        }

        return KillEffectType.fromDisplayName(requested.replace("\"", ""));
    }
}
