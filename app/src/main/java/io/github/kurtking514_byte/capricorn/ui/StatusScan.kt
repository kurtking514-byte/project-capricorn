package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kurtking514_byte.capricorn.capri.PhoneStatus
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlin.math.roundToInt

/**
 * 3.9a: hold two fingers on the home screen = status scan, "Readout" style (designed in
 * preview/scan-lab.html). After the C3 red hit a black panel (crimson edge, purple offset) snaps in
 * under the clock: a status icon strip on top, then BATTERY / ALARM / NOTIFS lines, each struck by a
 * bolt from the top and split green/crimson for one 83 ms step as it snaps in. Held 3.5 s, then it
 * breaks into 2 steps of bars. Classes, the battery estimate, screen time and weather come in 3.9b–e.
 */
object StatusScan {
    var status by mutableStateOf<PhoneStatus?>(null)
        private set
    var at by mutableLongStateOf(0L)
        private set

    fun show(s: PhoneStatus) {
        status = s
        at = android.os.SystemClock.uptimeMillis()
    }

    internal fun clear() { status = null }
}

private const val PANEL_AT = 110L
private const val ROW_GAP = 60L
private const val SPLIT_MS = 83L
private const val BOLT_MS = 90L
private const val BOLT_THICK_MS = 45L
private const val HOLD_MS = 3_500L
private const val STEP_MS = 83L
private val PURPLE = Color(0xFF37197B)
private val OFF = Color(0xFF4A4458)
private val SEG_OFF = Color(0xFF1C1824)

private class Row(val label: String, val value: String, val bad: Boolean = false, val dim: Boolean = false, val bar: Boolean = false)

private fun rows(s: PhoneStatus): List<Row> = listOf(
    Row("BATTERY", "${s.battery}%", bad = s.battery <= 15 && !s.charging, bar = true),
    when {
        s.alarm == null -> Row("ALARM", "NONE", dim = true)
        s.alarmInMin != null && s.alarmInMin in 0..60 -> Row("ALARM", "IN ${s.alarmInMin} MIN", bad = true)
        else -> Row("ALARM", s.alarm)
    },
    when (s.unread) {
        null -> Row("NOTIFS", "NO ACCESS", dim = true)
        0 -> Row("NOTIFS", "NONE")
        else -> Row("NOTIFS", "${s.unread} UNREAD")
    },
)

