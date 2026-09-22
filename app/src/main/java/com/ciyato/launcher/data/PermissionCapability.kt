package com.ciyato.launcher.data

/**
 * One sensitive capability: what it is for, what it reaches, where the data
 * goes, and what happens when it is refused.
 *
 * Every feature used to phrase this for itself. Photos said one thing in its
 * denial card and another in Settings; the breach checker's own screen was
 * precise about sending a hash prefix while the Settings row that opened it
 * said the check "never leaves your device" (F-194, F-196). Nothing was lying
 * on purpose — there was simply no single place where the answer lived, so each
 * screen wrote its own and they drifted.
 *
 * This is that place. It is deliberately data, not copy: the Play Data Safety
 * form, the privacy policy, the in-app disclosures and `DATA_INVENTORY.md` all
 * answer the same questions, and they should answer them from one row each
 * rather than from four authors' memories.
 *
 * `PermissionRegistryTest` holds it to that — it fails when a sensitive
 * permission reaches the manifest without a row here, when a row names a
 * permission the manifest does not declare, and when a row claims an off-device
 * recipient that no network call in the app actually contacts.
 */
data class PermissionCapability(
    /** Exactly as the manifest spells it. */
    val permission: String,
    val kind: Kind,
    /** The feature that needs it, in the words the UI uses. */
    val feature: String,
    /** What the person gets for granting it. */
    val userValue: String,
    /** How far it reaches — the honest scope, not the reassuring one. */
    val scope: String,
    /** Play Data Safety data type. */
    val dataCategory: DataCategory,
    /** Where any of it goes, if anywhere. */
    val destination: Destination,
    /** False when the app cannot perform its core purpose without it. */
    val optional: Boolean,
    /** What the feature does when this is refused. Never "nothing happens". */
    val onDenial: String,
    /** Where in the app or the system the person turns it off again. */
    val settingsPath: String,
    val playDeclaration: PlayDeclaration,
) {
    enum class Kind {
        /** Requested at runtime with a system dialog. */
        RUNTIME,

        /** Granted only from a Settings screen — Usage access, All files, notification listener. */
        SPECIAL_ACCESS,

        /** Install-time, no prompt. */
        NORMAL,
    }

    enum class DataCategory {
        APP_ACTIVITY,
        APPROXIMATE_LOCATION,
        CALENDAR,
        FILES_AND_DOCS,
        INSTALLED_APPS,
        AUDIO,
        PHOTOS_AND_VIDEOS,
        DEVICE_OR_OTHER_IDS,
        NONE,
    }

    /**
     * Where data read under this permission ends up.
     *
     * [OnDevice] is the honest default and covers most of the app. A row that
     * claims it while the feature makes a network call is the exact defect this
     * registry exists to prevent, so the recipients in [OffDevice] are checked
     * against the hosts the source actually contacts.
     */
    sealed interface Destination {
        data object OnDevice : Destination

        /**
         * @param hosts the exact hosts contacted.
         * @param sends what is actually transmitted — a narrower thing than
         *   what the permission grants, in every case here, and worth stating
         *   precisely because the difference is the whole trust argument.
         */
        data class OffDevice(val hosts: List<String>, val sends: String) : Destination

        /**
         * Leaves Ciyato for another app on the device, which then decides.
         *
         * Speech recognition is the case that matters: Android's
         * SpeechRecognizer is implemented by whichever provider the device
         * ships, and several of them process audio on a server. Ciyato cannot
         * know which, so it must not claim either way (F-144).
         */
        data class HandedToSystem(val handler: String, val note: String) : Destination
    }

    enum class PlayDeclaration {
        /** Nothing to file. */
        NONE,

        /** Restricted: needs a Play Console declaration and review. */
        DECLARATION_FORM,

        /** Needs prominent in-app disclosure before first use. */
        PROMINENT_DISCLOSURE,
    }
}

/**
 * Every sensitive capability Ciyato declares.
 *
 * Ordered by how much it asks of the person, not alphabetically — a reviewer
 * reading top to bottom should meet the hardest justifications first.
 */
object PermissionRegistry {

    private val On = PermissionCapability.Destination.OnDevice

