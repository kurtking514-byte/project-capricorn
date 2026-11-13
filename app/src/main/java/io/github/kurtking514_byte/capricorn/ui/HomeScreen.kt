package io.github.kurtking514_byte.capricorn.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
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
 * Home: clock, Capri standing in the middle, what she's saying, and the chatbox.
 * The chatbox is the main way to do things: type an app name or a command.
 * Swiping up still opens the full app list.
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
    onSubmit: (String) -> Unit,
    onPoke: () -> Unit,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
) {
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val keyboard = LocalSoftwareKeyboardController.current
    val typing = WindowInsets.isImeVisible
    var query by remember { mutableStateOf("") }

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

    val suggestions = remember(query, apps) {
        val q = query.trim().replace(Regex("^(open|launch|start|run)\\s+", RegexOption.IGNORE_CASE), "")
        if (q.isEmpty()) emptyList()
        else (apps.filter { it.label.startsWith(q, ignoreCase = true) } +
            apps.filter { !it.label.startsWith(q, ignoreCase = true) && it.label.contains(q, ignoreCase = true) })
            .take(4)
    }

    fun submit() {
        val text = query
        query = ""
        onSubmit(text)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(CapriColors.Black)
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -100.dp.toPx()) openDrawer() },
                    onDragCancel = { dragged = 0f },
                ) { change, amount ->
                    dragged += amount
                    change.consume()
                }
            }
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
                Spacer(Modifier.height(20.dp))
                Clock(resumeTick)
            }

            SpeechPanel(text = capriLine.take(shown), speaking = speaking)
            Spacer(Modifier.height(8.dp))

            CapriViewport(
                skin = skin,
                speaking = speaking,
                mood = mood,
                moodId = lineId,
                onPoke = onPoke,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            if (suggestions.isNotEmpty()) {
                Row(
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
                            onLaunch = { query = ""; keyboard?.hide(); onLaunch(it) },
                            onToggleDock = onToggleDock,
                            onAppInfo = onAppInfo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
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
                    focusedContainerColor = CapriColors.Panel,
                    unfocusedContainerColor = CapriColors.Panel,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!typing) {
                Spacer(Modifier.height(10.dp))
                Dock(dockApps, onLaunch, onToggleDock, onAppInfo)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Clock(resumeTick: Int) {
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
    val date = now.format(DateTimeFormatter.ofPattern("EEE · d MMM", Locale.getDefault()))

    Text(
        text = time,
        style = TextStyle(
            fontFamily = Exo2,
            fontWeight = FontWeight.Black,
            fontSize = 56.sp,
            color = CapriColors.Text,
        ),
    )
    Text(
        text = date.uppercase(),
        color = CapriColors.Cyan,
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 3.sp,
    )
    Spacer(Modifier.height(10.dp))
}

/**
 * Capri standing on the home screen, jittering like a hand-cut animation (about 9 fps).
 * She blinks at random, her mouth flaps while she talks, and tapping her pokes her.
 * The jitter stops whenever the launcher isn't on screen.
 */
@Composable
private fun CapriViewport(
    skin: CapriSkin?,
    speaking: Boolean,
    mood: String,
    moodId: Int,
    onPoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val poke by rememberUpdatedState(onPoke)
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

    // Framed viewport with corner brackets and a small status tag, like a video feed of her.
    Box(
        modifier.drawBehind {
            val c = CapriColors.Cyan
            val arm = 18.dp.toPx()
            val sw = 2.dp.toPx()
            drawRect(c.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()))
            for ((x, y) in listOf(0f to 0f, size.width to 0f, 0f to size.height, size.width to size.height)) {
                val dx = if (x == 0f) arm else -arm
                val dy = if (y == 0f) arm else -arm
                drawLine(c, Offset(x, y), Offset(x + dx, y), strokeWidth = sw)
                drawLine(c, Offset(x, y), Offset(x, y + dy), strokeWidth = sw)
            }
        }
    ) {
        if (skin != null) {
            CapriSpriteFigure(
                skin = skin,
                seed = seed,
                speaking = speaking,
                mood = mood,
                moodId = moodId,
                onPoke = onPoke,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 22.dp, bottom = 6.dp),
            )
        } else {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { poke() }) }
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
        Text(
            text = if (speaking) "CAPRI // SPEAKING" else "CAPRI // ${mood.uppercase()}",
            color = CapriColors.Black,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .padding(start = 10.dp)
                .background(CapriColors.Cyan)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** What Capri is saying, in a flat cut-corner panel. */
@Composable
private fun SpeechPanel(text: String, speaking: Boolean) {
    val shape = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, CapriColors.Cyan, shape)
            .background(CapriColors.Panel, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row {
            Text("CAPRI", color = CapriColors.Pink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(
                "  /  INCOMING TRANSMISSION",
                color = CapriColors.Dim,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = text.ifEmpty { " " },
            color = CapriColors.Text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
        )
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
) {
    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CapriColors.Cyan.copy(alpha = 0.5f), shape)
            .background(CapriColors.Panel, shape)
            .padding(vertical = 6.dp, horizontal = 8.dp),
    ) {
        if (apps.isEmpty()) {
            Text(
                "long-press any app to pin it here",
                color = CapriColors.Dim,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                apps.forEach { app ->
                    AppItem(
                        app = app,
                        inDock = true,
                        showLabel = false,
                        onLaunch = onLaunch,
                        onToggleDock = onToggleDock,
                        onAppInfo = onAppInfo,
                    )
                }
            }
        }
    }
}
