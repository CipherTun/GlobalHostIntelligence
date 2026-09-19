package io.ciphertun.ghi.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
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
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val TAG = "GhiAdBanner"

private const val PRODUCTION_BANNER_AD_UNIT_ID =
    "ca-app-pub-3583424243110322/6485592223"
private const val GOOGLE_TEST_BANNER_AD_UNIT_ID =
    "ca-app-pub-3940256099942544/6300978111"

private fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

@Composable
fun GhiAdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adUnitId = if (isDebugBuild(context)) GOOGLE_TEST_BANNER_AD_UNIT_ID else PRODUCTION_BANNER_AD_UNIT_ID
    val adView = remember(context, adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, 320))
            this.adUnitId = adUnitId
        }
    }

    DisposableEffect(adView) {
        if (GhiAdManager.isReady()) {
            try {
                adView.adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d(TAG, "Banner loaded")
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "Banner impression recorded")
                    }

                    override fun onAdClicked() {
                        Log.d(TAG, "Banner clicked")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Banner failed: code=${error.code}, domain=${error.domain}, message=${error.message}")
                    }
                }
                adView.loadAd(AdRequest.Builder().build())
            } catch (_: Throwable) {
                // A banner is optional; keep the screen usable if Google Play
                // services or an ad request is unavailable.
            }
        }
        onDispose {
            runCatching { adView.destroy() }
        }
    }

    Box(modifier.fillMaxWidth().wrapContentHeight()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            factory = { adView }
        )
    }
}
