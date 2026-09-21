package com.example.poster.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks the foreground Activity so code that needs one can reach it.
 *
 * Credential Manager launches its account-picker sheet from the context it is
 * given, and that context has to be an Activity — the application context works
 * on some devices but fails on others (MIUI: "Failed to launch the selector UI.
 * … ensure the 'context' parameter is an Activity-based context"). Held weakly,
 * and cleared when the Activity goes away, so nothing outlives its screen.
 */
class CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    private var current: WeakReference<Activity> = WeakReference(null)

    val activity: Activity? get() = current.get()

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current.get() === activity) current = WeakReference(null)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current.get() === activity) current = WeakReference(null)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
