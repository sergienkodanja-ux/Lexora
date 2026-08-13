# ============================================================================
# ProGuard правила для Lexora Visuals — МАКСИМАЛЬНОЕ ОСЛАБЛЕНИЕ
# ============================================================================
#
# Плащ пропал (без краша) при частичном keep — значит проблема не только
# в переименовании, а в том, что ProGuard всё ещё что-то трогает в структуре
# рендер-пайплайна (порядок методов/полей, инлайнинг, удаление "неиспользуемых"
# приватных членов ищё каким-то анализом, или атрибуты, влияющие на то, как
# WaveyCapeFeatureRenderer кастует/ищет CapeHolder на игроке).
#
# РЕШЕНИЕ: -dontobfuscate ПОЛНОСТЬЮ отключает переименование для ВСЕГО
# приложения (не только твоего пакета) — оставляя ProGuard'у только роль
# "склейки" jar + удаления неиспользуемых library-классов, если он вообще
# что-то удаляет (у нас и так -dontshrink). Это самый безопасный режим:
# ProGuard практически не меняет байткод твоего мода вообще.
#
# ВАЖНО ПОНИМАТЬ: это означает что обфускации кода фактически больше нет.
# Если через какое-то время нужно будет вернуть защиту — потребуется
# отдельно разбираться, какой конкретно механизм (не отдельный флаг,
# а что-то в связке classes ProGuard видит) ломает рендер-пайплайн,
# скорее всего сравнивая mapping.txt до/после точечных правок.

-dontobfuscate

# ── Отключаем шринк/оптимизацию (чтобы не ломать миксины и рендер) ─────────
-dontshrink
-dontoptimize
-dontpreverify
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

# ── На всякий случай держим явные keep (не помешает, даже если -dontobfuscate
#    уже гарантирует отсутствие переименований) ─────────────────────────────
-keep class com.lexoravisauls.** { *; }
-keepclassmembers class com.lexoravisauls.** { *; }
-dontwarn com.lexoravisauls.**

-keep class net.minecraft.** { *; }
-keep class net.fabricmc.** { *; }
-keep class org.spongepowered.asm.** { *; }
-keep class com.mojang.** { *; }
-dontwarn net.minecraft.**
-dontwarn net.fabricmc.**
-dontwarn org.spongepowered.**
-dontwarn com.mojang.**

-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class club.minnced.** { *; }
-keep class dev.redstones.** { *; }
-keep class org.freedesktop.dbus.** { *; }
-keep class com.github.hypfvieh.** { *; }
-keep class org.luaj.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn club.minnced.**
-dontwarn dev.redstones.**
-dontwarn org.freedesktop.dbus.**
-dontwarn org.luaj.**
-dontwarn com.google.gson.**

-keep @org.spongepowered.asm.mixin.Mixin class ** { *; }
-keepclassmembers @org.spongepowered.asm.mixin.Mixin class * { *; }

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes StackMapTable

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-printmapping build/libs/mapping.txt