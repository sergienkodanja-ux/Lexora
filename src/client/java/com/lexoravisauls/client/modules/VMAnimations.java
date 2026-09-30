package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec2f;
import org.joml.Quaternionf;

public class VMAnimations {

    public record SwingTransformations(
            float anchorX, float anchorY, float anchorZ,
            float moveX, float moveY, float moveZ,
            float rotateX, float rotateY, float rotateZ
    ) {}

    public record SwingPreset(
            String name,
            Vec2f bezierStart,
            Vec2f bezierEnd,
            boolean swingBack,
            float speed,
            SwingTransformations from,
            SwingTransformations to
    ) {}

    // ── Пресеты кривых Безье из Rockstar 1.21.4 (swings.*) ────────────────────
    public static final SwingPreset BLOCK_HIT = new SwingPreset(
            "Под наклоном",
            new Vec2f(0.5F, 1.0F),
            new Vec2f(0.5F, 0.0F),
            true,
            2.0F,
            new SwingTransformations(0.0F, -0.05F, -0.7F, 1.0500001F, -0.7F, -1.1F, -120.0F, -135.0F, -60.0F),
            new SwingTransformations(0.0F, -0.05F, -0.7F, 1.0500001F, -0.7F, -1.1F, -120.0F, -180.0F, -60.0F)
    );

    public static final SwingPreset BONK = new SwingPreset(
            "Боньк",
            new Vec2f(0.40131578F, 0.53543305F),
            new Vec2f(0.0F, -0.24409449F),
            true,
            2.0F,
            new SwingTransformations(0.0F, -0.4F, -0.65000004F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
            new SwingTransformations(0.0F, -0.4F, -0.65000004F, 0.0F, 0.0F, 0.0F, -45.0F, 0.0F, 0.0F)
    );

    public static final SwingPreset ROTATE_360 = new SwingPreset(
            "Вращение на 360",
            new Vec2f(0.43421054F, 0.61417323F),
            new Vec2f(0.04605263F, -0.26771653F),
            false,
            2.0F,
            new SwingTransformations(0.0F, -0.4F, -0.65000004F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
            new SwingTransformations(0.0F, -0.4F, -0.65000004F, 0.0F, 0.0F, 0.0F, -360.0F, 0.0F, 0.0F)
    );

    public static final SwingPreset FROM_ME = new SwingPreset(
            "От себя",
            new Vec2f(0.42105263F, 0.87401575F),
            new Vec2f(0.3881579F, -0.4566929F),
            true,
            2.0F,
            new SwingTransformations(0.0F, 0.0F, -1.1F, 0.2F, 0.0F, -0.1F, -135.0F, 45.0F, 60.0F),
            new SwingTransformations(0.0F, 0.0F, -1.1F, 0.2F, 0.0F, -0.3F, -180.0F, 45.0F, 60.0F)
    );

    // ── Применение кастомной анимации (11 режимов Rockstar) ───────────────────
    public static boolean applyCustomAnimation(MatrixStack matrices, String rawMode, float swingProgress, Arm arm) {
        if (rawMode == null) return false;
        String m = rawMode.trim().toLowerCase();
        if (m.equals("standard") || m.equals("стандарт") || m.isEmpty()) {
            return false;
        }

        // Фикс бага 1: не умножаем swingProgress на speed повторно,
        // так как скорость уже управляет общей длительностью тиков в MixinLivingEntity.
        float effectiveSwing = MathHelper.clamp(swingProgress, 0.0f, 1.0f);
        float progress = MathHelper.sin(MathHelper.sqrt(effectiveSwing) * (float) Math.PI);

        float isRight = (arm == Arm.RIGHT) ? 1.0f : -1.0f;

        // 1. "Под наклоном" (Block Hit из Rockstar 1.21.4)
        if (m.contains("под наклоном") || m.contains("block hit") || m.contains("blockhit")) {
            applyCurvePreset(matrices, BLOCK_HIT, effectiveSwing, arm);
            return true;
        }

        // 2. "Наклон" (Slant)
        if (m.equals("наклон") || m.contains("slant") || (m.contains("наклон") && !m.contains("под"))) {
            matrices.translate(0.0f, 0.0f, -0.15f * progress);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0f * progress));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(35.0f * progress * isRight));
            return true;
        }

