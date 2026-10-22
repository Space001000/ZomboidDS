package dev.zomboidds.companion.domain

/**
 * The player's tab order. [saved] holds tab ids; ids the app doesn't know are skipped, and tabs
 * the saved order doesn't name (a new tab after an update) keep their place from [all], the
 * default order: after the tab before them there.
 */
fun <T : Any> orderTabs(all: List<T>, saved: List<String>, id: (T) -> String): List<T> {
    val order = saved.mapNotNull { s -> all.firstOrNull { id(it) == s } }.distinct().toMutableList()
    all.forEachIndexed { index, tab ->
        if (tab !in order) {
            val before = all.subList(0, index).lastOrNull { it in order }
            order.add(if (before == null) 0 else order.indexOf(before) + 1, tab)
        }
    }
    return order
}

/**
 * [order] after the shown tabs were rearranged to [shown]: tabs that aren't shown (Map while it sits
 * beside Here) keep their slots, the shown ones fill the other slots in their new order.
 */
fun <T> reorderShown(order: List<T>, shown: List<T>): List<T> {
    val next = shown.iterator()
    return order.map { if (it in shown) next.next() else it }
}
