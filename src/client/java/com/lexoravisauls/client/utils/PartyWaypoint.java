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
    //  Поставить свою метку (биндится на кнопку)
    // =========================================================================
    public static void placeWaypoint() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (!LexoraPartyManager.inParty()) {
            NotifManager.show("Метка", "Вы не в пати!", NotifManager.NotifType.ERROR);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSendMs < SEND_CD_MS) return;
        lastSendMs = now;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        String myName = mc.player.getName().getString();
        String myUuid = mc.player.getUuid().toString();
        String code   = LexoraPartyManager.partyCode;

        // Добавляем метку себе сразу
        String label = "Метка: " + myName;
        addMark(label, x, y, z);

        JsonObject body = new JsonObject();
        body.addProperty("action", "send_mark");
        body.addProperty("code",   code);
        body.addProperty("uuid",   myUuid);
        body.addProperty("name",   myName);
        body.addProperty("x", x);
        body.addProperty("y", y);
        body.addProperty("z", z);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Lexora/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("X-Lexora-Secret", "LexoraAdmin2026") // <--- НАШ VIP ПАРОЛЬ
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .exceptionally(e -> {
                    System.err.println("[PartyWaypoint] Ошибка сети: " + e.getMessage());
                    return null;
                });
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
                .header("X-Lexora-Secret", "LexoraAdmin2026") // <--- НАШ VIP ПАРОЛЬ
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
                            // Пока ответ летел по сети, игрок мог выйти из пати
                            // или сменить его — в этом случае просто игнорируем.
                            if (!LexoraPartyManager.inParty() || !code.equals(LexoraPartyManager.partyCode)) {
                                return;
                            }

                            for (int i = 0; i < arr.size(); i++) {
                                JsonObject obj  = arr.get(i).getAsJsonObject();
                                int    id   = obj.get("id").getAsInt();
                                String name = obj.get("name").getAsString();

                                if (id > lastMarkId) lastMarkId = id;

                                double x = obj.get("x").getAsDouble();
                                double y = obj.get("y").getAsDouble();
                                double z = obj.get("z").getAsDouble();

                                String label = "Метка: " + name;
                                addMark(label, x, y, z);

                                NotifManager.show(
                                        "Метка пати",
                                        name + " поставил метку",
                                        NotifManager.NotifType.WARNING
                                );

                                if (mc.player != null) {
                                    mc.player.playSound(
                                            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.7f, 1.0f
                                    );
                                }
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
        // Максимум 5 меток
        while (MARKS.size() > 5) MARKS.remove(0);
    }

    // =========================================================================
    //  HUD — стрелка с пилюлей (аналогично GPS)
    // =========================================================================
    public static void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || MARKS.isEmpty()) return;

        float cx = ctx.getScaledWindowWidth()  / 2.0f;
        float cy = ctx.getScaledWindowHeight() / 2.0f;

        for (ActiveMark m : MARKS) {
            double dx = m.x - mc.player.getX();
            double dz = m.z - mc.player.getZ();
            double dist = Math.sqrt(dx*dx + dz*dz);

            float yaw = mc.player.getYaw();
            double cos = Math.cos(Math.toRadians(yaw));
            double sin = Math.sin(Math.toRadians(yaw));
            double rotY = -(dz * cos - dx * sin);
            double rotX = -(dx * cos + dz * sin);
            if (Math.abs(rotX) < 0.01 && Math.abs(rotY) < 0.01) continue;

            float angle = (float)(Math.atan2(rotY, rotX) * 180.0 / Math.PI);
            float ax = (float)(50f * MathHelper.cos((float)Math.toRadians(angle)) + cx);
            float ay = (float)(50f * MathHelper.sin((float)Math.toRadians(angle)) + cy);

            // Пилюля с анимацией цвета
            int color = m.getBlinkColor();
            float alpha = m.animFadeOut;
            drawMarkPill(ctx, m.name, String.format(Locale.US, "%.0fм", dist), ax, ay, color, alpha);
        }
    }

    private static void drawMarkPill(DrawContext ctx, String name, String dist,
                                     float cx, float cy, int color, float alpha) {
        float fs = 9f;
        float nameW = getFont().getWidth(name, fs);
        float sepW  = getFont().getWidth(" · ", fs);
        float distW = getFont().getWidth(dist, fs);
        float padX = 8f, padY = 5f;
        float pillW = nameW + sepW + distW + padX * 2f;
        float pillH = fs + padY * 2f;
        float pillR = pillH / 2f;
        float px = cx - pillW / 2f;
        float py = cy - pillH / 2f;

        int a = (int)(210 * alpha);
        RoundedRectShader.draw(ctx, px, py, pillW, pillH, pillR, (a << 24) | 0x0D0C14);
        RoundedRectShader.draw(ctx, px, py, pillW, pillH, pillR, ((int)(18*alpha) << 24) | 0xFFFFFF);

        float tx = px + padX;
        float ty = py + (pillH - fs) / 2f - 0.5f;
        int textA = (int)(255 * alpha);
        getFont().draw(ctx.getMatrices(), name, tx, ty, fs, (textA << 24) | (color & 0xFFFFFF)); tx += nameW;
        getFont().draw(ctx.getMatrices(), " · ", tx, ty, fs, (textA/3 << 24) | 0xFFFFFF);       tx += sepW;
        getFont().draw(ctx.getMatrices(), dist, tx, ty, fs, (textA << 24) | (color & 0xFFFFFF));
    }

    // =========================================================================
    //  3D рендер — текст падает + круг вырастает + свечение
    // =========================================================================
    public static void render3D(DrawContext ctx, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || MARKS.isEmpty()) return;

        double px = MathHelper.lerp(tickDelta, mc.player.prevX, mc.player.getX());
        double py = MathHelper.lerp(tickDelta, mc.player.prevY, mc.player.getY());
        double pz = MathHelper.lerp(tickDelta, mc.player.prevZ, mc.player.getZ());

        for (ActiveMark m : MARKS) {
            renderMark3D(ctx, camera, m, px, py, pz);
        }
    }

    private static void renderMark3D(DrawContext ctx, Camera cam,
                                     ActiveMark m, double px, double py, double pz) {
        // ── Проецируем текстовый якорь (немного над землёй) ─────────────────
        Vec3d worldPos = new Vec3d(m.x, m.y + 0.1, m.z);
        double dx = worldPos.x - px, dy = worldPos.y - py, dz = worldPos.z - pz;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

        Vec3d camPos   = cam.getPos();
        Vec3d toTarget = worldPos.subtract(camPos);
        Vec3d dir      = toTarget.lengthSquared() < 0.0001 ? new Vec3d(0,0,1) : toTarget.normalize();

        boolean farProxy = dist > 80.0;
        Vec3d renderPos  = farProxy ? camPos.add(dir.multiply(30.0)) : worldPos;

        // Точка экрана для текста (поднимаем на 2.5 блока)
        Vec3d screenText = worldToScreen2D(renderPos.add(0, 2.5, 0), cam);
        // Точка экрана для круга на земле
        Vec3d screenCircle = worldToScreen2D(renderPos.add(0, 0.05, 0), cam);
        if (screenText == null || screenCircle == null) return;

        float scale = farProxy
                ? MathHelper.clamp(1f - (float)(dist - 80) / 500f, 0.5f, 1f)
                : MathHelper.clamp(1f - (float)(dist / 160f), 0.55f, 1f);

        float alpha     = m.animFadeOut;
        int   markColor = m.getBlinkColor();
        int   r         = (markColor >> 16) & 0xFF;
        int   g         = (markColor >> 8)  & 0xFF;
        int   b         =  markColor        & 0xFF;

        // ── 1. Круг на земле ─────────────────────────────────────────────────
        float circleSize = 80f * scale * m.animCircle;   // вырастает с 0 до 80px
        if (circleSize > 2f) {
            float cx = (float)screenCircle.x;
            float cy = (float)screenCircle.y;

            // Свечение (несколько слоёв с разной прозрачностью)
            int glowA = (int)(60 * alpha * m.animCircle);
            drawCircleGlow(ctx, cx, cy, circleSize, glowA, r, g, b);

            // Основной круг (текстура jump_circles.png)
            int texA = (int)(220 * alpha * m.animCircle);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(r/255f, g/255f, b/255f, texA/255f);
            ctx.drawTexture(RenderLayer::getGuiTextured, CIRCLE_TEX,
                    (int)(cx - circleSize/2f), (int)(cy - circleSize/2f),
                    0f, 0f,
                    (int)circleSize, (int)circleSize,
                    (int)circleSize, (int)circleSize);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        // ── 2. Текст с анимацией падения ─────────────────────────────────────
        // easeOutCubic — текст появляется быстро, замедляется
        float eased    = easeOutCubic(m.animAppear);
        // Начальная позиция выше на 30px, опускается к финальной
        float textOffY = (1f - eased) * -30f;

        float textAlpha = eased * alpha;
        if (textAlpha < 0.02f) return;

        int textA = (int)(255 * textAlpha);
        String label = m.name;
        float fs = 10f * scale;
        float tw = getFont().getWidth(label, fs);

        float tx = (float)screenText.x - tw / 2f;
        float ty = (float)screenText.y + textOffY;

        // Фон текста (размытый прямоугольник)
        float padX = 6f * scale, padY = 4f * scale;
        boolean blurEnabled = com.lexoravisauls.client.gui.LexoraGui.moduleStates
                .getOrDefault("Watermark Blur", true);

        int bgA = (int)(180 * textAlpha);
        int bgColor = (bgA << 24) | 0x0D0C14;

        if (blurEnabled) {
            try {
                com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(
                        ctx, tx - padX, ty - padY, tw + padX * 2, fs + padY * 2,
                        5f, 10f, bgColor);
            } catch (Throwable ignored) {
                RoundedRectShader.draw(ctx, tx - padX, ty - padY,
                        tw + padX*2, fs + padY*2, 5f, bgColor);
            }
        } else {
            RoundedRectShader.draw(ctx, tx - padX, ty - padY,
                    tw + padX*2, fs + padY*2, 5f, bgColor);
        }

        // Текст
        getFont().draw(ctx.getMatrices(), label, tx, ty, fs,
                (textA << 24) | (markColor & 0xFFFFFF));

        // Линия от текста к кругу
        if (screenCircle != null && m.animCircle > 0.3f) {
            float lineAlpha = (float)Math.min(textAlpha, m.animCircle * alpha);
            drawLine(ctx,
                    (float)screenText.x, (float)screenText.y + fs + padY,
                    (float)screenCircle.x, (float)screenCircle.y,
                    (int)(120 * lineAlpha), r, g, b);
        }
    }

    // ── Свечение круга (несколько размытых колец) ─────────────────────────
    private static void drawCircleGlow(DrawContext ctx, float cx, float cy,
                                       float size, int alpha, int r, int g, int b) {
        // 3 слоя всё большего размера с убывающей прозрачностью
        for (int layer = 0; layer < 3; layer++) {
            float layerSize = size * (1f + layer * 0.28f);
            int   layerA    = alpha / (layer + 1);
            if (layerA < 3) continue;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(r/255f, g/255f, b/255f, layerA/255f);
            ctx.drawTexture(RenderLayer::getGuiTextured, CIRCLE_TEX,
                    (int)(cx - layerSize/2f), (int)(cy - layerSize/2f),
                    0f, 0f,
                    (int)layerSize, (int)layerSize,
                    (int)layerSize, (int)layerSize);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ── Линия-коннектор ────────────────────────────────────────────────────
    private static void drawLine(DrawContext ctx,
                                 float x1, float y1, float x2, float y2,
                                 int alpha, int r, int g, int b) {
        int color = (alpha << 24) | (r << 16) | (g << 8) | b;
        // Вертикальная линия — рисуем как тонкий прямоугольник
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);
        float minX = Math.min(x1, x2) - 0.5f;
        RoundedRectShader.draw(ctx, minX, minY, 1f, maxY - minY, 0f, color);
    }

    // ── Проекция мировых координат на экран ───────────────────────────────
    private static Vec3d worldToScreen2D(Vec3d worldPos, Camera camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d camPos = camera.getPos();
        Vector3f rel = new Vector3f(
                (float)(worldPos.x - camPos.x),
                (float)(worldPos.y - camPos.y),
                (float)(worldPos.z - camPos.z));
        new Quaternionf(camera.getRotation()).conjugate().transform(rel);
        if (rel.z() >= -0.001f) return null;

        float guiW   = mc.getWindow().getScaledWidth();
        float guiH   = mc.getWindow().getScaledHeight();
        float aspect = guiW / guiH;
        float tan    = (float)Math.tan(Math.toRadians(mc.options.getFov().getValue()) * 0.5);

        float ndcX = rel.x() / (-rel.z() * tan * aspect);
        float ndcY = rel.y() / (-rel.z() * tan);
        float sx   = (ndcX * 0.5f + 0.5f) * guiW;
        float sy   = (0.5f - ndcY * 0.5f) * guiH;

        float pad = 50f;
        sx = MathHelper.clamp(sx, pad, guiW - pad);
        sy = MathHelper.clamp(sy, pad, guiH - pad);
        return new Vec3d(sx, sy, 0);
    }

    private static float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return 1f - (float)Math.pow(1f - t, 3);
    }
}