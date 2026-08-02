# Ciyato Store Readiness Notes

Date: 2026-07-09

## Current Android posture

- Ciyato is an Android launcher and local phone organizer. The current deliverable is an Android APK, not an iOS build.
- The manifest keeps runtime-sensitive access narrow: no full-gallery media permission, no broad storage permission, no fine location, no background location, no microphone, no calendar, no boot receiver, no foreground service, and no notification listener in the beta manifest.
- Files use Android Storage Access Framework instead of `MANAGE_EXTERNAL_STORAGE`.
- Photos use Android Photo Picker instead of full photo-library access.
- Weather requests approximate foreground location only after the user opens Weather and sees an explanation.
- App discovery uses `QUERY_ALL_PACKAGES` because launcher/search functionality must see installed apps. This needs a Play Console Permissions Declaration Form and store-listing copy that clearly explains installed-app discovery as core launcher functionality.

## Policy alignment checks

- Android runtime permission UX follows contextual permission guidance: explain the feature, ask only when the user invokes that feature, allow cancellation, and degrade gracefully on denial.
- Android package visibility is documented as sensitive. Google Play allows broad visibility only when the app's core user-facing purpose requires it and requires a declaration in Play Console.
- Android all-files access is intentionally avoided. Google Play recommends privacy-friendly alternatives such as SAF or MediaStore unless all-files access is core and approved.
- Apple App Store guidance requires consent, clear purpose strings, data minimization, and alternatives when users deny access. A separate iOS implementation would need iOS-specific privacy strings and App Store review work.

## Release caveats

- Google Play acceptance cannot be guaranteed by code alone; the Play listing, privacy policy, Data Safety form, and `QUERY_ALL_PACKAGES` declaration must be truthful and consistent with the APK.
- iPhone installation is not possible with this APK. iOS requires a separate native iOS project or cross-platform build output.
- Final UX acceptance still needs a physical Android device because emulator runtime testing was intentionally not used in this pass.

## Official references

- Google Play `QUERY_ALL_PACKAGES` policy: https://support.google.com/googleplay/android-developer/answer/10158779
- Android package visibility: https://developer.android.com/training/package-visibility
- Android runtime permissions: https://developer.android.com/training/permissions/requesting
- Google Play all-files access policy: https://support.google.com/googleplay/android-developer/answer/10467955
- Apple App Review Guidelines, privacy: https://developer.apple.com/app-store/review/guidelines/
