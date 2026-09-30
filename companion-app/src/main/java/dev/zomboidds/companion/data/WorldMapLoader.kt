package dev.zomboidds.companion.data

import android.content.Context
import android.util.Log
import dev.zomboidds.companion.domain.WorldMap
import dev.zomboidds.companion.setup.SetupController
import dev.zomboidds.companion.setup.ZomdroidStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WorldMapState {
    data object NotLoaded : WorldMapState
    data object Loading : WorldMapState
    class Loaded(val map: WorldMap) : WorldMapState
    data class Failed(val reason: String) : WorldMapState
}

/**
 * Reads the game's map from the player's own copy of the game in Zomdroid (we ship no game
 * files): `instances/<instance>/game/media/maps/Muldraugh, KY/`. Loaded once, on first use.
 */
class WorldMapLoader(private val context: Context, private val scope: CoroutineScope, private val setup: SetupController) {

    private val _state = MutableStateFlow<WorldMapState>(WorldMapState.NotLoaded)
    val state: StateFlow<WorldMapState> = _state.asStateFlow()

    /** Reads the map unless it's read or being read; tries again after a failure (e.g. access granted since). */
    fun load() {
        if (_state.value == WorldMapState.Loading || _state.value is WorldMapState.Loaded) return
        _state.value = WorldMapState.Loading
        scope.launch(Dispatchers.IO) {
            _state.value = runCatching { read() }.fold(
                onSuccess = { WorldMapState.Loaded(it) },
                onFailure = {
                    Log.w(TAG, "could not read the game's map", it)
                    WorldMapState.Failed(it.message ?: it.javaClass.simpleName)
                },
            )
        }
    }

    private fun read(): WorldMap {
        val started = System.currentTimeMillis()
        val storage = ZomdroidStorage.find(context.contentResolver) ?: error("no access to Zomdroid yet")
        val instance = setup.selectedInstance(storage) ?: error("no Zomdroid game instance found")
        val dir = storage.path(instance.id, "game", "media", "maps", MAP_FOLDER)
            ?: error("the game's map folder is missing")
        val files = MAP_FILES.map { name ->
            val file = storage.child(dir.id, name) ?: error("$name is missing")
            WorldMapBinary.read(storage.readBytes(file.id))
        }
        val map = WorldMap(WorldMapBinary.CELL_SIZE, WorldMapBinary.merge(*files.toTypedArray()))
        Log.i(TAG, "read ${map.cellCount} map cells in ${System.currentTimeMillis() - started} ms")
        return map
    }

    private companion object {
        const val TAG = "WorldMapLoader"
        const val MAP_FOLDER = "Muldraugh, KY"
        val MAP_FILES = listOf("worldmap-forest.xml.bin", "worldmap.xml.bin")
    }
}
