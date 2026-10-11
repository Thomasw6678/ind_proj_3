package com.example.myapplication

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test

/** Run on Android 13+ to exercise the granted notification permission path. */
@SdkSuppress(minSdkVersion = 33)
class FoundItemNotificationsTest {


    @Test fun notificationIncludesFoundLocationAndOpensAlerts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val lost = ReportBook().addLost("Test backpack", "Library", null)
        val book = lost.addFound("", "Reception desk", "test-photo", lost.reports.single().id)
        val alert = book.alerts.single()
        val manager = context.getSystemService(NotificationManager::class.java)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            FoundItemNotifications.send(context, alert, book)
            var posted = manager.activeNotifications.firstOrNull { it.id == alert.id.hashCode() }
            val deadline = System.currentTimeMillis() + 5000
            while (posted == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
                posted = manager.activeNotifications.firstOrNull { it.id == alert.id.hashCode() }
            }
            assertNotNull("Android notification should be posted when permission is granted", posted)
            val notification = posted!!.notification
            assertEquals("Your lost item was reported found", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Reception desk"))
            notification.contentIntent.send()
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
            assertNotNull("Tapping a notification should open the application", activity)
            assertTrue(activity!!.intent.getBooleanExtra(FoundItemNotifications.OPEN_ALERTS, false))
            instrumentation.runOnMainSync { activity.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
            manager.cancel(alert.id.hashCode())
        }
    }
}
