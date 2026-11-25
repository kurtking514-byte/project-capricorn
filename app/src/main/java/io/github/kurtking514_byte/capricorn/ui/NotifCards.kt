package io.github.kurtking514_byte.capricorn.ui

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlin.math.abs

/**
 * 5.2: notification cards beside her on the home screen ("she drags notifications to you").
 * Each card shows the app and the title (usually the sender); the message text is never read.
 * Fed by CapriNotifListener (Notification access). Tap = open it, swipe sideways = dismiss it.
 */
object NotifCards {
    class Card(
        val key: String,
        val pkg: String,
        val app: String,
        val title: String?,
        val icon: ImageBitmap?,
        val intent: PendingIntent?,
        val autoCancel: Boolean,
        val at: Long,                 // uptime it arrived (0 = was already there: no arrival glitch)
    )

    /** Newest first. Only changed on the main thread. */
    val cards = mutableStateListOf<Card>()

    /** Set while the listener runs: dismisses a notification on the phone too. */
    @Volatile
    var cancel: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    /** The same filter the listener uses everywhere: not ours, not ongoing, not a group summary, clearable. */
    fun counts(context: Context, sbn: StatusBarNotification) =
        sbn.packageName != context.packageName && !sbn.isOngoing && sbn.isClearable &&
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0

    fun post(context: Context, sbn: StatusBarNotification, fresh: Boolean) {
        if (!counts(context, sbn)) return
        val card = make(context, sbn, fresh)
        main.post {
            val i = cards.indexOfFirst { it.key == card.key }
            // a quiet update of the same thing (same sender) keeps its place in time: no new glitch
            val same = i >= 0 && cards[i].title == card.title
            val c = if (same) Card(card.key, card.pkg, card.app, card.title, card.icon, card.intent, card.autoCancel, cards[i].at) else card
            if (i >= 0) cards.removeAt(i)
            cards.add(0, c)
        }
    }

    fun remove(key: String) = main.post { cards.removeAll { it.key == key } }

    fun load(context: Context, list: Array<StatusBarNotification>?) {
        val all = list.orEmpty().filter { counts(context, it) }.sortedByDescending { it.postTime }.map { make(context, it, false) }
        main.post { cards.clear(); cards.addAll(all) }
    }

    fun clear() = main.post { cards.clear() }

    /** Swipe: gone here and on the phone. */
    fun dismiss(key: String) {
        cards.removeAll { it.key == key }
        cancel?.invoke(key)
    }

    private fun make(context: Context, sbn: StatusBarNotification, fresh: Boolean): Card {
        val pm = context.packageManager
        val app = try { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName.substringAfterLast('.') }
        val icon = try { pm.getApplicationIcon(sbn.packageName).toBitmap(64, 64).asImageBitmap() } catch (e: Exception) { null }
        val n = sbn.notification
        // the title only (usually who it's from); never EXTRA_TEXT
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        return Card(
            sbn.key, sbn.packageName, app, title, icon, n.contentIntent,
            n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            if (fresh) SystemClock.uptimeMillis() else 0L,
        )
    }
}

private const val MAX_SHOWN = 3
private const val STEP = 83L                 // one show frame-step
private const val SWIPE_DP = 60

/** The cards beside her: up to 3, newest on top, then "+N". */
@Composable
fun NotifStack(onOpen: (NotifCards.Card) -> Unit, modifier: Modifier = Modifier) {
    val cards = NotifCards.cards
    if (cards.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
        for (c in cards.take(MAX_SHOWN)) key(c.key) { NotifCardView(c, onOpen) }
        if (cards.size > MAX_SHOWN) {
            Text(
                "+${cards.size - MAX_SHOWN}", color = CapriColors.Acid, fontSize = 11.sp, letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/**
 * One card. Arrival (show-style, no slide): first 83 ms it sits at the TOP EDGE of the screen with a
 * crimson split, then snaps down into place with green + crimson copies peeking out, then holds.
 * Swipe sideways past 60 dp = two glitch steps, then it's dismissed.
 */
@Composable
private fun NotifCardView(c: NotifCards.Card, onOpen: (NotifCards.Card) -> Unit) {
    val density = LocalDensity.current
    var rootY by remember { mutableFloatStateOf(0f) }
    var since by remember { mutableLongStateOf(if (c.at == 0L) Long.MAX_VALUE else SystemClock.uptimeMillis() - c.at) }
    var drag by remember { mutableFloatStateOf(0f) }
    var goneAt by remember { mutableLongStateOf(0L) }
    var goneMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(c.at) {
        if (c.at == 0L) return@LaunchedEffect
        do {
            withFrameMillis { }
            since = SystemClock.uptimeMillis() - c.at
        } while (since < STEP * 2)
    }
    LaunchedEffect(goneAt) {
        if (goneAt == 0L) return@LaunchedEffect
        do {
            withFrameMillis { }
            goneMs = SystemClock.uptimeMillis() - goneAt
        } while (goneMs < STEP * 2)
        NotifCards.dismiss(c.key)
    }
    val arriving = since < STEP * 2
    val leaving = goneAt != 0L
    val step = when {
        leaving -> if (goneMs < STEP) 0 else 1
        arriving -> if (since < STEP) 0 else 1
        else -> -1
    }
    val shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootY = it.positionInRoot().y }
            .graphicsLayer {
                translationX = drag + when (step) { 0 -> 3.dp.toPx(); 1 -> (-2).dp.toPx(); else -> 0f }
                // arriving step 0: up at the top edge of the screen (she pulls it down from there)
                translationY = if (arriving && step == 0) -rootY + 4.dp.toPx() else 0f
                alpha = if (leaving && step == 1) 0.5f else 1f
            }
            .drawBehind {
                if (step < 0) return@drawBehind
                val d = 4.dp.toPx()
                if (step == 0) drawRect(GLITCH_CRIMSON, Offset(d, 0f), size)
                else {
                    drawRect(GLITCH_GREEN, Offset(-d, 0f), size)
                    if (leaving) drawRect(GLITCH_CRIMSON, Offset(d * 2, size.height * 0.4f), size.copy(height = size.height * 0.3f))
                }
            }
            .background(CapriColors.Panel.copy(alpha = maxOf(Panels.alpha, 0.5f)), shape)
            .border(1.dp, Psyche.accent, shape)
            .pointerInput(c.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) > SWIPE_DP * density.density) goneAt = SystemClock.uptimeMillis() else drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { change, dx ->
                    change.consume()
                    drag += dx
                }
            }
            .clickable { if (!leaving) onOpen(c) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (c.icon != null) Image(c.icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.app.uppercase(), color = CapriColors.Dim, fontSize = 9.sp, letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                c.title ?: "New", color = CapriColors.Text, fontSize = 13.sp, fontFamily = FAN_FONT,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
