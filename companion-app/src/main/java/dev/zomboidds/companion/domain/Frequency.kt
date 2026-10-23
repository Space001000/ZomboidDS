package dev.zomboidds.companion.domain

/**
 * Radio frequencies as the game keeps them (kHz, 93200) and shows them (MHz with one decimal,
 * "93.2"), and the number pad's typing.
 */
object Frequency {
    /** "93.2" for 93200. */
    fun format(khz: Int): String = "${khz / 1000}.${(khz % 1000) / 100}"

    /** The typed MHz text as kHz, or null when it isn't a number yet. */
    fun parse(text: String): Int? {
        if (text.isEmpty() || text == ".") return null
        val whole = text.substringBefore('.').ifEmpty { "0" }.toIntOrNull() ?: return null
        val tenth = text.substringAfter('.', "").take(1).ifEmpty { "0" }.toIntOrNull() ?: return null
        return whole * 1000 + tenth * 100
    }

    /**
     * [text] after pressing [key] ("0".."9", "." or "⌫"): at most four digits before the point and
     * one after it, like the game's 0.1 MHz steps.
     */
    fun type(text: String, key: String): String = when (key) {
        "⌫" -> text.dropLast(1)
        "." -> if ('.' in text) text else text.ifEmpty { "0" } + "."
        else -> {
            val point = text.indexOf('.')
            when {
                key.length != 1 || !key[0].isDigit() -> text
                point >= 0 && text.length - point > 1 -> text // already one decimal
                point < 0 && text.length >= 4 -> text
                text == "0" -> key
                else -> text + key
            }
        }
    }
}