@Composable
fun StatusScanPanel() {
    val s = StatusScan.status ?: return
    val start = StatusScan.at
    val list = remember(s) { rows(s) }
    val endAt = PANEL_AT + (list.size - 1) * ROW_GAP + HOLD_MS
    var now by remember { mutableLongStateOf(start) }
    LaunchedEffect(start) {
        while (true) {
            withFrameMillis { }
            now = android.os.SystemClock.uptimeMillis()
            if (now - start > endAt + 2 * STEP_MS) { StatusScan.clear(); break }
        }
    }
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val topInset = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()

    Canvas(Modifier.fillMaxSize()) {
        val k = now - start
        if (k < PANEL_AT) return@Canvas
        val W = size.width
        val px = W * 0.08f
        val pw = W * 0.84f
        val strip = 34.dp.toPx()
        val lh = 52.dp.toPx()
        val py = topInset + size.height * 0.15f
        val ph = strip + 12.dp.toPx() + list.size * lh
        val x0 = px + 16.dp.toPx()

        if (k >= endAt) {                                   // break into bars, 2 steps
            val step = ((k - endAt) / STEP_MS).toInt()
            var i = 0
            fun r() = capriJitter(step * 31 + start.toInt(), i++) * 0.5f + 0.5f
            repeat(if (step == 0) 12 else 6) {
                drawRect(
                    if (r() < 0.55f) GLITCH_GREEN else GLITCH_CRIMSON,
                    Offset(px + r() * pw * 0.7f, py + r() * ph),
                    Size(W * (0.1f + 0.4f * r()), size.height * (0.004f + 0.02f * r() * r())),
                )
            }
            return@Canvas
        }

        // panel: purple offset, black, crimson edge, crimson line under the icon strip
        val o = 4.dp.toPx()
        drawRect(PURPLE, Offset(px + o, py + o), Size(pw, ph))
        drawRect(Color.Black, Offset(px, py), Size(pw, ph))
        drawRect(GLITCH_CRIMSON, Offset(px, py), Size(pw, ph), style = Stroke(2.dp.toPx()))
        drawRect(GLITCH_CRIMSON, Offset(px, py + strip), Size(pw, 2.dp.toPx()))

        // status icons: on = bright (airplane / silent / DND crimson), off = dim + slash
        val icons = listOf("wifi" to s.wifi, "bt" to s.bluetooth, "data" to s.mobileData, "plane" to s.airplane,
            "silent" to s.silent, "dnd" to s.dnd, "loc" to s.location)
        val gap = (pw - 24.dp.toPx()) / icons.size
        icons.forEachIndexed { i, (key, on) -> statusIcon(key, px + 12.dp.toPx() + gap * (i + 0.5f), py + strip / 2, on) }

        list.forEachIndexed { i, row ->
            val at = PANEL_AT + i * ROW_GAP
            if (k < at) return@forEachIndexed
            val ki = k - at
            val top = py + strip + 10.dp.toPx() + i * lh
            val valueY = top + 14.dp.toPx()
            // bolt from the top of the screen onto the line
            if (ki < BOLT_MS) {
                var n = 0
                fun r() = capriJitter(i * 31 + start.toInt(), n++) * 0.5f + 0.5f
                val tx = x0 + 40.dp.toPx() + r() * W * 0.4f
                val sx = tx + (r() - 0.5f) * W * 0.5f
                val ty = valueY + 10.dp.toPx()
                val path = Path().apply {
                    moveTo(sx, 0f)
                    for (q in 1 until 7) {
                        val f = q / 7f
                        lineTo(sx + (tx - sx) * f + (r() - 0.5f) * W * 0.1f * (1 - f), ty * f)
                    }
                    lineTo(tx, ty)
                }
                drawPath(path, CapriColors.Pink, style = Stroke((if (ki < BOLT_THICK_MS) 4f else 2.5f).dp.toPx(), join = StrokeJoin.Miter))
            }
            text(measurer, row.label, x0, top, 11.sp, CapriColors.Dim, FontFamily.Monospace)
            if (ki < SPLIT_MS) {
                text(measurer, row.value, x0 - 4.dp.toPx(), valueY, 26.sp, GLITCH_GREEN)
                text(measurer, row.value, x0 + 4.dp.toPx(), valueY, 26.sp, GLITCH_CRIMSON)
                return@forEachIndexed
            }
            val color = when { row.bad -> GLITCH_CRIMSON; row.dim -> CapriColors.Dim; else -> CapriColors.Text }
            val w = text(measurer, row.value, x0, valueY, 26.sp, color)
            if (row.bar) {                                  // chunky 10-block battery bar
                val bx = x0 + w + 14.dp.toPx()
                val sw = 9.dp.toPx(); val sh = 18.dp.toPx(); val sg = 3.dp.toPx()
                val segTop = valueY + 8.dp.toPx()
                val on = (s.battery / 10f).roundToInt()
                for (j in 0 until 10) {
                    drawRect(if (j < on) (if (row.bad) GLITCH_CRIMSON else GLITCH_GREEN) else SEG_OFF, Offset(bx + j * (sw + sg), segTop), Size(sw, sh))
                }
                if (s.charging) drawRect(GLITCH_GREEN, Offset(bx + 10 * (sw + sg) + 2.dp.toPx(), segTop + sh * 0.3f), Size(4.dp.toPx(), sh * 0.4f))
            }
        }
    }
}

