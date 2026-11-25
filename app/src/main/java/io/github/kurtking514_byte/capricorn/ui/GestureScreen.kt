package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kurtking514_byte.capricorn.data.Gesture
import io.github.kurtking514_byte.capricorn.data.GestureJob
import io.github.kurtking514_byte.capricorn.data.Gestures
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors

/**
 * 3.12: gesture settings, opened by chat "gestures". One switch per gesture; the three
 * empty-screen trigger gestures also get a row of jobs to pick from. Changes apply at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GestureScreen(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } } // nothing falls through to home
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("GESTURES", fontFamily = FAN_FONT, fontSize = 20.sp, color = CapriColors.Text, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "✕", fontSize = 20.sp, color = CapriColors.Dim,
                modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Gesture.entries.groupBy { it.group }.forEach { (group, list) ->
                Text(
                    group, Modifier.padding(top = 14.dp, bottom = 6.dp),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp, color = GLITCH_CRIMSON,
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(GLITCH_CRIMSON))
                for (g in list) GestureRow(g)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GestureRow(g: Gesture) {
    val on = Gestures.on(g)
    val job = Gestures.job(g)
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.title.uppercase(), fontFamily = FAN_FONT, fontSize = 15.sp, color = if (on) CapriColors.Text else CapriColors.Dim)
                val what = if (job != null) "Does: ${job.label.lowercase()}" else g.what
                Text(what, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CapriColors.Dim)
            }
            Spacer(Modifier.width(12.dp))
            Chip(if (on) "ON" else "OFF", on, Modifier.width(64.dp)) { Gestures.setOn(g, !on) }
        }
        if (job != null && on) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (j in GestureJob.entries) Chip(j.label, j == job) { Gestures.setJob(g, j) }
            }
        }
    }
}

/** Flat crimson-edged button; filled crimson when [selected]. */
@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) GLITCH_CRIMSON else Color.Black).border(1.5.dp, if (selected) GLITCH_CRIMSON else CapriColors.Dim)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = FAN_FONT, fontSize = 12.sp, color = if (selected) Color.Black else CapriColors.Text)
    }
}
