package com.lexoravisauls.client.gui.main_menu;

import com.google.common.collect.ImmutableList;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.mixin.SessionAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class LexoraMainMenu extends Screen {

    public static final Identifier FONT_HELLO = Identifier.of("lexoravisauls", "sfui_menu_hello");
    public static final Identifier FONT_TIME  = Identifier.of("lexoravisauls", "sfui_menu_time");
    public static final Identifier FONT_DATE  = Identifier.of("lexoravisauls", "sfui_menu_date");
    public static final Identifier FONT_HINT  = Identifier.of("lexoravisauls", "sfui_menu_hint");
    public static final Identifier FONT_SMALL = Identifier.of("lexoravisauls", "sfui_menu_small");

    public static final Identifier ADD_TEX = Identifier.of("lexoravisauls", "textures/gui/add.png");
    public static final Identifier BACKGROUND_TEX = Identifier.of("lexoravisauls", "textures/gui/background.png");

    public static final Identifier STEVE_SKIN = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    public static final Identifier ALEX_SKIN = Identifier.of("minecraft", "textures/entity/player/slim/alex.png");
    public static final List<Identifier> ALT_SKINS = ImmutableList.of(STEVE_SKIN, ALEX_SKIN);
    private static final Map<String, Identifier> altSkins = new HashMap<>();

    private boolean inAltManager = false;
    public static final List<String> savedAlts = new ArrayList<>();
    private static boolean altsLoaded = false;

    private TextFieldWidget usernameField;
    private String selectedAlt = "";
    private int scrollY = 0;
    private long lastClickTime = 0;

    private enum IntroStage {
        BLACK_HOLD,
        HELLO,
        WAIT_CONTINUE,
        TRANSITION_TO_MENU,
        MENU_READY
    }

    private static boolean introPlayedOnceThisSession = false;

    private IntroStage introStage = IntroStage.BLACK_HOLD;
    private long introStageStart = 0L;
    private boolean introStartedFromRender = false;

    private float helloAlpha = 0.0f;
    private float blackOverlayAlpha = 1.0f;
    private float centerClockAlpha = 0.0f;
    private float continueTextAlpha = 0.0f;
    private float topClockProgress = 0.0f;
    private float menuButtonsProgress = 0.0f;

    private final List<LexoraButton> menuButtons = new ArrayList<>();

    public LexoraMainMenu() {
        super(Text.literal("Lexora Main Menu"));

        if (!altsLoaded) {
            loadAlts();
            altsLoaded = true;
        }

        if (savedAlts.isEmpty() && MinecraftClient.getInstance().getSession() != null) {
            savedAlts.add(MinecraftClient.getInstance().getSession().getUsername());
            saveAlts();
        }
    }

    private static Text txtHello(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(FONT_HELLO));
    }

    private static Text txtTime(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(FONT_TIME));
    }

    private static Text txtDate(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(FONT_DATE));
    }

    private static Text txtHint(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(FONT_HINT));
    }

    private static Text txtSmall(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(FONT_SMALL));
    }

    private static void loadAlts() {
        try {
            Path file = MinecraftClient.getInstance().runDirectory.toPath().resolve("lexora_alts.txt");
            if (Files.exists(file)) {
                savedAlts.clear();
                savedAlts.addAll(Files.readAllLines(file));
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки Альтов: " + e.getMessage());
        }
    }

    private static void saveAlts() {
        try {
            Path file = MinecraftClient.getInstance().runDirectory.toPath().resolve("lexora_alts.txt");
            Files.write(file, savedAlts, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.err.println("Ошибка сохранения Альтов: " + e.getMessage());
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.menuButtons.clear();

        if (inAltManager) {
            initAltManager();
            introStage = IntroStage.MENU_READY;
            introStartedFromRender = true;
            helloAlpha = 0.0f;
            blackOverlayAlpha = 0.0f;
            centerClockAlpha = 1.0f;
            continueTextAlpha = 0.0f;
            topClockProgress = 1.0f;
            menuButtonsProgress = 1.0f;
            return;
        }

        initMainMenu();

        if (introPlayedOnceThisSession) {
            introStage = IntroStage.MENU_READY;
            introStartedFromRender = true;
            helloAlpha = 0.0f;
            blackOverlayAlpha = 0.0f;
            centerClockAlpha = 1.0f;
            continueTextAlpha = 0.0f;
            topClockProgress = 1.0f;
            menuButtonsProgress = 1.0f;
            setMenuButtonsVisible(true, true, 1.0f);
        } else {
            introStage = IntroStage.BLACK_HOLD;
            introStartedFromRender = false;
            introStageStart = 0L;

            helloAlpha = 0.0f;
            blackOverlayAlpha = 1.0f;
            centerClockAlpha = 0.0f;
            continueTextAlpha = 0.0f;
            topClockProgress = 0.0f;
            menuButtonsProgress = 0.0f;
            setMenuButtonsVisible(false, false, 0.0f);
        }
    }

    private void initMainMenu() {
        int btnW = 140;
        int btnH = 22;
        int centerX = this.width / 2 - btnW / 2;
        int startY = this.height / 2 + 42;

        menuButtons.add(new LexoraButton(centerX, startY, btnW, btnH, "Одиночная игра",
                () -> this.client.setScreen(new SelectWorldScreen(this))));
        menuButtons.add(new LexoraButton(centerX, startY + 28, btnW, btnH, "Сетевая игра",
                () -> this.client.setScreen(new MultiplayerScreen(this))));
        menuButtons.add(new LexoraButton(centerX, startY + 56, btnW, btnH, "Настройки",
                () -> this.client.setScreen(new OptionsScreen(this, this.client.options))));

        int halfW = (btnW - 6) / 2;
        menuButtons.add(new LexoraButton(centerX, startY + 84, halfW, btnH, "Альты", () -> {
            this.inAltManager = true;
            this.init();
        }));
        menuButtons.add(new LexoraButton(centerX + halfW + 6, startY + 84, halfW, btnH, "Выйти",
                () -> this.client.scheduleStop()));

        for (LexoraButton b : menuButtons) {
            b.visible = false;
            b.active = false;
            b.setRevealAlpha(0.0f);
            this.addDrawableChild(b);
        }
    }

    private void initAltManager() {
        int panelW = 540;
        int panelH = 260;
        int startX = (this.width - panelW) / 2;
        int startY = (this.height - panelH) / 2;
        int leftBoxX = startX + 10;
        int leftBoxY = startY + 35;
        int leftBoxW = 160;

        this.usernameField = new TextFieldWidget(this.textRenderer, leftBoxX + 16, leftBoxY + 28, leftBoxW - 32, 16, Text.literal(""));
        this.usernameField.setMaxLength(16);
        this.usernameField.setDrawsBackground(false);
        this.usernameField.setEditableColor(0xFFFFFFFF);
        this.addDrawableChild(this.usernameField);

        int btnH = 22;

        this.addDrawableChild(new LexoraButton(leftBoxX + 10, leftBoxY + 64, leftBoxW - 20, btnH, "Войти", () -> {
            String name = this.usernameField.getText().trim();
            if (!name.isEmpty()) login(name);
        }));

        this.addDrawableChild(new LexoraButton(leftBoxX + 10, leftBoxY + 90, leftBoxW - 20, btnH, "Случайный", () -> {
            String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
            StringBuilder sb = new StringBuilder();
            Random rnd = new Random();
            for (int i = 0; i < 6 + rnd.nextInt(6); i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            String randomName = sb.toString();
            this.usernameField.setText(randomName);
            login(randomName);
        }));

        this.addDrawableChild(new LexoraButton(leftBoxX + 10, leftBoxY + 116, leftBoxW - 20, btnH, "Удалить", () -> {
            if (!selectedAlt.isEmpty()) {
                savedAlts.remove(selectedAlt);
                selectedAlt = "";
                saveAlts();
            }
        }));

        this.addDrawableChild(new LexoraButton(leftBoxX + 10, startY + panelH - btnH - 10, leftBoxW - 20, btnH, "Назад", () -> {
            this.inAltManager = false;
            this.init();
        }));
    }

    private void setMenuButtonsVisible(boolean visible, boolean active, float alpha) {
        for (LexoraButton b : menuButtons) {
            b.visible = visible;
            b.active = active;
            b.setRevealAlpha(alpha);
        }
    }

    private void login(String name) {
        String trimName = name.trim();
        if (trimName.isEmpty()) return;

        if (this.client != null && this.client.getSession() != null) {
            ((SessionAccessor) this.client.getSession()).setUsername(trimName);
        }

        if (!savedAlts.contains(trimName)) {
            savedAlts.add(trimName);
            saveAlts();
        }
    }

    private static Identifier getAltSkin(String altName) {
        if (!altSkins.containsKey(altName)) {
            Random random = new Random(altName.hashCode());
            altSkins.put(altName, ALT_SKINS.get(random.nextInt(ALT_SKINS.size())));
        }
        return altSkins.get(altName);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (inAltManager) {
            this.inAltManager = false;
            this.init();
            return false;
        }
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private void updateIntro() {
        if (inAltManager) return;

        if (!introStartedFromRender) {
            introStartedFromRender = true;
            introStageStart = System.currentTimeMillis();
            introStage = introPlayedOnceThisSession ? IntroStage.MENU_READY : IntroStage.BLACK_HOLD;
        }

        if (introPlayedOnceThisSession && introStage == IntroStage.MENU_READY) {
            topClockProgress = 1.0f;
            menuButtonsProgress = 1.0f;
            centerClockAlpha = 1.0f;
            blackOverlayAlpha = 0.0f;
            helloAlpha = 0.0f;
            continueTextAlpha = 0.0f;
            setMenuButtonsVisible(true, true, 1.0f);
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - introStageStart;

        switch (introStage) {
            case BLACK_HOLD -> {
                helloAlpha = 0.0f;
                blackOverlayAlpha = 1.0f;
                centerClockAlpha = 0.0f;
                continueTextAlpha = 0.0f;

                if (elapsed >= 450L) {
                    introStage = IntroStage.HELLO;
                    introStageStart = now;
                }
            }

            case HELLO -> {
                if (elapsed < 1300L) {
                    helloAlpha = 1.0f;
                    blackOverlayAlpha = 1.0f;
                    centerClockAlpha = 0.0f;
                    continueTextAlpha = 0.0f;
                } else if (elapsed < 2200L) {
                    float p = (elapsed - 1300L) / 900.0f;
                    helloAlpha = 1.0f - p;
                    blackOverlayAlpha = 1.0f;
                    centerClockAlpha = 0.0f;
                    continueTextAlpha = 0.0f;
                } else if (elapsed < 3600L) {
                    float p = (elapsed - 2200L) / 1400.0f;
                    helloAlpha = 0.0f;
                    blackOverlayAlpha = 1.0f - p;
                    centerClockAlpha = easeOutCubic(p);
                    continueTextAlpha = easeOutCubic(p);
                } else {
                    helloAlpha = 0.0f;
                    blackOverlayAlpha = 0.0f;
                    centerClockAlpha = 1.0f;
                    continueTextAlpha = 1.0f;
                    introStage = IntroStage.WAIT_CONTINUE;
                    introStageStart = now;
                }
            }

            case WAIT_CONTINUE -> {
                centerClockAlpha = 1.0f;
                blackOverlayAlpha = 0.0f;
                continueTextAlpha = (float) (0.65f + 0.35f * Math.sin(now / 240.0));
            }

            case TRANSITION_TO_MENU -> {
                long transElapsed = now - introStageStart;
                float p = Math.min(1.0f, transElapsed / 1000.0f);

                topClockProgress = easeOutCubic(p);
                menuButtonsProgress = easeOutCubic(p);
                centerClockAlpha = 1.0f;
                continueTextAlpha = 1.0f - p;

                if (p >= 1.0f) {
                    introStage = IntroStage.MENU_READY;
                    introStageStart = now;
                    topClockProgress = 1.0f;
                    menuButtonsProgress = 1.0f;
                    setMenuButtonsVisible(true, true, 1.0f);
                    introPlayedOnceThisSession = true;
                }
            }

            case MENU_READY -> {
                topClockProgress = 1.0f;
                menuButtonsProgress = 1.0f;
                centerClockAlpha = 1.0f;
                blackOverlayAlpha = 0.0f;
                helloAlpha = 0.0f;
                continueTextAlpha = 0.0f;
                setMenuButtonsVisible(true, true, 1.0f);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateIntro();

        drawTexQuad(context, BACKGROUND_TEX, 0, 0, this.width, this.height, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
        context.fill(0, 0, this.width, this.height, 0x22000000);

        if (!inAltManager) {
            renderIntroOrMenu(context);
        } else {
            renderTopClock(context, 1.0f);
            renderAltManager(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderIntroOrMenu(DrawContext context) {
        if (blackOverlayAlpha > 0.01f) {
            int alpha = (int) (blackOverlayAlpha * 255.0f);
            context.fill(0, 0, this.width, this.height, (alpha << 24));
        }

        if ((introStage == IntroStage.HELLO || introStage == IntroStage.BLACK_HOLD) && helloAlpha > 0.01f) {
            renderHello(context);
        }

        if ((introStage == IntroStage.HELLO || introStage == IntroStage.WAIT_CONTINUE) && centerClockAlpha > 0.01f) {
            renderCenterClock(context, 0.0f);
        } else if (introStage == IntroStage.TRANSITION_TO_MENU) {
            renderCenterClock(context, topClockProgress);
            renderSlidingButtons();
        } else if (introStage == IntroStage.MENU_READY) {
            renderTopClock(context, 1.0f);
            renderSlidingButtons();
        }
    }

    private void renderHello(DrawContext context) {
        TextRenderer tr = this.client.textRenderer;
        Text hello = txtHello("Hello");

        int a = (int) (helloAlpha * 255.0f);
        int color = (a << 24) | 0xFFFFFF;

        int w = tr.getWidth(hello);
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 16;

        context.drawText(tr, hello, x, y, color, false);
    }

    private void renderCenterClock(DrawContext context, float moveToTopProgress) {
        TextRenderer tr = this.client.textRenderer;

        String dateStr = getCurrentDateString();
        String timeStr = getCurrentTimeString();

        int alpha = (int) (centerClockAlpha * 255.0f);
        int textColor = (alpha << 24) | 0xFFFFFFFF;

        float startY = this.height / 2f - 52.0f;
        float endY = 16.0f;
        float y = lerp(startY, endY, moveToTopProgress);

        Text dateText = txtDate(dateStr);
        int dateW = tr.getWidth(dateText);
        context.drawText(tr, dateText, this.width / 2 - dateW / 2, (int) y, textColor, false);

        Text timeText = txtTime(timeStr);
        int timeW = tr.getWidth(timeText);
        context.drawText(tr, timeText, this.width / 2 - timeW / 2, (int) (y + 30.0f), textColor, false);

        if (moveToTopProgress < 0.95f) {
            int continueAlpha = (int) (continueTextAlpha * 255.0f);
            int continueColor = (continueAlpha << 24) | 0xDDDDDD;

            Text pressText = txtHint("Нажмите, чтобы продолжить");
            int pressW = tr.getWidth(pressText);

            context.drawText(
                    tr,
                    pressText,
                    this.width / 2 - pressW / 2,
                    (int) (y + 76.0f),
                    continueColor,
                    false
            );
        }
    }

    private void renderTopClock(DrawContext context, float progress) {
        renderCenterClock(context, progress);
    }

    private void renderSlidingButtons() {
        int btnW = 140;
        int centerX = this.width / 2 - btnW / 2;
        int startY = this.height / 2 + 42;

        float p = menuButtonsProgress;
        int hiddenOffset = 70;

        for (int i = 0; i < menuButtons.size(); i++) {
            LexoraButton btn = menuButtons.get(i);

            int targetY = switch (i) {
                case 0 -> startY;
                case 1 -> startY + 28;
                case 2 -> startY + 56;
                case 3 -> startY + 84;
                case 4 -> startY + 84;
                default -> startY;
            };

            int targetX = switch (i) {
                case 3 -> centerX;
                case 4 -> centerX + (btnW - 6) / 2 + 6;
                default -> centerX;
            };

            int fromY = targetY + hiddenOffset;
            int currentY = (int) lerp(fromY, targetY, p);

            btn.setPosition(targetX, currentY);
            btn.visible = p > 0.01f;
            btn.active = introStage == IntroStage.MENU_READY;
            btn.setRevealAlpha(p);
        }
    }

    private void renderAltManager(DrawContext context, int mouseX, int mouseY) {
        TextRenderer tr = this.client.textRenderer;
        int panelW = 540;
        int panelH = 260;
        int startX = (this.width - panelW) / 2;
        int startY = (this.height - panelH) / 2;

        drawSmoothRect(context, startX, startY, panelW, panelH, 0xAA211536);
        context.drawText(tr, txtHint("Менеджер Аккаунтов"), startX + 10, startY + 10, 0xFFFFFFFF, false);

        int leftBoxX = startX + 10;
        int leftBoxY = startY + 35;
        int leftBoxW = 160;
        int leftBoxH = panelH - 45;

        drawSmoothRect(context, leftBoxX, leftBoxY, leftBoxW, leftBoxH, 0xAA2D1D4A);
        context.drawText(tr, txtHint("АККАУНТ"), leftBoxX + 10, leftBoxY + 10, 0xFFDDDDDD, false);

        int fieldBgColor = this.usernameField.isFocused() ? 0xAA452C70 : 0xAA31224D;
        drawSmoothRect(context, leftBoxX + 10, leftBoxY + 24, leftBoxW - 20, 22, fieldBgColor);

        if (this.usernameField.getText().isEmpty() && !this.usernameField.isFocused()) {
            context.drawText(tr, txtSmall("Введите ник..."), leftBoxX + 16, leftBoxY + 31, 0xFF888888, false);
        }

        String currentName = (this.client != null && this.client.getSession() != null)
                ? this.client.getSession().getUsername()
                : "Player";

        int activeBadgeY = startY + panelH - 65;
        drawSmoothRect(context, leftBoxX + 10, activeBadgeY, leftBoxW - 20, 20, 0x883B256B);

        Text badge = txtSmall("Активный: " + currentName);
        int badgeCenterX = leftBoxX + 10 + (leftBoxW - 20) / 2;
        context.drawText(tr, badge, badgeCenterX - tr.getWidth(badge) / 2, activeBadgeY + 6, 0xFFDDDDDD, false);

        int rightBoxX = leftBoxX + leftBoxW + 10;
        int rightBoxY = leftBoxY;
        int rightBoxW = panelW - leftBoxW - 30;
        int rightBoxH = leftBoxH;

        drawSmoothRect(context, rightBoxX, rightBoxY, rightBoxW, rightBoxH, 0xAA2D1D4A);
        context.drawText(tr, txtHint("АККАУНТЫ"), rightBoxX + 10, rightBoxY + 10, 0xFFDDDDDD, false);

        int listX = rightBoxX + 10;
        int listY = rightBoxY + 28;
        int listW = rightBoxW - 20;
        int listH = rightBoxH - 35;
        int itemH = 26;

        context.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < savedAlts.size(); i++) {
            int itemY = listY + (i * (itemH + 4)) - scrollY;
            if (itemY < listY || itemY + itemH > listY + listH) continue;

            String alt = savedAlts.get(i);
            boolean isHovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= itemY && mouseY <= itemY + itemH;
            boolean isSelected = alt.equals(selectedAlt);
            boolean isCrossHovered = mouseX >= listX + listW - 24 && mouseX <= listX + listW && mouseY >= itemY && mouseY <= itemY + itemH;

            int bgColor = isSelected ? 0xAA5B3A99 : (isHovered ? 0xAA452C70 : 0xAA3B256B);
            drawSmoothRect(context, listX, itemY, listW, itemH, bgColor);

            Identifier skin = getAltSkin(alt);
            drawTexQuad(context, skin, listX + 6, itemY + 5, 16, 16, 0.125F, 0.125F, 0.25F, 0.25F, 0xFFFFFFFF);

            int textColor = isSelected ? 0xFFFFFFFF : 0xFFDDDDDD;
            context.drawText(tr, txtSmall(alt), listX + 30, itemY + 9, textColor, false);

            int crossColor = isCrossHovered ? 0xFFFF5555 : 0xFFDDDDDD;
            drawRotatedTexQuad(context, ADD_TEX, listX + listW - 16, itemY + 9, 8, 8, 0.0F, 0.0F, 1.0F, 1.0F, crossColor, (float) (Math.PI / 4.0));
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inAltManager) {
            if (introStage == IntroStage.WAIT_CONTINUE && button == 0) {
                introStage = IntroStage.TRANSITION_TO_MENU;
                introStageStart = System.currentTimeMillis();
                return true;
            }

            if (introStage != IntroStage.MENU_READY) {
                return true;
            }
        }

        if (inAltManager && button == 0) {
            int panelW = 540;
            int panelH = 260;
            int startX = (this.width - panelW) / 2;
            int startY = (this.height - panelH) / 2;
            int listX = startX + 10 + 160 + 10 + 10;
            int listY = startY + 35 + 28;
            int listW = panelW - 160 - 30 - 20;
            int listH = panelH - 45 - 35;
            int itemH = 30;

            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
                int clickY = (int) (mouseY - listY + scrollY);
                int index = clickY / itemH;

                if (index >= 0 && index < savedAlts.size()) {
                    String clickedAlt = savedAlts.get(index);

                    if (mouseX >= listX + listW - 24) {
                        savedAlts.remove(index);
                        if (selectedAlt.equals(clickedAlt)) selectedAlt = "";
                        saveAlts();
                        return true;
                    }

                    long time = System.currentTimeMillis();
                    if (clickedAlt.equals(selectedAlt) && (time - lastClickTime < 300)) {
                        login(clickedAlt);
                        this.usernameField.setText(clickedAlt);
                    } else {
                        selectedAlt = clickedAlt;
                        this.usernameField.setText(clickedAlt);
                    }
                    lastClickTime = time;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inAltManager) {
            scrollY -= verticalAmount * 20;
            if (scrollY < 0) scrollY = 0;
            int maxScroll = Math.max(0, (savedAlts.size() * 30) - (260 - 45 - 35));
            if (scrollY > maxScroll) scrollY = maxScroll;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private String getCurrentTimeString() {
        LocalTime now = LocalTime.now();
        return String.format("%02d:%02d", now.getHour(), now.getMinute());
    }

    private String getCurrentDateString() {
        Locale ru = new Locale("ru");
        LocalDate now = LocalDate.now();

        String dayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, ru);
        String month = now.getMonth().getDisplayName(TextStyle.FULL, ru);

        return capitalize(dayOfWeek) + ", " + now.getDayOfMonth() + " " + month;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float easeOutCubic(float x) {
        return 1.0f - (float) Math.pow(1.0f - x, 3.0f);
    }

    public static int blendColors(int c1, int c2, float r) {
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        return ((int) (a1 + (a2 - a1) * r) << 24)
                | ((int) (r1 + (r2 - r1) * r) << 16)
                | ((int) (g1 + (g2 - g1) * r) << 8)
                | (int) (b1 + (b2 - b1) * r);
    }

    public static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        float radius = Math.min(6.0f, Math.min(width / 2.0f, height / 2.0f));
        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }

    public static void drawTexQuad(DrawContext context, Identifier texture, float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1, int color) {
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumerProvider.Immediate bufferSource = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = RenderLayer.getGuiTextured(texture);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(layer);

        vertexConsumer.vertex(matrix, x, y, 0.0F).color(r, g, b, a).texture(u0, v0);
        vertexConsumer.vertex(matrix, x, y + h, 0.0F).color(r, g, b, a).texture(u0, v1);
        vertexConsumer.vertex(matrix, x + w, y + h, 0.0F).color(r, g, b, a).texture(u1, v1);
        vertexConsumer.vertex(matrix, x + w, y, 0.0F).color(r, g, b, a).texture(u1, v0);

        bufferSource.draw(layer);
    }

    public static void drawRotatedTexQuad(DrawContext context, Identifier texture, float x, float y, float w, float h,
                                          float u0, float v0, float u1, float v1, int color, float angle) {
        context.getMatrices().push();
        context.getMatrices().translate(x + w / 2f, y + h / 2f, 0);
        context.getMatrices().multiply(new Quaternionf().rotateZ(angle));
        context.getMatrices().translate(-w / 2f, -h / 2f, 0);
        drawTexQuad(context, texture, 0, 0, w, h, u0, v0, u1, v1, color);
        context.getMatrices().pop();
    }

    public static class LexoraButton extends ClickableWidget {
        private final Runnable pressAction;
        private float hoverAnim = 0f;
        private float revealAlpha = 1.0f;

        public LexoraButton(int x, int y, int width, int height, String text, Runnable pressAction) {
            super(x, y, width, height, Text.literal(text));
            this.pressAction = pressAction;
        }

        public void setRevealAlpha(float revealAlpha) {
            this.revealAlpha = revealAlpha;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;

            float hoverTarget = this.isHovered() ? 1.0f : 0.0f;
            hoverAnim += (hoverTarget - hoverAnim) * 0.15f;

            int bgColor = blendColors(0xAA31224D, 0xAA452C70, hoverAnim);
            int textColor = blendColors(0xFFCCCCCC, 0xFFFFFFFF, hoverAnim);

            int bgA = (int) (((bgColor >> 24) & 0xFF) * revealAlpha);
            int txA = (int) (((textColor >> 24) & 0xFF) * revealAlpha);

            bgColor = (bgA << 24) | (bgColor & 0xFFFFFF);
            textColor = (txA << 24) | (textColor & 0xFFFFFF);

            drawSmoothRect(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(), bgColor);

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            Text t = txtHint(this.getMessage().getString());
            context.drawText(tr, t, getX() + (width - tr.getWidth(t)) / 2, getY() + (height - 8) / 2, textColor, false);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            super.onClick(mouseX, mouseY);
            if (this.isHovered() && this.pressAction != null) {
                this.pressAction.run();
            }
        }
    }
}