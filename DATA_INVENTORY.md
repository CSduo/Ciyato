# Data inventory

Every piece of data Ciyato touches, where it came from, and where it goes.

This document exists because "local-first" is a slogan and Google Play's Data
Safety form is a set of questions. Ciyato really is local-first — the layout,
the organisation, the categories, the photo labelling and the file index never
leave the phone — but it is not "nothing ever leaves". Weather sends a rounded
location to three hosts, and the optional breach checker sends part of a
password hash to a fourth. A Data Safety answer that said "no data collected"
would be wrong, and being wrong there is an enforcement matter, not a wording
one (F-195).

Every row below is derived from
[`PermissionCapability.kt`](app/src/main/java/com/ciyato/launcher/data/PermissionCapability.kt),
which is the source of truth in code. `PermissionRegistryTest` fails the build
when the manifest declares a sensitive permission with no row, when a row names
a permission the manifest does not declare, when a network host in the source is
not disclosed here, and when a capability exists in code but is missing from
this file.

**Scope of this document:** the release build, as merged. Library manifests
contribute permissions the source manifest never mentions, so everything here is
checked against the merged manifest when one exists (F-184).

---

## 1. What leaves the device

Four hosts. That is the complete list, and the test enumerates the source to
prove nothing else is contacted.

| Host | Feature | What is sent | What is never sent | When |
|---|---|---|---|---|
| `api.open-meteo.com` | Weather | Latitude and longitude rounded to 2 decimals (~1 km) | Exact coordinates, any identifier, any account, any history | When the weather card refreshes, and only if you enabled it |
| `air-quality-api.open-meteo.com` | Weather (air quality) | The same rounded coordinates | As above | With the same refresh |
| `nominatim.openstreetmap.org` | Weather (place name) | The same rounded coordinates | As above | To turn coordinates into a city name |
| `api.pwnedpasswords.com` | Breach checker | The first 5 characters of the SHA-1 hash of the password | The password, the rest of the hash, anything identifying you | Only when you type a password into that screen and tap check |

**Rounding is done before the request is built, not after.** Two decimal places
is indistinguishable for a forecast, an air-quality reading or a city lookup,
and it is the difference between disclosing a neighbourhood and disclosing a
address.

**The breach check uses k-anonymity.** Ciyato sends five characters of a hash
and receives back every hash suffix sharing that prefix — typically several
hundred. The comparison happens on the device. The service cannot tell which
one, if any, was yours, and it never sees the password.

**No analytics, advertising, attribution or crash-reporting SDK is present.**
The dependency list is in `app/build.gradle.kts`; the only non-AndroidX,
non-Kotlin entries are Coil (image loading, no network beyond the URLs it is
given) and ML Kit image labelling.

**ML Kit runs entirely on the device.** The dependency is
`com.google.mlkit:image-labeling`, the bundled variant — the model ships inside
the APK. It is not `play-services-mlkit-image-labeling`, which downloads a model
and would be a different disclosure. Photos are labelled locally; no image and
no label is transmitted.

---

## 2. Permissions, and what each one is for

Full text for each row — user value, scope, denial behaviour and settings path —
lives in `PermissionCapability.kt`. This is the reviewer's summary.

### Restricted — these need a Play Console declaration

| Permission | Data category | Why it is core | Leaves device |
|---|---|---|---|
| `QUERY_ALL_PACKAGES` | Installed apps | Ciyato is a home-screen replacement. A launcher that cannot enumerate installed apps has nothing to show, sort, hide, lock or launch. | No |
| `MANAGE_EXTERNAL_STORAGE` | Files and docs | Files is a device-wide file manager: search, categorisation, duplicate detection and cleanup across folders other apps created. SAF can open a file the person picks; it cannot find duplicates across a device. | No |
| `READ_MEDIA_IMAGES` | Photos and videos | Photos is a whole-library organiser: collections, duplicate cleanup, on-device labelling, Photos-to-PDF. The system picker returns a selection, which cannot answer "which of my photos are duplicates". | No |
| `READ_MEDIA_VIDEO` | Photos and videos | Same, for video, including storage cleanup totals. | No |

Partial access (`READ_MEDIA_VISUAL_USER_SELECTED`) is supported and honoured:
when it is granted, totals and duplicate results describe only the selected
photos and the UI says so rather than presenting a partial count as a complete
one.

### Runtime

| Permission | Data category | Feature | Leaves device |
|---|---|---|---|
| `READ_MEDIA_VISUAL_USER_SELECTED` | Photos and videos | Photos, partial access | No |
| `READ_MEDIA_AUDIO` | Audio files | Files — audio category | No |
| `READ_EXTERNAL_STORAGE` | Files and docs | Files and Photos on Android 12 and below (`maxSdkVersion`) | No |
| `ACCESS_COARSE_LOCATION` | Approximate location | Weather | **Yes** — rounded to 2 decimals, to the three weather hosts above |
| `READ_CALENDAR` | Calendar | Agenda and the Today card. Read only; Ciyato never writes or deletes an event | No |
| `RECORD_AUDIO` | Audio | Voice commands | **Handed to the system** — see below |

