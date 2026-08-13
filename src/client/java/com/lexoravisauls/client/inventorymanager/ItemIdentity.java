package com.lexoravisauls.client.inventorymanager;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Утилиты для определения "личности" предмета: id + кастомное имя (наковальня) +
 * отпечаток скина для голов игрока + отпечаток содержимого для зелий + отпечаток
 * CustomModelData (кастомная модель/ретекстур) + отпечаток Lore (текст-описание,
 * часто используется под "перки"/особые свойства предмета на серверах). Специально
 * НЕ учитываем зачарования/прочность при сравнении — раскладка следит за тем,
 * ЧТО и ГДЕ лежит, а не за точным NBT/компонентами стака целиком.
 */
public final class ItemIdentity {

    private ItemIdentity() {}

    public static String idOf(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id.toString();
    }

    /** Кастомное имя (заданное в наковальне), либо null, если его нет. */
    public static String customNameOf(ItemStack stack) {
        Text text = stack.get(DataComponentTypes.CUSTOM_NAME);
        return text != null ? text.getString() : null;
    }

    /** Сырые данные профиля головы (не отпечаток!) — для реконструкции иконки. Может быть EMPTY. */
    private record SkinData(String name, String id, String textureValue, String textureSignature) {
        private static final SkinData EMPTY = new SkinData(null, null, null, null);
    }

    /**
     * Читает профиль головы за один проход. Обёрнуто в try/catch: если конкретная
     * сборка authlib когда-нибудь разойдётся по мелочи с ожидаемым API, лучше молча
     * остаться без данных, чем уронить сохранение/загрузку раскладки целиком.
     */
    private static SkinData readSkinData(ItemStack stack) {
        try {
            ProfileComponent profile = stack.get(DataComponentTypes.PROFILE);
            if (profile == null) return SkinData.EMPTY;

            String name = profile.name().orElse(null);
            String id = profile.id().map(UUID::toString).orElse(null);
            String value = null, signature = null;

            Collection<Property> textures = profile.properties().get("textures");
            if (textures != null && !textures.isEmpty()) {
                Property first = textures.iterator().next();
                value = invokeStringGetter(first, "value", "getValue");
                signature = invokeStringGetter(first, "signature", "getSignature");
            }
            return new SkinData(name, id, value, signature);
        } catch (Throwable ignored) {
            return SkinData.EMPTY;
        }
    }

