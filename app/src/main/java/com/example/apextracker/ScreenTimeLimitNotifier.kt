package com.example.apextracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

private const val SCREEN_LIMIT_CHANNEL_ID = "screen_limit_channel"

/**
 * Posts the "you've hit your daily limit for <app>" notification (Issue #124) on its own channel,
 * separate from reminders so a user can tune or mute screen-time alerts independently. Tapping it
 * opens the Screen Time screen. The notification id is derived from the package name so each app's
 * alert replaces its own rather than stacking.
 */
fun postAppLimitNotification(context: Context, packageName: String, appName: String, limitMinutes: Int) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            SCREEN_LIMIT_CHANNEL_ID,
            context.getString(R.string.screen_limit_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.screen_limit_channel_desc) }
        notificationManager.createNotificationChannel(channel)
    }

    val tapIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_NAVIGATE_TO, "screen_time")
    }
    val contentIntent = PendingIntent.getActivity(
        context,
        packageName.hashCode(),
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, SCREEN_LIMIT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(context.getString(R.string.screen_limit_notif_title, appName))
        .setContentText(context.getString(R.string.screen_limit_notif_text, limitMinutes))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)

    notificationManager.notify(packageName.hashCode(), builder.build())
}
