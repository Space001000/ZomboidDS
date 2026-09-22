package dev.zomboidds.companion

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never take input focus: the gamepad must keep driving the game on the other screen
        // (Zomdroid stops listening to it when its activity pauses). Touches still arrive.
        // On the Thor the firmware already routes the gamepad to the top screen; this is a safety net.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        handleDevIntent(intent)
        keepSetupFresh()

        val gateway = container.gateway
        setContent {
            val state by gateway.state.collectAsStateWithLifecycle()
            val connection by gateway.connection.collectAsStateWithLifecycle()
            val report by setup.report.collectAsStateWithLifecycle()
            CompanionScreen(state, connection, report, setupActions, iconUrl = gateway::iconUrl, perform = gateway::perform)
        }
    }

    override fun onResume() {
        super.onResume()
        setup.refresh()
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
