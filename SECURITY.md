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

## What Ciyato enforces, and what it cannot

Ciyato is a launcher. A launcher is an ordinary app with one extra job: it draws the home
screen and starts other apps. That job gives it real control over its own surfaces and
almost none over the device (F-207).

**App lock, Focus and hidden apps all work the same way, and all have the same edge.**

| Ciyato can | Ciyato cannot |
|---|---|
| Refuse to launch an app from Home, the drawer, search or suggestions until you authenticate | Stop the app opening from Recents, a notification, a widget, a shared intent, the Play Store, Android's own search, or another launcher |
| Hide apps and categories from everything it draws | Hide them from Settings, Recents, or any other launcher |
| Require biometric or device credential before revealing the vault or hidden apps | Prevent someone with your unlocked phone from reaching the app another way |

None of that is a bug or a gap to close later. No launcher has those powers; the ones that
claim to are relying on accessibility services or device-admin roles, which is a different
product with a different permission story and a much worse one for privacy.

So the copy says so. "Require unlock" rather than "lock", "Hide categories" rather than
"Block categories", "Unlock to open it from Ciyato" rather than "this app is locked". The
App Lock screen states the boundary in the UI itself, next to the control, rather than in a
document nobody reads before trusting it.

**What this means in practice:** Ciyato's lock is good for the over-the-shoulder case — a
handed-over phone, a curious child, a colleague who wanted to see one photo. It is not a
security boundary against someone who has your unlocked device and wants in. For that, use
the app's own lock if it has one, or a work profile.

**Known bypasses, stated rather than left to be discovered:** Recents; notification taps;
home-screen widgets belonging to the locked app; any deep link or share target; switching
launchers; Settings > Apps. Each of these reaches the app without Ciyato being asked.

## Encrypted vault

The secure file vault is active, not a roadmap item. Files imported into it are encrypted
with AES-256-GCM using a key generated in the Android Keystore, which is non-extractable and
never written to disk. Decryption requires biometric or device-credential authentication.
The vault is excluded from Android's automatic backup, so encrypted material is not copied
off the device by the platform.

The limit worth stating: this protects the file at rest inside Ciyato. It does not protect
against a compromised device, and it cannot protect a copy you exported somewhere else.

## Roadmap boundaries

Accounts, device migration and semantic AI grouping are roadmap items and are not active.
Any future cloud feature must be opt-in, explain its data destination, provide deletion
controls, and avoid uploading sensitive content by default.

Photo backup exists and is opt-in: it copies to a folder you choose through the system
document picker, which may belong to a cloud provider. `DATA_INVENTORY.md` §4 covers what
that means.
