package dev.zomboidds.companion.domain

/**
 * The order of the cards on Here, kept by the app instead of the game's menu order (which changes
 * whenever you move or turn): what has been in front of you comes first, the most recent on top,
 * then everything else in the order it first showed up. When something new comes in front, only
 * the cards above its old place move down one; the ones below it stay put.
 */
class HereOrder {
    private val firstSeen = mutableMapOf<String, Int>()
    private val recent = ArrayList<String>()

    /** Remembers when each object first showed up. */
    fun see(options: List<MenuOption>) {
        for (option in options) firstSeen.getOrPut(option.stableKey) { firstSeen.size }
    }

    /** [key] has been in front for a while: it goes on top. */
    fun promote(key: String) {
        recent.remove(key)
        recent.add(0, key)
    }

    /** The world menu split for the screen: the objects in order, and the lists and loose actions. */
    fun arrange(menu: ItemMenu): Arranged {
        see(menu.options)
        val (objects, rest) = menu.options.partition { it.children.isNotEmpty() && !it.tray }
        fun rank(option: MenuOption): Int {
            val i = recent.indexOf(option.stableKey)
            return if (i >= 0) i else recent.size + (firstSeen[option.stableKey] ?: Int.MAX_VALUE / 2)
        }
        return Arranged(objects.sortedBy(::rank), rest)
    }

    data class Arranged(val objects: List<MenuOption>, val tray: List<MenuOption>)
}

/** The world menu's key for an option, or its id where the game sent none. */
val MenuOption.stableKey: String get() = key ?: id

/**
 * The same option in a newer menu: the top-level option with [topKey], then down by name. Used
 * when a tap lands on a menu that has been replaced since (the screen holds still while touched).
 */
fun ItemMenu.find(topKey: String, names: List<String>): MenuOption? {
    var option = options.firstOrNull { it.stableKey == topKey } ?: return null
    for (name in names) option = option.children.firstOrNull { it.name == name } ?: return null
    return option
}
