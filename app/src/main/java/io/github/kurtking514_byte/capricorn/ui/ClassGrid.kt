package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kurtking514_byte.capricorn.capri.ClassSlot
import io.github.kurtking514_byte.capricorn.capri.Timetable
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 3.9d: the week grid (designed in preview/class-lab.html), opened by chat "classes".
 * Mon–Fri, 7:00–18:00. Tap an empty spot = add a 1 h class; drag a class = move it (snaps to
 * 15 min, can change day); drag its bottom edge = longer/shorter; tap it = rename, copy to other
 * days, delete. Dragging on an empty spot scrolls. Every change is saved at once ([Timetable]),
 * so chat and the status scan see the same week.
 */

private const val G0 = 7 * 60
private const val G1 = 18 * 60
private const val PX_PER_MIN = 1.1f                      // dp per minute
private val GUTTER = 34.dp
private val DEEP = Color(0xFF3A000D)
private val HOUR_LINE = Color(0xFF17131E)

private fun minuteNow(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
private fun snap(v: Float): Int = (v / 15f).roundToInt() * 15
private fun dayShort(d: Int) = Timetable.DAYS[d].let { it[0] + it.substring(1, 2).lowercase() }

@Composable
fun ClassGrid(onClose: () -> Unit) {
    val context = LocalContext.current
    val classes = remember { Timetable.load(context).toMutableStateList() }
    var sel by remember { mutableStateOf<Int?>(null) }
    var focusNew by remember { mutableStateOf(false) }
    fun save() = Timetable.save(context, classes.toList())

    var nowMin by remember { mutableIntStateOf(minuteNow()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowMin = minuteNow() } }
    val today = Timetable.today()

    val density = LocalDensity.current
    val ppm = with(density) { PX_PER_MIN.dp.toPx() }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {                                // start around "now"
        snapshotFlow { scroll.maxValue }.first { it > 0 && it < Int.MAX_VALUE }
        scroll.scrollTo(((nowMin - G0) * ppm - with(density) { 120.dp.toPx() }).toInt().coerceIn(0, scroll.maxValue))
    }
    val focus = LocalFocusManager.current

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } } // nothing falls through to home
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("WEEK", fontFamily = FAN_FONT, fontSize = 20.sp, color = CapriColors.Text, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "✕", fontSize = 20.sp, color = CapriColors.Dim,
                modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp, end = 6.dp)) {
            Spacer(Modifier.width(GUTTER))
            for (d in 0 until 5) {
                Text(
                    dayShort(d), Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = FAN_FONT, fontSize = 13.sp,
                    color = if (d == today) GLITCH_CRIMSON else CapriColors.Dim,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(end = 6.dp, top = 8.dp).height(((G1 - G0) * PX_PER_MIN + 10).dp)) {
                val colW = (maxWidth - GUTTER) / 5
                Canvas(Modifier.fillMaxSize()) {
                    for (m in G0..G1 step 60) drawRect(HOUR_LINE, Offset(0f, (m - G0) * ppm), Size(size.width, 1.dp.toPx()))
                    if (today < 5 && nowMin in G0..G1) {
                        drawRect(GLITCH_GREEN, Offset(30.dp.toPx(), (nowMin - G0) * ppm - 1.dp.toPx()), Size(size.width - 30.dp.toPx(), 2.dp.toPx()))
                    }
                }
                for (m in G0..G1 step 60) {
                    Text(
                        "${m / 60}", Modifier.offset(y = ((m - G0) * PX_PER_MIN - 6).dp).width(30.dp), textAlign = TextAlign.End,
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp, color = CapriColors.Dim,
                    )
                }
                classes.forEachIndexed { i, c ->
                    if (c.day < 5) ClassBlock(
                        c, selected = i == sel,
                        Modifier.offset(x = GUTTER + colW * c.day + 2.dp, y = ((c.start - G0) * PX_PER_MIN).dp)
                            .size(colW - 4.dp, ((c.end - c.start) * PX_PER_MIN).dp),
                    )
                }
                // one touch layer over everything: blocks are found by position
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    val gutter = GUTTER.toPx()
                    val slop = 6.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val colPx = (size.width - gutter) / 5
                        val p = down.position
                        val hit = classes.indices.lastOrNull { i ->
                            val c = classes[i]
                            c.day < 5 && p.x >= gutter + c.day * colPx && p.x < gutter + (c.day + 1) * colPx &&
                                p.y >= (c.start - G0) * ppm && p.y <= (c.end - G0) * ppm
                        }
                        if (hit != null) {
                            val c0 = classes[hit]
                            val h = (c0.end - c0.start) * ppm
                            val resize = p.y >= (c0.end - G0) * ppm - minOf(16.dp.toPx(), h * 0.4f)
                            down.consume()
                            var moved = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                ch.consume()
                                if (!ch.pressed) break
                                val dx = ch.position.x - p.x
                                val dy = ch.position.y - p.y
                                if (!moved && abs(dx) + abs(dy) > slop) moved = true
                                if (!moved || hit !in classes.indices) continue
                                val dMin = dy / ppm
                                classes[hit] = if (resize) {
                                    c0.copy(end = snap(c0.end + dMin).coerceIn(c0.start + 15, G1))
                                } else {
                                    val len = c0.end - c0.start
                                    val st = snap(c0.start + dMin).coerceIn(G0, G1 - len)
                                    c0.copy(start = st, end = st + len, day = ((ch.position.x - gutter) / colPx).toInt().coerceIn(0, 4))
                                }
                            }
                            if (moved) save() else { sel = if (sel == hit) null else hit; focus.clearFocus() }
                        } else if (p.x >= gutter) {
                            waitForUpOrCancellation() ?: return@awaitEachGesture   // it became a scroll
                            val day = ((p.x - gutter) / colPx).toInt().coerceIn(0, 4)
                            val st = snap(G0 + p.y / ppm).coerceIn(G0, G1 - 60)
                            classes.add(ClassSlot("NEW CLASS", day, st, st + 60))
                            save()
                            sel = classes.lastIndex
                            focusNew = true
                        }
                    }
                })
            }
        }

        val s = sel?.takeIf { it in classes.indices }
        if (s != null) {
            ClassSheet(
                key = s, slot = classes[s], focusNew = focusNew, onFocused = { focusNew = false },
                classes = classes,
                onRename = { new ->
                    val old = classes[s]
                    for (j in classes.indices) if (classes[j].name == old.name && classes[j].start == old.start) classes[j] = classes[j].copy(name = new)
                    save()
                },
                onCopy = { d ->
                    val c = classes[s]
                    val twin = classes.indexOfFirst { it.name == c.name && it.day == d && it.start == c.start }
                    when {
                        twin < 0 -> classes.add(c.copy(day = d, end = c.end))
                        twin != s -> { classes.removeAt(twin); if (twin < s) sel = s - 1 }
                    }
                    save()
                },
                onDone = { sel = null; focus.clearFocus() },
                onDelete = { classes.removeAt(s); sel = null; focus.clearFocus(); save() },
            )
        } else {
            Text(
                "TAP EMPTY = ADD · DRAG = MOVE · EDGE = LONGER", Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
                textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp, color = CapriColors.Dim,
            )
        }
    }
}

