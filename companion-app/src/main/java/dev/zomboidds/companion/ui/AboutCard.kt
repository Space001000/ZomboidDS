package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.BuildConfig
import dev.zomboidds.companion.setup.AppUpdater

private const val SOURCE_URL = "https://github.com/${AppUpdater.REPOSITORY}"
private const val KO_FI_URL = "https://ko-fi.com/space000"

/**
 * Who made this and under which terms: version, licence, source, the open-source licences of what
 * it includes, and a Ko-fi link. Donating never unlocks anything (The Indie Stone's modding policy).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutCard(openUrl: (String) -> Unit, onLicences: () -> Unit, onWhatsNew: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "ZomboidDS ${BuildConfig.VERSION_NAME}: free and open source (GPL-3.0). An unofficial mod, not " +
                    "affiliated with The Indie Stone. You need your own copy of Project Zomboid.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onWhatsNew) { Text("What's new") }
                OutlinedButton(onClick = { openUrl(SOURCE_URL) }) { Text("Source on GitHub") }
                OutlinedButton(onClick = onLicences) { Text("Open-source licences") }
                OutlinedButton(onClick = { openUrl(KO_FI_URL) }) { Text("Support on Ko-fi") }
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
