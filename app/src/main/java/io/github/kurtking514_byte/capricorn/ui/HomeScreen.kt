package io.github.kurtking514_byte.capricorn.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Minimal home: clock, Capri's projection spot, dock. Swipe up opens the app drawer. */
@Composable
fun HomeScreen(
    dockApps: List<AppEntry>,
    resumeTick: Int,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
) {
    val openDrawer by rememberUpdatedState(onOpenDrawer)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CapriColors.Black, Color(0xFF16091C), CapriColors.Black)
                )
            )
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
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            Clock(resumeTick)
            Spacer(Modifier.weight(1f))
            ProjectionStandby()
            Spacer(Modifier.weight(1f))
            Text(
                "⌃ swipe up",
                color = CapriColors.Dim,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(10.dp))
            Dock(dockApps, onLaunch, onToggleDock, onAppInfo)
            Spacer(Modifier.height(12.dp))
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
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            fontSize = 76.sp,
            color = CapriColors.Pink,
            shadow = Shadow(color = CapriColors.Pink, blurRadius = 28f),
        ),
    )
    Text(
        text = date.uppercase(),
        color = CapriColors.Green,
        style = MaterialTheme.typography.titleSmall,
    )
}

/** Placeholder where Capri will be projected (Phase 2). */
@Composable
private fun ProjectionStandby() {
    val blink = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "CAPRI",
            color = CapriColors.Blue,
            style = MaterialTheme.typography.headlineSmall,
        )
        Row {
            Text(
                "[ projection standby ",
                color = CapriColors.Dim,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "█",
                color = CapriColors.Green,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(cursorAlpha),
            )
            Text(
                " ]",
                color = CapriColors.Dim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CapriColors.Pink.copy(alpha = 0.45f), shape)
            .background(CapriColors.Panel.copy(alpha = 0.7f), shape)
            .padding(vertical = 8.dp, horizontal = 8.dp),
    ) {
        if (apps.isEmpty()) {
            Text(
                "long-press any app to pin it here",
                color = CapriColors.Dim,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 20.dp),
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