        // 3. "Вращение на 360" (Rotate 360 из Rockstar 1.21.4)
        if (m.contains("360") || m.contains("вращен") || m.contains("rotate")) {
            applyCurvePreset(matrices, ROTATE_360, effectiveSwing, arm);
            return true;
        }

        // 4. "От себя" (Push / From Me из Rockstar 1.21.4)
        if (m.contains("от себя") || m.contains("push") || m.contains("from me") || m.contains("from_me")) {
            applyCurvePreset(matrices, FROM_ME, effectiveSwing, arm);
            return true;
        }

        // Кастомная анимация из Редактора Взмаха (Rockstar Swing Animation Editor)
        if (m.contains("кастом") || m.contains("custom") || m.contains("редактор") || m.contains("editor")) {
            com.lexoravisauls.client.modules.swinganim.SwingTransformations trans =
                    com.lexoravisauls.client.modules.swinganim.SwingManager.getInstance().transformations(effectiveSwing);
            applyTransformations(matrices, trans, arm);
            return true;
        }

        // Совместимость со старыми конфигами
        if (m.contains("боньк") || m.contains("bonk")) {
            applyCurvePreset(matrices, BONK, effectiveSwing, arm);
            return true;
        }

        return false;
    }

    public static void applyTransformations(MatrixStack matrices, com.lexoravisauls.client.modules.swinganim.SwingTransformations trans, Arm arm) {
        float anchorX = trans.anchorX();
        float anchorY = trans.anchorY();
        float anchorZ = trans.anchorZ();

        float moveX   = trans.moveX();
        float moveY   = trans.moveY();
        float moveZ   = trans.moveZ();

        float rotateX = trans.rotateX();
        float rotateY = trans.rotateY();
        float rotateZ = trans.rotateZ();

        if (arm == Arm.LEFT) {
            anchorX = -anchorX;
            moveX = -moveX;
            rotateY = -rotateY;
            rotateZ = -rotateZ;
        }

        matrices.translate(anchorX, anchorY, anchorZ);
        matrices.translate(moveX, moveY, moveZ);
        matrices.multiply(
                new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(rotateX),
                        (float) Math.toRadians(rotateY),
                        (float) Math.toRadians(rotateZ)
                )
        );
        matrices.translate(-anchorX, -anchorY, -anchorZ);
    }

    private static void applyCurvePreset(MatrixStack matrices, SwingPreset preset, float effectiveSwing, Arm arm) {
        float progress = easeBezier(
                effectiveSwing,
                preset.bezierStart().x,
                1.0F - preset.bezierStart().y,
                preset.bezierEnd().x,
                1.0F - preset.bezierEnd().y
        );

        if (preset.swingBack()) {
            progress = MathHelper.sin(MathHelper.sqrt(progress) * (float) Math.PI);
        }

        SwingTransformations from = preset.from();
        SwingTransformations to = preset.to();

        float anchorX = lerp(from.anchorX(), to.anchorX(), progress);
        float anchorY = lerp(from.anchorY(), to.anchorY(), progress);
        float anchorZ = lerp(from.anchorZ(), to.anchorZ(), progress);

        float moveX   = lerp(from.moveX(),   to.moveX(),   progress);
        float moveY   = lerp(from.moveY(),   to.moveY(),   progress);
        float moveZ   = lerp(from.moveZ(),   to.moveZ(),   progress);

        float rotateX = lerp(from.rotateX(), to.rotateX(), progress);
        float rotateY = lerp(from.rotateY(), to.rotateY(), progress);
        float rotateZ = lerp(from.rotateZ(), to.rotateZ(), progress);

        if (arm == Arm.LEFT) {
            anchorX = -anchorX;
            moveX = -moveX;
            rotateY = -rotateY;
            rotateZ = -rotateZ;
        }

        matrices.translate(anchorX, anchorY, anchorZ);
        matrices.translate(moveX, moveY, moveZ);
        matrices.multiply(
                new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(rotateX),
                        (float) Math.toRadians(rotateY),
                        (float) Math.toRadians(rotateZ)
                )
        );
        matrices.translate(-anchorX, -anchorY, -anchorZ);
    }

    public static boolean isCustomAnimMode(String rawMode) {
        if (rawMode == null) return false;
        String m = rawMode.trim().toLowerCase();
        return !m.equals("standard") && !m.equals("стандарт") && !m.isEmpty();
    }

    // ── Кубический Безье солвер (Rockstar Easing.generate) ────────────────────
    public static float easeBezier(float t, float x1, float y1, float x2, float y2) {
        if (t <= 0.0F) return 0.0F;
        if (t >= 1.0F) return 1.0F;

        float tBez = solveTBez(x1, x2, t);
        return bezierY(tBez, y1, y2);
    }

    private static float solveTBez(float x1, float x2, float progress) {
        float t = progress;
        for (int i = 0; i < 8; i++) {
            float x = bezierX(t, x1, x2);
            float dx = bezierDX(t, x1, x2);
            if (Math.abs(x - progress) < 1.0E-5F || Math.abs(dx) < 1.0E-6F) {
                break;
            }
            t -= (x - progress) / dx;
            t = Math.max(0.0F, Math.min(1.0F, t));
        }
        return t;
    }

    private static float bezierX(float t, float x1, float x2) {
        return 3.0F * (1.0F - t) * (1.0F - t) * t * x1 + 3.0F * (1.0F - t) * t * t * x2 + t * t * t;
    }

    private static float bezierDX(float t, float x1, float x2) {
        return 3.0F * ((1.0F - t) * (1.0F - 3.0F * t) * x1 + (2.0F * t - 3.0F * t * t) * x2) + 3.0F * t * t;
    }

    private static float bezierY(float t, float y1, float y2) {
        return 3.0F * (1.0F - t) * (1.0F - t) * t * y1 + 3.0F * (1.0F - t) * t * t * y2 + t * t * t;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ── Применение кастомных смещений рук из меню (Right/Left Hand X/Y/Z) ───
    public static void applyGuiOffsets(MatrixStack matrices, Arm arm) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        boolean enabled = ClientData.moduleStates.getOrDefault("View Model", LexoraGui.moduleStates.getOrDefault("View Model", false))
                || ClientData.moduleStates.getOrDefault("ViewModel", LexoraGui.moduleStates.getOrDefault("ViewModel", false));

        if (!enabled) return;

        boolean isRight = arm == Arm.RIGHT;

        float x = ClientData.numSettings.getOrDefault(
                isRight ? "Right Hand X" : "Left Hand X", LexoraGui.numSettings.getOrDefault(isRight ? "Right Hand X" : "Left Hand X", 0.0f));

        float y = ClientData.numSettings.getOrDefault(
                isRight ? "Right Hand Y" : "Left Hand Y", LexoraGui.numSettings.getOrDefault(isRight ? "Right Hand Y" : "Left Hand Y", 0.0f));

        float z = ClientData.numSettings.getOrDefault(
                isRight ? "Right Hand Z" : "Left Hand Z", LexoraGui.numSettings.getOrDefault(isRight ? "Right Hand Z" : "Left Hand Z", 0.0f));

        float scale = ClientData.numSettings.getOrDefault(
                isRight ? "Right Hand Scale" : "Left Hand Scale", LexoraGui.numSettings.getOrDefault(isRight ? "Right Hand Scale" : "Left Hand Scale", 1.0f));

        matrices.translate(x, y, z);
        matrices.scale(scale, scale, scale);
    }

    // ── Проверка применимости анимации (из Rockstar SwingAnimation) ───────────
    public static boolean shouldApplyAnimation(AbstractClientPlayerEntity player, Hand hand, ItemStack itemStack, Arm arm) {
        if (player == null || itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        // Анимация взмаха применяется строго к основной руке
        if (arm != player.getMainArm()) {
            return false;
        }

        Item item = itemStack.getItem();
        if (item == Items.AIR
                || item == Items.FILLED_MAP
                || item == Items.CROSSBOW
                || item == Items.BOW
                || item == Items.TRIDENT) {
            return false;
        }

        if (player.isUsingItem() && player.getActiveHand() == hand) {
            UseAction action = itemStack.getUseAction();
            if (action == UseAction.DRINK
                    || action == UseAction.EAT
                    || action == UseAction.BOW
                    || action == UseAction.CROSSBOW
                    || action == UseAction.SPEAR
                    || action == UseAction.BLOCK) {
                return false;
            }
        }

        return true;
    }
}