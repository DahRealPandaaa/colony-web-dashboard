package DahRealPanda.plugins.colonyweb.service

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.Config
import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.facade.ColonyScanFacade
import com.mojang.logging.LogUtils
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ColonyRefreshScheduler(
    private val server: MinecraftServer,
    private val scanFacade: ColonyScanFacade,
    private val sseService: SseService,
    private val auth: AuthService
) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val HOUSEKEEPING_SECONDS = 30
    }

    private var ticks = 0
    private var scheduler: java.util.concurrent.ScheduledExecutorService? = null

    fun start() {
        val s = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "colonyweb-scheduler").also { it.isDaemon = true }
        }
        scheduler = s
        val interval = maxOf(1, Config.refreshIntervalSeconds)
        s.scheduleAtFixedRate({ tick() }, interval.toLong(), interval.toLong(), TimeUnit.SECONDS)
    }

    fun stop() { scheduler?.shutdownNow(); scheduler = null }

    private fun tick() {
        try { server.execute { scanAndBroadcast() } }
        catch (e: Exception) { LOGGER.debug("{} failed to schedule scan", ColonyWeb.LOG, e) }
    }

    private fun scanAndBroadcast() {
        try {
            scanFacade.tick()

            if (++ticks % housekeepingEveryNScans() == 0) {
                sseService.heartbeat()
                auth.purgeExpiredSessions()
            }
        } catch (e: Exception) { LOGGER.debug("{} scan failed", ColonyWeb.LOG, e) }
    }

    private fun housekeepingEveryNScans(): Int =
        maxOf(1, HOUSEKEEPING_SECONDS / maxOf(1, Config.refreshIntervalSeconds))
}
