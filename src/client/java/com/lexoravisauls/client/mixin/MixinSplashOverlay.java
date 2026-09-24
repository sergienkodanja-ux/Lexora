package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.events.RoundedRectShader;
import com.lexoravisauls.client.gui.MsdfFont;
import com.lexoravisauls.client.gui.main_menu.SmokeBackgroundShader;
import com.lexoravisauls.client.gui.modern.GuiLocalization;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public abstract class MixinSplashOverlay {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private Consumer<Optional<Throwable>> exceptionHandler;
    @Shadow @Final private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    @Unique private static final Identifier FONT_TEX  = Identifier.of("lexoravisauls", "msdf_data/font.png");
    @Unique private static final Identifier FONT_JSON = Identifier.of("lexoravisauls", "msdf_data/font.json");
    @Unique private static MsdfFont msdfFont = null;
    @Unique private static int reloadCounter = 0;
    @Unique private static int selectedTipIndex = -1;

    @Unique
    private static final String[] TIPS_RU = {
            "Нажмите RSHIFT, чтобы открыть ClickGUI и настроить визуалы под себя.",
            "Вы можете свободно перемещать и масштабировать любые модули в HUD.",
            "TargetHUD отображает точное здоровье, броню и активные эффекты цели.",
            "Используйте F3 + T в любой момент для мгновенной перезагрузки текстур и звуков.",
            "В менеджере аккаунтов можно быстро переключать и сохранять свои профили.",
            "Модуль FreeLook позволяет оглядываться вокруг на 360° без изменения направления.",
            "GPS-модуль поможет ставить метки координат и всегда находить дорогу к базе.",
            "Все интерфейсы и модули Lexora оптимизированы для максимального FPS и плавности."
    };

    @Unique
    private static final String[] TIPS_EN = {
            "Press RSHIFT to open ClickGUI and customize visuals to your liking.",
            "You can freely move, snap, and resize any module in the HUD editor.",
            "TargetHUD displays precise health, armor durability, and active potion effects.",
            "Press F3 + T at any time to instantly reload resource packs and sounds.",
            "Save and switch between your player profiles in the Account Manager.",
            "FreeLook lets you view your surroundings in 360° without altering movement direction.",
            "Use the GPS module to mark important coordinates and track distances in real-time.",
            "All Lexora shaders and modules are optimized for peak FPS and minimum latency."
    };

    @Unique
    private static boolean isRu() {
        try {
            return GuiLocalization.getLanguage() == GuiLocalization.Language.RU;
        } catch (Throwable ignored) {
            return true;
        }
    }

    @Unique
    private static String tr(String ru, String en) {
        return isRu() ? ru : en;
    }

    @Unique
    private static int adjustAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0xFFFFFF);
    }

    @Unique
    private static MsdfFont getMsdfFont() {
        if (msdfFont == null) {
            msdfFont = new MsdfFont(FONT_TEX, FONT_JSON);
        }
        return msdfFont;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // 1-я загрузка при запуске — стандартная ванильная загрузка Майнкрафта (Mojang Studios)
        if (!this.reloading && reloadCounter == 0) {
            return;
        }

        // 2-я загрузка и последующие (перезагрузка ресурс-паков и т.д.) — НАША КАСТОМНАЯ!
        ci.cancel();

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        long now = Util.getMeasuringTimeMs();

        if (this.reloadStartTime == -1L) {
            this.reloadStartTime = now;
        }

        if (selectedTipIndex == -1) {
            selectedTipIndex = new Random().nextInt(TIPS_RU.length);
        }

        float completeElapsed = this.reloadCompleteTime > -1L ? (float)(now - this.reloadCompleteTime) / 1000.0F : -1.0F;
        float startElapsed = this.reloadStartTime > -1L ? (float)(now - this.reloadStartTime) / 500.0F : -1.0F;

        // Завершение загрузки ресурсов
        if (this.reloadCompleteTime == -1L && this.reload.isComplete() && startElapsed >= 2.0F) {
            try {
                this.reload.throwException();
                this.exceptionHandler.accept(Optional.empty());
            } catch (Throwable throwable) {
                this.exceptionHandler.accept(Optional.of(throwable));
            }
            this.reloadCompleteTime = Util.getMeasuringTimeMs();
            if (this.client.currentScreen != null) {
                this.client.currentScreen.init(this.client, width, height);
            }
        }

        // Закрытие оверлея
        if (completeElapsed >= 0.5F) {
            reloadCounter++;
            selectedTipIndex = -1;
            this.client.setOverlay(null);
            return;
        }

        // Opacity
        float opacity;
        if (this.reloadCompleteTime > -1L) {
            this.progress = 1.0F;
            opacity = 1.0F - MathHelper.clamp(completeElapsed / 0.5F, 0.0F, 1.0F);
        } else {
            opacity = MathHelper.clamp(startElapsed, 0.0F, 1.0F);
        }

        // Плавный прогресс
        float targetProgress = this.reload.getProgress();
        this.progress = MathHelper.clamp(this.progress * 0.90F + targetProgress * 0.10F, 0.0F, 1.0F);

        // Отрисовка нашей кастомной загрузки
        renderCustomSplash(context, width, height, opacity, mouseX, mouseY);
    }

    @Unique
    private void renderCustomSplash(DrawContext context, int width, int height, float opacity, int mouseX, int mouseY) {
        int a = Math.max(0, Math.min(255, (int)(opacity * 255f)));
        if (a <= 0) return;

        long now = Util.getMeasuringTimeMs();

        // 1. Анимированный фоновый шейдер дыма (или мягкий темный фон при сбое)
        try {
            SmokeBackgroundShader.render(context, width, height, mouseX, mouseY, opacity);
            context.fill(0, 0, width, height, (int)(a * 0.45f) << 24);
        } catch (Throwable t) {
            context.fill(0, 0, width, height, (a << 24) | 0x07080E);
        }

        // Мягкий затемняющий виньетинг сверху и снизу в точности как в главном меню
        int topVignette = (int)(a * 0.50f) << 24;
        context.fillGradient(0, 0, width, 55, topVignette | 0x050508, 0x00050508);
        int botVignette = (int)(a * 0.65f) << 24;
        context.fillGradient(0, height - 65, width, height, 0x00050508, botVignette | 0x050508);

        MsdfFont font = getMsdfFont();

        // 2. Верхняя панель (Header в стиле главного меню)
        try {
            if (font != null) {
                // Логотип LEXORA слева
                float logoHeadX = 22f;
                float barY = 14f;
                font.draw(context, "LEXORA", logoHeadX, barY + 3f, 11.0f, adjustAlpha(0xFFFFFFFF, opacity));

                // Разделитель и бейдж CLIENT
                float divX = logoHeadX + font.getWidth("LEXORA", 11.0f) + 8f;
                context.fill((int)divX, (int)barY + 4, (int)divX + 1, (int)barY + 16, adjustAlpha(0x33FFFFFF, opacity));

                font.draw(context, "CLIENT 1.21.4", divX + 8f, barY + 5.5f, 7.5f, adjustAlpha(0x88FFFFFF, opacity));

                // Бейдж статуса справа вверху (в стиле пилла профиля из главного меню)
                String statusBadge = tr("ПЕРЕЗАГРУЗКА РЕСУРСОВ", "RELOADING RESOURCES");
                float textW = font.getWidth(statusBadge, 7.5f);
                float badgeW = textW + 34f;
                float badgeH = 24f;
                float badgeX = width - badgeW - 22f;
                float badgeY = barY + 1f;

                // Градиентный фон пилла (top rgba(40,40,48,220), bottom rgba(24,24,31,220))
                RoundedRectShader.drawVerticalGradient(context, badgeX, badgeY, badgeW, badgeH, 5f,
                        adjustAlpha(0xDC282830, opacity), adjustAlpha(0xDC18181F, opacity));
                // Тонкая окантовка (0.6f, alpha 45)
                RoundedRectShader.drawOutline(context, badgeX, badgeY, badgeW, badgeH, 5f, 0.6f,
                        adjustAlpha((45 << 24) | 0xFFFFFF, opacity));

                // Пульсирующая зеленая точка-индикатор
                float pulse = 0.6f + 0.4f * (float)Math.sin(now / 220.0);
                int dotAlpha = (int)(a * pulse);
                RoundedRectShader.draw(context, badgeX + 10f, badgeY + 10f, 4f, 4f, 2f, (dotAlpha << 24) | 0x22C55E);

                font.draw(context, statusBadge, badgeX + 20f, badgeY + 7.5f, 7.5f, adjustAlpha(0xFFEBEBF0, opacity));
            }
        } catch (Throwable ignored) {}

        // 3. Центральный блок: "lexora." + подзаголовок + карточка загрузки в стиле Velocity
        float cx = width / 2f;
        float centerY = height / 2f;

        // Логотип: "lexora." в стиле Velocity
        float logoY = centerY - 65f;
        try {
            if (font != null) {
                String logoText = "lexora.";
                float logoW = font.getWidth(logoText, 34f);
                font.draw(context, logoText, cx - logoW / 2f, logoY, 34f, adjustAlpha(0xFFFFFFFF, opacity));

                String subTitle = tr("Синхронизация и компиляция ресурсов", "Syncing & loading client resources");
                float subW = font.getWidth(subTitle, 7.5f);
                font.draw(context, subTitle, cx - subW / 2f, logoY + 36f, 7.5f, adjustAlpha(0x88FFFFFF, opacity));
            }
        } catch (Throwable ignored) {}

        float p = MathHelper.clamp(this.progress, 0.0f, 1.0f);

        // Главная карточка прогресса (плашка) в стиле Velocity
        float cardW = 380f;
        float cardH = 76f;
        float cardX = cx - cardW / 2f;
        float cardY = logoY + 50f;
        float cardR = 6f;

        // Градиентный фон карточки в стиле Velocity (0xDC282830 -> 0xDC18181F)
        RoundedRectShader.drawVerticalGradient(context, cardX, cardY, cardW, cardH, cardR,
                adjustAlpha(0xEE282832, opacity), adjustAlpha(0xEE161720, opacity));
        // Тонкая элегантная окантовка (45 alpha, 0.6f)
        RoundedRectShader.drawOutline(context, cardX, cardY, cardW, cardH, cardR, 0.6f,
                adjustAlpha((45 << 24) | 0xFFFFFF, opacity));

        // Внутри карточки:
        // 1. Верхний ряд: плашка-тег этапа слева, процент справа
        String stageDesc;
        if (p < 0.30f) {
            stageDesc = tr("Подготовка текстур и моделей...", "Preparing textures & models...");
        } else if (p < 0.70f) {
            stageDesc = tr("Синхронизация шейдеров и ассетов...", "Syncing shaders & assets...");
        } else if (p < 1.0f) {
            stageDesc = tr("Финализация графического пайплайна...", "Finalizing render pipeline...");
        } else {
            stageDesc = tr("Загрузка успешно завершена!", "Reload complete!");
        }

        try {
            if (font != null) {
                // Тег этапа (мини-плашка в стиле меню)
                float stageTagW = font.getWidth(stageDesc, 7.5f) + 16f;
                float stageTagH = 18f;
                float stageTagX = cardX + 14f;
                float stageTagY = cardY + 10f;

                RoundedRectShader.draw(context, stageTagX, stageTagY, stageTagW, stageTagH, 4f,
                        adjustAlpha(0x44303248, opacity));
                RoundedRectShader.drawOutline(context, stageTagX, stageTagY, stageTagW, stageTagH, 4f, 0.5f,
                        adjustAlpha((35 << 24) | 0xFFFFFF, opacity));
                font.draw(context, stageDesc, stageTagX + 8f, stageTagY + 4.5f, 7.5f, adjustAlpha(0xFFE2E8F0, opacity));

                // Процент в правом верхнем углу карточки
                int percent = Math.min(100, Math.max(0, Math.round(p * 100f)));
                String percentStr = percent + "%";
                float pctW = font.getWidth(percentStr, 8.5f);
                font.draw(context, percentStr, cardX + cardW - 14f - pctW, cardY + 14f, 8.5f, adjustAlpha(0xFFFFFFFF, opacity));
            }
        } catch (Throwable ignored) {}

        // 2. Средний ряд: Полоска прогресса (аккуратный закругленный трек и заливка)
        float barX = cardX + 14f;
        float barY = cardY + 36f;
        float barW = cardW - 28f;
        float barH = 6f;
        float barR = 3f;

        // Трек полоски
        RoundedRectShader.draw(context, barX, barY, barW, barH, barR, adjustAlpha(0x5512131A, opacity));
        RoundedRectShader.drawOutline(context, barX, barY, barW, barH, barR, 0.5f, adjustAlpha(0x28FFFFFF, opacity));

        // Заполнение полоски прогресса градиентом
        float fillW = barW * p;
        if (fillW > 0.5f) {
            float fillR = Math.min(barR, fillW / 2f);
            RoundedRectShader.drawGradient(context, barX, barY, fillW, barH, fillR,
                    adjustAlpha(0xFFFFFFFF, opacity), adjustAlpha(0xFFE0E7FF, opacity));
        }

        // 3. Нижний ряд: подсказка о статусе слева, бейдж готовности справа
        try {
            if (font != null) {
                String subInfo = p >= 1.0f
                        ? tr("Готово к игре", "Ready to play")
                        : tr("Пожалуйста, подождите завершения...", "Please wait for completion...");
                font.draw(context, subInfo, cardX + 14f, cardY + 50f, 7.5f, adjustAlpha(0xEBA0A0AC, opacity));

                String statusTag = p >= 1.0f ? "COMPLETE" : "PROCESSING";
                int tagCol = p >= 1.0f ? 0xFF4ADE80 : 0xFF94A3B8;
                float tagW = font.getWidth(statusTag, 7.0f);
                font.draw(context, statusTag, cardX + cardW - 14f - tagW, cardY + 50.5f, 7.0f, adjustAlpha(tagCol, opacity));
            }
        } catch (Throwable ignored) {}

        // 4. Нижняя карточка с подсказками (в точном стиле Velocity-пилла из главного меню)
        try {
            if (font != null) {
                int tipIdx = (int)(((now - this.reloadStartTime) / 4500L + (selectedTipIndex >= 0 ? selectedTipIndex : 0)) % TIPS_RU.length);
                if (tipIdx < 0) tipIdx = 0;
                String tipText = isRu() ? TIPS_RU[tipIdx] : TIPS_EN[tipIdx];

                float tipCardW = Math.min(width - 48f, 540f);
                float tipCardH = 32f;
                float tipCardX = cx - tipCardW / 2f;
                float tipCardY = height - 50f;
                float tipCardR = 5f;

                // Градиентный фон пилла в стиле главного меню
                RoundedRectShader.drawVerticalGradient(context, tipCardX, tipCardY, tipCardW, tipCardH, tipCardR,
                        adjustAlpha(0xDC282830, opacity), adjustAlpha(0xDC18181F, opacity));
                RoundedRectShader.drawOutline(context, tipCardX, tipCardY, tipCardW, tipCardH, tipCardR, 0.6f,
                        adjustAlpha((45 << 24) | 0xFFFFFF, opacity));

                // Тег "СОВЕТ" / "TIP" слева внутри карточки
                String tagStr = tr("СОВЕТ", "TIP");
                float tagW = font.getWidth(tagStr, 7.0f) + 12f;
                float tagH = 18f;
                float tagX = tipCardX + 8f;
                float tagY = tipCardY + 7f;

                RoundedRectShader.draw(context, tagX, tagY, tagW, tagH, 3.5f, adjustAlpha(0x444F46E5, opacity));
                RoundedRectShader.drawOutline(context, tagX, tagY, tagW, tagH, 3.5f, 0.5f, adjustAlpha(0x35818CF8, opacity));
                font.draw(context, tagStr, tagX + 6f, tagY + 4.5f, 7.0f, adjustAlpha(0xFFC7D2FE, opacity));

                // Текст подсказки
                float tipTextX = tagX + tagW + 8f;
                font.draw(context, tipText, tipTextX, tipCardY + 11.5f, 7.5f, adjustAlpha(0xFFE2E8F0, opacity));

                // Клавиша [ F3 + T ] справа
                String keyBadge = "[ F3 + T ]";
                float keyW = font.getWidth(keyBadge, 7.0f) + 10f;
                float keyH = 16f;
                float keyX = tipCardX + tipCardW - keyW - 8f;
                float keyY = tipCardY + 8f;

                RoundedRectShader.draw(context, keyX, keyY, keyW, keyH, 3f, adjustAlpha(0x331E1E28, opacity));
                RoundedRectShader.drawOutline(context, keyX, keyY, keyW, keyH, 3f, 0.5f, adjustAlpha(0x22FFFFFF, opacity));
                font.draw(context, keyBadge, keyX + 5f, keyY + 3.5f, 7.0f, adjustAlpha(0xFF8A8A9C, opacity));
            }
        } catch (Throwable ignored) {}

        // 5. Нижняя строка версии в точности как в главном меню
        try {
            if (font != null) {
                String ver = "Lexora Visuals Client • 1.21.4 Fabric";
                float vw = font.getWidth(ver, 6.5f);
                font.draw(context, ver, cx - vw / 2f, height - 12f, 6.5f, adjustAlpha(0x35FFFFFF, opacity));
            }
        } catch (Throwable ignored) {}
    }
}
