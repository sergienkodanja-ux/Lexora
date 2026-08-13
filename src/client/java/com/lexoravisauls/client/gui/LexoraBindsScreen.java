package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.core.BindManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран биндов Lexora. Теперь не просто картинка: клавиши и кнопки мыши,
 * на которых уже что-то забинджено, подсвечиваются цветом акцента. Клик
 * по занятой клавише открывает мини-окно "что тут забинджено"
 * (LexoraBindInfoScreen), клик по свободной — окно выбора режима
 * Hold/Toggle (LexoraBindModeScreen), которое ведёт дальше к выбору
 * модуля/команды (LexoraBindPickerScreen).
 */
public class LexoraBindsScreen extends Screen {
    private final Screen parent;

    private long openTimeMs = 0L;

    private boolean transitioningOut  = false;
    private long    transitionStartMs = 0L;
    private static final long TRANSITION_MS = 250L;
    private static final long REVEAL_MS     = 320L;

    private final List<KeySpec>   keys       = new ArrayList<>();
    private final List<MouseZone> mouseZones = new ArrayList<>();

    private int panelX, panelY, panelW, panelH;
    private int mouseBodyX, mouseBodyY, mouseBodyW, mouseBodyH;
    private int lastPanelOffsetY = 0;

    public LexoraBindsScreen(Screen parent) {
        super(Text.literal("Бинды Lexora"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        computeLayout();
        openTimeMs = System.currentTimeMillis();

        int backW = 152, backH = 26;
        int backX = this.width / 2 - backW / 2;
        int backY = panelY + panelH + 20;

        this.addDrawableChild(new LexoraButtonWidget(backX, backY, backW, backH,
                Text.literal("← Назад"), btn -> triggerBack()));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            triggerBack();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // фон рисуем сами в render(), чтобы контролировать fade-in
    }

    private void triggerBack() {
        if (transitioningOut) return;
        transitioningOut  = true;
        transitionStartMs = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !transitioningOut) {
            for (KeySpec k : keys) {
                int ky = k.y + lastPanelOffsetY;
                if (mx >= k.x && mx <= k.x + k.w && my >= ky && my <= ky + k.h) {
                    openBindPopup(k.keyCode, k.label);
                    return true;
                }
            }
            for (MouseZone z : mouseZones) {
                int zy = z.y + lastPanelOffsetY;
                if (mx >= z.x && mx <= z.x + z.w && my >= zy && my <= zy + z.h) {
                    openBindPopup(z.keyCode, z.label);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void openBindPopup(int keyCode, String label) {
        boolean bound = BindManager.findModuleOnKey(keyCode) != null
                || BindManager.getCommandForKey(keyCode) != null;
        if (bound) {
            this.client.setScreen(new LexoraBindInfoScreen(this.parent, keyCode, label));
        } else {
            this.client.setScreen(new LexoraBindModeScreen(this.parent, keyCode, label));
        }
    }

    private record KeyDef(String label, int code) {}

    private static int letter(char c) {
        return GLFW.GLFW_KEY_A + (Character.toUpperCase(c) - 'A');
    }

    private void computeLayout() {
        keys.clear();
        mouseZones.clear();

        int keyW = 30, keyH = 26, gap = 4;

        KeyDef[][] rows = {
                { new KeyDef("Esc", GLFW.GLFW_KEY_ESCAPE),
                        new KeyDef("F1", GLFW.GLFW_KEY_F1), new KeyDef("F2", GLFW.GLFW_KEY_F2),
                        new KeyDef("F3", GLFW.GLFW_KEY_F3), new KeyDef("F4", GLFW.GLFW_KEY_F4),
                        new KeyDef("F5", GLFW.GLFW_KEY_F5), new KeyDef("F6", GLFW.GLFW_KEY_F6),
                        new KeyDef("F7", GLFW.GLFW_KEY_F7), new KeyDef("F8", GLFW.GLFW_KEY_F8),
                        new KeyDef("F9", GLFW.GLFW_KEY_F9), new KeyDef("F10", GLFW.GLFW_KEY_F10),
                        new KeyDef("F11", GLFW.GLFW_KEY_F11), new KeyDef("F12", GLFW.GLFW_KEY_F12) },

                { new KeyDef("~", GLFW.GLFW_KEY_GRAVE_ACCENT),
                        new KeyDef("1", GLFW.GLFW_KEY_1), new KeyDef("2", GLFW.GLFW_KEY_2),
                        new KeyDef("3", GLFW.GLFW_KEY_3), new KeyDef("4", GLFW.GLFW_KEY_4),
                        new KeyDef("5", GLFW.GLFW_KEY_5), new KeyDef("6", GLFW.GLFW_KEY_6),
                        new KeyDef("7", GLFW.GLFW_KEY_7), new KeyDef("8", GLFW.GLFW_KEY_8),
                        new KeyDef("9", GLFW.GLFW_KEY_9), new KeyDef("0", GLFW.GLFW_KEY_0),
                        new KeyDef("-", GLFW.GLFW_KEY_MINUS), new KeyDef("=", GLFW.GLFW_KEY_EQUAL),
                        new KeyDef("Back", GLFW.GLFW_KEY_BACKSPACE) },

                { new KeyDef("Tab", GLFW.GLFW_KEY_TAB),
                        new KeyDef("Q", letter('Q')), new KeyDef("W", letter('W')), new KeyDef("E", letter('E')),
                        new KeyDef("R", letter('R')), new KeyDef("T", letter('T')), new KeyDef("Y", letter('Y')),
                        new KeyDef("U", letter('U')), new KeyDef("I", letter('I')), new KeyDef("O", letter('O')),
                        new KeyDef("P", letter('P')),
                        new KeyDef("[", GLFW.GLFW_KEY_LEFT_BRACKET), new KeyDef("]", GLFW.GLFW_KEY_RIGHT_BRACKET),
                        new KeyDef("\\", GLFW.GLFW_KEY_BACKSLASH) },

                { new KeyDef("Caps", GLFW.GLFW_KEY_CAPS_LOCK),
                        new KeyDef("A", letter('A')), new KeyDef("S", letter('S')), new KeyDef("D", letter('D')),
                        new KeyDef("F", letter('F')), new KeyDef("G", letter('G')), new KeyDef("H", letter('H')),
                        new KeyDef("J", letter('J')), new KeyDef("K", letter('K')), new KeyDef("L", letter('L')),
                        new KeyDef(";", GLFW.GLFW_KEY_SEMICOLON), new KeyDef("'", GLFW.GLFW_KEY_APOSTROPHE),
                        new KeyDef("Enter", GLFW.GLFW_KEY_ENTER) },

                { new KeyDef("Shift", GLFW.GLFW_KEY_LEFT_SHIFT),
                        new KeyDef("Z", letter('Z')), new KeyDef("X", letter('X')), new KeyDef("C", letter('C')),
                        new KeyDef("V", letter('V')), new KeyDef("B", letter('B')), new KeyDef("N", letter('N')),
                        new KeyDef("M", letter('M')),
                        new KeyDef(",", GLFW.GLFW_KEY_COMMA), new KeyDef(".", GLFW.GLFW_KEY_PERIOD),
                        new KeyDef("/", GLFW.GLFW_KEY_SLASH), new KeyDef("Shift", GLFW.GLFW_KEY_RIGHT_SHIFT) },

                { new KeyDef("Ctrl", GLFW.GLFW_KEY_LEFT_CONTROL), new KeyDef("Win", GLFW.GLFW_KEY_LEFT_SUPER),
                        new KeyDef("Alt", GLFW.GLFW_KEY_LEFT_ALT), new KeyDef("Space", GLFW.GLFW_KEY_SPACE),
                        new KeyDef("Alt", GLFW.GLFW_KEY_RIGHT_ALT), new KeyDef("Win", GLFW.GLFW_KEY_RIGHT_SUPER),
                        new KeyDef("Menu", GLFW.GLFW_KEY_MENU), new KeyDef("Ctrl", GLFW.GLFW_KEY_RIGHT_CONTROL) }
        };

        int maxRowWidth = 0;
        for (KeyDef[] row : rows) {
            int w = 0;
            for (KeyDef k : row) w += keyWidth(k.label(), keyW, gap) + gap;
            w -= gap;
            maxRowWidth = Math.max(maxRowWidth, w);
        }

        int mouseW = 70, mouseH = 138, mouseGapLeft = 30, mouseSideBtnW = 14;
        int contentW = maxRowWidth + mouseGapLeft + mouseSideBtnW + 6 + mouseW;
        int contentH = Math.max(rows.length * (keyH + gap) - gap, mouseH);

        int padding = 26;
        int headerH = 34;
        panelW = contentW + padding * 2;
        panelH = contentH + padding * 2 + headerH;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(50, (this.height - panelH) / 2 - 10);

        int kbX = panelX + padding;
        int kbY = panelY + padding + headerH;

        for (int r = 0; r < rows.length; r++) {
            int x = kbX;
            int y = kbY + r * (keyH + gap);
            for (KeyDef def : rows[r]) {
                int w = keyWidth(def.label(), keyW, gap);
                keys.add(new KeySpec(x, y, w, keyH, def.label(), def.code(), r));
                x += w + gap;
            }
        }

        int mouseX = kbX + maxRowWidth + mouseGapLeft + mouseSideBtnW + 6;
        int mouseY = kbY + (contentH - mouseH) / 2;

        mouseBodyX = mouseX; mouseBodyY = mouseY; mouseBodyW = mouseW; mouseBodyH = mouseH;

        int half = mouseW / 2;
        mouseZones.add(new MouseZone(mouseX + 4, mouseY + 4, half - 6, mouseH / 3 - 4, "ЛКМ",
                BindManager.encodeMouseBind(GLFW.GLFW_MOUSE_BUTTON_LEFT), 8f));
        mouseZones.add(new MouseZone(mouseX + half + 2, mouseY + 4, half - 6, mouseH / 3 - 4, "ПКМ",
                BindManager.encodeMouseBind(GLFW.GLFW_MOUSE_BUTTON_RIGHT), 8f));
        mouseZones.add(new MouseZone(mouseX + half - 6, mouseY + mouseH / 3 + 6, 12, mouseH / 3 - 8, "СКМ",
                BindManager.encodeMouseBind(GLFW.GLFW_MOUSE_BUTTON_MIDDLE), 6f));
        mouseZones.add(new MouseZone(mouseX - mouseSideBtnW - 6, mouseY + 16, mouseSideBtnW, 20, "M4",
                BindManager.encodeMouseBind(3), 5f));
        mouseZones.add(new MouseZone(mouseX - mouseSideBtnW - 6, mouseY + 42, mouseSideBtnW, 20, "M5",
                BindManager.encodeMouseBind(4), 5f));
        mouseZones.add(new MouseZone(mouseX - mouseSideBtnW - 6, mouseY + 68, mouseSideBtnW, 20, "M6",
                BindManager.encodeMouseBind(5), 5f));
    }

    private int keyWidth(String key, int base, int gap) {
        return switch (key) {
            case "Back" -> base * 2 + gap;
            case "Tab" -> base + 10;
            case "Caps" -> base + 20;
            case "Enter" -> base * 2 + gap + 20;
            case "Shift" -> base * 2 + gap + 30;
            case "Space" -> base * 6 + gap * 5;
            default -> base;
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float reveal = LexoraStyle.easeOutCubic(clamp01((now - openTimeMs) / (float) REVEAL_MS));

        // затемнённый фон с fade-in
        int bgAlpha = (int) (reveal * 210);
        context.fill(0, 0, this.width, this.height, bgAlpha << 24);

        int panelOffsetY = (int) LexoraStyle.lerp(24f, 0f, reveal);
        lastPanelOffsetY = panelOffsetY;

        // заголовок
        String title = "БИНДЫ";
        float titleW = LexoraStyle.font().getWidth(title, LexoraStyle.SIZE_TITLE);
        LexoraStyle.font().draw(context.getMatrices(), title,
                this.width / 2f - titleW / 2f, panelY - 30,
                LexoraStyle.SIZE_TITLE, LexoraStyle.adjustAlpha(LexoraStyle.COL_TEXT_PRIMARY, reveal));

        // панель
        LexoraStyle.drawSmoothRect(context, panelX, panelY + panelOffsetY, panelW, panelH, 10f,
                LexoraStyle.adjustAlpha(LexoraStyle.COL_PANEL_BG, reveal));

        LexoraStyle.font().draw(context.getMatrices(), "РАСКЛАДКА — нажми на клавишу, чтобы настроить бинд",
                panelX + 18, panelY + panelOffsetY + 12, LexoraStyle.SIZE_SMALL,
                LexoraStyle.adjustAlpha(LexoraStyle.COL_TEXT_SECONDARY, reveal));
        context.fill(panelX + 16, panelY + panelOffsetY + 28, panelX + panelW - 16, panelY + panelOffsetY + 29,
                LexoraStyle.adjustAlpha(LexoraStyle.COL_DIVIDER, reveal));

        // hover для клавиш и зон мыши (учитываем текущий сдвиг панели)
        for (KeySpec k : keys) {
            int ky = k.y + panelOffsetY;
            boolean hov = mouseX >= k.x && mouseX <= k.x + k.w && mouseY >= ky && mouseY <= ky + k.h;
            k.hoverAnim += ((hov ? 1f : 0f) - k.hoverAnim) * 0.2f;
            if (k.hoverAnim < 0.01f) k.hoverAnim = 0f;
        }
        for (MouseZone z : mouseZones) {
            int zy = z.y + panelOffsetY;
            boolean hov = mouseX >= z.x && mouseX <= z.x + z.w && mouseY >= zy && mouseY <= zy + z.h;
            z.hoverAnim += ((hov ? 1f : 0f) - z.hoverAnim) * 0.2f;
            if (z.hoverAnim < 0.01f) z.hoverAnim = 0f;
        }

        // клавиатура — со стаггером по строкам + подсветка занятых клавиш
        for (KeySpec k : keys) {
            float rowReveal = LexoraStyle.easeOutCubic(clamp01((reveal * 1.4f - k.row * 0.12f) / 0.7f));
            float a = reveal * rowReveal;
            if (a <= 0.01f) continue;
            int ky = k.y + panelOffsetY + (int) ((1f - rowReveal) * 8f);

            boolean bound = BindManager.findModuleOnKey(k.keyCode) != null
                    || BindManager.getCommandForKey(k.keyCode) != null;

            int baseBg = bound
                    ? LexoraStyle.blendColors(LexoraStyle.COL_KEY_BG, LexoraStyle.COL_ACCENT, 0.55f)
                    : LexoraStyle.COL_KEY_BG;
            int hoverBg = bound
                    ? LexoraStyle.blendColors(LexoraStyle.COL_KEY_HOVER, LexoraStyle.COL_ACCENT, 0.7f)
                    : LexoraStyle.COL_KEY_HOVER;
            int bg = LexoraStyle.blendColors(baseBg, hoverBg, k.hoverAnim);
            LexoraStyle.drawSmoothRect(context, k.x, ky, k.w, k.h, 5f, LexoraStyle.adjustAlpha(bg, a));

            int textCol = LexoraStyle.blendColors(LexoraStyle.COL_TEXT_PRIMARY, LexoraStyle.COL_TEXT_WHITE, k.hoverAnim);
            float lw = LexoraStyle.font().getWidth(k.label, LexoraStyle.SIZE_TINY);
            LexoraStyle.font().draw(context.getMatrices(), k.label,
                    k.x + (k.w - lw) / 2f, ky + (k.h - LexoraStyle.SIZE_TINY) / 2f,
                    LexoraStyle.SIZE_TINY, LexoraStyle.adjustAlpha(textCol, a));
        }

        // мышь — появляется чуть позже последней строки клавиатуры
        float mouseReveal = LexoraStyle.easeOutCubic(clamp01((reveal * 1.4f - 6 * 0.12f) / 0.7f));
        float ma = reveal * mouseReveal;
        if (ma > 0.01f) {
            int mby = mouseBodyY + panelOffsetY + (int) ((1f - mouseReveal) * 8f);
            LexoraStyle.drawSmoothRect(context, mouseBodyX, mby, mouseBodyW, mouseBodyH, 18f,
                    LexoraStyle.adjustAlpha(0x77151515, ma));

            for (MouseZone z : mouseZones) {
                int zy = z.y + panelOffsetY + (int) ((1f - mouseReveal) * 8f);
                boolean bound = BindManager.findModuleOnKey(z.keyCode) != null
                        || BindManager.getCommandForKey(z.keyCode) != null;

                int baseBg = bound
                        ? LexoraStyle.blendColors(LexoraStyle.COL_KEY_BG, LexoraStyle.COL_ACCENT, 0.55f)
                        : LexoraStyle.COL_KEY_BG;
                int hoverBg = bound
                        ? LexoraStyle.blendColors(LexoraStyle.COL_KEY_HOVER, LexoraStyle.COL_ACCENT, 0.7f)
                        : LexoraStyle.COL_KEY_HOVER;
                int bg = LexoraStyle.blendColors(baseBg, hoverBg, z.hoverAnim);
                LexoraStyle.drawSmoothRect(context, z.x, zy, z.w, z.h, z.radius, LexoraStyle.adjustAlpha(bg, ma));

                int textCol = LexoraStyle.blendColors(LexoraStyle.COL_TEXT_PRIMARY, LexoraStyle.COL_TEXT_WHITE, z.hoverAnim);
                float lw = LexoraStyle.font().getWidth(z.label, LexoraStyle.SIZE_TINY);
                LexoraStyle.font().draw(context.getMatrices(), z.label,
                        z.x + (z.w - lw) / 2f, zy + (z.h - LexoraStyle.SIZE_TINY) / 2f,
                        LexoraStyle.SIZE_TINY, LexoraStyle.adjustAlpha(textCol, ma));
            }
        }

        // кнопка "Назад" и прочие дочерние виджеты
        super.render(context, mouseX, mouseY, delta);

        // fade-выход (ESC или кнопка "Назад")
        if (transitioningOut) {
            long el = now - transitionStartMs;
            float p = Math.min(1f, el / (float) TRANSITION_MS);
            int a = (int) (LexoraStyle.easeOutQuart(p) * 255f);
            context.fill(0, 0, this.width, this.height, a << 24);
            if (p >= 1f) {
                Screen target = parent;
                transitioningOut = false;
                this.client.execute(() -> this.client.setScreen(target));
            }
        }
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static final class KeySpec {
        final int x, y, w, h, row, keyCode;
        final String label;
        float hoverAnim = 0f;

        KeySpec(int x, int y, int w, int h, String label, int keyCode, int row) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label; this.keyCode = keyCode; this.row = row;
        }
    }

    private static final class MouseZone {
        final int x, y, w, h, keyCode;
        final String label;
        final float radius;
        float hoverAnim = 0f;

        MouseZone(int x, int y, int w, int h, String label, int keyCode, float radius) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label; this.keyCode = keyCode; this.radius = radius;
        }
    }
}