@Composable
private fun ClassBlock(c: ClassSlot, selected: Boolean, modifier: Modifier) {
    val ink = if (selected) Color.Black else CapriColors.Text
    Box(modifier.clipToBounds().background(if (selected) GLITCH_CRIMSON else DEEP).border(1.5.dp, GLITCH_CRIMSON)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
            Text(c.name, fontFamily = FAN_FONT, fontSize = 11.sp, lineHeight = 12.sp, color = ink, maxLines = 2, overflow = TextOverflow.Clip)
            Text(Timetable.fmt(c.start), fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 11.sp, color = if (selected) Color.Black else CapriColors.Dim)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(14.dp, 2.dp).background(if (selected) Color.Black else GLITCH_CRIMSON))
    }
}

@Composable
private fun ClassSheet(
    key: Int, slot: ClassSlot, focusNew: Boolean, onFocused: () -> Unit, classes: List<ClassSlot>,
    onRename: (String) -> Unit, onCopy: (Int) -> Unit, onDone: () -> Unit, onDelete: () -> Unit,
) {
    var field by remember(key) { mutableStateOf(TextFieldValue(slot.name, TextRange(0, slot.name.length))) }
    val req = remember { FocusRequester() }
    LaunchedEffect(key, focusNew) { if (focusNew) { req.requestFocus(); onFocused() } }

    Column(
        Modifier.fillMaxWidth().background(Color.Black).padding(top = 0.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(GLITCH_CRIMSON))
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicTextField(
                value = field,
                onValueChange = { v ->
                    val up = v.text.uppercase().take(24)
                    field = v.copy(text = up)
                    onRename(up.trim().ifEmpty { "CLASS" })
                },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FAN_FONT, fontSize = 15.sp, color = CapriColors.Text),
                cursorBrush = SolidColor(GLITCH_CRIMSON),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(req).border(1.5.dp, GLITCH_CRIMSON).padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Text(
                "${Timetable.DAYS[slot.day]} ${Timetable.fmt(slot.start)}–${Timetable.fmt(slot.end)} · copy to:",
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = CapriColors.Dim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in 0 until 5) {
                    val on = classes.any { it.name == slot.name && it.day == d && it.start == slot.start }
                    SheetButton(dayShort(d), on, Modifier.weight(1f)) { onCopy(d) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetButton("DONE", true, Modifier.weight(1f), onDone)
                SheetButton("DELETE", false, Modifier.weight(1f), onDelete)
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (on) GLITCH_CRIMSON else Color.Black).border(1.5.dp, GLITCH_CRIMSON)
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = FAN_FONT, fontSize = 13.sp, color = if (on) Color.Black else CapriColors.Text)
    }
}
