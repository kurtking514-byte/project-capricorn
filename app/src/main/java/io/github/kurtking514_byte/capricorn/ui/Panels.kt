package io.github.kurtking514_byte.capricorn.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 4.6b: how dark the see-through ink panels behind the clock, speech bubble, chatbox and dock are
 * (designed in preview/panel-lab.html). OFF = outlines only, the wallpaper shows fully.
 * Set on the Gestures screen or with chat "panels" (next step). Saved in prefs "panels".
 */
object Panels {
    val LEVELS = listOf("OFF", "LIGHT", "MEDIUM", "STRONG")
    private val ALPHA = floatArrayOf(0f, 0.40f, 0.65f, 0.85f)
    private const val START = 2                              // MEDIUM

    var level by mutableIntStateOf(START)
        private set

    /** Panel opacity right now (multiply into CapriColors.Panel). */
    val alpha: Float get() = ALPHA[level]

    fun init(context: Context) {
        level = prefs(context).getInt("level", START).coerceIn(0, LEVELS.lastIndex)
    }

    fun set(context: Context, l: Int) {
        level = l.coerceIn(0, LEVELS.lastIndex)
        prefs(context).edit().putInt("level", level).apply()
    }

    /** Chat "panels": the next step, wrapping STRONG → OFF. Returns the new level. */
    fun next(context: Context): Int = (level + 1).mod(LEVELS.size).also { set(context, it) }

    private fun prefs(context: Context) = context.getSharedPreferences("panels", Context.MODE_PRIVATE)
}
