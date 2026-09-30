package com.lexoravisauls.client.modules.swinganim;

import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class SwingAnimScreen extends Screen {

    private final SwingManager manager = SwingManager.getInstance();

    private static final int CARD_W = 168;
    private static final int CARD_H = 280;
    private static final int CARD_GAP = 12;

    // Редактор кривой Безье
    private boolean draggingStartHandle = false;
    private boolean draggingEndHandle = false;

    // Слайдер скорости
    private boolean draggingSpeed = false;

    // Слайдеры фаз
    private int draggingPhaseIndex = -1; // 0..8
    private boolean draggingIsStartPhase = true;

    // Поле ввода пресета
    private String newPresetName = "";
    private boolean inputFocused = false;
    private float presetScroll = 0f;

    private final Screen parent;

    public SwingAnimScreen(Screen parent) {
        super(Text.of("Swing Animation Editor"));
        this.parent = parent;
    }

    public SwingAnimScreen() {
        this(null);
    }

    @Override
    protected void init() {
        super.init();
        manager.getPresetManager().scanPresetDirectory();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        // Автоматическое сохранение при закрытии
        new SwingPresetFile("autosave").save(manager);
        if (this.client != null && this.parent != null) {
            this.client.setScreen(this.parent);
        } else {
            super.close();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Убираем фоновое затемнение и размытие мира
    }

    @Override
    protected void applyBlur() {
        // Отключаем размытие GameRenderer.renderBlur()
    }

    @Override
    protected void renderDarkening(DrawContext context) {
        // Отключаем затемнение текстурой
    }

    @Override
    protected void renderDarkening(DrawContext context, int x, int y, int width, int height) {
        // Отключаем затемнение текстурой
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Авто-взмах для живого превью (срабатывает после завершения взмаха)
        if (this.client != null && this.client.player != null) {
            if (!this.client.player.handSwinging && this.client.player.age % 10 == 0) {
                this.client.player.swingHand(Hand.MAIN_HAND);
            }
        }

        int totalW = CARD_W * 4 + CARD_GAP * 3;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - CARD_H) / 2;

        // Заголовок экрана
        float titleW = ModernClickGui.SFUI.getWidth("РЕДАКТОР АНИМАЦИИ ВЗМАХА", 10.0f);
        ModernClickGui.SFUI.draw(context, "РЕДАКТОР АНИМАЦИИ ВЗМАХА", (this.width - titleW) / 2f, startY - 26, 10.0f, 0xFFFFFFFF);

        String hint = "Подсказка: зажмите Shift при движении слайдера для синхронизации фаз";
        float hintW = ModernClickGui.SFUI.getWidth(hint, 7.0f);
        ModernClickGui.SFUI.draw(context, hint, (this.width - hintW) / 2f, startY + CARD_H + 8, 7.0f, 0xAA9999AA);

        // 1. Карточка Пресетов
        renderPresetsCard(context, startX, startY, mouseX, mouseY);

        // 2. Карточка Общих настроек (Безье + Скорость + Откат)
        renderSharedCard(context, startX + CARD_W + CARD_GAP, startY, mouseX, mouseY);

        // 3. Карточка Фазы Начала
        renderPhaseCard(context, startX + (CARD_W + CARD_GAP) * 2, startY, "Фаза начала", manager.getStartPhase(), true, mouseX, mouseY);

        // 4. Карточка Фазы Конца
        renderPhaseCard(context, startX + (CARD_W + CARD_GAP) * 3, startY, "Фаза конца", manager.getEndPhase(), false, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    // ── 1. КАРТОЧКА ПРЕСЕТОВ ──────────────────────────────────────────────────
    private void renderPresetsCard(DrawContext context, int x, int y, int mouseX, int mouseY) {
        renderCardBackground(context, x, y, CARD_W, CARD_H, "Пресеты");

        int listX = x + 8;
        int listY = y + 26;
        int listW = CARD_W - 16;
        int listH = CARD_H - 62;

        // Фон области списка
        RoundedRectShader.draw(context, listX, listY, listW, listH, 5.0f, 0xFF0D0D12);
        RoundedRectShader.drawOutline(context, listX, listY, listW, listH, 5.0f, 0.5f, 0x22FFFFFF);

        int itemY = listY + 4 - (int) presetScroll;

        // Встроенные пресеты
        for (SwingPreset preset : manager.getBuiltInPresets()) {
            if (itemY + 18 >= listY && itemY <= listY + listH - 18) {
                boolean active = preset.getName().equals(manager.getCurrentPresetName());
                boolean hovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= itemY && mouseY <= itemY + 16;

                if (active) {
                    RoundedRectShader.draw(context, listX + 3, itemY, listW - 6, 16, 4.0f, 0x334466FF);
                    RoundedRectShader.drawOutline(context, listX + 3, itemY, listW - 6, 16, 4.0f, 0.5f, 0x556688FF);
                } else if (hovered) {
                    RoundedRectShader.draw(context, listX + 3, itemY, listW - 6, 16, 4.0f, 0x18FFFFFF);
                }

                int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFE0E0E0 : 0xFFA0A0A8);
                ModernClickGui.SFUI.draw(context, preset.getDisplayName(), listX + 8, itemY + 4.5f, 7.5f, textColor);

                if (active) {
                    ModernClickGui.SFUI.draw(context, "✓", listX + listW - 16, itemY + 4.5f, 7.5f, 0xFF55FF77);
                }
            }
            itemY += 18;
        }

        // Кастомные пресеты
        List<SwingPresetFile> customFiles = manager.getPresetManager().getPresetFiles();
        for (SwingPresetFile file : customFiles) {
            if (file.getFileName().equalsIgnoreCase("autosave")) continue;

            if (itemY + 18 >= listY && itemY <= listY + listH - 18) {
                boolean active = file.getFileName().equals(manager.getCurrentPresetName());
                boolean hovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= itemY && mouseY <= itemY + 16;

                if (active) {
                    RoundedRectShader.draw(context, listX + 3, itemY, listW - 6, 16, 4.0f, 0x334466FF);
                    RoundedRectShader.drawOutline(context, listX + 3, itemY, listW - 6, 16, 4.0f, 0.5f, 0x556688FF);
                } else if (hovered) {
                    RoundedRectShader.draw(context, listX + 3, itemY, listW - 6, 16, 4.0f, 0x18FFFFFF);
                }

                int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFE0E0E0 : 0xFFA0A0A8);
                ModernClickGui.SFUI.draw(context, file.getFileName(), listX + 8, itemY + 4.5f, 7.5f, textColor);

                // Кнопка удаления (крестик)
                boolean deleteHovered = mouseX >= listX + listW - 18 && mouseX <= listX + listW - 4 && mouseY >= itemY && mouseY <= itemY + 16;
                int delColor = deleteHovered ? 0xFFFF5555 : (hovered ? 0x88FF6666 : 0x00000000);
                if (delColor != 0) {
                    ModernClickGui.SFUI.draw(context, "✕", listX + listW - 14, itemY + 4.5f, 7.0f, delColor);
                } else if (active) {
                    ModernClickGui.SFUI.draw(context, "✓", listX + listW - 16, itemY + 4.5f, 7.5f, 0xFF55FF77);
                }
            }
            itemY += 18;
        }

        // Поле ввода нового пресета
        int inputY = y + CARD_H - 28;
        int inputW = listW - 22;
        int inputH = 18;

        RoundedRectShader.draw(context, listX, inputY, inputW, inputH, 4.0f, inputFocused ? 0xFF14141E : 0xFF0D0D12);
        RoundedRectShader.drawOutline(context, listX, inputY, inputW, inputH, 4.0f, 0.5f, inputFocused ? 0x884488FF : 0x25FFFFFF);

        String showText = newPresetName.isEmpty() ? (inputFocused ? "" : "Название...") : newPresetName;
        int txtCol = newPresetName.isEmpty() ? 0x669999AA : 0xFFFFFFFF;
        ModernClickGui.SFUI.draw(context, showText, listX + 6, inputY + 5.5f, 7.0f, txtCol);

        // Кнопка добавления (+)
        int addBtnX = listX + inputW + 4;
        int addBtnW = 18;
        boolean addHover = mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= inputY && mouseY <= inputY + inputH;
        RoundedRectShader.draw(context, addBtnX, inputY, addBtnW, inputH, 4.0f, addHover ? 0xFF2A3A66 : 0xFF182038);
        RoundedRectShader.drawOutline(context, addBtnX, inputY, addBtnW, inputH, 4.0f, 0.5f, addHover ? 0x9955AAFF : 0x443377CC);
        ModernClickGui.SFUI.draw(context, "+", addBtnX + 5, inputY + 4.5f, 8.5f, 0xFFFFFFFF);
    }

    // ── 2. КАРТОЧКА ОБЩИХ НАСТРОЕК (ХОЛСТ БЕЗЬЕ + СКОРОСТЬ + ОТКАТ) ──────────
    private void renderSharedCard(DrawContext context, int x, int y, int mouseX, int mouseY) {
        renderCardBackground(context, x, y, CARD_W, CARD_H, "Общие");

        int bx = x + 10;
        int by = y + 36;
        int bw = CARD_W - 20;
        int bh = 110;

        ModernClickGui.SFUI.draw(context, "Кривая Безье", bx, y + 26, 7.5f, 0xFFAAAAAC);

        // Фоновый бокс холста Безье
        RoundedRectShader.draw(context, bx, by, bw, bh, 5.0f, 0xFF0B0B10);
        RoundedRectShader.drawOutline(context, bx, by, bw, bh, 5.0f, 0.5f, 0x30FFFFFF);

        // Обработка перетаскивания точек Безье
        if (draggingStartHandle) {
            float newX = screenToBezierX((float) mouseX, bx, bw);
            float newY = screenToBezierY((float) mouseY, by, bh);
            manager.setBezierStart(new Vec2f(newX, newY));
        } else if (draggingEndHandle) {
            float newX = screenToBezierX((float) mouseX, bx, bw);
            float newY = screenToBezierY((float) mouseY, by, bh);
            manager.setBezierEnd(new Vec2f(newX, newY));
        }

        // Вычисляем экранные координаты точек
        float p1X = bezierToScreenX(manager.getBezierStart().x, bx, bw);
        float p1Y = bezierToScreenY(manager.getBezierStart().y, by, bh);
        float p2X = bezierToScreenX(manager.getBezierEnd().x, bx, bw);
        float p2Y = bezierToScreenY(manager.getBezierEnd().y, by, bh);

        Vec2f p0 = new Vec2f(bezierToScreenX(0.0f, bx, bw), bezierToScreenY(1.0f, by, bh)); // Старт движения (v=1.0)
        Vec2f p3 = new Vec2f(bezierToScreenX(1.0f, bx, bw), bezierToScreenY(0.0f, by, bh)); // Конец движения (v=0.0)
        Vec2f p1 = new Vec2f(p1X, p1Y);
        Vec2f p2 = new Vec2f(p2X, p2Y);

        context.enableScissor(bx, by, bx + bw, by + bh);

        // Координатная сетка
        float baseY = bezierToScreenY(1.0f, by, bh);
        float topY = bezierToScreenY(0.0f, by, bh);
        float midX = bezierToScreenX(0.5f, bx, bw);

        drawThickLine(context, bx + 6, baseY, bx + bw - 6, baseY, 0.6f, 0x22FFFFFF);
        drawThickLine(context, bx + 6, topY, bx + bw - 6, topY, 0.6f, 0x22FFFFFF);
        drawThickLine(context, midX, by + 6, midX, by + bh - 6, 0.6f, 0x14FFFFFF);

        ModernClickGui.SFUI.draw(context, "0", bx + 4, baseY - 3.5f, 5.0f, 0x55FFFFFF);
        ModernClickGui.SFUI.draw(context, "1", bx + 4, topY - 3.5f, 5.0f, 0x55FFFFFF);

        // Направляющие линии от анкоров к точкам управления (полупрозрачный синий)
        drawThickLine(context, p0.x, p0.y, p1.x, p1.y, 0.8f, 0x662979FF);
        drawThickLine(context, p3.x, p3.y, p2.x, p2.y, 0.8f, 0x662979FF);

        // Отрисовка сплошной кривой Безье (чистый синий)
        drawBezierCurve(context, p0, p1, p2, p3, 2.0f, 0xFF2979FF, 64);

        // Анкоры P0 и P3 (небольшие маркеры)
        drawCircle(context, p0.x, p0.y, 1.8f, 0x99FFFFFF);
        drawCircle(context, p3.x, p3.y, 1.8f, 0x99FFFFFF);

        // Контрольная точка 1 (P1 - Синяя)
        boolean hover1 = Math.hypot(mouseX - p1.x, mouseY - p1.y) <= 8.5;
        float r1 = hover1 || draggingStartHandle ? 3.8f : 3.0f;
        drawCircle(context, p1.x, p1.y, r1 + 0.8f, 0x552979FF);
        drawCircle(context, p1.x, p1.y, r1, 0xFF2979FF);
        drawCircle(context, p1.x, p1.y, r1 * 0.45f, 0xFF0A0A10);

        // Контрольная точка 2 (P2 - Светло-синяя)
        boolean hover2 = Math.hypot(mouseX - p2.x, mouseY - p2.y) <= 8.5;
        float r2 = hover2 || draggingEndHandle ? 3.8f : 3.0f;
        drawCircle(context, p2.x, p2.y, r2 + 0.8f, 0x555599FF);
        drawCircle(context, p2.x, p2.y, r2, 0xFF5599FF);
        drawCircle(context, p2.x, p2.y, r2 * 0.45f, 0xFF0A0A10);

        context.disableScissor();

        // Настройки под холстом:
        // 1. Тумблер Отката анимации (Swing Back)
        int toggleY = by + bh + 13;
        ModernClickGui.SFUI.draw(context, "Откат взмаха", bx, toggleY + 2.5f, 7.5f, 0xFFE0E0E6);

        int swW = 24;
        int swH = 12;
        int swX = bx + bw - swW;
        boolean swHover = mouseX >= swX && mouseX <= swX + swW && mouseY >= toggleY && mouseY <= toggleY + swH;

        int swBg = manager.isSwingBack() ? (swHover ? 0xFF35C055 : 0xFF28A745) : (swHover ? 0xFF44444D : 0xFF333339);
        RoundedRectShader.draw(context, swX, toggleY, swW, swH, swH / 2f, swBg);
        RoundedRectShader.drawOutline(context, swX, toggleY, swW, swH, swH / 2f, 0.5f, 0x33FFFFFF);

        float knobX = manager.isSwingBack() ? swX + swW - swH + 1.5f : swX + 1.5f;
        drawCircle(context, knobX + (swH - 3) / 2f, toggleY + swH / 2f, (swH - 3) / 2f, 0xFFFFFFFF);

        // 2. Слайдер Скорости взмаха (Speed: 0.2x .. 4.0x)
        int speedY = toggleY + 21;
        ModernClickGui.SFUI.draw(context, "Скорость", bx, speedY, 7.5f, 0xFFE0E0E6);

        String speedStr = String.format("%.1fx", manager.getSpeed());
        float sStrW = ModernClickGui.SFUI.getWidth(speedStr, 7.5f);
        ModernClickGui.SFUI.draw(context, speedStr, bx + bw - sStrW, speedY, 7.5f, 0xFF2979FF);

        int barY = speedY + 11;
        int barH = 4;
        RoundedRectShader.draw(context, bx, barY, bw, barH, 2.0f, 0xFF1A1A22);

        float speedFrac = MathHelper.clamp((manager.getSpeed() - 0.2f) / 3.8f, 0.0f, 1.0f);
        if (speedFrac > 0) {
            RoundedRectShader.draw(context, bx, barY, bw * speedFrac, barH, 2.0f, 0xFF2979FF);
        }

        float thumbX = bx + bw * speedFrac;
        drawCircle(context, thumbX, barY + barH / 2f, 4.0f, 0xFFFFFFFF);

        if (draggingSpeed) {
            float val = 0.2f + MathHelper.clamp((mouseX - bx) / (float) bw, 0.0f, 1.0f) * 3.8f;
            val = Math.round(val * 10f) / 10f;
            manager.setSpeed(val);
        }

        // 3. Кнопка "🎲 Придумать" (Авто-настройка)
        int btnGenY = y + 218;
        int btnGenH = 20;
        boolean genHover = mouseX >= bx && mouseX <= bx + bw && mouseY >= btnGenY && mouseY <= btnGenY + btnGenH;
        RoundedRectShader.draw(context, bx, btnGenY, bw, btnGenH, 4.0f, genHover ? 0xFF24365D : 0xFF162038);
        RoundedRectShader.drawOutline(context, bx, btnGenY, bw, btnGenH, 4.0f, 0.5f, genHover ? 0xFF3B82F6 : 0x553B82F6);
        String genText = "🎲 Придумать";
        float genW = ModernClickGui.SFUI.getWidth(genText, 7.5f);
        ModernClickGui.SFUI.draw(context, genText, bx + (bw - genW) / 2f, btnGenY + 5.5f, 7.5f, 0xFFFFFFFF);

        // 4. Кнопка "↺ Сброс" (Стандарт)
        int btnResetY = y + 244;
        int btnResetH = 20;
        boolean resetHover = mouseX >= bx && mouseX <= bx + bw && mouseY >= btnResetY && mouseY <= btnResetY + btnResetH;
        RoundedRectShader.draw(context, bx, btnResetY, bw, btnResetH, 4.0f, resetHover ? 0xFF2A2A36 : 0xFF181820);
        RoundedRectShader.drawOutline(context, bx, btnResetY, bw, btnResetH, 4.0f, 0.5f, resetHover ? 0x66FFFFFF : 0x22FFFFFF);
        String resetText = "↺ Сбросить к стандарту";
        float resetW = ModernClickGui.SFUI.getWidth(resetText, 7.0f);
        ModernClickGui.SFUI.draw(context, resetText, bx + (bw - resetW) / 2f, btnResetY + 6.0f, 7.0f, resetHover ? 0xFFFFFFFF : 0xFFA0A0A8);
    }

    // ── 3 & 4. КАРТОЧКА ФАЗЫ (НАЧАЛО И КОНЕЦ) ─────────────────────────────────
    private void renderPhaseCard(DrawContext context, int x, int y, String title, SwingPhase phase, boolean isStartPhase, int mouseX, int mouseY) {
        renderCardBackground(context, x, y, CARD_W, CARD_H, title);

        int px = x + 10;
        int py = y + 27;
        int pw = CARD_W - 20;

        String[] names = {
                "Anchor X", "Anchor Y", "Anchor Z",
                "Move X", "Move Y", "Move Z",
                "Rotate X", "Rotate Y", "Rotate Z"
        };

        float[] values = {
                phase.anchorX, phase.anchorY, phase.anchorZ,
                phase.moveX, phase.moveY, phase.moveZ,
                phase.rotateX, phase.rotateY, phase.rotateZ
        };

        float[] mins = {-5.0f, -5.0f, -5.0f, -5.0f, -5.0f, -3.0f, -360.0f, -360.0f, -360.0f};
        float[] maxs = {5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 3.0f, 360.0f, 360.0f, 360.0f};
        float[] steps = {0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 15.0f, 15.0f, 15.0f};

        for (int i = 0; i < 9; i++) {
            int rowY = py + i * 27;

            // Название слева
            ModernClickGui.SFUI.draw(context, names[i], px, rowY, 7.2f, 0xFFCACACC);

            // Значение справа
            String valStr;
            if (i >= 6) {
                valStr = Math.round(values[i]) + "°";
            } else {
                valStr = String.format("%.2f", values[i]);
            }
            float valW = ModernClickGui.SFUI.getWidth(valStr, 7.2f);
            ModernClickGui.SFUI.draw(context, valStr, px + pw - valW, rowY, 7.2f, 0xFF88BBFF);

            // Полоска слайдера
            int trackY = rowY + 11;
            int trackH = 3;
            RoundedRectShader.draw(context, px, trackY, pw, trackH, 1.5f, 0xFF191922);

            float frac = MathHelper.clamp((values[i] - mins[i]) / (maxs[i] - mins[i]), 0.0f, 1.0f);
            if (frac > 0) {
                RoundedRectShader.draw(context, px, trackY, pw * frac, trackH, 1.5f, 0xFF4F46E5);
            }

            float knobX = px + pw * frac;
            drawCircle(context, knobX, trackY + trackH / 2f, 3.5f, 0xFFFFFFFF);

            // Обработка перетаскивания слайдера
            if (draggingPhaseIndex == i && draggingIsStartPhase == isStartPhase) {
                float rawVal = mins[i] + MathHelper.clamp((mouseX - px) / (float) pw, 0.0f, 1.0f) * (maxs[i] - mins[i]);
                float step = steps[i];
                float snappedVal = Math.round(rawVal / step) * step;
                snappedVal = MathHelper.clamp(snappedVal, mins[i], maxs[i]);

                setPhaseValue(phase, i, snappedVal);

                // Синхронизация через Shift со второй фазой!
                if (hasShiftDown()) {
                    SwingPhase otherPhase = isStartPhase ? manager.getEndPhase() : manager.getStartPhase();
                    setPhaseValue(otherPhase, i, snappedVal);
                }
            }
        }
    }

    private void setPhaseValue(SwingPhase phase, int index, float val) {
        switch (index) {
            case 0 -> phase.anchorX = val;
            case 1 -> phase.anchorY = val;
            case 2 -> phase.anchorZ = val;
            case 3 -> phase.moveX = val;
            case 4 -> phase.moveY = val;
            case 5 -> phase.moveZ = val;
            case 6 -> phase.rotateX = val;
            case 7 -> phase.rotateY = val;
            case 8 -> phase.rotateZ = val;
        }
    }

    // ── ОБЩИЙ РЕНДЕР КАРТОЧКИ ────────────────────────────────────────────────
    private void renderCardBackground(DrawContext context, int x, int y, int w, int h, String title) {
        RoundedRectShader.draw(context, x, y, w, h, 7.0f, 0xDC111116);
        RoundedRectShader.drawOutline(context, x, y, w, h, 7.0f, 0.6f, 0x2AFFFFFF);

        // Заголовок
        ModernClickGui.SFUI.draw(context, title, x + 10, y + 8, 8.0f, 0xFFEEEEEE);

        // Разделительная линия
        RoundedRectShader.draw(context, x + 8, y + 21, w - 16, 0.5f, 0.25f, 0x20FFFFFF);
    }

    // ── ОБРАБОТКА МЫШИ ────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int totalW = CARD_W * 4 + CARD_GAP * 3;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - CARD_H) / 2;

        // 1. Клики по карточке пресетов
        int listX = startX + 8;
        int listY = startY + 26;
        int listW = CARD_W - 16;
        int listH = CARD_H - 62;

        int itemY = listY + 4 - (int) presetScroll;

        // Встроенные пресеты
        for (SwingPreset preset : manager.getBuiltInPresets()) {
            if (mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= itemY && mouseY <= itemY + 16) {
                manager.applyBuiltInPreset(preset);
                if (this.client != null && this.client.player != null) {
                    this.client.player.swingHand(Hand.MAIN_HAND);
                }
                return true;
            }
            itemY += 18;
        }

        // Кастомные пресеты
        for (SwingPresetFile file : manager.getPresetManager().getPresetFiles()) {
            if (file.getFileName().equalsIgnoreCase("autosave")) continue;

            if (mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= itemY && mouseY <= itemY + 16) {
                // Клик по крестику удаления
                if (mouseX >= listX + listW - 18 && mouseX <= listX + listW - 4) {
                    file.delete();
                    manager.getPresetManager().scanPresetDirectory();
                    return true;
                }

                // Клик по загрузке пресета
                file.load(manager);
                if (this.client != null && this.client.player != null) {
                    this.client.player.swingHand(Hand.MAIN_HAND);
                }
                return true;
            }
            itemY += 18;
        }

        // Поле ввода пресета
        int inputY = startY + CARD_H - 28;
        int inputW = listW - 22;
        if (mouseX >= listX && mouseX <= listX + inputW && mouseY >= inputY && mouseY <= inputY + 18) {
            inputFocused = true;
            return true;
        } else {
            inputFocused = false;
        }

        // Кнопка (+) создания пресета
        int addBtnX = listX + inputW + 4;
        if (mouseX >= addBtnX && mouseX <= addBtnX + 18 && mouseY >= inputY && mouseY <= inputY + 18) {
            createPreset();
            return true;
        }

        // 2. Клики по карточке Общих настроек
        int sharedX = startX + CARD_W + CARD_GAP;
        int bx = sharedX + 10;
        int by = startY + 36;
        int bw = CARD_W - 20;
        int bh = 110;

        float p1X = bezierToScreenX(manager.getBezierStart().x, bx, bw);
        float p1Y = bezierToScreenY(manager.getBezierStart().y, by, bh);
        float p2X = bezierToScreenX(manager.getBezierEnd().x, bx, bw);
        float p2Y = bezierToScreenY(manager.getBezierEnd().y, by, bh);

        if (Math.hypot(mouseX - p1X, mouseY - p1Y) <= 8.5) {
            draggingStartHandle = true;
            return true;
        }
        if (Math.hypot(mouseX - p2X, mouseY - p2Y) <= 8.5) {
            draggingEndHandle = true;
            return true;
        }
        if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
            double d1 = Math.hypot(mouseX - p1X, mouseY - p1Y);
            double d2 = Math.hypot(mouseX - p2X, mouseY - p2Y);
            if (d1 < d2) {
                draggingStartHandle = true;
            } else {
                draggingEndHandle = true;
            }
            return true;
        }

        // Тумблер отката
        int toggleY = by + bh + 13;
        int swW = 24;
        int swH = 12;
        int swX = bx + bw - swW;
        if (mouseX >= swX && mouseX <= swX + swW && mouseY >= toggleY && mouseY <= toggleY + swH) {
            manager.setSwingBack(!manager.isSwingBack());
            return true;
        }

        // Слайдер скорости (0.2x .. 4.0x)
        int speedY = toggleY + 21;
        int barY = speedY + 8;
        if (mouseX >= bx && mouseX <= bx + bw && mouseY >= barY - 4 && mouseY <= barY + 12) {
            draggingSpeed = true;
            float val = 0.2f + MathHelper.clamp((float)(mouseX - bx) / (float) bw, 0.0f, 1.0f) * 3.8f;
            val = Math.round(val * 10f) / 10f;
            manager.setSpeed(val);
            return true;
        }

        // Кнопка "🎲 Придумать" (Авто-настройка)
        int btnGenY = startY + 218;
        int btnGenH = 20;
        if (mouseX >= bx && mouseX <= bx + bw && mouseY >= btnGenY && mouseY <= btnGenY + btnGenH) {
            manager.generateRandomAnimation();
            if (this.client != null && this.client.player != null) {
                this.client.player.swingHand(Hand.MAIN_HAND);
            }
            return true;
        }

        // Кнопка "↺ Сбросить к стандарту"
        int btnResetY = startY + 244;
        int btnResetH = 20;
        if (mouseX >= bx && mouseX <= bx + bw && mouseY >= btnResetY && mouseY <= btnResetY + btnResetH) {
            manager.resetToStandard();
            if (this.client != null && this.client.player != null) {
                this.client.player.swingHand(Hand.MAIN_HAND);
            }
            return true;
        }

        // 3. Клики по слайдерам Фазы Начала
        int startPhaseX = startX + (CARD_W + CARD_GAP) * 2 + 10;
        int phaseY = startY + 27;
        int pw = CARD_W - 20;

        for (int i = 0; i < 9; i++) {
            int trackY = phaseY + i * 27 + 8;
            if (mouseX >= startPhaseX && mouseX <= startPhaseX + pw && mouseY >= trackY - 4 && mouseY <= trackY + 10) {
                draggingPhaseIndex = i;
                draggingIsStartPhase = true;
                return true;
            }
        }

        // 4. Клики по слайдерам Фазы Конца
        int endPhaseX = startX + (CARD_W + CARD_GAP) * 3 + 10;
        for (int i = 0; i < 9; i++) {
            int trackY = phaseY + i * 27 + 8;
            if (mouseX >= endPhaseX && mouseX <= endPhaseX + pw && mouseY >= trackY - 4 && mouseY <= trackY + 10) {
                draggingPhaseIndex = i;
                draggingIsStartPhase = false;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasDragging = draggingStartHandle || draggingEndHandle || draggingSpeed || (draggingPhaseIndex != -1);
        draggingStartHandle = false;
        draggingEndHandle = false;
        draggingSpeed = false;
        draggingPhaseIndex = -1;
        if (wasDragging) {
            new SwingPresetFile("autosave").save(manager);
            if (this.client != null && this.client.player != null) {
                this.client.player.swingHand(Hand.MAIN_HAND);
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int totalW = CARD_W * 4 + CARD_GAP * 3;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - CARD_H) / 2;

        if (mouseX >= startX && mouseX <= startX + CARD_W && mouseY >= startY && mouseY <= startY + CARD_H) {
            presetScroll = Math.max(0, presetScroll - (float) verticalAmount * 16.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                createPreset();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!newPresetName.isEmpty()) {
                    newPresetName = newPresetName.substring(0, newPresetName.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                inputFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused && newPresetName.length() < 16) {
            if (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-' || chr == ' ') {
                newPresetName += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    private void createPreset() {
        String trimmed = newPresetName.trim();
        if (!trimmed.isEmpty()) {
            manager.resetToStandard();
            manager.getPresetManager().createPreset(trimmed, manager);
            manager.setCurrentPresetName(trimmed);
            newPresetName = "";
            inputFocused = false;
            if (this.client != null && this.client.player != null) {
                this.client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    // ── ПРЕОБРАЗОВАНИЕ КООРДИНАТ БЕЗЬЕ ───────────────────────────────────────
    private static float bezierToScreenX(float u, float bx, float bw) {
        float pad = 14f;
        float w = bw - pad * 2f;
        return bx + pad + MathHelper.clamp(u, 0.0f, 1.0f) * w;
    }

    private static float bezierToScreenY(float v, float by, float bh) {
        float padY = 14f;
        float uh = bh - padY * 2f;
        return by + padY + 0.25f * uh + v * (0.50f * uh);
    }

    private static float screenToBezierX(float sx, float bx, float bw) {
        float pad = 14f;
        float w = bw - pad * 2f;
        return MathHelper.clamp((sx - (bx + pad)) / w, 0.0f, 1.0f);
    }

    private static float screenToBezierY(float sy, float by, float bh) {
        float padY = 14f;
        float uh = bh - padY * 2f;
        float v = (sy - (by + padY + 0.25f * uh)) / (0.50f * uh);
        return MathHelper.clamp(v, -0.6f, 1.4f);
    }

    // ── ГРАФИЧЕСКИЕ ПРИМИТИВЫ ─────────────────────────────────────────────────
    private static void drawCircle(DrawContext context, float cx, float cy, float radius, int color) {
        RoundedRectShader.draw(context, cx - radius, cy - radius, radius * 2f, radius * 2f, radius, color);
    }

    private static void drawThickLine(DrawContext context, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * (thickness * 0.5f);
        float ny = dx / len * (thickness * 0.5f);

        float a = ((color >>> 24) & 0xFF) / 255.0f;
        float r = ((color >>> 16) & 0xFF) / 255.0f;
        float g = ((color >>> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        context.draw();
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x1 + nx, y1 + ny, 0f).color(r, g, b, a);
        buffer.vertex(matrix, x1 - nx, y1 - ny, 0f).color(r, g, b, a);
        buffer.vertex(matrix, x2 - nx, y2 - ny, 0f).color(r, g, b, a);

        buffer.vertex(matrix, x1 + nx, y1 + ny, 0f).color(r, g, b, a);
        buffer.vertex(matrix, x2 - nx, y2 - ny, 0f).color(r, g, b, a);
        buffer.vertex(matrix, x2 + nx, y2 + ny, 0f).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        context.draw();
    }

    private static void drawBezierCurve(DrawContext context, Vec2f p0, Vec2f p1, Vec2f p2, Vec2f p3, float thickness, int color, int segments) {
        float a = ((color >>> 24) & 0xFF) / 255.0f;
        float r = ((color >>> 16) & 0xFF) / 255.0f;
        float g = ((color >>> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        context.draw();
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        Vec2f[] pts = new Vec2f[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            pts[i] = new Vec2f(cubicBezier(t, p0.x, p1.x, p2.x, p3.x), cubicBezier(t, p0.y, p1.y, p2.y, p3.y));
        }

        float halfW = thickness * 0.5f;
        for (int i = 0; i < segments; i++) {
            Vec2f curr = pts[i];
            Vec2f next = pts[i + 1];
            float dx = next.x - curr.x;
            float dy = next.y - curr.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) continue;
            float nx = -dy / len * halfW;
            float ny = dx / len * halfW;

            buffer.vertex(matrix, curr.x + nx, curr.y + ny, 0f).color(r, g, b, a);
            buffer.vertex(matrix, curr.x - nx, curr.y - ny, 0f).color(r, g, b, a);
            buffer.vertex(matrix, next.x - nx, next.y - ny, 0f).color(r, g, b, a);

            buffer.vertex(matrix, curr.x + nx, curr.y + ny, 0f).color(r, g, b, a);
            buffer.vertex(matrix, next.x - nx, next.y - ny, 0f).color(r, g, b, a);
            buffer.vertex(matrix, next.x + nx, next.y + ny, 0f).color(r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        context.draw();
    }

    private static float cubicBezier(float t, float p0, float p1, float p2, float p3) {
        float u = 1.0F - t;
        float tt = t * t;
        float uu = u * u;
        return uu * u * p0 + 3.0F * uu * t * p1 + 3.0F * u * tt * p2 + tt * t * p3;
    }
}
