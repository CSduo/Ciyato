# Ciyato Launcher — ProGuard / R8 Rules
# Suggestion #114: comprehensive shrinking rules for safe release builds.

# ── Kotlin ────────────────────────────────────────────────────────────────────

-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlin.Unit
-dontwarn kotlinx.coroutines.**

# ── Compose / UI ──────────────────────────────────────────────────────────────

-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
# @Stable / @Immutable must not be renamed (Suggestion 115)
-keep @androidx.compose.runtime.Stable    class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }

# ── Ciyato data layer ─────────────────────────────────────────────────────────

# Preserve AppCategory.valueOf() — called at runtime from DataStore strings
-keepclassmembers enum com.ciyato.launcher.data.AppCategory {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Sealed result types — when() needs all subclasses to survive shrinking
-keep class com.ciyato.launcher.data.WeatherRepository$WeatherState    { *; }
-keep class com.ciyato.launcher.data.WeatherRepository$WeatherState$*  { *; }
-keep class com.ciyato.launcher.data.UiState                            { *; }
-keep class com.ciyato.launcher.data.UiState$*                          { *; }
-keep class com.ciyato.launcher.data.InstalledApp                       { *; }
-keep class com.ciyato.launcher.data.LauncherSettingsRepository         { *; }

# Preserve stack traces for local CrashReporter (Suggestion 144)
-keepattributes StackTrace

# ── JSON (bundled org.json) ───────────────────────────────────────────────────
-keep class org.json.** { *; }

# ── AndroidX ─────────────────────────────────────────────────────────────────

-keep class androidx.datastore.**    { *; }
-keep class androidx.navigation.**   { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── Entry points ──────────────────────────────────────────────────────────────

-keep class com.ciyato.launcher.MainActivity           { *; }
-keep class com.ciyato.launcher.LauncherHomeActivity   { *; }
-keep class com.ciyato.launcher.CiyatoApplication      { *; }

# ── ML Kit image labeling (bundled model) ────────────────────────────────────
# The bundled pipeline loads classes reflectively; stripping them crashes the
# release build the first time the AI photo scan runs.

-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_label.** { *; }
-keep class com.google.android.odml.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.odml.**

# ── Suppress harmless warnings ────────────────────────────────────────────────

-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
