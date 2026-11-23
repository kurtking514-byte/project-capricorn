package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import io.github.kurtking514_byte.capricorn.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 3.8b: the app list as her hair fanning out (designed in preview/fan-lab.html, user approved
 * 2026-11-23). An outer ring of letters (★ = most used) turns like a dial: move your finger around
 * her head and the ring follows it 1:1; tap a ring letter and it comes to the top; Box arrows in
 * the bottom corners step one letter (hold = keeps going, faster). The apps of the letter at the
 * top fan out on arcs closer to her, each on the tip of a deep-red hair strand. She watches your
 * finger and blinks; tapping an app = she blinks, the app goes crimson and opens. Every letter
 * change glitches the letters for ~125 ms (crimson + green split, bars).
 */
private const val RING_STEP = 11f
private const val GLITCH_MS = 125L
private const val GROW_MS = 300f
private const val OPEN_DELAY_MS = 150L
private val STRAND_FILL = Color(0xFF3A000D)
private val STRAND_DIM = Color(0xFF22000A)
private val EDGE_PURPLE = Color(0xFF37197B)
/** Russo One (SIL Open Font License, see third_party/RussoOne-OFL.txt); user's pick for the fan. */
private val FAN_FONT = FontFamily(Font(R.font.russo_one))

private class FanItem(
    val x: Float, val y: Float, val app: AppEntry? = null, val letter: String? = null,
    val sel: Boolean = false, val off: Int = 0,
)

