package com.lexoravisauls.client.utils;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

public class TabAnimState {
    public static float animY = -150f;
    public static float targetY = -150f;

    // Сохраняем данные для отрисовки закрывающегося таба
    public static Scoreboard lastScoreboard = null;
    public static ScoreboardObjective lastObjective = null;
}