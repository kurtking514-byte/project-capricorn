package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 3.3: drag an app onto Capri and she grabs it (designed in preview/grab-lab.html, user picked
 * the "Beside her" menu). Press and hold an app in the dock or the search row, drag it onto her,
 * let go: red command hit, she pose-glitches to her hand-on-hip smirk, the app sits beside her
 * hand and a small column of options snaps in next to it.
 */
object AppGrab {
    /** The app being dragged right now, and where the finger is (root px). */
    var dragging by mutableStateOf<AppEntry?>(null)
    var pos by mutableStateOf(Offset.Zero)
    var over by mutableStateOf(false)

    /** Where Capri's body is on screen (root px). Set by [CapriSpriteFigure] while drawing. */
    var herBox: Rect = Rect.Zero
    var rootWidth = 1f

    /** The app she's holding (menu open), and when she grabbed it (uptime ms). */
    var held by mutableStateOf<AppEntry?>(null)
    var heldAt by mutableLongStateOf(0L)

    /** Pin lightning: which dock slot, and when (uptime ms). */
    var strikeSlot by mutableIntStateOf(-1)
    var strikeAt by mutableLongStateOf(0L)

    /** Set by the home screen. */
    var onHover: (Boolean) -> Unit = {}
    var onDrop: (AppEntry) -> Unit = {}

    fun start(app: AppEntry) {
        dragging = app
        over = false
    }

    fun move(p: Offset) {
        pos = p
        CapriGaze.x = (p.x / rootWidth).coerceIn(0f, 1f)          // she watches the app
        CapriGaze.at = android.os.SystemClock.uptimeMillis()
        val o = herBox.contains(p)
        if (o != over) {
            over = o
            onHover(o)
        }
    }

    fun drop() {
        val app = dragging
        val hit = over
        dragging = null
        over = false
        if (app != null && hit) {
            held = app
            heldAt = android.os.SystemClock.uptimeMillis()
            onDrop(app)
        }
    }

    fun strike(slot: Int) {
        strikeSlot = slot
        strikeAt = android.os.SystemClock.uptimeMillis()
    }
}

enum class GrabAction { PIN, INFO, UNINSTALL, HIDE, CANCEL }

/**
 * Tap = [onTap]. Press and hold = [onMenu] (the old long-press menu). Press, hold, then move =
 * drag the app (only if [draggable]); dropping it on Capri hands it to [AppGrab].
 * Moving before the hold finishes is left alone, so lists still scroll and swipes still work.
 */
fun Modifier.appGesture(
    app: AppEntry, draggable: Boolean, onTap: () -> Unit, onMenu: () -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .semantics {
            onClick { onTap(); true }
            onLongClick { onMenu(); true }
        }
        .pointerInput(app.key, draggable) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                // 1. tap, cancel (moved / taken by a scroll), or long press (timeout)
                val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var o = ""
                    while (o.isEmpty()) {
                        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        o = when {
                            ch == null -> "cancel"
                            !ch.pressed -> { ch.consume(); "tap" }       // claimed: not a tap on the empty screen
                            ch.isConsumed || (ch.position - down.position).getDistance() > slop -> "cancel"
                            else -> ""
                        }
                    }
                    o
                }
                if (outcome == "tap") { onTap(); return@awaitEachGesture }
                if (outcome == "cancel") return@awaitEachGesture
                // 2. held long enough: drag if the finger moves, else the menu on release
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                var dragging = false
                while (true) {
                    val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    if (draggable && !dragging && (ch.position - down.position).getDistance() > slop) {
                        dragging = true
                        AppGrab.start(app)
                    }
                    if (dragging) {
                        ch.consume()
                        AppGrab.move(origin + ch.position)
                    }
                }
                if (dragging) AppGrab.drop() else onMenu()
            }
        }
}

/** How long the grab parts take, ms (same numbers as the Grab Lab). */
private const val ICON_GLITCH_MS = 150L
private const val MENU_AT_MS = 170L
private const val MENU_POP_MS = 83L

/**
 * Drawn over the whole home screen: the app under your finger while dragging, the app beside
 * her with the options menu while she holds it, and the lightning when she pins it.
 * Only takes touches while she holds an app (tap anywhere else = never mind).
 */
@Composable
fun GrabLayer(inDock: (AppEntry) -> Boolean, onAction: (AppEntry, GrabAction) -> Unit) {
    val density = LocalDensity.current
    val iconPx = with(density) { 52.dp.toPx() }
    Box(Modifier.fillMaxSize().onSizeChanged { AppGrab.rootWidth = it.width.toFloat().coerceAtLeast(1f) }) {
        // the app under your finger
        AppGrab.dragging?.let { app ->
            Image(
                bitmap = app.icon, contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset((AppGrab.pos.x - iconPx / 2).roundToInt(), (AppGrab.pos.y - iconPx * 0.9f).roundToInt()) }
                    .size(52.dp),
            )
        }

        // she holds it: app beside her hand + the menu
        AppGrab.held?.let { app ->
            HeldApp(app, inDock(app), onAction)
        }

        // pin: 3 bolts from the top onto the new dock slot, slot flashes crimson (like the boot)
        StrikeBolts()
    }
}