@Composable
fun HairFan(
    apps: List<AppEntry>,
    frequent: List<AppEntry>,
    skin: CapriSkin?,
    dockKeys: Set<String>,
    bottomPad: Float,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
) {
    val launch by rememberUpdatedState(onLaunch)
    val byLetter = remember(apps) { apps.groupBy { letterOf(it.label) } }
    val letters = remember(apps) { listOf("★") + byLetter.keys.sorted() }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 96)

    var rot by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableFloatStateOf(0f) }
    var now by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    val openAt = remember { android.os.SystemClock.uptimeMillis() }
    var ringIdx by remember { mutableIntStateOf(0) }
    var changedAt by remember { mutableLongStateOf(-100_000L) }
    var blinkAt by remember { mutableLongStateOf(-100_000L) }
    var gazeX by remember { mutableFloatStateOf(0.5f) }
    var gazeAt by remember { mutableLongStateOf(-100_000L) }
    var pickedKey by remember { mutableStateOf<String?>(null) }
    var pickedAt by remember { mutableLongStateOf(-100_000L) }
    var arrowSide by remember { mutableIntStateOf(0) }
    var arrowAt by remember { mutableLongStateOf(-100_000L) }
    var holdSide by remember { mutableIntStateOf(0) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var menuPos by remember { mutableStateOf(IntOffset.Zero) }
    val hold = remember { longArrayOf(0L, 0L) }            // hold start, next repeat
    val items = remember { ArrayList<FanItem>() }           // last drawn layout, for hit tests
    val geo = remember { FloatArray(4) }                   // cx, cy, arrowY, width

    fun step(a: Int) {
        rot = ((rot / RING_STEP).roundToInt() + a) * RING_STEP
        arrowSide = a
        arrowAt = android.os.SystemClock.uptimeMillis()
    }

    // her frames: decode off the main thread so the first draw doesn't stutter
    LaunchedEffect(skin) {
        if (skin == null) return@LaunchedEffect
        withContext(Dispatchers.IO) { listOf("neutral", "idle_look_left", "idle_look_right", "idle_blink_closed").forEach { skin.frame(it) } }
    }

    // one clock: smooth turning, which letter is on top, blinks, held arrows
    LaunchedEffect(letters) {
        var last = android.os.SystemClock.uptimeMillis()
        var nextBlink = last + BLINK_GAP_MIN_MS
        var doubleAt = -1L
        while (true) {
            withFrameMillis { }
            val t = android.os.SystemClock.uptimeMillis()
            val dt = (t - last).coerceAtMost(100)
            last = t
            now = t
            shown += (rot - shown) * min(1f, dt / 60f)
            if (abs(rot - shown) < 0.01f) shown = rot
            val n = letters.size
            val sel = (((shown / RING_STEP).roundToInt() % n) + n) % n
            if (sel != ringIdx) { ringIdx = sel; changedAt = t }
            if (t >= nextBlink) {
                blinkAt = t
                doubleAt = if (Math.random() < BLINK_DOUBLE_CHANCE) t + BLINK_DOUBLE_APART_MS else -1L
                nextBlink = t + BLINK_GAP_MIN_MS + (Math.random() * (BLINK_GAP_MAX_MS - BLINK_GAP_MIN_MS)).toLong()
            }
            if (doubleAt in 1..t) { blinkAt = t; doubleAt = -1L }
            if (holdSide != 0 && t >= hold[1]) {
                step(holdSide)
                hold[1] += max(110f, 260f - (t - hold[0]) * 0.12f).toLong()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(letters, apps) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val p0 = down.position
                        gazeX = p0.x / size.width; gazeAt = android.os.SystemClock.uptimeMillis()
                        // arrows in the bottom corners
                        val aw = 70.dp.toPx()
                        val a = if (p0.y < geo[2] - 40.dp.toPx()) 0 else if (p0.x < aw) -1 else if (p0.x > size.width - aw) 1 else 0
                        if (a != 0) {
                            down.consume()
                            step(a)
                            holdSide = a
                            hold[0] = android.os.SystemClock.uptimeMillis(); hold[1] = hold[0] + 400
                            waitForUpOrCancellation()
                            holdSide = 0
                            return@awaitEachGesture
                        }
                        val cx = geo[0]; val cy = geo[1]
                        fun ang(p: Offset) = (atan2(p.y - cy, p.x - cx) * 180 / PI).toFloat()
                        val ang0 = ang(p0)
                        val rot0 = rot
                        val slop = viewConfiguration.touchSlop
                        var dragging = false
                        var released = false
                        val res = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                gazeX = c.position.x / size.width; gazeAt = android.os.SystemClock.uptimeMillis()
                                if (!c.pressed) return@withTimeoutOrNull true
                                if ((c.position - p0).getDistance() > slop) { dragging = true; return@withTimeoutOrNull false }
                            }
                            @Suppress("UNREACHABLE_CODE") false
                        }
                        fun hit(p: Offset): FanItem? = items.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
                            ?.takeIf { hypot(it.x - p.x, it.y - p.y) < 30.dp.toPx() }
                        if (res == null && !dragging) {                  // long-press: pin / app info menu
                            val it = hit(p0)
                            if (it?.app != null) { menuApp = it.app; menuPos = IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        if (res == true) released = true
                        if (released) {                                  // a tap
                            val it = hit(p0) ?: return@awaitEachGesture
                            if (it.letter != null) rot = ((shown / RING_STEP).roundToInt() + it.off) * RING_STEP
                            else if (it.app != null) {
                                val app = it.app
                                pickedKey = app.key; pickedAt = android.os.SystemClock.uptimeMillis(); blinkAt = pickedAt
                                scope.launch { delay(OPEN_DELAY_MS); launch(app) }
                            }
                            return@awaitEachGesture
                        }
                        // dial: the ring follows the finger's angle around her head
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            gazeX = c.position.x / size.width; gazeAt = android.os.SystemClock.uptimeMillis()
                            var d = ang(c.position) - ang0
                            d = ((d + 540f) % 360f) - 180f
                            rot = rot0 - d
                        }
                        rot = (rot / RING_STEP).roundToInt() * RING_STEP    // rest on the nearest letter
                    }
                },
        ) {
            val t = now
            val W = size.width
            val H = size.height
            val cx = W / 2f
            val cy = H * 0.74f
            val arrowY = H - bottomPad - 30.dp.toPx()
            geo[0] = cx; geo[1] = cy; geo[2] = arrowY; geo[3] = W
            val n = letters.size
            val selRaw = (shown / RING_STEP).roundToInt()
            val sel = ((selRaw % n) + n) % n
            val frac = shown - selRaw * RING_STEP
            val glitching = t - changedAt in 0 until GLITCH_MS

            // ---- layout ----
            items.clear()
            for (i in -9..9) {
                val deg = -90f + i * RING_STEP - frac
                if (deg < -172f || deg > -8f) continue
                val r = W * 0.90f
                val L = letters[(((sel + i) % n) + n) % n]
                items.add(FanItem(cx + r * cosd(deg), cy + r * sind(deg), letter = L, sel = i == 0, off = i))
            }
            val list = if (letters[sel] == "★") frequent else byLetter[letters[sel]].orEmpty()
            val arcs: List<Pair<Float, List<AppEntry>>> = when {
                list.size <= 5 -> listOf(0.40f to list)
                list.size <= 14 -> list.chunked((list.size + 1) / 2).zip(listOf(0.40f, 0.64f)) { l, r -> r to l }
                else -> list.chunked((list.size + 2) / 3).zip(listOf(0.36f, 0.54f, 0.72f)) { l, r -> r to l }
            }
            for ((r, arc) in arcs) {
                val m = arc.size
                val span = min(150f, (if (arcs.size == 3) 26f else 34f) * (m - 1))
                arc.forEachIndexed { k, app ->
                    val deg = if (m == 1) -90f else -90f - span / 2 + span * k / (m - 1)
                    items.add(FanItem(cx + W * r * cosd(deg), cy + W * r * sind(deg), app = app))
                }
            }

            // ---- hair strands from her head to every item ----
            val grow = ((t - openAt) / GROW_MS).coerceIn(0f, 1f).pow(0.6f)
            val root = Offset(cx, cy)
            val edge = 1.2.dp.toPx()
            for (it in items) {
                val tipR = (if (it.letter != null) 10 else 22).dp.toPx()
                val baseR = 4.dp.toPx()
                val dx = it.x - root.x; val dy = it.y - root.y
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val ux = dx / len; val uy = dy / len; val nx = -uy; val ny = ux
                val l = len * grow + (if (it.letter != null) 0f else 18.dp.toPx()) * grow
                val tx = root.x + ux * l; val ty = root.y + uy * l
                val path = poly(
                    root.x + nx * baseR to root.y + ny * baseR, tx + nx * tipR to ty + ny * tipR,
                    tx + ux * 8.dp.toPx() to ty + uy * 8.dp.toPx(), tx - nx * tipR to ty - ny * tipR,
                    root.x - nx * baseR to root.y - ny * baseR,
                )
                drawPath(path, if (it.letter != null && !it.sel) STRAND_DIM else STRAND_FILL)
                drawPath(path, GLITCH_CRIMSON, style = Stroke(edge, join = StrokeJoin.Miter))
            }

            // ---- her head (the real skin; the body runs off the bottom of the screen) ----
            val blinking = t - blinkAt in 0 until BLINK_CLOSED_MS
            val looking = t - gazeAt < GAZE_MS
            val frameName = when {
                blinking -> "idle_blink_closed"
                looking && gazeX < 0.4f -> "idle_look_left"
                looking && gazeX > 0.6f -> "idle_look_right"
                else -> "neutral"
            }
            val bmp = skin?.let { s -> s.frame(s.pick(frameName, "neutral")) }
            if (skin != null && bmp != null) {
                val hf = W * 2.6f
                val wf = hf * bmp.width / bmp.height
                val figureTop = cy - 0.065f * hf
                val top = figureTop - skin.top(frameName) * hf
                drawImage(
                    bmp, dstOffset = IntOffset((cx - wf / 2).roundToInt(), top.roundToInt()),
                    dstSize = IntSize(wf.roundToInt(), hf.roundToInt()),
                )
            } else {
                drawStandIn(cx, cy, W, blinking, if (looking) (gazeX - 0.5f) * 2f else 0f)
            }

            // ---- items ----
            val appAlpha = (((t - openAt) - 150) / 200f).coerceIn(0f, 1f) * ((t - changedAt) / 150f).coerceIn(0f, 1f)
            for (it in items) {
                if (it.letter != null) {
                    val s = (if (it.sel) 24 else 18).dp.toPx()
                    val fs = if (it.sel) 24.sp else 18.sp
                    if (it.sel) {
                        val b = s / 2 + 6.dp.toPx()
                        val o = 3.dp.toPx()
                        drawRect(EDGE_PURPLE, Offset(it.x - b + o, it.y - b + o), Size(2 * b, 2 * b))
                        drawRect(Color.Black, Offset(it.x - b, it.y - b), Size(2 * b, 2 * b))
                        drawRect(GLITCH_CRIMSON, Offset(it.x - b, it.y - b), Size(2 * b, 2 * b), style = Stroke(2.dp.toPx()))
                        if (glitching) glitchText(measurer, it.letter, it.x, it.y, fs, t - changedAt, changedAt.toInt())
                    }
                    centerText(measurer, it.letter, it.x, it.y, fs, if (it.sel) CapriColors.Text else CapriColors.Dim)
                    continue
                }
                val app = it.app ?: continue
                val h = 21.dp.toPx()
                val picked = pickedKey == app.key && t - pickedAt < 400
                val tile = poly(
                    it.x - h to it.y - h, it.x + h to it.y - h, it.x + h to it.y + h - 14.dp.toPx(),
                    it.x + h - 14.dp.toPx() to it.y + h, it.x - h to it.y + h,
                )
                drawPath(tile, if (picked) GLITCH_CRIMSON else Color.Black, alpha = appAlpha)
                drawPath(tile, GLITCH_CRIMSON, alpha = appAlpha, style = Stroke(1.5.dp.toPx()))
                val ic = 30.dp.toPx()
                drawImage(
                    app.icon, dstOffset = IntOffset((it.x - ic / 2).roundToInt(), (it.y - ic / 2).roundToInt()),
                    dstSize = IntSize(ic.roundToInt(), ic.roundToInt()), alpha = appAlpha,
                )
                centerText(measurer, app.label.uppercase(), it.x, it.y + h + 9.dp.toPx(), 10.sp, CapriColors.Text.copy(alpha = appAlpha), maxWidth = 76.dp.toPx())
            }

            // ---- Box arrows, each showing the letter it goes to ----
            for (a in intArrayOf(-1, 1)) {
                val on = (arrowSide == a && t - arrowAt < 120) || holdSide == a
                val x = if (a < 0) 34.dp.toPx() else W - 34.dp.toPx()
                val w = 46.dp.toPx(); val h = 40.dp.toPx(); val c = 10.dp.toPx(); val o = 3.dp.toPx()
                fun box(dx: Float) = poly(
                    x - w / 2 + dx to arrowY - h / 2 + dx, x + w / 2 + dx to arrowY - h / 2 + dx,
                    x + w / 2 + dx to arrowY + h / 2 - c + dx, x + w / 2 - c + dx to arrowY + h / 2 + dx, x - w / 2 + dx to arrowY + h / 2 + dx,
                )
                drawPath(box(o), EDGE_PURPLE)
                drawPath(box(0f), if (on) GLITCH_CRIMSON else Color.Black)
                drawPath(box(0f), GLITCH_CRIMSON, style = Stroke(1.5.dp.toPx()))
                val k = 1.dp.toPx()
                drawPath(poly(x + a * 8 * k to arrowY, x - a * 6 * k to arrowY - 10 * k, x - a * 6 * k to arrowY + 10 * k), if (on) Color.Black else GLITCH_CRIMSON)
                val L = letters[(((sel + a) % n) + n) % n]
                val lx = if (a < 0) x + w / 2 + 16.dp.toPx() else x - w / 2 - 16.dp.toPx()
                if (glitching) glitchText(measurer, L, lx, arrowY, 20.sp, t - changedAt, changedAt.toInt() + a)
                centerText(measurer, L, lx, arrowY, 20.sp, if (on) GLITCH_CRIMSON else CapriColors.Text)
            }

            // ---- top line ----
            val opening = pickedKey != null && t - pickedAt < 900
            val topText = when {
                opening -> "OPENING " + (apps.firstOrNull { it.key == pickedKey }?.label?.uppercase() ?: "")
                letters[sel] == "★" -> "MOST USED"
                else -> "STARTS WITH ${letters[sel]}"
            }
            val ty = 22.dp.toPx()
            if (glitching && !opening) glitchText(measurer, topText, cx, ty, 16.sp, t - changedAt, changedAt.toInt() + 7)
            centerText(measurer, topText, cx, ty, if (opening) 20.sp else 16.sp, if (opening) GLITCH_CRIMSON else CapriColors.Dim)
        }

        // long-press menu
        Box(Modifier.offset { menuPos }) {
            DropdownMenu(expanded = menuApp != null, onDismissRequest = { menuApp = null }) {
                val app = menuApp
                if (app != null) {
                    DropdownMenuItem(
                        text = { Text(if (app.key in dockKeys) "Unpin from dock" else "Pin to dock") },
                        onClick = { menuApp = null; onToggleDock(app) },
                    )
                    DropdownMenuItem(text = { Text("App info") }, onClick = { menuApp = null; onAppInfo(app) })
                }
            }
        }
    }
}

