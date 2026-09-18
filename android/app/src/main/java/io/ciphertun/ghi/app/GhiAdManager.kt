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

/**
 * GHI Google Mobile Ads controller.
 *
 * Production:
 *   Normal Interstitial:
 *   ca-app-pub-3583424243110322/3774929712
 *
 * Production banner remains in GhiAdBanner.kt.
 *
 * Ads remain isolated from the discovery/core execution path.
 * Any Google Mobile Ads failure must never prevent GHI from starting.
 */
object GhiAdManager {

    private const val TAG = "GhiAdManager"

    private const val PRODUCTION_INTERSTITIAL =
        "ca-app-pub-3583424243110322/3774929712"

    private const val TEST_INTERSTITIAL =
        "ca-app-pub-3940256099942544/1033173712"

    /*
     * Do not show an interstitial immediately when the application starts.
     * The first automatic opportunity becomes available after one minute.
     */
    private const val FIRST_AD_DELAY_MS = 60_000L

    /*
     * Never present automatic interstitials more frequently than once
     * every 60 seconds.
     */
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
    private var lastShownAt = 0L

    @Volatile
    private var automaticAdsAllowedAfter = Long.MAX_VALUE

    private fun isDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun adUnitId(context: Context): String =
        if (isDebug(context)) {
            TEST_INTERSTITIAL
        } else {
            PRODUCTION_INTERSTITIAL
        }

    /**
     * Called after UMP consent has been updated.
     */
    @MainThread
    fun initialize(
        context: Context,
        adsAllowed: Boolean
    ) {
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

    /**
     * Preloads one normal interstitial.
     */
    @MainThread
    fun preload(context: Context) {
        if (!initialized) return
        if (!canRequestAds) return
        if (loading) return
        if (interstitial != null) return

        loading = true

        try {
            InterstitialAd.load(
                context.applicationContext,
                adUnitId(context),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(
                        ad: InterstitialAd
                    ) {
                        loading = false
                        interstitial = ad

                        Log.d(
                            TAG,
                            "Interstitial loaded"
                        )
                    }

                    override fun onAdFailedToLoad(
                        error: LoadAdError
                    ) {
                        loading = false
                        interstitial = null

                        Log.w(
                            TAG,
                            "Interstitial unavailable: ${error.message}"
                        )
                    }
                }
            )
        } catch (t: Throwable) {
            loading = false
            interstitial = null

            Log.e(
                TAG,
                "Interstitial request failed",
                t
            )
        }
    }

    fun isReady(): Boolean =
        initialized && canRequestAds

    fun isInterstitialLoaded(): Boolean =
        interstitial != null

    /**
     * Automatically displays a normal interstitial when:
     *
     * - Google Mobile Ads initialized
     * - consent permits ads
     * - the first one-minute delay has elapsed
     * - the 60-second presentation cooldown has elapsed
     * - a real interstitial has already loaded
     *
     * There is deliberately no custom confirmation dialog.
     */
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

        val now = System.currentTimeMillis()

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
        lastShownAt = now

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdShowedFullScreenContent() {
                    Log.d(
                        TAG,
                        "Interstitial shown"
                    )
                }

                override fun onAdImpression() {
                    Log.d(
                        TAG,
                        "Interstitial impression"
                    )
                }

                override fun onAdClicked() {
                    Log.d(
                        TAG,
                        "Interstitial clicked"
                    )
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(
                        TAG,
                        "Interstitial dismissed"
                    )

                    interstitial = null
                    preload(activity)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(
                    adError: AdError
                ) {
                    Log.w(
                        TAG,
                        "Interstitial failed to show: ${adError.message}"
                    )

                    interstitial = null
                    preload(activity)
                    onFinished()
                }
            }

        return try {
            ad.show(activity)
            true
        } catch (t: Throwable) {
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
