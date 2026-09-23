package dev.zomboidds.companion.domain

/** Crafting as the game's crafting window offers it (recipes you know, with what's in reach). */
interface Crafting {
    /** What the game's crafting window lists now, and whether each can be made. Never throws. */
    suspend fun recipes(): Fetched<RecipeList>

    /** What a recipe ([RecipeSummary.id]) needs and makes, and how often it can be made now. Never throws. */
    suspend fun recipe(id: String): Fetched<RecipeDetails>

    /** Crafts it [count] times (at most [RecipeDetails.max]), like the window's Craft button. Never throws. */
    suspend fun craft(id: String, count: Int): CommandResult
}

/** Something asked of the game: what it sent, or why there's nothing. */
sealed interface Fetched<out T> {
    data class Ready<T>(val value: T) : Fetched<T>

    data class Failed(val reason: String) : Fetched<Nothing>
}

data class RecipeList(val recipes: List<RecipeSummary>, val categories: List<RecipeCategory>)

data class RecipeSummary(val id: String, val name: String, val icon: String?, val category: String?, val canCraft: Boolean)

/** [id] as the recipes name it ("Tailoring"), [name] as the game shows it. */
data class RecipeCategory(val id: String, val name: String)

data class RecipeDetails(
    val id: String,
    val name: String,
    val icon: String?,
    val category: String?,
    /** How long one takes, as the game's crafting window says it. */
    val seconds: Int?,
    val canCraft: Boolean,
    /** How many times it can be made with what's in reach. */
    val max: Int,
    val inputs: List<RecipeInput>,
    val outputs: List<RecipeOutput>,
    val skills: List<RecipeSkill>,
)

/**
 * An ingredient: [name] and [icon] of the item the player has for it (else one that would do),
 * [others] how many other items would do too; [keep]: a tool, not used up; [unit] "L" for fluids.
 */
data class RecipeInput(
    val name: String,
    val icon: String?,
    val need: Float,
    val have: Float,
    val ok: Boolean,
    val keep: Boolean = false,
    val others: Int = 0,
    val unit: String? = null,
)

data class RecipeOutput(val name: String, val icon: String?, val amount: Float, val unit: String? = null)

data class RecipeSkill(val name: String, val level: Int, val have: Int) {
    val ok: Boolean get() = have >= level
}
