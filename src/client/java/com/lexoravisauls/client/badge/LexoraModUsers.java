package com.lexoravisauls.client.badge;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LexoraModUsers {
    private static final Set<UUID> SERVER_USERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SELF_USERS = ConcurrentHashMap.newKeySet();

    // ВАЖНО — НАЙДЕННАЯ ПРИЧИНА МИГАНИЯ КОСМЕТИКИ:
    // Раньше был ОДИН общий набор BACKEND_USERS/BACKEND_NAMES, который писали
    // ДВА независимых, ничего не знающих друг о друге источника:
    //   1) CosmeticsManager.fetchModUsers() — раз в ~10 сек, через mod_users.php,
    //      список игроков КОНКРЕТНОГО Minecraft-сервера (dexland и т.д.)
    //   2) LexoraExternalPresence.sync() — раз в 7 сек, через /irc, список
    //      участников ГЛОБАЛЬНОГО IRC-чата мода (никак не привязан к серверу)
    // Эти два цикла гонялись друг за другом и оба вызывали setBackendNames(),
    // полностью перезаписывая один и тот же Set. Когда /irc возвращал другой
    // (часто пустой или неполный) список участников чата, он стирал корректный
    // список игроков сервера, который на несколько секунд раньше установил
    // mod_users.php — из-за чего hasName() то true, то false для одного и
    // того же игрока каждые несколько секунд, что и вызывало мигание рендера
    // косметики (см. лог: BACKEND_NAMES=[] сразу после успешного обновления).
    //
    // Фикс: разделяем на два по-настоящему независимых набора. SERVER_* —
    // источник истины для рендера косметики/бейджа (hasName/has), пишет
    // ТОЛЬКО mod_users.php. IRC_* — отдельно, для списка участников чата
    // (LexoraIrcScreen), пишет ТОЛЬКО LexoraExternalPresence. Они больше не
    // пересекаются, поэтому один цикл не может стереть данные другого.
    private static final Set<UUID> SERVER_BACKEND_USERS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SERVER_BACKEND_NAMES = ConcurrentHashMap.newKeySet();

    private static final Set<UUID> IRC_USERS = ConcurrentHashMap.newKeySet();
    private static final Set<String> IRC_NAMES = ConcurrentHashMap.newKeySet();

    private static final Set<String> SELF_NAMES = ConcurrentHashMap.newKeySet();

    private LexoraModUsers() {
    }

    // --- ДЛЯ ОТОБРАЖЕНИЯ ОНЛАЙНА В GUI (список участников IRC-чата) ---
    // ВАЖНО: это теперь IRC_NAMES, не SERVER_BACKEND_NAMES — LexoraIrcScreen
    // показывает список чата, а не список игроков конкретного сервера.
    // Если экран должен был показывать именно игроков сервера — поменяй на
    // SERVER_BACKEND_NAMES, но исходно (судя по BACKEND_URL="/irc" в
    // LexoraExternalPresence) это задумывался как список чата.
    public static Set<String> getOnlineNames() {
        return IRC_NAMES;
    }

    public static boolean has(UUID uuid) {
        return uuid != null
                && (
                SERVER_USERS.contains(uuid)
                        || SERVER_BACKEND_USERS.contains(uuid)
                        || SELF_USERS.contains(uuid)
        );
    }

    public static boolean has(UUID uuid, String name) {
        return has(uuid) || hasName(name);
    }

    public static boolean hasName(String name) {
        String clean = normalizeName(name);

        return !clean.isEmpty()
                && (
                SERVER_BACKEND_NAMES.contains(clean)
                        || SELF_NAMES.contains(clean)
        );
    }

    public static void addSelf(UUID uuid) {
        if (uuid != null) {
            SELF_USERS.add(uuid);
        }
    }

    public static void addSelf(UUID uuid, String name) {
        addSelf(uuid);

        String clean = normalizeName(name);

        if (!clean.isEmpty()) {
            SELF_NAMES.add(clean);
        }
    }

    public static void setServerUsers(Collection<UUID> uuids) {
        SERVER_USERS.clear();

        if (uuids != null) {
            SERVER_USERS.addAll(uuids);
        }
    }

    // Используется CosmeticsManager.fetchModUsers() — список игроков
    // КОНКРЕТНОГО Minecraft-сервера. Это единственный писатель в
    // SERVER_BACKEND_USERS теперь (LexoraExternalPresence больше сюда не пишет
    // — см. setIrcUsers ниже).
    public static void setBackendUsers(Collection<UUID> uuids) {
        SERVER_BACKEND_USERS.clear();

        if (uuids != null) {
            SERVER_BACKEND_USERS.addAll(uuids);
        }
    }

    // Используется CosmeticsManager.fetchModUsers() — список игроков
    // КОНКРЕТНОГО Minecraft-сервера. Единственный писатель в
    // SERVER_BACKEND_NAMES (LexoraExternalPresence больше сюда не пишет).
    public static void setBackendNames(Collection<String> names) {
        SERVER_BACKEND_NAMES.clear();

        if (names == null) {
            return;
        }

        for (String name : names) {
            String clean = normalizeName(name);

            if (!clean.isEmpty()) {
                SERVER_BACKEND_NAMES.add(clean);
            }
        }
    }

    // НОВОЕ: отдельные методы для IRC-списка (список участников глобального
    // чата). Используются ТОЛЬКО из LexoraExternalPresence.sync() — раньше
    // этот класс по ошибке звал setBackendUsers()/setBackendNames() и тем
    // самым затирал список игроков сервера каждые 7 секунд.
    public static void setIrcUsers(Collection<UUID> uuids) {
        IRC_USERS.clear();

        if (uuids != null) {
            IRC_USERS.addAll(uuids);
        }
    }

    public static void setIrcNames(Collection<String> names) {
        IRC_NAMES.clear();

        if (names == null) {
            return;
        }

        for (String name : names) {
            String clean = normalizeName(name);

            if (!clean.isEmpty()) {
                IRC_NAMES.add(clean);
            }
        }
    }

    // Полный сброс — вызывать ТОЛЬКО при реальном разрыве соединения с сервером
    // (ClientPlayConnectionEvents.DISCONNECT), когда игрок физически покинул
    // сервер целиком. Не вызывать на JOIN — см. clearOnRejoin() ниже.
    public static void clear() {
        SERVER_USERS.clear();
        SERVER_BACKEND_USERS.clear();
        SELF_USERS.clear();
        SERVER_BACKEND_NAMES.clear();
        SELF_NAMES.clear();
        // IRC_USERS/IRC_NAMES намеренно НЕ сбрасываем — это не привязано
        // к конкретному Minecraft-серверу, глобальный чат остаётся тем же
        // независимо от того, на каком сервере ты сейчас находишься.
    }

    // Лёгкий сброс — вызывать на ClientPlayConnectionEvents.JOIN. Некоторые
    // серверы (например Dexland с ареными/дуэльными переходами) шлют повторный
    // JOIN каждые 10-15 секунд при переброске игрока между внутренними мирами,
    // хотя игрок с точки зрения игрока никуда не "уходил". Полный clear() в
    // такой момент сносил SERVER_BACKEND_NAMES (список модеров с HTTP-бэкенда)
    // и SELF_NAMES, из-за чего рендер косметики мигал: список успевал заново
    // наполниться только через 10-25 секунд, а следующий JOIN тут же сносил
    // его опять.
    //
    // Здесь мы сбрасываем только то, что приходит через нативный Fabric-канал
    // (SERVER_USERS, SELF_USERS через UUID) — эти данные действительно
    // привязаны к конкретному сетевому соединению и должны быть переустановлены
    // заново. SERVER_BACKEND_NAMES и SELF_NAMES НЕ трогаем: они приходят из
    // HTTP и не зависят от Minecraft-протокола, у них свой TTL/кулдаун на
    // обновление, реконнект внутри того же сервера их не обесценивает.
    public static void clearOnRejoin() {
        SERVER_USERS.clear();
        SELF_USERS.clear();
    }

    public static void setAll(List<UUID> users) {
        setBackendUsers(users);
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "");
    }
}