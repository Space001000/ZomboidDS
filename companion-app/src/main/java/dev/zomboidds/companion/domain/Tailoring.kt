package dev.zomboidds.companion.domain

/**
 * Tailoring as the game's Inspect window offers it: the player's clothes with their holes and
 * patches, one garment part by part, and the window's own menu for a part (patch, pad, unpatch).
 */
interface Tailoring {
    /** The clothes in the inventory and bags (worn first) and the sewing kit. Never throws. */
    suspend fun tailorList(): Fetched<TailorList>

    /** One garment as the Inspect window shows it. Never throws. */
    suspend fun garment(itemId: Long): Fetched<Garment>

    /**
     * The Inspect window's menu for one part ([GarmentPart.id]) of a garment. Options run with
     * [ItemActions.selectMenuOption]. Never throws.
     */
    suspend fun garmentMenu(itemId: Long, partId: String): ItemMenuResult
}

data class TailorList(val garments: List<GarmentSummary>, val kit: SewingKit, val tailoring: Int? = null)

/**
 * A garment in the list. [bag]: the bag it's in, when carried in one. [repairable]: false for
 * clothes without a fabric (boots, helmets), which the game can't patch.
 */
data class GarmentSummary(
    val id: Long,
    val name: String,
    val icon: String?,
    val condition: Float? = null,
    val worn: Boolean = false,
    val bag: String? = null,
    val holes: Int = 0,
    val patches: Int = 0,
    val repairable: Boolean = true,
)

/** What the game's menu sews with, found anywhere in the inventory and bags. */
data class SewingKit(val needle: Boolean, val thread: Boolean, val fabrics: List<Fabric>)

/** A fabric the game patches with: its item [name] and how many the player has. */
data class Fabric(val type: String, val name: String, val icon: String?, val count: Int)

/**
 * One garment. [blood] and [dirt] are 0–1 (the window's Overall Bloodiness / Dirtiness);
 * [cantRepair] is the game's "Can't be repaired." for clothes without a fabric.
 */
data class Garment(
    val id: Long,
    val name: String,
    val icon: String?,
    val worn: Boolean = false,
    val condition: Float? = null,
    val blood: Float = 0f,
    val dirt: Float = 0f,
    val cantRepair: String? = null,
    val tailoring: Int? = null,
    val parts: List<GarmentPart> = emptyList(),
) {
    /** The patch being sewn on this garment, if any. */
    val sewing: Sewing? get() = parts.firstNotNullOfOrNull { it.sewing }
}

/**
 * A body part the garment covers, with the defence it gives there (0 over a [hole]). [blood] 0–1
 * when bloody; [patch] is the game's line ("Leather Strips patch"); [sewing] while the player
 * patches or unpatches it.
 */
data class GarmentPart(
    val id: String,
    val name: String,
    val bite: Int = 0,
    val scratch: Int = 0,
    val bullet: Int = 0,
    val hole: Boolean = false,
    val blood: Float? = null,
    val patch: String? = null,
    val sewing: Sewing? = null,
)

/** The game's label for what it's sewing ("Patch Hole") and how far along it is (0–1). */
data class Sewing(val name: String, val progress: Float)
