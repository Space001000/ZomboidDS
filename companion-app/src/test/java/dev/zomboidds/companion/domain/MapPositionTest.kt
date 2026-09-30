package dev.zomboidds.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPositionTest {

    private fun at(miniMap: Boolean, worldMap: Boolean, alwaysShow: Boolean) =
        MapPosition(0f, 0f, 0, null, miniMap, worldMap, alwaysShow)

    @Test
    fun `the map shows where the save allows the minimap`() {
        assertTrue(at(miniMap = true, worldMap = true, alwaysShow = false).shown)
        assertFalse(at(miniMap = false, worldMap = true, alwaysShow = false).shown)
    }

    @Test
    fun `the mod option shows it on every save with a world map`() {
        assertTrue(at(miniMap = false, worldMap = true, alwaysShow = true).shown)
        assertFalse("no world map in the game: none here either", at(miniMap = false, worldMap = false, alwaysShow = true).shown)
    }
}
