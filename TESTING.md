# Ciyato Testing Guide

## Automated verification

From `ciyato-android/`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --console=plain testDebugUnitTest assembleDebug
```

Expected result:

- `BUILD SUCCESSFUL`
- 9 `AppCategorizerTest` tests pass
- APK at `app/build/outputs/apk/debug/app-debug.apk`
- Convenience APK copy at `Ciyato.apk`

The build has also been verified with `apksigner` using the Android debug certificate. A release build requires a production signing configuration.

## Install

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

If no device is connected, copy the APK to a test phone and install it from the file manager.

## Manual acceptance checklist

### First launch and Home role

- [ ] Open Ciyato from the app list.
- [ ] Onboarding explains launcher behavior, local data, Files, Photos, Weather permission timing, and switch-back.
- [ ] No location, files, photos, calendar, microphone, or notification permission appears automatically.
- [ ] Tap **Set Ciyato as Home** and choose Ciyato.
- [ ] Press Home and confirm `LauncherHomeActivity` opens.
- [ ] Back does not trap the user or expose an invalid screen.

### Installed apps

- [ ] Real installed apps load.
- [ ] Real app icons render.
- [ ] Tap an app in Home, category detail, App Library, Search, duplicate shortcuts, and dock.
- [ ] Confirm the correct explicit launcher activity opens.
- [ ] Return Home and confirm Ciyato remains stable.

### Categories and App Library

- [ ] Tap every Home category card.
- [ ] Tap app preview icons directly.
- [ ] Expand and collapse App Library sections.
- [ ] Tap the drawer sort/filter button and confirm it cycles A-Z, most-used, and recently-installed sorting.
- [ ] Tap **View all** in a large drawer section and confirm the full section expands.
- [ ] Search inside a category.
- [ ] Use Manage to rename a category.
- [ ] Reopen Ciyato and confirm the rename persists.
- [ ] Long-press an app and change its category.
- [ ] Reset the category override.

### Hidden and Removed

- [ ] Long-press an app and choose **Hide App**.
- [ ] Confirm it disappears from Home, App Library, categories, and normal search.
- [ ] Search within Hidden Apps and use **Restore all**.
- [ ] Open Settings → Hidden Apps and restore it.
- [ ] Long-press an app and choose **Remove from display**.
- [ ] Confirm it disappears but remains installed.
- [ ] Search within Removed Apps and use **Restore all**.
- [ ] Open Settings → Removed Apps and restore it.
- [ ] Confirm counts update after each action.

### Dock

- [ ] Long-press an app and pin it to the dock.
- [ ] Confirm it appears in the dock.
- [ ] Recreate the activity and confirm the dock choice persists.
- [ ] Long-press and unpin it.

### Duplicate Smart Shortcuts

- [ ] The duplicate strip appears when multi-category apps exist.
- [ ] Tap a shortcut and confirm the real app opens.
- [ ] Tap the strip/manage action and open Duplicate Shortcuts.
- [ ] Confirm the UI explains that no APK is cloned.

### Search

- [ ] Open Search from the Home search bar and sparkle action.
- [ ] Search by app label.
- [ ] Search by package name.
- [ ] Search by category phrase.
- [ ] Launch an app result.
- [ ] Test **Work apps**, **Social apps**, **Games**, and **Finance**.
- [ ] Confirm file/photo semantic search is not presented as active.
- [ ] Confirm recent search history stores complete actions, not every typed prefix.

### Weather

- [ ] Open Weather with location denied.
- [ ] Confirm Ciyato explains the request before Android asks.
- [ ] Tap Not now and confirm the app remains usable.
- [ ] Tap Enable and deny the Android dialog.
- [ ] Retry and grant coarse location.
- [ ] Confirm loading, success, no-location, offline, and refresh states do not crash.
- [ ] Confirm no background-location request occurs.

### Files

- [ ] Open Files with no selected folder.
- [ ] Confirm Ciyato asks the user to choose a folder before claiming file access.
- [ ] Select a folder and confirm folders and files are listed.
- [ ] Tap a folder and confirm it navigates inside the folder.
- [ ] Use Back and confirm nested folder navigation steps back before closing Files.
- [ ] Open real files with the system viewer.
- [ ] Confirm empty folders show a clear empty state.
- [ ] Confirm cleanup, junk, vault, and broad-storage actions are not presented as active.
- [ ] Confirm no file is automatically moved or deleted.
- [ ] Reopen Files and confirm the selected folder is remembered.
- [ ] Use Forget Folder in Files or Settings and confirm Ciyato returns to the choose-folder state.

### Photos

- [ ] Open Photos with no selected media.
- [ ] Confirm no full-gallery permission is requested.
- [ ] Tap Select Photos and cancel Android Photo Picker.
- [ ] Select multiple photos and confirm real thumbnails appear.
- [ ] Add more photos.
- [ ] Confirm Collections states that automatic grouping is staged.
- [ ] Confirm no upload or cloud-analysis action exists.

### Theme Studio and guidance

- [ ] Switch dense/spacious layout and confirm Home columns change.
- [ ] Switch silver/silver-blue accent and confirm Home action accent and live preview change.
- [ ] Switch drawer style between smart, dense, and spacious and confirm App Library layout changes.
- [ ] Change appearance, icon shape, font, and background blur controls and confirm they persist.
- [ ] Recreate the activity and confirm settings persist.
- [ ] Dismiss the Home tip and confirm it stays dismissed.
- [ ] Reset Tips & Onboarding and confirm guidance returns.
- [ ] Reset shipped theme settings.

### Switch back and uninstall

- [ ] Open Settings and confirm default-Home status is shown.
- [ ] Tap **Switch back to system launcher**.
- [ ] Choose the previous launcher and press Home.
- [ ] Open Ciyato App Info.
- [ ] Confirm Android uninstall guidance is available.

## Denied and empty states

Test every user-controlled permission flow with:

- cancel before selection,
- deny once,
- deny repeatedly,
- grant and then revoke in Android settings,
- selected folder with no matching files,
- no installed apps in a category,
- no hidden or removed apps,
- no network for weather.

## Current environment limitation

Per client instruction, this pass does not use an emulator or simulated phone runtime. Full interaction and screenshot acceptance still require a physical Android device. Build-only verification is acceptable for this handoff; real-device UX is not claimed complete until installed on a phone.
