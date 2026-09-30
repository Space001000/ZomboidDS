package dev.zomboidds.companion.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.MapPlacement
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.ExploredAreas
import dev.zomboidds.companion.domain.GameState
import dev.zomboidds.companion.domain.MapCell
import dev.zomboidds.companion.domain.MapLayer
import dev.zomboidds.companion.domain.MapPosition
import dev.zomboidds.companion.domain.MapSymbol
import dev.zomboidds.companion.domain.WorldMap
import dev.zomboidds.companion.domain.gameZoom
import dev.zomboidds.companion.domain.pixelsPerTileAt
import kotlinx.coroutines.delay
import kotlin.math.floor

/** The map, once the game's map files are read, where the user wants it, and whether it shows their symbols. */
class MapDisplay(
    val map: WorldMap,
    val placement: MapPlacement,
    val onPlacementChange: (MapPlacement) -> Unit,
    val showSymbols: Boolean = false,
    val onShowSymbolsChange: (Boolean) -> Unit = {},
)

/**
 * The map around the player. Until the game reports where they are (or with an older mod), it
 * looks at Muldraugh's centre without a marker.
 */
@Composable
fun PlayerMiniMap(
    display: MapDisplay,
    state: GameState,
    onPlacementChange: (MapPlacement) -> Unit,
    iconUrl: (String) -> String,
    modifier: Modifier = Modifier,
) = MiniMap(
    display.map, state.mapPosition, state.explored, display.placement, onPlacementChange, modifier,
    symbols = if (display.showSymbols) state.mapSymbols else emptyList(),
    showSymbols = display.showSymbols, onShowSymbolsChange = display.onShowSymbolsChange, iconUrl = iconUrl,
)

/** Where the map is looking: a tile position and a zoom in pixels per tile. */
private data class MapView(val x: Float, val y: Float, val scale: Float)

/**
 * The game's minimap, drawn from its own map data in its own colours, with the areas the player
 * hasn't seen greyed out as the game does ([explored]; everything shows while that's unknown).
 * Follows [position] unless the user drags it; it returns after a few seconds, or with ⌖.
 */
@Composable
fun MiniMap(
    map: WorldMap,
    position: MapPosition?,
    explored: ExploredAreas?,
    placement: MapPlacement,
    onPlacementChange: (MapPlacement) -> Unit,
    modifier: Modifier = Modifier,
    symbols: List<MapSymbol> = emptyList(),
    showSymbols: Boolean = false,
    onShowSymbolsChange: ((Boolean) -> Unit)? = null,
    iconUrl: (String) -> String = { it },
) {
    var scale by remember { mutableFloatStateOf(DEFAULT_SCALE) }
    // Zoom limits in the game's zoom levels, so they cover the same area in any size of view.
    var heightPx by remember { mutableFloatStateOf(0f) }
    fun limited(s: Float): Float =
        if (heightPx <= 0f) s else s.coerceIn(pixelsPerTileAt(MIN_GAME_ZOOM, heightPx), pixelsPerTileAt(MAX_GAME_ZOOM, heightPx))
    val target = position?.let { Offset(it.x, it.y) } ?: MULDRAUGH
    // The game reports about four times a second: glide between reports instead of jumping.
    // A long way (the first report, a teleport) jumps.
    val glide = remember { Animatable(target, Offset.VectorConverter) }
    LaunchedEffect(target) {
        if ((target - glide.value).getDistance() > JUMP_TILES) glide.snapTo(target)
        else glide.animateTo(target, tween(GLIDE_MS, easing = LinearEasing))
    }
    val player = glide.value
    val currentPlayer by rememberUpdatedState(player)
    // The arrow turns the short way round (350 to 10 degrees is 20, not 340).
    val heading = position?.heading
    val turn = remember { Animatable(heading?.toFloat() ?: 0f) }
    LaunchedEffect(heading) {
        if (heading != null) {
            val from = turn.value
            turn.animateTo(from + ((heading - from) % 360 + 540) % 360 - 180, tween(TURN_MS))
        }
    }
    // Set while the user has dragged the map away from the player.
    var looking by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(looking) {
        if (looking != null) {
            delay(LOOK_AROUND_MS)
            looking = null
        }
    }
    val center = looking ?: player
    val paths = remember(map) { HashMap<Long, LayerPaths>() }
    val mask = remember(explored) { explored?.takeIf { it.width > 0 && it.height > 0 }?.let(::unexploredMask) }

    Box(modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged {
                heightPx = it.height.toFloat()
                scale = limited(scale)
            }.pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val from = looking ?: currentPlayer
                    looking = from - drag / scale
                }
            },
        ) {
            val view = MapView(center.x, center.y, scale)
            drawMap(map, view, paths)
            if (explored != null && mask != null) drawUnexplored(explored, mask, view)
            if (position != null) {
                drawPlayer(Offset(size.width / 2 + (player.x - center.x) * scale, size.height / 2 + (player.y - center.y) * scale),
                    if (heading != null) turn.value else null)
            }
        }
        Symbols(symbols, center, scale, iconUrl)
        PlacementButton(placement, onPlacementChange, Modifier.align(Alignment.TopStart).padding(8.dp))
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundMapButton("+") { scale = limited(scale * ZOOM_STEP) }
            RoundMapButton("−") { scale = limited(scale / ZOOM_STEP) }
            if (onShowSymbolsChange != null) SymbolsButton(showSymbols, iconUrl) { onShowSymbolsChange(!showSymbols) }
            if (looking != null) RoundMapButton("⌖") { looking = null }
        }
    }
}

