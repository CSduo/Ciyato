package com.ciyato.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("ciyato_settings")

/**
 * Persists ALL user preferences locally via DataStore.
 *
 * Suggestion coverage:
 *  1   Haptic feedback
 *  7   Icon shape
 *  21  Temp unit (°C/°F)
 *  22  Default home screen link
 *  23  App hide list
 *  24  Category renames
 *  25  Recently launched
 *  36  Recent searches
 *  72  Time-aware layout
 *  74  Bedtime mode
 *  75  Focus sessions
 *  91  Custom accent color (Theme Studio)
 *  93  Wallpaper blur
 *  97  Dark/Light/AMOLED mode
 *  98  Font selector
 *  99  Glass morphism preset
 *  100 Seasonal themes
 *  113 BuildConfig/debug stubs
 *  116 Weather cache
 *  117 Offline indicator enabled
 *  125 OTA update check enabled
 *  136 Biometric app lock
 *  137 Hidden apps with PIN
 *  138 Privacy mode
 *  141 App lock timer
 *  143 Certificate pinning toggle
 *  144 Crash reporting
 *  145 Anti-screenshot flag
 */
class LauncherSettingsRepository(private val context: Context) {

    private companion object {
        // ── Core ─────────────────────────────────────────────────────────────
        val KEY_ONBOARDING_DONE        = booleanPreferencesKey("onboarding_done")
        val KEY_HOME_TIP_DISMISSED     = booleanPreferencesKey("home_tip_dismissed")
        val KEY_SHOW_HOME_GREETING     = booleanPreferencesKey("show_home_greeting")
        val KEY_SHOW_HOME_SEARCH       = booleanPreferencesKey("show_home_search")
        val KEY_SHOW_HOME_WEATHER      = booleanPreferencesKey("show_home_weather")
        val KEY_SHOW_HOME_AGENDA       = booleanPreferencesKey("show_home_agenda")
        val KEY_SHOW_HOME_DOCK         = booleanPreferencesKey("show_home_dock")
        val KEY_SHOW_APP_DRAWER        = booleanPreferencesKey("show_app_drawer")
        val KEY_HIDDEN_HOME_CATEGORIES = stringPreferencesKey("hidden_home_categories")
        val KEY_DENSE_LAYOUT           = booleanPreferencesKey("dense_layout")
        val KEY_HOME_LAYOUT_MODE       = stringPreferencesKey("home_layout_mode") // smart | dense | spacious
        val KEY_DARK_MODE              = stringPreferencesKey("dark_mode")          // auto | dark | light | amoled
        val KEY_GOLD_ACCENT            = booleanPreferencesKey("gold_accent")
        val KEY_SMART_CATEGORIES       = booleanPreferencesKey("smart_categories")
        val KEY_DUPLICATE_SHORTCUTS    = booleanPreferencesKey("duplicate_shortcuts")
        val KEY_ICON_STYLE             = stringPreferencesKey("icon_style")         // real | rounded | squircle | circle

        // ── Appearance (#7, #91, #93, #97, #98, #99, #100) ──────────────────
        val KEY_ICON_SHAPE             = stringPreferencesKey("icon_shape")         // squircle | circle | rounded | raw
        val KEY_FONT                   = stringPreferencesKey("font")               // inter | outfit | dm_sans | syne | geist
        val KEY_WALLPAPER_BLUR         = intPreferencesKey("wallpaper_blur")        // 0–20
        val KEY_CUSTOM_ACCENT_COLOR    = stringPreferencesKey("custom_accent_color") // hex string e.g. "#E8E8E4"
        val KEY_GLASS_MORPHISM_PRESET  = stringPreferencesKey("glass_preset")       // none | light | medium | heavy
        val KEY_SEASONAL_THEMES        = booleanPreferencesKey("seasonal_themes")   // auto seasonal accent
        val KEY_MATERIAL_YOU           = booleanPreferencesKey("material_you")      // dynamic color

        // ── Weather (#21, #116) ───────────────────────────────────────────────
        val KEY_TEMP_UNIT              = stringPreferencesKey("temp_unit")          // C | F
        val KEY_WEATHER_CACHE_JSON     = stringPreferencesKey("weather_cache_json")
        val KEY_WEATHER_CACHE_AT       = longPreferencesKey("weather_cache_at")

        // ── Organization (#23, #24, #25) ──────────────────────────────────────
        val KEY_HIDDEN_APPS            = stringPreferencesKey("hidden_apps")        // comma-separated packageNames
        val KEY_REMOVED_APPS           = stringPreferencesKey("removed_apps")       // display-only removal
        val KEY_DOCK_PACKAGES          = stringPreferencesKey("dock_packages")      // ordered CSV package names
        val KEY_DOCK_INITIALIZED       = booleanPreferencesKey("dock_initialized") // true once seeded/edited; distinguishes "never set" from "intentionally emptied"
        val KEY_GRID_SIZE              = stringPreferencesKey("grid_size")          // "CxR" columns x rows for the workspace app grid
        val KEY_CATEGORY_RENAMES       = stringPreferencesKey("category_renames")  // JSON {"WORK":"Office"}
        val KEY_RECENTLY_LAUNCHED      = stringPreferencesKey("recently_launched")  // JSON list (max 10)
        val KEY_SHOW_RECENTLY_LAUNCHED = booleanPreferencesKey("show_recently_launched")
        val KEY_APP_CATEGORY_OVERRIDES = stringPreferencesKey("app_category_overrides")
        val KEY_APP_LABEL_OVERRIDES    = stringPreferencesKey("app_label_overrides")
        val KEY_APP_VISUAL_OVERRIDES   = stringPreferencesKey("app_visual_overrides")

        // ── Search (#36) ─────────────────────────────────────────────────────
        val KEY_RECENT_SEARCHES        = stringPreferencesKey("recent_searches")    // JSON list (max 10)

        // ── Smart Layout (#72, #74, #100) ─────────────────────────────────────
        val KEY_TIME_AWARE_LAYOUT      = booleanPreferencesKey("time_aware_layout")
        val KEY_BEDTIME_MODE           = booleanPreferencesKey("bedtime_mode")
        val KEY_BEDTIME_HOUR           = intPreferencesKey("bedtime_hour")           // default 23
        val KEY_BEDTIME_HIDDEN_CATS    = stringPreferencesKey("bedtime_hidden_cats") // CSV AppCategory names

        // ── Focus (#75) ───────────────────────────────────────────────────────
        val KEY_FOCUS_MODE_ACTIVE      = booleanPreferencesKey("focus_mode_active")
        val KEY_FOCUS_BLOCKED_CATS     = stringPreferencesKey("focus_blocked_cats")
        val KEY_FOCUS_DURATION_MIN     = intPreferencesKey("focus_duration_min")

        // ── Haptic (#1) ───────────────────────────────────────────────────────
        val KEY_HAPTIC_FEEDBACK        = booleanPreferencesKey("haptic_feedback")
        val KEY_REDUCE_MOTION          = booleanPreferencesKey("reduce_motion")

        // ── Notification badges (#2, #81) ─────────────────────────────────────
        val KEY_NOTIFICATION_BADGES    = booleanPreferencesKey("notification_badges")

        // ── Context-aware features (#76, #77, #78, #79) ───────────────────────
        val KEY_CONTEXTUAL_WIDGETS     = booleanPreferencesKey("contextual_widgets")
        val KEY_SMART_DOCK_ROTATION    = booleanPreferencesKey("smart_dock_rotation")
        val KEY_CATEGORY_LEARNING      = booleanPreferencesKey("category_learning")
        val KEY_APP_PREDICTION         = booleanPreferencesKey("app_prediction")

        // ── Location-aware (#73) ─────────────────────────────────────────────
        val KEY_LOCATION_AWARE_CATS    = booleanPreferencesKey("location_aware_categories")

        // ── Usage stats (#71) ────────────────────────────────────────────────
        val KEY_USAGE_STATS_ENABLED    = booleanPreferencesKey("usage_stats_enabled")

        // ── App lock & security (#136, #137, #141) ────────────────────────────
        val KEY_BIOMETRIC_LOCK         = booleanPreferencesKey("biometric_lock")
        val KEY_HIDDEN_APPS_LOCKED     = booleanPreferencesKey("hidden_apps_locked")
        val KEY_APP_LOCK_TIMER_MIN     = intPreferencesKey("app_lock_timer_min")     // 0 = immediate
        val KEY_APP_LOCK_PACKAGES      = stringPreferencesKey("app_lock_packages")   // JSON set of pkgs

        // ── Privacy & Security (#138, #143, #144, #145) ───────────────────────
        val KEY_PRIVACY_MODE           = booleanPreferencesKey("privacy_mode")
        val KEY_SCREENSHOT_BLOCKED     = booleanPreferencesKey("screenshot_blocked")
        val KEY_CRASH_REPORTING        = booleanPreferencesKey("crash_reporting")
        val KEY_CERT_PINNING           = booleanPreferencesKey("cert_pinning")

        // ── Cloud & Backup (#120, #125) ───────────────────────────────────────
        val KEY_OTA_CHECK_ENABLED      = booleanPreferencesKey("ota_check_enabled")
        val KEY_BACKUP_WEBDAV_URL      = stringPreferencesKey("backup_webdav_url")
        val KEY_BACKUP_ENABLED         = booleanPreferencesKey("backup_enabled")

        // ── App recommendations (#123) ────────────────────────────────────────
        val KEY_APP_RECOMMENDATIONS    = booleanPreferencesKey("app_recommendations")

        // ── Network call log (#142) ───────────────────────────────────────────
        val KEY_NETWORK_CALL_LOG       = booleanPreferencesKey("network_call_log")

        // ── Debug (#113) ──────────────────────────────────────────────────────
        val KEY_DEBUG_WEATHER_STUB     = booleanPreferencesKey("debug_weather_stub")
        val KEY_DEBUG_LOCATION_STUB    = booleanPreferencesKey("debug_location_stub")

        // ── Custom Layout Settings ──
        val KEY_USE_SYSTEM_WALLPAPER   = booleanPreferencesKey("use_system_wallpaper")
        val KEY_CIYATO_VIDEO_WALLPAPER = stringPreferencesKey("ciyato_video_wallpaper")
        val KEY_CIYATO_IMAGE_WALLPAPER = stringPreferencesKey("ciyato_image_wallpaper")
        val KEY_WALLPAPER_DIM          = intPreferencesKey("wallpaper_dim")
        val KEY_WALLPAPER_IMAGE_SCALE  = floatPreferencesKey("wallpaper_image_scale")
        val KEY_WALLPAPER_IMAGE_OFFSET = floatPreferencesKey("wallpaper_image_offset")
        val KEY_CATEGORY_ORDER         = stringPreferencesKey("category_order")
        val KEY_CATEGORY_TILES_SIZES   = stringPreferencesKey("category_tiles_sizes")
        val KEY_CUSTOM_CATEGORIES     = stringPreferencesKey("custom_categories")
        val KEY_CUSTOM_CATEGORY_ICONS = stringPreferencesKey("custom_category_icons")
        val KEY_CUSTOM_CATEGORY_PRESENTATIONS = stringPreferencesKey("custom_category_presentations")
        val KEY_PAGE_0_APPS            = stringPreferencesKey("page_0_apps")
        val KEY_PAGE_2_APPS            = stringPreferencesKey("page_2_apps")
        val KEY_WORKSPACE_COUNT        = intPreferencesKey("workspace_count")
        val KEY_WORKSPACE_APPS         = stringPreferencesKey("workspace_apps")
        val KEY_WORKSPACE_CATEGORIES   = stringPreferencesKey("workspace_categories")
        val KEY_WORKSPACE_TRANSITION   = stringPreferencesKey("workspace_transition")
        val KEY_WORKSPACE_LAYOUT_V2    = stringPreferencesKey("workspace_layout_v2")
        val KEY_FILES_ROOT_URI         = stringPreferencesKey("files_root_uri")
        val KEY_DRAWER_STYLE           = stringPreferencesKey("drawer_style")       // smart | dense | spacious
        val KEY_PHOTO_MEDIA_URIS       = stringPreferencesKey("photo_media_uris")
        val KEY_PHOTO_COLLECTIONS      = stringPreferencesKey("photo_collections")
        val KEY_FILE_SEARCH_HISTORY    = stringPreferencesKey("file_search_history")
        val KEY_SAVE_FILE_SEARCH_HISTORY = booleanPreferencesKey("save_file_search_history")
        val KEY_FILE_SEARCH_INDEX      = stringPreferencesKey("file_search_index")
        val KEY_EXPANDED_APPS          = stringPreferencesKey("expanded_apps")
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    val onboardingDone:         Flow<Boolean> = pref(KEY_ONBOARDING_DONE,         false)
    val homeTipDismissed:       Flow<Boolean> = pref(KEY_HOME_TIP_DISMISSED,      false)
    val showHomeGreeting:       Flow<Boolean> = pref(KEY_SHOW_HOME_GREETING,      true)
    val showHomeSearch:         Flow<Boolean> = pref(KEY_SHOW_HOME_SEARCH,        true)
    val showHomeWeather:        Flow<Boolean> = pref(KEY_SHOW_HOME_WEATHER,       true)
    val showHomeAgenda:         Flow<Boolean> = pref(KEY_SHOW_HOME_AGENDA,        true)
    val showHomeDock:           Flow<Boolean> = pref(KEY_SHOW_HOME_DOCK,          true)
    val showAppDrawer:          Flow<Boolean> = pref(KEY_SHOW_APP_DRAWER,         true)
    val hiddenHomeCategories:   Flow<String>  = pref(KEY_HIDDEN_HOME_CATEGORIES,  "")
    val denseLayout:            Flow<Boolean> = pref(KEY_DENSE_LAYOUT,            true)
    val homeLayoutMode:         Flow<String>  = pref(KEY_HOME_LAYOUT_MODE,        "dense")
    val expandedApps:           Flow<String>  = pref(KEY_EXPANDED_APPS,           "")
    val darkMode:               Flow<String>  = pref(KEY_DARK_MODE,               "dark")
    val goldAccent:             Flow<Boolean> = pref(KEY_GOLD_ACCENT,             true)
    val smartCategories:        Flow<Boolean> = pref(KEY_SMART_CATEGORIES,        true)
    val duplicateShortcuts:     Flow<Boolean> = pref(KEY_DUPLICATE_SHORTCUTS,     true)
    val iconStyle:              Flow<String>  = pref(KEY_ICON_STYLE,              "real")

    val iconShape:              Flow<String>  = pref(KEY_ICON_SHAPE,              "squircle")
    val font:                   Flow<String>  = pref(KEY_FONT,                    "inter")
    val wallpaperBlur:          Flow<Int>     = pref(KEY_WALLPAPER_BLUR,          0)
    val customAccentColor:      Flow<String>  = pref(KEY_CUSTOM_ACCENT_COLOR,     "#E8E8E4")
    val glassMorphismPreset:    Flow<String>  = pref(KEY_GLASS_MORPHISM_PRESET,   "medium")
    val seasonalThemes:         Flow<Boolean> = pref(KEY_SEASONAL_THEMES,         true)
    val materialYou:            Flow<Boolean> = pref(KEY_MATERIAL_YOU,            false)

    val tempUnit:               Flow<String>  = pref(KEY_TEMP_UNIT, WeatherRepository.localeDefaultUnit())
    val weatherCacheJson:       Flow<String>  = pref(KEY_WEATHER_CACHE_JSON,      "")
    val weatherCacheAt:         Flow<Long>    = pref(KEY_WEATHER_CACHE_AT,        0L)

    val hiddenApps:             Flow<String>  = pref(KEY_HIDDEN_APPS,             "")
    val removedApps:            Flow<String>  = pref(KEY_REMOVED_APPS,            "")
    val dockPackages:           Flow<String>  = pref(KEY_DOCK_PACKAGES,           "")
    val dockInitialized:        Flow<Boolean> = pref(KEY_DOCK_INITIALIZED,        false)
    val gridSize:               Flow<String>  = pref(KEY_GRID_SIZE,               "6x5")
    val categoryRenames:        Flow<String>  = pref(KEY_CATEGORY_RENAMES,        "{}")
    val recentlyLaunched:       Flow<String>  = pref(KEY_RECENTLY_LAUNCHED,       "[]")
    val showRecentlyLaunched:   Flow<Boolean> = pref(KEY_SHOW_RECENTLY_LAUNCHED,  true)

    val recentSearches:         Flow<String>  = pref(KEY_RECENT_SEARCHES,         "[]")
    val appCategoryOverrides:   Flow<String>  = pref(KEY_APP_CATEGORY_OVERRIDES,   "{}")
    val appLabelOverrides:      Flow<String>  = pref(KEY_APP_LABEL_OVERRIDES,      "{}")
    val appVisualOverrides:     Flow<String>  = pref(KEY_APP_VISUAL_OVERRIDES,     "{}")

    val timeAwareLayout:        Flow<Boolean> = pref(KEY_TIME_AWARE_LAYOUT,       true)
    val bedtimeMode:            Flow<Boolean> = pref(KEY_BEDTIME_MODE,            false)
    val bedtimeHour:            Flow<Int>     = pref(KEY_BEDTIME_HOUR,            23)
    val bedtimeHiddenCats:      Flow<String>  = pref(KEY_BEDTIME_HIDDEN_CATS,     "SOCIAL,ENTERTAINMENT,GAMES")

    val focusModeActive:        Flow<Boolean> = pref(KEY_FOCUS_MODE_ACTIVE,       false)
    val focusBlockedCats:       Flow<String>  = pref(KEY_FOCUS_BLOCKED_CATS,      "SOCIAL,ENTERTAINMENT,GAMES")
    val focusDurationMin:       Flow<Int>     = pref(KEY_FOCUS_DURATION_MIN,      25)

    val hapticFeedback:         Flow<Boolean> = pref(KEY_HAPTIC_FEEDBACK,         true)
    val reduceMotion:           Flow<Boolean> = pref(KEY_REDUCE_MOTION,           false)
    val notificationBadges:     Flow<Boolean> = pref(KEY_NOTIFICATION_BADGES,     true)

    val contextualWidgets:      Flow<Boolean> = pref(KEY_CONTEXTUAL_WIDGETS,      true)
    val smartDockRotation:      Flow<Boolean> = pref(KEY_SMART_DOCK_ROTATION,     true)
    val categoryLearning:       Flow<Boolean> = pref(KEY_CATEGORY_LEARNING,       true)
    val appPrediction:          Flow<Boolean> = pref(KEY_APP_PREDICTION,          true)

    val locationAwareCats:      Flow<Boolean> = pref(KEY_LOCATION_AWARE_CATS,     false)
    val usageStatsEnabled:      Flow<Boolean> = pref(KEY_USAGE_STATS_ENABLED,     false)

    val biometricLock:          Flow<Boolean> = pref(KEY_BIOMETRIC_LOCK,          false)
    val hiddenAppsLocked:       Flow<Boolean> = pref(KEY_HIDDEN_APPS_LOCKED,      false)
    val appLockTimerMin:        Flow<Int>     = pref(KEY_APP_LOCK_TIMER_MIN,      0)
    val appLockPackages:        Flow<String>  = pref(KEY_APP_LOCK_PACKAGES,       "[]")

    val privacyMode:            Flow<Boolean> = pref(KEY_PRIVACY_MODE,            false)
    val screenshotBlocked:      Flow<Boolean> = pref(KEY_SCREENSHOT_BLOCKED,      false)
    val crashReporting:         Flow<Boolean> = pref(KEY_CRASH_REPORTING,         true)
    val certPinning:            Flow<Boolean> = pref(KEY_CERT_PINNING,            true)

    val otaCheckEnabled:        Flow<Boolean> = pref(KEY_OTA_CHECK_ENABLED,       true)
    val backupWebDavUrl:        Flow<String>  = pref(KEY_BACKUP_WEBDAV_URL,       "")
    val backupEnabled:          Flow<Boolean> = pref(KEY_BACKUP_ENABLED,          false)

    val appRecommendations:     Flow<Boolean> = pref(KEY_APP_RECOMMENDATIONS,     true)
    val networkCallLog:         Flow<Boolean> = pref(KEY_NETWORK_CALL_LOG,        false)

    val debugWeatherStub:       Flow<Boolean> = pref(KEY_DEBUG_WEATHER_STUB,      false)
    val debugLocationStub:      Flow<Boolean> = pref(KEY_DEBUG_LOCATION_STUB,     false)

    val useSystemWallpaper:     Flow<Boolean> = pref(KEY_USE_SYSTEM_WALLPAPER,    true)
    val ciyatoVideoWallpaper:   Flow<String>  = pref(KEY_CIYATO_VIDEO_WALLPAPER,  "")
    val ciyatoImageWallpaper:   Flow<String>  = pref(KEY_CIYATO_IMAGE_WALLPAPER,  "")
    val wallpaperDim:           Flow<Int>     = pref(KEY_WALLPAPER_DIM,           32)
    val wallpaperImageScale:    Flow<Float>   = pref(KEY_WALLPAPER_IMAGE_SCALE,   1f)
    val wallpaperImageOffset:   Flow<Float>   = pref(KEY_WALLPAPER_IMAGE_OFFSET,  0f)
    val categoryOrder:          Flow<String>  = pref(KEY_CATEGORY_ORDER,         "")
    val categoryTilesSizes:     Flow<String>  = pref(KEY_CATEGORY_TILES_SIZES,     "{}")
    val customCategories:       Flow<String>  = pref(KEY_CUSTOM_CATEGORIES,       "")
    val customCategoryIcons:    Flow<String>  = pref(KEY_CUSTOM_CATEGORY_ICONS,   "{}")
    val customCategoryPresentations: Flow<String> = pref(KEY_CUSTOM_CATEGORY_PRESENTATIONS, "{}")
    val page0Apps:              Flow<String>  = pref(KEY_PAGE_0_APPS,             "")
    val page2Apps:              Flow<String>  = pref(KEY_PAGE_2_APPS,             "")
    val workspaceCount:         Flow<Int>     = pref(KEY_WORKSPACE_COUNT,          3)
    val workspaceApps:          Flow<String>  = pref(KEY_WORKSPACE_APPS,           "{}")
    val workspaceCategories:    Flow<String>  = pref(KEY_WORKSPACE_CATEGORIES,     "{}")
    val workspaceTransition:    Flow<String>  = pref(KEY_WORKSPACE_TRANSITION,     "slide")
    val workspaceLayoutV2:      Flow<String>  = pref(KEY_WORKSPACE_LAYOUT_V2,      "")
    val filesRootUri:           Flow<String>  = pref(KEY_FILES_ROOT_URI,          "")
    val drawerStyle:            Flow<String>  = pref(KEY_DRAWER_STYLE,            "smart")
    val photoMediaUris:         Flow<String>  = pref(KEY_PHOTO_MEDIA_URIS,        "[]")
    val photoCollections:       Flow<String>  = pref(KEY_PHOTO_COLLECTIONS,       "[]")
    val fileSearchHistory:      Flow<String>  = pref(KEY_FILE_SEARCH_HISTORY,     "[]")
    val saveFileSearchHistory:  Flow<Boolean> = pref(KEY_SAVE_FILE_SEARCH_HISTORY, true)
    val fileSearchIndex:        Flow<String>  = pref(KEY_FILE_SEARCH_INDEX,       "")

    // ── Setters ───────────────────────────────────────────────────────────────

    suspend fun setOnboardingDone(v: Boolean)          = set(KEY_ONBOARDING_DONE,          v)
    suspend fun setHomeTipDismissed(v: Boolean)        = set(KEY_HOME_TIP_DISMISSED,       v)
    suspend fun setShowHomeGreeting(v: Boolean)        = set(KEY_SHOW_HOME_GREETING,       v)
    suspend fun setShowHomeSearch(v: Boolean)          = set(KEY_SHOW_HOME_SEARCH,         v)
    suspend fun setShowHomeWeather(v: Boolean)         = set(KEY_SHOW_HOME_WEATHER,        v)
    suspend fun setShowHomeAgenda(v: Boolean)          = set(KEY_SHOW_HOME_AGENDA,         v)
    suspend fun setShowHomeDock(v: Boolean)            = set(KEY_SHOW_HOME_DOCK,           v)
    suspend fun setShowAppDrawer(v: Boolean)           = set(KEY_SHOW_APP_DRAWER,          v)
    suspend fun setHiddenHomeCategories(v: String)     = set(KEY_HIDDEN_HOME_CATEGORIES,  v)
    suspend fun setDenseLayout(v: Boolean)             = set(KEY_DENSE_LAYOUT,             v)
    suspend fun setHomeLayoutMode(v: String)           = set(KEY_HOME_LAYOUT_MODE,         v)
    suspend fun setExpandedApps(v: String)             = set(KEY_EXPANDED_APPS,            v)
    suspend fun setDarkMode(v: String)                 = set(KEY_DARK_MODE,                v)
    suspend fun setGoldAccent(v: Boolean)              = set(KEY_GOLD_ACCENT,              v)
    suspend fun setSmartCategories(v: Boolean)         = set(KEY_SMART_CATEGORIES,         v)
    suspend fun setDuplicateShortcuts(v: Boolean)      = set(KEY_DUPLICATE_SHORTCUTS,      v)
    suspend fun setIconStyle(v: String)                = set(KEY_ICON_STYLE,               v)
    suspend fun setIconShape(v: String)                = set(KEY_ICON_SHAPE,               v)
    suspend fun setFont(v: String)                     = set(KEY_FONT,                     v)
    suspend fun setWallpaperBlur(v: Int)               = set(KEY_WALLPAPER_BLUR,           v.coerceIn(0, 20))
    suspend fun setCustomAccentColor(v: String)        = set(KEY_CUSTOM_ACCENT_COLOR,      v)
    suspend fun setGlassMorphismPreset(v: String)      = set(KEY_GLASS_MORPHISM_PRESET,    v)
    suspend fun setSeasonalThemes(v: Boolean)          = set(KEY_SEASONAL_THEMES,          v)
    suspend fun setMaterialYou(v: Boolean)             = set(KEY_MATERIAL_YOU,             v)

    suspend fun setTempUnit(v: String)                 = set(KEY_TEMP_UNIT,                v)
    suspend fun setWeatherCache(json: String) {
        context.dataStore.edit { it[KEY_WEATHER_CACHE_JSON] = json; it[KEY_WEATHER_CACHE_AT] = System.currentTimeMillis() }
    }
    suspend fun clearWeatherCache() {
        context.dataStore.edit { it[KEY_WEATHER_CACHE_JSON] = ""; it[KEY_WEATHER_CACHE_AT] = 0L }
    }

    suspend fun setHiddenApps(csv: String)             = set(KEY_HIDDEN_APPS,             csv)
    suspend fun setRemovedApps(csv: String)            = set(KEY_REMOVED_APPS,            csv)
    suspend fun setDockPackages(csv: String)            = set(KEY_DOCK_PACKAGES,           csv)
    suspend fun setDockInitialized(v: Boolean)          = set(KEY_DOCK_INITIALIZED,        v)
    suspend fun setGridSize(v: String)                  = set(KEY_GRID_SIZE,               v)
    suspend fun setCategoryRenames(json: String)       = set(KEY_CATEGORY_RENAMES,        json)
    suspend fun setRecentlyLaunched(json: String)      = set(KEY_RECENTLY_LAUNCHED,       json)
    suspend fun setShowRecentlyLaunched(v: Boolean)    = set(KEY_SHOW_RECENTLY_LAUNCHED,  v)
    suspend fun setAppCategoryOverrides(json: String)  = set(KEY_APP_CATEGORY_OVERRIDES,  json)
    suspend fun setAppLabelOverrides(json: String)     = set(KEY_APP_LABEL_OVERRIDES,     json)
    suspend fun setAppVisualOverrides(json: String)    = set(KEY_APP_VISUAL_OVERRIDES,    json)

    suspend fun setRecentSearches(json: String)        = set(KEY_RECENT_SEARCHES,         json)

    suspend fun setTimeAwareLayout(v: Boolean)         = set(KEY_TIME_AWARE_LAYOUT,       v)
    suspend fun setBedtimeMode(v: Boolean)             = set(KEY_BEDTIME_MODE,            v)
    suspend fun setBedtimeHour(v: Int)                 = set(KEY_BEDTIME_HOUR,            v.coerceIn(18, 23))
    suspend fun setBedtimeHiddenCats(csv: String)      = set(KEY_BEDTIME_HIDDEN_CATS,     csv)

    suspend fun setFocusModeActive(v: Boolean)         = set(KEY_FOCUS_MODE_ACTIVE,       v)
    suspend fun setFocusBlockedCats(csv: String)       = set(KEY_FOCUS_BLOCKED_CATS,      csv)
    suspend fun setFocusDurationMin(v: Int)            = set(KEY_FOCUS_DURATION_MIN,      v.coerceIn(5, 120))

    suspend fun setHapticFeedback(v: Boolean)          = set(KEY_HAPTIC_FEEDBACK,         v)
    suspend fun setReduceMotion(v: Boolean)            = set(KEY_REDUCE_MOTION,           v)
    suspend fun setNotificationBadges(v: Boolean)      = set(KEY_NOTIFICATION_BADGES,     v)

    suspend fun setContextualWidgets(v: Boolean)       = set(KEY_CONTEXTUAL_WIDGETS,      v)
    suspend fun setSmartDockRotation(v: Boolean)       = set(KEY_SMART_DOCK_ROTATION,     v)
    suspend fun setCategoryLearning(v: Boolean)        = set(KEY_CATEGORY_LEARNING,       v)
    suspend fun setAppPrediction(v: Boolean)           = set(KEY_APP_PREDICTION,          v)

    suspend fun setLocationAwareCats(v: Boolean)       = set(KEY_LOCATION_AWARE_CATS,     v)
    suspend fun setUsageStatsEnabled(v: Boolean)       = set(KEY_USAGE_STATS_ENABLED,     v)

    suspend fun setBiometricLock(v: Boolean)           = set(KEY_BIOMETRIC_LOCK,          v)
    suspend fun setHiddenAppsLocked(v: Boolean)        = set(KEY_HIDDEN_APPS_LOCKED,      v)
    suspend fun setAppLockTimerMin(v: Int)             = set(KEY_APP_LOCK_TIMER_MIN,      v.coerceIn(0, 60))
    suspend fun setAppLockPackages(json: String)       = set(KEY_APP_LOCK_PACKAGES,       json)

    suspend fun setPrivacyMode(v: Boolean)             = set(KEY_PRIVACY_MODE,            v)
    suspend fun setScreenshotBlocked(v: Boolean)       = set(KEY_SCREENSHOT_BLOCKED,      v)
    suspend fun setCrashReporting(v: Boolean)          = set(KEY_CRASH_REPORTING,         v)
    suspend fun setCertPinning(v: Boolean)             = set(KEY_CERT_PINNING,            v)

    suspend fun setOtaCheckEnabled(v: Boolean)         = set(KEY_OTA_CHECK_ENABLED,       v)
    suspend fun setBackupWebDavUrl(v: String)          = set(KEY_BACKUP_WEBDAV_URL,       v)
    suspend fun setBackupEnabled(v: Boolean)           = set(KEY_BACKUP_ENABLED,          v)

    suspend fun setAppRecommendations(v: Boolean)      = set(KEY_APP_RECOMMENDATIONS,     v)
    suspend fun setNetworkCallLog(v: Boolean)          = set(KEY_NETWORK_CALL_LOG,        v)

    suspend fun setDebugWeatherStub(v: Boolean)        = set(KEY_DEBUG_WEATHER_STUB,      v)
    suspend fun setDebugLocationStub(v: Boolean)       = set(KEY_DEBUG_LOCATION_STUB,     v)

    suspend fun setUseSystemWallpaper(v: Boolean)      = set(KEY_USE_SYSTEM_WALLPAPER,     v)
    suspend fun setCiyatoVideoWallpaper(v: String)     = set(KEY_CIYATO_VIDEO_WALLPAPER,   v)
    suspend fun setCiyatoImageWallpaper(v: String)     = set(KEY_CIYATO_IMAGE_WALLPAPER,   v)
    suspend fun setWallpaperDim(v: Int)                = set(KEY_WALLPAPER_DIM,            v.coerceIn(0, 80))
    suspend fun setWallpaperImageScale(v: Float)       = set(KEY_WALLPAPER_IMAGE_SCALE,    v.coerceIn(1f, 1.6f))
    suspend fun setWallpaperImageOffset(v: Float)      = set(KEY_WALLPAPER_IMAGE_OFFSET,   v.coerceIn(-1f, 1f))
    suspend fun setCategoryOrder(v: String)            = set(KEY_CATEGORY_ORDER,           v)
    suspend fun setCategoryTilesSizes(v: String)        = set(KEY_CATEGORY_TILES_SIZES,       v)
    suspend fun setCustomCategories(v: String)          = set(KEY_CUSTOM_CATEGORIES,         v)
    suspend fun setCustomCategoryIcons(v: String)       = set(KEY_CUSTOM_CATEGORY_ICONS,     v)
    suspend fun setCustomCategoryPresentations(v: String) = set(KEY_CUSTOM_CATEGORY_PRESENTATIONS, v)
    suspend fun setPage0Apps(v: String)                = set(KEY_PAGE_0_APPS,              v)
    suspend fun setPage2Apps(v: String)                = set(KEY_PAGE_2_APPS,              v)
    suspend fun setWorkspaceCount(v: Int)              = set(KEY_WORKSPACE_COUNT,          v.coerceIn(3, 11))
    suspend fun setWorkspaceApps(v: String)            = set(KEY_WORKSPACE_APPS,           v)
    suspend fun setWorkspaceCategories(v: String)      = set(KEY_WORKSPACE_CATEGORIES,     v)
    suspend fun setWorkspaceTransition(v: String)      = set(KEY_WORKSPACE_TRANSITION,     v)
    suspend fun setWorkspaceLayoutV2(v: String)        = set(KEY_WORKSPACE_LAYOUT_V2,      v)
    suspend fun setFilesRootUri(v: String)             = set(KEY_FILES_ROOT_URI,           v)
    suspend fun setDrawerStyle(v: String)              = set(KEY_DRAWER_STYLE,             v)
    suspend fun setPhotoMediaUris(v: String)           = set(KEY_PHOTO_MEDIA_URIS,         v)
    suspend fun setPhotoCollections(v: String)         = set(KEY_PHOTO_COLLECTIONS,        v)
    suspend fun setFileSearchHistory(v: String)        = set(KEY_FILE_SEARCH_HISTORY,      v)
    suspend fun setSaveFileSearchHistory(v: Boolean)   = set(KEY_SAVE_FILE_SEARCH_HISTORY, v)
    suspend fun setFileSearchIndex(v: String)          = set(KEY_FILE_SEARCH_INDEX,       v)

    suspend fun resetLayout() {
        context.dataStore.edit { p ->
            p[KEY_DENSE_LAYOUT]        = false
            p[KEY_HOME_LAYOUT_MODE]    = "spacious"
            p[KEY_SHOW_HOME_GREETING]  = true
            p[KEY_SHOW_HOME_SEARCH]    = true
            p[KEY_SHOW_HOME_WEATHER]   = true
            p[KEY_SHOW_HOME_AGENDA]    = true
            p[KEY_SHOW_HOME_DOCK]      = true
            p[KEY_SHOW_APP_DRAWER]     = true
            p[KEY_HIDDEN_HOME_CATEGORIES] = ""
            p[KEY_WORKSPACE_TRANSITION] = "slide"
            p[KEY_DARK_MODE]           = "dark"
            p[KEY_GOLD_ACCENT]         = true
            p[KEY_SMART_CATEGORIES]    = true
            p[KEY_DUPLICATE_SHORTCUTS] = true
            p[KEY_ICON_STYLE]          = "real"
            p[KEY_ICON_SHAPE]          = "squircle"
            p[KEY_FONT]                = "inter"
            p[KEY_WALLPAPER_BLUR]      = 0
            p[KEY_CUSTOM_ACCENT_COLOR]  = "#E8E8E4"
            p[KEY_GLASS_MORPHISM_PRESET] = "medium"
            p[KEY_MATERIAL_YOU]        = false
            p[KEY_USE_SYSTEM_WALLPAPER] = true
            p[KEY_DRAWER_STYLE]        = "smart"
            // Clear the dock and mark it un-seeded so it re-populates with
            // sensible defaults exactly once after a reset.
            p[KEY_DOCK_PACKAGES]       = ""
            p[KEY_DOCK_INITIALIZED]    = false
            p[KEY_GRID_SIZE]           = "4x5"
        }
    }

    suspend fun resetAllPreferences() {
        context.dataStore.edit { it.clear() }
    }

    // ── Generic helpers ───────────────────────────────────────────────────────

    private fun <T> pref(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data
            .catch { e ->
                if (e is java.io.IOException) emit(emptyPreferences()) else throw e
            }
            .map { it[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) =
        context.dataStore.edit { it[key] = value }
}
