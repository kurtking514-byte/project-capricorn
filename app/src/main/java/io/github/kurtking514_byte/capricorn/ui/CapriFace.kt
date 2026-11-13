package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors

/*
 * Capri's floating face, drawn in code in her design language (our own drawing, no show art):
 * one flat green panel with notched, slanted edges; two sharp black eyes with blue iris rings
 * and white highlights; a separate jagged mouth floating underneath.
 */

private val IrisDark = Color(0xFF1F4FE0)
private val GlitchViolet = Color(0xFF7A3CFF)

/**
 * @param width  panel width in px (panel height is 0.42 × width)
 * @param open   0 = eyes shut, 1 = wide open
 * @param frame  0..1 how far the panel has materialized (grows out from the center)
 * @param mouth  0..1 mouth visibility
 * @param talk   0..1 how wide the mouth is open (0 = closed zigzag)
 * @param tilt   degrees to tilt the whole face
 * @param glitch 0..1 strength of the pink/blue split outlines
 */
fun DrawScope.drawCapriFace(
    center: Offset,
    width: Float,
    open: Float,
    frame: Float = 1f,
    mouth: Float = 1f,
    talk: Float = 0f,
    tilt: Float = 0f,
    glitch: Float = 0f,
    alpha: Float = 1f,
) {
    if (frame <= 0f) return
    val h = width * 0.42f

    rotate(tilt, pivot = center) {
        withTransform({ scale(scaleX = frame.coerceAtMost(1f), scaleY = 1f, pivot = center) }) {
            if (glitch > 0f) {
                val dx = width * 0.05f * glitch
                drawPath(panelPath(center + Offset(-dx, 0f), width, h), CapriColors.Pink.copy(alpha = 0.8f * alpha), style = Stroke(width * 0.014f))
                drawPath(panelPath(center + Offset(dx, h * 0.05f), width, h), CapriColors.Blue.copy(alpha = 0.7f * alpha), style = Stroke(width * 0.014f))
            }

            val panel = panelPath(center, width, h)
            drawPath(panel, CapriColors.Green.copy(alpha = alpha))
            drawPath(panel, GlitchViolet.copy(alpha = 0.55f * alpha), style = Stroke(width * 0.008f))

            val eyeY = center.y - h * 0.06f
            drawPanelEye(Offset(center.x - width * 0.22f, eyeY), width, h, open, mirror = -1f, alpha = alpha)
            drawPanelEye(Offset(center.x + width * 0.22f, eyeY), width, h, open, mirror = 1f, alpha = alpha)
        }
        if (mouth > 0f) drawMouth(center, width, h, talk, mouth * alpha)
    }
}

/** Trapezoid panel, wider on top, with a notch in the top-right and slanted sides. */
private fun panelPath(c: Offset, w: Float, h: Float): Path = Path().apply {
    val l = c.x - w / 2f
    val r = c.x + w / 2f
    val t = c.y - h / 2f
    val b = c.y + h / 2f
    moveTo(l + w * 0.04f, t)
    lineTo(r - w * 0.12f, t)
    lineTo(r - w * 0.08f, t + h * 0.10f)
    lineTo(r, t + h * 0.02f)
    lineTo(r - w * 0.12f, b)
    lineTo(l + w * 0.14f, b)
    lineTo(l, t + h * 0.12f)
    close()
}

/**
 * One sharp eye. [mirror] is -1 for her left eye, +1 for her right, so the outer corners
 * angle up and the little eyeliner spikes point outward on both sides.
 */
private fun DrawScope.drawPanelEye(ec: Offset, w: Float, h: Float, open: Float, mirror: Float, alpha: Float) {
    val half = w * 0.15f
    val outer = Offset(ec.x + mirror * half, ec.y - h * 0.06f)
    val inner = Offset(ec.x - mirror * half, ec.y + h * 0.04f)
    val black = Color.Black.copy(alpha = alpha)

    // Eyeliner spikes: one off the outer-bottom corner, one off the inner-top corner.
    drawPath(
        Path().apply {
            moveTo(outer.x, outer.y)
            lineTo(outer.x + mirror * w * 0.025f, outer.y + h * 0.18f)
            lineTo(outer.x - mirror * w * 0.05f, outer.y + h * 0.06f)
            close()
            moveTo(inner.x, inner.y)
            lineTo(inner.x - mirror * w * 0.03f, inner.y - h * 0.16f)
            lineTo(inner.x + mirror * w * 0.045f, inner.y - h * 0.04f)
            close()
        },
        black,
    )

    val o = open.coerceIn(0f, 1f)
    if (o <= 0.04f) {
        drawLine(black, inner, outer, strokeWidth = h * 0.07f, cap = StrokeCap.Round)
        return
    }

    val lift = h * 0.55f * o
    @Suppress("DEPRECATION")
    val eye = Path().apply {
        moveTo(inner.x, inner.y)
        quadraticBezierTo(ec.x, ec.y - lift, outer.x, outer.y)
        quadraticBezierTo(ec.x, ec.y + lift * 0.8f, inner.x, inner.y)
        close()
    }
    drawPath(eye, black)
    clipPath(eye) {
        drawCircle(IrisDark.copy(alpha = alpha), radius = h * 0.24f, center = ec)
        drawCircle(CapriColors.Blue.copy(alpha = alpha), radius = h * 0.17f, center = ec)
        drawCircle(Color.White.copy(alpha = alpha), radius = h * 0.10f, center = ec)
    }
}

/** Jagged mouth floating under the panel: a white zigzag, or an open fanged grin while she talks. */
private fun DrawScope.drawMouth(c: Offset, w: Float, h: Float, talk: Float, alpha: Float) {
    val y = c.y + h * 0.5f + h * 0.30f
    val mw = w * 0.15f
    val amp = h * 0.07f
    val white = Color.White.copy(alpha = alpha)
    if (talk > 0.05f) {
        val drop = h * 0.26f * talk
        val grin = Path().apply {
            moveTo(c.x - mw / 2f, y - amp)
            lineTo(c.x - mw / 6f, y)
            lineTo(c.x + mw / 6f, y - amp)
            lineTo(c.x + mw / 2f, y - amp * 0.4f)
            lineTo(c.x + mw / 8f, y + drop)
            lineTo(c.x - mw / 4f, y + drop * 0.7f)
            close()
        }
        drawPath(grin, white)
        return
    }
    val zigzag = Path().apply {
        moveTo(c.x - mw / 2f, y - amp * 0.3f)
        lineTo(c.x - mw / 6f, y + amp)
        lineTo(c.x + mw / 6f, y - amp)
        lineTo(c.x + mw / 2f, y + amp * 0.3f)
    }
    drawPath(
        zigzag,
        white,
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round, join = StrokeJoin.Miter),
    )
}

/** Faint horizontal scanlines for the hologram look. */
fun DrawScope.drawScanlines(alpha: Float = 0.05f) {
    val step = 4.dp.toPx()
    val color = Color.White.copy(alpha = alpha)
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}
