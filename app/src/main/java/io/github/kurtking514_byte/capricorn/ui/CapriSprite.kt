package io.github.kurtking514_byte.capricorn.ui

import io.github.kurtking514_byte.capricorn.data.Gesture
import io.github.kurtking514_byte.capricorn.data.Gestures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Optional sprite skin for Capri.
 *
 * If the app's assets contain a `skin/` folder of PNG frames, Capri is drawn from those
 * instead of the built-in code drawing. The repo ships no character art: put your own
 * frames in `app/src/debug/assets/skin/` (git-ignored) to use them on your own phone.
 *
 * All frames share one square canvas with the ground line 93% of the way down.
 * Only `neutral` is required. Others, all optional:
 *  - expressions: idle_happy, idle_smirk, idle_skeptical, idle_shrug, idle_excited,
 *    idle_laughing, arms_crossed_annoyed, hand_on_hip_smirk
 *  - blink / talk / wave: idle_blink_half, idle_blink_closed, idle_mouth_small,
 *    idle_mouth_round, idle_mouth_wide, idle_wave_a, idle_wave_b
 *  - idle activities: recline_neutral, recline_sleepy,
 *    crouch_neutral, chin_rest_neutral, hand_on_hip_smirk, arms_crossed_annoyed, idle_shrug,
 *    lean_in_attentive, idle_look_left, idle_look_right, idle_thinking
 *  - `meta.json`: {"top": {"neutral": 0.09, ...}} = where the top of her head/hair is in
 *    each frame (0..1 down the frame), so the speech bubble can sit right above her.
 */
class CapriSkin private constructor(private val context: Context, private val names: Set<String>) {

    // Small cache: each frame is about 1.5 MB in memory, so keep only the recent ones.
    private val cache = LruCache<String, ImageBitmap>(8)
    private val tops: Map<String, Float> = try {
        val top = JSONObject(context.assets.open("$DIR/meta.json").bufferedReader().use { it.readText() }).getJSONObject("top")
        top.keys().asSequence().associateWith { top.getDouble(it).toFloat() }
    } catch (e: Exception) {
        emptyMap()
    }

    fun has(name: String) = name in names

    /** Where the top of her figure is in this frame, 0..1 down the frame. */
    fun top(name: String): Float = tops[name] ?: 0.09f

    fun frame(name: String): ImageBitmap? {
        if (name !in names) return null
        cache.get(name)?.let { return it }
        return try {
            context.assets.open("$DIR/$name.png").use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
                ?.also { cache.put(name, it) }
        } catch (e: Exception) {
            null
        }
    }

    fun bitmap(name: String): Bitmap? = frame(name)?.asAndroidBitmap()

    /** [name] if the skin has it, else the first of [fallbacks] it has, else neutral. */
    fun pick(name: String, vararg fallbacks: String): String =
        (listOf(name) + fallbacks).firstOrNull { it in names } ?: "neutral"

    companion object {
        private const val DIR = "skin"

        /**
         * The ONLY frames the app will ever use, whatever else is in the skin folder.
         * User rule (2026-11-18): safe standing poses only. No sitting, crouching, lying down,
         * leaning, or frames with odd eyes. Never add those back.
         */
        val ALLOWED = setOf(
            "neutral", "idle_smirk", "hand_on_hip_smirk", "arms_crossed_annoyed",
            "idle_shrug", "idle_thinking", "idle_look_left", "idle_look_right", "idle_wave_raise",
            // building blocks for blinking, talking and waving
            "idle_blink_half", "idle_blink_closed", "idle_mouth_small", "idle_mouth_round", "idle_mouth_wide",
            "idle_wave_a", "idle_wave_b",
        )

        /** Returns the skin if one is installed, else null (the code-drawn Capri is used). */
        fun load(context: Context): CapriSkin? {
            val files = try {
                context.assets.list(DIR).orEmpty()
            } catch (e: Exception) {
                emptyArray()
            }
            val names = files.filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }.toSet() intersect ALLOWED
            return if ("neutral" in names) CapriSkin(context.applicationContext, names) else null
        }
    }
}

