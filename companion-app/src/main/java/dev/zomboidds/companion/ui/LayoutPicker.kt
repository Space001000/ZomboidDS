package dev.zomboidds.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small button showing the current layout; a tap opens all of them as little screen pictures,
 * like a window layout menu. [parts]: the filled areas of an option's picture, as fractions (0-1)
 * of the screen inside the outline. Used by the map (where it sits) and the inventory (how its
 * containers are laid out).
 */
@Composable
internal fun <T> LayoutPicker(
    title: String,
    current: T,
    options: List<T>,
    label: (T) -> String,
    parts: (T) -> List<Rect>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 40.dp,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    outlined: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier) {
        Box(
            Modifier.size(buttonSize).clip(shape)
                .background(if (open) MaterialTheme.colorScheme.primaryContainer else background)
                .then(
                    if (open) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                    else if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    else Modifier,
                )
                .clickable { open = true }
                .semantics { contentDescription = "$title: ${label(current)}" },
            contentAlignment = Alignment.Center,
        ) { LayoutGlyph(parts(current), Size(buttonSize.value * 0.5f, buttonSize.value * 0.38f)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, properties = NoFocusMenu) {
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { option ->
                        val selected = option == current
                        Column(
                            Modifier.width(68.dp).clip(shape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                                .clickable { open = false; onChange(option) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            LayoutGlyph(parts(option), Size(40f, 28f))
                            Text(label(option), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Menus open in a window of their own, and Material's are focusable: that window would take the
 * controller from the game on the top screen, and once it closed Android would report the app as
 * not responding (the controller's input waits for a focused window the app never has).
 */
internal val NoFocusMenu = PopupProperties(focusable = false)

/** A small screen outline with [parts] filled in. */
@Composable
private fun LayoutGlyph(parts: List<Rect>, sizeDp: Size) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(sizeDp.width.dp, sizeDp.height.dp)) {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(color, topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        val inset = stroke * 2
        val inner = Size(size.width - inset * 2, size.height - inset * 2)
        for (part in parts) {
            drawRect(color, topLeft = Offset(inset + part.left * inner.width, inset + part.top * inner.height),
                size = Size(part.width * inner.width, part.height * inner.height))
        }
    }
}
