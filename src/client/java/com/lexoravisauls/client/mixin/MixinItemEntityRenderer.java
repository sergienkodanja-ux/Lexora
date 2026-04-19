package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class MixinItemEntityRenderer {

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V", shift = At.Shift.AFTER))
    private void makeItemsPhysic(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {

        if (LexoraGui.moduleStates.getOrDefault("Item Physics", false)) {
            matrices.pop(); // Сбрасываем ванильное вращение и подпрыгивание
            matrices.push();

            // Создаем псевдо-рандом на основе сида состояния предмета (чтобы каждый предмет лежал под своим углом)
            float randomYaw = (state.seed * 13.5f) % 360.0f;

            // Если предмет на земле (или почти не движется)
            // В 1.21.4 нет прямого isOnGround, но можно определить по отсутствию сильного вращения или просто задать общую физику:
            // Чтобы было красиво, мы делаем так: если у предмета есть скорость (он летит) - он крутится. Если нет - лежит.
            // Но самый надежный способ без залезания в логику Entity - использовать время для вращения в воздухе.

            // Просто красивый фикс: если он лежит - он лежит плашмя.
            matrices.translate(0.0, 0.05, 0.0); // Приподнимаем над землей, чтобы не проваливался
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(randomYaw)); // Поворот по горизонтали случайный
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f)); // Кладем плашмя на землю
        }
    }
}