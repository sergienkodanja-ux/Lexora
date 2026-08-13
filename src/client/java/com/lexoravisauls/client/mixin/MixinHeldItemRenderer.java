package com.lexoravisauls.client.mixin;

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
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class MixinHeldItemRenderer {

    @Unique
    private static boolean lexora$handShaderActive = false;

    @Unique
    private static long lexora$targetHitUntilMs = 0L;

    @Unique
    private static float lexora$lastMainHandSwingProgress = 0.0f;

    @Unique
    private static final long lexora$TARGET_HIT_HOLD_MS = 2500L;

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
        return LexoraGui.moduleStates.getOrDefault("View Model", false)
                || LexoraGui.moduleStates.getOrDefault("ViewModel", false);
    }

    @Unique
    private boolean lexora$isStandardMode() {
        String mode = LexoraGui.modeSettings.getOrDefault("VM Anim", "Standard").trim();
        return mode.equalsIgnoreCase("Standard") || mode.equalsIgnoreCase("Стандарт");
    }

    @Unique
    private boolean lexora$isTargetHitMode() {
        return LexoraGui.moduleStates.getOrDefault("VM Target Hit", false)
                || LexoraGui.moduleStates.getOrDefault("Target Hit VM", false)
                || LexoraGui.moduleStates.getOrDefault("Target Hit Anim", false);
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

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
                    shift = At.Shift.AFTER
            )
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

        if (lexora$shouldApplyOffsets(player, hand, item, arm)) {
            VMAnimations.applyGuiOffsets(matrices, arm);
        }

        boolean allowCustomAnimationNow = !targetHitGate || targetHitActive;

        if (lexora$shouldApplyCustomAnimation(player, hand, item, arm) && allowCustomAnimationNow) {
            VMAnimations.handleSwing(matrices, arm, swingProgress);
        }
    }

    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void lexora$blockSwingOffset(
            MatrixStack matrices,
            Arm arm,
            float swingProgress,
            CallbackInfo ci
    ) {
        if (lexora$shouldCancelVanillaForArm(arm)) {
            ci.cancel();
        }
    }

    @Inject(method = "applyEquipOffset", at = @At("HEAD"), cancellable = true)
    private void lexora$blockEquipOffset(
            MatrixStack matrices,
            Arm arm,
            float equipProgress,
            CallbackInfo ci
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null) {
            Hand hand = lexora$getHandForArm(arm);
            ItemStack stack = hand == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();

            lexora$updateItemPopFix(hand, stack);

            /*
             * Второй маленький кусок фикса:
             * на первый кадр смены предмета не отменяем ванильный equip offset.
             */
            if (lexora$shouldBlockOneFramePop(hand, equipProgress)) {
                return;
            }
        }

        if (lexora$shouldCancelVanillaForArm(arm)) {
            ci.cancel();
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void lexora$blockSwingArm(
            float swingProgress,
            float equipProgress,
            MatrixStack matrices,
            int armX,
            Arm arm,
            CallbackInfo ci
    ) {
        if (lexora$shouldCancelVanillaForArm(arm)) {
            ci.cancel();
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

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int prevTex1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

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
            activeShader.bind();

            int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

            if (currentProgram == activeShader.getProgramID() && currentProgram != 0) {
                float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
                float glowPercent = LexoraGui.numSettings.getOrDefault("Hand Glow %", 35.0f);
                float glow = Math.max(0.0f, Math.min(1.0f, (glowPercent / 100.0f) * 1.8f));

                activeShader.setUniform1f("time", time);
                activeShader.setUniform2f(
                        "resolution",
                        mc.getWindow().getFramebufferWidth(),
                        mc.getWindow().getFramebufferHeight()
                );

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
                    activeShader.setUniform3f("smokeColor", 0.7f, 0.2f, 1.0f);
                    activeShader.setUniform1f("smokeIntensity", 3.0f);
                } else if (activeShader == LexoraShaders.stripesShader) {
                    activeShader.setUniform3f("stripesColor1", 1.0f, 0.1f, 0.1f);
                    activeShader.setUniform3f("stripesColor2", 0.1f, 0.0f, 0.0f);
                    activeShader.setUniform1f("stripesWidth", 15.0f);
                    activeShader.setUniform1f("stripesSpeed", 8.0f);
                } else if (activeShader == LexoraShaders.solidShader) {
                    activeShader.setUniform3f("customColor1", 0.0f, 0.8f, 1.0f);
                    activeShader.setUniform3f("customColor2", 1.0f, 0.0f, 0.8f);
                }

                HandShaderRenderer.render(activeShader);
            }

            activeShader.unbind();
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        GL30.glBindVertexArray(prevVAO);

        GL20.glUseProgram(prevProgram);

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