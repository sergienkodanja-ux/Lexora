package com.lexoravisauls.client.core;

import com.lexoravisauls.client.utils.CalloutManager;
import com.lexoravisauls.client.utils.PartyWaypoint;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import com.lexoravisauls.client.gui.modern.ModernClickGui;
import com.lexoravisauls.client.utils.ConfigManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BindManager {

    public static final int MOUSE_BIND_BASE  = -1000;
    public static final int SCROLL_UP_BIND   = -2001;
    public static final int SCROLL_DOWN_BIND = -2002;

    private static final Set<String>  ACTION_MODULES    = new HashSet<>();
    private static final Set<Integer> previouslyPressed = new HashSet<>();

    static {
        ACTION_MODULES.add("Zoom");
        ACTION_MODULES.add("Free Look");
        ACTION_MODULES.add("Item Swap");
        ACTION_MODULES.add("Elytra Swap");
    }

    private BindManager() {}

    public static boolean isActionModule(String module) {
        return ACTION_MODULES.contains(module);
    }

    public static boolean isActionBindKey(String key) {
        return key != null && (key.endsWith(" Action") || key.startsWith("Bind_"));
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
        if (isActionBindKey(key))
            return ClientData.numSettings.getOrDefault(key, (float) GLFW.GLFW_KEY_UNKNOWN).intValue();
        return ClientData.moduleBinds.getOrDefault(key, GLFW.GLFW_KEY_UNKNOWN);
    }

    public static void setStoredBindValue(String key, int bind) {
        if (isActionBindKey(key)) {
            ClientData.numSettings.put(key, (float) bind);
        } else {
            if (bind == GLFW.GLFW_KEY_UNKNOWN) ClientData.moduleBinds.remove(key);
            else ClientData.moduleBinds.put(key, bind);
        }
    }

    // =========================================================================
    //  НОВОЕ: режим бинда (Удержание / Однократно) — для модулей и команд.
    //  Раньше эта логика была заведена (isActionBindKey/ACTION_MODULES), но
    //  реально нигде не использовалась — теперь handleInputEvents ниже
    //  действительно её учитывает.
    // =========================================================================
    public enum BindMode { TOGGLE, HOLD }

    // moduleName -> режим. Если записи нет — TOGGLE (как было раньше, для
    // обратной совместимости со старыми сохранёнными биндами).
    private static final Map<String, BindMode> moduleBindMode = new HashMap<>();

    public static BindMode getModuleBindMode(String moduleName) {
        return moduleBindMode.getOrDefault(moduleName, BindMode.TOGGLE);
    }

    /**
     * Привязать модуль к клавише. Снимает любой другой бинд (модуль ИЛИ
     * команду), который сейчас висит на этой же физической клавише — один
     * бинд = одна клавиша, как договорились.
     */
    public static void setModuleBind(String moduleName, int keyCode, BindMode mode) {
        clearAnyBindOnKey(keyCode);
        ClientData.moduleBinds.put(moduleName, keyCode);
        moduleBindMode.put(moduleName, mode);
    }

    /** Имя модуля, привязанного к данной физической клавише, либо null. */
    public static String findModuleOnKey(int keyCode) {
        for (Map.Entry<String, Integer> e : ClientData.moduleBinds.entrySet()) {
            if (e.getValue() != null && e.getValue() == keyCode) return e.getKey();
        }
        return null;
    }

    /**
     * ФИКС (ключ хранения бинда != ключ состояния модуля):
     * Для экшн-модулей (Zoom, Free Look, Item Swap, Elytra Swap) GUI хранит
     * их собственный on/off-бинд в ClientData.moduleBinds под ключом
     * "Toggle_<Модуль>" (см. ModernClickGui: bindMapKey = "Toggle_" + module).
     * Но ClientData.moduleStates (реальное состояние вкл/выкл, которое читают
     * сами модули, например Zoom.tick()) всегда ключуется чистым именем
     * модуля, например "Zoom" — БЕЗ префикса "Toggle_".
     * <p>
     * Раньше handleInputEvents ниже писал состояние прямо под ключом бинда
     * ("Toggle_Zoom"), из-за чего физический бинд модуля вообще не влиял на
     * реальное состояние "Zoom" — переключался "мёртвый", никем не читаемый
     * флаг (только звук и saveConfig создавали иллюзию переключения).
     * <p>
     * Этот метод переводит ключ ХРАНЕНИЯ бинда в правильный ключ СОСТОЯНИЯ.
     */
    private static String resolveModuleStateKey(String bindStorageKey) {
        if (bindStorageKey.startsWith("Toggle_")) {
            String moduleName = bindStorageKey.substring("Toggle_".length());
            if (isActionModule(moduleName)) return moduleName;
        }
        return bindStorageKey;
    }

    // =========================================================================
    //  НОВОЕ: бинды на произвольную команду
    // =========================================================================
    private static final Map<Integer, String>   commandBinds     = new HashMap<>();
    private static final Map<Integer, BindMode> commandBindMode  = new HashMap<>();
    private static final Map<Integer, Long>      commandLastRunMs = new HashMap<>();
    private static final long COMMAND_REPEAT_MS = 250L; // защита от спама при удержании

    public static String getCommandForKey(int keyCode) {
        return commandBinds.get(keyCode);
    }

    public static BindMode getCommandBindMode(int keyCode) {
        return commandBindMode.getOrDefault(keyCode, BindMode.TOGGLE);
    }

    public static void setCommandBind(int keyCode, String command, BindMode mode) {
        clearAnyBindOnKey(keyCode);
        commandBinds.put(keyCode, command);
        commandBindMode.put(keyCode, mode);
    }

    public static void removeCommandBind(int keyCode) {
        commandBinds.remove(keyCode);
        commandBindMode.remove(keyCode);
        commandLastRunMs.remove(keyCode);
    }

    /** Снять ЛЮБОЙ бинд (модуль или команду) с физической клавиши. */
    public static void clearAnyBindOnKey(int keyCode) {
        String existingModule = findModuleOnKey(keyCode);
        if (existingModule != null) {
            ClientData.moduleBinds.remove(existingModule);
            moduleBindMode.remove(existingModule);
        }
        removeCommandBind(keyCode);
    }

    /**
     * ФИКС (защита от коллизии при назначении бинда через GUI):
     * Снимает АБСОЛЮТНО любой бинд с указанной физической клавиши/кнопки —
     * не только модули/команды (как clearAnyBindOnKey), но и внутренние
     * action-параметры вроде "Zoom Action", "Item Swap Action", "Bind_*",
     * которые хранятся не в moduleBinds, а в ClientData.numSettings и
     * раньше вообще не проверялись на конфликт.
     * <p>
     * Вызывать ПЕРЕД тем, как записать новый бинд в keyPressed/mouseClicked
     * в ModernClickGui — тогда одна и та же клавиша физически не сможет
     * оказаться одновременно и на тумблере модуля, и на его же Action-бинде.
     *
     * @param keyCode   код клавиши/кнопки мыши (как из keyPressed / encodeMouseBind)
     * @param exceptKey ключ, который сейчас сам записывается — его не трогаем,
     *                  даже если на нём уже стоит то же значение
     */
    public static void clearAnyBindOnKeyEverywhere(int keyCode, String exceptKey) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode == -1) return;

        // 1. Бинды модулей (toggle/hold) + бинды команд.
        clearAnyBindOnKey(keyCode);

        // 2. Внутренние action-бинды (Zoom Action, Item Swap Action, Bind_*),
        //    которые живут в ClientData.numSettings, а не в moduleBinds.
        for (String k : new HashSet<>(ClientData.numSettings.keySet())) {
            if (!isActionBindKey(k) || k.equals(exceptKey)) continue;
            Float v = ClientData.numSettings.get(k);
            if (v != null && v.intValue() == keyCode) {
                ClientData.numSettings.put(k, (float) GLFW.GLFW_KEY_UNKNOWN);
            }
        }
    }

    /**
     * ФИКС (баг "бинд возвращается сам после перезахода, невидим в GUI"):
     * Карточка модуля для экшн-модулей всегда читает бинд по ключу
     * "Toggle_" + module (см. ModernClickGui: bindMapKey). Но если где-то
     * в ClientData.moduleBinds завалялся "сырой" ключ без префикса —
     * например "Zoom" вместо "Toggle_Zoom" (вероятно из дефолтных биндов,
     * заведённых ещё до появления префикса "Toggle_") — GUI его никогда не
     * покажет, а handleInputEvents ниже всё равно честно перебирает ВСЕ
     * ключи moduleBinds.keySet() и его отрабатывает. Отсюда "невидимый"
     * бинд, который переживает сохранение конфига и возвращается заново.
     * <p>
     * Подчищаем это автоматически при каждом вызове handleInputEvents:
     * переносим значение сырого ключа в "Toggle_"+module (если там ещё
     * пусто) и удаляем сырой ключ. Дёшево — всего 4 экшн-модуля, можно
     * смело звать каждый тик.
     */
    private static void migrateLegacyActionModuleBinds() {
        for (String module : ACTION_MODULES) {
            Integer legacy = ClientData.moduleBinds.remove(module);
            if (legacy == null) continue;

            String properKey = "Toggle_" + module;
            boolean properAlreadySet = ClientData.moduleBinds.containsKey(properKey)
                    && ClientData.moduleBinds.get(properKey) != GLFW.GLFW_KEY_UNKNOWN;

            if (legacy != GLFW.GLFW_KEY_UNKNOWN && !properAlreadySet) {
                ClientData.moduleBinds.put(properKey, legacy);
            }
            // если properAlreadySet — сырой ключ просто отбрасываем, чтобы
            // не дублировать переключение одним и тем же физическим биндом
        }
    }

    /**
     * ПРЕДПОЛОЖЕНИЕ: стандартный ванильный способ — sendChatCommand через
     * networkHandler. Если у вас свой обработчик команд (не через чат) —
     * замените тело этого метода на свой вызов.
     */
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
        migrateLegacyActionModuleBinds(); // ФИКС: подчистить "невидимые" сырые бинды перед обработкой

        if (client.player == null || client.currentScreen != null) {
            previouslyPressed.clear();
            return;
        }

        long window = client.getWindow().getHandle();

        // ── 1. Бинд открытия GUI ────────────────────────────────────────────
        int guiBind = ClientData.numSettings
                .getOrDefault("ClickGuiBind", (float) GLFW.GLFW_KEY_RIGHT_SHIFT).intValue();
        if (guiBind != GLFW.GLFW_KEY_UNKNOWN && isBindDown(window, guiBind)) {
            if (!previouslyPressed.contains(guiBind)) {
                previouslyPressed.add(guiBind);
                client.setScreen(new ModernClickGui());
                ModernClickGui.playSound("gui_open");
                return;
            }
        } else {
            previouslyPressed.remove(guiBind);
        }

        // ── 2. Бинд Callout "Позвать на мету" ───────────────────────────────
        int calloutBind = ClientData.numSettings
                .getOrDefault("CalloutBind", (float) GLFW.GLFW_KEY_UNKNOWN).intValue();
        if (calloutBind != GLFW.GLFW_KEY_UNKNOWN && calloutBind != -1
                && isBindDown(window, calloutBind)) {
            if (!previouslyPressed.contains(calloutBind + 90000)) { // offset чтобы не пересекался с модульными
                previouslyPressed.add(calloutBind + 90000);
                CalloutManager.sendCallout();
            }
        } else {
            previouslyPressed.remove(calloutBind + 90000);
        }

        // ── 3. Бинд метки пати ──────────────────────────────────────────────
        int waypointBind = ClientData.numSettings
                .getOrDefault("PartyWaypointBind", (float) GLFW.GLFW_KEY_UNKNOWN).intValue();
        if (waypointBind != GLFW.GLFW_KEY_UNKNOWN && waypointBind != -1
                && isBindDown(window, waypointBind)) {
            if (!previouslyPressed.contains(waypointBind + 80000)) {
                previouslyPressed.add(waypointBind + 80000);
                PartyWaypoint.placeWaypoint();
            }
        } else {
            previouslyPressed.remove(waypointBind + 80000);
        }

        // ── 4. Бинды модулей — теперь с реальным Hold/Toggle ────────────────
        for (String key : ClientData.moduleBinds.keySet()) {
            if (isActionBindKey(key)) continue;

            int bind = ClientData.moduleBinds.get(key);
            if (bind == GLFW.GLFW_KEY_UNKNOWN) continue;

            // ФИКС: писать/читать состояние нужно под реальным именем модуля
            // ("Zoom"), а не под ключом хранения бинда ("Toggle_Zoom").
            String stateKey = resolveModuleStateKey(key);

            BindMode mode = getModuleBindMode(key);
            boolean down = isBindDown(window, bind);

            if (mode == BindMode.HOLD) {
                boolean wasDown = previouslyPressed.contains(bind);
                if (down != wasDown) {
                    ClientData.moduleStates.put(stateKey, down);
                    ModernClickGui.playModuleToggleSound(down);
                    ConfigManager.saveConfig();
                    if (down) previouslyPressed.add(bind); else previouslyPressed.remove(bind);
                }
            } else {
                if (down) {
                    if (!previouslyPressed.contains(bind)) {
                        previouslyPressed.add(bind);
                        boolean currentState = ClientData.moduleStates.getOrDefault(stateKey, false);
                        boolean newState = !currentState;
                        ClientData.moduleStates.put(stateKey, newState);
                        ModernClickGui.playModuleToggleSound(newState);
                        ConfigManager.saveConfig();
                    }
                } else {
                    previouslyPressed.remove(bind);
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
                    if (!previouslyPressed.contains(bind)) {
                        previouslyPressed.add(bind);
                        runCommand(command);
                    }
                } else {
                    previouslyPressed.remove(bind);
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