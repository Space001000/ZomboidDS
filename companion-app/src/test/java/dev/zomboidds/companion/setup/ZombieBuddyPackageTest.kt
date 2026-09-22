package dev.zomboidds.companion.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZombieBuddyPackageTest {

    /** Shaped like GitHub's source archive for a tag: everything under `<repo>-<tag>/`. */
    private val sourceArchive = zip(
        "ZombieBuddy-2.3.3/42/mod.info" to "id=ZombieBuddy",
        "ZombieBuddy-2.3.3/42/media/lua/client/ZombieBuddy.lua" to "-- lua",
        "ZombieBuddy-2.3.3/common/media/ui/icon.png" to "png",
        "ZombieBuddy-2.3.3/LICENSE.txt" to "MIT",
        "ZombieBuddy-2.3.3/java/src/Main.java" to "class Main {}",
        "ZombieBuddy-2.3.3/README.md" to "readme",
    )

    @Test
    fun `keeps only the mod files`() {
        val files = ZombieBuddyPackage.extractModFiles(ByteArrayInputStream(sourceArchive))
        assertEquals(
            listOf("42/media/lua/client/ZombieBuddy.lua", "42/mod.info", "LICENSE.txt", "common/media/ui/icon.png"),
            files.keys.toList(),
        )
    }

    @Test
    fun `full zip has the layout Zomdroid's installer expects`() {
        val files = ZombieBuddyPackage.extractModFiles(ByteArrayInputStream(sourceArchive))
        val jar = File.createTempFile("ZombieBuddy", ".jar").apply { writeText("jar"); deleteOnExit() }

        val out = ByteArrayOutputStream()
        ZombieBuddyPackage.writeFullZip(jar, files, out)

        val entries = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertEquals(
            listOf(
                "ZombieBuddy/42/media/lua/client/ZombieBuddy.lua",
                "ZombieBuddy/42/mod.info",
                "ZombieBuddy/LICENSE.txt",
                "ZombieBuddy/common/media/ui/icon.png",
                "ZombieBuddy/libs/ZombieBuddy.jar",
            ),
            entries,
        )
    }

    @Test
    fun `digest depends on content, not on order`() {
        val a = mapOf("42/mod.info" to "x".toByteArray(), "LICENSE.txt" to "MIT".toByteArray())
        val reordered = linkedMapOf("LICENSE.txt" to "MIT".toByteArray(), "42/mod.info" to "x".toByteArray())
        val changed = mapOf("42/mod.info" to "y".toByteArray(), "LICENSE.txt" to "MIT".toByteArray())

        assertEquals(ZombieBuddyPackage.modFilesDigest(a), ZombieBuddyPackage.modFilesDigest(reordered))
        assertNotEquals(ZombieBuddyPackage.modFilesDigest(a), ZombieBuddyPackage.modFilesDigest(changed))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects path traversal`() {
        ZombieBuddyPackage.extractModFiles(ByteArrayInputStream(zip("ZombieBuddy-2.3.3/42/../../evil.lua" to "x")))
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
