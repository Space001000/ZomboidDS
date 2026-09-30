package dev.zomboidds.companion.ui

import dev.zomboidds.companion.domain.RELEASES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    @Test
    fun `starred words are bold and the stars are gone`() {
        val text = bold("**Map:** the game's minimap, **beside** Here")
        assertEquals("Map: the game's minimap, beside Here", text.text)
        assertEquals(listOf("Map:", "beside"), text.spanStyles.map { text.text.substring(it.start, it.end) })
    }

    @Test
    fun `releases are newest first, each once, with notes`() {
        fun parts(v: String) = v.split('.').map { it.toInt() }
        val versions = RELEASES.map { parts(it.version) }
        val sorted = versions.sortedWith(compareByDescending<List<Int>> { it[0] }.thenByDescending { it[1] }.thenByDescending { it[2] })
        assertEquals(sorted, versions)
        assertEquals(versions.size, versions.toSet().size)
        assertTrue(RELEASES.all { it.notes.isNotEmpty() && it.notes.all { n -> n.split("**").size % 2 == 1 } })
    }
}
