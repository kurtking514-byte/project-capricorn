package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors

/** App icon (+ optional label). Tap launches, long-press opens a small menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppEntry,
    inDock: Boolean,
    showLabel: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(6.dp),
        ) {
            Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(48.dp))
            if (showLabel) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = CapriColors.Text,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (inDock) "Remove from dock" else "Pin to dock") },
                onClick = { menuOpen = false; onToggleDock(app) },
            )
            DropdownMenuItem(
                text = { Text("App info") },
                onClick = { menuOpen = false; onAppInfo(app) },
            )
        }
    }
}
