package dev.zomboidds.companion.domain

/**
 * Newspapers, magazines and flyers as the game's reader shows them ([GameEvent.ShowPrint]): the
 * printed page, its text version, and buttons for the places it mentions.
 */
interface PrintMediaReader {
    /** What the player just read. Never throws. */
    suspend fun printMedia(): Fetched<PrintedPage>

    /** The window's map button [index] (1–5): the place known on the map, the game's map opened there. */
    suspend fun showOnMap(index: Int): CommandResult

    suspend fun closePrint()
}

/** [page] is the game's layout of it; [text] its text version; [places] how many map buttons it has. */
data class PrintedPage(val title: String, val icon: String?, val page: PageLayout, val text: String, val places: Int)

/** A printed page at the game's own size ([width] × [height] page pixels), its [parts] drawn in order. */
data class PageLayout(val width: Float, val height: Float, val parts: List<PagePart>) {
    companion object {
        private val ELEMENT = Regex("<([^>]*)>([^<]*)")

        /**
         * The game's page description (Print_Media *_info, read by PZAPI.UI.PrintMedia): elements like
         * `<type:texture, x:20, y:20, width:375, height:105, r:0, g:0, b:0, a:0.9>` or
         * `<type:text, x:35, y:35, scaleX:0.75, font:SdfOldBold>Rangers triumph^in West Point`.
         */
        fun parse(info: String): PageLayout {
            var width = 800f
            var height = 900f
            val parts = mutableListOf<PagePart>()
            for (match in ELEMENT.findAll(info)) {
                val params = match.groupValues[1].split(',').mapNotNull { p ->
                    val i = p.indexOf(':')
                    if (i < 0) null else p.substring(0, i).trim() to p.substring(i + 1).trim()
                }.toMap()
                fun num(key: String, default: Float) = params[key]?.toFloatOrNull() ?: default
                val color = PageColor(num("r", 1f), num("g", 1f), num("b", 1f), num("a", 1f))
                when (params["type"]) {
                    "parent" -> {
                        width = num("width", width)
                        height = num("height", height)
                    }
                    "texture" -> parts += PagePart.Picture(
                        x = num("x", 0f), y = num("y", 0f), width = num("width", 0f), height = num("height", 0f),
                        pivotX = num("pivotX", 0f), pivotY = num("pivotY", 0f), angle = num("angle", 0f),
                        scaleX = num("scaleX", 1f), scaleY = num("scaleY", 1f), color = color,
                        texture = params["texture"]?.let(::textureName),
                    )
                    "text" -> parts += PagePart.Words(
                        x = num("x", 0f), y = num("y", 0f), pivotX = num("pivotX", 0f),
                        scale = num("scaleY", num("scaleX", 1f)), leading = num("textLeading", 0f),
                        wrapWidth = params["autoWidth"]?.toFloatOrNull()?.let { it * num("scaleX", 1f) },
                        font = PageFont.of(params["font"]), color = color,
                        text = match.groupValues[2].replace('^', '\n'),
                    )
                }
            }
            return PageLayout(width, height, parts)
        }

        /** "media/textures/printMedia/logos/kk.png" as the icon endpoint knows it: "printMedia/logos/kk". */
        fun textureName(path: String) = path.removePrefix("media/textures/").removePrefix("media/ui/").removeSuffix(".png")
    }
}

data class PageColor(val r: Float, val g: Float, val b: Float, val a: Float)

sealed interface PagePart {
    /** A picture ([texture]) or, without one, a plain rectangle in [color]; placed at its pivot, turned [angle]°. */
    data class Picture(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val pivotX: Float, val pivotY: Float, val angle: Float, val scaleX: Float, val scaleY: Float,
        val color: PageColor, val texture: String?,
    ) : PagePart

    /** Text in the game's newspaper type at [scale] of its 32 px size; [wrapWidth] in page pixels, or one line each. */
    data class Words(
        val x: Float, val y: Float, val pivotX: Float, val scale: Float, val leading: Float,
        val wrapWidth: Float?, val font: PageFont, val color: PageColor, val text: String,
    ) : PagePart
}

enum class PageFont(val bold: Boolean, val italic: Boolean) {
    REGULAR(false, false), BOLD(true, false), ITALIC(false, true), BOLD_ITALIC(true, true);

    companion object {
        fun of(name: String?) = when (name) {
            "SdfOldBold" -> BOLD
            "SdfOldBoldItalic" -> BOLD_ITALIC
            "SdfOldItalic" -> ITALIC
            else -> REGULAR
        }
    }
}
