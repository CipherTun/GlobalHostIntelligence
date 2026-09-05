package io.ciphertun.ghi.app

import android.os.Bundle
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            GhiTheme {
                GhiAppChrome()
            }
        }
    }
}
