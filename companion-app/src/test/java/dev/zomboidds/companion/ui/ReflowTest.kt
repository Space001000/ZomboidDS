package dev.zomboidds.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReflowTest {

    @Test
    fun `hard-wrapped paragraphs join, everything else keeps its lines`() {
        val text = """
            # Title

            ZomboidDS is licensed under the GPL, and
            includes the software below.

            | A | B |
            |---|---|
            - an item
              continued
            ```
            Copyright (C) 2012
            Redistribution and use
            ```
            1. Numbered
            stays.
        """.trimIndent()
        val expected = """
            # Title

            ZomboidDS is licensed under the GPL, and includes the software below.

            | A | B |
            |---|---|
            - an item
              continued
            ```
            Copyright (C) 2012
            Redistribution and use
            ```
            1. Numbered
            stays.
        """.trimIndent()
        assertEquals(expected, reflow(text))
    }
}
