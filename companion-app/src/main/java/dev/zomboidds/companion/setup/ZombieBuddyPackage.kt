package dev.zomboidds.companion.setup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Builds the "full" ZombieBuddy zip that Zomdroid's ZombieBuddy installer needs:
 * `ZombieBuddy/{42/, common/, LICENSE.txt, libs/ZombieBuddy.jar}`.
 *
 * ZombieBuddy's GitHub release only has the jar; the mod folder (which the game needs, since mods
 * like ours `require=\ZombieBuddy`) comes from the same tag's source archive.
 */
object ZombieBuddyPackage {

    const val FOLDER = "ZombieBuddy"

    /** The mod's files from a GitHub source archive (`<repo>-<tag>/...`), keyed by path below the root. */
    fun extractModFiles(sourceArchive: InputStream): Map<String, ByteArray> {
        val files = sortedMapOf<String, ByteArray>()
        ZipInputStream(sourceArchive).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val path = entry.name.substringAfter('/', "")
                require(path.split('/').none { it == ".." }) { "unsafe path in archive: ${entry.name}" }
                if (path.startsWith("42/") || path.startsWith("common/") || path == "LICENSE.txt") {
                    files[path] = zip.readBytes()
                }
            }
        }
        return files
    }

    /**
     * Digest over the files' paths and contents (not the archive bytes, which GitHub may
     * recompress). Must match the value in gradle.properties.
     */
    fun modFilesDigest(files: Map<String, ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((path, bytes) in files.toSortedMap()) {
            digest.update(path.encodeToByteArray())
            digest.update(0)
            digest.update(bytes)
            digest.update(0)
        }
        return digest.digest().toHex()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun writeFullZip(jar: File, modFiles: Map<String, ByteArray>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            for ((path, bytes) in modFiles) {
                zip.putNextEntry(ZipEntry("$FOLDER/$path"))
                zip.write(bytes)
                zip.closeEntry()
            }
            // Where the mod's own mod.info expects it (javaJarFile=../libs/ZombieBuddy.jar);
            // Zomdroid's installer also finds it here and installs it as the Java agent.
            zip.putNextEntry(ZipEntry("$FOLDER/libs/ZombieBuddy.jar"))
            jar.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
