package dev.zomboidds.companion.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's view of the game. The UI only talks to this interface; how the data gets here
 * (WebSocket to the mod, fake data for previews, ...) is an implementation detail.
 */
interface GameGateway : ItemActions {
    val state: StateFlow<GameState>
    val connection: StateFlow<ConnectionStatus>

    /** Starts connecting (and reconnecting) until [scope] is cancelled. Calling it again is a no-op. */
    fun start(scope: CoroutineScope)

    /** Where to load an item icon from, e.g. for "Item_Axe". */
    fun iconUrl(icon: String): String


}
