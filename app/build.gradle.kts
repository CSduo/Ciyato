import org.gradle.api.tasks.PathSensitivity
// `java` resolves to the Android/Gradle extension inside this script, so the
// package cannot be referenced inline — import the type explicitly.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)                // KSP for Room (#111)
}

// Fails the build with an explanation the moment a release assembly is requested
// without signing credentials. Without this the release task would still run and
// emit an unsigned APK/AAB — technically "successful", and useless. An explicit
// message beats discovering it at upload time.
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { task ->
        task.name.contains("Release") &&
            (task.name.startsWith("assemble") || task.name.startsWith("bundle"))
    }
    if (wantsRelease && project.extensions.getByType(com.android.build.gradle.AppExtension::class.java)
            .signingConfigs.findByName("upload") == null
    ) {
        throw GradleException(
            """
            Release signing is not configured, so this build was stopped.

            Provide an upload key in ONE of these ways:
              1. app/keystore.properties (gitignored) containing:
                     storeFile=/absolute/path/to/upload-keystore.jks
                     storePassword=...
                     keyAlias=...
                     keyPassword=...
              2. Environment variables:
                     CIYATO_KEYSTORE, CIYATO_KEYSTORE_PASSWORD,
                     CIYATO_KEY_ALIAS, CIYATO_KEY_PASSWORD

            Debug signing is deliberately NOT used as a fallback: Play rejects
            debug-signed uploads, and the debug key is publicly known, so anything
            signed with it can be replaced by anyone.
            """.trimIndent()
        )
    }
}

