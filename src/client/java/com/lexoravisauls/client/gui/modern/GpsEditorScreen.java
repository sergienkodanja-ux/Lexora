package com.lexoravisauls.client.gui.modern;

import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.events.ShatterShader;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.utils.ConfigManager;
import com.lexoravisauls.client.utils.GPS;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Standalone GPS waypoint editor — opened via the "Открыть редактор" button in
 * ModernClickGui's GPS settings (see ModernClickGui#drawGPSSettings), which
 * calls client.setScreen(new GpsEditorScreen()). That call replaces the
 * current screen, so ModernClickGui closes itself the normal Minecraft way;
 * this class does not need to (and does not) reach back into ModernClickGui
 * to do so.
 * <p>
 * Layout: a scrollable list of every waypoint down the left side, with
 * pagination centered under THAT list (not the whole screen). Selecting a
 * waypoint fills the entire right-hand area with its editor: name, X/Y/Z
 * (all editable text fields), a read-only "created at" timestamp, and a
 * 5x2 grid of the 10 baked marker-icon variants at the bottom, with the
 * waypoint's current icon outlined — click any tile to switch it.
 * <p>
 * Visual language matches ModernClickGui: RoundedRectShader panels/rows,
 * the same muted palette constants, and an MsdfFont instance for text
 * (a private one here — ModernClickGui.SFUI is that class's own public
 * static field, not shared infrastructure, so this screen owns its font
 * instance the same way GPS.java and PartyWaypoint.java already each own
 * theirs rather than reaching into ModernClickGui for it).
 */
public class GpsEditorScreen extends Screen {

    private static final MsdfFont FONT = new MsdfFont(
            Identifier.of("lexoravisauls", "msdf_data/font.png"),
            Identifier.of("lexoravisauls", "msdf_data/font.json")
    );

    // ── Palette — copied from ModernClickGui's constants so this screen reads
    //    as the same product, not a bolted-on separate style. ──────────────
    private static final int PANEL_COLOR   = 0x60141414;
    private static final int ELEM_COLOR    = 0x80222222;
    private static final int TEXT_COLOR    = 0xFFEDEDED;
    private static final int SUBTEXT_COLOR = 0xFF7A7A7A;
    private static final int BG_COLOR      = 0xCC050505;

    // ── Geometry ─────────────────────────────────────────────────────────
    // Fixed window size, centered on screen — same pattern ModernClickGui
    // uses (its GUI_W/GUI_H), rather than the old "stretch to fill the
    // screen minus MARGIN" layout. WINDOW_W/H are the outer window; MARGIN
    // is the padding between the window's edge and its content (list panel,
    // editor panel, close button) — same role MARGIN always had, just now
    // measured from the window's own edge instead of the screen's edge.
    private static final int WINDOW_W      = 460;
    private static final int WINDOW_H      = 300;
    private static final int LIST_W        = 130;
    private static final int ROW_H         = 34;
    private static final int MARGIN        = 12;
    private static final int ICON_TILE     = 28;
    private static final int ICON_GAP      = 6;
    private static final int ICON_COLS     = 5;
    private static final int ICON_ROWS     = 2;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    /**
     * Same 10 baked icon textures GPS.java draws with — this screen doesn't
     * redefine them, it reads the identical array so "which icon is this"
     * always means the same thing in both places. Package-private access
     * would be cleaner but GPS.java's array is private; duplicating the 10
     * Identifiers here is the pragматic option without changing GPS.java's
     * encapsulation just for this screen to peek at them.
     */
    private static final Identifier[] ICON_TEXTURES = GPS.MARKER_TEXTURES;

    /** White-on-transparent glyph icons for the close button and the "Применить" button — tinted at draw time via drawIcon's own alpha, same white-tintable technique the marker icons and the rest of the project's PNG glyphs (accept.png/guiswap.png in ModernClickGui) already use. */
    private static final Identifier ICON_CLOSE_TEX = Identifier.of("lexoravisauls", "textures/gui/icon_close.png");
    private static final Identifier ICON_APPLY_TEX = Identifier.of("lexoravisauls", "textures/gui/icon_apply.png");

