package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NotifManager {
    private static final Identifier SUCCESS_ICON = Identifier.of("lexoravisauls", "textures/gui/success.png");
    private static final Identifier WARNING_ICON = Identifier.of("lexoravisauls", "textures/gui/info11.png");
    private static final Identifier CUSTOM_FONT = Identifier.of("lexoravisauls", "sfui");

    public enum NotifType { SUCCESS, WARNING }

    private static class Notif {
        String title, content;
        NotifType type;
        long startTime;
        long maxTime = 3000;
        long fadeTime = 250;
        float currentY = 0, targetY = 0;

        Notif(String title, String content, NotifType type) {
            this.title = title;
            this.content = content;
            this.type = type;
            this.startTime = System.currentTimeMillis();
        }
    }

    private static class TimerNotif {
        String title;
        NotifType type;
        long endTime;
        float currentY = 0, targetY = 0;

        TimerNotif(String title, float seconds, NotifType type) {
            this.title = title;
            this.type = type;
            this.endTime = System.currentTimeMillis() + (long) (seconds * 1000);
        }
    }

    private static final List<Notif> notifs = new ArrayList<>();
    private static final Map<String, TimerNotif> activeTimers = new HashMap<>();

    private static boolean hpAlerted = false;
    private static boolean armorAlerted = false;
    private static final Set<StatusEffect> alertedPotions = new HashSet<>();

    // Превью для чата
    private static Notif chatPreviewNotif = null;
    private static boolean wasChatOpenLastFrame = false;

    public static void show(String title, String content, NotifType type) {
        if (type == NotifType.SUCCESS && !LexoraGui.moduleStates.getOrDefault("Notif Swap", true)) return;
        notifs.add(new Notif(title, content, type));
        SoundUtil.playCustomSound("notification", 1.0f);
    }

    public static void startTimer(String id, String title, float seconds, NotifType type) {
        activeTimers.put(id, new TimerNotif(title, seconds, type));
        SoundUtil.playCustomSound("notification", 1.0f);
    }

    public static void tick(MinecraftClient mc) {
        if (!LexoraGui.moduleStates.getOrDefault("Notifications", true) || mc.player == null) return;

        if (LexoraGui.moduleStates.getOrDefault("Notif HP", true)) {
            if (mc.player.getHealth() <= 8.0f) {
                if (!hpAlerted) {
                    show("Низкое здоровье!", "Срочно восстановите ХП", NotifType.WARNING);
                    hpAlerted = true;
                }
            } else {
                hpAlerted = false;
            }
        }

        if (LexoraGui.moduleStates.getOrDefault("Notif Armor", true)) {
            boolean lowArmor = false;
            for (ItemStack armor : mc.player.getArmorItems()) {
                if (armor.isEmpty() || !armor.isDamageable()) continue;
                float percent = 1.0f - ((float) armor.getDamage() / armor.getMaxDamage());
                if (percent < 0.1f) {
                    lowArmor = true;
                    break;
                }
            }

            if (lowArmor) {
                if (!armorAlerted) {
                    show("Низкая прочность!", "Броня скоро сломается", NotifType.WARNING);
                    armorAlerted = true;
                }
            } else {
                armorAlerted = false;
            }
        }

        if (LexoraGui.moduleStates.getOrDefault("Notif Potions", true)) {
            Set<StatusEffect> activeEffects = new HashSet<>();
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                StatusEffect type = effect.getEffectType().value();
                activeEffects.add(type);

                if (type.isBeneficial() && effect.getDuration() == 60) {
                    if (!alertedPotions.contains(type)) {
                        show("Действие зелья", "Эффект скоро закончится", NotifType.WARNING);
                        alertedPotions.add(type);
                    }
                }
            }
            alertedPotions.retainAll(activeEffects);
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        if (!LexoraGui.moduleStates.getOrDefault("Notifications", true)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isChatOpen = mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;

        if (!isChatOpen) {
            notifs.removeIf(n -> System.currentTimeMillis() - n.startTime > n.maxTime);
            activeTimers.entrySet().removeIf(entry -> System.currentTimeMillis() > entry.getValue().endTime);
        }

        if (isChatOpen && !wasChatOpenLastFrame) {
            chatPreviewNotif = new Notif("Свапнул на Тотем бессмертия", "Успешно!", NotifType.SUCCESS);
            chatPreviewNotif.currentY = 0;
            chatPreviewNotif.targetY = 0;
        }

        if (!isChatOpen) {
            chatPreviewNotif = null;
        }

        wasChatOpenLastFrame = isChatOpen;

        if (notifs.isEmpty() && activeTimers.isEmpty() && chatPreviewNotif == null && !isChatOpen) return;

        TextRenderer tr = mc.textRenderer;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float scale = LexoraGui.numSettings.getOrDefault("Notif Scale", 1.0f);
        boolean solidBg = LexoraGui.moduleStates.getOrDefault("Notif Solid Bg", false);

        int spacing = 5;
        int height = 15;
        float targetY = screenH - 100;

        context.getMatrices().push();
        context.getMatrices().translate(screenW / 2f, screenH, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-screenW / 2f, -screenH, 0);

        // Таймеры
        for (TimerNotif timer : activeTimers.values()) {
            long timeLeft = timer.endTime - System.currentTimeMillis();
            int secondsLeft = Math.max(0, (int) Math.ceil(timeLeft / 1000.0));

            timer.targetY = targetY;
            if (timer.currentY == 0) timer.currentY = targetY + 15;
            timer.currentY += (timer.targetY - timer.currentY) * 0.15f;

            Text titleText = Text.literal(timer.title).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));
            Text contentText = Text.literal("Осталось: " + secondsLeft + " сек").setStyle(Style.EMPTY.withFont(CUSTOM_FONT));

            int titleWidth = tr.getWidth(titleText);
            int contentWidth = tr.getWidth(contentText);
            int textWidth = titleWidth + 4 + contentWidth;

            int iconSize = 10;
            int padding = 4;
            int gap = 4;

            int width = padding + iconSize + gap + textWidth + padding + 2;
            int x = (screenW - width) / 2;
            int y = (int) timer.currentY;

            int bgAlpha = solidBg ? 255 : 153;
            int bgColor = (bgAlpha << 24) | 0x141416;
            drawSmoothRect(context, x, y, width, height, bgColor);

            int iconX = x + padding;
            int iconY = y + (height - iconSize) / 2;
            Identifier iconTex = timer.type == NotifType.SUCCESS ? SUCCESS_ICON : WARNING_ICON;
            drawColoredQuad(context, iconTex, iconX, iconY, iconSize, iconSize, 255, 255, 255, 255);

            int textStartX = iconX + iconSize + gap;
            context.drawText(tr, titleText, textStartX, y + 4, 0xFFFFFFFF, false);
            context.drawText(tr, contentText, textStartX + titleWidth + 4, y + 4, 0xFFAAAAAA, false);

            targetY -= (height + spacing);
        }

        // Обычные уведомления + превью в чате
        List<Notif> renderList = new ArrayList<>(notifs);
        if (chatPreviewNotif != null) {
            renderList.add(chatPreviewNotif);
        }

        for (Notif notif : renderList) {
            boolean isPreview = notif == chatPreviewNotif;
            long elapsed = System.currentTimeMillis() - notif.startTime;

            notif.targetY = targetY;
            if (notif.currentY == 0) notif.currentY = targetY + 15;
            notif.currentY += (notif.targetY - notif.currentY) * 0.15f;

            float animProgress = 1.0f;

            if (isPreview) {
                if (elapsed < notif.fadeTime) {
                    animProgress = (float) elapsed / notif.fadeTime;
                    animProgress = (float) (1.0 - Math.pow(1.0 - animProgress, 3));
                }
            } else if (!isChatOpen) {
                if (elapsed < notif.fadeTime) {
                    animProgress = (float) elapsed / notif.fadeTime;
                    animProgress = (float) (1.0 - Math.pow(1.0 - animProgress, 3));
                } else if (elapsed > notif.maxTime - notif.fadeTime) {
                    animProgress = (float) (notif.maxTime - elapsed) / notif.fadeTime;
                    animProgress = animProgress * animProgress;
                }
            }

            int alphaInt = Math.max(0, Math.min(255, (int) (animProgress * 255)));
            if (alphaInt <= 5) continue;

            Text titleText = Text.literal(notif.title).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));
            Text contentText = Text.literal(notif.content).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));

            int titleWidth = tr.getWidth(titleText);
            int contentWidth = tr.getWidth(contentText);
            int textWidth = titleWidth + 4 + contentWidth;

            int iconSize = 10;
            int padding = 4;
            int gap = 4;

            int width = padding + iconSize + gap + textWidth + padding + 2;
            int x = (screenW - width) / 2;
            int y = (int) notif.currentY;

            int bgAlpha = solidBg ? alphaInt : (int) (alphaInt * 0.6f);
            int bgColor = (bgAlpha << 24) | 0x141416;
            drawSmoothRect(context, x, y, width, height, bgColor);

            int iconX = x + padding;
            int iconY = y + (height - iconSize) / 2;
            Identifier iconTex = notif.type == NotifType.SUCCESS ? SUCCESS_ICON : WARNING_ICON;
            drawColoredQuad(context, iconTex, iconX, iconY, iconSize, iconSize, 255, 255, 255, alphaInt);

            int textStartX = iconX + iconSize + gap;
            context.drawText(tr, titleText, textStartX, y + 4, (alphaInt << 24) | 0xFFFFFF, false);
            context.drawText(tr, contentText, textStartX + titleWidth + 4, y + 4, (alphaInt << 24) | 0xAAAAAA, false);

            targetY -= (height + spacing);
        }

        context.getMatrices().pop();
    }

    private static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        float radius = Math.min(6.0f, Math.min(width / 2.0f, height / 2.0f));
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }

    private static void drawColoredQuad(DrawContext context, Identifier texture, int x, int y, int width, int height, int r, int g, int b, int a) {
        RenderSystem.setShaderColor(r / 255f, g / 255f, b / 255f, a / 255f);
        RenderSystem.enableBlend();

        context.drawTexture(
                RenderLayer::getGuiTextured,
                texture,
                x, y,
                0.0f, 0.0f,
                width, height,
                width, height
        );

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}