package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.MsdfFont;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPS {

    // =========================================================================
    //  Waypoint
    // =========================================================================

    public static final class GpsWaypoint {
        public final String name;
        public double x, y, z; // Убрали final, чтобы можно было редактировать в GUI
        public int color;      // Добавили цвет для кастомизации из меню

        float   smoothAngle  = 0f;
        double  smoothArrowX = 0, smoothArrowY = 0;
        boolean smoothInit   = false;

        final long createdAt = System.currentTimeMillis();
        public boolean fromCallout = false;

        public float floatOffset = -45f;
        public float floatAlpha  = 0f;

        public float ringPhase = 0f;

        public GpsWaypoint(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = getAccentColor(name); // Цвет по умолчанию с автоопределением ивентов
        }

        public boolean calloutExpired() {
            return fromCallout && System.currentTimeMillis() - createdAt > 15_000L;
        }

        public float blinkAlpha() {
            if (!fromCallout) return 1f;
            long elapsed = System.currentTimeMillis() - createdAt;
            long total   = 15_000L;
            if (elapsed >= total) return 0f;
            long fadeStart = total - 2000L;
            if (elapsed > fadeStart) {
                return MathHelper.clamp((total - elapsed) / 2000f, 0f, 1f);
            }
            return 1f;
        }
    }

    // =========================================================================
    //  MSDF Font
    // =========================================================================

    private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;
    private static MsdfFont getFont() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    // =========================================================================
    //  Textures & Colors
    // =========================================================================

    private static final Identifier RING_TEX = Identifier.of("lexoravisauls", "textures/gui/jump_circles.png");
    private static final Identifier ARROW_TEXTURE = Identifier.of("lexoravisauls", "textures/gui/gps_arrow.png");

    private static int getAccentColor(String name) {
        if (name == null) return 0xFF1D9E75;
        String l = name.toLowerCase(Locale.ROOT);
        if (l.contains("маяк"))                           return 0xFF7F77DD;
        if (l.contains("метеор"))                         return 0xFFEF9F27;
        if (l.contains("вулкан"))                         return 0xFFD85A30;
        if (l.contains("резня") || l.contains("адск"))    return 0xFFE24B4A;
        if (l.contains("сундук"))                         return 0xFF888780;
        return 0xFF1D9E75;
    }

    // =========================================================================
    //  State
    // =========================================================================

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Сделали публичным и переименовали в waypoints для связи с ModernClickGui
    public static final List<GpsWaypoint> waypoints = new CopyOnWriteArrayList<>();

    private static String pendingEventName     = null;
    private static long   pendingEventExpireAt = 0L;

    private static final double FAR_PROXY_START_DISTANCE  = 95.0;
    private static final double FAR_PROXY_RENDER_DISTANCE = 38.0;

    // =========================================================================
    //  Patterns
    // =========================================================================

    private static final Pattern GPS_SET_PATTERN = Pattern.compile(
            "^\\.gps\\s+set\\s+\"([^\"]+)\"(?:\\s+(-?\\d+)(?:\\s+(-?\\d+))?(?:\\s+(-?\\d+))?)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GPS_CLEAR_ALL_PATTERN = Pattern.compile(
            "^\\.gps\\s+clear\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GPS_CLEAR_ONE_PATTERN = Pattern.compile(
            "^\\.gps\\s+clear\\s+\"([^\"]+)\"\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GPS_LIST_PATTERN = Pattern.compile(
            "^\\.gps\\s+list\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEATH_CHEST_PATTERN = Pattern.compile(
            "сундук\\s+смерти.*?появится\\s+уже\\s+через\\s+(\\d+)\\s+минут", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_STARTED_PATTERN = Pattern.compile(
            "(портал\\s+на\\s+резню\\s+активирован|ид[её]т\\s+страшный\\s+бой|адская\\s+резня)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKET_COORDS_PATTERN = Pattern.compile(
            "\\[\\s*(-?\\d{1,7})\\s+(-?\\d{1,4})\\s+(-?\\d{1,7})\\s*\\]");
    private static final Pattern KEYWORD_COORDS_PATTERN = Pattern.compile(
            "(?:на\\s+координатах|координаты|координатах|появился\\s+на\\s+координатах)\\s*:?\\s*\\[?\\s*(-?\\d{1,7})\\s*[ ,]+\\s*(-?\\d{1,4})\\s*[ ,]+\\s*(-?\\d{1,7})\\s*\\]?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_COORDS_PATTERN = Pattern.compile(
            "(?<!\\d)(-?\\d{1,7})\\s+(-?\\d{1,4})\\s+(-?\\d{1,7})(?!\\d)");
    private static final Pattern GENERIC_EVENT_LINE_PATTERN = Pattern.compile(
            "(?:следующий|текущий|активный)?\\s*(?:ивент|событие|event)\\s*:?\\s*([^\\[]+?)(?:\\s+на\\s+координатах|\\s*\\[|\\s+через|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_EVENT_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2}).{0,40}?(маяк\\s+убийца|загадочный\\s+маяк|метеоритный\\s+дождь|вулкан|адская\\s+резня|сундук\\s+смерти)",
            Pattern.CASE_INSENSITIVE);

    private GPS() {}

    // =========================================================================
    //  Public API
    // =========================================================================

    public static List<GpsWaypoint> getWaypoints()  { return Collections.unmodifiableList(waypoints); }
    public static boolean           hasTarget()      { return !waypoints.isEmpty(); }
    public static String  getLastEventName()         { return waypoints.isEmpty() ? "" : waypoints.get(waypoints.size()-1).name; }
    public static double  getTargetX()               { return waypoints.isEmpty() ? 0 : waypoints.get(0).x; }
    public static double  getTargetY()               { return waypoints.isEmpty() ? 0 : waypoints.get(0).y; }
    public static double  getTargetZ()               { return waypoints.isEmpty() ? 0 : waypoints.get(0).z; }

    public static void addWaypoint(String name, double x, double y, double z) {
        waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        GpsWaypoint wp = new GpsWaypoint(name, x, y, z);
        if (name.contains("(callout)") || name.startsWith("§b")) wp.fromCallout = true;
        waypoints.add(wp);
    }

    public static void addCalloutWaypoint(String name, double x, double y, double z) {
        waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        GpsWaypoint wp = new GpsWaypoint(name, x, y, z);
        wp.fromCallout = true;
        wp.floatAlpha  = 1f; // callout живёт 15 сек — пропускаем анимацию появления
        wp.floatOffset = 0f; // без плавающего смещения
        waypoints.add(wp);
    }

    public static void removeWaypoint(String name)  { waypoints.removeIf(w -> w.name.equalsIgnoreCase(name)); }
    public static void clearAll()                    { waypoints.clear(); pendingEventName = null; pendingEventExpireAt = 0L; }
    public static void setTarget(double x, double y, double z)              { addWaypoint("GPS", x, y, z); }
    public static void setTarget(double x, double y, double z, String name) { addWaypoint(name.isEmpty() ? "GPS" : name, x, y, z); }
    public static void clearTarget()                                          { clearAll(); }
    public static void reset()                                                { clearAll(); }

    // =========================================================================
    //  Commands
    // =========================================================================

    public static boolean handleLocalCommand(String rawMessage) {
        if (mc.player == null || mc.world == null || rawMessage == null) return false;
        String msg = rawMessage.trim();
        if (GPS_LIST_PATTERN.matcher(msg).matches())      { printList(); return true; }
        if (GPS_CLEAR_ALL_PATTERN.matcher(msg).matches()) {
            clearAll();
            mc.player.sendMessage(Text.literal("§cВсе GPS метки очищены"), true);
            return true;
        }
        Matcher co = GPS_CLEAR_ONE_PATTERN.matcher(msg);
        if (co.matches()) {
            String n = co.group(1).trim();
            removeWaypoint(n);
            mc.player.sendMessage(Text.literal("§cМетка §e" + n + " §cудалена"), true);
            return true;
        }
        Matcher set = GPS_SET_PATTERN.matcher(msg);
        if (!set.matches()) return false;
        String name = set.group(1).trim();
        String g2 = set.group(2), g3 = set.group(3), g4 = set.group(4);
        double x, y, z;
        if (g2 == null)      { x = mc.player.getX(); y = mc.player.getY(); z = mc.player.getZ(); }
        else if (g4 == null) { x = Integer.parseInt(g2); y = Math.floor(mc.player.getY()); z = Integer.parseInt(g3); }
        else                  { x = Integer.parseInt(g2); y = Integer.parseInt(g3); z = Integer.parseInt(g4); }
        addWaypoint(name, x, y, z);
        mc.player.sendMessage(Text.literal("§aGPS: §e" + name + " §7[" + (int)x + " " + (int)y + " " + (int)z + "]"), true);
        return true;
    }

    private static void printList() {
        if (mc.player == null) return;
        if (waypoints.isEmpty()) {
            mc.player.sendMessage(Text.literal("§7Нет активных GPS метки"), false);
            return;
        }
        mc.player.sendMessage(Text.literal("§6§lGPS метки:"), false);
        for (GpsWaypoint w : waypoints) {
            double dist = Math.sqrt(Math.pow(w.x - mc.player.getX(), 2) + Math.pow(w.z - mc.player.getZ(), 2));
            MutableText del = Text.literal("§c[x]").styled(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ".gps clear \"" + w.name + "\""))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§cНажми Enter для удаления"))));
            mc.player.sendMessage(Text.literal("").append(del)
                    .append(Text.literal(" §e" + w.name))
                    .append(Text.literal(" §7" + (int)w.x + " " + (int)w.y + " " + (int)w.z))
                    .append(Text.literal(" §f• §a" + String.format(Locale.US, "%.0fм", dist))), false);
        }
    }

    // =========================================================================
    //  Auto-GPS
    // =========================================================================

    public static void processMessage(String message) {
        if (!ClientData.moduleStates.getOrDefault("GPS", false)) return;
        if (!ClientData.moduleStates.getOrDefault("GPS Auto", false)) return;
        if (mc.player == null || message == null || message.isEmpty()) return;
        String clean = normalizeMessage(message);
        String mode  = ClientData.modeSettings.getOrDefault("GPS Mode", "Funtime");
        if ("Funtime".equalsIgnoreCase(mode))        processFuntimeMessage(clean);
        else if ("HolyWorld".equalsIgnoreCase(mode)) processHolyWorldMessage(clean);
    }

    private static String normalizeMessage(String m) {
        return m.replaceAll("§[0-9a-fk-or]", "")
                .replaceAll("&[0-9a-fk-or]", "")
                .replaceAll("[\\x00-\\x1F\\x7F]", "")
                .replaceAll("\\p{C}", "")
                .replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ").trim();
    }

    private static void processFuntimeMessage(String clean) {
        String lower = clean.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (pendingEventExpireAt > 0L && now > pendingEventExpireAt) {
            pendingEventName = null; pendingEventExpireAt = 0L;
        }
        if (EVENT_STARTED_PATTERN.matcher(lower).find()) return;
        Matcher dc = DEATH_CHEST_PATTERN.matcher(lower);
        if (dc.find()) {
            try {
                if (Integer.parseInt(dc.group(1)) == 3 && mc.player != null)
                    mc.player.sendMessage(Text.literal("§cСундук Смерти появится через 3 минуты"), true);
            } catch (NumberFormatException ignored) {}
            return;
        }
        String en = detectFuntimeEventName(clean, lower);
        Vec3d  co = tryExtractCoords(clean);
        if (en != null && co != null) {
            applyTarget(co, en); pendingEventName = null; pendingEventExpireAt = 0L;
        } else if (en != null) {
            pendingEventName = en; pendingEventExpireAt = now + 15000L;
        } else if (co != null && pendingEventName != null) {
            applyTarget(co, pendingEventName); pendingEventName = null; pendingEventExpireAt = 0L;
        }
    }

    private static void processHolyWorldMessage(String clean) {
        String lower = clean.toLowerCase(Locale.ROOT);
        if (!(lower.contains("ивент") || lower.contains("event") ||
                lower.contains("метеорит") || lower.contains("вулкан") || lower.contains("маяк"))) return;
        Vec3d c = tryExtractCoords(clean);
        if (c != null) applyTarget(c, "HolyWorld Event");
    }

    private static String detectFuntimeEventName(String clean, String lower) {
        if (lower.contains("маяк убийца") || (lower.contains("маяк") && lower.contains("убийц"))) return "Маяк убийца";
        if (lower.contains("метеорит"))    return "Метеоритный дождь";
        if (lower.contains("вулкан"))      return "Вулкан";
        if (lower.contains("адская резня") || lower.contains("портал на резню")) return "Адская резня";
        if (lower.contains("сундук смерти")) return "Сундук смерти";
        Matcher m = GENERIC_EVENT_LINE_PATTERN.matcher(clean);
        if (m.find()) { String r = m.group(1).trim(); if (!r.isEmpty()) return cleanupEventName(r); }
        Matcher t = TIME_EVENT_PATTERN.matcher(lower);
        if (t.find()) return cleanupEventName(t.group(2));
        return null;
    }

    private static String cleanupEventName(String raw) {
        return raw.replaceAll("^[\\-–—: ]+", "").replaceAll("[\\-–—: ]+$", "").replaceAll("\\s+", " ").trim();
    }

    private static Vec3d tryExtractCoords(String text) {
        Matcher m;
        m = KEYWORD_COORDS_PATTERN.matcher(text); if (m.find()) return pc(m.group(1), m.group(2), m.group(3));
        m = BRACKET_COORDS_PATTERN.matcher(text);  if (m.find()) return pc(m.group(1), m.group(2), m.group(3));
        m = PLAIN_COORDS_PATTERN.matcher(text);    if (m.find()) return pc(m.group(1), m.group(2), m.group(3));
        return null;
    }

    private static Vec3d pc(String sx, String sy, String sz) {
        try { return new Vec3d(Double.parseDouble(sx), Double.parseDouble(sy), Double.parseDouble(sz)); }
        catch (Exception e) { return null; }
    }

    private static void applyTarget(Vec3d coords, String name) {
        for (GpsWaypoint w : waypoints)
            if (w.name.equalsIgnoreCase(name) && Math.abs(w.x - coords.x) < 0.01 && Math.abs(w.z - coords.z) < 0.01) return;
        addWaypoint(name, coords.x, coords.y, coords.z);
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§aGPS §e" + name + " §7[" + (int)coords.x + " " + (int)coords.y + " " + (int)coords.z + "]"), true);
    }

    // =========================================================================
    //  HUD — статический текст + вращающаяся стрелка
    // =========================================================================

    public static void renderHud(DrawContext context) {
        if (mc.player == null || mc.world == null) return;
        if (!ClientData.moduleStates.getOrDefault("GPS", false)) return;
        if (waypoints.isEmpty()) return;

        float cx             = context.getScaledWindowWidth()  / 2.0f;
        float cy             = context.getScaledWindowHeight() / 2.0f;
        float distFromCenter = ClientData.numSettings.getOrDefault("GPS HUD Distance", 50.0f);
        float arrowSize      = ClientData.numSettings.getOrDefault("GPS Arrow Size",   20.0f);

        for (GpsWaypoint w : waypoints)
            renderWaypointHud(context, w, cx, cy, distFromCenter, arrowSize);

        PartyWaypoint.renderHud(context);
    }

    private static void renderWaypointHud(DrawContext ctx, GpsWaypoint w,
                                          float cx, float cy,
                                          float distFromCenter, float arrowSize) {
        double deltaX   = w.x - mc.player.getX();
        double deltaZ   = w.z - mc.player.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float  yaw = mc.player.getYaw();
        double cos = Math.cos(Math.toRadians(yaw));
        double sin = Math.sin(Math.toRadians(yaw));
        double rotY = -(deltaZ * cos - deltaX * sin);
        double rotX = -(deltaX * cos + deltaZ * sin);
        if (Math.abs(rotX) < 0.01 && Math.abs(rotY) < 0.01) return;

        float targetAngle = (float)(Math.atan2(rotY, rotX) * 180.0 / Math.PI);
        if (!w.smoothInit) { w.smoothAngle = targetAngle; w.smoothInit = true; }
        float diff = targetAngle - w.smoothAngle;
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        w.smoothAngle += diff * 0.3f;

        double tAX = distFromCenter * MathHelper.cos((float)Math.toRadians(targetAngle)) + cx;
        double tAY = distFromCenter * MathHelper.sin((float)Math.toRadians(targetAngle)) + cy;
        w.smoothArrowX += (tAX - w.smoothArrowX) * 0.3;
        w.smoothArrowY += (tAY - w.smoothArrowY) * 0.3;

        float ax = (float)w.smoothArrowX;
        float ay = (float)w.smoothArrowY;

        // Берем цвет прямо из нашей метки
        int accent = w.color;
        float ar = ((accent >> 16) & 0xFF) / 255f;
        float ag = ((accent >>  8) & 0xFF) / 255f;
        float ab = ( accent        & 0xFF) / 255f;
        float blink = w.blinkAlpha();

        ctx.getMatrices().push();
        ctx.getMatrices().translate(ax, ay, 0f);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(w.smoothAngle + 90f));
        ctx.getMatrices().translate(-arrowSize / 2f, -arrowSize / 2f, 0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(ar, ag, ab, blink);
        ctx.drawTexture(RenderLayer::getGuiTextured, ARROW_TEXTURE,
                0, 0, 0f, 0f, (int)arrowSize, (int)arrowSize, (int)arrowSize, (int)arrowSize);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ctx.getMatrices().pop();

        drawHudElements(ctx, w, (float)distance, ax, ay, arrowSize, blink);
    }

    private static void drawHudElements(DrawContext ctx, GpsWaypoint w, float distance,
                                        float ax, float ay, float arrowSize, float blink) {

        float nameSize = 7.0f;
        float distSize = 7.0f;
        int alphaI = MathHelper.clamp((int)(255 * blink), 0, 255);
        if (alphaI < 8) return;

        String cleanName = w.name.replaceAll("§[0-9a-fk-or]", "");
        float nameW = getFont().getWidth(cleanName, nameSize);
        String distStr = String.format(Locale.US, "%.0fм", distance);
        float distW = getFont().getWidth(distStr, distSize);

        int accent = w.color; // Берем цвет прямо из метки
        int shadowA = MathHelper.clamp((int)(alphaI * 0.6f), 0, 200);

        float textGap = 4f;

        float nameX = ax - nameW / 2f;
        float nameY = ay - (arrowSize / 2f) - nameSize - textGap;

        float distX = ax - distW / 2f;
        float distY = ay + (arrowSize / 2f) + textGap;

        float stickW = 2.0f;
        float stickH = 8.0f;
        float stickX = ax - (arrowSize / 2f) - stickW - 3f;
        float stickY = ay - stickH / 2f;

        int stickColor = 0xFFFFFFFF;
        RoundedRectShader.draw(ctx, (int)stickX, (int)stickY, (int)stickW, (int)stickH, 1.0f, (alphaI << 24) | (stickColor & 0xFFFFFF));

        getFont().draw(ctx.getMatrices(), cleanName, nameX + 1f, nameY + 1f, nameSize, (shadowA << 24) | 0x000000);
        getFont().draw(ctx.getMatrices(), cleanName, nameX, nameY, nameSize, (alphaI << 24) | 0xFFFFFF);

        getFont().draw(ctx.getMatrices(), distStr, distX + 1f, distY + 1f, distSize, (shadowA << 24) | 0x000000);
        getFont().draw(ctx.getMatrices(), distStr, distX, distY, distSize, (alphaI << 24) | (accent & 0xFFFFFF));
    }

    // =========================================================================
    //  Tick — регистрировать в ClientModInitializer через ClientTickEvents
    // =========================================================================

    public static void tick() {
        waypoints.removeIf(GpsWaypoint::calloutExpired);

        for (GpsWaypoint w : waypoints) {
            w.ringPhase   = (w.ringPhase + 0.013f) % 1.0f;
            w.floatOffset += (0f - w.floatOffset) * 0.04f;
            w.floatAlpha  += (1f - w.floatAlpha)  * 0.04f;
        }
    }

    // =========================================================================
    //  2D проекция плавающего текста
    // =========================================================================

    public static void render3DAsHud(DrawContext context, Camera camera, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        if (!ClientData.moduleStates.getOrDefault("GPS", false)) return;
        if (!ClientData.moduleStates.getOrDefault("GPS Show 3D Marker", true)) return;

        if (waypoints.isEmpty()) return;

        double px = MathHelper.lerp(tickDelta, mc.player.prevX, mc.player.getX());
        double py = MathHelper.lerp(tickDelta, mc.player.prevY, mc.player.getY());
        double pz = MathHelper.lerp(tickDelta, mc.player.prevZ, mc.player.getZ());

        for (GpsWaypoint w : waypoints)
            renderFloatingText2D(context, camera, w, px, py, pz);

        PartyWaypoint.render3D(context, camera, tickDelta);
    }

    private static void renderFloatingText2D(DrawContext ctx, Camera camera, GpsWaypoint w, double px, double py, double pz) {
        Vec3d actualPos = new Vec3d(w.x, w.y + 0.3, w.z);
        double dx = actualPos.x - px;
        double dy = actualPos.y - py;
        double dz = actualPos.z - pz;
        double actualDistance = Math.sqrt(dx*dx + dy*dy + dz*dz);

        Vec3d camPos   = camera.getPos();
        Vec3d toTarget = actualPos.subtract(camPos);
        Vec3d dir      = toTarget.lengthSquared() < 0.0001 ? new Vec3d(0, 0, 1) : toTarget.normalize();

        boolean farProxy = actualDistance > FAR_PROXY_START_DISTANCE;
        Vec3d proxyPos = farProxy ? camPos.add(dir.multiply(FAR_PROXY_RENDER_DISTANCE)) : actualPos;

        Vec3d screen = worldToScreen2D(proxyPos, camera);
        if (screen == null) return;

        String distStr = actualDistance >= 1000
                ? String.format(Locale.US, "%.1fкм", actualDistance / 1000.0)
                : String.format(Locale.US, "%.0fм", actualDistance);

        float scale = farProxy
                ? MathHelper.clamp(1.0f - (float)(actualDistance - FAR_PROXY_START_DISTANCE) / 600.0f, 0.6f, 1.0f)
                : MathHelper.clamp(1.0f - (float)(actualDistance / 180.0), 0.6f, 1.0f);

        float blink = w.blinkAlpha();

        draw3DFloatingText(ctx, w.name, distStr, (float)screen.x, (float)screen.y, scale, blink, w);
    }

    private static void draw3DFloatingText(DrawContext ctx, String name, String dist,
                                           float screenX, float screenY,
                                           float scale, float blink, GpsWaypoint w) {
        int accent = w.color; // Цвет напрямую из метки

        float nameSize = 11f * scale;
        float distSize =  8.5f * scale;
        float gap      =  3f * scale;

        String cleanName = name.replaceAll("§[0-9a-fk-or]", "");

        float nameW = getFont().getWidth(cleanName, nameSize);
        float distW = getFont().getWidth(dist, distSize);

        float floatY = w.floatOffset * scale;

        float nameX = screenX - nameW / 2f;
        float nameY = screenY - nameSize - gap - distSize + floatY;
        float distX = screenX - distW / 2f;
        float distY = nameY + nameSize + gap;

        int baseAlpha = MathHelper.clamp((int)(255 * blink * w.floatAlpha), 0, 255);
        if (baseAlpha < 8) return;

        int shadowA = MathHelper.clamp((int)(baseAlpha * 0.55f), 0, 180);

        getFont().draw(ctx.getMatrices(), cleanName,
                nameX + 1f, nameY + 1f, nameSize, (shadowA << 24) | 0x000000);
        getFont().draw(ctx.getMatrices(), dist,
                distX + 1f, distY + 1f, distSize, (shadowA << 24) | 0x000000);

        getFont().draw(ctx.getMatrices(), cleanName,
                nameX, nameY, nameSize, (baseAlpha << 24) | 0xFFFFFF);
        getFont().draw(ctx.getMatrices(), dist,
                distX, distY, distSize, (baseAlpha << 24) | (accent & 0xFFFFFF));

        float arrowSize = 12f * scale;
        float arrowX = screenX;
        float arrowY = distY + distSize + gap + (arrowSize / 2f) + floatY;

        float ar = ((accent >> 16) & 0xFF) / 255f;
        float ag = ((accent >>  8) & 0xFF) / 255f;
        float ab = ( accent        & 0xFF) / 255f;
        float arrowAlpha = MathHelper.clamp(blink * w.floatAlpha, 0f, 1f);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(arrowX, arrowY, 0f);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
        ctx.getMatrices().translate(-arrowSize / 2f, -arrowSize / 2f, 0f);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(ar, ag, ab, arrowAlpha);
        ctx.drawTexture(RenderLayer::getGuiTextured, ARROW_TEXTURE, 0, 0, 0f, 0f, (int)arrowSize, (int)arrowSize, (int)arrowSize, (int)arrowSize);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ctx.getMatrices().pop();
    }

    // =========================================================================
    //  3D мировой рендер (ОПТИМИЗИРОВАННЫЙ MULTI-LAYER БАТЧИНГ)
    // =========================================================================

    public static void render3D(MatrixStack matrices, Camera camera, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        if (!ClientData.moduleStates.getOrDefault("GPS", false)) return;
        if (!ClientData.moduleStates.getOrDefault("GPS Show 3D Marker", true)) return;

        double px = MathHelper.lerp(tickDelta, mc.player.prevX, mc.player.getX());
        double py = MathHelper.lerp(tickDelta, mc.player.prevY, mc.player.getY());
        double pz = MathHelper.lerp(tickDelta, mc.player.prevZ, mc.player.getZ());

        for (GpsWaypoint w : waypoints) {
            double actualDistance = Math.sqrt(Math.pow(w.x - px, 2) + Math.pow(w.y - py, 2) + Math.pow(w.z - pz, 2));
            if (actualDistance < 80.0) {
                drawBatchedFloorRing3D(matrices, camera, w, w.blinkAlpha());
            }
        }
    }

    private static void drawBatchedFloorRing3D(MatrixStack matrices, Camera camera, GpsWaypoint w, float blink) {
        if (mc.world == null) return;

        int accent = w.color & 0xFFFFFF; // Цвет напрямую из метки
        float r = ((accent >> 16) & 0xFF) / 255f;
        float g = ((accent >>  8) & 0xFF) / 255f;
        float b = ( accent        & 0xFF) / 255f;

        float pulse = (float) (Math.sin(w.ringPhase * Math.PI * 2) * 0.5f + 0.5f);
        float radius = 0.8f + pulse * 0.6f;

        float alpha = MathHelper.clamp(w.floatAlpha * blink * (0.3f + pulse * 0.7f), 0f, 1f);
        if (alpha < 0.03f) return;

        double floorY = Math.floor(w.y) + 0.02;

        Vec3d camPos = camera.getPos();
        float relX = (float)(w.x - camPos.x);
        float relY = (float)(floorY - camPos.y);
        float relZ = (float)(w.z - camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.setShaderTexture(0, RING_TEX);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR_TEX_LIGHTMAP);

        matrices.push();
        matrices.translate(relX, relY, relZ);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        Tessellator tess = Tessellator.getInstance();

        // --- Слой 1: аура ---
        BufferBuilder buf1 = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
        appendGlowQuad(buf1, mat, radius * 1.8f, r, g, b, alpha * 0.15f);
        BufferRenderer.drawWithGlobalProgram(buf1.end());

        // --- Слой 2: основное кольцо ---
        BufferBuilder buf2 = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
        appendGlowQuad(buf2, mat, radius, r, g, b, alpha);
        BufferRenderer.drawWithGlobalProgram(buf2.end());

        // --- Слой 3: ядро ---
        BufferBuilder buf3 = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
        appendGlowQuad(buf3, mat, radius * 0.45f, r, g, b, Math.min(1f, alpha * 2f));
        BufferRenderer.drawWithGlobalProgram(buf3.end());

        matrices.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void appendGlowQuad(BufferBuilder buf, Matrix4f mat, float radius, float r, float g, float b, float a) {
        int cR = MathHelper.clamp((int)(r * 255), 0, 255);
        int cG = MathHelper.clamp((int)(g * 255), 0, 255);
        int cB = MathHelper.clamp((int)(b * 255), 0, 255);
        int cA = MathHelper.clamp((int)(a * 255), 0, 255);

        float[] xs = { -radius, -radius,  radius,  radius };
        float[] zs = { -radius,  radius,  radius, -radius };
        float[] us = {  0f,       0f,      1f,      1f    };
        float[] vs = {  0f,       1f,      1f,      0f    };

        for (int i = 0; i < 4; i++) {
            buf.vertex(mat, xs[i], 0f, zs[i])
                    .color(cR, cG, cB, cA)
                    .texture(us[i], vs[i])
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }

    // =========================================================================
    //  Projection
    // =========================================================================

    private static Vec3d worldToScreen2D(Vec3d worldPos, Camera camera) {
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

        float pad = 60f;
        sx = MathHelper.clamp(sx, pad, guiW - pad);
        sy = MathHelper.clamp(sy, pad, guiH - pad);

        return new Vec3d(sx, sy, 0);
    }
}