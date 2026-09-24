package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.badge.LexoraIrcClient;
import com.lexoravisauls.client.badge.LexoraModUsers;
import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.events.HudThemeHelper;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.lexoravisauls.client.gui.modern.ModernGuiIcons;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Премиальный IRC чат Lexora Visuals.
 * Полностью согласован по стилю, геометрии и цветовой гамме с InventoryManagerScreen и ModernClickGui.
 */
public class LexoraIrcScreen extends Screen {

    private static final int GUI_W = 660;
    private static final int GUI_H = 370;
    private static final int HEADER_H = 32;
    private static final int LEFT_W = 170;

    private float openAnim = 0.0f;
    private boolean closing = false;

    private String messageInput = "";
    private boolean inputFocused = true;

    private String searchInput = "";
    private boolean searchFocused = false;

    private float chatScroll = 0f;
    private float targetChatScroll = 0f;
    private float userScroll = 0f;
    private float targetUserScroll = 0f;

    private final Map<String, Float> hoverAnims = new HashMap<>();
    private final Map<String, int[]> clickBounds = new HashMap<>();

    public LexoraIrcScreen() {
        super(Text.literal("Lexora IRC"));
    }

    private static MsdfFont font() {
        return ModernClickGui.SFUI;
    }

    private boolean isDark() {
        return HudThemeHelper.isDark();
    }

    @Override
    protected void init() {
        super.init();
        openAnim = 0.0f;
        closing = false;
        inputFocused = true;
        searchFocused = false;
        LexoraIrcClient.UNREAD_COUNTS.put(LexoraIrcClient.currentChannel, 0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    private void playClickSound() {
        try {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        } catch (Throwable ignored) {}
    }

    private float updateHover(String key, boolean isHovered) {
        float t = hoverAnims.getOrDefault(key, 0.0f);
        t += ((isHovered ? 1.0f : 0.0f) - t) * 0.25f;
        if (t < 0.001f) t = 0.0f;
        if (t > 0.999f) t = 1.0f;
        hoverAnims.put(key, t);
        return t;
    }

    private int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int interpolateColor(int c1, int c2, float ratio) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private List<String> wrapText(String text, float maxWidth, float fontSize) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (font().getWidth(testLine, fontSize) <= maxWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (currentLine.length() > 0) lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
        }
    }

    // ─── RENDER ──────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (closing) {
            openAnim += (0.0f - openAnim) * 0.22f;
            if (openAnim < 0.01f) {
                super.close();
                return;
            }
        } else {
            openAnim += (1.0f - openAnim) * 0.18f;
            if (openAnim > 0.999f) openAnim = 1.0f;
        }

        chatScroll += (targetChatScroll - chatScroll) * 0.25f;
        userScroll += (targetUserScroll - userScroll) * 0.25f;

        clickBounds.clear();

        boolean dark = isDark();

        // 1. Затемнённый фон (Overlay)
        int overlayAlpha = (int) (140 * openAnim);
        context.fill(0, 0, this.width, this.height, (overlayAlpha << 24));

        float guiX = (this.width - GUI_W) / 2.0f;
        float guiY = (this.height - GUI_H) / 2.0f;

        float scale = 0.94f + 0.06f * openAnim;
        float scaledX = guiX + (GUI_W * (1.0f - scale) / 2.0f);
        float scaledY = guiY + (GUI_H * (1.0f - scale) / 2.0f);
        float scaledW = GUI_W * scale;
        float scaledH = GUI_H * scale;

        int winBg = dark ? 0xF00D0D11 : 0xF4F5F6FA;

        // 2. Главная плашка окна (идентично ModernClickGui и InventoryManager)
        RoundedRectShader.draw(context, scaledX, scaledY, scaledW, scaledH, 12.0f, withAlpha(winBg, openAnim));

        // 3. Шапка окна
        drawHeader(context, scaledX, scaledY, scaledW, HEADER_H * scale, dark, openAnim, mouseX, mouseY);

        // 4. Две колонки контента
        float contentX = scaledX + 8;
        float contentY = scaledY + (HEADER_H * scale) + 6;
        float contentW = scaledW - 16;
        float contentH = scaledH - (HEADER_H * scale) - 14;

