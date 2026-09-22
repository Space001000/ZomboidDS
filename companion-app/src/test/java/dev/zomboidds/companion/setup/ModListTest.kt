package dev.zomboidds.companion.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModListTest {

    /** Copied from a real 42.20 install (Zomdroid on the AYN Thor). */
    private val defaultTxt = """
        VERSION = 1,

        mods
        {
            mod = ZombieBuddy,
        }

        maps
        {
        }

    """.trimIndent()

    @Test
    fun `reads enabled mods`() {
        assertEquals(listOf("ZombieBuddy"), ModList.enabledMods(defaultTxt))
    }

    @Test
    fun `adds missing mods inside the mods block and keeps the rest`() {
        val updated = ModList.withMods(defaultTxt, listOf("ZombieBuddy", "ZomboidDS"))

        assertEquals(listOf("ZombieBuddy", "ZomboidDS"), ModList.enabledMods(updated))
        assertTrue(updated.contains("maps\n{\n}"))
        assertTrue(updated.startsWith("VERSION = 1,"))
    }

    @Test
    fun `leaves the text alone when nothing is missing`() {
        assertSame(defaultTxt, ModList.withMods(defaultTxt, listOf("ZombieBuddy")))
    }

    @Test
    fun `creates the file content when it is empty`() {
        val created = ModList.withMods("", listOf("ZombieBuddy", "ZomboidDS"))
        assertEquals(listOf("ZombieBuddy", "ZomboidDS"), ModList.enabledMods(created))
        assertTrue(created.startsWith("VERSION = 1,"))
    }

    @Test
    fun `handles an empty mods block`() {
        val text = "VERSION = 1,\n\nmods\n{\n}\n"
        assertEquals(emptyList<String>(), ModList.enabledMods(text))
        assertEquals(listOf("ZomboidDS"), ModList.enabledMods(ModList.withMods(text, listOf("ZomboidDS"))))
    }
}
