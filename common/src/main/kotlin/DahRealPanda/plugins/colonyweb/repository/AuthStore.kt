package DahRealPanda.plugins.colonyweb.repository

import DahRealPanda.plugins.colonyweb.ColonyWeb
import DahRealPanda.plugins.colonyweb.auth.WebUser
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.logging.LogUtils
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class AuthStore(private val file: Path) {
    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    fun load(): MutableMap<String, WebUser> {
        if (!Files.isRegularFile(file)) return LinkedHashMap()
        try {
            val json = Files.readString(file, StandardCharsets.UTF_8)
            val users: Map<String, WebUser>? = GSON.fromJson(
                json,
                object : TypeToken<LinkedHashMap<String, WebUser>>() {}.type
            )
            return if (users != null) LinkedHashMap(users) else LinkedHashMap()
        } catch (e: Exception) {
            LOGGER.error("{} could not read {} — starting with no accounts", ColonyWeb.LOG, file, e)
            return LinkedHashMap()
        }
    }

    fun save(users: Map<String, WebUser>) {
        try {
            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(tmp, GSON.toJson(users), StandardCharsets.UTF_8)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: java.io.IOException) {
            LOGGER.error("{} could not write {}", ColonyWeb.LOG, file, e)
        }
    }
}
