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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.random.Random

/**
 * Mini Capri for the floating overlay: a small plain View (no Compose, to stay light).
 * She moves smoothly with the same mesh warp as the home screen: sways, breathes, her
 * hair ripples, she blinks, and she bounces like a spring when touched.
 * Drawn at about 30 fps to go easy on the battery; paused while the screen is off.
 * Without a sprite skin it falls back to the app icon.
 */
class MiniCapriView(context: Context, skin: CapriSkin?) : View(context) {

    private val neutral: Bitmap? = skin?.bitmap("neutral")
    private val blinkHalf: Bitmap? = skin?.bitmap("idle_blink_half")
    private val blinkClosed: Bitmap? = skin?.bitmap("idle_blink_closed")
    private val happy: Bitmap? = skin?.bitmap("idle_smirk")
    private val fallback = if (neutral == null) ContextCompat.getDrawable(context, R.mipmap.ic_launcher) else null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val verts = CapriMesh.newVerts()
    private val power = context.getSystemService(PowerManager::class.java)
    private val born = SystemClock.uptimeMillis()

    private var blinkAt = born + 3000        // when the current/next blink starts
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

        // blink: half → closed → half over about a quarter second
        val sinceBlink = now - blinkAt
        if (sinceBlink > 260) blinkAt = now + Random.nextLong(2200, 5500)
        val frame = when {
            now < happyUntil -> happy ?: neutral
            sinceBlink in 70..180 -> blinkClosed ?: neutral
            sinceBlink in 0..260 -> blinkHalf ?: neutral
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
