package com.lexoravisauls.client.gui.main_menu;

import com.google.common.collect.ImmutableList;
import com.lexoravisauls.client.auth.AuthManager;
import com.lexoravisauls.client.cosmetics.CosmeticsManager;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.mixin.SessionAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;

public class LexoraMainMenu extends Screen {

    // ─── MSDF Font ───────────────────────────────────────────────────────────
    private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    // ─── Размеры текста ───────────────────────────────────────────────────────
    public static final float SIZE_TIME  = 28.0f;
    public static final float SIZE_DATE  = 12.0f;
    public static final float SIZE_HINT  = 10.0f;
    public static final float SIZE_SMALL = 8.0f;

    // ─── Текстуры ─────────────────────────────────────────────────────────────
    public static final Identifier ADD_TEX        = Identifier.of("lexoravisauls", "textures/gui/add.png");
    public static final Identifier BACKGROUND_TEX = Identifier.of("lexoravisauls", "textures/gui/background.png");
    public static final Identifier HELLO_GIF_TEX  = Identifier.of("lexoravisauls", "textures/gui/hello.gif");

    // ─── Палитра ─────────────────────────────
    private static final int COL_PANEL_BG      = 0x990A0A0A;
    private static final int COL_PANEL_BG2     = 0x99111111;
    private static final int COL_DIVIDER       = 0xFF1A1A1A;
    private static final int COL_BTN_NORMAL    = 0xCC181818;
    private static final int COL_BTN_HOVER     = 0xCC2E2E2E;
    private static final int COL_TEXT_PRIMARY  = 0xFFE8E8E8;
    private static final int COL_TEXT_SECONDARY= 0xFF555555;
    private static final int COL_TEXT_MUTED    = 0xFF333333;
    private static final int COL_TEXT_WHITE    = 0xFFFFFFFF;
    private static final int COL_FIELD_BG      = 0xAA050505;
    private static final int COL_FIELD_FOCUSED = 0xAA1A1A1A;
    private static final int COL_LIST_ITEM     = 0x880E0E0E;
    private static final int COL_LIST_HOVER    = 0xAA1C1C1C;
    private static final int COL_LIST_SELECTED = 0xCC2A2A2A;

    // ─── Скины ────────────────────────────────────────────────────────────────
    public static final Identifier STEVE_SKIN = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    public static final Identifier ALEX_SKIN  = Identifier.of("minecraft", "textures/entity/player/slim/alex.png");
    public static final List<Identifier> ACCOUNT_SKINS = ImmutableList.of(STEVE_SKIN, ALEX_SKIN);
    private static final Map<String, Identifier> accountSkins = new HashMap<>();

    // ─── GIF ──────────────────────────────────────────────────────────────────
    private static final float HELLO_GIF_SCALE        = 0.18f;
    private static final long  BLACK_HOLD_MS           = 150L;
    private static final long  GIF_FADE_MS             = 350L;
    private static final long  BLACK_AFTER_GIF_FADE_MS = 450L;
    private static final int   PRELOAD_UPLOAD_PER_TICK  = 1;
    private static final int   MENU_UPLOAD_PER_TICK     = 2;

    private static final AnimatedGifTexture HELLO_GIF = new AnimatedGifTexture(HELLO_GIF_TEX);
    private static boolean gifPreloaderRegistered = false;

    // ─── Состояния авторизации на сайте ───────────────────────────────────────
    public static boolean isAuthenticatedSession = false;

    // ─── Автологин по HWID ──────────────────────────────────────────────────
    private static volatile boolean hwidCheckStarted = false;
    private static volatile boolean hwidCheckDone    = false;
    private static volatile boolean hwidCheckSuccess = false; // аккаунт найден, залогинили автоматически
    private static volatile boolean hwidConfirmedNew = false; // сервер точно подтвердил: hwid ни к чему не привязан

    private String  authInputText     = "";
    private String  authPasswordText  = "";
    private int     authFocusedField  = 0; // 0 = нет, 1 = логин, 2 = пароль
    private int     authCursorPos     = 0;
    private int     authPassCursorPos = 0;
    private boolean isAuthLoading     = false;

    private String  authStatusTarget   = "";
    private String  authStatusCurrent  = "";
    private int     authStatusColor    = 0xFFFFFFFF;
    private long    authStatusNextChar = 0L;
    private float   authScreenAlpha    = 0.0f;

    // ─── Менеджер аккаунтов ───────────────────────────────────────────────────
    private boolean inAccountManager = false;
    public static final List<String> savedAccounts = new ArrayList<>();
    private static boolean accountsLoaded = false;
    private static final Set<String> usedNames = new HashSet<>();
    private static final long globalSeed = UUID.randomUUID().getMostSignificantBits() ^ System.nanoTime();

    private String  inputText     = "";
    private boolean inputFocused  = false;
    private int     cursorPos     = 0;

    private String typewriterTarget = "";
    private int    typewriterPos    = 0;
    private long   typewriterNextMs = 0L;
    private static final long TYPEWRITER_CHAR_MS = 38L;

    private String selectedAccount = "";
    private float  scrollYAnim     = 0f;
    private int    scrollYTarget   = 0;
    private long   lastClickTime   = 0;

    // ─── Анимация панели аккаунтов ────────────────────────────────────────────
    private float   accountPanelAlpha    = 0f;
    private boolean accountPanelAnimIn   = false;
    private long    accountPanelAnimStart= 0L;
    private static final long ACCOUNT_PANEL_MS = 350L;

    private float menuButtonsAlpha     = 1f;
    private long  menuButtonsAnimStart = 0L;
    private boolean menuButtonsAnimIn  = true;
    private static final long MENU_BUTTONS_MS = 250L;

    // ─── Анимация выхода ──────────────────────────────────────────────────────
    private boolean exitAnimating = false;
    private long    exitStartMs   = 0L;
    private static final long EXIT_ANIM_MS = 500L;

    // ─── Intro stages ─────────────────────────────────────────────────────────
    private enum IntroStage {
        BLACK_HOLD, HELLO, WAIT_CONTINUE, AUTH_SCREEN, TRANSITION_TO_MENU, MENU_READY
    }

    private static boolean introPlayedOnceThisSession = false;

    private IntroStage introStage             = IntroStage.BLACK_HOLD;
    private long       introStageStart        = 0L;
    private boolean    introStartedFromRender = false;
    private boolean    helloGifTimerStarted   = false;

    private float helloAlpha        = 0.0f;
    private float blackOverlayAlpha = 1.0f;
    private float centerClockAlpha  = 0.0f;
    private float continueTextAlpha = 0.0f;
    private float topClockProgress  = 0.0f;
    private float menuButtonsProgress = 0.0f;

    // ─── Переход к другим экранам ─────────────────────────────────────────────
    private boolean  transitionOut      = false;
    private long     transitionOutStart = 0L;
    private static final long TRANSITION_OUT_MS = 300L;
    private Runnable transitionOutTarget = null;

    private final List<LexoraButton> menuButtons = new ArrayList<>();

    // ─── Кнопки менеджера ─────────────────────────────────────────────────────
    private final int[]    mgrBtnX     = new int[4];
    private final int[]    mgrBtnY     = new int[4];
    private final int[]    mgrBtnW     = new int[4];
    private final int[]    mgrBtnH     = new int[4];
    private final float[]  mgrBtnHover = new float[4];
    private final String[] mgrBtnLabel = {"Войти", "Случайный", "Удалить", "← Назад"};

    public LexoraMainMenu() {
        super(Text.literal("Lexora Main Menu"));
        if (!accountsLoaded) {
            loadAccounts();
            accountsLoaded = true;
        }
        if (savedAccounts.isEmpty() && MinecraftClient.getInstance().getSession() != null) {
            savedAccounts.add(MinecraftClient.getInstance().getSession().getUsername());
            saveAccounts();
        }
        HELLO_GIF.startPreload();
        startHwidAutoLogin();
    }

