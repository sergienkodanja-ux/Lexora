package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.ClientData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;

import java.util.*;

public class AutoLayout {

    private static boolean internalExecution = false;

    // Раскладка ЙЦУКЕН -> QWERTY
    private static final String RU_MAP = "йцукенгшщзхъфывапролджэячсмитьбю.";
    private static final String EN_MAP = "qwertyuiop[]asdfghjkl;'zxcvbnm,./";

    private static final Map<Character, Character> RU_TO_EN = new HashMap<>();

    static {
        for (int i = 0; i < RU_MAP.length(); i++) {
            char r = RU_MAP.charAt(i);
            char e = EN_MAP.charAt(i);
            RU_TO_EN.put(r, e);
            RU_TO_EN.put(Character.toUpperCase(r), Character.toUpperCase(e));
        }
        RU_TO_EN.put(',', '?');
        RU_TO_EN.put('?', '&');
        RU_TO_EN.put('ё', '`');
        RU_TO_EN.put('Ё', '~');
    }

    // Известные команды серверов на английском
    private static final Set<String> KNOWN_COMMANDS = new HashSet<>(Arrays.asList(
            "ah", "auction", "spawn", "home", "sethome", "delhome", "homes",
            "warp", "setwarp", "delwarp", "warps", "tp", "tpa", "tpaccept", "tpdeny", "tphere",
            "m", "msg", "tell", "w", "r", "reply", "clan", "c", "chat", "near", "rtp",
            "pay", "bal", "balance", "money", "kit", "pv", "ec", "ender", "craft", "workbench",
            "fix", "repair", "feed", "heal", "suicide", "ignore", "unignore", "trade", "duel",
            "call", "hub", "anarchy", "server", "help", "list", "rules", "pass", "bp", "donate",
            "discord", "ds", "vk", "tg", "top", "trash", "shop", "callout", "p", "party",
            "callouts", "vanish", "v", "fly", "gm", "gamemode", "time", "weather", "effect"
    ));

    // Известные подкоманды
    private static final Set<String> KNOWN_SUBCOMMANDS = new HashSet<>(Arrays.asList(
            "search", "sell", "buy", "history", "menu", "bid", "expire", "claim", "take",
            "add", "del", "delete", "remove", "set", "list", "info", "create", "disband",
            "invite", "kick", "leave", "accept", "deny", "toggle", "stats", "top", "war",
            "chest", "deposit", "withdraw", "chat", "home", "start", "daily", "bonus", "food",
            "pvp", "vip", "premium", "deluxe", "clear", "open", "help", "max"
    ));

    // Русские прямые алиасы и частые опечатки команд
    private static final Map<String, String> RU_ALIASES = new HashMap<>();

    static {
        RU_ALIASES.put("ах", "ah");
        RU_ALIASES.put("аук", "ah");
        RU_ALIASES.put("аукцион", "ah");
        RU_ALIASES.put("спавн", "spawn");
        RU_ALIASES.put("дом", "home");
        RU_ALIASES.put("сетхом", "sethome");
        RU_ALIASES.put("делхом", "delhome");
        RU_ALIASES.put("варп", "warp");
        RU_ALIASES.put("сетварп", "setwarp");
        RU_ALIASES.put("делварп", "delwarp");
        RU_ALIASES.put("тпа", "tpa");
        RU_ALIASES.put("тп", "tp");
        RU_ALIASES.put("еыз", "tpa");
        RU_ALIASES.put("езф", "tpa");
        RU_ALIASES.put("еуз", "tp");
        RU_ALIASES.put("тпакцепт", "tpaccept");
        RU_ALIASES.put("тпденай", "tpdeny");
        RU_ALIASES.put("клан", "clan");
        RU_ALIASES.put("пв", "pv");
        RU_ALIASES.put("эндер", "ec");
        RU_ALIASES.put("крафт", "craft");
        RU_ALIASES.put("фикс", "fix");
        RU_ALIASES.put("починка", "repair");
        RU_ALIASES.put("хил", "heal");
        RU_ALIASES.put("нир", "near");
        RU_ALIASES.put("баланс", "balance");
        RU_ALIASES.put("деньги", "money");
        RU_ALIASES.put("кит", "kit");
        RU_ALIASES.put("хаб", "hub");
        RU_ALIASES.put("анархия", "anarchy");
        RU_ALIASES.put("дуэль", "duel");
        RU_ALIASES.put("трейд", "trade");
        RU_ALIASES.put("донат", "donate");
        RU_ALIASES.put("помощь", "help");
        RU_ALIASES.put("хелп", "help");
        RU_ALIASES.put("поиск", "search");
        RU_ALIASES.put("продать", "sell");
        RU_ALIASES.put("купить", "buy");
        RU_ALIASES.put("история", "history");
    }

    public static boolean isEnabled() {
        return ClientData.moduleStates.getOrDefault("Auto Layout", true);
    }

