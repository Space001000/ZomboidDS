package dev.zomboidds.companion.setup

import android.content.ContentResolver
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Installs the ZomboidDS mod into a Zomdroid instance's `Zomboid/mods/` folder.
 *
 * The zip holds one top-level `ZomboidDS/` folder. It's unpacked into a staging folder first and
 * only swapped in when complete, so an interrupted install never leaves a half-written mod behind.
 */
class ModInstaller(private val storage: ZomdroidStorage, private val resolver: ContentResolver) {

    data class Result(val instance: String, val files: Int)

    fun install(zip: InputStream, instance: ZomdroidStorage.Entry): Result {
        val mods = storage.modsDir(instance) ?: error("no Zomboid/mods folder in instance '${instance.name}'")

        storage.child(mods.id, STAGING_NAME)?.let { storage.delete(it.id) } // leftover from an interrupted install
        val staging = storage.createDirectory(mods.id, STAGING_NAME)

        var files = 0
        val directories = mutableMapOf("" to staging) // relative path -> document id
        ZipInputStream(zip).use { entries ->
            while (true) {
                val entry = entries.nextEntry ?: break
                val path = modRelativePath(entry.name) ?: continue
                if (entry.isDirectory) {
                    directory(directories, path)
                } else {
                    val parent = directory(directories, path.substringBeforeLast('/', ""))
                    val file = storage.createFile(parent, path.substringAfterLast('/'))
                    resolver.openOutputStream(storage.uri(file), "w")!!.use { entries.copyTo(it) }
                    files++
                }
            }
        }
        check(files > 0) { "the mod archive is empty" }

        storage.child(mods.id, MOD_NAME)?.let { storage.delete(it.id) }
        storage.rename(staging, MOD_NAME)
        return Result(instance.name, files)
    }

    /** Path inside the mod folder, or null for entries outside `ZomboidDS/`. Rejects `..` (zip slip). */
    private fun modRelativePath(entryName: String): String? {
        val name = entryName.replace('\\', '/').trimEnd('/')
        if (!name.startsWith("$MOD_NAME/")) return null
        val path = name.removePrefix("$MOD_NAME/")
        require(path.split('/').none { it == ".." || it.isEmpty() }) { "unsafe path in archive: $entryName" }
        return path
    }

    /** Creates (or reuses) the directory for [path] and its parents. */
    private fun directory(known: MutableMap<String, String>, path: String): String =
        known.getOrPut(path) {
            val parent = directory(known, path.substringBeforeLast('/', ""))
            storage.createDirectory(parent, path.substringAfterLast('/'))
        }

    companion object {
        const val MOD_NAME = "ZomboidDS"
        private const val STAGING_NAME = "$MOD_NAME.installing"
    }
}
