package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.VMAnimations;
import com.lexoravisauls.client.utils.HandShaderCopy;
import com.lexoravisauls.client.utils.HandShaderRenderer;
import com.lexoravisauls.client.utils.LexoraShaders;
import com.lexoravisauls.client.utils.ShaderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class MixinHeldItemRenderer {

    @Shadow
    public abstract void renderItem(
            LivingEntity entity,
            ItemStack item,
            ModelTransformationMode modelTransformationMode,
            boolean leftHanded,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    );

    @Unique
    private static boolean lexora$handShaderActive = false;

    @Unique
    private static long lexora$targetHitUntilMs = 0L;

    @Unique
    private static float lexora$lastMainHandSwingProgress = 0.0f;

    @Unique
    private static final long lexora$TARGET_HIT_HOLD_MS = 2500L;

    // ── Динамика поворота камеры и шлейфа для шейдера огня/дыма ─────
    @Unique
    private static float lexora$lastYaw = 0.0f;

    @Unique
    private static float lexora$lastPitch = 0.0f;

    @Unique
    private static float lexora$camVelX = 0.0f;

    @Unique
    private static float lexora$camVelY = 0.0f;

    @Unique
    private static float lexora$trailDecay = 0.0f;

    @Unique
    private static float lexora$trailDirX = 0.0f;

    @Unique
    private static float lexora$trailDirY = 0.0f;

    /*
     * Мини-фикс бага, когда предмет на 1 кадр становится огромным
     * при выборе/смене предмета.
     */
    @Unique
    private static final long lexora$ITEM_POP_FIX_MS = 140L;

    @Unique
    private static final float lexora$ITEM_POP_FIX_EQUIP_LIMIT = 0.12f;

    @Unique
    private Item lexora$lastMainItem = null;

    @Unique
    private Item lexora$lastOffItem = null;

    @Unique
    private boolean lexora$mainItemSeen = false;

    @Unique
    private boolean lexora$offItemSeen = false;

    @Unique
    private long lexora$mainItemPopFixUntilMs = 0L;

    @Unique
    private long lexora$offItemPopFixUntilMs = 0L;

    @Unique
    private boolean lexora$isVmEnabled() {
        return ClientData.moduleStates.getOrDefault("View Model",
                LexoraGui.moduleStates.getOrDefault("View Model", false))
                || ClientData.moduleStates.getOrDefault("ViewModel",
                LexoraGui.moduleStates.getOrDefault("ViewModel", false));
    }

    @Unique
    private boolean lexora$isStandardMode() {
        String mode = ClientData.modeSettings.getOrDefault("VM Anim",
                LexoraGui.modeSettings.getOrDefault("VM Anim", "Standard")).trim();
        return mode.equalsIgnoreCase("Standard") || mode.equalsIgnoreCase("Стандарт");
    }

    @Unique
    private boolean lexora$isTargetHitMode() {
        return ClientData.moduleStates.getOrDefault("VM Target Hit",
                LexoraGui.moduleStates.getOrDefault("VM Target Hit", false))
                || ClientData.moduleStates.getOrDefault("Target Hit VM",
                LexoraGui.moduleStates.getOrDefault("Target Hit VM", false))
                || ClientData.moduleStates.getOrDefault("Target Hit Anim",
                LexoraGui.moduleStates.getOrDefault("Target Hit Anim", false));
    }

    @Unique
    private Arm lexora$getArm(AbstractClientPlayerEntity player, Hand hand) {
        return hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    @Unique
    private Hand lexora$getHandForArm(Arm arm) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            return Hand.MAIN_HAND;
        }

        return arm == mc.player.getMainArm() ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    @Unique
    private void lexora$updateItemPopFix(Hand hand, ItemStack stack) {
        Item currentItem = stack.isEmpty() ? null : stack.getItem();
        long now = System.currentTimeMillis();

        if (hand == Hand.MAIN_HAND) {
            if (!lexora$mainItemSeen) {
                lexora$mainItemSeen = true;
                lexora$lastMainItem = currentItem;

                if (currentItem != null) {
                    lexora$mainItemPopFixUntilMs = now + lexora$ITEM_POP_FIX_MS;
                }

                return;
            }

            if (lexora$lastMainItem != currentItem) {
                lexora$lastMainItem = currentItem;
                lexora$mainItemPopFixUntilMs = now + lexora$ITEM_POP_FIX_MS;
            }

            return;
        }

        if (!lexora$offItemSeen) {
            lexora$offItemSeen = true;
            lexora$lastOffItem = currentItem;

            if (currentItem != null) {
                lexora$offItemPopFixUntilMs = now + lexora$ITEM_POP_FIX_MS;
            }

            return;
        }

        if (lexora$lastOffItem != currentItem) {
            lexora$lastOffItem = currentItem;
            lexora$offItemPopFixUntilMs = now + lexora$ITEM_POP_FIX_MS;
        }
    }

    @Unique
    private boolean lexora$shouldBlockOneFramePop(Hand hand, float equipProgress) {
        long now = System.currentTimeMillis();
        long until = hand == Hand.MAIN_HAND ? lexora$mainItemPopFixUntilMs : lexora$offItemPopFixUntilMs;

        return now < until && equipProgress <= lexora$ITEM_POP_FIX_EQUIP_LIMIT;
    }

    @Unique
    private boolean lexora$hasHoveredLivingTarget() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.player == null || mc.crosshairTarget == null) {
            return false;
        }

        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return false;
        }

        Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();

        if (!(entity instanceof LivingEntity living)) {
            return false;
        }

        if (entity == mc.player) {
            return false;
        }

        if (!living.isAlive()) {
            return false;
        }

        return living.distanceTo(mc.player) <= 30.0f;
    }

    @Unique
    private boolean lexora$shouldForceVanillaPose(AbstractClientPlayerEntity player, Hand hand, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (player.isUsingItem() && player.getActiveHand() == hand) {
            UseAction action = stack.getUseAction();

            return action == UseAction.BOW
                    || action == UseAction.CROSSBOW
                    || action == UseAction.SPEAR
                    || action == UseAction.EAT
                    || action == UseAction.DRINK
                    || action == UseAction.BLOCK;
        }

        return false;
    }

    @Unique
    private boolean lexora$shouldApplyOffsets(
            AbstractClientPlayerEntity player,
            Hand hand,
            ItemStack item,
            Arm arm
    ) {
        if (player == null || item.isEmpty() || !lexora$isVmEnabled()) {
            return false;
        }

        if (lexora$shouldForceVanillaPose(player, hand, item)) {
            return false;
        }

        return true;
    }

    @Unique
    private boolean lexora$shouldApplyCustomAnimation(
            AbstractClientPlayerEntity player,
            Hand hand,
            ItemStack item,
            Arm arm
    ) {
        if (player == null || item.isEmpty() || !lexora$isVmEnabled() || lexora$isStandardMode()) {
            return false;
        }

        if (arm != player.getMainArm()) {
            return false;
        }

        if (lexora$shouldForceVanillaPose(player, hand, item)) {
            return false;
        }

        return true;
    }

    @Unique
    private boolean lexora$shouldGateByTargetHit(
            AbstractClientPlayerEntity player,
            Hand hand,
            ItemStack item,
            Arm arm
    ) {
        if (player == null || item.isEmpty()) {
            return false;
        }

        if (!lexora$isVmEnabled()) {
            return false;
        }

        if (!lexora$isTargetHitMode()) {
            return false;
        }

        if (lexora$isStandardMode()) {
            return false;
        }

        if (hand != Hand.MAIN_HAND) {
            return false;
        }

        if (arm != player.getMainArm()) {
            return false;
        }

        if (lexora$shouldForceVanillaPose(player, hand, item)) {
            return false;
        }

        return true;
    }

    @Unique
    private boolean lexora$isTargetHitActive() {
        return System.currentTimeMillis() < lexora$targetHitUntilMs;
    }

    @Unique
    private boolean lexora$updateTargetHitState(float swingProgress) {
        long now = System.currentTimeMillis();

        boolean hasTarget = lexora$hasHoveredLivingTarget();

        boolean swingStarted = swingProgress > 0.025f
                && lexora$lastMainHandSwingProgress <= 0.010f;

        boolean swingWrapped = swingProgress > 0.025f
                && lexora$lastMainHandSwingProgress > 0.80f
                && swingProgress < 0.30f;

        if (hasTarget && (swingStarted || swingWrapped)) {
            lexora$targetHitUntilMs = now + lexora$TARGET_HIT_HOLD_MS;
        }

        lexora$lastMainHandSwingProgress = swingProgress;

        return now < lexora$targetHitUntilMs;
    }

    @Unique
    private boolean lexora$shouldCancelVanillaForArm(Arm arm) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || !lexora$isVmEnabled()) {
            return false;
        }

        Hand hand = arm == mc.player.getMainArm() ? Hand.MAIN_HAND : Hand.OFF_HAND;
        ItemStack stack = hand == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();

        if (hand == Hand.OFF_HAND) {
            return false;
        }

        if (lexora$isStandardMode()) {
            return false;
        }

        if (lexora$shouldForceVanillaPose(mc.player, hand, stack)) {
            return false;
        }

        boolean targetHitGate = lexora$shouldGateByTargetHit(mc.player, hand, stack, arm);

        if (targetHitGate) {
            return lexora$isTargetHitActive();
        }

        return true;
    }

    /**
     * Возвращает цвет пламени в зависимости от выбранной темы в Lexora GUI
     */
    @Unique
    private static float[] lexora$getThemeOrFlameColor() {
        String colorMode = LexoraGui.modeSettings.getOrDefault("Hand Color Mode", "Theme").trim();
        if (colorMode.equalsIgnoreCase("Custom") || colorMode.equalsIgnoreCase("Свой цвет")) {
            float[] custom = ClientData.colorSettings.get("Hand Custom Color");
            if (custom == null) {
                custom = LexoraGui.colorSettings.get("Hand Custom Color");
            }
            if (custom != null && custom.length >= 3) {
                int rgb = java.awt.Color.HSBtoRGB(custom[0], custom[1], custom[2]);
                return new float[]{
                        ((rgb >> 16) & 0xFF) / 255.0f,
                        ((rgb >> 8) & 0xFF) / 255.0f,
                        (rgb & 0xFF) / 255.0f
                };
            }
        }

        // Режим Theme / Клиент цвет
        float[] themeHsv = ClientData.colorSettings.get("Theme Color 1");
        if (themeHsv == null) {
            themeHsv = LexoraGui.colorSettings.get("Theme Color 1");
        }
        if (themeHsv != null && themeHsv.length >= 3) {
            int rgb = java.awt.Color.HSBtoRGB(themeHsv[0], themeHsv[1], themeHsv[2]);
            return new float[]{
                    ((rgb >> 16) & 0xFF) / 255.0f,
                    ((rgb >> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f
            };
        }

        String theme = LexoraGui.modeSettings.getOrDefault("Theme", "Purple").trim();

        if (theme.equalsIgnoreCase("Red") || theme.equalsIgnoreCase("Красный")) {
            return new float[]{1.0f, 0.20f, 0.20f};
        } else if (theme.equalsIgnoreCase("Blue") || theme.equalsIgnoreCase("Синий")) {
            return new float[]{0.15f, 0.55f, 1.0f};
        } else if (theme.equalsIgnoreCase("Green") || theme.equalsIgnoreCase("Зеленый")) {
            return new float[]{0.20f, 0.95f, 0.35f};
        } else if (theme.equalsIgnoreCase("Orange") || theme.equalsIgnoreCase("Оранжевый") || theme.equalsIgnoreCase("Fire")) {
            return new float[]{1.0f, 0.45f, 0.12f};
        } else if (theme.equalsIgnoreCase("Gold") || theme.equalsIgnoreCase("Золотой")) {
            return new float[]{1.0f, 0.80f, 0.15f};
        } else if (theme.equalsIgnoreCase("Rainbow") || theme.equalsIgnoreCase("Радуга")) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
            int rgb = MathHelper.hsvToRgb(hue, 0.85f, 1.0f);
            return new float[]{
                    ((rgb >> 16) & 0xFF) / 255.0f,
                    ((rgb >> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f
            };
        }

        // По умолчанию: неоново-фиолетовый стиль Lexora
        return new float[]{0.65f, 0.25f, 1.0f};
    }

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void lexora$applyCustomVm(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        lexora$updateItemPopFix(hand, item);

        if (!lexora$isVmEnabled() || item.isEmpty()) {
            return;
        }

        /*
         * Фикс огромного предмета:
         * на самый первый момент смены предмета не применяем кастом VM.
         */
        if (lexora$shouldBlockOneFramePop(hand, equipProgress)) {
            return;
        }

        Arm arm = lexora$getArm(player, hand);

        boolean targetHitGate = lexora$shouldGateByTargetHit(player, hand, item, arm);
        boolean targetHitActive = false;

        if (targetHitGate) {
            targetHitActive = lexora$updateTargetHitState(swingProgress);
        }

        // Применяем смещения положения рук из GUI (Right/Left Hand X/Y/Z)
        if (lexora$shouldApplyOffsets(player, hand, item, arm)) {
            VMAnimations.applyGuiOffsets(matrices, arm);
        }

        boolean allowCustomAnimationNow = !targetHitGate || targetHitActive;

        if (allowCustomAnimationNow && VMAnimations.shouldApplyAnimation(player, hand, item, arm)) {
            String mode = ClientData.modeSettings.getOrDefault("VM Anim",
                    LexoraGui.modeSettings.getOrDefault("VM Anim", "Standard")).trim();

            boolean applied = VMAnimations.applyCustomAnimation(matrices, mode, swingProgress, arm);
            if (applied) {
                boolean isRightArm = arm == Arm.RIGHT;
                int i = isRightArm ? 1 : -1;

                // Базовая позиция руки первого лица
                matrices.translate(i * 0.56F, -0.52F, -0.72F);

                // Отрисовка предмета в руке
                if (!item.isEmpty()) {
                    this.renderItem(
                            player,
                            item,
                            isRightArm ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND,
                            !isRightArm,
                            matrices,
                            vertexConsumers,
                            light
                    );
                }

                // Закрываем матричный стек и отменяем ванильную обработку
                matrices.pop();
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void onPreRenderHandShader(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        lexora$handShaderActive = false;

        if (!LexoraGui.moduleStates.getOrDefault("Hand Shaders", false)) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || !mc.options.getPerspective().isFirstPerson()) {
            return;
        }

        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }

        int beforeTex = HandShaderCopy.captureBefore();

        if (beforeTex != 0) {
            lexora$handShaderActive = true;
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void onPostRenderHandShader(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (!lexora$handShaderActive) {
            return;
        }

        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {
            lexora$handShaderActive = false;
            return;
        }

        int beforeTex = HandShaderCopy.getBeforeTex();
        int afterTex = HandShaderCopy.captureAfter();

        if (beforeTex == 0 || afterTex == 0) {
            lexora$handShaderActive = false;
            return;
        }

        float dYaw = 0.0f;
        float dPitch = 0.0f;

        // ── Мягкая инерция камеры для подсветки при разворотах ──
        if (mc.player != null) {
            float curYaw = mc.player.getYaw();
            float curPitch = mc.player.getPitch();

            dYaw = MathHelper.wrapDegrees(curYaw - lexora$lastYaw);
            dPitch = curPitch - lexora$lastPitch;
            lexora$lastYaw = curYaw;
            lexora$lastPitch = curPitch;

            // Плавное накопление скорости мыши:
            // Мышь вправо -> dYaw > 0 -> targetVelX > 0 (в шейдере подсветка уходит влево)
            // Мышь влево -> dYaw < 0 -> targetVelX < 0 (в шейдере подсветка уходит вправо)
            float targetVelX = MathHelper.clamp(dYaw * 0.08f, -1.2f, 1.2f);
            float targetVelY = MathHelper.clamp(dPitch * 0.08f, -1.2f, 1.2f);

            lexora$camVelX += (targetVelX - lexora$camVelX) * 0.30f;
            lexora$camVelY += (targetVelY - lexora$camVelY) * 0.30f;
        }

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int prevTex1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        int prevTex2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevVBO = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        ShaderUtil activeShader = LexoraShaders.getCurrentHandShader();

        if (activeShader != null && activeShader.isValid()) {
            float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
            float glowPercent = LexoraGui.numSettings.getOrDefault("Hand Glow %", 35.0f);
            float fireStrength = LexoraGui.numSettings.getOrDefault("Fire Strength", 1.0f);
            float fireSpeed = LexoraGui.numSettings.getOrDefault("Fire Speed", 1.0f);

            float glow = Math.max(0.0f, Math.min(2.5f, (glowPercent / 100.0f) * 1.5f * fireStrength));
            float width = (float) mc.getWindow().getFramebufferWidth();
            float height = (float) mc.getWindow().getFramebufferHeight();
            float[] flameCol = lexora$getThemeOrFlameColor();

            activeShader.bind();

            int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

            if (currentProgram == activeShader.getProgramID() && currentProgram != 0) {
                activeShader.setUniform1f("time", time);
                activeShader.setUniform2f("resolution", width, height);

                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, beforeTex);
                activeShader.setUniform1i("BeforeTexture", 0);

                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, afterTex);
                activeShader.setUniform1i("AfterTexture", 1);

                activeShader.setUniform1f("effectAlpha", glow);

                if (activeShader == LexoraShaders.snowShader) {
                    activeShader.setUniform3f("snowColor", 1.0f, 1.0f, 1.0f);
                    activeShader.setUniform1f("snowIntensity", 2.5f);
                    activeShader.setUniform1f("snowSpeed", 1.5f);
                } else if (activeShader == LexoraShaders.smokeShader) {
                    activeShader.setUniform3f("smokeColor", flameCol[0], flameCol[1], flameCol[2]);
                    activeShader.setUniform1f("smokeIntensity", 3.0f);
                } else if (activeShader == LexoraShaders.stripesShader) {
                    activeShader.setUniform3f("stripesColor1", flameCol[0], flameCol[1], flameCol[2]);
                    activeShader.setUniform3f("stripesColor2", 0.1f, 0.0f, 0.0f);
                    activeShader.setUniform1f("stripesWidth", 15.0f);
                    activeShader.setUniform1f("stripesSpeed", 8.0f);
                } else if (activeShader == LexoraShaders.solidShader) {
                    activeShader.setUniform3f("customColor1", flameCol[0], flameCol[1], flameCol[2]);
                    activeShader.setUniform3f("customColor2", 1.0f, 0.0f, 0.8f);
                } else if (activeShader == LexoraShaders.fireShader) {
                    activeShader.setUniform3f("flameColor", flameCol[0], flameCol[1], flameCol[2]);
                    activeShader.setUniform1f("fireStrength", fireStrength);
                    activeShader.setUniform1f("fireSpeed", fireSpeed);
                    activeShader.setUniform2f("camOffset", lexora$camVelX, lexora$camVelY);
                }

                HandShaderRenderer.drawPerfectQuad();
            }

            activeShader.unbind();
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFBO);
        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        GL30.glBindVertexArray(prevVAO);

        GL20.glUseProgram(prevProgram);

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex1);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex0);

        GL13.glActiveTexture(prevActiveTexture);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        lexora$handShaderActive = false;
    }
}