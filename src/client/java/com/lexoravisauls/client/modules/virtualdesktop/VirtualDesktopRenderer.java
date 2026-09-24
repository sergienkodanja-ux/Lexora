package com.lexoravisauls.client.modules.virtualdesktop;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.lexoravisauls.client.modules.virtualdesktop.VirtualDesktopManager.*;

/**
 * 3D in-world floating monitor rendering an authentic modern Web Browser environment.
 * Features truly round circular shortcuts, procedural diagonal cross for close button,
 * and completely standalone in-game pages (no external windows opened).
 */
public final class VirtualDesktopRenderer {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private VirtualDesktopRenderer() {
    }

    public static void render(MatrixStack matrices, Camera camera, VertexConsumerProvider consumers, float tickDelta) {
        VirtualDesktopManager vdm = VirtualDesktopManager.getInstance();
        if (!vdm.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        // Smooth dragging per-frame interpolation and visual scale decay
        vdm.updateDragging(tickDelta);
        vdm.decayScaleFeedback();

        Vec3d camPos = camera.getPos();
        double sx = vdm.getPosX() - camPos.x;
        double sy = vdm.getPosY() - camPos.y;
        double sz = vdm.getPosZ() - camPos.z;

        // Distance culling: beyond 30 blocks skip rendering entirely
        double distSq = sx * sx + sy * sy + sz * sz;
        if (distSq > 30.0 * 30.0) return;

        // Frustum culling: if monitor is behind the camera and not being dragged, skip rendering
        Vec3d look = mc.player.getRotationVec(tickDelta);
        double dot = look.x * sx + look.y * sy + look.z * sz;
        if (dot < 0.0 && !vdm.isDragging()) {
            return;
        }

        matrices.push();
        matrices.translate(sx, sy, sz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-vdm.getYaw()));

        float w = vdm.getScreenWidth();
        float h = vdm.getScreenHeight();
        float hw = w / 2.0f;
        float hh = h / 2.0f;

        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();

        try {
            // 1. Solid backplate chassis anchors the monitor in 3D world (WRITES TO DEPTH BUFFER)
            renderMonitorChassis(mat, hw, hh, vdm);

            // 2. All visual surface elements use Painter's Algorithm (depthMask = false)
            // Depth test remains active against world terrain, but monitor surface elements (borders,
            // lines, screen, tabs, crosses, text) NEVER fight each other in depth buffer! Completely eliminates disappearing lines.
            RenderSystem.depthMask(false);

            // Border outline and bottom chin
            renderMonitorDecorations(mat, hw, hh, vdm);

            // Display Canvas: Real Headless Chrome Browser Viewport
            HeadlessBrowserService browser = HeadlessBrowserService.getInstance();
            browser.updateTexture();
            int texId = browser.getTextureId();

            float scaleY = (hh * 2.0f) / V_HEIGHT;
            float topY = hh - 65.0f * scaleY;
            float botY = -hh;

            if (texId != -1) {
                renderTextureQuad(mat, -hw, hw, botY, topY, texId);
            } else {
                renderBackdrop(mat, hw, hh);
            }

            // 3. Render Top Chrome Browser Header (Visor UI, Tabs, Navigation Bar, Omnibox, Neon Accents)
            renderBrowserInterface(matrices, mat, hw, hh, vdm, texId != -1);

        } finally {
            // Strict OpenGL / RenderSystem restoration (Guarantees HUD, ClickGui and F3 never glitch or distort)
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            matrices.pop();
        }
    }

