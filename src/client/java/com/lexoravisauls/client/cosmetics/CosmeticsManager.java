package com.lexoravisauls.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CosmeticsManager {

    // ─── ОБЩИЙ ПУЛ ПОТОКОВ ДЛЯ СЕТЕВЫХ ЗАПРОСОВ ───
    // Раньше каждый вызов sendHeartbeat/fetchModUsers/fetchCosmetics создавал
    // СВОЙ новый Thread. При заходе на людный сервер, когда в кадре сразу
    // появляется 20-30 игроков с модом, это давало всплеск из 20-30
    // одновременных потоков разом (плюс heartbeat, плюс список модеров) —
    // создание такого количества потоков одновременно ощутимо нагружает
    // JVM/ОС и может накладываться по времени ровно на момент прогрузки
    // чанков, из-за чего территория грузилась 1-2 минуты вместо обычного.
    // Фиксированный пул из 4 потоков с очередью обрабатывает всплеск
    // постепенно, не создавая лишних потоков разом.
    private static final ExecutorService NETWORK_POOL = new ThreadPoolExecutor(
            4, 4,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Lexora-Network-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    // Кэш косметики ПО НИКУ (не по серверу — сервер больше не отдаёт списки).
    private static final Map<String, Map<String, String>> equippedCosmetics = new ConcurrentHashMap<>();
    private static final Map<String, String> EMPTY_COSMETICS = new HashMap<>();

    // Когда последний раз запрашивали косметику для конкретного ника —
    // чтобы не спамить api_cosmetics.php на каждый кадр рендера.
    private static final Map<String, Long> lastFetchTime = new ConcurrentHashMap<>();
    private static final long FETCH_COOLDOWN_MS = 30_000; // 30 секунд на ник

    // Ники, которые прямо сейчас в процессе запроса — чтобы не запускать
    // несколько параллельных потоков на один и тот же ник одновременно.
    private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    // ─── ТАЙМЕР ДЛЯ HEARTBEAT ───
    private static long lastHeartbeatTime = 0;
    private static final long HEARTBEAT_COOLDOWN_MS = 5000; // 5 секунд

    // ─── ТАЙМЕР ДЛЯ СПИСКА "КТО НА СЕРВЕРЕ С МОДОМ" ───
    // Отдельно от heartbeat: бэкенд лимитирует mod_users.php раз в 10 сек
    // на токен (см. mod_users.php). Держим минимально возможный запас (1 сек),
    // чтобы список модеров не протухал дольше, чем строго необходимо —
    // это напрямую влияет на то, как долго держится рендер чужой косметики
    // между обновлениями BACKEND_NAMES.
    private static long lastModListTime = 0;
    private static final long MOD_LIST_COOLDOWN_MS = 11_000;

    // ─── Парсер IP (Берёт только ядро, например "funtime") ───
    public static String getCleanServerName(MinecraftClient client) {
        if (client.isInSingleplayer() || client.getCurrentServerEntry() == null) return "singleplayer";
        String ip = client.getCurrentServerEntry().address.toLowerCase();
        int portIndex = ip.indexOf(':');
        if (portIndex != -1) ip = ip.substring(0, portIndex);
        if (ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") || ip.equals("localhost")) return ip;
        String[] parts = ip.split("\\.");
        if (parts.length >= 2) return parts[parts.length - 2];
        return ip;
    }

    // ─── МГНОВЕННЫЙ РЕНДЕР (0 интернета в игре) ───
    // Если ника нет в кэше — рендерер должен сам вызвать ensureFetched(),
    // это НЕ делается автоматически внутри getEquipped, чтобы не плодить
    // сетевые запросы прямо из рендер-луп.
    public static String getEquipped(String username, String type) {
        if (username == null || username.isEmpty()) return "none";
        return equippedCosmetics.getOrDefault(username, EMPTY_COSMETICS).getOrDefault(type, "none");
    }

    // ─── Гарантирует, что для этого ника есть свежие данные (или запрос уже летит) ───
    // Вызывать из фиче-рендереров, когда видят игрока в мире. Дешёвая операция:
    // если кэш свежий или запрос уже в процессе — сразу выходит, ничего не делает.
    public static void ensureFetched(String username) {
        if (username == null || username.isEmpty()) return;

        Long last = lastFetchTime.get(username);
        long now = System.currentTimeMillis();
        if (last != null && (now - last) < FETCH_COOLDOWN_MS) return;
        if (!inFlight.add(username)) return; // уже кто-то запрашивает этот ник

        fetchCosmetics(username);
    }

    // ─── ВЫЗЫВАТЬ ПРИ ЗАХОДЕ НА СЕРВЕР ───
    // Отправляет heartbeat немедленно (обновит current_server в БД). Список
    // модеров запрашивается не мгновенно — иначе на серверах, где JOIN
    // срабатывает часто (например Dexland с внутренними ареными переходами
    // каждые 10-15 сек), можно упереться в rate-limit бэкенда (mod_users.php,
    // 10 сек на токен) и получить "Too many requests" на каждой попытке.
    public static void onJoinServer(String currentName) {
        if (currentName == null || currentName.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        lastHeartbeatTime = currentTime;
        sendHeartbeat(currentName);

        // Откатываем lastModListTime только если это реально ПРИБЛИЗИТ
        // следующий запрос списка — то есть только если с прошлого запроса
        // прошло больше времени, чем нужно для отката. Без этой проверки
        // частые повторные JOIN (реконнект каждые 10-15 сек) откатывали бы
        // таймер друг за другом быстрее, чем разрешает rate-limit бэкенда,
        // и fetchModUsers() бился бы в "Too many requests" на каждой попытке.
        long candidateTime = currentTime - MOD_LIST_COOLDOWN_MS + HEARTBEAT_COOLDOWN_MS;
        if (candidateTime > lastModListTime) {
            lastModListTime = candidateTime;
        }
    }

    // ─── ФОНОВЫЙ HEARTBEAT РАЗ В 5 СЕКУНД ───
    // Вызывай этот метод в ClientTickEvent. Это НЕ загружает косметику других
    // игроков — только сообщает сайту, что ты онлайн и на каком сервере.
    public static void onClientTick(String currentName) {
        if (currentName == null || currentName.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastHeartbeatTime >= HEARTBEAT_COOLDOWN_MS) {
            lastHeartbeatTime = currentTime;
            sendHeartbeat(currentName);
        }

        if (currentTime - lastModListTime >= MOD_LIST_COOLDOWN_MS) {
            lastModListTime = currentTime;
            fetchModUsers();
        }
    }

    // ─── HEARTBEAT (Асинхронно, без обработки списка игроков) ───
    // server_sync.php теперь ТОЛЬКО обновляет твою запись в БД (current_ign,
    // current_server, last_active). Раньше этот же вызов возвращал ники+косметику
    // ВСЕХ игроков сервера — это была утечка данных (любой с валидным token мог
    // получить живой список ников сервера, по сути радар для читеров). Теперь
    // сервер физически не может ответить списком, потому что server_sync.php
    // такой функционал больше не содержит.
    private static void sendHeartbeat(String currentName) {
        String token = com.lexoravisauls.client.auth.AuthManager.sessionToken;
        if (token == null || token.isEmpty()) return;

        NETWORK_POOL.submit(() -> {
            try {
                String cleanServer = getCleanServerName(MinecraftClient.getInstance());

                URL url = new URL("https://lexoravisuals.fun/server_sync.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String data = "token=" + URLEncoder.encode(token, "UTF-8")
                        + "&ign=" + URLEncoder.encode(currentName, "UTF-8")
                        + "&server=" + URLEncoder.encode(cleanServer, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data.getBytes(StandardCharsets.UTF_8));
                }

                // Нам не нужен ответ — это просто heartbeat. Читаем и закрываем поток,
                // чтобы не оставлять соединение висеть.
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                // Игнорируем ошибки интернета в фоне
            }
        });
    }

    // ─── СПИСОК "КТО НА СЕРВЕРЕ С МОДОМ" (Асинхронно) ───
    // Это ЕДИНСТВЕННЫЙ источник данных для LexoraModUsers.BACKEND_NAMES.
    // Раньше этот список приходил из ответа server_sync.php (players), но
    // вместе с ним утекала и косметика всех игроков сервера — это и была
    // основная дыра. mod_users.php отдаёт ТОЛЬКО ники, без косметики, и
    // сам ограничен rate-limit на бэкенде (раз в 10 сек на токен).
    private static void fetchModUsers() {
        String token = com.lexoravisauls.client.auth.AuthManager.sessionToken;
        if (token == null || token.isEmpty()) return;

        NETWORK_POOL.submit(() -> {
            try {
                String cleanServer = getCleanServerName(MinecraftClient.getInstance());

                URL url = new URL("https://lexoravisuals.fun/mod_users.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String data = "token=" + URLEncoder.encode(token, "UTF-8")
                        + "&server=" + URLEncoder.encode(cleanServer, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();

                    if (json.has("status") && json.get("status").getAsString().equals("success") && json.has("names")) {
                        JsonArray namesArr = json.getAsJsonArray("names");
                        List<String> names = new ArrayList<>();
                        for (JsonElement el : namesArr) {
                            names.add(el.getAsString());
                        }
                        com.lexoravisauls.client.badge.LexoraModUsers.setBackendNames(names);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                // Игнорируем ошибки интернета — старый список в LexoraModUsers
                // просто останется как был до следующей успешной попытки.
            }
        });
    }

    // ─── ЗАПРОС КОСМЕТИКИ ДЛЯ КОНКРЕТНОГО НИКА (Асинхронно) ───
    // Используется и для главного меню, и теперь для рендера в игре —
    // вызывается через ensureFetched() когда рендерер видит нового игрока.
    // ВАЖНО: параметр "username" здесь — это ИГРОВОЙ НИК (current_ign),
    // не логин на сайте. Называется username по историческим причинам
    // (так было в оригинальном коде), но по факту сюда всегда приходит
    // state.name из рендерера, то есть то имя, что видно в игре. api_cosmetics.php
    // теперь тоже ищет по current_ign — так что поведение согласовано,
    // просто имя параметра вводит в заблуждение при чтении.
    public static void fetchCosmetics(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }

        NETWORK_POOL.submit(() -> {
            try {
                String encodedName = URLEncoder.encode(username, "UTF-8");
                URL url = new URL("https://lexoravisuals.fun/api_cosmetics.php?username=" + encodedName);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();

                    if (json.has("status") && json.get("status").getAsString().equals("success")) {
                        String back = json.has("back") ? json.get("back").getAsString() : "none";
                        String head = json.has("head") ? json.get("head").getAsString() : "none";
                        String shoulder = json.has("shoulder") ? json.get("shoulder").getAsString() : "none";

                        setCosmetics(username, back, head, shoulder);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                // Игнорируем ошибки интернета
            } finally {
                lastFetchTime.put(username, System.currentTimeMillis());
                inFlight.remove(username);
            }
        });
    }

    // ─── ЛОКАЛЬНЫЕ МЕТОДЫ ───
    public static void setCosmetics(String username, String back, String head, String shoulder) {
        if (username == null || username.isEmpty()) return;
        Map<String, String> cMap = equippedCosmetics.computeIfAbsent(username, k -> new HashMap<>());
        cMap.put("back", back != null ? back : "none");
        cMap.put("head", head != null ? head : "none");
        cMap.put("shoulder", shoulder != null ? shoulder : "none");
    }

    // Вызывать при отключении от сервера
    public static void clearCache() {
        equippedCosmetics.clear();
        lastFetchTime.clear();
        inFlight.clear();
        lastHeartbeatTime = 0;
        lastModListTime = 0;
    }
}