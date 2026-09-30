package dev.zomboidds.companion.data

import dev.zomboidds.companion.domain.MapCell
import dev.zomboidds.companion.domain.MapFeature
import dev.zomboidds.companion.domain.MapLayer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the game's `worldmap.xml.bin` / `worldmap-forest.xml.bin` (format as read by
 * `zombie.worldMap.WorldMapBinary` in 42.20, little-endian):
 *
 * ```
 * "IGMB", int version (2), int cellSize (256), int width, int height (in cells)
 * int stringCount, each: short length, UTF-8 bytes
 * width * height cells, row by row, each:
 *   int x (-1: no cell, nothing follows), int y, int featureCount, each feature:
 *     short type (string index), byte ringCount, each: short pointCount, pointCount * (short x, short y)
 *     byte propertyCount, each: short key, short value (string indexes)
 * ```
 *
 * Keeps only polygons the minimap draws (see [MapLayer]).
 */
object WorldMapBinary {

    const val CELL_SIZE = 256

    fun read(bytes: ByteArray): List<MapCell> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 4 || bytes.decodeToString(0, 4) != "IGMB") throw IOException("not a world map file")
        val version = buffer.getInt(4)
        if (version != 2) throw IOException("unsupported map file version $version")
        buffer.position(8)
        val cellSize = buffer.int
        if (cellSize != CELL_SIZE) throw IOException("unsupported cell size $cellSize")
        val width = buffer.int
        val height = buffer.int

        val strings = Array(buffer.int) {
            val length = buffer.short.toInt()
            val text = bytes.decodeToString(buffer.position(), buffer.position() + length)
            buffer.position(buffer.position() + length)
            text
        }

        val cells = ArrayList<MapCell>()
        repeat(width * height) {
            val x = buffer.int
            if (x == -1) return@repeat
            val y = buffer.int
            val features = ArrayList<MapFeature>()
            repeat(buffer.int) {
                val type = strings[buffer.short.toInt()]
                val rings = List(buffer.get().toInt() and 0xff) {
                    ShortArray((buffer.short.toInt() and 0xffff) * 2) { buffer.short }
                }
                val properties = HashMap<String, String>()
                repeat(buffer.get().toInt() and 0xff) {
                    val key = strings[buffer.short.toInt()]
                    properties[key] = strings[buffer.short.toInt()]
                }
                val layer = MapLayer.of(properties)
                if (type == "Polygon" && layer != null && rings.isNotEmpty()) features += MapFeature(layer, rings)
            }
            if (features.isNotEmpty()) cells += MapCell(x, y, features)
        }
        return cells
    }

    /** Joins the cells of several files (the town file and the forest file cover the same cells). */
    fun merge(vararg files: List<MapCell>): List<MapCell> =
        files.flatMap { it }.groupBy { it.x to it.y }.map { (position, parts) ->
            MapCell(position.first, position.second, parts.flatMap { it.features })
        }
}
