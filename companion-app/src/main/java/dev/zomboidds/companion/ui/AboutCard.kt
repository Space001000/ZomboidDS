package dev.zomboidds.companion.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.BuildConfig
import dev.zomboidds.companion.R
import dev.zomboidds.companion.setup.AppUpdater

private const val SOURCE_URL = "https://github.com/${AppUpdater.REPOSITORY}"
private const val KO_FI_URL = "https://ko-fi.com/space000"
private const val DISCORD_URL = "https://discord.gg/NEJeAHgWss"

/**
 * Who made this and under which terms: the logo, name, maker and version, a splash line, the
 * Discord (first: it's where questions and ideas go), What's new, Ko-fi, the source and the
 * open-source licences, then the small print. The icons are drawn like the logo (AboutIcons).
 * Donating never unlocks anything (The Indie Stone's modding policy).
 */
@Composable
fun AboutCard(openUrl: (String) -> Unit, onLicences: () -> Unit, onWhatsNew: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                    modifier = Modifier.size(52.dp).background(LogoBackground, RoundedCornerShape(12.dp)))
                Column {
                    Text("ZomboidDS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("by Space000 · ${BuildConfig.VERSION_NAME} · free and open source (GPL-3.0)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // A different line each time, like Minecraft's splash text.
            val splash = remember { SPLASHES.random() }
            Text(splash, Modifier.padding(start = 6.dp).rotate(-2f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary, fontStyle = FontStyle.Italic)
            AboutLink(AboutIcon.WALKIE, "Join the Discord", "Questions, bugs, ideas, other survivors", highlighted = true,
                modifier = Modifier.fillMaxWidth()) { openUrl(DISCORD_URL) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutLink(AboutIcon.NEWSPAPER, "What's new", "Every update", modifier = Modifier.weight(1f), onClick = onWhatsNew)
                AboutLink(AboutIcon.MUG, "Support on Ko-fi", "Buy me a coffee", modifier = Modifier.weight(1f)) { openUrl(KO_FI_URL) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutLink(AboutIcon.CODE, "Source on GitHub", "The code", modifier = Modifier.weight(1f)) { openUrl(SOURCE_URL) }
                AboutLink(AboutIcon.LICENCE, "Licences", "What's inside", modifier = Modifier.weight(1f), onClick = onLicences)
            }
            Text(
                "An unofficial mod, not affiliated with The Indie Stone. You need your own copy of Project Zomboid.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The splash line under the name, one picked each time the card shows. Add your own. */
private val SPLASHES = listOf(
    "Most of it started as someone's idea, so keep them coming.",
    "This is how you died. Now on two screens.",
    "Now with 100% more screens.",
    "Have you tried turning the TV on?",
    "Now tuning in on 93.2.",
    "Your inventory, finally out of the way.",
    "Check behind you.",
    "Bandage first, loot later.",
    "Close the curtains.",
    "Keep the noise down.",
    "Remember to sleep.",
    "Read the skill book first.",
    "Day one of many.",
    "Don't trust the fridge after day seven.",
    "Bugs go in the Discord, not in your arm.",
    "Built on a lot of coffee.",
    "Tap, hold, drag. No more digging through menus.",
    "The other screen finally pulls its weight.",
    "Sneak more, sprint less.",
    "One more day in Knox County.",
)

/** The logo's dark background (ic_launcher_background). */
private val LogoBackground = Color(0xFF121212)

@Composable
private fun AboutLink(
    icon: AboutIcon,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AboutIconTile(icon)
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The licences shipped in assets/legal (see companion-app/build.gradle.kts): notices first, the GPL on request. */
@Composable
fun BoxScope.LicencesPanel(onClose: () -> Unit) = BottomPanel(onDismiss = onClose) {
    val assets = LocalContext.current.assets
    fun read(name: String) = runCatching { assets.open("legal/$name").bufferedReader().use { it.readText() } }
        .getOrDefault("($name is missing from this build)")
    var file by remember { mutableStateOf("THIRD_PARTY_NOTICES.md") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Open-source licences", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClose) { Text("Close") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((name, label) in listOf("THIRD_PARTY_NOTICES.md" to "Included software", "LICENSE" to "ZomboidDS (GPL-3.0)",
            "Apache-2.0.txt" to "Apache 2.0")) {
            OutlinedButton(onClick = { file = name }, enabled = file != name) { Text(label) }
        }
    }
    Text(reflow(read(file)), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
}

/**
 * Joins the files' hard-wrapped lines into paragraphs, so they wrap to the screen instead. Blank
 * lines, headings, table rows, list items, indented lines and code blocks stay as they are.
 */
internal fun reflow(text: String): String {
    val out = StringBuilder()
    var inCode = false
    var joinable = false // the last line written is paragraph text the next one may continue
    for (line in text.lines()) {
        val fence = line.startsWith("```")
        if (fence) inCode = !inCode
        val paragraph = !inCode && !fence && line.isNotBlank() && !line.first().isWhitespace() &&
            !line.startsWith("#") && !line.startsWith("|") && !line.startsWith("- ") && !NUMBERED.containsMatchIn(line)
        if (out.isNotEmpty()) out.append(if (paragraph && joinable) ' ' else '\n')
        out.append(if (paragraph) line.trim() else line)
        joinable = paragraph
    }
    return out.toString()
}

private val NUMBERED = Regex("""^\d+\. """)
