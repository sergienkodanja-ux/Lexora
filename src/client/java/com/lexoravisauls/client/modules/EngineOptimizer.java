package com.lexoravisauls.client.modules;

import net.minecraft.client.MinecraftClient;

public class EngineOptimizer {

    private static boolean threadPrioritiesBoosted = false;
    private static long lastGcCheckMs = 0L;

    public static void tick() {
        // 1. Повышение приоритета процесса рендера Майнкрафта (Thread Priority Boost)
        if (!threadPrioritiesBoosted) {
            try {
                Thread renderThread = Thread.currentThread();
                renderThread.setPriority(Thread.MAX_PRIORITY); // Приоритет 10 для основного потока кадров
                threadPrioritiesBoosted = true;
            } catch (Throwable ignored) {}
        }

        // 2. Стабилизатор памяти и Garbage Collector
        // Предотвращает микрофризы (1% Low FPS drops) от внезапных пауз сборщика мусора
        long now = System.currentTimeMillis();
        if (now - lastGcCheckMs > 60_000L) { // Раз в 60 секунд на паузе/экране
            lastGcCheckMs = now;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen != null) {
                Runtime runtime = Runtime.getRuntime();
                long usedMem = runtime.totalMemory() - runtime.freeMemory();
                long maxMem = runtime.maxMemory();
                if ((double) usedMem / maxMem > 0.75) {
                    System.gc();
                }
            }
        }
    }
}
