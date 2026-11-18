package io.github.kurtking514_byte.capricorn.capri

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.overlay.CapriOverlayService
import io.github.kurtking514_byte.capricorn.overlay.QuickActions
import java.time.LocalTime
import java.util.Locale

/**
 * Capri's offline "brain": a small rule-based command parser plus her lines.
 * No AI and no internet. Her voice is teasing, cocky and playful, never a polite assistant.
 * (All lines are original, written in her style; none are quoted from the show.)
 */
class CapriBrain(private val context: Context) {

    /** The pose/expression that goes with her latest line (see CapriSprite.moodFrame). */
    var mood: String = "neutral"
        private set

    fun greeting(): String = (when (LocalTime.now().hour) {
        in 5..10 -> pick("Rise and shine. Or just rise. Baby steps.", "Morning, sleepyhead. What are we doing first?")
        in 11..17 -> pick("Miss me? Course you did.", "There you are. What do you want to do?")
        in 18..22 -> pick("Evening. Let's make it interesting.", "Back again? Can't stay away, huh.")
        else -> pick("Still up? Bold. What do you need?", "It's late. Perfect time for bad ideas.")
    }).also { mood = "wave" }

    fun poke(): String = pick(
        "Hey! Hands off the merchandise.",
        "Poke me again. See what happens.",
        "Bored already? Give me something to do.",
        "That tickles. Do it again and I bite.",
        "Yes, yes, I'm adorable. Now type something.",
    ).also { mood = "annoyed" }

    /**
     * Understands a typed command, does it, and returns what Capri says.
     * @param launch   opens an app, returns false if it couldn't
     * @param showApps opens the full app list
     */
    fun respond(
        raw: String,
        apps: List<AppEntry>,
        launch: (AppEntry) -> Boolean,
        showApps: () -> Unit,
    ): String {
        mood = "skeptical"
        return understand(raw, apps, launch, showApps)
    }

    private fun done(line: String, pose: String = "happy"): String {
        mood = pose
        return line
    }

    private fun understand(
        raw: String,
        apps: List<AppEntry>,
        launch: (AppEntry) -> Boolean,
        showApps: () -> Unit,
    ): String {
        val text = raw.trim()
        val t = text.lowercase(Locale.ROOT)
        if (t.isEmpty()) return pick("Use your words.", "I'm good, but I'm not a mind reader. Yet.")

        // show every pose once, 2 s each (handled by CapriSpriteFigure)
        if (t == "poses" || t == "pose" || t == "show poses") {
            return done(pick("Fine. Full catalogue. Don't blink.", "Runway mode. Try to keep up."), "showcase")
        }

        // alarms
        if (Regex("\\b(alarm|wake)\\b").containsMatchIn(t)) {
            val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(t)
                ?: return "An alarm for when, genius? Try \"alarm 7:30\"."
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            when (m.groupValues[3]) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            if (hour !in 0..23 || minute !in 0..59) return "That's not a real time. Nice try."
            val ok = start(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            )
            val shown = String.format(Locale.ROOT, "%d:%02d", hour, minute)
            return if (ok) done(pick(
                "Alarm set for $shown. Snooze it and I'll be very disappointed.",
                "$shown. I'll drag you out of bed myself.",
            ), "sass") else "Your clock app is ignoring me. Rude."
        }

        // timers
        if (Regex("\\b(timer|countdown)\\b").containsMatchIn(t)) {
            val m = Regex("(\\d+)\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)?").find(t)
                ?: return "A timer for how long? Try \"timer 10 min\"."
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2]
            val seconds = when {
                unit.startsWith("h") -> n * 3600
                unit.startsWith("s") -> n
                else -> n * 60
            }
            if (seconds !in 1..86_400) return "Pick a sane number."
            val ok = start(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            )
            val label = "$n ${if (unit.isEmpty()) "min" else unit}"
            return if (ok) done(pick("$label on the clock. Tick tock.", "Timer's running. $label. Don't waste it."), "smirk")
            else "Your clock app is ignoring me. Rude."
        }

