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

        public String title, content;
        public NotifType type;
        public ItemStack itemStack;
        public long startTime;
        public long maxTime = 3500;
        public long fadeTime = 300;

        // Переменные для ЖЕЛЕ-АНИМАЦИИ
        public float animY = 0;
        public boolean animYSet = false;
        public float scale = 0.0f;     // Текущий размер (0.0 -> 1.0)
        public float velocity = 0.0f;  // Скорость пружины

        public Notif(String title, String content, NotifType type, ItemStack itemStack) {
            this.title = title;
            this.content = content;
            this.type = type;
            this.itemStack = itemStack;
            this.startTime = System.currentTimeMillis();
        }
    }

    private static final List<Notif> notifs = new ArrayList<>();

    private static boolean hpAlerted = false;
    private static final Map<String, Boolean> armorAlerted = new HashMap<>();
    private static final Set<StatusEffect> alertedPotions = new HashSet<>();

    public static List<Notif> getNotifs() {
        return notifs;
    }

    public static void show(String title, String content, NotifType type) {
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

        notifs.add(new Notif(title, content, type, itemStack));
        try {
            SoundUtil.playCustomSound("notification", 1.0f);
        } catch (Exception ignored) {}
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
        try { SoundUtil.playCustomSound("notification", 1.0f); } catch (Exception ignored) {}
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