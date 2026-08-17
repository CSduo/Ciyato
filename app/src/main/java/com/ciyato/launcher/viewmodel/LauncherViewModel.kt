package com.ciyato.launcher.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.Window
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ciyato.launcher.BuildConfig
import com.ciyato.launcher.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * LauncherViewModel — central state hub.
 *
 * Suggestions implemented:
 *  1  Haptic feedback setting exposed
 *  21 Temp unit (°C/°F)
 *  23 App hide list management
 *  24 Category renames
 *  25 Recently launched tracking
 *  36 Recent searches history
 *  37 Usage frequency sort
 *  38 Fuzzy search
 *  40 NLP intent detection
 *  72 Time-aware layout
 *  74 Bedtime mode
 *  75 Focus sessions (FocusSessionManager integration)
 *  103 UiState sealed class
 *  104 Event<T> one-shot events
 *  112 Coroutine dispatcher injection pattern (IO on repo, Main on ViewModel)
 *  113 Debug stubs via settings
 *  116 Weather cache (30-min TTL)
 *  117 Offline state
 *  118 Retry with backoff (in WeatherRepository)
 *  138 Privacy mode
 *  145 Screenshot blocking (FLAG_SECURE)
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    val repo     = LauncherRepository(app)
    val settings = LauncherSettingsRepository(app)
    // Retained only for a legacy screen that is compiled but no longer reachable.
    val aiOptimizer = AIOptimizerManager(app)

    fun optimizeSystem() {
        viewModelScope.launch {
            aiOptimizer.optimizeSystem(this@LauncherViewModel)
        }
    }
    // ── App list ──────────────────────────────────────────────────────────────

    val apps      get() = repo.apps
    val allApps   get() = repo.allApps
    val isLoading get() = repo.isLoading

    // Transient launcher interactions must never survive Home, Recents, another
    // activity, or a fresh Home intent. The UI consumes this event to restore a
    // clean launcher state without persisting temporary edit state.
    private val _exitLauncherEditing = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitLauncherEditing = _exitLauncherEditing.asSharedFlow()

    fun cancelLauncherEditing() {
        _exitLauncherEditing.tryEmit(Unit)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<InstalledApp>> = searchQuery
        .combine(apps) { q, list ->
            when {
                q.isBlank()   -> list
                else          -> repo.search(q).ifEmpty { repo.fuzzySearch(q) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSearch(q: String) {
        _searchQuery.value = q
    }

    fun recordSearch(q: String) {
        if (q.isNotBlank()) viewModelScope.launch { addRecentSearch(q.trim()) }
    }

    // ── NLP / grouped search (Suggestions 40, 42) ─────────────────────────────

    val nlpSearchResult: StateFlow<Pair<AppCategory?, List<InstalledApp>>?> = searchQuery
        .debounce(400L)
        .map { q -> if (q.length >= 3) repo.nlpSearch(q) else null }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ── Settings ──────────────────────────────────────────────────────────────

    /**
     * null until DataStore answers — deliberately NOT `false`.
     *
     * A `false` placeholder is indistinguishable from a genuine "never
     * onboarded", so a NavHost created during that first frame commits to the
     * onboarding destination and an already-onboarded person is shown
     * onboarding again on every launch. Callers must wait for a non-null value
     * before choosing a start destination.
     */
    val onboardingDone: StateFlow<Boolean?> = settings.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val homeTipDismissed   = settings.homeTipDismissed  .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val showHomeGreeting   = settings.showHomeGreeting  .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showHomeSearch     = settings.showHomeSearch    .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showHomeWeather    = settings.showHomeWeather   .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showHomeAgenda     = settings.showHomeAgenda    .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showHomeDock       = settings.showHomeDock      .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showAppDrawer      = settings.showAppDrawer     .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hiddenHomeCategories = settings.hiddenHomeCategories.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val denseLayout        = settings.denseLayout        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val homeLayoutMode     = settings.homeLayoutMode     .stateIn(viewModelScope, SharingStarted.Eagerly, "dense")
    val expandedApps       = settings.expandedApps       .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val darkMode           = settings.darkMode           .stateIn(viewModelScope, SharingStarted.Eagerly, "auto")
    val goldAccent         = settings.goldAccent         .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val smartCategories    = settings.smartCategories    .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val duplicateShortcuts = settings.duplicateShortcuts .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val iconStyle          = settings.iconStyle          .stateIn(viewModelScope, SharingStarted.Eagerly, "real")
    val iconShape          = settings.iconShape          .stateIn(viewModelScope, SharingStarted.Eagerly, "squircle")
    val font               = settings.font               .stateIn(viewModelScope, SharingStarted.Eagerly, "inter")
    val materialYou        = settings.materialYou        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val wallpaperBlur      = settings.wallpaperBlur      .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val tempUnit           = settings.tempUnit           .stateIn(viewModelScope, SharingStarted.Eagerly, WeatherRepository.localeDefaultUnit())
    val hiddenApps         = settings.hiddenApps         .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val removedApps        = settings.removedApps        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val dockPackages       = settings.dockPackages       .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val categoryRenames    = settings.categoryRenames    .stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val appCategoryOverrides = settings.appCategoryOverrides.stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val appLabelOverrides    = settings.appLabelOverrides.stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val appVisualOverrides   = settings.appVisualOverrides.stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val showRecentlyLaunched= settings.showRecentlyLaunched.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val timeAwareLayout    = settings.timeAwareLayout    .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val bedtimeMode        = settings.bedtimeMode        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val bedtimeHour        = settings.bedtimeHour        .stateIn(viewModelScope, SharingStarted.Eagerly, 23)
    val focusModeActive    = settings.focusModeActive    .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val focusBlockedCats   = settings.focusBlockedCats   .stateIn(viewModelScope, SharingStarted.Eagerly, "SOCIAL,ENTERTAINMENT,GAMES")
    val focusDurationMin   = settings.focusDurationMin   .stateIn(viewModelScope, SharingStarted.Eagerly, 25)
    val hapticFeedback     = settings.hapticFeedback     .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val reduceMotion       = settings.reduceMotion       .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val privacyMode        = settings.privacyMode        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val screenshotBlocked  = settings.screenshotBlocked  .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val crashReporting     = settings.crashReporting     .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val biometricLock      = settings.biometricLock      .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val appLockTimerMin    = settings.appLockTimerMin    .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val useSystemWallpaper = settings.useSystemWallpaper.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val ciyatoVideoWallpaper = settings.ciyatoVideoWallpaper.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val ciyatoImageWallpaper = settings.ciyatoImageWallpaper.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val wallpaperDim = settings.wallpaperDim.stateIn(viewModelScope, SharingStarted.Eagerly, 32)
    val wallpaperImageScale = settings.wallpaperImageScale.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)
    val wallpaperImageOffset = settings.wallpaperImageOffset.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val categoryOrder      = settings.categoryOrder     .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val homeSectionOrder   = settings.homeSectionOrder  .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val categoryTilesSizes = settings.categoryTilesSizes.stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val customCategories   = settings.customCategories  .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val customCategoryIcons = settings.customCategoryIcons.stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val customCategoryPresentations = settings.customCategoryPresentations.stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val page0Apps          = settings.page0Apps         .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val page2Apps          = settings.page2Apps         .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val workspaceCount     = settings.workspaceCount    .stateIn(viewModelScope, SharingStarted.Eagerly, 3)
    val workspaceApps      = settings.workspaceApps     .stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val workspaceCategories = settings.workspaceCategories.stateIn(viewModelScope, SharingStarted.Eagerly, "{}")
    val workspaceTransition = settings.workspaceTransition.stateIn(viewModelScope, SharingStarted.Eagerly, "slide")
    val workspaceLayoutV2  = settings.workspaceLayoutV2.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val filesRootUri       = settings.filesRootUri      .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val drawerStyle        = settings.drawerStyle       .stateIn(viewModelScope, SharingStarted.Eagerly, "smart")
    val photoMediaUris    = settings.photoMediaUris    .stateIn(viewModelScope, SharingStarted.Eagerly, "[]")
    val photoCollections  = settings.photoCollections  .stateIn(viewModelScope, SharingStarted.Eagerly, "[]")
    val fileSearchHistory = settings.fileSearchHistory.stateIn(viewModelScope, SharingStarted.Eagerly, "[]")
    val saveFileSearchHistory = settings.saveFileSearchHistory.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val fileSearchIndex = settings.fileSearchIndex.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            // Order matters: migration establishes the layout, then the column
            // reconciliation runs against the migrated result rather than
            // racing it.
            ensureWorkspaceLayoutMigration()
            syncGridColumnsToLayout()
        }
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    fun setOnboardingDone()               = viewModelScope.launch { settings.setOnboardingDone(true) }
    fun dismissHomeTip()                  = viewModelScope.launch { settings.setHomeTipDismissed(true) }
    fun setShowHomeGreeting(v: Boolean)   = viewModelScope.launch { settings.setShowHomeGreeting(v) }
    fun setShowHomeSearch(v: Boolean)     = viewModelScope.launch { settings.setShowHomeSearch(v) }
    fun setShowHomeWeather(v: Boolean)    = viewModelScope.launch { settings.setShowHomeWeather(v) }
    fun setShowHomeAgenda(v: Boolean)     = viewModelScope.launch { settings.setShowHomeAgenda(v) }
    fun setShowHomeDock(v: Boolean)       = viewModelScope.launch { settings.setShowHomeDock(v) }
    fun setShowAppDrawer(v: Boolean)      = viewModelScope.launch { settings.setShowAppDrawer(v) }
    fun removeCategoryFromHome(categoryKey: String) = viewModelScope.launch {
        val hidden = parsePackageCsv(settings.hiddenHomeCategories.first()).toMutableSet()
        hidden.add(categoryKey)
        settings.setHiddenHomeCategories(hidden.joinToString(","))
    }
    fun restoreCategoryToHome(categoryKey: String) = viewModelScope.launch {
        val hidden = parsePackageCsv(settings.hiddenHomeCategories.first()).toMutableSet()
        hidden.remove(categoryKey)
        settings.setHiddenHomeCategories(hidden.joinToString(","))
    }
    fun resetGuidance()                   = viewModelScope.launch {
        settings.setHomeTipDismissed(false)
        settings.setOnboardingDone(false)
    }
    fun setDenseLayout(v: Boolean)        = viewModelScope.launch {
        settings.setDenseLayout(v)
        settings.setHomeLayoutMode(if (v) "dense" else "spacious")
    }
    fun setHomeLayoutMode(v: String)      = viewModelScope.launch {
        settings.setHomeLayoutMode(v)
        settings.setDenseLayout(v == "dense")
    }
    fun setDarkMode(v: String)            = viewModelScope.launch { settings.setDarkMode(v) }
    fun setGoldAccent(v: Boolean)         = viewModelScope.launch { settings.setGoldAccent(v) }
    fun setSmartCategories(v: Boolean)    = viewModelScope.launch { settings.setSmartCategories(v) }
    fun setDuplicateShortcuts(v: Boolean) = viewModelScope.launch { settings.setDuplicateShortcuts(v) }
    fun setIconStyle(v: String)           = viewModelScope.launch { settings.setIconStyle(v) }
    fun setIconShape(v: String)           = viewModelScope.launch { settings.setIconShape(v) }
    fun setFont(v: String)                = viewModelScope.launch { settings.setFont(v) }
    fun setMaterialYou(v: Boolean)        = viewModelScope.launch { settings.setMaterialYou(v) }
    fun setWallpaperBlur(v: Int)          = viewModelScope.launch { settings.setWallpaperBlur(v) }
    fun setTempUnit(v: String)            = viewModelScope.launch { settings.setTempUnit(v) }
    fun setTimeAwareLayout(v: Boolean)    = viewModelScope.launch { settings.setTimeAwareLayout(v) }
    fun setBedtimeMode(v: Boolean)        = viewModelScope.launch { settings.setBedtimeMode(v) }
    fun setBedtimeHour(v: Int)            = viewModelScope.launch { settings.setBedtimeHour(v) }
    fun setFocusDurationMin(v: Int)       = viewModelScope.launch { settings.setFocusDurationMin(v) }
    fun setFocusBlockedCats(csv: String)  = viewModelScope.launch { settings.setFocusBlockedCats(csv) }
    fun setHapticFeedback(v: Boolean)     = viewModelScope.launch { settings.setHapticFeedback(v) }
    fun setReduceMotion(v: Boolean)       = viewModelScope.launch { settings.setReduceMotion(v) }
    fun setPrivacyMode(v: Boolean)        = viewModelScope.launch { settings.setPrivacyMode(v) }
    fun setScreenshotBlocked(v: Boolean)  = viewModelScope.launch { settings.setScreenshotBlocked(v) }
    fun setCrashReporting(v: Boolean)     = viewModelScope.launch { settings.setCrashReporting(v) }
    fun setBiometricLock(v: Boolean)      = viewModelScope.launch { settings.setBiometricLock(v) }
    fun setAppLockTimerMin(v: Int)        = viewModelScope.launch { settings.setAppLockTimerMin(v) }
    fun setShowRecentlyLaunched(v: Boolean)= viewModelScope.launch { settings.setShowRecentlyLaunched(v) }

    fun setUseSystemWallpaper(v: Boolean)  = viewModelScope.launch { settings.setUseSystemWallpaper(v) }
    fun setCiyatoVideoWallpaper(uri: String) = viewModelScope.launch {
        if (uri.isBlank()) {
            val previous = Uri.parse(ciyatoVideoWallpaper.value)
            val privateWallpaperDirectory = File(getApplication<Application>().filesDir, "wallpapers").canonicalFile
            val previousFile = previous.path?.let(::File)?.canonicalFile
            if (previousFile != null && previousFile.parentFile == privateWallpaperDirectory) {
                runCatching { previousFile.delete() }
            }
        }
        settings.setCiyatoVideoWallpaper(uri)
    }
    fun setCiyatoImageWallpaper(uri: String) = viewModelScope.launch {
        val previous = Uri.parse(ciyatoImageWallpaper.value)
        if (previous.toString() != uri) {
            val privateWallpaperDirectory = File(getApplication<Application>().filesDir, "wallpapers").canonicalFile
            val previousFile = previous.path?.let(::File)?.canonicalFile
            if (previousFile != null && previousFile.parentFile == privateWallpaperDirectory &&
                previousFile.name.startsWith("ciyato_image_wallpaper")
            ) {
                runCatching { previousFile.delete() }
            }
        }
        settings.setCiyatoImageWallpaper(uri)
    }
    fun setWallpaperDim(value: Int) = viewModelScope.launch { settings.setWallpaperDim(value) }
    fun setWallpaperImageScale(value: Float) = viewModelScope.launch { settings.setWallpaperImageScale(value) }
    fun setWallpaperImageOffset(value: Float) = viewModelScope.launch { settings.setWallpaperImageOffset(value) }
    fun setCategoryOrder(v: String)        = viewModelScope.launch { settings.setCategoryOrder(v) }
    fun setHomeSectionOrder(v: String)     = viewModelScope.launch { settings.setHomeSectionOrder(v) }
    fun setCategoryTilesSizes(v: String)    = viewModelScope.launch { settings.setCategoryTilesSizes(v) }
    fun setWorkspaceLayout(v: String) = viewModelScope.launch {
        if (WorkspaceStore.parse(v) != null) settings.setWorkspaceLayoutV2(v)
    }
    fun setCustomCategories(v: String)     = viewModelScope.launch { settings.setCustomCategories(v) }

    /** Restores one coherent user-layout snapshot after an explicit Undo or Cancel. */
    fun restoreLayoutEditState(
        categoryOrder: String,
        tileSizes: String,
        workspaceLayout: String,
        customCategories: String,
        customCategoryIcons: String,
        customCategoryPresentations: String,
        appCategoryOverrides: String,
        hiddenHomeCategories: String,
    ) = viewModelScope.launch {
        val parsed = WorkspaceStore.parse(workspaceLayout) ?: return@launch
        // Serialize the workspace-layout write with every other layout mutation
        // so an Undo can't interleave with a concurrent add.
        layoutMutex.withLock {
            settings.setWorkspaceLayoutV2(workspaceLayout)
            settings.setWorkspaceCount(parsed.visualOrder.size + 1)
        }
        settings.setCategoryOrder(categoryOrder)
        settings.setCategoryTilesSizes(tileSizes)
        settings.setCustomCategories(customCategories)
        settings.setCustomCategoryIcons(customCategoryIcons)
        settings.setCustomCategoryPresentations(customCategoryPresentations)
        settings.setAppCategoryOverrides(appCategoryOverrides)
        settings.setHiddenHomeCategories(hiddenHomeCategories)
        repo.loadApps()
    }
    fun setPage0Apps(v: String)            = viewModelScope.launch { settings.setPage0Apps(v) }
    fun setPage2Apps(v: String)            = viewModelScope.launch { settings.setPage2Apps(v) }
    fun setFilesRootUri(v: String)         = viewModelScope.launch { settings.setFilesRootUri(v) }
    fun clearFilesRootUri() = viewModelScope.launch {
        val rawUri = filesRootUri.value
        if (rawUri.isNotBlank()) {
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(rawUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settings.setFilesRootUri("")
        settings.setFileSearchIndex("")
    }
    fun recordFileSearch(query: String) = viewModelScope.launch {
        if (!settings.saveFileSearchHistory.first()) return@launch
        settings.setFileSearchHistory(FileSearchHistoryStore.record(settings.fileSearchHistory.first(), query))
    }
    fun clearFileSearchHistory() = viewModelScope.launch { settings.setFileSearchHistory("[]") }
    fun setSaveFileSearchHistory(enabled: Boolean) = viewModelScope.launch {
        settings.setSaveFileSearchHistory(enabled)
        if (!enabled) settings.setFileSearchHistory("[]")
    }
    fun updateFileSearchIndex(rootUri: String, entries: Collection<FileSearchIndexEntry>, reachedLimit: Boolean) = viewModelScope.launch {
        settings.setFileSearchIndex(
            FileSearchIndexStore.serialize(
                FileSearchIndex(
                    rootUri = rootUri,
                    indexedAt = System.currentTimeMillis(),
                    reachedLimit = reachedLimit,
                    entries = entries.toList(),
                ),
            ),
        )
    }
    fun clearFileSearchIndex() = viewModelScope.launch { settings.setFileSearchIndex("") }
    fun setDrawerStyle(v: String)          = viewModelScope.launch { settings.setDrawerStyle(v) }
    fun setWorkspaceTransition(v: String)  = viewModelScope.launch { settings.setWorkspaceTransition(v) }
    fun addPhotoUris(uris: Collection<String>) = viewModelScope.launch {
        val merged = PhotoLibraryStore.parseUris(photoMediaUris.value) + uris
        settings.setPhotoMediaUris(PhotoLibraryStore.serializeUris(merged))
    }
    fun removePhotoUris(uris: Collection<String>) = viewModelScope.launch {
        val remaining = PhotoLibraryStore.parseUris(photoMediaUris.value) - uris.toSet()
        settings.setPhotoMediaUris(PhotoLibraryStore.serializeUris(remaining))
        val collections = PhotoLibraryStore.parseCollections(photoCollections.value).map { collection ->
            collection.copy(uris = collection.uris - uris.toSet())
        }.filter { it.uris.isNotEmpty() }
        settings.setPhotoCollections(PhotoLibraryStore.serializeCollections(collections))
    }
    fun clearPhotoLibrary() = viewModelScope.launch {
        settings.setPhotoMediaUris("[]")
        settings.setPhotoCollections("[]")
    }
    fun addPhotoCollection(name: String, uris: Collection<String>) = viewModelScope.launch {
        val cleanName = name.trim().take(48)
        if (cleanName.isBlank() || uris.isEmpty()) return@launch
        val collections = PhotoLibraryStore.parseCollections(photoCollections.value).toMutableList()
        collections.add(
            PhotoCollection(
                id = "collection_" + System.currentTimeMillis(),
                name = cleanName,
                uris = uris.distinct(),
            ),
        )
        settings.setPhotoCollections(PhotoLibraryStore.serializeCollections(collections))
    }

    fun resetLayout() = viewModelScope.launch { settings.resetLayout() }
    fun resetAllPreferences() = viewModelScope.launch {
        settings.resetAllPreferences()
        repo.setHiddenPackages("")
        repo.setRemovedPackages("")
        repo.loadApps()
    }

    // ── Custom Category Customizers ───────────────────────────────────────────

    fun byCustomCategory(name: String): List<InstalledApp> = repo.byCustomCategoryName(name)

    fun addCustomCategory(
        name: String,
        presentation: CustomCategoryPresentation = CustomCategoryPresentation.GROUP,
    ) = viewModelScope.launch {
        val current = parsePackageCsv(customCategories.value).toMutableList()
        if (name !in current) {
            current.add(name)
            settings.setCustomCategories(current.joinToString(","))
            settings.setCustomCategoryPresentations(
                CustomCategoryPresentationStore.update(
                    settings.customCategoryPresentations.first(),
                    name,
                    presentation,
                ),
            )
        }
    }

    fun removeCustomCategory(name: String) = viewModelScope.launch {
        val current = parsePackageCsv(customCategories.value).toMutableList()
        current.remove(name)
        settings.setCustomCategories(current.joinToString(","))
        
        // Cleanup overrides mapped to this custom category
        val overrides = try { JSONObject(appCategoryOverrides.value) } catch(_: Exception) { JSONObject() }
        val toRemove = mutableListOf<String>()
        overrides.keys().forEach { key ->
            if (overrides.getString(key) == name) {
                toRemove.add(key)
            }
        }
        toRemove.forEach { overrides.remove(it) }
        settings.setAppCategoryOverrides(overrides.toString())
        val icons = try { JSONObject(customCategoryIcons.value) } catch (_: Exception) { JSONObject() }
        icons.remove(name)
        settings.setCustomCategoryIcons(icons.toString())
        settings.setCustomCategoryPresentations(
            CustomCategoryPresentationStore.remove(settings.customCategoryPresentations.first(), name),
        )
        repo.loadApps()
    }

    fun setCustomCategoryIcon(name: String, icon: String) = viewModelScope.launch {
        val icons = try { JSONObject(customCategoryIcons.value) } catch (_: Exception) { JSONObject() }
        icons.put(name, icon)
        settings.setCustomCategoryIcons(icons.toString())
    }

    fun getCustomCategoryIcon(name: String): String =
        try { JSONObject(customCategoryIcons.value).optString(name, "folder") }
        catch (_: Exception) { "folder" }

    fun getCustomCategoryPresentation(name: String): CustomCategoryPresentation =
        CustomCategoryPresentationStore.presentationFor(customCategoryPresentations.value, name)

    fun setAppCustomCategoryOverride(packageName: String, customName: String?) = viewModelScope.launch {
        val map = try { JSONObject(appCategoryOverrides.value) } catch (_: Exception) { JSONObject() }
        if (customName == null) {
            map.remove(packageName)
        } else {
            map.put(packageName, customName)
        }
        settings.setAppCategoryOverrides(map.toString())
        repo.loadApps()
    }

    fun isAppExpanded(packageName: String): Boolean {
        val current = parsePackageCsv(expandedApps.value)
        return packageName in current
    }

    fun toggleExpandedApp(packageName: String) = viewModelScope.launch {
        val current = parsePackageCsv(expandedApps.value).toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        settings.setExpandedApps(current.joinToString(","))
    }

    fun setAppsForCustomCategory(categoryName: String, packageNames: Set<String>) = viewModelScope.launch {
        val map = try { JSONObject(appCategoryOverrides.value) } catch (_: Exception) { JSONObject() }
        val toRemove = mutableListOf<String>()
        map.keys().forEach { key ->
            if (map.optString(key) == categoryName) {
                toRemove.add(key)
            }
        }
        toRemove.forEach { map.remove(it) }
        packageNames.forEach { pkg ->
            map.put(pkg, categoryName)
        }
        settings.setAppCategoryOverrides(map.toString())
        repo.loadApps()
    }

    /** Renames a user-created collection and every persisted reference to it. */
    fun renameCustomCategory(
        currentName: String,
        requestedName: String,
        icon: String? = null,
        presentation: CustomCategoryPresentation? = null,
    ) = viewModelScope.launch {
        val current = currentName.trim()
        val replacement = requestedName.trim().take(24)
        val categoryNames = parsePackageCsv(settings.customCategories.first())
        if (current !in categoryNames || replacement.isBlank() ||
            (replacement != current && replacement in categoryNames)
        ) return@launch

        settings.setCustomCategories(
            categoryNames.map { if (it == current) replacement else it }.joinToString(","),
        )

        val overrides = jsonObject(settings.appCategoryOverrides.first())
        overrides.keys().asSequence().toList().forEach { packageName ->
            if (overrides.optString(packageName) == current) overrides.put(packageName, replacement)
        }
        settings.setAppCategoryOverrides(overrides.toString())

        val icons = jsonObject(settings.customCategoryIcons.first())
        val resolvedIcon = icon ?: icons.optString(current, "folder")
        icons.remove(current)
        icons.put(replacement, resolvedIcon)
        settings.setCustomCategoryIcons(icons.toString())

        val presentationMap = if (replacement == current) {
            settings.customCategoryPresentations.first()
        } else {
            CustomCategoryPresentationStore.rename(
                settings.customCategoryPresentations.first(),
                current,
                replacement,
            )
        }
        settings.setCustomCategoryPresentations(
            presentation?.let {
                CustomCategoryPresentationStore.update(presentationMap, replacement, it)
            } ?: presentationMap,
        )

        val tileSizes = jsonObject(settings.categoryTilesSizes.first())
        val size = tileSizes.optString(current, "")
        tileSizes.remove(current)
        if (size.isNotBlank()) tileSizes.put(replacement, size)
        settings.setCategoryTilesSizes(tileSizes.toString())

        settings.setCategoryOrder(replaceCategoryInCsv(settings.categoryOrder.first(), current, replacement))
        settings.setHiddenHomeCategories(
            replaceCategoryInCsv(settings.hiddenHomeCategories.first(), current, replacement),
        )

        updateLayout { layout ->
            layout.copy(workspaces = layout.workspaces.map { workspace ->
                workspace.copy(
                    categoryKeys = workspace.categoryKeys.map { key ->
                        if (key == current) replacement else key
                    }.distinct(),
                )
            })
        }
        repo.loadApps()
    }

    /** Moves all members into a destination collection and removes only the source collection. */
    fun mergeCustomCategories(sourceName: String, destinationName: String) = viewModelScope.launch {
        val source = sourceName.trim()
        val destination = destinationName.trim()
        val categoryNames = parsePackageCsv(settings.customCategories.first())
        if (source == destination || source !in categoryNames || destination !in categoryNames) return@launch

        settings.setCustomCategories(categoryNames.filterNot { it == source }.joinToString(","))

        val overrides = jsonObject(settings.appCategoryOverrides.first())
        overrides.keys().asSequence().toList().forEach { packageName ->
            if (overrides.optString(packageName) == source) overrides.put(packageName, destination)
        }
        settings.setAppCategoryOverrides(overrides.toString())

        val icons = jsonObject(settings.customCategoryIcons.first())
        icons.remove(source)
        settings.setCustomCategoryIcons(icons.toString())
        settings.setCustomCategoryPresentations(
            CustomCategoryPresentationStore.remove(settings.customCategoryPresentations.first(), source),
        )

        val tileSizes = jsonObject(settings.categoryTilesSizes.first())
        if (!tileSizes.has(destination) && tileSizes.has(source)) {
            tileSizes.put(destination, tileSizes.optString(source))
        }
        tileSizes.remove(source)
        settings.setCategoryTilesSizes(tileSizes.toString())

        settings.setCategoryOrder(replaceCategoryInCsv(settings.categoryOrder.first(), source, destination))
        settings.setHiddenHomeCategories(
            replaceCategoryInCsv(settings.hiddenHomeCategories.first(), source, destination),
        )

        updateLayout { layout ->
            layout.copy(workspaces = layout.workspaces.map { workspace ->
                workspace.copy(
                    categoryKeys = workspace.categoryKeys.map { key ->
                        if (key == source) destination else key
                    }.distinct(),
                )
            })
        }
        repo.loadApps()
    }

    fun updateAppAppearance(
        packageName: String,
        label: String,
        originalLabel: String,
        scale: Float,
        rotation: Float,
        accent: String?,
    ) = viewModelScope.launch {
        val labelMap = try { JSONObject(appLabelOverrides.value) } catch (_: Exception) { JSONObject() }
        if (label.trim().isBlank() || label.trim() == originalLabel) {
            labelMap.remove(packageName)
        } else {
            labelMap.put(packageName, label.trim().take(40))
        }
        settings.setAppLabelOverrides(labelMap.toString())

        val visualMap = try { JSONObject(appVisualOverrides.value) } catch (_: Exception) { JSONObject() }
        val isDefault = scale == 1f && rotation == 0f && accent.isNullOrBlank()
        if (isDefault) {
            visualMap.remove(packageName)
        } else {
            visualMap.put(packageName, JSONObject().apply {
                put("scale", scale.toDouble())
                put("rotation", rotation.toDouble())
                put("accent", accent ?: "")
            })
        }
        settings.setAppVisualOverrides(visualMap.toString())
        repo.loadApps()
    }

    fun setCategoryTileSize(categoryKey: String, size: String) = viewModelScope.launch {
        val map = try { JSONObject(categoryTilesSizes.value) } catch (_: Exception) { JSONObject() }
        map.put(categoryKey, size)
        settings.setCategoryTilesSizes(map.toString())
    }

    fun getCategoryTileSize(categoryKey: String): String {
        return try {
            val map = JSONObject(categoryTilesSizes.value)
            map.optString(categoryKey, "medium")
        } catch (_: Exception) { "medium" }
    }

    // ── Atomic layout writer ─────────────────────────────────────────────────
    // Every workspace-layout mutation goes through this single serialized writer.
    // Previously each mutator was its own launch{} doing read-modify-write on the
    // whole layout; concurrent adds (the "+ Add App" multi-select loop) all read
    // the same base and last-writer-wins clobbered the rest — the added app would
    // briefly appear then vanish. The Mutex guarantees each transform sees a fresh,
    // fully-committed base, so no update is ever lost.
    private val layoutMutex = Mutex()

    private suspend fun updateLayout(transform: (WorkspaceLayout) -> WorkspaceLayout?) {
        layoutMutex.withLock {
            val base = WorkspaceStore.parse(settings.workspaceLayoutV2.first())
                ?: legacyWorkspaceLayoutFromSettings()
            transform(base)?.let { persistWorkspaceLayout(it) }
        }
    }

    // ── Multi-Page Custom Apps ───────────────────────────────────────────────

    /** Atomic batch add — the whole selection persists in one transaction, each
     *  app dropped into the next free grid cell. */
    fun addAppsToPage(pageIndex: Int, packages: Collection<String>) = viewModelScope.launch {
        unhidePackages(packages)
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.addAppsAtFreeCells(layout, workspace.id, packages)
        }
    }

    fun addAppToPage(pageIndex: Int, pkg: String) = addAppsToPage(pageIndex, listOf(pkg))

    fun removeAppFromPage(pageIndex: Int, pkg: String) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.removeApp(layout, workspace.id, pkg)
        }
    }

    /** Places an app at a specific grid cell (used by free drag). */
    fun placeAppAtCell(pageIndex: Int, pkg: String, cell: Int) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.placeApp(layout, workspace.id, pkg, cell)
        }
    }

    /**
     * Resizes a home-screen tile to span [spanX] x [spanY] cells.
     *
     * WorkspaceStore.resizeApp returns null when the new rectangle would overlap
     * a neighbour or run off the right edge, and updateLayout skips a null
     * transform — so a rejected resize leaves the saved layout exactly as it was
     * rather than persisting a size that doesn't actually fit. Callers must
     * reflect that by snapping the tile back, never by showing the rejected size.
     */
    fun resizeAppTile(pageIndex: Int, pkg: String, spanX: Int, spanY: Int) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.resizeApp(layout, workspace.id, pkg, spanX, spanY)
        }
    }

    /**
     * Moves an app to a free canvas position on [pageIndex] — [x]/[y] are
     * normalized fractions of the workspace grid's own rendered area (see
     * [CanvasPos]). The grid still generates the DEFAULT layout, but once an
     * object is dragged like this it renders at this position instead, on
     * every future load, until [resetAppToGridPos]. z always comes from
     * [WorkspaceStore.nextZ] so the moved object comes to the front, same as
     * bringing a window forward on a desktop.
     */
    fun moveAppToCanvasPos(pageIndex: Int, pkg: String, x: Float, y: Float) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.moveAppToCanvas(layout, workspace.id, pkg, x, y, WorkspaceStore.nextZ(layout, workspace.id))
        }
    }

    /** Clears an app's free canvas position on [pageIndex] so it falls back
     *  to ordinary grid flow at its preserved cell. */
    fun resetAppToGridPos(pageIndex: Int, pkg: String) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.resetAppToGrid(layout, workspace.id, pkg)
        }
    }

    /**
     * Moves a non-app Home object (greeting, search, weather, a category
     * card…) to a free canvas position on [pageIndex] — same coordinate
     * contract as [moveAppToCanvasPos]: [x]/[y] are normalized fractions of
     * the page's own canvas area. [objectId] is a stable id (see
     * [WorkspaceRecord.objectPositions]), e.g. "greeting", "datetime",
     * "category:Work". z comes from [WorkspaceStore.nextZ] — the SAME shared
     * counter [moveAppToCanvasPos] uses — so an object dragged after an app
     * outranks it, and vice versa.
     */
    fun moveObjectToCanvasPos(pageIndex: Int, objectId: String, x: Float, y: Float) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.moveObjectToCanvas(layout, workspace.id, objectId, x, y, WorkspaceStore.nextZ(layout, workspace.id))
        }
    }

    /** Clears a Home object's free canvas position on [pageIndex] so it
     *  returns to ordinary document flow. */
    fun resetObjectToFlowPos(pageIndex: Int, objectId: String) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.resetObjectToFlow(layout, workspace.id, objectId)
        }
    }

    /** objectId -> free canvas position, for one page. Mirrors [canvasPosForPage]
     *  for apps; an id absent here is still flow-positioned. */
    fun objectPositionsForPage(pageIndex: Int): Map<String, CanvasPos> {
        val layout = currentWorkspaceLayout()
        val workspaceId = workspaceIdForPage(layout, pageIndex) ?: return emptyMap()
        return layout.workspaceById(workspaceId)?.objectPositions.orEmpty()
    }

    /** Hides a Home object with no dedicated global setting of its own (e.g.
     *  "datetime") on [pageIndex]. See [WorkspaceRecord.hiddenObjects]. */
    fun hideObjectOnPage(pageIndex: Int, objectId: String) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.hideObject(layout, workspace.id, objectId)
        }
    }

    /** Reverses [hideObjectOnPage]. */
    fun showObjectOnPage(pageIndex: Int, objectId: String) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.showObject(layout, workspace.id, objectId)
        }
    }

    /** Object ids explicitly hidden on [pageIndex]. */
    fun hiddenObjectsForPage(pageIndex: Int): Set<String> {
        val layout = currentWorkspaceLayout()
        val workspaceId = workspaceIdForPage(layout, pageIndex) ?: return emptySet()
        return layout.workspaceById(workspaceId)?.hiddenObjects.orEmpty()
    }

    fun getAppsForPage(pageIndex: Int): List<InstalledApp> {
        val layout = currentWorkspaceLayout()
        val workspaceId = workspaceIdForPage(layout, pageIndex) ?: return emptyList()
        val packages = layout.workspaceById(workspaceId)?.appPackages.orEmpty()
        val byPkg = apps.value.associateBy { it.packageName }
        return packages.mapNotNull { byPkg[it] }
    }

    /**
     * The home page the person is currently on. Restored when HomeScreen is
     * recreated (returning from a category, an app, Settings…) so they stay put
     * instead of snapping back to a workspace. In-memory only: a cold start
     * always opens on the centre Home page (index 1).
     */
    var lastHomePage: Int = 1

    // ── Grid dimensions & positioned cells ────────────────────────────────────

    val gridSize = settings.gridSize.stateIn(viewModelScope, SharingStarted.Eagerly, "6x5")

    /** Parsed (columns, rows) with sane bounds. Default 6×5 is the default layout. */
    fun gridColsRows(): Pair<Int, Int> {
        val parts = gridSize.value.split("x")
        val cols = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(3, 8) ?: 6
        val rows = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(3, 8) ?: 5
        return cols to rows
    }

    /**
     * Make the layout model agree with the grid the screen actually draws.
     *
     * Two independent defaults disagreed: the renderer takes its column count
     * from [gridSize] ("6x5"), while [WorkspaceLayout.authorColumns] defaults
     * to 4 — and nothing wrote authorColumns except [setGridSize], which only
     * runs if someone opens Theme Studio and picks a size. So on a fresh
     * install the grid was drawn 6 wide over a model that placed, spanned and
     * bounds-checked every cell against 4.
     *
     * That single mismatch produced several long-standing symptoms at once:
     * resizing appeared to do nothing (the store rejected spans that overflowed
     * its narrower row), and newly added icons landed underneath existing ones
     * (the store's idea of a free cell was a different screen position).
     *
     * [LauncherSettingsRepository.resetLayout] also writes the grid-size key
     * straight to DataStore without going through [setGridSize], so the two
     * could drift apart again after a reset. Reconciling at startup repairs
     * that case too, instead of only preventing new ones.
     */
    private fun syncGridColumnsToLayout() = viewModelScope.launch {
        // Deliberately the stored value, not gridSize.value: that StateFlow
        // carries a "6x5" placeholder until DataStore answers, and reconciling
        // against a placeholder would reflow a 4-column layout to 6 for someone
        // who had actually chosen 4x4. first() waits for the real answer.
        val stored = settings.gridSize.first()
        val cols = stored.split("x").getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(3, 8) ?: return@launch
        updateLayout { layout ->
            // Returning null skips the write entirely, so the common case
            // (already in agreement) costs nothing but a read.
            if (layout.authorColumns == cols) null
            else layout.copy(
                authorColumns = cols,
                workspaces = layout.workspaces.map { WorkspaceStore.reflow(it, cols) },
            )
        }
    }

    /** Change the grid dimensions and reflow every workspace to the new columns. */
    fun setGridSize(value: String) = viewModelScope.launch {
        val parts = value.split("x")
        val cols = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(4, 6) ?: 4
        val rows = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(4, 6) ?: 5
        settings.setGridSize("${cols}x${rows}")
        updateLayout { layout ->
            layout.copy(
                authorColumns = cols,
                workspaces = layout.workspaces.map { WorkspaceStore.reflow(it, cols) },
            )
        }
    }

    /** cell index → installed app, for one workspace page. Missing apps drop out. */
    fun cellAppsForPage(pageIndex: Int): Map<Int, InstalledApp> {
        val layout = currentWorkspaceLayout()
        val workspaceId = workspaceIdForPage(layout, pageIndex) ?: return emptyMap()
        val cells = layout.workspaceById(workspaceId)?.cells.orEmpty()
        val byPkg = apps.value.associateBy { it.packageName }
        return cells.mapNotNull { c -> byPkg[c.packageName]?.let { c.cell to it } }.toMap()
    }

    /** cell index → (spanX, spanY), for one workspace page. Mirrors
     *  [cellAppsForPage]'s keys — WorkspaceGrid needs both maps to render and
     *  resize spanning tiles, since [cellAppsForPage] alone loses span. */
    fun cellSpansForPage(pageIndex: Int): Map<Int, Pair<Int, Int>> {
        val layout = currentWorkspaceLayout()
        val workspaceId = workspaceIdForPage(layout, pageIndex) ?: return emptyMap()
        val cells = layout.workspaceById(workspaceId)?.cells.orEmpty()
        return cells.associate { it.cell to (it.spanX to it.spanY) }
    }

    /** packageName -> free canvas position, for one workspace page. Mirrors
     *  [cellAppsForPage]/[cellSpansForPage]'s page resolution. A package
     *  absent here is still grid-positioned — see [AppCell.pos]. */
    fun canvasPosForPage(pageIndex: Int): Map<String, CanvasPos> {
        val layout = currentWorkspaceLayout()
        val workspaceId = workspaceIdForPage(layout, pageIndex) ?: return emptyMap()
        val cells = layout.workspaceById(workspaceId)?.cells.orEmpty()
        return cells.mapNotNull { c -> c.pos?.let { c.packageName to it } }.toMap()
    }

    fun getCategoriesForWorkspace(pageIndex: Int): List<String> {
        val layout = currentWorkspaceLayout()
        return workspaceIdForPage(layout, pageIndex)
            ?.let { layout.workspaceById(it)?.categoryKeys }
            .orEmpty()
    }

    fun workspaceName(pageIndex: Int): String {
        val layout = currentWorkspaceLayout()
        return workspaceIdForPage(layout, pageIndex)
            ?.let { layout.workspaceById(it) }
            ?.name
            ?: "Workspace"
    }

    fun workspaceOverview(): List<WorkspaceRecord> {
        val layout = currentWorkspaceLayout()
        return layout.visualOrder.mapNotNull { id -> layout.workspaces.firstOrNull { it.id == id } }
    }

    fun workspaceLayoutSnapshot(): String = WorkspaceStore.parse(workspaceLayoutV2.value)
        ?.let(WorkspaceStore::serialize)
        ?: WorkspaceStore.serialize(legacyWorkspaceLayout())

    fun isDefaultWorkspace(pageIndex: Int): Boolean {
        val layout = currentWorkspaceLayout()
        return workspaceIdForPage(layout, pageIndex)
            ?.let { layout.workspaceById(it)?.id }
            ?.let { it == layout.defaultWorkspaceId }
            ?: false
    }

    fun addCategoryToWorkspace(pageIndex: Int, categoryKey: String) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            if (categoryKey in workspace.categoryKeys) return@updateLayout null
            WorkspaceStore.withWorkspace(layout, workspace.copy(categoryKeys = workspace.categoryKeys + categoryKey))
        }
    }

    fun removeCategoryFromWorkspace(pageIndex: Int, categoryKey: String) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            if (categoryKey !in workspace.categoryKeys) return@updateLayout null
            WorkspaceStore.withWorkspace(layout, workspace.copy(categoryKeys = workspace.categoryKeys - categoryKey))
        }
    }

    fun moveCategoryInWorkspace(pageIndex: Int, categoryKey: String, shift: Int) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            val categories = workspace.categoryKeys.toMutableList()
            val from = categories.indexOf(categoryKey)
            if (from < 0) return@updateLayout null
            val to = (from + shift).coerceIn(0, categories.lastIndex)
            if (from == to) return@updateLayout null
            categories.removeAt(from)
            categories.add(to, categoryKey)
            WorkspaceStore.withWorkspace(layout, workspace.copy(categoryKeys = categories))
        }
    }

    fun moveCategoryBetweenWorkspaces(fromPage: Int, toPage: Int, categoryKey: String) = viewModelScope.launch {
        updateLayout { layout ->
            if (fromPage == toPage) return@updateLayout null
            val from = layout.workspaceById(workspaceIdForPage(layout, fromPage) ?: return@updateLayout null)
                ?: return@updateLayout null
            val to = layout.workspaceById(workspaceIdForPage(layout, toPage) ?: return@updateLayout null)
                ?: return@updateLayout null
            if (categoryKey !in from.categoryKeys) return@updateLayout null
            val without = WorkspaceStore.withWorkspace(layout, from.copy(categoryKeys = from.categoryKeys - categoryKey))
                ?: return@updateLayout null
            val destination = without.workspaces.first { it.id == to.id }
            WorkspaceStore.withWorkspace(without, destination.copy(categoryKeys = destination.categoryKeys + categoryKey))
        }
    }

    /** Accessible alternative to drag-and-drop for placing a collection in one workspace. */
    fun moveCategoryToWorkspace(categoryKey: String, destinationPage: Int) = viewModelScope.launch {
        updateLayout { layout ->
            val destination = layout.workspaceById(workspaceIdForPage(layout, destinationPage) ?: return@updateLayout null)
                ?: return@updateLayout null
            layout.copy(workspaces = layout.workspaces.map { workspace ->
                val withoutCategory = workspace.categoryKeys.filterNot { it == categoryKey }
                if (workspace.id == destination.id) {
                    workspace.copy(categoryKeys = (withoutCategory + categoryKey).distinct())
                } else {
                    workspace.copy(categoryKeys = withoutCategory)
                }
            })
        }
    }

    fun moveAppBetweenWorkspaces(fromPage: Int, toPage: Int, packageName: String) = viewModelScope.launch {
        updateLayout { layout ->
            if (fromPage == toPage) return@updateLayout null
            val from = layout.workspaceById(workspaceIdForPage(layout, fromPage) ?: return@updateLayout null)
                ?: return@updateLayout null
            val to = layout.workspaceById(workspaceIdForPage(layout, toPage) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.moveApp(layout, from.id, to.id, packageName, WorkspaceStore.firstFreeCell(to.cells, layout.authorColumns))
        }
    }

    fun moveAppWithinWorkspace(pageIndex: Int, packageName: String, destinationIndex: Int) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            WorkspaceStore.moveAppWithinWorkspace(layout, workspace.id, packageName, destinationIndex)
        }
    }

    fun addWorkspace() = insertWorkspaceAt(currentWorkspaceLayout().visualOrder.size)

    fun insertWorkspaceAt(visualIndex: Int) = viewModelScope.launch {
        updateLayout { layout -> WorkspaceStore.insert(layout, visualIndex) }
    }

    fun insertWorkspaceBeforePage(pageIndex: Int) =
        insertWorkspaceAt(visualIndexForPage(pageIndex) ?: 0)

    fun insertWorkspaceAfterPage(pageIndex: Int) =
        insertWorkspaceAt((visualIndexForPage(pageIndex) ?: currentWorkspaceLayout().visualOrder.lastIndex) + 1)

    fun renameWorkspace(pageIndex: Int, name: String) = viewModelScope.launch {
        updateLayout { layout ->
            val id = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)?.id
                ?: return@updateLayout null
            WorkspaceStore.rename(layout, id, name)
        }
    }

    fun duplicateWorkspace(pageIndex: Int) = viewModelScope.launch {
        updateLayout { layout ->
            val id = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)?.id
                ?: return@updateLayout null
            WorkspaceStore.duplicate(layout, id)
        }
    }

    fun setDefaultWorkspace(pageIndex: Int) = viewModelScope.launch {
        updateLayout { layout ->
            val id = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)?.id
                ?: return@updateLayout null
            WorkspaceStore.setDefault(layout, id)
        }
    }

    fun dismissWorkspaceStarter(pageIndex: Int) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            if (workspace.starterDismissed) return@updateLayout null
            WorkspaceStore.withWorkspace(layout, workspace.copy(starterDismissed = true))
        }
    }

    fun applyWorkspaceTemplate(pageIndex: Int, categoryKeys: List<String>) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            val merged = (workspace.categoryKeys + categoryKeys).distinct()
            WorkspaceStore.withWorkspace(layout, workspace.copy(categoryKeys = merged, starterDismissed = true))
        }
    }

    fun reorderWorkspace(fromIndex: Int, toIndex: Int) = viewModelScope.launch {
        updateLayout { layout -> WorkspaceStore.reorder(layout, fromIndex, toIndex) }
    }

    fun removeWorkspace(pageIndex: Int, moveContentsToPage: Int? = null) = viewModelScope.launch {
        updateLayout { layout ->
            val workspace = layout.workspaceById(workspaceIdForPage(layout, pageIndex) ?: return@updateLayout null)
                ?: return@updateLayout null
            val destination = moveContentsToPage?.let { workspaceIdForPage(layout, it) }
            WorkspaceStore.remove(layout, workspace.id, destination)
        }
    }

    fun removeLastWorkspace() = removeWorkspace(workspaceCount.value - 1)

    /** Resolves a pager page to the id of the [WorkspaceRecord] backing it — page 1
     *  is the fixed Home page, pages 0 and 2+ are movable workspaces addressed by
     *  their [WorkspaceLayout.visualOrder] position. Giving Home a real, stable id
     *  here (instead of the old index-based lookup, which had no slot for page 1
     *  at all) is what lets every grid operation below actually persist on Home. */
    private fun workspaceIdForPage(layout: WorkspaceLayout, pageIndex: Int): String? = when {
        pageIndex == 1 -> WorkspaceLayout.HOME_WORKSPACE_ID
        pageIndex == 0 -> layout.visualOrder.getOrNull(0)
        pageIndex >= 2 -> layout.visualOrder.getOrNull(pageIndex - 1)
        else -> null
    }

    /** Same page→position mapping as [workspaceIdForPage], but as a raw
     *  [WorkspaceLayout.visualOrder] index for callers that are inserting a new
     *  workspace rather than looking up an existing one — Home has no visual
     *  position, so (unlike [workspaceIdForPage]) it has nothing to return for
     *  page 1; callers never invoke this for page 1 in practice ("+ Workspace"
     *  only renders on non-Home pages). */
    private fun visualIndexForPage(pageIndex: Int): Int? = when {
        pageIndex == 0 -> 0
        pageIndex >= 2 -> pageIndex - 1
        else -> null
    }

    private fun currentWorkspaceLayout(): WorkspaceLayout =
        WorkspaceStore.parse(workspaceLayoutV2.value) ?: legacyWorkspaceLayout()

    private suspend fun currentWorkspaceLayoutForWrite(): WorkspaceLayout =
        WorkspaceStore.parse(settings.workspaceLayoutV2.first()) ?: legacyWorkspaceLayoutFromSettings()

    private fun legacyWorkspaceLayout(): WorkspaceLayout = WorkspaceStore.migrateLegacy(
        count = workspaceCount.value,
        page0Apps = page0Apps.value,
        page2Apps = page2Apps.value,
        workspaceApps = workspaceApps.value,
        workspaceCategories = workspaceCategories.value,
        ciyatoPackage = getApplication<Application>().packageName,
    )

    private suspend fun legacyWorkspaceLayoutFromSettings(): WorkspaceLayout = WorkspaceStore.migrateLegacy(
        count = settings.workspaceCount.first(),
        page0Apps = settings.page0Apps.first(),
        page2Apps = settings.page2Apps.first(),
        workspaceApps = settings.workspaceApps.first(),
        workspaceCategories = settings.workspaceCategories.first(),
        ciyatoPackage = getApplication<Application>().packageName,
    )

    private suspend fun ensureWorkspaceLayoutMigration() {
        // Under the same lock as every mutator, and re-checked inside it, so a
        // user add that lands between the read and the write can't be clobbered
        // by the legacy-migration overwrite (cold-start TOCTOU).
        layoutMutex.withLock {
            if (WorkspaceStore.parse(settings.workspaceLayoutV2.first()) != null) return@withLock
            val legacy = legacyWorkspaceLayoutFromSettings()
            settings.setWorkspaceLayoutV2(WorkspaceStore.serialize(legacy))
            settings.setWorkspaceCount(legacy.visualOrder.size + 1)
        }
    }

    private suspend fun persistWorkspaceLayout(layout: WorkspaceLayout) {
        settings.setWorkspaceLayoutV2(WorkspaceStore.serialize(layout))
        settings.setWorkspaceCount(layout.visualOrder.size + 1)
    }

    private fun jsonObject(raw: String): JSONObject = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())

    private fun replaceCategoryInCsv(raw: String, source: String, destination: String): String =
        parsePackageCsv(raw)
            .map { item -> if (item == source) destination else item }
            .distinct()
            .joinToString(",")

    // ── Screenshot blocking (Suggestion 145) ──────────────────────────────────

    fun applyScreenshotFlag(window: Window) {
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (screenshotBlocked.value) window.addFlags(flag) else window.clearFlags(flag)
    }

    // ── Hidden apps (Suggestion 23) ───────────────────────────────────────────

    fun hideApp(pkg: String) = viewModelScope.launch {
        val hidden = parsePackageCsv(settings.hiddenApps.first()).toMutableSet().apply { add(pkg) }
        val removed = parsePackageCsv(settings.removedApps.first()).toMutableSet().apply { remove(pkg) }
        val hiddenCsv = hidden.sorted().joinToString(",")
        val removedCsv = removed.sorted().joinToString(",")
        settings.setHiddenApps(hiddenCsv)
        settings.setRemovedApps(removedCsv)
        repo.setHiddenPackages(hiddenCsv)
        repo.setRemovedPackages(removedCsv)
    }

    fun unhideApp(pkg: String) = viewModelScope.launch {
        val hidden = parsePackageCsv(settings.hiddenApps.first()).toMutableSet().apply { remove(pkg) }
        val hiddenCsv = hidden.sorted().joinToString(",")
        settings.setHiddenApps(hiddenCsv)
        repo.setHiddenPackages(hiddenCsv)
    }

    /**
     * Placing an app onto Home, a workspace, or the dock is an explicit
     * "I want to see this" signal. [apps] (what the grid/dock resolve cells
     * against) excludes hidden/removed packages, while the add-app picker
     * lists from [allApps] (unfiltered) — so without this, adding a package
     * that happened to be hidden/removed stores it correctly but the icon
     * never resolves and silently "vanishes".
     */
    private suspend fun unhidePackages(packages: Collection<String>) {
        if (packages.isEmpty()) return
        val targets = packages.toSet()
        val hidden = parsePackageCsv(settings.hiddenApps.first()).toMutableSet()
        val removed = parsePackageCsv(settings.removedApps.first()).toMutableSet()
        val changedHidden = hidden.removeAll(targets)
        val changedRemoved = removed.removeAll(targets)
        if (!changedHidden && !changedRemoved) return
        val hiddenCsv = hidden.sorted().joinToString(",")
        val removedCsv = removed.sorted().joinToString(",")
        settings.setHiddenApps(hiddenCsv)
        settings.setRemovedApps(removedCsv)
        repo.setHiddenPackages(hiddenCsv)
        repo.setRemovedPackages(removedCsv)
    }

    fun removeAppFromDisplay(pkg: String) = viewModelScope.launch {
        val removed = parsePackageCsv(settings.removedApps.first()).toMutableSet().apply { add(pkg) }
        val hidden = parsePackageCsv(settings.hiddenApps.first()).toMutableSet().apply { remove(pkg) }
        val removedCsv = removed.sorted().joinToString(",")
        val hiddenCsv = hidden.sorted().joinToString(",")
        settings.setRemovedApps(removedCsv)
        settings.setHiddenApps(hiddenCsv)
        repo.setRemovedPackages(removedCsv)
        repo.setHiddenPackages(hiddenCsv)
    }

    fun restoreApp(pkg: String) = restoreApps(listOf(pkg))

    /**
     * Restores a whole selection in ONE read-modify-write.
     *
     * "Restore all" used to be `apps.forEach { restoreApp(it) }`, and each of
     * those is a read-modify-write that suspends at `first()` before writing.
     * On Main.immediate every coroutine therefore read the same original CSV
     * and wrote it back minus only its own package — last writer wins, so
     * restoring 8 hidden apps restored exactly one and silently left seven
     * hidden. Same lost-update shape the workspace layout hit, fixed the same
     * way: one read, one write, whole batch.
     */
    fun restoreApps(packages: Collection<String>) = viewModelScope.launch {
        if (packages.isEmpty()) return@launch
        val drop = packages.toSet()
        val removedCsv = parsePackageCsv(settings.removedApps.first())
            .filterNot { it in drop }.sorted().joinToString(",")
        val hiddenCsv = parsePackageCsv(settings.hiddenApps.first())
            .filterNot { it in drop }.sorted().joinToString(",")
        settings.setRemovedApps(removedCsv)
        settings.setHiddenApps(hiddenCsv)
        repo.setRemovedPackages(removedCsv)
        repo.setHiddenPackages(hiddenCsv)
    }

    fun isHidden(pkg: String): Boolean = pkg in parsePackageCsv(hiddenApps.value)
    fun isRemoved(pkg: String): Boolean = pkg in parsePackageCsv(removedApps.value)
    fun hiddenAppItems(): List<InstalledApp> = repo.hiddenApps()
    fun removedAppItems(): List<InstalledApp> = repo.removedApps()

    fun isPinnedToDock(pkg: String): Boolean = pkg in parsePackageCsv(dockPackages.value)

    fun defaultDockApps(): List<InstalledApp> {
        val available = apps.value
        if (available.isEmpty()) return emptyList()
        val byPackage = available.associateBy(InstalledApp::packageName)
        val packageManager = getApplication<Application>().packageManager
        fun handlerPackage(intent: Intent): String? =
            packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        val candidates = buildList {
            handlerPackage(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))?.let(::add)
            handlerPackage(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))?.let(::add)
            handlerPackage(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")))?.let(::add)
            available.firstOrNull { it.packageName == "com.google.android.youtube" }
                ?.packageName
                ?.let(::add)
                ?: available.firstOrNull { it.label.contains("youtube", ignoreCase = true) }
                    ?.packageName
                    ?.let(::add)
            handlerPackage(Intent(MediaStore.ACTION_IMAGE_CAPTURE))?.let(::add)
        }
        return candidates.distinct().mapNotNull(byPackage::get).take(5)
    }

    val dockInitialized = settings.dockInitialized.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Seeds the dock with sensible defaults exactly once, and never again after
     *  the person has intentionally edited (or emptied) it. */
    fun ensureDefaultDock() = viewModelScope.launch {
        if (settings.dockInitialized.first()) return@launch
        val defaults = defaultDockApps()
        if (defaults.isNotEmpty()) settings.setDockPackages(defaults.joinToString(",") { it.packageName })
        settings.setDockInitialized(true)
    }

    fun pinToDock(pkg: String) = pinToDockAt(pkg, 0)

    /** Inserts [pkg] at [index] in the dock (cap 5). Marks the dock as user-managed. */
    fun pinToDockAt(pkg: String, index: Int) = viewModelScope.launch {
        unhidePackages(listOf(pkg))
        val current = settings.dockPackages.first()
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it == pkg }
            .toMutableList()
        current.add(index.coerceIn(0, current.size), pkg)
        settings.setDockPackages(current.take(5).joinToString(","))
        settings.setDockInitialized(true)
    }

    fun unpinFromDock(pkg: String) = viewModelScope.launch {
        val updated = settings.dockPackages.first()
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it == pkg }
        settings.setDockPackages(updated.joinToString(","))
        // Mark as user-managed so an emptied dock stays empty (no auto-repopulate).
        settings.setDockInitialized(true)
    }

    fun moveDockShortcut(pkg: String, shift: Int) = viewModelScope.launch {
        val current = settings.dockPackages.first()
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toMutableList()
        val from = current.indexOf(pkg)
        if (from >= 0) {
            val destination = (from + shift).coerceIn(0, current.lastIndex)
            if (destination != from) {
                current.removeAt(from)
                current.add(destination, pkg)
                settings.setDockPackages(current.joinToString(","))
                settings.setDockInitialized(true)
            }
        }
    }

    // ── Category renames (Suggestion 24) ──────────────────────────────────────

    fun setCategoryRename(cat: AppCategory, newName: String) = viewModelScope.launch {
        val map = try { JSONObject(categoryRenames.value) } catch (_: Exception) { JSONObject() }
        map.put(cat.name, newName)
        settings.setCategoryRenames(map.toString())
    }

    fun setAppCategoryOverride(packageName: String, newCategory: AppCategory?) = viewModelScope.launch {
        val map = try { JSONObject(appCategoryOverrides.value) } catch (_: Exception) { JSONObject() }
        if (newCategory == null) {
            map.remove(packageName)
        } else {
            map.put(packageName, newCategory.name)
        }
        settings.setAppCategoryOverrides(map.toString())
        repo.loadApps()
    }

    fun getCategoryDisplayName(category: AppCategory): String {
        return try {
            val map = JSONObject(categoryRenames.value)
            map.optString(category.name).takeIf { it.isNotBlank() } ?: category.displayName
        } catch (_: Exception) { category.displayName }
    }

    // ── Recently launched (Suggestion 25) ────────────────────────────────────

    val recentlyLaunchedPackages = settings.recentlyLaunched
        .stateIn(viewModelScope, SharingStarted.Eagerly, "[]")

    fun getRecentlyLaunchedApps(): List<InstalledApp> {
        val pkgs = try {
            val arr = JSONArray(recentlyLaunchedPackages.value)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
        val byPkg = apps.value.associateBy { it.packageName }
        return pkgs.mapNotNull { byPkg[it] }
    }

    private suspend fun recordLaunch(pkg: String) {
        val pkgs = try {
            val arr = JSONArray(settings.recentlyLaunched.first())
            (0 until arr.length()).map { arr.getString(it) }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
        pkgs.remove(pkg)
        pkgs.add(0, pkg)
        val capped = pkgs.take(10)
        val arr = JSONArray().also { capped.forEach { p -> it.put(p) } }
        settings.setRecentlyLaunched(arr.toString())
    }

    // ── Recent searches (Suggestion 36) ───────────────────────────────────────

    val recentSearches = settings.recentSearches
        .map { json ->
            try { val arr = JSONArray(json); (0 until arr.length()).map { arr.getString(it) } }
            catch (_: Exception) { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private suspend fun addRecentSearch(q: String) {
        val current = try {
            val arr = JSONArray(settings.recentSearches.first())
            (0 until arr.length()).map { arr.getString(it) }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
        current.remove(q)
        current.add(0, q)
        val capped = current.take(10)
        settings.setRecentSearches(JSONArray().also { capped.forEach { s -> it.put(s) } }.toString())
    }

    fun removeRecentSearch(q: String) = viewModelScope.launch {
        val current = try {
            val arr = JSONArray(settings.recentSearches.first())
            (0 until arr.length()).map { arr.getString(it) }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
        current.remove(q)
        settings.setRecentSearches(JSONArray().also { current.forEach { s -> it.put(s) } }.toString())
    }

    fun clearRecentSearches() = viewModelScope.launch { settings.setRecentSearches("[]") }

    // ── Category helpers ──────────────────────────────────────────────────────

    fun byCategory(cat: AppCategory)       = repo.byCategory(cat)
    fun multiCategoryApps()                = repo.multiCategoryApps()
    fun recentlyAdded()                    = repo.recentlyAdded()
    fun categoriesForApp(app: InstalledApp)= repo.categoriesForApp(app)
    fun byUsageFrequency()                 = repo.byUsageFrequency()
    fun launchCount(pkg: String)           = repo.launchCount(pkg)

    // ── Time-aware layout helpers (Suggestion 72) ─────────────────────────────

    val currentHour: Int get() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val currentDayOfWeek: Int get() = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

    /** Returns the "featured" categories for the current time of day. */
    fun timeAwareCategories(): List<AppCategory> {
        val hour = currentHour
        val isWeekend = currentDayOfWeek in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        return when {
            hour < 7              -> listOf(AppCategory.DAILY, AppCategory.UTILITIES, AppCategory.PRODUCTIVITY)
            hour < 12             -> listOf(AppCategory.WORK, AppCategory.PRODUCTIVITY, AppCategory.COMMUNICATION)
            hour < 14             -> listOf(AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.DAILY)
            hour < 18 && !isWeekend -> listOf(AppCategory.WORK, AppCategory.PRODUCTIVITY, AppCategory.FINANCE)
            hour < 18 && isWeekend  -> listOf(AppCategory.ENTERTAINMENT, AppCategory.SOCIAL, AppCategory.TRAVEL)
            hour < 22             -> listOf(AppCategory.ENTERTAINMENT, AppCategory.SOCIAL, AppCategory.CREATIVITY)
            else                  -> listOf(AppCategory.DAILY, AppCategory.ENTERTAINMENT, AppCategory.UTILITIES)
        }
    }

    /** True if bedtime mode should be active right now. */
    fun isBedtimeNow(): Boolean {
        if (!bedtimeMode.value) return false
        return currentHour >= bedtimeHour.value
    }

    // ── Focus sessions (Suggestion 75) ────────────────────────────────────────

    /**
     * The running session, derived from the persisted end instant.
     *
     * Survives process death and reboot because nothing about it is held in
     * memory — see [FocusSessionManager]. Note this emits when the stored values
     * change, not once per second: expiry is evaluated whenever the value is
     * read, and a screen that wants a live countdown ticks locally for display.
     */
    val focusSession: StateFlow<FocusSessionManager.FocusSession?> =
        combine(
            settings.focusEndsAt,
            settings.focusDurationMin,
            settings.focusBlockedCats,
        ) { endsAt, durationMin, csv ->
            FocusSessionManager.sessionOf(endsAt, durationMin, csv)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Starts a session using the person's own configured duration and categories. */
    fun startFocusSession(durationMin: Int? = null) = viewModelScope.launch {
        val minutes = (durationMin ?: focusDurationMin.value).coerceIn(1, 120)
        // Absolute end instant, so no ticker owns the session's lifetime.
        settings.setFocusEndsAt(System.currentTimeMillis() + minutes * 60_000L)
    }

    fun endFocusSession() = viewModelScope.launch {
        settings.setFocusEndsAt(0L)
    }

    /**
     * Whether a category is held back right now.
     *
     * Reads the clock rather than a cached flag, so an expired session stops
     * taking effect on its own. Deliberately scoped to launches that go through
     * Ciyato — this is not OS-level enforcement and the UI must not say it is.
     */
    fun isCategoryBlocked(cat: AppCategory): Boolean =
        FocusSessionManager.isBlocked(focusSession.value, cat)

    // ── App launch ────────────────────────────────────────────────────────────

    fun launchApp(app: InstalledApp) {
        if (isCategoryBlocked(app.category)) {
            // Scoped honestly: Ciyato declined to open it. It is not blocked
            // at the OS level and remains reachable elsewhere.
            _toastEvent.value = Event("Focus is on — Ciyato won't open ${app.label}")
            return
        }
        val context = getApplication<Application>()
        // Ciyato's own tile must always open the organizer, never re-resolve the
        // home activity (which is already in the foreground and looks like a no-op).
        if (app.packageName == context.packageName) {
            context.startActivity(
                Intent(context, com.ciyato.launcher.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            viewModelScope.launch { recordLaunch(app.packageName) }
            return
        }
        val launched = repo.launchApp(context, app)
        if (launched) {
            viewModelScope.launch { recordLaunch(app.packageName) }
        } else {
            _toastEvent.value = Event("Could not open ${app.label}")
        }
    }

    private val _toastEvent = MutableStateFlow<Event<String>?>(null)
    val toastEvent: StateFlow<Event<String>?> = _toastEvent.asStateFlow()

    /** Avoid a broad installed-app scan on each ordinary launcher resume. */
    fun refreshApps() = viewModelScope.launch { repo.refreshIfStale() }

    // ── Live weather (Open-Meteo) ─────────────────────────────────────────────

    private val _weatherState = MutableStateFlow<WeatherRepository.WeatherState>(
        WeatherRepository.WeatherState.NoPermission
    )
    val weatherState: StateFlow<WeatherRepository.WeatherState> = _weatherState.asStateFlow()

    /**
     * Fetches weather, using the persisted cache both as a TTL-based "don't
     * refetch yet" short-circuit AND, on a genuine network failure, as a
     * last-known-good snapshot so the user sees real (if stale) numbers
     * instead of a bare failure screen. See [applyWeatherResult].
     */
    fun fetchWeather(context: Context) {
        viewModelScope.launch {
            val cacheJson = settings.weatherCacheJson.first()
            val cacheAt   = settings.weatherCacheAt.first()
            val cacheAge  = System.currentTimeMillis() - cacheAt
            val cached    = weatherStateFromCacheJson(cacheJson, cacheAt)

            // Fresh cache (within TTL) is painted instantly and marked NOT
            // stale — covers both "already fetched this session" and "cold
            // start with a recent snapshot still on disk", neither of which
            // needs a network round trip.
            if (cached != null && cacheAge < BuildConfig.WEATHER_CACHE_TTL_MS) {
                _weatherState.value = cached.copy(isStale = false, cachedAtMillis = null)
                return@launch
            }

            if (_weatherState.value !is WeatherRepository.WeatherState.Success) {
                _weatherState.value = WeatherRepository.WeatherState.Loading
            }
            val loc = LocationHelper.getLocation(context)
            val result = if (loc != null) {
                WeatherRepository.fetchWeather(loc.lat, loc.lon)
            } else {
                WeatherRepository.WeatherState.NoLocation
            }
            applyWeatherResult(result, cached)
        }
    }

    fun forceRefreshWeather(context: Context) {
        viewModelScope.launch {
            // Deliberately does NOT clear the cache first: if the forced
            // refresh itself fails, the last known-good snapshot is still
            // there to fall back to (stale-labeled) instead of losing it.
            val cacheJson = settings.weatherCacheJson.first()
            val cacheAt   = settings.weatherCacheAt.first()
            val cached    = weatherStateFromCacheJson(cacheJson, cacheAt)

            if (_weatherState.value !is WeatherRepository.WeatherState.Success) {
                _weatherState.value = WeatherRepository.WeatherState.Loading
            }
            val loc = LocationHelper.getLocation(context)
            val refreshed = if (loc != null)
                WeatherRepository.fetchWeather(loc.lat, loc.lon)
            else
                WeatherRepository.WeatherState.NoLocation
            applyWeatherResult(refreshed, cached)
        }
    }

    /**
     * On [result] Success: persists a real cache snapshot (the previous code
     * wrote the literal string "cached" — a debounce flag with no actual
     * data, useless for offline display). On Offline/Error: falls back to
     * [cached] (already marked stale by [weatherStateFromCacheJson]) so a
     * transient outage shows last-known weather instead of a blank failure
     * card — but only when we truly have something to fall back to; with no
     * cache the honest Offline/Error state passes through unchanged.
     * NoLocation/NoPermission are not network failures and pass through as-is —
     * showing stale weather there would misrepresent why nothing loaded.
     */
    private suspend fun applyWeatherResult(
        result: WeatherRepository.WeatherState,
        cached: WeatherRepository.WeatherState.Success?,
    ) {
        _weatherState.value = when (result) {
            is WeatherRepository.WeatherState.Success -> {
                settings.setWeatherCache(result.toCacheJson())
                result
            }
            is WeatherRepository.WeatherState.Offline,
            is WeatherRepository.WeatherState.Error -> cached ?: result
            else -> result
        }
    }

    // ── Greeting (de-duplicated) ──────────────────────────────────────────────

    val greeting: String by lazy {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 5  -> "Good night"
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            hour < 21 -> "Good evening"
            else      -> "Good night"
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            repo.loadApps()
        }
    }

    fun parsePackageCsv(csv: String): Set<String> =
        csv.split(",").map(String::trim).filter(String::isNotEmpty).toSet()
}
