package io.github.kurtking514_byte.capricorn.capri

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.location.LocationManagerCompat
import io.github.kurtking514_byte.capricorn.overlay.CapriNotifListener

/**
 * 3.9a: what the status scan shows. Everything is read on the spot, nothing is stored, and no
 * permission is needed except the unread count (Notification access, chat "notifs").
 */
data class PhoneStatus(
    val battery: Int,
    val charging: Boolean,
    /** 3.9b: "~6 H 40 M LEFT", "FULL IN 40 MIN", "LEARNING…" … (see [BatteryTracker]). */
    val batteryLeft: String,
    /** Next alarm as the phone shows times ("6:30"), or null if none is set. */
    val alarm: String?,
    /** Minutes until that alarm, or null. */
    val alarmInMin: Long?,
    /** Unread notifications, or null without Notification access. */
    val unread: Int?,
    val wifi: Boolean,
    val bluetooth: Boolean,
    val mobileData: Boolean,
    val airplane: Boolean,
    val silent: Boolean,
    val dnd: Boolean,
    val location: Boolean,
    /** 3.9c: class now / next class from the saved timetable ([Timetable]). */
    val classes: Timetable.Status = Timetable.Status(false, null, null),
) {
    companion object {
        fun read(context: Context): PhoneStatus {
            val cr = context.contentResolver
            fun global(key: String, def: Int = 0) = try { Settings.Global.getInt(cr, key, def) } catch (e: Exception) { def }

            val bm = context.getSystemService(BatteryManager::class.java)
            val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 0
            val charging = bm?.isCharging == true

            val next = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
            val alarm = next?.let { DateFormat.getTimeFormat(context).format(java.util.Date(it.triggerTime)) }
            val inMin = next?.let { (it.triggerTime - System.currentTimeMillis()) / 60_000L }

            val audio = context.getSystemService(AudioManager::class.java)
            val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
                ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            val loc = context.getSystemService(android.location.LocationManager::class.java)

            return PhoneStatus(
                battery = battery,
                charging = charging,
                batteryLeft = BatteryTracker.estimate(context, battery, charging),
                alarm = alarm,
                alarmInMin = inMin,
                unread = CapriNotifListener.unreadCount(context),
                wifi = global(Settings.Global.WIFI_ON) != 0,
                bluetooth = global(Settings.Global.BLUETOOTH_ON) != 0,
                mobileData = global("mobile_data", 1) != 0,
                airplane = global(Settings.Global.AIRPLANE_MODE_ON) != 0,
                silent = audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL,
                dnd = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
                location = loc != null && LocationManagerCompat.isLocationEnabled(loc),
                classes = Timetable.status(context),
            )
        }
    }
}
