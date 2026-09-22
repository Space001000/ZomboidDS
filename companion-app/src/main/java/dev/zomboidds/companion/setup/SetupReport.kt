package dev.zomboidds.companion.setup

/** Result of checking everything the mod needs, as shown in the setup checklist. */
data class SetupReport(
    val zomdroidInstalled: Boolean,
    val hasAccess: Boolean,
    val instances: List<String> = emptyList(),
    val instance: String? = null,
    val zombieBuddy: ZombieBuddyStatus = ZombieBuddyStatus.UNKNOWN,
    val installedModVersion: String? = null,
    val bundledModVersion: String,
    /** Whether ZomboidDS is in the main menu's mod list (what new games start with). */
    val enabledForNewGames: Boolean? = null,
    /** Name of the ZombieBuddy zip this app saved to Downloads, waiting to be installed in Zomdroid. */
    val zombieBuddyDownload: String? = null,
    /** An action in progress, e.g. "Installing the mod...". */
    val busy: String? = null,
    /** The last action's failure, shown until the next check. */
    val error: String? = null,
) {
    val modUpToDate: Boolean get() = installedModVersion == bundledModVersion

    /** Everything is in place; only starting the game (with the mod enabled) is left. */
    val ready: Boolean
        get() = zomdroidInstalled && hasAccess && instance != null &&
            zombieBuddy == ZombieBuddyStatus.OK && modUpToDate && enabledForNewGames == true
}

enum class ZombieBuddyStatus {
    UNKNOWN,
    MISSING,

    /** Zomdroid installed only the jar: the GitHub release has no mod folder, so mods requiring it can't be enabled. */
    JAR_ONLY,

    /** The mod folder is there but Zomdroid doesn't have the jar (not installed through Zomdroid's ZombieBuddy option). */
    MOD_FOLDER_ONLY,
    OK,
}
