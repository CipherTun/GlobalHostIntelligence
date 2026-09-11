package io.ciphertun.ghi.app

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val PRODUCTION_BANNER_AD_UNIT_ID =
    "ca-app-pub-3583424243110322/6485592223"

private const val GOOGLE_TEST_BANNER_AD_UNIT_ID =
    "ca-app-pub-3940256099942544/6300978111"

private fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

@Composable
fun GhiAdBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val adUnitId = if (isDebugBuild(context)) {
        GOOGLE_TEST_BANNER_AD_UNIT_ID
    } else {
        PRODUCTION_BANNER_AD_UNIT_ID
    }

    val adView = remember(context, adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
        }
    }

    DisposableEffect(adView) {
        var active = true

        GhiAdManager.whenReady(context) {
            if (active) {
                try {
                    adView.loadAd(
                        AdRequest.Builder().build()
                    )
                } catch (t: Throwable) {
                    android.util.Log.e(
                        "GhiAdBanner",
                        "Banner load failed; continuing without banner",
                        t
                    )
                }
            }
        }

        onDispose {
            active = false
            try {
                adView.destroy()
            } catch (t: Throwable) {
                android.util.Log.w(
                    "GhiAdBanner",
                    "Banner cleanup failed",
                    t
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = {
                adView
            }
        )
    }
}
