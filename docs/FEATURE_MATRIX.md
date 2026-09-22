# Feature matrix

The answer to "is this shipped, or just compiled?"

Ciyato accumulated screens that were complete, working, and reachable from nothing: a
data-usage report, a bulk file deleter, a daily agenda, a breathing exercise, and a
custom-greeting editor wired to state that could not hold a value. Each compiled. Each
looked like a feature in the source tree. None could be opened (F-170).

That is worse than dead weight, because the obvious next task for anyone reading an unwired
screen is to wire it — which is how a feature that was deliberately dropped comes back, and
how Settings became a catalogue.

**The rule: a screen is reachable from a route, or it does not exist.** `FeatureReachabilityTest`
fails the build on any third state, and on any route missing from this file.

## Maturity

| | Meaning |
|---|---|
| **Core** | Named in the store listing. Justifies a restricted permission. Must work. |
| **Supported** | Shipped, reachable, maintained. Not a reason to ask for anything sensitive. |
| **Lab** | Reachable but narrow or provisional. Never in the store listing (`PLAY_POSITIONING.md` §1). |

`PLAY_POSITIONING.md` decides which features may justify a restricted permission. This file
records what exists. They must agree.

---

## Core — the launcher

| Feature | Route / destination | Permissions | Tests | Source |
|---|---|---|---|---|
| Home canvas | `LauncherDest.Home` (HOME activity) | `QUERY_ALL_PACKAGES` | `WorkspaceStoreTest`, `CategoryMutationsTest`, `WorkspacePagingTest`, `TimeAwareLayoutTest` | `ui/screens/HomeScreen.kt` + `HomeCanvas/Backgrounds/Dialogs/Controls/Chrome/Types.kt` |
| App drawer | `LauncherDest.Drawer` | `QUERY_ALL_PACKAGES` | `AppCategorizerTest` | `ui/screens/AppDrawerScreen.kt` |
| Search | `search`, `LauncherDest.Search` | `QUERY_ALL_PACKAGES` | `SearchRankingEngineTest` | `ui/screens/SearchScreen.kt` |
| Overview / dashboard | `overview` | `QUERY_ALL_PACKAGES` | — | `ui/screens/DashboardScreen.kt` |
| Category detail | `LauncherDest.CategoryDetail` | `QUERY_ALL_PACKAGES` | `CategoryMutationsTest` | `ui/screens/CategoryDetailScreen.kt` |
| Widgets on Home | `widget_host`, `LauncherDest.WidgetHost` | — | `WidgetPlacementStoreTest` | `ui/screens/WidgetHostScreen.kt`, `data/WidgetPlacementStore.kt`, `data/LauncherWidgetHost.kt`, `ui/components/HostedWidget.kt` |
| App visibility / hidden apps | `hidden_apps`, `LauncherDest.HiddenApps` | biometric | — | `ui/screens/AppVisibilityScreen.kt` |
| Removed apps | `removed_apps`, `LauncherDest.RemovedApps` | — | — | `ui/screens/AppVisibilityScreen.kt` |
| Theme studio | `theme`, `LauncherDest.ThemeStudio` | — | — | `ui/screens/ThemeStudioScreen.kt` |
| Wallpaper | `wallpaper`, `LauncherDest.WallpaperStudio` | `SET_WALLPAPER` | — | `ui/screens/WallpaperPickerScreen.kt` |
| Onboarding | `onboarding` | — | — | `ui/screens/OnboardingScreen.kt` |

## Core — the organizer

