package dev.zomboidds.companion.domain

/** Building as the game's build window offers it: pick a recipe, then place it with the game's cursor. */
interface Building {
    /** What the game's build window lists now, and whether each can be built. Never throws. */
    suspend fun buildRecipes(): Fetched<BuildList>

    /** What a recipe ([BuildRecipe.id]) needs, with what's in reach. Never throws. */
    suspend fun buildRecipe(id: String): Fetched<RecipeDetails>

    /** Turns on the game's placement cursor for it on the top screen. Never throws. */
    suspend fun place(id: String): CommandResult

    /** Puts the cursor away, as B on the controller does. Never throws. */
    suspend fun stopPlacing(): CommandResult
}

data class BuildList(val recipes: List<BuildRecipe>, val categories: List<RecipeCategory>) {
    /**
     * One entry per thing: its versions (Shoddy, Poor, Good) together, lowest level first, in the
     * order the game lists them.
     */
    val groups: List<BuildGroup> by lazy {
        recipes.groupBy { it.group ?: it.id }.values.map { versions -> BuildGroup(versions.sortedBy { it.level ?: 0 }) }
    }
}

/**
 * A build recipe. Versions of one thing share a [group] (the game's entity name without its level),
 * with their [level] and [version] label ("Poor"); [groupName] is the name without that label.
 * [skill] is the first skill it needs.
 */
data class BuildRecipe(
    val id: String,
    val name: String,
    val icon: String?,
    val category: String?,
    val canBuild: Boolean,
    val group: String? = null,
    val level: Int? = null,
    val version: String? = null,
    val groupName: String? = null,
    val skill: SkillNeed? = null,
)

data class SkillNeed(val name: String, val level: Int)

/** A thing to build in all its versions. */
data class BuildGroup(val versions: List<BuildRecipe>) {
    val first: BuildRecipe get() = versions.first()
    val key: String get() = first.group ?: first.id
    val name: String get() = if (versions.size > 1) first.groupName ?: first.name else first.name
    val icon: String? get() = first.icon
    val category: String? get() = first.category
    val canBuild: Boolean get() = versions.any { it.canBuild }
    val buildable: Int get() = versions.count { it.canBuild }

    /** The version to show first: the best one the player can build, else the first. */
    val preferred: BuildRecipe get() = versions.lastOrNull { it.canBuild } ?: first
}

/**
 * What the player is placing with the game's cursor. [blocked]: the game won't place it now;
 * [missing] names what's short, when known.
 */
data class Placing(val id: String, val name: String, val icon: String?, val blocked: Boolean = false, val missing: List<String> = emptyList())
