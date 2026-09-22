package dev.zomboidds.companion.setup

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import dev.zomboidds.companion.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Gets ZombieBuddy for the user: downloads the pinned version from ZombieBuddy's own GitHub,
 * verifies it against the hashes in gradle.properties, and saves the full zip to Downloads,
 * ready to pick in Zomdroid (Optimization → ZombieBuddy). We don't ship or host ZombieBuddy.
 */
class ZombieBuddyFetcher(private val context: Context, private val client: OkHttpClient) {

    private val version = BuildConfig.ZOMBIE_BUDDY_VERSION
    val fileName = "ZombieBuddy-$version-full.zip"

    /** Blocking; call off the main thread. Returns the name the file got in Downloads. */
    fun fetchToDownloads(): String {
        val work = File(context.cacheDir, "zombiebuddy").apply { deleteRecursively(); mkdirs() }
        try {
            val jar = download("$RELEASES/v$version/ZombieBuddy.jar", File(work, "ZombieBuddy.jar"))
            check(ZombieBuddyPackage.sha256(jar) == BuildConfig.ZOMBIE_BUDDY_JAR_SHA256) {
                "The downloaded ZombieBuddy.jar doesn't match the expected version. Nothing was saved."
            }

            val source = download("$SOURCE_ARCHIVES/v$version", File(work, "source.zip"))
            val modFiles = source.inputStream().use { ZombieBuddyPackage.extractModFiles(it) }
            check(ZombieBuddyPackage.modFilesDigest(modFiles) == BuildConfig.ZOMBIE_BUDDY_MOD_FILES_SHA256) {
                "The downloaded ZombieBuddy mod files don't match the expected version. Nothing was saved."
            }

            return saveToDownloads { out -> ZombieBuddyPackage.writeFullZip(jar, modFiles, out) }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun download(url: String, target: File): File {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Download failed (${response.code}). Check the internet connection." }
            target.outputStream().use { response.body.byteStream().copyTo(it) }
        }
        return target
    }

    private fun saveToDownloads(write: (java.io.OutputStream) -> Unit): String {
        val resolver = context.contentResolver
        val downloads = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // Replace an earlier copy we saved, instead of piling up "(1)", "(2)" duplicates.
        resolver.delete(downloads, "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(fileName))

        val item = resolver.insert(downloads, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }) ?: error("Could not create $fileName in Downloads")
        try {
            resolver.openOutputStream(item)!!.use(write)
            resolver.update(item, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            // Android renames it ("... (1).zip") if a file we don't own already has the name.
            return resolver.query(item, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: fileName
        } catch (e: Exception) {
            resolver.delete(item, null, null)
            throw e
        }
    }

    private companion object {
        const val RELEASES = "https://github.com/zed-0xff/ZombieBuddy/releases/download"
        const val SOURCE_ARCHIVES = "https://codeload.github.com/zed-0xff/ZombieBuddy/zip/refs/tags"
    }
}
