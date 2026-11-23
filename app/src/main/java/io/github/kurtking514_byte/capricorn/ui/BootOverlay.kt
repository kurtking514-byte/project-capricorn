package io.github.kurtking514_byte.capricorn.ui

import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.abs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlin.random.Random

/**
 * Wordless boot, tap to skip (designed in preview/boot-lab.html):
 *  1. 3–5 hard crimson hits at random times and in random shapes (full screen, slash, band,
 *     half screen); somewhere in the middle she glitches through for a split second.
 *  2. She glitches in at her home-screen spot, then glitches into her raised-arm pose.
 *  3. 3–4 lightning bolts strike down onto each pinned app, app by app in random order; each
 *     dock slot flashes crimson, pops in and glitches (see [BootDock] / [bootSlot]).
 * Without a sprite skin only the flashes play; the boot never draws a code-made Capri.
 */
@Composable
fun BootOverlay(skin: CapriSkin?, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val plan = remember(skin) { BootPlan.random(withGlitch = skin != null) }
    val painter = remember { CapriMeshPainter() }
    val still = remember(skin) { skin?.bitmap("neutral") }
    val raised = remember(skin) { skin?.let { it.bitmap(it.pick("idle_wave_raise", "neutral")) } }
    val noTilt = remember { FloatArray(2) }

    var time by remember { mutableFloatStateOf(0f) }
    val finish = {
        BootDock.finish()
        done()
    }
    LaunchedEffect(Unit) {
        BootDock.start()
        val begin = withFrameNanos { it }
        while (true) {
            withFrameNanos {
                time = (it - begin) / 1e9f
                BootDock.time = time                       // one clock for bolts and slot flashes
            }
            // strike the pinned apps one by one, in this boot's random order
            val order = strikeOrder(plan)
            if (still != null) order.forEachIndexed { j, i ->
                if (i in BootDock.slots && time >= plan.strikeStart + plan.strikeAt[j] && i !in BootDock.struck) {
                    BootDock.struck[i] = plan.strikeStart + plan.strikeAt[j]   // exact scheduled time
                }
            }
            val total = when {
                still == null -> plan.flashEnd
                order.isEmpty() -> plan.strikeStart + HOLD
                else -> plan.strikeStart + STRIKE_DELAY + BootDock.FLASH + BootDock.GLITCH + 0.05f
            }
            if (time >= total) break
        }
        finish()
    }
    DisposableEffect(Unit) { onDispose { BootDock.finish() } }

    Canvas(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { finish() }
    ) {
        val t = time
        // black over everything; once the strikes start, only down to the dock so the real dock shows
        val striking = still != null && t >= plan.strikeStart && BootDock.slots.isNotEmpty()
        if (striking) {
            val dockTop = BootDock.slots.values.minOf { it.top } - 14.dp.toPx()
            drawRect(CapriColors.Black, size = Size(size.width, dockTop))
        } else {
            drawRect(CapriColors.Black)
        }
        val inset = size.height * 0.42f          // keeps her the size she is on the home screen
        val rest = size.height * 0.17f           // her feet sit above the chatbox and dock

        // she is drawn with her feet [rest] px above the bottom, like on the home screen
        fun glitch(from: android.graphics.Bitmap, to: android.graphics.Bitmap, p: Float, seed: Int) =
            translate(top = -rest) { drawPoseGlitch(painter, from, to, p, seed, t, noTilt, 0f, inset) }

        // 1. flashes
        for (hit in plan.hits) {
            if (t < hit.at || t >= hit.at + hit.dur) continue
            val k = (t - hit.at) / hit.dur
            if (hit.shape == Shape.GLITCH) still?.let { glitch(it, it, k, hit.seed) }
            else drawHit(hit, k)
        }

        // 2. she arrives
        if (still == null || raised == null || t < plan.flashEnd) return@Canvas
        val a = t - plan.flashEnd
        when {
            a < GLITCH_IN -> glitch(still, still, a / GLITCH_IN, plan.arriveSeed)
            a < GLITCH_IN + TO_RAISED -> glitch(still, raised, (a - GLITCH_IN) / TO_RAISED, plan.arriveSeed + 1)
            else -> painter.draw(this, raised, 1f, t, 0f, 0f, 0f, lift = rest, topInset = inset)
        }

        // 3. lightning: 3–4 bolts from the top of the screen converge on each pinned app
        //    (with no pinned apps, placeholder tiles at the bottom get struck instead)
        if (still == null || t < plan.strikeStart) return@Canvas
        val dockReal = hasDock()
        val slots: Map<Int, Rect> = BootDock.slots + (if (dockReal) emptyMap() else placeholderSlots())
        strikeOrder(plan).forEachIndexed { j, i ->
            val slot = slots[i] ?: return@forEachIndexed
            val hitAt = plan.strikeStart + plan.strikeAt[j]
            if (i < MAX_DOCK && !dockReal && t >= hitAt) drawPlaceholder(slot, t - hitAt, i)
            for (b in plan.bolts[i]) {
                val bs = t - hitAt - b.delay
                if (bs < 0f || bs >= BootDock.FLASH + 0.02f) continue
                // the chatbox is wide, so its bolts land spread along it
                val cx = if (i == BootDock.CHAT) slot.center.x + b.sx * slot.width else slot.center.x
                val ty = slot.top
                val x0 = cx + b.sx * size.width
                val bolt = Path().apply {
                    moveTo(x0, 0f)
                    for (q in 1 until b.pts.size) {
                        val f = q.toFloat() / b.pts.size
                        lineTo(x0 + (cx - x0) * f + b.pts[q] * size.width * 0.06f * (1f - f), ty * f)
                    }
                    lineTo(cx, ty)
                }
                val width = if (bs < 0.045f) 5.dp.toPx() else 3.dp.toPx()
                drawPath(bolt, CapriColors.Pink, style = Stroke(width = width, join = StrokeJoin.Miter))
            }
        }
    }
}

