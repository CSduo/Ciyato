# Ciyato — 150 Feature & Improvement Suggestions

Organized by complexity: **Quick Wins → Medium → Advanced → Moonshot**.
All suggestions maintain Ciyato's dark premium design system.

---

## 🟢 QUICK WINS (1–2 days each)

### UX & Visual Polish
1. **Haptic feedback** — add `HapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)` on every card tap and icon launch.
2. **App icon badges** — draw notification count overlay on dock icons using AccessibilityService data.
3. **Long-press context menu** — on any app icon: Open, App Info, Pin to Dock, Hide, Add to Category.
4. **Live clock widget** — replace static date text with a `LaunchedEffect`-driven clock that ticks every second.
5. **Silver gradient text** - apply a horizontal `Brush.linearGradient` to screen titles for a premium feel.
6. **Smooth spring animations** — replace instant navigation transitions with `spring(dampingRatio=0.7f)` motion.
7. **Icon shape selector** — squircle, circle, rounded square, or raw (system) shape applied to all `RealAppIcon`.
8. **Category color coding** — each `AppCategory` gets a unique accent color used on card borders and headers.
9. **Swipe down to search** — `AnchoredDraggable` from home screen triggers search bar focus.
10. **Pinch-to-zoom categories** — pinch on the home grid to switch between Dense (3-col) and Spacious (2-col).
11. **Double-tap home** — opens the most recently used app.
12. **Empty state illustrations** — custom SVG illustrations for no-apps, no-files, no-internet states.
13. **Glowing silver dot** - animated pulse behind the "C*" logo badge (scale + alpha loop).
14. **Bottom sheet for settings** — replace full SettingsScreen navigation with a `ModalBottomSheet`.
15. **Swipe-to-dismiss notifications** — show notification heads-up cards on the home screen with swipe dismiss.

### Performance
16. **`Modifier.graphicsLayer`** — pre-compose category cards off the main thread using `drawWithContent`.
17. **Icon bitmap LRU cache** — cache `Drawable.toBitmap()` results by packageName to avoid repeated allocation.
18. **Lazy loading staggered grid** — `LazyVerticalStaggeredGrid` in AppDrawer so items load as user scrolls.
19. **`remember(key)` everywhere** — audit all missing `remember` keys that cause unnecessary recomposition.
20. **Background app refresh** — `WorkManager` task refreshes the installed-app list every 30 min.

### Data & Settings
21. **Unit toggle °C / °F** — single `DataStore` bool; Weather card and detail screen respect it.
22. **Default home screen** — Settings item that takes user directly to Android's default-app settings page.
23. **App hide list** — store a `Set<String>` package names that are filtered from all grids.
24. **Category rename** — allow the user to rename any `AppCategory` with a custom label (stored in DataStore).
25. **Recently launched** — track the last 5 launched apps and surface them in "Suggested" section.

---

## 🟡 MEDIUM FEATURES (3–7 days each)

### Weather (extends Open-Meteo integration)
26. **Hourly forecast strip** — add `hourly=temperature_2m,weather_code` param; show a horizontal scroll of 12 hours.
27. **7-day forecast card** — `daily=temperature_2m_max,temperature_2m_min,weather_code` for a full week view.
28. **Weather-based home theme** — change `CiyatoBgEl` gradient hue slightly based on current condition (rainy = bluer).
29. **AQI card** — integrate Open-Meteo's `air_quality` endpoint; show PM2.5, AQI index with a color bar.
30. **Sunrise / sunset times** — show from Open-Meteo `daily=sunrise,sunset`; draw a sun arc progress indicator.
31. **UV index display** — `daily=uv_index_max`; colour-coded warning pill on the weather detail card.
32. **Wind direction compass** — animate a compass needle using `daily=wind_direction_10m_dominant`.
33. **Rain probability bar** — `hourly=precipitation_probability`; horizontal bar chart for next 6 hours.
34. **Weather notification** — background periodic work that pushes a notification if rain is expected within 2 h.
35. **Home screen animated icons** — swap the static `WbSunny` icon for a Lottie animation matching weather code.