    private static MsdfFont getFont() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    public static void registerGifPreloader() {
        if (gifPreloaderRegistered) return;
        gifPreloaderRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HELLO_GIF.startPreload();
            HELLO_GIF.tickUpload(PRELOAD_UPLOAD_PER_TICK);
        });
    }

    private static void loadAccounts() {
        try {
            Path file = MinecraftClient.getInstance().runDirectory.toPath().resolve("lexora_accounts.txt");
            if (!Files.exists(file)) {
                Path legacy = MinecraftClient.getInstance().runDirectory.toPath().resolve("lexora_alts.txt");
                if (Files.exists(legacy)) file = legacy;
            }
            if (Files.exists(file)) { savedAccounts.clear(); savedAccounts.addAll(Files.readAllLines(file)); }
        } catch (Exception e) { System.err.println("Ошибка загрузки аккаунтов: " + e.getMessage()); }
    }

    private static void saveAccounts() {
        try {
            Path file = MinecraftClient.getInstance().runDirectory.toPath().resolve("lexora_accounts.txt");
            Files.write(file, savedAccounts, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) { System.err.println("Ошибка сохранения аккаунтов: " + e.getMessage()); }
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.menuButtons.clear();
        HELLO_GIF.startPreload();
        HELLO_GIF.tickUpload(MENU_UPLOAD_PER_TICK);

        if (inAccountManager) {
            accountPanelAlpha    = 1f;
            accountPanelAnimIn   = true;
            menuButtonsAlpha     = 0f;
            introStage = IntroStage.MENU_READY;
            introStartedFromRender = true;
            helloGifTimerStarted   = true;
            helloAlpha = 0f; blackOverlayAlpha = 0f;
            centerClockAlpha = 1f; continueTextAlpha = 0f;
            topClockProgress = 1f; menuButtonsProgress = 1f;
            authScreenAlpha = 0f;
            computeManagerLayout();
            return;
        }

        initMainMenu();

        if (introPlayedOnceThisSession) {
            introStage = IntroStage.MENU_READY;
            introStartedFromRender = true;
            helloGifTimerStarted   = true;
            helloAlpha = 0f; blackOverlayAlpha = 0f;
            centerClockAlpha = 1f; continueTextAlpha = 0f;
            topClockProgress = 1f; menuButtonsProgress = 1f;
            menuButtonsAlpha = 1f; authScreenAlpha = 0f;
            setMenuButtonsVisible(true, true, 1.0f);
        } else {
            introStage = IntroStage.BLACK_HOLD;
            introStartedFromRender = false;
            helloGifTimerStarted   = false;
            introStageStart = 0L;
            helloAlpha = 0f; blackOverlayAlpha = 1f;
            centerClockAlpha = 0f; continueTextAlpha = 0f;
            topClockProgress = 0f; menuButtonsProgress = 0f;
            authScreenAlpha = 0f;
            // ФИКС АНИМАЦИИ: Держим Alpha на 1, прогресс анимации сам плавно выведет кнопки!
            menuButtonsAlpha = 1f;
            setMenuButtonsVisible(true, false, 0.0f);
            HELLO_GIF.reset();
        }
    }

    private void initMainMenu() {
        int btnW    = 152;
        int btnH    = 26;
        int centerX = this.width / 2 - btnW / 2;
        int startY  = this.height / 2 + 40;
        int gap     = 34;

        menuButtons.add(new LexoraButton(centerX, startY, btnW, btnH, "Одиночная игра",
                () -> navigateTo(() -> this.client.setScreen(new SelectWorldScreen(this)))));
        menuButtons.add(new LexoraButton(centerX, startY + gap, btnW, btnH, "Сетевая игра",
                () -> navigateTo(() -> this.client.setScreen(new MultiplayerScreen(this)))));
        menuButtons.add(new LexoraButton(centerX, startY + gap * 2, btnW, btnH, "Настройки",
                () -> navigateTo(() -> this.client.setScreen(new OptionsScreen(this, this.client.options)))));

        int halfW = (btnW - 6) / 2;
        menuButtons.add(new LexoraButton(centerX, startY + gap * 3, halfW, btnH, "Аккаунты",
                this::openAccountManager));
        menuButtons.add(new LexoraButton(centerX + halfW + 6, startY + gap * 3, halfW, btnH, "Выйти",
                this::triggerExitAnimation));

        for (LexoraButton b : menuButtons) {
            b.visible = false; b.active = false; b.setRevealAlpha(0f);
            this.addDrawableChild(b);
        }
    }

    private void computeManagerLayout() {
        int panelW = 580, panelH = 300;
        int startX = (this.width - panelW) / 2;
        int startY = (this.height - panelH) / 2;
        int leftW  = 180;
        int lx     = startX + 14;
        int ly     = startY + 42;
        int btnW   = leftW - 20;
        int btnH   = 23;

        for (int i = 0; i < 3; i++) {
            mgrBtnX[i] = lx + 10;
            mgrBtnY[i] = ly + 58 + i * 29;
            mgrBtnW[i] = btnW;
            mgrBtnH[i] = btnH;
        }
        mgrBtnX[3] = lx + 10;
        mgrBtnY[3] = startY + panelH - btnH - 14;
        mgrBtnW[3] = btnW;
        mgrBtnH[3] = btnH;
    }

    private void openAccountManager() {
        inAccountManager     = true;
        accountPanelAnimIn   = true;
        accountPanelAlpha    = 0f;
        accountPanelAnimStart = System.currentTimeMillis();

        menuButtonsAnimIn    = false;
        menuButtonsAnimStart = System.currentTimeMillis();
        menuButtonsAlpha     = 1f;
        computeManagerLayout();
    }

    private void closeAccountManager() {
        accountPanelAnimIn    = false;
        accountPanelAnimStart = System.currentTimeMillis();

        menuButtonsAnimIn    = true;
        menuButtonsAnimStart = System.currentTimeMillis() + 150L;
        menuButtonsAlpha     = 0f;
    }

    private void finishCloseAccountManager() {
        inAccountManager  = false;
        accountPanelAlpha = 0f;
        for (LexoraButton b : menuButtons) {
            b.visible = true;
            b.active  = false;
        }
    }

    private void navigateTo(Runnable target) {
        if (transitionOut) return;
        transitionOut       = true;
        transitionOutStart  = System.currentTimeMillis();
        transitionOutTarget = target;
    }

    private void triggerExitAnimation() {
        if (exitAnimating) return;
        exitAnimating = true;
        exitStartMs   = System.currentTimeMillis();
    }

    /**
     * Отправляет на сайт текущий игровой ник для привязки косметики.
     * Вызывай этот метод каждый раз после смены ника в Альт Менеджере!
     */
    public static void syncCurrentIgn(String hwid, String newIgn) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.net.URL url = new java.net.URL("https://lexoravisuals.fun/update_ign.php");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                conn.setConnectTimeout(5000);
                conn.setDoOutput(true);

                // Отправляем HWID и текущий игровой ник
                String data = "hwid=" + hwid + "&ign=" + newIgn;
                conn.getOutputStream().write(data.getBytes());

                int code = conn.getResponseCode();
                if (code == 200) {
                    System.out.println("[Lexora Backend] Игровой ник успешно синхронизирован: " + newIgn);

                    // 1. Очищаем локальный кэш (удаляем крылья со старого ника)
                    com.lexoravisauls.client.cosmetics.CosmeticsManager.clearCache();

                    // 2. ИСПРАВЛЕНИЕ: Сразу же запрашиваем косметику для НОВОГО ника!
                    com.lexoravisauls.client.cosmetics.CosmeticsManager.fetchCosmetics(newIgn);
                }
            } catch (Exception e) {
                System.out.println("[Lexora Backend] Ошибка синхронизации ника: " + e.getMessage());
            }
        });
    }

    private void setMenuButtonsVisible(boolean visible, boolean active, float alpha) {
        for (LexoraButton b : menuButtons) {
            b.visible = visible;
            b.active  = active;
            b.setRevealAlpha(alpha);
        }
    }

    private void loginAccount(String name) {
        String n = name.trim();
        if (n.isEmpty()) return;

        if (this.client != null && this.client.getSession() != null)
            ((com.lexoravisauls.client.mixin.SessionAccessor) this.client.getSession()).setUsername(n);

        if (!savedAccounts.contains(n)) {
            savedAccounts.add(n);
            saveAccounts();
        }

        // Вызываем привязку!
        if (isAuthenticatedSession) {
            com.lexoravisauls.client.badge.LexoraAccount.syncCurrentIgn(n);
        }
        com.lexoravisauls.client.badge.LexoraAccount.syncCurrentIgn(n);
    }

    private static String generateUniqueFakeName() {
        Random random = new Random(System.nanoTime() ^ globalSeed);

        String[] nameStart = {
                "Kai", "Lex", "Val", "Max", "Zor", "Dex", "Tor", "Ren", "Leo", "Luc",
                "Rav", "Ash", "Ard", "Nik", "Sam", "Ben", "Kel", "Jax", "Nol", "Vin",
                "Tyr", "Sil", "Cai", "Dar", "Fen", "Gav", "Mal", "Neo"
        };
        String[] nameEnd = {
                "on", "ix", "us", "en", "or", "el", "ar", "an", "eo", "is", "eth", "il",
                "ad", "os", "an", "eus", "es", "ym", "ax", "ik"
        };
        String[] suffixes = {"x", "q", "zz", "ai", "xy", "zor", "ar", "yn"};

        int tries = 0;
        String finalName;

        do {
            StringBuilder name = new StringBuilder();
            name.append(nameStart[random.nextInt(nameStart.length)]);
            name.append(nameEnd[random.nextInt(nameEnd.length)]);

            if (random.nextFloat() < 0.25f) {
                name.append(suffixes[random.nextInt(suffixes.length)]);
            }
            if (random.nextFloat() < 0.15f && name.length() >= 4) {
                int mid = 1 + random.nextInt(name.length() - 2);
                name.setCharAt(mid, Character.toUpperCase(name.charAt(mid)));
            }
            if (random.nextFloat() < 0.2f) {
                name.append("_");
            }

            long uniquePart = (System.currentTimeMillis() + tries * 1234567L + globalSeed) & 0xFFFFF;
            name.append(uniquePart % 10000);

            finalName = name.toString();
            tries++;
        } while (usedNames.contains(finalName) && tries < 20);

        usedNames.add(finalName);
        return finalName;
    }

    private static Identifier getAccountSkin(String name) {
        return accountSkins.computeIfAbsent(name, k -> {
            Random r = new Random(k.hashCode());
            return ACCOUNT_SKINS.get(r.nextInt(ACCOUNT_SKINS.size()));
        });
    }

    private void startTypewriter(String target) {
        typewriterTarget = target;
        typewriterPos    = 0;
        typewriterNextMs = System.currentTimeMillis();
        inputText        = "";
        cursorPos        = 0;
    }

    private void setAuthStatus(String text, int color) {
        authStatusTarget = text;
        authStatusCurrent = "";
        authStatusColor = color;
        authStatusNextChar = System.currentTimeMillis();
    }

    private void tickTypewriter() {
        // Менеджер аккаунтов (ин-гейм)
        if (typewriterPos < typewriterTarget.length()) {
            long now = System.currentTimeMillis();
            while (typewriterPos < typewriterTarget.length() && now >= typewriterNextMs) {
                typewriterPos++;
                inputText        = typewriterTarget.substring(0, typewriterPos);
                cursorPos        = inputText.length();
                typewriterNextMs += TYPEWRITER_CHAR_MS;
            }
        }

        // Экран авторизации (перед меню)
        if (authStatusCurrent.length() < authStatusTarget.length()) {
            long now = System.currentTimeMillis();
            while (authStatusCurrent.length() < authStatusTarget.length() && now >= authStatusNextChar) {
                authStatusCurrent = authStatusTarget.substring(0, authStatusCurrent.length() + 1);
                authStatusNextChar += 25L;
            }
        }
    }

    private void tickScrollAnim() {
        float diff = scrollYTarget - scrollYAnim;
        if (Math.abs(diff) < 0.3f) { scrollYAnim = scrollYTarget; return; }
        scrollYAnim += diff * 0.25f;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (inAccountManager) { closeAccountManager(); return false; }
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    private static void startHwidAutoLogin() {
        if (hwidCheckStarted) return;
        hwidCheckStarted = true;

        new Thread(() -> {
            try {
                AuthManager.HwidResult res = AuthManager.checkHwid();

                if (res.success && res.found) {
                    isAuthenticatedSession = true;

                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null && mc.getSession() != null) {
                        ((SessionAccessor) mc.getSession()).setUsername(res.username);
                    }
                    if (!savedAccounts.contains(res.username)) {
                        savedAccounts.add(res.username);
                        saveAccounts();
                    }

                    com.lexoravisauls.client.badge.LexoraAccount.syncCurrentIgn(res.username);
                    com.lexoravisauls.client.emotion.EmotionManager.fetchRadialSlots();

                    hwidCheckSuccess = true;
                } else if (res.success) {
                    // сервер ответил и явно сказал: этот HWID ни к одному аккаунту не привязан
                    hwidConfirmedNew = true;
                }
                // res.success == false -> не было связи с сервером, не считаем это "новым ПК",
                // чтобы не отобрать кнопку "Пропустить" из-за обрыва сети
            } catch (Exception e) {
                System.out.println("[Lexora Backend] Ошибка автологина по HWID: " + e.getMessage());
            } finally {
                hwidCheckDone = true;
            }
        }, "Lexora-HWID-Autologin").start();
    }

    private void advanceFromHello(long now) {
        if (hwidCheckDone && hwidCheckSuccess) {
            introStage = IntroStage.TRANSITION_TO_MENU;
        } else {
            introStage = IntroStage.WAIT_CONTINUE;
        }
        introStageStart = now;
    }

    private void updateIntro() {
        HELLO_GIF.tickUpload(MENU_UPLOAD_PER_TICK);
        if (inAccountManager) return;

        if (!introStartedFromRender) {
            introStartedFromRender = true;
            introStageStart = System.currentTimeMillis();
            introStage = introPlayedOnceThisSession ? IntroStage.MENU_READY : IntroStage.BLACK_HOLD;
        }

        if (introPlayedOnceThisSession && introStage == IntroStage.MENU_READY) {
            topClockProgress = 1f; menuButtonsProgress = 1f; centerClockAlpha = 1f;
            continueTextAlpha = 0f; blackOverlayAlpha = 0f; helloAlpha = 0f; authScreenAlpha = 0f;
            return;
        }

        long now = System.currentTimeMillis(), elapsed = now - introStageStart;

        switch (introStage) {
            case BLACK_HOLD -> {
                helloAlpha = 0f; blackOverlayAlpha = 1f;
                centerClockAlpha = 0f; continueTextAlpha = 0f;
                topClockProgress = 0f; menuButtonsProgress = 0f; authScreenAlpha = 0f;
                if (elapsed >= BLACK_HOLD_MS) {
                    introStage = IntroStage.HELLO;
                    introStageStart = now;
                    helloGifTimerStarted = false;
                    HELLO_GIF.reset();
                }
            }
            case HELLO -> {
                HELLO_GIF.tickUpload(MENU_UPLOAD_PER_TICK);
                boolean gifReady  = HELLO_GIF.isReady();
                boolean gifFailed = HELLO_GIF.isFailed();
                if (!gifReady && !gifFailed) {
                    helloAlpha = 0f; blackOverlayAlpha = 1f;
                    return;
                }
                if (gifFailed) {
                    advanceFromHello(now);
                    return;
                }
                if (!helloGifTimerStarted) {
                    helloGifTimerStarted = true;
                    introStageStart = now;
                    HELLO_GIF.reset();
                    elapsed = 0L;
                }
                long gifDuration = HELLO_GIF.getTotalDurationMs();
                long gifFadeEnd  = gifDuration + GIF_FADE_MS;
                long blackEnd    = gifFadeEnd + BLACK_AFTER_GIF_FADE_MS;

                if (elapsed < gifDuration) {
                    helloAlpha = 1f; blackOverlayAlpha = 1f;
                } else if (elapsed < gifFadeEnd) {
                    float p = (elapsed - gifDuration) / (float) GIF_FADE_MS;
                    helloAlpha = 1f - p; blackOverlayAlpha = 1f;
                } else if (elapsed < blackEnd) {
                    float p = (elapsed - gifFadeEnd) / (float) BLACK_AFTER_GIF_FADE_MS;
                    helloAlpha = 0f; blackOverlayAlpha = 1f - p;
                } else {
                    advanceFromHello(now);
                }
            }
            case WAIT_CONTINUE -> {
                helloAlpha = 0f; blackOverlayAlpha = 0f;
                centerClockAlpha = 1f; authScreenAlpha = 0f;
                continueTextAlpha = (float)(0.55f + 0.45f * Math.sin(now / 260.0));

                if (hwidCheckDone && hwidCheckSuccess) {
                    introStage = IntroStage.TRANSITION_TO_MENU;
                    introStageStart = now;
                }
            }
            case AUTH_SCREEN -> {
                helloAlpha = 0f; blackOverlayAlpha = 0f; centerClockAlpha = 1f; continueTextAlpha = 0f;
                float p = Math.min(1f, elapsed / 400f);
                topClockProgress = easeOutQuart(p); authScreenAlpha = easeOutQuart(p); menuButtonsProgress = 0f;
            }
            case TRANSITION_TO_MENU -> {
                float p = Math.min(1f, (now - introStageStart) / 850f);
                float ep = easeOutQuart(p);
                topClockProgress = 1f; menuButtonsProgress = ep; // Часы остаются наверху
                authScreenAlpha = 1f - ep; // Окно логина уезжает в прозрачность
                centerClockAlpha = 1f; continueTextAlpha = 0f;
                blackOverlayAlpha = 0f; helloAlpha = 0f;

                if (p >= 1f) {
                    introStage = IntroStage.MENU_READY;
                    introStageStart = now;
                    topClockProgress = 1f; menuButtonsProgress = 1f;
                    centerClockAlpha = 1f; continueTextAlpha = 0f; authScreenAlpha = 0f;
                    introPlayedOnceThisSession = true;
                }
            }
            case MENU_READY -> {
                topClockProgress = 1f; menuButtonsProgress = 1f;
                centerClockAlpha = 1f; continueTextAlpha = 0f;
                blackOverlayAlpha = 0f; helloAlpha = 0f; authScreenAlpha = 0f;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateIntro();
        tickScrollAnim();
        tickTypewriter();

        long nowMs = System.currentTimeMillis();

        if (inAccountManager) {
            if (accountPanelAnimIn) {
                float p = Math.min(1f, (nowMs - accountPanelAnimStart) / (float) ACCOUNT_PANEL_MS);
                accountPanelAlpha = easeOutQuart(p);
            } else {
                float p = Math.min(1f, (nowMs - accountPanelAnimStart) / (float) ACCOUNT_PANEL_MS);
                accountPanelAlpha = 1f - easeOutQuart(p);
                if (p >= 1f) finishCloseAccountManager();
            }
        }

        // Обновление состояния кнопок
        {
            long animStart = menuButtonsAnimStart;
            if (animStart > 0) {
                long el = nowMs - animStart;
                if (el >= 0) {
                    float p = Math.min(1f, el / (float) MENU_BUTTONS_MS);
                    if (menuButtonsAnimIn) {
                        menuButtonsAlpha = easeOutQuart(p);
                    } else {
                        menuButtonsAlpha = 1f - easeOutQuart(p);
                    }
                }
            }

            // ФИКС АЛЬФЫ И ВИДИМОСТИ: теперь кнопки полностью скрываются
            float combinedAlpha = menuButtonsAlpha * menuButtonsProgress;
            for (int i = 0; i < menuButtons.size(); i++) {
                LexoraButton b = menuButtons.get(i);

                // Для анимации выезда из-под экрана
                float sp = Math.min(1f, Math.max(0f, (menuButtonsProgress - i * 0.07f) / 0.65f));
                float finalReveal = combinedAlpha * easeOutQuart(sp);

                b.setRevealAlpha(finalReveal);
                b.visible = finalReveal > 0.01f;
                b.active  = menuButtonsAlpha > 0.6f && introStage == IntroStage.MENU_READY
                        && !transitionOut && !exitAnimating && !inAccountManager;
            }
        }

        for (int i = 0; i < 4; i++) {
            boolean hov = mouseX >= mgrBtnX[i] && mouseX <= mgrBtnX[i] + mgrBtnW[i]
                    && mouseY >= mgrBtnY[i] && mouseY <= mgrBtnY[i] + mgrBtnH[i];
            mgrBtnHover[i] += ((hov ? 1f : 0f) - mgrBtnHover[i]) * 0.18f;
            if (mgrBtnHover[i] < 0.005f) mgrBtnHover[i] = 0f;
        }

        drawTexQuad(context, BACKGROUND_TEX, 0, 0, this.width, this.height, 0f, 0f, 1f, 1f, 0xFFFFFFFF);

        if (inAccountManager) {
            renderTopClock(context, 1f);
            renderSlidingButtons();
            renderAccountManager(context, mouseX, mouseY);
        } else {
            renderIntroOrMenu(context, mouseX, mouseY);
        }

        // ИНДИКАТОР АВТОРИЗАЦИИ ЛЕВЫЙ ВЕРХНИЙ УГОЛ
        if ((introStage == IntroStage.MENU_READY || introStage == IntroStage.TRANSITION_TO_MENU) && !inAccountManager) {
            float ap = introStage == IntroStage.MENU_READY ? 1f : menuButtonsProgress;
            if (ap > 0.01f) {
                if (!isAuthenticatedSession) {
                    getFont().draw(context.getMatrices(), "Статус: Не авторизован", 10, 10, SIZE_SMALL, adjustAlpha(0xFFDD4444, ap));
                } else {
                    String u = (this.client != null && this.client.getSession() != null) ? this.client.getSession().getUsername() : "Player";
                    getFont().draw(context.getMatrices(), "Игрок: " + u, 10, 10, SIZE_SMALL, adjustAlpha(0xFFE8E8E8, ap));

                    // --- ЭТОТ КУСОК МЕНЯЕМ ---
                    String w = CosmeticsManager.getEquipped(u, "back");
                    if (w != null && !w.equals("none")) {
                        getFont().draw(context.getMatrices(), "Спина: " + w, 10, 22, SIZE_SMALL, adjustAlpha(0xFF44DD44, ap));
                    }

                    // --- ДОБАВЛЯЕМ ПРОВЕРКУ ГОЛОВЫ ---
                    String h = CosmeticsManager.getEquipped(u, "head");
                    if (h != null && !h.equals("none")) {
                        // Рисуем текст чуть ниже, на координате Y = 34
                        getFont().draw(context.getMatrices(), "Голова: " + h, 10, 34, SIZE_SMALL, adjustAlpha(0xFF44DD44, ap));
                    }
                    // ---------------------------------
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);

        // Переход с затемнением
        if (transitionOut) {
            long el = nowMs - transitionOutStart;
            float p = Math.min(1f, el / (float) TRANSITION_OUT_MS);
            int a = (int)(easeOutQuart(p) * 255f);
            context.fill(0, 0, this.width, this.height, a << 24);

            if (p >= 1f && transitionOutTarget != null) {
                Runnable t = transitionOutTarget;
                transitionOutTarget = null;
                transitionOut = false;
                this.client.execute(t);
                return;
            }
        }

        if (exitAnimating) {
            long el = nowMs - exitStartMs;
            float p = Math.min(1f, el / (float) EXIT_ANIM_MS);
            int fadeA = (int)(easeOutQuart(p) * 255f);
            context.fill(0, 0, this.width, this.height, fadeA << 24);
            if (p >= 1f) { this.client.scheduleStop(); return; }
        }
    }

    private void renderIntroOrMenu(DrawContext context, int mx, int my) {
        if (blackOverlayAlpha > 0.01f) {
            int a = (int)(blackOverlayAlpha * 255f);
            context.fill(0, 0, this.width, this.height, a << 24);
        }
        if (introStage == IntroStage.HELLO && helloAlpha > 0.01f) renderHello(context);

        if (introStage == IntroStage.WAIT_CONTINUE) {
            renderCenterClock(context, 0f);
        } else if (introStage == IntroStage.AUTH_SCREEN) {
            renderCenterClock(context, topClockProgress);
            renderAuthScreen(context, mx, my);
        } else if (introStage == IntroStage.TRANSITION_TO_MENU) {
            renderCenterClock(context, topClockProgress);
            if (authScreenAlpha > 0.01f) renderAuthScreen(context, mx, my);
            renderSlidingButtons();
        } else if (introStage == IntroStage.MENU_READY) {
            renderTopClock(context, 1f);
            renderSlidingButtons();
        }
    }

    private void renderAuthScreen(DrawContext context, int mx, int my) {
        if (authScreenAlpha <= 0.01f) return;
        int boxW = 320, boxH = 220;
        int boxX = this.width / 2 - boxW / 2;
        int boxY = this.height / 2 - boxH / 2 + 30; // Сдвинуто вниз из-за часов

        drawSmoothRect(context, boxX, boxY, boxW, boxH, adjustAlpha(COL_PANEL_BG, authScreenAlpha));
        getFont().draw(context.getMatrices(), "АВТОРИЗАЦИЯ", boxX + boxW/2f - getFont().getWidth("АВТОРИЗАЦИЯ", SIZE_SMALL)/2f, boxY + 15, SIZE_SMALL, adjustAlpha(COL_TEXT_PRIMARY, authScreenAlpha));
        context.fill(boxX + 20, boxY + 30, boxX + boxW - 20, boxY + 31, adjustAlpha(COL_DIVIDER, authScreenAlpha));

        int fX = boxX + 40, fW = boxW - 80, fH = 24;

        // Логин
        int fY1 = boxY + 50;
        drawSmoothRect(context, fX, fY1, fW, fH, adjustAlpha(authFocusedField == 1 ? COL_FIELD_FOCUSED : COL_FIELD_BG, authScreenAlpha));
        if (authInputText.isEmpty() && authFocusedField != 1) {
            getFont().draw(context.getMatrices(), "Логин от сайта...", fX + 8, fY1 + 8, SIZE_SMALL, adjustAlpha(COL_TEXT_MUTED, authScreenAlpha));
        } else {
            getFont().draw(context.getMatrices(), authInputText, fX + 8, fY1 + 8, SIZE_SMALL, adjustAlpha(COL_TEXT_PRIMARY, authScreenAlpha));
            if (authFocusedField == 1 && ((System.currentTimeMillis() / 530) % 2) == 0) {
                float cX = fX + 8 + getFont().getWidth(authInputText.substring(0, authCursorPos), SIZE_SMALL);
                context.fill((int)cX, fY1 + 5, (int)cX + 1, fY1 + fH - 5, adjustAlpha(COL_TEXT_PRIMARY, authScreenAlpha));
            }
        }

        // Пароль
        int fY2 = boxY + 90;
        drawSmoothRect(context, fX, fY2, fW, fH, adjustAlpha(authFocusedField == 2 ? COL_FIELD_FOCUSED : COL_FIELD_BG, authScreenAlpha));
        if (authPasswordText.isEmpty() && authFocusedField != 2) {
            getFont().draw(context.getMatrices(), "Пароль...", fX + 8, fY2 + 8, SIZE_SMALL, adjustAlpha(COL_TEXT_MUTED, authScreenAlpha));
        } else {
            String hidden = "*".repeat(authPasswordText.length());
            getFont().draw(context.getMatrices(), hidden, fX + 8, fY2 + 8, SIZE_SMALL, adjustAlpha(COL_TEXT_PRIMARY, authScreenAlpha));
            if (authFocusedField == 2 && ((System.currentTimeMillis() / 530) % 2) == 0) {
                float cX = fX + 8 + getFont().getWidth("*".repeat(authPassCursorPos), SIZE_SMALL);
                context.fill((int)cX, fY2 + 5, (int)cX + 1, fY2 + fH - 5, adjustAlpha(COL_TEXT_PRIMARY, authScreenAlpha));
            }
        }

        // Статус
        if (!authStatusCurrent.isEmpty()) {
            float tw = getFont().getWidth(authStatusCurrent, SIZE_SMALL);
            getFont().draw(context.getMatrices(), authStatusCurrent, boxX + boxW/2f - tw/2f, boxY + 130, SIZE_SMALL, adjustAlpha(authStatusColor, authScreenAlpha));
        }

        // Кнопки
        int bW = 110, bH = 26;
        boolean showSkip = !hwidConfirmedNew;
        int bX1 = showSkip ? boxX + 40 : boxX + boxW/2 - bW/2, bY1 = boxY + 170;
        int bX2 = boxX + boxW - 40 - bW, bY2 = boxY + 170;

        boolean h1 = mx >= bX1 && mx <= bX1 + bW && my >= bY1 && my <= bY1 + bH;

        drawSmoothRect(context, bX1, bY1, bW, bH, adjustAlpha(h1 ? COL_BTN_HOVER : COL_BTN_NORMAL, authScreenAlpha));
        getFont().draw(context.getMatrices(), "ВОЙТИ", bX1 + bW/2f - getFont().getWidth("ВОЙТИ", SIZE_HINT)/2f, bY1 + 8, SIZE_HINT, adjustAlpha(COL_TEXT_PRIMARY, authScreenAlpha));

        if (showSkip) {
            boolean h2 = mx >= bX2 && mx <= bX2 + bW && my >= bY2 && my <= bY2 + bH;
            drawSmoothRect(context, bX2, bY2, bW, bH, adjustAlpha(h2 ? COL_BTN_HOVER : COL_BTN_NORMAL, authScreenAlpha));
            getFont().draw(context.getMatrices(), "ПРОПУСТИТЬ", bX2 + bW/2f - getFont().getWidth("ПРОПУСТИТЬ", SIZE_HINT)/2f, bY2 + 8, SIZE_HINT, adjustAlpha(COL_TEXT_PRIMARY, authScreenAlpha));
        }
    }

    private void renderHello(DrawContext context) {
        Identifier frame = HELLO_GIF.getFrame();
        if (frame == null) return;
        int a = Math.max(0, Math.min(255, (int)(helloAlpha * 255f)));
        float drawW = HELLO_GIF.getWidth()  * HELLO_GIF_SCALE;
        float drawH = HELLO_GIF.getHeight() * HELLO_GIF_SCALE;
        drawTexQuad(context, frame,
                this.width / 2f - drawW / 2f, this.height / 2f - drawH / 2f,
                drawW, drawH, 0f, 0f, 1f, 1f, (a << 24) | 0xFFFFFF);
    }

    private void renderCenterClock(DrawContext context, float moveToTopProgress) {
        String dateStr = getCurrentDateString();
        String timeStr = getCurrentTimeString();
        int alpha     = (int)(centerClockAlpha * 255f);
        int textColor = (alpha << 24) | 0xFFFFFF;

        float startY = this.height / 2f - 52f, endY = 12f;
        float y      = lerp(startY, endY, moveToTopProgress);

        float dateW = getFont().getWidth(dateStr, SIZE_DATE);
        getFont().draw(context.getMatrices(), dateStr,
                this.width / 2f - dateW / 2f, y, SIZE_DATE, textColor);
        float timeW = getFont().getWidth(timeStr, SIZE_TIME);
        getFont().draw(context.getMatrices(), timeStr,
                this.width / 2f - timeW / 2f, y + 18f, SIZE_TIME, textColor);

        if (introStage == IntroStage.WAIT_CONTINUE) {
            int hA = Math.max(0, Math.min(255, (int)(continueTextAlpha * 255f)));
            String pressText = "Нажмите, чтобы продолжить";
            float pw = getFont().getWidth(pressText, SIZE_HINT);
            getFont().draw(context.getMatrices(), pressText,
                    this.width / 2f - pw / 2f, y + 72f, SIZE_HINT, (hA << 24) | 0x777777);
        }
    }

    private void renderTopClock(DrawContext context, float progress) {
        renderCenterClock(context, progress);
    }

    private void renderSlidingButtons() {
        int btnW    = 152;
        int centerX = this.width / 2 - btnW / 2;
        int startY  = this.height / 2 + 40;
        int halfW   = (btnW - 6) / 2;

        for (int i = 0; i < menuButtons.size(); i++) {
            LexoraButton btn = menuButtons.get(i);
            float sp = Math.min(1f, Math.max(0f, (menuButtonsProgress - i * 0.07f) / 0.65f));
            sp = easeOutQuart(sp);

            int[] targets = {startY, startY + 34, startY + 68, startY + 102, startY + 102};
            int targetY   = targets[Math.min(i, targets.length - 1)];
            int targetX   = (i >= 3) ? (i == 3 ? centerX : centerX + halfW + 6) : centerX;

            float slideOffset = (1f - menuButtonsAlpha) * 20f;
            btn.setPosition(targetX, (int)(lerp(targetY + 65, targetY, sp) + slideOffset));
        }
    }

    private void renderAccountManager(DrawContext context, int mouseX, int mouseY) {
        float ap = accountPanelAlpha;
        if (ap <= 0.01f) return;

        int panelW = 580, panelH = 300;
        int startX = (this.width - panelW) / 2;
        int startY = (this.height - panelH) / 2;

        int panelOffsetY = (int)lerp(25, 0, ap);
        int panelA       = (int)(ap * 255);

        drawSmoothRect(context, startX, startY + panelOffsetY, panelW, panelH,
                (panelA << 24) | (COL_PANEL_BG & 0xFFFFFF));

        getFont().draw(context.getMatrices(), "АККАУНТЫ",
                startX + 16, startY + panelOffsetY + 14, SIZE_SMALL,
                adjustAlpha(COL_TEXT_SECONDARY, ap));
        context.fill(startX + 14, startY + panelOffsetY + 28,
                startX + panelW - 14, startY + panelOffsetY + 29,
                adjustAlpha(COL_DIVIDER, ap));

        int leftW = 184;
        int lx    = startX + 14;
        int ly    = startY + panelOffsetY + 36;
        int leftH = panelH - 50;

        drawSmoothRect(context, lx, ly, leftW, leftH, adjustAlpha(COL_PANEL_BG2, ap));

        getFont().draw(context.getMatrices(), "ВХОД", lx + 12, ly + 12, SIZE_SMALL,
                adjustAlpha(COL_TEXT_SECONDARY, ap));

        int fieldX = lx + 10, fieldY = ly + 28, fieldW = leftW - 20, fieldH = 22;
        drawSmoothRect(context, fieldX, fieldY, fieldW, fieldH,
                adjustAlpha(inputFocused ? COL_FIELD_FOCUSED : COL_FIELD_BG, ap));

        if (inputText.isEmpty() && !inputFocused) {
            getFont().draw(context.getMatrices(), "Введите ник...",
                    fieldX + 7, fieldY + 7, SIZE_SMALL, adjustAlpha(COL_TEXT_MUTED, ap));
        } else {
            getFont().draw(context.getMatrices(), inputText,
                    fieldX + 7, fieldY + 7, SIZE_SMALL, adjustAlpha(COL_TEXT_PRIMARY, ap));
            if (inputFocused) {
                boolean cursorVisible = ((System.currentTimeMillis() / 530) % 2) == 0;
                if (cursorVisible) {
                    String beforeCursor = inputText.substring(0, Math.min(cursorPos, inputText.length()));
                    float cursorX = fieldX + 7 + getFont().getWidth(beforeCursor, SIZE_SMALL);
                    context.fill((int)cursorX, fieldY + 5, (int)cursorX + 1, fieldY + fieldH - 5,
                            adjustAlpha(COL_TEXT_PRIMARY, ap));
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            int bx = mgrBtnX[i], by = mgrBtnY[i] + panelOffsetY;
            int bw = mgrBtnW[i], bh = mgrBtnH[i];
            float hov = mgrBtnHover[i];
            int btnBg = adjustAlpha(blendColors(COL_BTN_NORMAL, COL_BTN_HOVER, hov), ap);

            drawSmoothRect(context, bx, by, bw, bh, btnBg);

            context.getMatrices().push();
            float sc = 1f + hov * 0.02f;
            context.getMatrices().translate(bx + bw / 2f, by + bh / 2f, 0);
            context.getMatrices().scale(sc, sc, 1f);
            context.getMatrices().translate(-bw / 2f, -bh / 2f, 0);

            int textCol = blendColors(adjustAlpha(COL_TEXT_PRIMARY, ap), adjustAlpha(COL_TEXT_WHITE, ap), hov);
            String lbl = mgrBtnLabel[i];
            float lw = getFont().getWidth(lbl, SIZE_HINT);
            getFont().draw(context.getMatrices(), lbl,
                    (bw - lw) / 2f, (bh - SIZE_HINT) / 2f - 0.5f, SIZE_HINT, textCol);
            context.getMatrices().pop();
        }

        String curName = (this.client != null && this.client.getSession() != null)
                ? this.client.getSession().getUsername() : "Player";

        // ФИКС НАЛЕЗАНИЯ: Привязали значок строго над кнопкой "Назад"
        int badgeY = (mgrBtnY[3] + panelOffsetY) - 28;

        drawSmoothRect(context, lx + 10, badgeY, leftW - 20, 22,
                adjustAlpha(0x99101010, ap));
        String bStr = "◈  " + curName;
        float bW = getFont().getWidth(bStr, SIZE_SMALL);
        getFont().draw(context.getMatrices(), bStr,
                lx + 10 + (leftW - 20) / 2f - bW / 2f, badgeY + 7f,
                SIZE_SMALL, adjustAlpha(COL_TEXT_PRIMARY, ap));

        int rx = lx + leftW + 12;
        int ry = ly;
        int rw = panelW - leftW - 40;
        int rh = leftH;

        drawSmoothRect(context, rx, ry, rw, rh, adjustAlpha(COL_PANEL_BG2, ap));
        getFont().draw(context.getMatrices(), "СПИСОК АККАУНТОВ",
                rx + 12, ry + 12, SIZE_SMALL, adjustAlpha(COL_TEXT_SECONDARY, ap));

        int lx2 = rx + 8, ly2 = ry + 28, lw2 = rw - 16, lh2 = rh - 36;
        int itemH = 30;

        context.enableScissor(lx2, ly2, lx2 + lw2, ly2 + lh2);

        for (int i = 0; i < savedAccounts.size(); i++) {
            int itemY = ly2 + (i * (itemH + 4)) - (int)scrollYAnim;
            if (itemY + itemH < ly2 || itemY > ly2 + lh2) continue;

            String acc    = savedAccounts.get(i);
            boolean hov   = mouseX >= lx2 && mouseX <= lx2 + lw2
                    && mouseY >= itemY && mouseY <= itemY + itemH;
            boolean sel   = acc.equals(selectedAccount);
            boolean delHov= mouseX >= lx2 + lw2 - 26 && mouseX <= lx2 + lw2 - 4
                    && mouseY >= itemY && mouseY <= itemY + itemH;

            int itemBg = adjustAlpha(sel ? COL_LIST_SELECTED : (hov ? COL_LIST_HOVER : COL_LIST_ITEM), ap);

            drawSmoothRect(context, lx2 + 1, itemY, lw2 - 2, itemH, itemBg);

            drawTexQuad(context, getAccountSkin(acc), lx2 + 8, itemY + 7, 16, 16,
                    0.125f, 0.125f, 0.25f, 0.25f, adjustAlpha(0xFFFFFFFF, ap));

            int textCol = adjustAlpha(sel ? COL_TEXT_WHITE : COL_TEXT_PRIMARY, ap);
            getFont().draw(context.getMatrices(), acc, lx2 + 30, itemY + 11, SIZE_SMALL, textCol);

            int crossCol = delHov ? adjustAlpha(0xFFCC4444, ap) : adjustAlpha(0xFF3A3A3A, ap);
            drawRotatedTexQuad(context, ADD_TEX, lx2 + lw2 - 20, itemY + 11, 8, 8,
                    0f, 0f, 1f, 1f, crossCol, (float)(Math.PI / 4.0));
        }

        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inAccountManager && introStage == IntroStage.WAIT_CONTINUE && button == 0) {
            introStage = IntroStage.AUTH_SCREEN;
            introStageStart = System.currentTimeMillis();
            return true;
        }

        // КЛИКИ В ОКНЕ АВТОРИЗАЦИИ (Перед меню)
        if (introStage == IntroStage.AUTH_SCREEN && button == 0) {
            if (isAuthLoading) return true; // Блок во время проверки

            int boxW = 320, boxH = 220;
            int boxX = this.width / 2 - boxW / 2, boxY = this.height / 2 - boxH / 2 + 30;
            int fX = boxX + 40, fW = boxW - 80, fH = 24;
            int fY1 = boxY + 50, fY2 = boxY + 90;

            if (mx >= fX && mx <= fX + fW && my >= fY1 && my <= fY1 + fH) { authFocusedField = 1; return true; }
            else if (mx >= fX && mx <= fX + fW && my >= fY2 && my <= fY2 + fH) { authFocusedField = 2; return true; }
            else { authFocusedField = 0; }

            int bW = 110, bH = 26;
            boolean showSkip = !hwidConfirmedNew;
            int bX1 = showSkip ? boxX + 40 : boxX + boxW/2 - bW/2, bY1 = boxY + 170;
            int bX2 = boxX + boxW - 40 - bW, bY2 = boxY + 170;

            if (mx >= bX1 && mx <= bX1 + bW && my >= bY1 && my <= bY1 + bH) {
                // ВОЙТИ
                String u = authInputText.trim(), p = authPasswordText.trim();
                if (u.isEmpty() || p.isEmpty()) { setAuthStatus("Заполните все поля", 0xFFDD4444); return true; }
                isAuthLoading = true; setAuthStatus("Связь с сервером...", 0xFFEEEEEE);

                new Thread(() -> {
                    AuthManager.AuthResult res = AuthManager.login(u, p);
                    if (res.success) {
                        isAuthenticatedSession = true;
                        setAuthStatus("Успешно!", 0xFF44DD44);
                        loginAccount(u); // Сразу добавляем ник в альт менеджер

                        // === ОБНОВЛЕНО ===
                        // Передаем только ник, так как метод сам под капотом цепляет токен и HWID[cite: 13]
                        com.lexoravisauls.client.badge.LexoraAccount.syncCurrentIgn(u);
                        // =================
                        com.lexoravisauls.client.emotion.EmotionManager.fetchRadialSlots();

                        try { Thread.sleep(700); } catch (Exception e) {}
                        introStage = IntroStage.TRANSITION_TO_MENU; introStageStart = System.currentTimeMillis();
                    } else {
                        isAuthenticatedSession = false;
                        setAuthStatus(res.message, 0xFFDD4444);
                    }
                    isAuthLoading = false;
                }).start();
                return true;
            }

            if (showSkip && mx >= bX2 && mx <= bX2 + bW && my >= bY2 && my <= bY2 + bH) {
                // ПРОПУСТИТЬ
                isAuthenticatedSession = false;
                introStage = IntroStage.TRANSITION_TO_MENU;
                introStageStart = System.currentTimeMillis();
                return true;
            }
            return true;
        }

        if (!inAccountManager && introStage != IntroStage.MENU_READY) return true;

        if (inAccountManager && button == 0) {
            int panelW = 580, panelH = 300;
            int startX = (this.width - panelW) / 2;
            int startY = (this.height - panelH) / 2;
            int leftW  = 184;
            int lx     = startX + 14;
            int ly     = startY + 36;
            int rh     = panelH - 50;

            for (int i = 0; i < 4; i++) {
                if (mx >= mgrBtnX[i] && mx <= mgrBtnX[i] + mgrBtnW[i]
                        && my >= mgrBtnY[i] && my <= mgrBtnY[i] + mgrBtnH[i]) {
                    switch (i) {
                        case 0 -> { String n = inputText.trim(); if (!n.isEmpty()) loginAccount(n); }
                        case 1 -> {
                            String rn = generateUniqueFakeName();
                            startTypewriter(rn);
                            loginAccount(rn);
                        }
                        case 2 -> {
                            if (!selectedAccount.isEmpty()) {
                                savedAccounts.remove(selectedAccount);
                                selectedAccount = "";
                                saveAccounts();
                            }
                        }
                        case 3 -> closeAccountManager();
                    }
                    return true;
                }
            }

            int fieldX = lx + 10, fieldY = ly + 28, fieldW = leftW - 20, fieldH = 22;
            if (mx >= fieldX && mx <= fieldX + fieldW && my >= fieldY && my <= fieldY + fieldH) {
                inputFocused = true;
                return true;
            } else {
                inputFocused = false;
            }

            int rx  = lx + leftW + 12;
            int ry  = ly;
            int rw  = panelW - leftW - 40;
            int lx2 = rx + 8, ly2 = ry + 28, lw2 = rw - 16, lh2 = rh - 36;
            int itemH = 30;

            if (mx >= lx2 && mx <= lx2 + lw2 && my >= ly2 && my <= ly2 + lh2) {
                int clickY = (int)(my - ly2 + scrollYAnim);
                int index  = clickY / (itemH + 4);
                if (index >= 0 && index < savedAccounts.size()) {
                    String clicked = savedAccounts.get(index);
                    if (mx >= lx2 + lw2 - 26) {
                        savedAccounts.remove(index);
                        if (selectedAccount.equals(clicked)) selectedAccount = "";
                        saveAccounts();
                        return true;
                    }
                    long time = System.currentTimeMillis();
                    if (clicked.equals(selectedAccount) && time - lastClickTime < 350) {
                        loginAccount(clicked);
                        startTypewriter(clicked);
                    } else {
                        selectedAccount = clicked;
                        startTypewriter(clicked);
                    }
                    lastClickTime = time;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (inAccountManager) {
            scrollYTarget -= (int)(vAmt * 22);
            if (scrollYTarget < 0) scrollYTarget = 0;
            int maxScroll = Math.max(0, savedAccounts.size() * 34 - 200);
            if (scrollYTarget > maxScroll) scrollYTarget = maxScroll;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        // Окно авторизации
        if (introStage == IntroStage.AUTH_SCREEN && !isAuthLoading) {
            if (authFocusedField == 1 && authInputText.length() < 16 && (Character.isLetterOrDigit(c) || c == '_')) {
                authInputText = authInputText.substring(0, authCursorPos) + c + authInputText.substring(authCursorPos); authCursorPos++; return true;
            }
            if (authFocusedField == 2 && authPasswordText.length() < 32) {
                authPasswordText = authPasswordText.substring(0, authPassCursorPos) + c + authPasswordText.substring(authPassCursorPos); authPassCursorPos++; return true;
            }
        }

        // Альт менеджер
        if (inAccountManager && inputFocused) {
            if (inputText.length() < 16 && (Character.isLetterOrDigit(c) || c == '_')) {
                String before = inputText.substring(0, Math.min(cursorPos, inputText.length()));
                String after  = inputText.substring(Math.min(cursorPos, inputText.length()));
                inputText = before + c + after;
                cursorPos++;
                typewriterTarget = inputText;
                typewriterPos    = inputText.length();
            }
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Окно авторизации
        if (introStage == IntroStage.AUTH_SCREEN && !isAuthLoading) {
            if (keyCode == 258) { authFocusedField = authFocusedField == 1 ? 2 : 1; return true; } // TAB
            if (authFocusedField == 1) {
                if (keyCode == 259 && authCursorPos > 0) { authInputText = authInputText.substring(0, authCursorPos - 1) + authInputText.substring(authCursorPos); authCursorPos--; }
                if (keyCode == 261 && authCursorPos < authInputText.length()) { authInputText = authInputText.substring(0, authCursorPos) + authInputText.substring(authCursorPos + 1); }
                if (keyCode == 263 && authCursorPos > 0) authCursorPos--; if (keyCode == 262 && authCursorPos < authInputText.length()) authCursorPos++;
            }
            if (authFocusedField == 2) {
                if (keyCode == 259 && authPassCursorPos > 0) { authPasswordText = authPasswordText.substring(0, authPassCursorPos - 1) + authPasswordText.substring(authPassCursorPos); authPassCursorPos--; }
                if (keyCode == 261 && authPassCursorPos < authPasswordText.length()) { authPasswordText = authPasswordText.substring(0, authPassCursorPos) + authPasswordText.substring(authPassCursorPos + 1); }
                if (keyCode == 263 && authPassCursorPos > 0) authPassCursorPos--; if (keyCode == 262 && authPassCursorPos < authPasswordText.length()) authPassCursorPos++;
            }
            if (keyCode == 256) { authFocusedField = 0; }
            return true;
        }

        // Альт менеджер
        if (inAccountManager && inputFocused) {
            if (keyCode == 259 && !inputText.isEmpty() && cursorPos > 0) {
                inputText = inputText.substring(0, cursorPos - 1)
                        + inputText.substring(Math.min(cursorPos, inputText.length()));
                cursorPos--;
                typewriterTarget = inputText; typewriterPos = inputText.length();
            }
            if (keyCode == 261 && cursorPos < inputText.length()) {
                inputText = inputText.substring(0, cursorPos) + inputText.substring(cursorPos + 1);
                typewriterTarget = inputText; typewriterPos = inputText.length();
            }
            if (keyCode == 263 && cursorPos > 0) cursorPos--;
            if (keyCode == 262 && cursorPos < inputText.length()) cursorPos++;
            if (keyCode == 257 || keyCode == 335) {
                String n = inputText.trim(); if (!n.isEmpty()) loginAccount(n);
            }
            if (keyCode == 256) inputFocused = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String getCurrentTimeString() {
        LocalTime now = LocalTime.now();
        return String.format("%02d:%02d", now.getHour(), now.getMinute());
    }

    private String getCurrentDateString() {
        Locale ru = new Locale("ru");
        LocalDate now = LocalDate.now();
        return capitalize(now.getDayOfWeek().getDisplayName(TextStyle.FULL, ru))
                + ", " + now.getDayOfMonth() + " "
                + now.getMonth().getDisplayName(TextStyle.FULL, ru);
    }

    private String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int adjustAlpha(int color, float alpha) {
        int a = (int)(((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0xFFFFFF);
    }

    private float lerp(float a, float b, float t)   { return a + (b - a) * t; }

    private float easeOutQuart(float x)             { return 1f - (float)Math.pow(1f - x, 4f); }

    public static int blendColors(int c1, int c2, float r) {
        int a1=(c1>>24)&0xFF, r1=(c1>>16)&0xFF, g1=(c1>>8)&0xFF, b1=c1&0xFF;
        int a2=(c2>>24)&0xFF, r2=(c2>>16)&0xFF, g2=(c2>>8)&0xFF, b2=c2&0xFF;
        return ((int)(a1+(a2-a1)*r)<<24)|((int)(r1+(r2-r1)*r)<<16)
                |((int)(g1+(g2-g1)*r)<<8)|(int)(b1+(b2-b1)*r);
    }

    public static void drawSmoothRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        RoundedRectShader.draw(ctx, x, y, w, h, 6f, color);
    }

    // ОПТИМИЗИРОВАННЫЙ метод отрисовки (УБРАЛ ЛАГИ)
    public static void drawTexQuad(DrawContext ctx, Identifier tex,
                                   float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1, int color) {
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        float af = a / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(r, g, b, af);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX);

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        buffer.vertex(matrix, x, y, 0).texture(u0, v0);
        buffer.vertex(matrix, x, y + h, 0).texture(u0, v1);
        buffer.vertex(matrix, x + w, y + h, 0).texture(u1, v1);
        buffer.vertex(matrix, x + w, y, 0).texture(u1, v0);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void drawRotatedTexQuad(DrawContext ctx, Identifier tex,
                                          float x, float y, float w, float h,
                                          float u0, float v0, float u1, float v1,
                                          int color, float angle) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + w / 2f, y + h / 2f, 0);
        ctx.getMatrices().multiply(new Quaternionf().rotateZ(angle));
        ctx.getMatrices().translate(-w / 2f, -h / 2f, 0);
        drawTexQuad(ctx, tex, 0, 0, w, h, u0, v0, u1, v1, color);
        ctx.getMatrices().pop();
    }

    public static class LexoraButton extends ClickableWidget {
        private final Runnable pressAction;
        private float hoverAnim   = 0f;
        private float revealAlpha = 1f;

        public LexoraButton(int x, int y, int w, int h, String text, Runnable pressAction) {
            super(x, y, w, h, Text.literal(text));
            this.pressAction = pressAction;
        }

        public void setRevealAlpha(float a) { this.revealAlpha = Math.max(0f, Math.min(1f, a)); }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder b) {}

        @Override
        public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
            if (!this.visible || revealAlpha < 0.01f) return;

            float targetHov = (this.isHovered() && this.active) ? 1f : 0f;
            hoverAnim += (targetHov - hoverAnim) * 0.18f;
            if (hoverAnim < 0.005f) hoverAnim = 0f;

            float sc = 1f + hoverAnim * 0.02f;
            float cx = getX() + width / 2f, cy = getY() + height / 2f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().scale(sc, sc, 1f);
            ctx.getMatrices().translate(-cx, -cy, 0);

            int bgRaw  = blendColors(COL_BTN_NORMAL, COL_BTN_HOVER, hoverAnim);
            int bgA    = (int)(((bgRaw >> 24) & 0xFF) * revealAlpha);
            int bgColor= (bgA << 24) | (bgRaw & 0xFFFFFF);

            drawSmoothRect(ctx, getX(), getY(), width, height, bgColor);

            int txA = (int)(255 * revealAlpha);
            if (txA > 5) {
                int textColor = (txA << 24) | (blendColors(COL_TEXT_PRIMARY, COL_TEXT_WHITE, hoverAnim) & 0xFFFFFF);
                String t  = getMessage().getString();
                float tw  = getFont().getWidth(t, SIZE_HINT);
                getFont().draw(ctx.getMatrices(), t,
                        getX() + (width - tw) / 2f, getY() + (height - SIZE_HINT) / 2f - 0.5f,
                        SIZE_HINT, textColor);
            }

            ctx.getMatrices().pop();
        }

        @Override
        public void onClick(double mx, double my) {
            super.onClick(mx, my);
            if (this.isHovered() && pressAction != null) pressAction.run();
        }
    }

    private static final class AnimatedGifTexture {
        private static final int MAX_GIF_FRAMES = 240;
        private static final int MAX_GIF_PIXELS = 12_000_000;

        private final Identifier gifId;
        private final List<Identifier> frames = new ArrayList<>();
        private final List<Integer>    delays = new ArrayList<>();

        private volatile boolean decodeStarted = false;
        private volatile boolean decodeDone    = false;
        private volatile boolean decodeFailed  = false;

        private volatile List<DecodedFrame> decodedFrames    = List.of();
        private volatile int  decodedWidth      = 1;
        private volatile int  decodedHeight     = 1;
        private volatile long decodedDurationMs = 1000L;

        private int     uploadedIndex = 0;
        private boolean uploadDone    = false;

        private long startTime       = 0L;
        private int  width           = 1;
        private int  height          = 1;
        private long totalDurationMs = 1000L;
        private boolean missingLogged = false;
        private long    missingSinceMs = 0L;

        private AnimatedGifTexture(Identifier id) { this.gifId = id; }

        public void reset() { startTime = System.currentTimeMillis(); }

        public void startPreload() {
            if (decodeStarted || decodeDone || decodeFailed) return;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.getResourceManager() == null) return;
                Optional<Resource> res = mc.getResourceManager().getResource(gifId);
                if (res.isEmpty()) {
                    long now = System.currentTimeMillis();
                    if (missingSinceMs == 0L) missingSinceMs = now;
                    if (!missingLogged) { missingLogged = true; System.err.println("Lexora hello gif not found: " + gifId); }
                    if (now - missingSinceMs > 3000L) decodeFailed = true;
                    return;
                }
                decodeStarted = true;
                byte[] bytes;
                try (InputStream is = res.get().getInputStream()) { bytes = is.readAllBytes(); }
                Thread t = new Thread(() -> decodeGif(bytes), "Lexora-GIF-Loader");
                t.setDaemon(true);
                t.start();
            } catch (Throwable t) { decodeFailed = true; t.printStackTrace(); }
        }

        public void tickUpload(int max) {
            startPreload();
            if (!decodeDone || uploadDone || decodeFailed) return;
            List<DecodedFrame> lf = decodedFrames;
            if (lf.isEmpty()) { uploadDone = true; return; }
            int limit = Math.min(lf.size(), uploadedIndex + Math.max(1, max));
            MinecraftClient mc = MinecraftClient.getInstance();
            while (uploadedIndex < limit) {
                try {
                    DecodedFrame df = lf.get(uploadedIndex);
                    frames.add(uploadFrame(mc, df.image, uploadedIndex));
                    delays.add(df.delayMs);
                    uploadedIndex++;
                } catch (Throwable t) {
                    decodeFailed = true; uploadDone = false;
                    frames.clear(); delays.clear();
                    System.err.println("Lexora GIF upload failed");
                    t.printStackTrace();
                    return;
                }
            }
            if (uploadedIndex >= lf.size()) {
                width = decodedWidth; height = decodedHeight;
                totalDurationMs = Math.max(1000L, decodedDurationMs);
                uploadDone = true;
                reset();
            }
        }

        public boolean isReady()  { tickUpload(MENU_UPLOAD_PER_TICK); return uploadDone && !frames.isEmpty(); }
        public boolean isFailed() { return decodeFailed; }

        public Identifier getFrame() {
            tickUpload(MENU_UPLOAD_PER_TICK);
            if (frames.isEmpty()) return null;
            long elapsed = System.currentTimeMillis() - startTime;
            int total = delays.stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) return frames.get(0);
            long time = Math.min(elapsed, total - 1L);
            int cursor = 0;
            for (int i = 0; i < frames.size(); i++) {
                cursor += delays.get(i);
                if (time < cursor) return frames.get(i);
            }
            return frames.get(frames.size() - 1);
        }

        public int  getWidth()           { return width; }
        public int  getHeight()          { return height; }
        public long getTotalDurationMs() { return Math.max(1000L, totalDurationMs); }

        private void decodeGif(byte[] bytes) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                 ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                if (!readers.hasNext()) { decodeFailed = true; return; }
                ImageReader reader = readers.next();
                reader.setInput(iis, false);
                int count = reader.getNumImages(true);
                if (count <= 0 || count > MAX_GIF_FRAMES)
                    throw new IllegalStateException("Bad frame count: " + count);
                int lw = 1, lh = 1;
                try {
                    int[] sz = readLogicalSize(reader.getStreamMetadata());
                    lw = sz[0]; lh = sz[1];
                } catch (Throwable ignored) {}
                if (lw <= 1 || lh <= 1) {
                    BufferedImage f = reader.read(0);
                    lw = Math.max(1, f.getWidth()); lh = Math.max(1, f.getHeight());
                }
                if ((long)lw * lh > MAX_GIF_PIXELS)
                    throw new IllegalStateException("GIF too large");
                List<DecodedFrame> result = new ArrayList<>();
                BufferedImage canvas = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                g.setComposite(AlphaComposite.SrcOver);
                int duration = 0;
                for (int i = 0; i < count; i++) {
                    BufferedImage raw = toArgb(reader.read(i));
                    FrameMeta meta = readFrameMeta(reader.getImageMetadata(i), raw);
                    BufferedImage prev = "restoreToPrevious".equals(meta.disposalMethod) ? copyImage(canvas) : null;
                    g.drawImage(raw, meta.left, meta.top, null);
                    int delay = Math.max(meta.delayMs, 35);
                    result.add(new DecodedFrame(copyImage(canvas), delay));
                    duration += delay;
                    if ("restoreToBackgroundColor".equals(meta.disposalMethod)) {
                        g.setComposite(AlphaComposite.Clear);
                        g.fillRect(meta.left, meta.top, meta.width, meta.height);
                        g.setComposite(AlphaComposite.SrcOver);
                    } else if ("restoreToPrevious".equals(meta.disposalMethod) && prev != null) {
                        g.dispose(); canvas = prev;
                        g = canvas.createGraphics();
                        g.setComposite(AlphaComposite.SrcOver);
                    }
                }
                g.dispose(); reader.dispose();
                decodedWidth = lw; decodedHeight = lh;
                decodedDurationMs = Math.max(1000L, duration);
                decodedFrames = List.copyOf(result);
                decodeDone = true;
            } catch (Throwable t) { decodeFailed = true; t.printStackTrace(); }
        }

        private int[] readLogicalSize(IIOMetadata meta) {
            if (meta == null) return new int[]{1, 1};
            org.w3c.dom.Node root = meta.getAsTree(meta.getNativeMetadataFormatName());
            org.w3c.dom.Node node = findNode(root, "LogicalScreenDescriptor");
            if (node == null || node.getAttributes() == null) return new int[]{1, 1};
            return new int[]{
                    Math.max(1, readIntAttr(node, "logicalScreenWidth", 1)),
                    Math.max(1, readIntAttr(node, "logicalScreenHeight", 1))
            };
        }

        private FrameMeta readFrameMeta(IIOMetadata meta, BufferedImage img) {
            FrameMeta fm = new FrameMeta();
            fm.left = 0; fm.top = 0;
            fm.width = img.getWidth(); fm.height = img.getHeight();
            fm.delayMs = 70; fm.disposalMethod = "none";
            try {
                org.w3c.dom.Node root = meta.getAsTree(meta.getNativeMetadataFormatName());
                org.w3c.dom.Node id = findNode(root, "ImageDescriptor");
                if (id != null) {
                    fm.left   = readIntAttr(id, "imageLeftPosition", 0);
                    fm.top    = readIntAttr(id, "imageTopPosition", 0);
                    fm.width  = readIntAttr(id, "imageWidth", img.getWidth());
                    fm.height = readIntAttr(id, "imageHeight", img.getHeight());
                }
                org.w3c.dom.Node gce = findNode(root, "GraphicControlExtension");
                if (gce != null) {
                    fm.delayMs = readIntAttr(gce, "delayTime", 7) * 10;
                    org.w3c.dom.Node d = gce.getAttributes().getNamedItem("disposalMethod");
                    if (d != null) fm.disposalMethod = d.getNodeValue();
                }
            } catch (Throwable ignored) {}
            fm.width  = Math.max(1, fm.width);
            fm.height = Math.max(1, fm.height);
            return fm;
        }

        private int readIntAttr(org.w3c.dom.Node node, String name, int fallback) {
            try {
                if (node == null || node.getAttributes() == null) return fallback;
                org.w3c.dom.Node a = node.getAttributes().getNamedItem(name);
                return a == null ? fallback : Integer.parseInt(a.getNodeValue());
            } catch (Throwable ignored) { return fallback; }
        }

        private BufferedImage toArgb(BufferedImage src) {
            if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
            BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = c.createGraphics(); g.drawImage(src, 0, 0, null); g.dispose(); return c;
        }

        private BufferedImage copyImage(BufferedImage src) {
            BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = c.createGraphics(); g.drawImage(src, 0, 0, null); g.dispose(); return c;
        }

        private Identifier uploadFrame(MinecraftClient mc, BufferedImage img, int idx) {
            if (mc == null) throw new IllegalStateException("MC is null");
            int maxTex = 4096;
            try { maxTex = Math.max(1024, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)); } catch (Throwable ignored) {}
            if (img.getWidth() > maxTex || img.getHeight() > maxTex)
                throw new IllegalStateException("Frame too large");
            NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), true);
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) ni.setColorArgb(x, y, img.getRGB(x, y));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(ni);
            Identifier id = Identifier.of("lexoravisauls", "dynamic/hello_gif_" + idx);
            mc.getTextureManager().registerTexture(id, tex);
            try {
                int glTex = mc.getTextureManager().getTexture(id).getGlId();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTex);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            } catch (Throwable ignored) {}
            return id;
        }

        private org.w3c.dom.Node findNode(org.w3c.dom.Node root, String name) {
            if (root == null) return null;
            if (name.equals(root.getNodeName())) return root;
            org.w3c.dom.Node c = root.getFirstChild();
            while (c != null) {
                org.w3c.dom.Node f = findNode(c, name);
                if (f != null) return f;
                c = c.getNextSibling();
            }
            return null;
        }

        private static final class DecodedFrame {
            final BufferedImage image; final int delayMs;
            DecodedFrame(BufferedImage i, int d) { image = i; delayMs = d; }
        }
        private static final class FrameMeta {
            int left, top, width, height, delayMs; String disposalMethod;
        }
    }
}