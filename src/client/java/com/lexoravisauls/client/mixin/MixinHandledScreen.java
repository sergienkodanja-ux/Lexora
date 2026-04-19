package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.modules.ItemScroller;
import com.lexoravisauls.client.utils.InvAnimState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public class MixinHandledScreen {

    // Тень для получения слота, над которым находится мышка
    @Shadow protected Slot focusedSlot;

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        InvAnimState.openTime = System.currentTimeMillis();
    }

    // Хватаем контекст в самом начале без конфликтов сигнатур
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private DrawContext captureContext(DrawContext context) {
        InvAnimState.forcePop(); // Сброс на случай багов
        InvAnimState.currentContext = context;
        return context;
    }

    // Закрываем анимацию в самом конце
    @Inject(method = "render", at = @At("RETURN"))
    private void endAnim(CallbackInfo ci) {
        InvAnimState.pop();
        InvAnimState.currentContext = null;

        // Вызов нашего скроллера (Fast Shift-Click)
        ItemScroller.handleDrag((HandledScreen<?>)(Object)this, this.focusedSlot);
    }

    // 🔥 Сломанный метод onMouseClickLock ПОЛНОСТЬЮ УДАЛЕН 🔥
    // Защита Lock Slot всё равно будет работать через кнопку Q благодаря MixinMinecraftClient!
}