package dev.zomboidds.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The ZomboidDS logo's colours (docs/logo.svg): cardboard, packing tape, the dark device, blood.
private val Cardboard = Color(0xFF8F6B4D)
private val Tape = Color(0xFFBD9E82)
private val Paper = Color(0xFFD2B393)
private val Ink = Color(0xFF080808)
private val Blood = Color(0xFF8A0303)
private val LogoBack = Color(0xFF121212)

/**
 * The About card's icons, drawn like the logo (they can't be the game's art: that comes from the
 * game, and the About card shows before there is one).
 */
internal enum class AboutIcon { WALKIE, MUG, NEWSPAPER, CODE, LICENCE }

/** [icon] on the logo's dark tile. */
@Composable
internal fun AboutIconTile(icon: AboutIcon, size: Dp = 34.dp) {
    Box(Modifier.size(size).background(LogoBack, RoundedCornerShape(9.dp)).padding(3.dp)) {
        Canvas(Modifier.size(size - 6.dp)) {
            // Drawn on a 64-unit grid, like the mockup's SVGs.
            val u = this.size.width / 64f
            when (icon) {
                AboutIcon.WALKIE -> walkie(u)
                AboutIcon.MUG -> mug(u)
                AboutIcon.NEWSPAPER -> newspaper(u)
                AboutIcon.CODE -> code(u)
                AboutIcon.LICENCE -> licence(u)
            }
        }
    }
}

private fun DrawScope.box(u: Float, x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, alpha: Float = 1f) =
    drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), alpha = alpha)

/** A line of text or grille: a 3-unit rounded bar. */
private fun DrawScope.bar(u: Float, x: Float, y: Float, w: Float, alpha: Float = 0.3f) =
    box(u, x, y, w, 3f, 1.5f, Color.Black, alpha)

private fun DrawScope.walkie(u: Float) {
    box(u, 22f, 4f, 5f, 18f, 2.5f, Tape)
    box(u, 16f, 18f, 32f, 42f, 7f, Cardboard)
    box(u, 16f, 18f, 32f, 3f, 1.5f, Color.White, 0.15f)
    box(u, 22f, 24f, 20f, 11f, 2.5f, Ink, 0.7f)
    box(u, 25f, 28f, 7f, 3f, 1.5f, Tape, 0.9f)
    bar(u, 22f, 40f, 20f)
    bar(u, 22f, 46f, 20f)
    bar(u, 22f, 52f, 20f)
    drawCircle(Blood, 3f * u, Offset(44f * u, 22f * u))
}

private fun DrawScope.mug(u: Float) {
    val steam = Stroke(3f * u, cap = StrokeCap.Round)
    for (x in listOf(24f, 32f)) {
        val wisp = Path().apply { moveTo(x * u, 14f * u); quadraticTo((x - 4f) * u, 9f * u, x * u, 5f * u) }
        drawPath(wisp, Tape, alpha = 0.8f, style = steam)
    }
    val handle = Path().apply {
        moveTo(44f * u, 28f * u); lineTo(48f * u, 28f * u)
        arcTo(Rect(40f * u, 28f * u, 56f * u, 44f * u), -90f, 180f, false)
        lineTo(44f * u, 44f * u)
    }
    drawPath(handle, Cardboard, style = Stroke(6f * u))
    box(u, 12f, 20f, 34f, 38f, 6f, Cardboard)
    drawRect(Tape, Offset(25f * u, 20f * u), Size(9f * u, 38f * u))
    box(u, 14f, 20f, 30f, 5f, 2.5f, Color(0xFF2A1D12))
    drawRect(Color.White, Offset(12f * u, 25f * u), Size(34f * u, 2.5f * u), alpha = 0.12f)
}

private fun DrawScope.newspaper(u: Float) {
    box(u, 8f, 10f, 48f, 44f, 4f, Paper)
    box(u, 8f, 10f, 48f, 3f, 1.5f, Color.White, 0.25f)
    box(u, 13f, 16f, 38f, 8f, 2f, Ink, 0.55f)
    box(u, 13f, 28f, 16f, 12f, 2f, Cardboard)
    bar(u, 33f, 28f, 18f)
    bar(u, 33f, 34f, 18f)
    bar(u, 33f, 40f, 14f)
    bar(u, 13f, 45f, 38f)
}

private fun DrawScope.code(u: Float) {
    box(u, 12f, 10f, 40f, 48f, 5f, Cardboard)
    box(u, 12f, 10f, 40f, 3f, 1.5f, Color.White, 0.15f)
    box(u, 16f, 16f, 32f, 38f, 3f, Paper)
    for (x in listOf(20f, 28f, 36f, 44f)) drawCircle(Ink, 3f * u, Offset(x * u, 10f * u))
    val brackets = Path().apply {
        moveTo(27f * u, 28f * u); lineTo(21f * u, 35f * u); lineTo(27f * u, 42f * u)
        moveTo(37f * u, 28f * u); lineTo(43f * u, 35f * u); lineTo(37f * u, 42f * u)
        moveTo(34f * u, 26f * u); lineTo(30f * u, 44f * u)
    }
    drawPath(brackets, Cardboard, style = Stroke(3.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.licence(u: Float) {
    box(u, 12f, 6f, 38f, 50f, 4f, Paper)
    box(u, 12f, 6f, 38f, 3f, 1.5f, Color.White, 0.25f)
    box(u, 18f, 14f, 26f, 4f, 2f, Ink, 0.5f)
    bar(u, 18f, 23f, 26f, 0.25f)
    bar(u, 18f, 29f, 26f, 0.25f)
    bar(u, 18f, 35f, 18f, 0.25f)
    val ribbon = Path().apply {
        moveTo(38f * u, 52f * u); lineTo(35f * u, 62f * u); lineTo(41f * u, 58f * u)
        lineTo(47f * u, 62f * u); lineTo(44f * u, 52f * u); close()
    }
    drawPath(ribbon, Blood)
    drawCircle(Blood, 8f * u, Offset(41f * u, 48f * u))
    drawCircle(Color.Black, 4.5f * u, Offset(41f * u, 48f * u), alpha = 0.2f)
}
