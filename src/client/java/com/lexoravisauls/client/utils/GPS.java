package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.TriState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPS {

    // =========================================================================
    //  Waypoint
    // =========================================================================

    public static final class GpsWaypoint {
        public String name; // Редактируется из GpsEditorScreen
        public double x, y, z;
        public int color;
        public int iconBackgroundIndex;

        public final long createdAt = System.currentTimeMillis();
        public boolean fromCallout = false;

        public float floatOffset = -45f;
        public float floatAlpha  = 0f;
        public float screenAlpha = 0f;

        public GpsWaypoint(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.iconBackgroundIndex = detectIconIndex(name);
            this.color = (this.iconBackgroundIndex >= 0 && this.iconBackgroundIndex < MARKER_COLORS.length)
                    ? MARKER_COLORS[this.iconBackgroundIndex]
                    : getAccentColor(name);
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

    public static final Identifier[] MARKER_TEXTURES = {
            Identifier.of("lexoravisauls", "textures/gui/pin.png"),        // 0: Точка
            Identifier.of("lexoravisauls", "textures/gui/beacon.png"),     // 1: Маяк
            Identifier.of("lexoravisauls", "textures/gui/meteor.png"),     // 2: Метеорит
            Identifier.of("lexoravisauls", "textures/gui/volcano.png"),    // 3: Вулкан
            Identifier.of("lexoravisauls", "textures/gui/slaughter.png"),  // 4: Резня
            Identifier.of("lexoravisauls", "textures/gui/chest.png"),      // 5: Сундук
            Identifier.of("lexoravisauls", "textures/gui/help.png"),       // 6: Хелпа
            Identifier.of("lexoravisauls", "textures/gui/target.png"),     // 7: Цель
            Identifier.of("lexoravisauls", "textures/gui/home.png"),       // 8: База
            Identifier.of("lexoravisauls", "textures/gui/star.png")        // 9: Звезда
    };

    public static final String[] MARKER_NAMES = {
            "Точка", "Маяк", "Метеорит", "Вулкан", "Резня", "Сундук", "Хелпа", "Цель", "База", "Звезда"
    };

    public static final int[] MARKER_COLORS = {
            0xFF10B981, // 0: Точка (Emerald)
            0xFF8B5CF6, // 1: Маяк (Electric Purple)
            0xFFF59E0B, // 2: Метеорит (Amber)
            0xFFEF4444, // 3: Вулкан (Fiery Red)
            0xFFDC2626, // 4: Резня (Blood Crimson)
            0xFFEAB308, // 5: Сундук (Gold)
            0xFFF43F5E, // 6: Хелпа (Neon Rose)
            0xFF06B6D4, // 7: Цель (Electric Cyan)
            0xFF3B82F6, // 8: База (Sky Blue)
            0xFFFBBF24  // 9: Звезда (Golden Yellow)
    };

    /** Обратная совместимость с редактором */
    public static final Identifier[] ICON_BACKGROUND_TEXTURES = MARKER_TEXTURES;

    private static final Identifier ARROW_TEX  = Identifier.of("lexoravisauls", "textures/gui/gps_arrow.png");
    private static final Identifier CIRCLE_TEX = Identifier.of("lexoravisauls", "textures/gui/jump_circles.png");

    public static int detectIconIndex(String name) {
        if (name == null) return 0;
        String l = name.toLowerCase(Locale.ROOT);
        if (l.contains("маяк")) return 1;
        if (l.contains("метеор")) return 2;
        if (l.contains("вулкан")) return 3;
        if (l.contains("резня") || l.contains("адск") || l.contains("портал")) return 4;
        if (l.contains("сундук") || l.contains("смерти") || l.contains("аирдроп")) return 5;
        if (l.contains("хелп") || l.contains("help") || l.contains("callout") || l.contains("sos") || l.contains("спасите")) return 6;
        if (l.contains("цель") || l.contains("target") || l.contains("пвп") || l.contains("pvp") || l.contains("килл")) return 7;
        if (l.contains("дом") || l.contains("база") || l.contains("home") || l.contains("base") || l.contains("хата")) return 8;
        if (l.contains("звезд") || l.contains("star") || l.contains("топ") || l.contains("vip")) return 9;
        return 0;
    }

    public static int getAccentColor(String name) {
        int idx = detectIconIndex(name);
        if (idx >= 0 && idx < MARKER_COLORS.length) {
            return MARKER_COLORS[idx];
        }
        return 0xFF10B981;
    }

    // =========================================================================
    //  Smooth (bilinear + mipmap) icon rendering
    // =========================================================================

    private static final Set<Identifier> mipmapChecked = new HashSet<>();

    /**
     * Custom RenderLayer with bilinear filtering + mipmaps enabled.
     * Replaces RenderLayer::getGuiTextured everywhere GPS marker icons are drawn.
     */
    public static RenderLayer getSmoothGuiTextured(Identifier tex) {
        return RenderLayer.getGuiTextured(tex);
    }

    public static void ensureLinearFilter(Identifier tex) {
        if (tex == null || mc == null) return;
        try {
            var t = mc.getTextureManager().getTexture(tex);
            if (t != null) {
                t.setFilter(true, true);
                if (mipmapChecked.add(tex)) {
                    t.bindTexture();
                    GL30.glGenerateMipmap(GL30.GL_TEXTURE_2D);
                }
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    //  State
    // =========================================================================

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Сделали публичным и переименовали в waypoints для связи с ModernClickGui
    public static final List<GpsWaypoint> waypoints = new CopyOnWriteArrayList<>();

    private static String pendingEventName     = null;
    private static long   pendingEventExpireAt = 0L;

    // Beyond this distance, the panel is drawn at a fixed close proxy point
    // along the camera->waypoint ray instead of at the true world position —
    // otherwise a very far waypoint projects to a tiny/degenerate screen
    // point (or behind the far plane) instead of a stable on-screen panel.
    private static final double FAR_PROXY_START_DISTANCE  = 95.0;
    private static final double FAR_PROXY_RENDER_DISTANCE = 38.0;

    // Panel geometry, all in screen px (pre-GUI-scale, same space DrawContext/
    // RoundedRectShader already draw in elsewhere in this file).
    private static final float PANEL_HEIGHT        = 22f;
    private static final float PANEL_ICON_SIZE      = 15f;
    private static final float PANEL_PAD_X          = 7f;
    private static final float PANEL_PAD_Y          = 3.5f;
    private static final float PANEL_ICON_TEXT_GAP  = 7f;
    private static final float PANEL_NAME_SIZE      = 8.5f;
    private static final float PANEL_DIST_SIZE      = 7.5f;
    private static final float PANEL_TEXT_LINE_GAP  = 1.5f;
    private static final float PANEL_BG_ALPHA       = 0.78f;

    // Longest a name is allowed to make the text column at scale==1 before it
    // gets ellipsized (see ellipsize(...)) — keeps one very long event name
    // from stretching the panel across a large chunk of the screen. Distance
    // strings ("123м"/"1.2км") are always short so this only ever clips name.
    private static final float PANEL_MAX_TEXT_WIDTH = 130f;
    // NOTE: PANEL_RADIUS is intentionally gone as a constant — the panel's
    // corner radius is now always half of the panel's ACTUAL height (computed
    // per-waypoint below), not a fixed value derived from the base
    // PANEL_HEIGHT. A fixed radius stopped looking "fully rounded" whenever a
    // tall name+distance text block pushed the real panel height past the
    // base constant.

    // =========================================================================
    //  Patterns
    // =========================================================================
    // NOTE: the old ".gps set/clear/list" chat-message patterns are gone — that
    // whole family of manually-typed commands now lives in GpsCommand as a real
    // "/gps" slash command instead (see handleLocalCommand's javadoc below for
    // why). The patterns below are UNRELATED: they belong to the auto-GPS
    // feature that scans SERVER broadcast messages for event names/coords, and
    // are untouched.

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

    /**
     * @deprecated The manually-typed ".gps set/clear/list" chat commands this
     * used to parse via regex are replaced by the real "/gps" Brigadier
     * command (see GpsCommand) — a raw chat-message interceptor can never give
     * tab-completion, which was the whole point of the move. Kept as a no-op
     * (always "not handled", i.e. the message just sends normally) only in
     * case something outside these files still calls this before sending
     * chat; find and remove that call site, then delete this method too.
     */
    @Deprecated
    public static boolean handleLocalCommand(String rawMessage) {
        return false;
    }

    /** Package-private so GpsCommand's "/gps list" can call straight into it. */
    static void printList() {
        if (mc.player == null) return;
        if (waypoints.isEmpty()) {
            mc.player.sendMessage(Text.literal("§7Нет активных GPS метки"), false);
            return;
        }
        mc.player.sendMessage(Text.literal("§6§lGPS метки:"), false);
        for (GpsWaypoint w : waypoints) {
            double dist = Math.sqrt(Math.pow(w.x - mc.player.getX(), 2) + Math.pow(w.z - mc.player.getZ(), 2));
            MutableText del = Text.literal("§c[x]").styled(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/gps clear " + w.name))
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
    //  Tick — регистрировать в ClientModInitializer через ClientTickEvents
    // =========================================================================

    public static void tick() {
        waypoints.removeIf(GpsWaypoint::calloutExpired);

        for (GpsWaypoint w : waypoints) {
            w.floatOffset += (0f - w.floatOffset) * 0.04f;
            w.floatAlpha  += (1f - w.floatAlpha)  * 0.04f;
        }
    }

    // =========================================================================
    //  Waypoint panels — THE marker visual. Rounded pill: icon left, name +
    //  distance stacked right. No arrow, no floor ring, no separate far/near
    //  text style — this single method replaces every previous visual
    //  (drawBatchedFloorRing3D, render3D's billboard quads, and
    //  render3DAsHud/renderFloatingText2D/draw3DFloatingText) entirely.
    // =========================================================================

    /**
     * Call this from your HUD render hook (same call site the old
     * render3DAsHud used to occupy) — NOT from a WorldRenderEvents hook. Even
     * though each waypoint panel represents a position in the 3D world, the
     * panel itself is 2D screen content (RoundedRectShader draws through
     * DrawContext, not a MatrixStack billboard) — projecting the world
     * position to a screen point first and drawing a flat rounded pill there
     * is what RoundedRectShader is built for, and matches how
     * PartyWaypoint.render3D already draws its own pill/label in this same
     * codebase.
     * <p>
     * Unlike the old render3DAsHud, this does NOT also call
     * PartyWaypoint.render3D(...) — that's now a separate call site in
     * LexoravisaulsClient.java, right next to this one, so the two waypoint
     * systems (GPS vs. party) stay independent instead of one silently
     * triggering the other.
     */
    public static void renderWaypointPanels(DrawContext context, Camera camera, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        if (waypoints.isEmpty()) return;

        boolean gpsEnabled = ClientData.moduleStates.getOrDefault("GPS", false);

        double px = MathHelper.lerp(tickDelta, mc.player.prevX, mc.player.getX());
        double py = MathHelper.lerp(tickDelta, mc.player.prevY, mc.player.getY());
        double pz = MathHelper.lerp(tickDelta, mc.player.prevZ, mc.player.getZ());

        for (GpsWaypoint w : waypoints) {
            if (!w.fromCallout && !gpsEnabled) continue;
            drawWaypointPanel(context, camera, w, px, py, pz);
        }
    }

    private static void drawWaypointPanel(DrawContext ctx, Camera camera, GpsWaypoint w,
                                          double px, double py, double pz) {
        Vec3d actualPos = new Vec3d(w.x, w.y + 0.3, w.z);
        double dx = actualPos.x - px;
        double dy = actualPos.y - py;
        double dz = actualPos.z - pz;
        double actualDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        Vec3d camPos   = camera.getPos();
        Vec3d toTarget = actualPos.subtract(camPos);
        Vec3d dir      = toTarget.lengthSquared() < 0.0001 ? new Vec3d(0, 0, 1) : toTarget.normalize();

        boolean farProxy = actualDistance > FAR_PROXY_START_DISTANCE;
        Vec3d proxyPos = farProxy ? camPos.add(dir.multiply(FAR_PROXY_RENDER_DISTANCE)) : actualPos;

        float blink = w.blinkAlpha();
        float alpha = MathHelper.clamp(blink * w.floatAlpha, 0f, 1f);
        if (alpha < 0.02f) return;

        // Camera local coordinates
        Vector3f rel = new Vector3f(
                (float)(proxyPos.x - camPos.x),
                (float)(proxyPos.y - camPos.y),
                (float)(proxyPos.z - camPos.z));
        new Quaternionf(camera.getRotation()).conjugate().transform(rel);

        float guiW   = mc.getWindow().getScaledWidth();
        float guiH   = mc.getWindow().getScaledHeight();
        float aspect = guiW / guiH;
        float tan    = (float)Math.tan(Math.toRadians(mc.options.getFov().getValue()) * 0.5);

        boolean inFront = rel.z() < -0.05f;
        float ndcX = inFront ? (rel.x() / (-rel.z() * tan * aspect)) : 0f;
        float ndcY = inFront ? (rel.y() / (-rel.z() * tan)) : 0f;

        int accent = w.color & 0xFFFFFF;
        int iconIdx = (w.iconBackgroundIndex >= 0 && w.iconBackgroundIndex < MARKER_TEXTURES.length)
                ? w.iconBackgroundIndex : 0;
        Identifier iconTex = MARKER_TEXTURES[iconIdx];

        String distStr = actualDistance >= 1000
                ? String.format(Locale.US, "%.1fкм", actualDistance / 1000.0)
                : String.format(Locale.US, "%.0fм", actualDistance);

        // Edge-fade: compute spatial visibility gradient
        float targetAlpha;
        if (!inFront) {
            targetAlpha = 0f;
        } else {
            float absX = Math.abs(ndcX);
            float absY = Math.abs(ndcY);
            // Safe zone: full opacity. Fade zone: linear falloff to 0 at border.
            float fadeX = absX < 0.76f ? 1f : absX > 0.90f ? 0f : (0.90f - absX) / (0.90f - 0.76f);
            float fadeY = absY < 0.72f ? 1f : absY > 0.86f ? 0f : (0.86f - absY) / (0.86f - 0.72f);
            targetAlpha = Math.min(fadeX, fadeY);
        }

        // Temporal lerp for smooth fade-in / fade-out
        w.screenAlpha += (targetAlpha - w.screenAlpha) * 0.18f;
        if (w.screenAlpha < 0.005f) { w.screenAlpha = 0f; return; }
        if (w.screenAlpha > 0.995f) w.screenAlpha = 1f;

        // Final alpha incorporates appearance fade, blink, and screen edge fade
        float finalAlpha = alpha * w.screenAlpha;
        if (finalAlpha < 0.01f) return;

        float sx = (ndcX * 0.5f + 0.5f) * guiW;
        float sy = (0.5f - ndcY * 0.5f) * guiH;
        renderOnScreenPanel(ctx, w, sx, sy, actualDistance, dy, distStr, accent, iconTex, finalAlpha, farProxy);
    }

    private static void renderOnScreenPanel(DrawContext ctx, GpsWaypoint w, float sx, float sy,
                                           double dist, double dy, String distStr, int accent,
                                           Identifier iconTex, float alpha, boolean farProxy) {
        float userScale = LexoraGui.numSettings.getOrDefault("GPS 3D Marker Size", 1.0f);
        boolean showDistance = ClientData.moduleStates.getOrDefault("GPS Show Distance", true);

        float scale = (farProxy
                ? MathHelper.clamp(1.0f - (float) (dist - FAR_PROXY_START_DISTANCE) / 600.0f, 0.68f, 1.0f)
                : MathHelper.clamp(1.0f - (float) (dist / 180.0), 0.72f, 1.0f)) * userScale;

        String rawName = w.name.replaceAll("§[0-9a-fk-or]", "");
        float maxNameW = PANEL_MAX_TEXT_WIDTH * scale;
        float nameSize = 8.5f * scale;
        float distSize = 7.5f * scale;
        String cleanName = ellipsize(rawName, nameSize, maxNameW);

        // Height arrow indicator if vertical delta is noticeable
        String heightStr = "";
        if (dy > 4.0) heightStr = " ▲" + (int)Math.abs(dy);
        else if (dy < -4.0) heightStr = " ▼" + (int)Math.abs(dy);
        String fullDist = showDistance ? (distStr + heightStr) : "";

        float nameW = getFont().getWidth(cleanName, nameSize);
        float distW = showDistance ? getFont().getWidth(fullDist, distSize) : 0f;
        float textW = Math.max(nameW, distW);

        float iconBoxSize = 18f * scale;
        float padX = 6.0f * scale;
        float padY = 4.0f * scale;
        float gap  = 5.5f * scale;

        float panelH = showDistance ? Math.max(24f * scale, iconBoxSize + padY * 2f) : Math.max(18f * scale, iconBoxSize + padY * 1.5f);
        float panelW = padX + iconBoxSize + gap + textW + padX + 2f * scale;
        float radius = 6f * scale;

        float floatY = w.floatOffset * scale;
        float panelX = sx - panelW / 2f;
        float panelY = sy - panelH / 2f + floatY;

        int alphaI = MathHelper.clamp((int) (255 * alpha), 0, 255);
        if (alphaI < 6) return;

        // Clean sleek glass vertical gradient background (no darkened outline or shadow)
        int topBg = (((int) (225 * alpha)) << 24) | 0x1A1926;
        int botBg = (((int) (235 * alpha)) << 24) | 0x100F18;
        RoundedRectShader.drawVerticalGradient(ctx, panelX, panelY, panelW, panelH, radius, topBg, botBg);

        // Subtle accent outline border (clean and bright, not dark)
        int borderA = (int) ((50 + (w.fromCallout ? 30 : 0)) * alpha);
        int borderCol = (borderA << 24) | (accent & 0xFFFFFF);
        RoundedRectShader.drawOutline(ctx, panelX, panelY, panelW, panelH, radius, 0.5f, borderCol);

        // Icon (clean direct rendering with bilinear filtering — no dark box container or rim)
        float iconX = panelX + padX;
        float iconY = panelY + (panelH - iconBoxSize) / 2f;

        ensureLinearFilter(iconTex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(GPS::getSmoothGuiTextured, iconTex,
                Math.round(iconX), Math.round(iconY), 0f, 0f,
                Math.round(iconBoxSize), Math.round(iconBoxSize),
                Math.round(iconBoxSize), Math.round(iconBoxSize));
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Typography on the right
        float textX = iconX + iconBoxSize + gap;
        if (showDistance) {
            float textBlockH = nameSize + 2.0f * scale + distSize;
            float nameY = panelY + (panelH - textBlockH) / 2f;
            float distY = nameY + nameSize + 2.0f * scale;

            int shadowA = MathHelper.clamp((int) (alphaI * 0.6f), 0, 180);
            getFont().draw(ctx.getMatrices(), cleanName, textX + 0.6f, nameY + 0.6f, nameSize, (shadowA << 24) | 0x000000);
            getFont().draw(ctx.getMatrices(), cleanName, textX, nameY, nameSize, (alphaI << 24) | 0xFFEDEDF2);

            getFont().draw(ctx.getMatrices(), fullDist, textX + 0.6f, distY + 0.6f, distSize, (shadowA << 24) | 0x000000);
            getFont().draw(ctx.getMatrices(), fullDist, textX, distY, distSize, (alphaI << 24) | (accent & 0xFFFFFF));
        } else {
            float nameY = panelY + (panelH - nameSize) / 2f;
            int shadowA = MathHelper.clamp((int) (alphaI * 0.6f), 0, 180);
            getFont().draw(ctx.getMatrices(), cleanName, textX + 0.6f, nameY + 0.6f, nameSize, (shadowA << 24) | 0x000000);
            getFont().draw(ctx.getMatrices(), cleanName, textX, nameY, nameSize, (alphaI << 24) | 0xFFEDEDF2);
        }
    }



    /**
     * 3D in-world rendering (3D свечение отключено по запросу пользователя)
     */
    public static void render3D(MatrixStack matrices, Camera camera, float tickDelta) {
        // 3D свечение и лучи в мире отключены
    }

    private static String ellipsize(String text, float fontSize, float maxWidth) {
        if (getFont().getWidth(text, fontSize) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        for (int len = text.length() - 1; len > 0; len--) {
            String candidate = text.substring(0, len) + ellipsis;
            if (getFont().getWidth(candidate, fontSize) <= maxWidth) {
                return candidate;
            }
        }
        return ellipsis;
    }
}