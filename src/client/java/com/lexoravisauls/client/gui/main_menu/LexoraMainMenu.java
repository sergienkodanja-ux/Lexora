package com.lexoravisauls.client.gui.main_menu;

import com.google.common.collect.ImmutableList;
import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.modern.GuiLocalization;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.lexoravisauls.client.mixin.SessionAccessor;
import com.lexoravisauls.client.utils.ConfigManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.render.*;
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
import java.util.*;

public class LexoraMainMenu extends Screen {

    // ─── MSDF Font ───────────────────────────────────────────────────────────
    private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    // ─── Размеры шрифта ───────────────────────────────────────────────────────
    public static final float SIZE_LOGO  = 15.0f;
    public static final float SIZE_TIME  = 20.0f;
    public static final float SIZE_DATE  = 8.5f;
    public static final float SIZE_TITLE = 11.5f;
    public static final float SIZE_DESC  = 7.5f;
    public static final float SIZE_HINT  = 8.0f;
    public static final float SIZE_SMALL = 8.0f;
    public static final float SIZE_TINY  = 6.5f;

    // ─── Текстуры ─────────────────────────────────────────────────────────────
    public static final Identifier ODINOCH_TEX   = Identifier.of("lexoravisauls", "textures/gui/odinoch.png");
    public static final Identifier SETEVA_TEX    = Identifier.of("lexoravisauls", "textures/gui/seteva.png");
    public static final Identifier HELLO_GIF_TEX = Identifier.of("lexoravisauls", "textures/gui/hello.gif");
    public static final Identifier ADD_TEX       = Identifier.of("lexoravisauls", "textures/gui/add.png");

    // ─── Палитра ─────────────────────────────────────────────────────────────
    private static final int COL_TEXT_PRIMARY    = 0xFFEBEBF0;
    private static final int COL_TEXT_SECONDARY  = 0xFF8A8A9C;
    private static final int COL_TEXT_MUTED      = 0xFF555566;
    private static final int COL_TEXT_WHITE      = 0xFFFFFFFF;
    private static final int COL_CARD_BORDER     = 0x22FFFFFF;
    private static final int COL_CARD_BORDER_HOV = 0x65FFFFFF;
    private static final int COL_DOCK_BG         = 0x880B0C15;
    private static final int COL_PANEL_BG        = 0xEE0C0D15;
    private static final int COL_PANEL_BG2       = 0x99131420;
    private static final int COL_DIVIDER         = 0x20FFFFFF;
    private static final int COL_FIELD_BG        = 0xAA090910;
    private static final int COL_FIELD_FOCUSED   = 0xAA1E1E2C;
    private static final int COL_LIST_ITEM       = 0x55151622;
    private static final int COL_LIST_HOVER      = 0x88252636;
    private static final int COL_LIST_SELECTED   = 0xCC383955;

    // ─── Скины ────────────────────────────────────────────────────────────────
    public static final Identifier STEVE_SKIN = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    public static final Identifier ALEX_SKIN  = Identifier.of("minecraft", "textures/entity/player/slim/alex.png");
    public static final List<Identifier> ACCOUNT_SKINS = ImmutableList.of(STEVE_SKIN, ALEX_SKIN);
    private static final Map<String, Identifier> accountSkins = new HashMap<>();

    // ─── GIF Интро ────────────────────────────────────────────────────────────
    private static final AnimatedGifTexture HELLO_GIF = new AnimatedGifTexture(HELLO_GIF_TEX);
    private static boolean gifPreloaderRegistered = false;
    private static boolean introPlayedOnceThisSession = false;

    // ─── Состояния сессии (совместимость) ─────────────────────────────────────
    public static boolean isAuthenticatedSession = true;

    // ─── Стадии меню ─────────────────────────────────────────────────────────
    private enum MenuStage {
        HELLO, MENU
    }

    private MenuStage stage = MenuStage.MENU;
    private long stageStartTime = 0L;
    private boolean helloStarted = false;
    private float helloAlpha = 0.0f;
    private float menuEnterAnim = 0.0f;

    // ─── Анимации наведения ──────────────────────────────────────────────────
    private float spHoverAnim = 0.0f;
    private float mpHoverAnim = 0.0f;
    private float profileHoverAnim = 0.0f;

    // Dock buttons: 0 = Settings, 1 = ClickGUI, 2 = Accounts, 3 = Language, 4 = Exit
    private final float[] dockHoverAnim = new float[5];

    // ─── Анимация смены языка ────────────────────────────────────────────────
    private boolean langAnimActive = false;
    private long langAnimStart = 0L;
    private static final long LANG_ANIM_MS = 280L;
    private GuiLocalization.Language pendingTargetLang = null;

    // ─── Переход к другим экранам ─────────────────────────────────────────────
    private boolean transitionOut = false;
    private long transitionOutStart = 0L;
    private static final long TRANSITION_OUT_MS = 220L;
    private Runnable transitionOutTarget = null;

    private boolean exitAnimating = false;
    private long exitStartMs = 0L;
    private static final long EXIT_ANIM_MS = 350L;

    // ─── Менеджер аккаунтов ───────────────────────────────────────────────────
    private boolean inAccountManager = false;
    public static final List<String> savedAccounts = new ArrayList<>();
    private static boolean accountsLoaded = false;
    private static final Set<String> usedNames = new HashSet<>();
    private static final long globalSeed = UUID.randomUUID().getMostSignificantBits() ^ System.nanoTime();

    private float accountPanelAlpha = 0f;
    private boolean accountPanelAnimIn = false;
    private long accountPanelAnimStart = 0L;
    private static final long ACCOUNT_PANEL_MS = 260L;

    private String inputText = "";
    private boolean inputFocused = false;
    private int cursorPos = 0;

    private String typewriterTarget = "";
    private int typewriterPos = 0;
    private long typewriterNextMs = 0L;
    private static final long TYPEWRITER_CHAR_MS = 35L;

    private String selectedAccount = "";
    private float scrollYAnim = 0f;
    private int scrollYTarget = 0;
    private long lastClickTime = 0;

