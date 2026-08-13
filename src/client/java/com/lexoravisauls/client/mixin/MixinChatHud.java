package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.AutoLeave;
import com.lexoravisauls.client.modules.StreamerMode;
import com.lexoravisauls.client.utils.GPS;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class MixinChatHud {

    private float chatAnimY = 0f;

    @ModifyVariable(
            method = {
                    "addMessage(Lnet/minecraft/text/Text;)V",
                    "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Text lexora$filterChatMessage(Text message) {
        return StreamerMode.filterText(message);
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onMessageAddedSimple(Text message, CallbackInfo ci) {
        handleIncomingMessage(message);
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD")
    )
    private void onMessageAddedFull(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        handleIncomingMessage(message);
    }

    private void handleIncomingMessage(Text message) {
        chatAnimY += 10f;

        if (message != null) {
            GPS.processMessage(message.getString());
        }

        if (LexoraGui.moduleStates.getOrDefault("Auto Leave", false) && message != null) {
            String text = message.getString().toLowerCase();

            boolean isNearMsg = text.contains("игроки поблизости")
                    || text.contains("рядом с вами")
                    || text.contains("игроки рядом")
                    || text.contains("в радиусе");

            boolean nothingFound = text.contains("не найден")
                    || text.contains("никого нет")
                    || text.contains("нет игроков");

            if (isNearMsg && !nothingFound) {
                AutoLeave.executeLeave(MinecraftClient.getInstance());
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void preRenderChat(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (!LexoraGui.moduleStates.getOrDefault("Animations", true)
                || !LexoraGui.moduleStates.getOrDefault("Anim Chat", true)) return;

        chatAnimY += (0 - chatAnimY) * 0.1f;
        context.getMatrices().push();
        context.getMatrices().translate(0, chatAnimY, 0);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void postRenderChat(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("Animations", true)
                && LexoraGui.moduleStates.getOrDefault("Anim Chat", true)) {
            context.getMatrices().pop();
        }
    }
}