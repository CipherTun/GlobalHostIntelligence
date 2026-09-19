package io.ciphertun.ghi.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object GhiExtraAdManager {
    private const val TAG = "GhiExtraAdManager"

    private const val PRODUCTION_APP_OPEN =
        "ca-app-pub-3583424243110322/3124206334"
    private const val TEST_APP_OPEN =
        "ca-app-pub-3940256099942544/9257395921"

    private const val PRODUCTION_REWARDED =
        "ca-app-pub-3583424243110322/9196369677"
    private const val TEST_REWARDED =
        "ca-app-pub-3940256099942544/5224354917"

    private const val APP_OPEN_MAX_AGE_MS = 4L * 60L * 60L * 1000L
    private const val REWARDED_MAX_AGE_MS = 50L * 60L * 1000L
    private const val APP_OPEN_COOLDOWN_MS = 15L * 60L * 1000L

    private const val PREFS = "ghi_extra_ads"
    private const val FOREGROUND_COUNT = "foreground_count"
    private const val LAST_APP_OPEN_SHOWN = "last_app_open_shown"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var applicationContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var initialized = false

    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var appOpenLoading = false
    private var appOpenShowing = false
    private var appOpenRetryScheduled = false
    private var appOpenRetryAttempt = 0

    private var rewardedAd: RewardedAd? = null
    private var rewardedLoadedAt = 0L
    private var rewardedLoading = false
    private var rewardedRetryScheduled = false
    private var rewardedRetryAttempt = 0

    private val retryDelaysMs = longArrayOf(60_000L, 120_000L, 300_000L)

    private val appOpenRetryRunnable = Runnable {
        appOpenRetryScheduled = false
        applicationContext?.let(::loadAppOpen)
    }

    private val rewardedRetryRunnable = Runnable {
        rewardedRetryScheduled = false
        applicationContext?.let(::loadRewarded)
    }

    private fun isDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun appOpenUnitId(context: Context): String =
        if (isDebug(context)) TEST_APP_OPEN else PRODUCTION_APP_OPEN

    private fun rewardedUnitId(context: Context): String =
        if (isDebug(context)) TEST_REWARDED else PRODUCTION_REWARDED

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        val appContext = context.applicationContext
        applicationContext = appContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        initialized = true

        loadAppOpen(appContext)
        loadRewarded(appContext)

        Log.d(TAG, "App Open + Rewarded initialized")
    }

    fun onForeground(activity: Activity) {
        if (!initialized) return

        val store = prefs ?: return
        val count = store.getInt(FOREGROUND_COUNT, 0) + 1
        store.edit().putInt(FOREGROUND_COUNT, count).apply()

        if (count < 3) {
            loadAppOpen(activity)
            loadRewarded(activity)
            return
        }

        val lastShown = store.getLong(LAST_APP_OPEN_SHOWN, 0L)
        val now = System.currentTimeMillis()

        if (
            now - lastShown >= APP_OPEN_COOLDOWN_MS &&
            !appOpenShowing &&
            !GhiAdManager.isFullscreenAdActive()
        ) {
            showAppOpenIfReady(activity)
        } else {
            loadAppOpen(activity)
        }

        loadRewarded(activity)
    }

    private fun loadAppOpen(context: Context) {
        if (!initialized || appOpenLoading) return

        val existing = appOpenAd
        if (existing != null) {
            val age = System.currentTimeMillis() - appOpenLoadedAt
            if (age < APP_OPEN_MAX_AGE_MS) return

            appOpenAd = null
            appOpenLoadedAt = 0L
        }

        appOpenLoading = true

        try {
            AppOpenAd.load(
                context.applicationContext,
                appOpenUnitId(context),
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenLoading = false
                        appOpenAd = ad
                        appOpenLoadedAt = System.currentTimeMillis()
                        appOpenRetryAttempt = 0
                        appOpenRetryScheduled = false
                        mainHandler.removeCallbacks(appOpenRetryRunnable)
                        Log.d(TAG, "App open ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        appOpenLoading = false
                        appOpenAd = null
                        appOpenLoadedAt = 0L
                        Log.w(
                            TAG,
                            "App open failed: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )
                        scheduleAppOpenRetry()
                    }
                }
            )
        } catch (t: Throwable) {
            appOpenLoading = false
            appOpenAd = null
            appOpenLoadedAt = 0L
            Log.e(TAG, "App open request failed", t)
            scheduleAppOpenRetry()
        }
    }

    private fun scheduleAppOpenRetry() {
        if (!initialized || appOpenLoading || appOpenAd != null || appOpenRetryScheduled) {
            return
        }

        val index = appOpenRetryAttempt.coerceAtMost(retryDelaysMs.lastIndex)
        val delay = retryDelaysMs[index]
        appOpenRetryAttempt++
        appOpenRetryScheduled = true

        mainHandler.postDelayed(appOpenRetryRunnable, delay)
    }

    private fun isAppOpenAvailable(): Boolean {
        if (appOpenAd == null) return false

        val age = System.currentTimeMillis() - appOpenLoadedAt
        if (age >= APP_OPEN_MAX_AGE_MS) {
            appOpenAd = null
            appOpenLoadedAt = 0L
            return false
        }

        return true
    }

    private fun showAppOpenIfReady(activity: Activity) {
        if (!isAppOpenAvailable()) {
            loadAppOpen(activity)
            return
        }

        if (appOpenShowing || GhiAdManager.isFullscreenAdActive()) return

        val ad = appOpenAd ?: return
        appOpenAd = null
        appOpenLoadedAt = 0L
        appOpenShowing = true

        prefs?.edit()
            ?.putLong(LAST_APP_OPEN_SHOWN, System.currentTimeMillis())
            ?.apply()

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    GhiAdManager.markExternalFullscreenStart()
                    Log.d(TAG, "App open ad shown")
                }

                override fun onAdImpression() {
                    Log.d(TAG, "App open impression recorded")
                }

                override fun onAdClicked() {
                    Log.d(TAG, "App open clicked")
                }

                override fun onAdDismissedFullScreenContent() {
                    GhiAdManager.markExternalFullscreenEnd()
                    appOpenShowing = false
                    Log.d(TAG, "App open dismissed")
                    loadAppOpen(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    GhiAdManager.markExternalFullscreenEnd()
                    appOpenShowing = false
                    Log.w(TAG, "App open failed to show: ${adError.message}")
                    loadAppOpen(activity)
                }
            }

        try {
            ad.show(activity)
        } catch (t: Throwable) {
            GhiAdManager.markExternalFullscreenEnd()
            appOpenShowing = false
            Log.e(TAG, "App open presentation failed", t)
            loadAppOpen(activity)
        }
    }

    private fun loadRewarded(context: Context) {
        if (!initialized || rewardedLoading) return

        val existing = rewardedAd
        if (existing != null) {
            val age = System.currentTimeMillis() - rewardedLoadedAt
            if (age < REWARDED_MAX_AGE_MS) return

            rewardedAd = null
            rewardedLoadedAt = 0L
        }

        rewardedLoading = true

        try {
            RewardedAd.load(
                context.applicationContext,
                rewardedUnitId(context),
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedLoading = false
                        rewardedAd = ad
                        rewardedLoadedAt = System.currentTimeMillis()
                        rewardedRetryAttempt = 0
                        rewardedRetryScheduled = false
                        mainHandler.removeCallbacks(rewardedRetryRunnable)
                        Log.d(TAG, "Rewarded ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedLoading = false
                        rewardedAd = null
                        rewardedLoadedAt = 0L
                        Log.w(
                            TAG,
                            "Rewarded failed: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )
                        scheduleRewardedRetry()
                    }
                }
            )
        } catch (t: Throwable) {
            rewardedLoading = false
            rewardedAd = null
            rewardedLoadedAt = 0L
            Log.e(TAG, "Rewarded request failed", t)
            scheduleRewardedRetry()
        }
    }

    private fun scheduleRewardedRetry() {
        if (!initialized || rewardedLoading || rewardedAd != null || rewardedRetryScheduled) {
            return
        }

        val index = rewardedRetryAttempt.coerceAtMost(retryDelaysMs.lastIndex)
        val delay = retryDelaysMs[index]
        rewardedRetryAttempt++
        rewardedRetryScheduled = true

        mainHandler.postDelayed(rewardedRetryRunnable, delay)
    }

    fun isRewardedReady(): Boolean {
        if (rewardedAd == null) return false

        val age = System.currentTimeMillis() - rewardedLoadedAt
        if (age >= REWARDED_MAX_AGE_MS) {
            rewardedAd = null
            rewardedLoadedAt = 0L
            scheduleRewardedRetry()
            return false
        }

        return true
    }

    fun showRewarded(
        activity: Activity,
        onReward: (RewardItem) -> Unit,
        onUnavailable: () -> Unit = {}
    ): Boolean {
        if (!initialized || GhiAdManager.isFullscreenAdActive()) {
            onUnavailable()
            return false
        }

        if (!isRewardedReady()) {
            loadRewarded(activity)
            onUnavailable()
            return false
        }

        val ad = rewardedAd ?: run {
            onUnavailable()
            return false
        }

        rewardedAd = null
        rewardedLoadedAt = 0L

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    GhiAdManager.markExternalFullscreenStart()
                    Log.d(TAG, "Rewarded ad shown")
                }

                override fun onAdImpression() {
                    Log.d(TAG, "Rewarded impression recorded")
                }

                override fun onAdClicked() {
                    Log.d(TAG, "Rewarded clicked")
                }

                override fun onAdDismissedFullScreenContent() {
                    GhiAdManager.markExternalFullscreenEnd()
                    Log.d(TAG, "Rewarded dismissed")
                    loadRewarded(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    GhiAdManager.markExternalFullscreenEnd()
                    Log.w(TAG, "Rewarded failed to show: ${adError.message}")
                    loadRewarded(activity)
                    onUnavailable()
                }
            }

        return try {
            ad.show(activity) { rewardItem ->
                Log.d(
                    TAG,
                    "Reward earned: amount=${rewardItem.amount}, type=${rewardItem.type}"
                )
                onReward(rewardItem)
            }
            true
        } catch (t: Throwable) {
            GhiAdManager.markExternalFullscreenEnd()
            Log.e(TAG, "Rewarded presentation failed", t)
            loadRewarded(activity)
            onUnavailable()
            false
        }
    }
}
