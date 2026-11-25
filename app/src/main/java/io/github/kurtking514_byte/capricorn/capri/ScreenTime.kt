package io.github.kurtking514_byte.capricorn.capri

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

/**
 * 3.9e: today's screen time for the status scan. Needs "Usage access" (chat "usage" opens the
 * page). Only reads when apps came to the front and left it, never anything inside them.
 * Capri herself (the home screen) doesn't count.
 */
object ScreenTime {
    data class Today(val totalMs: Long, val topApp: String?, val topMs: Long)

    fun allowed(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Today since midnight, or null without Usage access. */
    fun today(context: Context): Today? {
        if (!allowed(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = System.currentTimeMillis()
        val events = try { usm.queryEvents(start, end) } catch (e: Exception) { return null } ?: return null

        val open = HashMap<String, Long>()      // app → when it came to the front
        val seen = HashSet<String>()
        val total = HashMap<String, Long>()
        fun add(pkg: String, ms: Long) { if (ms > 0) total[pkg] = (total[pkg] ?: 0L) + ms }
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                RESUMED -> { open.putIfAbsent(pkg, e.timeStamp); seen += pkg }
                PAUSED -> {
                    val from = open.remove(pkg) ?: if (pkg !in seen) start else null   // already open at midnight
                    if (from != null) add(pkg, e.timeStamp - from)
                    seen += pkg
                }
                SCREEN_OFF -> { open.forEach { (p, from) -> add(p, e.timeStamp - from) }; open.clear() }
            }
        }
        open.forEach { (p, from) -> add(p, end - from) }   // still on screen now
        total.remove(context.packageName)

        val top = total.maxByOrNull { it.value }
        val label = top?.key?.let { pkg ->
            try {
                val pm = context.packageManager
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (e: Exception) { pkg.substringAfterLast('.') }
        }
        return Today(total.values.sum(), label, top?.value ?: 0L)
    }

    /** "3 H 12 M", "45 MIN". */
    fun span(ms: Long): String {
        val min = ms / 60_000L
        return if (min < 60) "$min MIN" else "${min / 60} H ${min % 60} M"
    }

    // UsageEvents.Event types (same numbers on every Android version this app supports)
    private const val RESUMED = 1       // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val PAUSED = 2        // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val SCREEN_OFF = 16   // SCREEN_NON_INTERACTIVE
}
