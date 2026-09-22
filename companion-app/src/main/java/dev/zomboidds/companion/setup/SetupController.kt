package dev.zomboidds.companion.setup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import dev.zomboidds.companion.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * Checks whether everything the mod needs is in place and performs the fixes the user asks for
 * (install the mod, enable it for new games). All file work happens off the main thread.
 */
class SetupController(private val context: Context, private val scope: CoroutineScope, httpClient: OkHttpClient) {

    private val _report = MutableStateFlow(SetupReport(
        zomdroidInstalled = false, hasAccess = false, bundledModVersion = BuildConfig.BUNDLED_MOD_VERSION))
    val report: StateFlow<SetupReport> = _report.asStateFlow()

    private val prefs = context.getSharedPreferences("setup", Context.MODE_PRIVATE)
    private val mutex = Mutex() // one check or action at a time
    private val zombieBuddy = ZombieBuddyFetcher(context, httpClient)

    fun refresh() = perform(busy = null) { }

    /** Called with the folder the user picked. If it's not Zomdroid's top folder, the report says so. */
    fun onFolderPicked(tree: Uri) = perform(busy = "Checking access...") {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val valid = tree.authority == ZomdroidStorage.AUTHORITY &&
            ZomdroidStorage(resolver, tree).instances().isNotEmpty()
        check(valid) { "That's not Zomdroid's main folder. Pick 'Zomdroid' in the picker and tap 'Use this folder' right away." }
        // Replace any earlier grant so there's only ever one.
        resolver.persistedUriPermissions.filter { it.uri.authority == ZomdroidStorage.AUTHORITY }
            .forEach { resolver.releasePersistableUriPermission(it.uri, flags) }
        resolver.takePersistableUriPermission(tree, flags)
    }

    fun selectInstance(name: String) = perform(busy = null) {
        prefs.edit().putString(KEY_INSTANCE, name).apply()
    }

    /** Downloads ZombieBuddy from its GitHub and saves the zip for Zomdroid's installer to Downloads. */
    fun getZombieBuddy() = perform(busy = "Downloading ZombieBuddy...") {
        prefs.edit().putString(KEY_ZOMBIE_BUDDY_DOWNLOAD, zombieBuddy.fetchToDownloads()).apply()
    }

    fun installMod() = performOnInstance(busy = "Installing the mod...") { storage, instance ->
        context.assets.open(MOD_ASSET).use { ModInstaller(storage, context.contentResolver).install(it, instance) }
    }

    /** Adds ZombieBuddy and ZomboidDS to the main menu's mod list, which new games start with. */
    fun enableForNewGames() = performOnInstance(busy = "Enabling the mod...") { storage, instance ->
        val mods = storage.modsDir(instance) ?: error("no mods folder")
        val file = storage.child(mods.id, DEFAULT_MOD_LIST)?.id ?: storage.createFile(mods.id, DEFAULT_MOD_LIST)
        val text = storage.readText(file)
        storage.writeText(file, ModList.withMods(text, listOf(ZOMBIE_BUDDY, ModInstaller.MOD_NAME)))
    }

    private fun performOnInstance(busy: String?, action: (ZomdroidStorage, ZomdroidStorage.Entry) -> Unit) =
        perform(busy) {
            val storage = ZomdroidStorage.find(context.contentResolver) ?: error("no access to Zomdroid yet")
            val instance = selectedInstance(storage) ?: error("no Zomdroid game instance found")
            action(storage, instance)
        }

