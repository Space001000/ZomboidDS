package dev.zomboidds.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameZoomTest {

    @Test
    fun `the game's zoom from pixels per tile and the view's height`() {
        // The game's minimap at its default zoom 19 in a 200 px view: 2^19 * 200 / 40075017 px per tile.
        assertEquals(19f, gameZoom(2.6166f, 200f), 0.01f)
        assertEquals("twice the pixels per tile is one zoom level in", 20f, gameZoom(2 * 2.6166f, 200f), 0.01f)
        assertEquals("a view twice as tall shows the same at one level out", 18f, gameZoom(2.6166f, 400f), 0.01f)
    }

    @Test
    fun `pixels per tile for a zoom level is the inverse`() {
        assertEquals(15.5f, gameZoom(pixelsPerTileAt(15.5f, 575f), 575f), 0.001f)
        assertEquals("zoom 15.5 shows about 850 tiles top to bottom", 865f, 575f / pixelsPerTileAt(15.5f, 575f), 5f)
    }

    @Test
    fun `labels show within their zoom levels`() {
        val town = MapSymbol(null, "Muldraugh", 0f, 0f, listOf(0f, 0f, 0f, 0f), 1f, 0f, 0.5f, 0.5f, label = true, maxZoom = 13f)
        assertTrue(town.isShownAt(12.9f))
        assertFalse("zoomed in past the town name", town.isShownAt(13f))
    }
}
