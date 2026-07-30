package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.auth.AuthService
import DahRealPanda.plugins.colonyweb.auth.WebUser
import DahRealPanda.plugins.colonyweb.service.SseEvent
import DahRealPanda.plugins.colonyweb.service.SseService

class EventsFacade(
    private val sseService: SseService,
    private val auth: AuthService
) {
    fun subscribe(listener: (SseEvent) -> Unit): () -> Unit = sseService.subscribe(listener)

    fun authenticate(token: String?): WebUser? {
        if (!auth.enabled()) return null
        return auth.userForToken(token)
    }

    fun isAuthEnabled(): Boolean = auth.enabled()
}
