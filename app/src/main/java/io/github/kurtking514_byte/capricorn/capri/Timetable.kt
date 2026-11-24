package io.github.kurtking514_byte.capricorn.capri

import android.content.Context
import java.util.Calendar

/** One class in the week. [day] 0 = Monday … 6 = Sunday; [start] / [end] are minutes after midnight. */
data class ClassSlot(val name: String, val day: Int, val start: Int, val end: Int)

/**
 * 3.9c: the user's class timetable (designed in preview/class-lab.html), saved on the phone only.
 * Filled by telling Capri in chat ([ClassChat]); read by the status scan for CLASS NOW / NEXT CLASS.
 */
object Timetable {
    private const val PREFS = "timetable"
    private const val KEY = "classes"
    val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    fun load(context: Context): MutableList<ClassSlot> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null).orEmpty()
            .split("\n").mapNotNull { line ->
                val p = line.split("|")
                if (p.size != 4) return@mapNotNull null
                ClassSlot(p[0], p[1].toIntOrNull() ?: return@mapNotNull null, p[2].toIntOrNull() ?: return@mapNotNull null, p[3].toIntOrNull() ?: return@mapNotNull null)
            }.toMutableList()

    fun save(context: Context, list: List<ClassSlot>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("\n") { "${it.name.replace("|", " ")}|${it.day}|${it.start}|${it.end}" })
            .apply()
    }

    fun byDay(list: List<ClassSlot>, day: Int) = list.filter { it.day == day }.sortedBy { it.start }

    /** The class happening now and how many minutes until it ends. */
    data class Now(val slot: ClassSlot, val endsIn: Int)

    /** The next class: [inMin] if it's later today, else [whenLabel] ("TOMORROW" or a weekday). */
    data class Next(val slot: ClassSlot, val inMin: Int?, val whenLabel: String?)

    data class Status(val hasClasses: Boolean, val now: Now?, val next: Next?)

    fun status(context: Context, cal: Calendar = Calendar.getInstance()): Status {
        val list = load(context)
        if (list.isEmpty()) return Status(false, null, null)
        val day = today(cal)
        val min = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todays = byDay(list, day)
        val now = todays.firstOrNull { it.start <= min && min < it.end }?.let { Now(it, it.end - min) }
        var next = todays.firstOrNull { it.start > min }?.let { Next(it, it.start - min, null) }
        if (next == null) {
            for (k in 1..7) {
                val first = byDay(list, (day + k) % 7).firstOrNull() ?: continue
                next = Next(first, null, if (k == 1) "TOMORROW" else DAYS[(day + k) % 7])
                break
            }
        }
        return Status(true, now, next)
    }

    /** 0 = Monday … 6 = Sunday. */
    fun today(cal: Calendar = Calendar.getInstance()): Int = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun fmt(min: Int): String = "${min / 60}:${(min % 60).toString().padStart(2, '0')}"
}

/** What [ClassChat] answered: her line, whether it changed something (red hit), and her pose. */
data class ClassReply(val line: String, val acted: Boolean, val mood: String)

/**
 * 3.9c: class commands in chat, same rules as the Class Lab preview:
 * - add: "math mon wed 8 to 9:30", "physics tue thu 10:30-12", "chem fri 1pm" (no end = 1 hour)
 * - "move physics to 1pm" (keeps its length; "to 1-2:30" sets both), "move physics to fri"
 * - "remove english", "remove english on fri", "clear classes"
 * - "what's today", "what's tomorrow", "classes tomorrow"
 * Times 1–6 without am/pm are afternoon (school hours). Days: mon…sun, weekdays, daily, mwf, tth.
 * Messages about alarms or timers are left alone. Returns null if it isn't a class command.
 */
object ClassChat {
    private val DAY_WORDS: Map<String, List<Int>> = mapOf(
        "mon" to listOf(0), "monday" to listOf(0), "tue" to listOf(1), "tues" to listOf(1), "tuesday" to listOf(1),
        "wed" to listOf(2), "weds" to listOf(2), "wednesday" to listOf(2), "thu" to listOf(3), "thur" to listOf(3),
        "thurs" to listOf(3), "thursday" to listOf(3), "fri" to listOf(4), "friday" to listOf(4), "sat" to listOf(5),
        "saturday" to listOf(5), "sun" to listOf(6), "sunday" to listOf(6), "weekdays" to (0..4).toList(),
        "daily" to (0..4).toList(), "everyday" to (0..4).toList(), "mwf" to listOf(0, 2, 4), "tth" to listOf(1, 3), "tuth" to listOf(1, 3),
    )
    private val FILLER = setOf("to", "-", "at", "on", "and", "until", "till", "from", "every", "class", "classes")
    private val NOT_A_CLASS = Regex("\\b(alarm|wake|timer|countdown|remind)\\b")

    private data class Span(val start: Int, val end: Int, val range: Boolean)

