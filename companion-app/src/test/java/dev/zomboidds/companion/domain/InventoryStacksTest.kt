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
}
