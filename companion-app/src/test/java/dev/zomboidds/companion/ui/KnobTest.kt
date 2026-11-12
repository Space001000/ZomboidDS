package dev.zomboidds.companion.ui

import dev.zomboidds.companion.domain.Knob
import dev.zomboidds.companion.domain.KnobMark
import org.junit.Assert.assertEquals
import org.junit.Test

/** The oven panel's knobs stop where the game's knob does (ISKnob:onMouseMove). */
class KnobTest {
    // 42.21's oven timer: 16 marks every 18°, 0 to 270.
    private val timer = Knob("KnobBGOvenTimer", listOf(0, 1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 40, 50, 60, 90, 120)
        .mapIndexed { i, v -> KnobMark(i * 18f, v) })

    // The microwave's power: marks at 270, 315, 0, 45, 90, added in that value order by the game.
    private val power = Knob("KnobBGMicrowaveTemp", listOf(0f to 90, 45f to 110, 90f to 130, 270f to 50, 315f to 70)
        .map { (a, v) -> KnobMark(a, v) })

    @Test
    fun `a finger snaps to the nearest mark, past halfway to the next`() {
        assertEquals(0, knobMarkAt(timer, 8f)?.value)
        assertEquals(1, knobMarkAt(timer, 10f)?.value)
        assertEquals(120, knobMarkAt(timer, 280f)?.value)
    }

    @Test
    fun `past halfway from the last mark round to the top is the first again`() {
        assertEquals(120, knobMarkAt(timer, 314f)?.value)
        assertEquals(0, knobMarkAt(timer, 316f)?.value)
    }

    @Test
    fun `a knob whose marks wrap past the top`() {
        assertEquals(90, knobMarkAt(power, 10f)?.value)
        assertEquals(70, knobMarkAt(power, 300f)?.value)
        assertEquals(50, knobMarkAt(power, 250f)?.value)
        assertEquals(listOf(50, 70, 90, 110, 130), power.byValue.map { it.value })
    }
}
