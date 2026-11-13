package io.github.kurtking514_byte.capricorn.data

import android.content.Context

/** Small on-device settings store: dock apps and launch counts. Nothing leaves the phone. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("capri", Context.MODE_PRIVATE)

    fun dock(): List<String> =
        sp.getString(KEY_DOCK, "").orEmpty().split('|').filter { it.isNotBlank() }

    fun setDock(keys: List<String>) {
        sp.edit().putString(KEY_DOCK, keys.joinToString("|")).apply()
    }

    fun launchCounts(): Map<String, Int> =
        sp.all.mapNotNull { (k, v) ->
            if (k.startsWith(COUNT_PREFIX) && v is Int) k.removePrefix(COUNT_PREFIX) to v else null
        }.toMap()

    fun recordLaunch(key: String) {
        val k = COUNT_PREFIX + key
        sp.edit().putInt(k, sp.getInt(k, 0) + 1).apply()
    }

    companion object {
        const val MAX_DOCK = 5
        private const val KEY_DOCK = "dock"
        private const val COUNT_PREFIX = "count:"
    }
}
