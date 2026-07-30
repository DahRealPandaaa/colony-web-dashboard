package DahRealPanda.plugins.colonyweb

import DahRealPanda.plugins.colonyweb.platform.ConfigBuilder
import java.util.function.BooleanSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

object Config {
    @JvmField var httpPort: Int = 0
    @JvmField var bindAddress: String = ""
    @JvmField var refreshIntervalSeconds: Int = 0
    @JvmField var autoDownloadVanillaAssets: Boolean = false
    @JvmField var mapEnabled: Boolean = false
    @JvmField var mapRadius: Int = 0
    @JvmField var publicHost: String = ""
    @JvmField var authEnabled: Boolean = false
    @JvmField var sessionDays: Int = 0
    @JvmField var loginCodeMinutes: Int = 0

    private val APPLY = mutableListOf<Runnable>()

    @JvmStatic
    fun define(builder: ConfigBuilder) {
        APPLY.clear()

        val httpPort: IntSupplier = builder.defineInt("httpPort", 8723, 1, 65535,
            "Port the dashboard web server listens on.")

        val bindAddress: Supplier<String> = builder.defineString("bindAddress", "0.0.0.0",
            "Address the web server binds to. Use 0.0.0.0 to expose it on the network, or 127.0.0.1 for local only.")

        val refreshIntervalSeconds: IntSupplier = builder.defineInt("refreshIntervalSeconds", 3, 1, 3600,
            "How often (in seconds) the server re-scans colony data and pushes SSE updates to connected browsers.")

        val autoDownloadVanillaAssets: BooleanSupplier = builder.defineBoolean("autoDownloadVanillaAssets", true,
            "When true the server downloads the vanilla client jar (matching this Minecraft version) on first",
            "start and caches its textures so vanilla item/block icons can be shown on the dashboard.",
            "A dedicated server has no client textures of its own, so this is required for vanilla icons.")

        val mapEnabled: BooleanSupplier = builder.defineBoolean("mapEnabled", true,
            "Show the colony map tab on the dashboard.",
            "The map is drawn from chunks the server already has loaded, a few at a time, and only",
            "while somebody actually has the map open — so it costs nothing when nobody is looking.")

        val mapRadius: IntSupplier = builder.defineInt("mapRadius", 256, 64, 512,
            "How far from the colony centre the map reaches, in blocks.",
            "The image is one pixel per block, so 256 means at most a 512x512 map per colony.")

        val publicHost: Supplier<String> = builder.defineString("publicHost", "",
            "Host name shown in the /colonyweb command link. Leave blank to use the server's detected address.")

        val authEnabled: BooleanSupplier = builder.defineBoolean("authEnabled", true,
            "Require players to sign in before they can see any colony data.",
            "Players run /colonyweb sync in-game to get a pairing code, then enter it on the dashboard.",
            "Each player only sees the colonies they belong to (plus anything an operator granted).",
            "Turning this OFF makes the dashboard fully public to anyone who can reach the port.")

        val sessionDays: IntSupplier = builder.defineInt("sessionDays", 30, 1, 365,
            "How many days a browser stays signed in after entering a pairing code.")

        val loginCodeMinutes: IntSupplier = builder.defineInt("loginCodeMinutes", 10, 1, 1440,
            "How long a pairing code from /colonyweb sync stays valid, in minutes.")

        APPLY.add(Runnable {
            Config.httpPort = httpPort.getAsInt()
            Config.bindAddress = bindAddress.get()
            Config.refreshIntervalSeconds = refreshIntervalSeconds.getAsInt()
            Config.autoDownloadVanillaAssets = autoDownloadVanillaAssets.getAsBoolean()
            Config.mapEnabled = mapEnabled.getAsBoolean()
            Config.mapRadius = mapRadius.getAsInt()
            Config.publicHost = publicHost.get()
            Config.authEnabled = authEnabled.getAsBoolean()
            Config.sessionDays = sessionDays.getAsInt()
            Config.loginCodeMinutes = loginCodeMinutes.getAsInt()
        })
    }

    @JvmStatic
    fun reload() {
        APPLY.forEach(Runnable::run)
    }
}
