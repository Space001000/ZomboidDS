package dev.zomboidds.companion.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatedSplashTest {

    @Test
    fun `special days get their line, other days none`() {
        assertEquals("Merry Knoxmas!", datedSplash(LocalDate.of(2026, 12, 24)))
        assertEquals("Merry Knoxmas!", datedSplash(LocalDate.of(2026, 12, 26)))
        assertEquals("Happy new year! Day one, again.", datedSplash(LocalDate.of(2026, 12, 31)))
        assertEquals("Happy new year! Day one, again.", datedSplash(LocalDate.of(2027, 1, 1)))
        assertEquals("Spooky! Wait, that's every day.", datedSplash(LocalDate.of(2026, 10, 31)))
        assertEquals("July 9th, 1993. Here we go again.", datedSplash(LocalDate.of(2027, 7, 9)))
        assertNull(datedSplash(LocalDate.of(2026, 12, 27)))
        assertNull(datedSplash(LocalDate.of(2026, 1, 31)))
        assertNull(datedSplash(LocalDate.of(2026, 11, 17)))
    }
}