private fun DrawScope.drawMap(map: WorldMap, view: MapView, cache: HashMap<Long, LayerPaths>) {
    drawRect(MapColors.background)
    val cell = map.cellSize
    val halfW = size.width / 2 / view.scale
    val halfH = size.height / 2 / view.scale
    val x0 = floor((view.x - halfW) / cell).toInt()
    val x1 = floor((view.x + halfW) / cell).toInt()
    val y0 = floor((view.y - halfH) / cell).toInt()
    val y1 = floor((view.y + halfH) / cell).toInt()
    val visible = buildList { for (cy in y0..y1) for (cx in x0..x1) map.cell(cx, cy)?.let(::add) }
    val zoom = gameZoom(view.scale, size.height)

    withTransform({
        translate(size.width / 2, size.height / 2)
        scale(view.scale, view.scale, pivot = Offset.Zero)
        translate(-view.x, -view.y)
    }) {
        // Layer by layer across all cells, so a road in one cell never covers a house in the next.
        for (layer in MapLayer.entries) {
            // Like the game's minimap, detail fades out as you zoom out (and isn't built at all then).
            val alpha = MapColors.alpha(layer, zoom)
            if (alpha <= 0f) continue
            val color = MapColors.of(layer).copy(alpha = alpha)
            for (c in visible) {
                val paths = cache.getOrPut(key(c, layer)) { LayerPaths.of(c, layer, cell) }
                paths.solid?.let { drawPath(it, color) }
                paths.holed?.let { drawPath(it, color) }
            }
        }
    }
}

/**
 * Grey over every unit the player hasn't seen, with the game's faint grid on it. The grey is one
 * pixel per unit in [mask], stretched over the map; the grid only while units are big enough.
 */
