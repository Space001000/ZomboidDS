package dev.zomboidds.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.BodyPartStatus
import dev.zomboidds.companion.domain.HealthTone

/**
 * The game's own body silhouette (its health panel's `bps_male_*` / `bps_female_*` images, one per
 * body part on the same canvas, plus an outline), with each hurt part tinted by its most urgent line.
 */
@Composable
fun BodySilhouette(parts: List<BodyPartStatus>, female: Boolean, iconUrl: (String) -> String, modifier: Modifier = Modifier) {
    val prefix = if (female) "bps_female_" else "bps_male_"
    val tones = parts.associate { it.id to it.tone }
    Box(modifier) {
        IMAGES.forEach { (partId, image) ->
            AsyncImage(
                model = iconUrl(prefix + image),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(bodyColor(tones[partId]), BlendMode.SrcIn),
                modifier = Modifier.fillMaxSize(),
            )
        }
        AsyncImage(model = iconUrl(prefix + "outlines"), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/** The game's body part ids (BodyPartType) and their silhouette images. */
private val IMAGES = listOf(
    "Head" to "head", "Neck" to "neck", "Torso_Upper" to "chest", "Torso_Lower" to "abdomen", "Groin" to "groin",
    "UpperArm_L" to "upper-left-arm", "UpperArm_R" to "upper-right-arm",
    "ForeArm_L" to "lower-left-arm", "ForeArm_R" to "lower-right-arm",
    "Hand_L" to "left-hand", "Hand_R" to "right-hand",
    "UpperLeg_L" to "left-thigh", "UpperLeg_R" to "right-thigh",
    "LowerLeg_L" to "left-calf", "LowerLeg_R" to "right-calf",
    "Foot_L" to "left-foot", "Foot_R" to "right-foot",
)

private fun bodyColor(tone: HealthTone?) = when (tone) {
    HealthTone.BAD -> Color(0xFFD23737)
    HealthTone.WARN -> Color(0xFFE8873A)
    HealthTone.GOOD -> Color(0xFF5ABE5A)
    else -> Color(0xFF4F4943) // unhurt: warm grey, like the panels
}
