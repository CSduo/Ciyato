package com.ciyato.launcher

import android.app.Application

/**
 * Application class.
 * SECURITY: No analytics SDK, no crash reporter, no remote config initialized here.
 * Everything is local-first.
 */
class CiyatoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Nothing remote. Nothing hidden.
    }
}