private fun DrawScope.drawUnexplored(explored: ExploredAreas, mask: ImageBitmap, view: MapView) {
    val unit = explored.unit
    withTransform({
        translate(size.width / 2, size.height / 2)
        scale(view.scale, view.scale, pivot = Offset.Zero)
        translate(-view.x, -view.y)
    }) {
        drawImage(
            mask,
            dstOffset = IntOffset(explored.originX, explored.originY),
            dstSize = IntSize(explored.width * unit, explored.height * unit),
            filterQuality = FilterQuality.None,
        )
    }
    val unitPx = unit * view.scale
    if (unitPx < GRID_MIN_UNIT_PX) return
    val halfW = size.width / 2 / view.scale
    val halfH = size.height / 2 / view.scale
    val ux0 = Math.floorDiv(floor(view.x - halfW).toInt() - explored.originX, unit)
    val ux1 = Math.floorDiv(floor(view.x + halfW).toInt() - explored.originX, unit)
    val uy0 = Math.floorDiv(floor(view.y - halfH).toInt() - explored.originY, unit)
    val uy1 = Math.floorDiv(floor(view.y + halfH).toInt() - explored.originY, unit)
    val hairline = 1.dp.toPx().coerceAtMost(unitPx / 8)
    for (uy in uy0..uy1) {
        for (ux in ux0..ux1) {
            if (explored.isUnitSeen(ux, uy)) continue
            val topLeft = Offset(
                size.width / 2 + (explored.originX + ux * unit - view.x) * view.scale,
                size.height / 2 + (explored.originY + uy * unit - view.y) * view.scale,
            )
            drawRect(MapColors.unexploredGrid, topLeft, Size(unitPx, unitPx), style = Stroke(hairline))
        }
    }
}

/** The unexplored grey as a bitmap: one pixel per unit, transparent where the player has been. */
private fun unexploredMask(explored: ExploredAreas): ImageBitmap {
    val pixels = IntArray(explored.width * explored.height)
    val grey = MapColors.unexplored.toArgb()
    for (uy in 0 until explored.height) {
        for (ux in 0 until explored.width) {
            if (!explored.isUnitSeen(ux, uy)) pixels[uy * explored.width + ux] = grey
        }
    }
    return Bitmap.createBitmap(pixels, explored.width, explored.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun key(cell: MapCell, layer: MapLayer): Long =
    ((cell.x.toLong() * 4096 + cell.y) shl 8) or layer.ordinal.toLong()

/**
 * A cell's shapes on one layer, in tile coordinates. Shapes with holes get their own even-odd
 * path; the rest share one path.
 */
private class LayerPaths(val solid: Path?, val holed: Path?) {
    companion object {
        fun of(cell: MapCell, layer: MapLayer, cellSize: Int): LayerPaths {
            val ox = cell.x * cellSize.toFloat()
            val oy = cell.y * cellSize.toFloat()
            var solid: Path? = null
            var holed: Path? = null
            for (feature in cell.features) {
                if (feature.layer != layer) continue
                val target = if (feature.rings.size > 1) {
                    holed ?: Path().apply { fillType = PathFillType.EvenOdd }.also { holed = it }
                } else {
                    solid ?: Path().also { solid = it }
                }
                for (ring in feature.rings) {
                    if (ring.size < 6) continue
                    target.moveTo(ox + ring[0], oy + ring[1])
                    for (i in 2 until ring.size step 2) target.lineTo(ox + ring[i], oy + ring[i + 1])
                    target.close()
                }
            }
            return LayerPaths(solid, holed)
        }
    }
}

/** An arrow pointing where the player faces ([heading]: degrees clockwise from east); a dot while unknown. */
private fun DrawScope.drawPlayer(at: Offset, heading: Float?) {
    if (heading == null) {
        drawCircle(Color.Black.copy(alpha = 0.6f), 7.dp.toPx(), at)
        drawCircle(MapColors.player, 5.5.dp.toPx(), at)
        return
    }
    val s = 7.dp.toPx()
    val arrow = Path().apply {
        moveTo(at.x, at.y - s * 1.3f)
        lineTo(at.x + s, at.y + s)
        lineTo(at.x, at.y + s * 0.4f)
        lineTo(at.x - s, at.y + s)
        close()
    }
    // The arrow is drawn pointing up (north, -90 degrees).
    rotate(heading + 90f, at) {
        drawPath(arrow, Color.Black.copy(alpha = 0.6f), style = Stroke(width = 2.dp.toPx()))
        drawPath(arrow, MapColors.player)
    }
}

/**
 * The game's labels and the player's symbols and notes, at their spots on the map, as the game's
 * minimap draws them: icons tinted in their colour; all text in the game's handwriting (its
 * minimap has no text styles of its own, so every label uses the default: black handwriting, or
 * the player's colour), each only within its zoom levels. They keep their size while zooming.
 */
@Composable
private fun Symbols(symbols: List<MapSymbol>, center: Offset, scale: Float, iconUrl: (String) -> String) {
    if (symbols.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val zoom = gameZoom(scale, height)
        for (symbol in symbols) {
            if (!symbol.isShownAt(zoom)) continue
            val px = width / 2 + (symbol.x - center.x) * scale
            val py = height / 2 + (symbol.y - center.y) * scale
            if (px < -SYMBOL_MARGIN_PX || py < -SYMBOL_MARGIN_PX || px > width + SYMBOL_MARGIN_PX || py > height + SYMBOL_MARGIN_PX) continue
            val size = (symbol.scale / GAME_SYMBOL_SCALE).coerceIn(0.5f, MAX_SYMBOL_SIZE)
            val color = if (symbol.color[3] == 0f) Color.Black
            else Color(symbol.color[0], symbol.color[1], symbol.color[2], symbol.color[3].coerceAtLeast(0.2f))
            // Placed with its anchor point on the spot, then turned around that point, as in the game.
            val placed = Modifier.wrapContentSize(unbounded = true).align(Alignment.TopStart).layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, p.height) {
                    p.placeWithLayer((px - p.width * symbol.anchorX).toInt(), (py - p.height * symbol.anchorY).toInt()) {
                        rotationZ = symbol.rotation
                        transformOrigin = TransformOrigin(symbol.anchorX, symbol.anchorY)
                    }
                }
            }
            if (symbol.icon != null) {
                AsyncImage(iconUrl(symbol.icon), null, placed.size((SYMBOL_ICON_DP * size).dp),
                    colorFilter = ColorFilter.tint(color, BlendMode.Modulate))
            } else if (symbol.text != null) {
                Text(symbol.text, placed, color = color, fontSize = (SYMBOL_TEXT_SP * size).sp, fontFamily = FontFamily.Cursive,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = (SYMBOL_TEXT_SP * size * 1.05f).sp)
            }
        }
    }
}

