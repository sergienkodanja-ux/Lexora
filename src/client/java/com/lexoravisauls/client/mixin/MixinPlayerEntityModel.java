package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.emotion.AnimationLoader;
import com.lexoravisauls.client.emotion.EmotionManager;
import com.lexoravisauls.client.emotion.LexoraAnimation;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class MixinPlayerEntityModel {

    @Unique
    private static final float FADE_IN_DURATION = 0.20f;

    @Unique
    private static final float FADE_OUT_DURATION = 0.25f;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void applyLexoraEmotes(PlayerEntityRenderState state, CallbackInfo ci) {
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;

        String playerName = state.name;
        if (playerName == null) return;

        if (state.isInSneakingPose || state.isGliding || state.isSwimming) {
            EmotionManager.cancelEmotion(playerName);
            return;
        }

        EmotionManager.ActiveEmotion active = EmotionManager.playingEmotions.get(playerName);
        if (active == null) return;

        LexoraAnimation anim = AnimationLoader.getAnimation(active.animationId);
        if (anim == null) return;

        float elapsedSeconds = (System.currentTimeMillis() - active.startTime) / 1000f;
        if (elapsedSeconds < 0f) elapsedSeconds = 0f;

        float weight = 1.0f;

        if (elapsedSeconds < FADE_IN_DURATION) {
            weight = Math.max(0.01f, elapsedSeconds / FADE_IN_DURATION);
        }

        if (!anim.isLoop && elapsedSeconds > anim.length) {
            float fadeTime = elapsedSeconds - anim.length;
            if (fadeTime >= FADE_OUT_DURATION) {
                EmotionManager.cancelEmotion(playerName);
                return;
            }
            weight = 1.0f - (fadeTime / FADE_OUT_DURATION);
        }

        // 1. Применяем кинематику к костям
        anim.apply(model, elapsedSeconds, weight);

        // 2. УБИРАЕМ 4 КВАДРАТА: скрываем 2-й слой скина на время танца
        model.hat.visible = false;
        model.jacket.visible = false;
        model.rightSleeve.visible = false;
        model.leftSleeve.visible = false;
        model.rightPants.visible = false;
        model.leftPants.visible = false;
    }
}