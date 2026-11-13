package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlinx.coroutines.delay

private const val TITLE = "PROJECT CAPRICORN"
private const val BODY = "\n> biochip linked\n> psyches merged ... OK\n> Capri online."

/** Typed-out boot sequence on cold start. Tap anywhere to skip. */
@Composable
fun BootOverlay(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val full = TITLE + BODY
    var shown by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (shown < full.length) {
            shown++
            delay(if (full[shown - 1] == '\n') 220L else 24L)
        }
        delay(600L)
        done()
    }

    val text = buildAnnotatedString {
        val titleChars = minOf(shown, TITLE.length)
        withStyle(SpanStyle(color = CapriColors.Pink)) { append(TITLE.take(titleChars)) }
        if (shown > TITLE.length) {
            withStyle(SpanStyle(color = CapriColors.Green)) { append(BODY.take(shown - TITLE.length)) }
        }
        withStyle(SpanStyle(color = CapriColors.Green)) { append("█") }
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxSize()
            .background(CapriColors.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { done() }
            .padding(32.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}