// Talking mouth (CAPRI_REFERENCE.md 2.3): only "small" and "wide" are used; closed = neutral.
// The show has no round mouth, so idle_mouth_round is no longer used.
private val TALK = listOf("idle_mouth_small", "idle_mouth_wide")
internal const val MOUTH_BIG_MS = 167L        // big mouth held 4 frames at 24 fps
internal const val MOUTH_SHORT_MS = 83L       // small or closed held 2 frames
internal const val MOUTH_SMALL_CHANCE = 0.67f // after the big mouth: small 2 of 3 times, closed 1 of 3
private val QUICK = TALK.toSet() + setOf("idle_blink_half", "idle_blink_closed", "idle_wave_a", "idle_wave_b")
private val BLINK = setOf("idle_blink_half", "idle_blink_closed")

// Blink timing from the show (CAPRI_REFERENCE.md 2.1). The gap range and double-blink chance are
// estimates from a few measured blinks plus 4.5–6.6 s stretches with none.
internal const val BLINK_CLOSED_MS = 83L
internal const val BLINK_GAP_MIN_MS = 2_500L
internal const val BLINK_GAP_MAX_MS = 7_000L
internal const val BLINK_DOUBLE_APART_MS = 333L
internal const val BLINK_DOUBLE_CHANCE = 0.3f

/** Every full pose, for the "poses" chat command. Only poses in [CapriSkin.ALLOWED]. */
private val SHOWCASE = listOf(
    "neutral", "idle_smirk", "hand_on_hip_smirk", "arms_crossed_annoyed",
    "idle_shrug", "idle_thinking", "idle_look_left", "idle_look_right", "idle_wave_raise",
)

/** Things she does on her own when you leave her alone for a bit. Standing poses only. */
private val ACTIVITIES = listOf(
    "hand_on_hip_smirk", "arms_crossed_annoyed", "idle_shrug", "idle_smirk",
    "idle_look_left", "idle_look_right", "idle_thinking",
)

private fun moodFrame(mood: String): String = when (mood) {
    "happy" -> "idle_smirk"
    "smirk", "sass" -> "hand_on_hip_smirk"
    "annoyed" -> "arms_crossed_annoyed"
    "skeptical" -> "idle_thinking"
    "shrug" -> "idle_shrug"
    "smug" -> "idle_smirk"
    "notif" -> "idle_look_left"      // C5: glances up toward the notification icons
    else -> "neutral"
}

/** Everything needed to draw one smoothly-moving sprite; reused every frame, no allocation. */
class CapriMeshPainter {
    private val verts = CapriMesh.newVerts()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    /** Same frame as a flat crimson silhouette, for the glitch split. */
    private val tintPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(CapriColors.Pink.toArgb(), PorterDuff.Mode.SRC_IN)
    }
    /** Same frame as a dim crimson silhouette, for the first part of her appear glitch. */
    private val dimPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(GLITCH_DIM.toArgb(), PorterDuff.Mode.SRC_IN)
    }

    /**
     * How tall the whole frame is drawn. A standing figure fills the space under
     * [topInset]; the faint outer hair may spill a little past the sides.
     */
    fun frameHeight(size: Size, bitmap: Bitmap, zoom: Float = 1f, topInset: Float = 0f): Float {
        val fit = minOf(
            (size.height - topInset) / (STANDING * bitmap.height),
            size.width / (0.80f * bitmap.width),
        )
        return bitmap.height * fit * zoom
    }

    /**
     * Draws [bitmap] with her feet on the bottom edge of the canvas, bent by [CapriMesh].
     * [lift] raises her (px), [shiftX] moves her sideways (px), [zoom] scales her about her feet.
     */
    fun draw(
        scope: DrawScope, bitmap: Bitmap, alpha: Float,
        t: Float, tiltX: Float, tiltY: Float, bounce: Float,
        zoom: Float = 1f, life: Float = 1f, lift: Float = 0f, shiftX: Float = 0f, topInset: Float = 0f,
        crimson: Boolean = false, dim: Boolean = false,
    ) {
        if (alpha <= 0f) return
        val size = scope.size
        val h = frameHeight(size, bitmap, zoom, topInset)
        val w = h * bitmap.width / bitmap.height
        CapriMesh.fill(
            verts, (size.width - w) / 2f + shiftX, size.height - GROUND * h - lift, w, h,
            t, tiltX, tiltY, bounce, life,
        )
        val p = if (dim) dimPaint else if (crimson) tintPaint else paint
        p.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        scope.drawIntoCanvas {
            it.nativeCanvas.drawBitmapMesh(bitmap, CapriMesh.COLS, CapriMesh.ROWS, verts, 0, null, 0, p)
        }
    }

    companion object {
        /** The ground line, as a fraction down the frame. */
        const val GROUND = 0.93f
        /** How much of the frame's height a standing Capri covers (hair top to feet). */
        const val STANDING = 0.86f
    }
}

