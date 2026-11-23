package io.github.kurtking514_byte.capricorn.capri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

/**
 * 3.9b: an honest battery time estimate for the status scan.
 *
 * - Charging: Android's own "full in" estimate ([BatteryManager.computeChargeTimeRemaining]).
 * - Not charging: Android doesn't share its estimate with apps, so this measures the real drain.
 *   Every time the level drops (and once when you unplug) the time and level are saved. The
 *   estimate uses the drop over the last few hours up to now: rate = levels lost / time passed,
 *   time left = level now / rate. Until it has seen at least [MIN_DROP]% over [MIN_SPAN_MS], it
 *   says "LEARNING…" instead of guessing. Saved samples survive the app restarting; plugging in
 *   (or the level going up) starts over.
 */
object BatteryTracker {
    private const val PREFS = "battery_tracker"
    private const val KEY = "samples"
    private const val MIN_DROP = 3
    private const val MIN_SPAN_MS = 15 * 60_000L
    private const val WINDOW_MS = 3 * 60 * 60_000L          // look at the last 3 hours
    private const val KEEP_MS = 12 * 60 * 60_000L
    const val LEARNING = "LEARNING…"

    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level < 0 || scale <= 0) return
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            record(context, level * 100 / scale, charging)
        }
    }

    /** Starts listening for battery changes for as long as the app runs (call once, any context). */
    fun start(context: Context) {
        if (started) return
        started = true
        ContextCompat.registerReceiver(
            context.applicationContext, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private class Sample(val t: Long, val level: Int)

    @Synchronized
    private fun record(context: Context, level: Int, charging: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = load(prefs.getString(KEY, null))
        if (charging) {
            if (list.isNotEmpty()) prefs.edit().remove(KEY).apply()
            return
        }
        val now = System.currentTimeMillis()
        val last = list.lastOrNull()
        if (last != null && level > last.level) list.clear()       // it was charged in between
        if (last == null || list.isEmpty() || level < last.level) list.add(Sample(now, level))
        list.removeAll { now - it.t > KEEP_MS }
        prefs.edit().putString(KEY, list.joinToString(";") { "${it.t},${it.level}" }).apply()
    }

    /** "~6 H 40 M LEFT", "~35 MIN LEFT", "FULL IN 40 MIN", "FULL", "CHARGING" or [LEARNING]. */
    fun estimate(context: Context, level: Int, charging: Boolean): String {
        val bm = context.getSystemService(BatteryManager::class.java)
        if (charging) {
            if (level >= 100) return "FULL"
            val ms = bm?.computeChargeTimeRemaining() ?: -1L
            return if (ms > 0) "FULL IN " + span(ms) else "CHARGING"
        }
        record(context, level, false)
        val list = load(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        val now = System.currentTimeMillis()
        val first = list.firstOrNull { now - it.t <= WINDOW_MS } ?: return LEARNING
        val drop = first.level - level
        val spanMs = now - first.t
        if (drop < MIN_DROP || spanMs < MIN_SPAN_MS) return LEARNING
        val msPerLevel = spanMs.toDouble() / drop
        val left = (level * msPerLevel).toLong().coerceAtMost(99 * 60 * 60_000L)
        return "~" + span(left) + " LEFT"
    }

    private fun span(ms: Long): String {
        val min = (ms / 60_000L).coerceAtLeast(1)
        return if (min < 60) "$min MIN" else "${min / 60} H ${min % 60} M"
    }

    private fun load(s: String?): MutableList<Sample> =
        s.orEmpty().split(";").mapNotNull { p ->
            val (t, l) = p.split(",").takeIf { it.size == 2 } ?: return@mapNotNull null
            Sample(t.toLongOrNull() ?: return@mapNotNull null, l.toIntOrNull() ?: return@mapNotNull null)
        }.toMutableList()
}
