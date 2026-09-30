package dev.zomboidds.companion

import dev.zomboidds.companion.setup.AppUpdater
import android.content.Context
import dev.zomboidds.companion.data.WebSocketGameGateway
import dev.zomboidds.companion.data.WorldMapLoader
import dev.zomboidds.companion.domain.GameGateway
import dev.zomboidds.companion.setup.SetupController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Creates the app's long-lived objects. Swap implementations here (e.g. a fake gateway). */
class AppContainer(context: Context) {

    /** Lives as long as the process: the connection is kept while the activity is recreated. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS) // the bridge drops connections idle for 30 s
        .build()

    val gateway: GameGateway = WebSocketGameGateway(
        httpClient, "http://${BuildConfig.BRIDGE_HOST}:${BuildConfig.BRIDGE_PORT}")

    val setup = SetupController(context.applicationContext, appScope, httpClient)

    val updater = AppUpdater(context.applicationContext, appScope, httpClient)

    val settings = AppSettings(context.applicationContext)

    val worldMap = WorldMapLoader(context.applicationContext, appScope, setup)
}
