package com.lexoravisauls.client.utils;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

public class TabAnimState {
    // Для DynamicIsland (не трогаем)
    public static float animY = -150f;
    public static float targetY = -150f;

    // Scale анимация таба: 0 = скрыт, 1 = полный размер
    public static float scaleY = 0f;

    public static Scoreboard lastScoreboard = null;
    public static ScoreboardObjective lastObjective = null;

    /**
     * Вызывать каждый рендер-фрейм.
     * open=true → плавно к 1.0, open=false → плавно к 0.0
     */
    public static void updateScale(boolean open) {
        float target = open ? 1.0f : 0.0f;
        // Скорость: 0.18 открытие, 0.22 закрытие — быстро и плавно
        float speed = open ? 0.18f : 0.22f;
        scaleY += (target - scaleY) * speed;
    }
}