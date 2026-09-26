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
    /** The game's icon for it (texture name for the icon endpoint), if any. */
    val icon: String? = null,
    /** One of the item's main uses, shown as a button above the rest; null for the rest. */
    val pill: MenuPill? = null,
)

/** How a pill is drawn: a main use, or Drop (quieter). */
enum class MenuPill { ACTION, DROP }

/** What the app can ask the game to do with an item. */
interface ItemActions {
    /** A quick action (equip, wear, drop, ...). Never throws. */
    suspend fun perform(command: ItemCommand): CommandResult

    /** The game's own right-click menu for an item. Never throws. */
    suspend fun itemMenu(itemId: Long): ItemMenuResult

    /** Runs an option of a menu from [itemMenu], like clicking it in the game. Never throws. */
    suspend fun selectMenuOption(menuId: String, optionId: String): CommandResult

    /** Moves an item into a container ([Container.id]), walking there first like the game. Never throws. */
    suspend fun transfer(itemId: Long, toContainer: String): CommandResult

    /** Moves everything the game's Take All / Transfer All would. Never throws. */
    suspend fun transferAll(fromContainer: String, toContainer: String): CommandResult

    /** Selects a container around the player in the game, which outlines it in the world. Never throws. */
    suspend fun selectContainer(containerId: String): CommandResult
}

sealed interface ItemMenuResult {
    data class Ready(val menu: ItemMenu) : ItemMenuResult

    data class Failed(val reason: String) : ItemMenuResult
}
