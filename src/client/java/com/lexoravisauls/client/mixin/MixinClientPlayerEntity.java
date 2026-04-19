package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.AutoSprint;
import com.lexoravisauls.client.modules.ElytraSwap;
import com.lexoravisauls.client.modules.ItemSwap;
import com.lexoravisauls.client.modules.PvPSave;
import com.lexoravisauls.client.utils.DiscordRPCManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinClientPlayerEntity {

    @Shadow private int itemUseCooldown;
    private int rpcTick = 0;

    @Inject(method = "updateWindowTitle", at = @At("HEAD"), cancellable = true)
    private void onUpdateWindowTitle(CallbackInfo ci) {
        MinecraftClient.getInstance().getWindow().setTitle("Lexora Visuals");
        ci.cancel();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(RunArgs args, CallbackInfo ci) {
        DiscordRPCManager.start();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        DiscordRPCManager.stop();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        ItemSwap.tick();
        AutoSprint.tick();
        ElytraSwap.tick();

        MinecraftClient mc = MinecraftClient.getInstance();

        // 🔥 ИДЕАЛЬНЫЙ Lock Slot (Защита от выкидывания на Q без ошибок в коде!)
        if (LexoraGui.moduleStates.getOrDefault("Lock Slot", false) && mc.player != null) {
            int slot = mc.player.getInventory().selectedSlot;
            if (LexoraGui.moduleStates.getOrDefault("LockSlot_" + slot, false)) {
                // Если слот заблокирован, мы просто "съедаем" все нажатия кнопки Q!
                while (mc.options.dropKey.wasPressed()) {
                    // Пустота. Мышка/Клава нажалась, но предмет не выкинется.
                }
            }
        }

        // Fast EXP
        if (LexoraGui.moduleStates.getOrDefault("Fast EXP", false)) {
            if (mc.player != null && mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
                if (this.itemUseCooldown > 2) this.itemUseCooldown = 2;
            }
        }

        // Discord RPC
        rpcTick++;
        if (rpcTick >= 20) {
            rpcTick = 0;
            if (mc.player != null && mc.world != null) {
                if (mc.getCurrentServerEntry() != null) {
                    DiscordRPCManager.update("Играет на сервере", mc.getCurrentServerEntry().address);
                } else if (mc.isInSingleplayer()) {
                    DiscordRPCManager.update("Играет", "Одиночный мир");
                }
            } else {
                DiscordRPCManager.update("В главном меню", "Отдыхает...");
            }
        }
    }
}