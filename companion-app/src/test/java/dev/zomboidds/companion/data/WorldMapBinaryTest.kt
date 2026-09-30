package dev.zomboidds.companion.data

import dev.zomboidds.companion.domain.MapLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WorldMapBinaryTest {

    /** Writes the game's map format (see [WorldMapBinary]) for a 2x1-cell map. */
    private class MapFile(strings: List<String>) {
        private val out = ByteArrayOutputStream()
        private val index = strings.withIndex().associate { it.value to it.index }

        init {
            out.write("IGMB".toByteArray())
            int(2); int(256); int(2); int(1)
            int(strings.size)
            strings.forEach { short(it.length); out.write(it.toByteArray()) }
        }

        fun int(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun short(v: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())

        fun noCell() = int(-1)

        fun cell(x: Int, y: Int, featureCount: Int) { int(x); int(y); int(featureCount) }

        fun feature(type: String, rings: List<List<Int>>, properties: Map<String, String>) {
            short(index.getValue(type))
            out.write(rings.size)
            rings.forEach { ring -> short(ring.size / 2); ring.forEach(::short) }
            out.write(properties.size)
            properties.forEach { (k, v) -> short(index.getValue(k)); short(index.getValue(v)) }
        }

        fun bytes(): ByteArray = out.toByteArray()
    }

    private val square = listOf(0, 0, 10, 0, 10, 10, 0, 10)

    @Test
    fun `reads the polygons the minimap draws and drops the rest`() {
        val file = MapFile(listOf("Polygon", "LineString", "building", "Medical", "RoomTone", "Clinic", "highway", "primary", "place", "town"))
        file.cell(1, 0, 4)
        file.feature("Polygon", listOf(square, listOf(2, 2, 4, 2, 4, 4)), mapOf("building" to "Medical", "RoomTone" to "Clinic"))
        file.feature("LineString", listOf(square), mapOf("highway" to "primary"))
        file.feature("Polygon", listOf(square), mapOf("place" to "town"))
        file.feature("Polygon", listOf(square), mapOf("highway" to "primary"))
        file.noCell()

        val cells = WorldMapBinary.read(file.bytes())

        assertEquals(1, cells.size)
        val cell = cells.single()
        assertEquals(1 to 0, cell.x to cell.y)
        assertEquals(listOf(MapLayer.MEDICAL, MapLayer.PRIMARY), cell.features.map { it.layer })
        val clinic = cell.features.first()
        assertEquals(2, clinic.rings.size)
        assertEquals(square, clinic.rings[0].map { it.toInt() })
    }

    @Test
    fun `a railway of any kind is drawn`() {
        assertEquals(MapLayer.RAILWAY, MapLayer.of(mapOf("railway" to "*")))
        assertEquals(MapLayer.RAILWAY, MapLayer.of(mapOf("railway" to "abandoned")))
        assertEquals(null, MapLayer.of(mapOf("building" to "Unknown")))
    }

    @Test
    fun `cells of the town and forest files are joined`() {
        val town = MapFile(listOf("Polygon", "water", "river"))
        town.cell(0, 0, 1); town.feature("Polygon", listOf(square), mapOf("water" to "river")); town.noCell()
        val forest = MapFile(listOf("Polygon", "natural", "forest"))
        forest.cell(0, 0, 1); forest.feature("Polygon", listOf(square), mapOf("natural" to "forest"))
        forest.cell(1, 0, 1); forest.feature("Polygon", listOf(square), mapOf("natural" to "forest"))

        val cells = WorldMapBinary.merge(WorldMapBinary.read(forest.bytes()), WorldMapBinary.read(town.bytes()))

        assertEquals(2, cells.size)
        assertEquals(listOf(MapLayer.FOREST, MapLayer.WATER), cells.first { it.x == 0 }.features.map { it.layer })
    }

    @Test
    fun `other files are refused`() {
        assertThrows(IOException::class.java) { WorldMapBinary.read("PK\u0003\u0004 not a map".toByteArray()) }
    }
}
