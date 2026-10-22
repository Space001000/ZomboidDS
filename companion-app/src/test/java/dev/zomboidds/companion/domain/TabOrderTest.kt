package dev.zomboidds.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {

    private val all = listOf("inventory", "here", "deck", "status", "craft", "map")

    private fun order(vararg saved: String) = orderTabs(all, saved.toList()) { it }

    @Test
    fun `nothing saved is the default order`() {
        assertEquals(all, order())
    }

    @Test
    fun `the saved order wins`() {
        assertEquals(listOf("map", "status", "inventory", "here", "deck", "craft"),
            order("map", "status", "inventory", "here", "deck", "craft"))
    }

    @Test
    fun `unknown and repeated ids are skipped`() {
        assertEquals(all, order("inventory", "radio", "here", "here", "deck", "status", "craft", "map"))
    }

    @Test
    fun `a tab the saved order doesn't name goes after its neighbour from the default order`() {
        // Saved before "status" existed: it lands after deck, where the player put deck.
        assertEquals(listOf("deck", "status", "inventory", "here", "craft", "map"),
            order("deck", "inventory", "here", "craft", "map"))
        // A first tab that's missing goes to the front.
        assertEquals(listOf("inventory", "map", "here", "deck", "status", "craft"),
            order("map", "here", "deck", "status", "craft"))
    }

    @Test
    fun `hidden tabs keep their slot when the shown ones move`() {
        // Map beside Here: not shown, so it stays last while Status moves up.
        val shown = listOf("inventory", "status", "here", "deck", "craft")
        assertEquals(listOf("inventory", "status", "here", "deck", "craft", "map"), reorderShown(all, shown))
        // Map in the middle stays in the middle.
        val withMapInside = listOf("inventory", "map", "here", "deck", "status", "craft")
        assertEquals(listOf("craft", "map", "inventory", "here", "deck", "status"),
            reorderShown(withMapInside, listOf("craft", "inventory", "here", "deck", "status")))
    }
}
