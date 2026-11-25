package io.github.kurtking514_byte.capricorn.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kurtking514_byte.capricorn.capri.Contacts
import io.github.kurtking514_byte.capricorn.capri.FaceDown
import io.github.kurtking514_byte.capricorn.capri.ScreenTime
import io.github.kurtking514_byte.capricorn.capri.Weather
import io.github.kurtking514_byte.capricorn.overlay.CapriNotifListener
import io.github.kurtking514_byte.capricorn.overlay.CapriOverlayService
import io.github.kurtking514_byte.capricorn.ui.theme.CapriColors
import kotlinx.coroutines.delay

/**
 * 4.4: every permission Capri can use, in her voice (designed in preview/setup-lab.html).
 * [line] = her line under the row, [how] = what she says when you tap ALLOW and Android's page
 * opens, [yes] = her reaction when it turns on. Only HOME is needed; the rest just switch features on.
 */
enum class SetupItem(val title: String, val line: String, val how: String, val yes: String, val needed: Boolean = false) {
    HOME("Home app", "Make me your home screen. Obviously.", "Pick Capri. Then I'm home.", "Finally. I live here now.", needed = true),
    OVERLAY("Overlay", "Let me follow you into other apps. Mini me.",
        "Tap \"Downloaded apps\", then \"Capri overlay\", switch me on.", "Now I'm everywhere. Try to behave."),
    NOTIFS("Notifications", "So I can say who's pinging. Never what they say.",
        "Find \"Capri\" and switch it on.", "Pings unlocked. Popular, aren't we?"),
    USAGE("Usage access", "Screen time. I count minutes, not secrets.",
        "Find \"Capri\" and allow usage access.", "Minutes, counted. Brace yourself."),
    DND("Do Not Disturb", "Face down = real silence, not just buzzing.",
        "Find \"Capri\" and allow it.", "Full silence. Flip me and I go dark."),
    LOCATION("Rough location", "For the weather. Rounded to about 10 km.",
        "Tap allow. Approximate is plenty.", "Sky, located. I'll warn you about rain."),
    CONTACTS("Contacts", "So \"call mom\" knows who mom is.",
        "Tap allow. Names and numbers, that's all.", "Got your people. I won't judge. Much.");

    fun isOn(context: Context): Boolean = when (this) {
        HOME -> isHome(context)
        OVERLAY -> CapriOverlayService.instance != null
        NOTIFS -> CapriNotifListener.isEnabled(context)
        USAGE -> ScreenTime.allowed(context)
        DND -> FaceDown.fullSilentAllowed(context)
        LOCATION -> Weather.allowed(context)
        CONTACTS -> Contacts.allowed(context)
    }

    companion object {
        fun isHome(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
            }
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == context.packageName
        }
    }
}

/** 4.4: her line in the box at the top of the setup screen (MainActivity sets it too, on a "no"). */
object Setup {
    var line by mutableStateOf("")

    fun deny() { line = listOf("Changed your mind? Typical.", "No? Fine. That bit of me stays off.").random() }
}

private val INTRO = listOf(
    "Quick setup. Tap what you want me to do. Skip the rest, I'll live.",
    "Permissions. Boring, I know. Tap, tap, done.",
)
private const val ALL_ON = "All of me, unlocked. You won't regret it. Probably."
private val SEG_OFF = Color(0xFF1C1824)
private val ROW_LINE = Color(0xFF17131E)

/**
 * 4.4: the SETUP checklist. Opens once after the very first boot and on chat "setup".
 * ALLOW → [onAllow] opens Android's page / pop-up; the screen re-checks every 0.5 s, and when one
 * turns on the chip snaps to a green ON with a short glitch and she reacts. [onClose] gets
 * later = true for LATER / ✕ / Back.
 */