/**
 * The phone's tilt as two numbers in -1..1 (left/right, away/toward), read from the
 * gravity sensor only while the screen is showing. The returned array is updated in place.
 */
@Composable
fun rememberTilt(): FloatArray {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val target = remember { FloatArray(2) }
    DisposableEffect(lifecycle) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var restY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gx = e.values[0] / 9.81f
                val gy = e.values[1] / 9.81f
                // However you normally hold the phone slowly becomes "level".
                if (restY.isNaN()) restY = gy
                restY += (gy - restY) * 0.01f
                target[0] = (-gx / 0.5f).coerceIn(-1f, 1f)
                target[1] = ((gy - restY) * 2.5f).coerceIn(-1f, 1f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor?.let { sm?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                Lifecycle.Event.ON_PAUSE -> sm?.unregisterListener(listener)
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sm?.unregisterListener(listener)
        }
    }
    return target
}

/**
 * Capri from the sprite skin, standing free on the home screen. Like in the show she holds
 * still (no sway, breathing, hair motion, tilt lean or bounce) and changes pose with a
 * short glitch.
 * Left alone for a while she does her own thing: sits down, lies down, crouches, looks
 * around. Any tap or new line gets her back on her feet.
 *
 * @param topInset   space to keep free above a standing Capri (for her speech bubble), px
 * @param onHeadTop  reports where the top of her head/hair currently is, px from the top
 */