private fun cosd(d: Float) = cos(d * PI / 180).toFloat()
private fun sind(d: Float) = sin(d * PI / 180).toFloat()

private fun poly(vararg p: Pair<Float, Float>) = Path().apply {
    moveTo(p[0].first, p[0].second)
    for (i in 1 until p.size) lineTo(p[i].first, p[i].second)
    close()
}

private fun DrawScope.centerText(
    m: androidx.compose.ui.text.TextMeasurer, s: String, x: Float, y: Float,
    size: androidx.compose.ui.unit.TextUnit, color: Color, maxWidth: Float = Float.MAX_VALUE,
) {
    val r = m.measure(
        s, TextStyle(fontFamily = FAN_FONT, fontSize = size, color = color, letterSpacing = 0.04.sp),
        overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = if (maxWidth < Float.MAX_VALUE) Constraints(maxWidth = maxWidth.roundToInt()) else Constraints(),
    )
    drawText(r, topLeft = Offset(x - r.size.width / 2f, y - r.size.height / 2f))
}

/** The letter glitch: crimson + green copies split sideways (closing up), 3 thin bars through. */
private fun DrawScope.glitchText(
    m: androidx.compose.ui.text.TextMeasurer, s: String, x: Float, y: Float,
    size: androidx.compose.ui.unit.TextUnit, k: Long, seed: Int,
) {
    val left = 1f - k / GLITCH_MS.toFloat()
    val d = (2f + 4f * left).dp.toPx()
    centerText(m, s, x + d, y, size, GLITCH_CRIMSON)
    centerText(m, s, x - d, y, size, GLITCH_GREEN)
    val px = size.toPx()
    val base = seed * 31 + (k / 40).toInt()
    var i = 0
    fun r() = capriJitter(base, i++) * 0.5f + 0.5f
    repeat(3) {
        drawRect(
            if (r() < 0.55f) GLITCH_GREEN else GLITCH_CRIMSON,
            Offset(x - px * (0.4f + r() * 0.8f), y - px * (0.35f - r() * 0.7f)),
            Size(px * (0.6f + r() * 1.2f), (1.5f + r() * 3f).dp.toPx()),
        )
    }
}

