package DahRealPanda.plugins.untitled1.texture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Downloads and caches the vanilla client jar for the running MC version so that a
 * dedicated server (which has no client assets) can still serve vanilla item/block icons.
 */
public final class VanillaAssetProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String VERSION_MANIFEST =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    private final String minecraftVersion;
    private final Path cacheDir;
    private final Path clientJar;
    private volatile boolean ready;
    private volatile boolean attempted;

    public VanillaAssetProvider(String minecraftVersion, Path baseDir) {
        this.minecraftVersion = minecraftVersion;
        this.cacheDir = baseDir;
        this.clientJar = baseDir.resolve("client-" + minecraftVersion + ".jar");
    }

    public boolean isReady() {
        return ready;
    }

    /** Ensure the client jar is present (downloading once). Safe to call repeatedly. */
    public synchronized void ensureDownloaded() {
        if (ready) {
            return;
        }
        if (Files.isRegularFile(clientJar)) {
            ready = true;
            return;
        }
        if (attempted) {
            return;
        }
        attempted = true;
        try {
            Files.createDirectories(cacheDir);
            String versionUrl = findVersionJsonUrl();
            if (versionUrl == null) {
                LOGGER.warn("[ColonyWeb] could not find version manifest entry for {}", minecraftVersion);
                return;
            }
            String clientUrl = readClientUrl(versionUrl);
            if (clientUrl == null) {
                LOGGER.warn("[ColonyWeb] no client download url for {}", minecraftVersion);
                return;
            }
            LOGGER.info("[ColonyWeb] downloading vanilla client jar for {} ...", minecraftVersion);
            downloadTo(clientUrl, clientJar);
            ready = Files.isRegularFile(clientJar);
            LOGGER.info("[ColonyWeb] vanilla client assets ready: {}", ready);
        } catch (Exception e) {
            LOGGER.warn("[ColonyWeb] vanilla asset download failed (icons will use placeholders)", e);
        }
    }

    /** Read a texture PNG ({@code assets/minecraft/textures/<path>.png}) from the cached jar. */
    public byte[] readTexture(String assetPath) {
        if (!ready) {
            return null;
        }
        String entryName = "assets/minecraft/" + assetPath;
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            LOGGER.debug("[ColonyWeb] failed reading vanilla asset {}", entryName, e);
            return null;
        }
    }

    /** Read a model/JSON resource ({@code assets/minecraft/<path>}) from the cached jar. */
    public String readAsset(String assetPath) {
        if (!ready) {
            return null;
        }
        String entryName = "assets/minecraft/" + assetPath;
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private String findVersionJsonUrl() throws IOException {
        String json = httpGet(VERSION_MANIFEST);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray versions = root.getAsJsonArray("versions");
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            if (minecraftVersion.equals(v.get("id").getAsString())) {
                return v.get("url").getAsString();
            }
        }
        return null;
    }

    private String readClientUrl(String versionUrl) throws IOException {
        String json = httpGet(versionUrl);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject downloads = root.getAsJsonObject("downloads");
        if (downloads == null) {
            return null;
        }
        JsonObject client = downloads.getAsJsonObject("client");
        if (client == null) {
            return null;
        }
        return client.get("url").getAsString();
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "ColonyWebDashboard");
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static void downloadTo(String url, Path target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", "ColonyWebDashboard");
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
