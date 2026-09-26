package dev.zomboidds.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GameHourTest {
    @Test
    fun `the hour of the game's clock, in either of its formats`() {
        assertEquals(14, gameHour("14:25"))
        assertEquals(14, gameHour("2:25 PM"))
        assertEquals(0, gameHour("12:05 AM"))
        assertEquals(12, gameHour("12:05 PM"))
        assertEquals(null, gameHour(null))
        assertEquals(null, gameHour("soon"))
    }
}
