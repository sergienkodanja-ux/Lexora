package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.modules.PvPSave;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
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

    // 🌐 АВТО-РАСКЛАДКА КОМАНД (.фр ыуфкср -> /ah search)
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        if (com.lexoravisauls.client.modules.AutoLayout.handleChatMessage(message, (ClientPlayNetworkHandler)(Object)this)) {
            ci.cancel();
        }
    }

    // 🔥 ИНЖЕКТ 1: ОТМЕНА КОМАНД (PvP Save) & АВТО-РАСКЛАДКА
    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onSendCommand(String command, CallbackInfo ci) {
        if (com.lexoravisauls.client.modules.AutoLayout.handleChatCommand(command, (ClientPlayNetworkHandler)(Object)this)) {
            ci.cancel();
            return;
        }
        if (PvPSave.handleCommand(command)) ci.cancel();
    }

    // 🔥 ИНЖЕКТ 2: ИНДИКАТОР ТОТЕМОВ И СМЕРТИ (Kill Effect)
    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void onEntityStatus(net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // 3 - статус смерти сущности на сервере
        if (packet.getStatus() == 3) {
            net.minecraft.entity.Entity entity = packet.getEntity(mc.world);
            if (entity instanceof net.minecraft.entity.player.PlayerEntity player && entity != mc.player) {
                com.lexoravisauls.client.modules.killeffect.KillEffectManager.triggerAt(
                        player, player.getX(), player.getY(), player.getZ(), null
                );
            }
        }

        // Проверяем включен ли модуль тотемов
        if (!LexoraGui.moduleStates.getOrDefault("Totem Indicator", true)) return;

        // 35 - статус срабатывания тотема
        if (packet.getStatus() == 35) {
            net.minecraft.entity.Entity entity = packet.getEntity(mc.world);

            // Проверяем, что это Живая Сущность (игрок/моб) и она в радиусе 16 блоков
            if (entity instanceof net.minecraft.entity.LivingEntity livingEntity && entity.distanceTo(mc.player) <= 16.0f) {

                // Получаем предметы именно ТОЙ сущности, у которой сработал тотем
                net.minecraft.item.ItemStack stack = livingEntity.getMainHandStack();
                if (stack.isEmpty() || (!stack.getName().getString().toLowerCase().contains("талисман") && !stack.getName().getString().toLowerCase().contains("тотем"))) {
                    stack = livingEntity.getOffHandStack();
                }

                // Парсим данные
                String itemName = stack.getName().getString().toLowerCase().contains("талисман") ? "Талисман" : "Тотем";
                boolean isEnchanted = stack.hasGlint();
                String enchantText = isEnchanted ? "Да" : "Нет";
                String playerName = entity.getDisplayName().getString(); // Имя того, кто потерял тотем

                // Отправляем в Dynamic Island (Тип WARNING даст желтую точку)
                // И мы передаем stack, чтобы рисовалась миниатюра предмета!
                com.lexoravisauls.client.utils.NotifManager.showWithItem(
                        "Сбит " + itemName,
                        playerName + " (Зачарован: " + enchantText + ")",
                        com.lexoravisauls.client.utils.NotifManager.NotifType.WARNING,
                        stack
                );
            }
        }
    }

    // ⚡ ИНЖЕКТ 3: ПЕРЕХВАТ ПАКЕТОВ ОБНОВЛЕНИЯ СЛОТОВ И ИНВЕНТАРЯ (HW Helper серверные бинды)
    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("RETURN"))
    private void onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            com.lexoravisauls.client.modules.HwHelper.checkInventory(mc);
        }
    }

    @Inject(method = "onInventory", at = @At("RETURN"))
    private void onInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            com.lexoravisauls.client.modules.HwHelper.checkInventory(mc);
        }
    }
}