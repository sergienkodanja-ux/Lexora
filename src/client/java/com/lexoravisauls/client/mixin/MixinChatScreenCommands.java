package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.utils.GPS;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreenCommands {

    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void lexora$handleLocalCommands(String chatText, boolean addToHistory, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        boolean handled = GPS.handleLocalCommand(chatText);



        if (handled) {
            if (addToHistory && client != null && client.inGameHud != null) {
                client.inGameHud.getChatHud().addToMessageHistory(chatText);
            }
            ci.cancel();
        }
    }
}