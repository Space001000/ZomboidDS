package dev.zomboidds.companion.domain

import dev.zomboidds.companion.data.ProtocolV1
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class InventoryStacksTest {

    /** The shared inventory fixture: an equipped axe, worn cap, two bandages, water, beans. */
    private val inventory = ProtocolV1.apply(GameState(), File("../protocol/fixtures/inventory.json").readText()).inventory!!

    @Test
    fun `hands first, then worn, then the rest by category`() {
        val names = inventory.stacks().map { it.first.name }
        assertEquals(listOf("Axe", "Baseball Cap", "Bandage", "Beans", "Water Bottle"), names)
    }

    @Test
    fun `identical loose items stack`() {
        val bandages = inventory.stacks().single { it.first.name == "Bandage" }
        assertEquals(2, bandages.count)
        assertEquals(setOf(10235L, 10236L), bandages.items.map { it.id }.toSet())
    }

    @Test
    fun `items with a condition never stack`() {
        val knife = InventoryItem(1, "Base.Knife", "Knife", "Weapon", null, 0.3f, 0.5f, null)
        val stacks = Inventory(1f, 10f, listOf(knife, knife.copy(id = 2, condition = 0.9f))).stacks()
        assertEquals(2, stacks.size)
    }

    private fun PaneEntry.label() = when (this) {
        is PaneEntry.Stack -> stack.first.name + if (worn) " (worn)" else ""
        is PaneEntry.Worn -> "Worn ×${stacks.size}" + if (open) " open" else ""
    }

    @Test
    fun `worn clothes fold into one tile after what's in hand`() {
        assertEquals(listOf("Axe", "Worn ×1", "Bandage", "Beans", "Water Bottle"), inventory.stacks().foldWorn(open = false).map { it.label() })
        assertEquals(listOf("Axe", "Worn ×1 open", "Baseball Cap (worn)", "Bandage", "Beans", "Water Bottle"),
            inventory.stacks().foldWorn(open = true).map { it.label() })
        val nothingWorn = inventory.items.filter { it.equipped != EquipSlot.WORN }.stacks()
        assertEquals("no tile without worn clothes", nothingWorn.size, nothingWorn.foldWorn(open = false).size)
    }

    @Test
    fun `key rings are recognised by type, whoever owns them`() {
        val ring = InventoryItem(1, "Base.KeyRing", "Hortense Scroggins's Key Ring", "Container", null, 0.1f, null, null)
        assertEquals(true, ring.isKeyRing)
        assertEquals(false, ring.copy(type = "Base.Key1").isKeyRing)
    }

    @Test
    fun `an unfolded stack is followed by its items one by one`() {
        fun item(id: Long) = InventoryItem(id, "Base.Bandage", "Bandage", "First Aid", null, 0.1f, null, null)
        val bandages = ItemStack(listOf(item(1), item(2), item(3)))
        val pen = ItemStack(listOf(InventoryItem(9, "Base.Pen", "Pen", "Junk", null, 0.1f, null, null)))
        val stacks = listOf(bandages, pen)
        assertEquals(listOf(PaneEntry.Stack(bandages), PaneEntry.Stack(pen)), stacks.paneEntries(wornOpen = false, unfolded = emptySet()))
        val open = stacks.paneEntries(wornOpen = false, unfolded = setOf(bandages.key, pen.key))
        assertEquals(PaneEntry.Stack(bandages, unfolded = true), open[0])
        assertEquals((1L..3L).map { PaneEntry.Stack(ItemStack(listOf(item(it))), part = true) }, open.subList(1, 4))
        assertEquals("a single item doesn't unfold", PaneEntry.Stack(pen), open[4])
    }
}
