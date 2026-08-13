package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.modern.ModernTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.Locale;

public class TargetHudRenderer {

    // ─── Пропорции ────────────────────────────────────────────────────────────
    public static final int WIDTH  = 180;
    public static final int HEIGHT = 46;

    // Геометрия лица
    private static final float FACE_PAD  = 6f;
    private static final float FACE_SIZE = HEIGHT - (FACE_PAD * 2); // 26px

    // Правая колонка (текст и ХП)
    private static final float RIGHT_X  = FACE_PAD + FACE_SIZE + 8f; // 40px
    private static final float RIGHT_W  = WIDTH - RIGHT_X - 6f;      // 109px

    // Полоса здоровья (Уменьшена в 2 раза)
    private static final float BAR_H    = 3f;

    // Панель используемого предмета
    private static final int   USING_SIZE  = 24;
    private static final float USING_GAP   = 5f;

    // ─── MSDF Шрифт ───────────────────────────────────────────────────────────
    private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    private static MsdfFont msdfFont = null;

    private static final float FONT_NAME = 9.0f;
    private static final float FONT_INFO = 7.5f;

    private static MsdfFont font() {
        if (msdfFont == null) msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        return msdfFont;
    }

    private static void str(DrawContext ctx, String text, float x, float y, float size, int color) {
        if (text == null || text.isBlank()) return;
        font().draw(ctx.getMatrices(), text, x, y, size, color);
    }

    private static float strW(String text, float size) {
        if (text == null || text.isBlank()) return 0f;
        return font().getWidth(text, size);
    }

    // ─── Текстуры ──────────────────────────────────────────────────────────────
    private static final Identifier WHODARK_TEX = Identifier.of("lexoravisauls", "textures/gui/whodark.png");

    // Партиклы — варианты для тумблера "Target HUD Particles"
    private static final Identifier TEX_STAR    = Identifier.of("lexoravisauls", "textures/particle/star1.png");
    private static final Identifier TEX_SKULL   = Identifier.of("lexoravisauls", "textures/particle/ded1.png");
    private static final Identifier TEX_BUCKS   = Identifier.of("lexoravisauls", "textures/particle/bucks1.png");
    private static final Identifier TEX_SNOW    = Identifier.of("lexoravisauls", "textures/particle/snownew1.png");
    private static final Identifier TEX_BLAST   = Identifier.of("lexoravisauls", "textures/particle/snowblast1.png");
    private static final Identifier TEX_BRICH   = Identifier.of("lexoravisauls", "textures/particle/snowbrich1.png");
    private static final Identifier TEX_CORE    = Identifier.of("lexoravisauls", "textures/particle/core1.png");
    private static final Identifier TEX_SHOW    = Identifier.of("lexoravisauls", "textures/particle/show1.png");
    private static final Identifier TEX_SNOWBAG = Identifier.of("lexoravisauls", "textures/particle/snowbag1.png");
    private static final Identifier TEX_GENSHIN = Identifier.of("lexoravisauls", "textures/particle/genshin.png");
    private static final Identifier TEX_HEART   = Identifier.of("lexoravisauls", "textures/particle/heart1.png");

    // Иконка стрелочки для поля выбора партикла (сглаженная, не пиксельная)
    private static final Identifier TEX_ARROW   = Identifier.of("lexoravisauls", "textures/gui/text.png");

    // ─── Типы партиклов ──────────────────────────────────────────────────────
    public enum ParticleType {
        STAR   ("Star",    TEX_STAR,    true),
        SKULL  ("Skull",   TEX_SKULL,   true),
        BUCKS  ("Bucks",   TEX_BUCKS,   false),
        SNOW   ("Snow",    TEX_SNOW,    true),
        BLAST  ("Blast",   TEX_BLAST,   true),
        BIRCH  ("Birch",   TEX_BRICH,   true),
        CORE   ("Core",    TEX_CORE,    true),
        SHOW   ("Show",    TEX_SHOW,    true),
        SNOWBAG("Snowbag", TEX_SNOWBAG, true),
        GENSHIN("Genshin", TEX_GENSHIN, false),
        HEART  ("Heart",   TEX_HEART,   false);

        public final String     label;
        public final Identifier texture;
        public final boolean    tinted; // true = красится в цвет темы, false = свои цвета текстуры

        ParticleType(String label, Identifier texture, boolean tinted) {
            this.label = label;
            this.texture = texture;
            this.tinted = tinted;
        }

        public static ParticleType byIndex(int idx) {
            ParticleType[] v = values();
            if (idx < 0 || idx >= v.length) return STAR;
            return v[idx];
        }
    }

    /** Текущий выбранный тип партикла. Хранится как индекс во float-настройках LexoraGui. */
    public static ParticleType getSelectedParticle() {
        float idx = LexoraGui.numSettings.getOrDefault("Target HUD Particle Type", 0f);
        return ParticleType.byIndex(Math.round(idx));
    }

    // ─── Состояния ─────────────────────────────────────────────────────────────
    private static LivingEntity currentTarget = null;
    private static LivingEntity lastTarget    = null;

    private static float hudAlpha       = 0f;
    private static float healthAnim     = 1f;
    private static float absAnim        = 0f;  // Анимация поглощения
    private static float armorAnim      = 0f;  // Анимация брони
    private static float smoothedHealth = -1f;

    // ЖЕЛЕ-АНИМАЦИЯ
    private static float animScale    = 0.0f;
    private static float animVelocity = 0.0f;