### Search & AI
36. **Recent searches history** — `DataStore` list; show below search bar with swipe-to-delete per item.
37. **App usage frequency sort** — track launches per package; sort search results by frequency descending.
38. **Fuzzy search** — Levenshtein distance fallback when exact substring match returns 0 results.
39. **Voice search** — `SpeechRecognizer` integration; mic button in search bar triggers listening.
40. **NLP category detection** — parse natural-language queries like "open a music app" → filters ENTERTAINMENT.
41. **Cross-app search** — `ContentResolver` search across Contacts, Bookmarks, installed packages simultaneously.
42. **Search result grouping** — group results into Apps / Files / Contacts / Bookmarks with section headers.
43. **Shortcut results in search** — return `ShortcutManager.getShortcuts()` results as searchable items.
44. **App not installed suggestion** — if no app matches a category query, show "Find on Play Store" CTA.
45. **AI-powered suggestion chips** — rotate suggestion chips daily using time-of-day + app usage heuristics.

### Calendar & Agenda
46. **Real calendar integration** — `READ_CALENDAR` permission + `CalendarContract.Events` query.
47. **Event countdown pills** — show "in 23 min" countdown on the AgendaCard for the next event.
48. **Calendar account selector** — let user pick which Google / Exchange account to show events from.
49. **Event color chips** — read `CalendarContract.Events.CALENDAR_COLOR` and apply to the accent left-bar.
50. **Add event shortcut** — "Add" button opens `Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)`.
51. **Recurring event badge** — show a loop icon on events where `CalendarContract.Events.RRULE != null`.
52. **All-day event section** — separate all-day events above the timed event list.
53. **Calendar widget mini month** — collapsible mini month calendar at the top of AgendaScreen.
54. **Reminder notification** — foreground service that pings 10 min before next calendar event.
55. **iCal export** — generate an `.ics` file for any manually added Ciyato event.

### Files & Photos
56. **File type grouping** — within FileCollectionDetailScreen, group by mime type (PDF, DOC, TXT, etc.).
57. **File size badge** — show human-readable file size under each file row (`1.2 MB`, `400 KB`).
58. **Grid / list toggle** — icon button in FileCollectionDetailScreen to switch between list and 2-col grid.
59. **File search within folder** — `BasicTextField` in FileCollectionDetailScreen filters displayed files live.
60. **Share sheet** — long-press any file → show Android share sheet via `Intent.ACTION_SEND`.
61. **Duplicate file detector** — compare file names and sizes within a folder; surface duplicates with a warning card.
62. **Storage breakdown donut chart** — `Canvas` donut showing % used by category (Images, Video, Docs, APKs, Other).
63. **Photo timeline view** — `LazyVerticalGrid` with sticky date headers using `stickyHeader { }`.
64. **Thumbnail generation** — `ThumbnailUtils.createImageThumbnail()` for photo tiles in grid.
65. **Recycle bin** — soft-delete files to a Ciyato-managed trash folder; empty trash after 30 days.
66. **Quick zip** — multi-select files in a folder and create a `.zip` via `java.util.zip.ZipOutputStream`.
67. **Cloud folder integration** — list Google Drive / Dropbox folders via their REST APIs.
68. **SAF persisted locations list** — show previously granted folder URIs as bookmarks in FilesScreen.
69. **File metadata sheet** — `ModalBottomSheet` showing EXIF data for photos, word count for text files.
70. **Secure vault** — AES-256 encrypt selected files using Android Keystore; unlock with biometric.

---

## 🔴 ADVANCED FEATURES (1–2 weeks each)

### Smart Launcher Intelligence
71. **App usage analytics** — use `UsageStatsManager` (requires `PACKAGE_USAGE_STATS`) to rank apps by daily use.
72. **Time-aware home screen** — show Work category prominently Mon–Fri 9–6, Entertainment evenings/weekends.
73. **Location-aware categories** — use geofence to auto-expand "Travel" category when at airport.
74. **Bedtime mode** — after 11 PM, dim the screen, hide social media categories, surface Sleep/Health apps.
75. **Focus sessions** — block distracting app categories for N minutes; integrates with Digital Wellbeing.
76. **Contextual widget injection** — show a Spotify "Now Playing" card on home when music is detected playing.
77. **Smart dock rotation** — dock apps rotate based on time of day and recently launched frequency.
78. **Category learning** — offer "was this categorized correctly?" after launching an app; apply corrections.
79. **App prediction engine** — simple Markov chain or frequency + time model to predict next app to open.
80. **Ciyato AI assistant** — on-device LLM (Gemini Nano via MediaPipe) for natural-language launcher queries.

