package dev.zomboidds.companion.domain

/**
 * More of the game's own windows on the bottom screen: its yes/no questions ([GameEvent.ShowQuestion]),
 * its info windows ([GameEvent.ShowInfo]) and its exercise window (opened from the Status tab).
 * Each read never throws.
 */
interface GameWindows {
    /** The question [id], or [Question.gone] once it was answered (here, or with the controller). */
    suspend fun question(id: Long): Fetched<Question>

    /** Presses one of its buttons ([QuestionButton.id]). */
    suspend fun answer(id: Long, button: String): CommandResult

    /** The info window the game opened, or [InfoCard.gone] once the game would close it. */
    suspend fun infoCard(): Fetched<InfoCard>

    suspend fun closeInfo()

    suspend fun exercise(): Fetched<ExerciseState>

    suspend fun startExercise(id: String, minutes: Int): CommandResult

    suspend fun stopExercise(): CommandResult
}

/** One of the game's yes/no (or OK) questions: its words and its buttons, Yes / OK first. */
data class Question(val gone: Boolean = false, val id: Long = 0, val text: String = "", val buttons: List<QuestionButton> = emptyList())

data class QuestionButton(val id: String, val label: String)

/**
 * What one of the game's info windows says: [rows] are its lines, either a [label] with a [value]
 * (and a [meter] for a percentage or a plant's water) or plain [text]; [bad] for red ones; [icon]
 * for a plant's pests; [indent] for what belongs to the row above.
 */
data class InfoCard(
    val gone: Boolean = false,
    val kind: String = "",
    val title: String = "",
    val icon: String? = null,
    val status: String? = null,
    val rows: List<InfoRow> = emptyList(),
)

data class InfoRow(
    val label: String? = null,
    val value: String? = null,
    val text: String? = null,
    val meter: Float? = null,
    val bad: Boolean = false,
    val icon: String? = null,
    val indent: Boolean = false,
    val water: Boolean = false,
)

/**
 * The game's exercise window: its exercises, whether Start is open (and [why] not), the minutes
 * it allows, and the one [running] now.
 */
data class ExerciseState(
    val exercises: List<ExerciseType> = emptyList(),
    val canStart: Boolean = false,
    val why: String? = null,
    val minutes: IntProgression = 10..60 step 10,
    val running: RunningExercise? = null,
)

/** [needs]: the game's "(Requires Barbell)" when the item isn't on you; [regularity] 0–1, the window's bar. */
data class ExerciseType(
    val id: String,
    val name: String,
    val tooltip: String? = null,
    val icon: String? = null,
    val needs: String? = null,
    val regularity: Float = 0f,
    val strain: List<String> = emptyList(),
)

data class RunningExercise(val id: String, val minutes: Int, val done: Int)
