package dev.zomboidds.companion.domain

/**
 * The shapes the game's minimap draws, in its draw order (`MapUtils.initDefaultStyleV1` in
 * `ISMapDefinitions.lua`): a feature belongs to the first layer whose property it has.
 * A [value] of null matches any value (the game's "*").
 */
enum class MapLayer(val key: String, val value: String?) {
    FOREST("natural", "forest"),
    WATER("water", "river"),
    TRAIL("highway", "trail"),
    TERTIARY("highway", "tertiary"),
    SECONDARY("highway", "secondary"),
    PRIMARY("highway", "primary"),
    RAILWAY("railway", null),
    BUILDING("building", "yes"),
    RESIDENTIAL("building", "Residential"),
    COMMUNITY_SERVICES("building", "CommunityServices"),
    HOSPITALITY("building", "Hospitality"),
    INDUSTRIAL("building", "Industrial"),
    MEDICAL("building", "Medical"),
    RESTAURANTS("building", "RestaurantsAndEntertainment"),
    RETAIL("building", "RetailAndCommercial"),
    ;

    companion object {
        /** The layer a feature with these properties is drawn on, or null if the minimap doesn't draw it. */
        fun of(properties: Map<String, String>): MapLayer? =
            entries.firstOrNull { layer -> properties[layer.key]?.let { layer.value == null || it == layer.value } == true }
    }
}

/**
 * One polygon. [rings] hold x,y pairs in tiles relative to the cell's corner: the outline first,
 * then any holes.
 */
class MapFeature(val layer: MapLayer, val rings: List<ShortArray>)

/** A square of [WorldMap.cellSize] tiles; cell (x, y) starts at tile (x * cellSize, y * cellSize). */
class MapCell(val x: Int, val y: Int, val features: List<MapFeature>)

/**
 * Where the player (or the car they're in) is, in tiles. [heading]: degrees clockwise from east,
 * as the map's y axis points down (90 = south); null while unknown. [miniMapAllowed] and
 * [worldMapAllowed]: the save's sandbox Map options. [alwaysShow]: the player ticked "Map on every
 * save" in the game's mod options.
 */
data class MapPosition(
    val x: Float,
    val y: Float,
    val z: Int,
    val heading: Int?,
    val miniMapAllowed: Boolean,
    val worldMapAllowed: Boolean,
    val alwaysShow: Boolean = false,
) {
    /**
     * Whether the app shows the map: where the save allows the game's minimap, or everywhere if the
     * player asked for it. Never on a save without the world map: there the game has no map at all.
     */
    val shown: Boolean get() = worldMapAllowed && (miniMapAllowed || alwaysShow)
}

/**
 * The parts of the world the player has seen, as the game's map remembers them: [width] x [height]
 * units of [unit] tiles from tile ([originX], [originY]), two bits per unit (visited, known), four
 * units per byte. The minimap shows a unit when either bit is set.
 */
class ExploredAreas(
    val originX: Int,
    val originY: Int,
    val unit: Int,
    val width: Int,
    val height: Int,
    private val bits: ByteArray,
) {
    /** Whether the unit containing tile ([x], [y]) has been seen. Outside the world: no. */
    fun isSeen(x: Int, y: Int): Boolean = isUnitSeen(Math.floorDiv(x - originX, unit), Math.floorDiv(y - originY, unit))

    fun isUnitSeen(ux: Int, uy: Int): Boolean {
        if (ux < 0 || uy < 0 || ux >= width || uy >= height) return false
        val index = ux / 4 + uy * (width / 4)
        if (index >= bits.size) return false
        return (bits[index].toInt() shr ((ux % 4) * 2)) and 3 != 0
    }
}

/** The game's world map, as read from its map files. */
class WorldMap(val cellSize: Int, cells: Collection<MapCell>) {

    private val byPosition: Map<Long, MapCell> = cells.associateBy { key(it.x, it.y) }

    val cellCount: Int get() = byPosition.size

    fun cell(x: Int, y: Int): MapCell? = byPosition[key(x, y)]

    private fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
}