### System Integration
81. **Notification listener service** — `NotificationListenerService` to read and display notification counts on icons.
82. **Quick settings tiles** — expose Ciyato Focus Mode and Weather refresh as custom Android Quick Settings tiles.
83. **Tasker plugin** — expose `BroadcastReceiver` intents that Tasker can trigger (e.g., switch category layout).
84. **Lock screen widget** — Android 14+ lock-screen widget showing next calendar event and weather.
85. **Android App Shortcuts** — add `<shortcuts>` to Manifest so long-pressing Ciyato icon shows Jump Start options.
86. **Digital Wellbeing integration** — show daily screen time per category in SettingsScreen.
87. **Accessibility service** — optional AS that reads notification counts and provides "last opened app" data.
88. **Multiple user profiles** — support Android Work Profile app separation in the app drawer.
89. **Foldable / tablet layout** — two-pane layout on foldables: left = categories, right = app grid.
90. **External display support** — `Presentation` API for showing a minimal launcher view on secondary screens.

### Personalization & Theme
91. **Theme Studio — custom palette** — user picks any hex color; app generates a full `CiyatoTheme` from it.
92. **Dynamic color (Material You)** — opt-in `dynamicColorScheme()` that blends with the wallpaper palette.
93. **Custom wallpaper blur** — `RenderEffect.createBlurEffect` behind cards; intensity slider in Theme Studio.
94. **Widget pack system** — load third-party Ciyato widget packs as APKs via `DexClassLoader`.
95. **Icon pack support** — read icon packs via their `ContentProvider` API and apply to all `RealAppIcon`.
96. **Custom category icons** — user picks any installed app's icon to represent a category card.
97. **Dark / light / amoled modes** — three explicit themes; AMOLED uses pure `Color(0xFF000000)` background.
98. **Font selector** — choose from 5 bundled fonts (Inter, Outfit, DM Sans, Syne, Geist) stored in res/font/.
99. **Glass morphism presets** — presets for blur radius / opacity / border intensity applied globally.
100. **Seasonal themes** — automatically switch accent colors for holidays while preserving the monochrome premium base.

---

## ⚡ ARCHITECTURE & CODE QUALITY

101. **Repository pattern extract** — move weather, calendar, files, photos into separate repository classes.
102. **Hilt DI** — replace manual constructor injection with `@HiltViewModel` and `@Inject` throughout.
103. **Sealed UI state pattern** — `UiState<T>` wrapper for every screen (Loading / Success / Error / Empty).
104. **StateFlow → SharedFlow events** — use `SharedFlow` for one-shot events (navigation, toasts) not `StateFlow`.
105. **Paging 3** — load app list in pages of 50 for devices with 400+ installed apps.
106. **Room database** — persist app categories, usage stats, weather cache, and agenda items in Room.
107. **Proto DataStore** — migrate `Preferences DataStore` to `Proto DataStore` for type-safe settings.
108. **Unit tests for categorizer** — parameterized tests for all `AppCategorizer` regex rules.
109. **Screenshot tests** — Paparazzi screenshot tests for all composable screens at multiple densities.
110. **Baseline profiles** — `ProfileInstaller` + Macrobenchmark to eliminate cold-start jank.
111. **Kotlin Symbol Processing** — replace reflection in any dynamic code with KSP compile-time generation.
112. **Coroutine dispatcher injection** — inject `CoroutineDispatchers` interface so tests can substitute `TestDispatcher`.
113. **BuildConfig flags** — `IS_DEBUG_WEATHER`, `IS_DEBUG_LOCATION` flags to stub responses in debug builds.
114. **R8 / ProGuard rules** — add rules to preserve `AppCategory.valueOf()` after shrinking.
115. **Compose stability** — annotate all data classes with `@Stable` / `@Immutable` to eliminate redundant recompositions.

---

## 🌐 CONNECTIVITY & CLOUD

