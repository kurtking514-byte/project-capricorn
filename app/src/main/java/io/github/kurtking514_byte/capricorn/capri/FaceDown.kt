package io.github.kurtking514_byte.capricorn.capri

import io.github.kurtking514_byte.capricorn.data.Gesture
import io.github.kurtking514_byte.capricorn.data.Gestures

import android.app.NotificationManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.SystemClock
import kotlin.math.abs

/**
 * 3.10: lay the phone face down = quiet; pick it up = your old sound mode comes back.
 *
 * Watches the accelerometer only while someone asks it to: the home screen while it's showing,
 * and the overlay service while the screen is on (so it also works inside other apps). Nothing
 * runs with the screen off. Face down = screen pointing at the table (gravity on the back) for
 * [DOWN_MS]; picked up = tilted back up for [UP_MS].
 *
 * Quiet = full silent if Capri has "Do Not Disturb access" (chat "quiet"), else vibrate. It only
 * acts if the ringer was on normal, and only restores it if nobody changed it in between. The
 * "muted by Capri" mark is saved, so a restart or a screen-off in between still restores it.
 */
object FaceDown : SensorEventListener {
    private const val PREFS = "face_down"
    private const val DOWN_Z = -8.0f                 // m/s²; flat face down is about -9.8
    private const val UP_Z = -4.0f
    private const val FLAT_XY = 3.0f
    private const val DOWN_MS = 1_000L
    private const val UP_MS = 300L

    /** Set by MainActivity: she glitches out on the home screen. */
    var onFaceDown: (() -> Unit)? = null
    /** Set by MainActivity: [fullSilent] false = it could only do vibrate. */
    var onPickedUp: ((fullSilent: Boolean) -> Unit)? = null

    private val owners = mutableSetOf<String>()
    private var app: Context? = null
    private var registered = false
    private var downSince = 0L
    private var upSince = 0L

    /** [owner] wants face-down watched ([on]) or not. Main thread only. */
    fun watch(context: Context, owner: String, on: Boolean) {
        app = context.applicationContext
        if (on) owners += owner else owners -= owner
        val sm = app?.getSystemService(SensorManager::class.java) ?: return
        val want = owners.isNotEmpty()
        if (want && !registered) {
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            downSince = 0L; upSince = 0L
            registered = sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        } else if (!want && registered) {
            sm.unregisterListener(this)
            registered = false
        }
    }

    /** True if Capri may use full silent (Do Not Disturb access is on). */
    fun fullSilentAllowed(context: Context) =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val ctx = app ?: return
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val now = SystemClock.uptimeMillis()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val muted = prefs.getBoolean("muted", false)
        if (!muted && !Gestures.on(Gesture.FACE_DOWN)) { downSince = 0L; return }   // 3.12: switched off
        if (muted) {
            downSince = 0L
            if (z > UP_Z) {
                if (upSince == 0L) upSince = now
                if (now - upSince >= UP_MS) { upSince = 0L; pickedUp(ctx) }
            } else upSince = 0L
        } else {
            upSince = 0L
            if (z < DOWN_Z && abs(x) < FLAT_XY && abs(y) < FLAT_XY) {
                if (downSince == 0L) downSince = now
                if (now - downSince >= DOWN_MS) { downSince = 0L; faceDown(ctx) }
            } else downSince = 0L
        }
    }

    private fun faceDown(ctx: Context) {
        val audio = ctx.getSystemService(AudioManager::class.java) ?: return
        val before = audio.ringerMode
        if (before != AudioManager.RINGER_MODE_NORMAL) return          // already quiet: leave it alone
        var set = if (fullSilentAllowed(ctx)) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_VIBRATE
        try { audio.ringerMode = set } catch (e: SecurityException) {
            set = AudioManager.RINGER_MODE_VIBRATE
            try { audio.ringerMode = set } catch (e2: Exception) { return }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("muted", true).putInt("before", before).putInt("set", set).apply()
        onFaceDown?.invoke()
    }

    private fun pickedUp(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getInt("before", AudioManager.RINGER_MODE_NORMAL)
        val set = prefs.getInt("set", AudioManager.RINGER_MODE_VIBRATE)
        prefs.edit().putBoolean("muted", false).apply()
        val audio = ctx.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode == set) {                                  // you didn't change it yourself meanwhile
            try { audio.ringerMode = before } catch (e: Exception) { }
        }
        onPickedUp?.invoke(set == AudioManager.RINGER_MODE_SILENT)
    }
}
