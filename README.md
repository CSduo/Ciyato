# Ciyato Android

Ciyato is a native Kotlin and Jetpack Compose Android launcher plus local phone organizer.

This repository contains older web and Expo prototypes, but the product deliverable is the native project in `ciyato-android/`.

## Current beta

### Working

- Selectable Android Home launcher
- Real installed launcher apps, labels, icons, and explicit activity launching
- Smart categories with expandable category details
- Expandable Smart App Library
- Duplicate Smart Shortcuts using the same installed app in multiple contexts
- Long-press actions to pin to the dock, hide, remove from Ciyato, recategorize, open app info, or request uninstall
- Separate persistent Hidden and Removed app lists with restore actions
- Persistent category renames and per-app primary-category overrides
- Persistent dense/spacious layout, drawer style, silver/white accent choice, dock choices, launcher tip state, and onboarding state
- Full local installed-app search by label, package name, and category
- Search prompt routes for installed-app categories such as Work, Social, Games, and Finance
- Foreground coarse-location weather flow using Open-Meteo
- Storage Access Framework folder selection, nested local folder browsing, local file listing, and system file opening
- Android Photo Picker selected-media display without full-gallery permission
- Professional multi-step onboarding that explains launcher behavior, permissions, privacy, Files, Photos, Weather, and switch-back
- Android Home settings, app info, uninstall guidance, and switch-back path

### Staged or limited

- Agenda uses clearly labelled sample events.
- Photo collections and automatic moment grouping are staged; selected Photo Picker media is real.
- File collections use local filename, MIME type, and date heuristics within the selected folder. Recursive indexing is not implemented.
- Cleanup, junk removal, vault, and destructive file actions are disabled/staged.
- Search is real for installed apps. File/photo semantic indexing across all device data is not implemented.
- Theme Studio currently ships real density, drawer style, accent, appearance, icon shape, font, background blur, preview, and reset controls. Wallpaper packs, system wallpaper changes, and glass presets are staged.
- No remote AI model, cloud backup, account, analytics, or hidden upload is active.

## Build

Requirements:

- Android SDK 34
- JDK 17
- Android Studio or the included Gradle wrapper

Windows PowerShell:

```powershell
cd C:\Users\ADMIN\StudioProjects\-nv-k-ik-\ciyato-android
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS/Linux:

```bash
cd ciyato-android
./gradlew testDebugUnitTest assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A convenience copy is also written to:

```text
ciyato-android/Ciyato.apk
```

## Install

With Android platform tools:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Or copy the APK to a phone, allow installation from the chosen file manager when Android asks, and open the APK.

## Use as Home

1. Open Ciyato.
2. Complete or skip onboarding.
3. Tap **Set Ciyato as Home**.
4. Choose **Ciyato** in Android's Home app chooser.
5. Press the Home button.

## Switch back

1. Open Ciyato **Settings**.
2. Tap **Switch back to system launcher**.
3. Choose the previous Home app in Android settings.

If Ciyato cannot be opened, use Android **Settings → Apps → Default apps → Home app**. App info and uninstall guidance are also available from Ciyato Settings.

## Privacy

Ciyato categorizes apps locally. Files are available only from folders the user selects through Android's folder picker, and the remembered folder can be forgotten from Files or Settings. Photos are available only from Android Photo Picker selections. Weather requests approximate foreground location only after an explanation and explicit user action.

See `SECURITY.md`, `TESTING.md`, `STORE_READINESS.md`, `PROJECT_AUDIT.md`, and `IMPLEMENTATION_PLAN.md`.
