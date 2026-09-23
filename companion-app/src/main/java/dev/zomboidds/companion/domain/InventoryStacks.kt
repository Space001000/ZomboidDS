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
