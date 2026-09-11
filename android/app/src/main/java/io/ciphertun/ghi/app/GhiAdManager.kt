package io.ciphertun.ghi.app

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GHI's isolated advertising controller.
 *
 * Ads are deliberately kept outside the discovery/core execution path. A Google
 * Mobile Ads failure must never prevent the app or embedded Go engine from
 * starting or operating.
 */
object GhiAdManager {
    private const val TAG = "GhiAdManager"
    private const val PRODUCTION_REWARDED_INTERSTITIAL =
        "ca-app-pub-3583424243110322/6776449846"
    private const val TEST_REWARDED_INTERSTITIAL =
        "ca-app-pub-3940256099942544/5354046379"

    private const val COOLDOWN_MS = 60_000L
    private const val REWARD_MS = 60_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _offerVisible = MutableStateFlow(false)
    val offerVisible: StateFlow<Boolean> = _offerVisible.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile private var initialized = false
    @Volatile private var canRequestAds = false
    @Volatile private var loading = false
    @Volatile private var rewardedInterstitial: RewardedInterstitialAd? = null
    @Volatile private var lastShownAt = 0L
    @Volatile private var adFreeUntil = 0L
    @Volatile private var lastOfferAt = 0L

    private fun isDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun adUnitId(context: Context): String =
        if (isDebug(context)) TEST_REWARDED_INTERSTITIAL else PRODUCTION_REWARDED_INTERSTITIAL

    /** Must be called from the main thread after UMP consent has been updated. */
    @MainThread
    fun initialize(context: Context, adsAllowed: Boolean) {
        if (initialized || !adsAllowed) {
            canRequestAds = adsAllowed
            return
        }

        canRequestAds = true
        val appContext = context.applicationContext
        try {
            MobileAds.initialize(appContext) {
                initialized = true
                _ready.value = true
                mainHandler.post { preload(appContext) }
                Log.d(TAG, "Google Mobile Ads initialized")
            }
        } catch (t: Throwable) {
            // Keep ads optional. Never propagate an SDK failure into app startup.
            initialized = false
            canRequestAds = false
            _ready.value = false
            Log.e(TAG, "Ad SDK initialization failed; continuing without ads", t)
        }
    }

    @MainThread
    fun preload(context: Context) {
        if (!initialized || !canRequestAds || loading || rewardedInterstitial != null) return
        loading = true
        try {
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

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loading = false
                        rewardedInterstitial = null
                        Log.w(TAG, "Rewarded interstitial unavailable: ${error.message}")
                    }
                }
            )
        } catch (t: Throwable) {
            loading = false
            rewardedInterstitial = null
            Log.e(TAG, "Rewarded interstitial request failed", t)
        }
    }

    /**
     * Requests the policy-required pre-ad intro. This never forces an ad and
     * respects a 60-second presentation cooldown.
     */
    fun requestOffer(now: Long = System.currentTimeMillis()): Boolean {
        if (!initialized || !canRequestAds) return false
        if (now < adFreeUntil) return false
        if (now - lastShownAt < COOLDOWN_MS) return false
        if (now - lastOfferAt < COOLDOWN_MS) return false
        if (rewardedInterstitial == null) return false
        lastOfferAt = now
        _offerVisible.value = true
        return true
    }

    fun dismissOffer() {
        _offerVisible.value = false
    }

    fun isReady(): Boolean = initialized && canRequestAds

    @MainThread
    fun show(activity: Activity, onFinished: () -> Unit = {}) {
        _offerVisible.value = false
        val ad = rewardedInterstitial ?: run {
            preload(activity)
            onFinished()
            return
        }

        rewardedInterstitial = null
        lastShownAt = System.currentTimeMillis()

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity)
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                preload(activity)
                onFinished()
            }
        }

        try {
            ad.show(activity) {
                // Rewarded interstitial reward: a short ad-free period. No money,
                // premium access, or external entitlement is promised.
                adFreeUntil = System.currentTimeMillis() + REWARD_MS
                Log.d(TAG, "Rewarded interstitial completed; 60s ad-free reward")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Rewarded interstitial presentation failed", t)
            preload(activity)
            onFinished()
        }
    }

    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit = {}) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                if (formError != null) Log.w(TAG, "Privacy options: ${formError.message}")
                onDismissed()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Privacy options unavailable", t)
            onDismissed()
        }
    }
}
