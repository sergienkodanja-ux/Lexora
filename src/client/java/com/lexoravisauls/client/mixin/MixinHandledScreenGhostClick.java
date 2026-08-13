package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.inventorymanager.ItemIdentity;
import com.lexoravisauls.client.inventorymanager.LoadoutRestoreExecutor;
import com.lexoravisauls.client.inventorymanager.LoadoutSlotData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * mouseClicked в 1.21.4 не переопределён в InventoryScreen — он объявлен в
 * HandledScreen (и повторно в RecipeBookScreen поверх). @Inject ищет метод
 * строго в указанном классе, не поднимаясь по иерархии, поэтому таргетом
 * обязан быть сам HandledScreen.
 * <p>
 * Чтобы перехват остался только в survival-инвентаре (как и задумано в
 * MixinInventoryScreen), а не полез в сундуки/верстаки — instanceof-проверка
 * первой строкой метода.
 */
@Mixin(HandledScreen.class)
public abstract class MixinHandledScreenGhostClick {

    @Shadow @Final protected ScreenHandler handler;
    @Shadow protected int x;
    @Shadow protected int y;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lexora$interceptGhostClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) return;

        Map<Integer, LoadoutSlotData> ghosts = LoadoutRestoreExecutor.ghosts();
        if (ghosts.isEmpty()) return;

        Integer clickedSlot = null;
        LoadoutSlotData clickedData = null;

        for (Map.Entry<Integer, LoadoutSlotData> entry : ghosts.entrySet()) {
            Slot slot = this.handler.getSlot(entry.getKey());
            int sx = this.x + slot.x;
            int sy = this.y + slot.y;
            if (mouseX < sx || mouseX >= sx + 16 || mouseY < sy || mouseY >= sy + 16) continue;
            clickedSlot = entry.getKey();
            clickedData = entry.getValue();
            break; // нашли совпадение — дальше ghosts не трогаем, пока цикл не завершён
        }

        if (clickedSlot == null) return;

        // Мутируем activeGhosts только ПОСЛЕ того, как цикл по нему уже закончился —
        // иначе .remove(...) во время активной for-each итерации по той же Map
        // рискует ConcurrentModificationException.
        LoadoutRestoreExecutor.dismissGhost(clickedSlot);

        String query = ItemIdentity.displayName(clickedData.itemId, clickedData.customName);
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(null);
        ClientPlayNetworkHandler net = client.getNetworkHandler();
        if (net != null) net.sendChatCommand("ah search " + query);

        cir.setReturnValue(true);
    }
}