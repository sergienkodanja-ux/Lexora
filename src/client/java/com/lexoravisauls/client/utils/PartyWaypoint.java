package com.lexoravisauls.client.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.party.LexoraPartyManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lexora Party Waypoint System
 *
 * ФИКС: раньше метки шли через отдельную глобальную IRC-комнату
 * "party_waypoints" БЕЗ проверки принадлежности к пати — любой игрок
 * с клиентом получал метки вообще всех, даже не будучи в пати или
 * состоя в другом пати.
 *
 * Теперь: метка отправляется и принимается ТОЛЬКО через party.php,
 * привязана к конкретному коду пати (LexoraPartyManager.partyCode),
 * и сервер сам проверяет, что отправитель/получатель реально состоят
 * в этом пати (action=send_mark / poll_marks).
 *
 * Если игрок не в пати — поллинг вообще не выполняется, а
 * placeWaypoint() ничего никуда не шлёт (можно оставить локальную
 * метку себе, но рассылки не будет).
 *
 * Использование:
 * 1. Вызвать PartyWaypoint.register() в ClientModInitializer
 * 2. Забиндить PartyWaypoint.placeWaypoint() на кнопку
 * 3. Вызвать PartyWaypoint.renderHud() из HUD рендера
 * 4. Вызвать PartyWaypoint.render3D() из 3D рендера
 */
public final class PartyWaypoint {

    // ── Константы ────────────────────────────────────────────────────────────
    private static final String BASE_URL     = "https://lexoravisuals.fun/party.php";
    private static final long   LIFETIME_MS  = 15_000L;   // 15 секунд (клиентская жизнь метки)
    private static final long   POLL_MS      = 2_000L;
    private static final long   SEND_CD_MS   = 1_000L;

    private static final Identifier FONT_TEX    = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON   = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static final Identifier CIRCLE_TEX  = Identifier.of("lexoravisauls", "textures/gui/jump_circles.png");
    private static final Identifier ICON_HELP   = Identifier.of("lexoravisauls", "textures/gui/markers/marker_help.png");

