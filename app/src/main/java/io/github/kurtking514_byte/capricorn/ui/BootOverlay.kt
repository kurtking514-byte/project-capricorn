package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * Wordless boot, tap to skip. With a sprite skin she streaks across the screen in
 * crimson flashes (see [SpriteBoot]); without one it uses the code-drawn version below: a crimson slash,
 * her shard hair bursts out of it, her body and face snap in, she blinks.
 */
@Composable
fun BootOverlay(skin: CapriSkin?, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    if (skin != null) {
        SpriteBoot(skin) { done() }
        return
    }
    val t = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(durationMillis = 2100, easing = LinearEasing))
        done()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .background(CapriColors.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { done() }
    ) {
        val p = t.value
        val slash = segment(p, 0.00f, 0.16f)
        val slashOut = segment(p, 0.16f, 0.34f)
        val grow = segment(p, 0.14f, 0.70f)
        val blink = segment(p, 0.78f, 0.90f)
        val blinkClosed = if (blink > 0f && blink < 1f) 1f - abs(blink * 2f - 1f) else 0f

        val u = minOf(size.height / 2.0f, size.width / 1.28f)
        val c = Offset(size.width / 2f, size.height / 2f)

        if (slash > 0f && slashOut < 1f) {
            val half = size.width * 0.5f * easeOut(slash)
            val thickness = u * 0.012f * (1f - slashOut)
            val y = c.y - 0.09f * u
            drawLine(
                CapriColors.Pink,
                Offset(c.x - half, y + half * 0.08f),
                Offset(c.x + half, y - half * 0.08f),
                strokeWidth = thickness,
            )
        }

        drawCapriBody(
            center = c,
            u = u,
            seed = (p * 26f).toInt(),
            grow = grow,
            open = 1f - 0.97f * blinkClosed,
        )
    }
}

/**
 * Boot from the sprite skin, a little over a second, like her entrances in the show:
 * the screen flashes crimson and she streaks across it, flashes again as she whips back
 * the other way in close-up, then one more flash and she snaps out from a face close-up
 * to her spot on the home screen, shards flying.
 */
