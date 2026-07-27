package DahRealPanda.plugins.colonyweb;

import DahRealPanda.plugins.colonyweb.platform.ConfigBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Configuration for the Colony Web Dashboard.
 *
 * <p>All settings are server side because the whole mod runs on the server only.</p>
 *
 * <p>The options are declared once, here, against the loader-neutral {@link ConfigBuilder}; each
 * {@code versions/<mc>-<loader>} project supplies a short adapter over its own config-spec
 * builder. The values themselves stay plain static fields, so the rest of the mod reads them
 * without knowing a loader exists.</p>
 */
public final class Config {

    // ------------------------------------------------------------------
    // Live values. Valid from the first config load until the server stops.
    // ------------------------------------------------------------------

    public static int httpPort;
    public static String bindAddress;
    public static int refreshIntervalSeconds;
    public static boolean autoDownloadVanillaAssets;
    public static boolean mapEnabled;
    public static int mapRadius;
    public static String publicHost;
    public static boolean authEnabled;
    public static int sessionDays;
    public static int loginCodeMinutes;

    /** Copies each loaded value into the field above. Populated by {@link #define}. */
    private static final List<Runnable> APPLY = new ArrayList<>();

    private Config() {
    }

    /**
     * Declare every setting against a loader's config spec. Called once, from the loader's
     * {@code @Mod} entrypoint, while that spec is being built.
     */
    public static void define(ConfigBuilder builder) {
        APPLY.clear();

        IntSupplier httpPort = builder.defineInt("httpPort", 8723, 1, 65535,
                "Port the dashboard web server listens on.");

        Supplier<String> bindAddress = builder.defineString("bindAddress", "0.0.0.0",
                "Address the web server binds to. Use 0.0.0.0 to expose it on the network, or 127.0.0.1 for local only.");

        IntSupplier refreshIntervalSeconds = builder.defineInt("refreshIntervalSeconds", 3, 1, 3600,
                "How often (in seconds) the server re-scans colony data and pushes SSE updates to connected browsers.");

        BooleanSupplier autoDownloadVanillaAssets = builder.defineBoolean("autoDownloadVanillaAssets", true,
                "When true the server downloads the vanilla client jar (matching this Minecraft version) on first",
                "start and caches its textures so vanilla item/block icons can be shown on the dashboard.",
                "A dedicated server has no client textures of its own, so this is required for vanilla icons.");

        BooleanSupplier mapEnabled = builder.defineBoolean("mapEnabled", true,
                "Show the colony map tab on the dashboard.",
                "The map is drawn from chunks the server already has loaded, a few at a time, and only",
                "while somebody actually has the map open — so it costs nothing when nobody is looking.");

        IntSupplier mapRadius = builder.defineInt("mapRadius", 256, 64, 512,
                "How far from the colony centre the map reaches, in blocks.",
                "The image is one pixel per block, so 256 means at most a 512x512 map per colony.");

        Supplier<String> publicHost = builder.defineString("publicHost", "",
                "Host name shown in the /colonyweb command link. Leave blank to use the server's detected address.");

        BooleanSupplier authEnabled = builder.defineBoolean("authEnabled", true,
                "Require players to sign in before they can see any colony data.",
                "Players run /colonyweb sync in-game to get a pairing code, then enter it on the dashboard.",
                "Each player only sees the colonies they belong to (plus anything an operator granted).",
                "Turning this OFF makes the dashboard fully public to anyone who can reach the port.");

        IntSupplier sessionDays = builder.defineInt("sessionDays", 30, 1, 365,
                "How many days a browser stays signed in after entering a pairing code.");

        IntSupplier loginCodeMinutes = builder.defineInt("loginCodeMinutes", 10, 1, 1440,
                "How long a pairing code from /colonyweb sync stays valid, in minutes.");

        APPLY.add(() -> {
            Config.httpPort = httpPort.getAsInt();
            Config.bindAddress = bindAddress.get();
            Config.refreshIntervalSeconds = refreshIntervalSeconds.getAsInt();
            Config.autoDownloadVanillaAssets = autoDownloadVanillaAssets.getAsBoolean();
            Config.mapEnabled = mapEnabled.getAsBoolean();
            Config.mapRadius = mapRadius.getAsInt();
            Config.publicHost = publicHost.get();
            Config.authEnabled = authEnabled.getAsBoolean();
            Config.sessionDays = sessionDays.getAsInt();
            Config.loginCodeMinutes = loginCodeMinutes.getAsInt();
        });
    }

    /** Called by the loader whenever its config file is loaded or reloaded. */
    public static void reload() {
        APPLY.forEach(Runnable::run);
    }
}