        float leftColW = LEFT_W * scale;
        float rightColW = contentW - leftColW - 8;

        drawLeftColumn(context, contentX, contentY, leftColW, contentH, dark, openAnim, mouseX, mouseY);
        drawChatColumn(context, contentX + leftColW + 8, contentY, rightColW, contentH, dark, openAnim, mouseX, mouseY);
    }

    // ─── ХЕДЕР ───────────────────────────────────────────────────────────────

    private void drawHeader(DrawContext context, float x, float y, float w, float h, boolean dark, float anim, int mx, int my) {
        int sepCol = dark ? 0x25FFFFFF : 0x20000000;
        RoundedRectShader.draw(context, x, y + h - 1, w, 1, 0.0f, withAlpha(sepCol, anim));

        // Иконка и заголовок
        ModernGuiIcons.draw(context, ModernGuiIcons.Icon.SPARKLE, x + 12, y + (h - 11.0f) / 2.0f, 11.0f, withAlpha(dark ? 0xFFFFFFFF : 0xFF141418, anim));
        font().draw(context, "|", x + 26, y + (h - 10.0f) / 2.0f, 9.5f, withAlpha(dark ? 0xFF454555 : 0xFFB5B5C5, anim));
        font().draw(context, "Lexora IRC", x + 34, y + (h - 10.0f) / 2.0f, 9.5f, withAlpha(dark ? 0xFFEEEEF5 : 0xFF141418, anim));
        font().draw(context, "Глобальный чат пользователей", x + 105, y + (h - 8.0f) / 2.0f + 0.5f, 7.5f, withAlpha(dark ? 0xFF7A7A8A : 0xFF8A8A9A, anim));

        // Онлайн счетчик (Pill)
        int onlineCount = Math.max(1, LexoraModUsers.getOnlineNames().size());
        float pillX = x + w - 175;
        float pillY = y + (h - 18) / 2.0f;
        int pillBg = dark ? 0x9022222C : 0xD0FFFFFF;
        RoundedRectShader.draw(context, pillX, pillY, 68, 18, 5.0f, withAlpha(pillBg, anim));

        // Зеленая точка
        long t = System.currentTimeMillis();
        float pulse = (float) (0.7 + 0.3 * Math.sin(t / 250.0));
        int dotCol = ((int)(255 * pulse) << 24) | 0x22C55E;
        RoundedRectShader.draw(context, pillX + 6, pillY + 6, 6, 6, 3f, withAlpha(dotCol, anim));
        font().draw(context, onlineCount + " в сети", pillX + 16, pillY + 5.5f, 7.5f, withAlpha(dark ? 0xFFEEEEF2 : 0xFF141418, anim));

        // Кнопка DND (Не беспокоить)
        boolean isDnd = ClientData.moduleStates.getOrDefault("Lexora IRC DND", false)
                || LexoraGui.moduleStates.getOrDefault("Lexora IRC DND", false);
        float dndX = x + w - 100;
        float dndY = y + (h - 18) / 2.0f;
        boolean hDnd = inside(mx, my, dndX, dndY, 68, 18);
        float hDndAnim = updateHover("btn:dnd", hDnd);
        int dndBg = isDnd ? (dark ? 0x50EF4444 : 0x35EF4444)
                : interpolateColor(dark ? 0x9022222C : 0xD0FFFFFF, dark ? 0xFF2A2A38 : 0xFFE0E4EC, hDndAnim);
        RoundedRectShader.draw(context, dndX, dndY, 68, 18, 5.0f, withAlpha(dndBg, anim));
        int dndTextCol = isDnd ? 0xFFEF4444 : (dark ? 0xFFEEEEF2 : 0xFF141418);
        font().draw(context, isDnd ? "DND: ВКЛ" : "DND: ВЫКЛ", dndX + 9, dndY + 5.5f, 7.5f, withAlpha(dndTextCol, anim));
        clickBounds.put("action:dnd", new int[]{(int) dndX, (int) dndY, 68, 18});

        // Кнопка закрыть (✕)
        float btnX = x + w - 24;
        float btnY = y + (h - 18) / 2.0f;
        boolean hClose = inside(mx, my, btnX, btnY, 18, 18);
        float hCloseAnim = updateHover("btn:close", hClose);
        int closeBg = interpolateColor(0x00000000, dark ? 0x40EF4444 : 0x30EF4444, hCloseAnim);
        if (hCloseAnim > 0.01f) {
            RoundedRectShader.draw(context, btnX, btnY, 18, 18, 4.0f, withAlpha(closeBg, anim));
        }
        int closeCol = interpolateColor(dark ? 0xFF8A8A9A : 0xFF656575, 0xFFFF4444, hCloseAnim);
        LexoraIcons.draw(context, LexoraIcons.Icon.CLOSE, btnX + 4.5f, btnY + 4.5f, 9.0f, withAlpha(closeCol, anim));
        clickBounds.put("action:close", new int[]{(int) btnX, (int) btnY, 18, 18});
    }

    // ─── ЛЕВАЯ КОЛОНКА (КАНАЛЫ И СПИСОК ОНЛАЙНА) ─────────────────────────────

    private void drawLeftColumn(DrawContext context, float x, float y, float w, float h, boolean dark, float anim, int mx, int my) {
        int colBg = dark ? 0xC0111115 : 0xD5FFFFFF;
        RoundedRectShader.draw(context, x, y, w, h, 8.0f, withAlpha(colBg, anim));

        float curY = y + 8;

        // Заголовок: Категории
        int secTitleCol = dark ? 0xFF585866 : 0xFF8A8A99;
        font().draw(context, "КАНАЛЫ", x + 10, curY, 7.5f, withAlpha(secTitleCol, anim));
        curY += 12;

        // Кнопка # Общий чат
        boolean isGlobal = LexoraIrcClient.currentChannel.equals("Global");
        boolean hGlobal = inside(mx, my, x + 6, curY, w - 12, 22);
        float hGlobalAnim = updateHover("chan:global", hGlobal);

        if (isGlobal) {
            int activePill = dark ? 0x9022222C : 0xD0FFFFFF;
            RoundedRectShader.draw(context, x + 6, curY, w - 12, 22, 5.0f, withAlpha(activePill, anim));
        } else if (hGlobalAnim > 0.01f) {
            int hoverPill = dark ? 0x401C1C24 : 0x50E0E4ED;
            RoundedRectShader.draw(context, x + 6, curY, w - 12, 22, 5.0f, withAlpha(hoverPill, anim * hGlobalAnim));
        }

        int globalIconCol = isGlobal ? (dark ? 0xFFA855F7 : 0xFF9333EA) : (dark ? 0xFF8E8E9E : 0xFF6E6E7E);
        LexoraIcons.draw(context, LexoraIcons.Icon.GLOBE, x + 12, curY + 6, 10.0f, withAlpha(globalIconCol, anim));
        int globalTextCol = isGlobal ? (dark ? 0xFFFFFFFF : 0xFF0E0E12) : (dark ? 0xFF9E9EAE : 0xFF5E5E6E);
        font().draw(context, "Общий чат", x + 28, curY + 7.5f, 8.5f, withAlpha(globalTextCol, anim));

        int globalUnread = LexoraIrcClient.UNREAD_COUNTS.getOrDefault("Global", 0);
        if (globalUnread > 0) {
            RoundedRectShader.draw(context, x + w - 24, curY + 4, 14, 14, 7f, withAlpha(0xFFEF4444, anim));
            font().draw(context, String.valueOf(globalUnread), x + w - 20, curY + 7.5f, 7f, withAlpha(0xFFFFFFFF, anim));
        }
        clickBounds.put("chan:global", new int[]{(int)(x + 6), (int)curY, (int)(w - 12), 22});

        curY += 28;

        // Разделитель и заголовок пользователей
        List<String> rawUsers = new ArrayList<>(LexoraModUsers.getOnlineNames());
        font().draw(context, "В СЕТИ (" + Math.max(1, rawUsers.size()) + ")", x + 10, curY, 7.5f, withAlpha(secTitleCol, anim));
        curY += 11;

        // Поле поиска
        float inX = x + 6;
        float inW = w - 12;
        float inH = 20.0f;
        int inBg = searchFocused ? (dark ? 0xFF22222E : 0xFFD8DCE8) : (dark ? 0xFF181820 : 0xFFE5E8F0);
        RoundedRectShader.draw(context, inX, curY, inW, inH, 4.5f, withAlpha(inBg, anim));
        ModernGuiIcons.draw(context, ModernGuiIcons.Icon.SEARCH, inX + 6, curY + 5.5f, 9.0f, withAlpha(dark ? 0xFF656575 : 0xFF8A8A9A, anim));

        String displaySearch = searchInput.isEmpty() && !searchFocused ? "Поиск игрока..." : searchInput;
        if (searchFocused && ((System.currentTimeMillis() / 500) % 2 == 0)) displaySearch += "|";
        int searchTxtCol = searchInput.isEmpty() && !searchFocused ? (dark ? 0xFF555566 : 0xFF9999AA) : (dark ? 0xFFFFFFFF : 0xFF141418);
        font().draw(context, displaySearch, inX + 18, curY + 6.0f, 7.5f, withAlpha(searchTxtCol, anim));
        clickBounds.put("input:search", new int[]{(int) inX, (int) curY, (int) inW, (int) inH});

        curY += 25;

        // Список онлайн пользователей
        List<String> filteredUsers = new ArrayList<>();
        for (String u : rawUsers) {
            String disp = LexoraModUsers.getIrcDisplayName(u);
            if (searchInput.isEmpty() || disp.toLowerCase().contains(searchInput.toLowerCase())) {
                filteredUsers.add(u);
            }
        }

        filteredUsers.sort((a, b) -> {
            int ua = LexoraIrcClient.UNREAD_COUNTS.getOrDefault(a, 0);
            int ub = LexoraIrcClient.UNREAD_COUNTS.getOrDefault(b, 0);
            if (ua != ub) return Integer.compare(ub, ua);
            return LexoraModUsers.getIrcDisplayName(a).compareToIgnoreCase(LexoraModUsers.getIrcDisplayName(b));
        });

        float listH = (y + h - 8) - curY;
        context.enableScissor((int)x, (int)curY, (int)(x + w), (int)(y + h - 6));

        float uY = curY + userScroll;
        for (String userName : filteredUsers) {
            if (uY > y + h) break;
            if (uY + 22 >= curY) {
                boolean isSelected = LexoraIrcClient.currentChannel.equals(userName);
                boolean hUser = inside(mx, my, x + 6, uY, w - 12, 22);
                float hUserAnim = updateHover("user:" + userName, hUser);

                if (isSelected) {
                    int activePill = dark ? 0x9022222C : 0xD0FFFFFF;
                    RoundedRectShader.draw(context, x + 6, uY, w - 12, 22, 5.0f, withAlpha(activePill, anim));
                } else if (hUserAnim > 0.01f) {
                    int hoverPill = dark ? 0x401C1C24 : 0x50E0E4ED;
                    RoundedRectShader.draw(context, x + 6, uY, w - 12, 22, 5.0f, withAlpha(hoverPill, anim * hUserAnim));
                }

                String dispName = LexoraModUsers.getIrcDisplayName(userName);
                UUID uUuid = LexoraIrcClient.NAME_TO_UUID.getOrDefault(userName, LexoraIrcClient.NAME_TO_UUID.getOrDefault(dispName, Util.NIL_UUID));

                // Голова скина игрока
                SkinTextures textures = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(new GameProfile(uUuid, dispName));
                PlayerSkinDrawer.draw(context, textures, (int)(x + 10), (int)(uY + 3), 8);

                int userTextCol = isSelected ? (dark ? 0xFFFFFFFF : 0xFF0E0E12) : (dark ? 0xFF9E9EAE : 0xFF5E5E6E);
                font().draw(context, dispName, x + 26, uY + 7.5f, 8.0f, withAlpha(userTextCol, anim));

                // Непрочитанные ЛС
                int unread = LexoraIrcClient.UNREAD_COUNTS.getOrDefault(userName, 0);
                if (unread > 0) {
                    RoundedRectShader.draw(context, x + w - 22, uY + 4, 14, 14, 7f, withAlpha(0xFFEF4444, anim));
                    font().draw(context, String.valueOf(unread), x + w - 18, uY + 7.5f, 7f, withAlpha(0xFFFFFFFF, anim));
                }
                clickBounds.put("user:" + userName, new int[]{(int)(x + 6), (int)uY, (int)(w - 12), 22});
            }
            uY += 24;
        }

        context.disableScissor();
    }

    // ─── ПРАВАЯ КОЛОНКА (ЧАТ И ВВОД) ──────────────────────────────────────────

    private void drawChatColumn(DrawContext context, float x, float y, float w, float h, boolean dark, float anim, int mx, int my) {
        int colBg = dark ? 0xC0111115 : 0xD5FFFFFF;
        RoundedRectShader.draw(context, x, y, w, h, 8.0f, withAlpha(colBg, anim));

        // Верхняя плашка текущего чата
        float headerH = 26;
        int sepCol = dark ? 0x20FFFFFF : 0x15000000;
        RoundedRectShader.draw(context, x, y + headerH, w, 1, 0.0f, withAlpha(sepCol, anim));

        boolean isGlobal = LexoraIrcClient.currentChannel.equals("Global");
        String currentDisp = isGlobal ? "Общий чат" : LexoraModUsers.getIrcDisplayName(LexoraIrcClient.currentChannel);

        if (isGlobal) {
            LexoraIcons.draw(context, LexoraIcons.Icon.GLOBE, x + 10, y + 8, 10.0f, withAlpha(dark ? 0xFFA855F7 : 0xFF9333EA, anim));
            font().draw(context, "# общий-чат", x + 24, y + 9.5f, 8.5f, withAlpha(dark ? 0xFFEEEEF5 : 0xFF141418, anim));
            font().draw(context, "— сообщения видны всем пользователям Lexora", x + 85, y + 10.5f, 7.0f, withAlpha(dark ? 0xFF7A7A8A : 0xFF8A8A9A, anim));
        } else {
            UUID uUuid = LexoraIrcClient.NAME_TO_UUID.getOrDefault(LexoraIrcClient.currentChannel, Util.NIL_UUID);
            SkinTextures textures = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(new GameProfile(uUuid, currentDisp));
            PlayerSkinDrawer.draw(context, textures, (int)(x + 10), (int)(y + 5), 8);
            font().draw(context, "@ " + currentDisp, x + 24, y + 9.5f, 8.5f, withAlpha(dark ? 0xFFEEEEF5 : 0xFF141418, anim));
            font().draw(context, "— личные сообщения", x + 24 + font().getWidth("@ " + currentDisp, 8.5f) + 6, y + 10.5f, 7.0f, withAlpha(dark ? 0xFF7A7A8A : 0xFF8A8A9A, anim));
        }

        // Область сообщений
        float inputBarH = 34.0f;
        float chatAreaY = y + headerH + 4;
        float chatAreaH = h - headerH - inputBarH - 8;

        List<LexoraIrcClient.IrcMessage> msgs = LexoraIrcClient.getMessages(LexoraIrcClient.currentChannel);

        context.enableScissor((int)x + 4, (int)chatAreaY, (int)(x + w - 4), (int)(chatAreaY + chatAreaH));

        if (msgs.isEmpty()) {
            // Заглушка, если сообщений нет
            float emptyX = x + w / 2.0f;
            float emptyY = chatAreaY + chatAreaH / 2.0f - 10;
            LexoraIcons.draw(context, LexoraIcons.Icon.TEXT, emptyX - 6, emptyY - 10, 14f, withAlpha(dark ? 0xFF454555 : 0xFF9999AA, anim));
            String emptyStr = isGlobal ? "Здесь пока нет сообщений. Напишите первым!" : "Начните личную переписку с @" + currentDisp;
            float ew = font().getWidth(emptyStr, 8.0f);
            font().draw(context, emptyStr, emptyX - ew / 2.0f, emptyY + 10, 8.0f, withAlpha(dark ? 0xFF656575 : 0xFF8A8A9A, anim));
        } else {
            float msgY = chatAreaY + chatAreaH - 16 + chatScroll;
            String myName = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getName().getString() : "";

            for (int i = msgs.size() - 1; i >= 0; i--) {
                LexoraIrcClient.IrcMessage msg = msgs.get(i);
                if (msgY < chatAreaY - 80) break;

                boolean mentionsMe = !msg.isSelf && !myName.isEmpty() && msg.text.toLowerCase().contains("@" + myName.toLowerCase());
                String senderDisp = msg.isSelf ? "Вы" : LexoraModUsers.getIrcDisplayName(msg.name);
                float nameW = font().getWidth(senderDisp + ": ", 8.0f);

                List<String> wrapped = wrapText(msg.text, w - 45 - nameW, 8.0f);

                for (int j = wrapped.size() - 1; j >= 0; j--) {
                    if (msgY <= chatAreaY + chatAreaH + 6 && msgY >= chatAreaY - 12) {
                        if (mentionsMe) {
                            RoundedRectShader.draw(context, x + 6, msgY - 2, w - 12, 13, 3.5f, withAlpha(dark ? 0x25EAB308 : 0x20EAB308, anim));
                        }

                        if (j == 0) {
                            // Аватарка
                            UUID uUuid = msg.isSelf ? (MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getUuid() : Util.NIL_UUID)
                                    : LexoraIrcClient.NAME_TO_UUID.getOrDefault(msg.name, Util.NIL_UUID);
                            SkinTextures textures = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(new GameProfile(uUuid, senderDisp));
                            PlayerSkinDrawer.draw(context, textures, (int)(x + 10), (int)(msgY - 1), 6);

                            int nameColor = msg.isSelf ? (dark ? 0xFFC084FC : 0xFF7C3AED) : (mentionsMe ? 0xFFEAB308 : (dark ? 0xFFE0E0EC : 0xFF141418));
                            font().draw(context, senderDisp + ":", x + 20, msgY + 0.5f, 8.0f, withAlpha(nameColor, anim));

                            int textColor = msg.isSelf ? (dark ? 0xFFE9D5FF : 0xFF4C1D95) : (dark ? 0xFFD1D5DB : 0xFF374151);
                            font().draw(context, wrapped.get(j), x + 20 + nameW, msgY + 0.5f, 8.0f, withAlpha(textColor, anim));
                        } else {
                            int textColor = msg.isSelf ? (dark ? 0xFFE9D5FF : 0xFF4C1D95) : (dark ? 0xFFD1D5DB : 0xFF374151);
                            font().draw(context, wrapped.get(j), x + 20 + nameW, msgY + 0.5f, 8.0f, withAlpha(textColor, anim));
                        }
                    }
                    msgY -= 12;
                }
                msgY -= 4;
            }
        }

        context.disableScissor();

        // Нижняя панель ввода (Поле ввода + кнопка "Отправить" в стиле InventoryManager)
        float inX = x + 8;
        float inY = y + h - 28;
        float btnW = 74.0f;
        float inW = w - 16 - btnW - 6;
        float inH = 20.0f;

        int inBg = inputFocused ? (dark ? 0xFF22222E : 0xFFD8DCE8) : (dark ? 0xFF181820 : 0xFFE5E8F0);
        RoundedRectShader.draw(context, inX, inY, inW, inH, 4.5f, withAlpha(inBg, anim));

        String placeholder = isGlobal ? "Написать в #общий-чат..." : "Личное сообщение для @" + currentDisp + "...";
        String displayInput = messageInput.isEmpty() && !inputFocused ? placeholder : messageInput;
        if (inputFocused && ((System.currentTimeMillis() / 500) % 2 == 0)) displayInput += "|";
        int inputTxtCol = messageInput.isEmpty() && !inputFocused ? (dark ? 0xFF555566 : 0xFF9999AA) : (dark ? 0xFFFFFFFF : 0xFF141418);
        font().draw(context, displayInput, inX + 7, inY + 5.5f, 7.5f, withAlpha(inputTxtCol, anim));
        clickBounds.put("input:message", new int[]{(int) inX, (int) inY, (int) inW, (int) inH});

        // Кнопка "Отправить"
        float sBtnX = inX + inW + 6;
        boolean canSend = !messageInput.trim().isEmpty();
        boolean hSend = canSend && inside(mx, my, sBtnX, inY, btnW, inH);
        float hSendAnim = updateHover("btn:send", hSend);

        int sendBg = canSend
                ? interpolateColor(dark ? 0xFFA855F7 : 0xFF9333EA, dark ? 0xFFC084FC : 0xFFA855F7, hSendAnim)
                : (dark ? 0xFF22222C : 0xFFD2D6E0);
        int sendTextCol = canSend
                ? 0xFFFFFFFF
                : (dark ? 0xFF666677 : 0xFF888899);

        RoundedRectShader.draw(context, sBtnX, inY, btnW, inH, 4.5f, withAlpha(sendBg, anim));
        font().draw(context, "Отправить", sBtnX + 13, inY + 5.5f, 7.5f, withAlpha(sendTextCol, anim));
        if (canSend) {
            clickBounds.put("action:send", new int[]{(int) sBtnX, (int) inY, (int) btnW, (int) inH});
        }
    }

    // ─── ОБРАБОТКА ВВОДА И КЛИКОВ ────────────────────────────────────────────

    private void sendMessage() {
        String text = messageInput.trim();
        if (!text.isEmpty()) {
            if (LexoraIrcClient.sendFromGui(text)) {
                messageInput = "";
                targetChatScroll = 0f;
                playClickSound();
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (inputFocused) {
                sendMessage();
                return true;
            }
        }

        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !messageInput.isEmpty()) {
                messageInput = messageInput.substring(0, messageInput.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null) {
                    messageInput += clip;
                    if (messageInput.length() > 200) messageInput = messageInput.substring(0, 200);
                }
                return true;
            }
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchInput.isEmpty()) {
                searchInput = searchInput.substring(0, searchInput.length() - 1);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused) {
            if (chr >= 32 && chr != 127 && messageInput.length() < 200) {
                messageInput += chr;
                return true;
            }
        }
        if (searchFocused) {
            if (chr >= 32 && chr != 127 && searchInput.length() < 30) {
                searchInput += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float guiX = (this.width - GUI_W) / 2.0f;
        float guiY = (this.height - GUI_H) / 2.0f;

        if (mouseX >= guiX && mouseX <= guiX + LEFT_W) {
            targetUserScroll += (float) (verticalAmount * 24.0);
            if (targetUserScroll > 0) targetUserScroll = 0;
            return true;
        } else if (mouseX > guiX + LEFT_W && mouseX <= guiX + GUI_W) {
            targetChatScroll -= (float) (verticalAmount * 24.0);
            if (targetChatScroll < 0) targetChatScroll = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Map.Entry<String, int[]> entry : clickBounds.entrySet()) {
            int[] b = entry.getValue();
            if (inside((float) mouseX, (float) mouseY, b[0], b[1], b[2], b[3])) {
                String key = entry.getKey();

                if ("action:close".equals(key)) {
                    playClickSound();
                    close();
                    return true;
                }

                if ("action:dnd".equals(key)) {
                    boolean cur = ClientData.moduleStates.getOrDefault("Lexora IRC DND", false);
                    ClientData.moduleStates.put("Lexora IRC DND", !cur);
                    LexoraGui.moduleStates.put("Lexora IRC DND", !cur);
                    playClickSound();
                    return true;
                }

                if ("chan:global".equals(key)) {
                    LexoraIrcClient.currentChannel = "Global";
                    LexoraIrcClient.UNREAD_COUNTS.put("Global", 0);
                    targetChatScroll = 0f;
                    playClickSound();
                    return true;
                }

                if (key.startsWith("user:")) {
                    String userName = key.substring(5);
                    LexoraIrcClient.currentChannel = userName;
                    LexoraIrcClient.UNREAD_COUNTS.put(userName, 0);
                    targetChatScroll = 0f;
                    playClickSound();
                    return true;
                }

                if ("input:message".equals(key)) {
                    inputFocused = true;
                    searchFocused = false;
                    return true;
                }

                if ("input:search".equals(key)) {
                    searchFocused = true;
                    inputFocused = false;
                    return true;
                }

                if ("action:send".equals(key)) {
                    sendMessage();
                    return true;
                }
            }
        }

        inputFocused = false;
        searchFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
