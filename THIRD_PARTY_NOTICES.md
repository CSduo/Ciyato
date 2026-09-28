# Third-party notices

Ciyato is proprietary (see [`LICENSE`](LICENSE)) and incorporates open-source
components, each under its own licence.

**This is a shipping requirement, not a courtesy.** Apache-2.0 section 4(d)
requires that attribution notices be carried in derivative works, and almost
everything below is Apache-2.0. A commercial Android app that omits them is
distributing those libraries outside their licence terms. Google Play does not
check for it; that does not make it optional.

---

## What has to be in the app

An **Open-source licences** entry in Settings, showing the notices below.

- [x] Settings → Open-source licences, listing every component below with its
      licence text — `ui/screens/OpenSourceLicencesScreen.kt`, reached from the
      About section of Settings
- [ ] **Verified present in a release build, not just a debug one (yours)** — the
      asset is copied by a task wired into asset merging for every variant, and
      `ThirdPartyNoticesTest` asserts the wiring exists, but only installing a
      release build proves the screen renders there

### Why not the oss-licenses plugin

`com.google.android.gms:oss-licenses-plugin` is the usual answer, and it was
recommended here until somebody checked the dependency list. Ciyato has **no Google
Play Services dependency at all** — ML Kit is the bundled on-device variant
(`com.google.mlkit:image-labeling`), which carries no GMS. That plugin would make a
licences screen the reason this app gains its first Play Services dependency, in an
app whose whole positioning is that nothing leaves the device. Wrong trade.

A static hand-written screen is the other usual answer, and it drifts — which is a
real objection, not a stylistic one, because a stale attribution list is the same
licence breach as no list at all.

So neither. The document you are reading **is** the screen:

- The section between the `SHIPPED` markers below is copied into the APK as an asset
  by the `copyThirdPartyNotices` Gradle task, wired ahead of asset merging. There is
  one copy of the text, so there is nothing to drift.
- The task fails the build if the markers are missing or the extracted text is
  empty, so the screen cannot silently ship blank.
- `ThirdPartyNoticesTest` fails the build when a dependency in `build.gradle.kts`
  has no entry here. That closes the gap the plugin's dependency-graph generation
  was wanted for, without the dependency.
- Only the marked section ships. Everything else on this page — these notes, the
  outstanding wallpaper question, the regeneration instructions — is internal and
  must not appear in front of a user.

---

<!-- SHIPPED:BEGIN - everything below this marker is copied into the APK and shown
     to users. Keep it free of internal notes, questions and checkboxes. -->

## Components

### Apache License 2.0

The full text is at https://www.apache.org/licenses/LICENSE-2.0

| Component | Coordinates | What it does here |
|---|---|---|
| AndroidX Core | `androidx.core:core-ktx` | Platform compatibility |
| AndroidX Activity | `androidx.activity:activity-compose` | Activity + Compose integration |
| AndroidX Lifecycle | `androidx.lifecycle:lifecycle-runtime-ktx`, `-runtime-compose`, `-viewmodel-compose` | Lifecycle-aware state |
| Jetpack Compose (version alignment) | `androidx.compose:compose-bom` | Keeps the Compose artifacts below on one consistent version |
| Compose UI | `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling-preview` | The rendering and layout foundation |
| Compose Material 3 | `androidx.compose.material3:material3` | Buttons, cards, dialogs, the app bar |
| Compose Material Icons | `androidx.compose.material:material-icons-extended` | Every icon in the interface |
| AndroidX Navigation | `androidx.navigation:navigation-compose` | The organizer's route graph |
| AndroidX DataStore | `androidx.datastore:datastore-preferences` | Settings and layout persistence |
| AndroidX DocumentFile | `androidx.documentfile:documentfile` | SAF file access |
| AndroidX Room | `androidx.room:room-runtime`, `-ktx`, `-paging`, `-compiler` | The file search index |
| AndroidX WorkManager | `androidx.work:work-runtime-ktx` | Scheduled photo backup, file cleanup |
| AndroidX Biometric | `androidx.biometric:biometric` | Vault, app lock, hidden apps |
| AndroidX Paging | `androidx.paging:paging-runtime-ktx`, `paging-compose` | Large media and file lists |
| AndroidX ProfileInstaller | `androidx.profileinstaller:profileinstaller` | Baseline profile installation |
| Kotlin Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Asynchronous work throughout |
| Kotlin standard library | `org.jetbrains.kotlin:kotlin-stdlib` | Language runtime |
| Coil | `io.coil-kt:coil-compose` | Image loading in Photos and Files |
| ML Kit Image Labeling | `com.google.mlkit:image-labeling` | On-device photo labelling. **Bundled model — the model ships inside the APK and is not downloaded** (see `DATA_INVENTORY.md` §1) |

<!-- SHIPPED:END - internal from here down. -->

### Test and build only — not distributed

These do not ship in the release artifact, so they carry no attribution
obligation in the app. Listed for completeness.

| Component | Licence |
|---|---|
| JUnit 4 (`junit:junit`) | Eclipse Public License 1.0 |
| Mockito (`org.mockito:mockito-core`, `mockito-kotlin`) | MIT |
| AndroidX Test (`androidx.test.ext:junit`, `espresso-core`, `uiautomator`) | Apache-2.0 |
| AndroidX Benchmark (`androidx.benchmark:benchmark-macro-junit4`) | Apache-2.0 |
| Compose UI Test (`androidx.compose.ui:ui-test-junit4`, `ui-test-manifest`) | Apache-2.0 |
| `org.json:json` (unit tests) | Public domain / JSON licence |
| Android Gradle Plugin, Kotlin Gradle Plugin, KSP | Apache-2.0 |

### Assets

The bundled wallpapers in `app/src/main/res/drawable-nodpi/` are project assets.
**Their provenance is not recorded anywhere in the repository**, and that is a gap
worth closing before release: if any was sourced from a stock library or generated
by a service with terms attached, those terms need to be either satisfied or the
asset replaced. If they are original or generated by the copyright holder, say so
here and the question is settled.

- [ ] Confirm the origin of each bundled wallpaper and record it above

---

## Keeping this accurate

Regenerate the resolved dependency list rather than editing this table from
memory:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

Anything in that output which is not covered above is an omission. A transitive
dependency arriving through an upgrade is exactly how an attribution list goes
stale, and it does so silently.
