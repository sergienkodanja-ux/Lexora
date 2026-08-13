package com.lexoravisauls.client.gui;

import com.lexoravisauls.client.badge.LexoraIrcClient;
import com.lexoravisauls.client.badge.LexoraModUsers;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LexoraIrcScreen extends Screen {

    private static final MsdfFont SFUI = new MsdfFont(
            Identifier.of("lexoravisauls", "msdf_data/font.png"),
            Identifier.of("lexoravisauls", "msdf_data/font.json")
    );

    private final int windowWidth = 470;
    private final int windowHeight = 280;

    private TextFieldWidget inputField;
    private float scrollY = 0;
    private float userScrollY = 0;
    private float animProgress = 0f;
    private boolean isClosing = false;

    public LexoraIrcScreen() {
        super(Text.literal("Lexora IRC"));
    }

    @Override
    protected void init() {
        int x = (this.width - windowWidth) / 2;
        int y = (this.height - windowHeight) / 2;

        inputField = new TextFieldWidget(this.textRenderer, x + 140, y + windowHeight - 38, 275, 28, Text.empty());
        inputField.setMaxLength(200);
        this.addDrawableChild(inputField);
        this.setInitialFocus(inputField);

        LexoraIrcClient.UNREAD_COUNTS.put(LexoraIrcClient.currentChannel, 0);

        animProgress = 0f;
        isClosing = false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void close() {
        if (!isClosing) {
            isClosing = true;
        }
    }

    private List<String> wrapText(String text, float maxWidth, float fontSize) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (SFUI.getWidth(testLine, fontSize) <= maxWidth) {
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isClosing) {
            animProgress -= delta * 0.20f;
            if (animProgress <= 0.02f) {
                super.close();
                return;
            }
        } else {
            animProgress += (1f - animProgress) * 0.15f * delta;
        }

        context.getMatrices().push();
        context.getMatrices().translate(this.width / 2f, this.height / 2f, 0);
        context.getMatrices().scale(animProgress, animProgress, 1f);
        context.getMatrices().translate(-this.width / 2f, -this.height / 2f, 0);

        int x = (this.width - windowWidth) / 2;
        int y = (this.height - windowHeight) / 2;

        // --- Главный фон ---
        RoundedRectShader.draw(context, x, y, windowWidth, windowHeight, 12f, 0xF2111115);

        // --- Левая панель ---
        int leftW = 125;
        RoundedRectShader.draw(context, x, y, leftW, windowHeight, 12f, 0x40000000);

        boolean isGlobal = LexoraIrcClient.currentChannel.equals("Global");
        RoundedRectShader.draw(context, x + 5, y + 5, leftW - 10, 22, 6f, isGlobal ? 0x60A855F7 : 0x20FFFFFF);

        // 🔥 ИСПРАВЛЕНО: Векторная иконка глобуса вместо уродливого эмодзи "🌐"
        int globalTextColor = isGlobal ? 0xFFFFFFFF : 0xFFAAAAAA;
        LexoraIcons.draw(context, LexoraIcons.Icon.GLOBE, x + 10, y + 10.5f, 10f, globalTextColor);
        SFUI.draw(context, "Общий чат", x + 24, y + 12, 8.5f, globalTextColor); // Фикс мыла!

        int globalUnread = LexoraIrcClient.UNREAD_COUNTS.getOrDefault("Global", 0);
        if (globalUnread > 0) {
            RoundedRectShader.draw(context, x + leftW - 22, y + 9, 14, 14, 7f, 0xFFEF4444);
            SFUI.draw(context, String.valueOf(globalUnread), x + leftW - 18, y + 13, 7f, 0xFFFFFFFF); // Фикс мыла!
        }

        RoundedRectShader.draw(context, x + 10, y + 32, leftW - 20, 1, 0f, 0x33FFFFFF);

        List<String> sortedUsers = new ArrayList<>(LexoraModUsers.getOnlineNames());
        sortedUsers.sort((a, b) -> {
            int ua = LexoraIrcClient.UNREAD_COUNTS.getOrDefault(a, 0);
            int ub = LexoraIrcClient.UNREAD_COUNTS.getOrDefault(b, 0);
            if (ua != ub) return Integer.compare(ub, ua);
            return a.compareToIgnoreCase(b);
        });

        context.enableScissor(x, y + 35, x + leftW, y + windowHeight - 5);
        float userY = y + 40 + userScrollY;

        for (String userName : sortedUsers) {
            if (userY > y + windowHeight) break;
            if (userY > y + 20) {
                boolean isSelected = LexoraIrcClient.currentChannel.equals(userName);

                if (isSelected) {
                    RoundedRectShader.draw(context, x + 5, (int)userY - 3, leftW - 10, 18, 4f, 0x40A855F7);
                }

                UUID u = LexoraIrcClient.NAME_TO_UUID.getOrDefault(userName, Util.NIL_UUID);
                SkinTextures textures = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(new GameProfile(u, userName));
                net.minecraft.client.gui.PlayerSkinDrawer.draw(context, textures, x + 8, (int)userY - 1, 8);

                SFUI.draw(context, userName, x + 22, userY, 8f, isSelected ? 0xFFFFFFFF : 0xFFE9D5FF); // Фикс мыла!

                int unread = LexoraIrcClient.UNREAD_COUNTS.getOrDefault(userName, 0);
                if (unread > 0) {
                    RoundedRectShader.draw(context, x + leftW - 20, (int)userY - 1, 14, 14, 7f, 0xFFEF4444);
                    SFUI.draw(context, String.valueOf(unread), x + leftW - 16, userY + 3, 7f, 0xFFFFFFFF); // Фикс мыла!
                }
            }
            userY += 20;
        }
        context.disableScissor();

        // --- Чат панель ---
        int chatX = x + leftW + 10;
        int chatY = y + 30;
        int chatW = windowWidth - leftW - 20;
        int chatH = windowHeight - 75;

        String chatTitle = LexoraIrcClient.currentChannel.equals("Global") ? "Общий чат" : "ЛС: " + LexoraIrcClient.currentChannel;
        SFUI.draw(context, chatTitle, chatX, y + 12, 10f, 0xFFFFFFFF); // Фикс мыла!
        RoundedRectShader.draw(context, chatX, y + 25, chatW, 1, 0f, 0x33FFFFFF);

        context.enableScissor(chatX, chatY, chatX + chatW, chatY + chatH);
        float currentMessageY = chatY + chatH - 15 + scrollY;

        List<LexoraIrcClient.IrcMessage> msgs = LexoraIrcClient.getMessages(LexoraIrcClient.currentChannel);

        for (int i = msgs.size() - 1; i >= 0; i--) {
            LexoraIrcClient.IrcMessage msg = msgs.get(i);
            if (currentMessageY < chatY - 100) break;

            float prefixW = SFUI.getWidth(msg.name + ": ", 8.5f);
            List<String> lines = wrapText(msg.text, chatW - prefixW - 10, 8.5f);

            for (int j = lines.size() - 1; j >= 0; j--) {
                if (currentMessageY <= chatY + chatH && currentMessageY >= chatY - 10) {
                    int nameColor = msg.isSelf ? 0xFFC084FC : 0xFFE9D5FF;
                    int textColor = msg.isSelf ? 0xFFD8B4FE : 0xFFAAAAAA;

                    if (j == 0) {
                        SFUI.draw(context, msg.name + ":", chatX, currentMessageY, 8.5f, nameColor); // Фикс мыла!
                        SFUI.draw(context, lines.get(j), chatX + prefixW, currentMessageY, 8.5f, textColor); // Фикс мыла!
                    } else {
                        SFUI.draw(context, lines.get(j), chatX + prefixW, currentMessageY, 8.5f, textColor); // Фикс мыла!
                    }
                }
                currentMessageY -= 12;
            }
            currentMessageY -= 6;
        }
        context.disableScissor();

        // --- Поле ввода ---
        int inputY = y + windowHeight - 38;
        RoundedRectShader.draw(context, chatX, inputY, chatW, 28, 8f, 0x60000000);

        String typedText = inputField.getText();
        boolean showCursor = inputField.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0;
        String displayText = typedText + (showCursor ? "_" : "");

        float maxInputW = chatW - 45; // Оставляем место для кнопки
        float textW = SFUI.getWidth(displayText, 8.5f);
        float textX = chatX + 6;

        // Авто-скролл текста влево, если он слишком длинный
        if (textW > maxInputW) {
            textX -= (textW - maxInputW);
        }

        context.enableScissor(chatX + 5, inputY, chatX + chatW - 40, inputY + 28);
        SFUI.draw(context, displayText, textX, inputY + 10, 8.5f, 0xFFFFFFFF); // Фикс мыла!
        context.disableScissor();

        boolean hoveredSend = mouseX >= chatX + chatW - 35 && mouseX <= chatX + chatW - 5 && mouseY >= inputY + 4 && mouseY <= inputY + 24;
        RoundedRectShader.draw(context, chatX + chatW - 35, inputY + 4, 30, 20, 6f, hoveredSend ? 0xFFA855F7 : 0xFF6B21A8);

        // 🔥 ИСПРАВЛЕНО: Векторная иконка отправки (PLAY) вместо ">"
        LexoraIcons.draw(context, LexoraIcons.Icon.PLAY, chatX + chatW - 24, inputY + 9, 10f, 0xFFFFFFFF);

        context.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - windowWidth) / 2;
        int y = (this.height - windowHeight) / 2;
        int leftW = 125;
        int chatX = x + leftW + 10;
        int chatY = y + 30;
        int chatH = windowHeight - 75;
        int chatW = windowWidth - leftW - 20;

        // Клики по левой панели
        if (mouseX >= x && mouseX <= x + leftW) {
            if (mouseY >= y + 5 && mouseY <= y + 27) {
                LexoraIrcClient.currentChannel = "Global";
                LexoraIrcClient.UNREAD_COUNTS.put("Global", 0);
                scrollY = 0;
                return true;
            }

            float userY = y + 40 + userScrollY;
            List<String> sortedUsers = new ArrayList<>(LexoraModUsers.getOnlineNames());
            sortedUsers.sort((a, b) -> {
                int ua = LexoraIrcClient.UNREAD_COUNTS.getOrDefault(a, 0);
                int ub = LexoraIrcClient.UNREAD_COUNTS.getOrDefault(b, 0);
                if (ua != ub) return Integer.compare(ub, ua);
                return a.compareToIgnoreCase(b);
            });

            for (String userName : sortedUsers) {
                if (mouseY >= userY - 3 && mouseY <= userY + 15) {
                    LexoraIrcClient.currentChannel = userName;
                    LexoraIrcClient.UNREAD_COUNTS.put(userName, 0);
                    scrollY = 0;
                    return true;
                }
                userY += 20;
            }
        }

        // ОДИН КЛИК для копирования ника в поле ввода
        if (button == 0 && mouseX >= chatX && mouseX <= chatX + chatW && mouseY >= chatY && mouseY <= chatY + chatH) {
            float currentMessageY = chatY + chatH - 15 + scrollY;
            List<LexoraIrcClient.IrcMessage> msgs = LexoraIrcClient.getMessages(LexoraIrcClient.currentChannel);

            for (int i = msgs.size() - 1; i >= 0; i--) {
                LexoraIrcClient.IrcMessage msg = msgs.get(i);
                float prefixW = SFUI.getWidth(msg.name + ": ", 8.5f);
                List<String> lines = wrapText(msg.text, chatW - prefixW - 10, 8.5f);

                float msgBottomY = currentMessageY + 12;
                float msgTopY = currentMessageY - ((lines.size() - 1) * 12);

                if (mouseY >= msgTopY && mouseY <= msgBottomY) {
                    // Вставляем ник и возвращаем фокус на поле ввода
                    inputField.setText("@" + msg.name + " ");
                    inputField.setFocused(true);
                    return true;
                }

                currentMessageY -= (lines.size() * 12) + 6;
            }
        }

        // Клик по кнопке ">"
        if (button == 0 && mouseX >= chatX + chatW - 35 && mouseX <= chatX + chatW - 5 && mouseY >= y + windowHeight - 38 + 4 && mouseY <= y + windowHeight - 38 + 24) {
            sendMessage();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            sendMessage();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = (this.width - windowWidth) / 2;
        int leftW = 125;

        if (mouseX >= x && mouseX <= x + leftW) {
            userScrollY += (float) (verticalAmount * 20f);
            if (userScrollY > 0) userScrollY = 0;
        } else {
            scrollY += (float) (verticalAmount * 20f);
            if (scrollY < 0) scrollY = 0;
        }
        return true;
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && LexoraIrcClient.sendFromGui(text)) {
            inputField.setText("");
            scrollY = 0;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}