116. **Weather location caching** — cache the last successful Open-Meteo response in Room for 30 min; show it offline.
117. **Offline indicator** — `ConnectivityManager.NetworkCallback` drives a persistent "Offline" chip on weather card.
118. **Automatic retry with backoff** — failed weather / geocoding requests retry with exponential backoff (1s, 2s, 4s).
119. **Ciyato account sync** — optional Firebase account; syncs category customisations and settings across devices.
120. **WebDAV backup** — export all Ciyato settings and category customizations as a `.ciyato` JSON backup to WebDAV.
121. **Encrypted backup** — GPG-encrypt the Ciyato backup file before exporting or uploading.
122. **P2P sync** — NFC or QR-code handshake to transfer settings between two Ciyato installs on the same LAN.
123. **App recommendation feed** — opt-in feed that recommends trending productivity apps from a curated list.
124. **Play Store deep link** — tapping "Find on Play Store" opens the Store search for the exact app name.
125. **OTA update checker** — poll a public GitHub Releases URL and notify if a newer APK version exists.

---

## 🎨 MOTION & ANIMATION

126. **Shared element transitions** — `SharedTransitionLayout` when navigating from category card to detail screen.
127. **Particle burst on app launch** — 8 silver particles explode outward from the icon tap point using `Canvas`.
128. **Elastic dock spring** — dock icons spring-compress and rebound when pressed, using `Animatable`.
129. **Morphing category icons** — icons smoothly cross-fade (alpha + scale) when category filter changes.
130. **Page-turn drawer** — `HorizontalPager` with a 3D perspective `graphicsLayer` rotation for drawer pages.
131. **Floating action glow** — silver `BlurMaskFilter` pulsing shadow behind the Ciyato AI button.
132. **Skeleton loading** — shimmer placeholder for category cards while apps are loading.
133. **Confetti burst** — `Canvas`-drawn confetti on completing the onboarding flow.
134. **Ripple customisation** — replace Android's default grey ripple with a silver-tinted custom `Indication`.
135. **Scroll parallax** — home screen greeting slides upward at 0.5× scroll speed for a depth effect.

---

## 🔒 SECURITY & PRIVACY

136. **Biometric app lock** — `BiometricPrompt` gating the entire launcher or specific categories.
137. **Hidden apps with PIN** — apps in the HIDDEN category require a PIN or biometric to reveal.
138. **Privacy mode** — one tap hides all notification counts and app labels (useful when screen sharing).
139. **Permission audit screen** — list all permissions requested by each installed app; flag high-risk ones.
140. **Safe mode indicator** — detect and show a banner if a potentially harmful sideloaded app is found.
141. **App lock timer** — automatically re-lock an app after N minutes of inactivity using `UsageStatsManager`.
142. **Network call log** — debug-only screen listing every HTTP call made by Ciyato (URL, status, latency).
143. **Certificate pinning** — pin Open-Meteo and Nominatim TLS certs using `OkHttp CertificatePinner`.
144. **Crash reporter** — opt-in local crash log stored in app-private files; user can share it via email.
145. **Anti-screenshot flag** — `WindowManager.LayoutParams.FLAG_SECURE` toggle in Privacy settings.

---

## 🚀 MOONSHOT IDEAS

146. **On-device ML categorizer** — replace regex rules with a TFLite model trained on package names + labels.
147. **Ciyato OS skin** — AOSP system overlay that applies Ciyato's visual system to status bar and quick settings.
148. **Launcher-as-a-Service SDK** — `aar` library so developers can embed a mini Ciyato launcher inside their app.
149. **AR home screen** — ARCore anchors app icons in 3D space around the user; open with camera permission.
150. **Generative wallpaper engine** — Stable Diffusion on-device (via MediaPipe) generates a new wallpaper every morning matching today's weather and calendar mood.

---

## Implementation Priority Matrix

| Priority | Items | Time | Impact |
|----------|-------|------|--------|
| 🔥 Ship now  | 1–5, 16–17, 21–22, 26–28 | 1–3 days | High visual quality & live data |
| 📈 Next sprint | 36–38, 46–48, 56–58 | 1 week | Core features complete |
| 🛠 Polish sprint | 101–115 | 2 weeks | Production-ready code quality |
| 🌟 Future | 71–90, 126–135 | 1 month | Premium differentiator |
| 🚀 v2.0 | 146–150 | 3+ months | Category-defining |

---
*Ciyato — Organize Smarter. Live Better.*
