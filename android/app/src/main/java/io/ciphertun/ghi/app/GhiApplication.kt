package io.ciphertun.ghi.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GhiApplication :
    Application(),
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        currentActivity?.let {
            GhiExtraAdManager.onForeground(it)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is MainActivity) {
            currentActivity = activity
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity === currentActivity) {
            currentActivity = null
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
    ) = Unit

    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) {
            currentActivity = activity
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === currentActivity) {
            currentActivity = null
        }
    }
}
