package DahRealPanda.plugins.colonyweb;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Configuration for the Colony Web Dashboard.
 *
 * <p>All settings are server side because the whole mod runs on the server only.</p>
 */
@Mod.EventBusSubscriber(modid = ColonyWeb.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue HTTP_PORT = BUILDER
            .comment("Port the dashboard web server listens on.")
            .defineInRange("httpPort", 8723, 1, 65535);

    private static final ForgeConfigSpec.ConfigValue<String> BIND_ADDRESS = BUILDER
            .comment("Address the web server binds to. Use 0.0.0.0 to expose it on the network, or 127.0.0.1 for local only.")
            .define("bindAddress", "0.0.0.0");

    private static final ForgeConfigSpec.IntValue REFRESH_INTERVAL_SECONDS = BUILDER
            .comment("How often (in seconds) the server re-scans colony data and pushes SSE updates to connected browsers.")
            .defineInRange("refreshIntervalSeconds", 3, 1, 3600);

    private static final ForgeConfigSpec.BooleanValue AUTO_DOWNLOAD_ASSETS = BUILDER
            .comment(
                    "When true the server downloads the vanilla client jar (matching this Minecraft version) on first",
                    "start and caches its textures so vanilla item/block icons can be shown on the dashboard.",
                    "A dedicated server has no client textures of its own, so this is required for vanilla icons.")
            .define("autoDownloadVanillaAssets", true);

    private static final ForgeConfigSpec.ConfigValue<String> PUBLIC_HOST = BUILDER
            .comment("Host name shown in the /colonyweb command link. Leave blank to use the server's detected address.")
            .define("publicHost", "");

    private static final ForgeConfigSpec.BooleanValue AUTH_ENABLED = BUILDER
            .comment(
                    "Require players to sign in before they can see any colony data.",
                    "Players run /colonyweb sync in-game to get a pairing code, then enter it on the dashboard.",
                    "Each player only sees the colonies they belong to (plus anything an operator granted).",
                    "Turning this OFF makes the dashboard fully public to anyone who can reach the port.")
            .define("authEnabled", true);

    private static final ForgeConfigSpec.IntValue SESSION_DAYS = BUILDER
            .comment("How many days a browser stays signed in after entering a pairing code.")
            .defineInRange("sessionDays", 30, 1, 365);

    private static final ForgeConfigSpec.IntValue LOGIN_CODE_MINUTES = BUILDER
            .comment("How long a pairing code from /colonyweb sync stays valid, in minutes.")
            .defineInRange("loginCodeMinutes", 10, 1, 1440);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int httpPort;
    public static String bindAddress;
    public static int refreshIntervalSeconds;
    public static boolean autoDownloadVanillaAssets;
    public static String publicHost;
    public static boolean authEnabled;
    public static int sessionDays;
    public static int loginCodeMinutes;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        httpPort = HTTP_PORT.get();
        bindAddress = BIND_ADDRESS.get();
        refreshIntervalSeconds = REFRESH_INTERVAL_SECONDS.get();
        autoDownloadVanillaAssets = AUTO_DOWNLOAD_ASSETS.get();
        publicHost = PUBLIC_HOST.get();
        authEnabled = AUTH_ENABLED.get();
        sessionDays = SESSION_DAYS.get();
        loginCodeMinutes = LOGIN_CODE_MINUTES.get();
    }
}
