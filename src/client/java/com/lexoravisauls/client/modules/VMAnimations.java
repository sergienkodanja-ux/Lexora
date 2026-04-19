package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class VMAnimations {

    public static void applyGuiOffsets(MatrixStack matrices, Arm arm) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        boolean enabled = LexoraGui.moduleStates.getOrDefault("View Model", false)
                || LexoraGui.moduleStates.getOrDefault("ViewModel", false);

        if (!enabled) return;

        boolean isRight = arm == Arm.RIGHT;

        float x = LexoraGui.numSettings.getOrDefault(
                isRight ? "Right Hand X" : "Left Hand X", 0.0f);

        float y = LexoraGui.numSettings.getOrDefault(
                isRight ? "Right Hand Y" : "Left Hand Y", 0.0f);

        float z = LexoraGui.numSettings.getOrDefault(
                isRight ? "Right Hand Z" : "Left Hand Z", 0.0f);

        float scale = LexoraGui.numSettings.getOrDefault(
                isRight ? "Right Hand Scale" : "Left Hand Scale", 1.0f);

        matrices.translate(x, y, z);
        matrices.scale(scale, scale, scale);
    }

    public static boolean handleSwing(MatrixStack matrices, Arm arm, float swingProgress) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        boolean enabled = LexoraGui.moduleStates.getOrDefault("View Model", false)
                || LexoraGui.moduleStates.getOrDefault("ViewModel", false);

        if (!enabled) return false;

        if (arm != mc.player.getMainArm()) {
            return false;
        }

        String mode = LexoraGui.modeSettings.getOrDefault("VM Anim", "Standard").trim();
        if (mode.equalsIgnoreCase("Standard") || mode.equalsIgnoreCase("Стандарт")) {
            return false;
        }

        float strength = LexoraGui.numSettings.getOrDefault("Сила наклона", 5.0f);
        float turn = LexoraGui.numSettings.getOrDefault("Поворот", 0.0f);

        int handOffset = (arm == Arm.RIGHT) ? 1 : -1;
        float anim = MathHelper.sin(swingProgress * ((float)Math.PI / 2f) * 2f);

        if (mode.equalsIgnoreCase("Взмах")) {
            float f = (float) Math.sin(swingProgress * Math.PI);

            // 🔥 ДОБАВЛЕНО: Смещаем руку в нормальную позицию, чтобы она не была в "лице"
            // (0.7f - в сторону, -0.5f - вниз, -1.0f - назад от камеры)
            matrices.translate(handOffset * 0.7f, -0.5f, -1.0f);

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handOffset * 20.0f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-35.0f - f * strength));
        }
        else if (mode.equalsIgnoreCase("Взмах 2")) {
            // Оставлено без изменений по твоей просьбе
            matrices.translate(0.55f * handOffset, -0.5f, -(0.7f + (anim * 0.002f)));
            matrices.scale(1f, 1f, anim + 1f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22.5f * anim * (strength * 0.2f)));
        }
        else if (mode.equalsIgnoreCase("Сдвиг")) {
            // 🔥 Пофикшено расположение (вернул -90 и 90 из оригинального сурса)
            matrices.translate(handOffset * 0.75f, -0.35f, -1f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handOffset * 90f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(handOffset * -60f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f - (strength * anim) + turn));
        }
        else if (mode.equalsIgnoreCase("Ломание")) {
            // 🔥 Пофикшено расположение
            matrices.translate(handOffset * 0.75f, -0.35f, -1f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handOffset * 90f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(handOffset * -30f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f - (strength * anim) + turn));
        }
        else if (mode.equalsIgnoreCase("Выпад")) {
            float f = MathHelper.sin(swingProgress * ((float)Math.PI / 2.0f) * 2.0f);
            float f2 = strength * 2.5F;
            float f4 = (f2 / 100.0f) * 40.0f;

            matrices.translate(handOffset * 1.35f, -0.55f, -1.3F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handOffset * 45.0f));

            // Анимация удара
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handOffset * f * -(f4 / 2.0f)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(handOffset * f * -(f4 / 2.0f)));
            // 🔥 ФИКС: Убрал handOffset из POSITIVE_X. Удар всегда идет вперед, независимо от руки.
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -f4));

            matrices.translate(handOffset * -0.5f, 0.2f, -0.3f);

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handOffset * 30f));
            // 🔥 ФИКС: Убрал handOffset из POSITIVE_X. Оружие всегда должно смотреть вперед (-80 градусов).
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(handOffset * 60f));
        }
        return true;
    }
}