package com.lexoravisauls.client.auth;

import com.lexoravisauls.client.badge.LexoraAccount;
import com.lexoravisauls.client.gui.main_menu.LexoraMainMenu;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class LexoraSessionMonitor {
    private static String lastKnownUsername = "";

    // КРИТИЧНО: LexoravisaulsClient.java вызывает LexoraSessionMonitor.register()
    // ИЗНУТРИ своего END_CLIENT_TICK-обработчика (строка "LexoraSessionMonitor.register();"
    // в основном тик-луп мода) — то есть register() дёргается на КАЖДОМ тике игры,
    // десятки раз в секунду. Fabric API не защищает от повторной регистрации
    // одного и того же listener: без этого флага каждый вызов register() добавлял
    // ЕЩЁ ОДИН END_CLIENT_TICK и ЕЩЁ ОДИН JOIN обработчик поверх уже существующих.
    // Через несколько секунд накапливались сотни дублирующих обработчиков, каждый
    // из которых независимо тикал onClientTick()/onJoinServer() — это и есть
    // причина пачек "Too many requests" (несколько обработчиков одновременно
    // проходили проверку кулдауна до того, как кто-то из них успевал обновить
    // общий таймер) и общего мигания косметики/бейджа.
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getSession() != null) {
                String currentName = client.getSession().getUsername();

                // Если поменял ник в свитчере — синхронизируем новую косметику
                if (!currentName.equals(lastKnownUsername)) {
                    if (!lastKnownUsername.isEmpty()) {
                        com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.onJoinServer(client);
                    }
                    lastKnownUsername = currentName;
                }

                // Синхронизация и heartbeat новой косметики
                com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.onClientTick(client);
            }
        });

        // Срабатывает когда мир загрузился
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getSession() != null) {
                com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.onJoinServer(client);
            }
        });

        // Срабатывает при выходе с сервера или из игры
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.lexoravisauls.client.cosmetic.sync.PlayerCosmeticsSync.onDisconnect();
        });
    }
}