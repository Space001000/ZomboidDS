package dev.zomboidds.companion

import java.io.File
import dev.zomboidds.companion.setup.AppRelease
import android.net.Uri
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.zomboidds.companion.setup.SetupController
import dev.zomboidds.companion.setup.ZomdroidStorage
import dev.zomboidds.companion.ui.CompanionScreen
import dev.zomboidds.companion.ui.InventoryDisplay
import dev.zomboidds.companion.ui.MapDisplay
import dev.zomboidds.companion.data.WorldMapState
import dev.zomboidds.companion.ui.SetupActions

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never take input focus: the gamepad must keep driving the game on the other screen
        // (Zomdroid stops listening to it when its activity pauses). Touches still arrive.
        // On the Thor the firmware already routes the gamepad to the top screen; this is a safety net.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // Debug builds only: this activity can be started by any app, and these reset or remove mods.
        if (BuildConfig.DEBUG) handleDevIntent(intent)
        keepSetupFresh()

        val gateway = container.gateway
        setContent {
            val state by gateway.state.collectAsStateWithLifecycle()
            val connection by gateway.connection.collectAsStateWithLifecycle()
            val report by setup.report.collectAsStateWithLifecycle()
            val update by container.updater.state.collectAsStateWithLifecycle()
            val inventoryLayout by container.settings.inventoryLayout.collectAsStateWithLifecycle()
            val containerLayout by container.settings.containerLayout.collectAsStateWithLifecycle()
            val deckCommands by container.settings.deckCommands.collectAsStateWithLifecycle()
            val worldMap by container.worldMap.state.collectAsStateWithLifecycle()
            val mapPlacement by container.settings.mapPlacement.collectAsStateWithLifecycle()
            val whatsNewSeen by container.settings.whatsNewSeen.collectAsStateWithLifecycle()
            // The map files are read once, the first time a game shows the map.
            val mapShown = state.mapPosition?.shown == true
            LaunchedEffect(mapShown) { if (mapShown) container.worldMap.load() }
            CompanionScreen(
                state, connection, report, update, setupActions,
                iconUrl = gateway::iconUrl,
                actions = gateway,
                controls = gateway,
                inventoryDisplay = InventoryDisplay(inventoryLayout, containerLayout),
                onInventoryDisplayChange = {
                    container.settings.setInventoryLayout(it.items)
                    container.settings.setContainerLayout(it.containers)
                },
                events = gateway.events,
                crafting = gateway,
                deckCommands = deckCommands,
                onDeckCommandsChange = container.settings::setDeckCommands,
                // On a new install and after every update, until closed.
                whatsNewDue = whatsNewSeen != BuildConfig.VERSION_NAME,
                onWhatsNewSeen = { container.settings.setWhatsNewSeen(BuildConfig.VERSION_NAME) },
                map = (worldMap as? WorldMapState.Loaded)?.let {
                    MapDisplay(it.map, mapPlacement, container.settings::setMapPlacement)
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
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

    /** Opens Zomdroid, on the main (top) screen when this app runs on a secondary one. */
    private fun openZomdroidOnMainScreen() {
        val launch = packageManager.getLaunchIntentForPackage(ZomdroidStorage.PACKAGE) ?: return
        val options = ActivityOptions.makeBasic()
        if (display?.displayId != Display.DEFAULT_DISPLAY) options.launchDisplayId = Display.DEFAULT_DISPLAY
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
