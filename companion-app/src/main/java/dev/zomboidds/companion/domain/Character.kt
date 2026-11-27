package dev.zomboidds.companion.domain

/**
 * The game's Info tab (its character window). [professionIcon] is the profession's icon (none for
 * Unemployed); [beard] is null for female characters; [survived] is null while the game's clock
 * hides the date.
 */
data class CharacterInfo(
    val name: String,
    val profession: String?,
    val professionIcon: String?,
    val weight: Int?,
    val weightTrend: WeightTrend?,
    val traits: List<Trait>,
    val hair: String?,
    val canChangeHair: Boolean,
    val beard: String?,
    val canChangeBeard: Boolean,
    val favouriteWeapon: String?,
    val kills: Int?,
    val survived: String?,
)

enum class WeightTrend { UP, UP_LOT, DOWN }

/** A trait as the game's Info tab shows it: its icon, and on hover its name and description. */
data class Trait(val id: String, val name: String, val description: String?, val icon: String?, val tone: TraitTone)

/** GOOD: the player paid points for it; BAD: it gave points; NEUTRAL: from the profession. */
enum class TraitTone { GOOD, BAD, NEUTRAL }

/** Which of the game's Change buttons: hair or beard. */
enum class StyleKind { HAIR, BEARD }

/** The game's Skills tab: groups (Passive first), each with its skills. */
data class Skills(val groups: List<SkillGroup>)

data class SkillGroup(val id: String, val name: String, val skills: List<Skill>)

/**
 * A skill at [level] (0-10). [xp] of [xpNeeded] towards the next level (null at 10). [boost] is the
 * game's XP boost from profession and traits (0-3, it colours the name); [multiplier] the one from
 * skill books (the game's moving arrows), null without one.
 */
data class Skill(
    val id: String,
    val name: String,
    val level: Int,
    val xp: Int?,
    val xpNeeded: Int?,
    val boost: Int,
    val multiplier: Float?,
) {
    val progress: Float get() = if (xp != null && xpNeeded != null && xpNeeded > 0) (xp.toFloat() / xpNeeded).coerceIn(0f, 1f) else 0f
}

/**
 * The game's Temperature tab. [core] in °C; the `Bar` values are where the game's heat bars put
 * their marker (0-1, 0.5 normal). [celsius]: the player's unit option.
 */
data class Temperature(
    val core: Float,
    val coreBar: Float,
    val heatBar: Float,
    val celsius: Boolean,
    val parts: List<PartTemperature>,
) {
    /** The game's hypothermia moodle starts below 36.5 °C, hyperthermia above 37.5 °C. */
    val state: TemperatureState
        get() = when {
            core < 36.5f -> TemperatureState.COLD
            core > 37.5f -> TemperatureState.HOT
            else -> TemperatureState.NORMAL
        }

    private fun average(value: (PartTemperature) -> Float?): Float? =
        parts.mapNotNull(value).takeIf { it.isNotEmpty() }?.average()?.toFloat()

    val insulation: Float? get() = average { it.insulation }
    val insulationBar: Float? get() = average { it.insulationBar }
    val wind: Float? get() = average { it.wind }
    val windBar: Float? get() = average { it.windBar }
    val bodyWet: Float? get() = average { it.bodyWet }
    val clothesWet: Float? get() = average { it.clothesWet }
    val response: Float? get() = average { it.response }

    /** Shown the player's way: "35.6 °C" or "96.1 °F". */
    fun format(celsiusValue: Float): String =
        if (celsius) "%.1f °C".format(celsiusValue) else "%.1f °F".format(celsiusValue * 9 / 5 + 32)
}

enum class TemperatureState { COLD, NORMAL, HOT }

/** One body part on the game's Temperature tab; `Bar` values as its bars draw them (0-1). */
data class PartTemperature(
    val id: String,
    val name: String,
    val skin: Float?,
    val skinBar: Float?,
    val response: Float?,
    val insulation: Float?,
    val insulationBar: Float?,
    val wind: Float?,
    val windBar: Float?,
    val bodyWet: Float?,
    val clothesWet: Float?,
)
