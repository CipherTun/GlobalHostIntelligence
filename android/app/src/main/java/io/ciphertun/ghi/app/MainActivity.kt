package io.ciphertun.ghi.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.AndroidEntryPoint
import io.ciphertun.ghi.core.designsystem.GhiTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val splashStarted = SystemClock.elapsedRealtime()
        splash.setKeepOnScreenCondition { SystemClock.elapsedRealtime() - splashStarted < 3000L }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prepareAds()
        setContent {
            GhiTheme {
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
                        GhiAdManager.initialize(this, consentInformation.canRequestAds)
                    }
                },
                {
                    // If the network is unavailable, UMP may still have a usable
                    // previous-session decision. Never block application startup.
                    GhiAdManager.initialize(this, consentInformation.canRequestAds)
                }
            )
        }.onFailure {
            // Ads are optional. A consent/SDK failure must not affect GHI startup.
        }
    }
}
