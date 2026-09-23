package dev.zomboidds.companion

import android.content.Context
import android.util.Log
import dev.zomboidds.companion.setup.ModInstaller
import dev.zomboidds.companion.setup.ZomdroidStorage
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Development helpers, not part of the product: debug builds only (MainActivity). They use the
 * folder permission granted in the app and are triggered over adb (see tools/deploy-mod.sh):
 *
 *   --es devList    "<path below Zomdroid's files dir>"   log a folder's contents
 *   --es devExport  "<path>"                               zip its .class/.jar files to export.zip
 *   --es devInstall "ZomboidDS.zip"                        install the mod zip from our files dir
 *   --ez devReset true                                     undo the wizard (mod folder + default.txt entry)
 *   --ez devRemoveZombieBuddy true                         uninstall ZombieBuddy (jar + mod folder)
 *
 * Files are exchanged through /sdcard/Android/data/dev.zomboidds.companion/files/.
 */
object DevExport {

    private const val TAG = "ZomboidDS-dev"
    private val EXPORTED_EXTENSIONS = listOf(".class", ".jar")

    fun list(context: Context, relativePath: String) = run(context) { storage ->
        storage.children(docId(storage, relativePath)).forEach {
            Log.i(TAG, "${if (it.isDirectory) "d" else "-"} ${it.name}")
        }
        Log.i(TAG, "list done: $relativePath")
    }

    fun cat(context: Context, relativePath: String) = run(context) { storage ->
        val text = context.contentResolver.openInputStream(storage.uri(docId(storage, relativePath)))
            ?.use { it.readBytes().decodeToString() }
        text.orEmpty().lines().forEach { Log.i(TAG, "| $it") }
        Log.i(TAG, "cat done: $relativePath")
    }

    fun export(context: Context, relativePath: String) = run(context) { storage ->
        val out = File(context.getExternalFilesDir(null), "export.zip")
        var count = 0
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun walk(id: String, prefix: String) {
                for (child in storage.children(id)) {
                    val path = prefix + child.name
                    if (child.isDirectory) {
                        if (child.name != "media") walk(child.id, "$path/") // skip game assets
                    } else if (EXPORTED_EXTENSIONS.any { child.name.endsWith(it) }) {
                        zip.putNextEntry(ZipEntry(path))
                        context.contentResolver.openInputStream(storage.uri(child.id))?.use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
                }
            }
            walk(docId(storage, relativePath), "")
        }
        Log.i(TAG, "export done: $count files -> ${out.absolutePath} (${out.length() / 1024} KiB)")
    }

    fun install(context: Context, zipName: String) = run(context) { storage ->
        val zip = File(context.getExternalFilesDir(null), zipName)
        val instance = storage.instances().firstOrNull() ?: error("no Zomdroid game instance found")
        val result = zip.inputStream().use { ModInstaller(storage, context.contentResolver).install(it, instance) }
        Log.i(TAG, "install done: ${result.files} files into instance '${result.instance}'")
    }

    /** Undoes what the setup wizard does (mod folder + main menu mod list entry), to test it from scratch. */
    fun reset(context: Context) = run(context) { storage ->
        val instance = storage.instances().firstOrNull() ?: error("no Zomdroid game instance found")
        val mods = storage.modsDir(instance) ?: error("no mods folder")
        storage.child(mods.id, ModInstaller.MOD_NAME)?.let { storage.delete(it.id) }
        storage.child(mods.id, "default.txt")?.let { file ->
            val kept = storage.readText(file.id).lines().filterNot { it.trim() == "mod = ${ModInstaller.MOD_NAME}," }
            storage.writeText(file.id, kept.joinToString("\n"))
        }
        Log.i(TAG, "reset done")
    }

    /** Removes ZombieBuddy from Zomdroid, to test the "ZombieBuddy missing" setup flow. */
    fun removeZombieBuddy(context: Context) = run(context) { storage ->
        val instance = storage.instances().firstOrNull() ?: error("no Zomdroid game instance found")
        storage.child(instance.id, "game")?.let { game -> storage.child(game.id, "ZombieBuddy.jar")?.let { storage.delete(it.id) } }
        storage.modsDir(instance)?.let { mods -> storage.child(mods.id, "ZombieBuddy")?.let { storage.delete(it.id) } }
        Log.i(TAG, "remove ZombieBuddy done")
    }

    private fun docId(storage: ZomdroidStorage, relativePath: String) =
        if (relativePath.isEmpty()) storage.rootId else "${storage.rootId}/$relativePath" // Zomdroid ids are paths

    private fun run(context: Context, action: (ZomdroidStorage) -> Unit) {
        Thread {
            try {
                val tree = context.contentResolver.persistedUriPermissions.firstOrNull()?.uri
                    ?: error("no folder permission yet: tap 'Grant Zomdroid access' first")
                action(ZomdroidStorage(context.contentResolver, tree))
            } catch (e: Exception) {
                Log.e(TAG, "FAILED: $e", e)
            }
        }.start()
    }
}
