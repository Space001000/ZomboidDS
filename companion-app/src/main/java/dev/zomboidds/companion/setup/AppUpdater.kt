package dev.zomboidds.companion.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dev.zomboidds.companion.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** A published release of the app, from GitHub. */
data class AppRelease(val version: String, val apkUrl: String, val apkSize: Long?, val pageUrl: String?)

/** Where the app's own update stands, as the setup checklist shows it. */
sealed interface AppUpdateState {
    /** Debug builds are signed with another key, so a release couldn't replace them: no checks. */
    data object Disabled : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val version: String) : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Downloading(val release: AppRelease, val percent: Int?) : AppUpdateState
    data class Downloaded(val release: AppRelease, val file: File) : AppUpdateState
    data class Failed(val reason: String, val release: AppRelease? = null) : AppUpdateState
}

/**
 * Checks GitHub for a newer release of the app, downloads its APK and hands it to Android's
 * installer. Android only installs it over this app if it's signed with the same key, so a
 * tampered file can't replace it. Checks at most once a day, when the setup screen is shown.
 */
class AppUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) {
    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<AppUpdateState>(
        if (BuildConfig.DEBUG) AppUpdateState.Disabled else remembered() ?: AppUpdateState.UpToDate(currentVersion))
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private var job: Job? = null

    /** Checks if the last check is older than a day (or [force]). */
    fun checkIfDue(force: Boolean = false) {
        if (BuildConfig.DEBUG || job?.isActive == true) return
        if (_state.value is AppUpdateState.Downloading || _state.value is AppUpdateState.Downloaded) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (!force && System.currentTimeMillis() - last < DAY_MS) return
        job = scope.launch(Dispatchers.IO) {
            _state.value = AppUpdateState.Checking
            _state.value = runCatching {
                val body = client.newCall(Request.Builder().url(LATEST_RELEASE)
                    .header("Accept", "application/vnd.github+json").build()).execute().use { response ->
                    // 404: nothing published yet (no release, or the repository isn't public yet).
                    if (response.code == 404) return@use null
                    check(response.isSuccessful) { "GitHub answered ${response.code}" }
                    response.body.string()
                }
                val release = body?.let(::parseLatestRelease)
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                    .putString(KEY_VERSION, release?.version).putString(KEY_URL, release?.apkUrl)
                    .putLong(KEY_SIZE, release?.apkSize ?: -1).putString(KEY_PAGE, release?.pageUrl).apply()
                if (release != null && isNewer(release.version, currentVersion)) {
                    AppUpdateState.Available(release)
                } else {
                    AppUpdateState.UpToDate(currentVersion)
                }
            }.getOrElse {
                Log.w(TAG, "update check failed", it)
                AppUpdateState.Failed("Couldn't check for updates (${it.message ?: it.javaClass.simpleName})")
            }
        }
    }

    /** Downloads the release's APK into the app's cache, with progress. */
    fun download(release: AppRelease) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            _state.value = AppUpdateState.Downloading(release, 0)
            _state.value = runCatching {
                val dir = File(context.cacheDir, UPDATE_DIR).apply { deleteRecursively(); mkdirs() }
                val file = File(dir, "ZomboidDS-${release.version}.apk")
                client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                    check(response.isSuccessful) { "the download answered ${response.code}" }
                    val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSize
                    response.body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                _state.value = AppUpdateState.Downloading(release, total?.let { (done * 100 / it).toInt() })
                            }
                        }
                    }
                }
                AppUpdateState.Downloaded(release, file)
            }.getOrElse {
                Log.w(TAG, "update download failed", it)
                AppUpdateState.Failed("The download failed (${it.message ?: it.javaClass.simpleName})", release)
            }
        }
    }

    /**
     * Opens Android's installer for the downloaded APK. The first time, Android asks to allow this
     * app to install apps: we open that setting, and the user taps Install again afterwards.
     */
    fun install(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            _state.value = AppUpdateState.Failed("No installer found on this device")
        }
    }

    /** The last check's answer, so the checklist shows it without asking GitHub again. */
    private fun remembered(): AppUpdateState? {
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        if (!isNewer(version, currentVersion)) return null
        return AppUpdateState.Available(AppRelease(version, url, prefs.getLong(KEY_SIZE, -1).takeIf { it > 0 }, prefs.getString(KEY_PAGE, null)))
    }

    companion object {
        private const val TAG = "ZomboidDS"
        const val REPOSITORY = "Space001000/ZomboidDS"
        private const val LATEST_RELEASE = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        private const val APK_TYPE = "application/vnd.android.package-archive"
        const val UPDATE_DIR = "updates"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val KEY_LAST_CHECK = "lastCheck"
        private const val KEY_VERSION = "version"
        private const val KEY_URL = "url"
        private const val KEY_SIZE = "size"
        private const val KEY_PAGE = "page"
        private val json = Json { ignoreUnknownKeys = true }

        /** GitHub's "latest release" answer: its version (tag without "v") and APK, or null without one. */
        fun parseLatestRelease(body: String): AppRelease? {
            val release = json.parseToJsonElement(body).jsonObject
            val tag = release["tag_name"]?.jsonPrimitive?.content ?: return null
            val apk = release["assets"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { it.string("name")?.endsWith(".apk") == true } ?: return null
            return AppRelease(
                version = tag.removePrefix("v"),
                apkUrl = apk.string("browser_download_url") ?: return null,
                apkSize = apk["size"]?.jsonPrimitive?.longOrNull,
                pageUrl = release.string("html_url"),
            )
        }

        /** Whether [candidate] ("1.2.0") is a newer version than [current] ("1.1.3"), by number. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content
    }
}