    /**
     * Достаёт строковое значение через рефлексию, пробуя несколько вариантов имени
     * метода по очереди. У com.mojang.authlib.properties.Property конкретно
     * getValue() реально переименовывали между версиями Minecraft (record-рефактор
     * той же природы, что и у ProfileComponent) — известный сломанный API,
     * см. напр. GeyserMC/Floodgate#464 ("NoSuchMethodError: Property.getValue()").
     * Поэтому не зашиваем конкретное имя метода на этапе компиляции.
     */
    private static String invokeStringGetter(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Object result = target.getClass().getMethod(name).invoke(target);
                if (result instanceof String) return (String) result;
            } catch (Throwable ignored) {
                // пробуем следующее имя
            }
        }
        return null;
    }

    /**
     * Отпечаток текстуры скина для minecraft:player_head с профилем (декоративные
     * "кастомные головы" на серверах — обычно это один и тот же item id без
     * кастомного имени, различаются только скином). Для всех остальных предметов,
     * и для голов без профиля/текстуры — пустая строка. Это СВЁРНУТЫЙ ключ для
     * сравнения — сырые данные для восстановления иконки см. skinName/skinId/
     * skinTextureValue/skinTextureSignature.
     */
    public static String skinFingerprint(ItemStack stack) {
        SkinData d = readSkinData(stack);
        StringBuilder sb = new StringBuilder();
        if (d.name() != null) sb.append(d.name());
        sb.append('|');
        if (d.id() != null) sb.append(d.id());
        if (d.textureValue() != null && !d.textureValue().isEmpty()) sb.append('|').append(d.textureValue());
        return sb.toString();
    }

    public static String skinName(ItemStack stack) { return readSkinData(stack).name(); }
    public static String skinId(ItemStack stack) { return readSkinData(stack).id(); }
    public static String skinTextureValue(ItemStack stack) { return readSkinData(stack).textureValue(); }
    public static String skinTextureSignature(ItemStack stack) { return readSkinData(stack).textureSignature(); }

    /**
     * Отпечаток содержимого зелья (minecraft:potion / splash_potion / lingering_potion
     * / tipped_arrow) — тип зелья + кастомный цвет + число кастомных эффектов.
     * Без этого "Зелье лечения" и "Зелье силы" неотличимы друг от друга: у обоих
     * один и тот же item id и обычно нет кастомного имени, различает их только
     * DataComponentTypes.POTION_CONTENTS. На сервере, где зелья стакаются (кастомный
     * max stack size), это особенно заметно — путаница именно там, где стак > 1.
     * <p>
     * DataComponentTypes.POTION_CONTENTS даёт PotionContentsComponent(potion,
     * customColor, customEffects) — сигнатура именно для 1.21.1–1.21.4 (в 1.21.5+
     * туда добавили ещё customName, но нам актуальна версия мода).
     */
    public static String potionFingerprint(ItemStack stack) {
        try {
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null) return "";

            StringBuilder sb = new StringBuilder();
            contents.potion().ifPresent(entry -> entry.getKey().ifPresent(key -> sb.append(key.getValue())));
            sb.append('|');
            contents.customColor().ifPresent(sb::append);
            sb.append('|').append(contents.customEffects().size());
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Отпечаток CustomModelData — 4 списка (floats, flags, strings, colors), формат
     * с 1.21.4 (раньше было одно число). Частая причина "разных вариантов" одного
     * и того же vanilla id: ретекстур/другая модель через ресурспак — тот же меч
     * визуально другой, потому что у него другая CustomModelData, а не потому что
     * это другой item. Кастомные "перки" на серверах очень часто реализованы именно
     * так (разная модель под разный перк на одном и том же minecraft:diamond_sword).
     */
    public static String customModelFingerprint(ItemStack stack) {
        try {
            CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (cmd == null) return "";
            return cmd.floats() + "|" + cmd.flags() + "|" + cmd.strings() + "|" + cmd.colors();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Отпечаток лора (текст под названием предмета) — второе частое место, где
     * серверы описывают "перки"/особые свойства без изменения кастомного имени.
     */
    public static String loreFingerprint(ItemStack stack) {
        try {
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore == null || lore.lines().isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Text line : lore.lines()) sb.append(line.getString()).append('\n');
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String matchKeyOf(ItemStack stack) {
        LoadoutSlotData tmp = new LoadoutSlotData(0, idOf(stack), customNameOf(stack), stack.getCount(), skinFingerprint(stack));
        tmp.potionKey = potionFingerprint(stack);
        tmp.modelKey = customModelFingerprint(stack);
        tmp.loreKey = loreFingerprint(stack);
        return tmp.matchKey();
    }

    /** Имя для отображения и для запроса на АХ: кастомное, если есть, иначе ванильное. */
    public static String displayName(String itemId, String customName) {
        if (customName != null && !customName.isEmpty()) return customName;
        return vanillaName(itemId);
    }

    public static String vanillaName(String itemId) {
        return new ItemStack(itemFromId(itemId)).getName().getString();
    }

    public static Item itemFromId(String itemId) {
        if (itemId == null) return Items.AIR;
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return Items.AIR;
        return Registries.ITEM.get(id);
    }

    /** "Шаблонный" стак только для отрисовки иконки (без реального инстанса в инвентаре, без текстуры головы). */
    public static ItemStack templateStack(String itemId) {
        return new ItemStack(itemFromId(itemId), 1);
    }

    /**
     * То же самое, но по возможности с ВОССТАНОВЛЕННОЙ текстурой головы — собирает
     * ProfileComponent обратно из сырых skinName/skinId/skinTextureValue/
     * skinTextureSignature, сохранённых при captureCurrent(...). Если данных нет
     * (обычный предмет) или собрать профиль не получилось (сборка authlib разошлась
     * с ожидаемым API) — тихо возвращает обычный бланк-стак без текстуры, не падает.
     * <p>
     * Для зелий иконка НЕ восстанавливает точный цвет/тип — только сравнение
     * (matchKey) учитывает содержимое зелья, отображение иконки в UI пока нет
     * (можно добавить отдельно, если понадобится).
     */
    public static ItemStack templateStack(LoadoutSlotData d) {
        ItemStack stack = new ItemStack(itemFromId(d.itemId), 1);
        if (d.skinTextureValue == null || d.skinTextureValue.isEmpty()) return stack;

        try {
            UUID uuid = (d.skinId != null && !d.skinId.isEmpty()) ? UUID.fromString(d.skinId) : new UUID(0L, 0L);
            String name = (d.skinName != null && !d.skinName.isEmpty()) ? d.skinName : "CustomHead";

            Property property = buildProperty(d.skinTextureValue, d.skinTextureSignature);
            if (property == null) return stack;

            GameProfile gameProfile = new GameProfile(uuid, name);
            gameProfile.getProperties().put("textures", property);

            ProfileComponent profile = new ProfileComponent(
                    Optional.ofNullable(d.skinName),
                    Optional.of(uuid),
                    gameProfile.getProperties(),
                    gameProfile
            );
            stack.set(DataComponentTypes.PROFILE, profile);
        } catch (Throwable ignored) {
            // Не восстановили профиль — покажем обычную голову. Это не критично:
            // реальная расстановка предметов идёт живыми стаками из инвентаря,
            // а не этой реконструкцией — она только для иконок в UI.
        }
        return stack;
    }

    /**
     * Собирает Property через рефлексию, пробуя оба варианта конструктора (с
     * подписью и без) — конкретная сигнатура тоже могла измениться вместе с
     * переименованием getValue() → value() (см. invokeStringGetter).
     */
    private static Property buildProperty(String value, String signature) {
        if (signature != null && !signature.isEmpty()) {
            try {
                return Property.class.getConstructor(String.class, String.class, String.class)
                        .newInstance("textures", value, signature);
            } catch (Throwable ignored) {
                // пробуем короткий конструктор ниже
            }
        }
        try {
            return Property.class.getConstructor(String.class, String.class).newInstance("textures", value);
        } catch (Throwable ignored) {
            return null;
        }
    }
}