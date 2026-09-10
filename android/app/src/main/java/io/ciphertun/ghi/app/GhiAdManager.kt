package io.ciphertun.ghi.app

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

/**
 * Centralized AdMob controller for GHI.
 *
 * Release builds use the production rewarded-interstitial unit. Debug builds use
 * Google's test unit so production traffic is never generated during development.
 * The controller deliberately never stacks fullscreen ads and enforces a 30-second
 * minimum interval between successful presentations.
 */
object GhiAdManager {
    private const val TAG = "GhiAdManager"
    private const val PRODUCTION_AD_UNIT_ID = "ca-app-pub-3583424243110322/6776449846"
    private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    private const val COOLDOWN_MS = 30_000L

    private var rewardedInterstitial: RewardedInterstitialAd? = null
    private var loading = false
    private var showing = false
    private var lastShownAt = 0L
    private var lastOfferAt = 0L
    private var adFreeUntil = 0L

    private fun isDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun adUnitId(context: Context): String =
        if (isDebug(context)) TEST_AD_UNIT_ID else PRODUCTION_AD_UNIT_ID

    fun preload(context: Context) {
        if (loading || rewardedInterstitial != null) return
        loading = true
        RewardedInterstitialAd.load(
            context.applicationContext,
            adUnitId(context),
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    loading = false
                    rewardedInterstitial = ad
                    Log.d(TAG, "Rewarded interstitial loaded")
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    loading = false
                    rewardedInterstitial = null
                    Log.w(TAG, "Rewarded interstitial failed: ${error.message}")
                }
            }
        )
    }

    fun shouldOffer(now: Long = System.currentTimeMillis()): Boolean {
        if (now < adFreeUntil) return false
        if (showing) return false
        if (rewardedInterstitial == null) return false
        if (now - lastShownAt < COOLDOWN_MS) return false
        if (now - lastOfferAt < COOLDOWN_MS) return false
        lastOfferAt = now
        return true
    }

    fun show(activity: Activity, onFinished: () -> Unit = {}) {
        val ad = rewardedInterstitial ?: run {
            preload(activity)
            return
        }
        if (showing) return

        showing = true
        rewardedInterstitial = null
        lastShownAt = System.currentTimeMillis()

        ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                preload(activity)
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                showing = false
                preload(activity)
                onFinished()
            }
        }

        ad.show(activity) {
            // The configured reward is a silent 60-second ad-free period.
            // Nothing monetary or external is promised to the user.
            adFreeUntil = System.currentTimeMillis() + 60_000L
            Log.d(TAG, "Rewarded interstitial completed; 60s ad-free reward granted")
        }
    }
}
