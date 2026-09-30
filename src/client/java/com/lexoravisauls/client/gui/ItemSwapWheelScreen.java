package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.core.BindManager;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.modern.ModernGuiRender;
import com.lexoravisauls.client.inventorymanager.ItemIdentity;
import com.lexoravisauls.client.inventorymanager.LoadoutSlotData;
import com.lexoravisauls.client.modules.ItemSwap;
import com.lexoravisauls.client.utils.ConfigManager;
import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemSwapWheelScreen — стильное круговое меню-бублик на 3 сектора в стиле Lexora.
 *
 * Особенности:
 *  - Форма бублика (donut ring) без громоздких квадратов и лишнего текста.
 *  - 3 сектора:
 *      Sector 1: Вверх-вправо (0°..120°)
 *      Sector 2: Вниз (120°..240°) — при движении мыши вниз гарантированно выбирается нижний предмет
 *      Sector 0: Вверх-влево (240°..360°)
 *  - Аддитивное неоновое свечение сектора при наведении (стиль GUI Lexora).
 *  - Свап происходит СТРОГО при отпускании зажатой клавиши бинда.
 *  - ЛКМ по сектору открывает реальную сетку инвентаря игрока для выбора предмета.
 *  - ПКМ по сектору очищает привязку ("Пусто").
 */
public class ItemSwapWheelScreen extends Screen {

    private final int bindKey;
    private int hoveredSector = -1;
    private int configuringSector = -1; // индекс сектора, для которого открыт инвентарь (-1 если закрыт)
    private float openProgress = 0.0f;
    private boolean closing = false;
    private boolean wasBindHeld = false;

    private static final int SECTOR_COUNT = 3;

    public static class WheelSlotData {
        public String displayName = "Пусто";
        public String itemId = "";
        public String customName = "";
        public String skinKey = "";
        public String skinName = "";
        public String skinId = "";
        public String skinTextureValue = "";
        public String skinTextureSignature = "";
        public String potionKey = "";
        public String modelKey = "";
        public String loreKey = "";
        public boolean isPreset = false;

        public boolean isEmpty() {
            return displayName == null || displayName.isEmpty() || displayName.equalsIgnoreCase("Пусто");
        }
    }

    private static final WheelSlotData[] wheelSlots = new WheelSlotData[SECTOR_COUNT];
    private static final ItemStack[] cachedWheelStacks = new ItemStack[SECTOR_COUNT];

    // Геометрия бублика
    private static final float RADIUS_OUTER = 80.0f;
    private static final float RADIUS_INNER = 42.0f;
    private static final float RADIUS_ITEMS = 60.0f;
    private static final float GLOW_WIDTH   = 14.0f;
    private static final int GLOW_MAX_ALPHA = 130;

    // Цвета в стиле GUI Lexora
    private static final int COLOR_BG_RING     = 0xD010131E;
    private static final int COLOR_BG_INNER    = 0xEE0B0D16;
    private static final int COLOR_ACCENT      = 0xFF8A8FFF;
    private static final int COLOR_ACCENT_FILL = 0x558A8FFF;
    private static final int COLOR_DIVIDER     = 0x2AFFFFFF;
    private static final int COLOR_BORDER      = 0x35FFFFFF;

    private final float[] sectorGlowAnim = new float[SECTOR_COUNT];
    private static final float SECTOR_GLOW_SPEED = 0.35f;

    public ItemSwapWheelScreen() {
        this(resolveCurrentBindKey());
    }

    public ItemSwapWheelScreen(int bindKey) {
        super(Text.literal("Item Swap Wheel"));
        this.bindKey = bindKey;
    }

    @Override
    protected void init() {
        super.init();
        openProgress = 0.0f;
        closing = false;
        configuringSector = -1;
        wasBindHeld = isBindStillHeld();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Проверяем удержание клавиши бинда
        boolean held = isBindStillHeld();
        if (held) {
            wasBindHeld = true;
        }

        // Свап происходит СТРОГО в момент отпускания бинда:
        if (!closing && configuringSector == -1) {
            if (wasBindHeld && !held) {
                wasBindHeld = false;
                onBindReleased();
                return;
            }
        }

        // Анимация открытия / закрытия
        float target = closing ? 0.0f : 1.0f;
        float speed = closing ? 0.45f : 0.35f;
        openProgress += (target - openProgress) * speed;
        if (Math.abs(openProgress - target) < 0.01f) openProgress = target;

        if (closing && openProgress <= 0.01f) {
            this.close();
            return;
        }

        // Легкое затемнение фона мира
        int dimAlpha = (int) (90 * openProgress);
        RoundedRectShader.draw(context, 0, 0, this.width, this.height, 0f, (dimAlpha << 24) | 0x060810);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Вычисление угла мыши (0° = 12 часов, 90° = 3 часа, 180° = 6 часов вниз, 270° = 9 часов)
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (configuringSector == -1 && !closing) {
            if (dist > 20.0) {
                double mouseAngle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
                if (mouseAngle < 0) mouseAngle += 360.0;

                // 3 сектора по 120 градусов:
                // [120° .. 240°] -> Вниз (Sector 2)
                // [240° .. 360°] -> Вверх-влево (Sector 0)
                // [0° .. 120°]   -> Вверх-вправо (Sector 1)
                if (mouseAngle >= 120.0 && mouseAngle < 240.0) {
                    hoveredSector = 2; // Вниз
                } else if (mouseAngle >= 240.0 && mouseAngle < 360.0) {
                    hoveredSector = 0; // Вверх-влево
                } else {
                    hoveredSector = 1; // Вверх-вправо
                }
            } else {
                hoveredSector = -1; // В центре бублика
            }
        }

        // Анимация плавного свечения секторов
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float t = (i == hoveredSector && configuringSector == -1) ? 1.0f : 0.0f;
            sectorGlowAnim[i] += (t - sectorGlowAnim[i]) * SECTOR_GLOW_SPEED;
            if (Math.abs(sectorGlowAnim[i] - t) < 0.01f) sectorGlowAnim[i] = t;
        }

