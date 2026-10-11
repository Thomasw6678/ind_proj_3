package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object FoundItemNotifications {
    const val OPEN_ALERTS = "open_alerts"
    private const val CHANNEL = "found_items"

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun send(context: Context, alert: ItemAlert, book: ReportBook) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "Found item updates", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Updates when a lost report is linked to a found item" })
        }
        if (!allowed(context)) return
        val lost = book.reports.first { it.id == alert.lostReportId }
        val found = book.reports.first { it.id == alert.foundReportId }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(OPEN_ALERTS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "${lost.description.take(120)} was reported found at ${found.location.take(120)}."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Your lost item was reported found")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // Permission can be revoked between checking and posting; the in-app alert remains available.
        try {
            manager.notify(alert.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Reports and in-app alerts do not depend on Android notification permission.
        }
    }
}