    /**
     * Plain solid-white texture — ShatterShader has no untextured fill mode
     * (unlike RoundedRectShader.draw's solid-color path), so a flat white
     * stand-in texture, tinted via drawShattered's colorTint argument, is
     * how a solid-colour shattered background gets drawn. This does NOT
     * carry any real panel content (text/icons) — see ShatterShader's own
     * class javadoc for why (no render-to-texture in this project yet); the
     * window background shattering is real, what's ON that background isn't
     * shattered along with it yet.
     */
    private static final Identifier SHATTER_FILL_TEX = Identifier.of("lexoravisauls", "textures/gui/white_pixel.png");

    // ── State ────────────────────────────────────────────────────────────
    private GPS.GpsWaypoint selected = null;
    /** Continuous scroll offset (px) — lerped toward targetScroll every frame, same pattern ModernClickGui's scrolls[]/targetScrolls[] uses. Replaces the old page-based pagination entirely. */
    private float scroll = 0f;
    private float targetScroll = 0f;

    // Editable fields, loaded from `selected` when it changes (see selectWaypoint).
    private String editName = "";
    private String editX = "";
    private String editY = "";
    private String editZ = "";
    /** 0 = none, 1 = name, 2 = x, 3 = y, 4 = z — same single-index-focus pattern ModernClickGui uses for its GPS add-row. */
    private int focusedField = 0;

    private float openAnim = 0f;
    private boolean closing = false;
    /** Separate, deliberately SLOWER progress value driving only the shader's
     *  shatter geometry — kept independent from openAnim (which still drives
     *  everything else: content alpha, list rows, editor fields) because the
     *  shatter itself was too fast at openAnim's normal lerp rate, but
     *  slowing openAnim down would have slowed the text/list fade-in along
     *  with it, which wasn't the ask. */
    private float shatterProgress = 0f;
    /** Rolled once per screen-open in init(), NOT per-frame — a stable seed
     *  for the whole time this screen is open, so the shatter pattern doesn't
     *  visibly shift every frame; a fresh pattern each time the screen opens
     *  instead of always the exact same crack layout. */
    private float shatterSeed = 0f;
    /** Continuously-growing timer (seconds), NOT lerped toward a target like
     *  every other animation field on this screen — it starts at 0 the moment
     *  shatterProgress FIRST reaches ~1.0 (see the render() tick logic) and
     *  keeps climbing every frame after that, driving the post-assembly drip
     *  sequence (glow -> droplet -> falls down -> merges with other droplets
     *  -> spreads across the finished panel as a glow wash). This is
     *  deliberately independent of shatterProgress/openAnim: those two
     *  finish at Progress==1 and stay there, but the drip sequence needs to
     *  keep animating for several more seconds AFTER assembly completes, so
     *  it needs its own clock that doesn't stop when the others do. */
    private float dripTime = 0f;
    /** Whether dripTime has started counting yet this screen-open — guards
     *  against re-triggering the drip sequence every frame after assembly
     *  finishes; it should fire exactly once per screen-open. */
    private boolean dripStarted = false;
    /** After this many seconds of dripTime, the whole drip/merge/spread
     *  sequence is considered fully finished and dripTime stops advancing —
     *  purely to stop paying the shader's per-frame cost for an effect that's
     *  no longer visually doing anything by that point. */
    private static final float DRIP_TOTAL_SECONDS = 3.2f;

    private final Map<String, int[]> clickBounds = new HashMap<>();

    public GpsEditorScreen() {
        super(Text.literal("GPS Editor"));
    }

    @Override
    protected void init() {
        super.init();
        this.closing = false;
        this.openAnim = 0f;
        this.shatterProgress = 0f;
        this.shatterSeed = (float) (Math.random() * 1000.0);
        this.dripTime = 0f;
        this.dripStarted = false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (!this.closing) {
            this.closing = true;
        }
    }

    private void selectWaypoint(GPS.GpsWaypoint waypoint) {
        selected = waypoint;
        focusedField = 0;
        if (waypoint == null) {
            editName = "";
            editX = "";
            editY = "";
            editZ = "";
            return;
        }
        editName = waypoint.name;
        editX = formatCoord(waypoint.x);
        editY = formatCoord(waypoint.y);
        editZ = formatCoord(waypoint.z);
    }