    public static String toEnglish(String str) {
        if (str == null) return null;
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            sb.append(RU_TO_EN.getOrDefault(c, c));
        }
        return sb.toString();
    }

    public static boolean hasCyrillic(String str) {
        if (str == null) return false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((c >= 'а' && c <= 'я') || (c >= 'А' && c <= 'Я') || c == 'ё' || c == 'Ё') {
                return true;
            }
        }
        return false;
    }

    public static List<String> tokenize(String input) {
        List<String> list = new ArrayList<>();
        if (input == null || input.isEmpty()) return list;

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                sb.append(c);
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (sb.length() > 0) {
                    list.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            list.add(sb.toString());
        }
        return list;
    }

    /**
     * Анализирует сообщение и, если это ошибочная раскладка, возвращает исправленную команду.
     * Возвращает null, если исправление не требуется.
     */
    public static String fixCommand(String message) {
        if (message == null || message.isEmpty()) return null;

        boolean startsWithDot = message.startsWith(".");
        boolean startsWithSlash = message.startsWith("/");

        if (!startsWithDot && !startsWithSlash) {
            return null;
        }

        String raw = message.substring(1).trim();
        if (raw.isEmpty() || raw.startsWith(".") || raw.startsWith("/")) {
            return null;
        }

        List<String> tokens = tokenize(raw);
        if (tokens.isEmpty()) return null;

        String cmdToken = tokens.get(0);
        String fixedCmd;

        String alias = RU_ALIASES.get(cmdToken.toLowerCase(Locale.ROOT));
        if (alias != null) {
            fixedCmd = alias;
        } else if (hasCyrillic(cmdToken)) {
            fixedCmd = toEnglish(cmdToken).toLowerCase(Locale.ROOT);
        } else {
            fixedCmd = cmdToken;
        }

        boolean somethingChanged = startsWithDot || !fixedCmd.equalsIgnoreCase(cmdToken);

        List<String> fixedTokens = new ArrayList<>();
        fixedTokens.add(fixedCmd);

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);

            // Текст в кавычках (например, "название предмета") никогда не трогаем!
            if (token.startsWith("\"") && token.endsWith("\"")) {
                fixedTokens.add(token);
                continue;
            }

            // Команда ah / auction: подкоманда (search, sell, buy...) переводится, а название предмета остаётся русским
            if (fixedCmd.equalsIgnoreCase("ah") || fixedCmd.equalsIgnoreCase("auction")) {
                if (i == 1) {
                    String subEn = toEnglish(token).toLowerCase(Locale.ROOT);
                    String subAlias = RU_ALIASES.get(token.toLowerCase(Locale.ROOT));
                    if (subAlias != null) {
                        fixedTokens.add(subAlias);
                        somethingChanged = true;
                        continue;
                    } else if (KNOWN_SUBCOMMANDS.contains(subEn)) {
                        fixedTokens.add(subEn);
                        somethingChanged = true;
                        continue;
                    }
                }
                // Аргументы поиска (например, шар, меч, алмаз) сохраняются на русском!
                fixedTokens.add(token);
                continue;
            }

            // Команды ЛС и телепортации (/m, /msg, /tell, /w, /tpa, /tp, /pay): никнейм на латинице
            if (i == 1 && (fixedCmd.equalsIgnoreCase("m") || fixedCmd.equalsIgnoreCase("msg")
                    || fixedCmd.equalsIgnoreCase("tell") || fixedCmd.equalsIgnoreCase("w")
                    || fixedCmd.equalsIgnoreCase("tpa") || fixedCmd.equalsIgnoreCase("tp")
                    || fixedCmd.equalsIgnoreCase("pay"))) {
                if (hasCyrillic(token)) {
                    fixedTokens.add(toEnglish(token));
                    somethingChanged = true;
                    continue;
                }
            }

            // Общая проверка подкоманд
            if (hasCyrillic(token)) {
                String subEn = toEnglish(token).toLowerCase(Locale.ROOT);
                String subAlias = RU_ALIASES.get(token.toLowerCase(Locale.ROOT));
                if (subAlias != null) {
                    fixedTokens.add(subAlias);
                    somethingChanged = true;
                    continue;
                } else if (KNOWN_SUBCOMMANDS.contains(subEn)) {
                    fixedTokens.add(subEn);
                    somethingChanged = true;
                    continue;
                }
            }

            fixedTokens.add(token);
        }

        if (!somethingChanged) {
            return null;
        }

        return String.join(" ", fixedTokens);
    }

    public static boolean handleChatMessage(String message, ClientPlayNetworkHandler handler) {
        if (internalExecution) return false;
        if (!isEnabled()) return false;

        String fixed = fixCommand(message);
        if (fixed != null) {
            executeFixed(fixed, handler);
            return true;
        }
        return false;
    }

    public static boolean handleChatCommand(String command, ClientPlayNetworkHandler handler) {
        if (internalExecution) return false;
        if (!isEnabled()) return false;

        String fixed = fixCommand("/" + command);
        if (fixed != null) {
            executeFixed(fixed, handler);
            return true;
        }
        return false;
    }

    private static void executeFixed(String fixedCommand, ClientPlayNetworkHandler handler) {
        MinecraftClient mc = MinecraftClient.getInstance();
        internalExecution = true;
        try {
            if (handler != null) {
                handler.sendChatCommand(fixedCommand);
            }

            boolean alert = ClientData.moduleStates.getOrDefault("AutoLayoutAlert", true);
            if (alert && mc.player != null) {
                mc.player.sendMessage(Text.literal("§7[§bLexora§7] §fРаскладка: §e/" + fixedCommand), false);
            }
        } finally {
            internalExecution = false;
        }
    }
}
