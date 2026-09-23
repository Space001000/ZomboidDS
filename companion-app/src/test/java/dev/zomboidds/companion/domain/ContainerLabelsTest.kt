package dev.zomboidds.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ContainerLabelsTest {

    private fun container(id: String, name: String) =
        Container(id, ContainerKind.NEARBY, name, null, null, null, locked = false, items = emptyList())

    @Test
    fun `repeated names are numbered in the game's order, unique ones stay as they are`() {
        val labels = listOf(
            container("c1", "Shelves"), container("c2", "Drawer"), container("c3", "Shelves"), container("c4", "Ground"),
        ).labels()
        assertEquals(mapOf("c1" to "Shelves 1", "c2" to "Drawer", "c3" to "Shelves 2", "c4" to "Ground"), labels)
    }
}
