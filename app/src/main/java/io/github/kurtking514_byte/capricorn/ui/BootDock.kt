package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlin.math.abs

/**
 * Shared between the boot and the home screen. The chatbox and dock slots report where they are;
 * the boot strikes them with lightning one by one. While the boot runs, a slot stays hidden until
 * it's struck, then flashes crimson, pops in and glitches.
 *
 * Everything runs on ONE clock, [time], which the boot sets every frame, so a slot's flash lands
 * on exactly the same frame as its lightning bolts.
 */
object BootDock {
    /** Slot index used for the chatbox (dock slots are 0..4). */
    const val CHAT = 5

    /** How long a struck slot stays solid crimson, then how long it glitches in (seconds). */
    const val FLASH = 0.07f
    const val GLITCH = 0.16f

    /** Each slot's bounds in root (= screen) coordinates: dock slots by index, plus [CHAT]. */
    val slots = mutableStateMapOf<Int, Rect>()

    /** Slot index -> boot time (s) when lightning hits it. */
    val struck = mutableStateMapOf<Int, Float>()

    /** The boot's clock in seconds, updated every frame while the boot runs. */
    var time by mutableFloatStateOf(0f)

    var active by mutableStateOf(false)
        private set

    fun start() {
        struck.clear()
        time = 0f
        active = true
    }

    fun finish() {
        active = false
    }
}

/** Put on the chatbox and each dock slot: reports its position and plays its boot reveal. */
fun Modifier.bootSlot(index: Int): Modifier = this
    .onGloballyPositioned { BootDock.slots[index] = it.boundsInRoot() }
    .drawWithContent {
        if (!BootDock.active) {                       // no boot running (or it was skipped): normal
            drawContent()
            return@drawWithContent
        }
        val hit = BootDock.struck[index] ?: return@drawWithContent      // hidden until struck
        val since = BootDock.time - hit
        if (since < 0f) return@drawWithContent
        if (since < BootDock.FLASH) {
            drawRect(CapriColors.Pink)
            return@drawWithContent
        }
        val g = (since - BootDock.FLASH) / BootDock.GLITCH
        if (g >= 1f) {
            drawContent()
            return@drawWithContent
        }
        // pop in, torn into slices with a crimson split, tearing twice
        val pop = 0.7f + 0.3f * popEase((g * 1.15f).coerceAtMost(1f))
        val phase = if (g < 0.5f) 0 else 1
        val bands = 5
        val bh = size.height / bands
        scale(pop) {
            for (b in 0 until bands) {
                val r = capriJitter(index * 31 + phase * 7 + (hit * 1000f).toInt(), b)
                val dx = if (abs(r) > 0.4f) r * size.width * 0.35f * (1f - g) else 0f
                clipRect(top = b * bh, bottom = (b + 1) * bh + 0.5f) {
                    translate(left = dx + size.width * 0.08f) { drawRect(CapriColors.Pink) }
                    if (r > -0.6f) translate(left = dx) { this@drawWithContent.drawContent() }
                }
            }
        }
    }

private fun popEase(x: Float): Float {
    val c1 = 1.70158f
    val y = x - 1f
    return 1f + (c1 + 1f) * y * y * y + c1 * y * y
}
