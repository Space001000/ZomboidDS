package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.setup.SetupReport
import dev.zomboidds.companion.setup.ZombieBuddyStatus

/** What the checklist can ask the app to do. */
interface SetupActions {
    fun grantAccess()
    fun selectInstance(name: String)
    fun getZombieBuddy()
    fun openZomdroid()
    fun installMod()
    fun enableForNewGames()
}

private enum class Check { OK, TODO, BLOCKED }

/**
 * Every step the user needs, top to bottom. Each unmet step says what to do, with a button when
 * the app can do it itself. Later steps stay greyed out until the earlier ones are done.
 */
@Composable
fun SetupChecklist(report: SetupReport, gameStep: String, actions: SetupActions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Setup", style = MaterialTheme.typography.titleMedium)
            report.busy?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            report.error?.let { Text(it, color = Color(0xFFE57373)) }

            Step(
                if (report.zomdroidInstalled) Check.OK else Check.TODO,
                "Zomdroid",
                if (report.zomdroidInstalled) "Installed." else "Install Zomdroid and Project Zomboid first.",
            )

            val canAccess = report.zomdroidInstalled
            Step(
                when { report.hasAccess -> Check.OK; canAccess -> Check.TODO; else -> Check.BLOCKED },
                "Access to Zomdroid",
                if (report.hasAccess) "Granted." else "Lets this app install the mod. In the picker, tap 'Use this folder', then 'Allow'.",
                action = "Grant access".takeIf { canAccess && !report.hasAccess && report.busy == null },
                onAction = actions::grantAccess,
            )

            if (report.instances.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.instances.forEach { name ->
                        FilterChip(selected = name == report.instance, onClick = { actions.selectInstance(name) }, label = { Text(name) })
                    }
                }
            }

            val hasInstance = report.hasAccess && report.instance != null
            val zombieBuddyOk = report.zombieBuddy == ZombieBuddyStatus.OK
            val idle = report.busy == null
            val download = report.zombieBuddyDownload
            Step(
                when { zombieBuddyOk -> Check.OK; hasInstance -> Check.TODO; else -> Check.BLOCKED },
                "ZombieBuddy",
                when {
                    zombieBuddyOk -> "Installed."
                    !hasInstance -> "Grant access first."
                    download != null ->
                        "Saved '$download' to Downloads. In Zomdroid: Optimization → ZombieBuddy → select that file → Install. This list updates by itself."
                    report.zombieBuddy == ZombieBuddyStatus.JAR_ONLY ->
                        "Only half installed (the GitHub download lacks the mod folder), so the game can't enable mods that need it. Get the complete version:"
                    report.zombieBuddy == ZombieBuddyStatus.MOD_FOLDER_ONLY ->
                        "Not set up in Zomdroid yet. Get it here, then install it in Zomdroid:"
                    else -> "Required. This app can download it for you from ZombieBuddy's GitHub (about 13 MB), then you install it in Zomdroid:"
                },
                action = when {
                    zombieBuddyOk || !hasInstance || !idle -> null
                    download != null -> "Open Zomdroid"
                    else -> "Download"
                },
                onAction = if (download != null) actions::openZomdroid else actions::getZombieBuddy,
            )

            // The mod needs ZombieBuddy: without it the game won't enable (or load) ZomboidDS.
            val canInstall = hasInstance && zombieBuddyOk
            Step(
                when { report.modUpToDate -> Check.OK; canInstall -> Check.TODO; else -> Check.BLOCKED },
                "ZomboidDS mod",
                when {
                    report.modUpToDate -> "Installed (${report.installedModVersion})."
                    report.installedModVersion != null -> "Version ${report.installedModVersion} installed, this app has ${report.bundledModVersion}."
                    else -> "Not installed yet."
                },
                action = (if (report.installedModVersion == null) "Install" else "Update")
                    .takeIf { canInstall && !report.modUpToDate && idle },
                onAction = actions::installMod,
            )

            val modInstalled = canInstall && report.installedModVersion != null
            Step(
                when { report.enabledForNewGames == true -> Check.OK; modInstalled -> Check.TODO; else -> Check.BLOCKED },
                "Enabled for new games",
                if (report.enabledForNewGames == true) "New games start with ZomboidDS."
                else "Adds ZomboidDS (and ZombieBuddy) to the game's mod list, like ticking them in the Mods menu.",
                action = "Enable".takeIf { modInstalled && report.enabledForNewGames != true && idle },
                onAction = actions::enableForNewGames,
            )

            Step(
                if (report.ready) Check.TODO else Check.BLOCKED,
                "Play",
                gameStep,
            )
        }
    }
}

@Composable
private fun Step(check: Check, title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    val dim = if (check == Check.BLOCKED) 0.45f else 1f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (check) { Check.OK -> "✓"; Check.TODO -> "•"; Check.BLOCKED -> "–" },
            Modifier.width(28.dp),
            color = if (check == Check.OK) Color(0xFF7CB342) else MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
            fontWeight = FontWeight.Bold,
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim))
        }
        if (action != null) {
            Button(onClick = onAction, Modifier.padding(start = 12.dp)) { Text(action) }
        }
    }
}

@Preview(widthDp = 540)
@Composable
private fun HalfwayPreview() {
    SetupChecklist(
        SetupReport(
            zomdroidInstalled = true, hasAccess = true, instances = listOf("Project Zomboid"), instance = "Project Zomboid",
            zombieBuddy = ZombieBuddyStatus.OK, installedModVersion = null, bundledModVersion = "0.1.0", enabledForNewGames = false,
        ),
        gameStep = "Start the game.",
        actions = object : SetupActions {
            override fun grantAccess() {}
            override fun selectInstance(name: String) {}
            override fun getZombieBuddy() {}
            override fun openZomdroid() {}
            override fun installMod() {}
            override fun enableForNewGames() {}
        },
    )
}