// Arrival timing, seconds.
private const val GLITCH_IN = 0.12f
private const val TO_RAISED = 0.10f
private const val STRIKE_DELAY = 0.04f  // after her arm is up, everything is struck at once
private const val HOLD = 0.3f           // if there's nothing to strike
private const val MAX_DOCK = 5
private const val PLACEHOLDERS = 4      // tiles struck when no apps are pinned

private fun hasDock() = BootDock.slots.keys.any { it < MAX_DOCK }

/** Strike order: the chatbox first, then the pinned apps (or the placeholder tiles if none are pinned). */
private fun strikeOrder(plan: BootPlan): List<Int> {
    val chat = if (BootDock.CHAT in BootDock.slots) listOf(BootDock.CHAT) else emptyList()
    val dock = if (hasDock()) plan.order.filter { it in BootDock.slots } else plan.order.filter { it < PLACEHOLDERS }
    return chat + dock
}

/** Where the placeholder tiles sit: a row near the bottom, where the dock is. */
private fun DrawScope.placeholderSlots(): Map<Int, Rect> {
    val s = size.width * 0.13f
    val cy = size.height * 0.9f
    return (0 until PLACEHOLDERS).associateWith { i ->
        val cx = size.width * (0.2f + 0.2f * i)
        Rect(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
    }
}

/** A placeholder app tile [since] seconds after its strike: crimson hit, then pop-in glitch, then plain. */
private fun DrawScope.drawPlaceholder(slot: Rect, since: Float, i: Int) {
    val c = slot.width * 0.2f
    val tile = Path().apply {
        moveTo(slot.left, slot.top + c); lineTo(slot.left + c, slot.top); lineTo(slot.right, slot.top)
        lineTo(slot.right, slot.bottom - c); lineTo(slot.right - c, slot.bottom); lineTo(slot.left, slot.bottom); close()
    }
    val edge = Stroke(width = 1.5.dp.toPx())
    if (since < 0.04f) {
        drawPath(tile, CapriColors.Pink)
        return
    }
    val g = (since - 0.04f) / 0.16f
    if (g >= 1f) {
        drawPath(tile, CapriColors.Panel)
        drawPath(tile, CapriColors.Cyan, style = edge)
        return
    }
    val bands = 5
    val bh = slot.height / bands
    for (b in 0 until bands) {
        val r = capriJitter(i * 31 + if (g < 0.5f) 0 else 7, b)
        val dx = if (abs(r) > 0.4f) r * slot.width * 0.35f * (1f - g) else 0f
        clipRect(top = slot.top + b * bh, bottom = slot.top + (b + 1) * bh + 0.5f) {
            translate(left = dx + slot.width * 0.08f) { drawPath(tile, CapriColors.Pink) }
            if (r > -0.6f) translate(left = dx) {
                drawPath(tile, CapriColors.Panel)
                drawPath(tile, CapriColors.Cyan, style = edge)
            }
        }
    }
}

private enum class Shape { FULL, SLASH, BAND, HALF, GLITCH }

/** One crimson hit. The numbers in [v] are shape-specific random values in 0..1. */
private class Hit(val at: Float, val dur: Float, val shape: Shape, val seed: Int, val v: FloatArray)

/** One lightning bolt: start offset across the top (fraction of width), delay (s), zigzag offsets. */
private class Bolt(val sx: Float, val delay: Float, val pts: FloatArray)

/** A random flash pattern and strike order; every boot is different. */
private class BootPlan(
    val hits: List<Hit>,
    val flashEnd: Float,
    val arriveSeed: Int,
    val order: List<Int>,          // dock indices in strike order
    val strikeAt: FloatArray,      // seconds after strikeStart, by strike rank
    val bolts: List<List<Bolt>>,   // per dock index
) {
    val strikeStart = flashEnd + GLITCH_IN + TO_RAISED

    companion object {
        fun random(withGlitch: Boolean): BootPlan {
            val r = Random(System.nanoTime())
            fun rnd(a: Float, b: Float) = a + r.nextFloat() * (b - a)
            val n = 3 + r.nextInt(3)
            val glitchAt = if (withGlitch) 1 + r.nextInt(n - 1) else -1
            val plain = listOf(Shape.FULL, Shape.SLASH, Shape.BAND, Shape.HALF)
            val hits = mutableListOf<Hit>()
            var t = rnd(0.03f, 0.08f)
            var last: Shape? = null
            for (i in 0 until n) {
                var shape = if (i == glitchAt) Shape.GLITCH else plain.random(r)
                while (shape == last && i != glitchAt) shape = plain.random(r)
                last = shape
                val dur = if (shape == Shape.GLITCH) rnd(0.08f, 0.12f) else rnd(0.05f, 0.09f)
                hits += Hit(t, dur, shape, r.nextInt(1_000_000), FloatArray(6) { r.nextFloat() })
                t += dur + if (r.nextFloat() < 0.35f) rnd(0.02f, 0.04f) else rnd(0.05f, 0.12f)
            }
            // at least one full-screen hit, so it reads as one big flash
            if (hits.none { it.shape == Shape.FULL }) {
                val idx = if (hits.first().shape != Shape.GLITCH) 0 else hits.lastIndex
                val h = hits[idx]
                hits[idx] = Hit(h.at, h.dur, Shape.FULL, h.seed, h.v)
            }
            val strikeAt = FloatArray(MAX_DOCK + 1)        // + 1 for the chatbox
            // chatbox and every pinned app are struck at the SAME moment (user rule)
            for (j in strikeAt.indices) strikeAt[j] = STRIKE_DELAY
            val bolts = List(MAX_DOCK + 1) {
                // first bolt lands exactly on the hit; the others within the slot's crimson flash
                List(3 + r.nextInt(2)) { k ->
                    Bolt(rnd(-0.4f, 0.4f), if (k == 0) 0f else rnd(0f, 0.03f), FloatArray(8) { rnd(-1f, 1f) })
                }
            }
            return BootPlan(hits, t + 0.06f, r.nextInt(1_000_000), (0 until MAX_DOCK).shuffled(r), strikeAt, bolts)
        }
    }
}

/** Draws one flat crimson hit; [k] runs 0..1 through it. */
private fun DrawScope.drawHit(hit: Hit, k: Float) {
    val w = size.width
    val h = size.height
    val v = hit.v
    val red = CapriColors.Pink
    when (hit.shape) {
        Shape.FULL -> {
            drawRect(red)
            if (v[0] < 0.5f) {                   // a black tear through the flash
                val y = h * (0.2f + 0.6f * v[1])
                val th = h * (0.01f + 0.02f * v[2])
                rotate(degrees = (v[3] - 0.5f) * 28f, pivot = Offset(w / 2f, y)) {
                    drawRect(CapriColors.Black, topLeft = Offset(-w, y - th / 2f), size = Size(w * 3f, th))
                }
            }
        }
        Shape.SLASH -> {
            val y = h * (0.25f + 0.5f * v[0])
            val bw = h * (0.08f + 0.14f * v[1])
            val len = kotlin.math.hypot(w, h) * 1.2f
            val angle = (v[2] - 0.5f) * 103f + if (v[3] < 0.5f) 0f else 180f
            val s = (k - 0.5f) * w * 0.15f
            val cx = w / 2f
            val slash = Path().apply {
                moveTo(cx - len / 2f + s, y - bw / 2f)
                lineTo(cx + len / 2f + s, y - bw / 2f + bw * 0.25f)
                lineTo(cx + len / 2f + s, y + bw / 2f - bw * 0.35f)
                lineTo(cx - len / 2f + s, y + bw / 2f)
                close()
            }
            rotate(degrees = angle, pivot = Offset(cx, y)) { drawPath(slash, red) }
        }
        Shape.BAND -> {
            val x = w * (-0.1f + 0.25f * v[0] + (v[1] - 0.5f) * 0.1f * k)
            drawRect(red, topLeft = Offset(x, h * (0.05f + 0.75f * v[2])), size = Size(w * 1.1f, h * (0.06f + 0.22f * v[3])))
        }
        Shape.HALF -> {
            val f = 0.35f + 0.25f * v[0]
            when ((v[1] * 4).toInt().coerceAtMost(3)) {
                0 -> drawRect(red, size = Size(w, h * f))
                1 -> drawRect(red, topLeft = Offset(0f, h * (1f - f)), size = Size(w, h * f))
                2 -> drawRect(red, size = Size(w * f, h))
                else -> drawRect(red, topLeft = Offset(w * (1f - f), 0f), size = Size(w * f, h))
            }
        }
        Shape.GLITCH -> Unit                     // drawn with her sprite, see BootOverlay
    }
}
