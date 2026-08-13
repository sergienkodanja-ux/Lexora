package com.lexoravisauls.client.cosmetics;

import com.lexoravisauls.client.cosmetics.models.ObjModel;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

public class HornsRenderer {

    // Укажи правильные пути до своих файлов
    private static final ObjModel HORNS_OBJ = new ObjModel(Identifier.of("lexoravisauls", "models/cosmetics/roga.obj"));
    private static final Identifier HORNS_TEXTURE = Identifier.of("lexoravisauls", "textures/cosmetics/roga.png");

    public static void renderHorns(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        matrices.push();

        // 1. ПЕРЕВОРОТ: Крутим на 180 градусов, чтобы они встали нормально
        // Math.PI — это ровно 180 градусов в радианах.
        matrices.multiply(new Quaternionf().rotateX((float) Math.PI));
        // (Если вдруг рога стали смотреть задом наперед, добавь еще это: matrices.multiply(new Quaternionf().rotateY((float) Math.PI)); )

        // 2. ПОЗИЦИЯ: X (влево/вправо), Y (вверх/вниз), Z (вперед/назад)
        // Так как мы их перевернули, старые координаты уже не подходят.
        // Y = -0.2f (немного подгоняем высоту, отрицательное значение тянет ВВЕРХ).
        // Z = 0.15f (сдвигаем немного НАЗАД к затылку).
        matrices.translate(0.0f, -1f, -0.1f);

        // 3. МАСШТАБ: Ставим 0.65, как ты просил
        float scale = 0.65f;
        matrices.scale(scale, scale, scale);

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(HORNS_TEXTURE));

        // Рендерим левую и правую половину
        HORNS_OBJ.renderLeft(matrices, consumer, light, overlay, 0xFFFFFFFF);
        HORNS_OBJ.renderRight(matrices, consumer, light, overlay, 0xFFFFFFFF);

        matrices.pop();
    }
}