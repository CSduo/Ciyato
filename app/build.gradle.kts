import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.tasks.PathSensitivity
// `java` resolves to the Android/Gradle extension inside this script, so the
// package cannot be referenced inline — import the type explicitly.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)                // KSP for Room (#111)
    // Consumer side of the baseline profile the :macrobenchmark module
    // generates. Without this the generated profile is produced and then not
    // packaged, which is a slower app and a green build (F-166).
    id("androidx.baselineprofile")
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
    // CI needs to prove the release variant still ASSEMBLES — ProGuard rules,
    // resource shrinking and manifest merging are release-only and a debug build
    // never exercises them — without holding the upload key, which it must not.
    //
    // The opt-out is an explicit, awkwardly-named property rather than an
    // automatic "skip if on CI" check, because the failure this guard prevents
    // (shipping a debug-signed or unsigned artifact) is worse than the
    // inconvenience of typing it. Anything built this way is an assembly check,
    // not a publishable artifact.
    val allowUnsigned = project.findProperty("ciyatoAllowUnsignedRelease") == "true"
    if (allowUnsigned && wantsRelease) {
        logger.lifecycle(
            "ciyatoAllowUnsignedRelease=true: building an UNSIGNED release for verification only. " +
                "This artifact cannot be uploaded to Play.",
        )
    }
    if (wantsRelease && !allowUnsigned &&
        project.extensions.getByType(com.android.build.gradle.AppExtension::class.java)
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

/**
 * Short commit SHA of the working tree, plus "-dirty" when it has uncommitted
 * changes, or "unknown" when git is not available.
 *
 * A local crash log is only worth keeping if it identifies the exact build that
 * produced it (F-055). versionName answers that for a release; between releases
 * dozens of builds share "1.1.0", and the commit is the only thing separating
 * them.
 *
 * Degrades rather than fails: a source archive with no .git directory must still
 * build, and a missing SHA weakens a diagnostic instead of blocking a release.
 * "-dirty" matters because a bare SHA would claim the artifact matches that
 * commit when it does not.
 */
fun ciyatoGitSha(): String = runCatching {
    fun git(vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readText().trim()
        return if (process.waitFor() != 0) "" else out
    }
    val sha = git("rev-parse", "--short", "HEAD")
    when {
        sha.isEmpty() -> "unknown"
        git("status", "--porcelain").isNotEmpty() -> "$sha-dirty"
        else -> sha
    }
}.getOrDefault("unknown")


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
        // GITHUB_RELEASES_URL was removed rather than corrected (F-005). It
        // pointed at github.com/ciyato/launcher — a different project from
        // CSduo/Ciyato — and nothing in src/ ever read it. A dead constant that
        // names a plausible endpoint is worse than no constant: it tells the
        // next maintainer an update check exists. If in-app update checking is
        // ever built, it should arrive with the code that uses it.
        buildConfigField("long",    "WEATHER_CACHE_TTL_MS","1800000L")   // 30 min
        buildConfigField("int",     "MAX_CRASH_LOGS",      "10")
        buildConfigField("boolean", "IS_INTERNAL",         "false")
        // Which commit this artifact was built from - see ciyatoGitSha above.
        buildConfigField("String", "GIT_SHA", "\"${ciyatoGitSha()}\"")
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
        /**
         * Release-shaped, measurable, and not publishable.
         *
         * Benchmarks have to measure something a user would actually run: R8
         * and resource shrinking change what code exists, and a debuggable
         * build is slower in ways that have nothing to do with the code being
         * measured. So this inherits release.
         *
         * It is signed with the DEBUG key on purpose. The release signing
         * config is deliberately null without real credentials so Gradle
         * refuses to emit an unsigned release; a benchmark build needs neither
         * the upload key nor the guard, and its APK must never be mistaken for
         * a shippable one.
         */
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // Profileable, NOT debuggable. Making it debuggable disables R8
            // and resource shrinking outright - Gradle says so - which would
            // have measured a build that does not exist and called it release
            // performance. Profileable lets macrobenchmark attach from API 29
            // up; on 26-28 these benchmarks cannot run, which is a stated limit
            // rather than a silently wrong number.
            isDebuggable = false
            isProfileable = true
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

    testOptions {
        unitTests {
            // Robolectric inflates real resources. Without this every lookup
            // returns a stub and a golden is a picture of nothing.
            isIncludeAndroidResources = true
        }
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

    // Installs the baseline profile at first run. Already present transitively,
    // declared explicitly because the profile is useless without it and a
    // transitive dependency can disappear in an upgrade (F-166, F-184).
    implementation(libs.androidx.profileinstaller)

    // Testing
    testImplementation(libs.junit)
    // Golden image tests. See screenshots/README.md and F-165.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
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
    // Roborazzi reads SYSTEM properties, not Gradle project properties.
    //
    // Passing -Proborazzi.record.image=true alone does nothing: it never reaches
    // the test JVM, captureRoboImage() becomes a no-op, no image is written, and
    // the test passes. That is the worst possible shape for a golden test - it
    // cannot fail, so it silently certifies whatever the layout does.
    //
    // Default is VERIFY, deliberately. If neither property is set Roborazzi does
    // nothing at all, which would make an ordinary run vacuous; comparing against
    // the committed goldens is the behaviour that earns the test its place.
    val recording = providers.gradleProperty("roborazzi.record.image").isPresent
    systemProperty("roborazzi.test.record", recording.toString())
    systemProperty("roborazzi.test.verify", (!recording).toString())

    inputs.file(rootProject.file("app/src/main/AndroidManifest.xml"))
        .withPropertyName("shippingManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("STORE_READINESS.md"))
        .withPropertyName("storeReadinessDoc")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // Same reason: PermissionRegistryTest reads DATA_INVENTORY.md from disk, so
    // without this an edit to it alone would leave the test task up to date.
    inputs.file(rootProject.file("DATA_INVENTORY.md"))
        .withPropertyName("dataInventoryDoc")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}


// ── Merged-manifest gate ─────────────────────────────────────────────────────
//
// What ships is the MERGED manifest, not app/src/main/AndroidManifest.xml.
// AndroidX, WorkManager and the profile installer each contribute permissions
// and components the source manifest never mentions, and a dependency upgrade
// can add one without anybody noticing. Comments in one XML file are not proof
// of what a Play reviewer will see (F-184).
//
// This diffs the merged release manifest against release-manifest-allowlist.txt
// in both directions and archives the inventory. An ADDITION is a capability
// nobody reviewed. A REMOVAL is a feature that has gone quiet - which this
// project has produced three times, each one silent: PACKAGE_USAGE_STATS
// missing while six screens queried usage stats, two Quick Settings tiles
// undeclared, and a notification listener marked exported="false" so the system
// could never bind it.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val manifestFile = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val allowlistFile = layout.projectDirectory.file("release-manifest-allowlist.txt")
        val reportFile = layout.buildDirectory.file("reports/merged-manifest/release-inventory.txt")

        tasks.register("verifyReleaseManifest") {
            group = "verification"
            description = "Diffs the merged release manifest against release-manifest-allowlist.txt."

            inputs.file(manifestFile).withPropertyName("mergedManifest")
            inputs.file(allowlistFile).withPropertyName("allowlist")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            outputs.file(reportFile).withPropertyName("inventory")

            doLast {
                val manifest = manifestFile.get().asFile.readText()

                val permissions = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
                    .findAll(manifest).map { it.groupValues[1] }.toSortedSet()

                val exported = Regex("""<(activity|service|receiver|provider)\b([^>]*)>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(manifest)
                    .mapNotNull { match ->
                        val attrs = match.groupValues[2]
                        if (!attrs.contains("android:exported=\"true\"")) return@mapNotNull null
                        val name = Regex("""android:name="([^"]+)"""").find(attrs)?.groupValues?.get(1)
                        name?.let { "${match.groupValues[1]} $it" }
                    }
                    .toSortedSet()

                // Sections are parsed rather than the whole file being read as
                // one list, so a permission cannot silently satisfy a component
                // expectation or the reverse.
                var section = ""
                val allowedPermissions = sortedSetOf<String>()
                val allowedExported = sortedSetOf<String>()
                allowlistFile.asFile.readLines().forEach { raw ->
                    val line = raw.substringBefore('#').trim()
                    when {
                        line.isEmpty() -> Unit
                        line.startsWith("[") -> section = line.trim('[', ']')
                        section == "permissions" -> allowedPermissions.add(line)
                        section == "exported" -> allowedExported.add(line)
                    }
                }

                val report = buildString {
                    appendLine("Merged release manifest inventory")
                    appendLine("Source: " + manifestFile.get().asFile.path)
                    appendLine()
                    appendLine("Permissions (" + permissions.size + "):")
                    permissions.forEach { appendLine("  " + it) }
                    appendLine()
                    appendLine("Exported components (" + exported.size + "):")
                    exported.forEach { appendLine("  " + it) }
                }
                val out = reportFile.get().asFile
                out.parentFile.mkdirs()
                out.writeText(report)

                val problems = buildList {
                    (permissions - allowedPermissions).forEach {
                        add("UNDECLARED PERMISSION in the release build: " + it)
                    }
                    (allowedPermissions - permissions).forEach {
                        add("EXPECTED PERMISSION MISSING from the release build: " + it +
                            " - whatever depends on it is now silently inert")
                    }
                    (exported - allowedExported).forEach {
                        add("UNREVIEWED EXPORTED COMPONENT: " + it)
                    }
                    (allowedExported - exported).forEach {
                        add("EXPECTED EXPORTED COMPONENT MISSING: " + it +
                            " - if the system binds it, it can no longer reach it")
                    }
                }

                if (problems.isNotEmpty()) {
                    throw GradleException(
                        "Merged release manifest does not match release-manifest-allowlist.txt:\n" +
                            problems.joinToString("\n") { "  - " + it } +
                            "\n\nInventory written to " + out.path +
                            "\nIf the change is intended, update the allowlist and say why in the " +
                            "commit message; add a DATA_INVENTORY.md row if it is a permission."
                    )
                }
                logger.lifecycle("Merged release manifest verified: " + permissions.size +
                    " permissions, " + exported.size + " exported components. Inventory: " + out.path)
            }
        }
    }
}

// The release bundle cannot be produced without passing the gate. Running it
// only in CI would mean a local release build skips the one check that looks at
// what actually ships.
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    dependsOn("verifyReleaseManifest")
}

// ─────────────────────────────────────────────────────────────────────────────
// Open-source attribution, shipped from the same file the repository documents.
//
// Apache-2.0 section 4(d) requires attribution notices to travel with derivative
// works, and almost every dependency here is Apache-2.0. A commercial app that
// omits them distributes those libraries outside their licence.
//
// The usual mechanical answer is com.google.android.gms:oss-licenses-plugin, and
// it is the wrong one for this app: Ciyato has no Google Play Services dependency
// at all - ML Kit is the bundled on-device variant - so that plugin would make the
// licences screen the reason the app gains its first GMS dependency, in an app
// whose entire claim is that nothing leaves the device.
//
// The other usual answer is a hand-written screen, which drifts. That objection is
// real: a stale attribution list is the same breach as no list.
//
// So the document IS the screen. THIRD_PARTY_NOTICES.md is the single copy, the
// marked section of it is copied into assets here, and ThirdPartyNoticesTest fails
// the build when a dependency has no entry in it.
val copyThirdPartyNotices = tasks.register("copyThirdPartyNotices") {
    group = "build"
    description = "Extracts the shipped section of THIRD_PARTY_NOTICES.md into assets."

    val source = rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md")
    val target = layout.buildDirectory.file("generated/notices/assets/third_party_notices.md")

    inputs.file(source).withPropertyName("notices").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(target).withPropertyName("asset")

    doLast {
        val text = source.asFile.readText()
        val begin = text.indexOf("<!-- SHIPPED:BEGIN")
        val end = text.indexOf("<!-- SHIPPED:END")

        // Failing loudly here rather than shipping an empty screen. A blank
        // licences page is indistinguishable from no licences page, and this
        // project has already produced three features that were silently inert.
        require(begin >= 0) {
            "THIRD_PARTY_NOTICES.md has no <!-- SHIPPED:BEGIN --> marker, so there is " +
                "nothing to put in the app's licences screen."
        }
        require(end > begin) {
            "THIRD_PARTY_NOTICES.md has no <!-- SHIPPED:END --> marker after the BEGIN " +
                "marker. Without it the internal notes and open questions would ship to users."
        }

        // Past the end of the BEGIN comment itself, so the marker text is not shown.
        val bodyStart = text.indexOf("-->", begin).let { if (it < 0) begin else it + 3 }
        val body = text.substring(bodyStart, end).trim()
        require(body.length > 200) {
            "The shipped section of THIRD_PARTY_NOTICES.md is only ${body.length} characters. " +
                "That is not an attribution list - check the markers."
        }
        require(body.contains("Apache License 2.0")) {
            "The shipped section does not mention Apache License 2.0, which nearly every " +
                "dependency is licensed under. The markers are probably around the wrong section."
        }

        val out = target.get().asFile
        out.parentFile.mkdirs()
        out.writeText(body + "\n")
        logger.lifecycle("Open-source notices: ${body.length} characters -> ${out.name}")
    }
}

// Registering the TASK PROVIDER as the source directory rather than a bare path, so
// Gradle infers the dependency for every consumer instead of only the ones named by
// hand. The hand-written version wired merge*Assets and missed the lint model tasks,
// which read the same source set - so a combined `testDebugUnitTest lintDebug` run
// raced the copy and failed intermittently. An intermittent build failure is worse
// than a consistent one; it gets re-run rather than diagnosed.
android {
    sourceSets.getByName("main").assets.srcDir(copyThirdPartyNotices)
}

// ─────────────────────────────────────────────────────────────────────────────
// The guard tests read files Gradle does not otherwise associate with them, and a
// test that does not run is a test that cannot fail.
//
// Found by proving ThirdPartyNoticesTest could fail: adding an unattributed
// dependency produced no failure on the first attempt, because the test task was
// UP-TO-DATE and skipped. The dependency change did eventually invalidate it, but a
// change to THIRD_PARTY_NOTICES.md alone would not have - so deleting an attribution
// row would leave the guard passing by never executing.
//
// That is the same defect as the golden test that silently recorded nothing earlier
// in this project: a check that looks green because it did not happen. These
// declarations make each document a real input, so editing it re-runs the test that
// guards it.
tasks.withType<Test>().configureEach {
    val guarded = listOf(
        // ThirdPartyNoticesTest - Apache-2.0 4(d) attribution coverage
        rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"),
        // ThirdPartyNoticesTest resolves catalog aliases to coordinates from here
        rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"),
        // PermissionRegistryTest - every capability documented for Play
        rootProject.layout.projectDirectory.file("DATA_INVENTORY.md"),
        // FeatureReachabilityTest - a route with no row, or a row with no route
        rootProject.layout.projectDirectory.file("docs/FEATURE_MATRIX.md"),
        // StoreReadinessTest - the release checklist
        rootProject.layout.projectDirectory.file("STORE_READINESS.md"),
        // NoSuggestionNumbersTest - the archived catalogue those numbers pointed at
        rootProject.layout.projectDirectory.file("docs/archive/2026-07/SUGGESTIONS.md"),
    )
    guarded.forEachIndexed { index, file ->
        if (file.asFile.exists()) {
            inputs.file(file)
                .withPropertyName("guardedDoc$index")
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }

    // ThirdPartyNoticesTest and the reachability tests read these two directly.
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        .withPropertyName("guardedBuildScript")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("src/main/AndroidManifest.xml"))
        .withPropertyName("guardedManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// ─────────────────────────────────────────────────────────────────────────────
// Sideload-safe debug build: ./gradlew assembleDebug -PsideloadSafe
//
// A normal debug APK of Ciyato is refused by the installer on most modern
// phones, and the scanners are not wrong to refuse it. They see a debuggable
// package that wants MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES and
// REQUEST_DELETE_PACKAGES together - every file on the device, a list of every
// app, and the ability to remove them. That is the signature of a malicious
// sideload, and a scanner cannot tell that a launcher has honest reasons for it.
//
// Opt-in rather than default, because a build that quietly drops permissions is
// a test of a different app. The reasoning and the exact cost are in
// app/src/sideload/AndroidManifest.xml.
val sideloadSafe = providers.gradleProperty("sideloadSafe").isPresent
if (sideloadSafe) {
    android {
        // Merged ON TOP of src/main, so this only removes; it never becomes the
        // whole manifest.
        sourceSets.getByName("debug").manifest.srcFile("src/sideload/AndroidManifest.xml")
    }
    logger.lifecycle(
        "Ciyato: sideload-safe debug build - MANAGE_EXTERNAL_STORAGE and " +
            "REQUEST_DELETE_PACKAGES are removed. Whole-device file browsing and the " +
            "uninstall menu entry will not work. Release builds are unaffected."
    )
}
