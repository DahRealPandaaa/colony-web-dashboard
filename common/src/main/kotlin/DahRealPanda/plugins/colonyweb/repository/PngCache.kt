package DahRealPanda.plugins.colonyweb.repository

import com.mojang.logging.LogUtils
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class PngCache(baseDir: Path) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val RENDER_VERSION = "3"
        private const val VERSION_FILE = ".render-version"
    }

    private val memory = ConcurrentHashMap<String, ByteArray>()
    private val diskDir: Path = baseDir.resolve("textures")

    init {
        try {
            Files.createDirectories(diskDir)
            discardStaleRenders()
        } catch (e: java.io.IOException) {
            LOGGER.warn("[ColonyWeb] could not create texture cache dir {}", diskDir, e)
        }
    }

    private fun discardStaleRenders() {
        val stamp = diskDir.resolve(VERSION_FILE)
        try {
            if (Files.isRegularFile(stamp) && RENDER_VERSION == Files.readString(stamp).trim()) return
            val dropped: Long
            Files.list(diskDir).use { entries ->
                dropped = entries.filter { p -> p.fileName.toString().endsWith(".png") }
                    .peek { p -> deleteQuietly(p) }
                    .count()
            }
            Files.writeString(stamp, RENDER_VERSION)
            if (dropped > 0) {
                LOGGER.info("[ColonyWeb] renderer updated — discarded {} cached icon(s), they will re-render", dropped)
            }
        } catch (e: java.io.IOException) {
            LOGGER.warn("[ColonyWeb] could not refresh the texture cache in {}", diskDir, e)
        }
    }

    private fun deleteQuietly(file: Path) {
        try { Files.deleteIfExists(file) } catch (e: java.io.IOException) {
            LOGGER.debug("[ColonyWeb] failed deleting stale cached texture {}", file, e)
        }
    }

    fun get(key: String): ByteArray? {
        val mem = memory[key] ?: run {
            val file = diskFile(key)
            try {
                if (Files.isRegularFile(file)) {
                    val bytes = Files.readAllBytes(file)
                    memory[key] = bytes
                    return bytes
                }
            } catch (e: java.io.IOException) {
                LOGGER.debug("[ColonyWeb] failed reading cached texture {}", key, e)
            }
            return null
        }
        return mem
    }

    fun put(key: String, bytes: ByteArray?) {
        if (bytes == null) return
        memory[key] = bytes
        try {
            Files.write(diskFile(key), bytes)
        } catch (e: java.io.IOException) {
            LOGGER.debug("[ColonyWeb] failed writing cached texture {}", key, e)
        }
    }

    private fun safeName(key: String): String {
        val sb = StringBuilder(key.length)
        for (c in key) {
            sb.append(if (c.isLetterOrDigit() || c == '.' || c == '-' || c == '_') c else '_')
        }
        return sb.toString()
    }

    private fun diskFile(key: String): Path = diskDir.resolve("${safeName(key)}.png")
}