@Composable
fun SetupScreen(onAllow: (SetupItem) -> Unit, onClose: (later: Boolean) -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(SetupItem.entries.filter { it.isOn(ctx) }.toSet()) }
    var glitchIds by remember { mutableStateOf(mapOf<SetupItem, Int>()) }
    LaunchedEffect(Unit) {
        Setup.line = if (on.size == SetupItem.entries.size) ALL_ON else INTRO.random()
        while (true) {
            delay(500)
            val now = SetupItem.entries.filter { it.isOn(ctx) }.toSet()
            val fresh = now - on
            on = now
            if (fresh.isNotEmpty()) {
                glitchIds = glitchIds + fresh.associateWith { (glitchIds[it] ?: 0) + 1 }
                Setup.line = if (now.size == SetupItem.entries.size) ALL_ON else fresh.first().yes
            }
        }
    }
    val total = SetupItem.entries.size
    val all = on.size == total

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } } // nothing falls through to home
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SETUP", fontFamily = FAN_FONT, fontSize = 20.sp, color = CapriColors.Text, letterSpacing = 1.sp)
            Spacer(Modifier.width(10.dp))
            Text("${on.size} / $total", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = CapriColors.Dim, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "✕", fontSize = 20.sp, color = CapriColors.Dim,
                modifier = Modifier.clickable { onClose(true) }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Segments(on.size, total)
        Column(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).border(1.5.dp, CapriColors.Pink).padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text("CAPRI", fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 2.sp, color = CapriColors.Pink)
            Spacer(Modifier.height(4.dp))
            Text(Setup.line, fontSize = 14.sp, color = CapriColors.Text, lineHeight = 19.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            for (item in SetupItem.entries) SetupRow(item, item in on, glitchIds[item] ?: 0) {
                Setup.line = item.how
                onAllow(item)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!all) BigButton("LATER", false, Modifier.weight(1f)) { onClose(true) }
            BigButton("DONE", true, Modifier.weight(1f)) { onClose(false) }
        }
    }
}

/** Green progress bar: one slanted segment per permission. */
@Composable
private fun Segments(count: Int, total: Int) {
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val gap = 3.dp.toPx()
        val sk = size.height * 0.58f
        val w = (size.width - gap * (total - 1)) / total
        for (i in 0 until total) {
            val x = i * (w + gap)
            val p = Path().apply {
                moveTo(x + sk, 0f); lineTo(x + w, 0f); lineTo(x + w - sk, size.height); lineTo(x, size.height); close()
            }
            drawPath(p, if (i < count) GLITCH_GREEN else SEG_OFF)
        }
    }
}

@Composable
private fun SetupRow(item: SetupItem, on: Boolean, glitchId: Int, onAllow: () -> Unit) {
    // 2 steps of 83 ms: green + crimson copies peek out on both sides while the chip jumps 2 dp
    var step by remember { mutableIntStateOf(-1) }
    LaunchedEffect(glitchId) {
        if (glitchId == 0) return@LaunchedEffect
        step = 0; delay(83); step = 1; delay(83); step = -1
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title.uppercase(), fontFamily = FAN_FONT, fontSize = 15.sp, color = CapriColors.Text)
                    if (item.needed) {
                        Spacer(Modifier.width(6.dp))
                        Text("NEEDED", fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp, color = CapriColors.Acid)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(item.line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CapriColors.Dim, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.width(76.dp)
                    .offset(x = when (step) { 0 -> 2.dp; 1 -> (-2).dp; else -> 0.dp })
                    .drawBehind {
                        val d = 4.dp.toPx()
                        if (step == 0) {
                            drawRect(GLITCH_GREEN, Offset(-d, 0f), size)
                            drawRect(GLITCH_CRIMSON, Offset(d, 0f), size)
                        } else if (step == 1) drawRect(GLITCH_GREEN, Offset(d, 0f), size)
                    }
                    .background(Color.Black)
                    .border(1.5.dp, if (on) GLITCH_GREEN else CapriColors.Pink)
                    .then(if (on) Modifier else Modifier.clickable(onClick = onAllow))
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (on) "ON" else "ALLOW", fontFamily = FAN_FONT, fontSize = 12.sp, color = if (on) GLITCH_GREEN else CapriColors.Text)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ROW_LINE))
    }
}

@Composable
private fun BigButton(label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (filled) CapriColors.Pink else Color.Black).border(1.5.dp, CapriColors.Pink)
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = FAN_FONT, fontSize = 14.sp, color = if (filled) Color.Black else CapriColors.Text)
    }
}