    private static void renderMonitorChassis(Matrix4f mat, float hw, float hh, VirtualDesktopManager vdm) {
        float visorExtra = vdm.getVisorExtra();
        float x1 = -hw - BORDER;
        float x2 = hw + BORDER;
        float y1 = -hh - BORDER;
        float y2 = hh + visorExtra;
        float rad = 0.040f;
        float bz = -0.008f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Dark titanium outer backplate (the only element writing to depth buffer)
        float cr = 0.065f, cg = 0.075f, cb = 0.10f;
        drawRoundedRect(buf, mat, x1, x2, y1, y2, bz, rad, 16, cr, cg, cb, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void renderMonitorDecorations(Matrix4f mat, float hw, float hh, VirtualDesktopManager vdm) {
        float visorExtra = vdm.getVisorExtra();
        float x1 = -hw - BORDER;
        float x2 = hw + BORDER;
        float y1 = -hh - BORDER;
        float y2 = hh + visorExtra;
        float rad = 0.040f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // 1. Seamless curved border outline (illuminates cyan when dragging or hovered)
        boolean handle = vdm.isHoveringHandle() || vdm.isDragging();
        float gr = handle ? 0.25f : 0.16f;
        float gg = handle ? 0.70f : 0.20f;
        float gb = handle ? 0.95f : 0.28f;
        float ga = handle ? 0.95f : 0.65f;
        drawRoundedBorder(buf, mat, x1, x2, y1, y2, 0.001f, rad, 16, gr, gg, gb, ga, 0.0035f);

        // 2. Bottom bezel accent bar (chin)
        float br = 0.08f, bg = 0.09f, bb = 0.12f;
        addQuad(buf, mat, -hw, hw, -hh - 0.020f, -hh, 0.002f, br, bg, bb, 0.98f);

        // 3. Centered branding plate on chin
        float plateW = 0.10f;
        addQuad(buf, mat, -plateW, plateW, -hh - 0.013f, -hh - 0.007f, 0.003f, 0.22f, 0.26f, 0.36f, 0.90f);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void renderBackdrop(Matrix4f mat, float hw, float hh) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float r = 0.078f, g = 0.082f, b = 0.106f;
        buf.vertex(mat, -hw, -hh, 0.0f).color(r, g, b, 1.0f);
        buf.vertex(mat, hw, -hh, 0.0f).color(r, g, b, 1.0f);
        buf.vertex(mat, hw, hh, 0.0f).color(r * 1.08f, g * 1.08f, b * 1.08f, 1.0f);
        buf.vertex(mat, -hw, hh, 0.0f).color(r * 1.08f, g * 1.08f, b * 1.08f, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void renderTextureQuad(Matrix4f mat, float x1, float x2, float y1, float y2, int texId) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texId);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(mat, x1, y1, 0.001f).texture(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, 1.0f);
        buf.vertex(mat, x2, y1, 0.001f).texture(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, 1.0f);
        buf.vertex(mat, x2, y2, 0.001f).texture(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f);
        buf.vertex(mat, x1, y2, 0.001f).texture(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void renderBrowserInterface(MatrixStack matrices, Matrix4f mat, float hw, float hh, VirtualDesktopManager vdm, boolean hasTexture) {
        float scaleX = (hw * 2.0f) / V_WIDTH;
        float scaleY = (hh * 2.0f) / V_HEIGHT;

        String act = vdm.getHoveredAction();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // --- 0. Elevated Visor Header UI (vy: -38 to 0) ---
        float visorY1 = hh;
        float visorY2 = hh + 38.0f * scaleY;
        addQuad(buf, mat, -hw, hw, visorY1, visorY2, 0.002f, 0.09f, 0.10f, 0.14f, 0.98f);

        // Visor top neon accent line
        boolean handle = vdm.isHoveringHandle() || vdm.isDragging();
        float tr = handle ? 0.35f : 0.18f;
        float tg = handle ? 0.85f : 0.45f;
        float tb = handle ? 1.00f : 0.85f;
        addQuad(buf, mat, -hw + 0.020f, hw - 0.020f, visorY2 - 0.003f, visorY2, 0.003f, tr, tg, tb, 0.90f);

        // Visor separator neon line (at seam y = hh)
        float lr = handle ? 0.30f : 0.15f;
        float lg = handle ? 0.75f : 0.18f;
        float lb = handle ? 0.95f : 0.24f;
        addQuad(buf, mat, -hw, hw, hh - 0.0015f, hh + 0.0015f, 0.0035f, lr, lg, lb, 0.90f);

        // Status LED Indicator on left
        float ledCx = -hw + 20.0f * scaleX;
        float ledCy = hh + 19.0f * scaleY;
        boolean focused = vdm.isPageFocused();
        float ledR = focused ? 0.22f : 0.13f;
        float ledG = focused ? 0.74f : 0.77f;
        float ledB = focused ? 0.97f : 0.37f;
        drawCircle(buf, mat, ledCx, ledCy, 0.003f, 4.0f * scaleX, 16, ledR, ledG, ledB, 1.0f);
        drawCircle(buf, mat, ledCx, ledCy, 0.0025f, 6.5f * scaleX, 16, ledR, ledG, ledB, 0.35f);

        // Chrome Capsule Badge
        float cbX1 = -hw + 32.0f * scaleX;
        float cbX2 = -hw + 88.0f * scaleX;
        float cbY1 = hh + 10.0f * scaleY;
        float cbY2 = hh + 28.0f * scaleY;
        drawRoundedRect(buf, mat, cbX1, cbX2, cbY1, cbY2, 0.003f, 0.004f, 6, 0.14f, 0.16f, 0.22f, 0.95f);
        drawRoundedBorder(buf, mat, cbX1, cbX2, cbY1, cbY2, 0.0035f, 0.004f, 6, 0.22f, 0.26f, 0.36f, 0.85f, 0.0012f);

        // Center Dynamic Status / Hint Capsule
        boolean hasScaleAnim = vdm.getScaleFeedbackAnim() > 0.01f;
        float infoW = (hasScaleAnim || focused) ? 360.0f : 340.0f;
        float infoX1 = -hw + ((V_WIDTH - infoW) / 2.0f) * scaleX;
        float infoX2 = -hw + ((V_WIDTH + infoW) / 2.0f) * scaleX;
        float infoY1 = hh + 10.0f * scaleY;
        float infoY2 = hh + 28.0f * scaleY;
        float infoBgR = (hasScaleAnim || focused) ? 0.10f : 0.12f;
        float infoBgG = (hasScaleAnim || focused) ? 0.18f : 0.13f;
        float infoBgB = (hasScaleAnim || focused) ? 0.26f : 0.18f;
        drawRoundedRect(buf, mat, infoX1, infoX2, infoY1, infoY2, 0.003f, 0.004f, 6, infoBgR, infoBgG, infoBgB, 0.95f);
        if (hasScaleAnim || focused) {
            drawRoundedBorder(buf, mat, infoX1, infoX2, infoY1, infoY2, 0.0035f, 0.004f, 6, 0.22f, 0.74f, 0.97f, 0.95f, 0.0015f);
        } else {
            drawRoundedBorder(buf, mat, infoX1, infoX2, infoY1, infoY2, 0.0035f, 0.004f, 6, 0.18f, 0.20f, 0.26f, 0.80f, 0.0012f);
        }

        // Right Digital Clock Capsule
        float clkX1 = -hw + (V_WIDTH - 85.0f) * scaleX;
        float clkX2 = -hw + (V_WIDTH - 12.0f) * scaleX;
        float clkY1 = hh + 10.0f * scaleY;
        float clkY2 = hh + 28.0f * scaleY;
        drawRoundedRect(buf, mat, clkX1, clkX2, clkY1, clkY2, 0.003f, 0.004f, 6, 0.13f, 0.15f, 0.20f, 0.95f);
        drawRoundedBorder(buf, mat, clkX1, clkX2, clkY1, clkY2, 0.0035f, 0.004f, 6, 0.20f, 0.22f, 0.30f, 0.80f, 0.0012f);

        // Procedural Wi-Fi / connectivity bars next to clock
        float wifiX = clkX2 - 14.0f * scaleX;
        float wifiY = hh + 14.0f * scaleY;
        float barW = 2.0f * scaleX;
        addQuad(buf, mat, wifiX, wifiX + barW, wifiY, wifiY + 4.0f * scaleY, 0.004f, 0.22f, 0.74f, 0.97f, 0.9f);
        addQuad(buf, mat, wifiX + 3.0f * scaleX, wifiX + 3.0f * scaleX + barW, wifiY, wifiY + 7.0f * scaleY, 0.004f, 0.22f, 0.74f, 0.97f, 0.9f);
        addQuad(buf, mat, wifiX + 6.0f * scaleX, wifiX + 6.0f * scaleX + barW, wifiY, wifiY + 10.0f * scaleY, 0.004f, 0.22f, 0.74f, 0.97f, 0.9f);

        // --- A. Top Tab Strip Bar (vy: 0 to 33) ---
        float tabStripY1 = hh - 33.0f * scaleY;
        float tabStripY2 = hh;
        addQuad(buf, mat, -hw, hw, tabStripY1, tabStripY2, 0.002f, 0.11f, 0.12f, 0.15f, 0.98f);

        // Multi-Tab strip (up to 7 tabs dynamically rendered)
        List<VirtualDesktopManager.WebTab> tabs = vdm.getTabs();
        int activeIdx = vdm.getActiveTabIndex();
        float tabW = Math.min(130.0f, 540.0f / Math.max(1, tabs.size()));

        for (int i = 0; i < tabs.size(); i++) {
            float tx1_px = 12.0f + i * (tabW + 4.0f);
            float tx2_px = tx1_px + tabW;
            float tabX1 = -hw + tx1_px * scaleX;
            float tabX2 = -hw + tx2_px * scaleX;
            float tabY1 = hh - 33.0f * scaleY;
            float tabY2 = hh - 6.0f * scaleY;

            boolean isActive = (i == activeIdx);
            boolean isTabHov = ("TAB_SELECT_" + i).equals(act);
            boolean isCloseHov = ("TAB_CLOSE_" + i).equals(act);

            if (isActive) {
                drawTopRoundedRect(buf, mat, tabX1, tabX2, tabY1, tabY2, 0.003f, 0.004f, 6, 0.16f, 0.17f, 0.22f, 1.0f);
                addQuad(buf, mat, tabX1 + 0.002f, tabX2 - 0.002f, tabY2 - 0.0022f, tabY2, 0.004f, 0.40f, 0.75f, 1.0f, 0.95f);
            } else {
                float inactBg = isTabHov ? 0.16f : 0.11f;
                drawTopRoundedRect(buf, mat, tabX1, tabX2, tabY1, tabY2, 0.0025f, 0.004f, 6, inactBg, inactBg + 0.01f, inactBg + 0.02f, 0.90f);
            }

            // Tab Close Cross button [ ✕ ]
            float ctX1 = -hw + (tx2_px - 18.0f) * scaleX;
            float ctX2 = -hw + (tx2_px - 4.0f) * scaleX;
            float ctY1 = hh - 26.0f * scaleY;
            float ctY2 = hh - 12.0f * scaleY;
            float ctBg = isCloseHov ? 0.85f : (isActive ? 0.20f : 0.14f);
            drawRoundedRect(buf, mat, ctX1, ctX2, ctY1, ctY2, 0.005f, 0.003f, 6, ctBg, isCloseHov ? 0.20f : ctBg, isCloseHov ? 0.20f : ctBg, 0.95f);
            drawPerfectCross(buf, mat, (ctX1 + ctX2) / 2.0f, (ctY1 + ctY2) / 2.0f, 0.006f, 0.0026f, 0.0008f, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Add Tab '+' Button
        boolean addTabHov = "TAB_ADD".equals(act);
        float plusX_px = 12.0f + tabs.size() * (tabW + 4.0f) + 4.0f;
        float atX1 = -hw + plusX_px * scaleX;
        float atX2 = -hw + (plusX_px + 22.0f) * scaleX;
        float atY1 = hh - 27.0f * scaleY;
        float atY2 = hh - 9.0f * scaleY;
        float atBg = addTabHov ? 0.26f : 0.14f;
        drawRoundedRect(buf, mat, atX1, atX2, atY1, atY2, 0.003f, 0.004f, 8, atBg, atBg + 0.01f, atBg + 0.02f, 0.9f);
        drawProceduralPlus(buf, mat, (atX1 + atX2) / 2.0f, (atY1 + atY2) / 2.0f, 0.004f, 0.0038f, 0.0010f, 0.85f, 0.88f, 0.95f, 1.0f);

        // Window Controls (top-right): Close button [ ✕ ]
        boolean winCloseHov = "WIN_CLOSE".equals(act);
        float wcX1 = -hw + 724.0f * scaleX;
        float wcX2 = -hw + 750.0f * scaleX;
        float wcY1 = hh - 27.0f * scaleY;
        float wcY2 = hh - 7.0f * scaleY;
        float wcBgR = winCloseHov ? 0.92f : 0.24f;
        float wcBgG = winCloseHov ? 0.20f : 0.14f;
        float wcBgB = winCloseHov ? 0.20f : 0.16f;
        drawRoundedRect(buf, mat, wcX1, wcX2, wcY1, wcY2, 0.003f, 0.005f, 8, wcBgR, wcBgG, wcBgB, 0.95f);
        drawRoundedBorder(buf, mat, wcX1, wcX2, wcY1, wcY2, 0.004f, 0.005f, 8, winCloseHov ? 1.0f : 0.45f, winCloseHov ? 0.5f : 0.20f, winCloseHov ? 0.5f : 0.22f, 0.9f, 0.0015f);
        drawPerfectCross(buf, mat, (wcX1 + wcX2) / 2.0f, (wcY1 + wcY2) / 2.0f, 0.005f, 0.0042f, 0.0012f, 1.0f, 1.0f, 1.0f, 1.0f);

        // --- B. Navigation Bar & Omnibox (vy: 33 to 65) ---
        float navY1 = hh - 65.0f * scaleY;
        float navY2 = hh - 33.0f * scaleY;
        addQuad(buf, mat, -hw, hw, navY1, navY2, 0.002f, 0.14f, 0.15f, 0.18f, 0.98f);

        // Navigation Buttons (<, >, ⟳, ⌂) Backgrounds
        boolean backHov = "NAV_BACK".equals(act);
        boolean fwdHov = "NAV_FORWARD".equals(act);
        boolean refHov = "NAV_REFRESH".equals(act);
        boolean homeHov = "NAV_HOME".equals(act);

        if (backHov) drawRoundedRect(buf, mat, -hw + 14.0f * scaleX, -hw + 32.0f * scaleX, hh - 58.0f * scaleY, hh - 40.0f * scaleY, 0.003f, 0.003f, 6, 0.22f, 0.24f, 0.30f, 0.9f);
        if (fwdHov) drawRoundedRect(buf, mat, -hw + 40.0f * scaleX, -hw + 58.0f * scaleX, hh - 58.0f * scaleY, hh - 40.0f * scaleY, 0.003f, 0.003f, 6, 0.22f, 0.24f, 0.30f, 0.9f);
        if (refHov) drawRoundedRect(buf, mat, -hw + 66.0f * scaleX, -hw + 84.0f * scaleX, hh - 58.0f * scaleY, hh - 40.0f * scaleY, 0.003f, 0.003f, 6, 0.22f, 0.24f, 0.30f, 0.9f);
        if (homeHov) drawRoundedRect(buf, mat, -hw + 92.0f * scaleX, -hw + 110.0f * scaleX, hh - 58.0f * scaleY, hh - 40.0f * scaleY, 0.003f, 0.003f, 6, 0.22f, 0.24f, 0.30f, 0.9f);

        // Omnibox URL Bar (vx: 118 to 660, vy: 37 to 61)
        boolean isTypingOmni = vdm.isTypingOmnibox();
        boolean omniHov = "OMNIBOX".equals(act);
        float obX1 = -hw + 118.0f * scaleX;
        float obX2 = -hw + 660.0f * scaleX;
        float obY1 = hh - 61.0f * scaleY;
        float obY2 = hh - 37.0f * scaleY;
        drawRoundedRect(buf, mat, obX1, obX2, obY1, obY2, 0.003f, 0.005f, 8, 0.09f, 0.10f, 0.13f, 0.98f);
        if (isTypingOmni) {
            drawRoundedBorder(buf, mat, obX1, obX2, obY1, obY2, 0.004f, 0.005f, 8, 0.35f, 0.70f, 1.0f, 1.0f, 0.0028f);
        } else {
            float obBorderCol = omniHov ? 0.60f : 0.22f;
            drawRoundedBorder(buf, mat, obX1, obX2, obY1, obY2, 0.004f, 0.005f, 8, obBorderCol, obBorderCol + 0.05f, obBorderCol + 0.15f, 0.9f, 0.002f);
        }

        // Profile Avatar Circle
        float paX1 = -hw + 718.0f * scaleX;
        float paX2 = -hw + 742.0f * scaleX;
        float paY1 = hh - 60.0f * scaleY;
        float paY2 = hh - 38.0f * scaleY;
        drawCircle(buf, mat, (paX1 + paX2) / 2.0f, (paY1 + paY2) / 2.0f, 0.003f, (paX2 - paX1) / 2.0f, 16, 0.22f, 0.25f, 0.35f, 0.95f);

        // Nav divider line
        float divY = hh - 65.0f * scaleY;
        addQuad(buf, mat, -hw, hw, divY - 0.002f, divY, 0.003f, 0.18f, 0.20f, 0.26f, 0.9f);

        BufferRenderer.drawWithGlobalProgram(buf.end());

        // Typography overlay
        matrices.push();
        matrices.translate(-hw, hh, 0.008f);
        matrices.scale(scaleX, -scaleY, scaleX);

        renderBrowserText(matrices, vdm, act, hasTexture);

        matrices.pop();
    }

    private static void renderBrowserText(MatrixStack matrices, VirtualDesktopManager vdm, String act, boolean hasTexture) {
        // 1. Visor Text (centered vertically between y = -28.0f and y = -10.0f, baseline ~ -23.0f)
        ModernClickGui.SFUI.draw(matrices, "CHROME", 39.0f, -23.0f, 9.5f, 0xFFE2E8F0);
        ModernClickGui.SFUI.draw(matrices, "v128 • Online", 96.0f, -22.5f, 8.5f, 0xFF94A3B8);

        if (vdm.getScaleFeedbackAnim() > 0.01f) {
            float curScale = ClientData.numSettings.getOrDefault("VD Scale", 1.8f);
            int pct = Math.round((curScale / 1.8f) * 100.0f);
            String scaleMsg = "🔍 МАСШТАБ МОНИТОРА: " + pct + "%";
            float sW = ModernClickGui.SFUI.getWidth(scaleMsg, 8.5f);
            ModernClickGui.SFUI.draw(matrices, scaleMsg, (V_WIDTH - sW) / 2.0f, -22.5f, 8.5f, 0xFF38BDF8);
        } else if (vdm.isPageFocused()) {
            String focusMsg = "⌨ РЕЖИМ ВВОДА В СТРАНИЦУ • ESC ДЛЯ ВЫХОДА";
            float fW = ModernClickGui.SFUI.getWidth(focusMsg, 8.5f);
            ModernClickGui.SFUI.draw(matrices, focusMsg, (V_WIDTH - fW) / 2.0f, -22.5f, 8.5f, 0xFF38BDF8);
        } else {
            String hintMsg = "Клик: ввод/ссылки | Колесико: зум | Потяните за рамку";
            float hW = ModernClickGui.SFUI.getWidth(hintMsg, 8.0f);
            ModernClickGui.SFUI.draw(matrices, hintMsg, (V_WIDTH - hW) / 2.0f, -22.0f, 8.0f, 0xFF64748B);
        }

        String timeStr = LocalTime.now().format(TIME_FMT);
        ModernClickGui.SFUI.draw(matrices, timeStr, V_WIDTH - 80.0f, -23.0f, 9.5f, 0xFFE2E8F0);

        // 2. Tab Titles
        List<VirtualDesktopManager.WebTab> tabs = vdm.getTabs();
        int activeIdx = vdm.getActiveTabIndex();
        float tabW = Math.min(130.0f, 540.0f / Math.max(1, tabs.size()));

        for (int i = 0; i < tabs.size(); i++) {
            VirtualDesktopManager.WebTab tab = tabs.get(i);
            float tx1 = 12.0f + i * (tabW + 4.0f);
            String title = (i == activeIdx) ? vdm.getTabTitle() : tab.title;
            if (title == null || title.isEmpty()) title = "Новая вкладка";
            int maxChars = Math.max(3, (int) ((tabW - 24.0f) / 6.0f));
            if (title.length() > maxChars) {
                title = title.substring(0, Math.max(2, maxChars - 1)) + "..";
            }
            int color = (i == activeIdx) ? 0xFFFFFFFF : 0xFF9AA0A6;
            ModernClickGui.SFUI.draw(matrices, title, tx1 + 6.0f, 13.0f, 9.0f, color);
        }

        // Navigation Controls (<, >, O, H)
        ModernClickGui.SFUI.draw(matrices, "<", 20.0f, 41.0f, 14.0f, 0xFFCCCCCC);
        ModernClickGui.SFUI.draw(matrices, ">", 46.0f, 41.0f, 14.0f, 0xFF777777);
        ModernClickGui.SFUI.draw(matrices, "O", 72.0f, 43.0f, 11.5f, 0xFFCCCCCC);
        ModernClickGui.SFUI.draw(matrices, "H", 97.0f, 43.0f, 10.5f, 0xFF9AA0A6);

        // Omnibox URL Text
        boolean isTypingOmni = vdm.isTypingOmnibox();
        boolean blink = (System.currentTimeMillis() % 1000) < 500;
        String cursor = blink ? "|" : "";

        ModernClickGui.SFUI.draw(matrices, "[L]", 130.0f, 44.0f, 9.0f, 0xFF4ADE80);
        if (isTypingOmni) {
            String typed = vdm.getTypingQuery();
            ModernClickGui.SFUI.draw(matrices, typed + cursor, 152.0f, 43.5f, 9.5f, 0xFFFFFFFF);
            ModernClickGui.SFUI.draw(matrices, "[Enter] Перейти", 580.0f, 43.5f, 8.5f, 0xFF38BDF8);
        } else {
            String url = vdm.getCurrentUrl();
            if (url.length() > 65) url = url.substring(0, 62) + "...";
            ModernClickGui.SFUI.draw(matrices, url, 152.0f, 43.5f, 9.5f, 0xFFD1D5DB);
        }

        // Profile Avatar icon
        ModernClickGui.SFUI.draw(matrices, "G", 726.0f, 44.0f, 9.5f, 0xFFFFFFFF);

        // Loading message if texture not yet ready
        if (!hasTexture) {
            String loadMsg = "Запуск Google Chrome...";
            float msgW = ModernClickGui.SFUI.getWidth(loadMsg, 16.0f);
            ModernClickGui.SFUI.draw(matrices, loadMsg, (V_WIDTH - msgW) / 2.0f, 240.0f, 16.0f, 0xFF38BDF8);
            String subMsg = "Загрузка веб-сайта...";
            float subW = ModernClickGui.SFUI.getWidth(subMsg, 10.5f);
            ModernClickGui.SFUI.draw(matrices, subMsg, (V_WIDTH - subW) / 2.0f, 265.0f, 10.5f, 0xFF94A3B8);
        }

        // Reset RenderSystem shader after MSDF text drawing to prevent shader state leaks
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * True 45-degree diagonal cross '✕' with uniform line thickness.
     * Guaranteed to render a crisp, classic window close cross!
     */
    private static void drawPerfectCross(BufferBuilder buf, Matrix4f mat, float cx, float cy, float z,
                                         float size, float thick, float r, float g, float b, float a) {
        float d = thick * 0.7071f;

        // Diagonal 1: Bottom-Left to Top-Right (\)
        buf.vertex(mat, cx - size - d, cy - size + d, z).color(r, g, b, a);
        buf.vertex(mat, cx + size - d, cy + size + d, z).color(r, g, b, a);
        buf.vertex(mat, cx + size + d, cy + size - d, z).color(r, g, b, a);
        buf.vertex(mat, cx - size + d, cy - size - d, z).color(r, g, b, a);

        // Diagonal 2: Top-Left to Bottom-Right (/)
        buf.vertex(mat, cx - size - d, cy + size - d, z).color(r, g, b, a);
        buf.vertex(mat, cx + size - d, cy - size - d, z).color(r, g, b, a);
        buf.vertex(mat, cx + size + d, cy - size + d, z).color(r, g, b, a);
        buf.vertex(mat, cx - size + d, cy + size + d, z).color(r, g, b, a);
    }

    private static void drawProceduralPlus(BufferBuilder buf, Matrix4f mat, float cx, float cy, float z,
                                           float size, float thick, float r, float g, float b, float a) {
        addQuad(buf, mat, cx - size, cx + size, cy - thick, cy + thick, z, r, g, b, a);
        addQuad(buf, mat, cx - thick, cx + thick, cy - size, cy + size, z, r, g, b, a);
    }

    /**
     * Procedural circle geometry (24-32 segments) for perfectly rounded shortcut badges.
     */
    private static void drawCircle(BufferBuilder buf, Matrix4f mat, float cx, float cy, float z,
                                   float rad, int steps, float r, float g, float b, float a) {
        float stepDeg = 360.0f / steps;
        for (int i = 0; i < steps; i++) {
            float a1 = (float) Math.toRadians(i * stepDeg);
            float a2 = (float) Math.toRadians((i + 1) * stepDeg);

            float p1x = cx + (float) Math.cos(a1) * rad;
            float p1y = cy + (float) Math.sin(a1) * rad;
            float p2x = cx + (float) Math.cos(a2) * rad;
            float p2y = cy + (float) Math.sin(a2) * rad;

            buf.vertex(mat, cx, cy, z).color(r, g, b, a);
            buf.vertex(mat, cx, cy, z).color(r, g, b, a);
            buf.vertex(mat, p1x, p1y, z).color(r, g, b, a);
            buf.vertex(mat, p2x, p2y, z).color(r, g, b, a);
        }
    }

    private static void drawCircleRing(BufferBuilder buf, Matrix4f mat, float cx, float cy, float z,
                                       float rad, float thick, int steps, float r, float g, float b, float a) {
        float stepDeg = 360.0f / steps;
        float inRad = Math.max(0.001f, rad - thick);
        for (int i = 0; i < steps; i++) {
            float a1 = (float) Math.toRadians(i * stepDeg);
            float a2 = (float) Math.toRadians((i + 1) * stepDeg);

            float o1x = cx + (float) Math.cos(a1) * rad;
            float o1y = cy + (float) Math.sin(a1) * rad;
            float o2x = cx + (float) Math.cos(a2) * rad;
            float o2y = cy + (float) Math.sin(a2) * rad;

            float i1x = cx + (float) Math.cos(a1) * inRad;
            float i1y = cy + (float) Math.sin(a1) * inRad;
            float i2x = cx + (float) Math.cos(a2) * inRad;
            float i2y = cy + (float) Math.sin(a2) * inRad;

            buf.vertex(mat, i1x, i1y, z).color(r, g, b, a);
            buf.vertex(mat, o1x, o1y, z).color(r, g, b, a);
            buf.vertex(mat, o2x, o2y, z).color(r, g, b, a);
            buf.vertex(mat, i2x, i2y, z).color(r, g, b, a);
        }
    }

    private static void drawRoundedRect(BufferBuilder buf, Matrix4f mat, float x1, float x2, float y1, float y2,
                                        float z, float rad, int steps, float r, float g, float b, float a) {
        addQuad(buf, mat, x1 + rad, x2 - rad, y1 + rad, y2 - rad, z, r, g, b, a);
        addQuad(buf, mat, x1, x1 + rad, y1 + rad, y2 - rad, z, r, g, b, a);
        addQuad(buf, mat, x2 - rad, x2, y1 + rad, y2 - rad, z, r, g, b, a);
        addQuad(buf, mat, x1 + rad, x2 - rad, y2 - rad, y2, z, r, g, b, a);
        addQuad(buf, mat, x1 + rad, x2 - rad, y1, y1 + rad, z, r, g, b, a);

        addCornerFan(buf, mat, x1 + rad, y2 - rad, rad, 90.0f, 180.0f, steps, z, r, g, b, a);
        addCornerFan(buf, mat, x2 - rad, y2 - rad, rad, 0.0f, 90.0f, steps, z, r, g, b, a);
        addCornerFan(buf, mat, x1 + rad, y1 + rad, rad, 180.0f, 270.0f, steps, z, r, g, b, a);
        addCornerFan(buf, mat, x2 - rad, y1 + rad, rad, 270.0f, 360.0f, steps, z, r, g, b, a);
    }

    private static void drawTopRoundedRect(BufferBuilder buf, Matrix4f mat, float x1, float x2, float y1, float y2,
                                           float z, float rad, int steps, float r, float g, float b, float a) {
        addQuad(buf, mat, x1 + rad, x2 - rad, y1, y2 - rad, z, r, g, b, a);
        addQuad(buf, mat, x1, x1 + rad, y1, y2 - rad, z, r, g, b, a);
        addQuad(buf, mat, x2 - rad, x2, y1, y2 - rad, z, r, g, b, a);
        addQuad(buf, mat, x1 + rad, x2 - rad, y2 - rad, y2, z, r, g, b, a);

        addCornerFan(buf, mat, x1 + rad, y2 - rad, rad, 90.0f, 180.0f, steps, z, r, g, b, a);
        addCornerFan(buf, mat, x2 - rad, y2 - rad, rad, 0.0f, 90.0f, steps, z, r, g, b, a);
    }

    private static void drawRoundedBorder(BufferBuilder buf, Matrix4f mat, float x1, float x2, float y1, float y2,
                                          float z, float rad, int steps, float r, float g, float b, float a, float t) {
        addQuad(buf, mat, x1 + rad, x2 - rad, y2 - t, y2, z, r, g, b, a);
        addQuad(buf, mat, x1 + rad, x2 - rad, y1, y1 + t, z, r, g, b, a);
        addQuad(buf, mat, x1, x1 + t, y1 + rad, y2 - rad, z, r, g, b, a);
        addQuad(buf, mat, x2 - t, x2, y1 + rad, y2 - rad, z, r, g, b, a);

        addCornerStrip(buf, mat, x1 + rad, y2 - rad, rad, 90.0f, 180.0f, steps, z, r, g, b, a, t);
        addCornerStrip(buf, mat, x2 - rad, y2 - rad, rad, 0.0f, 90.0f, steps, z, r, g, b, a, t);
        addCornerStrip(buf, mat, x1 + rad, y1 + rad, rad, 180.0f, 270.0f, steps, z, r, g, b, a, t);
        addCornerStrip(buf, mat, x2 - rad, y1 + rad, rad, 270.0f, 360.0f, steps, z, r, g, b, a, t);
    }

    private static void addCornerFan(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rad,
                                     float startDeg, float endDeg, int steps, float z,
                                     float r, float g, float b, float a) {
        float stepDeg = (endDeg - startDeg) / steps;
        for (int i = 0; i < steps; i++) {
            float a1 = (float) Math.toRadians(startDeg + i * stepDeg);
            float a2 = (float) Math.toRadians((i + 1) * stepDeg);

            float p1x = cx + (float) Math.cos(a1) * rad;
            float p1y = cy + (float) Math.sin(a1) * rad;
            float p2x = cx + (float) Math.cos(a2) * rad;
            float p2y = cy + (float) Math.sin(a2) * rad;

            buf.vertex(mat, cx, cy, z).color(r, g, b, a);
            buf.vertex(mat, cx, cy, z).color(r, g, b, a);
            buf.vertex(mat, p1x, p1y, z).color(r, g, b, a);
            buf.vertex(mat, p2x, p2y, z).color(r, g, b, a);
        }
    }

    private static void addCornerStrip(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rad,
                                       float startDeg, float endDeg, int steps, float z,
                                       float r, float g, float b, float a, float t) {
        float stepDeg = (endDeg - startDeg) / steps;
        float innerRad = Math.max(0.001f, rad - t);
        for (int i = 0; i < steps; i++) {
            float a1 = (float) Math.toRadians(startDeg + i * stepDeg);
            float a2 = (float) Math.toRadians((i + 1) * stepDeg);

            float o1x = cx + (float) Math.cos(a1) * rad;
            float o1y = cy + (float) Math.sin(a1) * rad;
            float o2x = cx + (float) Math.cos(a2) * rad;
            float o2y = cy + (float) Math.sin(a2) * rad;

            float i1x = cx + (float) Math.cos(a1) * innerRad;
            float i1y = cy + (float) Math.sin(a1) * innerRad;
            float i2x = cx + (float) Math.cos(a2) * innerRad;
            float i2y = cy + (float) Math.sin(a2) * innerRad;

            buf.vertex(mat, i1x, i1y, z).color(r, g, b, a);
            buf.vertex(mat, o1x, o1y, z).color(r, g, b, a);
            buf.vertex(mat, o2x, o2y, z).color(r, g, b, a);
            buf.vertex(mat, i2x, i2y, z).color(r, g, b, a);
        }
    }

    private static void addQuad(BufferBuilder buf, Matrix4f mat, float x1, float x2, float y1, float y2,
                                float z, float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z).color(r, g, b, a);
        buf.vertex(mat, x2, y1, z).color(r, g, b, a);
        buf.vertex(mat, x2, y2, z).color(r, g, b, a);
        buf.vertex(mat, x1, y2, z).color(r, g, b, a);
    }
}