    private static String formatCoord(double v) {
        // Whole-number waypoints (the overwhelming common case — most come from
        // /gps set or a player-standing-here callout) show as "123", not
        // "123.0" — matches how the rest of GPS.java already formats coordinates
        // (e.g. printList's "(int)w.x"). Falls back to one decimal place for the
        // rare waypoint that genuinely has a fractional Y.
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.US, "%.1f", v);
    }

    /** Parses a coordinate field; returns null (rather than throwing) on invalid input so the caller can no-op instead of crashing on a stray keystroke mid-edit. */
    private static Double parseCoord(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void commitEdits() {
        if (selected == null) return;
        String trimmedName = editName.trim();
        if (!trimmedName.isEmpty()) {
            selected.name = trimmedName;
        }
        Double x = parseCoord(editX);
        Double y = parseCoord(editY);
        Double z = parseCoord(editZ);
        if (x != null) selected.x = x;
        if (y != null) selected.y = y;
        if (z != null) selected.z = z;
        ConfigManager.saveConfig();
    }

    // =========================================================================
    //  RENDER
    // =========================================================================
    /** System time (nanos) at the last render() call — used only to compute
     *  a real elapsed-seconds delta for dripTime, since this file doesn't
     *  have a confirmed meaning for render()'s own `delta` parameter (partial
     *  ticks vs. seconds vs. something else) and dripTime's timing needs to
     *  be frame-rate independent, not tied to a value of unconfirmed units. */
    private long lastFrameTimeNanos = 0L;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float elapsedSeconds = lastFrameTimeNanos == 0L ? 0f
                : clamp((now - lastFrameTimeNanos) / 1_000_000_000f, 0f, 0.25f); // clamp guards against a huge jump after e.g. a debugger pause or lag spike
        lastFrameTimeNanos = now;

        if (!closing) {
            openAnim += (1f - openAnim) * 0.15f;
            shatterProgress += (1f - shatterProgress) * 0.05f;

            // Drip sequence starts exactly once, the first time shatterProgress
            // is close enough to "assembled" to read as landed — 0.985 rather
            // than a stricter 0.999+ so it starts the instant assembly visually
            // finishes rather than waiting on the lerp's long asymptotic tail.
            if (!dripStarted && shatterProgress >= 0.985f) {
                dripStarted = true;
                dripTime = 0f;
            }
            if (dripStarted && dripTime < DRIP_TOTAL_SECONDS) {
                dripTime = Math.min(DRIP_TOTAL_SECONDS, dripTime + elapsedSeconds);
            }
        } else {
            openAnim += (0f - openAnim) * 0.12f;
            // ~1/3 the lerp rate of openAnim's own closing rate — the actual
            // "slow the shatter down 2-3x" ask. openAnim still gates when the
            // screen actually closes below, but that gate now waits on
            // shatterProgress too (see the condition below) specifically so
            // this slower shatter isn't cut off mid-animation the instant
            // openAnim itself reaches zero.
            shatterProgress += (0f - shatterProgress) * 0.04f;
            // Drip sequence doesn't run while closing — the shatter itself
            // takes over as the primary visual on close, and a closing screen
            // is torn down (setScreen(null)) as soon as it finishes anyway, so
            // there'd be no time left for a multi-second drip sequence to play.
            dripStarted = false;
            dripTime = 0f;
            if (openAnim <= 0.02f && shatterProgress <= 0.02f) {
                openAnim = 0f;
                shatterProgress = 0f;
                if (client != null) client.setScreen(null);
                return;
            }
        }
        openAnim = clamp(openAnim, 0f, 1f);
        shatterProgress = clamp(shatterProgress, 0f, 1f);
        float eased = smoothT(openAnim);

        scroll += (targetScroll - scroll) * 0.25f;

        clickBounds.clear();

        // Dim the WHOLE screen behind the window (same role BG_COLOR always
        // had), then draw the window itself as a fixed-size panel centered on
        // top of that dimmed backdrop.
        context.fill(0, 0, width, height, withAlpha(BG_COLOR, openAnim * 0.75f));

        // Window scale-in: computed as real geometry (winX/winY/winW/winH
        // shrink as actual numbers), NOT via context.getMatrices().scale(...).
        // RoundedRectShader/MsdfFont's own draw calls aren't guaranteed to
        // respect an active matrix scale without checking their source, which
        // isn't available here — plain arithmetic on the numbers passed to
        // them works regardless of how they draw internally, so that's what
        // drives the grow-in instead.
        float winScale = 0.90f + 0.10f * eased;
        int winW = Math.round(WINDOW_W * winScale);
        int winH = Math.round(WINDOW_H * winScale);
        int winX = (width - winW) / 2;
        int winY = (height - winH) / 2;

        // Window background drawn as a shattering solid colour instead of a
        // flat rounded rect. Driven by shatterProgress, NOT openAnim — the
        // shatter is intentionally slower than the rest of the screen's
        // open/close animation (see shatterProgress's own field javadoc for
        // why openAnim itself couldn't just be slowed down instead).
        //
        // Alpha passed here is a flat 1f, NOT shatterProgress, on purpose:
        // the shader's own shardAlpha/edgeAlpha already fully own the fade
        // in/out (verified: at progress==1, shardAlpha resolves to exactly
        // 1.0 for every shard regardless of its stagger, so full opacity at
        // rest is preserved). Also multiplying by a progress value here would
        // double up the fade — every shard would additionally dim as a whole
        // on top of its own geometric shatter-fade, muddying the "sharp
        // shards materializing" look into something duller.
        //
        // See SHATTER_FILL_TEX's javadoc: this shatters the background
        // colour only, not the text/icons drawn on top of it afterward —
        // those still fade in normally via their own openAnim-based alpha.
        //
        // dripTime drives the post-assembly glow/droplet/fall/merge/spread
        // sequence — see dripTime's own field javadoc for why it's a separate
        // continuously-growing clock rather than reusing shatterProgress.
        ShatterShader.drawShattered(context, SHATTER_FILL_TEX,
                winX, winY, winW, winH, 12f,
                0f, 0f, 1f, 1f, withAlpha(0xF0101010, 1f),
                shatterProgress, 8f, 260f, shatterSeed, dripTime);

        List<GPS.GpsWaypoint> waypoints = GPS.getWaypoints();

        int listX = winX + MARGIN;
        int listY = winY + MARGIN;
        int listH = winH - MARGIN * 2;
        drawList(context, waypoints, listX, listY, LIST_W, listH, mouseX, mouseY, eased);

        int editorX = listX + LIST_W + MARGIN;
        int editorY = winY + MARGIN;
        int editorW = winX + winW - MARGIN - editorX;
        int editorH = winH - MARGIN * 2;
        drawEditorPanel(context, editorX, editorY, editorW, editorH, mouseX, mouseY, eased);

        int closeSize = 18;
        int closeX = winX + winW - MARGIN - closeSize;
        int closeY = winY + MARGIN;
        drawCloseButton(context, closeX, closeY, closeSize, mouseX, mouseY);
    }

    private void drawCloseButton(DrawContext context, int x, int y, int size, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, size, size);
        RoundedRectShader.draw(context, x, y, size, size, 6f, withAlpha(hovered ? 0xFF3A1A1A : ELEM_COLOR, openAnim));
        int iconSize = Math.round(size * 0.5f);
        int iconInset = (size - iconSize) / 2;
        // Tinted reddish, same as the old text glyph's 0xFFFF6666, so the
        // "destructive/close" colour cue carries over rather than getting
        // lost when the glyph became a plain white PNG.
        drawIcon(context, ICON_CLOSE_TEX, x + iconInset, y + iconInset, iconSize, openAnim, 1f, 0.4f, 0.4f);
        clickBounds.put("close", new int[]{x, y, size, size});
    }

    private void drawList(DrawContext context, List<GPS.GpsWaypoint> waypoints, int x, int y, int w, int h, int mouseX, int mouseY, float eased) {
        RoundedRectShader.draw(context, x, y, w, h, 10f, withAlpha(PANEL_COLOR, openAnim));

        drawTextTiny(context, "МЕТКИ (" + waypoints.size() + ")", x + 10f, y + 10f, withAlpha(SUBTEXT_COLOR, openAnim));

        int rowsAreaY = y + 24;
        int rowsAreaH = h - 24 - 6;
        int rowsAreaBottom = rowsAreaY + rowsAreaH;

        float totalRowsH = waypoints.size() * ROW_H;
        float maxScroll = Math.max(0f, totalRowsH - rowsAreaH);
        targetScroll = clamp(targetScroll, 0f, maxScroll);
        scroll = clamp(scroll, 0f, maxScroll);

        if (waypoints.isEmpty()) {
            drawTextTiny(context, "Нет меток", x + 10f, rowsAreaY + 6f, withAlpha(SUBTEXT_COLOR, openAnim));
            return;
        }

        // Scissor is required now that rows scroll continuously — without it,
        // rows above/below the panel's own bounds would draw straight over
        // the panel header and the editor column next to it instead of being
        // clipped at the list panel's own edges.
        context.enableScissor(x, rowsAreaY, x + w, rowsAreaBottom);
        try {
            float rowY = rowsAreaY - scroll;
            for (int i = 0; i < waypoints.size(); i++) {
                float thisRowY = rowY;
                rowY += ROW_H;

                // Skip rows fully outside the visible scissor band — no point
                // computing/drawing hover state or text for something clipped away.
                if (thisRowY + ROW_H < rowsAreaY || thisRowY > rowsAreaBottom) continue;

                GPS.GpsWaypoint wp = waypoints.get(i);
                boolean isSelected = wp == selected;
                boolean isHovered = inside(mouseX, mouseY, x + 6, thisRowY, w - 12, ROW_H - 4);

                // Cascaded fade-in + rise on open: each row's own progress lags
                // slightly behind the previous one (capped so the last few rows
                // don't end up waiting an unreasonably long time on a long list),
                // giving the "list appears row by row" feel rather than a single
                // flat fade of the whole panel.
                float rowDelay = Math.min(i * 0.08f, 0.35f);
                float rowT = clamp((eased - rowDelay) / (1f - rowDelay), 0f, 1f);
                float rowAlpha = openAnim * rowT;
                if (rowAlpha < 0.02f) continue;
                float riseOffset = (1f - rowT) * 10f;
                float drawY = thisRowY + riseOffset;

                int rowBg = isSelected ? blendColors(ELEM_COLOR, wp.color, 0.35f) : (isHovered ? 0x90222222 : ELEM_COLOR);
                int drawYi = Math.round(drawY);
                RoundedRectShader.draw(context, x + 6, drawYi, w - 12, ROW_H - 4, 7f, withAlpha(rowBg, rowAlpha));

                Identifier iconTex = ICON_TEXTURES[wp.iconBackgroundIndex % ICON_TEXTURES.length];
                int iconSize = 18;
                int iconX = x + 12;
                float iconY = drawY + (ROW_H - 4 - iconSize) / 2f;
                drawIcon(context, iconTex, iconX, Math.round(iconY), iconSize, rowAlpha);

                String shownName = clipToWidth(wp.name, w - 12 - iconSize - 16f, 7.5f);
                drawText(context, shownName, iconX + iconSize + 8f, drawY + 4f, 7.5f, withAlpha(TEXT_COLOR, rowAlpha));

                String coordStr = "(" + (int) wp.x + ", " + (int) wp.z + ")";
                drawTextTiny(context, coordStr, iconX + iconSize + 8f, drawY + 15f, withAlpha(SUBTEXT_COLOR, rowAlpha));

                // Click bounds use the row's REST position (thisRowY), not the
                // animated drawY, so clicking is only ever a pixel or two off
                // during the opening animation instead of chasing a moving target.
                clickBounds.put("row:" + i, new int[]{x + 6, (int) thisRowY, w - 12, ROW_H - 4});
            }
        } finally {
            context.disableScissor();
        }
    }

    private void drawEditorPanel(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY, float eased) {
        RoundedRectShader.draw(context, x, y, w, h, 10f, withAlpha(PANEL_COLOR, openAnim));

        if (selected == null) {
            String hint = "Выбери метку слева";
            float hintW = getTextWidth(hint, 9f);
            drawText(context, hint, x + (w - hintW) / 2f, y + h / 2f - 5f, 9f, withAlpha(SUBTEXT_COLOR, openAnim));
            return;
        }

        // Whole editor content rises + fades in together on open — one shared
        // offset for the form (unlike the list's per-row cascade), since this
        // is a single connected block rather than independent list items.
        float riseOffset = (1f - eased) * 12f;
        int pad = 16;
        int cy = Math.round(y + pad + riseOffset);

        drawTextTiny(context, "НАЗВАНИЕ", x + pad, cy, withAlpha(SUBTEXT_COLOR, openAnim));
        cy += 12;
        drawEditField(context, editName, x + pad, cy, w - pad * 2, focusedField == 1, "name");
        cy += 26;

        drawTextTiny(context, "КООРДИНАТЫ", x + pad, cy, withAlpha(SUBTEXT_COLOR, openAnim));
        cy += 12;
        int coordFieldW = (w - pad * 2 - 8 * 2) / 3;
        drawEditField(context, editX, x + pad, cy, coordFieldW, focusedField == 2, "x");
        drawEditField(context, editY, x + pad + coordFieldW + 8, cy, coordFieldW, focusedField == 3, "y");
        drawEditField(context, editZ, x + pad + (coordFieldW + 8) * 2, cy, coordFieldW, focusedField == 4, "z");
        cy += 26;

        String createdLabel = "Установлена: " + DATE_FORMAT.format(new Date(selected.createdAt));
        drawTextTiny(context, createdLabel, x + pad, cy + 4, withAlpha(SUBTEXT_COLOR, openAnim));
        cy += 22;

        // Apply button — commits name/x/y/z edits into the actual GpsWaypoint.
        int applyW = 90;
        int applyH = 20;
        int applyX = x + pad;
        boolean applyHovered = inside(mouseX, mouseY, applyX, cy, applyW, applyH);
        RoundedRectShader.draw(context, applyX, cy, applyW, applyH, 6f, withAlpha(applyHovered ? 0x9022C55E : 0x7022C55E, openAnim));

        // Checkmark + label, centered as one unit rather than the label alone
        // — computing the combined width first keeps both pieces balanced
        // inside the button instead of the icon just being tacked onto one side.
        String applyLabel = "Применить";
        float applyLabelW = getTextWidth(applyLabel, 7.5f);
        int applyIconSize = 9;
        float applyIconGap = 4f;
        float applyGroupW = applyIconSize + applyIconGap + applyLabelW;
        float applyGroupX = applyX + (applyW - applyGroupW) / 2f;

        drawIcon(context, ICON_APPLY_TEX, Math.round(applyGroupX), cy + (applyH - applyIconSize) / 2, applyIconSize, openAnim);
        drawText(context, applyLabel, applyGroupX + applyIconSize + applyIconGap, cy + (applyH - 7.5f) / 2f, 7.5f, withAlpha(0xFFFFFFFF, openAnim));

        clickBounds.put("apply", new int[]{applyX, cy, applyW, applyH});
        cy += applyH + 18;

        drawTextTiny(context, "ИКОНКА", x + pad, cy, withAlpha(SUBTEXT_COLOR, openAnim));
        cy += 14;
        drawIconGrid(context, x + pad, cy, mouseX, mouseY);
    }

    private void drawEditField(DrawContext context, String value, int x, int y, int w, boolean focused, String fieldId) {
        int h = 18;
        RoundedRectShader.draw(context, x, y, w, h, 5f, withAlpha(focused ? 0xFF1E1E2E : ELEM_COLOR, openAnim));
        if (focused) {
            RoundedRectShader.draw(context, x - 1, y - 1, w + 2, h + 2, 6f, withAlpha(0xFF4C9EFF, 0.5f * openAnim));
        }
        String shown = value;
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) shown += "|";
        drawText(context, clipToWidth(shown, w - 8f, 7.5f), x + 5f, y + 5f, 7.5f, withAlpha(TEXT_COLOR, openAnim));
        clickBounds.put("field:" + fieldId, new int[]{x, y, w, h});
    }

    private void drawIconGrid(DrawContext context, int x, int y, int mouseX, int mouseY) {
        String hoveredName = null;
        int hoverX = 0, hoverY = 0;

        for (int i = 0; i < ICON_TEXTURES.length; i++) {
            int col = i % ICON_COLS;
            int row = i / ICON_COLS;
            int tx = x + col * (ICON_TILE + ICON_GAP);
            int ty = y + row * (ICON_TILE + ICON_GAP);

            boolean isCurrent = selected != null && selected.iconBackgroundIndex == i;
            boolean isHovered = inside(mouseX, mouseY, tx, ty, ICON_TILE, ICON_TILE);

            int accent = (i < GPS.MARKER_COLORS.length) ? GPS.MARKER_COLORS[i] : 0xFF4C9EFF;

            // Card background
            int tileBg = isCurrent ? 0xFF222234 : (isHovered ? 0xFF1C1C2A : 0xFF14141E);
            RoundedRectShader.draw(context, tx, ty, ICON_TILE, ICON_TILE, 5f, withAlpha(tileBg, openAnim));

            if (isCurrent) {
                RoundedRectShader.drawOutline(context, tx, ty, ICON_TILE, ICON_TILE, 5f, 1.2f, withAlpha(accent, openAnim));
            } else if (isHovered) {
                RoundedRectShader.drawOutline(context, tx, ty, ICON_TILE, ICON_TILE, 5f, 0.8f, withAlpha(0x60FFFFFF, openAnim));
                if (i < GPS.MARKER_NAMES.length) {
                    hoveredName = GPS.MARKER_NAMES[i];
                    hoverX = mouseX + 8;
                    hoverY = mouseY - 14;
                }
            }

            int pad = 3;
            drawIcon(context, ICON_TEXTURES[i], tx + pad, ty + pad, ICON_TILE - pad * 2, openAnim);
            clickBounds.put("icon:" + i, new int[]{tx, ty, ICON_TILE, ICON_TILE});
        }

        if (hoveredName != null) {
            float tw = getTextWidth(hoveredName, 7f);
            RoundedRectShader.draw(context, hoverX - 4, hoverY - 2, (int) tw + 8, 13, 4f, withAlpha(0xF0101018, openAnim));
            drawText(context, hoveredName, hoverX, hoverY + 2.5f, 7f, withAlpha(0xFFFFFFFF, openAnim));
        }
    }

    /**
     * Soft glow "halo" around a tile, standing in for the old single flat
     * RoundedRectShader square outline. No shader here does a real radial
     * falloff, so this fakes one the same way PartyWaypoint.drawCircleGlow
     * already does elsewhere in this project: several progressively larger,
     * progressively fainter rounded rects stacked on top of each other. Each
     * layer alone still has a hard edge, but overlapping several with
     * decreasing alpha reads as a soft glow rather than a flat ring once
     * they're layered — same trick, just squares instead of that method's
     * circles, since the icon tiles are square.
     */
    private void drawSoftGlow(DrawContext context, int tileX, int tileY, int tileSize, int color, float alpha) {
        int layers = 4;
        for (int layer = layers; layer >= 1; layer--) {
            float spread = layer * 2.2f;
            float layerAlpha = alpha * (0.22f / layer);
            int gx = Math.round(tileX - spread);
            int gy = Math.round(tileY - spread);
            int gSize = Math.round(tileSize + spread * 2f);
            RoundedRectShader.draw(context, gx, gy, gSize, gSize, 10f + spread, withAlpha(color, layerAlpha));
        }
        // Thin, still-fairly-solid core ring right at the tile edge, so the
        // selection is unambiguous up close and not just a diffuse haze.
        RoundedRectShader.draw(context, tileX - 2, tileY - 2, tileSize + 4, tileSize + 4, 9f, withAlpha(color, alpha * 0.65f));
    }

    // =========================================================================
    //  DRAW HELPERS
    // =========================================================================
    private void drawIcon(DrawContext context, Identifier tex, int x, int y, int size, float alpha) {
        drawIcon(context, tex, x, y, size, alpha, 1f, 1f, 1f);
    }

    /** Tinted variant — same draw call, but with an explicit RGB tint instead of always-white, for glyphs (like the close icon) that carry color meaning rather than just being a plain white symbol. */
    private void drawIcon(DrawContext context, Identifier tex, int x, int y, int size, float alpha, float r, float g, float b) {
        GPS.ensureLinearFilter(tex);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, g, b, alpha);
        context.drawTexture(GPS::getSmoothGuiTextured, tex, x, y, 0f, 0f, size, size, size, size);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawText(DrawContext c, String t, float x, float y, float size, int color) {
        if (t != null) FONT.draw(c.getMatrices(), t, x, y, size, color);
    }

    private void drawTextTiny(DrawContext c, String t, float x, float y, int color) {
        drawText(c, t, x, y, 6.5f, color);
    }

    private void drawTextCentered(DrawContext c, String t, float centerX, float y, int color) {
        float w = getTextWidth(t, 7.5f);
        drawText(c, t, centerX - w / 2f, y, 7.5f, color);
    }

    private float getTextWidth(String t, float size) {
        return FONT.getWidth(t, size);
    }

    private String clipToWidth(String text, float maxWidth, float size) {
        if (text == null) return "";
        if (getTextWidth(text, size) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (getTextWidth(sb + String.valueOf(text.charAt(i)) + "…", size) > maxWidth) break;
            sb.append(text.charAt(i));
        }
        return sb + "…";
    }

    private boolean inside(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int withAlpha(int c, float a) {
        a = clamp(a, 0f, 1f);
        return (((int) (((c >> 24) & 255) * a)) << 24) | (c & 0x00FFFFFF);
    }

    private int blendColors(int c1, int c2, float t) {
        t = clamp(t, 0f, 1f);
        float it = 1f - t;
        return (((int) (((c1 >> 24) & 255) * it + ((c2 >> 24) & 255) * t)) << 24)
                | (((int) (((c1 >> 16) & 255) * it + ((c2 >> 16) & 255) * t)) << 16)
                | (((int) (((c1 >> 8) & 255) * it + ((c2 >> 8) & 255) * t)) << 8)
                | ((int) ((c1 & 255) * it + (c2 & 255) * t));
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Same ease-in-out curve ModernClickGui's own smoothT uses — t*t*(3-2t), so window scale-in and row appearance both feel like the same product. */
    private float smoothT(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // =========================================================================
    //  MOUSE / KEYBOARD
    // =========================================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Map.Entry<String, int[]> e : new HashMap<>(clickBounds).entrySet()) {
            int[] b = e.getValue();
            if (!inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) continue;
            String id = e.getKey();

            if (id.equals("close")) {
                close();
                return true;
            }
            if (id.startsWith("row:")) {
                int idx = Integer.parseInt(id.substring(4));
                List<GPS.GpsWaypoint> waypoints = GPS.getWaypoints();
                if (idx >= 0 && idx < waypoints.size()) {
                    selectWaypoint(waypoints.get(idx));
                }
                return true;
            }
            if (id.equals("field:name")) { focusedField = 1; return true; }
            if (id.equals("field:x")) { focusedField = 2; return true; }
            if (id.equals("field:y")) { focusedField = 3; return true; }
            if (id.equals("field:z")) { focusedField = 4; return true; }
            if (id.equals("apply")) {
                commitEdits();
                return true;
            }
            if (id.startsWith("icon:")) {
                int idx = Integer.parseInt(id.substring(5));
                if (selected != null) {
                    // Direct field write — GpsWaypoint#iconBackgroundIndex is a
                    // per-waypoint rolled-once value (see GPS.java) that's
                    // intentionally NOT final specifically so this one screen
                    // can override it when the player manually picks a tile;
                    // no other field needs to change alongside it.
                    selected.iconBackgroundIndex = idx;
                    if (idx >= 0 && idx < GPS.MARKER_COLORS.length) {
                        selected.color = GPS.MARKER_COLORS[idx];
                    }
                    ConfigManager.saveConfig();
                }
                return true;
            }
        }

        focusedField = 0;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Recompute the list panel's current on-screen bounds the same way
        // render() does — mouseScrolled has no access to render()'s local
        // winX/winY, and the window's size/position isn't fixed (it animates
        // via winScale on open), so this has to be derived fresh rather than
        // reusing a stale cached rectangle.
        float winScale = 0.90f + 0.10f * smoothT(openAnim);
        int winW = Math.round(WINDOW_W * winScale);
        int winH = Math.round(WINDOW_H * winScale);
        int winX = (width - winW) / 2;
        int winY = (height - winH) / 2;
        int listX = winX + MARGIN;
        int listY = winY + MARGIN;
        int listH = winH - MARGIN * 2;

        if (inside(mouseX, mouseY, listX, listY, LIST_W, listH)) {
            targetScroll -= (float) verticalAmount * ROW_H;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focusedField == 1 && editName.length() < 24) {
            editName += chr;
            return true;
        }
        if ((focusedField == 2 || focusedField == 3 || focusedField == 4) && (Character.isDigit(chr) || chr == '-' || chr == '.')) {
            String current = fieldValue(focusedField);
            if (current.length() < 12) {
                setFieldValue(focusedField, current + chr);
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focusedField != 0 && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String current = fieldValue(focusedField);
            if (!current.isEmpty()) {
                setFieldValue(focusedField, current.substring(0, current.length() - 1));
            }
            return true;
        }
        if (focusedField != 0 && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitEdits();
            focusedField = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focusedField != 0) {
                focusedField = 0;
            } else {
                closing = true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String fieldValue(int field) {
        return switch (field) {
            case 1 -> editName;
            case 2 -> editX;
            case 3 -> editY;
            case 4 -> editZ;
            default -> "";
        };
    }

    private void setFieldValue(int field, String value) {
        switch (field) {
            case 1 -> editName = value;
            case 2 -> editX = value;
            case 3 -> editY = value;
            case 4 -> editZ = value;
            default -> {
            }
        }
    }
}