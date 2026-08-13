package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.main_menu.LexoraMainMenu;
import com.lexoravisauls.client.inventorymanager.InventoryManagerScreen;
import com.lexoravisauls.client.inventorymanager.ItemIdentity;
import com.lexoravisauls.client.inventorymanager.LoadoutRestoreExecutor;
import com.lexoravisauls.client.inventorymanager.LoadoutSlotData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen extends HandledScreen<PlayerScreenHandler> {

    protected MixinInventoryScreen(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void lexora$onInit(CallbackInfo ci) {
        int bw = 150, bh = 20;
        int bx = this.x + (this.backgroundWidth - bw) / 2;
        // Зажимаем снизу — если инвентарь открыт у самого верха экрана (маленькое
        // окно), кнопка над ним не должна вылезать за пределы видимой области.
        int by = Math.max(4, this.y - bh - 6);
        this.addDrawableChild(new LexoraMainMenu.LexoraButton(bx, by, bw, bh, "Менеджер инвентарей",
                () -> MinecraftClient.getInstance().setScreen(new InventoryManagerScreen())));

        LoadoutRestoreExecutor.tryHealGhosts(this.handler);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void lexora$renderGhosts(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Map<Integer, LoadoutSlotData> ghosts = LoadoutRestoreExecutor.ghosts();
        if (ghosts.isEmpty()) return;

        for (Map.Entry<Integer, LoadoutSlotData> entry : ghosts.entrySet()) {
            Slot slot = this.handler.getSlot(entry.getKey());
            int sx = this.x + slot.x;
            int sy = this.y + slot.y;

            context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x99551111);
            ItemStack ghost = ItemIdentity.templateStack(entry.getValue());
            context.drawItem(ghost, sx, sy);
            context.fill(sx, sy, sx + 16, sy + 16, 0x66771111);
        }
    }
}