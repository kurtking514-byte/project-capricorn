package io.github.kurtking514_byte.capricorn.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Optional sprite skin for Capri.
 *
 * If the app's assets contain a `skin/` folder of PNG frames, Capri is drawn from those
 * instead of the built-in code drawing. The repo ships no character art: put your own
 * frames in `app/src/debug/assets/skin/` (git-ignored) to use them on your own phone.
 *
 * Frame names (all optional except `neutral`): neutral, idle_happy, idle_smirk,
 * idle_annoyed, idle_skeptical, idle_shrug, idle_sleepy, idle_surprised, idle_blink_half,
 * idle_blink_closed, idle_mouth_small, idle_mouth_round, idle_mouth_wide, idle_wave_a,
 * idle_wave_b, arms_crossed_annoyed, hand_on_hip_smirk, intro_01 … intro_07.
 * All frames must share one canvas size so she doesn't jump between frames.
 */
class CapriSkin private constructor(private val context: Context, private val names: Set<String>) {

    // Small cache: each frame is about 1 MB in memory, so keep only the recent ones.
    private val cache = LruCache<String, ImageBitmap>(10)

    fun has(name: String) = name in names

    fun frame(name: String): ImageBitmap? {
        if (name !in names) return null
        cache.get(name)?.let { return it }
        return try {
            context.assets.open("$DIR/$name.png").use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
                ?.also { cache.put(name, it) }
        } catch (e: Exception) {
            null
        }
    }

    val introFrames: List<String> get() = names.filter { it.startsWith("intro_") }.sorted()

    companion object {
        private const val DIR = "skin"

        /** Returns the skin if one is installed, else null (the code-drawn Capri is used). */
        fun load(context: Context): CapriSkin? {
            val files = try {
                context.assets.list(DIR).orEmpty()
            } catch (e: Exception) {
                emptyArray()
            }
            val names = files.filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }.toSet()
            return if ("neutral" in names) CapriSkin(context.applicationContext, names) else null
        }
    }
}

private val TALK = listOf("idle_mouth_small", "idle_mouth_round", "idle_mouth_wide", "idle_mouth_round")

private fun moodFrame(mood: String): String = when (mood) {
    "happy" -> "idle_happy"
    "smirk" -> "idle_smirk"
    "annoyed" -> "arms_crossed_annoyed"
    "skeptical" -> "idle_skeptical"
    "shrug" -> "idle_shrug"
    "sleepy" -> "idle_sleepy"
    "surprised" -> "idle_surprised"
    "sass" -> "hand_on_hip_smirk"
    else -> "neutral"
}

/** Draws one sprite frame fitted into the canvas, with her small per-frame jitter. */
fun DrawScope.drawCapriSprite(image: ImageBitmap, seed: Int, jitter: Float = 1f) {
    val scale = minOf(size.width / image.width, size.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    val wobble = h * 0.004f * jitter
    val x = (size.width - w) / 2f + capriJitter(seed, 1) * wobble
    val y = (size.height - h) + capriJitter(seed, 2) * wobble
    rotate(capriJitter(seed, 3) * 0.45f * jitter, pivot = Offset(size.width / 2f, size.height)) {
        drawImage(
            image = image,
            dstOffset = IntOffset(x.toInt(), y.toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
        )
    }
}

/**
 * Capri from the sprite skin: holds a mood pose for a few seconds, blinks at random,
 * flaps her mouth while speaking, and jitters like a hand-cut animation.
 */
@Composable
fun CapriSpriteFigure(
    skin: CapriSkin,
    seed: Int,
    speaking: Boolean,
    mood: String,
    moodId: Int,
    onPoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val poke by rememberUpdatedState(onPoke)

    var current by remember { mutableStateOf("neutral") }
    LaunchedEffect(moodId) {
        current = mood
        delay(4_500)
        current = "neutral"
    }

    var blink by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2_200L, 5_500L))
            blink = 1; delay(90)
            blink = 2; delay(120)
            blink = 1; delay(90)
            blink = 0
        }
    }

    val name = when {
        speaking && current in setOf("neutral", "happy", "smirk", "wave") -> TALK[seed % TALK.size]
        current == "wave" -> if ((seed / 2) % 2 == 0) "idle_wave_a" else "idle_wave_b"
        current == "neutral" && blink == 2 -> "idle_blink_closed"
        current == "neutral" && blink == 1 -> "idle_blink_half"
        else -> moodFrame(current)
    }
    val image = skin.frame(name) ?: skin.frame("neutral")

    Canvas(modifier.pointerInput(Unit) { detectTapGestures(onTap = { poke() }) }) {
        if (image != null) drawCapriSprite(image, seed)
    }
}
