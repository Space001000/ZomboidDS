package dev.zomboidds.companion.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's view of the game. The UI only talks to this interface; how the data gets here
 * (WebSocket to the mod, fake data for previews, ...) is an implementation detail.
 */
interface GameGateway : ItemActions, GameControls {
    val state: StateFlow<GameState>
    val connection: StateFlow<ConnectionStatus>

    /** One-off requests from the game, e.g. "show this container". Not replayed; missed ones are gone. */
    val events: Flow<GameEvent>

    /** Starts connecting (and reconnecting) until [scope] is cancelled. Calling it again is a no-op. */
    fun start(scope: CoroutineScope)

    /** Where to load an item icon from, e.g. for "Item_Axe". */
    fun iconUrl(icon: String): String
}

/** What the app can ask of the game itself. */
interface GameControls {
    /** Presses the game's own speed button. Never throws. */
    suspend fun setSpeed(speed: GameSpeed): CommandResult

    /**
     * Whether the app shows "Here": while on, the game keeps [GameState.here] up to date as the
     * player moves (the game's world menu, what the controller's interact button opens). Call with
     * true every few seconds while shown (it expires), false when hidden. Options run with
     * [ItemActions.selectMenuOption]. Fire and forget.
     */
    fun watchHere(on: Boolean)

    /** The game's treatment menu for a body part ([BodyPartStatus.id]). Never throws. */
    suspend fun bodyPartMenu(partId: String): ItemMenuResult
}

sealed interface GameEvent {
    /**
     * The player asked the game for its inventory (e.g. the controller's Loot/Inventory button);
     * the mod kept the game's window closed so the app can show it. [containerId] is the container
     * the game would have opened.
     */
    data class ShowInventory(val containerId: String?) : GameEvent
}
