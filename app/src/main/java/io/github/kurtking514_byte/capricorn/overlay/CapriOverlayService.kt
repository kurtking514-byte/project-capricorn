package io.github.kurtking514_byte.capricorn.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import io.github.kurtking514_byte.capricorn.R
import io.github.kurtking514_byte.capricorn.capri.FaceDown
import io.github.kurtking514_byte.capricorn.ui.CapriSkin
import kotlin.math.abs

/**
 * Mini Capri floating over other apps, with quick controls.
 *
 * This is an accessibility service because that lets her (1) float over apps without a
 * separate "draw over apps" permission or a permanent notification, and (2) take
 * screenshots and lock the screen. It does NOT read what's on your screen: it only
 * listens for which app comes to the front, so she can hide on her own home screen.
 *
 *   tap         → open / close quick controls
 *   double-tap  → screenshot
 *   long-press  → back to Capri's home (chatbox)
 *   drag        → move her; she snaps to the nearest edge
 */
class CapriOverlayService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var mini: MiniCapriView? = null
    private var miniParams: WindowManager.LayoutParams? = null
    private var panel: View? = null

    private var atHome = false
    private var hiddenByUser = false

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
    private val miniW get() = dp(84f)
    private val miniH get() = dp(96f)

    // 3.10: face down = quiet, in every app, but only while the screen is on
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            FaceDown.watch(this@CapriOverlayService, "overlay", intent.action == android.content.Intent.ACTION_SCREEN_ON)
        }
    }

    override fun onServiceConnected() {
        instance = this
        io.github.kurtking514_byte.capricorn.data.Gestures.init(this)
        wm = getSystemService(WindowManager::class.java)
        addMini()
        registerReceiver(screenReceiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
        })
        FaceDown.watch(this, "overlay", getSystemService(android.os.PowerManager::class.java)?.isInteractive != false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        if (cls.contains("inputmethod", ignoreCase = true) || cls.contains("SoftInputWindow")) return
        if (pkg == "com.android.systemui") return
        if (pkg == packageName) {
            // Only our real home screen counts; our own overlay windows don't.
            if (cls.endsWith("MainActivity")) {
                atHome = true
                hiddenByUser = false
                refresh()
            }
        } else {
            atHome = false
            refresh()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        FaceDown.watch(this, "overlay", false)
        closePanel()
        mini?.let { runCatching { wm.removeView(it) } }
        mini = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ---- actions (also used by the chatbox) ----

    fun screenshot() {
        closePanel()
        // Hide her for a moment so she isn't in the picture.
        mini?.visibility = View.GONE
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }, 180)
        handler.postDelayed({
            refresh()
            mini?.cheer()
        }, 1400)
    }

    fun lockScreen() {
        closePanel()
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    // ---- mini Capri window ----

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    private fun addMini() {
        if (mini != null) return
        val view = MiniCapriView(this, CapriSkin.load(this))
        val screen = resources.displayMetrics
        val params = overlayParams(miniW, miniH).apply {
            x = screen.widthPixels - miniW
            y = (screen.heightPixels * 0.55f).toInt()
        }

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (panel == null) openPanel() else closePanel()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                screenshot()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                closePanel()
                QuickActions.openHome(this@CapriOverlayService)
            }
        })

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.bounce(0.8f)
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        closePanel()
                        // cancel any pending tap / long-press
                        val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                        gestures.onTouchEvent(cancel)
                        cancel.recycle()
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = (startY + dy.toInt()).coerceIn(0, screen.heightPixels - miniH)
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    // snap to the nearest side edge
                    val mid = params.x + miniW / 2
                    val target = if (mid < screen.widthPixels / 2) 0 else screen.widthPixels - miniW
                    ValueAnimator.ofInt(params.x, target).apply {
                        duration = 320
                        interpolator = OvershootInterpolator(1.6f)
                        addUpdateListener {
                            params.x = it.animatedValue as Int
                            runCatching { wm.updateViewLayout(view, params) }
                        }
                        start()
                    }
                    view.bounce(0.6f)
                }
            }
            if (!dragging) gestures.onTouchEvent(e)
            true
        }

        runCatching { wm.addView(view, params) }
        mini = view
        miniParams = params
        refresh()
    }

    private fun refresh() {
        val show = !atHome && !hiddenByUser
        mini?.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) closePanel()
    }

    // ---- quick controls panel ----

    @SuppressLint("ClickableViewAccessibility")
    private fun openPanel() {
        val anchor = miniParams ?: return
        val ink = Color.parseColor("#080E15")
        val cyan = Color.parseColor("#8EF5ED")
        val crimson = Color.parseColor("#FF3C68")
        val text = Color.parseColor("#E6F4F4")
        val font = runCatching { ResourcesCompat.getFont(this, R.font.exo2) }.getOrNull()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            background = GradientDrawable().apply {
                setColor(ink)
                setStroke(dp(1f), cyan)
            }
        }
        column.addView(TextView(this).apply {
            this.text = "CAPRI  /  QUICK"
            setTextColor(crimson)
            textSize = 11f
            letterSpacing = 0.12f
            typeface = font
            setPadding(dp(6f), 0, dp(6f), dp(6f))
        })

        fun button(label: String, action: () -> Unit) {
            column.addView(TextView(this).apply {
                this.text = label
                setTextColor(text)
                textSize = 15f
                typeface = font
                setPadding(dp(10f), dp(9f), dp(10f), dp(9f))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0C1A22"))
                    setStroke(dp(1f), Color.parseColor("#2A5560"))
                }
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4f)
            })
        }

        button("Screenshot") { screenshot() }
        button("Flashlight") { QuickActions.toggleTorch(this); mini?.cheer() }
        button("Camera") { closePanel(); QuickActions.openCamera(this) }
        button("Lock screen") { lockScreen() }
        button("Talk to Capri") { closePanel(); QuickActions.openHome(this) }
        button("Hide me for now") { hiddenByUser = true; refresh() }

        val width = dp(168f)
        val screen = resources.displayMetrics
        val params = overlayParams(width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            val onRight = anchor.x + miniW / 2 > screen.widthPixels / 2
            x = if (onRight) anchor.x - width - dp(6f) else anchor.x + miniW + dp(6f)
            y = (anchor.y - dp(60f)).coerceIn(dp(40f), screen.heightPixels - dp(330f))
        }
        // Touching anywhere outside the panel closes it.
        column.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }
        runCatching { wm.addView(column, params) }
        panel = column

        // Unfold out of her side instead of popping in, buttons following one by one.
        val onRight = anchor.x + miniW / 2 > screen.widthPixels / 2
        column.post {
            column.pivotX = if (onRight) column.width.toFloat() else 0f
            column.pivotY = column.height / 2f
        }
        column.alpha = 0f
        column.scaleX = 0.6f
        column.scaleY = 0.85f
        column.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(240).setInterpolator(OvershootInterpolator(1.4f)).start()
        for (i in 0 until column.childCount) {
            val child = column.getChildAt(i)
            child.alpha = 0f
            child.translationX = if (onRight) dp(24f).toFloat() else -dp(24f).toFloat()
            child.animate().alpha(1f).translationX(0f)
                .setStartDelay(60L + i * 35L).setDuration(220)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    private fun closePanel() {
        val closing = panel ?: return
        panel = null
        closing.animate().alpha(0f).scaleX(0.7f).scaleY(0.9f).setDuration(140)
            .withEndAction { runCatching { wm.removeView(closing) } }.start()
    }

    companion object {
        /** The running service, or null when the overlay is switched off in Accessibility settings. */
        var instance: CapriOverlayService? = null
            private set
    }
}
