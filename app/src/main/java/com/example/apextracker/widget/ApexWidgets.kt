package com.example.apextracker.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll

/**
 * Pushes fresh data to every ApexTracker home-screen widget (Issues #130/#131). Call after a
 * goal or completion change so the widgets don't wait for Android's slow periodic refresh.
 * Fire-and-forget: failures (e.g. no widget placed) are logged, never thrown.
 */
suspend fun refreshApexWidgets(context: Context) {
    try {
        StreakWidget().updateAll(context)
        GoalsWidget().updateAll(context)
    } catch (e: Exception) {
        Log.w("ApexWidgets", "Widget refresh failed", e)
    }
}
