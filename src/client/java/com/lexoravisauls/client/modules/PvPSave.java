package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.mixin.BossBarHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PvPSave {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)");

    private static long lastBossBarScanTime = 0L;
    private static boolean bossBarPvPActive = false;
    private static int bossBarSecondsLeft = 0;

    private static long lastWarningTime = 0L;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!LexoraGui.moduleStates.getOrDefault("PvP Save", false)) {
            reset();
            return;
        }

        if (mc == null || mc.world == null || mc.player == null || !isAlive()) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBossBarScanTime >= 1000L) {
            scanBossBars(mc);
            lastBossBarScanTime = now;
        }
    }

    private static void scanBossBars(MinecraftClient mc) {
        bossBarPvPActive = false;
        bossBarSecondsLeft = 0;

        try {
            BossBarHud bossBarHud = mc.inGameHud.getBossBarHud();
            if (bossBarHud == null) return;

            Map<UUID, ClientBossBar> bossBars = ((BossBarHudAccessor) bossBarHud).getBossBars();
            if (bossBars == null || bossBars.isEmpty()) return;

            for (ClientBossBar bossBar : bossBars.values()) {
                if (bossBar == null || bossBar.getName() == null) continue;

                String text = bossBar.getName().getString();
                if (!isPvPBossBar(text)) continue;

                bossBarPvPActive = true;

                int parsedSeconds = extractSeconds(text);
                if (parsedSeconds >= 0) {
                    bossBarSecondsLeft = parsedSeconds;
                }

                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isPvPBossBar(String text) {
        if (text == null || text.isEmpty()) return false;

        String lower = text.toLowerCase();

        return lower.contains("pvp")
                || lower.contains("пвп")
                || lower.contains("combat")
                || lower.contains("в бою")
                || lower.contains("не выходите")
                || lower.contains("покинете игру")
                || lower.contains("нельзя выйти")
                || lower.contains("бой");
    }

    private static int extractSeconds(String text) {
        if (text == null || text.isEmpty()) return -1;

        int result = -1;
        Matcher matcher = NUMBER_PATTERN.matcher(text);

        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                if (value >= 0 && value <= 300) {
                    result = value;
                }
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    public static boolean isAlive() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && !mc.player.isDead() && mc.player.getHealth() > 0.0f;
    }

    public static boolean isInPvP() {
        if (!LexoraGui.moduleStates.getOrDefault("PvP Save", false)) return false;
        if (!isAlive()) return false;

        tick();
        return bossBarPvPActive;
    }

    public static int getRemainingSeconds() {
        if (!LexoraGui.moduleStates.getOrDefault("PvP Save", false)) return 0;
        if (!isAlive()) return 0;

        tick();
        return bossBarPvPActive ? bossBarSecondsLeft : 0;
    }

    public static boolean handleCommand(String command) {
        if (command == null) return false;

        String cmd = command.toLowerCase().trim().replace("/", "");
        if (!(cmd.startsWith("hub")
                || cmd.startsWith("spawn")
                || cmd.startsWith("leave")
                || cmd.startsWith("q")
                || cmd.startsWith("rtp")
                || cmd.startsWith("home"))) {
            return false;
        }

        if (!isInPvP()) {
            lastWarningTime = 0L;
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastWarningTime < 3000L) {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(
                    Text.literal("§c[PvP Save] §fВы в PvP. Введите команду ещё раз в течение 3 сек, чтобы выйти.")
            );
        }

        lastWarningTime = now;
        return true;
    }

    public static void reset() {
        bossBarPvPActive = false;
        bossBarSecondsLeft = 0;
        lastBossBarScanTime = 0L;
        lastWarningTime = 0L;
    }
}