@Composable
fun CapriSpriteFigure(
    skin: CapriSkin,
    speaking: Boolean,
    mood: String,
    moodId: Int,
    topInset: Float,
    onHeadTop: (Float) -> Unit,
    onPoke: (held: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val poke by rememberUpdatedState(onPoke)
    val headTop by rememberUpdatedState(onHeadTop)
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val painter = remember { CapriMeshPainter() }
    val tilt = remember { FloatArray(2) }   // always 0: no phone-tilt lean (show style)
    var canvas by remember { mutableStateOf(IntSize.Zero) }

    // Clock for the motion. Only the drawing reads it, so nothing else is redone per frame.
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                    last = now
                    time += dt * CapriTime.period.sway     // C4: slower sway in the evening/night
                }
            }
        }
    }

    // Pose for the current mood, falling back to neutral after a few seconds.
    var current by remember { mutableStateOf("neutral") }

    // What she's doing on her own, or null while she's just standing there.
    var activity by remember { mutableStateOf<String?>(null) }
    var lastActivity by remember { mutableStateOf<String?>(null) }
    val activities = remember(skin) { ACTIVITIES.filter { skin.has(it) } }

    LaunchedEffect(moodId) {
        activity = null
        // Pose changes snap with no spring wobble (show style, CAPRI_REFERENCE.md).
        if (mood == "showcase") {
            // "poses" chat command: every pose in the skin once, 2 s each
            current = "neutral"
            for (pose in SHOWCASE.filter { skin.has(it) }) {
                activity = pose
                delay(2_000)
            }
            activity = null
        }
        current = if (mood == "showcase") "neutral" else mood
        delay(4_500)
        current = "neutral"
        // Left alone: every so often, do something for a while, then go back to neutral.
        // 4.1: the psyche in charge picks the poses and the rhythm (replaces the C4 time-of-day pace).
        while (activities.isNotEmpty()) {
            val ps = Psyche.current
            val acts = ps.poses.filter { it in activities }.ifEmpty { activities }
            if (ps.stare) {                                // PROTECTIVE: one pose, held, staring at you
                if (activity !in acts) { delay(ps.gap.first); activity = acts.first(); lastActivity = activity }
                delay(1_000)
                continue
            }
            delay(Random.nextLong(ps.gap.first, ps.gap.last + 1))
            if (CapriTime.dozing) { activity = null; continue }   // asleep on her feet: no activities
            if (Psyche.current != ps) continue
            val pick = (acts - lastActivity).randomOrNull() ?: acts.random()
            activity = pick
            lastActivity = pick
            val hold = Random.nextLong(ps.hold.first, ps.hold.last + 1)
            val looks = listOf("idle_look_left", "idle_look_right").filter { it in activities }
            if (ps.glance > 0f && Random.nextFloat() < ps.glance && pick !in looks && looks.isNotEmpty()) {
                delay(hold / 2)                            // CHAOTIC: a twitchy glance away and back
                activity = looks.random(); delay(250)
                activity = pick; delay(hold / 2)
            } else delay(hold)
            activity = null
        }
    }

    // Show-style blink (CAPRI_REFERENCE.md 2.1): eyes simply gone for 83 ms (2 frames at 24 fps),
    // no half-closed step. Irregular gaps; about 1 in 3 is a double blink, 333 ms apart.
    var blink by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(BLINK_GAP_MIN_MS, BLINK_GAP_MAX_MS))
            blink = 2; delay(BLINK_CLOSED_MS)
            blink = 0
            if (Random.nextFloat() < BLINK_DOUBLE_CHANCE) {
                delay(BLINK_DOUBLE_APART_MS - BLINK_CLOSED_MS)
                blink = 2; delay(BLINK_CLOSED_MS)
                blink = 0
            }
        }
    }
    // Wave frames alternate every 105 ms.
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(current) {
        while (current == "wave") {
            delay(105)
            step++
        }
    }
    // Talking, show rhythm: big mouth 167 ms, then small (2 of 3) or closed (1 of 3) for 83 ms,
    // repeat. About 250 ms per cycle, uneven. Ends closed (neutral) when she stops talking.
    var mouth by remember { mutableStateOf("neutral") }
    LaunchedEffect(speaking) {
        while (speaking) {
            mouth = "idle_mouth_wide"; delay(MOUTH_BIG_MS)
            mouth = if (Random.nextFloat() < MOUTH_SMALL_CHANCE) "idle_mouth_small" else "neutral"
            delay(MOUTH_SHORT_MS)
        }
        mouth = "neutral"
    }

    // C1: she looks toward wherever you touch the screen, for a moment
    var looking by remember { mutableStateOf(false) }
    LaunchedEffect(CapriGaze.at) {
        if (CapriGaze.at == 0L) return@LaunchedEffect
        looking = true
        delay(GAZE_MS)
        looking = false
    }

    // C4: keep the time-of-day period current; 4.1: and which psyche is in charge (checked once a minute).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            CapriTime.refresh()
            Psyche.update(ctx)
            delay(60_000)
        }
    }
    // Late at night, if nobody talks to or pokes her for a while, she dozes off standing up.
    val late = CapriTime.period == DayPeriod.NIGHT
    LaunchedEffect(moodId, late) {
        CapriTime.dozing = false
        if (!late) return@LaunchedEffect
        delay(DOZE_AFTER_MS)
        activity = null
        CapriTime.dozing = true
    }
    val dozing = CapriTime.dozing

    // 3.2: long-press = she's listening: the show's double blink (83 ms closed, 333 ms apart)
    // plays first, over everything else, then her smug pose and line.
    var listenTick by remember { mutableIntStateOf(0) }
    var listenBlink by remember { mutableStateOf(false) }
    LaunchedEffect(listenTick) {
        if (listenTick == 0) return@LaunchedEffect
        listenBlink = true; delay(BLINK_CLOSED_MS)
        listenBlink = false; delay(BLINK_DOUBLE_APART_MS - BLINK_CLOSED_MS)
        listenBlink = true; delay(BLINK_CLOSED_MS)
        listenBlink = false
    }

    val doing = activity
    val name = when {
        listenBlink -> "idle_blink_closed"
        dozing && !speaking -> "idle_blink_closed"
        speaking && current in setOf("neutral", "happy", "smirk", "wave") && doing == null -> mouth
        looking && !speaking -> when {
            CapriGaze.x < 0.4f -> "idle_look_left"
            CapriGaze.x > 0.6f -> "idle_look_right"
            else -> "neutral"
        }
        current == "wave" -> if ((step / 2) % 2 == 0) "idle_wave_a" else "idle_wave_b"
        current == "neutral" && doing != null -> doing
        current == "neutral" && blink == 2 -> "idle_blink_closed"
        current == "neutral" && blink == 1 -> "idle_blink_half"
        current == "neutral" && late -> "idle_blink_half"            // drowsy, heavy eyelids
        else -> moodFrame(current)
    }.let { if (skin.has(it)) it else "neutral" }

    // Blinks and mouth frames snap. Every real pose change glitches ("strips + green", picked in
    // preview/glitch-lab.html): 150 ms of slices with a crimson split hide the jump, while green
    // and crimson bars flicker around her in 3 steps of 83 ms (80%, 50%, 25%). 250 ms in all.
    var shownName by remember { mutableStateOf(name) }
    var fromName by remember { mutableStateOf<String?>(null) }
    val fade = remember { Animatable(1f) }
    var glitching by remember { mutableStateOf(false) }
    var glitchSeed by remember { mutableIntStateOf(0) }
    LaunchedEffect(name) {
        if (name != shownName) {
            // Blinks and mouth shapes snap: no fade, no glitch (also not when going back to
            // neutral, which is the closed mouth / open eyes).
            val prev = shownName
            val snap = BLINK + TALK
            if ((name in snap && prev in snap + "neutral") || (prev in snap && name in snap + "neutral")) {
                shownName = name
                fromName = null
                glitching = false
                fade.snapTo(1f)
                return@LaunchedEffect
            }
            fromName = shownName
            shownName = name
            val quick = name in QUICK && fromName in QUICK + "neutral"
            glitching = !quick
            glitchSeed++
            fade.snapTo(0f)
            fade.animateTo(1f, tween(if (quick) 55 else POSE_GLITCH_MS, easing = LinearEasing))
            fromName = null
            glitching = false
        }
    }

    // Tell the home screen where her head is, so the speech bubble can sit on top of it.
    // Blink and mouth frames don't move her head, so they're ignored here.
    val poseForHead = if (shownName in QUICK) "neutral" else shownName
    LaunchedEffect(poseForHead, canvas, topInset) {
        val bitmap = skin.bitmap(poseForHead) ?: return@LaunchedEffect
        if (canvas.height == 0) return@LaunchedEffect
        val size = Size(canvas.width.toFloat(), canvas.height.toFloat())
        val h = painter.frameHeight(size, bitmap, topInset = topInset)
        headTop(size.height - CapriMeshPainter.GROUND * h + skin.top(poseForHead) * h)
    }

    // 4.3: psyche switch = heavy green + crimson bars over her (1 / 0.6 / 0.3, 83 ms each) while the
    // accent flickers new / old / new; she snaps into the new psyche's first favorite pose.
    var switchMs by remember { mutableFloatStateOf(SWITCH_MS) }
    LaunchedEffect(Psyche.switchedAt) {
        if (Psyche.switchedAt == 0L) return@LaunchedEffect
        Psyche.current.poses.firstOrNull { skin.has(it) }?.let { activity = it; lastActivity = it }
        val new = Psyche.current.accent
        val old = Psyche.previous.accent
        do {
            withFrameMillis { }
            switchMs = (android.os.SystemClock.uptimeMillis() - Psyche.switchedAt).toFloat()
            Psyche.accent = when ((switchMs / GLITCH_STEP_MS).toInt()) { 1 -> old; else -> new }
        } while (switchMs < SWITCH_MS)
        Psyche.accent = new
    }

    // 3.4: ms since she glitched out / back in (real time, runs only during the glitch)
    var awayMs by remember { mutableFloatStateOf(AWAY_IN_MS) }
    LaunchedEffect(CapriAway.changedAt) {
        if (CapriAway.changedAt == 0L) return@LaunchedEffect
        do {
            withFrameMillis { }
            awayMs = (android.os.SystemClock.uptimeMillis() - CapriAway.changedAt).toFloat()
        } while (awayMs < AWAY_IN_MS)
    }

    val origin = remember { floatArrayOf(0f, 0f) }      // canvas position on screen, for AppGrab
    Canvas(
        modifier
            .onSizeChanged { canvas = it }
            .onGloballyPositioned { val p = it.positionInRoot(); origin[0] = p.x; origin[1] = p.y }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!CapriAway.away) poke(false) },
                    onLongPress = { if (!CapriAway.away && Gestures.on(Gesture.HER_HOLD)) { listenTick++; poke(true) } },
                )
            }
            .pointerInput(Unit) {
                // 3.4: a sideways swipe on her (or on the empty spot where she was) toggles her
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (kotlin.math.abs(dx) > AWAY_SWIPE_DP.dp.toPx() && (CapriAway.away || Gestures.on(Gesture.HER_SWIPE))) CapriAway.toggle() },
                ) { change, amount ->
                    dx += amount
                    change.consume()
                }
            }
    ) {
        val t = time
        val a = fade.value
        val from = fromName?.let { skin.bitmap(it) }
        val to = skin.bitmap(shownName)

        // 3.4: away = clean home. Out: strips + bars, 250 ms. Back: like her boot appear, 0.5 s
        // (dim crimson silhouette, then her colors with bars thinning out).
        if (CapriAway.away) {
            AppGrab.herBox = Rect.Zero                           // nothing to drop apps on
            if (to == null || awayMs >= AWAY_OUT_MS) return@Canvas
            if (awayMs < POSE_STRIPS_MS) drawPoseGlitch(painter, to, to, awayMs / POSE_STRIPS_MS, glitchSeed + 77, t, tilt, 0f, topInset)
            val step = (awayMs / GLITCH_STEP_MS).toInt()
            drawGlitchBars(painter, to, topInset, step, AWAY_OUT_BARS.getOrElse(step) { 0f }, glitchSeed + 77)
            return@Canvas
        }
        if (CapriAway.changedAt != 0L && awayMs < AWAY_IN_MS && to != null) {
            val f = awayMs / AWAY_IN_MS
            painter.draw(this, to, 1f, t, tilt[0], tilt[1], 0f, topInset = topInset, dim = f < 0.35f)
            val bars = if (f < 0.6f) 1f else 1f - (f - 0.6f) / 0.4f
            drawGlitchBars(painter, to, topInset, (awayMs / GLITCH_STEP_MS).toInt(), bars, glitchSeed + 78)
            return@Canvas
        }

        // 3.3: tell AppGrab where her body is (middle 60% of the frame, hair top to feet), on screen
        to?.let {
            val fh = painter.frameHeight(size, it, topInset = topInset)
            val fw = fh * it.width / it.height
            val left = origin[0] + (size.width - fw) / 2f
            val top = origin[1] + size.height - CapriMeshPainter.GROUND * fh
            AppGrab.herBox = Rect(left + 0.2f * fw, top + 0.07f * fh, left + 0.8f * fw, top + CapriMeshPainter.GROUND * fh)
        }
        if (glitching && from != null && to != null) {
            val ms = a * POSE_GLITCH_MS
            if (ms < POSE_STRIPS_MS) drawPoseGlitch(painter, from, to, ms / POSE_STRIPS_MS, glitchSeed, t, tilt, 0f, topInset)
            else painter.draw(this, to, 1f, t, tilt[0], tilt[1], 0f, topInset = topInset)
            val step = (ms / GLITCH_STEP_MS).toInt()
            drawGlitchBars(painter, to, topInset, step, POSE_BARS.getOrElse(step) { 0f }, glitchSeed)
            if (switchMs < SWITCH_MS) {
                val s = (switchMs / GLITCH_STEP_MS).toInt()
                drawGlitchBars(painter, to, topInset, s, SWITCH_BARS.getOrElse(s) { 0f }, glitchSeed + 91)
            }
            return@Canvas
        }
        from?.let {
            painter.draw(this, it, 1f - a, t, tilt[0], tilt[1], 0f, topInset = topInset)
        }
        skin.bitmap(shownName)?.let {
            painter.draw(this, it, if (fromName == null) 1f else a, t, tilt[0], tilt[1], 0f, topInset = topInset)
            if (switchMs < SWITCH_MS) {                        // 4.3 psyche switch bars
                val s = (switchMs / GLITCH_STEP_MS).toInt()
                drawGlitchBars(painter, it, topInset, s, SWITCH_BARS.getOrElse(s) { 0f }, glitchSeed + 91)
            }
        }
    }
}

