package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** How long a panel's error line stays. */
private const val MESSAGE_MS = 4_000L

/**
 * A full-height panel from the bottom over a dimmed tab, like the device panel: the game window
 * panels (oven, exercise, reader) use it. Tapping the dim calls [onDismiss]; taps on the panel stay
 * on it. Drawn in the app's window, no popup (nothing takes the controller's focus).
 */
@Composable
internal fun BoxScope.SheetPanel(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
    )
    Surface(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 460.dp).fillMaxHeight()
            .clickable(interactionSource = null, indication = null, onClick = {}),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** An error line that clears itself after a few seconds: set `.value` to show one. */
@Composable
internal fun rememberTransientMessage(): MutableState<String?> {
    val message = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message.value) {
        if (message.value != null) {
            delay(MESSAGE_MS)
            message.value = null
        }
    }
    return message
}

/** A round ‹ or › that steps a value one mark (the oven's dials, the exercise minutes). */
@Composable
internal fun RoundStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).alpha(if (enabled) 1f else 0.38f).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
    }
}
