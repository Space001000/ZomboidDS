package dev.zomboidds.companion.domain

/** Items picked to act on together (hold one, then tap more): all in one container, by item id. */
data class PickedItems(val containerId: String, val ids: Set<Long>) {

    /** The ids picked in [containerId]: none when the picks are in another container. */
    fun idsIn(containerId: String): Set<Long> = if (containerId == this.containerId) ids else emptySet()

    /** Which of [stacks] are picked: any of their items counts. */
    fun stacksIn(stacks: List<ItemStack>): List<ItemStack> = stacks.filter { stack -> stack.items.any { it.id in ids } }

    companion object {
        /** [ids] picked in [containerId]; null when there are none. */
        fun of(containerId: String, ids: Set<Long>): PickedItems? = ids.takeIf { it.isNotEmpty() }?.let { PickedItems(containerId, it) }
    }
}

/**
 * Picks [stack] in [from], or leaves it out again when it's picked: a whole stack at a time. Picking
 * in another container starts over; null once nothing is left.
 */
fun PickedItems?.toggle(stack: ItemStack, from: String): PickedItems? {
    val ids = stack.items.map { it.id }.toSet()
    val current = this?.idsIn(from).orEmpty()
    return PickedItems.of(from, if (ids.any { it in current }) current - ids else current + ids)
}

/**
 * What's picked, as shown now: the container it's in ([containerId], null when that isn't shown) and
 * its [stacks] there.
 */
class ShownPicks(val containerId: String?, val stacks: List<ItemStack>) {

    /** While something is picked in [from], a tap there picks too (instead of opening the item). */
    fun tapPicks(from: String): Boolean = containerId == from && stacks.isNotEmpty()

    /** What a drag of [stack] from [from] carries: everything picked with it (it first), or just it. */
    fun carried(stack: ItemStack, from: String): List<ItemStack> =
        if (containerId == from && stacks.any { it.first.id == stack.first.id }) {
            listOf(stack) + stacks.filter { it.first.id != stack.first.id }
        } else {
            listOf(stack)
        }
}
