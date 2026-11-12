package dev.zomboidds.companion.domain

/**
 * Ovens and microwaves as the game's settings window shows them: temperature, timer, Turn on / Turn
 * off. The game opens one at a time ([GameEvent.ShowOven]); the panel reads it with [ovenState] and
 * works it with [ovenControl], each control what the game's window does.
 */
interface Ovens {
    /** The oven the game opened, or [OvenState.gone] once the player walked away. Never throws. */
    suspend fun ovenState(): Fetched<OvenState>

    suspend fun ovenControl(control: OvenControl): CommandResult

    /** The panel closed: the game forgets the oven. */
    suspend fun closeOven()
}

sealed interface OvenControl {
    /** A knob turned to one of its marks ([KnobMark.value]). */
    data class Temperature(val value: Int) : OvenControl

    /** The timer knob, in minutes (one of its marks). */
    data class Timer(val minutes: Int) : OvenControl

    /** Turn on / Turn off, with the knobs as they are. */
    data object Toggle : OvenControl

    /** Fahrenheit or Celsius: the game's own option. */
    data class Units(val celsius: Boolean) : OvenControl
}

/**
 * One oven or microwave. [temperature] and [timer] are the knobs' values (the game's own numbers;
 * [temperatureText] is what the dial says there, "400 °F"); [left] the minutes still to go while it
 * runs. [container]: its id in the containers around, for what's inside. [celsius] is null on a
 * microwave (no degrees).
 */
data class OvenState(
    val gone: Boolean = false,
    val name: String = "",
    val icon: String? = null,
    val microwave: Boolean = false,
    val on: Boolean = false,
    val powered: Boolean = false,
    val broken: Boolean = false,
    val container: String? = null,
    val temperature: Int = 0,
    val temperatureText: String = "",
    val celsius: Boolean? = null,
    val timer: Int = 0,
    val left: Int? = null,
    val dial: String? = null,
    val temperatureDial: Knob = Knob(null, emptyList()),
    val timerDial: Knob = Knob(null, emptyList()),
)

/** A knob: the game's dial art behind it, and where it stops ([marks], in the game's order). */
data class Knob(val art: String?, val marks: List<KnobMark>) {
    /** The marks from lowest to highest value: the order ‹ › step through. */
    val byValue: List<KnobMark> = marks.sortedBy { it.value }

    /** The marks round the dial, clockwise from the top: how a finger finds one. */
    val byAngle: List<KnobMark> = marks.sortedBy { it.angle }

    /** The mark showing [value]; between marks (the time left), the nearest below the halfway point, as the game's knob. */
    fun markFor(value: Int): KnobMark? {
        val sorted = byValue
        marks.firstOrNull { it.value == value }?.let { return it }
        for (i in sorted.lastIndex downTo 1) {
            val prev = sorted[i - 1].value
            if (value >= prev + (sorted[i].value - prev) / 2f) return sorted[i]
        }
        return sorted.firstOrNull()
    }
}

/** Where the knob points ([angle], degrees clockwise from the top) for [value]; [label] what the dial says there. */
data class KnobMark(val angle: Float, val value: Int, val label: String? = null)

/** "450 °F" for an oven's dial, the text as it is for a microwave's ("4 of 5"). */
fun OvenState.withDegrees(text: String) = celsius?.let { "$text °${if (it) "C" else "F"}" } ?: text
