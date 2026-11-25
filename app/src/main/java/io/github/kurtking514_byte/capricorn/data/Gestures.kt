package io.github.kurtking514_byte.capricorn.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/** What a remappable gesture does (3.12). */
enum class GestureJob(val label: String) {
    LOCK("LOCK"), FLASHLIGHT("FLASHLIGHT"), SCAN("STATUS SCAN"), APPS("APP LIST"), WEEK("WEEK GRID"),
}

/**
 * Every gesture (3.12). [job] is set for the three remappable ones: that's their default job.
 * [group] is the heading it's listed under in the gestures screen.
 */
enum class Gesture(val group: String, val title: String, val what: String, val job: GestureJob? = null) {
    HER_DOUBLE_TAP("ON HER", "Double-tap her", "Repeats your last chat command"),
    HER_HOLD("ON HER", "Hold her", "She listens: the chatbox opens"),
    HER_DRAG("ON HER", "Drag an app onto her", "Pin, info, uninstall, hide"),
    HER_SWIPE("ON HER", "Swipe her sideways", "Sends her away (swipe again = back)"),
    LETTER("EMPTY SCREEN", "Draw a letter", "Opens an app starting with it"),
    SWIPE_UP("EMPTY SCREEN", "Swipe up", "App list"),
    DOUBLE_TAP("EMPTY SCREEN", "Double-tap", "", GestureJob.LOCK),
    TWO_DOWN("EMPTY SCREEN", "Two fingers down", "", GestureJob.FLASHLIGHT),
    TWO_HOLD("EMPTY SCREEN", "Two-finger hold", "", GestureJob.SCAN),
    FACE_DOWN("PHONE", "Face down", "Silent until you pick it up"),
}

/**
 * 3.12: which gestures are on and what the remappable ones do. Saved in SharedPreferences
 * "gestures"; read as Compose state, so switches take effect at once. Everything is on by default.
 */
object Gestures {
    private const val PREFS = "gestures"
    private val onMap = mutableStateMapOf<Gesture, Boolean>()
    private val jobMap = mutableStateMapOf<Gesture, GestureJob>()
    private var app: Context? = null

    /** Loads the saved settings once (home screen and overlay service both call it). */
    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (g in Gesture.entries) {
            onMap[g] = p.getBoolean("on_${g.name}", true)
            g.job?.let { def ->
                jobMap[g] = p.getString("job_${g.name}", null)?.let { s -> GestureJob.entries.firstOrNull { it.name == s } } ?: def
            }
        }
    }

    fun on(g: Gesture): Boolean = onMap[g] ?: true

    fun job(g: Gesture): GestureJob? = jobMap[g] ?: g.job

    fun setOn(g: Gesture, value: Boolean) {
        onMap[g] = value
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("on_${g.name}", value)?.apply()
    }

    fun setJob(g: Gesture, value: GestureJob) {
        jobMap[g] = value
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("job_${g.name}", value.name)?.apply()
    }
}
