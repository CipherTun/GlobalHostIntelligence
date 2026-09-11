package io.ciphertun.ghi.app

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized AdMob controller for GHI.
 *
 * Advertising is optional. Any AdMob/Google Play services failure is isolated
 * from the application so GHI can still start and operate normally.
 */
object GhiAdManager {
    private const val TAG = "GhiAdManager"
    private const val PRODUCTION_AD_UNIT_ID =
        "ca-app-pub-3583424243110322/6776449846"
    private const val TEST_AD_UNIT_ID =
        "ca-app-pub-3940256099942544/5354046379"

    private const val COOLDOWN_MS = 30_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val initializing = AtomicBoolean(false)

    @Volatile
    private var initialized = false

    @Volatile
    private var initializationFailed = false

    @Volatile
    private var rewardedInterstitial: RewardedInterstitialAd? = null

    @Volatile
    private var loading = false

    private var showing = false
    private var lastShownAt = 0L
    private var lastOfferAt = 0L
    private var adFreeUntil = 0L

    private val lock = Any()
    private val readyCallbacks = mutableListOf<() -> Unit>()

    private fun isDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun adUnitId(context: Context): String =
        if (isDebug(context)) TEST_AD_UNIT_ID else PRODUCTION_AD_UNIT_ID

    fun initialize(context: Context) {
        if (initialized ||
            initializationFailed ||
            !initializing.compareAndSet(false, true)
        ) {
            return
        }

        val appContext = context.applicationContext

        Thread {
            try {
                MobileAds.initialize(appContext) {
                    initialized = true
                    initializing.set(false)

                    Log.d(TAG, "Google Mobile Ads initialized")

                    val callbacks = synchronized(lock) {
                        val pending = readyCallbacks.toList()
                        readyCallbacks.clear()
                        pending
                    }

                    callbacks.forEach { callback ->
                        mainHandler.post {
                            try {
                                callback()
                            } catch (t: Throwable) {
                                Log.w(TAG, "Ad-ready callback failed", t)
                            }
                        }
                    }

                    mainHandler.post {
                        preload(appContext)
                    }
                }
            } catch (t: Throwable) {
                initializing.set(false)
                initializationFailed = true

                Log.e(
                    TAG,
                    "Google Mobile Ads initialization failed; continuing without ads",
                    t
                )

                synchronized(lock) {
                    readyCallbacks.clear()
                }
            }
        }.start()
    }

    fun whenReady(
        context: Context,
        callback: () -> Unit
    ) {
        if (initialized) {
            mainHandler.post {
                try {
                    callback()
                } catch (t: Throwable) {
                    Log.w(TAG, "Ad-ready callback failed", t)
                }
            }
            return
        }

        synchronized(lock) {
            if (initialized) {
                mainHandler.post {
                    try {
                        callback()
                    } catch (t: Throwable) {
                        Log.w(TAG, "Ad-ready callback failed", t)
                    }
                }
            } else if (!initializationFailed) {
                readyCallbacks += callback
            }
        }

        initialize(context)
    }

    fun isInitialized(): Boolean = initialized

    fun preload(context: Context) {
        if (!initialized ||
            initializationFailed ||
            loading ||
            rewardedInterstitial != null
        ) {
            return
        }

        loading = true

        try {
            RewardedInterstitialAd.load(
                context.applicationContext,
                adUnitId(context),
                AdRequest.Builder().build(),
                object : RewardedInterstitialAdLoadCallback() {

                    override fun onAdLoaded(
                        ad: RewardedInterstitialAd
                    ) {
                        loading = false
                        rewardedInterstitial = ad

                        Log.d(
                            TAG,
                            "Rewarded interstitial loaded"
                        )
                    }

                    override fun onAdFailedToLoad(
                        error: com.google.android.gms.ads.LoadAdError
                    ) {
                        loading = false
                        rewardedInterstitial = null

                        Log.w(
                            TAG,
                            "Rewarded interstitial failed: ${error.message}"
                        )
                    }
                }
            )
        } catch (t: Throwable) {
            loading = false
            rewardedInterstitial = null

            Log.e(
                TAG,
                "Rewarded interstitial request failed; continuing without fullscreen ad",
                t
            )
        }
    }

    fun shouldOffer(
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!initialized || initializationFailed) return false
        if (now < adFreeUntil) return false
        if (showing) return false
        if (rewardedInterstitial == null) return false
        if (now - lastShownAt < COOLDOWN_MS) return false
        if (now - lastOfferAt < COOLDOWN_MS) return false

        lastOfferAt = now
        return true
    }

    fun show(
        activity: Activity,
        onFinished: () -> Unit = {}
    ) {
        if (!initialized ||
            initializationFailed ||
            showing
        ) {
            return
        }

        val ad = rewardedInterstitial ?: run {
            preload(activity)
            return
        }

        showing = true
        rewardedInterstitial = null
        lastShownAt = System.currentTimeMillis()

        ad.fullScreenContentCallback =
            object : com.google.android.gms.ads.FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    showing = false
                    preload(activity)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(
                    adError: com.google.android.gms.ads.AdError
                ) {
                    showing = false
                    preload(activity)
                    onFinished()
                }
            }

        try {
            ad.show(activity) {
                adFreeUntil =
                    System.currentTimeMillis() + 60_000L

                Log.d(
                    TAG,
                    "Rewarded interstitial completed; 60s ad-free reward granted"
                )
            }
        } catch (t: Throwable) {
            showing = false

            Log.e(
                TAG,
                "Rewarded interstitial presentation failed",
                t
            )

            preload(activity)
            onFinished()
        }
    }
}
