package com.lexoravisauls.client.cosmetics;

import com.lexoravisauls.client.cosmetics.models.ObjModel;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

public class WingsRenderer {

    private static final ObjModel WINGS_OBJ = new ObjModel(Identifier.of("lexoravisauls", "models/cosmetics/heaven_wings.obj"));
    private static final Identifier WINGS_TEXTURE = Identifier.of("lexoravisauls", "textures/cosmetics/heaven_wings.png");

    public static void renderWings(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, float ageInTicks, boolean isSneaking, boolean isFlying) {
        matrices.push();

        // ИСПРАВЛЕНИЕ ПОЗИЦИИ: опустили крылья с головы ниже на спину (Y изменили с -0.05f на 0.45f)
        matrices.translate(0.0f, 0.45f, 0.15f);

        if (isSneaking) {
            matrices.translate(0.0f, 0.15f, 0.0f);
            matrices.multiply(new Quaternionf().rotateX((float) Math.toRadians(28.0)));
        }

        // Плавная анимация махов
        float flapSpeed = isFlying ? 0.35f : 0.07f;
        float amplitude = isFlying ? 20.0f : 7.0f;
        float flapAngle = MathHelper.sin(ageInTicks * flapSpeed) * amplitude;

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WINGS_TEXTURE));
        float scale = 0.30f;

        // --- ЛЕВОЕ КРЫЛО ---
        matrices.push();
        matrices.translate(-0.05f, 0.0f, 0.0f);
        matrices.multiply(new Quaternionf().rotateX((float) Math.PI));
        matrices.multiply(new Quaternionf().rotateY((float) Math.PI));
        // Отдельный взмах для левого крыла
        matrices.multiply(new Quaternionf().rotateY((float) Math.toRadians(flapAngle)));
        matrices.scale(scale, scale, scale);
        WINGS_OBJ.renderLeft(matrices, consumer, light, overlay, 0xFFFFFFFF);
        matrices.pop();

        // --- ПРАВОЕ КРЫЛО ---
        matrices.push();
        matrices.translate(0.05f, 0.0f, 0.0f);
        matrices.multiply(new Quaternionf().rotateX((float) Math.PI));
        matrices.multiply(new Quaternionf().rotateY((float) Math.PI));
        // Зеркальный взмах для правого крыла
        matrices.multiply(new Quaternionf().rotateY((float) Math.toRadians(-flapAngle)));
        matrices.scale(scale, scale, scale);
        WINGS_OBJ.renderRight(matrices, consumer, light, overlay, 0xFFFFFFFF);
        matrices.pop();

        matrices.pop();
    }
}