package com.lexoravisauls.client.core;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.lexoravisauls.client.utils.CalloutManager;
import com.lexoravisauls.client.utils.ConfigManager;
import com.lexoravisauls.client.utils.PartyWaypoint;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BindManager {

    public static final int MOUSE_BIND_BASE  = -1000;
    public static final int SCROLL_UP_BIND   = -2001;
    public static final int SCROLL_DOWN_BIND = -2002;

    private static final Set<String>  previouslyPressedModules = new HashSet<>();
    private static final Set<Integer> previouslyPressedSpecial = new HashSet<>();

    private BindManager() {}

    public static boolean isActionBindKey(String key) {
        return key != null && (key.endsWith(" Action") || key.startsWith("Bind_") || key.equals("Radial Menu")
                || key.startsWith("Party") || key.startsWith("Callout"));
    }

    public static int encodeMouseBind(int mouseButton) {
        return MOUSE_BIND_BASE - mouseButton;
    }

    public static boolean isMouseBind(int bindCode) {
        return bindCode <= MOUSE_BIND_BASE;
    }

    public static int decodeMouseBind(int bindCode) {
        return MOUSE_BIND_BASE - bindCode;
    }

    public static boolean isScrollBind(int bindCode) {
        return bindCode == SCROLL_UP_BIND || bindCode == SCROLL_DOWN_BIND;
    }

    public static boolean isBindDown(long window, int bindCode) {
        if (bindCode == GLFW.GLFW_KEY_UNKNOWN || bindCode == -1) return false;
        if (isScrollBind(bindCode)) return false;

        if (isMouseBind(bindCode)) {
            int mouseButton = decodeMouseBind(bindCode);
            if (mouseButton >= 0 && mouseButton <= 7)
                return GLFW.glfwGetMouseButton(window, mouseButton) == GLFW.GLFW_PRESS;
            return false;
        }

        if (bindCode < 0) return false;
        return GLFW.glfwGetKey(window, bindCode) == GLFW.GLFW_PRESS;
    }

    public static int getStoredBindValue(String key) {
        if (key == null) return GLFW.GLFW_KEY_UNKNOWN;
        if (ClientData.moduleBinds.containsKey(key)) {
            Integer b = ClientData.moduleBinds.get(key);
            if (b != null && b != GLFW.GLFW_KEY_UNKNOWN && b != -1) return b;
        }
        if (ClientData.numSettings.containsKey(key)) {
            Float f = ClientData.numSettings.get(key);
            if (f != null && f.intValue() != GLFW.GLFW_KEY_UNKNOWN && f.intValue() != -1) return f.intValue();
        }
        if (LexoraGui.numSettings.containsKey(key)) {
            Float f = LexoraGui.numSettings.get(key);
            if (f != null && f.intValue() != GLFW.GLFW_KEY_UNKNOWN && f.intValue() != -1) return f.intValue();
        }
        return GLFW.GLFW_KEY_UNKNOWN;
    }

    public static void setStoredBindValue(String key, int bind) {
        if (key == null) return;
        if (bind == GLFW.GLFW_KEY_UNKNOWN || bind == -1) {
            ClientData.numSettings.remove(key);
            ClientData.moduleBinds.remove(key);
            LexoraGui.numSettings.remove(key);
        } else {
            ClientData.numSettings.put(key, (float) bind);
            ClientData.moduleBinds.put(key, bind);
            LexoraGui.numSettings.put(key, (float) bind);
        }
    }

    // =========================================================================
    //  Режим бинда (Удержание / Однократно) — для модулей и команд.
    // =========================================================================
    public enum BindMode { TOGGLE, HOLD }

    private static final Map<String, BindMode> moduleBindMode = new HashMap<>();

    public static BindMode getModuleBindMode(String moduleName) {
        return moduleBindMode.getOrDefault(moduleName, BindMode.TOGGLE);
    }

    /**
     * Привязать модуль к клавише. Поддерживает назначение нескольких модулей
     * на одну и ту же клавишу.
     */
    public static void setModuleBind(String moduleName, int keyCode, BindMode mode) {
        setStoredBindValue(moduleName, keyCode);
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode == -1) {
            moduleBindMode.remove(moduleName);
        } else {
            moduleBindMode.put(moduleName, mode);
        }
    }

    /** Имя модуля, привязанного к данной физической клавише, либо null. */
    public static String findModuleOnKey(int keyCode) {
        for (Map.Entry<String, Integer> e : ClientData.moduleBinds.entrySet()) {
            if (e.getValue() != null && e.getValue() == keyCode) return e.getKey();
        }
        return null;
    }

    private static String resolveModuleStateKey(String bindStorageKey) {
        if (bindStorageKey.startsWith("Toggle_")) {
            return bindStorageKey.substring("Toggle_".length());
        }
        return bindStorageKey;
    }

    // =========================================================================
    //  Бинды на произвольную команду
    // =========================================================================
    private static final Map<Integer, String>   commandBinds     = new HashMap<>();
    private static final Map<Integer, BindMode> commandBindMode  = new HashMap<>();
    private static final Map<Integer, Long>     commandLastRunMs = new HashMap<>();
    private static final long COMMAND_REPEAT_MS = 250L;

    public static String getCommandForKey(int keyCode) {
        return commandBinds.get(keyCode);
    }

    public static BindMode getCommandBindMode(int keyCode) {
        return commandBindMode.getOrDefault(keyCode, BindMode.TOGGLE);
    }

    public static void setCommandBind(int keyCode, String command, BindMode mode) {
        commandBinds.put(keyCode, command);
        commandBindMode.put(keyCode, mode);
    }

    public static void removeCommandBind(int keyCode) {
        commandBinds.remove(keyCode);
        commandBindMode.remove(keyCode);
        commandLastRunMs.remove(keyCode);
    }

    public static void clearAnyBindOnKey(int keyCode) {
    }

    public static void clearAnyBindOnKeyEverywhere(int keyCode, String exceptKey) {
    }

    private static void cleanActionModuleBinds() {
    }

    private static void runCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || command == null || command.isBlank()) return;
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        client.player.networkHandler.sendChatCommand(cmd);
    }

    // =========================================================================
    //  Главный обработчик всех бинд-нажатий
    // =========================================================================
    public static void handleInputEvents(MinecraftClient client) {
        cleanActionModuleBinds();

        if (client.player == null || client.currentScreen != null) {
            previouslyPressedModules.clear();
            previouslyPressedSpecial.clear();
            return;
        }

        long window = client.getWindow().getHandle();

        // ── 1. Бинд открытия GUI ────────────────────────────────────────────
        int guiBind = ClientData.numSettings
                .getOrDefault("ClickGuiBind", (float) GLFW.GLFW_KEY_RIGHT_SHIFT).intValue();
        if (guiBind != GLFW.GLFW_KEY_UNKNOWN && guiBind != -1 && isBindDown(window, guiBind)) {
            if (!previouslyPressedSpecial.contains(guiBind)) {
                previouslyPressedSpecial.add(guiBind);
                client.setScreen(new ModernClickGui());
                ModernClickGui.playSound("gui_open");
                return;
            }
        } else {
            previouslyPressedSpecial.remove(guiBind);
        }

        // ── 1b. Бинд отправки хелпы в пати (GPS метка + 3D луч + сигнал) ───
        int helpBind = getStoredBindValue("PartyHelpBind");
        if (helpBind == GLFW.GLFW_KEY_UNKNOWN) {
            helpBind = getStoredBindValue("PartyWaypointBind");
        }
        if (helpBind == GLFW.GLFW_KEY_UNKNOWN) {
            helpBind = getStoredBindValue("CalloutBind");
        }
        if (helpBind == GLFW.GLFW_KEY_UNKNOWN) {
            helpBind = getStoredBindValue("PartyBind");
        }
        if (helpBind != GLFW.GLFW_KEY_UNKNOWN && helpBind != -1 && isBindDown(window, helpBind)) {
            if (!previouslyPressedSpecial.contains(helpBind + 70000)) {
                previouslyPressedSpecial.add(helpBind + 70000);
                com.lexoravisauls.client.party.LexoraPartyClient.sendPartyHelp();
            }
        } else {
            previouslyPressedSpecial.remove(helpBind + 70000);
        }

        // ── 4. Бинды модулей (поддержка нескольких модулей на одной клавише) ─
        for (Map.Entry<String, Integer> entry : new HashMap<>(ClientData.moduleBinds).entrySet()) {
            String key = entry.getKey();
            Integer bind = entry.getValue();
            if (bind == null || bind == GLFW.GLFW_KEY_UNKNOWN || bind == -1) continue;
            if (isActionBindKey(key)) continue;

            String stateKey = resolveModuleStateKey(key);
            BindMode mode = getModuleBindMode(key);
            boolean down = isBindDown(window, bind);

            if (mode == BindMode.HOLD) {
                boolean wasDown = previouslyPressedModules.contains(key);
                if (down != wasDown) {
                    ClientData.moduleStates.put(stateKey, down);
                    ModernClickGui.playModuleToggleSound(down);
                    ConfigManager.saveConfig();
                    if (down) previouslyPressedModules.add(key); else previouslyPressedModules.remove(key);
                }
            } else {
                if (down) {
                    if (!previouslyPressedModules.contains(key)) {
                        previouslyPressedModules.add(key);
                        boolean currentState = ClientData.moduleStates.getOrDefault(stateKey, false);
                        boolean newState = !currentState;
                        ClientData.moduleStates.put(stateKey, newState);
                        ModernClickGui.playModuleToggleSound(newState);
                        ConfigManager.saveConfig();
                    }
                } else {
                    previouslyPressedModules.remove(key);
                }
            }
        }

        // ── 5. Бинды на команды (новое) ──────────────────────────────────────
        for (Map.Entry<Integer, String> e : commandBinds.entrySet()) {
            int bind = e.getKey();
            String command = e.getValue();
            BindMode mode = getCommandBindMode(bind);
            boolean down = isBindDown(window, bind);

            if (mode == BindMode.HOLD) {
                if (down) {
                    long now = System.currentTimeMillis();
                    long last = commandLastRunMs.getOrDefault(bind, 0L);
                    if (now - last >= COMMAND_REPEAT_MS) {
                        commandLastRunMs.put(bind, now);
                        runCommand(command);
                    }
                } else {
                    commandLastRunMs.remove(bind);
                }
            } else {
                if (down) {
                    if (!previouslyPressedSpecial.contains(bind)) {
                        previouslyPressedSpecial.add(bind);
                        runCommand(command);
                    }
                } else {
                    previouslyPressedSpecial.remove(bind);
                }
            }
        }
    }

    public static String formatBindName(int bind) {
        if (bind == GLFW.GLFW_KEY_UNKNOWN || bind == -1) return "NONE";
        if (bind == SCROLL_UP_BIND)   return "MW UP";
        if (bind == SCROLL_DOWN_BIND) return "MW DOWN";

        if (isMouseBind(bind)) {
            int mouseButton = decodeMouseBind(bind);
            return switch (mouseButton) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT   -> "LMB";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT  -> "RMB";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
                case 3 -> "M4";
                case 4 -> "M5";
                case 5 -> "M6"; // было пропущено раньше — падало в default "M5"
                default -> "M" + mouseButton;
            };
        }

        String keyName = GLFW.glfwGetKeyName(bind, 0);
        if (keyName != null && !keyName.isEmpty()) return keyName.toUpperCase(Locale.ROOT);

        return switch (bind) {
            case GLFW.GLFW_KEY_LEFT_SHIFT   -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT  -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL-> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT     -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT    -> "RALT";
            case GLFW.GLFW_KEY_SPACE        -> "SPACE";
            case GLFW.GLFW_KEY_TAB          -> "TAB";
            case GLFW.GLFW_KEY_ENTER        -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE    -> "BACKSPACE";
            case GLFW.GLFW_KEY_DELETE       -> "DELETE";
            case GLFW.GLFW_KEY_ESCAPE       -> "ESC";
            case GLFW.GLFW_KEY_UP           -> "UP";
            case GLFW.GLFW_KEY_DOWN         -> "DOWN";
            case GLFW.GLFW_KEY_LEFT         -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT        -> "RIGHT";
            default -> "KEY_" + bind;
        };
    }
}