| Feature | Route / destination | Permissions | Tests | Source |
|---|---|---|---|---|
| Files | `files` | `MANAGE_EXTERNAL_STORAGE` (SAF fallback) | `FileAccessTest`, `FileTagStoreTest` | `ui/screens/FilesScreen.kt`, `data/FileAccess.kt` |
| File category browse | `photo_duplicates` group, in-Files | as above | — | `ui/screens/FileCategoryScreen.kt`, `FileCollectionDetailScreen.kt` |
| File search | `search_history` entry + in-Files | as above | `FileSearchIndexStoreTest`, `NlQueryTest` | `ui/screens/NlFileSearchScreen.kt` |
| Recent files | `recent_files`, `LauncherDest.RecentFiles` | as above | — | `ui/screens/RecentFilesScreen.kt` |
| Storage cleanup | `storage_cleanup`, `LauncherDest.StorageCleanup` | as above | `ByteFormatTest` | `ui/screens/StorageCleanupScreen.kt` |
| Photos library | `photos` | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` | `MediaAccessTest`, `PhotoAiCollectionStoreTest` | `ui/screens/PhotosLibraryScreen.kt`, `PhotosScreen.kt` |
| Duplicate photo cleanup | `photo_duplicates` | as above | `DuplicatePhotoDetectorTest` | `ui/screens/DuplicatePhotoCleanupScreen.kt` |
| Photos to PDF | `photos_to_pdf`, `LauncherDest.PhotosToPdf` | as above | `PdfPageLayoutTest` | `ui/screens/PhotosToPdfScreen.kt` |
| Photo backup | `auto_backup`, `LauncherDest.AutoBackup` | as above | `BackupConstraintsTest` | `ui/screens/AutoBackupScreen.kt`, `data/PhotoBackupWorker.kt` |
| Settings | `settings`, `LauncherDest.Settings` | — | `SettingsNavigationTest` | `ui/screens/SettingsScreen.kt`, `SettingsDestinations.kt` |

## Supported

| Feature | Route / destination | Permissions | Tests | Source |
|---|---|---|---|---|
| Weather | `weather_detail`, `LauncherDest.WeatherDetail` | `ACCESS_COARSE_LOCATION`, `INTERNET` | `ForecastClockTest`, `LocationFreshnessTest`, `WeatherRepositoryTest` | `ui/screens/WeatherDetailScreen.kt`, `data/WeatherRepository.kt` |
| Agenda | `agenda`, `LauncherDest.Agenda` | `READ_CALENDAR` | `AgendaEventStyleTest` | `ui/screens/CalendarAgendaScreen.kt` |
| Focus session | `focus`, `LauncherDest.FocusSession` | — | `FocusSessionManagerTest` | `ui/screens/FocusSessionScreen.kt`, `data/FocusSessionManager.kt` |
| App lock | `locked_apps`, `LauncherDest.LockedApps` | biometric | — | `ui/screens/LockedAppsScreen.kt`, `AppLockScreen.kt` |
| Secure vault | `secure_vault`, `LauncherDest.SecureVault` | biometric | `VaultCryptoTest` | `ui/screens/SecureFileVaultScreen.kt`, `data/VaultCrypto.kt` |
| Permission review | `permission_audit`, `LauncherDest.PermissionAudit` | `QUERY_ALL_PACKAGES` | — | `ui/screens/PermissionAuditScreen.kt` |
| Insights (hub) | `insights`, `LauncherDest.Insights` | `PACKAGE_USAGE_STATS` | `UsageAveragesTest` | `ui/screens/InsightsScreen.kt` |
| — Screen time | `app_usage`, `LauncherDest.AppUsage` | `PACKAGE_USAGE_STATS` | — | `ui/screens/AppUsageStatsScreen.kt` |
| — Today's summary | `ai_changelog`, `LauncherDest.AiChangelog` | `PACKAGE_USAGE_STATS` | — | `ui/screens/AiChangelogScreen.kt` |
| — Frequent apps | `contextual_suggestions`, `LauncherDest.ContextualSuggestions` | `PACKAGE_USAGE_STATS` | — | `ui/screens/ContextualSuggestionsScreen.kt` |
| — Unusual usage | `anomaly_detection`, `LauncherDest.AnomalyDetection` | `PACKAGE_USAGE_STATS` | `UsageAnomaliesTest` | `ui/screens/AnomalyDetectionScreen.kt` |
| — Data usage | `network_usage`, `LauncherDest.NetworkUsage` | `PACKAGE_USAGE_STATS` | — | `ui/screens/NetworkUsageScreen.kt` |
| Sticky notes | `sticky_notes`, `LauncherDest.StickyNotes` | — | `StickyNoteStoreTest` | `ui/screens/StickyNotesScreen.kt` |
| Search history | `search_history`, `LauncherDest.SearchHistory` | — | `FileSearchHistoryStoreTest` | `ui/screens/SearchHistoryScreen.kt` |
| Duplicate shortcuts | `duplicate_shortcuts`, `LauncherDest.DuplicateShortcuts` | `QUERY_ALL_PACKAGES` | — | `ui/screens/DuplicateShortcutsScreen.kt` |

## Lab — reachable, never in the store listing

| Feature | Route / destination | Permissions | Why it is Lab |
|---|---|---|---|
| Voice commands | `voice_commands`, `LauncherDest.VoiceCommands` | `RECORD_AUDIO` | Recognition belongs to the device's speech service, which Ciyato cannot see into. Every action has a tap equivalent |
| Breach checker | `breach_checker`, `LauncherDest.DataBreachChecker` | `INTERNET` | Sends a k-anonymous hash prefix to a third party. Useful, but not what a launcher is for |
| Safe browsing helper | `safe_browsing`, `LauncherDest.SafeBrowsing` | — | A link-inspection helper, not a security boundary |

---

## Removed rather than routed

Recorded here because "it exists in git history" is not an argument for bringing it back.
Full reasoning in `CLAUDE_REMOVAL_SALVAGE_LEDGER.md`.

| Removed | Why |
|---|---|
| `CustomGreetingScreen` | Could not work. Its only state was a file-level `MutableStateFlow`: not persisted, not collected, shared across every ViewModel instance |
| `AiDailyAgendaScreen` | A second "today's summary", duplicating the one that is routed |
| `BulkDeleteFilesScreen` | Photos already has multi-select with system consent and undo |
| `StressFreeModeScreen` | Reduced to a breathing exercise once its invented stress verdict was removed (F-131). A breathing exercise is not a launcher feature |

---

## Adding a feature

1. Decide the maturity **before** writing the screen. If it is Lab, it does not go in the
   store listing or justify a permission.
2. Give it a route in both hosts — `SettingsDestinations` makes the compiler insist.
3. Add a row here. `FeatureReachabilityTest` fails without one.
4. If it needs a sensitive permission, add a `PermissionCapability` row and a
   `DATA_INVENTORY.md` entry. `PermissionRegistryTest` fails without them.
