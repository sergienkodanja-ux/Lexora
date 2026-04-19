package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugHud.class)
public class MixinDebugHud {

    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void onGetLeftText(CallbackInfoReturnable<List<String>> cir) {
        // Проверяем, включен ли модуль и настройка скрытия координат
        if (!LexoraGui.moduleStates.getOrDefault("Streamer Mode", false)) return;
        if (!LexoraGui.moduleStates.getOrDefault("Hide Coords", true)) return;

        List<String> list = cir.getReturnValue();

        for (int i = 0; i < list.size(); i++) {
            String line = list.get(i);
            // Ищем строки, где пишутся координаты, блок и чанк
            if (line.startsWith("XYZ:") || line.startsWith("Block:") || line.startsWith("Chunk:")) {
                // Оставляем название (XYZ:), а цифры меняем на ???
                list.set(i, line.split(":")[0] + ": ??? ??? ???");
            }
        }
    }
}