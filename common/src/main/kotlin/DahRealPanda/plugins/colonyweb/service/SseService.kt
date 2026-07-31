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
        deliver(SseEvent(data = jsonData, event = "update"))
    }

    fun broadcast(data: JsonObject) {
        broadcast(JsonUtil.gson.toJson(data))
    }

    fun heartbeat() {
        deliver(SseEvent(data = ": ping"))
    }

    /**
     * Delivers [event] to every listener, dropping the ones that throw. A listener only fails once
     * its client is gone, and a caller that never got to unsubscribe cannot remove it either, so
     * keeping it would make every later broadcast pay its exception cost and let the queue grow
     * without bound.
     */
    private fun deliver(event: SseEvent) {
        val it = listeners.iterator()
        while (it.hasNext()) {
            val listener = it.next()
            try {
                listener(event)
            } catch (_: Exception) {
                it.remove() // client disconnected
            }
        }
    }

    /** Drops every listener, so restarting the service does not inherit the previous run's clients. */
    fun closeAll() {
        listeners.clear()
    }
}
