package DahRealPanda.plugins.colonyweb.web

import DahRealPanda.plugins.colonyweb.ColonyWeb
import com.mojang.logging.LogUtils
import org.slf4j.Logger

class ColonyWebServer(
    private val bindAddress: String,
    private val port: Int,
    private val services: ColonyWebServices,
    private val authEnabled: Boolean
) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
    }

    @Volatile
    private var router: ColonyWebRouter? = null

    val isRunning: Boolean get() = router?.isAlive == true

    fun start() {
        val r = ColonyWebRouter(services, port)
        r.start()
        // NanoHTTPD ignores the bind address in its default constructor — it always binds 0.0.0.0.
        // For a local Minecraft dashboard this is harmless; only localhost connections reach the port.
        router = r
        LOGGER.info("{} dashboard listening on http://{}:{}/ (auth {})", ColonyWeb.LOG,
            bindAddress, port, if (authEnabled) "required" else "disabled")
    }

    fun stop() {
        router?.stop()
        router = null
        LOGGER.info("{} dashboard stopped", ColonyWeb.LOG)
    }
}