/** Shows or hides the player's symbols, with the game's own star symbol; off by default like the game's minimap. */
@Composable
private fun SymbolsButton(on: Boolean, iconUrl: (String) -> String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MapColors.buttonBackground)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(iconUrl("LootableMaps/map_star"), if (on) "Hide your map symbols" else "Show your map symbols",
            Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, BlendMode.Modulate))
    }
}

@Composable
private fun RoundMapButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MapColors.buttonBackground)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
}

/** The top-left button: pick where the map sits, like a window layout menu. */
@Composable
private fun PlacementButton(placement: MapPlacement, onChange: (MapPlacement) -> Unit, modifier: Modifier) =
    LayoutPicker(
        "Map position", placement, MapPlacement.entries, { it.label }, { it.parts }, onChange, modifier,
        background = MapColors.buttonBackground, outlined = true,
    )

private val MapPlacement.label get() = when (this) {
    MapPlacement.LEFT_OF_HERE -> "Left of Here"
    MapPlacement.RIGHT_OF_HERE -> "Right of Here"
    MapPlacement.OWN_TAB -> "Own tab"
}

/** The map's part of the screen, for the picker's pictures. */
private val MapPlacement.parts get() = when (this) {
    MapPlacement.LEFT_OF_HERE -> listOf(Rect(0f, 0f, 0.35f, 1f))
    MapPlacement.RIGHT_OF_HERE -> listOf(Rect(0.65f, 0f, 1f, 1f))
    MapPlacement.OWN_TAB -> listOf(Rect(0f, 0f, 1f, 1f))
}

/** The minimap's colours, from `MapUtils.initDefaultStyleV1` (ISMapDefinitions.lua, 42.20). */
private object MapColors {
    val background = Color(219, 215, 192)
    val unexplored = Color(200, 197, 176) // background * 0.915 (setUnvisitedRGBA)
    val unexploredGrid = Color(170, 167, 149) // background * 0.777 (setUnvisitedGridRGBA)
    val player = Color(0xFFE35050)
    val buttonBackground = Color(0xDB121212)