    // ─── Задержка перед исчезновением (linger) ─────────────────────────────────
    private static long       lastSeenMs = 0L;
    private static final long LINGER_MS  = 1000L; // держим HUD ещё 1 сек после потери цели с прицела

    // Частицы
    private static final Random            RNG         = new Random();
    private static final List<HitParticle> PARTICLES   = new ArrayList<>();
    private static int  lastHurtId     = Integer.MIN_VALUE;
    private static int  lastHurtTime   = 0;
    private static long lastParticleMs = 0L;

    // Анимация использования предмета
    private static float usingItemAnim    = 0f;
    private static net.minecraft.item.Item lastUsingItem = Items.AIR;

    // ─── Плавное расстояние ────────────────────────────────────────────────────
    private static float smoothedDistance = 0f;

    // ─── Плавное покраснение при ударе ────────────────────────────────────────
    private static float smoothedHurt = 0f;

    // ─── Круговые полоски (справа от лица, вместо горизонтальной полосы) ──────
    private static final float RING_OUTER  = 16f;   // Сделали круг еще больше
    private static final float RING_THICK  = 3.5f;  // Сделали линию круга еще шире
    private static final float ABS_THICK   = 1.4f;  // (Больше не используется)
    private static final float ARMOR_THICK = 1.2f;  // (Больше не используется)

    // ─── Остров брони + экипировки (снизу, отдельный блок) ───────────────────
    private static final float EQUIP_ICON_SIZE  = 11f;   // было 14, уменьшено
    private static final float EQUIP_ISLAND_GAP = 3f;    // было 4

    // ─── Селектор партикла (виджет для панели настроек) ───────────────────────
    private static final float PARTICLE_ROW_H    = 20f;
    private static final float PARTICLE_OPTION_H = 16f;
    private static float       particleListAnim  = 0f; // 0..1, плавное раскрытие/закрытие списка

