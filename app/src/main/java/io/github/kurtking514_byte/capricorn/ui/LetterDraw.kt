package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.kurtking514_byte.capricorn.capri.LetterReader
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 3.5: draw a letter with one finger on the empty home screen (designed in
 * preview/letter-lab.html, user picked "Lightning while drawing"). A jagged crimson bolt
 * follows the finger, re-rolling every 83 ms; on release it flashes thick for 70 ms, then
 * breaks into green + crimson bars for 250 ms. A straight swipe up is still the app list.
 */
object LetterDraw {
    /** The stroke being drawn (root px). */
    val stroke = mutableStateListOf<Offset>()
    var drawing by mutableStateOf(false)

    /** The released stroke, while it flashes and breaks up. */
    var done by mutableStateOf<List<Offset>?>(null)
    var doneOk by mutableStateOf(false)
    var doneAt by mutableLongStateOf(0L)
    var seed by mutableIntStateOf(1)

    internal fun release(pts: List<Offset>, ok: Boolean) {
        drawing = false
        stroke.clear()
        done = pts
        doneOk = ok
        doneAt = android.os.SystemClock.uptimeMillis()
        seed++
    }

    internal fun cancel() {
        drawing = false
        stroke.clear()
    }
}

/** 3.7: how far both fingers must move down. */
private const val TWO_FINGER_DOWN_DP = 80

/** 3.9a: two fingers held this long, moving less than [TWO_FINGER_STILL_DP], = status scan. */
private const val TWO_FINGER_HOLD_MS = 500L
private const val TWO_FINGER_STILL_DP = 16

private const val FLASH_MS = 70L
private const val BREAK_MS = 250L
private val BREAK_BARS = floatArrayOf(0.9f, 0.55f, 0.25f)

/**
 * One-finger strokes on the home screen. A straight swipe up opens the app list ([onDrawer]).
 * Anything else that starts on the empty screen (not on her, not on the chatbox/dock, not taken
 * by a child like an app drag or her sideways swipe) is drawn and read: [onLetter] gets the
 * letter, or null if it wasn't one.
 */
fun Modifier.homeStrokes(
    onDrawer: () -> Unit, onLetter: (Char?) -> Unit, onDoubleTap: () -> Unit, onTwoFingerDown: () -> Unit,
    onTwoFingerHold: () -> Unit,
): Modifier = pointerInput(Unit) {
    // 3.6: two taps on the empty screen, close in time and place = double-tap
    var lastTapAt = 0L
    var lastTap = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val p0 = down.position
        val chatTop = BootDock.slots[BootDock.CHAT]?.top ?: Float.MAX_VALUE
        val canDraw = !down.isConsumed && !AppGrab.herBox.contains(p0) && p0.y < chatTop &&
            AppGrab.held == null && AppGrab.dragging == null
        val slop = viewConfiguration.touchSlop
        val pts = mutableListOf(p0)
        var started = false
        var aborted = false
        // 3.7: every finger's first and latest position, for the two-finger swipe down
        val starts = mutableMapOf<PointerId, Offset>()
        val lasts = mutableMapOf<PointerId, Offset>()
        var twoFinger = false
        var twoAt = 0L
        var held = false                                               // 3.9a: the hold already fired
        var holdMoved = false
        while (true) {
            val ev = if (twoFinger && !held && !holdMoved) {
                val left = TWO_FINGER_HOLD_MS - (android.os.SystemClock.uptimeMillis() - twoAt)
                if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
            } else awaitPointerEvent()
            if (ev == null) {                                          // two fingers held still: status scan
                held = true
                onTwoFingerHold()
                continue
            }
            for (c in ev.changes) if (c.pressed) { starts.getOrPut(c.id) { c.position }; lasts[c.id] = c.position }
            if (ev.changes.count { it.pressed } > 1) {                         // two fingers: not a letter
                if (!twoFinger) { LetterDraw.cancel(); twoAt = android.os.SystemClock.uptimeMillis() }
                twoFinger = true
                aborted = true
            }
            if (twoFinger && !holdMoved && starts.any { (id, p) -> lasts[id]?.let { (it - p).getDistance() > TWO_FINGER_STILL_DP.dp.toPx() } == true }) holdMoved = true
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            if (ch.isConsumed) aborted = true                                 // a child took it
            if (aborted) continue
            val last = pts.last()
            if (hypot(ch.position.x - last.x, ch.position.y - last.y) > 3.dp.toPx()) pts.add(ch.position)
            if (!started && (ch.position - p0).getDistance() > slop) started = true
            if (started && canDraw) {
                ch.consume()
                if (!LetterDraw.drawing) { LetterDraw.stroke.clear(); LetterDraw.drawing = true }
                LetterDraw.stroke.add(ch.position)
            }
        }
        // 3.7: two fingers that both moved down 80 dp+, mostly straight = flashlight
        if (twoFinger && !held && !down.isConsumed && starts.size >= 2) {
            val ids = starts.keys.take(2)
            val dys = ids.map { lasts.getValue(it).y - starts.getValue(it).y }
            val dxs = ids.map { abs(lasts.getValue(it).x - starts.getValue(it).x) }
            if (dys.all { it > TWO_FINGER_DOWN_DP.dp.toPx() } && dxs.sum() < dys.sum() * 0.6f) onTwoFingerDown()
            return@awaitEachGesture
        }
        if (held) return@awaitEachGesture
        if (!started && !aborted && canDraw) {                               // a tap on the empty screen
            val at = android.os.SystemClock.uptimeMillis()
            if (at - lastTapAt < viewConfiguration.doubleTapTimeoutMillis && (p0 - lastTap).getDistance() < 48.dp.toPx()) {
                lastTapAt = 0L
                onDoubleTap()
            } else {
                lastTapAt = at
                lastTap = p0
            }
        }
        if (!started || aborted) { LetterDraw.cancel(); return@awaitEachGesture }

        // a straight swipe up = the app list (as before), wherever it starts
        val end = pts.last()
        val dx = end.x - p0.x
        val dy = end.y - p0.y
        var length = 0f
        for (i in 1 until pts.size) length += (pts[i] - pts[i - 1]).getDistance()
        if (dy < -100.dp.toPx() && abs(dx) < abs(dy) * 0.4f && length < abs(dy) * 1.25f) {
            LetterDraw.cancel()
            onDrawer()
            return@awaitEachGesture
        }
        if (!canDraw) { LetterDraw.cancel(); return@awaitEachGesture }

        // too small = a tap or a twitch, not a letter
        val w = pts.maxOf { it.x } - pts.minOf { it.x }
        val h = pts.maxOf { it.y } - pts.minOf { it.y }
        if (maxOf(w, h) < 28.dp.toPx()) { LetterDraw.cancel(); return@awaitEachGesture }

        val letter = LetterReader.read(pts.map { it.x to it.y })
        LetterDraw.release(pts, letter != null)
        onLetter(letter)
    }
}