    fun handle(context: Context, raw: String): ClassReply? {
        val t = raw.trim().lowercase().replace(Regex("[.,!?]+$"), "").replace(Regex("\\s+"), " ")
        if (t.isEmpty() || NOT_A_CLASS.containsMatchIn(t)) return null
        val list = Timetable.load(context)

        if (Regex("^(clear|wipe|delete|remove)\\s+(all\\s+)?(my\\s+)?(classes|timetable|schedule)$").matches(t)) {
            Timetable.save(context, emptyList())
            return ClassReply("Wiped. Blank week. Enjoy it while it lasts.", true, "smirk")
        }

        Regex("^(?:remove|delete|drop)\\s+(.+?)(?:\\s+on\\s+(.+))?$").find(t)?.let { m ->
            val name = find(list, m.groupValues[1]) ?: return ClassReply("No \"${m.groupValues[1]}\" in your week. Can't drop what doesn't exist.", false, "shrug")
            val ds = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { days(it.split(" ")) }
            list.removeAll { it.name == name && (ds == null || it.day in ds) }
            Timetable.save(context, list)
            val on = ds?.let { " on " + it.joinToString(" + ") { d -> cap(Timetable.DAYS[d]) } }.orEmpty()
            return ClassReply("${cap(name)}$on: gone. Don't get used to skipping.", true, "smirk")
        }

        Regex("^move\\s+(.+?)\\s+to\\s+(.+)$").find(t)?.let { m ->
            val name = find(list, m.groupValues[1]) ?: return ClassReply("Move what? There's no \"${m.groupValues[1]}\".", false, "shrug")
            val ds = days(m.groupValues[2].split(" "))
            val tm = times(m.groupValues[2])
            if (tm == null && ds.isEmpty()) return ClassReply("Move ${cap(name)} to when? Try \"move ${name.lowercase()} to 1pm\".", false, "shrug")
            val hit = list.filter { it.name == name }
            val moved = hit.map { c ->
                if (tm == null) c else c.copy(start = tm.start, end = if (tm.range) tm.end else tm.start + (c.end - c.start))
            }
            list.removeAll { it.name == name }
            if (ds.isNotEmpty()) ds.forEach { d -> list.add(moved.first().copy(day = d)) } else list.addAll(moved)
            Timetable.save(context, list)
            val at = tm?.let { " to " + Timetable.fmt(it.start) }.orEmpty()
            val on = if (ds.isNotEmpty()) " on " + ds.joinToString(" + ") { cap(Timetable.DAYS[it]) } else ""
            return ClassReply("${cap(name)} moved$at$on. You're welcome.", true, "smirk")
        }

        val ask = Regex("^(?:what'?s|whats|what is)(?: on)? (today|tomorrow)$").find(t)
            ?: Regex("^(?:my )?(?:classes|class|schedule)(?: for)? (today|tomorrow)$").find(t)
        if (ask != null) {
            val d = (Timetable.today() + if (ask.groupValues[1] == "tomorrow") 1 else 0) % 7
            val l = Timetable.byDay(list, d)
            val who = cap(Timetable.DAYS[d])
            return if (l.isEmpty()) ClassReply("$who: nothing. Lucky.", false, "smug")
            else ClassReply("$who: " + l.joinToString(", ") { "${cap(it.name)} ${Timetable.fmt(it.start)}" } + ".", false, "smirk")
        }

        // add: "<subject> <days> <time>"
        val words = t.split(" ")
        val ds = days(words)
        val tm = times(t)
        if (ds.isEmpty() || tm == null) return null
        val nameWords = words.takeWhile { w -> w !in DAY_WORDS && !w.first().isDigit() && w !in FILLER }
        val name = nameWords.joinToString(" ").uppercase()
        if (name.isEmpty()) return ClassReply("Which subject? Put the name first: math mon 8 to 9.", false, "shrug")
        val added = ds.map { ClassSlot(name, it, tm.start, tm.end) }
        val clash = added.firstNotNullOfOrNull { a -> list.firstOrNull { it.day == a.day && it.name != name && it.start < a.end && a.start < it.end } }
        list.removeAll { it.name == name && it.day in ds }
        list.addAll(added)
        Timetable.save(context, list)
        val line = "${cap(name)}, ${ds.joinToString(" + ") { cap(Timetable.DAYS[it]) }}, ${Timetable.fmt(tm.start)}–${Timetable.fmt(tm.end)}. Got it." +
            (clash?.let { " That clashes with ${cap(it.name)} on ${cap(Timetable.DAYS[it.day])}. Fix it or suffer." } ?: " Don't be late.")
        return ClassReply(line, true, if (clash != null) "annoyed" else "smirk")
    }

    private fun cap(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }

    private fun find(list: List<ClassSlot>, s: String): String? {
        val n = s.trim().uppercase()
        return list.firstOrNull { it.name == n }?.name
    }

    private fun days(words: List<String>): List<Int> = words.flatMap { DAY_WORDS[it].orEmpty() }.distinct().sorted()

    private fun parseTime(tok: String, hint: String? = null): Int? {
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)?$").find(tok) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3].ifEmpty { hint }
        if (h > 23 || min > 59) return null
        if (ap == "pm" && h < 12) h += 12
        else if (ap == "am" && h == 12) h = 0
        else if (ap == null && h in 1..6) h += 12          // school hours: 1–6 means afternoon
        return h * 60 + min
    }

    private fun times(text: String): Span? {
        val t = text.replace(Regex("\\s*(am|pm)\\b"), "$1")
        Regex("(\\d{1,2}(?::\\d{2})?(?:am|pm)?)\\s*(?:to|-|–|until|till)\\s*(\\d{1,2}(?::\\d{2})?(?:am|pm)?)").find(t)?.let { r ->
            val a = r.groupValues[1]
            val b = r.groupValues[2]
            val endAp = Regex("(am|pm)$").find(b)?.value
            val s = parseTime(a, if (Regex("(am|pm)$").containsMatchIn(a)) null else endAp)
            var e = parseTime(b)
            if (s != null && e != null && e <= s && endAp == null) e += 12 * 60
            return if (s != null && e != null && e > s && e <= 24 * 60) Span(s, e, true) else null
        }
        Regex("(?:^|\\s)(?:at\\s+)?(\\d{1,2}(?::\\d{2})?(?:am|pm)?)(?=\\s|$)").find(t)?.let { r ->
            val s = parseTime(r.groupValues[1]) ?: return null
            return Span(s, minOf(s + 60, 24 * 60), false)
        }
        return null
    }
}
