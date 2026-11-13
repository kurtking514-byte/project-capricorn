package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlin.math.abs

/**
 * Wordless boot, about 2 seconds, tap to skip:
 * a crimson slash cuts across the screen, her shard hair bursts out of it one shard
 * after another, her body and face snap in, she blinks, and the home screen fades in
 * around her.
 */
@Composable
fun BootOverlay(skin: CapriSkin?, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val intro = remember(skin) { skin?.introFrames.orEmpty() }
    if (skin != null && intro.size >= 3) {
        SpriteBoot(skin, intro) { done() }
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

        // the slash: a thin crimson line that shoots out from the center, then thins away
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
 * Boot from the sprite skin intro frames: a signal slash, her eyes, she drops in,
 * lands, greets, winks, ready. Each frame is held like a hand-drawn animation key,
 * with her jitter running underneath.
 */
@Composable
private fun SpriteBoot(skin: CapriSkin, frames: List<String>, done: () -> Unit) {
    val holds = listOf(170L, 210L, 290L, 420L, 620L, 480L, 420L)
    var index by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        frames.forEachIndexed { i, _ ->
            index = i
            delay(holds.getOrElse(i) { 300L })
        }
        done()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(90)
            seed++
        }
    }
    val image = skin.frame(frames[index])
    Canvas(
        Modifier
            .fillMaxSize()
            .background(CapriColors.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { done() }
            .padding(horizontal = 24.dp, vertical = 150.dp)
    ) {
        if (image != null) drawCapriSprite(image, seed, jitter = 1.6f)
    }
}

/** Maps p in [start, end] to 0..1, clamped. */
private fun segment(p: Float, start: Float, end: Float): Float =
    ((p - start) / (end - start)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)
