package dev.zomboidds.companion.domain

/**
 * What to call each container on screen, by id. Names the game repeats ("Shelves" four times in one
 * room) get numbers in the game's order ("Shelves 1", "Shelves 2", ...), so tabs and "Move to"
 * buttons can be told apart; unique names stay as they are.
 */
fun List<Container>.labels(): Map<String, String> {
    val counts = groupingBy { it.name }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return associate { container ->
        val label = if (counts.getValue(container.name) > 1) {
            val n = seen.merge(container.name, 1, Int::plus)
            "${container.name} $n"
        } else {
            container.name
        }
        container.id to label
    }
}
