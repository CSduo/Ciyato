# Ciyato Security and Privacy

Ciyato is local-first. The beta does not include analytics, advertising SDKs, cloud backup, account login, photo upload, file upload, or remote app-list classification.

## APK permissions

The current debug APK declares:

| Permission | Purpose | Request timing |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Discover launchable apps for the Home launcher | Install-time capability; no dialog |
| `INTERNET` | Fetch weather from Open-Meteo | Only used by weather |
| `ACCESS_NETWORK_STATE` | Show weather/network failure states | No runtime dialog |
| `ACCESS_COARSE_LOCATION` | Approximate foreground location for local weather | Only after the user opens Weather and taps Enable |
| `VIBRATE` | Optional tap feedback | No runtime dialog |

Android also generates an app-private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. It is not a user data permission and is not exported to other apps.

The beta does not declare broad storage access, `MANAGE_EXTERNAL_STORAGE`, full-gallery media access, background location, calendar, microphone, notification-listener, usage-stat, exact-alarm, boot, foreground-service, or biometric permissions.

## Files

- Ciyato uses Android Storage Access Framework.
- The user selects a folder in Android's system picker.
- Persistable read access is kept only for the chosen folder.
- The selected folder URI is remembered locally in DataStore and can be forgotten from Files or Settings.
- The selected folder can be revoked in Android app/storage settings.
- Ciyato lists and opens files locally.
- Ciyato supports nested browsing inside the selected folder.
- Cleanup and deletion are disabled; there is no automatic destructive action.
- Smart Collections use local filename, MIME type, and date heuristics.

## Photos

- Ciyato uses Android Photo Picker.
- Full-gallery permission is not requested.
- Only selected media is displayed.
- Selected photos are not uploaded.
- Cloud AI analysis is not present.
- Automatic moment grouping is staged and is labelled as staged.

## Installed apps

- Labels, package names, activities, icons, install times, and category assignments stay on-device.
- Hidden and Removed states affect only Ciyato's display.
- Hide/Remove never uninstalls an app.
- Uninstall is a separate, explicit system-confirmed action.
- Category overrides and dock choices are stored locally in DataStore.

## Weather

- The user sees an explanation before the Android permission dialog.
- Only coarse foreground location is requested.
- Background location is not declared or used.
- Weather data is fetched from Open-Meteo.
- Denial leaves a graceful enable/limited state.

## Local storage

Preferences are stored with Android DataStore. Crash logs, when enabled, are written locally and are never uploaded automatically. `android:allowBackup` is disabled in the current beta.

Uninstalling Ciyato removes app-private preferences and logs. Android may separately retain or revoke document-picker grants according to platform behavior.

## User control and exit safety

- Ciyato Settings links to Android Home app settings.
- Ciyato Settings links to Android App Info and uninstall controls.
- The user can restore Hidden and Removed apps.
- The user can reset layout and first-run guidance.
- Ciyato does not attempt to block switching launchers or uninstalling.

## Roadmap boundaries

Optional backup, accounts, device migration, encrypted private areas, and semantic AI grouping are roadmap items. They are not active in this beta. Any future cloud feature must be opt-in, explain its data destination, provide deletion controls, and avoid uploading sensitive content by default.
