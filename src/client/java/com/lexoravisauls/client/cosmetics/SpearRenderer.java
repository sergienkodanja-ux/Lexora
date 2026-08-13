package com.lexoravisauls.client.cosmetics;

import com.lexoravisauls.client.cosmetics.models.ObjModel;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

public class SpearRenderer {

    // Требуется .obj-версия fallen_kings_sword.glb — Java-парсер (ObjModel)
    // читает только текстовый .obj, не .glb. Конвертируй через Blender или
    // онлайн-конвертер (например low-poly.com), положи файл сюда же, рядом
    // с roga.obj/heaven_wings.obj.
    private static final ObjModel SPEAR_OBJ = new ObjModel(Identifier.of("lexoravisauls", "models/cosmetics/fallen_kings_sword.obj"));
    private static final Identifier SPEAR_TEXTURE = Identifier.of("lexoravisauls", "textures/cosmetics/fallen_kings_sword.png");

    // Публичный доступ к загруженной модели — используется MixinHeldItemRenderer,
    // чтобы рендерить копьё от первого лица с ДРУГИМ позиционированием (рука/
    // камера, не тело персонажа), не загружая тот же .obj повторно.
    public static ObjModel getModel() {
        return SPEAR_OBJ;
    }

    // Позиция/поворот/масштаб для рендера НА СПИНЕ (когда в руке не меч).
    // ПЕРВАЯ ПОПЫТКА (перенос 1:1 с сайта: position(0, 1.0, -0.4), scale 0.75)
    // была протестирована в игре и подтвердила предупреждение ниже — копьё
    // "улетело" далеко от тела. Скорректировано по фидбеку: translate
    // уменьшен на порядок и сдвинут вниз, scale уменьшен.
    //
    // Оригинальная оговорка (актуальна и сейчас, для дальнейшей подгонки):
    // на сайте числа заданы в масштабе ВСЕЙ Three.js сцены (рост персонажа
    // целиком — тоже порядка 1.8-2.0 юнитов). Здесь, внутри FeatureRenderer,
    // translate() работает в ЛОКАЛЬНЫХ координатах модели персонажа —
    // сравни с уже работающими renderHorns()/renderWings() в этом же пакете:
    // там translate(0, -1f, -0.1f) и translate(0, 0.45f, 0.15f) — то есть
    // десятые доли, не целые единицы.
    public static void renderOnBack(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        matrices.push();

        // Порядок поворотов важен. История: было rotateY(90°)+rotateZ(-90°)
        // (перенос с сайта, неверно) → rotateX(90°)+rotateZ(-90°) (тоже не
        // то) → теперь по явному запросу добавлен ещё +90° к rotateX,
        // суммарно 180° по X, rotateZ(-90°) оставлен как был.
        matrices.multiply(new Quaternionf().rotateX((float) Math.toRadians(180.0)));
        matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians(-90.0)));

        // Было translate(0, 1.0, -0.4) — "улетело" далеко от тела. Уменьшено
        // на порядок и сдвинуто ниже (Y теперь отрицательный, ближе к
        // renderHorns/renderWings как ориентиру).
        matrices.translate(0.6f, -0.7f, -0.25f);

// Было 0.75, уменьшено — "меньше", как просил.
        float scale = 0.45f;
        matrices.scale(scale, scale, scale);

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SPEAR_TEXTURE));
        SPEAR_OBJ.renderAll(matrices, consumer, light, overlay, 0xFFFFFFFF);

        matrices.pop();
    }
}