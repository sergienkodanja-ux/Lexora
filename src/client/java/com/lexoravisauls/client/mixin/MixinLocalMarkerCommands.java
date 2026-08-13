package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.utils.GPS;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinLocalMarkerCommands {

    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void lexora$localMarkerCommand(String content, boolean addToHistory, CallbackInfo ci) {
        if (GPS.handleLocalCommand(content)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (addToHistory && mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addToMessageHistory(content);
            }
            ci.cancel();
        }
    }
}