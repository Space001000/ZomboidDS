package dev.zomboidds.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickedItemsTest {

    private fun item(id: Long, name: String) = InventoryItem(id, "Base.$name", name, "Item", null, 0.1f, null, null)

    /** Two bandages stacked, a knife, beans. */
    private val bandages = ItemStack(listOf(item(1, "Bandage"), item(2, "Bandage")))
    private val knife = ItemStack(listOf(item(3, "Knife")))
    private val beans = ItemStack(listOf(item(4, "Beans")))
    private val stacks = listOf(bandages, knife, beans)
    private val none: PickedItems? = null

    @Test
    fun `picking toggles a whole stack`() {
        val picked = none.toggle(bandages, "inventory")
        assertEquals(PickedItems("inventory", setOf(1L, 2L)), picked)
        assertEquals(PickedItems("inventory", setOf(1L, 2L, 3L)), picked.toggle(knife, "inventory"))
        assertEquals(PickedItems("inventory", setOf(3L)), picked.toggle(knife, "inventory").toggle(bandages, "inventory"))
    }

    @Test
    fun `picking in another container starts over`() {
        val picked = none.toggle(bandages, "inventory").toggle(knife, "shelves")
        assertEquals(PickedItems("shelves", setOf(3L)), picked)
    }

    @Test
    fun `removing the last pick clears it`() {
        assertNull(none.toggle(knife, "inventory").toggle(knife, "inventory"))
        assertNull(PickedItems.of("inventory", emptySet()))
    }

    @Test
    fun `picked stacks are whole stacks, or what is picked of an unfolded one`() {
        assertEquals(listOf(bandages, beans), PickedItems("inventory", setOf(1L, 2L, 4L)).stacksIn(stacks))
        assertEquals(listOf(ItemStack(listOf(bandages.items[1])), beans), PickedItems("inventory", setOf(2L, 4L)).stacksIn(stacks))
    }

    @Test
    fun `one item of an unfolded stack is picked on its own`() {
        val one = ItemStack(listOf(bandages.items[0]))
        assertEquals(PickedItems("inventory", setOf(1L)), none.toggle(one, "inventory"))
    }

    @Test
    fun `a drag of one picked item carries what's picked, not its whole stack`() {
        val one = ItemStack(listOf(bandages.items[0]))
        val shown = ShownPicks("inventory", listOf(knife, one))
        assertEquals(listOf(one, knife), shown.carried(one, "inventory"))
        // The other bandage isn't picked: a drag of it carries only it.
        val other = ItemStack(listOf(bandages.items[1]))
        assertEquals(listOf(other), shown.carried(other, "inventory"))
    }

    @Test
    fun `a drag from a picked stack carries all picked stacks, the dragged one first`() {
        val shown = ShownPicks("inventory", listOf(bandages, knife, beans))
        assertEquals(listOf(knife, bandages, beans), shown.carried(knife, "inventory"))
    }

    @Test
    fun `a drag from an unpicked stack carries only that stack`() {
        val shown = ShownPicks("inventory", listOf(bandages, knife))
        assertEquals(listOf(beans), shown.carried(beans, "inventory"))
        // Picked elsewhere: the same stack in another container is not picked there.
        assertEquals(listOf(knife), shown.carried(knife, "shelves"))
    }

    @Test
    fun `a tap picks only where something is picked and shown`() {
        val shown = ShownPicks("inventory", listOf(knife))
        assertEquals(true, shown.tapPicks("inventory"))
        assertEquals(false, shown.tapPicks("shelves"))
        assertEquals(false, ShownPicks(null, emptyList()).tapPicks("inventory"))
    }
}
