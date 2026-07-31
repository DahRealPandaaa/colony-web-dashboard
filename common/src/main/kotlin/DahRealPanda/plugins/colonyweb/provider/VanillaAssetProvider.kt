package DahRealPanda.plugins.colonyweb.provider

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.logging.LogUtils
import org.slf4j.Logger
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Downloads and caches the vanilla client jar for the running MC version so that a
 * dedicated server (which has no client assets) can still serve vanilla item/block icons.
 */
class VanillaAssetProvider(
    private val minecraftVersion: String,
    private val cacheDir: Path
) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()

        private const val VERSION_MANIFEST =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    }

    private val clientJar: Path = cacheDir.resolve("client-$minecraftVersion.jar")

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    private var attempted: Boolean = false

    @Volatile
    private var clientZip: ZipFile? = null

    /** Ensure the client jar is present (downloading once). Safe to call repeatedly. */
    @Synchronized
    fun ensureDownloaded() {
        if (isReady) return
        if (Files.isRegularFile(clientJar)) {
            isReady = true
            return
        }
        if (attempted) return
        attempted = true
        try {
            Files.createDirectories(cacheDir)
            val versionUrl = findVersionJsonUrl()
            if (versionUrl == null) {
                LOGGER.warn("[ColonyWeb] could not find version manifest entry for {}", minecraftVersion)
                return
            }
            val clientUrl = readClientUrl(versionUrl)
            if (clientUrl == null) {
                LOGGER.warn("[ColonyWeb] no client download url for {}", minecraftVersion)
                return
            }
            LOGGER.info("[ColonyWeb] downloading vanilla client jar for {} ...", minecraftVersion)
            downloadTo(clientUrl, clientJar)
            isReady = Files.isRegularFile(clientJar)
            LOGGER.info("[ColonyWeb] vanilla client assets ready: {}", isReady)
        } catch (e: Exception) {
            LOGGER.warn("[ColonyWeb] vanilla asset download failed (icons will use placeholders)", e)
        }
    }

    /** Read a texture PNG (`assets/minecraft/textures/<path>.png`) from the cached jar. */
    fun readTexture(assetPath: String): ByteArray? {
        if (!isReady) return null
        val entryName = "assets/minecraft/$assetPath"
        return try {
            val zip = jar() ?: return null
            val entry = zip.getEntry(entryName) ?: return null
            zip.getInputStream(entry).use { it.readAllBytes() }
        } catch (e: java.io.IOException) {
            LOGGER.debug("[ColonyWeb] failed reading vanilla asset {}", entryName, e)
            null
        }
    }

    /** Read a model/JSON resource (`assets/minecraft/<path>`) from the cached jar. */
    fun readAsset(assetPath: String): String? {
        if (!isReady) return null
        val entryName = "assets/minecraft/$assetPath"
        return try {
            val zip = jar() ?: return null
            val entry = zip.getEntry(entryName) ?: return null
            zip.getInputStream(entry).use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
        } catch (_: java.io.IOException) {
            null
        }
    }

    /** Lazily open and cache a single shared [ZipFile] (thread-safe for concurrent reads). */
    private fun jar(): ZipFile? {
        var zip = clientZip
        if (zip == null) {
            synchronized(this) {
                if (clientZip == null && Files.isRegularFile(clientJar)) {
                    try {
                        clientZip = ZipFile(clientJar.toFile())
                    } catch (e: java.io.IOException) {
                        LOGGER.warn("[ColonyWeb] could not open cached client jar", e)
                    }
                }
                zip = clientZip
            }
        }
        return zip
    }

    private fun findVersionJsonUrl(): String? {
        val json = httpGet(VERSION_MANIFEST) ?: return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val versions = root.getAsJsonArray("versions")
            for (el in versions) {
                val v = el.asJsonObject
                if (minecraftVersion == v.get("id").asString) {
                    return v.get("url").asString
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun readClientUrl(versionUrl: String): String? {
        val json = httpGet(versionUrl) ?: return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val downloads = root.getAsJsonObject("downloads") ?: return null
            val client = downloads.getAsJsonObject("client") ?: return null
            client.get("url").asString
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "ColonyWebDashboard")
            try {
                conn.inputStream.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadTo(url: String, target: Path) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 120000
        conn.setRequestProperty("User-Agent", "ColonyWebDashboard")
        val tmp = target.resolveSibling("${target.fileName}.tmp")
        try {
            conn.inputStream.use { Files.copy(it, tmp, StandardCopyOption.REPLACE_EXISTING) }
        } finally {
            conn.disconnect()
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