**Voice recognition is the one Ciyato cannot answer for.** It uses Android's
`SpeechRecognizer`, which is implemented by whichever speech service the device
ships. Several of those send audio to a server. Ciyato does not record, store or
transmit audio itself, and it cannot see what the provider does — so the
disclosure says exactly that rather than claiming either way (F-144). Every
voice action has a tap equivalent.

### Special access — granted only from a Settings screen

| Permission | Data category | Feature | Leaves device |
|---|---|---|---|
| `PACKAGE_USAGE_STATS` | App activity | Insights, screen time, anomaly detection, suggestions, daily summary. Per-app foreground time and launch counts — no content, no keystrokes, no screen contents | No |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | App activity | Notification badges. The count, not the contents | No |

### Install-time

`INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_DELETE_PACKAGES`, `SET_WALLPAPER`,
`VIBRATE`. `REQUEST_DELETE_PACKAGES` only asks Android to show its own uninstall
dialog; Ciyato cannot uninstall anything itself.

### Contributed by libraries, not by Ciyato's own manifest

`USE_BIOMETRIC` and `USE_FINGERPRINT` (androidx.biometric — the vault, hidden
apps and app lock), `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED` (WorkManager, so
scheduled photo backup survives a reboot), `FOREGROUND_SERVICE`. These are in
the merged manifest and therefore in the release build, which is why this
document is written against the merged manifest.

---

## 3. Data Ciyato stores on the device

| What | Where | Retention | Encrypted |
|---|---|---|---|
| Layout, workspaces, categories, settings | DataStore Preferences, app-private | Until changed or the app is uninstalled | Android app-private storage |
| File index and tags | Room database, app-private | Until rebuilt or cleared from Settings | Android app-private storage |
| Photo collections and labelling results | DataStore, as URIs only — never image data | Until rescanned or cleared. Rebuilt against the live library on restore, so a deleted photo cannot reappear | Android app-private storage |
| Secure vault files | App-private directory | Until removed from the vault | **AES-256-GCM**, key in the Android Keystore, non-extractable, never written to disk |
| Crash logs | `filesDir/crash-logs`, app-private | Until cleared from Settings | Android app-private storage |
| Search history | DataStore, app-private | Until cleared from Settings | Android app-private storage |

**Crash logs are never uploaded.** There is no crash-reporting SDK and no
endpoint to send them to; they exist so a person can read what happened and
choose to share it themselves. Logging is on by default, which is defensible
precisely because nothing leaves — and it is one switch in Settings, with a
screen that shows exactly what was recorded.

**Backups are excluded.** `backup_rules.xml` and `data_extraction_rules.xml`
keep the vault and crash logs out of Android's automatic backup, so encrypted
material and diagnostics are not copied off the device by the platform.

---

## 4. Data Ciyato writes somewhere the person chose

**Automatic photo backup** copies photos to a folder selected through the system
document picker. That folder may belong to a cloud provider — Drive, OneDrive,
a NAS — in which case the photos go wherever that provider sends them. Ciyato
cannot control or inspect that, and does not pretend to: when the destination
does not look local, backup additionally requires an unmetered network so it
cannot quietly spend a data allowance.

**Photos-to-PDF** and **file exports** write to a location chosen through the
same picker. Same caveat, same reason.

---

## 5. Traceability

| Play Data Safety question | Answer | Source |
|---|---|---|
| Does the app collect or share user data? | Shares approximate location and a partial password hash, for the two optional features named in §1. Collects nothing — there is no server, no account and no identifier. | §1 |
| Is data encrypted in transit? | Yes. All four hosts are HTTPS; there is no cleartext path. | §1 |
| Can users request deletion? | There is nothing stored off-device to delete. Everything on-device is cleared by uninstalling, and individually from Settings. | §3 |
| Approximate location — collected or shared? | Shared, not collected. Sent to the weather hosts at request time; Ciyato keeps no location history. | §1, §2 |
| Photos and videos | Accessed on device, never transmitted. | §2, §3 |
| Files and docs | Accessed on device, never transmitted. Written only where the person picks. | §2, §4 |
| App activity | Accessed on device, never transmitted. | §2 |
| Audio | Microphone handed to the system recogniser; Ciyato neither stores nor transmits audio. | §2 |

---

## 6. Before submission

- [ ] Re-run `PermissionRegistryTest` against the **release** merged manifest.
- [ ] Confirm no dependency added since the last review introduces a network
      call (the test enumerates hosts in `src/main/java`; a library's own calls
      are not visible to it, so the dependency list needs a human read).
- [ ] Capture a packet trace of a release build through a full session: launcher
      use, Files, Photos, a weather refresh, a breach check. Four hosts, nothing
      else.
- [ ] Complete the Play declaration for each row in §2's restricted table, using
      the "why it is core" column as the justification.
- [ ] Check the store listing against `PLAY_POSITIONING.md` — the declaration is
      judged against the stated core purpose.
