package io.github.kurtking514_byte.capricorn.capri

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 5.1: which apps you usually open around this hour (designed in preview/suggest-lab.html).
 * Reads the last 4 weeks of Android's app history (Usage access, the same one screen time uses):
 * only *when* an app came to the front, nothing inside it. Weekdays and weekends are counted apart.
 * An open in this hour counts 1, in the hour before or after 0.5. Launchers (Capri too) never count.
 */
object Suggest {
    data class Pick(val pkg: String, val score: Float)

    private const val DAYS = 28
    private const val DAY_MS = 24 * 60 * 60_000L
    private const val FRESH_MS = 10 * 60_000L        // re-read the history at most every 10 min per hour slot
    private const val MIN_SCORE = 2f                 // about two opens around this hour in 4 weeks

    private var cacheKey = ""
    private var cacheAt = 0L
    private var cache: List<Pick> = emptyList()

    /** Best first. Reads weeks of history, so call it off the main thread. Empty without Usage access. */
    @Synchronized
    fun rank(context: Context): List<Pick> {
        if (!ScreenTime.allowed(context)) return emptyList()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val weekend = weekend(cal)
        val key = "$hour/$weekend"
        if (key == cacheKey && now - cacheAt < FRESH_MS) return cache

        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = try { usm.queryEvents(now - DAYS * DAY_MS, now) } catch (e: Exception) { null } ?: return emptyList()
        val skip = homePackages(context) + context.packageName + "com.android.systemui"

        val scores = HashMap<String, Float>()
        var last: String? = null                     // one open = the app came to the front from something else
        val e = UsageEvents.Event()
        val c = Calendar.getInstance()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType != RESUMED) continue
            val pkg = e.packageName ?: continue
            if (pkg == last) continue
            last = pkg
            if (pkg in skip) continue
            c.timeInMillis = e.timeStamp
            if (weekend(c) != weekend) continue
            val d = Math.floorMod(c.get(Calendar.HOUR_OF_DAY) - hour, 24).let { minOf(it, 24 - it) }
            val w = when (d) { 0 -> 1f; 1 -> 0.5f; else -> continue }
            scores[pkg] = (scores[pkg] ?: 0f) + w
        }
        cache = scores.filterValues { it >= MIN_SCORE }.map { Pick(it.key, it.value) }.sortedByDescending { it.score }
        cacheKey = key
        cacheAt = now
        return cache
    }

    /** The top pick is a clear favorite: well ahead of the next one, and opened often enough. */
    fun clear(picks: List<Pick>): Boolean {
        val top = picks.firstOrNull() ?: return false
        val next = picks.getOrNull(1)?.score ?: 0f
        return top.score >= 3f && top.score > next * 1.4f
    }

    private fun weekend(c: Calendar) = c.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }

    private fun homePackages(context: Context): Set<String> = try {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .mapNotNull { it.activityInfo?.packageName }.toSet()
    } catch (e: Exception) { emptySet() }

    private const val RESUMED = 1                    // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
}
