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

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }

    companion object {
        /** The running listener (only while Notification access is on). */
        @Volatile
        private var instance: CapriNotifListener? = null

        /**
         * 3.9a: how many notifications are waiting (same filter as [onNotificationPosted]: not ours,
         * not ongoing, not group summaries). Null without Notification access. Only counts; never reads text.
         */
        fun unreadCount(context: Context): Int? {
            if (!isEnabled(context)) return null
            val l = instance ?: return null
            return try {
                l.activeNotifications.orEmpty().count {
                    it.packageName != context.packageName && !it.isOngoing &&
                        it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
                }
            } catch (e: Exception) {
                null
            }
        }

        /** Set by the home screen while it's visible; called with the sending app's package. */
        @Volatile
        var onNew: ((String) -> Unit)? = null

        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
