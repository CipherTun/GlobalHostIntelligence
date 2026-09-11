package io.ciphertun.ghi.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * AdMob is initialized through GhiAdManager so an advertising SDK failure
 * can never prevent the main application from starting.
 */
@HiltAndroidApp
class GhiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GhiAdManager.initialize(this)
    }
}