    /** Runs [action] (if any), then re-checks everything and publishes the new report. */
    private fun perform(busy: String?, action: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (busy != null) _report.update { it.copy(busy = busy, error = null) }
                val error = runCatching(action).exceptionOrNull()?.let {
                    Log.w(TAG, "setup action failed", it)
                    it.message ?: it.javaClass.simpleName
                }
                val fresh = runCatching { check() }.getOrElse { e ->
                    _report.value.copy(error = "Could not read Zomdroid's files: ${e.message}")
                }
                if (fresh.zombieBuddy == ZombieBuddyStatus.OK) prefs.edit().remove(KEY_ZOMBIE_BUDDY_DOWNLOAD).apply()
                // A plain refresh (busy == null) keeps the last action's error visible; a new action replaces it.
                val previousError = if (busy == null) _report.value.error else null
                _report.value = fresh.copy(
                    busy = null,
                    error = error ?: fresh.error ?: previousError,
                    zombieBuddyDownload = prefs.getString(KEY_ZOMBIE_BUDDY_DOWNLOAD, null),
                )
            }
        }
    }

    private fun check(): SetupReport {
        val bundled = BuildConfig.BUNDLED_MOD_VERSION
        val installed = isInstalled(ZomdroidStorage.PACKAGE)
        val storage = ZomdroidStorage.find(context.contentResolver)
            ?: return SetupReport(installed, hasAccess = false, bundledModVersion = bundled)

        val instances = storage.instances()
        val instance = selectedInstance(storage)
            ?: return SetupReport(installed, hasAccess = true, instances.map { it.name }, bundledModVersion = bundled)
        val mods = storage.modsDir(instance)

        return SetupReport(
            zomdroidInstalled = installed,
            hasAccess = true,
            instances = instances.map { it.name },
            instance = instance.name,
            zombieBuddy = zombieBuddyStatus(storage, instance, mods),
            installedModVersion = mods?.let { installedModVersion(storage, it) },
            bundledModVersion = bundled,
            enabledForNewGames = mods?.let { dir ->
                storage.child(dir.id, DEFAULT_MOD_LIST)?.let { ModInstaller.MOD_NAME in ModList.enabledMods(storage.readText(it.id)) }
            } ?: false,
        )
    }

    /** The instance the user picked, or the only/first one. */
    private fun selectedInstance(storage: ZomdroidStorage): ZomdroidStorage.Entry? {
        val instances = storage.instances()
        val saved = prefs.getString(KEY_INSTANCE, null)
        return instances.firstOrNull { it.name == saved } ?: instances.firstOrNull()
    }

    private fun zombieBuddyStatus(storage: ZomdroidStorage, instance: ZomdroidStorage.Entry, mods: ZomdroidStorage.Entry?): ZombieBuddyStatus {
        // Zomdroid puts the jar in the instance's game folder; the game needs the mod folder too.
        val jar = storage.child(instance.id, "game")?.let { storage.child(it.id, "ZombieBuddy.jar") } != null
        val modFolder = mods?.let { storage.child(it.id, ZOMBIE_BUDDY) } != null
        return when {
            jar && modFolder -> ZombieBuddyStatus.OK
            jar -> ZombieBuddyStatus.JAR_ONLY
            modFolder -> ZombieBuddyStatus.MOD_FOLDER_ONLY
            else -> ZombieBuddyStatus.MISSING
        }
    }

    private fun installedModVersion(storage: ZomdroidStorage, mods: ZomdroidStorage.Entry): String? {
        val modInfo = storage.child(mods.id, ModInstaller.MOD_NAME)
            ?.let { storage.child(it.id, "42") }
            ?.let { storage.child(it.id, "mod.info") } ?: return null
        return storage.readText(modInfo.id).lines()
            .firstOrNull { it.startsWith("modversion=") }?.substringAfter('=')?.trim() ?: "unknown"
    }

    private fun isInstalled(packageName: String) = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val TAG = "ZomboidDS"
        const val KEY_INSTANCE = "instance"
        const val KEY_ZOMBIE_BUDDY_DOWNLOAD = "zombieBuddyDownload"
        const val MOD_ASSET = "ZomboidDS.zip"
        const val DEFAULT_MOD_LIST = "default.txt"
        const val ZOMBIE_BUDDY = "ZombieBuddy"
    }
}
