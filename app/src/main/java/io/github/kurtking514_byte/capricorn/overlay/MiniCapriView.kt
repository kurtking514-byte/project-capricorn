package io.github.kurtking514_byte.capricorn.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import androidx.core.content.ContextCompat
import io.github.kurtking514_byte.capricorn.R
import io.github.kurtking514_byte.capricorn.ui.CapriMesh
import io.github.kurtking514_byte.capricorn.ui.CapriSkin
import io.github.kurtking514_byte.capricorn.ui.BLINK_CLOSED_MS
import io.github.kurtking514_byte.capricorn.ui.BLINK_DOUBLE_APART_MS
import io.github.kurtking514_byte.capricorn.ui.BLINK_DOUBLE_CHANCE
import io.github.kurtking514_byte.capricorn.ui.BLINK_GAP_MAX_MS
import io.github.kurtking514_byte.capricorn.ui.BLINK_GAP_MIN_MS
import kotlin.math.cos
import kotlin.math.exp
import kotlin.random.Random

/**
 * Mini Capri for the floating overlay: a small plain View (no Compose, to stay light).
 * Drawn on the same still mesh as the home screen (no sway, breathing or bounce, like the
 * show); she blinks.
 * Drawn at about 30 fps to go easy on the battery; paused while the screen is off.
 * Without a sprite skin it falls back to the app icon.
 */
class MiniCapriView(context: Context, skin: CapriSkin?) : View(context) {

    private val neutral: Bitmap? = skin?.bitmap("neutral")
    private val blinkClosed: Bitmap? = skin?.bitmap("idle_blink_closed")
    private val happy: Bitmap? = skin?.bitmap("idle_smirk")
    private val fallback = if (neutral == null) ContextCompat.getDrawable(context, R.mipmap.ic_launcher) else null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val verts = CapriMesh.newVerts()
    private val power = context.getSystemService(PowerManager::class.java)
    private val born = SystemClock.uptimeMillis()

    private var blinkAt = born + 3000        // when the current/next blink starts
    private var blinkDouble = false          // this blink is a double blink
    private var happyUntil = 0L
    private var bounceAt = 0L                // when she was last poked
    private var bounceSize = 0f

    private val tick = object : Runnable {
        override fun run() {
            if (!power.isInteractive || !isShown) {
                postDelayed(this, 1200)
                return
            }
            invalidate()
            postDelayed(this, 33)
        }
    }

    /** Spring bounce: positive squashes her first, then she wobbles back. */
    fun bounce(strength: Float = 1f) {
        bounceAt = SystemClock.uptimeMillis()
        bounceSize = strength
        invalidate()
    }

    /** Briefly switch to her happy pose (after she does something for you). */
    fun cheer() {
        happyUntil = SystemClock.uptimeMillis() + 1400
        bounce(0.7f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val t = (now - born) / 1000f

        // Show-style blink (same timing as the home screen): eyes gone for 83 ms, no half step,
        // irregular gaps, sometimes a second blink 333 ms after the first.
        val sinceBlink = now - blinkAt
        val blinkLen = if (blinkDouble) BLINK_DOUBLE_APART_MS + BLINK_CLOSED_MS else BLINK_CLOSED_MS
        if (sinceBlink > blinkLen) {
            blinkAt = now + Random.nextLong(BLINK_GAP_MIN_MS, BLINK_GAP_MAX_MS)
            blinkDouble = Random.nextFloat() < BLINK_DOUBLE_CHANCE
        }
        val closed = sinceBlink in 0 until BLINK_CLOSED_MS ||
            (blinkDouble && sinceBlink in BLINK_DOUBLE_APART_MS until BLINK_DOUBLE_APART_MS + BLINK_CLOSED_MS)
        val frame = when {
            now < happyUntil -> happy ?: neutral
            closed -> blinkClosed ?: neutral
            else -> neutral
        }

        // damped spring, so a tap makes her squash, stretch and settle
        val age = (now - bounceAt) / 1000f
        val spring = if (age < 1.6f) bounceSize * exp(-4.5f * age) * cos(17f * age) else 0f

        if (frame != null) {
            // leave a little room around her for the sway
            val fit = minOf(width / (0.80f * frame.width), height / (0.90f * frame.height))
            val w = frame.width * fit
            val h = frame.height * fit
            CapriMesh.fill(verts, (width - w) / 2f, height - 0.93f * h, w, h, t, 0f, 0f, spring)
            canvas.drawBitmapMesh(frame, CapriMesh.COLS, CapriMesh.ROWS, verts, 0, null, 0, paint)
        } else {
            fallback?.setBounds(0, height - width, width, height)
            fallback?.draw(canvas)
        }
    }
}