    /**
     * How visible a layer is at the game's [zoom] level: the fills of MapUtils.initDefaultStyleV1
     * (ISMapDefinitions.lua, 42.20) fade detail out as the map zooms out.
     */
    fun alpha(layer: MapLayer, zoom: Float): Float = when (layer) {
        MapLayer.FOREST -> ramp(zoom, 14.5f, 15f)
        MapLayer.TRAIL -> ramp(zoom, 12.25f, 13f)
        MapLayer.TERTIARY -> ramp(zoom, 11.5f, 13f)
        MapLayer.RAILWAY -> if (zoom >= 14f) 1f else 0f
        MapLayer.BUILDING, MapLayer.RESIDENTIAL, MapLayer.COMMUNITY_SERVICES, MapLayer.HOSPITALITY,
        MapLayer.INDUSTRIAL, MapLayer.MEDICAL, MapLayer.RESTAURANTS, MapLayer.RETAIL -> ramp(zoom, 13f, 13.5f)
        MapLayer.WATER, MapLayer.SECONDARY, MapLayer.PRIMARY -> 1f
    }

    private fun ramp(zoom: Float, from: Float, to: Float) = ((zoom - from) / (to - from)).coerceIn(0f, 1f)

    fun of(layer: MapLayer): Color = when (layer) {
        MapLayer.FOREST -> Color(189, 197, 163)
        MapLayer.WATER -> Color(59, 141, 149)
        MapLayer.TRAIL -> Color(185, 122, 87)
        MapLayer.TERTIARY -> Color(171, 158, 143)
        MapLayer.SECONDARY, MapLayer.PRIMARY -> Color(134, 125, 113)
        MapLayer.RAILWAY -> Color(200, 191, 231)
        MapLayer.BUILDING, MapLayer.RESIDENTIAL -> Color(210, 158, 105)
        MapLayer.COMMUNITY_SERVICES -> Color(139, 117, 235)
        MapLayer.HOSPITALITY -> Color(127, 206, 225)
        MapLayer.INDUSTRIAL -> Color(56, 54, 53)
        MapLayer.MEDICAL -> Color(229, 128, 151)
        MapLayer.RESTAURANTS -> Color(245, 225, 60)
        MapLayer.RETAIL -> Color(184, 205, 84)
    }
}

/** The game's default size for a symbol (ISMap.SCALE); drawn at the sizes below. */
private const val GAME_SYMBOL_SCALE = 0.666f
private const val SYMBOL_ICON_DP = 18f
private const val SYMBOL_TEXT_SP = 13f

/** The game's big labels (the Ohio River is 3.0) would cover the small map. */
private const val MAX_SYMBOL_SIZE = 2f

/** Below this size (pixels) the unexplored grid would be a grey haze: left out. */
private const val GRID_MIN_UNIT_PX = 8f

/** Symbols this far outside the map (pixels) are skipped. */
private const val SYMBOL_MARGIN_PX = 200f

/** Until the game reports the player's position. */
private val MULDRAUGH = Offset(10745f, 9960f)

/** Gliding from one reported position to the next; about the time between reports. */
private const val GLIDE_MS = 250
private const val TURN_MS = 200

/** Further than this (tiles) between two reports is a jump, not a walk or a drive. */
private const val JUMP_TILES = 60f

/** How long the map stays where the user dragged it before following the player again. */
private const val LOOK_AROUND_MS = 8_000L

private const val DEFAULT_SCALE = 3f // pixels per tile
/**
 * How far the map zooms, in the game's zoom levels: in as far as the game's minimap (its
 * setMaxZoom(20)); out to about 850 tiles top to bottom, a town and its surroundings, so it stays
 * a minimap. (The game's town and river names only show from zoom 13 out, so not here.)
 */
private const val MIN_GAME_ZOOM = 15.5f
private const val MAX_GAME_ZOOM = 20f
private const val ZOOM_STEP = 1.5f
