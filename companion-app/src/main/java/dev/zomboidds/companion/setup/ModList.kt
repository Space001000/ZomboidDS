package dev.zomboidds.companion.setup

/**
 * The game's mod list format, used by `Zomboid/mods/default.txt` (mods enabled at the main menu,
 * which new games start with):
 * ```
 * VERSION = 1,
 *
 * mods
 * {
 *     mod = ZombieBuddy,
 * }
 *
 * maps
 * {
 * }
 * ```
 */
object ModList {

    private val MOD_LINE = Regex("""^\s*mod\s*=\s*(.+?)\s*,?\s*$""")

    fun enabledMods(text: String): List<String> {
        val block = modsBlock(text.lines()) ?: return emptyList()
        return text.lines().subList(block.first + 1, block.last)
            .mapNotNull { MOD_LINE.find(it)?.groupValues?.get(1) }
    }

    /** [text] with [mods] added to the mods block (where missing); everything else is kept as is. */
    fun withMods(text: String, mods: List<String>): String {
        val missing = mods.filter { it !in enabledMods(text) }
        if (missing.isEmpty()) return text
        val newLines = missing.map { "    mod = $it," }

        val lines = text.lines().toMutableList()
        val block = modsBlock(lines)
        if (block == null) {
            val base = if (text.isBlank()) "VERSION = 1,\n" else text.trimEnd() + "\n"
            return base + "\nmods\n{\n" + newLines.joinToString("\n") + "\n}\n"
        }
        lines.addAll(block.last, newLines) // just before the closing brace
        return lines.joinToString("\n")
    }

    /** Line index of the block's `{` (first) and `}` (last), or null if there is no mods block. */
    private fun modsBlock(lines: List<String>): IntRange? {
        val header = lines.indexOfFirst { it.trim() == "mods" }
        if (header < 0) return null
        val open = (header + 1 until lines.size).firstOrNull { lines[it].trim().isNotEmpty() } ?: return null
        if (lines[open].trim() != "{") return null
        val close = (open + 1 until lines.size).firstOrNull { lines[it].trim() == "}" } ?: return null
        return open..close
    }
}