/** No skin installed: a plain green panel with two almond eyes (original shape, not her art). */
private fun DrawScope.drawStandIn(cx: Float, cy: Float, w: Float, blinking: Boolean, look: Float) {
    val pw = w * 0.32f
    val ph = pw * 0.56f
    val px = cx - pw / 2
    val py = cy - ph / 2
    val o = 3.dp.toPx()
    drawRect(EDGE_PURPLE, Offset(px + o, py + o), Size(pw, ph))
    drawRect(GLITCH_GREEN, Offset(px, py), Size(pw, ph))
    if (blinking) {
        drawRect(Color(0xFF003D18), Offset(cx - pw * 0.3f, cy - 1.5.dp.toPx()), Size(pw * 0.6f, 3.dp.toPx()))
        return
    }
    for (ex in floatArrayOf(-0.22f, 0.22f)) {
        val x0 = cx + ex * pw + look * pw * 0.07f
        val ew = pw * 0.19f
        val eh = ph * 0.2f
        val p = Path().apply {
            moveTo(x0 - ew, cy)
            cubicTo(x0 - ew * 0.6f, cy - eh * 1.6f, x0 + ew * 0.6f, cy - eh * 1.6f, x0 + ew, cy)
            cubicTo(x0 + ew * 0.6f, cy + eh * 1.6f, x0 - ew * 0.6f, cy + eh * 1.6f, x0 - ew, cy)
            close()
        }
        drawPath(p, Color.Black)
    }
}
