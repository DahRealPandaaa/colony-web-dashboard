package DahRealPanda.plugins.colonyweb.service

import com.google.gson.JsonObject
import DahRealPanda.plugins.colonyweb.web.JsonUtil
import java.util.concurrent.ConcurrentLinkedQueue

data class SseEvent(val data: String, val event: String? = null)

class SseService {
    private val listeners = ConcurrentLinkedQueue<(SseEvent) -> Unit>()

    val clientCount: Int get() = listeners.size

    /** Registers a listener and returns a function to unregister it. */
    fun subscribe(listener: (SseEvent) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun broadcast(jsonData: String) {
        val event = SseEvent(data = jsonData, event = "update")
        for (l in listeners) {
            try { l(event) } catch (_: Exception) { /* client disconnected */ }
        }
    }

    fun broadcast(data: JsonObject) {
        broadcast(JsonUtil.gson.toJson(data))
    }

    fun heartbeat() {
        for (l in listeners) {
            try { l(SseEvent(data = ": ping")) } catch (_: Exception) {}
        }
    }

    fun closeAll() = Unit
}
