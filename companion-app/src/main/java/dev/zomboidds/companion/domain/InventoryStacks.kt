package dev.zomboidds.companion.domain

/** One tile in the inventory grid: a single item, or several identical unequipped ones. */
data class ItemStack(val items: List<InventoryItem>) {
    val first: InventoryItem get() = items.first()
    val count: Int get() = items.size
}

fun Inventory.stacks(): List<ItemStack> = items.stacks()

/**
 * Groups a container's items the way players expect: what's in hand first, then what's worn, then the
 * rest, with identical items stacked ("Bandage ×2"). Equipped items and items with a condition are
 * never stacked, since each one differs.
 */
fun List<InventoryItem>.stacks(): List<ItemStack> {
    val (equipped, loose) = partition { it.equipped != null }
    val handsFirst = equipped.sortedBy { if (it.equipped == EquipSlot.WORN) 1 else 0 }
        .map { ItemStack(listOf(it)) }
    val grouped = loose
        .groupBy { if (it.condition == null) "${it.type}|${it.name}" else "id:${it.id}" }
        .values
        .map(::ItemStack)
        .sortedWith(compareBy({ it.first.category ?: "" }, { it.first.name }))
    return handsFirst + grouped
}

/** Identical items stack under this key; an unfolded stack is remembered by it. */
val ItemStack.key: String get() = "${first.type}|${first.name}"

/** What a container's grid shows: item stacks, and your worn clothes folded into one tile. */
sealed interface PaneEntry {
    /**
     * [worn]: one of your unfolded worn clothes. [unfolded]: a stack shown item by item after it;
     * [part]: one of those items, on its own.
     */
    data class Stack(val stack: ItemStack, val worn: Boolean = false, val unfolded: Boolean = false, val part: Boolean = false) : PaneEntry
    /** "Worn ×6": tap to show them right after it ([open]), tap again to fold them away. */
    data class Worn(val stacks: List<ItemStack>, val open: Boolean) : PaneEntry
}

/**
 * Worn clothes fill a whole row but are rarely what you're after: fold them into one tile after
 * what's in your hands. When [open], they follow that tile.
 */
fun List<ItemStack>.foldWorn(open: Boolean): List<PaneEntry> {
    val (worn, rest) = partition { it.first.equipped == EquipSlot.WORN }
    if (worn.isEmpty()) return map { PaneEntry.Stack(it) }
    val (hands, loose) = rest.partition { it.first.equipped != null }
    val fold = listOf(PaneEntry.Worn(worn, open)) + if (open) worn.map { PaneEntry.Stack(it, worn = true) } else emptyList()
    return hands.map { PaneEntry.Stack(it) } + fold + loose.map { PaneEntry.Stack(it) }
}

/**
 * The grid's entries: worn clothes folded unless [wornOpen], and each stack in [unfolded] (by
 * [key]) followed by its items one by one, like the game's inventory unfolds a stack.
 */
fun List<ItemStack>.paneEntries(wornOpen: Boolean, unfolded: Set<String>): List<PaneEntry> =
    foldWorn(wornOpen).flatMap { entry ->
        if (entry is PaneEntry.Stack && entry.stack.count > 1 && entry.stack.key in unfolded) {
            listOf(entry.copy(unfolded = true)) + entry.stack.items.map { PaneEntry.Stack(ItemStack(listOf(it)), part = true) }
        } else {
            listOf(entry)
        }
    }

/** A key ring is named after its owner ("Hortense Scroggins's Key Ring"): too long for a tile. */
val InventoryItem.isKeyRing: Boolean get() = type.substringAfter('.').startsWith("KeyRing")