    val capabilities: List<PermissionCapability> = listOf(

        // ── Restricted: these carry the Play review burden ──────────────────

        PermissionCapability(
            permission = "android.permission.QUERY_ALL_PACKAGES",
            kind = PermissionCapability.Kind.NORMAL,
            feature = "The launcher itself",
            userValue = "Shows and launches every app on the phone, sorts them into categories, and lets you hide, lock or remove them.",
            scope = "The name, icon and launch intent of every installed app.",
            dataCategory = PermissionCapability.DataCategory.INSTALLED_APPS,
            destination = On,
            optional = false,
            onDenial = "Not refusable — it is install-time. A launcher that cannot see installed apps has nothing to show.",
            settingsPath = "Not applicable.",
            playDeclaration = PermissionCapability.PlayDeclaration.DECLARATION_FORM,
        ),

        PermissionCapability(
            permission = "android.permission.MANAGE_EXTERNAL_STORAGE",
            kind = PermissionCapability.Kind.SPECIAL_ACCESS,
            feature = "Files",
            userValue = "Browses, searches, categorises and cleans up files anywhere on shared storage, including folders other apps created.",
            scope = "Every file on shared storage. This is the broadest thing Ciyato asks for.",
            dataCategory = PermissionCapability.DataCategory.FILES_AND_DOCS,
            destination = On,
            optional = true,
            onDenial = "Files falls back to the system document picker and MediaStore. Browsing works for what you pick; device-wide search, duplicate detection and cleanup do not.",
            settingsPath = "Settings > Files, or Android Settings > Apps > Ciyato > All files access",
            playDeclaration = PermissionCapability.PlayDeclaration.DECLARATION_FORM,
        ),

        PermissionCapability(
            permission = "android.permission.READ_MEDIA_IMAGES",
            kind = PermissionCapability.Kind.RUNTIME,
            feature = "Photos",
            userValue = "Organises the whole photo library: collections, duplicate cleanup, on-device labelling, and Photos-to-PDF.",
            scope = "Every image in the media library, or only the ones you select if you choose partial access.",
            dataCategory = PermissionCapability.DataCategory.PHOTOS_AND_VIDEOS,
            destination = On,
            optional = true,
            onDenial = "Photos shows what you grant. With partial access, totals and duplicate detection cover only the selected photos and say so.",
            settingsPath = "Android Settings > Apps > Ciyato > Permissions > Photos and videos",
            playDeclaration = PermissionCapability.PlayDeclaration.DECLARATION_FORM,
        ),

        PermissionCapability(
            permission = "android.permission.READ_MEDIA_VIDEO",
            kind = PermissionCapability.Kind.RUNTIME,
            feature = "Photos",
            userValue = "Includes videos in the library, thumbnails and storage cleanup.",
            scope = "Every video in the media library, or only the ones you select.",
            dataCategory = PermissionCapability.DataCategory.PHOTOS_AND_VIDEOS,
            destination = On,
            optional = true,
            onDenial = "Videos are absent from the library and from cleanup totals.",
            settingsPath = "Android Settings > Apps > Ciyato > Permissions > Photos and videos",
            playDeclaration = PermissionCapability.PlayDeclaration.DECLARATION_FORM,
        ),

        PermissionCapability(
            permission = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            kind = PermissionCapability.Kind.RUNTIME,
            feature = "Photos",
            userValue = "Lets you grant Ciyato a chosen set of photos instead of the whole library.",
            scope = "Only the photos you pick, and only until you change the selection.",
            dataCategory = PermissionCapability.DataCategory.PHOTOS_AND_VIDEOS,
            destination = On,
            optional = true,
            onDenial = "Photos asks for full access instead, or stays empty.",
            settingsPath = "Android Settings > Apps > Ciyato > Permissions > Photos and videos > Select photos",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        // ── Runtime: asked for when a feature needs them ────────────────────

        PermissionCapability(
            permission = "android.permission.READ_MEDIA_AUDIO",
            kind = PermissionCapability.Kind.RUNTIME,
            feature = "Files",
            userValue = "Lists and categorises audio files alongside documents and images.",
            scope = "Audio files in the media library.",
            dataCategory = PermissionCapability.DataCategory.AUDIO,
            destination = On,
            optional = true,
            onDenial = "The Audio category is empty and says why.",
            settingsPath = "Android Settings > Apps > Ciyato > Permissions > Music and audio",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        PermissionCapability(
            permission = "android.permission.READ_EXTERNAL_STORAGE",
            kind = PermissionCapability.Kind.RUNTIME,
            feature = "Files and Photos on Android 12 and below",
            userValue = "The pre-Android 13 equivalent of the media permissions above.",
            scope = "Shared storage on older releases. Declared with maxSdkVersion so newer releases use the granular permissions instead.",
            dataCategory = PermissionCapability.DataCategory.FILES_AND_DOCS,
            destination = On,
            optional = true,
            onDenial = "Same fallbacks as the granular permissions.",
            settingsPath = "Android Settings > Apps > Ciyato > Permissions",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        PermissionCapability(
            permission = "android.permission.ACCESS_COARSE_LOCATION",
            kind = PermissionCapability.Kind.RUNTIME,
            feature = "Weather",
            userValue = "Shows the forecast and air quality where you are, and names the place.",
            scope = "Approximate location only. Ciyato never requests precise location, and rounds what it gets to two decimal places before any of it is sent.",
            dataCategory = PermissionCapability.DataCategory.APPROXIMATE_LOCATION,
            destination = PermissionCapability.Destination.OffDevice(
                hosts = listOf(
                    "api.open-meteo.com",
                    "air-quality-api.open-meteo.com",
                    "nominatim.openstreetmap.org",
                ),
                sends = "Latitude and longitude rounded to two decimals (roughly a kilometre), and nothing else. No identifier, no account, no history.",
            ),
            optional = true,
            onDenial = "Weather asks for a city instead, or the card stays off. Nothing is sent.",
            settingsPath = "Settings > Weather, or Android Settings > Apps > Ciyato > Permissions > Location",
            playDeclaration = PermissionCapability.PlayDeclaration.PROMINENT_DISCLOSURE,
        ),

        PermissionCapability(
            permission = "android.permission.READ_CALENDAR",
            kind = PermissionCapability.Kind.RUNTIME,
            feature = "Agenda and the Today card",
            userValue = "Shows what is next today on Home, and the agenda screen.",
            scope = "Events from the calendars already on the device. Read only — Ciyato never writes or deletes an event.",
            dataCategory = PermissionCapability.DataCategory.CALENDAR,
            destination = On,
            optional = true,
            onDenial = "The Today card offers to connect a calendar instead of showing events.",
            settingsPath = "Android Settings > Apps > Ciyato > Permissions > Calendar",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        PermissionCapability(
            permission = "android.permission.RECORD_AUDIO",
            kind = PermissionCapability.Kind.RUNTIME,
            feature = "Voice commands",
            userValue = "Opens an app, searches or starts a focus session from a spoken phrase.",
            scope = "The microphone, only while you are holding the voice button on that screen.",
            dataCategory = PermissionCapability.DataCategory.AUDIO,
            destination = PermissionCapability.Destination.HandedToSystem(
                handler = "android.speech.SpeechRecognizer",
                note = "Ciyato does not process or store audio. It hands recognition to whichever speech service the device ships, and several of those send audio to a server. Which one, and whether it does, is the device's decision and is visible in Android's own settings — Ciyato cannot see it and must not claim otherwise.",
            ),
            optional = true,
            onDenial = "The voice screen explains that it needs the microphone and does nothing else. Every voice action has a tap equivalent.",
            settingsPath = "Android Settings > Apps > Ciyato > Permissions > Microphone",
            playDeclaration = PermissionCapability.PlayDeclaration.PROMINENT_DISCLOSURE,
        ),

        // ── Special access: granted from a Settings screen ──────────────────

        PermissionCapability(
            permission = "android.permission.PACKAGE_USAGE_STATS",
            kind = PermissionCapability.Kind.SPECIAL_ACCESS,
            feature = "Insights, screen time, anomaly detection and suggestions",
            userValue = "Shows how long apps are used, what changed this week, and which app to suggest next.",
            scope = "Per-app foreground time and launch counts from Android's own usage statistics. No content, no keystrokes, no screen contents.",
            dataCategory = PermissionCapability.DataCategory.APP_ACTIVITY,
            destination = On,
            optional = true,
            onDenial = "Each of those screens says Usage access is off and offers to open the Settings page. None of them invent numbers.",
            settingsPath = "Android Settings > Apps > Special app access > Usage access",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        PermissionCapability(
            permission = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            kind = PermissionCapability.Kind.SPECIAL_ACCESS,
            feature = "Notification badges",
            userValue = "Puts a count on an app icon when it has notifications waiting.",
            scope = "Which apps currently have notifications posted, and how many. Ciyato reads the count, not the contents.",
            dataCategory = PermissionCapability.DataCategory.APP_ACTIVITY,
            destination = On,
            optional = true,
            onDenial = "Icons show no badges. Nothing else changes.",
            settingsPath = "Android Settings > Notifications > Device and app notifications",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        // ── Normal: no prompt, listed for completeness ──────────────────────

        PermissionCapability(
            permission = "android.permission.INTERNET",
            kind = PermissionCapability.Kind.NORMAL,
            feature = "Weather and the breach checker",
            userValue = "The only two features that use the network.",
            scope = "Outbound HTTPS to the hosts listed for those features. Nothing else in Ciyato makes a network call.",
            dataCategory = PermissionCapability.DataCategory.NONE,
            destination = PermissionCapability.Destination.OffDevice(
                hosts = listOf(
                    "api.open-meteo.com",
                    "air-quality-api.open-meteo.com",
                    "nominatim.openstreetmap.org",
                    "api.pwnedpasswords.com",
                ),
                sends = "See the location row and DATA_INVENTORY.md. No analytics, advertising or crash-reporting SDK is present, so nothing else is transmitted.",
            ),
            optional = false,
            onDenial = "Not refusable — it is install-time. Turning off the features that use it is the equivalent, and both are off until used.",
            settingsPath = "Settings > Weather; the breach checker only runs when you type a password into it.",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        PermissionCapability(
            permission = "android.permission.ACCESS_NETWORK_STATE",
            kind = PermissionCapability.Kind.NORMAL,
            feature = "Weather and automatic photo backup",
            userValue = "Avoids a doomed request when there is no connection, and holds backup until the network matches what you chose.",
            scope = "Whether a network is available and whether it is metered. Not which network, not its name.",
            dataCategory = PermissionCapability.DataCategory.NONE,
            destination = On,
            optional = false,
            onDenial = "Not refusable — it is install-time.",
            settingsPath = "Not applicable.",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        PermissionCapability(
            permission = "android.permission.REQUEST_DELETE_PACKAGES",
            kind = PermissionCapability.Kind.NORMAL,
            feature = "Uninstall from the launcher",
            userValue = "Long-press an app and remove it without going to Settings.",
            scope = "Asks Android to show its own uninstall dialog. Ciyato cannot uninstall anything itself — the system dialog and your confirmation do it.",
            dataCategory = PermissionCapability.DataCategory.NONE,
            destination = On,
            optional = false,
            onDenial = "Not refusable — it is install-time. Declining the system dialog cancels the uninstall.",
            settingsPath = "Not applicable.",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        PermissionCapability(
            permission = "android.permission.SET_WALLPAPER",
            kind = PermissionCapability.Kind.NORMAL,
            feature = "Wallpaper",
            userValue = "Applies a wallpaper you picked to the system.",
            scope = "Setting the wallpaper. Ciyato cannot read the current one.",
            dataCategory = PermissionCapability.DataCategory.NONE,
            destination = On,
            optional = false,
            onDenial = "Not refusable — it is install-time.",
            settingsPath = "Not applicable.",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),

        PermissionCapability(
            permission = "android.permission.VIBRATE",
            kind = PermissionCapability.Kind.NORMAL,
            feature = "Haptic feedback",
            userValue = "A short buzz on long-press and drag.",
            scope = "The vibrator, for short feedback pulses only. No pattern, no sound, no access to anything else.",
            dataCategory = PermissionCapability.DataCategory.NONE,
            destination = On,
            optional = true,
            onDenial = "Not refusable — it is install-time. Settings > Haptic feedback turns it off.",
            settingsPath = "Settings > Haptic feedback",
            playDeclaration = PermissionCapability.PlayDeclaration.NONE,
        ),
    )

    /** Capabilities whose data reaches something outside the device. */
    val offDevice: List<PermissionCapability> = capabilities.filter {
        it.destination !is PermissionCapability.Destination.OnDevice
    }

    /** Capabilities that need a Play Console declaration before release. */
    val needingPlayDeclaration: List<PermissionCapability> = capabilities.filter {
        it.playDeclaration != PermissionCapability.PlayDeclaration.NONE
    }

    fun forPermission(permission: String): PermissionCapability? =
        capabilities.firstOrNull { it.permission == permission }
}
