package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.PvPSave;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {

    private long lastPopTime = 0;

    // 🔥 ИНЖЕКТ 1: ОТМЕНА КОМАНД (PvP Save)
    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
        if (PvPSave.handleCommand(command)) ci.cancel();
    }

    // 🔥 ИНЖЕКТ 2: ИНДИКАТОР ТОТЕМОВ
    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !LexoraGui.moduleStates.getOrDefault("Totem Indicator", true)) return;

        if (packet.getStatus() == 35 && System.currentTimeMillis() - lastPopTime > 500) {
            Entity entity = packet.getEntity(mc.world);

            if (entity != null && entity.equals(mc.player)) {
                lastPopTime = System.currentTimeMillis();

                ItemStack stack = mc.player.getMainHandStack();
                if (stack.isEmpty() || !stack.getName().getString().toLowerCase().contains("талисман")) {
                    stack = mc.player.getOffHandStack();
                }

                String itemName = stack.getName().getString().toLowerCase().contains("талисман") ? "Талисман" : "Тотем";
                boolean isEnchanted = stack.hasGlint();
                String circle = isEnchanted ? "§2●" : "§4●";
                String prefix = "§5[§dLexora§5]§f ";

                Text finalMsg = Text.literal(prefix + mc.player.getDisplayName().getString() + " §fпотерял §7" + itemName + " §fЗачарован: " + circle);
                mc.inGameHud.getChatHud().addMessage(finalMsg);
            }
        }
    }
}