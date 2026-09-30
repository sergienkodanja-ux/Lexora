package com.lexoravisauls.client.utils;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;

import java.util.*;

public class NotifManager {

    // Добавлен статус ERROR для брони и ХП
    public enum NotifType { SUCCESS, WARNING, ERROR, MODULE_ON, MODULE_OFF, SWAP }

    public static class Notif {
        public String id;
        public boolean isTimer = false;
        public boolean isModuleToggle = false;
        public String moduleName = null;
        public boolean moduleEnabled = false;

        public String title, content;
        public NotifType type;
        public ItemStack itemStack;
        public long startTime;
        public long maxTime = 2500;
        public long fadeTime = 350;

        // Переменные для анимаций стиля wedal.dlc
        public float appear = 0.0f;
        public float toggleProgress = 0.0f;
        public float currentY = -1.0f;
        public boolean initializedY = false;

        // Совместимость со старыми полями
        public float animY = 0;
        public boolean animYSet = false;
        public float scale = 0.0f;
        public float velocity = 0.0f;
        public float animProgress = 0.0f;

        public Notif(String title, String content, NotifType type, ItemStack itemStack) {
            this.title = title;
            this.content = content;
            this.type = type;
            this.itemStack = itemStack;
            this.startTime = System.currentTimeMillis();
            this.maxTime = 2500;
            this.fadeTime = 350;
        }

        public Notif(String moduleName, boolean enabled) {
            this.isModuleToggle = true;
            this.moduleName = moduleName;
            this.moduleEnabled = enabled;
            this.toggleProgress = enabled ? 0.0f : 1.0f;
            this.type = enabled ? NotifType.MODULE_ON : NotifType.MODULE_OFF;
            this.startTime = System.currentTimeMillis();
            this.maxTime = 2500;
            this.fadeTime = 350;
        }
    }

    public static void notifyModuleToggle(String moduleName, boolean enabled) {
        if (!LexoraGui.moduleStates.getOrDefault("Notifications", true)) return;

        // Если уже есть плашка для этого модуля, обновляем её состояние и продлеваем время
        for (Notif n : notifs) {
            if (n.isModuleToggle && Objects.equals(n.moduleName, moduleName)) {
                n.moduleEnabled = enabled;
                n.type = enabled ? NotifType.MODULE_ON : NotifType.MODULE_OFF;
                n.startTime = System.currentTimeMillis();
                // Звук НЕ играем здесь — он уже играется в ModernClickGui.playModuleToggleSound()
                return;
            }
        }

        notifs.add(new Notif(moduleName, enabled));
        // Звук НЕ играем здесь — он уже играется в ModernClickGui.playModuleToggleSound()
    }

    private static final List<Notif> notifs = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static boolean hpAlerted = false;
    private static final Map<String, Boolean> armorAlerted = new HashMap<>();
    private static final Set<StatusEffect> alertedPotions = new HashSet<>();

    public static List<Notif> getNotifs() {
        return notifs;
    }

    /** Возвращает имя звука уведомления по текущей настройке */
    public static String getNotifSoundName() {
        String mode = LexoraGui.modeSettings.getOrDefault("Notif Sound Mode", "Звук 1");
        if (mode == null) mode = "Звук 1";
        return switch (mode) {
            case "Звук 2" -> "notification2";
            case "Звук 3" -> "notification3";
            default -> "notification";
        };
    }

    public static void show(String title, String content, NotifType type) {
        show(title, content, type, null);
    }

    public static void notify(String title, String content, NotifType type) {
        show(title, content, type, null);
    }

    public static void show(String title, String content, NotifType type, ItemStack itemStack) {
        // ЖЕСТКИЙ ФИКС: Если уведомления о свапе выключены — блокируем моментально
        if (type == NotifType.SWAP) {
            boolean swapEnabled = LexoraGui.moduleStates.getOrDefault("Notif Swap", ClientData.moduleStates.getOrDefault("Notif Swap", true));
            if (!swapEnabled) {
                return;
            }
        }

        boolean soundEnabled = LexoraGui.moduleStates.getOrDefault("Notif Sound", true);
        notifs.add(new Notif(title, content, type, itemStack));
        if (soundEnabled) {
            try {
                SoundUtil.playCustomSound(getNotifSoundName(), 1.0f);
            } catch (Exception ignored) {}
        }
    }

