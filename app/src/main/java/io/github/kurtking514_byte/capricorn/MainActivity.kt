package io.github.kurtking514_byte.capricorn

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.lifecycle.lifecycleScope
import io.github.kurtking514_byte.capricorn.data.AppEntry
import io.github.kurtking514_byte.capricorn.data.AppRepository
import io.github.kurtking514_byte.capricorn.data.Prefs
import io.github.kurtking514_byte.capricorn.ui.AppDrawer
import io.github.kurtking514_byte.capricorn.ui.BootOverlay
import io.github.kurtking514_byte.capricorn.ui.HomeScreen
import io.github.kurtking514_byte.capricorn.ui.theme.CapriTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private lateinit var prefs: Prefs

    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var dockKeys by mutableStateOf<List<String>>(emptyList())
    private var launchCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    private var drawerOpen by mutableStateOf(false)
    private var showBoot by mutableStateOf(true)
    private var resumeTick by mutableIntStateOf(0)
    private var loadJob: Job? = null

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
        dockKeys = prefs.dock()
        launchCounts = prefs.launchCounts()
        getSystemService(LauncherApps::class.java).registerCallback(packageCallback)
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
                        dockApps = dockApps,
                        resumeTick = resumeTick,
                        onOpenDrawer = { drawerOpen = true },
                        onLaunch = ::launchApp,
                        onToggleDock = ::toggleDock,
                        onAppInfo = ::openAppInfo,
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
                            onLaunch = ::launchApp,
                            onToggleDock = ::toggleDock,
                            onAppInfo = ::openAppInfo,
                            onClose = { drawerOpen = false },
                        )
                    }
                    AnimatedVisibility(visible = showBoot, enter = fadeIn(), exit = fadeOut(tween(450))) {
                        BootOverlay(onDone = { showBoot = false })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    // Pressing the Home button while Capri is already open: go back to the home screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerOpen = false
    }

    override fun onDestroy() {
        getSystemService(LauncherApps::class.java).unregisterCallback(packageCallback)
        super.onDestroy()
    }

    private fun reloadApps() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch { apps = repo.loadApps() }
    }

    private fun launchApp(app: AppEntry) {
        try {
            repo.launch(app)
            prefs.recordLaunch(app.key)
            launchCounts = prefs.launchCounts()
            drawerOpen = false
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
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

    private fun openAppInfo(app: AppEntry) {
        try {
            repo.openAppInfo(app)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open app info", Toast.LENGTH_SHORT).show()
        }
    }
}
