package io.github.kurtking514_byte.capricorn.capri

import io.github.kurtking514_byte.capricorn.data.Gesture
import io.github.kurtking514_byte.capricorn.data.Gestures

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.overlay.CapriNotifListener
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

    /**
     * 3.9d/e, 3.12: a job for MainActivity hidden in the last reply ("gestures" = settings screen) ("classes" = week grid, "askLocation" =
     * ask for rough location, "fetchWeather" = fetch and report). Returned once; she then wears a smirk.
     */
    fun consumeRequest(): String? = mood.takeIf { it in setOf("classes", "askLocation", "fetchWeather", "gestures", "askContacts", "pickContact") }?.also { mood = "smirk" }

    /** 3.9e: what she says about the weather. */
    fun weatherLine(w: Weather.Info): String {
        val t = w.temp
        return when {
            w.code in 95..99 -> pick("$t° and storming. Stay in. With me.", "Thunder out there, $t°. My kind of weather.")
            Weather.wet(w.code) -> pick("$t°, rain. Umbrella or suffer.", "Wet out there. $t°. Don't come back soggy.")
            Weather.describe(w.code) == "SNOW" -> pick("$t° and snowing. Cute.", "Snow. $t°. Layers, genius.")
            w.code == 0 -> pick("$t° and clear. Go be outside for once.", "Clear skies, $t°. No excuses.")
            else -> pick("$t°, gray skies. Moody. Like me.", "$t° and cloudy. Thrilling.")
        }.also { mood = "smirk" }
    }

    /** 3.10: picked up after lying face down; her sound mode is back. */
    fun backOnline(fullSilent: Boolean): String = (
        if (!fullSilent) pick(
            "Back online. I could only do vibrate, though. Say \"quiet\" and I'll go fully dark next time.",
            "Sound's back. That was just vibrate: say \"quiet\" if you want real silence.",
        ) else pick(
            "Back online. Miss me?",
            "And we're live again. Ringer's back.",
            "Rebooted. Sound's on, try not to miss anything.",
        )
    ).also { mood = "smirk" }

    // 3.13: calling. "call mom" -> dialer with the number typed in; several matches -> pick row.
    /** The name to look up again once Contacts access is allowed. */
    var pendingCall: String? = null
        private set
    /** Up to 4 matches for the pick row (MainActivity reads it on "pickContact"). */
    var contactChoices: List<ContactNumber> = emptyList()
        private set

    fun call(name: String): String {
        if (!Contacts.allowed(context)) {
            pendingCall = name
            return done("Let me peek at your contacts first. Tap allow. I only look up names.", "askContacts")
        }
        pendingCall = null
        val found = Contacts.find(context, name)
        return when {
            found.isEmpty() -> pick("No \"$name\" in your contacts. Imaginary friend?", "Nobody called $name. Try their saved name.").also { mood = "shrug" }
            found.size == 1 -> contactPicked(found[0])
            else -> {
                contactChoices = found.take(4)
                pick("${found.size} matches for $name. Pick one.", "Which $name? Choose.").also { mood = "pickContact" }
            }
        }
    }

    fun contactPicked(c: ContactNumber): String =
        if (Contacts.dial(context, c)) done(pick("${c.name}. Number's in, you press call.", "Dialing ${c.name}. Your thumb does the rest."), "smirk")
        else "No phone app to dial with. Tragic.".also { mood = "shrug" }

    fun contactsDenied(): String = pick("No contacts, no calls. Dial it yourself then.", "Fine. Keep your friends secret.").also { mood = "shrug" }

    fun weatherFailed(): String = pick("Couldn't reach the sky. No signal, or no location fix.", "Weather's not answering. Try again outside.").also { mood = "shrug" }
    fun weatherDenied(): String = pick("No location, no weather. Your loss.", "Fine, keep your secrets. And your rain.").also { mood = "shrug" }

    fun greeting(): String = (when (LocalTime.now().hour) {
        in 5..10 -> pick("Rise and shine. Or just rise. Baby steps.", "Morning, sleepyhead. What are we doing first?")
        in 11..17 -> pick("Miss me? Course you did.", "There you are. What do you want to do?")
        in 18..22 -> pick("Evening. Let's make it interesting.", "Back again? Can't stay away, huh.")
        else -> pick("Still up? Bold. What do you need?", "It's late. Perfect time for bad ideas.")
    }).also { mood = "wave" }

    // C2 poke reactions: quick pokes in a row escalate. Allowed poses only (see CapriSkin.ALLOWED).
    private var pokeStreak = 0
    private var lastPoke = 0L

    /**
     * One tap on her. A quick second tap (within [DOUBLE_TAP_MS]) is a double-tap: step 3.1,
     * [again] repeats your last command. A slower second poke still gets the shrug, 3+ = annoyed.
     */
    fun poke(again: () -> String): String {
        val now = android.os.SystemClock.uptimeMillis()
        val double = pokeStreak == 1 && now - lastPoke < DOUBLE_TAP_MS && Gestures.on(Gesture.HER_DOUBLE_TAP)
        pokeStreak = if (now - lastPoke < POKE_WINDOW_MS) pokeStreak + 1 else 1
        lastPoke = now
        if (double) return again()
        return when {
            pokeStreak >= 3 -> pick(                                    // arms crossed, annoyed
                "Okay. Stop. I'm counting, and you're losing.",
                "Poke me one more time and your alarms go off at 4 a.m.",
                "Wow. Persistent. Annoying, but persistent.",
                "That's three. I don't do four.",
            ).also { mood = "annoyed" }
            pokeStreak == 2 -> pick(                                    // shrug
                "Again? What do you want, a medal?",
                "Twice. Cute. Got a point, or just a finger?",
                "Yeah, still here. Still gorgeous.",
            ).also { mood = "shrug" }
            else -> pick(                                               // hand on hip, smirk
                "Hey. Hands off the merchandise.",
                "Bored already? Give me something to do.",
                "Yes, yes, I'm adorable. Now type something.",
                "Careful. I bite.",
            ).also { mood = "sass" }
        }
    }

    // 3.3: drag an app onto her and she grabs it
    fun grabHover(): String = pick("Gimme.", "Hand it over.", "Ooh, for me?").also { mood = "smug" }
    fun grab(label: String): String = done(pick("Got $label. What's the plan?", "$label, huh? Your call, boss."), "smirk")
    fun grabPinned(): String = done(pick("Pinned. Front row.", "Done. Now it's VIP."), "smirk")
    fun grabUnpinned(): String = pick("Off the dock. Demoted.", "Benched. It'll live.").also { mood = "shrug" }
    fun grabDockFull(): String = pick("Dock's full. Unpin something first.", "No room up front. Kick someone out.").also { mood = "annoyed" }
    fun grabInfo(): String = pick("Pulling its file. Nosy, aren't we?", "Fine, let's see its secrets.").also { mood = "smug" }
    fun grabUninstall(): String = pick("Android will ask if you're sure. I'm already sure.", "Say goodbye. Android wants a yes first.").also { mood = "smug" }
    fun grabHidden(): String = done(pick("Gone. Our little secret. Say \"unhide\" to get it back.", "Hidden. Nobody saw nothing. \"unhide\" brings it back."), "smirk")
    fun grabCancel(): String = pick("Fine, keep it.", "Never mind? Typical.").also { mood = "shrug" }
    fun unhidden(n: Int): String = if (n == 0) pick("Nothing's hidden. You're an open book.").also { mood = "shrug" }
        else done(if (n == 1) "One app back from the shadows." else "$n apps back from the shadows.", "smirk")

    // 3.5: you drew a letter on the home screen
    fun letterOpen(letter: Char, label: String): String = done(pick("$letter. $label. Done.", "$letter for $label. Easy."), "smirk")
    fun letterPick(letter: Char, n: Int): String = pick("$letter? You've got $n. Pick one.", "$letter… which one, boss?").also { mood = "skeptical" }
    fun letterPicked(label: String): String = done(pick("$label. Good choice.", "$label it is."), "smirk")
    fun letterNone(letter: Char): String = pick("$letter? Nothing on your phone starts with $letter.", "No $letter apps. Draw something else.").also { mood = "shrug" }
    fun letterFail(): String = pick(
        "Was that a letter? Try again.",
        "Abstract art. Love it. Now draw a letter.",
        "I can't read that. Neat strokes, champ.",
    ).also { mood = "shrug" }

    // 3.6: double-tap the empty home screen to lock
    fun lockHome(): String = done(pick("Lights out.", "Night night, screen.", "Going dark."), "smirk")
    fun lockNeedsOverlay(): String = pick(
        "I can't lock without my overlay powers. Say \"overlay\" and switch me on.",
        "Double-tap all you want. Say \"overlay\" first, then I can lock it.",
    ).also { mood = "annoyed" }

    /** Flashlight on/off (chat "flashlight", or 3.7: two-finger swipe down on the home screen). */
    fun torch(): String = when (QuickActions.toggleTorch(context)) {
        true -> done(pick("Let there be light.", "Lights on. You're welcome."))
        false -> done(pick("And... dark again.", "Torch off. Mysterious."), "smirk")
        null -> "No flashlight on this thing. Tragic."
    }

    /** 3.9a: two-finger hold status scan. Her one line reacts to the numbers. */
    fun scan(s: PhoneStatus): String = when {
        s.battery <= 15 && !s.charging -> done(pick(
            "${s.battery}%? Plug me in before I black out on you.",
            "${s.battery}%. I'm running on fumes here.",
        ), "annoyed")
        s.charging -> done(pick("Charging. Finally, some respect.", "Plugged in. Look at you, being responsible."), "smug")
        // 3.9c classes
        s.classes.now != null && s.classes.now.endsIn <= 5 -> done(pick(
            "${s.classes.now.endsIn} minutes of ${cap(s.classes.now.slot.name)} left. Try to look awake.",
            "${cap(s.classes.now.slot.name)} ends in ${s.classes.now.endsIn}. Hold it together.",
        ), "smirk")
        s.classes.now == null && s.classes.next?.inMin != null && s.classes.next.inMin <= 10 -> done(pick(
            "${cap(s.classes.next.slot.name)} in ${s.classes.next.inMin}. Move.",
            "${cap(s.classes.next.slot.name)} starts in ${s.classes.next.inMin} minutes. Why are you still here?",
        ), "annoyed")
        s.alarmInMin != null && s.alarmInMin in 0..60 -> done(pick(
            "Alarm in ${s.alarmInMin} minutes. Sleep fast.",
            "${s.alarmInMin} minutes till your alarm. Why are you awake?",
        ), "smirk")
        s.weather != null && Weather.fresh(s.weather) && Weather.wet(s.weather.code) ->
            done(pick("It's ${Weather.describe(s.weather.code).lowercase()} out there. Take an umbrella.", "Wet outside. Don't blame me."), "smirk")
        s.screenTime != null && s.screenTime.totalMs >= 5 * 60 * 60_000L ->
            done(pick("${s.screenTime.totalMs / 3_600_000L} hours of screen today. I'm flattered, but blink.", "${ScreenTime.span(s.screenTime.totalMs).lowercase()} on me today. Touch grass."), "smug")
        s.unread == null -> done("Can't count your notifs without access. Say \"notifs\".", "shrug")
        s.unread == 0 -> done(pick("Zero unread. Nobody misses you. Lucky me.", "Inbox clean. Suspicious."), "smirk")
        s.classes.hasClasses && s.classes.now == null && s.classes.next?.inMin == null ->
            done(pick("No more classes. Go touch grass.", "School's done. Try not to waste it."), "smug")
        else -> done(pick("There. Don't make me repeat it.", "Scanned. You're welcome.", "All yours. Try to keep up."), "smirk")
    }

    /** 3.4: swiped back in after you swiped her away. */
    fun back(): String = pick(
        "Back. Try not to look so relieved.",
        "Couldn't last a minute without me, huh?",
        "Did you miss me, or do you need something?",
    ).also { mood = "smirk" }

    /** C4: poked while she was dozing late at night. */
    fun wakeUp(): String = pick(
        "Wha— I wasn't sleeping. I was buffering.",
        "Rude. I was having a great dream and you weren't in it.",
        "It's the middle of the night. What could you possibly need?",
        "Poke me awake again and I'm changing your ringtone.",
    ).also {
        pokeStreak = 0
        mood = "annoyed"
    }

    // C5 phone reactions (allowed poses only).

    /** Charger plugged in. */
    fun charging(): String = pick(
        "Ooh, juice. Now we're talking.",
        "Plugged in. Feeding me finally? Took you long enough.",
        "Charging up. Try not to miss me while I glow.",
    ).also { mood = "smug" }

    /** Battery dropped to the low mark while not charging. */
    fun lowBattery(level: Int): String = pick(
        "$level%. You're starving me here. Charger. Now.",
        "Battery's at $level%. If I pass out, that's on you.",
        "$level% left. Plug me in or say goodbye to all this.",
    ).also { mood = "annoyed" }

    /** A new notification arrived. Only the app's name is used, never the message text. */
    fun notification(app: String?): String = (
        if (app != null) pick(
            "$app wants you. Popular, aren't we?",
            "Ping from $app. Don't keep them waiting. Or do.",
            "$app's knocking. Up top.",
        ) else pick(
            "Someone wants you. Up top.",
            "New ping. Popular, aren't we?",
        )
    ).also { mood = "notif" }

    /** Press and hold on her (3.2): the chatbox opens, she's listening, smug about it. */
    fun hold(): String = pick(
        "Go on. I'm listening.",
        "You've got my attention. Use it well.",
        "Talk to me. Make it interesting.",
        "Fine, I'm all ears. Type.",
    ).also {
        pokeStreak = 0
        mood = "smug"
    }

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
        return understand(raw, apps, launch, showApps).also { if (acted) lastCommand = raw }
    }

    /** 3.1: the last chat command that actually did something, for "do that again". */
    private var lastCommand: String? = null

    /** 3.1 double-tap: runs the last command again; she snaps to a smirk. */
    fun repeatLast(apps: List<AppEntry>, launch: (AppEntry) -> Boolean, showApps: () -> Unit): String {
        val cmd = lastCommand ?: return pick(
            "Again? You haven't asked me for anything yet.",
            "Repeat what, genius? Give me something first.",
        ).also { mood = "smirk" }
        return respond(cmd, apps, launch, showApps).also { mood = "smirk" }
    }

    /** True if the last reply actually did something (C3 red hit). Reading it resets it. */
    private var acted = false
    fun consumeActed(): Boolean = acted.also { acted = false }

    private fun done(line: String, pose: String = "happy"): String {
        acted = true
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

        // replay the boot animation (handled by MainActivity.say)
        if (t == "boot" || t == "reboot") return done("", "reboot")

        // show every pose once, 2 s each (handled by CapriSpriteFigure)
        if (t == "poses" || t == "pose" || t == "show poses") {
            return done(pick("Fine. Full catalogue. Don't blink.", "Runway mode. Try to keep up."), "showcase")
        }

        // 3.13: call a contact (dialer only)
        Regex("^(?:call|phone|dial|ring)\\s+(.+)$").find(t)?.let { m -> return call(m.groupValues[1].trim()) }

        // 3.13: music (media keys go to whatever is playing)
        Regex("^(pause|stop|play|resume|next|skip|previous|prev)(?: (?:the )?(?:music|song|track|it))?$").find(t)?.let { m ->
            val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return "No audio here. Weird phone."
            val word = m.groupValues[1]
            if ((word == "pause" || word == "stop") && !audio.isMusicActive) return "Nothing's playing. Pausing silence? Bold.".also { mood = "shrug" }
            val key = when (word) {
                "pause", "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
                "play", "resume" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                "next", "skip" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                else -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            }
            audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
            audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
            return done(when (key) {
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> pick("Paused. Savor the quiet.", "Music's on hold. You're welcome.")
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> pick("Hit play. Vibe responsibly.", "Music's back.")
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> pick("Skipped. Bad taste, fixed.", "Next. That one was mid.")
                else -> pick("Running it back.", "Again? Fine, rewinding.")
            }, "smirk")
        }

        // 3.13: Wi-Fi / Bluetooth / data. Android won't let apps flip them, so she opens the panel.
        Regex("^(wifi|wi-fi|wi fi|bluetooth|bt|internet|mobile data|data)(?: on| off| settings)?$").find(t)?.let { m ->
            val (intent, line) = when (m.groupValues[1]) {
                "bluetooth", "bt" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS) to "Bluetooth. Android makes you flip it yourself."
                "internet", "mobile data", "data" ->
                    (if (android.os.Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS)) to
                        "Connections, right there. Tap away."
                else -> (if (android.os.Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)) to
                    "Wi-Fi panel's up. Android won't let me flip it, so that's on you."
            }
            if (!start(intent)) return "Can't open that here. Blame the phone.".also { mood = "shrug" }
            return done(line, "smirk")
        }

        // 3.13: ringer: silent / vibrate / sound on
        Regex("^(silent|silence|mute|vibrate|vibration|sound on|sound|unmute|ring|ringer on|loud|normal)(?: mode)?$").find(t)?.let { m ->
            val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return "No audio here. Weird phone."
            val w = m.groupValues[1]
            val want = when (w) {
                "silent", "silence", "mute" -> android.media.AudioManager.RINGER_MODE_SILENT
                "vibrate", "vibration" -> android.media.AudioManager.RINGER_MODE_VIBRATE
                else -> android.media.AudioManager.RINGER_MODE_NORMAL
            }
            val full = FaceDown.fullSilentAllowed(context)
            val target = if (want == android.media.AudioManager.RINGER_MODE_SILENT && !full) android.media.AudioManager.RINGER_MODE_VIBRATE else want
            try { audio.ringerMode = target } catch (e: SecurityException) {
                return "Android blocked that. Say \"quiet\" and let me handle Do Not Disturb.".also { mood = "shrug" }
            }
            return done(when {
                want != target -> "Only vibrate without Do Not Disturb access. Say \"quiet\" to fix that."
                target == android.media.AudioManager.RINGER_MODE_SILENT -> pick("Silent. Not a peep.", "Muted. Peace at last.")
                target == android.media.AudioManager.RINGER_MODE_VIBRATE -> pick("Buzz mode.", "Vibrate. Subtle. Unlike you.")
                else -> pick("Sound's back on.", "Loud and proud again.")
            }, "smirk")
        }

        // 3.10: Do Not Disturb access, so face down can mean full silent
        if (t in setOf("quiet", "dnd", "do not disturb", "silent access")) {
            if (FaceDown.fullSilentAllowed(context)) return done("Already got it. Flip me over and I go dark.", "sass")
            start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return done("Find \"Capri\" and allow it. Then face down means real silence.", "smirk")
        }

        // 3.9e: screen time (Usage access)
        if (t in setOf("usage", "screen time", "screentime", "usage access")) {
            val st = ScreenTime.today(context)
            if (st == null) {
                start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                return done("Find \"Capri\" and switch on usage access. I count minutes, not secrets.", "smirk")
            }
            val top = st.topApp?.let { " Mostly $it. Shameless." } ?: ""
            return "${ScreenTime.span(st.totalMs).lowercase()} on screen today.$top".also { mood = "smug" }
        }

        // 3.9e: weather (rough location, Open-Meteo)
        if (Regex("^(the )?(weather|forecast|temp|temperature)( now| today| outside)?$").matches(t)) {
            if (!Weather.allowed(context)) return done("Let me see roughly where you are and I'll read the sky. Tap allow.", "askLocation")
            val w = Weather.cached(context)
            if (w != null && Weather.fresh(w)) return weatherLine(w)
            return "Checking the sky. Hold on.".also { mood = "fetchWeather" }
        }

        // 3.12: gesture settings
        if (t in setOf("gestures", "gesture", "settings", "gesture settings")) {
            return done(pick("Your controls. Don't break anything.", "Rewire me, then. Carefully."), "gestures")
        }

        // 3.9d: the week grid (opened by MainActivity.say)
        if (Regex("^(my |show |open )?(classes|class|timetable|schedule|week)$").matches(t)) {
            return done(pick("Your week. Try to show up.", "Here's the damage.", "Your schedule. Don't make that face."), "classes")
        }

        // 3.9c: classes ("math mon wed 8 to 9:30", "move math to 1pm", "remove math", "what's today")
        ClassChat.handle(context, text)?.let { r ->
            return if (r.acted) done(r.line, r.mood) else r.line.also { mood = r.mood }
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
        // C5: notification access (so she can react to new notifications)
        if (t in setOf("notifs", "notif", "notifications", "notification")) {
            if (CapriNotifListener.isEnabled(context)) return done("Already watching your pings. Nothing gets past me.", "sass")
            start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return done("Find \"Capri\" and switch me on. I'll only tell you who's pinging, not read your secrets.", "smirk")
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
            return torch()
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
            return done("Say an app's name and I open it. Also: alarm 7:30, timer 10 min, photo, flashlight, screenshot, lock, apps, notifs, unhide, call mom, pause / next, wifi, bluetooth, silent / vibrate / sound on, classes, weather, gestures. Drag an app onto me and I'll handle it. Say \"overlay\" and I follow you into other apps.", "neutral")
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

    private fun cap(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }
}

/** Pokes closer together than this count as one streak. */
private const val POKE_WINDOW_MS = 1_500L

/** A second tap sooner than this after the first is a double-tap ("do that again"). */
private const val DOUBLE_TAP_MS = 350L
