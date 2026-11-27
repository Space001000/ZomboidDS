package dev.zomboidds.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zomboidds.companion.domain.Skill
import dev.zomboidds.companion.domain.Skills

/**
 * Status ▾ Skills: the game's Skills tab as readable rows, grouped like the game (Passive first).
 * Each row: the name in the game's colour for its XP boost, ten boxes in the game's own art with the
 * level in progress partly filled, the level, and arrows while a skill book multiplies the XP. Tap a
 * row for the game's tooltip line: XP towards the next level and the multiplier.
 */
@Composable
fun SkillsScreen(skills: Skills, iconUrl: (String) -> String) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        skills.groups.forEach { group ->
            item(key = "g:" + group.id) {
                Text(group.name, Modifier.padding(top = 6.dp, bottom = 2.dp), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(group.skills, key = { "s:" + group.id + ":" + it.id }) { skill ->
                SkillRow(skill, iconUrl, skill.id == open) { open = if (open == skill.id) null else skill.id }
            }
        }
    }
}

@Composable
private fun SkillRow(skill: Skill, iconUrl: (String) -> String, open: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (open) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(skill.name, Modifier.width(130.dp), color = boostColor(skill.boost), maxLines = 1, overflow = TextOverflow.Ellipsis)
            SkillBoxes(skill, iconUrl)
            Text("${skill.level}", Modifier.width(30.dp), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(start = 8.dp)) {
                if (skill.multiplier != null) repeat(3) { AsyncImage(iconUrl("ArrowRight"), null, Modifier.size(10.dp)) }
            }
        }
        if (open) {
            val parts = buildList {
                add("Level ${skill.level}")
                if (skill.xp != null && skill.xpNeeded != null) add("%,d / %,d XP".format(skill.xp, skill.xpNeeded))
                skill.multiplier?.let { add("Multiplier: " + "%.2f".format(it).trimEnd('0').trimEnd('.')) }
            }
            Text(parts.joinToString(" · "), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ten boxes, drawn like the game's skill bar: gold for levels gained, grey filling for the one in progress. */
@Composable
private fun SkillBoxes(skill: Skill, iconUrl: (String) -> String) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        for (i in 0 until 10) {
            val gained = i < skill.level
            val current = i == skill.level
            Box(Modifier.size(18.dp)) {
                if (gained) UnitImage(iconUrl("SkillPanel/SkillUnit_fill"), Gained)
                if (current && skill.progress > 0f) {
                    Box(Modifier.fillMaxSize().padding(end = (18 * (1 - skill.progress)).dp).clipToBounds()) {
                        UnitImage(iconUrl("SkillPanel/SkillUnit_fill"), Progress, ContentScale.FillHeight, Alignment.CenterStart)
                    }
                }
                UnitImage(iconUrl("SkillPanel/SkillUnit_border"), if (gained) Gained else if (current) Progress else Locked)
            }
        }
    }
}

@Composable
private fun UnitImage(url: String, tint: Color, scale: ContentScale = ContentScale.Fit, alignment: Alignment = Alignment.Center) {
    AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = scale, alignment = alignment,
        colorFilter = ColorFilter.tint(tint, BlendMode.Modulate))
}

// The game's colours (ISSkillProgressBar): gained levels, the one in progress, locked ones.
private val Gained = Color(1f, 0.89f, 0.38f)
private val Progress = Color(0.4f, 0.4f, 0.4f)
private val Locked = Color(0.2f, 0.2f, 0.2f)

/** The game colours a skill's name by its XP boost from profession and traits (ISCharacterInfo). */
private fun boostColor(boost: Int) = when (boost) {
    0 -> Color(0.54f, 0.54f, 0.54f)
    1 -> Color(0.8f, 0.8f, 0.8f)
    3 -> Color(1f, 0.83f, 0f)
    else -> Color.White
}
