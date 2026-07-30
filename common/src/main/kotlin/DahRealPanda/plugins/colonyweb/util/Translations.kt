package DahRealPanda.plugins.colonyweb.util

import DahRealPanda.plugins.colonyweb.platform.Platform
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.logging.LogUtils
import org.slf4j.Logger
import java.io.InputStream
import java.nio.charset.StandardCharsets

object Translations {
    private val LOGGER: Logger = LogUtils.getLogger()
    private val GSON = Gson()

    @Volatile
    private var table: Map<String, String>? = null

    @JvmStatic
    fun lookup(key: String?): String? {
        if (key.isNullOrEmpty()) return null
        return table()?.get(key)
    }

    @JvmStatic
    fun format(key: String, args: Array<*>?): String? {
        val pattern = lookup(key) ?: return null
        return if (args.isNullOrEmpty()) pattern else substitute(pattern, args)
    }

    private fun substitute(pattern: String, args: Array<*>): String {
        val out = StringBuilder(pattern.length + 16)
        var next = 0
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c != '%' || i == pattern.length - 1) {
                out.append(c)
                i++
                continue
            }
            val after = pattern[i + 1]
            if (after == '%') {
                out.append('%')
                i += 2
                continue
            }
            var j = i + 1
            var index = -1
            var digits = j
            while (digits < pattern.length && Character.isDigit(pattern[digits])) {
                digits++
            }
            if (digits > j && digits < pattern.length && pattern[digits] == '$') {
                index = pattern.substring(j, digits).toInt() - 1
                j = digits + 1
            }
            if (j >= pattern.length || "sdf".indexOf(pattern[j]) < 0) {
                out.append(c)
                i++
                continue
            }
            val use = if (index >= 0) index else next++
            out.append(if (use >= 0 && use < args.size) argString(args[use]) else "")
            i = j + 1
        }
        return out.toString()
    }

    private fun argString(arg: Any?): String {
        val s = Text.componentString(arg)
        return s ?: ""
    }

    private fun table(): Map<String, String>? {
        var loaded = table
        if (loaded == null) {
            synchronized(this@Translations) {
                loaded = table
                if (loaded == null) {
                    loaded = load()
                    table = loaded
                }
            }
        }
        return loaded
    }

    private fun load(): Map<String, String>? {
        val merged = HashMap<String, String>()
        for (modId in modIds()) {
            val bytes = classpathBytes("assets/$modId/lang/en_us.json") ?: continue
            try {
                val json = GSON.fromJson(String(bytes, StandardCharsets.UTF_8), JsonObject::class.java)
                    ?: continue
                for ((key, value) in json.entrySet()) {
                    if (value is JsonElement && value.isJsonPrimitive) {
                        merged[key] = value.asString
                    }
                }
            } catch (e: Exception) {
                LOGGER.debug("[ColonyWeb] could not read the language file for {}", modId, e)
            }
        }
        LOGGER.info("[ColonyWeb] loaded {} modded translations", merged.size)
        return merged
    }

    private fun modIds(): Iterable<String> {
        return try {
            Platform.get().loadedModIds()
        } catch (_: Throwable) {
            listOf("minecolonies", "domum_ornamentum")
        }
    }

    private fun classpathBytes(resource: String): ByteArray? {
        return try {
            val stream: InputStream? = Translations::class.java.classLoader.getResourceAsStream(resource)
            stream?.readAllBytes()
        } catch (_: Exception) {
            null
        }
    }
}