/** Draws the lightning trail while drawing, and its flash + break-up after release. */
@Composable
fun LetterTrail() {
    var now by remember { mutableLongStateOf(0L) }
    val active = LetterDraw.drawing || LetterDraw.done != null
    LaunchedEffect(active) {
        while (active) {
            withFrameMillis { }
            now = android.os.SystemClock.uptimeMillis()
            val d = LetterDraw.done
            if (d != null && !LetterDraw.drawing && now - LetterDraw.doneAt > FLASH_MS + BREAK_MS) LetterDraw.done = null
        }
    }
    if (!active) return
    Canvas(Modifier.fillMaxSize()) {
        val t = now
        if (LetterDraw.drawing) {
            jagged(sparse(LetterDraw.stroke), ((t / 83).toInt()) * 31 + 7, 7.dp.toPx(), 3.5.dp.toPx())
        }
        val pts = LetterDraw.done ?: return@Canvas
        val k = t - LetterDraw.doneAt
        val seed = LetterDraw.seed
        val ok = LetterDraw.doneOk
        if (k < FLASH_MS) {
            jagged(sparse(pts), seed * 13 + (k / 83).toInt(), (if (ok) 9 else 5).dp.toPx(), (if (ok) 7 else 4).dp.toPx())
        } else if (k < FLASH_MS + BREAK_MS) {
            val step = ((k - FLASH_MS) / 83).toInt()
            if (step == 0) {          // the bolt falls apart: about half its pieces left
                val half = sparse(pts).filterIndexed { i, _ -> capriJitter(seed, 300 + i) > -0.1f }
                jagged(half, seed + 3, 9.dp.toPx(), 4.dp.toPx())
            }
            breakBars(pts, step, BREAK_BARS.getOrElse(step) { 0f }, seed)
        }
    }
}

/** Every 3rd point, so the bolt has visible zigzags instead of fuzz. */
private fun sparse(pts: List<Offset>): List<Offset> =
    pts.filterIndexed { i, _ -> i == 0 || i == pts.lastIndex || i % 3 == 0 }

/** A crimson bolt through [pts]: each segment kinks sideways by up to [amp] px at its middle. */
private fun DrawScope.jagged(pts: List<Offset>, seed: Int, amp: Float, width: Float) {
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val l = (b - a).getDistance().coerceAtLeast(1f)
            val nx = -(b.y - a.y) / l
            val ny = (b.x - a.x) / l
            val o = capriJitter(seed, i) * amp
            lineTo((a.x + b.x) / 2f + nx * o, (a.y + b.y) / 2f + ny * o)
            lineTo(b.x, b.y)
        }
    }
    drawPath(path, CapriColors.Pink, style = Stroke(width = width, join = StrokeJoin.Miter, cap = StrokeCap.Butt))
}

/** The show's green + crimson bars over where the letter was. */
private fun DrawScope.breakBars(pts: List<Offset>, step: Int, intensity: Float, seed: Int) {
    if (intensity <= 0f) return
    val pad = 10.dp.toPx()
    val left = pts.minOf { it.x } - pad
    val top = pts.minOf { it.y } - pad
    val w = pts.maxOf { it.x } - pts.minOf { it.x } + 2 * pad
    val h = pts.maxOf { it.y } - pts.minOf { it.y } + 2 * pad
    val base = seed * 7919 + step * 104729
    var k = 0
    fun r() = capriJitter(base, k++) * 0.5f + 0.5f
    val n = if (r() < 0.22f) 1 else (3 + 9 * intensity * r()).roundToInt()
    repeat(n) {
        val y = top + r() * h
        val bh = size.height * (0.003f + 0.018f * r() * r())
        val dir = if (r() < 0.5f) -1 else 1
        val x0 = left + r() * w
        val len = size.width * (0.05f + 0.25f * r())
        drawRect(
            if (r() < 0.55f) GLITCH_GREEN else GLITCH_CRIMSON,
            topLeft = Offset(if (dir > 0) x0 else x0 - len, y), size = Size(len, bh),
        )
    }
}