// ── Release signing credentials ───────────────────────────────────────────────
//
// Resolved at the top level on purpose: inside the `android { }` block, `java`
// resolves to the Android extension rather than the java package, so
// java.util.Properties cannot be referenced there.
//
// Credentials come from app/keystore.properties (gitignored) or environment
// variables, so nothing secret is committed. If neither is present the "upload"
// signing config is never created, the release build type gets no signingConfig,
// and the guard below stops the build — rather than silently emitting an
// unpublishable artifact.
//
// Release previously fell back to signingConfigs.getByName("debug"), so
// `assembleRelease` always succeeded and always produced a debug-signed APK.
// Play rejects those, and the debug key is publicly known, so anything signed
// with it can be replaced by anyone (F-003).
private val keystorePropsFile = rootProject.file("app/keystore.properties")
private val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
private fun signingValue(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val uploadStorePath: String? = signingValue("storeFile", "CIYATO_KEYSTORE")
val uploadStorePassword: String? = signingValue("storePassword", "CIYATO_KEYSTORE_PASSWORD")
val uploadKeyAlias: String? = signingValue("keyAlias", "CIYATO_KEY_ALIAS")
val uploadKeyPassword: String? = signingValue("keyPassword", "CIYATO_KEY_PASSWORD")
val hasUploadKey: Boolean =
    uploadStorePath != null && uploadStorePassword != null &&
        uploadKeyAlias != null && uploadKeyPassword != null &&
        File(uploadStorePath).exists()

/**
 * YYMMDDn as an Int - e.g. 2026-08-23 build 1 becomes 2608231.
 *
 * Stays below Play's 2,100,000,000 ceiling until the year 2121 and is strictly
 * increasing by construction. A malformed date fails the build rather than
 * silently yielding a code that could regress: a version code cannot be fixed
 * after upload, because that number is spent.
 */
fun ciyatoVersionCode(releaseDate: String, buildOfDay: Int): Int {
    val parts = releaseDate.split("-")
    require(parts.size == 3) { "releaseDate must be yyyy-MM-dd, was '$releaseDate'" }
    val nums = parts.map { it.toIntOrNull() ?: error("releaseDate must be numeric, was '$releaseDate'") }
    val (year, month, day) = nums
    require(month in 1..12 && day in 1..31) { "releaseDate is not a real date: '$releaseDate'" }
    require(buildOfDay in 0..9) { "buildOfDay must be a single digit, was $buildOfDay" }
    return (((year % 100) * 10000 + month * 100 + day) * 10) + buildOfDay
}

android {
    namespace = "com.ciyato.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ciyato.launcher"
        minSdk = 26
        targetSdk = 36
        // Monotonic and derived, not hand-bumped.
        //
        // versionCode sat at 1 across the whole of the product's evolution
        // (F-004). Play requires a strictly increasing code per upload, and a
        // crash report or an upgrade test naming "version 1" identifies nothing
        // when dozens of builds share it.
        //
        // The scheme is date-based: YYMMDDn, where n is the build within that
        // day. It cannot go backwards while the clock does not, needs no shared
        // counter, and the number itself says when the build was cut.
        versionCode = ciyatoVersionCode(releaseDate = "2026-08-23", buildOfDay = 1)
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // BuildConfig flags (#113)
        buildConfigField("String",  "WEATHER_BASE_URL",    "\"https://api.open-meteo.com/v1\"")
        buildConfigField("String",  "AQI_BASE_URL",        "\"https://air-quality-api.open-meteo.com/v1\"")
        buildConfigField("String",  "GEOCODE_BASE_URL",    "\"https://nominatim.openstreetmap.org\"")
        buildConfigField("String",  "GITHUB_RELEASES_URL", "\"https://api.github.com/repos/ciyato/launcher/releases/latest\"")
        buildConfigField("long",    "WEATHER_CACHE_TTL_MS","1800000L")   // 30 min
        buildConfigField("int",     "MAX_CRASH_LOGS",      "10")
        buildConfigField("boolean", "IS_INTERNAL",         "false")
        // #143 ENABLE_CERT_PINNING was declared here and in both build types
        // but never wired into any HTTP client — a security toggle that did
        // nothing. Deliberately NOT implemented rather than left dead:
        // every host this app calls (api.open-meteo.com, air-quality-api.
        // open-meteo.com, nominatim.openstreetmap.org, api.pwnedpasswords.com)
        // serves short-lived (~90 day) certs from an automated CA (Let's
        // Encrypt / Google Trust Services) that rotate on their schedule, not
        // ours. This app has no remote pin-update or forced-update path, so a
        // hard pin would go stale on routine rotation and permanently break
        // weather/geocoding/breach-check for every installed user until a new
        // APK is manually reinstalled — a worse outcome than the MITM risk
        // being defended against, especially since none of these calls carry
        // secrets (weather/location are public; the breach check already
        // sends only a 5-char hash prefix via k-anonymity). HTTPS-only
        // (usesCleartextTraffic="false", see AndroidManifest.xml) plus system
        // CA trust is the real, already-working protection here.
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(uploadStorePath!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true     // R8 (#114)
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only set when real credentials were found. Left null otherwise,
            // so Gradle refuses to produce an unsigned/unpublishable release
            // instead of quietly handing back a debug-signed one.
            signingConfig = signingConfigs.findByName("upload")
            buildConfigField("boolean", "IS_INTERNAL",         "false")
        }
        debug {
            isDebuggable = true
            buildConfigField("boolean", "IS_INTERNAL",         "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.runtime.ExperimentalComposeApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    buildFeatures {
        compose    = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // DocumentFile (SAF)
    implementation(libs.androidx.documentfile)

    // Room (#106)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // WorkManager (#20, #34, #54, #125)
    implementation(libs.androidx.work.runtime.ktx)

    // NOTE: OkHttp (#143 cert pinning, #142 network log) was declared here but
    // never imported anywhere in the source — cert pinning was rejected (see
    // the comment above ENABLE_CERT_PINNING's old declaration in defaultConfig)
    // and no network-logging interceptor was ever wired up either. Removed
    // rather than kept as unused APK weight; every real network call already
    // goes through data/NetworkClient.kt on top of java.net.HttpURLConnection.

    // Biometric (#136, #137)
    implementation(libs.androidx.biometric)

    // Paging 3 (#105)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Coil (#64 thumbnails)
    implementation(libs.coil.compose)

    // ML Kit image labeling — bundled on-device model, free, no network needed.
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // Testing
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.whenTaskAdded {
    if (name == "assembleDebug") {
        doLast {
            val apkSrc = file("${layout.buildDirectory.get().asFile}/outputs/apk/debug/app-debug.apk")
            val apkDst = file("${rootDir}/Ciyato.apk")
            if (apkSrc.exists()) { apkSrc.copyTo(apkDst, overwrite = true); println("✅ APK ready: ${apkDst.absolutePath}") }
        }
    }
}

// StoreReadinessDocTest reads AndroidManifest.xml and STORE_READINESS.md straight
// from disk, which Gradle cannot see. Without these declarations the unit-test
// task stays "up to date" when only those files change - so editing the manifest
// alone would skip the very test that exists to catch that edit. Verified by
// adding a permission and watching the run be skipped before this was added.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("app/src/main/AndroidManifest.xml"))
        .withPropertyName("shippingManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("STORE_READINESS.md"))
        .withPropertyName("storeReadinessDoc")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
