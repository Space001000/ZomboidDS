package dev.zomboidds.companion.devtools

import androidx.compose.runtime.Composable
import dev.zomboidds.companion.domain.GameGateway

/** The release build's stand-in: no development tools (the real ones are in src/devtools). */
class DevTools(@Suppress("UNUSED_PARAMETER") gateway: GameGateway) {
    @Composable
    fun DeckSection(@Suppress("UNUSED_PARAMETER") capabilities: Set<String>) {
    }
}
