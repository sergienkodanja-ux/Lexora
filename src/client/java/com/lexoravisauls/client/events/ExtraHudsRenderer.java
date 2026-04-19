package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExtraHudsRenderer {

    private static final Identifier CUSTOM_FONT = Identifier.of("lexoravisauls", "sfui");

    private static final Identifier ICON_ARMOR = Identifier.of("lexoravisauls", "textures/gui/armor.png");
    private static final Identifier ICON_INV = Identifier.of("lexoravisauls", "textures/gui/inventory.png");
    private static final Identifier ICON_COOL = Identifier.of("lexoravisauls", "textures/gui/cooldown.png");
    private static final Identifier ICON_KEYS = Identifier.of("lexoravisauls", "textures/gui/keybinds.png");

    public static int keybindsW = 120, keybindsH = 25;
    public static int armorW = 100, armorH = 45;
    public static int invW = 174, invH = 80;
    public static int coolW = 100, coolH = 50;

    private static float keyAlpha = 0f, keyScale = 0.8f, keyAnimH = 25f;
    private static float armAlpha = 0f, armScale = 0.8f, armAnimW = 100f;
    private static float invAlpha = 0f, invScale = 0.8f;
    private static float coolAlpha = 0f, coolScale = 0.8f, coolAnimW = 100f;

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        renderKeybinds(context, mc);
        renderArmor(context, mc);
        renderInventory(context, mc);
        renderCooldowns(context, mc, tickDelta);

        RenderSystem.enableDepthTest();
    }

    private static void drawHeader(DrawContext context, int x, int y, int width, Identifier icon, String title, int alpha, boolean isSolid) {
        int bgAlpha = isSolid ? 255 : alpha;
        drawSmoothRect(context, x, y, width, 20, (bgAlpha << 24) | 0x14141A);

        long time = System.currentTimeMillis();
        float wave = (float) (Math.sin(time / 400.0) * 0.5 + 0.5);
        int colorVal = (int) (150 + 80 * wave);

        drawColoredTexturedQuad(context, icon, x + 6, y + 5, 10, 10, 0, 0, 1, 1, colorVal, colorVal, colorVal, alpha);

        int textColor = (alpha << 24) | (colorVal << 16) | (colorVal << 8) | colorVal;
        drawCustomText(context, title, x + 22, y + 6, textColor);
    }

    // ==========================================
    // 1. KEYBINDS HUD
    // ==========================================
    private static float keyAnimW = 140.0f;

    private static void renderKeybinds(DrawContext context, MinecraftClient mc) {
        List<String> activeAndBound = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : LexoraGui.moduleStates.entrySet()) {
            if (entry.getValue() && LexoraGui.moduleBinds.containsKey(entry.getKey())) {
                activeAndBound.add(entry.getKey());
            }
        }

        boolean enabled = LexoraGui.moduleStates.getOrDefault("Keybinds", false);
        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        boolean shouldShow = enabled && (!activeAndBound.isEmpty() || showPreview);

        float smooth = 0.15f;
        keyAlpha += ((shouldShow ? 1.0f : 0.0f) - keyAlpha) * smooth;
        float targetScale = LexoraGui.numSettings.getOrDefault("Keybinds Scale", 1.0f);
        keyScale += ((shouldShow ? targetScale : targetScale - 0.2f) - keyScale) * smooth;

        if (keyAlpha < 0.02f) return;

        int targetH = 22 + (Math.max(1, activeAndBound.size()) * 15) + 5;
        keyAnimH += (targetH - keyAnimH) * smooth;

        int targetW = 120;
        if (activeAndBound.isEmpty() && showPreview) {
            targetW = Math.max(targetW, 16 + getCustomTextWidth("No Active Binds"));
        } else {
            for (String mod : activeAndBound) {
                int keycode = LexoraGui.moduleBinds.get(mod);
                String keyName = "[" + InputUtil.fromKeyCode(keycode, -1).getLocalizedText().getString().toUpperCase() + "]";
                int textW = 8 + getCustomTextWidth(mod) + 20 + getCustomTextWidth(keyName) + 8;
                if (textW > targetW) targetW = textW;
            }
        }

        keyAnimW += (targetW - keyAnimW) * smooth;
        keybindsW = Math.round(keyAnimW);
        keybindsH = Math.round(keyAnimH);

        int a = (int) (keyAlpha * 255);
        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Keybinds Solid", false);
        int bgAlpha = isSolid ? 255 : (int)(keyAlpha * 220);

        context.getMatrices().push();
        context.getMatrices().translate(HudManager.keybindsX + keybindsW / 2f, HudManager.keybindsY + keybindsH / 2f, -150);
        context.getMatrices().scale(keyScale, keyScale, 1.0f);
        context.getMatrices().translate(-(HudManager.keybindsX + keybindsW / 2f), -(HudManager.keybindsY + keybindsH / 2f), 0);

        drawSmoothRect(context, HudManager.keybindsX, HudManager.keybindsY, keybindsW, keybindsH, (bgAlpha << 24) | 0x0D0D11);
        drawHeader(context, HudManager.keybindsX, HudManager.keybindsY, keybindsW, ICON_KEYS, "Keybinds", a, isSolid);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 10);

        int currentY = HudManager.keybindsY + 24;
        long time = System.currentTimeMillis();

        if (activeAndBound.isEmpty() && showPreview) {
            float wave = (float) (Math.sin(time / 400.0) * 0.5 + 0.5);
            int colorVal = (int) (100 + 60 * wave);
            int previewColor = (a << 24) | (colorVal << 16) | (colorVal << 8) | colorVal;
            drawCustomText(context, "No Active Binds", HudManager.keybindsX + 8, currentY, previewColor);
        } else {
            int i = 1;
            for (String mod : activeAndBound) {
                float wave = (float) (Math.sin((time / 400.0) + (i * 0.6)) * 0.5 + 0.5);
                int colorVal = (int) (150 + 80 * wave);
                int textColor = (a << 24) | (colorVal << 16) | (colorVal << 8) | colorVal;

                int keycode = LexoraGui.moduleBinds.get(mod);
                String keyName = "[" + InputUtil.fromKeyCode(keycode, -1).getLocalizedText().getString().toUpperCase() + "]";

                drawCustomText(context, mod, HudManager.keybindsX + 8, currentY, textColor);
                int color = LexoraGui.getThemeColor(i * 0.1f);
                drawCustomText(context, keyName, HudManager.keybindsX + keybindsW - 8 - getCustomTextWidth(keyName), currentY, (a << 24) | color);

                currentY += 15;
                i++;
            }
        }
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

    // ==========================================
    // 2. ARMOR STATUS HUD
    // ==========================================
    private static void renderArmor(DrawContext context, MinecraftClient mc) {
        boolean enabled = LexoraGui.moduleStates.getOrDefault("Armor Status", false);

        List<ItemStack> items = new ArrayList<>();
        items.add(mc.player.getMainHandStack());
        for (int i = 3; i >= 0; i--) items.add(mc.player.getInventory().armor.get(i));
        items.add(mc.player.getOffHandStack());

        int count = 0;
        for (ItemStack st : items) if (!st.isEmpty()) count++;

        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        boolean shouldShow = enabled && (count > 0 || showPreview);

        float smooth = 0.15f;
        armAlpha += ((shouldShow ? 1.0f : 0.0f) - armAlpha) * smooth;
        float targetScale = LexoraGui.numSettings.getOrDefault("Armor Status Scale", 1.0f);
        armScale += ((shouldShow ? targetScale : targetScale - 0.2f) - armScale) * smooth;

        if (armAlpha < 0.02f) return;

        int targetW = Math.max(120, (Math.max(1, count) * 20) + 10);
        armAnimW += (targetW - armAnimW) * smooth;

        armorW = Math.round(armAnimW);
        armorH = 45;

        int a = (int) (armAlpha * 255);
        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Armor Status Solid", false);
        int bgAlpha = isSolid ? 255 : (int)(armAlpha * 220);

        context.getMatrices().push();
        context.getMatrices().translate(HudManager.armorX + armorW / 2f, HudManager.armorY + armorH / 2f, -150);
        context.getMatrices().scale(armScale, armScale, 1.0f);
        context.getMatrices().translate(-(HudManager.armorX + armorW / 2f), -(HudManager.armorY + armorH / 2f), 0);

        drawSmoothRect(context, HudManager.armorX, HudManager.armorY, armorW, armorH, (bgAlpha << 24) | 0x0D0D11);
        drawHeader(context, HudManager.armorX, HudManager.armorY, armorW, ICON_ARMOR, "Armor Status", a, isSolid);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 10);

        int currentX = HudManager.armorX + 5;
        if (count == 0 && showPreview) {
            long time = System.currentTimeMillis();
            float wave = (float) (Math.sin(time / 400.0) * 0.5 + 0.5);
            int colorVal = (int) (100 + 60 * wave);
            int previewColor = (a << 24) | (colorVal << 16) | (colorVal << 8) | colorVal;

            drawCustomText(context, "Empty", HudManager.armorX + armorW / 2 - getCustomTextWidth("Empty") / 2, HudManager.armorY + 28, previewColor);
        } else {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    context.drawItem(stack, currentX, HudManager.armorY + 24);

                    if (stack.getCount() > 1) {
                        String scount = String.valueOf(stack.getCount());
                        context.getMatrices().push();
                        context.getMatrices().translate(0, 0, 200);
                        context.drawText(mc.textRenderer, scount, currentX + 17 - mc.textRenderer.getWidth(scount), HudManager.armorY + 32, (a << 24) | 0xFFFFFF, true);
                        context.getMatrices().pop();
                    }

                    if (stack.isDamageable()) {
                        float damageProgress = (float) stack.getDamage() / stack.getMaxDamage();
                        float durabilityLeft = Math.max(0.0f, 1.0f - damageProgress);
                        if (durabilityLeft < 1.0f) {
                            int color = net.minecraft.util.math.MathHelper.hsvToRgb(durabilityLeft / 3.0f, 1.0f, 1.0f);
                            int barWidth = Math.round(16.0f * durabilityLeft);
                            int barY = HudManager.armorY + 24 + 17;
                            context.fill(currentX, barY, currentX + 16, barY + 2, (a << 24) | 0x000000);
                            context.fill(currentX, barY, currentX + barWidth, barY + 1, (a << 24) | (color & 0xFFFFFF));
                        }
                    }
                    currentX += 20;
                }
            }
        }
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

    // ==========================================
    // 3. INVENTORY HUD
    // ==========================================
    private static void renderInventory(DrawContext context, MinecraftClient mc) {
        boolean shouldShow = LexoraGui.moduleStates.getOrDefault("Inventory HUD", false);

        float smooth = 0.15f;
        invAlpha += ((shouldShow ? 1.0f : 0.0f) - invAlpha) * smooth;
        float targetScale = LexoraGui.numSettings.getOrDefault("Inventory HUD Scale", 1.0f);
        invScale += ((shouldShow ? targetScale : targetScale - 0.2f) - invScale) * smooth;

        if (invAlpha < 0.02f) return;

        invW = (9 * 18) + 12;
        invH = (3 * 18) + 26;

        int a = (int) (invAlpha * 255);
        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Inventory HUD Solid", false);
        int bgAlpha = isSolid ? 255 : (int)(invAlpha * 220);

        context.getMatrices().push();
        context.getMatrices().translate(HudManager.invX + invW / 2f, HudManager.invY + invH / 2f, -150);
        context.getMatrices().scale(invScale, invScale, 1.0f);
        context.getMatrices().translate(-(HudManager.invX + invW / 2f), -(HudManager.invY + invH / 2f), 0);

        drawSmoothRect(context, HudManager.invX, HudManager.invY, invW, invH, (bgAlpha << 24) | 0x0D0D11);
        drawHeader(context, HudManager.invX, HudManager.invY, invW, ICON_INV, "Inventory", a, isSolid);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 10);

        int startX = HudManager.invX + 6;
        int startY = HudManager.invY + 22;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().main.get(i);
            int col = (i - 9) % 9;
            int row = (i - 9) / 9;

            int slotX = startX + (col * 18);
            int slotY = startY + (row * 18);

            drawSmoothRect(context, slotX, slotY, 16, 16, (a << 24) | 0x1A1A20);

            if (!stack.isEmpty()) {
                context.drawItem(stack, slotX, slotY);
                if (stack.getCount() > 1) {
                    String scount = String.valueOf(stack.getCount());
                    context.getMatrices().push();
                    context.getMatrices().translate(0, 0, 200);
                    context.drawText(mc.textRenderer, scount, slotX + 17 - mc.textRenderer.getWidth(scount), slotY + 8, 0xFFFFFF, true);
                    context.getMatrices().pop();
                }
            }
        }
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

    // ==========================================
    // 4. COOLDOWN HUD
    // ==========================================
    private static void renderCooldowns(DrawContext context, MinecraftClient mc, float tickDelta) {
        boolean enabled = LexoraGui.moduleStates.getOrDefault("Cooldowns", false);

        List<ItemStack> cooldownItems = new ArrayList<>();
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

        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        boolean shouldShow = enabled && (!cooldownItems.isEmpty() || showPreview);

        float smooth = 0.15f;
        coolAlpha += ((shouldShow ? 1.0f : 0.0f) - coolAlpha) * smooth;
        float targetScale = LexoraGui.numSettings.getOrDefault("Cooldowns Scale", 1.0f);
        coolScale += ((shouldShow ? targetScale : targetScale - 0.2f) - coolScale) * smooth;

        if (coolAlpha < 0.02f) return;

        int targetW = 120;
        if (!cooldownItems.isEmpty()) {
            for (ItemStack stack : cooldownItems) {
                String pureName = stack.getItem().getName().getString();
                int reqW = 5 + 22 + 5 + 16 + 5 + getCustomTextWidth(pureName) + 8;
                if (reqW > targetW) targetW = reqW;
            }
        }

        int targetH = showPreview && cooldownItems.isEmpty() ? 40 : 25 + (cooldownItems.size() * 22);

        coolAnimW += (targetW - coolAnimW) * smooth;
        coolH += (targetH - coolH) * smooth;
        if (coolH < 10) coolH = targetH;
        coolW = Math.round(coolAnimW);

        int a = (int) (coolAlpha * 255);
        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Cooldowns Solid", false);
        int bgAlpha = isSolid ? 255 : (int)(coolAlpha * 220);

        context.getMatrices().push();
        context.getMatrices().translate(HudManager.coolX + coolW / 2f, HudManager.coolY + coolH / 2f, -150);
        context.getMatrices().scale(coolScale, coolScale, 1.0f);
        context.getMatrices().translate(-(HudManager.coolX + coolW / 2f), -(HudManager.coolY + coolH / 2f), 0);

        drawSmoothRect(context, HudManager.coolX, HudManager.coolY, coolW, (int) coolH, (bgAlpha << 24) | 0x0D0D11);
        drawHeader(context, HudManager.coolX, HudManager.coolY, coolW, ICON_COOL, "Cooldowns", a, isSolid);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 10);

        long time = System.currentTimeMillis();

        if (cooldownItems.isEmpty() && showPreview) {
            float wave = (float) (Math.sin(time / 400.0) * 0.5 + 0.5);
            int colorVal = (int) (100 + 60 * wave);
            int previewColor = (a << 24) | (colorVal << 16) | (colorVal << 8) | colorVal;
            drawCustomText(context, "No Cooldowns", HudManager.coolX + coolW / 2 - getCustomTextWidth("No Cooldowns") / 2, HudManager.coolY + 22, previewColor);
        } else {
            int currentY = HudManager.coolY + 20;
            for (int i = 0; i < cooldownItems.size(); i++) {
                float wave = (float) (Math.sin((time / 400.0) + (i * 0.6)) * 0.5 + 0.5);
                int colorVal = (int) (150 + 80 * wave);
                int textColor = (a << 24) | (colorVal << 16) | (colorVal << 8) | colorVal;

                int timeColorVal = (int) (100 + 60 * wave);
                int timeColor = (a << 24) | (timeColorVal << 16) | (timeColorVal << 8) | timeColorVal;

                ItemStack stack = cooldownItems.get(i);
                net.minecraft.item.Item item = stack.getItem();

                float progressRender = mc.player.getItemCooldownManager().getCooldownProgress(stack, tickDelta);
                float progressRaw = mc.player.getItemCooldownManager().getCooldownProgress(stack, 0.0f);

                Float lastP = HudManager.lastProgressMap.get(item);
                if (lastP == null || progressRaw > lastP) HudManager.lastProgressMap.put(item, progressRaw);
                else if (lastP > progressRaw) {
                    float diff = lastP - progressRaw;
                    if (diff > 0.00001f) {
                        int totalTicks = Math.round(1.0f / diff);
                        if (totalTicks > 10) HudManager.totalTicksMap.put(item, totalTicks);
                        HudManager.lastProgressMap.put(item, progressRaw);
                    }
                }

                int totalTicks = HudManager.totalTicksMap.getOrDefault(item, 100);
                float remainingSeconds = (progressRaw * totalTicks) / 20.0f;
                String secText = String.format("%.1f", remainingSeconds).replace(",", ".");

                int timeX = HudManager.coolX + 5;
                drawCustomText(context, secText, timeX, currentY + 4, timeColor);

                int iconX = timeX + 22 + 5;
                context.drawItem(stack, iconX, currentY);

                int textX = iconX + 16 + 5;
                String itemName = stack.getItem().getName().getString();
                drawCustomText(context, itemName, textX, currentY + 1, textColor);

                int barWidth = (int) ((coolW - (textX - HudManager.coolX) - 8) * progressRender);
                context.fill(textX, currentY + 11, textX + (coolW - (textX - HudManager.coolX) - 8), currentY + 13, (a << 24) | 0x222222);
                context.fill(textX, currentY + 11, textX + barWidth, currentY + 13, (a << 24) | LexoraGui.getThemeColor(i * 0.1f));

                currentY += 22;
            }
        }
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

    private static void drawCustomText(DrawContext context, String text, int x, int y, int color) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        context.drawText(tr, Text.literal(text).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)), x, y, color, false);
    }

    private static int getCustomTextWidth(String text) {
        return MinecraftClient.getInstance().textRenderer.getWidth(Text.literal(text).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)));
    }

    private static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;

        float radius = Math.min(6.0f, Math.min(width / 2.0f, height / 2.0f));
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }

    private static void drawColoredTexturedQuad(DrawContext context, Identifier texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int r, int g, int b, int a) {
        if (a <= 0) return;
        float alpha = a / 255.0f;
        context.draw();

        RenderSystem.enableDepthTest();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumerProvider.Immediate bufferSource = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderLayer.getGuiTextured(texture));

        vertexConsumer.vertex(matrix, (float) x, (float) y, 0.0F).color(r / 255f, g / 255f, b / 255f, alpha).texture(u0, v0);
        vertexConsumer.vertex(matrix, (float) x, (float) (y + height), 0.0F).color(r / 255f, g / 255f, b / 255f, alpha).texture(u0, v1);
        vertexConsumer.vertex(matrix, (float) (x + width), (float) (y + height), 0.0F).color(r / 255f, g / 255f, b / 255f, alpha).texture(u1, v1);
        vertexConsumer.vertex(matrix, (float) (x + width), (float) y, 0.0F).color(r / 255f, g / 255f, b / 255f, alpha).texture(u1, v0);

        bufferSource.draw(RenderLayer.getGuiTextured(texture));
    }
}