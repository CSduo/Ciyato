# Ciyato — Play Store readiness

**This file is checked against the shipping manifest by a test.**
`StoreReadinessDocTest` parses `AndroidManifest.xml` and fails the build if the
permission table below does not match it exactly. The previous version of this
document claimed the app had "no full-gallery media permission, no broad storage
permission, no fine location, no microphone, no calendar … and no notification
listener" while the manifest declared every one of those (F-183). Policy work
done from that file would have produced a false Play data-safety declaration.

A document describing shipping configuration cannot be maintained by hand, so
this one is no longer trusted to be — it is enforced.

Last verified: 2026-08-23 · `versionCode` 2608231 · `versionName` 1.1.0

## Versioning

`versionCode` is derived, not hand-bumped: `YYMMDDn` from the release date and
the build number within that day (see `ciyatoVersionCode` in `app/build.gradle.kts`).
It is strictly increasing by construction and stays inside Play's ceiling until
2121. A malformed date fails the build, because a version code cannot be
corrected after upload.

## Declared permissions — all 15

| Permission | Why it is declared | Play consideration |
|---|---|---|
| `INTERNET` | Weather, air quality, reverse geocoding, breach check | Normal |
| `ACCESS_NETWORK_STATE` | Skip network work while offline | Normal |
| `ACCESS_COARSE_LOCATION` | Weather and air quality for the current area | Runtime. **Approximate only** — coordinates are rounded to two decimals (~1.1 km) before any request leaves the device |
| `QUERY_ALL_PACKAGES` | A launcher must enumerate installed apps to draw a drawer and search them | **Requires a Permissions Declaration Form.** Core functionality; listing copy must explain installed-app discovery |
| `MANAGE_EXTERNAL_STORAGE` | The file manager browses internal storage; SAF cannot grant the internal root since Android 11 | **Requires an All-files-access declaration.** Highest-scrutiny item here |
| `READ_EXTERNAL_STORAGE` | Same, on API 29 and below | Runtime, legacy path |
| `READ_MEDIA_IMAGES` | Photos library | Runtime |
| `READ_MEDIA_VIDEO` | Photos library includes videos | Runtime |
| `READ_MEDIA_AUDIO` | Audio category in file browsing | Runtime |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Honours the Android 14 "Select photos" partial grant instead of demanding all-or-nothing | Runtime, privacy-positive |
| `READ_CALENDAR` | Agenda widget and the Calendar screen | Runtime |
| `RECORD_AUDIO` | Voice commands, and only while that screen is open | Runtime. Declare in data safety as not collected or transmitted |
| `REQUEST_DELETE_PACKAGES` | "Uninstall" in the app long-press menu | Normal; the OS still confirms |
| `SET_WALLPAPER` | Applying a wallpaper from Wallpaper Studio | Normal |
| `VIBRATE` | Haptics on drag, drop and long-press | Normal |

### Removed, and why

- **`ACCESS_FINE_LOCATION`** — used to prefer the GPS provider, but every
  coordinate is coarsened to ~1.1 km before use. Precise location was collected,
  paid for in battery and in a scarier permission prompt, then discarded. It also
  obliged a "precise location" data-safety entry for a benefit nobody received.
- **`FOREGROUND_SERVICE`** — declared while the app contains no `startForeground`,
  no `ForegroundInfo`, and no foreground service. A permission the app cannot
  exercise is a review liability and nothing else.

## Declared services — all 3

| Service | Bound by | Notes |
|---|---|---|
| `CiyatoNotificationListenerService` | `BIND_NOTIFICATION_LISTENER_SERVICE` | Powers app-icon notification badges. **Requires explicit user enablement in system settings**; the app cannot self-grant it. Disclose in the listing |
| `CiyatoWeatherTileService` | `BIND_QUICK_SETTINGS_TILE` | Quick Settings tile |
| `CiyatoFocusTileService` | `BIND_QUICK_SETTINGS_TILE` | Quick Settings tile |

No broadcast receivers are declared by the app. Boot rescheduling for periodic
work comes from WorkManager's own manifest entry, not one of ours.

## Data leaving the device

- **Weather / air quality / reverse geocoding** — coarsened coordinates only.
- **Breach checker** — the first five characters of a SHA-1 hash, with
  `Add-Padding` enabled so response size does not reveal the query.
- **Nothing else.** Workspace layout, app inventory, file and photo metadata,
  vault contents and crash logs stay local. `android:allowBackup="false"`, and
  `data_extraction_rules.xml` excludes the vault from device-to-device transfer
  because its Keystore key cannot leave the phone.

## Still outstanding before submission

These are device- or account-gated and are not code work:

- Upload signing key generated and the release build signed with it.
- Play Console: Permissions Declaration Form for `QUERY_ALL_PACKAGES`, and the
  All-files-access declaration for `MANAGE_EXTERNAL_STORAGE`.
- Data safety form filled from the table above.
- Predictive back and 16 KB page-size validation on hardware (F-045, F-188).
- Instrumentation suite executed on a device (F-053).
