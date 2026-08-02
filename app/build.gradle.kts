plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)                // KSP for Room (#111)
}

android {
    namespace = "com.ciyato.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ciyato.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-beta"

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
        buildConfigField("boolean", "ENABLE_CERT_PINNING", "true")       // #143
    }

    buildTypes {
        release {
            isMinifyEnabled   = true     // R8 (#114)
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug-signed so `assembleRelease` yields an installable, R8-tested
            // APK out of the box. A buyer swaps in their own upload keystore
            // (see docs/SALE_HANDOVER.md) before publishing to Google Play.
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "IS_INTERNAL",         "false")
            buildConfigField("boolean", "ENABLE_CERT_PINNING", "true")
        }
        debug {
            isDebuggable = true
            buildConfigField("boolean", "IS_INTERNAL",         "true")
            buildConfigField("boolean", "ENABLE_CERT_PINNING", "false")  // easier dev
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

    // OkHttp (#143 cert pinning, #142 network log)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

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
