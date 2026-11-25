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
import androidx.compose.ui.graphics.Color
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
 *  2. She appears at her home-screen spot with the show's bar glitch (0.5 s: dim crimson
 *     silhouette, then real colors with green + crimson bars that thin out), and at 0.3 s
 *     tears into her raised-arm pose (0.15 s strips). The lightning lands as the bars end.
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
    val blinkClosed = remember(skin) { skin?.bitmap("idle_blink_closed") }
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
        // 4.6d: black until the last flash; then it glitches away in 3 steps of 83 ms while she
        // glitches in, and the wallpaper is behind her (home parts stay hidden until struck)
        if (t < plan.flashEnd) drawRect(CapriColors.Black)
        else drawTear(plan, ((t - plan.flashEnd) * 1000f / GLITCH_STEP_MS).toInt())
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

        // 2. she appears (show's bar glitch, 0.5 s, picked in preview/glitch-lab.html), and
        //    raises her arm with the pose-change strips when the heavy part ends
        if (still == null || raised == null || t < plan.flashEnd) return@Canvas
        val a = t - plan.flashEnd
        val step = (a * 1000f / GLITCH_STEP_MS).toInt()
        when {
            a < APPEAR * SILHOUETTE ->                       // dim crimson silhouette inside the bars
                painter.draw(this, still, 1f, t, 0f, 0f, 0f, lift = rest, topInset = inset, dim = true)
            a < RAISE_AT -> {                                // real colors; her eyes drop out now and then
                val eyesOut = blinkClosed != null && capriJitter(plan.arriveSeed, 50 + step) > 0.7f
                painter.draw(this, if (eyesOut) blinkClosed!! else still, 1f, t, 0f, 0f, 0f, lift = rest, topInset = inset)
            }
            a < RAISE_AT + TO_RAISED -> glitch(still, raised, (a - RAISE_AT) / TO_RAISED, plan.arriveSeed + 1)
            else -> painter.draw(this, raised, 1f, t, 0f, 0f, 0f, lift = rest, topInset = inset)
        }
        val barsI = if (a < RAISE_AT) 1f else (1f - (a - RAISE_AT) / (APPEAR - RAISE_AT)).coerceAtLeast(0f)
        translate(top = -rest) { drawGlitchBars(painter, still, inset, step, barsI, plan.arriveSeed) }

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
                val wide = i == BootDock.CHAT || i == BootDock.CLOCK
                val cx = if (wide) slot.center.x + b.sx * slot.width else slot.center.x
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

// Arrival timing, seconds (CAPRI_REFERENCE.md glitch, squeezed to 0.5 s; user pick 2026-11-23).
private const val APPEAR = 0.5f         // whole appear glitch: bars from full strength to nothing
private const val SILHOUETTE = 0.35f    // first 35%: dim crimson silhouette
private const val RAISE_AT = APPEAR * 0.6f  // heavy bars until 60%; then she raises her arm
private const val TO_RAISED = 0.15f     // arm raise: pose-change strips (same as the home screen)
private const val STRIKE_DELAY = 0.04f  // after her arm is up, everything is struck at once
private const val HOLD = 0.3f           // if there's nothing to strike
private const val MAX_DOCK = 5
private const val PLACEHOLDERS = 4      // tiles struck when no apps are pinned

private fun hasDock() = BootDock.slots.keys.any { it < MAX_DOCK }

/** Strike order: the clock and chatbox, then the pinned apps (or the placeholder tiles if none are pinned). All at once. */
private fun strikeOrder(plan: BootPlan): List<Int> {
    val top = listOf(BootDock.CLOCK, BootDock.CHAT).filter { it in BootDock.slots }
    val dock = if (hasDock()) plan.order.filter { it in BootDock.slots } else plan.order.filter { it < PLACEHOLDERS }
    return top + dock
}

/** 4.6d: one horizontal strip of the black as it glitches away (fractions of the screen). */
private class Strip(val y: Float, val h: Float, val keep: Boolean, val bar: Color?, val x: Float, val w: Float)

/** 4.6d: how much of the black is left in each 83 ms step (picked in preview/boot-lab.html). */
private val TEAR_KEEP = floatArrayOf(0.6f, 0.3f, 0.1f)

private fun DrawScope.drawTear(plan: BootPlan, step: Int) {
    val strips = plan.tear.getOrNull(step) ?: return
    for (s in strips) {
        val y = s.y * size.height
        val h = s.h * size.height
        if (s.keep) drawRect(CapriColors.Black, Offset(0f, y), Size(size.width, h))
        else if (s.bar != null) drawRect(s.bar, Offset(s.x * size.width, y), Size(s.w * size.width, maxOf(2f, h * 0.35f)))
    }
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
    val bolts: List<List<Bolt>>,   // per dock index (+ chatbox, clock)
    val tear: List<List<Strip>>,   // 4.6d: the black glitching away, one list per step
) {
    val strikeStart = flashEnd + RAISE_AT + TO_RAISED

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
            val strikeAt = FloatArray(MAX_DOCK + 2)        // + chatbox + clock
            // chatbox and every pinned app are struck at the SAME moment (user rule)
            for (j in strikeAt.indices) strikeAt[j] = STRIKE_DELAY
            val bolts = List(MAX_DOCK + 2) {
                // first bolt lands exactly on the hit; the others within the slot's crimson flash
                List(3 + r.nextInt(2)) { k ->
                    Bolt(rnd(-0.4f, 0.4f), if (k == 0) 0f else rnd(0f, 0.03f), FloatArray(8) { rnd(-1f, 1f) })
                }
            }
            val tear = TEAR_KEEP.map { keep ->
                buildList {
                    var y = 0f
                    while (y < 1f) {
                        val h = rnd(0.01f, 0.06f)
                        val torn = r.nextFloat() >= keep
                        val bar = if (torn && r.nextFloat() < 0.35f) (if (r.nextFloat() < 0.55f) GLITCH_GREEN else GLITCH_CRIMSON) else null
                        add(Strip(y, h, !torn, bar, rnd(-0.1f, 0.6f), rnd(0.15f, 0.6f)))
                        y += h
                    }
                }
            }
            return BootPlan(hits, t + 0.06f, r.nextInt(1_000_000), (0 until MAX_DOCK).shuffled(r), strikeAt, bolts, tear)
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