/** 4.3: psyche switch glitch length and bar strength per 83 ms step (user picked "Bars + color flicker"). */
private const val SWITCH_MS = 250f
private val SWITCH_BARS = floatArrayOf(1f, 0.6f, 0.3f)

/**
 * One frame of the pose-change glitch. The screen is cut into uneven horizontal slices;
 * each slice shows the old or the new pose (more new ones as [p] goes 0..1), some jump
 * sideways, a crimson copy sits a few px to one side, and a few slices drop out.
 * The slice pattern re-rolls halfway, so she tears twice.
 */
internal fun DrawScope.drawPoseGlitch(
    painter: CapriMeshPainter, from: Bitmap, to: Bitmap, p: Float, seed: Int,
    t: Float, tilt: FloatArray, bounce: Float, topInset: Float,
) {
    val phase = if (p < 0.5f) 0 else 1
    val base = seed * 7 + phase * 997
    val split = (6f + 4f * capriJitter(base, 1)) * density * (if (capriJitter(base, 2) > 0f) 1f else -1f)
    var y = 0f
    var i = 0
    while (y < size.height) {
        val sliceH = size.height * (0.012f + 0.045f * (capriJitter(base, 10 + i) * 0.5f + 0.5f))
        val jump = capriJitter(base, 200 + i)
        val dx = if (kotlin.math.abs(jump) > 0.45f) jump * size.width * 0.14f else 0f
        val showNew = capriJitter(base, 400 + i) * 0.5f + 0.5f < p
        val crimsonOnly = capriJitter(base, 600 + i) > 0.55f
        val gone = capriJitter(base, 800 + i) > 0.8f
        if (!gone) {
            val frame = if (showNew) to else from
            clipRect(top = y, bottom = y + sliceH) {
                translate(left = dx + split) {
                    painter.draw(this, frame, 1f, t, tilt[0], tilt[1], bounce, topInset = topInset, crimson = true)
                }
                if (!crimsonOnly) translate(left = dx) {
                    painter.draw(this, frame, 1f, t, tilt[0], tilt[1], bounce, topInset = topInset)
                }
            }
        }
        y += sliceH
        i++
    }
}

