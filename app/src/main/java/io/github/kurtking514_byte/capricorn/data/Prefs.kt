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

    /** 3.3: apps hidden by dragging them onto Capri ("unhide" in chat brings them all back). */
    fun hidden(): Set<String> = sp.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun setHidden(keys: Set<String>) {
        sp.edit().putStringSet(KEY_HIDDEN, keys).apply()
    }

    /** 4.4: the setup screen opens by itself only once, after the very first boot. */
    fun setupSeen(): Boolean = sp.getBoolean(KEY_SETUP_SEEN, false)

    fun markSetupSeen() {
        sp.edit().putBoolean(KEY_SETUP_SEEN, true).apply()
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
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_SETUP_SEEN = "setup_seen"
        private const val COUNT_PREFIX = "count:"
    }
}
