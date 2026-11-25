package io.github.kurtking514_byte.capricorn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.BatteryManager
import android.os.Build
import android.app.role.RoleManager
import android.provider.Settings
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
import androidx.compose.animation.ExitTransition
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
import io.github.kurtking514_byte.capricorn.overlay.CapriOverlayService
import io.github.kurtking514_byte.capricorn.ui.DRAWER_CLOSE_MS
import io.github.kurtking514_byte.capricorn.ui.AppDrawer
import io.github.kurtking514_byte.capricorn.ui.ClassGrid
import io.github.kurtking514_byte.capricorn.ui.GestureScreen
import io.github.kurtking514_byte.capricorn.ui.Setup
import io.github.kurtking514_byte.capricorn.ui.SetupItem
import io.github.kurtking514_byte.capricorn.ui.SetupScreen
import io.github.kurtking514_byte.capricorn.data.GestureJob
import io.github.kurtking514_byte.capricorn.data.Gesture
import io.github.kurtking514_byte.capricorn.data.Gestures
import io.github.kurtking514_byte.capricorn.capri.Weather
import io.github.kurtking514_byte.capricorn.capri.FaceDown
import io.github.kurtking514_byte.capricorn.capri.ContactNumber
import io.github.kurtking514_byte.capricorn.capri.SettingsSearch
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import io.github.kurtking514_byte.capricorn.ui.StatusScan
import io.github.kurtking514_byte.capricorn.capri.PhoneStatus
import io.github.kurtking514_byte.capricorn.capri.BatteryTracker
import io.github.kurtking514_byte.capricorn.ui.AppGrab
import io.github.kurtking514_byte.capricorn.ui.CapriAway
import io.github.kurtking514_byte.capricorn.ui.GrabAction
import io.github.kurtking514_byte.capricorn.ui.BootOverlay
import io.github.kurtking514_byte.capricorn.ui.CapriSkin
import io.github.kurtking514_byte.capricorn.ui.CapriTime
import io.github.kurtking514_byte.capricorn.ui.HomeScreen
import io.github.kurtking514_byte.capricorn.ui.theme.CapriTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.kurtking514_byte.capricorn.capri.Suggest
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
    private var gesturesOpen by mutableStateOf(false)              // 3.12 gesture settings
    private var setupOpen by mutableStateOf(false)                 // 4.4 setup checklist
    private var nowApps by mutableStateOf<List<AppEntry>>(emptyList())   // 5.1 suggestions row
    private var bubbleApp by mutableStateOf<AppEntry?>(null)             // 5.1 her bubble opens this app
    private var lastNamed = 0L
    private var classesOpen by mutableStateOf(false)               // 3.9d week grid
    private var showBoot by mutableStateOf(true)
    private var resumeTick by mutableIntStateOf(0)
    private var commandId by mutableIntStateOf(0)
    private var loadJob: Job? = null

    private lateinit var brain: CapriBrain
    private var capriLine by mutableStateOf("")
    private var lineId by mutableIntStateOf(0)

    private var mood by mutableStateOf("neutral")
    private var letterPick by mutableStateOf<Pair<Char, List<AppEntry>>?>(null)   // 3.5
    private var contactPick by mutableStateOf<List<ContactNumber>?>(null)          // 3.13
    private var skin: CapriSkin? = null

    private fun say(line: String) {
        if (brain.mood == "reboot") {       // "boot" chat command: play the boot again
            showBoot = true
            brain.consumeActed()
            return
        }
        when (brain.consumeRequest()) {
            "askContacts" -> askContacts.launch(Manifest.permission.READ_CONTACTS)         // 3.13
            "pickContact" -> {
                val shown = brain.contactChoices
                contactPick = shown
                window.decorView.postDelayed({ if (contactPick === shown) contactPick = null }, 8_000)
            }
            "gestures" -> gesturesOpen = true                                              // 3.12
            "setup" -> setupOpen = true                                                    // 4.4
            "classes" -> classesOpen = true                                                // 3.9d week grid
            "askLocation" -> askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) // 3.9e
            "fetchWeather" -> fetchWeather()
        }
        bubbleApp = null                          // 5.1: any new line ends a tap-to-open suggestion
        capriLine = line
        mood = brain.mood
        lineId++
        if (brain.consumeActed()) commandId++     // C3 red hit
    }

    // 3.9e: rough location for the weather; on yes she fetches and says it.
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) fetchWeather() else say(brain.weatherDenied())
    }

    // 3.13: Contacts access for "call <name>"; on yes she looks the name up again.
    private val askContacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val name = brain.pendingCall
        say(if (ok && name != null) brain.call(name) else brain.contactsDenied())
    }

    // 4.4: permissions asked from the setup screen. If Android answers "no" at once (it stopped
    // showing the pop-up after earlier no's), open the page where it can still be switched on.
    private var setupAskedAt = 0L
    private fun answeredAtOnce() = android.os.SystemClock.uptimeMillis() - setupAskedAt < 400

    private val askSetupPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) return@registerForActivityResult
        if (answeredAtOnce()) openSetting(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        else Setup.deny()
    }
    private val askHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) return@registerForActivityResult
        if (answeredAtOnce()) openSetting(Settings.ACTION_HOME_SETTINGS) else Setup.deny()
    }

    private fun setupAllow(item: SetupItem) {
        setupAskedAt = android.os.SystemClock.uptimeMillis()
        when (item) {
            SetupItem.HOME -> {
                val rm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getSystemService(RoleManager::class.java) else null
                if (rm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    askHomeRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                } else openSetting(Settings.ACTION_HOME_SETTINGS)
            }
            SetupItem.OVERLAY -> openSetting(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            SetupItem.NOTIFS -> openSetting(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            SetupItem.USAGE -> openSetting(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SetupItem.DND -> openSetting(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            SetupItem.LOCATION -> askSetupPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            SetupItem.CONTACTS -> askSetupPerm.launch(Manifest.permission.READ_CONTACTS)
            SetupItem.BATTERY_SAVER -> try {                                   // 4.5: Android's own yes/no pop-up
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", packageName, null)))
            } catch (e: Exception) {
                openSetting(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
            SetupItem.AUTO_LAUNCH -> openSetting(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            SetupItem.RECENTS_LOCK -> Unit                                     // only her instructions
        }
    }

    private fun openSetting(action: String, data: Uri? = null) {
        try {
            startActivity(Intent(action).apply { if (data != null) setData(data) })
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
        }
    }

    private fun closeSetup(later: Boolean) {
        setupOpen = false
        val total = SetupItem.entries.size
        say(brain.setupClosed(SetupItem.entries.count { it.isOn(this) }, total, later))
    }

    /**
     * 5.1: re-reads which apps you usually open around now (cached per hour, off the main thread).
     * With [name], she may also say the top one: only on the home screen, only a clear favorite,
     * at most once every 30 min.
     */
    private fun refreshNow(name: Boolean) {
        lifecycleScope.launch {
            val picks = withContext(Dispatchers.IO) { Suggest.rank(this@MainActivity) }
            val docked = dockKeys.map { it.substringBefore('/') }.toSet()
            val byPkg = apps.groupBy { it.packageName }
            val list = picks.filter { it.pkg !in docked && it.pkg in byPkg }
            nowApps = list.mapNotNull { byPkg[it.pkg]?.firstOrNull() }.take(4)
            val now = System.currentTimeMillis()
            if (!name || CapriAway.away || (lastNamed != 0L && now - lastNamed < NAME_GAP_MS) || !Suggest.clear(list)) return@launch
            val app = byPkg[list[0].pkg]?.firstOrNull() ?: return@launch
            if (react { brain.suggestApp(app.label) }) {
                bubbleApp = app
                lastNamed = now
            }
        }
    }

    private fun fetchWeather() = Weather.refresh(this, force = true) { w ->
        say(if (w != null) brain.weatherLine(w) else brain.weatherFailed())
        if (w != null) StatusScan.update(PhoneStatus.read(this))
    }

    /** 3.12: a remappable gesture does whatever job it's set to (nothing if it's switched off). */
    private fun runGesture(g: Gesture) {
        if (!Gestures.on(g)) return
        when (Gestures.job(g)) {
            GestureJob.LOCK -> lockFromHome()
            GestureJob.FLASHLIGHT -> say(brain.torch())
            GestureJob.SCAN -> {
                val st = PhoneStatus.read(this)
                StatusScan.show(st)
                // 3.9e: stale weather -> fetch quietly; the panel updates if it's still up
                Weather.refresh(this) { if (it != null) StatusScan.update(PhoneStatus.read(this)) }
                say(brain.scan(st))
            }
            GestureJob.APPS -> drawerOpen = true
            GestureJob.WEEK -> classesOpen = true
            null -> {}
        }
    }

    private fun onHome() = !showBoot && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

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
        Gestures.init(this)                        // 3.12: which gestures are on
        io.github.kurtking514_byte.capricorn.ui.Panels.init(this)   // 4.6b: panel strength
        BatteryTracker.start(this)                 // 3.9b: learns the real drain for the battery estimate
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
        // 3.10: face down = quiet (she glitches out here), picked up = sound back + she glitches in
        FaceDown.onFaceDown = { if (onHome()) CapriAway.leave() }
        FaceDown.onPickedUp = { full -> if (onHome()) { CapriAway.appear(); say(brain.backOnline(full)) } else CapriAway.reset() }
        launchCounts = prefs.launchCounts()
        getSystemService(LauncherApps::class.java).registerCallback(packageCallback)
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        reloadApps()

        setContent {
            CapriTheme {
                // A launcher never "goes back" past home; back just closes the drawer.
                BackHandler { if (setupOpen) closeSetup(true) else if (gesturesOpen) gesturesOpen = false else if (classesOpen) classesOpen = false else drawerOpen = false }

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
                        letterPick = letterPick,
                        onLetter = ::onLetter,
                        onDoubleTapEmpty = { runGesture(Gesture.DOUBLE_TAP) },
                        onTwoFingerDown = { runGesture(Gesture.TWO_DOWN) },
                        onTwoFingerHold = { runGesture(Gesture.TWO_HOLD) },
                        contactPick = contactPick,
                        onPickContact = {
                            contactPick = null
                            say(brain.contactPicked(it))
                        },
                        onOpenSetting = { pg ->                      // 3.14: Capri's own pages go through her chat
                            if (pg.chat != null) say(brain.respond(pg.chat, apps, ::launchSoon) { drawerOpen = true })
                            else if (SettingsSearch.open(this@MainActivity, pg)) say(brain.settingOpened(pg.title))
                        },
                        onPickLetterApp = {
                            letterPick = null
                            say(brain.letterPicked(it.label))
                            launchSoon(it)
                        },
                        nowApps = nowApps.filter { it.key !in dockSet },
                        bubbleTap = bubbleApp != null,
                        onBubbleTap = {
                            bubbleApp?.let { app ->
                                bubbleApp = null
                                commandId++                          // C3 red hit, then it opens
                                launchSoon(app)
                            }
                        },
                    )
                    AnimatedVisibility(
                        visible = drawerOpen,
                        enter = EnterTransition.None,                              // 3.8: snaps in, rows get struck
                        exit = fadeOut(tween(durationMillis = 1, delayMillis = DRAWER_CLOSE_MS)), // bars, then gone
                    ) {
                        AppDrawer(
                            apps = apps,
                            frequent = frequent,
                            dockKeys = dockSet,
                            onLaunch = { launchApp(it) },
                            onToggleDock = ::toggleDock,
                            onAppInfo = ::openAppInfo,
                            onClose = { drawerOpen = false },
                            closing = !drawerOpen,
                            skin = skin,
                        )
                    }
                    if (classesOpen) ClassGrid(onClose = { classesOpen = false })
                    if (gesturesOpen) GestureScreen(onClose = { gesturesOpen = false })
                    if (setupOpen) SetupScreen(onAllow = ::setupAllow, onClose = ::closeSetup)
                    AnimatedVisibility(visible = showBoot, enter = EnterTransition.None, exit = ExitTransition.None) {   // 4.6d: snaps off
                        BootOverlay(skin = skin, onDone = {
                            if (showBoot) {
                                showBoot = false
                                say(brain.greeting())
                                if (!prefs.setupSeen()) {               // 4.4: first launch only
                                    prefs.markSetupSeen()
                                    setupOpen = true
                                }
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
        FaceDown.watch(this, "home", true)
        Weather.refresh(this)                     // 3.9e: keep the weather fresh (skips if not allowed / still fresh)
        CapriNotifListener.onNew = { pkg -> runOnUiThread { onNotification(pkg) } }
        refreshNow(name = true)                  // 5.1: NOW row, and maybe she names the top pick
    }

    override fun onPause() {
        FaceDown.watch(this, "home", false)
        CapriNotifListener.onNew = null
        super.onPause()
    }

    // Pressing the Home button while Capri is already open: go back to the home screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerOpen = false
        classesOpen = false
        gesturesOpen = false
    }

    override fun onDestroy() {
        getSystemService(LauncherApps::class.java).unregisterCallback(packageCallback)
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }

    private fun reloadApps() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch { allApps = repo.loadApps(); refreshNow(false) }
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
        classesOpen = false
        gesturesOpen = false
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

    /**
     * 3.5: you drew a letter (null = not a letter). She answers 120 ms after you let go, while the
     * trail flashes: one app → red hit, opens 180 ms later; several → "Pick one" (most used first).
     */
    private fun onLetter(letter: Char?) {
        letterPick = null
        window.decorView.postDelayed({
            if (letter == null) { say(brain.letterFail()); return@postDelayed }
            val found = apps.filter { it.label.startsWith(letter, ignoreCase = true) }
                .sortedByDescending { launchCounts[it.key] ?: 0 }
            when (found.size) {
                0 -> say(brain.letterNone(letter))
                1 -> { say(brain.letterOpen(letter, found[0].label)); launchSoon(found[0]) }
                else -> {
                    say(brain.letterPick(letter, found.size))
                    letterPick = letter to found.take(4)
                    val shown = letterPick
                    window.decorView.postDelayed({ if (letterPick === shown) letterPick = null }, 8_000)
                }
            }
        }, 120)
    }

    /**
     * 3.6: double-tap the empty home screen = lock. Red command hit, she glitches out (3.4's
     * glitch-out, 250 ms), then the screen locks at 320 ms. Locking needs her accessibility
     * service; without it she tells you to say "overlay".
     */
    private fun lockFromHome() {
        val overlay = CapriOverlayService.instance
        if (overlay == null) { say(brain.lockNeedsOverlay()); return }
        say(brain.lockHome())
        if (!CapriAway.away) CapriAway.toggle()
        window.decorView.postDelayed({ overlay.lockScreen() }, LOCK_AFTER_MS)
        window.decorView.postDelayed({ CapriAway.reset() }, LOCK_AFTER_MS + 600)
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

/** 3.6: the screen locks this long after the double-tap, so the red hit and her glitch-out show. */
private const val LOCK_AFTER_MS = 320L

/** C5: she complains once when the battery drops to this percent (not charging). */
private const val LOW_BATTERY = 15

/** C5: at most one notification reaction this often. */
private const val NAME_GAP_MS = 30 * 60_000L   // 5.1: she names a suggested app at most every 30 min
private const val NOTIF_GAP_MS = 10_000L
