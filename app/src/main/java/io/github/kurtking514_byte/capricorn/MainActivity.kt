package io.github.kurtking514_byte.capricorn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.BatteryManager
import android.os.Bundle
import android.os.UserHandle
import android.widget.Toast
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.github.kurtking514_byte.capricorn.capri.CapriBrain
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.data.AppRepository
import io.github.kurtking514_byte.capricorn.data.Prefs
import io.github.kurtking514_byte.capricorn.overlay.CapriNotifListener
import io.github.kurtking514_byte.capricorn.ui.AppDrawer
import io.github.kurtking514_byte.capricorn.ui.AppGrab
import io.github.kurtking514_byte.capricorn.ui.CapriAway
import io.github.kurtking514_byte.capricorn.ui.GrabAction
import io.github.kurtking514_byte.capricorn.ui.BootOverlay
import io.github.kurtking514_byte.capricorn.ui.CapriSkin
import io.github.kurtking514_byte.capricorn.ui.CapriTime
import io.github.kurtking514_byte.capricorn.ui.HomeScreen
import io.github.kurtking514_byte.capricorn.ui.theme.CapriTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private lateinit var prefs: Prefs

    private var allApps by mutableStateOf<List<AppEntry>>(emptyList())
    private var hidden by mutableStateOf<Set<String>>(emptySet())     // 3.3: hidden by dragging onto her
    private val apps: List<AppEntry> get() = allApps.filter { it.key !in hidden }
    private var dockKeys by mutableStateOf<List<String>>(emptyList())
    private var launchCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    private var drawerOpen by mutableStateOf(false)
    private var showBoot by mutableStateOf(true)
    private var resumeTick by mutableIntStateOf(0)
    private var commandId by mutableIntStateOf(0)
    private var loadJob: Job? = null

    private lateinit var brain: CapriBrain
    private var capriLine by mutableStateOf("")
    private var lineId by mutableIntStateOf(0)

    private var mood by mutableStateOf("neutral")
    private var skin: CapriSkin? = null

    private fun say(line: String) {
        if (brain.mood == "reboot") {       // "boot" chat command: play the boot again
            showBoot = true
            brain.consumeActed()
            return
        }
        capriLine = line
        mood = brain.mood
        lineId++
        if (brain.consumeActed()) commandId++     // C3 red hit
    }

    /** C5: a phone reaction. Only while home is on screen and the boot is done; returns true if she said it. */
    private fun react(line: () -> String): Boolean {
        if (showBoot || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        CapriTime.wake()
        say(line())
        return true
    }

    // C5: charger plugged in → smug; battery at LOW_BATTERY% or less (not charging) → annoyed, once.
    private var wasPlugged: Boolean? = null
    private var lowWarned = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val pct = if (level < 0) -1 else level * 100 / scale
            val before = wasPlugged
            wasPlugged = plugged
            if (before == false && plugged) react { brain.charging() }
            when {
                plugged || pct > LOW_BATTERY + 5 -> lowWarned = false
                pct in 0..LOW_BATTERY && !lowWarned -> lowWarned = react { brain.lowBattery(pct) }
            }
        }
    }

    // C5: new notification → she glances up and says who it's from (throttled).
    private var lastNotif = 0L
    private fun onNotification(pkg: String) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastNotif < NOTIF_GAP_MS) return
        val label = apps.firstOrNull { it.packageName == pkg }?.label
        if (react { brain.notification(label) }) lastNotif = now
    }

    // Refresh the app list when apps are installed, removed or updated.
    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reloadApps()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reloadApps()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reloadApps()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reloadApps()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Capri is always dark, so keep status/nav bar icons light even if the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        repo = AppRepository(this)
        prefs = Prefs(this)
        brain = CapriBrain(this)
        skin = CapriSkin.load(this)
        dockKeys = prefs.dock()
        hidden = prefs.hidden()
        AppGrab.onHover = { over -> if (over) say(brain.grabHover()) }
        AppGrab.onDrop = { say(brain.grab(it.label)) }
        CapriAway.onChange = { away -> if (!away) say(brain.back()) }
        launchCounts = prefs.launchCounts()
        getSystemService(LauncherApps::class.java).registerCallback(packageCallback)
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        reloadApps()

        setContent {
            CapriTheme {
                // A launcher never "goes back" past home; back just closes the drawer.
                BackHandler { drawerOpen = false }

                val byKey = remember(apps) { apps.associateBy { it.key } }
                val dockApps = remember(byKey, dockKeys) { dockKeys.mapNotNull { byKey[it] } }
                val dockSet = remember(dockKeys) { dockKeys.toSet() }
                val frequent = remember(apps, launchCounts) {
                    apps.filter { (launchCounts[it.key] ?: 0) > 0 }
                        .sortedByDescending { launchCounts[it.key] ?: 0 }
                        .take(4)
                }

                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        apps = apps,
                        dockApps = dockApps,
                        dockKeys = dockSet,
                        skin = skin,
                        capriLine = capriLine,
                        lineId = lineId,
                        mood = mood,
                        resumeTick = resumeTick,
                        commandId = commandId,
                        onSubmit = {
                            if (it.trim().equals("unhide", ignoreCase = true)) unhideAll()
                            else say(brain.respond(it, apps, ::launchSoon) { drawerOpen = true })
                        },
                        onPoke = { held ->
                            say(when {
                                CapriTime.wake() -> brain.wakeUp()      // C4: she was dozing
                                held -> brain.hold()
                                else -> brain.poke { brain.repeatLast(apps, ::launchSoon) { drawerOpen = true } }
                            })
                        },
                        onOpenDrawer = { drawerOpen = true },
                        onLaunch = { launchApp(it) },
                        onToggleDock = ::toggleDock,
                        onAppInfo = ::openAppInfo,
                        onGrabAction = ::grabAction,
                    )
                    AnimatedVisibility(
                        visible = drawerOpen,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        AppDrawer(
                            apps = apps,
                            frequent = frequent,
                            dockKeys = dockSet,
                            onLaunch = { launchApp(it) },
                            onToggleDock = ::toggleDock,
                            onAppInfo = ::openAppInfo,
                            onClose = { drawerOpen = false },
                        )
                    }
                    AnimatedVisibility(visible = showBoot, enter = EnterTransition.None, exit = fadeOut(tween(250))) {
                        BootOverlay(skin = skin, onDone = {
                            if (showBoot) {
                                showBoot = false
                                say(brain.greeting())
                            }
                        })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        CapriNotifListener.onNew = { pkg -> runOnUiThread { onNotification(pkg) } }
    }

    override fun onPause() {
        CapriNotifListener.onNew = null
        super.onPause()
    }

    // Pressing the Home button while Capri is already open: go back to the home screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerOpen = false
    }

    override fun onDestroy() {
        getSystemService(LauncherApps::class.java).unregisterCallback(packageCallback)
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }

    private fun reloadApps() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch { allApps = repo.loadApps() }
    }

    /** Opens an app from chat a moment later, so the red hit (C3) shows before the app covers it. */
    private fun launchSoon(app: AppEntry): Boolean {
        window.decorView.postDelayed({ launchApp(app) }, 180)
        return true
    }

    private fun launchApp(app: AppEntry): Boolean = try {
        repo.launch(app)
        prefs.recordLaunch(app.key)
        launchCounts = prefs.launchCounts()
        drawerOpen = false
        true
    } catch (e: Exception) {
        Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        false
    }

    private fun toggleDock(app: AppEntry) {
        dockKeys = when {
            app.key in dockKeys -> dockKeys - app.key
            dockKeys.size >= Prefs.MAX_DOCK -> {
                Toast.makeText(this, "Dock is full (${Prefs.MAX_DOCK} apps)", Toast.LENGTH_SHORT).show()
                dockKeys
            }
            else -> dockKeys + app.key
        }
        prefs.setDock(dockKeys)
    }

    /** 3.3: what she does with an app you dragged onto her. */
    private fun grabAction(app: AppEntry, action: GrabAction) {
        AppGrab.held = null
        when (action) {
            GrabAction.PIN -> {
                val was = app.key in dockKeys
                toggleDock(app)
                val slot = dockKeys.indexOf(app.key)
                say(when {
                    was -> brain.grabUnpinned()
                    slot < 0 -> brain.grabDockFull()
                    else -> brain.grabPinned().also { AppGrab.strike(slot) }   // lightning onto the new slot
                })
            }
            GrabAction.INFO -> {
                say(brain.grabInfo())
                openAppInfo(app)
            }
            GrabAction.UNINSTALL -> {
                say(brain.grabUninstall())
                try {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Couldn't uninstall ${app.label}", Toast.LENGTH_SHORT).show()
                }
            }
            GrabAction.HIDE -> {
                hidden = hidden + app.key
                prefs.setHidden(hidden)
                if (app.key in dockKeys) {
                    dockKeys = dockKeys - app.key
                    prefs.setDock(dockKeys)
                }
                say(brain.grabHidden())
            }
            GrabAction.CANCEL -> say(brain.grabCancel())
        }
    }

    /** Chat "unhide": every hidden app comes back. */
    private fun unhideAll() {
        val n = hidden.size
        hidden = emptySet()
        prefs.setHidden(hidden)
        say(brain.unhidden(n))
    }

    private fun openAppInfo(app: AppEntry) {
        try {
            repo.openAppInfo(app)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open app info", Toast.LENGTH_SHORT).show()
        }
    }
}

/** C5: she complains once when the battery drops to this percent (not charging). */
private const val LOW_BATTERY = 15

/** C5: at most one notification reaction this often. */
private const val NOTIF_GAP_MS = 10_000L
