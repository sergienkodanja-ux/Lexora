package com.lexoravisauls.client.cosmetics;

import com.lexoravisauls.client.cosmetics.models.ObjModel;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

public class HornAccessoryRenderer {

    // Требуется .obj-версия horn_accessory.glb — Java-парсер (ObjModel)
    // читает только текстовый .obj, не .glb. Конвертируй так же, как
    // fallen_kings_sword, и положи файл сюда же, рядом с roga.obj.
    private static final ObjModel HORN_ACCESSORY_OBJ = new ObjModel(Identifier.of("lexoravisauls", "models/cosmetics/horn_accessory.obj"));
    private static final Identifier HORN_ACCESSORY_TEXTURE = Identifier.of("lexoravisauls", "textures/cosmetics/horn_accessory.png");

    // Позиция/поворот/масштаб — на сайте подтверждены как position(0, 2.1, 0),
    // scale 0.5 (после ручной подгонки от изначально "огромного" 2.0 — см.
    // profile.php). Та же оговорка, что и в SpearRenderer: сайт задаёт эти
    // числа в масштабе ВСЕЙ Three.js сцены (рост персонажа целиком ~1.8-2.0
    // юнита), а здесь, внутри FeatureRenderer, translate() работает в
    // ЛОКАЛЬНЫХ координатах модели головы — на порядок мельче. Сравни с уже
    // работающим HornsRenderer.renderHorns() рядом: там translate(0, -1f,
    // -0.1f), а не translate(0, 2.1, 0). Прямой перенос "2.1" сюда почти
    // наверняка выбросит модель далеко от головы — начни пробовать в районе
    // тех же величин, что уже использует roga (-1f и близкие), а не 2.1.
    public static void renderHornAccessory(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        matrices.push();

        matrices.multiply(new Quaternionf().rotateX((float) Math.PI));

        // ВНИМАНИЕ: см. комментарий выше — 2.1 с сайта сюда переносить нельзя
        // буквально, реальный масштаб этой сцены другой. История правок:
        // было -1f (слишком низко) → -0.5f (низковато, смещено влево) →
        // теперь: ЗНАК X ПОМЕНЯН на отрицательный. Причина: перед этим стоит
        // rotateX(180°) (переворот всей модели), из-за чего направление оси X
        // ПОСЛЕ поворота инвертируется — положительный X реально давал сдвиг
        // ВЛЕВО на экране (что и подтвердил фидбек из игры), значит
        // отрицательный X должен дать вправо. Y поднят ещё выше.
        matrices.translate(-0.22f, 0.55f, -0.1f);

        // Масштаб перенесён с сайта (0.5) — это соотношение (во сколько раз
        // меньше, чем было "огромное" 2.0) может сохраниться даже при другом
        // абсолютном масштабе сцены, но не гарантированно. Подстрой визуально.
        float scale = 0.3f;
        matrices.scale(scale, scale, scale);

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(HORN_ACCESSORY_TEXTURE));

        // horn_accessory рендерится как единый объект — используем renderAll(),
        // не renderLeft/renderRight (та пара нужна только для парных объектов
        // вроде крыльев, где нужна независимая анимация левой/правой половины).
        HORN_ACCESSORY_OBJ.renderAll(matrices, consumer, light, overlay, 0xFFFFFFFF);

        matrices.pop();
    }
}