package io.github.kurtking514_byte.capricorn.ui

import io.github.kurtking514_byte.capricorn.capri.ContactNumber
import io.github.kurtking514_byte.capricorn.capri.Contacts
import io.github.kurtking514_byte.capricorn.capri.SettingPage
import io.github.kurtking514_byte.capricorn.capri.SettingsSearch

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import io.github.kurtking514_byte.capricorn.ui.theme.Exo2
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * Home: a cyberpunk clock, Capri standing free in the middle with her speech bubble right
 * above her head, the chatbox, and the dock. The chatbox is the main way to do things:
 * type an app name or a command. Swiping up still opens the full app list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    dockApps: List<AppEntry>,
    dockKeys: Set<String>,
    skin: CapriSkin?,
    capriLine: String,
    lineId: Int,
    mood: String,
    resumeTick: Int,
    commandId: Int,                 // goes up each time she runs a command (red hit)
    onSubmit: (String) -> Unit,
    onPoke: (held: Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onGrabAction: (AppEntry, GrabAction) -> Unit,
    letterPick: Pair<Char, List<AppEntry>>?,       // 3.5: several apps start with the drawn letter
    onLetter: (Char?) -> Unit,
    onPickLetterApp: (AppEntry) -> Unit,
    contactPick: List<ContactNumber>? = null,        // 3.13: "call alex" matched several numbers
    onPickContact: (ContactNumber) -> Unit = {},
    onOpenSetting: (SettingPage) -> Unit = {},        // 3.14: a settings chip in the live search
    onDoubleTapEmpty: () -> Unit,                   // 3.6: lock
    onTwoFingerDown: () -> Unit,                    // 3.7: flashlight
    onTwoFingerHold: () -> Unit,                    // 3.9a: status scan
) {
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val keyboard = LocalSoftwareKeyboardController.current
    val typing = WindowInsets.isImeVisible
    var query by remember { mutableStateOf("") }
    // 3.2: long-press on her puts the cursor in the chatbox and brings up the keyboard
    val chatFocus = remember { FocusRequester() }
    val pokeOrListen: (Boolean) -> Unit = { held ->
        if (held) {
            chatFocus.requestFocus()
            keyboard?.show()
        }
        onPoke(held)
    }

    // Her line types itself out; she "talks" while it does.
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(lineId) {
        shown = 0
        while (shown < capriLine.length) {
            shown++
            delay(22)
        }
    }
    val speaking = shown < capriLine.length

    // Smooth entrance once the boot has finished (her first line), a spring "pop" on her
    // speech bubble for every new line, and a quick crimson flash across the screen.
    val enter = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(lineId > 0) {
        if (lineId > 0) enter.snapTo(1f)               // 4.6d: the boot snaps off and home is already in place
    }
    LaunchedEffect(lineId) {
        if (lineId > 0) {
            pop.snapTo(0f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    LaunchedEffect(lineId) {
        if (lineId > 1) {
            flash.snapTo(0.34f)
            flash.animateTo(0f, tween(240))
        }
    }
    // C3: 0 = nothing, 1 = full-screen crimson, 2 = crimson slash
    var commandHit by remember { mutableIntStateOf(0) }
    LaunchedEffect(commandId) {
        if (commandId == 0) return@LaunchedEffect
        commandHit = 1; delay(70)
        commandHit = 0; delay(40)
        commandHit = 2; delay(60)
        commandHit = 0
    }

    val suggestions = remember(query, apps) {
        val q = query.trim().replace(Regex("^(open|launch|start|run)\\s+", RegexOption.IGNORE_CASE), "")
        if (q.isEmpty()) emptyList()
        else (apps.filter { it.label.startsWith(q, ignoreCase = true) } +
            apps.filter { !it.label.startsWith(q, ignoreCase = true) && it.label.contains(q, ignoreCase = true) })
            .take(4)
    }
    // 3.14: the same live search also finds contacts (only if Contacts access is already on) and settings pages
    val context = androidx.compose.ui.platform.LocalContext.current
    val contactHits = remember(query) {
        val q = query.trim().replace(Regex("^(call|phone|dial|ring)\\s+", RegexOption.IGNORE_CASE), "")
        if (q.length < 2) emptyList() else Contacts.find(context, q, all = true).take(3)
    }
    val settingHits = remember(query) { SettingsSearch.search(query, 3) }
    val labeled = listOf(suggestions.isNotEmpty(), contactHits.isNotEmpty(), settingHits.isNotEmpty()).count { it } > 1

    fun submit() {
        val text = query
        query = ""
        onSubmit(text)
    }

    Box(
        Modifier
            .fillMaxSize()                               // 4.6a: no background, the wallpaper shows through
            .trackGaze()                                 // she looks toward wherever you touch
            // straight swipe up = app list; any other one-finger stroke on the empty screen = a letter (3.5)
            .homeStrokes(onDrawer = { openDrawer() }, onLetter = onLetter, onDoubleTap = onDoubleTapEmpty,
                onTwoFingerDown = onTwoFingerDown, onTwoFingerHold = onTwoFingerHold)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            if (!typing) {
                Spacer(Modifier.height(14.dp))
                Clock(
                    resumeTick,
                    Modifier
                        .bootSlot(BootDock.CLOCK)                   // 4.6d: struck with the chatbox and apps
                        .graphicsLayer {
                            alpha = if (BootDock.active || BootDock.CLOCK in BootDock.struck) 1f else enter.value.coerceIn(0f, 1f)
                        },
                )
            }

            CapriStage(
                skin = skin,
                text = capriLine.take(shown),
                speaking = speaking,
                mood = mood,
                moodId = lineId,
                enter = { if (BootDock.active) 0f else enter.value.coerceIn(0f, 1f) },   // 4.6d: the boot draws her
                pop = { pop.value },
                onPoke = pokeOrListen,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            if (suggestions.isNotEmpty() || contactHits.isNotEmpty() || settingHits.isNotEmpty()) {
                fun done() { query = ""; keyboard?.hide() }
                if (labeled && suggestions.isNotEmpty()) SearchLabel("APPS")
                if (suggestions.isNotEmpty()) Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    suggestions.forEach { app ->
                        AppItem(
                            app = app,
                            inDock = app.key in dockKeys,
                            showLabel = true,
                            onLaunch = { done(); onLaunch(it) },
                            onToggleDock = onToggleDock,
                            onAppInfo = onAppInfo,
                            modifier = Modifier.weight(1f),
                            draggable = true,
                        )
                    }
                }
                if (contactHits.isNotEmpty()) {
                    if (labeled) SearchLabel("CONTACTS")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        contactHits.forEach { c -> SearchChip(c.name, c.type, Modifier.weight(1f)) { done(); onPickContact(c) } }
                        repeat(3 - contactHits.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (settingHits.isNotEmpty()) {
                    if (labeled) SearchLabel("SETTINGS")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        settingHits.forEach { pg -> SearchChip(pg.title, if (pg.isCapri) "CAPRI" else "SETTINGS", Modifier.weight(1f)) { done(); onOpenSetting(pg) } }
                        repeat(3 - settingHits.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else if (contactPick != null) {
                // 3.13: "Call who?": up to 4 matching numbers; tap one = dialer with it typed in
                Text(
                    "CALL WHO?", color = CapriColors.Acid,
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    contactPick.forEach { c ->
                        Column(
                            Modifier.weight(1f).background(androidx.compose.ui.graphics.Color.Black)
                                .border(1.5.dp, GLITCH_CRIMSON).clickable { onPickContact(c) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            Text(c.name, color = CapriColors.Text, fontSize = 13.sp, fontFamily = FAN_FONT, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(c.type, color = CapriColors.Dim, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1)
                        }
                    }
                }
            } else if (letterPick != null) {
                // 3.5: "Pick one": up to 4 apps that start with the letter you drew, most used first
                Text(
                    "STARTS WITH ${letterPick.first}", color = CapriColors.Acid,
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    letterPick.second.forEach { app ->
                        AppItem(
                            app = app,
                            inDock = app.key in dockKeys,
                            showLabel = true,
                            onLaunch = onPickLetterApp,
                            onToggleDock = onToggleDock,
                            onAppInfo = onAppInfo,
                            modifier = Modifier.weight(1f),
                            draggable = true,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("what do you want to do?", color = CapriColors.Dim) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CapriColors.Acid,
                    unfocusedBorderColor = CapriColors.Cyan,
                    cursorColor = CapriColors.Acid,
                    focusedTextColor = CapriColors.Text,
                    unfocusedTextColor = CapriColors.Text,
                    focusedContainerColor = CapriColors.Panel.copy(alpha = Panels.alpha),      // 4.6b
                    unfocusedContainerColor = CapriColors.Panel.copy(alpha = Panels.alpha),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(chatFocus)
                    .bootSlot(BootDock.CHAT)                // boot lightning reveals the chatbox
                    .graphicsLayer {
                        // struck in the boot: already in place; otherwise it slides in
                        val struck = BootDock.CHAT in BootDock.struck
                        translationY = if (struck || BootDock.active) 0f else 90.dp.toPx() * (1f - enter.value)
                        alpha = if (struck || BootDock.active) 1f else enter.value.coerceIn(0f, 1f)
                    },
            )

            if (!typing) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.graphicsLayer {
                        // 4.6d: during the boot the dock frame appears when lightning hits its slots
                        val struck = BootDock.struck.keys.any { it < BootDock.CHAT }
                        translationY = if (struck || BootDock.active) 0f else 140.dp.toPx() * (1f - enter.value)
                        alpha = if (BootDock.active) (if (struck) 1f else 0f) else if (struck) 1f else enter.value.coerceIn(0f, 1f)
                    }
                ) {
                    Dock(dockApps, onLaunch, onToggleDock, onAppInfo, onEmptySlot = openDrawer)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 3.3: the app you drag, the app she holds + her options menu, the pin lightning
        GrabLayer(inDock = { it.key in dockKeys }, onAction = onGrabAction)
        LetterTrail()                                   // 3.5: the lightning under your finger
        StatusScanPanel()                               // 3.9a: two-finger hold readout

        // Crimson flash whenever she does or says something (no touch handling, so it never blocks taps).
        Canvas(Modifier.fillMaxSize()) {
            // C3: hard red hit when she actually does something (full screen, then a slash)
            when (commandHit) {
                1 -> drawRect(CapriColors.Pink)
                2 -> {
                    val cy = size.height * 0.45f
                    val band = size.height * 0.12f
                    val dx = size.width
                    val slash = androidx.compose.ui.graphics.Path().apply {
                        moveTo(-dx, cy + band * 1.6f); lineTo(size.width + dx, cy - band * 2.4f)
                        lineTo(size.width + dx, cy - band * 1.4f); lineTo(-dx, cy + band * 2.6f); close()
                    }
                    drawPath(slash, CapriColors.Pink)
                }
            }
            val a = flash.value
            if (a > 0.004f) {
                drawRect(CapriColors.Pink, alpha = a)
                // two hard-edged slashes across the flash, like her red taking over the screen
                val w = size.width
                val h = size.height
                val dark = (a * 2.2f).coerceAtMost(0.8f)
                drawPath(Path().apply {
                    moveTo(0f, h * 0.30f); lineTo(w, h * 0.18f); lineTo(w, h * 0.24f); lineTo(0f, h * 0.42f); close()
                }, Color.Black, alpha = dark)
                drawPath(Path().apply {
                    moveTo(0f, h * 0.74f); lineTo(w, h * 0.60f); lineTo(w, h * 0.63f); lineTo(0f, h * 0.80f); close()
                }, Color.Black, alpha = dark)
            }
        }
    }
}

/**
 * Cyberpunk clock: a heavy slanted time with a crimson/cyan split, the date on an
 * acid-yellow tag, and a bar of 24 segments showing how far through the day it is.
 */
@Composable
private fun Clock(resumeTick: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // Re-sync on every resume (the phone may have slept), then tick on each minute.
    LaunchedEffect(resumeTick) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val time = now.format(DateTimeFormatter.ofPattern(pattern))
    val day = now.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase()
    val date = now.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
    val timeStyle = TextStyle(
        fontFamily = Exo2,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontSize = 64.sp,
        letterSpacing = (-1).sp,
    )

    Column(
        modifier.fillMaxWidth()
            .background(CapriColors.Panel.copy(alpha = Panels.alpha), CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp))   // 4.6b
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "// LOCAL TIME",
                color = CapriColors.Acid,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Psyche.accent.copy(alpha = 0.4f))
            )
            Spacer(Modifier.width(8.dp))
            Text("NC-SYS", color = CapriColors.Dim, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            // the time, drawn three times: crimson and cyan copies pushed apart behind the white one
            Box {
                Text(time, style = timeStyle, color = CapriColors.Pink, modifier = Modifier.offset(x = (-3).dp, y = 1.dp))
                Text(time, style = timeStyle, color = Psyche.accent, modifier = Modifier.offset(x = 3.dp, y = (-1).dp))
                Text(time, style = timeStyle, color = CapriColors.Text)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    day,
                    color = CapriColors.Black,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 3.sp,
                    modifier = Modifier
                        .background(CapriColors.Acid, CutCornerShape(topStart = 8.dp))
                        .padding(start = 12.dp, end = 8.dp, top = 1.dp, bottom = 1.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    date,
                    color = Psyche.accent,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    letterSpacing = 2.sp,
                )
            }
        }
        // how far through the day: 24 slanted segments, one per hour
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val gap = 3.dp.toPx()
            val slant = size.height * 0.6f
            val seg = (size.width - slant - gap * 23) / 24f
            for (hour in 0 until 24) {
                val x = hour * (seg + gap)
                val color = when {
                    hour < now.hour -> Psyche.accent
                    hour == now.hour -> CapriColors.Pink
                    else -> Psyche.accent.copy(alpha = 0.18f)
                }
                drawPath(Path().apply {
                    moveTo(x + slant, 0f); lineTo(x + seg + slant, 0f); lineTo(x + seg, size.height); lineTo(x, size.height); close()
                }, color)
            }
        }
    }
}

/**
 * The open area where Capri stands, with her speech bubble floating right above her
 * head. The bubble follows her: when she sits or lies down, it comes down with her.
 */
@Composable
private fun CapriStage(
    skin: CapriSkin?,
    text: String,
    speaking: Boolean,
    mood: String,
    moodId: Int,
    enter: () -> Float,
    pop: () -> Float,
    onPoke: (held: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val poke by rememberUpdatedState(onPoke)
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    var bubbleHeight by remember { mutableIntStateOf(0) }
    var headTop by remember { mutableFloatStateOf(0f) }

    Box(modifier) {
        if (skin != null) {
            CapriSpriteFigure(
                skin = skin,
                speaking = speaking,
                mood = mood,
                moodId = moodId,
                topInset = bubbleHeight + gap * 2.5f,
                onHeadTop = { headTop = it },
                onPoke = onPoke,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = enter() },
            )
        } else {
            // Code-drawn stand-in when no sprite skin is installed.
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val seed by produceState(0) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        delay(110)
                        value++
                    }
                }
            }
            val open = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(Random.nextLong(2_200L, 5_500L))
                    open.animateTo(0.03f, tween(80))
                    open.animateTo(1f, tween(120))
                }
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(top = with(density) { bubbleHeight.toDp() } + 8.dp)
                    .graphicsLayer { alpha = enter() }
                    .pointerInput(Unit) { detectTapGestures(onTap = { poke(false) }, onLongPress = { poke(true) }) }
            ) {
                val u = minOf(size.height / 1.22f, size.width / 1.28f)
                drawCapriBody(
                    center = Offset(center.x, center.y + 0.06f * u),
                    u = u,
                    seed = seed,
                    open = open.value,
                    talk = if (speaking) (if (seed % 2 == 0) 1f else 0.35f) else 0f,
                )
            }
        }

        val bubbleY by animateFloatAsState(
            targetValue = (headTop - bubbleHeight - gap).coerceAtLeast(0f),
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
            label = "bubbleY",
        )
        SpeechBubble(
            text = text,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.92f)
                .onSizeChanged { bubbleHeight = it.height }
                .graphicsLayer {
                    translationY = bubbleY + 14.dp.toPx() * (1f - pop())
                    alpha = if (CapriAway.away) 0f else enter()          // 3.4: clean home while she's away
                    scaleX = 0.94f + 0.06f * pop()
                    scaleY = 0.94f + 0.06f * pop()
                },
        )
    }
}

/** What Capri is saying: a flat cut-corner bubble with a small tail pointing down at her. */
@Composable
private fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    val shape = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
    Column(
        modifier
            .padding(bottom = 9.dp)
            .drawBehind {
                // tail
                val cx = size.width / 2f
                val half = 9.dp.toPx()
                val tail = Path().apply {
                    moveTo(cx - half, size.height - 1f); lineTo(cx + half, size.height - 1f); lineTo(cx, size.height + half); close()
                }
                drawPath(tail, CapriColors.Panel.copy(alpha = Panels.alpha))
                drawLine(Psyche.accent, Offset(cx - half, size.height), Offset(cx, size.height + half), strokeWidth = 1.dp.toPx())
                drawLine(Psyche.accent, Offset(cx + half, size.height), Offset(cx, size.height + half), strokeWidth = 1.dp.toPx())
            }
            .border(1.dp, Psyche.accent, shape)
            .background(CapriColors.Panel.copy(alpha = Panels.alpha), shape)                  // 4.6b
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            "CAPRI //",
            color = CapriColors.Pink,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Text(
            text = text.ifEmpty { " " },
            color = CapriColors.Text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
        )
    }
}

/** Empty slots shown in the dock when no apps are pinned. */
private const val EMPTY_SLOTS = 4

@Composable
private fun Dock(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onEmptySlot: () -> Unit,
) {
    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    LaunchedEffect(apps.size) {                         // forget slots that no longer exist
        val used = if (apps.isEmpty()) EMPTY_SLOTS else apps.size
        BootDock.slots.keys.filter { it >= used && it < BootDock.CHAT }.forEach { BootDock.slots.remove(it) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CapriColors.Cyan.copy(alpha = 0.5f), shape)
            .background(CapriColors.Panel.copy(alpha = Panels.alpha), shape)                  // 4.6b
            .padding(vertical = 6.dp, horizontal = 8.dp),
    ) {
        if (apps.isEmpty()) {
            // Nothing pinned: 4 empty slots (the boot's lightning reveals them). Tap one to pick an app.
            val slotShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                repeat(EMPTY_SLOTS) { i ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .bootSlot(i)
                            .size(48.dp)
                            .border(1.dp, CapriColors.Cyan.copy(alpha = 0.35f), slotShape)
                            .clickable(onClick = onEmptySlot),
                    ) {
                        Text("+", color = CapriColors.Dim, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                apps.forEachIndexed { i, app ->
                    Box(Modifier.bootSlot(i)) {          // boot lightning reveals each slot
                        AppItem(
                            app = app,
                            inDock = true,
                            showLabel = false,
                            onLaunch = onLaunch,
                            onToggleDock = onToggleDock,
                            onAppInfo = onAppInfo,
                            draggable = true,
                        )
                    }
                }
            }
        }
    }
}

/** 3.14: small label over a live search row (APPS / CONTACTS / SETTINGS). */
@Composable
private fun SearchLabel(text: String) {
    Text(
        text, color = CapriColors.Acid, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
    )
}

/** 3.14: flat crimson-edged chip: [title] on top, small [sub] (MOBILE / SETTINGS / CAPRI) under it. */
@Composable
private fun SearchChip(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(androidx.compose.ui.graphics.Color.Black).border(1.5.dp, GLITCH_CRIMSON)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 7.dp),
    ) {
        Text(title, color = CapriColors.Text, fontSize = 13.sp, fontFamily = FAN_FONT, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(sub, color = CapriColors.Dim, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1)
    }
}