// The show's glitch colors (CAPRI_REFERENCE.md): panel green, hair crimson, and a dim crimson
// for her silhouette at the start of the appear glitch.
internal val GLITCH_GREEN = Color(0xFF00B144)
internal val GLITCH_CRIMSON = Color(0xFFBC002B)
internal val GLITCH_DIM = Color(0xFF5A0018)
/** The show changes drawings every 2 frames at 24 fps, so the bars re-roll every 83 ms. */
internal const val GLITCH_STEP_MS = 83.33f
/** Pose change: slices for the first 150 ms, bars until 250 ms. */
internal const val POSE_STRIPS_MS = 150f
internal const val POSE_GLITCH_MS = 250
private val POSE_BARS = floatArrayOf(0.8f, 0.5f, 0.25f)

/**
 * One 83 ms step of the show's bar glitch around her ([bitmap] gives her size and place, like
 * [CapriMeshPainter.draw]). [intensity] 0..1: 3–12 thin horizontal bars sticking out sideways
 * from her body, green or crimson, 0.3–2.5% of the canvas height and up to 33% of its width;
 * sometimes a dense block of 8–10 beside her face; about 1 step in 5 is nearly clean.
 * Same rules as drawBars() in preview/glitch-lab.html.
 */
internal fun DrawScope.drawGlitchBars(
    painter: CapriMeshPainter, bitmap: Bitmap, topInset: Float, step: Int, intensity: Float, seed: Int,
) {
    if (intensity <= 0f) return
    val fh = painter.frameHeight(size, bitmap, topInset = topInset)
    val u = CapriMeshPainter.STANDING * fh                                  // her height, hair to feet
    val top = size.height - CapriMeshPainter.GROUND * fh + (CapriMeshPainter.GROUND - CapriMeshPainter.STANDING) * fh
    val cx = size.width / 2f
    val base = seed * 7919 + step * 104729
    var k = 0
    fun r() = capriJitter(base, k++) * 0.5f + 0.5f
    fun bar(y: Float, h: Float, x0: Float, len: Float, dir: Int, color: Color) = drawRect(
        color, topLeft = Offset(if (dir > 0) x0 else x0 - len, y), size = Size(len, h),
    )
    val n = if (r() < 0.22f) (if (r() < 0.5f) 0 else 1) else (3 + 9 * intensity * r()).roundToInt()
    repeat(n) {
        val y = top + r() * u * 0.95f
        val h = size.height * (0.003f + 0.022f * r() * r())
        val dir = if (r() < 0.5f) -1 else 1
        val x0 = cx + dir * u * (r() * 0.18f) - dir * u * 0.05f
        bar(y, h, x0, size.width * (0.05f + 0.28f * r()), dir, if (r() < 0.55f) GLITCH_GREEN else GLITCH_CRIMSON)
    }
    if (r() < 0.3f * intensity) {                                           // dense block beside her face
        val dir = if (r() < 0.5f) -1 else 1
        val y0 = top + u * (0.08f + r() * 0.06f)
        repeat(8 + (r() * 3).toInt()) { i ->
            bar(y0 + i * size.height * 0.006f, size.height * (0.003f + 0.002f * r()), cx + dir * u * 0.04f,
                size.width * (0.08f + 0.18f * r()), dir, if (r() < 0.8f) GLITCH_GREEN else GLITCH_CRIMSON)
        }
    }
}

