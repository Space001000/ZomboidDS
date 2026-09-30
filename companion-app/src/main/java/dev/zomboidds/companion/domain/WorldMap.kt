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

/** The game's world map, as read from its map files. */
class WorldMap(val cellSize: Int, cells: Collection<MapCell>) {

    private val byPosition: Map<Long, MapCell> = cells.associateBy { key(it.x, it.y) }

    val cellCount: Int get() = byPosition.size

    fun cell(x: Int, y: Int): MapCell? = byPosition[key(x, y)]

    private fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
}
