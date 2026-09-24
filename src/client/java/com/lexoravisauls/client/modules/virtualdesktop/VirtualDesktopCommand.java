package com.lexoravisauls.client.modules.virtualdesktop;

import com.lexoravisauls.client.core.ClientData;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class VirtualDesktopCommand {

    private VirtualDesktopCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var vdesk = ClientCommandManager.literal("vdesk")
                    .executes(ctx -> {
                        toggleModule();
                        return 1;
                    })
                    .then(ClientCommandManager.literal("toggle")
                            .executes(ctx -> {
                                toggleModule();
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("place")
                            .executes(ctx -> {
                                VirtualDesktopManager.getInstance().placeInFrontOfPlayer();
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("os")
                            .executes(ctx -> {
                                VirtualDesktopManager.getInstance().setLiveMirrorMode(false);
                                feedback("§8[§fLexora§8] §fРежим: §bCyber OS");
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("mirror")
                            .executes(ctx -> {
                                VirtualDesktopManager.getInstance().setLiveMirrorMode(true);
                                feedback("§8[§fLexora§8] §fРежим: §bЗеркало Windows");
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("google")
                            .executes(ctx -> {
                                VirtualDesktopManager.openUrl("");
                                feedback("§d[Google] §fОткрываю Google...");
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("search")
                            .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String query = StringArgumentType.getString(ctx, "query");
                                        VirtualDesktopManager.openUrl(query);
                                        feedback("§d[Google] §fИщу в Google: §b" + query);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("browser")
                            .executes(ctx -> {
                                VirtualDesktopManager.openUrl("");
                                feedback("§d[Google] §fОткрываю браузер Google...");
                                return 1;
                            })
                            .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String url = StringArgumentType.getString(ctx, "url");
                                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                            url = "https://" + url;
                                        }
                                        VirtualDesktopManager.openUrl(url);
                                        feedback("§d[Lexora] §fОткрываю ссылку: §b" + url);
                                        return 1;
                                    })));

            dispatcher.register(vdesk);

            // Alias /screen
            dispatcher.register(ClientCommandManager.literal("screen")
                    .redirect(dispatcher.getRoot().getChild("vdesk")));
        });
    }

    private static void toggleModule() {
        boolean curr = ClientData.moduleStates.getOrDefault("Virtual Desktop", false);
        boolean newState = !curr;
        ClientData.moduleStates.put("Virtual Desktop", newState);
        feedback("§d[Lexora] §fВиртуальный рабочий стол: " + (newState ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН"));
    }

    private static void feedback(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), false);
        }
    }
}
