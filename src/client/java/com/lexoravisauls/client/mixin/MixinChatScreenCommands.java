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

        if (!handled && chatText != null) {
            String trimmed = chatText.trim().toLowerCase();
            if (trimmed.equals(".swing") || trimmed.equals(".swings") || trimmed.equals(".anim") || trimmed.equals(".editor")) {
                if (client != null) {
                    com.lexoravisauls.client.core.ClientData.modeSettings.put("VM Anim", "Кастомный");
                    client.send(() -> client.setScreen(new com.lexoravisauls.client.modules.swinganim.SwingAnimScreen()));
                }
                handled = true;
            }
        }

        if (handled) {
            if (addToHistory && client != null && client.inGameHud != null) {
                client.inGameHud.getChatHud().addToMessageHistory(chatText);
            }
            ci.cancel();
        }
    }
}