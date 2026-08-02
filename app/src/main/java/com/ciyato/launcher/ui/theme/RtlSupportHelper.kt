package com.ciyato.launcher.ui.theme

import android.content.Context
import android.os.LocaleList
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * RtlSupportHelper — Suggestion #110
 * RTL (right-to-left) layout support for Arabic, Hebrew, Farsi, Urdu.
 * Provides a Compose wrapper that switches LayoutDirection and helpers
 * for detecting the current system locale direction.
 */
object RtlSupportHelper {

    private val RTL_LOCALES = setOf("ar", "he", "fa", "ur", "dv", "ha", "ps", "sd", "ug", "yi")

    /** Returns true if the current device locale is RTL. */
    fun isRtl(context: Context): Boolean {
        val lang = context.resources.configuration.locales[0].language
        return lang in RTL_LOCALES
    }

    /** Returns the appropriate LayoutDirection for the current locale. */
    fun layoutDirection(context: Context): LayoutDirection =
        if (isRtl(context)) LayoutDirection.Rtl else LayoutDirection.Ltr

    /**
     * Wrap content with the correct layout direction based on the app locale.
     * Use this at the root of any screen that needs RTL-aware mirroring.
     */
    @Composable
    fun RtlAwareLayout(
        context: Context,
        content: @Composable () -> Unit,
    ) {
        val direction = layoutDirection(context)
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            content()
        }
    }

    /** Force a specific locale on a Context (for in-app language switching). */
    fun forceLocale(context: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    /** List of supported RTL locale tags with display names. */
    val SUPPORTED_RTL_LANGUAGES = listOf(
        "ar" to "العربية (Arabic)",
        "he" to "עברית (Hebrew)",
        "fa" to "فارسی (Farsi)",
        "ur" to "اردو (Urdu)",
    )
}