    private final int[] mgrBtnX = new int[4];
    private final int[] mgrBtnY = new int[4];
    private final int[] mgrBtnW = new int[4];
    private final int[] mgrBtnH = new int[4];
    private final float[] mgrBtnHover = new float[4];

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
    }

    private static MsdfFont getFont() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    // ─── Локализация ─────────────────────────────────────────────────────────
    private static boolean isRu() {
        return GuiLocalization.getLanguage() == GuiLocalization.Language.RU;
    }

    private static String tr(String ru, String en) {
        return isRu() ? ru : en;
    }

    public static void registerGifPreloader() {
        if (gifPreloaderRegistered) return;
        gifPreloaderRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HELLO_GIF.startPreload();
            HELLO_GIF.tickUpload(4);
        });
    }

    private static void loadAccounts() {
        try {
            Path file = MinecraftClient.getInstance().runDirectory.toPath().resolve("lexora_accounts.txt");
            if (!Files.exists(file)) {
                Path legacy = MinecraftClient.getInstance().runDirectory.toPath().resolve("lexora_alts.txt");
                if (Files.exists(legacy)) file = legacy;
            }
            if (Files.exists(file)) {
                savedAccounts.clear();
                savedAccounts.addAll(Files.readAllLines(file));
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки аккаунтов: " + e.getMessage());
        }
    }

    private static void saveAccounts() {
        try {
            Path file = MinecraftClient.getInstance().runDirectory.toPath().resolve("lexora_accounts.txt");
            Files.write(file, savedAccounts, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.err.println("Ошибка сохранения аккаунтов: " + e.getMessage());
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        HELLO_GIF.startPreload();
        HELLO_GIF.tickUpload(96);

        stageStartTime = System.currentTimeMillis();

        if (introPlayedOnceThisSession) {
            stage = MenuStage.MENU;
            menuEnterAnim = 1.0f;
            helloAlpha = 0.0f;
        } else {
            stage = MenuStage.HELLO;
            menuEnterAnim = 0.0f;
            helloAlpha = 0.0f;
            helloStarted = false;
            HELLO_GIF.reset();
        }

        computeManagerLayout();
    }

    private void computeManagerLayout() {
        int panelW = 540, panelH = 280;
        int startX = (this.width - panelW) / 2;
        int startY = (this.height - panelH) / 2;
        int leftW  = 175;
        int lx     = startX + 16;
        int ly     = startY + 42;
        int btnW   = leftW - 20;
        int btnH   = 23;

        for (int i = 0; i < 3; i++) {
            mgrBtnX[i] = lx + 10;
            mgrBtnY[i] = ly + 54 + i * 28;
            mgrBtnW[i] = btnW;
            mgrBtnH[i] = btnH;
        }
        mgrBtnX[3] = lx + 10;
        mgrBtnY[3] = startY + panelH - btnH - 16;
        mgrBtnW[3] = btnW;
        mgrBtnH[3] = btnH;
    }

    private void openAccountManager() {
        inAccountManager = true;
        accountPanelAnimIn = true;
        accountPanelAlpha = 0f;
        accountPanelAnimStart = System.currentTimeMillis();
        computeManagerLayout();
    }

    private void closeAccountManager() {
        accountPanelAnimIn = false;
        accountPanelAnimStart = System.currentTimeMillis();
    }

    private void finishCloseAccountManager() {
        inAccountManager = false;
        accountPanelAlpha = 0f;
    }

    private void navigateTo(Runnable target) {
        if (transitionOut) return;
        transitionOut = true;
        transitionOutStart = System.currentTimeMillis();
        transitionOutTarget = target;
    }

    private void triggerExitAnimation() {
        if (exitAnimating) return;
        exitAnimating = true;
        exitStartMs = System.currentTimeMillis();
    }

    private void skipIntroToMenu() {
        stage = MenuStage.MENU;
        introPlayedOnceThisSession = true;
        stageStartTime = System.currentTimeMillis();
    }

    private void triggerLanguageSwitchAnimation() {
        if (langAnimActive) return;
        langAnimActive = true;
        langAnimStart = System.currentTimeMillis();
        pendingTargetLang = (GuiLocalization.getLanguage() == GuiLocalization.Language.RU)
                ? GuiLocalization.Language.EN : GuiLocalization.Language.RU;
    }

    private void loginAccount(String name) {
        String n = name.trim();
        if (n.isEmpty()) return;

        if (this.client != null && this.client.getSession() != null) {
            ((SessionAccessor) this.client.getSession()).setUsername(n);
        }

        if (!savedAccounts.contains(n)) {
            savedAccounts.add(n);
            saveAccounts();
        }

        com.lexoravisauls.client.badge.LexoraAccount.syncCurrentIgn(n);
    }

    private static String generateUniqueFakeName() {
        Random random = new Random(System.nanoTime() ^ globalSeed);
        String[] nameStart = {
                "Kai", "Lex", "Val", "Max", "Zor", "Dex", "Tor", "Ren", "Leo", "Luc",
                "Rav", "Ash", "Ard", "Nik", "Sam", "Ben", "Kel", "Jax", "Nol", "Vin",
                "Tyr", "Sil", "Cai", "Dar", "Fen", "Gav", "Mal", "Neo", "Nova", "Vex"
        };
        String[] nameEnd = {
                "on", "ix", "us", "en", "or", "el", "ar", "an", "eo", "is", "eth", "il",
                "ad", "os", "an", "eus", "es", "ym", "ax", "ik", "sky", "core"
        };
        String[] suffixes = {"x", "q", "zz", "ai", "xy", "zor", "ar", "yn"};

        int tries = 0;
        String finalName;
        do {
            StringBuilder name = new StringBuilder();
            name.append(nameStart[random.nextInt(nameStart.length)]);
            name.append(nameEnd[random.nextInt(nameEnd.length)]);

            if (random.nextFloat() < 0.25f) name.append(suffixes[random.nextInt(suffixes.length)]);
            if (random.nextFloat() < 0.2f) name.append("_");

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

    private void tickTypewriter() {
        if (typewriterPos < typewriterTarget.length()) {
            long now = System.currentTimeMillis();
            while (typewriterPos < typewriterTarget.length() && now >= typewriterNextMs) {
                typewriterPos++;
                inputText = typewriterTarget.substring(0, typewriterPos);
                cursorPos = inputText.length();
                typewriterNextMs += TYPEWRITER_CHAR_MS;
            }
        }
    }

    private void tickScrollAnim() {
        float diff = scrollYTarget - scrollYAnim;
        if (Math.abs(diff) < 0.3f) {
            scrollYAnim = scrollYTarget;
            return;
        }
        scrollYAnim += diff * 0.25f;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (inAccountManager) {
            closeAccountManager();
            return false;
        }
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Отрисовывается внутри render()
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        tickScrollAnim();
        tickTypewriter();

        // Обработка анимации смены языка
        float langTransitionAlpha = 1.0f;
        float langTransitionOffset = 0.0f;
        float globeSpinAngle = 0.0f;

        if (langAnimActive) {
            float p = Math.min(1.0f, (now - langAnimStart) / (float) LANG_ANIM_MS);
            globeSpinAngle = easeOutQuart(p) * (float) (Math.PI * 2.0);

            if (p < 0.5f) {
                float halfP = p / 0.5f;
                langTransitionAlpha = 1.0f - halfP * 0.6f;
                langTransitionOffset = -halfP * 4.0f;
            } else {
                if (pendingTargetLang != null) {
                    GuiLocalization.setLanguage(pendingTargetLang);
                    ConfigManager.saveConfig();
                    pendingTargetLang = null;
                }
                float halfP = (p - 0.5f) / 0.5f;
                langTransitionAlpha = 0.4f + halfP * 0.6f;
                langTransitionOffset = (1.0f - halfP) * 4.0f;
            }

            if (p >= 1.0f) {
                langAnimActive = false;
                langTransitionAlpha = 1.0f;
                langTransitionOffset = 0.0f;
            }
        }

        // 0. Если SplashOverlay (загрузка ресурсов) ещё активен — не тратим время интро и не играем звук!
        if (this.client != null && this.client.getOverlay() != null) {
            stageStartTime = now;
            helloStarted = false;
            context.fill(0, 0, this.width, this.height, 0xFF000000);
            return;
        }

        // 1. Анимация интро Hello
        if (stage == MenuStage.HELLO) {
            HELLO_GIF.tickUpload(16);

            if (!helloStarted) {
                helloStarted = true;
                stageStartTime = now;
                HELLO_GIF.reset();
                MainMenuSoundHelper.playHelloSound(true);
            }

            long elapsed = now - stageStartTime;
            long gifDuration = Math.max(2200L, HELLO_GIF.getTotalDurationMs());
            long fadeInMs = 280L;
            long fadeOutMs = 380L;

            if (elapsed < fadeInMs) {
                helloAlpha = (float) elapsed / fadeInMs;
            } else if (elapsed < gifDuration) {
                helloAlpha = 1.0f;
            } else if (elapsed < gifDuration + fadeOutMs) {
                helloAlpha = 1.0f - ((float) (elapsed - gifDuration) / fadeOutMs);
            } else {
                skipIntroToMenu();
                return;
            }

            renderHelloStage(context, mouseX, mouseY);
            return;
        }

        // Плавный выезд меню
        if (menuEnterAnim < 1.0f) {
            float p = Math.min(1.0f, (now - stageStartTime) / 380.0f);
            menuEnterAnim = easeOutQuart(p);
        }

        // Анимация модального менеджера
        if (inAccountManager) {
            float p = Math.min(1f, (now - accountPanelAnimStart) / (float) ACCOUNT_PANEL_MS);
            if (accountPanelAnimIn) {
                accountPanelAlpha = easeOutQuart(p);
            } else {
                accountPanelAlpha = 1f - easeOutQuart(p);
                if (p >= 1f) finishCloseAccountManager();
            }
        }

        // 2. Фоновый шейдер дыма
        SmokeBackgroundShader.render(context, this.width, this.height, mouseX, mouseY, 1.0f);

        // Затемняющий мягкий виньетинг для акцентирования центрального хаба
        int topVignette = (int) (0x80 * menuEnterAnim) << 24;
        context.fillGradient(0, 0, this.width, 55, topVignette | 0x050508, 0x00050508);
        int botVignette = (int) (0x90 * menuEnterAnim) << 24;
        context.fillGradient(0, this.height - 65, this.width, this.height, 0x00050508, botVignette | 0x050508);

        // 3. Верхняя панель
        renderHeader(context, mouseX, mouseY, menuEnterAnim);

        // 4. Единый центральный хаб (Часы + Компактные Карточки + Поднятая Панель Навигации)
        renderCentralHub(context, mouseX, mouseY, menuEnterAnim, langTransitionAlpha, langTransitionOffset, globeSpinAngle);

        // 5. Модальное окно менеджера аккаунтов (если открыто)
        if (inAccountManager && accountPanelAlpha > 0.01f) {
            renderAccountManagerModal(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);

        // 6. Плавное затемнение при переходе
        if (transitionOut) {
            long el = now - transitionOutStart;
            float p = Math.min(1f, el / (float) TRANSITION_OUT_MS);
            int a = (int) (easeOutQuart(p) * 255f);
            context.fill(0, 0, this.width, this.height, a << 24);

            if (p >= 1f && transitionOutTarget != null) {
                Runnable t = transitionOutTarget;
                transitionOutTarget = null;
                transitionOut = false;
                this.client.execute(t);
                return;
            }
        }

        // 7. Плавный выход
        if (exitAnimating) {
            long el = now - exitStartMs;
            float p = Math.min(1f, el / (float) EXIT_ANIM_MS);
            int fadeA = (int) (easeOutQuart(p) * 255f);
            context.fill(0, 0, this.width, this.height, fadeA << 24);
            if (p >= 1f) {
                this.client.scheduleStop();
            }
        }
    }

    private void renderHelloStage(DrawContext context, int mouseX, int mouseY) {
        // Чистый черный фон (#000000), чтобы GIF полностью сливался с экраном
        context.fill(0, 0, this.width, this.height, 0xFF000000);
        context.draw();

        int a = Math.max(0, Math.min(255, (int) (helloAlpha * 255f)));

        // 1. Отрисовка GIF кадра
        Identifier frame = HELLO_GIF.getFrame();
        if (frame != null && helloAlpha > 0.01f) {
            float drawW = Math.min(this.width * 0.30f, 220f);
            float drawH = drawW * ((float) HELLO_GIF.getHeight() / (float) HELLO_GIF.getWidth());
            float cx = this.width / 2f - drawW / 2f;
            float cy = this.height / 2f - drawH / 2f;

            drawTexQuad(context, frame, cx, cy, drawW, drawH, 0f, 0f, 1f, 1f, (a << 24) | 0xFFFFFF);
            context.draw();
        } else if (helloAlpha > 0.01f) {
            // Эстетичная надпись "hello." шрифтом MSDF
            String helloText = "hello.";
            float helloSize = 22.0f;
            float hw = getFont().getWidth(helloText, helloSize);
            float hx = this.width / 2f - hw / 2f;
            float hy = this.height / 2f - helloSize / 2f;

            getFont().draw(context, helloText, hx, hy, helloSize, (a << 24) | 0xFFFFFF);
        }

        // 2. Тонкая ненавязчивая подсказка о пропуске
        if (helloAlpha > 0.2f) {
            long now = System.currentTimeMillis();
            float pulse = 0.4f + 0.3f * (float) Math.sin(now / 280.0);
            int hintA = (int) (pulse * helloAlpha * 200f);
            if (hintA > 4) {
                String skipHint = tr("Нажмите любую клавишу для пропуска", "Press any key to skip");
                float hintW = getFont().getWidth(skipHint, SIZE_HINT);
                getFont().draw(context, skipHint,
                        this.width / 2f - hintW / 2f, this.height - 32f,
                        SIZE_HINT, (hintA << 24) | 0x666677);
            }
        }
    }

    // ─── Верхняя панель ───────────────────────────────────────────────────────
    private void renderHeader(DrawContext context, int mouseX, int mouseY, float anim) {
        float offsetY = (1.0f - anim) * -16f;
        int barY = (int) (14 + offsetY);

        // Логотип LEXORA слева
        float logoX = 22f;
        getFont().draw(context.getMatrices(), "LEXORA", logoX, barY + 3f, SIZE_LOGO, adjustAlpha(COL_TEXT_WHITE, anim));

        // Разделитель и бейдж CLIENT
        float dividerX = logoX + getFont().getWidth("LEXORA", SIZE_LOGO) + 8f;
        context.fill((int) dividerX, barY + 4, (int) dividerX + 1, barY + 16, adjustAlpha(0x33FFFFFF, anim));

        float clientTagX = dividerX + 8f;
        getFont().draw(context.getMatrices(), "CLIENT 1.21.4", clientTagX, barY + 5.5f, SIZE_DESC, adjustAlpha(0x88FFFFFF, anim));

        // Профиль игрока справа
        String currentName = (this.client != null && this.client.getSession() != null)
                ? this.client.getSession().getUsername() : "Player";
        float nameW = getFont().getWidth(currentName, SIZE_DESC);
        int profW = (int) (nameW + 48);
        int profH = 24;
        int profX = this.width - profW - 22;
        int profY = barY + 1;

        boolean isHoverProfile = mouseX >= profX && mouseX <= profX + profW && mouseY >= profY && mouseY <= profY + profH && !inAccountManager;
        profileHoverAnim += ((isHoverProfile ? 1f : 0f) - profileHoverAnim) * 0.18f;

        int top = blendColors(0xDC282830, 0xDC383848, profileHoverAnim);
        int bottom = blendColors(0xDC18181F, 0xDC22222E, profileHoverAnim);
        RoundedRectShader.drawVerticalGradient(context, profX, profY, profW, profH, 5f, adjustAlpha(top, anim), adjustAlpha(bottom, anim));
        int borderA = (int) (45 + profileHoverAnim * 75);
        RoundedRectShader.drawOutline(context, profX, profY, profW, profH, 5f, 0.6f, adjustAlpha((borderA << 24) | 0xFFFFFF, anim));

        drawPlayerHead(context, getAccountSkin(currentName), profX + 4, profY + 4, 16, anim);

        int nameCol = blendColors(0xEBA0A0AC, 0xFFF0F0F5, profileHoverAnim);
        getFont().draw(context.getMatrices(), currentName, profX + 24, profY + 7.5f, SIZE_DESC, adjustAlpha(nameCol, anim));

        // Индикатор онлайн
        RoundedRectShader.draw(context, profX + profW - 10, profY + 10, 4, 4, 2f, adjustAlpha(0xFF22C55E, anim));
    }

    // ─── Центральный хаб в стиле Velocity (с сохранением картинок) ─────────────
    private void renderCentralHub(DrawContext context, int mouseX, int mouseY, float anim,
                                  float langAlpha, float langOffsetY, float globeSpinAngle) {
        float enterSlide = (1.0f - anim) * 20f;
        float cx = this.width / 2f;

        // Размеры главного блока
        float cardW = 142f;
        float cardH = 76f;
        float gapCards = 8f;
        float totalRowW = cardW * 2f + gapCards; // 292 px
        float startX = cx - totalRowW / 2f;

        float hubTotalH = 34f + 16f + cardH + 6f + 22f; // ~154 px
        float baseY = (this.height - hubTotalH) / 2f + enterSlide;

        // 1. Логотип: "lexora." в стиле Velocity (минималистичный строчный жирный шрифт с точкой)
        float logoY = baseY;
        String logoText = "lexora.";
        float logoW = getFont().getWidth(logoText, 34f);
        getFont().draw(context.getMatrices(), logoText, cx - logoW / 2f, logoY, 34f, adjustAlpha(COL_TEXT_WHITE, anim));

        // Подзаголовок: время и дата с плавной анимацией при смене языка
        String subTitle = getCurrentTimeString() + "  •  " + getCurrentDateString();
        float subW = getFont().getWidth(subTitle, SIZE_DATE);
        context.getMatrices().push();
        context.getMatrices().translate(0, langOffsetY * 0.5f, 0);
        getFont().draw(context.getMatrices(), subTitle, cx - subW / 2f, logoY + 36f, SIZE_DATE, adjustAlpha(COL_TEXT_MUTED, anim * langAlpha));
        context.getMatrices().pop();

        // 2. Ряд 1: две главные кнопки-карточки (Одиночная игра и Сетевая игра)
        float cardsY = logoY + 52f;

        // Одиночная игра (odinoch.png)
        float spX = startX;
        boolean spHover = mouseX >= spX && mouseX <= spX + cardW && mouseY >= cardsY && mouseY <= cardsY + cardH && !inAccountManager && !transitionOut;
        spHoverAnim += ((spHover ? 1f : 0f) - spHoverAnim) * 0.18f;
        renderImageCard(context, spX, cardsY, cardW, cardH, ODINOCH_TEX,
                tr("Одиночная игра", "Singleplayer"),
                spHoverAnim, anim, langAlpha, langOffsetY);

        // Сетевая игра (seteva.png, БЕЗ посторонних цветных кругов)
        float mpX = startX + cardW + gapCards;
        boolean mpHover = mouseX >= mpX && mouseX <= mpX + cardW && mouseY >= cardsY && mouseY <= cardsY + cardH && !inAccountManager && !transitionOut;
        mpHoverAnim += ((mpHover ? 1f : 0f) - mpHoverAnim) * 0.18f;
        renderImageCard(context, mpX, cardsY, cardW, cardH, SETEVA_TEX,
                tr("Сетевая игра", "Multiplayer"),
                mpHoverAnim, anim, langAlpha, langOffsetY);

        // 3. Ряд 2: второстепенные кнопки (в стиле Velocity pills)
        float row2Y = cardsY + cardH + 6f;
        renderVelocityPillRow(context, startX, row2Y, totalRowW, mouseX, mouseY, anim, langAlpha, langOffsetY, globeSpinAngle);

        // Нижняя строка версии
        String ver = "Lexora Visuals Client • 1.21.4 Fabric";
        float vw = getFont().getWidth(ver, SIZE_TINY);
        getFont().draw(context.getMatrices(), ver, cx - vw / 2f, this.height - 12f, SIZE_TINY, adjustAlpha(0x35FFFFFF, anim));
    }

    // ─── Карточка режима игры в стиле Velocity (с картинкой) ─────────────────
    private void renderImageCard(DrawContext context, float x, float y, float w, float h, Identifier texture,
                                 String label, float hover, float anim, float langAlpha, float langOffsetY) {
        float r = 5f;
        float lift = hover * 1.5f;
        float cy = y - lift;

        // Картинка с мягким скруглением углов
        int imgTint = blendColors(0xFFB8B8C8, 0xFFFFFFFF, hover);
        RoundedRectShader.drawTextured(context, texture, x, cy, w, h, r, 0f, 0f, 1f, 1f, adjustAlpha(imgTint, anim));

        // Темный вертикальный градиент снизу в стиле Velocity
        int topFade = 0x0018181F;
        int botFade = 0xDC18181F;
        RoundedRectShader.drawVerticalGradient(context, x, cy + h - 36f, w, 36f, r, adjustAlpha(topFade, anim), adjustAlpha(botFade, anim));

        // Тонкая окантовка (Velocity outline: 45 resting, до 120 на hover)
        int borderA = (int) (45 + hover * 75);
        int borderCol = (borderA << 24) | 0xFFFFFF;
        RoundedRectShader.drawOutline(context, x, cy, w, h, r, 0.6f, adjustAlpha(borderCol, anim));

        // Центрированный текст кнопки в стиле Velocity
        context.getMatrices().push();
        context.getMatrices().translate(0, langOffsetY, 0);

        int textCol = blendColors(0xEBA0A0AC, 0xFFF0F0F5, hover);
        float tw = getFont().getWidth(label, 9.5f);
        getFont().draw(context.getMatrices(), label, x + (w - tw) / 2f, cy + h - 16f, 9.5f, adjustAlpha(textCol, anim * langAlpha));

        context.getMatrices().pop();
    }

    // ─── Ряд второстепенных кнопок в стиле Velocity ───────────────────────────
    private void renderVelocityPillRow(DrawContext context, float rowX, float rowY, float rowW,
                                       int mouseX, int mouseY, float anim,
                                       float langAlpha, float langOffsetY, float globeSpinAngle) {
        float bh = 22f;
        float r = 5f;
        int btnCount = 5;
        float gap = 4f;
        float bw = (rowW - (btnCount - 1) * gap) / btnCount;

        String langLabel = isRu() ? "RU" : "EN";
        String[] labels = {
                tr("Настройки", "Settings"),
                tr("Моды", "Mods"),
                tr("Аккаунты", "Accounts"),
                langLabel,
                tr("Выход", "Exit")
        };

        for (int i = 0; i < btnCount; i++) {
            float bx = rowX + i * (bw + gap);
            float by = rowY;

            boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh && !inAccountManager && !transitionOut;
            dockHoverAnim[i] += ((hov ? 1f : 0f) - dockHoverAnim[i]) * 0.18f;
            float a = dockHoverAnim[i];

            // Градиентный фон пилла (Velocity: top rgba(40,40,48,220), bottom rgba(24,24,31,220))
            int top = blendColors(0xDC282830, 0xDC383848, a);
            int bottom = blendColors(0xDC18181F, 0xDC22222E, a);
            RoundedRectShader.drawVerticalGradient(context, bx, by, bw, bh, r, adjustAlpha(top, anim), adjustAlpha(bottom, anim));

            // Окантовка (Velocity: alpha = 45 + a * 75)
            int borderA = (int) (45 + a * 75);
            int borderCol = (borderA << 24) | 0xFFFFFF;
            RoundedRectShader.drawOutline(context, bx, by, bw, bh, r, 0.6f, adjustAlpha(borderCol, anim));

            // Цвет текста (Velocity: resting 160,160,172 -> hover 240,240,245)
            int textCol = blendColors(0xEBA0A0AC, 0xFFF0F0F5, a);

            if (i == 3) {
                // Кнопка языка: строго по центру кнопки с плавной анимацией перехода
                context.getMatrices().push();
                context.getMatrices().translate(0, langOffsetY, 0);
                float tw = getFont().getWidth(langLabel, SIZE_DESC);
                getFont().draw(context.getMatrices(), langLabel, bx + (bw - tw) / 2f, by + 6.5f, SIZE_DESC, adjustAlpha(textCol, anim * langAlpha));
                context.getMatrices().pop();
            } else {
                context.getMatrices().push();
                context.getMatrices().translate(0, langOffsetY * 0.7f, 0);
                float tw = getFont().getWidth(labels[i], SIZE_DESC);
                getFont().draw(context.getMatrices(), labels[i], bx + (bw - tw) / 2f, by + 6.5f, SIZE_DESC, adjustAlpha(textCol, anim * langAlpha));
                context.getMatrices().pop();
            }
        }
    }

    // ─── Модальный менеджер аккаунтов ─────────────────────────────────────────
    private void renderAccountManagerModal(DrawContext context, int mouseX, int mouseY) {
        float ap = accountPanelAlpha;
        if (ap <= 0.01f) return;

        context.fill(0, 0, this.width, this.height, (int) (ap * 170) << 24);

        int panelW = 540, panelH = 280;
        int startX = (this.width - panelW) / 2;
        int startY = (this.height - panelH) / 2;
        int panelOffsetY = (int) lerp(14, 0, ap);

        int curY = startY + panelOffsetY;
        RoundedRectShader.draw(context, startX, curY, panelW, panelH, 10f, adjustAlpha(COL_PANEL_BG, ap));
        RoundedRectShader.draw(context, startX, curY, panelW, panelH, 10f, adjustAlpha(0x20FFFFFF, ap * 0.4f));

        // Заголовок
        getFont().draw(context.getMatrices(), tr("МЕНЕДЖЕР АККАУНТОВ", "ACCOUNT MANAGER"), startX + 16, curY + 14, SIZE_TITLE, adjustAlpha(COL_TEXT_PRIMARY, ap));

        // Кнопка закрытия ✕
        int closeX = startX + panelW - 28, closeY = curY + 11;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 18;
        if (closeHov) {
            RoundedRectShader.draw(context, closeX, closeY, 18, 18, 9f, adjustAlpha(0x33FF4444, ap));
        }
        getFont().draw(context.getMatrices(), "✕", closeX + 5, closeY + 4.5f, SIZE_DESC, adjustAlpha(closeHov ? 0xFFFF6666 : COL_TEXT_SECONDARY, ap));

        context.fill(startX + 14, curY + 32, startX + panelW - 14, curY + 33, adjustAlpha(COL_DIVIDER, ap));

        // Левая панель
        int leftW = 175;
        int lx = startX + 14;
        int ly = curY + 40;
        int leftH = panelH - 52;

        RoundedRectShader.draw(context, lx, ly, leftW, leftH, 7f, adjustAlpha(COL_PANEL_BG2, ap));
        getFont().draw(context.getMatrices(), tr("ДОБАВИТЬ НИК", "ADD USERNAME"), lx + 12, ly + 11, SIZE_DESC, adjustAlpha(COL_TEXT_SECONDARY, ap));

        int fieldX = lx + 10, fieldY = ly + 26, fieldW = leftW - 20, fieldH = 21;
        RoundedRectShader.draw(context, fieldX, fieldY, fieldW, fieldH, 4f, adjustAlpha(inputFocused ? COL_FIELD_FOCUSED : COL_FIELD_BG, ap));
        RoundedRectShader.draw(context, fieldX, fieldY, fieldW, fieldH, 4f, adjustAlpha(inputFocused ? 0x66FFFFFF : 0x1EFFFFFF, ap * 0.5f));

        if (inputText.isEmpty() && !inputFocused) {
            getFont().draw(context.getMatrices(), tr("Введите ник...", "Enter username..."), fieldX + 7, fieldY + 6.5f, SIZE_DESC, adjustAlpha(COL_TEXT_MUTED, ap));
        } else {
            getFont().draw(context.getMatrices(), inputText, fieldX + 7, fieldY + 6.5f, SIZE_DESC, adjustAlpha(COL_TEXT_PRIMARY, ap));
            if (inputFocused && ((System.currentTimeMillis() / 500) % 2 == 0)) {
                String before = inputText.substring(0, Math.min(cursorPos, inputText.length()));
                float curDrawX = fieldX + 7 + getFont().getWidth(before, SIZE_DESC);
                context.fill((int) curDrawX, fieldY + 4, (int) curDrawX + 1, fieldY + fieldH - 4, adjustAlpha(COL_TEXT_WHITE, ap));
            }
        }

        // Кнопки менеджера
        String[] mgrLabels = {
                tr("Войти", "Login"),
                tr("Случайный", "Random"),
                tr("Удалить", "Delete"),
                tr("← Назад", "← Back")
        };

        for (int i = 0; i < 4; i++) {
            int bx = mgrBtnX[i];
            int by = mgrBtnY[i] + panelOffsetY;
            int bw = mgrBtnW[i];
            int bh = mgrBtnH[i];

            boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
            mgrBtnHover[i] += ((hov ? 1f : 0f) - mgrBtnHover[i]) * 0.18f;
            float a = mgrBtnHover[i];

            int top = blendColors(0xDC282830, 0xDC383848, a);
            int bottom = blendColors(0xDC18181F, 0xDC22222E, a);
            RoundedRectShader.drawVerticalGradient(context, bx, by, bw, bh, 5f, adjustAlpha(top, ap), adjustAlpha(bottom, ap));

            int borderA = (int) (45 + a * 75);
            RoundedRectShader.drawOutline(context, bx, by, bw, bh, 5f, 0.6f, adjustAlpha((borderA << 24) | 0xFFFFFF, ap));

            int textCol = blendColors(0xEBA0A0AC, 0xFFF0F0F5, a);
            String lbl = mgrLabels[i];
            float lw = getFont().getWidth(lbl, SIZE_HINT);
            getFont().draw(context.getMatrices(), lbl, bx + bw / 2f - lw / 2f, by + 6.5f, SIZE_HINT, adjustAlpha(textCol, ap));
        }

        // Правая панель
        int rx = lx + leftW + 10;
        int ry = ly;
        int rw = panelW - leftW - 38;
        int rh = leftH;

        RoundedRectShader.draw(context, rx, ry, rw, rh, 7f, adjustAlpha(COL_PANEL_BG2, ap));
        String listTitle = tr("СОХРАНЕННЫЕ АККАУНТЫ", "SAVED ACCOUNTS") + " (" + savedAccounts.size() + ")";
        getFont().draw(context.getMatrices(), listTitle, rx + 12, ry + 11, SIZE_DESC, adjustAlpha(COL_TEXT_SECONDARY, ap));

        int lx2 = rx + 8, ly2 = ry + 26, lw2 = rw - 16, lh2 = rh - 34;
        int itemH = 26;

        context.enableScissor(lx2, ly2, lx2 + lw2, ly2 + lh2);

        for (int i = 0; i < savedAccounts.size(); i++) {
            int itemY = ly2 + (i * (itemH + 3)) - (int) scrollYAnim;
            if (itemY + itemH < ly2 || itemY > ly2 + lh2) continue;

            String acc = savedAccounts.get(i);
            boolean hov = mouseX >= lx2 && mouseX <= lx2 + lw2 && mouseY >= itemY && mouseY <= itemY + itemH;
            boolean sel = acc.equals(selectedAccount);
            boolean delHov = mouseX >= lx2 + lw2 - 22 && mouseX <= lx2 + lw2 - 4 && mouseY >= itemY && mouseY <= itemY + itemH;

            int itemBg = sel ? COL_LIST_SELECTED : (hov ? COL_LIST_HOVER : COL_LIST_ITEM);
            RoundedRectShader.draw(context, lx2, itemY, lw2, itemH, 5f, adjustAlpha(itemBg, ap));

            drawPlayerHead(context, getAccountSkin(acc), lx2 + 5, itemY + 5, 16, ap);

            int textCol = sel ? COL_TEXT_WHITE : COL_TEXT_PRIMARY;
            getFont().draw(context.getMatrices(), acc, lx2 + 26, itemY + 8.5f, SIZE_DESC, adjustAlpha(textCol, ap));

            int crossCol = delHov ? 0xFFEE4444 : 0xFF555566;
            getFont().draw(context.getMatrices(), "✕", lx2 + lw2 - 14, itemY + 8.5f, SIZE_DESC, adjustAlpha(crossCol, ap));
        }

        context.disableScissor();
    }

    // ─── Обработка кликов мыши ────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (stage == MenuStage.HELLO) {
            // Не пропускать интро если оверлей загрузки ещё активен
            if (this.client != null && this.client.getOverlay() != null) return false;
            // Не пропускать если интро ещё не началось
            if (!helloStarted) return false;
            // Защита от случайных кликов: минимум 800мс
            if (System.currentTimeMillis() - stageStartTime < 800L) return true;
            skipIntroToMenu();
            return true;
        }

        // Если открыт менеджер аккаунтов
        if (inAccountManager && button == 0) {
            int panelW = 540, panelH = 280;
            int startX = (this.width - panelW) / 2;
            int startY = (this.height - panelH) / 2;

            // Кнопка закрытия ✕
            int closeX = startX + panelW - 28, closeY = startY + 11;
            if (mx >= closeX && mx <= closeX + 18 && my >= closeY && my <= closeY + 18) {
                closeAccountManager();
                return true;
            }

            int leftW = 175;
            int lx = startX + 14;
            int ly = startY + 40;

            for (int i = 0; i < 4; i++) {
                if (mx >= mgrBtnX[i] && mx <= mgrBtnX[i] + mgrBtnW[i]
                        && my >= mgrBtnY[i] && my <= mgrBtnY[i] + mgrBtnH[i]) {
                    switch (i) {
                        case 0 -> {
                            String n = inputText.trim();
                            if (!n.isEmpty()) loginAccount(n);
                        }
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

            // Поле ввода
            int fieldX = lx + 10, fieldY = ly + 26, fieldW = leftW - 20, fieldH = 21;
            inputFocused = (mx >= fieldX && mx <= fieldX + fieldW && my >= fieldY && my <= fieldY + fieldH);

            // Клик по списку
            int rx = lx + leftW + 10;
            int ry = ly;
            int rw = panelW - leftW - 38;
            int lx2 = rx + 8, ly2 = ry + 26, lw2 = rw - 16, lh2 = panelH - 52 - 34;
            int itemH = 26;

            if (mx >= lx2 && mx <= lx2 + lw2 && my >= ly2 && my <= ly2 + lh2) {
                int clickY = (int) (my - ly2 + scrollYAnim);
                int index = clickY / (itemH + 3);
                if (index >= 0 && index < savedAccounts.size()) {
                    String clicked = savedAccounts.get(index);
                    if (mx >= lx2 + lw2 - 22) {
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
            return true;
        }

        if (!inAccountManager && !transitionOut && !exitAnimating) {
            // 1. Клик по профилю игрока -> менеджер аккаунтов
            String currentName = (this.client != null && this.client.getSession() != null)
                    ? this.client.getSession().getUsername() : "Player";
            float nameW = getFont().getWidth(currentName, SIZE_DESC);
            int profW = (int) (nameW + 48);
            int profH = 24;
            int profX = this.width - profW - 22;
            int profY = 15;

            if (button == 0 && mx >= profX && mx <= profX + profW && my >= profY && my <= profY + profH) {
                openAccountManager();
                return true;
            }

            // 2. Карточки по центру
            float cx = this.width / 2f;
            float cardW = 142f;
            float cardH = 76f;
            float gapCards = 8f;
            float totalRowW = cardW * 2f + gapCards;
            float startX = cx - totalRowW / 2f;

            float hubTotalH = 34f + 16f + cardH + 6f + 22f;
            float baseY = (this.height - hubTotalH) / 2f;
            float logoY = baseY;
            float cardsY = logoY + 52f;

            if (button == 0) {
                // Одиночная игра
                float spX = startX;
                if (mx >= spX && mx <= spX + cardW && my >= cardsY && my <= cardsY + cardH) {
                    navigateTo(() -> this.client.setScreen(new SelectWorldScreen(this)));
                    return true;
                }

                // Сетевая игра
                float mpX = startX + cardW + gapCards;
                if (mx >= mpX && mx <= mpX + cardW && my >= cardsY && my <= cardsY + cardH) {
                    navigateTo(() -> this.client.setScreen(new MultiplayerScreen(this)));
                    return true;
                }
            }

            // 3. Ряд 2 второстепенных кнопок
            float row2Y = cardsY + cardH + 6f;
            float bh2 = 22f;
            int btnCount = 5;
            float gap2 = 4f;
            float bw2 = (totalRowW - (btnCount - 1) * gap2) / btnCount;

            for (int i = 0; i < btnCount; i++) {
                float bx = startX + i * (bw2 + gap2);
                float by = row2Y;

                if (mx >= bx && mx <= bx + bw2 && my >= by && my <= by + bh2) {
                    switch (i) {
                        case 0 -> {
                            if (button == 0) navigateTo(() -> this.client.setScreen(new OptionsScreen(this, this.client.options)));
                        }
                        case 1 -> {
                            if (button == 0) navigateTo(() -> this.client.setScreen(new ModernClickGui(this)));
                        }
                        case 2 -> {
                            if (button == 0) openAccountManager();
                        }
                        case 3 -> {
                            // Кнопка языка:
                            if (button == 1 || Screen.hasShiftDown()) {
                                // Правый клик или Shift+клик -> открывает экран языков Minecraft
                                navigateTo(() -> this.client.setScreen(new LanguageOptionsScreen(this, this.client.options, this.client.getLanguageManager())));
                            } else {
                                // Левый клик -> плавная анимация переключения RU <-> EN
                                triggerLanguageSwitchAnimation();
                            }
                        }
                        case 4 -> {
                            if (button == 0) triggerExitAnimation();
                        }
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (inAccountManager) {
            scrollYTarget -= (int) (vAmt * 24);
            if (scrollYTarget < 0) scrollYTarget = 0;
            int maxScroll = Math.max(0, savedAccounts.size() * 29 - 150);
            if (scrollYTarget > maxScroll) scrollYTarget = maxScroll;
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (inAccountManager && inputFocused) {
            if (inputText.length() < 16 && (Character.isLetterOrDigit(c) || c == '_')) {
                String before = inputText.substring(0, Math.min(cursorPos, inputText.length()));
                String after = inputText.substring(Math.min(cursorPos, inputText.length()));
                inputText = before + c + after;
                cursorPos++;
                typewriterTarget = inputText;
                typewriterPos = inputText.length();
            }
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (stage == MenuStage.HELLO) {
            // Не пропускать интро если оверлей загрузки ещё активен
            if (this.client != null && this.client.getOverlay() != null) return false;
            // Не пропускать если интро ещё не началось
            if (!helloStarted) return false;
            // Защита от случайных нажатий: минимум 800мс
            if (System.currentTimeMillis() - stageStartTime < 800L) return true;
            skipIntroToMenu();
            return true;
        }

        if (inAccountManager && inputFocused) {
            if (keyCode == 259 && !inputText.isEmpty() && cursorPos > 0) { // Backspace
                inputText = inputText.substring(0, cursorPos - 1)
                        + inputText.substring(Math.min(cursorPos, inputText.length()));
                cursorPos--;
                typewriterTarget = inputText;
                typewriterPos = inputText.length();
            }
            if (keyCode == 261 && cursorPos < inputText.length()) { // Delete
                inputText = inputText.substring(0, cursorPos) + inputText.substring(cursorPos + 1);
                typewriterTarget = inputText;
                typewriterPos = inputText.length();
            }
            if (keyCode == 263 && cursorPos > 0) cursorPos--; // Left
            if (keyCode == 262 && cursorPos < inputText.length()) cursorPos++; // Right
            if (keyCode == 257 || keyCode == 335) { // Enter
                String n = inputText.trim();
                if (!n.isEmpty()) loginAccount(n);
            }
            if (keyCode == 256) inputFocused = false; // Escape
            return true;
        }

        if (keyCode == 256 && inAccountManager) {
            closeAccountManager();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─── Вспомогательные методы рендеринга ────────────────────────────────────
    public static void drawPlayerHead(DrawContext ctx, Identifier skin, int x, int y, int size, float alpha) {
        int tint = adjustAlpha(0xFFFFFFFF, alpha);
        // Базовый слой лица
        drawTexQuad(ctx, skin, x, y, size, size, 0.125f, 0.125f, 0.25f, 0.25f, tint);
        // Верхний слой шапки / волос
        drawTexQuad(ctx, skin, x, y, size, size, 0.625f, 0.125f, 0.75f, 0.25f, tint);
    }

    public static void drawTexQuad(DrawContext ctx, Identifier tex,
                                   float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1, int color) {
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        ctx.draw();
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

    private String getCurrentTimeString() {
        LocalTime now = LocalTime.now();
        return String.format("%02d:%02d", now.getHour(), now.getMinute());
    }

    private String getCurrentDateString() {
        Locale loc = isRu() ? new Locale("ru") : Locale.ENGLISH;
        LocalDate now = LocalDate.now();
        return capitalize(now.getDayOfWeek().getDisplayName(TextStyle.FULL, loc))
                + ", " + now.getDayOfMonth() + " "
                + now.getMonth().getDisplayName(TextStyle.FULL, loc);
    }

    private String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int adjustAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeOutQuart(float x) {
        return 1f - (float) Math.pow(1f - x, 4f);
    }

    public static int blendColors(int c1, int c2, float r) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * r) << 24)
                | ((int) (r1 + (r2 - r1) * r) << 16)
                | ((int) (g1 + (g2 - g1) * r) << 8)
                | (int) (b1 + (b2 - b1) * r);
    }

    // ─── Совместимость со сторонними миксинами (MixinInventoryScreen) ─────────
    public static class LexoraButton extends net.minecraft.client.gui.widget.ClickableWidget {
        private final Runnable pressAction;
        private float hoverAnim = 0f;
        private float revealAlpha = 1f;

        public LexoraButton(int x, int y, int w, int h, String text, Runnable pressAction) {
            super(x, y, w, h, Text.literal(text));
            this.pressAction = pressAction;
        }

        public void setRevealAlpha(float a) {
            this.revealAlpha = Math.max(0f, Math.min(1f, a));
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder b) {}

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

            int bgRaw = blendColors(0xCC181818, 0xCC2E2E2E, hoverAnim);
            int bgA = (int) (((bgRaw >> 24) & 0xFF) * revealAlpha);
            int bgColor = (bgA << 24) | (bgRaw & 0xFFFFFF);

            RoundedRectShader.draw(ctx, getX(), getY(), width, height, 6f, bgColor);

            int txA = (int) (255 * revealAlpha);
            if (txA > 5) {
                int textColor = (txA << 24) | (blendColors(COL_TEXT_PRIMARY, COL_TEXT_WHITE, hoverAnim) & 0xFFFFFF);
                String t = getMessage().getString();
                float tw = getFont().getWidth(t, SIZE_HINT);
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
}