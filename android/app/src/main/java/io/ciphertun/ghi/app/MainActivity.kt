package io.ciphertun.ghi.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.AndroidEntryPoint
import io.ciphertun.ghi.core.designsystem.GhiTheme
import io.ciphertun.ghi.core.designsystem.isDark

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Volatile private var uiReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val splashStarted = SystemClock.elapsedRealtime()
        // Previously a fixed 3 s hold. Now: leave as soon as the first frame is composed (800 ms cap).
        splash.setKeepOnScreenCondition { !uiReady && SystemClock.elapsedRealtime() - splashStarted < 800L }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prepareAds()
        val session = GhiSession.get(applicationContext)
        setContent {
            val style by session.style.collectAsState()
            val dark = style.isDark()
            // Keep status / navigation bar icon contrast in sync with the in-app theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }
                )
                onDispose { }
            }
            LaunchedEffect(Unit) { uiReady = true }
            GhiTheme(style = style) {
                GhiAppChrome()
            }
        }
    }

    private fun prepareAds() {
        // UMP consent is refreshed at every app launch. If no privacy message is
        // configured for the current user, the callback completes immediately.
        runCatching {
            val consentInformation = UserMessagingPlatform.getConsentInformation(this)
            val params = ConsentRequestParameters.Builder().build()
            consentInformation.requestConsentInfoUpdate(
                this,
                params,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                        GhiAdManager.initialize(this, consentInformation.canRequestAds())
                    }
                },
                {
                    // If the network is unavailable, UMP may still have a usable
                    // previous-session decision. Never block application startup.
                    GhiAdManager.initialize(this, consentInformation.canRequestAds())
                }
            )
        }.onFailure {
            // Ads are optional. A consent/SDK failure must not affect GHI startup.
        }
    }
}
