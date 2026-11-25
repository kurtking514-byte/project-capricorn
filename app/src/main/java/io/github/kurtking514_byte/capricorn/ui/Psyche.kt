package io.github.kurtking514_byte.capricorn.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.kurtking514_byte.capricorn.capri.ScreenTime
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors

/**
 * 4.1: which of her merged minds is in charge (designed in preview/psyche-lab.html; the user
 * kept 3 tells: favorite poses, idle rhythm, accent color; no tag under the clock).
 *
 * [poses] = the idle activities she leans toward (allowed standing poses only); between them she
 * goes back to neutral for [gap] ms, then holds one for [hold] ms. [glance] = chance of a quick
 * 250 ms look left/right during a hold. [stare] = picks one pose and holds it, no switching.
 * [accent] colors the clock and the speech bubble edge.
 */
enum class PsycheKind(
    val accent: Color,
    val poses: List<String>,
    val gap: LongRange,
    val hold: LongRange,
    val glance: Float = 0f,
    val stare: Boolean = false,
) {
    CALM(GLITCH_GREEN, listOf("idle_look_left", "idle_look_right", "idle_thinking"), 6_000L..9_000L, 6_000L..9_000L),
    NORMAL(
        CapriColors.Cyan,
        listOf("hand_on_hip_smirk", "arms_crossed_annoyed", "idle_shrug", "idle_smirk", "idle_look_left", "idle_look_right", "idle_thinking"),
        4_000L..8_000L, 6_000L..10_000L,
    ),
    CHAOTIC(
        Color(0xFF8A5CFF),
        listOf("idle_smirk", "idle_shrug", "idle_look_left", "idle_look_right", "hand_on_hip_smirk"),
        900L..2_200L, 900L..2_200L, glance = 0.45f,
    ),
    PROTECTIVE(CapriColors.Pink, listOf("arms_crossed_annoyed", "hand_on_hip_smirk"), 600L..600L, 0L..0L, stare = true),
}

/**
 * 4.1: PROTECTIVE whenever battery ≤ 15% (not charging) or screen time today ≥ 5 h; otherwise
 * CALM 5:00–10:59, NORMAL 11:00–23:59, CHAOTIC 0:00–4:59. Checked once a minute by the home screen.
 */
object Psyche {
    var current by mutableStateOf(PsycheKind.NORMAL)
        private set

    /** 4.3: the color the clock + speech bubble use right now (flickers old / new during a switch). */
    var accent by mutableStateOf(PsycheKind.NORMAL.accent)
    /** 4.3: when the psyche last switched (uptime ms), and from what. 0 = never. */
    var switchedAt by mutableLongStateOf(0L)
        private set
    var previous = PsycheKind.NORMAL
        private set
    private var started = false

    private var screenMs = 0L
    private var screenAt = 0L

    fun update(context: Context) {
        val bm = context.getSystemService(BatteryManager::class.java)
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val charging = bm?.isCharging == true
        val now = System.currentTimeMillis()
        if (now - screenAt > 5 * 60_000L) {                   // screen time is a bigger read: every 5 min
            screenMs = ScreenTime.today(context)?.totalMs ?: 0L
            screenAt = now
        }
        val hour = java.time.LocalTime.now().hour
        val next = when {
            (battery in 0..15 && !charging) || screenMs >= 5 * 60 * 60_000L -> PsycheKind.PROTECTIVE
            hour in 5..10 -> PsycheKind.CALM
            hour < 5 -> PsycheKind.CHAOTIC
            else -> PsycheKind.NORMAL
        }
        if (!started) { started = true; current = next; accent = next.accent; return }   // first check: no glitch
        if (next != current) {
            previous = current
            current = next
            switchedAt = android.os.SystemClock.uptimeMillis()
        }
    }
}
