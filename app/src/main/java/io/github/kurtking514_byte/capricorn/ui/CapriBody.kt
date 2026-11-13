package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlin.math.cos
import kotlin.math.sin

/*
 * Capri's full body, drawn in code in her cut-out style (our own drawing, no show art):
 * a huge crimson shard-hair mass with sparkle static, the green face panel, a thin black
 * body with a green chest V, arms akimbo, and shard legs tapering to points.
 *
 * Pass a new [seed] about 9 times a second to get her jittery, hand-cut motion:
 * every shard tip and joint is nudged by a small amount that depends on the seed.
 */

private val Violet = Color(0xFF7A3CFF)

/** One hair shard: direction (0° = right, -90° = up), length and sideways kink, in body-heights. */
private class Shard(val angle: Float, val len: Float, val kink: Float)

private val HAIR = listOf(
    Shard(-178f, .56f, .05f), Shard(-163f, .44f, -.04f), Shard(-150f, .60f, .05f),
    Shard(-134f, .40f, -.03f), Shard(-118f, .47f, .04f), Shard(-102f, .33f, -.03f),
    Shard(-88f, .40f, .03f), Shard(-73f, .31f, -.03f), Shard(-58f, .46f, .04f),
    Shard(-43f, .38f, -.04f), Shard(-28f, .58f, .05f), Shard(-14f, .42f, -.04f),
    Shard(0f, .54f, .05f), Shard(16f, .36f, -.03f), Shard(34f, .44f, .04f),
    Shard(164f, .38f, .03f), Shard(146f, .46f, -.04f),
)
private val GREEN_SHARDS = setOf(2, 8, 12, 16)

/** Cheap deterministic noise in -1..1. */
internal fun capriJitter(seed: Int, i: Int): Float = jit(seed, i)

private fun jit(seed: Int, i: Int): Float {
    var x = seed * 374761393 + i * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 32767.5f - 1f
}

private fun ease(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return 1f - (1f - c) * (1f - c) * (1f - c)
}

/**
 * @param center middle of the figure
 * @param u      body height unit in px; the figure spans about 1.25u wide and 1.2u tall
 * @param seed   jitter frame; change it to make her twitch
 * @param grow   0..1 materialize amount (hair shards shoot out one after another)
 * @param open   eye openness 0..1
 * @param talk   0..1 how wide her mouth is open
 */
