package com.lexoravisauls.client.cosmetics;

// Обычный публичный Java-интерфейс — НЕ миксин-класс. ВАЖНО: лежит НЕ в
// пакете com.lexoravisauls.client.mixin — тот пакет целиком объявлен как
// "mixin package" в lexoravisauls.mixins.json, и Mixin framework блокирует
// ПРЯМУЮ загрузку ЛЮБОГО класса из такого пакета, даже обычных интерфейсов,
// даже если они не зарегистрированы в списке mixins (подтверждено крашем:
// "IllegalClassLoadError: ... is in a defined mixin package ... and cannot
// be referenced directly"). Интерфейс лежит здесь, в cosmetics — вне
// защищённого пакета, поэтому его можно нормально импортировать и кастовать
// откуда угодно, включая сами миксины (импортировать класс ИЗ mixin-пакета
// В другие места нельзя, но импортировать класс extern В миксин — можно).
public interface IPlayerEntityRenderStateSpearAccess {
    boolean lexora$isHoldingSword();

    void lexora$setHoldingSword(boolean value);
}