package dev.zomboidds.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveAmountsTest {
    @Test
    fun `one, and half rounded down like the game's Grab half`() {
        assertEquals(emptyList<Int>(), moveAmounts(1))
        assertEquals(listOf(1), moveAmounts(2))
        assertEquals(listOf(1), moveAmounts(3))
        assertEquals(listOf(1, 2), moveAmounts(5))
        assertEquals(listOf(1, 3), moveAmounts(6))
        assertEquals(listOf(1, 20), moveAmounts(40))
    }
}
