package dev.zomboidds.companion

import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.zomboidds.companion.data.WorldMapState
import dev.zomboidds.companion.devtools.DevTools
import dev.zomboidds.companion.domain.ConnectionStatus
import dev.zomboidds.companion.setup.AppRelease
import dev.zomboidds.companion.setup.SetupController
import dev.zomboidds.companion.setup.ZomdroidStorage
import dev.zomboidds.companion.ui.CompanionScreen
import dev.zomboidds.companion.ui.EmoteSettings
import dev.zomboidds.companion.ui.InventoryDisplay
import dev.zomboidds.companion.ui.MapDisplay
import dev.zomboidds.companion.ui.ScreenPlace
import dev.zomboidds.companion.ui.SetupActions
import dev.zomboidds.companion.ui.TabSettings
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as CompanionApplication).container
    private val setup: SetupController get() = container.setup

    private val pickZomdroidFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            if (tree != null) setup.onFolderPicked(tree)
        }

    private val setupActions = object : SetupActions {
        // Opens directly on Zomdroid's top folder, so the user only confirms.
        override fun grantAccess() = pickZomdroidFolder.launch(ZomdroidStorage.pickerStartUri)
        override fun selectInstance(name: String) = setup.selectInstance(name)
        override fun getZombieBuddy() = setup.getZombieBuddy()
        override fun openZomdroid() = openZomdroidOnMainScreen()
        override fun installMod() = setup.installMod()
        override fun enableForNewGames() = setup.enableForNewGames()
        override fun checkForUpdate() = container.updater.checkIfDue(force = true)
        override fun downloadUpdate(release: AppRelease) = container.updater.download(release)
        override fun installUpdate(file: File) = container.updater.install(file)
        override fun openUrl(url: String) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        override fun moveToOtherScreen() = this@MainActivity.moveToOtherScreen()
    }

    /** Which screen this app is on; the checklist offers to move it off the main screen. */
    private val screenPlace = mutableStateOf(ScreenPlace.SINGLE)

    private val displays = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = updateScreenPlace()
        override fun onDisplayRemoved(displayId: Int) = updateScreenPlace()
        override fun onDisplayChanged(displayId: Int) = updateScreenPlace()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // While the game runs, never take input focus: the gamepad must keep driving the game on the
        // other screen (Zomdroid stops listening to it when its activity pauses), and touching a
        // window that can't take focus doesn't move it. Touches still arrive. See followGameFocus.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // Debug builds only: this activity can be started by any app, and these reset or remove mods.
        if (BuildConfig.DEBUG) handleDevIntent(intent)
        keepSetupFresh()
        // The task switcher can move this app to the other screen without restarting, resuming or
        // reconfiguring it; only its window is laid out again there. Check the screen then.
        var laidOutOn: Int? = null
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val now = window.decorView.display?.displayId
            if (now != laidOutOn) { laidOutOn = now; updateScreenPlace() }
        }

        keepBackGestureOff()

        val gateway = container.gateway
        setContent {
            val state by gateway.state.collectAsStateWithLifecycle()
            val connection by gateway.connection.collectAsStateWithLifecycle()
            LaunchedEffect(connection) { followGameFocus(connection == ConnectionStatus.Connected) }
            val report by setup.report.collectAsStateWithLifecycle()
            val update by container.updater.state.collectAsStateWithLifecycle()
            val inventoryLayout by container.settings.inventoryLayout.collectAsStateWithLifecycle()
            val containerLayout by container.settings.containerLayout.collectAsStateWithLifecycle()
            val itemSize by container.settings.itemSize.collectAsStateWithLifecycle()
            val deckCommands by container.settings.deckCommands.collectAsStateWithLifecycle()
            val pinnedCommands by container.settings.pinnedCommands.collectAsStateWithLifecycle()
            val favouriteEmotes by container.settings.favouriteEmotes.collectAsStateWithLifecycle()
            val worldMap by container.worldMap.state.collectAsStateWithLifecycle()
            val mapPlacement by container.settings.mapPlacement.collectAsStateWithLifecycle()
            val whatsNewSeen by container.settings.whatsNewSeen.collectAsStateWithLifecycle()
            val mapSymbols by container.settings.mapSymbols.collectAsStateWithLifecycle()
            val craftMode by container.settings.craftMode.collectAsStateWithLifecycle()
            val statusMode by container.settings.statusMode.collectAsStateWithLifecycle()
            val tabOrder by container.settings.tabOrder.collectAsStateWithLifecycle()
            val lastTab = remember { container.settings.lastTab }
            // The map files are read once, the first time a game shows the map.
            val mapShown = state.mapPosition?.shown == true
            LaunchedEffect(mapShown) { if (mapShown) container.worldMap.load() }
            CompanionScreen(
                state, connection, report, update, setupActions,
                iconUrl = gateway::iconUrl,
                actions = gateway,
                controls = gateway,
                inventoryDisplay = InventoryDisplay(inventoryLayout, containerLayout, itemSize),
                onInventoryDisplayChange = {
                    container.settings.setInventoryLayout(it.items)
                    container.settings.setContainerLayout(it.containers)
                    container.settings.setItemSize(it.size)
                },
                events = gateway.events,
                crafting = gateway,
                building = gateway,
                tailoring = gateway,
                devices = gateway,
                ovens = gateway,
                windows = gateway,
                reader = gateway,
                devTools = remember { DevTools(gateway) },
                craftMode = craftMode,
                onCraftModeChange = container.settings::setCraftMode,
                statusMode = statusMode,
                onStatusModeChange = container.settings::setStatusMode,
                deckCommands = deckCommands,
                onDeckCommandsChange = container.settings::setDeckCommands,
                pinnedCommands = pinnedCommands,
                onPinnedCommandsChange = container.settings::setPinnedCommands,
                emoteSettings = EmoteSettings(favouriteEmotes, container.settings::setFavouriteEmotes,
                    container.settings.lastEmoteGroup, container.settings::setLastEmoteGroup),
                // On a new install and after every update, until closed.
                whatsNewDue = whatsNewSeen != BuildConfig.VERSION_NAME,
                onWhatsNewSeen = { container.settings.setWhatsNewSeen(BuildConfig.VERSION_NAME) },
                map = (worldMap as? WorldMapState.Loaded)?.let {
                    MapDisplay(it.map, mapPlacement, container.settings::setMapPlacement, mapSymbols, container.settings::setMapSymbols)
                },
                tabSettings = TabSettings(tabOrder, container.settings::setTabOrder, lastTab, container.settings::setLastTab),
                screenPlace = screenPlace.value,
            )
        }
    }

    /**
     * Swiping in from a side edge is Android's Back gesture. This window can't take focus while the
     * game runs, so Back went to Zomdroid and sent the game to the background, which restarts it when
     * it comes back (issue 15). An app with this many touch targets near the edges hits that by
     * accident, so the side edges are excluded from the gesture. Android honours at most 200dp per
     * edge; the rest of each edge keeps Back.
     */
    private fun keepBackGestureOff() {
        val view = window.decorView
        view.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val edge = (EDGE_DP * resources.displayMetrics.density).toInt()
            val width = right - left
            val height = bottom - top
            view.systemGestureExclusionRects = listOf(Rect(0, 0, edge, height), Rect(width - edge, 0, width, height))
        }
    }

    /**
     * Without the game the app is the focused app on its screen, and Android sends it the gamepad's
     * buttons. A window that can't take focus never receives them, so after 5 seconds Android
     * reported the app as not responding. Without a game, the window takes focus like any app.
     */
    private fun followGameFocus(gameRunning: Boolean) {
        if (gameRunning) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    override fun onStart() {
        super.onStart()
        getSystemService(DisplayManager::class.java).registerDisplayListener(displays, null)
        updateScreenPlace()
    }

    override fun onStop() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displays)
        super.onStop()
    }

    /** A second screen that is switched on (the Thor's bottom screen, a monitor), if any. */
    private fun otherScreen(): Display? =
        getSystemService(DisplayManager::class.java).getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON }

    private fun updateScreenPlace() {
        // The window's own screen: after a move the activity's display can still name the old one.
        val displayId = window.decorView.display?.displayId ?: display?.displayId
        screenPlace.value = when {
            displayId != Display.DEFAULT_DISPLAY -> ScreenPlace.SECOND
            otherScreen() != null -> ScreenPlace.MAIN
            else -> ScreenPlace.SINGLE
        }
    }

    /**
     * Moves this app's task to the second screen: launching it there moves the same task over.
     * Closing this copy and opening a new one there instead froze both screens when the Thor's task
     * switcher had pulled the app up (its launcher still holds the task until Home or Back).
     */
    private fun moveToOtherScreen() {
        val target = otherScreen() ?: return
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
    }

    override fun onResume() {
        super.onResume()
        updateScreenPlace()
        setup.refresh()
        container.updater.checkIfDue() // at most once a day
    }

    /**
     * While setup is incomplete, re-check every few seconds: on a dual-screen device the user
     * installs ZombieBuddy in Zomdroid on the other screen while this app stays visible, so
     * onResume never fires.
     */
    private fun keepSetupFresh() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(5_000)
                    if (!setup.report.value.ready) setup.refresh()
                }
            }
        }
    }

    /** Opens Zomdroid on the main (top) screen. */
    private fun openZomdroidOnMainScreen() {
        val launch = packageManager.getLaunchIntentForPackage(ZomdroidStorage.PACKAGE) ?: return
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
    }

    /** adb-triggered development helpers, see [DevExport] and tools/deploy-mod.sh. */
    private fun handleDevIntent(intent: Intent) {
        getExternalFilesDir(null) // creates the folder adb exchanges dev files through
        intent.getStringExtra("devList")?.let { DevExport.list(this, it) }
        intent.getStringExtra("devCat")?.let { DevExport.cat(this, it) }
        intent.getStringExtra("devExport")?.let { DevExport.export(this, it) }
        intent.getStringExtra("devInstall")?.let { DevExport.install(this, it) }
        if (intent.getBooleanExtra("devReset", false)) DevExport.reset(this)
        if (intent.getBooleanExtra("devRemoveZombieBuddy", false)) DevExport.removeZombieBuddy(this)
    }
}

/** How far in from each side edge a swipe still counts as a touch, not Back (Android's gesture area is about 24dp wide). */
private const val EDGE_DP = 40