        // Масштабирование бублика вокруг центра
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(cx, cy, 0);
        matrices.scale(openProgress, openProgress, 1.0f);
        matrices.translate(-cx, -cy, 0);

        // 1. Отрисовка бублика с неоновым свечением
        drawDonutRing(context, cx, cy);

        // 2. Отрисовка предметов в секторах бублика
        drawSectorItems(context, cx, cy);

        matrices.pop();

        // 3. Тултип наведенного предмета
        if (configuringSector == -1 && hoveredSector != -1 && !closing) {
            WheelSlotData data = getWheelSlot(hoveredSector);
            if (!data.isEmpty()) {
                context.drawTooltip(this.textRenderer, Text.literal(data.displayName), mouseX, mouseY);
            }
        }

        // 4. Окно выбора предмета из инвентаря игрока (если кликнули ЛКМ по сектору)
        if (configuringSector >= 0) {
            renderInventoryPicker(context, cx, cy, mouseX, mouseY);
        }
    }

    /**
     * Отрисовка самого бублика (donut ring) со сглаживанием, границами и неоновым свечением.
     */
    private void drawDonutRing(DrawContext context, int cx, int cy) {
        context.draw();

        MatrixStack matrices = context.getMatrices();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        // 1. Тело бублика: 120 сегментов треугольников между innerR и outerR
        BufferBuilder ringBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int segments = 120;
        float segAngle = 360f / segments;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) Math.toRadians(i * segAngle - 90);
            float a1 = (float) Math.toRadians((i + 1) * segAngle - 90);

            float x0i = cx + (float) (Math.cos(a0) * RADIUS_INNER);
            float y0i = cy + (float) (Math.sin(a0) * RADIUS_INNER);
            float x0o = cx + (float) (Math.cos(a0) * RADIUS_OUTER);
            float y0o = cy + (float) (Math.sin(a0) * RADIUS_OUTER);
            float x1o = cx + (float) (Math.cos(a1) * RADIUS_OUTER);
            float y1o = cy + (float) (Math.sin(a1) * RADIUS_OUTER);
            float x1i = cx + (float) (Math.cos(a1) * RADIUS_INNER);
            float y1i = cy + (float) (Math.sin(a1) * RADIUS_INNER);

            float midAngle = (i + 0.5f) * segAngle;
            int s;
            if (midAngle >= 120f && midAngle < 240f) s = 2; // Вниз
            else if (midAngle >= 240f && midAngle < 360f) s = 0; // Вверх-влево
            else s = 1; // Вверх-вправо

            float glowT = sectorGlowAnim[s];
            int fillColor = glowT > 0.001f ? blendColors(COLOR_BG_RING, COLOR_ACCENT_FILL, glowT) : COLOR_BG_RING;
            float[] c = argbToFloats(fillColor);

            addVertex(ringBuf, mat, x0i, y0i, c);
            addVertex(ringBuf, mat, x0o, y0o, c);
            addVertex(ringBuf, mat, x1o, y1o, c);

            addVertex(ringBuf, mat, x0i, y0i, c);
            addVertex(ringBuf, mat, x1o, y1o, c);
            addVertex(ringBuf, mat, x1i, y1i, c);
        }

        var builtRing = ringBuf.endNullable();
        if (builtRing != null) {
            BufferRenderer.drawWithGlobalProgram(builtRing);
        }

        // 2. Внешнее аддитивное неоновое свечение активного сектора (стиль GUI)
        RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
        BufferBuilder glowBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        boolean anyGlow = false;

        for (int s = 0; s < SECTOR_COUNT; s++) {
            float glowT = sectorGlowAnim[s];
            if (glowT <= 0.02f) continue;
            anyGlow = true;

            // Углы секторов в координатах trig (минус 90 для 12 часов)
            float startAngle;
            float endAngle;
            if (s == 1) { // 0°..120°
                startAngle = (float) Math.toRadians(-90);
                endAngle = (float) Math.toRadians(30);
            } else if (s == 2) { // 120°..240°
                startAngle = (float) Math.toRadians(30);
                endAngle = (float) Math.toRadians(150);
            } else { // 240°..360°
                startAngle = (float) Math.toRadians(150);
                endAngle = (float) Math.toRadians(270);
            }

            int glowSegs = 20;
            for (int seg = 0; seg < glowSegs; seg++) {
                float a0 = startAngle + (endAngle - startAngle) * (seg / (float) glowSegs);
                float a1 = startAngle + (endAngle - startAngle) * ((seg + 1) / (float) glowSegs);

                float x0o = cx + (float) (Math.cos(a0) * RADIUS_OUTER);
                float y0o = cy + (float) (Math.sin(a0) * RADIUS_OUTER);
                float x1o = cx + (float) (Math.cos(a1) * RADIUS_OUTER);
                float y1o = cy + (float) (Math.sin(a1) * RADIUS_OUTER);
                float xGo = cx + (float) (Math.cos(a0) * (RADIUS_OUTER + GLOW_WIDTH));
                float yGo = cy + (float) (Math.sin(a0) * (RADIUS_OUTER + GLOW_WIDTH));
                float x1Go = cx + (float) (Math.cos(a1) * (RADIUS_OUTER + GLOW_WIDTH));
                float y1Go = cy + (float) (Math.sin(a1) * (RADIUS_OUTER + GLOW_WIDTH));

                float[] glowInner = argbToFloats(withAlpha(COLOR_ACCENT, (int) (GLOW_MAX_ALPHA * glowT)));
                float[] glowOuter = argbToFloats(withAlpha(COLOR_ACCENT, 0));

                addVertex(glowBuf, mat, x0o, y0o, glowInner);
                addVertex(glowBuf, mat, xGo, yGo, glowOuter);
                addVertex(glowBuf, mat, x1Go, y1Go, glowOuter);

                addVertex(glowBuf, mat, x0o, y0o, glowInner);
                addVertex(glowBuf, mat, x1Go, y1Go, glowOuter);
                addVertex(glowBuf, mat, x1o, y1o, glowInner);
            }
        }

        if (anyGlow) {
            var builtGlow = glowBuf.endNullable();
            if (builtGlow != null) {
                BufferRenderer.drawWithGlobalProgram(builtGlow);
            }
        }

        // 3. Круговые контуры и радиальные разделители
        RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder lineBuf = RenderSystem.renderThreadTesselator().begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        float[] borderColor = argbToFloats(COLOR_BORDER);
        float[] divColor = argbToFloats(COLOR_DIVIDER);

        // Внешний и внутренний ободки
        addCircleOutline(lineBuf, mat, cx, cy, RADIUS_OUTER, borderColor, 120);
        addCircleOutline(lineBuf, mat, cx, cy, RADIUS_INNER, borderColor, 90);

        // 3 радиальных разделителя (на 0°, 120°, 240°)
        addRadialLine(lineBuf, mat, cx, cy, RADIUS_INNER, RADIUS_OUTER, (float) Math.toRadians(-90), divColor);
        addRadialLine(lineBuf, mat, cx, cy, RADIUS_INNER, RADIUS_OUTER, (float) Math.toRadians(30), divColor);
        addRadialLine(lineBuf, mat, cx, cy, RADIUS_INNER, RADIUS_OUTER, (float) Math.toRadians(150), divColor);

        var builtLines = lineBuf.endNullable();
        if (builtLines != null) {
            BufferRenderer.drawWithGlobalProgram(builtLines);
        }

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        context.draw();

        // 4. Внутренний круг (дырка бублика) со стеклом
        float innerDiameter = RADIUS_INNER * 2f;
        ModernGuiRender.drawLiquidGlass(
                context,
                cx - RADIUS_INNER, cy - RADIUS_INNER,
                innerDiameter, innerDiameter,
                RADIUS_INNER,
                10f,
                COLOR_BG_INNER
        );
    }

    /**
     * Отрисовка предметов прямо внутри секторов бублика (без текста и квадратов).
     */
    private void drawSectorItems(DrawContext context, int cx, int cy) {
        // Позиции предметов по радиусу RADIUS_ITEMS (середина кольца бублика):
        // Sector 0 (Top-Left, 300° clock = 210° trig): cx - 52, cy - 30
        // Sector 1 (Top-Right, 60° clock = 330° trig): cx + 52, cy - 30
        // Sector 2 (Bottom, 180° clock = 90° trig):    cx,      cy + 60
        int[][] nodeCoords = {
                { cx - 52, cy - 30 }, // Sector 0
                { cx + 52, cy - 30 }, // Sector 1
                { cx,      cy + 60 }  // Sector 2
        };

        for (int i = 0; i < SECTOR_COUNT; i++) {
            int nx = nodeCoords[i][0];
            int ny = nodeCoords[i][1];

            ItemStack stack = getStackForSector(i);

            float glowT = sectorGlowAnim[i];

            // Если сектор активен — под предметом появляется мягкий световой круг
            if (glowT > 0.05f) {
                int glowCol = withAlpha(COLOR_ACCENT, (int) (70 * glowT));
                RoundedRectShader.draw(context, nx - 12, ny - 12, 24, 24, 12f, glowCol);
            }

            if (!stack.isEmpty()) {
                // Иконка предмета
                context.drawItem(stack, nx - 8, ny - 8);
                context.drawStackOverlay(this.textRenderer, stack, nx - 8, ny - 8);
            } else {
                // Пустой слот: минималистичный кружок с плюсиком
                RoundedRectShader.draw(context, nx - 9, ny - 9, 18, 18, 9f, 0x33FFFFFF);
                context.drawTextWithShadow(this.textRenderer, "+", nx - 3, ny - 4,
                        glowT > 0.1f ? COLOR_ACCENT : 0x88FFFFFF);
            }
        }
    }

    /**
     * Отрисовка полноценного интерфейса инвентаря игрока для выбора предмета.
     */
    private void renderInventoryPicker(DrawContext context, int cx, int cy, int mouseX, int mouseY) {
        int panelW = 186;
        int panelH = 154;
        int px = cx - panelW / 2;
        int py = cy - panelH / 2;

        // Затемнение
        RoundedRectShader.draw(context, 0, 0, this.width, this.height, 0f, 0x99000000);

        // Панель инвентаря
        RoundedRectShader.draw(context, px, py, panelW, panelH, 8.0f, 0xF50C0E17);
        RoundedRectShader.drawOutline(context, px, py, panelW, panelH, 8.0f, 1.0f, 0x558A8FFF);

        // Заголовок
        context.drawTextWithShadow(this.textRenderer, "Выберите предмет", px + 9, py + 8, COLOR_ACCENT);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int slotSize = 18;
        int startX = px + 12;

        // 1. Верхний ряд: быстрые PvP пресеты (Тотем, Шар, Щит, Чарка, Перл, Зелье)
        int presetY = py + 22;
        List<ItemOption> presets = getPresetOptions();
        for (int i = 0; i < presets.size() && i < 9; i++) {
            int sx = startX + i * slotSize;
            ItemOption opt = presets.get(i);
            boolean hov = mouseX >= sx && mouseX < sx + 17 && mouseY >= presetY && mouseY < presetY + 17;

            RoundedRectShader.draw(context, sx, presetY, 17, 17, 3.0f, hov ? 0x66334466 : 0x33181C2A);
            RoundedRectShader.drawOutline(context, sx, presetY, 17, 17, 3.0f, 0.8f, hov ? COLOR_ACCENT : 0x22FFFFFF);
            context.drawItem(opt.stack, sx + 1, presetY + 1);

            if (hov) {
                context.drawTooltip(this.textRenderer, Text.literal(opt.name), mouseX, mouseY);
            }
        }

        // Разделитель
        RoundedRectShader.draw(context, px + 8, presetY + 20, panelW - 16, 1, 0f, 0x25FFFFFF);

        // 2. Основной инвентарь игрока: 3 ряда по 9 слотов (слоты 9..35)
        int invStartY = presetY + 24;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = 9 + row * 9 + col;
                int sx = startX + col * slotSize;
                int sy = invStartY + row * slotSize;

                ItemStack stack = mc.player.getInventory().getStack(slotIdx);
                boolean hov = mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 17;

                RoundedRectShader.draw(context, sx, sy, 17, 17, 3.0f, hov ? 0x66334466 : 0x33141624);
                RoundedRectShader.drawOutline(context, sx, sy, 17, 17, 3.0f, 0.8f, hov ? COLOR_ACCENT : 0x20FFFFFF);

                if (!stack.isEmpty()) {
                    context.drawItem(stack, sx + 1, sy + 1);
                    context.drawStackOverlay(this.textRenderer, stack, sx + 1, sy + 1);
                    if (hov) {
                        context.drawTooltip(this.textRenderer, stack.getName(), mouseX, mouseY);
                    }
                }
            }
        }

        // Разделитель перед хотбаром
        int hotbarY = invStartY + 3 * slotSize + 2;
        RoundedRectShader.draw(context, px + 8, hotbarY, panelW - 16, 1, 0f, 0x25FFFFFF);

        // 3. Хотбар игрока: 1 ряд по 9 слотов (слоты 0..8)
        int hotbarSlotsY = hotbarY + 4;
        for (int col = 0; col < 9; col++) {
            int sx = startX + col * slotSize;
            ItemStack stack = mc.player.getInventory().getStack(col);
            boolean hov = mouseX >= sx && mouseX < sx + 17 && mouseY >= hotbarSlotsY && mouseY < hotbarSlotsY + 17;

            RoundedRectShader.draw(context, sx, hotbarSlotsY, 17, 17, 3.0f, hov ? 0x66334466 : 0x33141624);
            RoundedRectShader.drawOutline(context, sx, hotbarSlotsY, 17, 17, 3.0f, 0.8f, hov ? COLOR_ACCENT : 0x20FFFFFF);

            if (!stack.isEmpty()) {
                context.drawItem(stack, sx + 1, hotbarSlotsY + 1);
                context.drawStackOverlay(this.textRenderer, stack, sx + 1, hotbarSlotsY + 1);
                if (hov) {
                    context.drawTooltip(this.textRenderer, stack.getName(), mouseX, mouseY);
                }
            }
        }

        // 4. Кнопки внизу: "Снять привязку" и "Отмена"
        int btnY = hotbarSlotsY + slotSize + 4;

        // Кнопка очистки
        int clearW = 75;
        int clearX = px + 12;
        boolean clearHov = mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= btnY && mouseY <= btnY + 14;
        RoundedRectShader.draw(context, clearX, btnY, clearW, 14, 3.0f, clearHov ? 0x66992222 : 0x33441515);
        RoundedRectShader.drawOutline(context, clearX, btnY, clearW, 14, 3.0f, 0.8f, clearHov ? 0xFFEF4444 : 0x55EF4444);
        int tw1 = this.textRenderer.getWidth("✕ Очистить");
        context.drawTextWithShadow(this.textRenderer, "✕ Очистить", clearX + (clearW - tw1) / 2, btnY + 3, clearHov ? 0xFFFFFFFF : 0xFFEF4444);

        // Кнопка отмены
        int cancelW = 55;
        int cancelX = px + panelW - cancelW - 12;
        boolean cancelHov = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + 14;
        RoundedRectShader.draw(context, cancelX, btnY, cancelW, 14, 3.0f, cancelHov ? 0x55333344 : 0x331E202C);
        RoundedRectShader.drawOutline(context, cancelX, btnY, cancelW, 14, 3.0f, 0.8f, cancelHov ? 0x88FFFFFF : 0x33FFFFFF);
        int tw2 = this.textRenderer.getWidth("Отмена");
        context.drawTextWithShadow(this.textRenderer, "Отмена", cancelX + (cancelW - tw2) / 2, btnY + 3, cancelHov ? 0xFFFFFFFF : 0xFFAAAAAA);
    }

    /**
     * Срабатывает СТРОГО в момент отпускания клавиши бинда!
     */
    private void onBindReleased() {
        if (hoveredSector != -1) {
            WheelSlotData data = getWheelSlot(hoveredSector);
            if (!data.isEmpty()) {
                ItemSwap.triggerSwapToSector(hoveredSector);
                closing = true;
            } else {
                // Если слот пустой — открываем инвентарь для выбора предмета
                configuringSector = hoveredSector;
            }
        } else {
            // Отпустили в центре — закрываем колесо без свапа
            closing = true;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Если открыт выбор предмета из инвентаря
        if (configuringSector >= 0) {
            int cx = this.width / 2;
            int cy = this.height / 2;
            int panelW = 186;
            int panelH = 154;
            int px = cx - panelW / 2;
            int py = cy - panelH / 2;

            int slotSize = 18;
            int startX = px + 12;
            int presetY = py + 22;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                // Клик по пресетам
                List<ItemOption> presets = getPresetOptions();
                for (int i = 0; i < presets.size() && i < 9; i++) {
                    int sx = startX + i * slotSize;
                    if (mouseX >= sx && mouseX < sx + 17 && mouseY >= presetY && mouseY < presetY + 17) {
                        applySelectedPreset(presets.get(i).name);
                        return true;
                    }
                }

                // Клик по основному инвентарю (9..35)
                int invStartY = presetY + 24;
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 9; col++) {
                        int slotIdx = 9 + row * 9 + col;
                        int sx = startX + col * slotSize;
                        int sy = invStartY + row * slotSize;

                        if (mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 17) {
                            ItemStack stack = mc.player.getInventory().getStack(slotIdx);
                            if (!stack.isEmpty()) {
                                applySelectedStack(stack);
                                return true;
                            }
                        }
                    }
                }

                // Клик по хотбару (0..8)
                int hotbarSlotsY = invStartY + 3 * slotSize + 6;
                for (int col = 0; col < 9; col++) {
                    int sx = startX + col * slotSize;
                    if (mouseX >= sx && mouseX < sx + 17 && mouseY >= hotbarSlotsY && mouseY < hotbarSlotsY + 17) {
                        ItemStack stack = mc.player.getInventory().getStack(col);
                        if (!stack.isEmpty()) {
                            applySelectedStack(stack);
                            return true;
                        }
                    }
                }
            }

            // Кнопка "Очистить"
            int btnY = py + panelH - 20;
            int clearW = 75;
            int clearX = px + 12;
            if (mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= btnY && mouseY <= btnY + 14) {
                clearSelectedSector();
                return true;
            }

            // Кнопка "Отмена" или клик вне модалки
            int cancelW = 55;
            int cancelX = px + panelW - cancelW - 12;
            if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + 14
                    || mouseX < px || mouseX > px + panelW || mouseY < py || mouseY > py + panelH) {
                configuringSector = -1;
                return true;
            }

            return true;
        }

        // Клики по секторам бублика
        if (hoveredSector >= 0) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                // ЛКМ -> открываем инвентарь для выбора предмета
                configuringSector = hoveredSector;
                return true;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                // ПКМ -> очистить привязку
                clearSector(hoveredSector);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void applySelectedStack(ItemStack stack) {
        int sec = configuringSector >= 0 ? configuringSector : hoveredSector;
        if (sec >= 0) {
            setSectorFromStack(sec, stack);
        }
        configuringSector = -1;
    }

    private void applySelectedPreset(String presetName) {
        int sec = configuringSector >= 0 ? configuringSector : hoveredSector;
        if (sec >= 0) {
            setSectorPreset(sec, presetName);
        }
        configuringSector = -1;
    }

    private void clearSelectedSector() {
        int sec = configuringSector >= 0 ? configuringSector : hoveredSector;
        if (sec >= 0) {
            clearSector(sec);
        }
        configuringSector = -1;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int code = this.bindKey != GLFW.GLFW_KEY_UNKNOWN && this.bindKey != -1 ? this.bindKey : resolveCurrentBindKey();
        int expectedMouseBtn = -1;
        if (code <= -1000) expectedMouseBtn = -1000 - code;
        else if (code < 0 && code >= -10) expectedMouseBtn = -(code + 1);

        if (button == expectedMouseBtn && configuringSector == -1 && !closing) {
            onBindReleased();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        int code = this.bindKey != GLFW.GLFW_KEY_UNKNOWN && this.bindKey != -1 ? this.bindKey : resolveCurrentBindKey();
        if (keyCode == code && configuringSector == -1 && !closing) {
            onBindReleased();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (configuringSector >= 0) {
                configuringSector = -1;
                return true;
            }
            closing = true;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isBindStillHeld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return false;

        int code = this.bindKey != GLFW.GLFW_KEY_UNKNOWN && this.bindKey != -1 ? this.bindKey : resolveCurrentBindKey();
        if (code == GLFW.GLFW_KEY_UNKNOWN || code == -1) return false;

        return isKeyOrButtonDown(mc.getWindow().getHandle(), code);
    }

    public static boolean isKeyOrButtonDown(long window, int code) {
        if (code == GLFW.GLFW_KEY_UNKNOWN || code == -1) return false;

        // Mouse bind encoded as -1000 - button
        if (code <= -1000) {
            int button = -1000 - code;
            if (button >= 0 && button <= 7) {
                return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
            }
            return false;
        }

        // Mouse bind encoded as -(button + 1)
        if (code < 0 && code >= -10) {
            int button = -(code + 1);
            if (button >= 0 && button <= 7) {
                return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
            }
            return false;
        }

        // Keyboard GLFW key
        if (code > 0) {
            return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
        }

        return false;
    }

    public static int resolveCurrentBindKey() {
        int bindKey = BindManager.getStoredBindValue("Item Swap Action");
        if (bindKey != GLFW.GLFW_KEY_UNKNOWN && bindKey != -1) return bindKey;

        if (ClientData.moduleBinds.containsKey("Item Swap Action")) {
            Integer b = ClientData.moduleBinds.get("Item Swap Action");
            if (b != null && b != GLFW.GLFW_KEY_UNKNOWN && b != -1) return b;
        }
        if (LexoraGui.moduleBinds.containsKey("Item Swap Action")) {
            Integer b = LexoraGui.moduleBinds.get("Item Swap Action");
            if (b != null && b != GLFW.GLFW_KEY_UNKNOWN && b != -1) return b;
        }
        if (ClientData.numSettings.containsKey("Item Swap Action")) {
            Float f = ClientData.numSettings.get("Item Swap Action");
            if (f != null && f.intValue() != GLFW.GLFW_KEY_UNKNOWN && f.intValue() != -1) return f.intValue();
        }
        if (LexoraGui.numSettings.containsKey("Item Swap Action")) {
            Float f = LexoraGui.numSettings.get("Item Swap Action");
            if (f != null && f.intValue() != GLFW.GLFW_KEY_UNKNOWN && f.intValue() != -1) return f.intValue();
        }
        return GLFW.GLFW_KEY_UNKNOWN;
    }

    // --- Vertex and geometry helpers ---
    private void addVertex(BufferBuilder buf, Matrix4f mat, float x, float y, float[] rgba) {
        buf.vertex(mat, x, y, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private void addCircleOutline(BufferBuilder buf, Matrix4f mat, float cx, float cy, float radius, float[] rgba, int segments) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) Math.toRadians(i * 360f / segments);
            float a1 = (float) Math.toRadians((i + 1) * 360f / segments);
            float x0 = cx + (float) (Math.cos(a0) * radius);
            float y0 = cy + (float) (Math.sin(a0) * radius);
            float x1 = cx + (float) (Math.cos(a1) * radius);
            float y1 = cy + (float) (Math.sin(a1) * radius);
            buf.vertex(mat, x0, y0, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
            buf.vertex(mat, x1, y1, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        }
    }

    private void addRadialLine(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rInner, float rOuter, float angle, float[] rgba) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float x0 = cx + cos * rInner;
        float y0 = cy + sin * rInner;
        float x1 = cx + cos * rOuter;
        float y1 = cy + sin * rOuter;
        buf.vertex(mat, x0, y0, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buf.vertex(mat, x1, y1, 0).color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private float[] argbToFloats(int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    private int withAlpha(int argb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private int blendColors(int base, int overlay, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ba = (base >> 24) & 0xFF, br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int oa = (overlay >> 24) & 0xFF, or_ = (overlay >> 16) & 0xFF, og = (overlay >> 8) & 0xFF, ob = overlay & 0xFF;
        int a = (int) (ba + (oa - ba) * t);
        int r = (int) (br + (or_ - br) * t);
        int g = (int) (bg + (og - bg) * t);
        int b = (int) (bb + (ob - bb) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static String getDefaultItemForSector(int index) {
        return switch (index) {
            case 0 -> "Тотем";
            case 1 -> "Шар";
            case 2 -> "Щит";
            default -> "Тотем";
        };
    }

    public static WheelSlotData getWheelSlot(int sector) {
        if (sector < 0 || sector >= SECTOR_COUNT) return new WheelSlotData();
        if (wheelSlots[sector] != null) return wheelSlots[sector];

        String prefix = "Item Swap Wheel " + sector;
        String name = getModeSetting(prefix, getDefaultItemForSector(sector));
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("Пусто")) {
            WheelSlotData empty = new WheelSlotData();
            wheelSlots[sector] = empty;
            cachedWheelStacks[sector] = ItemStack.EMPTY;
            return empty;
        }

        WheelSlotData data = new WheelSlotData();
        data.displayName = name;
        data.itemId = getModeSetting(prefix + "_Id", "");
        data.customName = getModeSetting(prefix + "_CustomName", "");
        data.skinKey = getModeSetting(prefix + "_SkinKey", "");
        data.skinTextureValue = getModeSetting(prefix + "_SkinVal", "");
        data.skinTextureSignature = getModeSetting(prefix + "_SkinSig", "");
        data.skinName = getModeSetting(prefix + "_SkinName", "");
        data.skinId = getModeSetting(prefix + "_SkinId", "");
        data.potionKey = getModeSetting(prefix + "_PotionKey", "");
        data.modelKey = getModeSetting(prefix + "_ModelKey", "");
        data.loreKey = getModeSetting(prefix + "_LoreKey", "");
        data.isPreset = "true".equalsIgnoreCase(getModeSetting(prefix + "_Preset", "false"));

        if (data.itemId.isEmpty()) {
            initLegacyOrPresetData(data, name);
        }

        reconstructCachedStack(sector, data);
        wheelSlots[sector] = data;
        return data;
    }

    public static ItemStack getStackForSector(int sector) {
        if (sector < 0 || sector >= SECTOR_COUNT) return ItemStack.EMPTY;
        if (cachedWheelStacks[sector] != null && !cachedWheelStacks[sector].isEmpty()) {
            return cachedWheelStacks[sector];
        }
        getWheelSlot(sector);
        if (cachedWheelStacks[sector] != null && !cachedWheelStacks[sector].isEmpty()) {
            return cachedWheelStacks[sector];
        }
        return ItemStack.EMPTY;
    }

    public static void setSectorFromStack(int sector, ItemStack stack) {
        if (sector < 0 || sector >= SECTOR_COUNT) return;
        if (stack == null || stack.isEmpty()) {
            clearSector(sector);
            return;
        }

        WheelSlotData data = new WheelSlotData();
        data.itemId = ItemIdentity.idOf(stack);
        data.customName = ItemIdentity.customNameOf(stack);
        data.displayName = resolveDisplayName(stack);
        data.skinKey = ItemIdentity.skinFingerprint(stack);
        data.skinName = ItemIdentity.skinName(stack);
        data.skinId = ItemIdentity.skinId(stack);
        data.skinTextureValue = ItemIdentity.skinTextureValue(stack);
        data.skinTextureSignature = ItemIdentity.skinTextureSignature(stack);
        data.potionKey = ItemIdentity.potionFingerprint(stack);
        data.modelKey = ItemIdentity.customModelFingerprint(stack);
        data.loreKey = ItemIdentity.loreFingerprint(stack);
        data.isPreset = false;

        wheelSlots[sector] = data;
        cachedWheelStacks[sector] = stack.copy();
        cachedWheelStacks[sector].setCount(1);

        saveSectorToSettings(sector, data);
    }

    public static void setSectorPreset(int sector, String presetName) {
        if (sector < 0 || sector >= SECTOR_COUNT) return;
        WheelSlotData data = new WheelSlotData();
        data.displayName = presetName;
        data.isPreset = true;
        initLegacyOrPresetData(data, presetName);

        wheelSlots[sector] = data;
        cachedWheelStacks[sector] = getFallbackStackForName(presetName);

        saveSectorToSettings(sector, data);
    }

    public static void clearSector(int sector) {
        if (sector < 0 || sector >= SECTOR_COUNT) return;
        WheelSlotData data = new WheelSlotData();
        data.displayName = "Пусто";
        wheelSlots[sector] = data;
        cachedWheelStacks[sector] = ItemStack.EMPTY;
        saveSectorToSettings(sector, data);
    }

    private static void saveSectorToSettings(int sector, WheelSlotData data) {
        String prefix = "Item Swap Wheel " + sector;
        String name = data.displayName != null ? data.displayName : "Пусто";

        setModeSetting(prefix, name);
        setModeSetting(prefix + "_Id", data.itemId != null ? data.itemId : "");
        setModeSetting(prefix + "_CustomName", data.customName != null ? data.customName : "");
        setModeSetting(prefix + "_SkinKey", data.skinKey != null ? data.skinKey : "");
        setModeSetting(prefix + "_SkinVal", data.skinTextureValue != null ? data.skinTextureValue : "");
        setModeSetting(prefix + "_SkinSig", data.skinTextureSignature != null ? data.skinTextureSignature : "");
        setModeSetting(prefix + "_SkinName", data.skinName != null ? data.skinName : "");
        setModeSetting(prefix + "_SkinId", data.skinId != null ? data.skinId : "");
        setModeSetting(prefix + "_PotionKey", data.potionKey != null ? data.potionKey : "");
        setModeSetting(prefix + "_ModelKey", data.modelKey != null ? data.modelKey : "");
        setModeSetting(prefix + "_LoreKey", data.loreKey != null ? data.loreKey : "");
        setModeSetting(prefix + "_Preset", data.isPreset ? "true" : "false");

        ConfigManager.saveConfig();
    }

    private static void reconstructCachedStack(int sector, WheelSlotData data) {
        if (data == null || data.isEmpty()) {
            cachedWheelStacks[sector] = ItemStack.EMPTY;
            return;
        }

        // 1. Голова с кастомной текстурой (HolyWorld сферы, шары и талисманы)
        if ("minecraft:player_head".equalsIgnoreCase(data.itemId) && data.skinTextureValue != null && !data.skinTextureValue.isEmpty()) {
            LoadoutSlotData lsd = new LoadoutSlotData(0, data.itemId, data.customName, 1, data.skinKey);
            lsd.setSkinData(data.skinName, data.skinId, data.skinTextureValue, data.skinTextureSignature);
            ItemStack stack = ItemIdentity.templateStack(lsd);
            if (data.customName != null && !data.customName.isEmpty()) {
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(data.customName));
            }
            cachedWheelStacks[sector] = stack;
            return;
        }

        // 2. Предмет по ID
        if (data.itemId != null && !data.itemId.isEmpty() && !data.itemId.equals("minecraft:air")) {
            ItemStack stack = new ItemStack(ItemIdentity.itemFromId(data.itemId), 1);
            if (data.customName != null && !data.customName.isEmpty()) {
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(data.customName));
            }
            cachedWheelStacks[sector] = stack;
            return;
        }

        // 3. Fallback
        cachedWheelStacks[sector] = getFallbackStackForName(data.displayName);
    }

    private static void initLegacyOrPresetData(WheelSlotData data, String name) {
        data.isPreset = true;
        if ("Тотем".equalsIgnoreCase(name)) {
            data.itemId = "minecraft:totem_of_undying";
        } else if ("Шар".equalsIgnoreCase(name) || "Голова".equalsIgnoreCase(name)) {
            data.itemId = "minecraft:player_head";
        } else if ("Щит".equalsIgnoreCase(name)) {
            data.itemId = "minecraft:shield";
        } else if ("Золотое яблоко".equalsIgnoreCase(name) || "Яблоко".equalsIgnoreCase(name)) {
            data.itemId = "minecraft:golden_apple";
        } else if ("Чар. яблоко".equalsIgnoreCase(name)) {
            data.itemId = "minecraft:enchanted_golden_apple";
        } else if ("Эндер перл".equalsIgnoreCase(name) || "Перл".equalsIgnoreCase(name)) {
            data.itemId = "minecraft:ender_pearl";
        } else if ("Зелье".equalsIgnoreCase(name)) {
            data.itemId = "minecraft:splash_potion";
        } else {
            data.isPreset = false;
            data.customName = name;
        }
    }

    public static String resolveDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "Пусто";

        Text custom = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (custom != null && !custom.getString().trim().isEmpty()) {
            return custom.getString().trim();
        }

        if (stack.getItem() == Items.TOTEM_OF_UNDYING) return "Тотем";
        if (stack.getItem() == Items.SHIELD) return "Щит";
        if (stack.getItem() == Items.GOLDEN_APPLE) return "Золотое яблоко";
        if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return "Чар. яблоко";
        if (stack.getItem() == Items.ENDER_PEARL) return "Эндер перл";
        if (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.POTION) return "Зелье";

        if (stack.getItem() == Items.PLAYER_HEAD) {
            String skinName = ItemIdentity.skinName(stack);
            if (skinName != null && !skinName.isEmpty()) {
                return "Шар (" + skinName + ")";
            }
            return "Шар";
        }

        return stack.getName().getString();
    }

    private static ItemStack getFallbackStackForName(String name) {
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("Пусто")) return ItemStack.EMPTY;
        if ("Тотем".equalsIgnoreCase(name)) return new ItemStack(Items.TOTEM_OF_UNDYING);
        if ("Шар".equalsIgnoreCase(name) || "Голова".equalsIgnoreCase(name)) return new ItemStack(Items.PLAYER_HEAD);
        if ("Щит".equalsIgnoreCase(name)) return new ItemStack(Items.SHIELD);
        if ("Золотое яблоко".equalsIgnoreCase(name) || "Яблоко".equalsIgnoreCase(name)) return new ItemStack(Items.GOLDEN_APPLE);
        if ("Чар. яблоко".equalsIgnoreCase(name)) return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        if ("Эндер перл".equalsIgnoreCase(name) || "Перл".equalsIgnoreCase(name)) return new ItemStack(Items.ENDER_PEARL);
        if ("Зелье".equalsIgnoreCase(name)) return new ItemStack(Items.SPLASH_POTION);
        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }

    private static void setModeSetting(String key, String value) {
        ClientData.modeSettings.put(key, value);
        LexoraGui.modeSettings.put(key, value);
    }

    private static String getModeSetting(String key, String def) {
        if (ClientData.modeSettings.containsKey(key)) return ClientData.modeSettings.get(key);
        if (LexoraGui.modeSettings.containsKey(key)) return LexoraGui.modeSettings.get(key);
        return def;
    }

    private record ItemOption(String name, ItemStack stack) {}

    private List<ItemOption> getPresetOptions() {
        List<ItemOption> list = new ArrayList<>();
        list.add(new ItemOption("Тотем", new ItemStack(Items.TOTEM_OF_UNDYING)));
        list.add(new ItemOption("Шар", new ItemStack(Items.PLAYER_HEAD)));
        list.add(new ItemOption("Щит", new ItemStack(Items.SHIELD)));
        list.add(new ItemOption("Золотое яблоко", new ItemStack(Items.GOLDEN_APPLE)));
        list.add(new ItemOption("Чар. яблоко", new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
        list.add(new ItemOption("Эндер перл", new ItemStack(Items.ENDER_PEARL)));
        list.add(new ItemOption("Зелье", new ItemStack(Items.SPLASH_POTION)));
        return list;
    }
}
