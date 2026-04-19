package com.lexoravisauls.client.utils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public class InvAnimState {
    public static long openTime = 0;
    public static DrawContext currentContext = null;
    private static boolean isPushed = false;

    public static void push(Screen screen) {
        if (isPushed || currentContext == null) return;
        isPushed = true;

        float progress = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 450f);
        float scale = 1.0f;

        if (progress < 1.0f) {
            float c1 = 1.70158f;
            float c3 = c1 + 1f;
            scale = (float) (1 + c3 * Math.pow(progress - 1, 3) + c1 * Math.pow(progress - 1, 2));
        }

        currentContext.getMatrices().push();
        currentContext.getMatrices().translate(screen.width / 2f, screen.height / 2f, 0);
        currentContext.getMatrices().scale(scale, scale, 1.0f);
        currentContext.getMatrices().translate(-screen.width / 2f, -screen.height / 2f, 0);
    }

    public static void pop() {
        if (!isPushed || currentContext == null) return;
        isPushed = false;
        try {
            currentContext.getMatrices().pop();
        } catch (Exception ignored) {}
    }

    public static void forcePop() {
        if (isPushed) pop();
    }
}