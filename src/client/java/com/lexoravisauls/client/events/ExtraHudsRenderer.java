package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.LexoraIcons;
import com.lexoravisauls.client.gui.MsdfFont;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ExtraHudsRenderer {

    // --- ШРИФТЫ ---
    private static final Identifier FONT_TEX = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    // --- ФИЗИКА "ЖЕЛЕ" (Spring Animation) ---
    private static float keyAlpha = 0f, keyScale = 0f, keyAnimW = 100f, keyAnimH = 22f;
    private static float keyScaleV = 0f, keyWV = 0f, keyHV = 0f;

    private static float armAlpha = 0f, armScale = 0f, armAnimW = 82f, armAnimH = 22f;
    private static float armScaleV = 0f, armWV = 0f, armHV = 0f;

    private static float invAlpha = 0f, invScale = 0f, invAnimW = 174f, invAnimH = 22f;
    private static float invScaleV = 0f, invWV = 0f, invHV = 0f;

    private static float coolAlpha = 0f, coolScale = 0f, coolAnimW = 105f, coolAnimH = 22f;
    private static float coolScaleV = 0f, coolWV = 0f, coolHV = 0f;

    // --- РАЗМЕРЫ ДЛЯ ХИТБОКСОВ (HudManager Drag) ---
    public static int keybindsW = 100, keybindsH = 22;
    public static int armorW = 82, armorH = 22;
    public static int invW = 174, invH = 80;
    public static int coolW = 105, coolH = 22;

    private static final Random RANDOM = new Random();
    private static final List<ItemStack> PREVIEW_COOLDOWNS = new ArrayList<>();
    private static long lastCooldownPreviewUpdateMs = 0L;

    private static MsdfFont getFont() {
        if (msdfFont == null) {
            msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        }
        return msdfFont;
    }

    // Метод упругой анимации (Желе)
    private static float spring(float current, float target, float[] velocity, float tension, float friction) {
        velocity[0] += (target - current) * tension;
        velocity[0] *= friction;
        return current + velocity[0];
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.options.hudHidden) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        renderKeybinds(context, mc);
        renderArmor(context, mc);
        renderInventory(context, mc);
        renderCooldowns(context, mc, tickDelta);

        context.draw();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ==========================================================
    // 1. KEYBINDS HUD
    // ==========================================================
    private static void renderKeybinds(DrawContext context, MinecraftClient mc) {
        boolean enabled = isModuleEnabled("Keybinds", false);
        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;

        List<String> activeMods = new ArrayList<>();
        List<String> activeKeys = new ArrayList<>();
        float targetW = 95f;

        for (Map.Entry<String, Integer> entry : ClientData.moduleBinds.entrySet()) {
            String mod = entry.getKey();
            int bind = entry.getValue();
            if (bind == 0 || bind == -1) continue;
            if (!isModuleEnabled(mod, false)) continue;

            String keyStr = "[" + net.minecraft.client.util.InputUtil.fromKeyCode(bind, -1).getLocalizedText().getString().toUpperCase() + "]";
            activeMods.add(mod);
            activeKeys.add(keyStr);

            float w = 24f + width(mod, 7.5f) + 16f + width(keyStr, 7.5f);
            if (w > targetW) targetW = w;
        }

        if (activeMods.isEmpty() && showPreview) {
            targetW = Math.max(targetW, 24f + width("No Active Binds", 7.5f) + 12f);
        }

        boolean shouldShow = enabled && (!activeMods.isEmpty() || showPreview);
        float targetH = 22f + (activeMods.isEmpty() && showPreview ? 16f : activeMods.size() * 16f);

        keyAlpha += ((shouldShow ? 1.0f : 0.0f) - keyAlpha) * 0.15f;
        float baseScale = getNum("Keybinds Scale", 1.0f);
        float targetScaleValue = shouldShow ? baseScale : baseScale * 0.7f;

        float[] vScale = {keyScaleV}, vW = {keyWV}, vH = {keyHV};
        keyScale = spring(keyScale, targetScaleValue, vScale, 0.2f, 0.65f);
        keyAnimW = spring(keyAnimW, targetW, vW, 0.2f, 0.65f);
        keyAnimH = spring(keyAnimH, targetH, vH, 0.2f, 0.65f);
        keyScaleV = vScale[0]; keyWV = vW[0]; keyHV = vH[0];

        keybindsW = Math.round(keyAnimW);
        keybindsH = Math.round(keyAnimH);

        if (keyAlpha < 0.02f) return;

        float x = HudManager.keybindsX;
        float y = HudManager.keybindsY;

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(x + keybindsW / 2f, y + keybindsH / 2f, 0);
        ms.scale(keyScale, keyScale, 1.0f);
        ms.translate(-(x + keybindsW / 2f), -(y + keybindsH / 2f), 0);

        int alpha = (int) (keyAlpha * 255);
        drawPanel(context, x, y, keybindsW, keybindsH, alpha, "Keybinds"); // Передаем Имя модуля

        // Шапка
        LexoraIcons.draw(context, LexoraIcons.Icon.KEYBOARD, x + 6, y + 6, 9.0f, (alpha << 24) | 0xFFFFFF);
        drawString(context, "Keybinds", x + 18, y + 6.5f, 8.0f, (alpha << 24) | 0xFFFFFF);

        float currentY = y + 23;
        if (activeMods.isEmpty() && showPreview) {
            drawString(context, "No Active Binds", x + 6, currentY, 7.5f, (alpha << 24) | 0xAAAAAA);
        } else {
            for (int i = 0; i < activeMods.size(); i++) {
                drawString(context, activeMods.get(i), x + 6, currentY, 7.5f, (alpha << 24) | 0xEEEEEE);
                String keyStr = activeKeys.get(i);
                drawString(context, keyStr, x + keybindsW - 6 - width(keyStr, 7.5f), currentY, 7.5f, (alpha << 24) | 0x888888);
                currentY += 16;
            }
        }
        ms.pop();
    }

    // ==========================================================
    // 2. ARMOR HUD
    // ==========================================================
    private static void renderArmor(DrawContext context, MinecraftClient mc) {
        boolean enabled = isModuleEnabled("Armor Status", false);
        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;

        List<ItemStack> items = new ArrayList<>();
        items.add(mc.player.getMainHandStack());
        for (int i = 3; i >= 0; i--) items.add(mc.player.getInventory().armor.get(i));
        items.add(mc.player.getOffHandStack());
        items.removeIf(ItemStack::isEmpty);

        boolean shouldShow = enabled && (!items.isEmpty() || showPreview);
        float targetW = Math.max(85f, items.isEmpty() && showPreview ? 85f : items.size() * 20f + 8f);
        float targetH = items.isEmpty() && showPreview ? 22f : 45f;

        armAlpha += ((shouldShow ? 1.0f : 0.0f) - armAlpha) * 0.15f;
        float baseScale = getNum("Armor Status Scale", 1.0f);
        float targetScaleValue = shouldShow ? baseScale : baseScale * 0.7f;

        float[] vScale = {armScaleV}, vW = {armWV}, vH = {armHV};
        armScale = spring(armScale, targetScaleValue, vScale, 0.2f, 0.65f);
        armAnimW = spring(armAnimW, targetW, vW, 0.2f, 0.65f);
        armAnimH = spring(armAnimH, targetH, vH, 0.2f, 0.65f);
        armScaleV = vScale[0]; armWV = vW[0]; armHV = vH[0];

        armorW = Math.round(armAnimW);
        armorH = Math.round(armAnimH);

        if (armAlpha < 0.02f) return;

        float x = HudManager.armorX;
        float y = HudManager.armorY;

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(x + armorW / 2f, y + armorH / 2f, 0);
        ms.scale(armScale, armScale, 1.0f);
        ms.translate(-(x + armorW / 2f), -(y + armorH / 2f), 0);

        int alpha = (int) (armAlpha * 255);
        drawPanel(context, x, y, armorW, armorH, alpha, "Armor Status"); // Передаем Имя модуля

        // Шапка
        LexoraIcons.draw(context, LexoraIcons.Icon.SHIELD, x + 6, y + 6, 9.0f, (alpha << 24) | 0xFFFFFF);
        drawString(context, "Armor", x + 18, y + 6.5f, 8.0f, (alpha << 24) | 0xFFFFFF);

        if (!items.isEmpty() || !showPreview) {
            int currentX = (int) x + 4;
            int currentY = (int) y + 24;
            for (ItemStack stack : items) {
                context.drawItem(stack, currentX, currentY);

                if (stack.getCount() > 1) {
                    String cnt = String.valueOf(stack.getCount());
                    ms.push();
                    ms.translate(0, 0, 200);
                    drawString(context, cnt, currentX + 17 - width(cnt, 7.5f), currentY + 9, 7.5f, (alpha << 24) | 0xFFFFFF);
                    ms.pop();
                }

                if (stack.isDamageable()) {
                    float damageProgress = (float) stack.getDamage() / stack.getMaxDamage();
                    float durabilityLeft = Math.max(0.0f, 1.0f - damageProgress);
                    if (durabilityLeft < 1.0f) {
                        int color = MathHelper.hsvToRgb(durabilityLeft / 3.0f, 1.0f, 1.0f);
                        drawSmoothRect(context, currentX, currentY + 16, 16, 2, 1f, (alpha << 24) | 0x1E1E1E);
                        drawSmoothRect(context, currentX, currentY + 16, (int)(16 * durabilityLeft), 2, 1f, (alpha << 24) | (color & 0xFFFFFF));
                    }
                }
                currentX += 20;
            }
        }
        ms.pop();
    }

    // ==========================================================
    // 3. INVENTORY HUD
    // ==========================================================
    private static void renderInventory(DrawContext context, MinecraftClient mc) {
        boolean enabled = isModuleEnabled("Inventory HUD", false);
        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        boolean shouldShow = enabled && (mc.player != null || showPreview);

        float targetW = 12f + (9 * 18f);
        float targetH = 22f + (3 * 18f) + 4f;

        invAlpha += ((shouldShow ? 1.0f : 0.0f) - invAlpha) * 0.15f;
        float baseScale = getNum("Inventory HUD Scale", 1.0f);
        float targetScaleValue = shouldShow ? baseScale : baseScale * 0.7f;

        float[] vScale = {invScaleV}, vW = {invWV}, vH = {invHV};
        invScale = spring(invScale, targetScaleValue, vScale, 0.2f, 0.65f);
        invAnimW = spring(invAnimW, targetW, vW, 0.2f, 0.65f);
        invAnimH = spring(invAnimH, targetH, vH, 0.2f, 0.65f);
        invScaleV = vScale[0]; invWV = vW[0]; invHV = vH[0];

        invW = Math.round(invAnimW);
        invH = Math.round(invAnimH);

        if (invAlpha < 0.02f) return;

        float x = HudManager.invX;
        float y = HudManager.invY;

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(x + invW / 2f, y + invH / 2f, 0);
        ms.scale(invScale, invScale, 1.0f);
        ms.translate(-(x + invW / 2f), -(y + invH / 2f), 0);

        int alpha = (int) (invAlpha * 255);
        drawPanel(context, x, y, invW, invH, alpha, "Inventory HUD"); // Передаем Имя модуля

        // Шапка
        LexoraIcons.draw(context, LexoraIcons.Icon.APPS, x + 6, y + 6, 9.0f, (alpha << 24) | 0xFFFFFF);
        drawString(context, "Inventory", x + 18, y + 6.5f, 8.0f, (alpha << 24) | 0xFFFFFF);

        int startX = (int)x + 6;
        int startY = (int)y + 24;

        if (mc.player != null) {
            for (int i = 9; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().main.get(i);
                int col = (i - 9) % 9;
                int row = (i - 9) / 9;
                int slotX = startX + (col * 18);
                int slotY = startY + (row * 18);

                if (!stack.isEmpty()) {
                    context.drawItem(stack, slotX, slotY);
                    if (stack.getCount() > 1) {
                        String cnt = String.valueOf(stack.getCount());
                        ms.push();
                        ms.translate(0, 0, 200);
                        drawString(context, cnt, slotX + 17 - width(cnt, 7.5f), slotY + 9, 7.5f, (alpha << 24) | 0xFFFFFF);
                        ms.pop();
                    }
                }
            }
        }
        ms.pop();
    }

    // ==========================================================
    // 4. COOLDOWNS HUD
    // ==========================================================
    private static void generatePreviewCooldowns() {
        PREVIEW_COOLDOWNS.clear();
        Item[] possibleItems = {Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.SHIELD, Items.BOW};
        Item item = possibleItems[RANDOM.nextInt(possibleItems.length)];
        PREVIEW_COOLDOWNS.add(new ItemStack(item));
    }

    private static void renderCooldowns(DrawContext context, MinecraftClient mc, float tickDelta) {
        boolean enabled = isModuleEnabled("Cooldowns", false);
        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;

        List<ItemStack> cooldownItems = new ArrayList<>();
        if (mc.player != null) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().main.get(i);
                if (!stack.isEmpty() && mc.player.getItemCooldownManager().isCoolingDown(stack)) {
                    if (cooldownItems.stream().noneMatch(s -> s.getItem() == stack.getItem())) cooldownItems.add(stack);
                }
            }
            ItemStack offhand = mc.player.getOffHandStack();
            if (!offhand.isEmpty() && mc.player.getItemCooldownManager().isCoolingDown(offhand)) {
                if (cooldownItems.stream().noneMatch(s -> s.getItem() == offhand.getItem())) cooldownItems.add(offhand);
            }
        }

        boolean isPreviewMode = false;
        if (cooldownItems.isEmpty() && showPreview) {
            isPreviewMode = true;
            long now = System.currentTimeMillis();
            if (PREVIEW_COOLDOWNS.isEmpty() || now - lastCooldownPreviewUpdateMs >= 1500L) {
                generatePreviewCooldowns();
                lastCooldownPreviewUpdateMs = now;
            }
            cooldownItems.addAll(PREVIEW_COOLDOWNS);
        }

        boolean shouldShow = enabled && (!cooldownItems.isEmpty() || showPreview);
        float targetW = 105f;

        for (ItemStack stack : cooldownItems) {
            String name = stack.getItem().getName().getString();
            float w = 26f + width(name, 7.5f) + 20f + width("100%", 7.5f);
            if (w > targetW) targetW = w;
        }

        float targetH = 22f + (cooldownItems.isEmpty() && showPreview ? 0f : cooldownItems.size() * 18f);

        coolAlpha += ((shouldShow ? 1.0f : 0.0f) - coolAlpha) * 0.15f;
        float baseScale = getNum("Cooldowns Scale", 1.0f);
        float targetScaleValue = shouldShow ? baseScale : baseScale * 0.7f;

        float[] vScale = {coolScaleV}, vW = {coolWV}, vH = {coolHV};
        coolScale = spring(coolScale, targetScaleValue, vScale, 0.2f, 0.65f);
        coolAnimW = spring(coolAnimW, targetW, vW, 0.2f, 0.65f);
        coolAnimH = spring(coolAnimH, targetH, vH, 0.2f, 0.65f);
        coolScaleV = vScale[0]; coolWV = vW[0]; coolHV = vH[0];

        coolW = Math.round(coolAnimW);
        coolH = Math.round(coolAnimH);

        if (coolAlpha < 0.02f) return;

        float x = HudManager.coolX;
        float y = HudManager.coolY;

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(x + coolW / 2f, y + coolH / 2f, 0);
        ms.scale(coolScale, coolScale, 1.0f);
        ms.translate(-(x + coolW / 2f), -(y + coolH / 2f), 0);

        int alpha = (int) (coolAlpha * 255);
        drawPanel(context, x, y, coolW, coolH, alpha, "Cooldowns"); // Передаем Имя модуля

        // Шапка
        LexoraIcons.draw(context, LexoraIcons.Icon.HOURGLASS, x + 6, y + 6, 9.0f, (alpha << 24) | 0xFFFFFF);
        drawString(context, "Cooldowns", x + 18, y + 6.5f, 8.0f, (alpha << 24) | 0xFFFFFF);

        float currentY = y + 24;
        for (ItemStack stack : cooldownItems) {
            float progress;
            if (isPreviewMode) {
                long elapsed = System.currentTimeMillis() - lastCooldownPreviewUpdateMs;
                progress = Math.max(0.0f, 1.0f - (elapsed / 1500.0f));
            } else {
                progress = mc.player.getItemCooldownManager().getCooldownProgress(stack, tickDelta);
            }

            context.drawItem(stack, (int)x + 6, (int)currentY - 2);
            String name = stack.getItem().getName().getString();
            drawString(context, name, x + 26, currentY + 1, 7.5f, (alpha << 24) | 0xEEEEEE);

            String pct = String.format(java.util.Locale.US, "%.0f%%", progress * 100);
            drawString(context, pct, x + coolW - 6 - width(pct, 7.5f), currentY + 1, 7.5f, (alpha << 24) | 0xAAAAAA);

            // Тонкая белая полоска
            drawSmoothRect(context, (int)x + 26, (int)currentY + 11, (int)(coolW - 32), 1, 0.5f, (alpha << 24) | 0x1E1E1E);
            drawSmoothRect(context, (int)x + 26, (int)currentY + 11, (int)((coolW - 32) * progress), 1, 0.5f, (alpha << 24) | 0xFFFFFF);

            currentY += 18;
        }
        ms.pop();
    }

    // ==========================================================
    // УТИЛИТЫ ДЛЯ ОТРИСОВКИ И ФИКС БАГА С БЛЮРОМ
    // ==========================================================

    private static void drawPanel(DrawContext context, float x, float y, float width, float height, int alpha, String moduleName) {
        // ФИКС: Теперь каждый худ использует свою собственную настройку блюра из LexoraGui!
        boolean blurEnabled = LexoraGui.moduleStates.getOrDefault(moduleName + " Blur", true);

        int bgColor = (Math.min(alpha, 160) << 24) | 0x050505;

        if (blurEnabled && alpha > 10) {
            context.draw(); // <--- ДОБАВИТЬ ЭТО
            com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(context, x, y, width, height, 6f, 15f, bgColor);
        } else {
            drawSmoothRect(context, (int)x, (int)y, (int)width, (int)height, 6f, bgColor);
        }
    }

    private static boolean isModuleEnabled(String key, boolean fallback) {
        return LexoraGui.moduleStates.getOrDefault(key, ClientData.moduleStates.getOrDefault(key, fallback)) || ClientData.moduleStates.getOrDefault(key, fallback);
    }

    private static float getNum(String key, float fallback) {
        if (LexoraGui.numSettings.containsKey(key)) return LexoraGui.numSettings.get(key);
        return ClientData.numSettings.getOrDefault(key, fallback);
    }

    private static void drawString(DrawContext context, String text, float x, float y, float size, int color) {
        if (text == null || text.trim().isEmpty()) return;
        getFont().draw(context.getMatrices(), text, x, y, size, color);
    }

    private static float width(String text, float size) {
        if (text == null || text.trim().isEmpty()) return 0.0f;
        return getFont().getWidth(text, size);
    }

    private static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, float radius, int color) {
        if (width <= 0 || height <= 0) return;
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }
}