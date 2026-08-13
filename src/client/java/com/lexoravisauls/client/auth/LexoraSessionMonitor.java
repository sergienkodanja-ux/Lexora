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

                // Если поменял ник в свитчере — сообщаем сайту
                if (!currentName.equals(lastKnownUsername)) {
                    if (!lastKnownUsername.isEmpty()) {
                        if (LexoraMainMenu.isAuthenticatedSession) {
                            LexoraAccount.syncCurrentIgn(currentName);
                            // Если поменял ник прямо на сервере — сбрасываем кэш косметики,
                            // чтобы рендереры перезапросили данные заново под новым ником
                            // (косметика теперь привязана к нику через api_cosmetics.php,
                            // а не тянется массово с сервера).
                            if (client.getCurrentServerEntry() != null) {
                                com.lexoravisauls.client.cosmetics.CosmeticsManager.clearCache();
                            }
                        }
                    }
                    lastKnownUsername = currentName;
                }

                // Регулярный heartbeat — сообщает сайту, что ты онлайн и на каком
                // сервере. НЕ загружает косметику других игроков (это делают
                // фиче-рендереры сами через ensureFetched, когда видят игрока).
                if (LexoraMainMenu.isAuthenticatedSession && client.getCurrentServerEntry() != null) {
                    com.lexoravisauls.client.cosmetics.CosmeticsManager.onClientTick(currentName);
                }
            }
        });

        // Срабатывает когда мир загрузился
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getSession() != null && LexoraMainMenu.isAuthenticatedSession) {
                String currentName = client.getSession().getUsername();
                // Отправляем heartbeat сразу — это обновит current_server в БД.
                // Список модеров сервера НЕ запрашиваем в этот же момент: если
                // запустить его параллельно с первым heartbeat, есть риск, что
                // mod_users.php прочитает current_server раньше, чем heartbeat
                // успеет его записать (это два разных потока/запроса), и вернёт
                // неполный список. CosmeticsManager сам подтянет список модеров
                // на первом же regular tick, с небольшой намеренной задержкой —
                // см. onJoinServer().
                com.lexoravisauls.client.cosmetics.CosmeticsManager.onJoinServer(currentName);
            }
        });
    }
}