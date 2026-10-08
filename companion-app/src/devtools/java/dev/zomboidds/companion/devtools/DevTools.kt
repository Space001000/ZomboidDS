package dev.zomboidds.companion.devtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.GameGateway
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * DEVELOPMENT ONLY (debug and dev builds; the release build has an empty DevTools). Test kits: the
 * mod's dev build (mod/dev) gives a feature's test setup on request. Shown at the bottom of the Deck
 * when the running mod has them (capability `dev.kits`).
 */
class DevTools(private val gateway: GameGateway) {

    private class Kit(val id: String, val name: String, val description: String)

    @Composable
    fun DeckSection(capabilities: Set<String>) {
        if ("dev.kits" !in capabilities) return
        val kits by produceState<List<Kit>?>(null) {
            value = when (val reply = gateway.command("dev_kits", JsonObject(emptyMap()))) {
                is Fetched.Ready -> ((reply.value as? JsonObject)?.get("kits") as? JsonArray).orEmpty().mapNotNull { entry ->
                    val kit = entry as? JsonObject ?: return@mapNotNull null
                    fun text(key: String) = kit[key]?.jsonPrimitive?.content.orEmpty()
                    Kit(text("id"), text("name"), text("description"))
                }
                is Fetched.Failed -> emptyList()
            }
        }
        var message by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TEST KITS · development build", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                kits?.forEach { kit ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(kit.name, style = MaterialTheme.typography.titleSmall)
                            Text(kit.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val reply = gateway.command("dev_kit", buildJsonObject { put("kit", kit.id) })
                                message = when (reply) {
                                    is Fetched.Ready -> "${kit.name} kit given"
                                    is Fetched.Failed -> reply.reason
                                }
                            }
                        }) { Text("Give") }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
