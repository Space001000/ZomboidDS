package dev.zomboidds.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrequencyTest {

    private fun typed(vararg keys: String) = keys.fold("") { text, key -> Frequency.type(text, key) }

    @Test
    fun `shows kHz as MHz with one decimal`() {
        assertEquals("93.2", Frequency.format(93200))
        assertEquals("98.0", Frequency.format(98000))
        assertEquals("0.2", Frequency.format(200))
        assertEquals("1000.0", Frequency.format(1_000_000))
    }

    @Test
    fun `reads typed MHz as kHz`() {
        assertEquals(101200, Frequency.parse("101.2"))
        assertEquals(98000, Frequency.parse("98"))
        assertEquals(98000, Frequency.parse("98."))
        assertEquals(500, Frequency.parse(".5"))
        assertNull(Frequency.parse(""))
        assertNull(Frequency.parse("."))
    }

    @Test
    fun `typing a frequency`() {
        assertEquals("101.2", typed("1", "0", "1", ".", "2"))
        assertEquals("101.2", typed("1", "0", "1", ".", "2", "5"))
        assertEquals("0.", typed("."))
        assertEquals("93.", typed("9", "3", ".", "."))
        assertEquals("1000", typed("1", "0", "0", "0", "0"))
        assertEquals("7", typed("0", "7"))
        assertEquals("10", typed("1", "0", "1", "⌫"))
        assertEquals("", typed("⌫"))
    }
}