    public static void showWithItem(String title, String content, NotifType type, ItemStack stack) {
        show(title, content, type, stack);
    }

    public static void startTimer(String id, String title, float seconds, NotifType type) {
        notifs.removeIf(n -> id.equals(n.id));
        Notif notif = new Notif(title, "Осталось: " + seconds + " сек.", type, null);
        notif.id = id;
        notif.isTimer = true;
        notif.maxTime = (long) (seconds * 1000);
        notifs.add(notif);
        boolean soundEnabled = LexoraGui.moduleStates.getOrDefault("Notif Sound", true);
        if (soundEnabled) {
            try { SoundUtil.playCustomSound(getNotifSoundName(), 1.0f); } catch (Exception ignored) {}
        }
    }

    public static void tick(MinecraftClient mc) {
        // ФИКС: очистка просроченных уведомлений идёт ПЕРВОЙ и БЕЗУСЛОВНО.
        // Раньше removeIf стоял ниже раннего "return" — если тоггл "Notifications"
        // был выключен ИЛИ mc.player временно null (респавн/лаг/дисконнект),
        // весь метод выходил ДО очистки. При этом show() (например из LootNotifier)
        // не проверяет этот тоггл вообще и продолжает добавлять уведомления.
        // Итог: список рос, просроченные записи не удалялись, и на следующий рендер
        // ловили баг с alpha (просрочка => "вспышка" на полную непрозрачность),
        // а без tick() эта "вспышка" никогда не убиралась — visible "навсегда до рестарта".
        notifs.removeIf(n -> System.currentTimeMillis() - n.startTime > n.maxTime);

        if (!LexoraGui.moduleStates.getOrDefault("Notifications", true) || mc.player == null) return;

        // ХП -> статус ERROR (Красная точка)
        if (LexoraGui.moduleStates.getOrDefault("Notif HP", true)) {
            if (mc.player.getHealth() <= 8.0f) {
                if (!hpAlerted) {
                    show("Низкое здоровье!", "Срочно восстановите ХП", NotifType.ERROR);
                    hpAlerted = true;
                }
            } else {
                hpAlerted = false;
            }
        }

        // Броня -> статус ERROR (Красная точка)
        if (LexoraGui.moduleStates.getOrDefault("Notif Armor", true)) {
            String[] slotNames = {"Ботинки", "Поножи", "Нагрудник", "Шлем"};
            int idx = 0;
            for (ItemStack armor : mc.player.getArmorItems()) {
                String key = "armor_" + idx;
                if (armor.isEmpty() || !armor.isDamageable()) {
                    armorAlerted.remove(key);
                    idx++;
                    continue;
                }
                float percent = 1.0f - ((float) armor.getDamage() / armor.getMaxDamage());
                if (percent < 0.1f) {
                    if (!armorAlerted.getOrDefault(key, false)) {
                        show(slotNames[idx] + " ломается!", "Прочность < 10%", NotifType.ERROR, armor.copy());
                        armorAlerted.put(key, true);
                    }
                } else {
                    armorAlerted.put(key, false);
                }
                idx++;
            }
        }

        // Зелья -> статус WARNING (Желтая точка)
        if (LexoraGui.moduleStates.getOrDefault("Notif Potions", true)) {
            Set<StatusEffect> activeEffects = new HashSet<>();
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                StatusEffect type = effect.getEffectType().value();
                activeEffects.add(type);
                if (type.isBeneficial() && effect.getDuration() == 60) {
                    if (!alertedPotions.contains(type)) {
                        show("Зелье заканчивается!", type.getName().getString() + " истекает", NotifType.WARNING);
                        alertedPotions.add(type);
                    }
                }
            }
            alertedPotions.retainAll(activeEffects);
        }
    }
}