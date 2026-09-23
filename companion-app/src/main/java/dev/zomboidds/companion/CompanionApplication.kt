package dev.zomboidds.companion

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
     * Item icons come from the bridge and never change for a game install, so they're kept in a
     * disk cache: each icon is extracted from the game files only once.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }
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
