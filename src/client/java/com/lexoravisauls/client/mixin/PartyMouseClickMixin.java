package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.events.DynamicIslandRenderer;
import com.lexoravisauls.client.party.LexoraPartyManager;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class PartyMouseClickMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (client == null || client.getWindow().getHandle() != window) return;

        if (client.currentScreen == null) {
            if (com.lexoravisauls.client.modules.virtualdesktop.VirtualDesktopManager.handleMouseButton(button, action)) {
                ci.cancel();
                return;
            }
        }

        if (action != 1 || button != 0) return;

        double mx = client.mouse.getX() * client.getWindow().getScaledWidth()
                / (double) client.getWindow().getWidth();
        double my = client.mouse.getY() * client.getWindow().getScaledHeight()
                / (double) client.getWindow().getHeight();

        if (DynamicIslandRenderer.handlePartyNotifClick(mx, my, button)) {
            return;
        }
    }

    private static boolean inBounds(double mx, double my, float[] b) {
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }
}