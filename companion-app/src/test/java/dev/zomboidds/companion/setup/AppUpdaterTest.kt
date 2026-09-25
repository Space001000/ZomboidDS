package dev.zomboidds.companion.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    /** Trimmed from GitHub's GET /repos/{owner}/{repo}/releases/latest. */
    private val latest = """
        {
          "html_url": "https://github.com/Space001000/ZomboidDS/releases/tag/v1.2.0",
          "tag_name": "v1.2.0",
          "name": "ZomboidDS 1.2.0",
          "assets": [
            { "name": "checksums.txt", "size": 120, "browser_download_url": "https://example.invalid/checksums.txt" },
            { "name": "ZomboidDS-1.2.0.apk", "size": 3104187,
              "browser_download_url": "https://github.com/Space001000/ZomboidDS/releases/download/v1.2.0/ZomboidDS-1.2.0.apk" }
          ]
        }
    """.trimIndent()

    @Test
    fun `the latest release's version and APK come from GitHub's answer`() {
        val release = AppUpdater.parseLatestRelease(latest)!!
        assertEquals("1.2.0", release.version)
        assertTrue(release.apkUrl.endsWith("/ZomboidDS-1.2.0.apk"))
        assertEquals(3104187L, release.apkSize)
        assertEquals("https://github.com/Space001000/ZomboidDS/releases/tag/v1.2.0", release.pageUrl)
    }

    @Test
    fun `a release without an APK is no update`() {
        assertNull(AppUpdater.parseLatestRelease("""{ "tag_name": "v2.0.0", "assets": [] }"""))
    }

    @Test
    fun `versions compare by number, not as text`() {
        assertTrue(AppUpdater.isNewer("1.10.0", "1.9.3"))
        assertTrue(AppUpdater.isNewer("1.0.1", "1.0"))
        assertFalse(AppUpdater.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppUpdater.isNewer("0.9.9", "1.0.0"))
        assertFalse("a pre-release suffix doesn't make it newer", AppUpdater.isNewer("1.0.0-beta", "1.0.0"))
    }
}
