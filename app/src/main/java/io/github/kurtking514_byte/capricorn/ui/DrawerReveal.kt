package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlin.math.roundToInt

/**
 * 3.8: the app list appears like the boot's dock strike (designed in preview/drawer-lab.html,
 * user picked "Row by row"). The list snaps in black; the BOTTOM full row is struck first and the
 * strikes climb up, one row every 20 ms, 2 bolts per row shooting up from the bottom edge (where you
 * swiped). Each row: crimson 70 ms, then a crimson split for one 83 ms step, then normal.
 * Rows the screen edge would cut in half stay hidden until you scroll or search.
 * Closing: the list breaks into green + crimson bars for 2 steps (167 ms).
 */
private const val ROW_GAP_MS = 20L
private const val ROW_FLASH_MS = 70L
private const val ROW_POP_MS = 83L
private const val BOLT_THICK_MS = 45L
private const val BOLT_MS = 90L
internal const val DRAWER_CLOSE_MS = 167

internal class DrawerReveal {
    private class Bolt(val at: Long, val seed: Int, val tx: Float, val ty: Float)

    /** Every item's bounds (root px), by key. */
    val bounds = HashMap<String, Rect>()
    var list = Rect.Zero
    var bottomInset = 0f

    /** Row top (rounded px) → ms after the start when that row is struck. Null = not planned yet. */
    var schedule by mutableStateOf<Map<Int, Long>?>(null)
    var start = 0L
    var now by mutableLongStateOf(0L)

    /** True once the user scrolls or searches: everything just shows. */
    var free by mutableStateOf(false)

    var closeAt by mutableLongStateOf(-1L)

    private var bolts = emptyList<Bolt>()
    private var end = 0L

    fun plan(density: Density) {
        val bottom = list.bottom - bottomInset
        val rows = bounds.values
            .filter { it.top >= list.top - 0.5f && it.bottom <= bottom + 0.5f }  // fully visible only
            .groupBy { it.top.roundToInt() }
            .entries.sortedByDescending { it.key }                              // bottom row first
        val sched = HashMap<Int, Long>()
        val out = ArrayList<Bolt>()
        val margin = with(density) { 8.dp.toPx() }
        rows.forEachIndexed { i, (top, items) ->
            val at = i * ROW_GAP_MS
            sched[top] = at
            val left = items.minOf { it.left }
            val right = items.maxOf { it.right }
            val ty = minOf(top + items.maxOf { it.height } * 0.6f, bottom - margin)
            repeat(2) { k ->
                val f = 0.15f + 0.7f * (capriJitter(top + k * 13, i) * 0.5f + 0.5f)
                out.add(Bolt(at, i * 97 + k * 13 + top, left + (right - left) * f, ty))
            }
        }
        bolts = out
        end = (rows.size - 1).coerceAtLeast(0) * ROW_GAP_MS + ROW_FLASH_MS + ROW_POP_MS
        if (rows.isEmpty()) free = true
        start = android.os.SystemClock.uptimeMillis()
        now = start
        schedule = sched
    }

    val running get() = schedule == null || now - start < end + BOLT_MS

    /** Draws one list item according to the reveal. */
    fun Modifier.revealItem(key: String): Modifier = this
        .onGloballyPositioned { bounds[key] = it.boundsInRoot() }
        .drawWithContent {
            val s = schedule
            val at = s?.let { m -> bounds[key]?.let { m[it.top.roundToInt()] } }
            if (at == null) {                       // not planned yet, or a cut-off / scrolled-in row
                if (free) drawContent()
                return@drawWithContent
            }
            val k = now - start - at
            when {
                k < 0 -> Unit                                                       // still black
                k < ROW_FLASH_MS -> drawRect(GLITCH_CRIMSON)                       // crimson row
                k < ROW_FLASH_MS + ROW_POP_MS -> {                                  // crimson split
                    val d = 3.dp.toPx()
                    drawIntoCanvas { c ->
                        c.saveLayer(
                            Rect(-2 * d, -2 * d, size.width + 2 * d, size.height + 2 * d),
                            Paint().apply { colorFilter = ColorFilter.tint(GLITCH_CRIMSON) },
                        )
                        translate(left = d) { this@drawWithContent.drawContent() }
                        c.restore()
                    }
                    translate(left = -d) { this@drawWithContent.drawContent() }
                }
                else -> drawContent()
            }
        }

    /** Bolts on open, bars on close; draw over the whole drawer (which starts at the root's 0,0). */
    @Composable
    fun Overlay() {
        Canvas(Modifier.fillMaxSize()) {
            val t = now
            if (closeAt >= 0) {
                val step = ((t - closeAt) / 83).toInt()
                if (step < 2) {
                    var n = 0
                    fun r() = capriJitter(step * 31 + 5, n++) * 0.5f + 0.5f
                    repeat(if (step == 0) 12 else 6) {
                        val y = r() * size.height
                        val h = size.height * (0.004f + 0.02f * r() * r())
                        val x = r() * size.width * 0.7f
                        val l = size.width * (0.1f + 0.4f * r())
                        drawRect(if (r() < 0.55f) GLITCH_GREEN else GLITCH_CRIMSON, Offset(x, y), Size(l, h))
                    }
                }
                return@Canvas
            }
            if (schedule == null) return@Canvas
            val w = size.width
            val h = size.height
            for (b in bolts) {
                val k = t - start - b.at
                if (k < 0 || k >= BOLT_MS) continue
                var n = 0
                fun r() = capriJitter(b.seed, n++) * 0.5f + 0.5f
                val x0 = b.tx + (r() - 0.5f) * w * 0.7f
                val path = Path().apply {
                    moveTo(x0, h)
                    for (q in 1 until 8) {
                        val f = q / 8f
                        lineTo(x0 + (b.tx - x0) * f + (r() - 0.5f) * 2 * w * 0.05f * (1 - f), h + (b.ty - h) * f)
                    }
                    lineTo(b.tx, b.ty)
                }
                drawPath(path, CapriColors.Pink, style = Stroke(width = (if (k < BOLT_THICK_MS) 5 else 3).dp.toPx(), join = StrokeJoin.Miter))
            }
        }
    }
}

/** Runs the reveal clock: plans after the first layout, then ticks every frame until done. */
@Composable
internal fun DrawerRevealClock(state: DrawerReveal, density: Density, closing: Boolean) {
    LaunchedEffect(Unit) {
        withFrameMillis { }
        withFrameMillis { }                 // two frames so every visible item has its bounds
        state.plan(density)
        while (state.running) {
            withFrameMillis { }
            state.now = android.os.SystemClock.uptimeMillis()
        }
    }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        state.closeAt = android.os.SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis { }
            state.now = android.os.SystemClock.uptimeMillis()
            if (state.now - state.closeAt > DRAWER_CLOSE_MS) break
        }
    }
}
