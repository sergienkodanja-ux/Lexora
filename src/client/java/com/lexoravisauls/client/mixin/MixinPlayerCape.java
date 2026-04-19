package com.lexoravisauls.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class MixinPlayerCape {

    // Путь до твоей текстуры
    private static final Identifier LEXORA_CAPE = Identifier.of("lexoravisauls", "textures/cape.png");

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        // ОБЯЗАТЕЛЬНО: Получаем ссылку на себя
        MinecraftClient client = MinecraftClient.getInstance();

        // 🔥 СТРОГАЯ ПРОВЕРКА: Если этот игрок не является ЛОКАЛЬНЫМ ИГРОКОМ (то есть тобой), выходим
        if (client.player == null || (Object)this != client.player) {
            return;
        }

        // Если это ТЫ, заменяем текстуру
        SkinTextures original = cir.getReturnValue();
        SkinTextures customSkin = new SkinTextures(
                original.texture(),
                original.textureUrl(),
                LEXORA_CAPE, // Накидываем плащ Lexora
                LEXORA_CAPE, // Накидываем на элитры
                original.model(),
                original.secure()
        );

        cir.setReturnValue(customSkin);
    }
}