fun DrawScope.drawCapriBody(
    center: Offset,
    u: Float,
    seed: Int,
    grow: Float = 1f,
    open: Float = 1f,
    talk: Float = 0f,
    alpha: Float = 1f,
) {
    if (grow <= 0f || alpha <= 0f) return
    val hc = Offset(center.x, center.y - 0.07f * u)          // head center
    val hairC = Offset(hc.x, hc.y - 0.02f * u)
    val wobble = 0.013f * u
    fun p(x: Float, y: Float, j: Int = -1): Offset {
        val jx = if (j >= 0) jit(seed, j * 2) * wobble else 0f
        val jy = if (j >= 0) jit(seed, j * 2 + 1) * wobble else 0f
        return Offset(hc.x + x * u + jx, hc.y + y * u + jy)
    }

    // ---- hair ----
    val hair = Path()
    val bw = 0.10f * u
    val greenBits = Path()
    HAIR.forEachIndexed { i, s ->
        val g = ease(grow * 1.7f - i * 0.04f)
        if (g <= 0f) return@forEachIndexed
        val a = Math.toRadians(s.angle.toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val px = -dy
        val py = dx
        val len = s.len * u * g
        val tip = Offset(
            hairC.x + dx * len + jit(seed, 100 + i * 2) * wobble * 1.4f,
            hairC.y + dy * len + jit(seed, 101 + i * 2) * wobble * 1.4f,
        )
        val k = s.kink * u
        hair.moveTo(hairC.x - px * bw, hairC.y - py * bw)
        hair.lineTo(hairC.x + dx * len * .55f + px * (k - bw * .5f), hairC.y + dy * len * .55f + py * (k - bw * .5f))
        hair.lineTo(tip.x, tip.y)
        hair.lineTo(hairC.x + dx * len * .45f + px * bw * .9f, hairC.y + dy * len * .45f + py * bw * .9f)
        hair.lineTo(hairC.x + px * bw, hairC.y + py * bw)
        hair.close()

        if (i in GREEN_SHARDS) {
            val mx = hairC.x + dx * len * .62f
            val my = hairC.y + dy * len * .62f
            greenBits.moveTo(mx, my)
            greenBits.lineTo(mx + dx * .13f * u * g, my + dy * .13f * u * g)
            greenBits.lineTo(mx + px * .045f * u, my + py * .045f * u)
            greenBits.close()
        }
    }
    // jagged core so the shards read as one mass
    for (n in 0 until 7) {
        val a = Math.toRadians(n * 360.0 / 7 - 90)
        val r = 0.20f * u * ease(grow * 2f)
        val x = hairC.x + cos(a).toFloat() * r + jit(seed, 300 + n) * wobble
        val y = hairC.y + sin(a).toFloat() * r + jit(seed, 320 + n) * wobble
        if (n == 0) hair.moveTo(x, y) else hair.lineTo(x, y)
    }
    hair.close()

    // offset violet copy behind = her cut-out edge
    drawPath(Path().apply { addPath(hair, Offset(0.012f * u, 0.010f * u)) }, Violet.copy(alpha = 0.9f * alpha))
    drawPath(hair, CapriColors.Pink.copy(alpha = alpha))
    // sparkle static inside the hair
    clipPath(hair) {
        val white = Color.White.copy(alpha = 0.9f * alpha)
        for (k in 0 until 170) {
            val x = hairC.x + jit(seed, 500 + k * 2) * 0.50f * u
            val y = hairC.y - 0.06f * u + jit(seed, 501 + k * 2) * 0.36f * u
            val s = (0.004f + 0.006f * ((k % 5) / 4f)) * u
            drawRect(white, Offset(x, y), Size(s, s))
        }
    }
    drawPath(greenBits, CapriColors.Green.copy(alpha = alpha))

    val bodyA = alpha * ((grow - 0.35f) / 0.4f).coerceIn(0f, 1f)
    if (bodyA > 0f) {
        val black = Color.Black.copy(alpha = bodyA)
        val edge = Violet.copy(alpha = 0.85f * bodyA)
        val edgeStroke = Stroke(width = 0.007f * u, join = StrokeJoin.Miter)
        fun solid(path: Path, fill: Color) {
            drawPath(path, fill)
            drawPath(path, edge, style = edgeStroke)
        }
        fun poly(pts: List<Offset>) = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (n in 1 until pts.size) lineTo(pts[n].x, pts[n].y)
            close()
        }

        // ---- skirt shards + legs ----
        val pink = CapriColors.Pink.copy(alpha = bodyA)
        solid(poly(listOf(p(-.03f, .31f), p(.0f, .35f), p(-.16f, .50f, 40))), black)
        solid(poly(listOf(p(.03f, .31f), p(.0f, .35f), p(.16f, .48f, 41))), black)
        solid(poly(listOf(p(-.04f, .32f), p(.0f, .35f), p(-.05f, .64f, 42))), pink)
        solid(poly(listOf(p(.04f, .32f), p(.0f, .35f), p(.055f, .64f, 43))), pink)
        drawPath(poly(listOf(p(-.014f, .38f), p(.014f, .38f), p(.0f, .50f, 44))), black)

        // ---- arms akimbo ----
        fun arm(side: Float, j: Int) {
            val path = Path().apply {
                val s = p(side * .07f, .16f)
                val e = p(side * .17f, .25f, j)
                val h = p(side * .05f, .33f)
                moveTo(s.x, s.y); lineTo(e.x, e.y); lineTo(h.x, h.y)
            }
            drawPath(path, edge, style = Stroke(0.034f * u, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            drawPath(path, black, style = Stroke(0.022f * u, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
        }
        arm(-1f, 45)
        arm(1f, 46)

        // ---- torso + chest V ----
        solid(
            poly(listOf(p(-.04f, .11f), p(.04f, .11f), p(.075f, .15f), p(.03f, .34f), p(-.03f, .34f), p(-.075f, .15f))),
            black,
        )
        drawPath(poly(listOf(p(-.032f, .17f), p(.032f, .17f), p(.0f, .27f))), CapriColors.Green.copy(alpha = bodyA))

        // ---- head: black horns behind the face panel ----
        solid(
            poly(
                listOf(
                    p(-.15f, .0f), p(-.185f, -.13f, 47), p(-.09f, -.075f), p(.0f, -.105f, 48),
                    p(.09f, -.075f), p(.195f, -.14f, 49), p(.15f, .0f), p(.05f, .13f), p(-.05f, .13f),
                )
            ),
            black,
        )
    }

    val faceIn = ((grow - 0.45f) / 0.3f).coerceIn(0f, 1f)
    drawCapriFace(
        center = hc,
        width = 0.30f * u,
        open = open * faceIn,
        frame = ease(faceIn),
        mouth = faceIn,
        talk = talk,
        tilt = jit(seed, 900) * 1.5f,
        alpha = alpha,
    )
}
