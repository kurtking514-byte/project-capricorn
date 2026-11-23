package io.github.kurtking514_byte.capricorn.capri

import kotlin.math.hypot
import kotlin.math.max

/**
 * 3.5: reads one finger stroke as a letter A–Z (same reader as preview/letter-lab.html).
 * The stroke is resampled to [N] points, scaled evenly (no stretching, no rotating) and centred,
 * then compared with 1–2 hand-made templates per letter. Strokes drawn backwards also count, a
 * little worse. Tested in the preview on messy strokes: ~95% of letters right, ~81% of scribbles
 * rejected at [MATCH_MAX] = 13.
 */
object LetterReader {
    private const val N = 48
    private const val MATCH_MAX = 13f
    private const val BACKWARDS_PENALTY = 2f

    /** Templates in a 100×100 box, y down, in the usual writing direction. */
    private val RAW: Map<Char, List<List<Pair<Float, Float>>>> = mapOf(
        'A' to listOf(p(0, 100, 50, 0, 100, 100), p(0, 100, 50, 0, 100, 100, 75, 55, 25, 55)),
        'B' to listOf(p(0, 100, 0, 0, 60, 5, 78, 25, 60, 46, 10, 50, 70, 55, 88, 78, 66, 98, 0, 100)),
        'C' to listOf(p(90, 12, 55, 0, 18, 12, 0, 50, 18, 88, 55, 100, 90, 88)),
        'D' to listOf(p(0, 100, 0, 0, 55, 8, 92, 50, 55, 92, 0, 100)),
        'E' to listOf(p(90, 10, 35, 0, 10, 22, 45, 48, 10, 75, 35, 100, 90, 90), p(90, 0, 0, 0, 0, 50, 65, 50, 0, 50, 0, 100, 90, 100)),
        'F' to listOf(p(90, 0, 0, 0, 0, 100), p(90, 0, 0, 0, 0, 50, 60, 50, 0, 50, 0, 100)),
        'G' to listOf(p(90, 12, 50, 0, 8, 25, 5, 72, 45, 100, 88, 85, 90, 55, 55, 55)),
        'H' to listOf(p(0, 0, 0, 100, 0, 55, 100, 50, 100, 100), p(0, 0, 0, 100, 0, 60, 50, 42, 100, 60, 100, 100)),
        'I' to listOf(p(50, 0, 50, 100)),
        'J' to listOf(p(80, 0, 80, 78, 60, 100, 22, 100, 0, 80)),
        'K' to listOf(p(85, 0, 0, 50, 85, 100), p(0, 0, 0, 100, 0, 60, 80, 0, 30, 55, 85, 100)),
        'L' to listOf(p(0, 0, 0, 100, 90, 100)),
        'M' to listOf(p(0, 100, 0, 0, 50, 60, 100, 0, 100, 100)),
        'N' to listOf(p(0, 100, 0, 0, 100, 100, 100, 0)),
        'O' to listOf(
            p(50, 0, 15, 15, 0, 50, 15, 85, 50, 100, 85, 85, 100, 50, 85, 15, 50, 0),
            p(50, 0, 85, 15, 100, 50, 85, 85, 50, 100, 15, 85, 0, 50, 15, 15, 50, 0),
        ),
        'P' to listOf(p(0, 100, 0, 0, 60, 5, 82, 25, 60, 48, 0, 50)),
        'Q' to listOf(p(50, 0, 15, 15, 0, 50, 15, 85, 50, 100, 85, 85, 100, 50, 85, 15, 50, 0, 60, 60, 100, 100)),
        'R' to listOf(p(0, 100, 0, 0, 60, 5, 82, 25, 60, 48, 0, 50, 90, 100)),
        'S' to listOf(p(90, 10, 50, 0, 10, 15, 18, 40, 82, 60, 90, 85, 50, 100, 8, 90)),
        'T' to listOf(p(0, 0, 50, 0, 100, 0, 50, 0, 50, 100)),
        'U' to listOf(p(0, 0, 0, 70, 20, 100, 80, 100, 100, 70, 100, 0)),
        'V' to listOf(p(0, 0, 50, 100, 100, 0)),
        'W' to listOf(p(0, 0, 25, 100, 50, 40, 75, 100, 100, 0)),
        'X' to listOf(p(0, 0, 100, 100, 100, 0, 0, 100)),
        'Y' to listOf(p(0, 0, 50, 50, 100, 0, 50, 50, 50, 100), p(0, 0, 40, 55, 100, 0, 60, 60, 40, 100)),
        'Z' to listOf(p(0, 0, 100, 0, 0, 100, 100, 100)),
    )

    private class Template(val letter: Char, val pts: Array<FloatArray>)

    private val templates: List<Template> by lazy {
        RAW.flatMap { (letter, list) -> list.map { Template(letter, normalize(it)) } }
    }

    /** The letter, or null if the stroke doesn't look like one. Points are screen px (x, y). */
    fun read(stroke: List<Pair<Float, Float>>): Char? {
        if (stroke.size < 4) return null
        val c = normalize(stroke)
        val rc = c.reversedArray()
        var best: Template? = null
        var bestD = Float.MAX_VALUE
        for (t in templates) {
            val d = minOf(dist(c, t.pts), dist(rc, t.pts) + BACKWARDS_PENALTY)
            if (d < bestD) { bestD = d; best = t }
        }
        return if (bestD <= MATCH_MAX) best?.letter else null
    }

    private fun p(vararg v: Int): List<Pair<Float, Float>> =
        v.toList().chunked(2) { (x, y) -> x.toFloat() to y.toFloat() }

    private fun dist(a: Array<FloatArray>, b: Array<FloatArray>): Float {
        var s = 0f
        for (i in 0 until N) s += hypot(a[i][0] - b[i][0], a[i][1] - b[i][1])
        return s / N
    }

    private fun normalize(pts: List<Pair<Float, Float>>): Array<FloatArray> {
        val r = resample(pts)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var cx = 0f; var cy = 0f
        for (q in r) {
            minX = minOf(minX, q[0]); maxX = maxOf(maxX, q[0]); minY = minOf(minY, q[1]); maxY = maxOf(maxY, q[1])
            cx += q[0]; cy += q[1]
        }
        cx /= N; cy /= N
        val s = max(maxX - minX, maxY - minY).takeIf { it > 0f } ?: 1f
        return Array(N) { i -> floatArrayOf((r[i][0] - cx) * 100f / s, (r[i][1] - cy) * 100f / s) }
    }

    private fun resample(pts: List<Pair<Float, Float>>): Array<FloatArray> {
        val p = pts.map { floatArrayOf(it.first, it.second) }.toMutableList()
        var total = 0f
        for (i in 1 until p.size) total += hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1])
        val step = total / (N - 1)
        val out = mutableListOf(p[0].copyOf())
        var acc = 0f
        var i = 1
        while (i < p.size) {
            val d = hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1])
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                val q = floatArrayOf(p[i - 1][0] + t * (p[i][0] - p[i - 1][0]), p[i - 1][1] + t * (p[i][1] - p[i - 1][1]))
                out.add(q)
                p.add(i, q)
                acc = 0f
            } else {
                acc += d
            }
            i++
        }
        while (out.size < N) out.add(p.last().copyOf())
        return Array(N) { out[it] }
    }
}
