package dev.zomboidds.companion

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import coil3.util.DebugLogger
import okio.Path.Companion.toOkioPath

class CompanionApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.gateway.start(container.appScope)
    }

    /**
     * The game's icons come from the bridge and never change for a game install, so they're kept in
     * a disk cache: each icon is extracted from the game files only once. Mods' icons can change; the
     * bridge marks those "no-store".
     */
    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient }, cacheStrategy = { RespectNoStore })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("icons").toOkioPath())
                    .maxSizeBytes(20L * 1024 * 1024)
                    .build()
            }
            // Debug builds: log why an icon fails to load (logcat tag "RealImageLoader" and friends).
            .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
            .build()
}

/** Coil's default strategy keeps every response on disk, also ones marked "no-store". */
@OptIn(ExperimentalCoilApi::class)
private object RespectNoStore : CacheStrategy by CacheStrategy.DEFAULT {
    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ) = if (networkResponse.headers["Cache-Control"] == "no-store") {
        CacheStrategy.WriteResult.DISABLED
    } else {
        CacheStrategy.DEFAULT.write(cacheResponse, networkRequest, networkResponse, options)
    }
}