    private static MsdfFont font;
    private static MsdfFont getFont() {
        if (font == null) font = new MsdfFont(FONT_TEX, FONT_JSON);
        return font;
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // ── Активные метки ───────────────────────────────────────────────────────
    public static final List<ActiveMark> MARKS = new CopyOnWriteArrayList<>();

    // ── Состояние ────────────────────────────────────────────────────────────
    private static boolean registered  = false;
    private static long    lastPollMs  = 0;
    private static long    lastSendMs  = 0;
    private static int     lastMarkId  = 0;

    // Код пати, для которого валиден lastMarkId. Сбрасывается при смене
    // пати или перезаходе, чтобы не тащить курсор из чужой/старой сессии.
    private static String  trackedPartyCode = null;

    private PartyWaypoint() {}

    // =========================================================================
    //  Data class
    // =========================================================================
    public static final class ActiveMark {
        public final String name;       // "Метка от ИгрокА"
        public final double x, y, z;
        public final long   createdAt;

        // Анимации
        public float animAppear    = 0f;  // 0→1 за 0.6 сек (текст падает)
        public float animCircle    = 0f;  // 0→1 за 0.5 сек (круг растёт)
        public float animFadeOut   = 1f;  // 1→0 за последние 2 сек

        public ActiveMark(String name, double x, double y, double z) {
            this.name      = name;
            this.x         = x;
            this.y         = y;
            this.z         = z;
            this.createdAt = System.currentTimeMillis();
        }

        /** Прогресс жизни 0..1 */
        public float life() {
            return MathHelper.clamp((float)(System.currentTimeMillis() - createdAt) / LIFETIME_MS, 0f, 1f);
        }

        public boolean expired() {
            return System.currentTimeMillis() - createdAt > LIFETIME_MS;
        }

        /** Цвет метки: белый→красный плавно */
        public int getBlinkColor() {
            float t = life();                          // 0..1
            // Пульсация 3 Гц поверх прогресса
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 167.0) * 0.5 + 0.5);
            // Начало белый, конец красный, пульсирует
            float r = 1f;
            float g = MathHelper.clamp(1f - t * 1.2f - pulse * 0.15f, 0f, 1f);
            float b = MathHelper.clamp(1f - t * 1.2f - pulse * 0.15f, 0f, 1f);
            int ri = (int)(r * 255);
            int gi = (int)(g * 255);
            int bi = (int)(b * 255);
            return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
        }
    }

    // =========================================================================
    //  Register (вызвать один раз)
    // =========================================================================
    public static void register() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Удаляем истёкшие метки
            MARKS.removeIf(ActiveMark::expired);

            // Обновляем анимации
            float dt = 0.05f; // ~1 тик
            for (ActiveMark m : MARKS) {
                m.animAppear  = MathHelper.clamp(m.animAppear  + dt * 1.8f, 0f, 1f);
                m.animCircle  = MathHelper.clamp(m.animCircle  + dt * 2.2f, 0f, 1f);
                float remaining = 1f - m.life();
                m.animFadeOut = remaining < 0.13f
                        ? MathHelper.clamp(remaining / 0.13f, 0f, 1f)
                        : 1f;
            }

            // Не в пати — вообще не поллим чужие метки. Это и есть
            // главный фикс: без пати метки просто не запрашиваются.
            if (!LexoraPartyManager.inParty()) {
                if (trackedPartyCode != null) {
                    trackedPartyCode = null;
                    lastMarkId = 0;
                }
                return;
            }

            String code = LexoraPartyManager.partyCode;

            // Сменилось пати (вышли/зашли в другое, либо новый заход
            // в мир после ребута клиента) — сбрасываем курсор поллинга.
            if (!code.equals(trackedPartyCode)) {
                trackedPartyCode = code;
                lastMarkId = 0;
            }

            long now = System.currentTimeMillis();
            if (now - lastPollMs >= POLL_MS) {
                lastPollMs = now;
                pollMarks(client, code);
            }
        });
    }

    // =========================================================================
    //  Поставить свою метку (делегирует в единый sendPartyHelp)
    // =========================================================================
    public static void placeWaypoint() {
        com.lexoravisauls.client.party.LexoraPartyClient.sendPartyHelp();
    }

    // =========================================================================
    //  Поллинг меток от других — только своё пати (code передаём явно)
    // =========================================================================
    private static void pollMarks(MinecraftClient mc, String code) {
        String myUuid = mc.player != null ? mc.player.getUuid().toString() : "";
        if (myUuid.isEmpty()) return;

        JsonObject body = new JsonObject();
        body.addProperty("action", "poll_marks");
        body.addProperty("code",   code);
        body.addProperty("uuid",   myUuid);
        body.addProperty("after",  lastMarkId);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Lexora/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Lexora-Secret", "LexoraAdmin2026")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) return;
                    try {
                        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (!json.has("marks")) return;
                        JsonArray arr = json.getAsJsonArray("marks");
                        if (arr.isEmpty()) return;

                        mc.execute(() -> {
                            if (!LexoraPartyManager.inParty() || !code.equals(LexoraPartyManager.partyCode)) {
                                return;
                            }

                            for (int i = 0; i < arr.size(); i++) {
                                JsonObject obj  = arr.get(i).getAsJsonObject();
                                int    id   = obj.get("id").getAsInt();
                                String name = obj.get("name").getAsString();
                                String senderUuid = obj.has("uuid") ? obj.get("uuid").getAsString() : "";

                                if (id > lastMarkId) lastMarkId = id;

                                // 1. Пропускаем свои собственные метки
                                if (myUuid.equalsIgnoreCase(senderUuid) || (mc.player != null && mc.player.getName().getString().equalsIgnoreCase(name))) {
                                    continue;
                                }

                                double x = obj.get("x").getAsDouble();
                                double y = obj.get("y").getAsDouble();
                                double z = obj.get("z").getAsDouble();

                                // 2. Защита от дублирования меток
                                if (CalloutManager.isRecentPartyMark(name, x, z)) {
                                    continue;
                                }
                                CalloutManager.recordPartyMark(name, x, z);

                                // 3. Создаём единую аккуратную GPS-метку
                                String markName = "Хелпа: " + name;
                                GPS.addCalloutWaypoint(markName, x, y, z);

                                if (mc.player != null) {
                                    mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1f, 1.0f);
                                }

                                NotifManager.show("Метка в пати", name + " поставил метку!", NotifManager.NotifType.WARNING);
                            }
                        });
                    } catch (Exception ignored) {}
                })
                .exceptionally(e -> null);
    }

    private static void addMark(String name, double x, double y, double z) {
        // Убираем старую метку с тем же именем
        MARKS.removeIf(m -> m.name.equals(name));
        MARKS.add(new ActiveMark(name, x, y, z));
        while (MARKS.size() > 5) MARKS.remove(0);
    }

    // =========================================================================
    //  HUD
    // =========================================================================
    public static void renderHud(DrawContext ctx) {
        // Рендеринг объединён с GPS.renderWaypointPanels
    }

    // =========================================================================
    //  3D рендер — 3 круга на полу удалены по запросу пользователя.
    //  Все метки пати отображаются через современный GPS.renderWaypointPanels.
    // =========================================================================
    public static void render3D(DrawContext ctx, Camera camera, float tickDelta) {
        // Уродские 3 круга, линия и дублирующий текст удалены.
    }
}