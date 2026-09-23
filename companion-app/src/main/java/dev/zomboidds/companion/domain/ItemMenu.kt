package dev.zomboidds.companion.domain

/** The game's own right-click menu for an item, as built by the game. */
data class ItemMenu(val menuId: String, val options: List<MenuOption>)

data class MenuOption(
    val id: String,
    val name: String,
    val enabled: Boolean,
    /** The game's explanation, e.g. why an option is greyed out. */
    val tooltip: String? = null,
    /** Non-empty for submenus; only options without children can be selected. */
    val children: List<MenuOption> = emptyList(),
)

/** What the app can ask the game to do with an item. */
interface ItemActions {
    /** A quick action (equip, wear, drop, ...). Never throws. */
    suspend fun perform(command: ItemCommand): CommandResult

    /** The game's own right-click menu for an item. Never throws. */
    suspend fun itemMenu(itemId: Long): ItemMenuResult

    /** Runs an option of a menu from [itemMenu], like clicking it in the game. Never throws. */
    suspend fun selectMenuOption(menuId: String, optionId: String): CommandResult
}

sealed interface ItemMenuResult {
    data class Ready(val menu: ItemMenu) : ItemMenuResult

    data class Failed(val reason: String) : ItemMenuResult
}
