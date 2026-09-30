package dev.zomboidds.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zomboidds.companion.MapPlacement
import dev.zomboidds.companion.domain.MapCell
import dev.zomboidds.companion.domain.MapLayer
import dev.zomboidds.companion.domain.WorldMap
import kotlin.math.floor

/** The map, once the game's map files are read, and where the user wants it. */
class MapDisplay(val map: WorldMap, val placement: MapPlacement, val onPlacementChange: (MapPlacement) -> Unit)

/**
 * Where the player is on the map. For now a fixed spot in Muldraugh's centre, until the mod
 * sends the player's position.
 */
@Composable
fun PlayerMiniMap(display: MapDisplay, onPlacementChange: (MapPlacement) -> Unit, modifier: Modifier = Modifier) =
    MiniMap(display.map, 10745f, 9960f, display.placement, onPlacementChange, modifier)

/** Where the map is looking: a tile position and a zoom in pixels per tile. */
private data class MapView(val x: Float, val y: Float, val scale: Float)

/**
 * The game's minimap, drawn from its own map data in its own colours. Follows [playerX]/[playerY]
 * unless the user drags it; ⌖ brings it back.
 */
@Composable
fun MiniMap(
    map: WorldMap,
    playerX: Float,
    playerY: Float,
    placement: MapPlacement,
    onPlacementChange: (MapPlacement) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(DEFAULT_SCALE) }
    // Set while the user has dragged the map away from the player.
    var looking by remember { mutableStateOf<Offset?>(null) }
    val center = looking ?: Offset(playerX, playerY)
    val paths = remember(map) { HashMap<Long, LayerPaths>() }

    Box(modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val from = looking ?: Offset(playerX, playerY)
                    looking = from - drag / scale
                }
            },
        ) {
            drawMap(map, MapView(center.x, center.y, scale), paths)
            drawPlayer(Offset(size.width / 2 + (playerX - center.x) * scale, size.height / 2 + (playerY - center.y) * scale))
        }
        PlacementButton(placement, onPlacementChange, Modifier.align(Alignment.TopStart).padding(8.dp))
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundMapButton("+") { scale = (scale * ZOOM_STEP).coerceAtMost(MAX_SCALE) }
            RoundMapButton("−") { scale = (scale / ZOOM_STEP).coerceAtLeast(MIN_SCALE) }
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

    withTransform({
        translate(size.width / 2, size.height / 2)
        scale(view.scale, view.scale, pivot = Offset.Zero)
        translate(-view.x, -view.y)
    }) {
        // Layer by layer across all cells, so a road in one cell never covers a house in the next.
        for (layer in MapLayer.entries) {
            val color = MapColors.of(layer)
            for (c in visible) {
                val paths = cache.getOrPut(key(c, layer)) { LayerPaths.of(c, layer, cell) }
                paths.solid?.let { drawPath(it, color) }
                paths.holed?.let { drawPath(it, color) }
            }
        }
    }
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

private fun DrawScope.drawPlayer(at: Offset) {
    val s = 7.dp.toPx()
    val arrow = Path().apply {
        moveTo(at.x, at.y - s * 1.3f)
        lineTo(at.x + s, at.y + s)
        lineTo(at.x, at.y + s * 0.4f)
        lineTo(at.x - s, at.y + s)
        close()
    }
    drawPath(arrow, Color.Black.copy(alpha = 0.6f), style = Stroke(width = 2.dp.toPx()))
    drawPath(arrow, MapColors.player)
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
private fun PlacementButton(placement: MapPlacement, onChange: (MapPlacement) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val shape = RoundedCornerShape(8.dp)
        Box(
            Modifier.size(40.dp).clip(shape)
                .background(if (open) MaterialTheme.colorScheme.primaryContainer else MapColors.buttonBackground)
                .border(1.dp, if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { PlacementGlyph(placement, Size(20f, 15f)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Map position", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapPlacement.entries.forEach { option ->
                        val selected = option == placement
                        Column(
                            Modifier.width(64.dp).clip(shape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                                .clickable { open = false; onChange(option) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            PlacementGlyph(option, Size(40f, 28f))
                            Text(option.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

private val MapPlacement.label get() = when (this) {
    MapPlacement.LEFT_OF_HERE -> "Left of Here"
    MapPlacement.RIGHT_OF_HERE -> "Right of Here"
    MapPlacement.OWN_TAB -> "Own tab"
}

/** A small screen outline with the map's part filled in. */
@Composable
private fun PlacementGlyph(placement: MapPlacement, sizeDp: Size) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(sizeDp.width.dp, sizeDp.height.dp)) {
        val stroke = 1.5.dp.toPx()
        val r = CornerRadius(3.dp.toPx())
        drawRoundRect(color, topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke), cornerRadius = r, style = Stroke(stroke))
        val inset = stroke * 2
        val part = (size.width - inset * 2) * 0.35f
        val (left, width) = when (placement) {
            MapPlacement.LEFT_OF_HERE -> inset to part
            MapPlacement.RIGHT_OF_HERE -> size.width - inset - part to part
            MapPlacement.OWN_TAB -> inset to size.width - inset * 2
        }
        drawRect(color, topLeft = Offset(left, inset), size = Size(width, size.height - inset * 2))
    }
}

/** The minimap's colours, from `MapUtils.initDefaultStyleV1` (ISMapDefinitions.lua, 42.20). */
private object MapColors {
    val background = Color(219, 215, 192)
    val player = Color(0xFFE35050)
    val buttonBackground = Color(0xDB121212)

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

private const val DEFAULT_SCALE = 3f // pixels per tile
private const val MIN_SCALE = 0.25f
private const val MAX_SCALE = 16f
private const val ZOOM_STEP = 1.5f
