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
        // An empty bindAddress would resolve to the loopback interface, silently making the
        // dashboard local-only; fall back to the documented config default instead.
        val host = bindAddress.ifBlank { "0.0.0.0" }
        val r = ColonyWebRouter(services, host, port)
        r.start()
        router = r
        LOGGER.info("{} dashboard listening on http://{}:{}/ (auth {})", ColonyWeb.LOG,
            host, port, if (authEnabled) "required" else "disabled")
    }

    fun stop() {
        router?.stop()
        router = null
        LOGGER.info("{} dashboard stopped", ColonyWeb.LOG)
    }
}
