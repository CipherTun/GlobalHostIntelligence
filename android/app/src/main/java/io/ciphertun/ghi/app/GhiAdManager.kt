package io.ciphertun.ghi.app

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GhiAdManager {
    private const val TAG = "GhiAdManager"

    private const val PRODUCTION_INTERSTITIAL =
        "ca-app-pub-3583424243110322/3774929712"

    private const val TEST_INTERSTITIAL =
        "ca-app-pub-3940256099942544/1033173712"

    private const val FIRST_AD_DELAY_MS = 60_000L
    private const val COOLDOWN_MS = 60_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var canRequestAds = false

    @Volatile
    private var loading = false

    @Volatile
    private var interstitial: InterstitialAd? = null

    @Volatile
    private var interstitialLoadedAt = 0L

    @Volatile
    private var lastShownAt = 0L

    @Volatile
    private var automaticAdsAllowedAfter = Long.MAX_VALUE

    @Volatile
    private var fullscreenAdActive = false

    private const val MAX_CACHED_AD_AGE_MS = 50L * 60L * 1000L

    private val RETRY_DELAYS_MS = longArrayOf(
        30_000L,
        60_000L,
        120_000L,
        300_000L
    )

    private var retryAttempt = 0
    private var retryScheduled = false
    private var applicationContext: Context? = null

    private val retryRunnable = Runnable {
        retryScheduled = false
        applicationContext?.let { preload(it) }
    }

    private fun isDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun adUnitId(context: Context): String =
        if (isDebug(context)) {
            TEST_INTERSTITIAL
        } else {
            PRODUCTION_INTERSTITIAL
        }

    @MainThread
    fun initialize(
        context: Context,
        adsAllowed: Boolean
    ) {
        applicationContext = context.applicationContext
        canRequestAds = adsAllowed

        if (!adsAllowed) {
            _ready.value = false
            return
        }

        if (initialized) {
            return
        }

        val appContext = context.applicationContext

        try {
            MobileAds.initialize(appContext) {
                initialized = true
                _ready.value = true

                automaticAdsAllowedAfter =
                    System.currentTimeMillis() + FIRST_AD_DELAY_MS

                mainHandler.post {
                    preload(appContext)
                    GhiExtraAdManager.initialize(appContext)
                }

                Log.d(TAG, "Google Mobile Ads initialized")
            }
        } catch (t: Throwable) {
            initialized = false
            canRequestAds = false
            _ready.value = false

            Log.e(
                TAG,
                "Google Mobile Ads initialization failed",
                t
            )
        }
    }

    @MainThread
    fun preload(context: Context) {
        if (!initialized) return
        if (!canRequestAds) return
        if (loading) return

        val loadedAd = interstitial
        if (loadedAd != null) {
            val age = System.currentTimeMillis() - interstitialLoadedAt
            if (age < MAX_CACHED_AD_AGE_MS) {
                return
            }

            Log.d(
                TAG,
                "Discarding expired interstitial cache: age=${age}ms"
            )
            interstitial = null
            interstitialLoadedAt = 0L
        }

        loading = true

        try {
            InterstitialAd.load(
                context.applicationContext,
                adUnitId(context),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        loading = false
                        interstitial = ad
                        interstitialLoadedAt =
                            System.currentTimeMillis()
                        retryAttempt = 0
                        retryScheduled = false
                        mainHandler.removeCallbacks(retryRunnable)
                        Log.d(TAG, "Interstitial loaded")
                    }

                    override fun onAdFailedToLoad(
                        error: LoadAdError
                    ) {
                        loading = false
                        interstitial = null
                        interstitialLoadedAt = 0L

                        Log.w(
                            TAG,
                            "Interstitial unavailable: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )

                        scheduleRetry()
                    }
                }
            )
        } catch (t: Throwable) {
            loading = false
            interstitial = null
            interstitialLoadedAt = 0L

            Log.e(
                TAG,
                "Interstitial request failed",
                t
            )

            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (!canRequestAds) return
        if (interstitial != null) return
        if (loading) return
        if (retryScheduled) return

        val index = retryAttempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)
        val delay = RETRY_DELAYS_MS[index]
        retryAttempt++
        retryScheduled = true

        Log.d(TAG, "Scheduling interstitial retry in ${delay}ms")
        mainHandler.postDelayed(retryRunnable, delay)
    }

    fun isReady(): Boolean =
        initialized && canRequestAds

    fun isInterstitialLoaded(): Boolean {
        if (interstitial == null) return false

        val age = System.currentTimeMillis() - interstitialLoadedAt

        if (age >= MAX_CACHED_AD_AGE_MS) {
            interstitial = null
            interstitialLoadedAt = 0L
            Log.d(
                TAG,
                "Interstitial cache expired while checking readiness"
            )
            scheduleRetry()
            return false
        }

        return true
    }

    fun isFullscreenAdActive(): Boolean =
        fullscreenAdActive

    fun markExternalFullscreenStart() {
        fullscreenAdActive = true
        lastShownAt = System.currentTimeMillis()
    }

    fun markExternalFullscreenEnd() {
        fullscreenAdActive = false
    }

    @MainThread
    fun showIfReady(
        activity: Activity,
        onFinished: () -> Unit = {}
    ): Boolean {
        if (!initialized || !canRequestAds) {
            preload(activity)
            onFinished()
            return false
        }

        if (fullscreenAdActive) {
            return false
        }

        val now = System.currentTimeMillis()

        if (
            interstitial != null &&
            now - interstitialLoadedAt >= MAX_CACHED_AD_AGE_MS
        ) {
            Log.d(
                TAG,
                "Discarding expired interstitial before show"
            )
            interstitial = null
            interstitialLoadedAt = 0L
            scheduleRetry()
        }

        if (now < automaticAdsAllowedAfter) {
            preload(activity)
            return false
        }

        if (now - lastShownAt < COOLDOWN_MS) {
            preload(activity)
            return false
        }

        val ad = interstitial ?: run {
            preload(activity)
            return false
        }

        interstitial = null
        interstitialLoadedAt = 0L

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdShowedFullScreenContent() {
                    fullscreenAdActive = true
                    lastShownAt = System.currentTimeMillis()
                    Log.d(
                        TAG,
                        "Cooldown started after actual fullscreen show"
                    )
                    Log.d(
                        TAG,
                        "Interstitial shown"
                    )
                }

                override fun onAdImpression() {
                    Log.d(
                        TAG,
                        "Interstitial impression recorded"
                    )
                }

                override fun onAdClicked() {
                    Log.d(
                        TAG,
                        "Interstitial clicked"
                    )
                }

                override fun onAdDismissedFullScreenContent() {
                    fullscreenAdActive = false

                    Log.d(
                        TAG,
                        "Interstitial dismissed"
                    )

                    interstitial = null
                    interstitialLoadedAt = 0L
                    preload(activity)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(
                    adError: AdError
                ) {
                    fullscreenAdActive = false

                    Log.w(
                        TAG,
                        "Interstitial failed to show: ${adError.message}"
                    )

                    interstitial = null
                    interstitialLoadedAt = 0L
                    preload(activity)
                    onFinished()
                }
            }

        return try {
            ad.show(activity)
            true
        } catch (t: Throwable) {
            fullscreenAdActive = false

            Log.e(
                TAG,
                "Interstitial presentation failed",
                t
            )

            interstitial = null
            preload(activity)
            onFinished()

            false
        }
    }

    fun showPrivacyOptions(
        activity: Activity,
        onDismissed: () -> Unit = {}
    ) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                onDismissed()
            }
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "Privacy options unavailable",
                t
            )

            onDismissed()
        }
    }
}
