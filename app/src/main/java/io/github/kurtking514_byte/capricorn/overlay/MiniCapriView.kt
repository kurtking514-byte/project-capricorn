package io.github.kurtking514_byte.capricorn.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.PowerManager
import android.view.View
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import io.github.kurtking514_byte.capricorn.R
import io.github.kurtking514_byte.capricorn.ui.CapriSkin
import kotlin.random.Random

/**
 * Mini Capri for the floating overlay: a small plain View (no Compose, to stay light)
 * that draws one sprite frame with her jitter, blinks, and can flash a happy pose.
 * Without a sprite skin it falls back to the app icon.
 */
class MiniCapriView(context: Context, skin: CapriSkin?) : View(context) {

    private val neutral: Bitmap? = skin?.frame("neutral")?.asAndroidBitmap()
    private val blinkHalf: Bitmap? = skin?.frame("idle_blink_half")?.asAndroidBitmap()
    private val blinkClosed: Bitmap? = skin?.frame("idle_blink_closed")?.asAndroidBitmap()
    private val happy: Bitmap? = skin?.frame("idle_happy")?.asAndroidBitmap()
    private val fallback = if (neutral == null) ContextCompat.getDrawable(context, R.mipmap.ic_launcher) else null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val power = context.getSystemService(PowerManager::class.java)

    private var jx = 0f
    private var jy = 0f
    private var jr = 0f
    private var blinkStep = 0
    private var nextBlinkAt = System.currentTimeMillis() + 3000
    private var happyUntil = 0L

    private val tick = object : Runnable {
        override fun run() {
            // Don't animate while the screen is off or she's hidden.
            if (!power.isInteractive || !isShown) {
                postDelayed(this, 1500)
                return
            }
            val now = System.currentTimeMillis()
            val amp = height * 0.006f
            jx = (Random.nextFloat() * 2 - 1) * amp
            jy = (Random.nextFloat() * 2 - 1) * amp
            jr = (Random.nextFloat() * 2 - 1) * 0.6f
            if (blinkStep > 0) {
                blinkStep = (blinkStep + 1) % 4
            } else if (now >= nextBlinkAt) {
                blinkStep = 1
                nextBlinkAt = now + Random.nextLong(2200, 5500)
            }
            invalidate()
            postDelayed(this, 110)
        }
    }

    /** Briefly switch to her happy pose (after she does something for you). */
    fun cheer() {
        happyUntil = System.currentTimeMillis() + 1400
        invalidate()
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
        val frame = when {
            System.currentTimeMillis() < happyUntil -> happy ?: neutral
            blinkStep == 2 -> blinkClosed ?: neutral
            blinkStep == 1 || blinkStep == 3 -> blinkHalf ?: neutral
            else -> neutral
        }
        canvas.save()
        canvas.rotate(jr, width / 2f, height.toFloat())
        canvas.translate(jx, jy)
        if (frame != null) {
            val scale = minOf(width.toFloat() / frame.width, height.toFloat() / frame.height)
            val w = frame.width * scale
            val h = frame.height * scale
            dst.set((width - w) / 2f, height - h, (width + w) / 2f, height.toFloat())
            canvas.drawBitmap(frame, null, dst, paint)
        } else {
            fallback?.setBounds(0, height - width, width, height)
            fallback?.draw(canvas)
        }
        canvas.restore()
    }
}
