package DahRealPanda.plugins.colonyweb

import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.facade.AuthFacade
import DahRealPanda.plugins.colonyweb.facade.ColonyFacade
import DahRealPanda.plugins.colonyweb.facade.ColonyScanFacade
import DahRealPanda.plugins.colonyweb.facade.EventsFacade
import DahRealPanda.plugins.colonyweb.facade.MapFacade
import DahRealPanda.plugins.colonyweb.facade.TextureFacade
import DahRealPanda.plugins.colonyweb.platform.Platform
import DahRealPanda.plugins.colonyweb.provider.VanillaAssetProvider
import DahRealPanda.plugins.colonyweb.repository.ColonyRepository
import DahRealPanda.plugins.colonyweb.service.BuildingService
import DahRealPanda.plugins.colonyweb.service.CitizenService
import DahRealPanda.plugins.colonyweb.service.ColonyMapService
import DahRealPanda.plugins.colonyweb.service.ColonyRefreshScheduler
import DahRealPanda.plugins.colonyweb.service.CombatService
import DahRealPanda.plugins.colonyweb.service.EquipmentService
import DahRealPanda.plugins.colonyweb.service.RecipeService
import DahRealPanda.plugins.colonyweb.service.ResearchService
import DahRealPanda.plugins.colonyweb.service.SseService
import DahRealPanda.plugins.colonyweb.service.StatsService
import DahRealPanda.plugins.colonyweb.service.TextureService
import DahRealPanda.plugins.colonyweb.service.WarehouseService
import DahRealPanda.plugins.colonyweb.service.WorkOrderService
import DahRealPanda.plugins.colonyweb.web.ColonyWebServer
import DahRealPanda.plugins.colonyweb.web.ColonyWebServices
import com.mojang.logging.LogUtils
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class ColonyWebService private constructor(server: MinecraftServer) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val DATA_DIR = "colonyweb"

        @Volatile private var instance: ColonyWebService? = null

        @JvmStatic fun get(): ColonyWebService? = instance

        @JvmStatic fun start(server: MinecraftServer) { stop(); val s = ColonyWebService(server); instance = s; s.startInternal() }
        @JvmStatic fun stop() { instance?.stopInternal(); instance = null }
    }

    val auth: AuthService
    val sseService = SseService()
    val scanFacade: ColonyScanFacade
    val maps: ColonyMapService
    val textureService: TextureService
    private val vanillaAssets: VanillaAssetProvider
    private val webServer: ColonyWebServer
    private val scheduler: ColonyRefreshScheduler
    private val buildingService = BuildingService()
    private val citizenService = CitizenService()
    private val combatService = CombatService()
    private val researchService = ResearchService()

    init {
        val platform = Platform.get()
        val dataDir = platform.serverDataDir(server, DATA_DIR)
        val repo = ColonyRepository(server)

        maps = ColonyMapService(repo)

        val warehouseService = WarehouseService()
        val workOrderService = WorkOrderService()
        val equipmentService = EquipmentService()
        val recipeService = RecipeService()
        val statsService = StatsService()

        scanFacade = ColonyScanFacade(repo, buildingService, warehouseService, workOrderService,
            citizenService, equipmentService, combatService, researchService, recipeService, statsService,
            maps, sseService)

        auth = AuthService(dataDir)
        vanillaAssets = VanillaAssetProvider(platform.minecraftVersion(), dataDir)
        textureService = TextureService(dataDir, vanillaAssets)

        val colonyFacade = ColonyFacade(buildingService, citizenService, combatService, researchService, maps, auth)
        val webServices = ColonyWebServices(AuthFacade(auth), colonyFacade, EventsFacade(sseService, auth),
            TextureFacade(textureService), MapFacade(maps, auth))

        webServer = ColonyWebServer(Config.bindAddress, Config.httpPort, webServices, auth.enabled())
        scheduler = ColonyRefreshScheduler(server, scanFacade, sseService, auth)
    }

    private fun startInternal() {
        if (Config.autoDownloadVanillaAssets) CompletableFuture.runAsync { vanillaAssets.ensureDownloaded() }
        try { webServer.start() }
        catch (e: Exception) { LOGGER.error("{} failed to start web server on {}:{}", ColonyWeb.LOG, Config.bindAddress, Config.httpPort, e) }
        scheduler.start()
    }

    private fun stopInternal() { scheduler.stop(); webServer.stop(); maps.stop() }

    fun isWebRunning(): Boolean = webServer.isRunning
    fun getPort(): Int = Config.httpPort
    fun getSseClients(): Int = sseService.clientCount
    fun isMineColoniesDetected(): Boolean = scanFacade.isAvailable
}
