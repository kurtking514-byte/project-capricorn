package io.github.kurtking514_byte.capricorn.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
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

private val TALK = listOf("idle_mouth_small", "idle_mouth_round", "idle_mouth_wide", "idle_mouth_round")
private val QUICK = TALK.toSet() + setOf("idle_blink_half", "idle_blink_closed", "idle_wave_a", "idle_wave_b")

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
        crimson: Boolean = false,
    ) {
        if (alpha <= 0f) return
        val size = scope.size
        val h = frameHeight(size, bitmap, zoom, topInset)
        val w = h * bitmap.width / bitmap.height
        CapriMesh.fill(
            verts, (size.width - w) / 2f + shiftX, size.height - GROUND * h - lift, w, h,
            t, tiltX, tiltY, bounce, life,
        )
        val p = if (crimson) tintPaint else paint
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
 * Capri from the sprite skin, standing free on the home screen and moving smoothly:
 * she sways, breathes and her hair ripples, leans with the phone's tilt, blends from one
 * pose to the next and bounces when tapped.
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
    val tiltTarget = rememberTilt()
    val tilt = remember { FloatArray(2) }
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
                    val ease = (dt * 6f).coerceAtMost(1f)
                    tilt[0] += (tiltTarget[0] - tilt[0]) * ease
                    tilt[1] += (tiltTarget[1] - tilt[1]) * ease
                    time += dt * CapriTime.period.sway     // C4: slower sway in the evening/night
                }
            }
        }
    }

    // Pose for the current mood, falling back to neutral after a few seconds.
    var current by remember { mutableStateOf("neutral") }
    val bounce = remember { Animatable(0f) }
    fun kick(strength: Float) = scope.launch {
        bounce.snapTo(strength)
        bounce.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessLow))
    }

    // What she's doing on her own, or null while she's just standing there.
    var activity by remember { mutableStateOf<String?>(null) }
    var lastActivity by remember { mutableStateOf<String?>(null) }
    val activities = remember(skin) { ACTIVITIES.filter { skin.has(it) } }

    LaunchedEffect(moodId) {
        activity = null
        if (moodId > 0) {
            kick(0.55f)
        }
        if (mood == "showcase") {
            // "poses" chat command: every pose in the skin once, 2 s each
            current = "neutral"
            for (pose in SHOWCASE.filter { skin.has(it) }) {
                activity = pose
                kick(0.3f)
                delay(2_000)
            }
            activity = null
        }
        current = if (mood == "showcase") "neutral" else mood
        delay(4_500)
        current = "neutral"
        // Left alone: every so often, do something for a while, then get back up.
        // C4: brisk in the morning, slower in the evening, slowest late at night.
        while (activities.isNotEmpty()) {
            delay((Random.nextLong(4_000L, 8_000L) * CapriTime.period.pace).toLong())
            if (CapriTime.dozing) continue                 // asleep on her feet: no activities
            activity = (activities - lastActivity).randomOrNull() ?: activities.random()
            lastActivity = activity
            kick(0.4f)
            delay((Random.nextLong(6_000L, 10_000L) * CapriTime.period.pace).toLong())
            activity = null
            kick(0.4f)
        }
    }

    var blink by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2_200L, 5_500L))
            blink = 1; delay(70)
            blink = 2; delay(110)
            blink = 1; delay(70)
            blink = 0
        }
    }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(speaking, current) {
        while (speaking || current == "wave") {
            delay(105)
            step++
        }
    }

    // C1: she looks toward wherever you touch the screen, for a moment
    var looking by remember { mutableStateOf(false) }
    LaunchedEffect(CapriGaze.at) {
        if (CapriGaze.at == 0L) return@LaunchedEffect
        looking = true
        delay(GAZE_MS)
        looking = false
    }

    // C4: keep the time-of-day period current (checked once a minute).
    LaunchedEffect(Unit) {
        while (true) {
            CapriTime.refresh()
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

    val doing = activity
    val name = when {
        dozing && !speaking -> "idle_blink_closed"
        speaking && current in setOf("neutral", "happy", "smirk", "wave") && doing == null -> TALK[step % TALK.size]
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

    // Blinks and mouth frames blend quickly. Every real pose change glitches instead:
    // she tears into slices with a crimson split, old and new pose mixed, for ~150 ms.
    var shownName by remember { mutableStateOf(name) }
    var fromName by remember { mutableStateOf<String?>(null) }
    val fade = remember { Animatable(1f) }
    var glitching by remember { mutableStateOf(false) }
    var glitchSeed by remember { mutableIntStateOf(0) }
    LaunchedEffect(name) {
        if (name != shownName) {
            fromName = shownName
            shownName = name
            val quick = name in QUICK && fromName in QUICK + "neutral"
            glitching = !quick
            glitchSeed++
            fade.snapTo(0f)
            fade.animateTo(1f, tween(if (quick) 55 else 150, easing = LinearEasing))
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

    Canvas(
        modifier
            .onSizeChanged { canvas = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        kick(1f)
                        poke(false)
                    },
                    onLongPress = {
                        kick(0.6f)
                        poke(true)
                    },
                )
            }
    ) {
        val t = time
        val a = fade.value
        val from = fromName?.let { skin.bitmap(it) }
        val to = skin.bitmap(shownName)
        if (glitching && from != null && to != null) {
            drawPoseGlitch(painter, from, to, a, glitchSeed, t, tilt, bounce.value, topInset)
            return@Canvas
        }
        from?.let {
            painter.draw(this, it, 1f - a, t, tilt[0], tilt[1], bounce.value, topInset = topInset)
        }
        skin.bitmap(shownName)?.let {
            painter.draw(this, it, if (fromName == null) 1f else a, t, tilt[0], tilt[1], bounce.value, topInset = topInset)
        }
    }
}

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