    private static final class HitParticle {
        float x, y, vx, vy, size, life, maxLife, angle, spin;
        HitParticle(float x, float y, float vx, float vy,
                    float size, float life, float angle, float spin) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.size = size; this.life = this.maxLife = life;
            this.angle = angle; this.spin = spin;
        }
    }

    private TargetHudRenderer() {}

    // =========================================================================
    //  ГЛАВНЫЙ РЕНДЕР
    // =========================================================================

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        if (!LexoraGui.moduleStates.getOrDefault("Target HUD", true)) return;

        int baseX = HudManager.targetX == -1 ? 150 : HudManager.targetX;
        int baseY = HudManager.targetY == -1 ? 150 : HudManager.targetY;

        LivingEntity hovered = getHoveredTarget(mc);
        boolean preview = mc.currentScreen instanceof LexoraGui
                || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;

        currentTarget = hovered;
        if (hovered != null) {
            lastTarget = hovered;
            lastSeenMs = System.currentTimeMillis();
        }
        if (preview && currentTarget == null) lastTarget = mc.player;

        // Держим HUD видимым ещё LINGER_MS после того, как цель пропала с прицела
        boolean lingering = !preview && currentTarget == null && lastTarget != null
                && (System.currentTimeMillis() - lastSeenMs) < LINGER_MS;

        boolean visible = currentTarget != null || preview || lingering;

        hudAlpha += ((visible ? 1f : 0f) - hudAlpha) * 0.025f;

        // ── ЖЕЛЕ-АНИМАЦИЯ ПОЯВЛЕНИЯ (замедлено ещё ~в 4 раза поверх прошлого замедления) ──
        float targetScale = visible ? 1.0f : 0.0f;
        float tension = visible ? 0.035f : 0.02f;
        float dampening = visible ? 0.55f : 0.40f;

        animVelocity += (targetScale - animScale) * tension;
        animVelocity *= dampening;
        animScale += animVelocity;

        if (!visible && hudAlpha < 0.02f && Math.abs(animScale) < 0.02f) {
            currentTarget = null; lastTarget = null;
            PARTICLES.clear();
            smoothedHealth = -1f; usingItemAnim = 0f; absAnim = 0f; armorAnim = 0f; lastUsingItem = Items.AIR;
            smoothedDistance = 0f;
            smoothedHurt = 0f;
            animScale = 0f;
            lastSeenMs = 0L;
            return;
        }

        LivingEntity display = currentTarget != null ? currentTarget : lastTarget;
        if (display == null) return;

        int alpha = MathHelper.clamp((int)(hudAlpha * 255f), 0, 255);

        // ── Имя ──────────────────────────────────────────────────────────────
        String name = display.getName().getString();
        if (LexoraGui.moduleStates.getOrDefault("Streamer Mode", false)
                && LexoraGui.moduleStates.getOrDefault("Hide Name", true)
                && display.getUuid().equals(mc.player.getUuid()))
            name = "Protected";

        // ── Здоровье и Поглощение ─────────────────────────────────────────────
        float targetHp = Math.max(0f, display.getHealth());
        float maxHp    = Math.max(1f, display.getMaxHealth());
        float targetAbs = Math.max(0f, display.getAbsorptionAmount());

        // Плавное ХП
        if (smoothedHealth < 0f || Math.abs(smoothedHealth - targetHp) < 0.05f) {
            smoothedHealth = targetHp;
        } else {
            float factor = targetHp < smoothedHealth ? 0.15f : 0.07f;
            smoothedHealth = MathHelper.lerp(factor, smoothedHealth, targetHp);
        }

        float targetFraction = MathHelper.clamp(smoothedHealth / maxHp, 0f, 1f);
        if (healthAnim < 0f || Math.abs(healthAnim - targetFraction) < 0.001f) {
            healthAnim = targetFraction;
        } else {
            float barFactor = targetFraction < healthAnim ? 0.12f : 0.06f;
            healthAnim = MathHelper.lerp(barFactor, healthAnim, targetFraction);
        }

        // Плавное поглощение
        float absFraction = MathHelper.clamp(targetAbs / maxHp, 0f, 1f);
        if (Math.abs(absAnim - absFraction) < 0.001f) {
            absAnim = absFraction;
        } else {
            float absFactor = absFraction < absAnim ? 0.12f : 0.06f;
            absAnim = MathHelper.lerp(absFactor, absAnim, absFraction);
        }

        // Плавная броня
        float armorFraction = MathHelper.clamp(display.getArmor() / 20f, 0f, 1f);
        if (Math.abs(armorAnim - armorFraction) < 0.001f) {
            armorAnim = armorFraction;
        } else {
            armorAnim = MathHelper.lerp(0.09f, armorAnim, armorFraction);
        }

        // Плавное расстояние
        float realDist = (mc.player != null) ? display.distanceTo(mc.player) : 0f;
        smoothedDistance = MathHelper.lerp(0.08f, smoothedDistance, realDist);

        // ── Анимация предмета ──────────────────────────────────────────────
        boolean isUsing = display.isUsingItem();
        if (isUsing && !display.getActiveItem().isEmpty()) {
            lastUsingItem = display.getActiveItem().getItem();
        }
        float usingTarget = isUsing ? 1f : 0f;
        usingItemAnim += (usingTarget - usingItemAnim) * (isUsing ? 0.18f : 0.22f);
        if (Math.abs(usingItemAnim - usingTarget) < 0.01f) usingItemAnim = usingTarget;

        // ── Текстура лица ──────────────────────────────────────────────────────
        Identifier faceTex  = WHODARK_TEX;
        boolean    hasAlpha = false;
        if (display instanceof AbstractClientPlayerEntity p) {
            faceTex = p.getSkinTextures().texture();
            hasAlpha = true;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        MatrixStack ms = context.getMatrices();
        ms.push();

        float userScale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
        float displayScale = Math.max(0.001f, animScale);
        float finalScale = userScale * displayScale;

        float pivotX = baseX + WIDTH  / 2f;
        float pivotY = baseY + HEIGHT / 2f;

        ms.translate(pivotX, pivotY, 500f);
        ms.scale(finalScale, finalScale, 1f);
        ms.translate(-pivotX, -pivotY, 0f);

        // ── Отрисовка ────────────────────────────────────────────────────────
        drawPanel(context, baseX, baseY, alpha);

        if (usingItemAnim > 0.02f && !lastUsingItem.equals(Items.AIR)) {
            drawUsingItem(context, ms, baseX, baseY, alpha, display);
        }
        float fx = baseX + FACE_PAD;
        float fy = baseY + FACE_PAD;

        // Плавное покраснение: быстрое (но не мгновенное) нарастание, плавное затухание.
        // "Damage Tint" выключен -> rawHurt всегда 0, и smoothedHurt просто плавно уйдёт в 0
        // (а не будет продолжать краснеть, как было раньше — это был баг).
        boolean damageTintOn = LexoraGui.moduleStates.getOrDefault("Damage Tint", true);
        float rawHurt = (damageTintOn && display.hurtTime > 0) ? 1f : 0f;
        if (rawHurt > smoothedHurt) {
            smoothedHurt = MathHelper.lerp(0.6f, smoothedHurt, rawHurt); // быстрый, но сглаженный подъём
        } else {
            smoothedHurt = MathHelper.lerp(0.12f, smoothedHurt, 0f); // плавное затухание
        }

        drawFaceSlot(context, faceTex, fx, fy, alpha, hasAlpha, smoothedHurt);

        updateParticles(display, fx + 2f, fy + 2f, FACE_SIZE - 4f);
        drawParticles(context, alpha);

        // Имя + расстояние (в текстовой зоне сразу после лица)
        drawInfo(context, baseX, baseY, alpha, name, smoothedHealth, targetAbs, smoothedDistance);

        // Кольцо HP/поглощения/брони — справа, после текста
        float ringCx = baseX + WIDTH - RING_OUTER - 8f;
        float ringCy = baseY + HEIGHT / 2f;
        drawRingBars(context, ringCx, ringCy, alpha, healthAnim, absAnim, armorAnim, finalScale);

        // Остров экипировки + брони снизу (с блюром, анимируется быстрее чем основной HUD)
        // Используем animScale^2 — при исчезновении пропадает быстрее
        float islandScale = animScale * animScale;
        drawEquipmentIsland(context, ms, baseX, baseY, alpha, display, islandScale);

        context.draw();
        ms.pop();
        RenderSystem.enableDepthTest();
    }

    // =========================================================================
    //  ПАНЕЛЬ
    // =========================================================================

    private static void drawPanel(DrawContext ctx, int x, int y, int alpha) {
        boolean blurEnabled = LexoraGui.moduleStates.getOrDefault("Target HUD Blur", true);

        if (blurEnabled) {
            int bgColor = (a(alpha * 0.70f) << 24) | 0x141416;
            try {
                ctx.draw(); // <--- ДОБАВИТЬ ЭТО
                com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(ctx, x, y, WIDTH, HEIGHT, 5f, 15f, bgColor);
            } catch (Throwable ignored) {
                rr(ctx, x, y, WIDTH, HEIGHT, 5f, bgColor);
            }
        } else {
            int solidColor = (alpha << 24) | 0x000000;
            rr(ctx, x, y, WIDTH, HEIGHT, 5f, solidColor);
        }
    }

    // =========================================================================
    //  ЛИЦО (Увеличена краснота)
    // =========================================================================

    private static void drawFaceSlot(DrawContext ctx, Identifier tex,
                                     float x, float y, int alpha,
                                     boolean playerSkin, float hurtPct) {
        rr(ctx, x, y, FACE_SIZE, FACE_SIZE, 5f,
                (a(alpha * 0.80f) << 24) | 0x1A1A26);

        drawFace(ctx, tex, x, y, FACE_SIZE, playerSkin, 5f,
                (alpha << 24) | 0xFFFFFF, hurtPct);
    }

    private static void drawFace(DrawContext ctx, Identifier tex,
                                 float x, float y, float size,
                                 boolean playerSkin, float radius,
                                 int color, float hurtPct) {
        if (((color >>> 24) & 0xFF) <= 3) return;

        if (playerSkin) {
            RoundedRectShader.drawTextured(ctx, tex,
                    x, y, size, size, radius,
                    8/64f, 8/64f, 16/64f, 16/64f, color);
            RoundedRectShader.drawTextured(ctx, tex,
                    x, y, size, size, radius,
                    40/64f, 8/64f, 48/64f, 16/64f, color);
        } else {
            RoundedRectShader.drawTextured(ctx, tex,
                    x, y, size, size, radius,
                    0f, 0f, 1f, 1f, color);
        }

        if (hurtPct > 0.02f) {
            int baseA  = (color >>> 24) & 0xFF;
            // Усиление красноты в 3 раза
            int ha     = (int) Math.min(baseA, (hurtPct * 3.0f * baseA));
            RoundedRectShader.drawTextured(ctx, tex,
                    x, y, size, size, radius,
                    8/64f, 8/64f, 16/64f, 16/64f, (ha << 24) | 0xFF0000);
        }
    }

    // =========================================================================
    //  ТЕКСТ (имя + расстояние)
    // =========================================================================

    private static void drawInfo(DrawContext ctx, int x, int y, int alpha, String name,
                                 float hp, float abs, float dist) {
        // Текст от RIGHT_X, ширина до кольца (кольцо занимает правые ~36px)
        float textX  = x + RIGHT_X;
        float availW = WIDTH - RIGHT_X - RING_OUTER * 2f - 14f;

        String dispName = clip(name, availW, FONT_NAME);
        str(ctx, dispName, textX, y + 9f, FONT_NAME, (alpha << 24) | 0xFFFFFF);

        // Расстояние — плавный текст
        String distText = String.format(java.util.Locale.US, "%.1fm", dist);
        int dimA = (a(alpha * 0.75f) << 24) | 0xAABBCC;
        str(ctx, distText, textX, y + 22f, FONT_INFO, dimA);

        // Если есть поглощение — маленький золотой текст
        if (abs > 0.1f) {
            String absText = String.format(java.util.Locale.US, "+%.0f\u2764", abs);
            int absA = (a(alpha * 0.85f) << 24) | 0xFBBF24;
            str(ctx, absText, textX, y + 32f, FONT_INFO, absA);
        }
    }

    // =========================================================================
    //  КРУГОВЫЕ ПОЛОСКИ HP И ПОГЛОЩЕНИЯ
    // =========================================================================

    private static void drawRingBars(DrawContext ctx,
                                     float cx, float cy, int alpha,
                                     float hpFrac, float absFrac, float armorFrac,
                                     float scale) {

        float roHp = RING_OUTER;
        float riHp = roHp - RING_THICK;

        // ── 1. Фон кольца (темный) ───────────────────────────────────────────
        ArcShader.drawRing(ctx, cx, cy, roHp, riHp,
                (a(alpha * 0.30f) << 24) | 0x1E1E1E);

        // ── 2. Основное HP ───────────────────────────────────────────────────
        if (hpFrac > 0.005f) {
            int hpColor = getHealthColor(hpFrac);
            ArcShader.drawArcFraction(ctx, cx, cy, roHp, riHp,
                    hpFrac, (alpha << 24) | hpColor);
        }

        // ── 3. Кольцо поглощения (ПОВЕРХ HP, с теми же радиусами) ────────────
        if (absFrac > 0.005f) {
            ArcShader.drawArcFraction(ctx, cx, cy, roHp, riHp,
                    absFrac, (alpha << 24) | 0xFBBF24);
        }

        // ── 4. Текст HP в центре кольца ───────────────────────────────────────
        LivingEntity disp = currentTarget != null ? currentTarget : lastTarget;
        if (disp != null) {
            float hp = disp.getHealth();

            // Если здоровье целое (20.0), пишем "20", если с долями (15.6) — пишем "15.6"
            String hpTxt = (hp == (int) hp)
                    ? String.valueOf((int) hp)
                    : String.format(java.util.Locale.US, "%.1f", hp);

            // Немного увеличиваем размер шрифта, так как круг стал больше
            float  fSize   = FONT_INFO + 2.5f;
            float  tw      = strW(hpTxt, fSize);
            int    hpColor = getHealthColor(hpFrac);

            // Идеальное центрирование внутри кольца
            str(ctx, hpTxt,
                    cx - tw / 2f,
                    cy - fSize / 2f + 0.5f, // +0.5f для точной визуальной центровки MSDF
                    fSize,
                    (alpha << 24) | hpColor);
        }
    }

    // drawArcFill удалён — заменён на ArcShader.drawArcFraction / drawRing
    // Один SDF draw call вместо сотен pill-сегментов.

    // =========================================================================
    //  ОСТРОВ ЭКИПИРОВКИ + БРОНИ (снизу)
    // =========================================================================

    private static void drawEquipmentIsland(DrawContext ctx, MatrixStack ms,
                                            int baseX, int baseY, int alpha,
                                            LivingEntity display, float islandScale) {
        if (islandScale < 0.02f) return;

        // Собираем предметы: mainHand + offHand + 4 слота брони (без прочности!)
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        ItemStack mainHand = display.getMainHandStack();
        ItemStack offHand  = display.getOffHandStack();

        // Броня: helmet, chestplate, leggings, boots
        for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                net.minecraft.entity.EquipmentSlot.HEAD,
                net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS,
                net.minecraft.entity.EquipmentSlot.FEET}) {
            ItemStack s = display.getEquippedStack(slot);
            if (!s.isEmpty()) items.add(s);
        }
        if (!mainHand.isEmpty()) items.add(mainHand);
        if (!offHand.isEmpty())  items.add(offHand);

        if (items.isEmpty()) return;

        float iconS   = EQUIP_ICON_SIZE;
        float pad     = 4f;   // уменьшен padding
        float gap     = 2f;   // уменьшен gap
        float islandW = pad * 2f + items.size() * iconS + (items.size() - 1) * gap;
        float islandH = iconS + pad * 2f;
        float islandX = baseX + (WIDTH - islandW) / 2f;
        float islandY = baseY + HEIGHT + EQUIP_ISLAND_GAP;

        // Анимация масштаба острова — появляется/исчезает быстрее основного HUD
        float pivIslandX = islandX + islandW / 2f;
        float pivIslandY = islandY + islandH / 2f;
        int   islandAlpha = MathHelper.clamp((int)(alpha * islandScale), 0, 255);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(pivIslandX, pivIslandY, 0f);
        ctx.getMatrices().scale(islandScale, islandScale, 1f);
        ctx.getMatrices().translate(-pivIslandX, -pivIslandY, 0f);

        // Фон с блюром (такой же как основной HUD)
        // Фон с блюром (такой же как основной HUD)
        boolean blurEnabled = LexoraGui.moduleStates.getOrDefault("Target HUD Blur", true);
        int bgColor = (a(islandAlpha * 0.70f) << 24) | 0x141416;
        if (blurEnabled) {
            try {
                ctx.draw(); // <--- ДОБАВИТЬ ЭТО
                com.lexoravisauls.client.gui.modern.ModernGuiRender.drawLiquidGlass(
                        ctx, (int) islandX, (int) islandY,
                        (int) islandW, (int) islandH,
                        5f, 12f, bgColor);
            } catch (Throwable ignored) {
                rr(ctx, islandX, islandY, islandW, islandH, 5f, bgColor);
            }
        } else {
            rr(ctx, islandX, islandY, islandW, islandH, 5f, bgColor);
        }

        // Иконки предметов (БЕЗ счётчика прочности)
        for (int i = 0; i < items.size(); i++) {
            float ix = islandX + pad + i * (iconS + gap);
            float iy = islandY + pad;

            ms.push();
            ms.translate(ix + iconS / 2f, iy + iconS / 2f, 200f);
            float sc = iconS / 16f;
            ms.scale(sc, sc, 1f);
            ms.translate(-8f, -8f, 0f);

            RenderSystem.enableDepthTest();
            ctx.drawItemWithoutEntity(items.get(i).copy(), 0, 0);
            ms.pop();
        }

        // Надпись "что держит в руке" (имя mainHand), аккуратно
        if (!mainHand.isEmpty()) {
            String itemName = mainHand.getName().getString();
            itemName = clip(itemName, islandW - pad * 2f, 6.5f);
            float tw = strW(itemName, 6.5f);
            str(ctx, itemName,
                    islandX + (islandW - tw) / 2f,
                    islandY + islandH + 2f,
                    6.5f,
                    (a(islandAlpha * 0.65f) << 24) | 0xCCCCCC);
        }

        ctx.getMatrices().pop(); // закрываем scale острова
    }

    private static int getHealthColor(float fraction) {
        fraction = MathHelper.clamp(fraction, 0f, 1f);
        if (fraction >= 0.5f) {
            return blendRgb(0xFACC15, 0x4ADE80, (fraction - 0.5f) * 2f); // Желтый -> Зеленый
        } else {
            return blendRgb(0xF87171, 0xFACC15, fraction * 2f); // Красный -> Желтый
        }
    }

    // =========================================================================
    //  ПАНЕЛЬ ИСПОЛЬЗУЕМОГО ПРЕДМЕТА
    // =========================================================================

    private static void drawUsingItem(DrawContext ctx, MatrixStack ms,
                                      int baseX, int baseY,
                                      int alpha, LivingEntity display) {
        float anim = usingItemAnim;

        float progress = 0f;
        if (display.isUsingItem() && !display.getActiveItem().isEmpty()) {
            int maxTick = getMaxUseTick(lastUsingItem);
            progress = maxTick > 0
                    ? (display.getItemUseTime() / (float) maxTick) * 360f
                    : 0f;
            progress = MathHelper.clamp(progress, 0f, 360f);
        }

        float fullOffset = USING_GAP + USING_SIZE;
        float px = baseX - fullOffset * anim;
        float py = baseY + 7f;

        int panelAlpha = (int)(alpha * anim);
        if (panelAlpha < 5) return;

        rr(ctx, px, py, USING_SIZE, USING_SIZE, 5f,
                (a(panelAlpha * 0.92f) << 24) | 0x000000);
        rr(ctx, px + 1f, py + 1f, USING_SIZE - 2f, USING_SIZE - 2f, 4f,
                (a(panelAlpha * 0.25f) << 24) | 0x1A1A28);

        if (progress > 1f) {
            drawArcProgress(ctx, px, py, USING_SIZE, USING_SIZE, progress, panelAlpha);
        }

        ItemStack itemStack = lastUsingItem.getDefaultStack();
        if (!itemStack.isEmpty()) {
            float iconScale = 0.75f * anim;
            float cx = px + USING_SIZE / 2f;
            float cy = py + USING_SIZE / 2f;
            ms.push();
            ms.translate(cx, cy, 150f);
            ms.scale(iconScale, iconScale, 1f);
            ms.translate(-8f, -8f, 0f);
            RenderSystem.enableDepthTest();
            if (panelAlpha > 5) ctx.drawItem(itemStack, 0, 0);
            ms.pop();
        }
    }

    private static void drawArcProgress(DrawContext ctx,
                                        float x, float y, float w, float h,
                                        float degrees, int alpha) {
        float cx   = x + w / 2f;
        float cy   = y + h / 2f;
        float ro   = w / 2f - 1.5f;
        float ri   = ro - 2.5f;
        int   segs = Math.max(1, (int)(degrees / 3f));
        float step = degrees / segs;

        int themeColor = ModernTheme.accent(0f) & 0xFFFFFF;
        int finalColor = (alpha << 24) | themeColor;

        for (int i = 0; i < segs; i++) {
            float a0 = (float) Math.toRadians(i * step - 90f);
            float a1 = (float) Math.toRadians((i + 1) * step - 90f);

            float ox0 = cx + (float) Math.cos(a0) * ro;
            float oy0 = cy + (float) Math.sin(a0) * ro;
            float ox1 = cx + (float) Math.cos(a1) * ro;
            float oy1 = cy + (float) Math.sin(a1) * ro;
            float ix0 = cx + (float) Math.cos(a0) * ri;
            float iy0 = cy + (float) Math.sin(a0) * ri;
            float ix1 = cx + (float) Math.cos(a1) * ri;
            float iy1 = cy + (float) Math.sin(a1) * ri;

            int minX = (int) Math.floor(Math.min(Math.min(ox0, ox1), Math.min(ix0, ix1)));
            int maxX = (int) Math.ceil (Math.max(Math.max(ox0, ox1), Math.max(ix0, ix1)));
            int minY = (int) Math.floor(Math.min(Math.min(oy0, oy1), Math.min(iy0, iy1)));
            int maxY = (int) Math.ceil (Math.max(Math.max(oy0, oy1), Math.max(iy0, iy1)));

            for (int px = minX; px <= maxX; px++) {
                for (int py = minY; py <= maxY; py++) {
                    float pcx = px + 0.5f - cx;
                    float pcy = py + 0.5f - cy;
                    float dist = (float) Math.sqrt(pcx * pcx + pcy * pcy);
                    if (dist < ri || dist > ro) continue;
                    float ang = (float)(Math.toDegrees(Math.atan2(pcy, pcx)) + 90f);
                    if (ang < 0f) ang += 360f;
                    if (ang > degrees) continue;
                    ctx.fill(px, py, px + 1, py + 1, finalColor);
                }
            }
        }
    }

    private static int getMaxUseTick(net.minecraft.item.Item item) {
        if (item == Items.BOW || item == Items.CROSSBOW) return 72000;
        if (item == Items.SHIELD) return 72000;
        return 32;
    }

    // =========================================================================
    //  ЧАСТИЦЫ УДАРА
    // =========================================================================

    private static void updateParticles(LivingEntity entity, float fx, float fy, float fs) {
        long now = System.currentTimeMillis();
        if (lastParticleMs == 0L) lastParticleMs = now;
        float dt = Math.min(3f, (now - lastParticleMs) / 16.667f);
        lastParticleMs = now;

        for (int i = PARTICLES.size() - 1; i >= 0; i--) {
            HitParticle p = PARTICLES.get(i);
            p.x += p.vx * dt; p.y += p.vy * dt;
            p.vx *= Math.pow(0.965, dt);
            p.vy  = (float)(p.vy * Math.pow(0.975, dt) - 0.0065 * dt);
            p.angle += p.spin * dt;
            p.spin  *= Math.pow(0.992, dt);
            p.life  -= dt * 0.38f;
            if (p.life <= 0f) PARTICLES.remove(i);
        }

        int eid = entity != null ? entity.getId() : Integer.MIN_VALUE;
        int ht  = entity != null ? entity.hurtTime : 0;
        if (eid != lastHurtId) { lastHurtId = eid; lastHurtTime = ht; return; }

        if (LexoraGui.moduleStates.getOrDefault("Target HUD Particles", true)
                && entity != null && ht > 0 && ht > lastHurtTime)
            spawnParticles(fx, fy, fs, 8 + RNG.nextInt(4));
        lastHurtTime = ht;
    }

    private static void spawnParticles(float fx, float fy, float fs, int count) {
        float cx = fx + fs / 2f, cy = fy + fs / 2f;
        for (int i = 0; i < count; i++) {
            double a  = RNG.nextDouble() * Math.PI * 2;
            float  r  = 5f + RNG.nextFloat() * 8f;
            float  sp = 0.6f + RNG.nextFloat() * 0.95f;
            PARTICLES.add(new HitParticle(
                    cx + (float) Math.cos(a) * r,
                    cy + (float) Math.sin(a) * r,
                    (float) Math.cos(a) * sp,
                    (float) Math.sin(a) * sp - 0.10f - RNG.nextFloat() * 0.10f,
                    4.4f + RNG.nextFloat() * 3f,
                    18f  + RNG.nextFloat() * 10f,
                    RNG.nextFloat() * (float)(Math.PI * 2),
                    (RNG.nextFloat() * 0.24f + 0.08f) * (RNG.nextBoolean() ? 1f : -1f)
            ));
        }
    }

    private static void drawParticles(DrawContext ctx, int baseAlpha) {
        ParticleType type = getSelectedParticle();
        int rgb = type.tinted ? (ModernTheme.accent(0f) & 0xFFFFFF) : 0xFFFFFF;
        for (HitParticle p : PARTICLES) {
            float fade = (float) Math.pow(MathHelper.clamp(p.life / p.maxLife, 0f, 1f), 0.7f);
            int   al   = Math.min(180, (int)(baseAlpha * fade * 0.78f));
            if (al <= 4) continue;
            drawRotatedQuad(ctx, type.texture,
                    p.x - p.size / 2f, p.y - p.size / 2f, p.size, p.size,
                    0f, 0f, 1f, 1f, (al << 24) | rgb, p.angle);
        }
    }

    // =========================================================================
    //  СЕЛЕКТОР ТИПА ПАРТИКЛА (виджет для панели настроек Target HUD)
    // =========================================================================
    // К самому игровому HUD это не относится — это готовый виджет для ЭКРАНА
    // НАСТРОЕК, там же, где рисуются тумблеры "Target HUD Blur" / "Damage Tint".
    //
    //   renderParticleSelector(...)  — рисует поле + (если open) список вариантов
    //   hitTestParticleSelector(...) — обрабатывает клики по этому виджету:
    //        -2        клик по самому полю      → переключи свой флаг "open"
    //        -1        клик мимо списка (open)  → закрой список
    //        >= 0      индекс выбранного пункта → сохрани и закрой список
    //        MIN_VALUE клик мимо виджета вообще → передай обработку дальше

    public static void renderParticleSelector(DrawContext ctx, float x, float y, float width, boolean open,
                                              int mouseX, int mouseY) {
        ParticleType current = getSelectedParticle();

        // Поле текущего выбора
        rr(ctx, x, y, width, PARTICLE_ROW_H, 4f, 0xCC141416);
        str(ctx, current.label, x + 8f, y + PARTICLE_ROW_H / 2f - 3.5f, 7.5f, 0xFFFFFFFF);
        drawArrowIcon(ctx, x + width - 11f, y + PARTICLE_ROW_H / 2f, 9f, open, 0xFFAAAAAA);

        // Плавное раскрытие/закрытие списка
        particleListAnim += ((open ? 1f : 0f) - particleListAnim) * 0.14f;
        if (Math.abs(particleListAnim - (open ? 1f : 0f)) < 0.01f) particleListAnim = open ? 1f : 0f;
        if (particleListAnim < 0.01f) return;

        ParticleType[] all  = ParticleType.values();
        float listH  = all.length * PARTICLE_OPTION_H;
        int   accent = ModernTheme.accent(0f) & 0xFFFFFF;
        int   listA  = (int)(255 * particleListAnim); // альфа всего списка на время анимации

        // Список — самостоятельный элемент: сам решает, вниз ему открыться или вверх,
        // если снизу не хватает места на экране (не зависит от родительской панели).
        boolean upward = shouldOpenUpward(y);
        float   listY  = listOriginY(y, upward, listH);
        float   pivotY = upward ? (listY + listH) : listY; // растёт от края, ближнего к полю

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + width / 2f, pivotY, 0f);
        ctx.getMatrices().scale(1f, particleListAnim, 1f);
        ctx.getMatrices().translate(-(x + width / 2f), -pivotY, 0f);

        rr(ctx, x, listY, width, listH, 4f, (a(listA * 0.91f) << 24) | 0x101012);

        for (int i = 0; i < all.length; i++) {
            float   oy         = listY + i * PARTICLE_OPTION_H;
            boolean isSelected = all[i] == current;
            boolean isHovered  = !isSelected && inside(mouseX, mouseY, x, oy, width, PARTICLE_OPTION_H);

            if (isSelected) {
                rr(ctx, x + 2f, oy + 1f, width - 4f, PARTICLE_OPTION_H - 2f, 3f,
                        (a(listA * 0.32f) << 24) | accent);
            } else if (isHovered) {
                rr(ctx, x + 2f, oy + 1f, width - 4f, PARTICLE_OPTION_H - 2f, 3f,
                        (a(listA * 0.12f) << 24) | 0xFFFFFF);
            }

            str(ctx, all[i].label, x + 8f, oy + PARTICLE_OPTION_H / 2f - 3.5f, 7.5f,
                    (a(listA * (isSelected || isHovered ? 1f : 0.72f)) << 24)
                            | (isSelected ? 0xFFFFFF : 0xAAAAAA));
        }

        ctx.getMatrices().pop();
    }

    public static int hitTestParticleSelector(double mx, double my, float x, float y, float width, boolean open) {
        if (inside(mx, my, x, y, width, PARTICLE_ROW_H)) return -2;

        if (open) {
            ParticleType[] all  = ParticleType.values();
            float          listH = all.length * PARTICLE_OPTION_H;
            float          listY = listOriginY(y, shouldOpenUpward(y), listH);
            for (int i = 0; i < all.length; i++) {
                float oy = listY + i * PARTICLE_OPTION_H;
                if (inside(mx, my, x, oy, width, PARTICLE_OPTION_H)) return i;
            }
            return -1;
        }
        return Integer.MIN_VALUE;
    }

    /** Не влезает ли список вниз от поля — тогда его нужно открыть вверх. */
    private static boolean shouldOpenUpward(float fieldY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return false;
        float listH = ParticleType.values().length * PARTICLE_OPTION_H;
        float neededBottom = fieldY + PARTICLE_ROW_H + 2f + listH + 6f;
        return neededBottom > mc.getWindow().getScaledHeight();
    }

    private static float listOriginY(float fieldY, boolean upward, float listH) {
        return upward ? (fieldY - listH - 2f) : (fieldY + PARTICLE_ROW_H + 2f);
    }

    /** Полная высота виджета с учётом открытого списка — пригодится для раскладки панели. */
    public static float particleSelectorHeight(boolean open) {
        return open ? PARTICLE_ROW_H + 2f + ParticleType.values().length * PARTICLE_OPTION_H : PARTICLE_ROW_H;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static void drawArrowIcon(DrawContext ctx, float cx, float cy, float size, boolean open, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0f);
        if (open) ctx.getMatrices().multiply(new Quaternionf().rotateZ((float) Math.PI));
        ctx.getMatrices().translate(-size / 2f, -size / 2f, 0f);
        drawSmoothIcon(ctx, TEX_ARROW, 0f, 0f, size, size, color);
        ctx.getMatrices().pop();
    }

    // Отдельный от texQuad путь отрисовки — тут специально GL_LINEAR вместо GL_NEAREST,
    // чтобы иконка была гладкой, а не пиксельной (в отличие от лица/партиклов).
    private static void drawSmoothIcon(DrawContext ctx, Identifier tex,
                                       float x, float y, float w, float h, int color) {
        if (((color >>> 24) & 0xFF) <= 3) return;
        ctx.draw();

        float al = ((color >>> 24) & 0xFF) / 255f;
        float r  = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >>  8) & 0xFF) / 255f;
        float b  = ( color        & 0xFF) / 255f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, tex);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        Matrix4f      mat = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(mat, x,     y,     0f).texture(0f, 0f).color(r, g, b, al);
        buf.vertex(mat, x,     y + h, 0f).texture(0f, 1f).color(r, g, b, al);
        buf.vertex(mat, x + w, y + h, 0f).texture(1f, 1f).color(r, g, b, al);
        buf.vertex(mat, x + w, y,     0f).texture(1f, 0f).color(r, g, b, al);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // =========================================================================
    //  БАЗОВЫЙ РЕНДЕР
    // =========================================================================

    private static void rr(DrawContext ctx, float x, float y, float w, float h,
                           float r, int color) {
        if (w <= 0 || h <= 0) return;
        RoundedRectShader.draw(ctx, x, y, w, h, r, color);
    }

    private static void texQuad(DrawContext ctx, Identifier tex,
                                float x, float y, float w, float h,
                                float u0, float v0, float u1, float v1,
                                int color) {
        if (((color >>> 24) & 0xFF) <= 3) return;
        ctx.draw();

        float al = ((color >>> 24) & 0xFF) / 255f;
        float r  = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >>  8) & 0xFF) / 255f;
        float b  = ( color        & 0xFF) / 255f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, tex);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        Matrix4f      mat = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(mat, x,     y,     0f).texture(u0, v0).color(r, g, b, al);
        buf.vertex(mat, x,     y + h, 0f).texture(u0, v1).color(r, g, b, al);
        buf.vertex(mat, x + w, y + h, 0f).texture(u1, v1).color(r, g, b, al);
        buf.vertex(mat, x + w, y,     0f).texture(u1, v0).color(r, g, b, al);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void drawRotatedQuad(DrawContext ctx, Identifier tex,
                                        float x, float y, float w, float h,
                                        float u0, float v0, float u1, float v1,
                                        int color, float angle) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + w / 2f, y + h / 2f, 0f);
        ctx.getMatrices().multiply(new Quaternionf().rotateZ(angle));
        ctx.getMatrices().translate(-w / 2f, -h / 2f, 0f);
        texQuad(ctx, tex, 0f, 0f, w, h, u0, v0, u1, v1, color);
        ctx.getMatrices().pop();
    }

    // =========================================================================
    //  УТИЛИТЫ
    // =========================================================================

    private static LivingEntity getHoveredTarget(MinecraftClient mc) {
        if (mc.crosshairTarget == null
                || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return null;
        Entity e = ((EntityHitResult) mc.crosshairTarget).getEntity();
        if (!(e instanceof LivingEntity le)) return null;
        if (mc.player != null && le.distanceTo(mc.player) > 30f) return null;
        return le;
    }

    private static String clip(String text, float maxW, float size) {
        if (strW(text, size) <= maxW) return text;
        String r = text;
        while (!r.isEmpty()) {
            if (strW(r + "…", size) <= maxW) return r + "…";
            r = r.substring(0, r.length() - 1);
        }
        return "…";
    }

    private static int blendRgb(int c1, int c2, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r  = (int)(((c1>>16)&0xFF) + (((c2>>16)&0xFF)-((c1>>16)&0xFF))*t);
        int g  = (int)(((c1>> 8)&0xFF) + (((c2>> 8)&0xFF)-((c1>> 8)&0xFF))*t);
        int b  = (int)(( c1     &0xFF) + (( c2     &0xFF)-( c1     &0xFF))*t);
        return (r<<16)|(g<<8)|b;
    }

    private static int a(float v) { return MathHelper.clamp((int) v, 0, 255); }
}