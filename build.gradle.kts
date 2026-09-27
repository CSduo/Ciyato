plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Declared here so :app and :macrobenchmark can apply them without a
    // version. AGP is already on the classpath by the time a module is
    // configured, and asking for it again with an explicit version fails with
    // "already on the classpath with an unknown version".
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}