@Composable
private fun HeldApp(app: AppEntry, pinned: Boolean, onAction: (AppEntry, GrabAction) -> Unit) {
    val density = LocalDensity.current
    var stage by remember(app) { mutableIntStateOf(0) }   // 0 glitching in, 1 icon, 2 menu (popping), 3 menu
    LaunchedEffect(app, AppGrab.heldAt) {
        stage = 0; delay(ICON_GLITCH_MS)
        stage = 1; delay(MENU_AT_MS - ICON_GLITCH_MS)
        stage = 2; delay(MENU_POP_MS)
        stage = 3
    }
    val box = AppGrab.herBox
    val icon = with(density) { 48.dp.toPx() }
    val menuW = with(density) { 128.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    // beside her right hand (hand on hip), kept on screen
    val ix = (box.center.x + box.width * 0.30f).coerceAtMost(AppGrab.rootWidth - icon - margin)
    val iy = box.top + box.height * 0.32f
    val mx = ix.coerceAtMost(AppGrab.rootWidth - menuW - margin)
    val my = iy + icon + margin

    // tap anywhere else = never mind
    Box(Modifier.fillMaxSize().pointerInput(app) { detectTapGestures { onAction(app, GrabAction.CANCEL) } })

    // the app in her hand; a crimson copy beside it while it glitches in
    val split = with(density) { if (stage == 0) 6.dp.roundToPx() else 0 }
    if (stage == 0) Image(
        bitmap = app.icon, contentDescription = null, colorFilter = ColorFilter.tint(CapriColors.Pink),
        modifier = Modifier.offset { IntOffset(ix.roundToInt() + split, iy.roundToInt()) }.size(48.dp),
    )
    Image(
        bitmap = app.icon, contentDescription = app.label,
        modifier = Modifier.offset { IntOffset(ix.roundToInt(), iy.roundToInt()) }.size(48.dp),
    )

    if (stage < 2) return
    val pop = with(density) { if (stage == 2) 6.dp.roundToPx() else 0 }   // one 83 ms crimson pop
    Column(
        Modifier
            .offset { IntOffset(mx.roundToInt(), my.roundToInt()) }
            .width(128.dp),
    ) {
        Text(
            app.label.uppercase(), color = CapriColors.Acid, fontFamily = FontFamily.Monospace,
            fontSize = 10.sp, letterSpacing = 1.2.sp, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp),
        )
        val options = listOf(
            GrabAction.PIN to if (pinned) "Unpin" else "Pin to dock",
            GrabAction.INFO to "App info",
            GrabAction.UNINSTALL to "Uninstall",
            GrabAction.HIDE to "Hide",
        )
        for ((action, label) in options) {
            val shape = CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp)
            Box(
                Modifier
                    .padding(bottom = 6.dp)
                    .fillMaxWidth()
                    .offset { IntOffset(-pop / 2, 0) }
                    .background(if (pop > 0) CapriColors.Pink else CapriColors.Panel, shape)
                    .border(1.dp, CapriColors.Cyan.copy(alpha = 0.5f), shape)
                    .clickable { onAction(app, action) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp,
                    color = if (action == GrabAction.UNINSTALL) CapriColors.Pink else CapriColors.Text,
                )
            }
        }
    }
}

/** Lightning onto a dock slot after a pin: 3 bolts, first lands at 40 ms; slot crimson 40–110 ms. */
@Composable
private fun StrikeBolts() {
    val at = AppGrab.strikeAt
    var t by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(at) {
        if (at == 0L) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            t = (android.os.SystemClock.uptimeMillis() - at).toFloat()
            if (t > 220f) break
        }
        t = -1f
    }
    if (t < 0f) return
    val seed = at.toInt()
    Canvas(Modifier.fillMaxSize()) {
        val slot = BootDock.slots[AppGrab.strikeSlot] ?: return@Canvas
        val cx = slot.center.x
        val ty = slot.top
        for (n in 0 until 3) {
            val delayMs = if (n == 0) 40f else 40f + 30f * (capriJitter(seed, n) * 0.5f + 0.5f)
            val b = t - delayMs
            if (b < 0f || b >= 90f) continue
            val x0 = cx + capriJitter(seed, 10 + n) * size.width * 0.4f
            val bolt = Path().apply {
                moveTo(x0, 0f)
                for (q in 1 until 8) {
                    val f = q / 8f
                    lineTo(x0 + (cx - x0) * f + capriJitter(seed, 20 + n * 8 + q) * size.width * 0.06f * (1f - f), ty * f)
                }
                lineTo(cx, ty)
            }
            drawPath(bolt, CapriColors.Pink, style = Stroke(width = (if (b < 45f) 5.dp else 3.dp).toPx(), join = StrokeJoin.Miter))
        }
        if (t in 40f..110f) drawRect(CapriColors.Pink, topLeft = slot.topLeft, size = slot.size)
    }
}