/**
 * 3.4: swipe her sideways and she glitches out, leaving a clean home screen; swipe the same
 * spot again and she glitches back in. Taps do nothing while she's away.
 */
object CapriAway {
    var away by mutableStateOf(false)
        private set
    var changedAt by mutableLongStateOf(0L)
        private set
    /** Set by MainActivity (she says something when she's back). */
    var onChange: (Boolean) -> Unit = {}

    /** Back in place with no glitch and no line (3.6: after the screen locks, it's off anyway). */
    fun reset() {
        away = false
    }

    fun toggle() {
        away = !away
        changedAt = android.os.SystemClock.uptimeMillis()
        onChange(away)
    }

    /** 3.10 face down: she glitches out, no line. */
    fun leave() {
        if (away) return
        away = true
        changedAt = android.os.SystemClock.uptimeMillis()
    }

    /** 3.10 picked up: she glitches back in (0.5 s appear) even if she never left; the caller says the line. */
    fun appear() {
        away = false
        changedAt = android.os.SystemClock.uptimeMillis()
    }
}
internal const val AWAY_SWIPE_DP = 80
internal const val AWAY_OUT_MS = 250f
internal const val AWAY_IN_MS = 500f
private val AWAY_OUT_BARS = floatArrayOf(1f, 0.6f, 0.3f)

