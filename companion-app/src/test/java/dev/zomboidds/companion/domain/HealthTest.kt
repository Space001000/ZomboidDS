package dev.zomboidds.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {

    private fun part(vararg tones: HealthTone) =
        BodyPartStatus("Hand_R", "Right Hand", tones.map { HealthLine("line", it) })

    @Test
    fun `a part's tone is its most urgent line`() {
        assertEquals(HealthTone.BAD, part(HealthTone.GOOD, HealthTone.BAD).tone)
        assertEquals(HealthTone.WARN, part(HealthTone.GOOD, HealthTone.WARN).tone)
        assertEquals(HealthTone.GOOD, part(HealthTone.GOOD).tone)
        assertEquals(HealthTone.NEUTRAL, part().tone)
    }

    @Test
    fun `a dirty bandage still needs treatment, a clean one doesn't`() {
        assertTrue(part(HealthTone.WARN).needsTreatment)
        assertFalse(part(HealthTone.GOOD).needsTreatment)
    }
}