        // camera
        if (Regex("\\b(photo|picture|camera|selfie|pic)\\b").containsMatchIn(t)) {
            return if (start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) done(pick(
                "Smile. Or don't, I'm not your mom.",
                "Camera's up. Get my good side.",
            )) else "No camera app? What kind of phone is this."
        }

        // overlay + things that need it
        val overlay = CapriOverlayService.instance
        if (Regex("\\b(overlay|mini|follow me|float)\\b").containsMatchIn(t)) {
            if (overlay != null) return done("Already on it. Open any app and I'm right there with you.", "sass")
            start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return done("Tap \"Downloaded apps\", then \"Capri overlay\", and switch me on. Then I follow you everywhere.", "smirk")
        }
        if ("screenshot" in t) {
            if (overlay == null) return "I need my overlay for that. Say \"overlay\" and switch me on first."
            overlay.screenshot()
            return done("Snap. Got it.")
        }
        if (Regex("\\block\\b").containsMatchIn(t)) {
            if (overlay == null) return "I need my overlay for that. Say \"overlay\" and switch me on first."
            overlay.lockScreen()
            return done("Lights out.", "smirk")
        }
        if (Regex("\\b(flashlight|torch|flash)\\b").containsMatchIn(t)) {
            return when (QuickActions.toggleTorch(context)) {
                true -> done("Let there be light.")
                false -> done("And... dark again.", "smirk")
                null -> "No flashlight on this thing. Tragic."
            }
        }

        if (t in setOf("apps", "all apps", "app list", "drawer", "show apps", "list apps")) {
            showApps()
            return done(pick("Here's everything you hoard.", "The whole collection. Pick one."), "smirk")
        }

        if (Regex("^(hi|hey|hello|yo|sup|hiya|heya)\\b").containsMatchIn(t)) {
            return done(pick("Hey yourself.", "Yo. What are we getting into?", "Hi, hi. Skip the small talk, what do you want?"), "wave")
        }
        if ("who are you" in t || "your name" in t) {
            return done("Capri. A whole crowd of minds rolled into one gorgeous package. You're welcome.", "sass")
        }
        if (Regex("\\b(thanks|thank you|thx|ty)\\b").containsMatchIn(t)) {
            return done(pick("I know, I'm the best.", "You owe me one. I'm keeping count."), "sass")
        }
        if (Regex("\\b(help|what can you do)\\b").containsMatchIn(t)) {
            return done("Say an app's name and I open it. Also: alarm 7:30, timer 10 min, photo, flashlight, screenshot, lock, apps. Say \"overlay\" and I follow you into other apps.", "neutral")
        }

        // apps
        val openPrefix = Regex("^(open|launch|start|run|go to|play)\\s+(.+)$").find(t)
        val name = openPrefix?.groupValues?.get(2) ?: t
        val app = findApp(name, apps, strict = openPrefix == null)
        if (app != null) {
            return if (launch(app)) done(pick(
                "${app.label}, coming right up.",
                "Opening ${app.label}. Try to keep up.",
                "${app.label}? Fine. Go.",
            )) else "${app.label} won't open. Not my fault."
        }
        if (openPrefix != null) {
            return "\"${openPrefix.groupValues[2]}\"? Never heard of it. You sure that's on this thing?"
        }

        return done(
            pick(
                "No clue what that means. Use smaller words.",
                "That one's beyond me. For now.",
                "Cute. Try \"help\" if you're lost.",
            ),
            "shrug",
        )
    }

    private fun findApp(name: String, apps: List<AppEntry>, strict: Boolean): AppEntry? {
        val q = name.trim()
        if (q.isEmpty()) return null
        return apps.firstOrNull { it.label.equals(q, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.startsWith(q, ignoreCase = true) }
            ?: if (strict) null else apps.firstOrNull { it.label.contains(q, ignoreCase = true) }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private fun pick(vararg lines: String): String = lines.random()
}