/** Draws [s] with its top-left at (x, y); returns its width. */
private fun DrawScope.text(m: TextMeasurer, s: String, x: Float, y: Float, size: TextUnit, color: Color, family: FontFamily = FAN_FONT): Float {
    val r = m.measure(s, TextStyle(fontFamily = family, fontSize = size, color = color, letterSpacing = 0.04.sp))
    drawText(r, topLeft = Offset(x, y))
    return r.size.width.toFloat()
}

/** Flat 2 dp status icons, centred on (x, y). */
private fun DrawScope.statusIcon(key: String, x: Float, y: Float, on: Boolean) {
    val u = 1.dp.toPx()
    val c = if (!on) OFF else if (key == "plane" || key == "silent" || key == "dnd") GLITCH_CRIMSON else CapriColors.Text
    val stroke = Stroke(2 * u, join = StrokeJoin.Miter)
    fun p(vararg pts: Float) = Path().apply { moveTo(x + pts[0] * u, y + pts[1] * u); var i = 2; while (i < pts.size) { lineTo(x + pts[i] * u, y + pts[i + 1] * u); i += 2 } }
    when (key) {
        "wifi" -> {
            for (r in floatArrayOf(4f, 8f, 12f)) {
                drawArc(c, -135f, 90f, false, Offset(x - r * u, y + 5 * u - r * u), Size(2 * r * u, 2 * r * u), style = Stroke(2 * u))
            }
            drawRect(c, Offset(x - 1.5f * u, y + 3.5f * u), Size(3 * u, 3 * u))
        }
        "bt" -> drawPath(p(-4f, -4f, 4f, 4f, 0f, 8f, 0f, -8f, 4f, -4f, -4f, 4f), c, style = stroke)
        "data" -> for (i in 0 until 4) drawRect(c, Offset(x + (-8 + i * 4.5f) * u, y + (7 - (i + 1) * 3.5f) * u), Size(3 * u, (i + 1) * 3.5f * u))
        "plane" -> drawPath(p(0f, -9f, 1.5f, -2f, 9f, 2f, 9f, 4f, 1.5f, 2f, 1f, 6f, 4f, 8f, 4f, 9f, 0f, 8f, -4f, 9f, -4f, 8f, -1f, 6f, -1.5f, 2f, -9f, 4f, -9f, 2f, -1.5f, -2f).apply { close() }, c)
        "silent" -> {
            drawPath(Path().apply {
                moveTo(x - 6 * u, y + 4 * u); lineTo(x - 4 * u, y - 3 * u)
                quadraticTo(x, y - 9 * u, x + 4 * u, y - 3 * u); lineTo(x + 6 * u, y + 4 * u); close()
            }, c)
            drawRect(c, Offset(x - 1.5f * u, y + 5 * u), Size(3 * u, 2.5f * u))
            if (on) {
                drawLine(Color.Black, Offset(x - 8 * u, y - 7 * u), Offset(x + 8 * u, y + 8 * u), 2.5f * u)
                drawLine(c, Offset(x - 8 * u, y - 7 * u), Offset(x + 8 * u, y + 8 * u), 1.5f * u)
            }
        }
        "dnd" -> {
            drawCircle(c, 8 * u, Offset(x, y))
            drawRect(Color.Black, Offset(x - 5 * u, y - 1.5f * u), Size(10 * u, 3 * u))
        }
        "loc" -> {
            drawPath(Path().apply {
                moveTo(x, y + 9 * u)
                arcTo(Rect(Offset(x, y - 3 * u), 6 * u), 153f, 234f, false)
                close()
            }, c)
            drawRect(Color.Black, Offset(x - 1.5f * u, y - 4.5f * u), Size(3 * u, 3 * u))
        }
    }
    if (!on) drawLine(OFF, Offset(x - 9 * u, y + 9 * u), Offset(x + 9 * u, y - 9 * u), 2 * u)
}
