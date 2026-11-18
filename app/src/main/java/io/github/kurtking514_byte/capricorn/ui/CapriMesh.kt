package io.github.kurtking514_byte.capricorn.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Smooth "2.5D" motion for a flat Capri sprite.
 *
 * The sprite is drawn on a grid of points (a mesh). Every frame the points are moved a
 * little, so the picture itself bends: her hair ripples, her body sways and breathes,
 * she leans with the phone's tilt, and she squashes and stretches when poked. Her feet
 * (the bottom row) stay planted. Plain Kotlin, shared by the home screen and the overlay.
 */
object CapriMesh {
    const val COLS = 8
    const val ROWS = 12

    fun newVerts() = FloatArray((COLS + 1) * (ROWS + 1) * 2)

    /**
     * @param t       seconds, keeps increasing
     * @param tiltX   -1..1, phone tilted left/right
     * @param tiltY   -1..1, phone tilted away/toward
     * @param bounce  spring value: positive = squash (short and wide), negative = stretch
     * @param life    0..1 scales the idle motion (sway, breathing, hair)
     */
    fun fill(
        verts: FloatArray,
        left: Float, top: Float, w: Float, h: Float,
        t: Float, tiltX: Float, tiltY: Float, bounce: Float, life: Float = 1f,
    ) {
        var i = 0
        val breath = (sin(t * 1.5f) * 0.5f + 0.5f)
        for (r in 0..ROWS) {
            val v = r / ROWS.toFloat()
            val up = 1f - v                                   // 0 at her feet, 1 at the top
            var hw = ((0.5f - v) / 0.4f).coerceIn(0f, 1f)     // how much this row is hair
            hw = hw * hw * (3f - 2f * hw)
            val upCurve = up.pow(1.4f)
            for (c in 0..COLS) {
                val u = c / COLS.toFloat()
                val edge = 0.35f + 0.65f * abs((u - 0.5f) * 2f) // hair tips move more than her face

                // squash and stretch around her feet
                var x = left + w / 2f + (u - 0.5f) * w * (1f + 0.05f * bounce)
                var y = top + h - (h - v * h) * (1f - 0.07f * bounce)

                // slow body sway + breathing
                x += sin(t * 0.8f + v * 1.6f) * 0.012f * h * upCurve * life
                y -= breath * 0.010f * h * up * life

                // hair ripple
                x += sin(t * 1.9f + u * 5.0f + v * 7.0f) * 0.011f * h * hw * edge * life
                y += cos(t * 1.5f + u * 6.5f) * 0.008f * h * hw * edge * life

                // depth: lean with the phone's tilt, hair lagging further
                x += tiltX * (0.045f * h * up + 0.030f * h * hw)
                y += tiltY * 0.020f * h * up

                // hair follows the bounce late
                y += bounce * 0.02f * h * hw * edge

                verts[i++] = x
                verts[i++] = y
            }
        }
    }
}
