package io.github.kurtking514_byte.capricorn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors

/** Full-screen app drawer: search, frequent apps, then A–Z. */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    frequent: List<AppEntry>,
    dockKeys: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val close by rememberUpdatedState(onClose)

    val q = query.trim()
    val results = remember(q, apps) {
        if (q.isEmpty()) apps
        else apps
            .filter { it.label.contains(q, ignoreCase = true) }
            .sortedWith(
                compareByDescending<AppEntry> { it.label.startsWith(q, ignoreCase = true) }
                    .thenBy { it.label.lowercase() }
            )
    }
    val byLetter = remember(apps) { apps.groupBy { letterOf(it.label) } }

    val launch: (AppEntry) -> Unit = { keyboard?.hide(); onLaunch(it) }

    // Pulling down while the list is already at the top closes the drawer.
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > 260f) {
                        pulled = 0f
                        keyboard?.hide()
                        close()
                    }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        Modifier
            .fillMaxSize()
            .background(CapriColors.Black)
            .statusBarsPadding()
            .imePadding()
            .nestedScroll(pullToClose)
    ) {
        // Grab handle: tap to close.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { keyboard?.hide(); onClose() }
                .padding(vertical = 12.dp),
        ) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CapriColors.Pink)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("search apps…", color = CapriColors.Dim) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(launch) }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CapriColors.Green,
                unfocusedBorderColor = CapriColors.Pink,
                cursorColor = CapriColors.Green,
                focusedTextColor = CapriColors.Text,
                unfocusedTextColor = CapriColors.Text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottomInset + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val cell: @Composable (AppEntry) -> Unit = { app ->
                AppItem(
                    app = app,
                    inDock = app.key in dockKeys,
                    showLabel = true,
                    onLaunch = launch,
                    onToggleDock = onToggleDock,
                    onAppInfo = onAppInfo,
                )
            }

            if (q.isEmpty()) {
                if (frequent.isNotEmpty()) {
                    item(key = "h:frequent", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("FREQUENT") }
                    items(frequent, key = { "f:" + it.key }) { cell(it) }
                }
                byLetter.forEach { (letter, group) ->
                    item(key = "h:$letter", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(letter) }
                    items(group, key = { it.key }) { cell(it) }
                }
            } else {
                if (results.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "nothing by that name.",
                            color = CapriColors.Dim,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(results, key = { it.key }) { cell(it) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = CapriColors.Green,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp),
    )
}

private fun letterOf(label: String): String {
    val c = label.firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c.isLetter()) c.toString() else "#"
}
