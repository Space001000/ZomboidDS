package dev.zomboidds.companion.domain

/**
 * TVs, radios, walkie-talkies, CD players and car radios as the game's Device Options window shows
 * them. The game opens one device at a time ([GameEvent.ShowDevice]); the panel reads it with
 * [deviceState] and works it with [deviceControl], each control the game's own action.
 */
interface Devices {
    /** The device the game opened, or [DeviceState.gone] once the player walked away. Never throws. */
    suspend fun deviceState(): Fetched<DeviceState>

    suspend fun deviceControl(control: DeviceControl): CommandResult

    /** The panel closed: the game forgets the device. */
    suspend fun closeDevice()
}

/** What the panel can do with the device, as the game's window does it. */
sealed interface DeviceControl {
    data object Power : DeviceControl

    /** [level] 0–1, in the game's ten steps. */
    data class Volume(val level: Float) : DeviceControl

    data object Mute : DeviceControl

    /** Back to [level], the volume it had before muting. */
    data class Unmute(val level: Float) : DeviceControl

    /** Tune in to [frequency] (a TV channel or a radio frequency in kHz). A playing tape stops first. */
    data class Channel(val frequency: Int) : DeviceControl

    /** Play or stop the tape or CD. */
    data object Play : DeviceControl

    data object Eject : DeviceControl

    data object BatteryOut : DeviceControl

    data object HeadphonesOut : DeviceControl

    /** Something from the drawer goes in (a tape, CD, battery, headphones); what's in that slot comes out first. */
    data class Insert(val itemId: Long) : DeviceControl

    data class Microphone(val muted: Boolean) : DeviceControl

    /** Save [frequency] as a preset, named after the station there. */
    data class AddPreset(val frequency: Int) : DeviceControl
}

/**
 * One device, with only the parts it has: a [battery] or mains power ([grid]), [headphones] on
 * portable radios, a [range] and presets to tune on radios (a TV's [presets] are its channels), a
 * microphone on [twoWay] radios, a [media] slot on CD players and TVs with a VCR. The [drawer] has
 * what fits it, on the player and in the containers around.
 */
data class DeviceState(
    val gone: Boolean = false,
    val name: String = "",
    val icon: String? = null,
    val where: DeviceWhere? = null,
    val on: Boolean = false,
    val tv: Boolean = false,
    val battery: DeviceBattery? = null,
    val grid: GridPower? = null,
    /** 0–1, the game's ten steps; 0 is muted. */
    val volume: Float = 0f,
    val channel: Int? = null,
    val channelName: String? = null,
    val presets: List<Preset> = emptyList(),
    /** A radio's tuning range in kHz; null on a TV. */
    val range: IntRange? = null,
    val maxPresets: Int? = null,
    val headphones: HeadphoneSlot? = null,
    val twoWay: TwoWay? = null,
    val media: MediaSlot? = null,
    val drawer: List<DrawerGroup> = emptyList(),
) {
    /** Power to play: the switch, and a battery with charge or electricity. */
    val powered: Boolean
        get() = battery?.let { it.has && it.level > 0f } ?: (grid?.power == true)

    /** The preset (or TV channel) it's tuned to, unless a tape or CD is playing instead. */
    val tunedPreset: Preset?
        get() = if (media?.playing == true) null else presets.firstOrNull { it.frequency == channel }
}

enum class DeviceWhere { HAND, BACK, FLOOR, VEHICLE }

/** [level] 0–1 of the battery in it; [has] false when there's none. */
data class DeviceBattery(val level: Float, val has: Boolean)

/** Mains power: [text] is the game's "Has power." / "No power.". */
data class GridPower(val power: Boolean, val text: String)

/** A saved station, or a TV channel. [frequency] in kHz for radios (93200 = 93.2 MHz). */
data class Preset(val name: String, val frequency: Int)

/** The headphone slot; [name] and [icon] of what's in it, or null when empty. */
data class HeadphoneSlot(val name: String? = null, val icon: String? = null)

/** A two-way radio: how far it transmits (metres) and whether its microphone is muted. */
data class TwoWay(val range: Int?, val micMuted: Boolean)

/** The CD or VHS slot: [name] of what's in it, [empty] the game's "- NO TAPE -" / "- NO DISC -". */
data class MediaSlot(val kind: MediaKind, val name: String? = null, val playing: Boolean = false, val empty: String = "")

enum class MediaKind { CD, VHS }

/** Things that fit, on the player ([onYou]) or in one container around ([name]). */
data class DrawerGroup(val name: String?, val onYou: Boolean, val items: List<DrawerItem>)

/** One row: [count] of the same kind (batteries); [id] is the one that goes in (the fullest battery). */
data class DrawerItem(val id: Long, val name: String, val icon: String?, val fits: DrawerFit, val count: Int = 1)

enum class DrawerFit { MEDIA, BATTERY, HEADPHONES }