/** Where the user last touched the home screen (0..1 across) and when (uptime ms). */
object CapriGaze {
    var x by mutableFloatStateOf(0.5f)
    var at by mutableLongStateOf(0L)
}

/** How long she keeps looking toward a touch, ms. */
internal const val GAZE_MS = 1_500L

/** C4: time of day. [pace] stretches her idle timing, [sway] scales the speed of her motion. */
enum class DayPeriod(val pace: Float, val sway: Float) {
    MORNING(0.7f, 1.1f),   // 5:00–10:59, brisk
    DAY(1.0f, 1.0f),       // 11:00–17:59
    EVENING(1.3f, 0.85f),  // 18:00–23:59, slower
    NIGHT(1.6f, 0.6f),     // 0:00–4:59, drowsy, dozes off when left alone
}

object CapriTime {
    var period by mutableStateOf(now())
        private set

    /** True while she's dozing (late night, left alone). A poke wakes her up, annoyed. */
    var dozing by mutableStateOf(false)

    fun refresh() { period = now() }

    /** Wakes her if she was dozing; returns true if she was. */
    fun wake(): Boolean = dozing.also { dozing = false }

    private fun now(): DayPeriod = when (java.time.LocalTime.now().hour) {
        in 5..10 -> DayPeriod.MORNING
        in 11..17 -> DayPeriod.DAY
        in 18..23 -> DayPeriod.EVENING
        else -> DayPeriod.NIGHT
    }
}

/** Late at night she dozes off after this long with no pokes or chat. */
private const val DOZE_AFTER_MS = 25_000L

/** Put on the home screen root: notes every touch without taking it away from anything else. */
fun Modifier.trackGaze(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull() ?: continue
            if (c.pressed && !c.previousPressed) {
                CapriGaze.x = (c.position.x / size.width).coerceIn(0f, 1f)
                CapriGaze.at = android.os.SystemClock.uptimeMillis()
            }
        }
    }
}
