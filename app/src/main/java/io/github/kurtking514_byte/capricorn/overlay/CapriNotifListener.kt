package io.github.kurtking514_byte.capricorn.overlay

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * C5: tells home Capri when a new notification arrives, so she can react to it.
 * Only the sending app's package name is passed on; the notification's text is never read.
 * The user switches this on in Settings > Notification access (chat command "notifs").
 */
class CapriNotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        onNew?.invoke(sbn.packageName)
    }

    companion object {
        /** Set by the home screen while it's visible; called with the sending app's package. */
        @Volatile
        var onNew: ((String) -> Unit)? = null

        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
