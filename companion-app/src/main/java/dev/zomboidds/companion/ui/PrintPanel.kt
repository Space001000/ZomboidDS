package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import dev.zomboidds.companion.R
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.CommandResult
import dev.zomboidds.companion.domain.Fetched
import dev.zomboidds.companion.domain.PageColor
import dev.zomboidds.companion.domain.PageLayout
import dev.zomboidds.companion.domain.PagePart
import dev.zomboidds.companion.domain.PrintMediaReader
import dev.zomboidds.companion.domain.PrintedPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The game's page font is Old Standard TT at 32 px with 41 px lines at scale 1 (SdfOld*.fnt); shipped as res/font (OFL). */
private val OldStandard = FontFamily(
    Font(R.font.old_standard_regular, FontWeight.Normal),
    Font(R.font.old_standard_bold, FontWeight.Bold),
    Font(R.font.old_standard_italic, FontWeight.Normal, FontStyle.Italic),
)

private const val FONT_PX = 32f
private const val LINE_PX = 41f
private const val MAX_ZOOM = 4f

/**
 * A newspaper, magazine or flyer just read, as the game's reader shows it: the page across the panel
 * (drag to move, pinch to zoom), **Text** for its text version, and **Show on map** for the places it
 * mentions. Stays until closed, like the game's window.
 */
@Composable
internal fun BoxScope.PrintPanel(reader: PrintMediaReader, iconUrl: (String) -> String, onClose: () -> Unit) {
    var printed by remember { mutableStateOf<Fetched<PrintedPage>?>(null) }
    var showText by remember { mutableStateOf(false) }
    var message by rememberTransientMessage()
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { reader.closePrint() }
        onClose()
    }
    LaunchedEffect(Unit) { printed = reader.printMedia() }
    SheetPanel(onDismiss = close) {
            when (val current = printed) {
                null -> LoadingRow("Reading...")
                is Fetched.Failed -> FailedRow(current.reason, "Close", close)
                is Fetched.Ready -> {
                    val p = current.value
                    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameIcon(p.icon, iconUrl, 36.dp)
                        Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        // The window's map buttons: one per place it mentions.
                        for (i in 1..p.places) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val result = reader.showOnMap(i)
                                    if (result is CommandResult.Failed) message = result.reason
                                }
                            }) {
                                GameIcon("Item_Map", iconUrl, 22.dp)
                                Text(if (p.places == 1) " Show on map" else " $i")
                            }
                        }
                        if (showText) {
                            FilledTonalButton(onClick = { showText = false }) { Text("Page") }
                        } else {
                            OutlinedButton(onClick = { showText = true }) { Text("Text") }
                        }
                        TextButton(onClick = close) { Text("Close") }
                    }
                    message?.let { Text(it, color = ErrorText, style = MaterialTheme.typography.bodySmall) }
                    if (showText) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(p.title, fontFamily = OldStandard, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(p.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        PageView(p.page, iconUrl, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
    }
}

/**
 * The page at the panel's width; drag moves it, pinch zooms (up to [MAX_ZOOM]). Zoom and position are
 * read only in the draw layer, so a gesture redraws the page without recomposing its parts.
 */
@Composable
private fun PageView(layout: PageLayout, iconUrl: (String) -> String, modifier: Modifier) {
    var zoom by remember(layout) { mutableFloatStateOf(1f) }
    var pan by remember(layout) { mutableStateOf(Offset.Zero) }
    // The box's size, set on layout (it rarely changes); the gesture has its own (`size`).
    var box by remember { mutableStateOf(Size.Zero) }
    // Keep the page on screen: centred when it's narrower than the box, else no gap at its edges.
    fun clamp(p: Offset, z: Float, box: Size): Offset {
        val s = box.width / layout.width * z
        val w = layout.width * s
        val h = layout.height * s
        return Offset(if (w <= box.width) (box.width - w) / 2 else p.x.coerceIn(box.width - w, 0f),
            if (h <= box.height) 0f else p.y.coerceIn(box.height - h, 0f))
    }
    val density = LocalDensity.current
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF0D0C0B)).clipToBounds()
            .onSizeChanged { box = it.toSize() }
            .pointerInput(layout) {
                detectTransformGestures { centroid, move, pinch, _ ->
                    val z = (zoom * pinch).coerceIn(1f, MAX_ZOOM)
                    // Zoom about the fingers, then move with them.
                    val f = z / zoom
                    pan = clamp(Offset(centroid.x - (centroid.x - pan.x) * f, centroid.y - (centroid.y - pan.y) * f) + move, z,
                        size.toSize())
                    zoom = z
                }
            },
    ) {
        Box(
            Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(with(density) { layout.width.toDp() }, with(density) { layout.height.toDp() })
                .graphicsLayer {
                    val s = box.width / layout.width * zoom
                    val at = clamp(pan, zoom, box)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = s
                    scaleY = s
                    translationX = at.x
                    translationY = at.y
                },
        ) {
            layout.parts.forEach { part ->
                when (part) {
                    is PagePart.Picture -> PagePicture(part, iconUrl)
                    is PagePart.Words -> PageWords(part)
                }
            }
        }
    }
}

private fun PageColor.toColor() = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a.coerceIn(0f, 1f))

/** A picture, or a plain rectangle; placed by its pivot, turned and flipped like the game draws it. */
@Composable
private fun PagePicture(part: PagePart.Picture, iconUrl: (String) -> String) {
    val density = LocalDensity.current
    val m = Modifier.offset { IntOffset((part.x - part.pivotX * part.width).roundToInt(), (part.y - part.pivotY * part.height).roundToInt()) }
        .size(with(density) { part.width.toDp() }, with(density) { part.height.toDp() })
        .graphicsLayer {
            transformOrigin = TransformOrigin(part.pivotX, part.pivotY)
            rotationZ = part.angle
            scaleX = part.scaleX
            scaleY = part.scaleY
        }
    val texture = part.texture
    if (texture == null) {
        Box(m.background(part.color.toColor()))
    } else {
        val c = part.color
        AsyncImage(
            model = iconUrl(texture), contentDescription = null, contentScale = ContentScale.FillBounds, alpha = c.a,
            colorFilter = if (c.r < 1f || c.g < 1f || c.b < 1f) ColorFilter.tint(Color(c.r, c.g, c.b), BlendMode.Modulate) else null,
            modifier = m,
        )
    }
}

/** Text in the page's type, Old Standard TT, placed by its pivot. */
@Composable
private fun PageWords(part: PagePart.Words) {
    val density = LocalDensity.current
    val style = with(density) {
        TextStyle(
            color = part.color.toColor(), fontFamily = OldStandard,
            fontWeight = if (part.font.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (part.font.italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = (FONT_PX * part.scale).toSp(), lineHeight = ((LINE_PX + part.leading) * part.scale).toSp(),
        )
    }
    Text(
        part.text, style = style, softWrap = part.wrapWidth != null,
        modifier = Modifier.offset { IntOffset(part.x.roundToInt(), part.y.roundToInt()) }
            .layout { measurable, _ ->
                val wrap = part.wrapWidth
                val placeable = measurable.measure(
                    if (wrap != null) Constraints.fixedWidth(wrap.roundToInt()) else Constraints(),
                )
                layout(placeable.width, placeable.height) { placeable.place(-(part.pivotX * placeable.width).roundToInt(), 0) }
            },
    )
}