@Composable
private fun SpriteBoot(skin: CapriSkin, done: () -> Unit) {
    val poseA = remember(skin) { skin.pick("idle_excited", "idle_smirk") }
    val poseB = remember(skin) { skin.pick("lean_in_attentive", "idle_smirk") }
    val poseC = remember(skin) { skin.pick("idle_laughing", "idle_smirk") }

    val painter = remember { CapriMeshPainter() }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val begin = withFrameNanos { it }
        while (time < BOOT_TOTAL) withFrameNanos { time = (it - begin) / 1e9f }
        done()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .background(CapriColors.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { done() }
    ) {
        val t = time
        val w = size.width
        val h = size.height
        val inset = h * 0.42f                    // keeps her about the size she is on the home screen
        val rest = h * 0.17f                     // where her feet end up, above the chatbox and dock

        // crimson flashes at the start of each move
        var flash = 0f
        for (at in FLASHES) if (t >= at) flash = maxOf(flash, 0.9f * exp(-(t - at) / 0.07f))
        if (flash > 0.01f) drawRect(CapriColors.Pink, alpha = flash)

        val pA = segment(t, A0, A1)
        val pB = segment(t, B0, B1)
        val pC = segment(t, C0, C1)

        // speed streaks tearing across with her
        if (pA > 0f && pA < 1f) drawStreaks(pA, 11, 1f)
        if (pB > 0f && pB < 1f) drawStreaks(pB, 23, -1f)

        // lift that puts her face mid-screen (focus 1) or her feet on her spot (focus 0)
        fun liftFor(bitmap: android.graphics.Bitmap, zoom: Float, focus: Float): Float {
            val tall = painter.frameHeight(size, bitmap, zoom, inset)
            return rest + (h * 0.5f - 0.63f * tall - rest) * focus
        }

        // pass A: left to right, with fading copies trailing behind her
        if (pA > 0f && pA < 1f) skin.bitmap(poseA)?.let { b ->
            for (k in 3 downTo 0) {
                val q = (pA - k * 0.05f).coerceAtLeast(0f)
                val x = -1.25f * w + 2.5f * w * easeInOut(q)
                painter.draw(this, b, if (k == 0) 1f else 0.30f / k, t, 0f, 0f, -0.5f, 1.7f, 0f, liftFor(b, 1.7f, 0.55f), x, inset)
            }
        }
        // pass B: right to left, closer
        if (pB > 0f && pB < 1f) skin.bitmap(poseB)?.let { b ->
            for (k in 3 downTo 0) {
                val q = (pB - k * 0.05f).coerceAtLeast(0f)
                val x = 1.2f * w - 2.4f * w * easeInOut(q)
                painter.draw(this, b, if (k == 0) 1f else 0.30f / k, t, 0f, 0f, -0.5f, 2.7f, 0f, liftFor(b, 2.7f, 1f), x, inset)
            }
        }
        // pass C: snap out from a face close-up to her spot, overshooting a little
        if (pC > 0f) {
            val settle = easeOutBack(pC)
            val zoom = 1f + 2.6f * (1f - settle)
            val focus = (1f - easeOut(pC)).coerceIn(0f, 1f)
            val toNeutral = segment(pC, 0.62f, 0.86f)
            val appear = segment(pC, 0f, 0.08f)
            val squash = 0.9f * impact((pC - 0.42f) * (C1 - C0))
            if (toNeutral < 1f) skin.bitmap(poseC)?.let { b ->
                painter.draw(this, b, appear * (1f - toNeutral), t, 0f, 0f, squash, zoom, pC, liftFor(b, zoom, focus), 0f, inset)
            }
            if (toNeutral > 0f) skin.bitmap("neutral")?.let { b ->
                painter.draw(this, b, toNeutral, t, 0f, 0f, squash, zoom, pC, liftFor(b, zoom, focus), 0f, inset)
            }
        }
    }
}

// Boot timing, in seconds: pass A, pass B, then the snap into place.
private const val A0 = 0.00f
private const val A1 = 0.30f
private const val B0 = 0.24f
private const val B1 = 0.54f
private const val C0 = 0.50f
private const val C1 = 1.05f
private const val BOOT_TOTAL = 1.15f
private val FLASHES = floatArrayOf(A0, B0, C0)

/** Flat streaks racing across the screen in direction [dir] (+1 = left to right); [p] runs 0..1. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStreaks(p: Float, seed: Int, dir: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until 10) {
        val r1 = capriJitter(seed, i) * 0.5f + 0.5f
        val r2 = capriJitter(seed, 30 + i) * 0.5f + 0.5f
        val y = h * (0.06f + 0.88f * r1)
        val len = w * (0.45f + 0.7f * r2)
        val thick = h * (0.004f + 0.022f * r2 * r2)
        val q = (p * 1.35f - 0.25f * r1).coerceIn(0f, 1f)
        val head = if (dir > 0f) -len + (w + 2f * len) * q else w + len - (w + 2f * len) * q
        val tail = head - dir * len
        val slant = thick * 1.2f
        val streak = androidx.compose.ui.graphics.Path().apply {
            moveTo(tail, y); lineTo(head, y - slant); lineTo(head, y - slant + thick); lineTo(tail, y + thick * 0.3f); close()
        }
        drawPath(streak, if (i % 4 == 3) CapriColors.Cyan else CapriColors.Pink, alpha = 1f - p * p)
    }
}

/** Squash pulse around an impact at d = 0: rises fast just before, eases out after. */
private fun impact(d: Float): Float = if (d < 0f) exp(d / 0.025f) else exp(-d / 0.08f)

private fun easeInOut(x: Float): Float = x * x * (3f - 2f * x)

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val y = x - 1f
    return 1f + (c1 + 1f) * y * y * y + c1 * y * y
}

/** Maps p in [start, end] to 0..1, clamped. */
private fun segment(p: Float, start: Float, end: Float): Float =
    ((p - start) / (end - start)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)
